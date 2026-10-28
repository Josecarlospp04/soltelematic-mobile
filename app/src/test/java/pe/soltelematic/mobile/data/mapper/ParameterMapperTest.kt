package pe.soltelematic.mobile.data.mapper

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import pe.soltelematic.mobile.data.remote.dto.ParametersResponseDto
import pe.soltelematic.mobile.domain.model.ParameterKey

class ParameterMapperTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun parse(raw: String) = json.decodeFromString<ParametersResponseDto>(raw).toDomain()

    @Test
    fun `sensor_series con y sin unidad y sin sensor_id`() {
        val series = parse(
            """{"status":1,"data":[{"key":"voltage","points":[[1790312594,25.4]],"total":1}],
            "sensor_series":[
              {"key":"speed","name":"Velocidad","type":"speed","unit":"km/h","points":[[1790312594,40.5],[1790312654,42]]},
              {"key":"sensor_177","sensor_id":177,"name":"RESPALDO","type":"battery","unit":"%","points":[[1790312594,69.23]]},
              {"key":"sensor_173","sensor_id":173,"name":"SATÉLITES","type":"satellites","unit":"","points":[[1790312594,15]]}
            ]}"""
        )

        // Se ignora "data" y se respeta el orden recibido.
        assertEquals(listOf("Velocidad", "RESPALDO", "SATÉLITES"), series.map { it.name })
        assertEquals("km/h", series[0].displayUnit)
        assertEquals(ParameterKey.SPEED, series[0].key)
        assertEquals(2, series[0].points.size)
        assertEquals(1790312654L, series[0].points[1].timestamp.epochSecond)
        assertEquals(42.0, series[0].points[1].value, 0.0)
        assertEquals("%", series[1].displayUnit)
        assertEquals("battery", series[1].type)
        assertNull(series[2].displayUnit) // unit "" -> sin unidad
    }

    @Test
    fun `sin sensor_series usa data como antes`() {
        val series = parse(
            """{"status":1,"data":[
              {"key":"voltage","points":[[1790312594,25.424]],"total":1},
              {"key":"fuel","points":[[1790312594,9.1]],"total":1}
            ]}"""
        )

        assertEquals(listOf(ParameterKey.VOLTAGE, ParameterKey.FUEL), series.map { it.key })
        assertNull(series[0].name)
        assertEquals("V", series[0].displayUnit)
        assertNull(series[1].displayUnit)
    }

    @Test
    fun `sensor_series vacia no cae a data`() {
        val series = parse(
            """{"status":1,"data":[{"key":"voltage","points":[[1790312594,25.4]],"total":1}],"sensor_series":[]}"""
        )

        assertEquals(0, series.size)
    }

    @Test
    fun `descarta series sin key y pares mal formados`() {
        val series = parse(
            """{"status":1,"data":[],"sensor_series":[
              {"name":"Sin key","points":[[1,2]]},
              {"key":"sensor_9","name":" ","type":"gsm","unit":null,"points":[[1790312594,4],[1790312600]]}
            ]}"""
        )

        assertEquals(1, series.size)
        assertEquals("sensor_9", series[0].name) // name en blanco -> cae a key
        assertEquals(1, series[0].points.size)
    }
}
