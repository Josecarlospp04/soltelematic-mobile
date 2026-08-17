package pe.soltelematic.mobile.data.mapper

import pe.soltelematic.mobile.data.remote.dto.UserDto
import pe.soltelematic.mobile.domain.model.User

fun UserDto.toDomain(): User = User(id = id, name = name, email = email)
