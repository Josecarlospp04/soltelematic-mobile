<?php


namespace App\Http\Controllers\Api\ClientLite;


use App\Http\Controllers\Controller;
use Carbon\Carbon;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Validator;
use Tobuli\Entities\Device;
use Tobuli\Entities\Sharing;
use Tobuli\Services\SharingService;

/**
 * PARCHE SOLTELEMATIC MOBILE -- compartir ubicacion por enlace temporal.
 *
 * La plataforma web resuelve esto en Frontend\SharingController@send, que usa sesion de
 * navegador + CSRF y ademas devuelve solo ['status' => 1]: el enlace nunca vuelve al cliente
 * porque la web lo manda por SMS/email desde el servidor. La app necesita el enlace en mano
 * (para copiarlo o compartirlo por WhatsApp), asi que este controlador devuelve el hash y la
 * URL ya armada. Esa es la UNICA diferencia de comportamiento respecto a la web.
 *
 * Toda la logica de negocio la sigue haciendo Tobuli\Services\SharingService, el mismo que usa
 * la web: lo creado desde la app aparece identico en la plataforma.
 */
class SharingController extends Controller
{
    private $sharingService;

    public function __construct(SharingService $sharingService)
    {
        parent::__construct();

        $this->sharingService = $sharingService;
    }

    protected function afterAuth($user)
    {
        $action = match (request()->method()) {
            'POST'   => 'create',
            'DELETE' => 'view',
            default  => 'view',
        };

        $this->checkException('sharing', $action);
    }

    public function index()
    {
        $shares = Sharing::where('user_id', $this->user->id)
            ->orderBy('id', 'DESC')
            ->get();

        return response()->json([
            'status' => 1,
            'data'   => $shares->map(fn (Sharing $share) => $this->present($share))->values(),
        ]);
    }

    /**
     * Espera: devices (array de ids), expiration_by ('duration'|'date'), y segun ese valor
     * duration (ENTERO EN MINUTOS, igual que la web: Carbon::addMinutes) o expiration_date.
     */
    public function store(Request $request)
    {
        $validator = Validator::make($request->all(), [
            'devices'         => 'required|array|min:1',
            'devices.*'       => 'required|integer',
            'name'            => 'nullable|string|max:255',
            'expiration_by'   => 'required|in:duration,date',
            'duration'        => 'required_if:expiration_by,duration|integer|min:1',
            'expiration_date' => 'required_if:expiration_by,date|date',
        ]);

        if ($validator->fails()) {
            return response()->json(['status' => 0, 'errors' => $validator->errors()], 422);
        }

        // La web filtra los dispositivos con el loader de sesion (UserDevicesGroupLoader), que
        // aca no existe: sin esta comprobacion un id ajeno se compartiria igual.
        $devices = Device::whereIn('id', $request->get('devices'))
            ->filterUserAbility($this->user);

        if ($devices->isEmpty()) {
            return response()->json(['status' => 0, 'message' => 'Not found'], 404);
        }

        $data = [
            'active'                  => 1,
            'name'                    => $request->get('name') ?: $devices->first()->name,
            'delete_after_expiration' => 0,
            'expiration_date'         => $this->resolveExpirationDate($request),
        ];

        $sharing = $this->sharingService->create($this->user->id, $data);
        $this->sharingService->syncDevices($sharing, $devices);

        return response()->json(['status' => 1] + $this->present($sharing));
    }

    public function destroy($id)
    {
        $sharing = Sharing::where('user_id', $this->user->id)->find($id);

        if (!$sharing) {
            return response()->json(['status' => 0, 'message' => 'Not found'], 404);
        }

        $this->sharingService->remove($sharing);

        return response()->json(['status' => 1, 'id' => (int) $id]);
    }

    /**
     * Mismo criterio que Frontend\SharingController::normalize(): 'duration' son MINUTOS a
     * partir de ahora; 'date' es una fecha del usuario y pasa por Formatter::time()->reverse
     * para interpretarse en SU huso horario, no en el del servidor.
     */
    private function resolveExpirationDate(Request $request)
    {
        if ($request->get('expiration_by') === 'duration') {
            return Carbon::now()->addMinutes((int) $request->get('duration'));
        }

        return \Formatter::time()->reverse($request->get('expiration_date'));
    }

    /**
     * hash es el identificador publico del enlace (ver ruta 'sharing/{hash}' en routes/web.php).
     * Se devuelve tambien la url ya armada para que la app no tenga que conocer el patron.
     */
    private function present(Sharing $sharing)
    {
        return [
            'id'              => (int) $sharing->id,
            'name'            => $sharing->name,
            'hash'            => $sharing->hash,
            'url'             => url('sharing/' . $sharing->hash),
            'active'          => (bool) $sharing->active,
            'expiration_date' => $sharing->expiration_date,
            'devices'         => $sharing->devices->pluck('id')->map(fn ($id) => (int) $id)->values(),
        ];
    }
}
