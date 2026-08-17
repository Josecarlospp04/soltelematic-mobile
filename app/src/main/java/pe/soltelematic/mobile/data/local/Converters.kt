package pe.soltelematic.mobile.data.local

import androidx.room.TypeConverter
import java.time.Instant

/** minSdk 26 trae java.time nativo (desde API 26), no hace falta core library desugaring. */
class Converters {

    @TypeConverter
    fun fromEpochSeconds(value: Long?): Instant? = value?.let { Instant.ofEpochSecond(it) }

    @TypeConverter
    fun toEpochSeconds(instant: Instant?): Long? = instant?.epochSecond
}
