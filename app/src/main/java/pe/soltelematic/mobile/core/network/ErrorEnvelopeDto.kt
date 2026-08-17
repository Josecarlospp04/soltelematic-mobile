package pe.soltelematic.mobile.core.network

import kotlinx.serialization.Serializable

/**
 * Sobre de error común a todo `clientlite` (401, 422, 5xx): {statusCode, message, errors?}.
 * Vive en core/network porque lo interpreta el ApiCallExecutor, no una API en particular.
 */
@Serializable
data class ErrorEnvelopeDto(
    val statusCode: Int? = null,
    val message: String? = null,
    val errors: Map<String, List<String>>? = null
)
