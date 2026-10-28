<?php


namespace App\Http\Controllers\Api\ClientLite;


use App\Http\Controllers\Controller;
use Carbon\Carbon;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Validator;
use Tobuli\Entities\Device;
use Tobuli\Entities\DeviceSensor;
use Formatter;

/**
 * PARCHE SOLTELEMATIC MOBILE -- series temporales de parametros, para graficar en la app.
 *
 * La plataforma web solo muestra el VALOR ACTUAL de cada sensor (ver bloque "Sensores" de la
 * ficha): no existe ninguna vista ni endpoint de historico. Pero el dato SI esta guardado: cada
 * fila de positions_{device_id} (base de Traccar) trae el XML crudo del equipo en `other` y los
 * sensores configurados en `sensors_values`. Este controlador lo expone como serie temporal.
 *
 * Fuente de cada parametro (verificado sobre datos reales):
 *   power   -> <power>25.638</power>      voltaje externo (V)
 *   io113   -> <io113>95</io113>          bateria interna (%)
 *   rssi    -> <rssi>5</rssi>             senal
 *   sat     -> <sat>12</sat>              satelites
 *   speed   -> columna speed de la tabla  velocidad
 *   fuel    -> sensors_values, id del sensor fuel_tank del dispositivo
 *
 * Un parametro que la unidad no reporta NO se incluye en la respuesta (serie ausente, no serie
 * vacia): la app no dibuja esa grafica.
 *
 * Las series de arriba usan etiquetas FIJAS de Teltonika (<power>, <io113>): en otras marcas
 * (p. ej. JimiIoT) no existen. Por eso la respuesta lleva ademas `sensor_series`, una serie por
 * cada sensor configurado en la unidad, calculada con la configuracion del propio sensor
 * (DeviceSensor::getValue). `data` se mantiene tal cual por compatibilidad con la app 0.9.0.
 */
class ParametersController extends Controller
{
    /** Tope de puntos por serie. Mas que esto no se distingue en una pantalla de movil. */
    private const MAX_POINTS = 500;

    /** Etiquetas del XML de `other` que interesan, con el nombre que usa la app. */
    private const OTHER_KEYS = [
        'power'  => 'voltage',
        'io113'  => 'battery',
        'rssi'   => 'rssi',
        'sat'    => 'satellites',
    ];

    /** Tipos de sensor que no se grafican: estados, texto o acumulados sin sentido como curva. */
    private const NON_GRAPHABLE_SENSOR_TYPES = ['acc', 'ignition', 'logical', 'textual', 'odometer'];

    protected function afterAuth($user)
    {
        $this->checkException('history', 'view');
    }

    public function get(Request $request)
    {
        $validator = Validator::make($request->all(), [
            'device_id' => 'required|integer',
            'from'      => 'required|date',
            'to'        => 'required|date|after:from',
        ]);

        if ($validator->fails()) {
            return response()->json(['status' => 0, 'errors' => $validator->errors()], 422);
        }

        $device = Device::where('id', $request->get('device_id'))
            ->filterUserAbility($this->user)
            ->first();

        if (!$device) {
            return response()->json(['status' => 0, 'message' => 'Not found'], 404);
        }

        // Mismo tratamiento de fechas que HistoryController: el rango llega en la hora del
        // usuario y se convierte a la del servidor.
        $from = Formatter::time()->reverse($request->get('from'));
        $to   = Formatter::time()->reverse($request->get('to'));

        // El sensor de combustible es por dispositivo: su id es la clave dentro de
        // sensors_values, no un nombre fijo.
        $fuelSensor = DeviceSensor::where('device_id', $device->id)
            ->where('type', 'fuel_tank')
            ->first();

        $series = [];
        foreach (array_values(self::OTHER_KEYS) as $name) {
            $series[$name] = [];
        }
        $series['speed'] = [];
        if ($fuelSensor) {
            $series['fuel'] = [];
        }

        // Una sola consulta de sensores; los tipos no graficables se descartan ANTES del bucle de
        // posiciones, asi no cuestan ni una llamada a getValue().
        $sensors = DeviceSensor::where('device_id', $device->id)
            ->get()
            ->reject(function ($sensor) {
                return in_array($sensor->type, self::NON_GRAPHABLE_SENSOR_TYPES, true);
            })
            ->values();
        $sensorPoints = [];

        // chunk para no cargar en memoria una semana entera de posiciones de golpe.
        $device->positions()
            ->whereBetween('time', [$from, $to])
            ->orderBy('time', 'ASC')
            ->chunk(2000, function ($positions) use (&$series, &$sensorPoints, $fuelSensor, $sensors) {
                foreach ($positions as $position) {
                    $ts = Carbon::parse($position->time)->timestamp;

                    // Sin `other` no hay nada que calcular: getValue() parsea ese XML en cada llamada.
                    if (!empty($position->other)) {
                        foreach ($sensors as $sensor) {
                            $value = $this->sensorNumericValue($sensor, $position->other);
                            if ($value !== null) {
                                $sensorPoints[$sensor->id][] = [$ts, $value];
                            }
                        }
                    }

                    if ($position->speed !== null) {
                        // El equipo reporta en nudos; la plataforma muestra km/h.
                        $series['speed'][] = [$ts, round($position->speed * 1.852, 2)];
                    }

                    foreach ($this->parseOther($position->other) as $tag => $value) {
                        if (isset(self::OTHER_KEYS[$tag])) {
                            $series[self::OTHER_KEYS[$tag]][] = [$ts, $value];
                        }
                    }

                    if ($fuelSensor) {
                        $fuel = $this->extractSensorValue($position->sensors_values, $fuelSensor->id);
                        if ($fuel !== null) {
                            $series['fuel'][] = [$ts, $fuel];
                        }
                    }
                }
            });

        // Se descartan las series sin ningun dato: la unidad no reporta ese parametro.
        $result = [];
        $speedPoints = [];
        foreach ($series as $name => $points) {
            if (empty($points)) {
                continue;
            }

            $sampled = $this->downsample($points, self::MAX_POINTS);

            $result[] = [
                'key'    => $name,
                'points' => $sampled,
                'total'  => count($points),
            ];

            if ($name === 'speed') {
                $speedPoints = $sampled; // se reutiliza en sensor_series: no se submuestrea dos veces
            }
        }

        // Series por sensor: independientes de la marca del equipo (ver cabecera de la clase).
        $sensorSeries = [];
        if (!empty($speedPoints)) {
            $sensorSeries[] = [
                'key'    => 'speed',
                'name'   => 'Velocidad',
                'type'   => 'speed',
                'unit'   => 'km/h',
                'points' => $speedPoints,
            ];
        }
        foreach ($sensors as $sensor) {
            $points = $sensorPoints[$sensor->id] ?? [];
            if (empty($points)) {
                continue;
            }

            $sensorSeries[] = [
                'key'       => 'sensor_' . $sensor->id,
                'sensor_id' => $sensor->id,
                'name'      => $sensor->name,
                'type'      => $sensor->type,
                'unit'      => (string) $sensor->getUnit(),
                'points'    => $this->downsample($points, self::MAX_POINTS),
            ];
        }

        return response()->json([
            'status'        => 1,
            'data'          => $result,
            'sensor_series' => $sensorSeries,
        ]);
    }

    /**
     * Valor del sensor para esa posicion, con su propia configuracion (formula, min-max,
     * calibracion). getValue() puede devolver null, '-', booleanos o texto: solo pasa lo numerico.
     * Un sensor que lance excepcion en una posicion no tumba el endpoint entero.
     */
    private function sensorNumericValue($sensor, $other)
    {
        try {
            $value = $sensor->getValue($other, false);
        } catch (\Throwable $e) {
            return null;
        }

        if ($value === null || is_bool($value) || !is_numeric($value)) {
            return null;
        }

        return (float) $value;
    }

    /** `other` es XML plano de Traccar: <power>25.638</power><rssi>5</rssi>... */
    private function parseOther($other)
    {
        if (empty($other)) {
            return [];
        }

        $values = [];
        foreach (self::OTHER_KEYS as $tag => $name) {
            if (preg_match("#<{$tag}>([^<]+)</{$tag}>#", $other, $m) && is_numeric($m[1])) {
                $values[$tag] = (float) $m[1];
            }
        }

        return $values;
    }

    /** sensors_values es JSON: [{"id":127,"val":9.12}, ...] */
    private function extractSensorValue($raw, $sensorId)
    {
        if (empty($raw)) {
            return null;
        }

        // El modelo ya castea sensors_values a array; si llegara como texto, se decodifica.
        $decoded = is_array($raw) ? $raw : json_decode($raw, true);

        if (!is_array($decoded)) {
            return null;
        }

        foreach ($decoded as $entry) {
            if (($entry['id'] ?? null) == $sensorId && is_numeric($entry['val'] ?? null)) {
                return (float) $entry['val'];
            }
        }

        return null;
    }

    /**
     * LTTB (Largest Triangle Three Buckets), no promedio: promediar aplana los picos, y en una
     * grafica de voltaje o bateria el pico ES el dato que importa (una caida de tension de unos
     * segundos desaparece al promediar). LTTB elige, de cada bloque, el punto que forma el
     * triangulo de mayor area con el vecino anterior y el promedio del siguiente bloque: conserva
     * la forma visual de la curva y devuelve SIEMPRE valores reales medidos, nunca calculados.
     */
    private function downsample(array $points, $threshold)
    {
        $count = count($points);

        if ($threshold >= $count || $threshold < 3) {
            return $points;
        }

        $sampled = [$points[0]];
        $every   = ($count - 2) / ($threshold - 2);
        $a       = 0;

        for ($i = 0; $i < $threshold - 2; $i++) {
            $rangeStart = (int) floor(($i + 1) * $every) + 1;
            $rangeEnd   = (int) floor(($i + 2) * $every) + 1;
            $rangeEnd   = min($rangeEnd, $count);

            $avgX = 0;
            $avgY = 0;
            $rangeLength = $rangeEnd - $rangeStart;

            if ($rangeLength <= 0) {
                continue;
            }

            for ($j = $rangeStart; $j < $rangeEnd; $j++) {
                $avgX += $points[$j][0];
                $avgY += $points[$j][1];
            }
            $avgX /= $rangeLength;
            $avgY /= $rangeLength;

            $rangeOffs = (int) floor($i * $every) + 1;
            $rangeTo   = (int) floor(($i + 1) * $every) + 1;

            $pointAX = $points[$a][0];
            $pointAY = $points[$a][1];

            $maxArea = -1;
            $maxIndex = $rangeOffs;

            for ($j = $rangeOffs; $j < $rangeTo && $j < $count; $j++) {
                $area = abs(
                    ($pointAX - $avgX) * ($points[$j][1] - $pointAY)
                    - ($pointAX - $points[$j][0]) * ($avgY - $pointAY)
                ) / 2;

                if ($area > $maxArea) {
                    $maxArea  = $area;
                    $maxIndex = $j;
                }
            }

            $sampled[] = $points[$maxIndex];
            $a = $maxIndex;
        }

        $sampled[] = $points[$count - 1];

        return $sampled;
    }
}
