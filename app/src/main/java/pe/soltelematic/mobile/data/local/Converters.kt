package pe.soltelematic.mobile.data.local

import androidx.room.TypeConverter
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import pe.soltelematic.mobile.data.local.entity.TailPoint
import java.time.Instant

/** minSdk 26 trae java.time nativo (desde API 26), no hace falta core library desugaring. */
class Converters {

    private val json = Json { ignoreUnknownKeys = true }

    @TypeConverter
    fun fromEpochSeconds(value: Long?): Instant? = value?.let { Instant.ofEpochSecond(it) }

    @TypeConverter
    fun toEpochSeconds(instant: Instant?): Long? = instant?.epochSecond

    // La estela es un puñado de puntos por unidad: JSON en una columna es más simple que una
    // tabla relacionada, y Room no soporta listas de tipos compuestos de forma nativa.
    @TypeConverter
    fun fromTailJson(value: String?): List<TailPoint> =
        if (value.isNullOrBlank()) emptyList() else json.decodeFromString(value)

    @TypeConverter
    fun tailToJson(points: List<TailPoint>): String = json.encodeToString(points)
}
