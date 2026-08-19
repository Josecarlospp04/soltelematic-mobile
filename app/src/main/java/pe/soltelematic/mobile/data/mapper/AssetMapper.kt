package pe.soltelematic.mobile.data.mapper

import pe.soltelematic.mobile.core.network.IconUrlResolver
import pe.soltelematic.mobile.data.local.entity.AssetEntity
import pe.soltelematic.mobile.data.local.entity.TailPoint
import pe.soltelematic.mobile.data.remote.dto.AssetCoordinatesDto
import pe.soltelematic.mobile.data.remote.dto.AssetDto
import pe.soltelematic.mobile.domain.model.Asset
import pe.soltelematic.mobile.domain.model.AssetIcon
import pe.soltelematic.mobile.domain.model.AssetStatus
import pe.soltelematic.mobile.domain.model.AssetStatusType
import pe.soltelematic.mobile.domain.model.GeoPoint
import pe.soltelematic.mobile.domain.model.Ignition
import java.time.Instant

fun AssetDto.toEntity(iconUrlResolver: IconUrlResolver): AssetEntity = AssetEntity(
    id = id,
    groupId = groupId,
    name = name,
    active = active,
    lat = coordinates?.lat?.toDoubleOrNull(),
    lng = coordinates?.lng?.toDoubleOrNull(),
    speedValue = speed?.value,
    speedUnit = speed?.unit,
    speedHuman = speed?.human,
    engineStatus = engineStatus,
    statusType = status?.type,
    statusTitle = status?.title,
    statusColor = status?.color,
    iconUrl = iconUrlResolver.resolve(icon?.url),
    iconColor = icon?.color,
    iconCourse = icon?.course,
    tail = tail?.coordinates.orEmpty().mapNotNull { it.toTailPoint() },
    tailColor = tail?.color,
    lastSeenAt = time?.timestamp?.let(Instant::ofEpochSecond),
    lastSeenFormatted = time?.formatted
)

private fun AssetCoordinatesDto.toTailPoint(): TailPoint? {
    val latValue = lat?.toDoubleOrNull() ?: return null
    val lngValue = lng?.toDoubleOrNull() ?: return null
    return TailPoint(latValue, lngValue)
}

fun AssetEntity.toDomain(): Asset = Asset(
    id = id,
    groupId = groupId,
    name = name,
    active = active,
    position = if (lat != null && lng != null) GeoPoint(lat, lng) else null,
    speedKph = speedValue,
    speedText = speedHuman,
    ignition = when (engineStatus) {
        null -> Ignition.NO_SENSOR
        false -> Ignition.OFF
        true -> Ignition.ON
    },
    status = AssetStatus(
        type = statusType.toAssetStatusType(),
        title = statusTitle,
        colorHex = statusColor
    ),
    icon = AssetIcon(
        url = iconUrl,
        colorHex = iconColor,
        courseDegrees = iconCourse ?: 0f
    ),
    tail = tail.map { GeoPoint(it.lat, it.lng) },
    tailColorHex = tailColor,
    lastSeenAt = lastSeenAt,
    lastSeenFormatted = lastSeenFormatted
)

private fun String?.toAssetStatusType(): AssetStatusType = when (this) {
    "offline" -> AssetStatusType.OFFLINE
    "online" -> AssetStatusType.ONLINE
    "ack" -> AssetStatusType.ACK
    "engine" -> AssetStatusType.ENGINE
    "blocked" -> AssetStatusType.BLOCKED
    else -> AssetStatusType.UNKNOWN
}
