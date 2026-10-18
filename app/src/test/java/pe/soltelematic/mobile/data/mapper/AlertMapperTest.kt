package pe.soltelematic.mobile.data.mapper

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pe.soltelematic.mobile.domain.model.AlertRule

private fun parse(json: String): JsonObject = Json.parseToJsonElement(json).jsonObject

// Payload real de GET alerts (parche 10), campos con tipos mezclados a propósito.
private const val OVERSPEED_ALERT = """
{ "id": 4, "user_id": 53, "active": 1, "name": "prueba alerta", "type": "overspeed",
  "for_all_user_devices": 0, "schedule": "0", "schedules": null,
  "zone": 0, "command": { "active": "0", "type": "serial" },
  "notifications": { "color": {"active":"1","input":"#df5858"}, "push": {"active":"1"} },
  "overspeed": 5,
  "devices": [6,450,455], "drivers": [], "geofences": [], "events_custom": [], "pois": [], "zones": [] }
"""

private fun rule(
    type: String = "overspeed",
    forAll: Boolean = false,
    schedule: Boolean = false,
    command: Boolean = false,
    pois: List<Int> = emptyList(),
    zones: List<Int> = emptyList(),
    drivers: List<Int> = emptyList(),
    eventsCustom: List<Int> = emptyList()
) = AlertRule(
    id = 1,
    name = "x",
    type = type,
    active = true,
    deviceIds = listOf(1),
    forAllUserDevices = forAll,
    scheduleEnabled = schedule,
    commandEnabled = command,
    poiIds = pois,
    zoneIds = zones,
    driverIds = drivers,
    eventsCustomIds = eventsCustom,
    raw = JsonObject(emptyMap())
)

class AlertMapperTest {

    @Test
    fun `payload real se mapea y conserva el objeto crudo`() {
        val json = parse(OVERSPEED_ALERT)
        val alert = json.toAlertRule()!!

        assertEquals(4, alert.id)
        assertEquals("prueba alerta", alert.name)
        assertEquals("overspeed", alert.type)
        assertTrue(alert.active)
        assertEquals(listOf(6, 450, 455), alert.deviceIds)
        assertFalse(alert.forAllUserDevices)
        assertFalse(alert.scheduleEnabled)
        assertFalse(alert.commandEnabled)
        assertFalse(alert.isAdvanced)
        assertEquals(json, alert.raw)
    }

    @Test
    fun `active acepta int, string y bool`() {
        for ((raw, expected) in listOf("1" to true, "0" to false, "\"1\"" to true, "\"0\"" to false, "true" to true, "false" to false)) {
            val alert = parse("""{ "id": 1, "active": $raw }""").toAlertRule()!!
            assertEquals("active=$raw", expected, alert.active)
        }
    }

    @Test
    fun `schedule y command active tolerantes`() {
        val alert = parse("""{ "id": 1, "schedule": 1, "command": { "active": true } }""").toAlertRule()!!
        assertTrue(alert.scheduleEnabled)
        assertTrue(alert.commandEnabled)
        assertTrue(alert.isAdvanced)
    }

    @Test
    fun `command que no es objeto o ausente cuenta como inactivo`() {
        assertFalse(parse("""{ "id": 1, "command": [] }""").toAlertRule()!!.commandEnabled)
        assertFalse(parse("""{ "id": 1 }""").toAlertRule()!!.commandEnabled)
    }

    @Test
    fun `listas de ids toleran strings y formas raras`() {
        val alert = parse("""{ "id": 1, "devices": [1, "2", null], "pois": {}, "zones": "x" }""").toAlertRule()!!
        assertEquals(listOf(1, 2), alert.deviceIds)
        assertTrue(alert.poiIds.isEmpty())
        assertTrue(alert.zoneIds.isEmpty())
    }

    @Test
    fun `sin id valido no hay alerta`() {
        assertNull(parse("""{ "name": "sin id" }""").toAlertRule())
    }

    @Test
    fun `id como string se acepta`() {
        assertEquals(7, parse("""{ "id": "7" }""").toAlertRule()!!.id)
    }

    @Test
    fun `avanzada por cada criterio`() {
        assertFalse(rule().isAdvanced)
        assertTrue(rule(schedule = true).isAdvanced)
        assertTrue(rule(command = true).isAdvanced)
        assertTrue(rule(forAll = true).isAdvanced)
        assertTrue(rule(pois = listOf(1)).isAdvanced)
        assertTrue(rule(zones = listOf(1)).isAdvanced)
        assertTrue(rule(drivers = listOf(1)).isAdvanced)
        assertTrue(rule(eventsCustom = listOf(1)).isAdvanced)
    }

    @Test
    fun `avanzada por tipo`() {
        for (type in listOf("custom", "poi_stop_duration", "poi_idle_duration", "driver", "driver_unauthorized", "task_status")) {
            assertTrue(type, rule(type = type).isAdvanced)
        }
        assertFalse(rule(type = "stop_duration").isAdvanced)
    }
}
