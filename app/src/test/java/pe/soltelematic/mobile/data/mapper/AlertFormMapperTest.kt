package pe.soltelematic.mobile.data.mapper

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pe.soltelematic.mobile.data.remote.dto.AlertFormResponseDto
import pe.soltelematic.mobile.domain.model.AlertAttributeType
import pe.soltelematic.mobile.domain.model.AlertCreateRequest
import pe.soltelematic.mobile.domain.model.AlertNotificationSubmission
import pe.soltelematic.mobile.domain.model.NotificationInputType

private val json = Json { ignoreUnknownKeys = true }

// Recorte del GET alerts/form real (parche 10). Incluye un tipo avanzado (custom), un tipo con un
// atributo de tipo desconocido y ids de option numéricos y de texto.
private const val FORM = """
{
  "status": 1,
  "devices": [ { "id": 455, "value": "A1B949", "title": "A1B949" }, { "id": 450, "value": "ABC-123", "title": "ABC-123" } ],
  "geofences": [],
  "types": [
    { "type": "overspeed", "title": "Exceso de velocidad",
      "attributes": [ { "name": "overspeed", "type": "integer", "title": "Exceso de velocidad (kph)" } ] },
    { "type": "engine", "title": "Encendido ON/OFF",
      "attributes": [ { "name": "state", "type": "select", "title": "Estado",
                        "options": [ {"id":0,"title":"Todos"},{"id":1,"title":"ON"},{"id":2,"title":"OFF"} ] } ] },
    { "type": "geofence_in", "title": "Geocerca de Entrada",
      "attributes": [ { "name": "geofences", "type": "multiselect", "title": "Geocercas",
                        "options": [ {"id":3,"title":"Prueba1"},{"id":4,"title":"Prueba2"} ] } ] },
    { "type": "expiring", "title": "Vencimiento",
      "attributes": [ { "name": "case", "type": "select",
                        "options": [ {"id":"expired_device","title":"Expirado"} ] } ] },
    { "type": "sos", "title": "SOS", "attributes": [] },
    { "type": "custom", "title": "Personalizado", "attributes": [] },
    { "type": "raro", "title": "Raro",
      "attributes": [ { "name": "x", "type": "datetime", "title": "Fecha" } ] }
  ],
  "notifications": [
    { "name": "sound", "title": "Sonido", "active": true, "input": "alarm1", "input_type": "select",
      "options": [ {"id":"alarm1","title":"Alarma 1"},{"id":"alarm2","title":"Alarma 2"} ] },
    { "name": "popup", "title": "Notificación &quot;pop up&quot;", "active": true, "input": "10", "input_type": "integer",
      "description": "Segundos &amp; m&aacute;s" },
    { "name": "color", "title": "Color", "active": false, "input": "#df5858", "input_type": "color" },
    { "name": "push", "title": "Push", "active": true }
  ]
}
"""

class AlertFormMapperTest {

    private val form = json.decodeFromString(AlertFormResponseDto.serializer(), FORM).toAlertFormData()

    @Test
    fun `decodifica entidades HTML nombradas y numericas`() {
        assertEquals("Notificación \"pop up", decodeHtmlEntities("Notificación &quot;pop up"))
        assertEquals("a & b < c > d", decodeHtmlEntities("a &amp; b &lt; c &gt; d"))
        assertEquals("it's", decodeHtmlEntities("it&#39;s"))
        assertEquals("it's", decodeHtmlEntities("it&#x27;s"))
        // Entidad desconocida: se deja tal cual.
        assertEquals("&inventada;", decodeHtmlEntities("&inventada;"))
    }

    @Test
    fun `solo ofrece tipos soportados`() {
        assertEquals(
            listOf("overspeed", "engine", "geofence_in", "expiring", "sos"),
            form.types.map { it.type }
        )
    }

    @Test
    fun `parsea atributos y conserva el tipo JSON de los ids`() {
        val overspeed = form.types.first { it.type == "overspeed" }.attributes.single()
        assertEquals(AlertAttributeType.INTEGER, overspeed.type)

        val state = form.types.first { it.type == "engine" }.attributes.single()
        assertEquals(AlertAttributeType.SELECT, state.type)
        assertEquals(JsonPrimitive(0), state.options.first().id)

        val case = form.types.first { it.type == "expiring" }.attributes.single()
        assertEquals(JsonPrimitive("expired_device"), case.options.single().id)
        // Sin title en el servidor: cae al name.
        assertEquals("case", case.title)

        val geofences = form.types.first { it.type == "geofence_in" }.attributes.single()
        assertEquals(AlertAttributeType.MULTISELECT, geofences.type)

        assertTrue(form.types.first { it.type == "sos" }.attributes.isEmpty())
    }

    @Test
    fun `parsea unidades y canales con sus valores por defecto`() {
        assertEquals(listOf(455, 450), form.devices.map { it.id })
        assertEquals("A1B949", form.devices.first().name)

        val sound = form.notifications.first { it.name == "sound" }
        assertEquals(NotificationInputType.SELECT, sound.inputType)
        assertEquals("alarm1", sound.defaultInput)
        assertTrue(sound.defaultActive)

        val popup = form.notifications.first { it.name == "popup" }
        assertEquals("Notificación \"pop up\"", popup.title)
        assertEquals("Segundos & más", popup.description)
        assertEquals("10", popup.defaultInput)

        val color = form.notifications.first { it.name == "color" }
        assertFalse(color.defaultActive)
        assertEquals(NotificationInputType.COLOR, color.inputType)

        val push = form.notifications.first { it.name == "push" }
        assertEquals(NotificationInputType.NONE, push.inputType)
        assertNull(push.description)
    }

    @Test
    fun `cuerpo de POST con atributo integer`() {
        val overspeed = form.types.first { it.type == "overspeed" }
        val body = AlertCreateRequest(
            name = "Prueba",
            type = overspeed,
            attributeValues = mapOf("overspeed" to JsonPrimitive(80)),
            deviceIds = listOf(455, 450),
            notifications = listOf(
                AlertNotificationSubmission("popup", true, JsonPrimitive(10)),
                AlertNotificationSubmission("push", true, null)
            )
        ).toRequestBody()

        assertEquals("Prueba", body["name"]!!.jsonPrimitive.content)
        assertEquals("overspeed", body["type"]!!.jsonPrimitive.content)
        assertEquals(80, body["overspeed"]!!.jsonPrimitive.content.toInt())
        // Sin atributo "geofences" en el tipo: la relación va vacía.
        assertEquals(JsonArray(emptyList()), body["geofences"])
        assertEquals(listOf(455, 450), body["devices"]!!.jsonArray.map { it.jsonPrimitive.content.toInt() })
        for (key in listOf("drivers", "pois", "zones", "events_custom")) {
            assertEquals(key, JsonArray(emptyList()), body[key])
        }
        assertEquals(0, body["zone"]!!.jsonPrimitive.content.toInt())
        assertEquals(0, body["schedule"]!!.jsonPrimitive.content.toInt())
        assertEquals(0, body["command"]!!.jsonObject["active"]!!.jsonPrimitive.content.toInt())

        val notifications = body["notifications"]!!.jsonObject
        assertEquals(1, notifications["popup"]!!.jsonObject["active"]!!.jsonPrimitive.content.toInt())
        assertEquals(10, notifications["popup"]!!.jsonObject["input"]!!.jsonPrimitive.content.toInt())
        // Canal sin input_type: sin clave "input".
        assertFalse(notifications["push"]!!.jsonObject.containsKey("input"))
    }

    @Test
    fun `el atributo geofences del tipo sustituye a la relacion vacia`() {
        val type = form.types.first { it.type == "geofence_in" }
        val body = AlertCreateRequest(
            name = "G",
            type = type,
            attributeValues = mapOf("geofences" to JsonArray(listOf(JsonPrimitive(3), JsonPrimitive(4)))),
            deviceIds = listOf(1),
            notifications = emptyList()
        ).toRequestBody()

        assertEquals(listOf(3, 4), body["geofences"]!!.jsonArray.map { it.jsonPrimitive.content.toInt() })
    }

    @Test
    fun `ids de texto viajan como texto y canal apagado envia active 0`() {
        val type = form.types.first { it.type == "expiring" }
        val body = AlertCreateRequest(
            name = "E",
            type = type,
            attributeValues = mapOf("case" to JsonPrimitive("expired_device")),
            deviceIds = listOf(1),
            notifications = listOf(AlertNotificationSubmission("color", false, JsonPrimitive("#df5858")))
        ).toRequestBody()

        assertTrue(body["case"]!!.jsonPrimitive.isString)
        val color = body["notifications"]!!.jsonObject["color"]!!.jsonObject
        assertEquals(0, color["active"]!!.jsonPrimitive.content.toInt())
        assertEquals("#df5858", color["input"]!!.jsonPrimitive.content)
    }
}
