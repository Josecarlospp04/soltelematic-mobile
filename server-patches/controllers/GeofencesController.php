<?php


namespace App\Http\Controllers\Api\ClientLite;


use App\Transformers\ClientLite\GeofenceTransformer;
use Illuminate\Http\Request;
use App\Http\Controllers\Controller;
use Tobuli\Entities\Geofence;
use Tobuli\Services\FractalTransformerService;

class GeofencesController extends Controller
{
    protected $transformerService;

    public function __construct(FractalTransformerService $transformerService)
    {
        parent::__construct();

        $this->transformerService = $transformerService;
    }

    protected function afterAuth($user)
    {
        $action = match (request()->method()) { 'POST' => 'store', 'DELETE' => 'view', default => 'view' };
        $this->checkException('geofences', $action);
    }

    public function map(Request $request)
    {
        $geofences = Geofence::userOwned($this->user)
            ->visible()
            ->clearOrdersBy()
            ->cursorPaginate(100);

        return response()->json(
            $this->transformerService->cursorPaginate($geofences, GeofenceTransformer::class)->toArray()
        );
    }

    public function store(Request $request)
    {
        $service = app(\Tobuli\Services\GeofenceService::class);
        $geofence = $service->create($request->all() + ['user_id' => $this->user->id]);

        return response()->json(
            ['status' => 1] +
            $this->transformerService->item($geofence, GeofenceTransformer::class)->toArray()
        );
    }

    public function destroy($id)
    {
        $geofence = Geofence::userOwned($this->user)->find($id);

        if (!$geofence) {
            return response()->json(['status' => 0, 'message' => 'Not found'], 404);
        }

        app(\Tobuli\Services\GeofenceService::class)->delete($geofence);

        return response()->json(['status' => 1, 'id' => (int) $id]);
    }
}

