package pe.soltelematic.mobile.data.mapper

import pe.soltelematic.mobile.data.local.entity.AssetEntity
import pe.soltelematic.mobile.data.remote.dto.AssetDto
import pe.soltelematic.mobile.domain.model.Asset
import java.time.Instant

fun AssetDto.toEntity(): AssetEntity = AssetEntity(
    id = id,
    groupId = groupId,
    name = name,
    active = active,
    speed = speed,
    engineStatus = engineStatus,
    statusType = status?.type,
    statusTitle = status?.title,
    statusColor = status?.color,
    lat = coordinates?.lat,
    lng = coordinates?.lng,
    lastSeenAt = time?.timestamp?.let(Instant::ofEpochSecond),
    lastSeenFormatted = time?.formatted
)

fun AssetEntity.toDomain(): Asset = Asset(
    id = id,
    groupId = groupId,
    name = name,
    active = active,
    speed = speed,
    engineStatus = engineStatus,
    statusType = statusType,
    statusTitle = statusTitle,
    statusColor = statusColor,
    lat = lat,
    lng = lng,
    lastSeenAt = lastSeenAt,
    lastSeenFormatted = lastSeenFormatted
)
