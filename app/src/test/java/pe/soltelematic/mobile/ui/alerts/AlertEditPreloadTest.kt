package pe.soltelematic.mobile.ui.alerts

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import pe.soltelematic.mobile.domain.model.AlertAttribute
import pe.soltelematic.mobile.domain.model.AlertAttributeType
import pe.soltelematic.mobile.domain.model.AlertFormData
import pe.soltelematic.mobile.domain.model.AlertFormDevice
import pe.soltelematic.mobile.domain.model.AlertOption
import pe.soltelematic.mobile.domain.model.AlertRule
import pe.soltelematic.mobile.domain.model.AlertTypeDefinition

private val form = AlertFormData(
    devices = listOf(AlertFormDevice(455, "A1B949"), AlertFormDevice(450, "ABC-123"), AlertFormDevice(6, "Cosechadora")),
    types = listOf(
        AlertTypeDefinition(
            "overspeed", "Exceso de velocidad",
            listOf(AlertAttribute("overspeed", AlertAttributeType.INTEGER, "kph", emptyList()))
        ),
        AlertTypeDefinition(
            "engine", "Encendido",
            listOf(
                AlertAttribute(
                    "state", AlertAttributeType.SELECT, "Estado",
                    listOf(AlertOption(JsonPrimitive(0), "Todos"), AlertOption(JsonPrimitive(1), "ON"), AlertOption(JsonPrimitive(2), "OFF"))
                )
            )
        ),
        AlertTypeDefinition(
            "geofence_in", "Geocerca de Entrada",
            listOf(
                AlertAttribute(
                    "geofences", AlertAttributeType.MULTISELECT, "Geocercas",
                    listOf(AlertOption(JsonPrimitive(3), "Prueba1"), AlertOption(JsonPrimitive(4), "Prueba2"))
                )
            )
        ),
        AlertTypeDefinition("sos", "SOS", emptyList())
    ),
    notifications = emptyList()
)

private fun rule(
    type: String,
    raw: String,
    forAll: Boolean = false,
    zones: List<Int> = emptyList()
): AlertRule {
    val obj: JsonObject = Json.parseToJsonElement(raw).jsonObject
    return AlertRule(
        id = 4, name = "mi alerta", type = type, active = true, deviceIds = emptyList(),
        forAllUserDevices = forAll, scheduleEnabled = false, commandEnabled = false,
        poiIds = emptyList(), zoneIds = zones, driverIds = emptyList(), eventsCustomIds = emptyList(), raw = obj
    )
}

class AlertEditPreloadTest {

    @Test
    fun `precarga nombre, tipo, atributo integer y unidades`() {
        val preload = buildEditPreload(form, rule("overspeed", """{ "overspeed": 5, "devices": [455, 450] }"""))!!

        assertEquals("mi alerta", preload.name)
        assertEquals("overspeed", preload.type.type)
        assertEquals(AttributeValue.Text("5"), preload.attributeValues["overspeed"])
        assertEquals(setOf(455, 450), preload.deviceIds)
    }

    @Test
    fun `integer como string`() {
        val preload = buildEditPreload(form, rule("overspeed", """{ "overspeed": "80", "devices": [] }"""))!!
        assertEquals(AttributeValue.Text("80"), preload.attributeValues["overspeed"])
    }

    @Test
    fun `select se resuelve por texto aunque el tipo JSON difiera`() {
        val asString = buildEditPreload(form, rule("engine", """{ "state": "1", "devices": [] }"""))!!
        assertEquals(AttributeValue.Single(JsonPrimitive(1)), asString.attributeValues["state"])

        val asNumber = buildEditPreload(form, rule("engine", """{ "state": 2, "devices": [] }"""))!!
        assertEquals(AttributeValue.Single(JsonPrimitive(2)), asNumber.attributeValues["state"])
    }

    @Test
    fun `select null o sin opcion valida queda sin seleccionar`() {
        val nullState = buildEditPreload(form, rule("engine", """{ "state": null, "devices": [] }"""))!!
        assertEquals(AttributeValue.Single(null), nullState.attributeValues["state"])

        val unknown = buildEditPreload(form, rule("engine", """{ "state": 99, "devices": [] }"""))!!
        assertEquals(AttributeValue.Single(null), unknown.attributeValues["state"])

        // Sin seleccionar = incompleto: se exige como al crear.
        assertEquals(false, isAttributeComplete(form.types[1].attributes.single(), nullState.attributeValues["state"]))
    }

    @Test
    fun `multiselect geofences desde la lista de ids de la alerta`() {
        val preload = buildEditPreload(form, rule("geofence_in", """{ "geofences": [4, "3", 99], "devices": [6] }"""))!!
        assertEquals(
            AttributeValue.Multi(setOf(JsonPrimitive(3), JsonPrimitive(4))),
            preload.attributeValues["geofences"]
        )
        assertEquals(setOf(6), preload.deviceIds)
    }

    @Test
    fun `relaciones como objetos con id tambien se aceptan`() {
        val preload = buildEditPreload(
            form,
            rule("geofence_in", """{ "geofences": [ { "id": 3 } ], "devices": [ { "id": 455, "name": "A1B949" }, { "id": 450 } ] }""")
        )!!
        assertEquals(AttributeValue.Multi(setOf(JsonPrimitive(3))), preload.attributeValues["geofences"])
        assertEquals(setOf(455, 450), preload.deviceIds)
    }

    @Test
    fun `unidades que ya no existen en el formulario se descartan`() {
        val preload = buildEditPreload(form, rule("sos", """{ "devices": [455, 12345] }"""))!!
        assertEquals(setOf(455), preload.deviceIds)
        assertEquals(emptyMap<String, AttributeValue>(), preload.attributeValues)
    }

    @Test
    fun `alerta que ahora es avanzada no se precarga`() {
        assertNull(buildEditPreload(form, rule("overspeed", """{ "overspeed": 5 }""", forAll = true)))
        assertNull(buildEditPreload(form, rule("overspeed", """{ "overspeed": 5 }""", zones = listOf(1))))
    }

    @Test
    fun `tipo que el formulario no ofrece no se precarga`() {
        assertNull(buildEditPreload(form, rule("custom", """{ "devices": [] }""")))
        assertNull(buildEditPreload(form, rule("tipo_inexistente", """{ "devices": [] }""")))
        assertNotNull(buildEditPreload(form, rule("sos", """{ "devices": [] }""")))
    }
}
