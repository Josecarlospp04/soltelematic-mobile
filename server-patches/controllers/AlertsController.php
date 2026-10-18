<?php


namespace App\Http\Controllers\Api\ClientLite;


use Illuminate\Http\Request;
use App\Http\Controllers\Controller;
use Tobuli\Entities\Alert;

/**
 * PARCHE SOLTELEMATIC MOBILE -- gestion de alertas (CRUD + activar/desactivar).
 *
 * Toda la logica y los permisos viven en \ModalHelpers\AlertModalHelper (la misma que usa la web);
 * este controlador solo la expone como JSON. Los errores de validacion (ValidationException) y de
 * permisos los resuelve el manejador global de excepciones, igual que en GeofencesController.
 *
 * El helper se instancia DENTRO de cada metodo (no inyectado ni via la facade): su constructor
 * crea loaders con Auth::User() en ese momento.
 */
class AlertsController extends Controller
{
    protected function afterAuth($user)
    {
        // POST alerts/active no crea nada: cada alerta se autoriza con $user->can('active', $alert).
        $creating = request()->method() === 'POST' && !request()->is('*/alerts/active');
        $action = $creating ? 'store' : 'view';
        $this->checkException('alerts', $action);
    }

    protected function helper(array $data = []): \ModalHelpers\AlertModalHelper
    {
        $helper = new \ModalHelpers\AlertModalHelper();
        $helper->setUser($this->user);
        $helper->setApi(true);
        $helper->setData($data);

        return $helper;
    }

    public function index(Request $request)
    {
        $alerts = $this->helper()->get()['alerts'];

        // El helper en modo API no trae pois ni zones: se cargan de una vez para todos los IDs.
        $ids = collect($alerts)->map(fn ($alert) => data_get($alert, 'id'))->filter()->values()->all();
        $relations = Alert::with(['pois', 'zones'])->whereIn('id', $ids)->get()->keyBy('id');

        $alerts = collect($alerts)->map(function ($alert) use ($relations) {
            $alert = is_array($alert)
                ? $alert
                : (method_exists($alert, 'toArray') ? $alert->toArray() : (array) $alert);

            $model = $relations->get($alert['id'] ?? null);

            $alert['pois'] = $model ? $model->pois->pluck('id')->values()->all() : [];
            $alert['zones'] = $model ? $model->zones->pluck('id')->values()->all() : [];

            return $alert;
        })->values()->all();

        return response()->json(['status' => 1, 'alerts' => $alerts]);
    }

    public function form(Request $request, $id = null)
    {
        $helper = $this->helper($id === null ? [] : ['alert_id' => $id]);

        $data = $id === null ? $helper->createData() : $helper->editData();
        $alert = $id === null ? null : ($data['item'] ?? null);

        // Tipos con titulo y campos propios, transformados como la web.
        $attributesByType = collect($helper->getTypesWithAttributes($alert))->keyBy('type');

        $data['types'] = collect(\ModalHelpers\AlertModalHelper::getTypes())->map(function ($type) use ($attributesByType) {
            $attributes = collect(data_get($attributesByType->get($type['type']), 'attributes', []))
                ->map(function ($attr) {
                    $item = [
                        'name'  => $attr->getName(),
                        'type'  => $attr->getType(),
                        'title' => $attr->getTitle(),
                    ];

                    if ($attr instanceof \Tobuli\InputFields\SelectField) {
                        $item['options'] = $attr->getOptions();
                    }

                    return $item;
                })
                ->values()
                ->all();

            return [
                'type'       => $type['type'],
                'title'      => $type['title'],
                'attributes' => $attributes,
            ];
        })->values()->all();

        return response()->json(['status' => 1] + $data);
    }

    public function store(Request $request)
    {
        $result = $this->helper($request->all())->create();

        return response()->json(['status' => 1, 'item' => $result['item'] ?? null]);
    }

    public function update(Request $request, $id)
    {
        // El id de la ruta manda sobre cualquier "id" que venga en el body.
        $this->helper(array_merge($request->all(), ['id' => $id]))->edit();

        return response()->json(['status' => 1]);
    }

    public function destroy($id)
    {
        $this->helper(['id' => $id])->destroy();

        return response()->json(['status' => 1]);
    }

    /**
     * Misma logica que el controlador web (no esta en el helper). body: { id: int|int[], active: bool }
     */
    public function active(Request $request)
    {
        $data = $request->all();
        $user = $this->user;

        $ids = (array) ($data['id'] ?? []);
        $items = Alert::enabled()->whereIn('id', $ids)->get()
            ->filter(fn ($alert) => $user->can('active', $alert));

        $active = filter_var($data['active'] ?? false, FILTER_VALIDATE_BOOLEAN) ? 1 : 0;

        Alert::whereIn('id', $items->pluck('id')->all())->update(['active' => $active]);

        return response()->json(['status' => 1]);
    }
}
