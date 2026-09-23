<?php


namespace App\Http\Controllers\Api\ClientLite;


use App\Http\Controllers\Controller;
use App\Transformers\ClientLite\DeviceServicesTransformer;
use CustomFacades\ModalHelpers\ServiceModalHelper;
use Illuminate\Http\Request;
use Tobuli\Entities\Device;
use Tobuli\Entities\DeviceService;

/**
 * PARCHE SOLTELEMATIC MOBILE -- servicios de mantenimiento desde la app.
 *
 * La creacion/edicion existe en la plataforma solo en rutas web (routes/web.php ~321-327,
 * Frontend\ServicesController), que usan sesion de navegador + CSRF. Toda la logica real vive
 * en ModalHelpers\ServiceModalHelper, asi que este controlador solo la expone con Bearer de
 * Passport: lo creado desde la app aparece identico en la plataforma web y viceversa.
 *
 * ⚠️ TRAMPAS del helper (verificadas leyendo el codigo, no son opinables). ServiceModalHelper y
 * prepareServiceData() leen estas claves SIN isset(), asi que si faltan es un error fatal de PHP
 * (500), no un 422 limpio:
 *   - email          (validate(): explode(';', $this->data['email']))
 *   - last_service   (prepareServiceData(): $input['last_service'])
 *   - expiration_by  (prepareServiceData(): $input['expiration_by'])
 * Por eso normalizeInput() las rellena SIEMPRE antes de delegar. mobile_phone si tiene default
 * en el helper, no hace falta.
 */
class ServicesController extends Controller
{
    protected function afterAuth($user)
    {
        // 'view' tambien para DELETE/POST: el exceptionManager no resuelve bien acciones sobre
        // devices sin el modelo, y la pertenencia real se verifica en cada metodo con
        // checkDeviceAccess()/userOwned. Mismo criterio que GeofencesController y SharingController.
        $this->checkException('devices', 'view');
    }

    /** Servicios de una unidad. La app ya los lee embebidos en device/{id}, esto es el listado propio. */
    public function index($device_id)
    {
        $device = $this->findUserDevice($device_id);

        if (!$device) {
            return response()->json(['status' => 0, 'message' => 'Not found'], 404);
        }

        $services = DeviceService::where('device_id', $device->id)
            ->orderBy('id', 'DESC')
            ->get();

        return response()->json([
            'status' => 1,
            'data'   => $services->map(fn (DeviceService $s) => $this->present($s))->values(),
        ]);
    }

    /**
     * Datos para armar el formulario: odometro y horas de motor ACTUALES de la unidad (los
     * necesita el usuario para elegir un intervalo con sentido) y las opciones de "Expira el".
     */
    public function create($device_id)
    {
        $device = $this->findUserDevice($device_id);

        if (!$device) {
            return response()->json(['status' => 0, 'message' => 'Not found'], 404);
        }

        return response()->json(['status' => 1] + ServiceModalHelper::createData($device->id));
    }

    public function store(Request $request, $device_id)
    {
        $device = $this->findUserDevice($device_id);

        if (!$device) {
            return response()->json(['status' => 0, 'message' => 'Not found'], 404);
        }

        $this->normalizeInput($request, $device->id);

        $result = ServiceModalHelper::create($device->id);

        return response()->json($result);
    }

    public function update(Request $request, $service_id)
    {
        $service = $this->findUserService($service_id);

        if (!$service) {
            return response()->json(['status' => 0, 'message' => 'Not found'], 404);
        }

        $this->normalizeInput($request, $service->device_id);

        return response()->json(ServiceModalHelper::edit($service->id));
    }

    public function destroy($service_id)
    {
        $service = $this->findUserService($service_id);

        if (!$service) {
            return response()->json(['status' => 0, 'message' => 'Not found'], 404);
        }

        ServiceModalHelper::destroy($service->id);

        return response()->json(['status' => 1, 'id' => (int) $service_id]);
    }

    /**
     * Rellena las claves que el helper lee sin isset() y fuerza device_id. Se escribe sobre la
     * request real (request()->merge) porque ModalHelper toma $this->data de request()->all()
     * en su constructor -- modificar solo $request local no le llegaria.
     */
    private function normalizeInput(Request $request, $deviceId)
    {
        request()->merge([
            'device_id'    => $deviceId,
            'email'        => $request->get('email', ''),
            'mobile_phone' => $request->get('mobile_phone', ''),
            'last_service' => $request->get('last_service', 0),
            'expiration_by' => $request->get('expiration_by', 'odometer'),
        ]);
    }

    private function findUserDevice($deviceId)
    {
        return Device::where('id', $deviceId)
            ->filterUserAbility($this->user)
            ->first();
    }

    /** Un servicio es accesible si su unidad lo es -- misma regla que la plataforma web. */
    private function findUserService($serviceId)
    {
        $service = DeviceService::find($serviceId);

        if (!$service || !$this->findUserDevice($service->device_id)) {
            return null;
        }

        return $service;
    }

    /**
     * expires() ya devuelve el valor legible ("Odometro Restante (4788)"), que es lo que la
     * plataforma web muestra en su tabla. Se expone junto a los campos crudos para que la app
     * pueda mostrar texto entendible en vez del JSON en bruto.
     */
    private function present(DeviceService $service)
    {
        return [
            'id'                     => (int) $service->id,
            'device_id'              => (int) $service->device_id,
            'name'                   => (string) $service->name,
            'expiration_by'          => $service->expiration_by,
            'interval'               => (int) $service->interval,
            'last_service'           => $service->last_service,
            'trigger_event_left'     => (int) $service->trigger_event_left,
            'renew_after_expiration' => (bool) $service->renew_after_expiration,
            'expires'                => $service->expiration(),
            'expires_date'           => $service->expires_date,
            'expired'                => (bool) $service->expired,
            'description'            => $service->description,
            'email'                  => $service->email,
        ];
    }
}
