package pe.soltelematic.mobile.domain.model

/**
 * Sin name: /user no lo trae. El id sí vuelve a venir en /user (SettingsController) y se persiste
 * junto con la sesión (ver TokenStorage.saveUserId) para no depender de una llamada a /user
 * exitosa en cada arranque o reconexión.
 */
data class User(
    val id: Int?,
    val email: String?
)
