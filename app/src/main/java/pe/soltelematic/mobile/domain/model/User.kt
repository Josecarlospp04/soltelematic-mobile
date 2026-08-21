package pe.soltelematic.mobile.domain.model

/**
 * Sin id ni name: /user no los trae (verificado contra el servidor real, Bloque C Paso 0), y
 * modelarlos igual fue lo que rompía la deserialización en producción antes de este fix. El id
 * numérico del usuario existe (claim "sub" del JWT del access_token) pero no vive acá -- sacarlo
 * requiere decodificar el token, que hoy es deliberadamente responsabilidad de quien lo necesite
 * puntualmente, no algo que este modelo entregue como si viniera del servidor.
 */
data class User(
    val email: String?
)
