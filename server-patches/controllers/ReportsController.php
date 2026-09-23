<?php


namespace App\Http\Controllers\Api\ClientLite;


use App\Http\Controllers\Controller;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Validator;
use Tobuli\Entities\Device;
use Tobuli\Reports\ReportManager;

/**
 * PARCHE SOLTELEMATIC MOBILE -- informes descargables desde la app.
 *
 * A diferencia de servicios y sharing, aca NO existia ningun controlador de API previo
 * (Api/Frontend/ReportsController no existe): la generacion vive solo en
 * Frontend\ReportsController + ModalHelpers\ReportModalHelper, con sesion y CSRF.
 *
 * La generacion es SINCRONA (no hay cola): ReportModalHelper::generate() con 'generate' => 1
 * devuelve el archivo directamente. Sin esa clave devuelve status 3 + una URL intermedia, que
 * es como lo hace la web (dos pasos) -- la app se salta ese rodeo.
 *
 * ⚠️ Report::download() NO es uniforme: pdf/xlsx/xls/csv devuelven una respuesta de Laravel,
 * pero html y json hacen header() + echo directo, sin return. Por eso el caso html se captura
 * con un buffer de salida y se reenvia como respuesta propia.
 */
class ReportsController extends Controller
{
    /** Los unicos formatos que ofrece la app. El servidor soporta mas (xls, csv, json, pdf_land). */
    private const ALLOWED_FORMATS = ['html', 'xlsx', 'pdf'];
    
    /**
     * Lista blanca de tipos que ofrece la app. El servidor tiene ~80, la mayoria variantes
     * personalizadas de otros clientes que solo agregan confusion en movil. Ampliar aca, no en
     * la app: asi no hace falta publicar una version nueva del APK.
     */
    private const ALLOWED_TYPES = [
        1  => 'Informacion general',
        40 => 'Paradas',
        4  => 'Hoja de Viajes',
        11 => 'Rellenos de combustible',
        12 => 'Robos de combustible',
        29 => 'Horas del motor Diariamente',
        43 => 'Rutas',
    ];

    protected function afterAuth($user)
    {
        $this->checkException('reports', 'view');
    }

    /**
     * Tipos de informe habilitados PARA ESTE USUARIO (no una lista fija): si el cliente no tiene
     * permiso sobre un tipo, no debe aparecer en la app.
     */
    public function types()
    {
        $manager = (new ReportManager())->setUser($this->user);

        $types = collect($manager->getUserEnabledNameList($this->user))
            ->filter(fn ($name, $id) => array_key_exists((int) $id, self::ALLOWED_TYPES))
            ->map(function ($name, $id) use ($manager) {
                // Cada clase de informe declara su propio $formats: RoutesReport (43), por
                // ejemplo, solo admite html/json -- la web deshabilita el resto en su
                // desplegable. Se interseca con lo que ofrece la app para que el movil nunca
                // muestre una combinacion que el servidor rechazaria.
                // $formats es protected y la clase Report NO expone un getter. Se lee con
                // reflexion a proposito: agregar un getFormats() a Tobuli\Reports\Report seria
                // un parche mas que reaplicar en cada actualizacion de GPSWOX.
                $report    = $manager->report($id);
                $property  = new \ReflectionProperty($report, 'formats');
                $property->setAccessible(true);
                $formats   = array_values(array_intersect($property->getValue($report), self::ALLOWED_FORMATS));

                return ['id' => (int) $id, 'name' => $name, 'formats' => $formats];
            })
            ->values();

        return response()->json([
            'status'  => 1,
            'data'    => $types,
            'formats' => self::ALLOWED_FORMATS,
        ]);
    }

    /**
     * Genera y devuelve el archivo. Parametros: type (id), format, devices[], date_from,
     * date_to (Y-m-d), from_time, to_time (HH:MM).
     */
    public function generate(Request $request)
    {
        $validator = Validator::make($request->all(), [
            'type'      => 'required|integer',
            'format'    => 'required|in:' . implode(',', self::ALLOWED_FORMATS),
            'devices'   => 'required|array|min:1',
            'devices.*' => 'required|integer',
            'date_from' => 'required|date_format:Y-m-d',
            'date_to'   => 'required|date_format:Y-m-d',
            'from_time' => 'nullable|regex:/^\d{2}:\d{2}$/',
            'to_time'   => 'nullable|regex:/^\d{2}:\d{2}$/',
        ]);

        if ($validator->fails()) {
            return response()->json(['status' => 0, 'errors' => $validator->errors()], 422);
        }

        // Verificacion de pertenencia con la Collection (barata), pero al helper hay que pasarle
        // un QUERY BUILDER, no una Collection: el generador llama isJoined() sobre devices_query
        // y eso solo existe en el builder.
        // El formato pedido tiene que estar permitido POR ESE TIPO de informe (ver types()):
        // pedir pdf para Rutas revienta wkhtmltopdf con un 500 en vez de un error entendible.
        $report        = (new ReportManager())->setUser($this->user)->report((int) $request->get('type'));
        $formatsProp   = new \ReflectionProperty($report, 'formats');
        $formatsProp->setAccessible(true);
        $typeFormats   = array_intersect($formatsProp->getValue($report), self::ALLOWED_FORMATS);

        if (!in_array($request->get('format'), $typeFormats)) {
            return response()->json([
                'status' => 0,
                'errors' => ['format' => ['Formato no disponible para este tipo de informe.']],
            ], 422);
        }
        $owned = Device::whereIn('id', $request->get('devices'))
            ->filterUserAbility($this->user);

        if ($owned->isEmpty()) {
            return response()->json(['status' => 0, 'message' => 'Not found'], 404);
        }

        $ownedIds = $owned->pluck('id')->all();
        $devicesQuery = Device::whereIn('id', $ownedIds);


        // devices_query es un query builder y request()->merge() solo admite escalares/arrays,
        // asi que los datos se le pasan al helper con setData(), que acepta cualquier tipo.
        $data = $request->all() + [
            'generate'      => 1,
            'devices'       => $ownedIds,
            'devices_query' => $devicesQuery,
            'from_time'     => $request->get('from_time', '00:00'),
            'to_time'       => $request->get('to_time', '00:00'),
            'geofences'     => [],
        ];

        $data['generate']      = 1;
        $data['devices']       = $ownedIds;
        $data['devices_query'] = $devicesQuery;
        $data['geofences']     = [];

        $helper = app(\ModalHelpers\ReportModalHelper::class);
        $helper->setData($data);

        $format = $request->get('format');

        // html: Report::download() hace echo sin return -- se captura y se reenvia.
        if ($format === 'html') {
            ob_start();
            $helper->generate();
            $html = ob_get_clean();

            return response($html, 200, [
                'Content-Type'        => 'text/html; charset=UTF-8',
                'Content-Disposition' => 'attachment; filename="informe.html"',
            ]);
        }

        // pdf y xlsx ya devuelven una respuesta de Laravel utilizable tal cual.
        return $helper->generate();
    }
}
