package pe.soltelematic.mobile.domain.model

/**
 * Un comando disponible para una unidad (GET commands, connection=gprs, ver
 * AssetDetailRepository.getCommands). type identifica el comando ante el servidor -- fijo por
 * protocolo ("engineStop") o "template_<id>" para una plantilla que el propio usuario creó en la
 * plataforma web -- y se reenvía tal cual al hacer POST, junto con el resto de attributes por su
 * name. El servidor ya decide qué comandos devolver según los permisos del usuario y el protocolo
 * de la unidad: la app no filtra ni oculta nada de esta lista.
 */
data class DeviceCommand(
    val type: String,
    val title: String,
    val attributes: List<CommandAttribute>
) {
    /**
     * "custom"/"serial" (Tobuli\Protocols\Commands::TYPE_CUSTOM/TYPE_SERIAL en el servidor) son los
     * únicos dos tipos donde el usuario escribe un comando RAW arbitrario en vez de uno preconfigurado
     * por el protocolo o por una plantilla propia -- estos son los que llevan la advertencia especial
     * antes de enviar (ver CommandsSection), el resto se envía con una confirmación simple.
     */
    val isRawCommand: Boolean get() = type == "custom" || type == "serial"

    /**
     * Sin atributos, o todos con un valor por defecto y ninguno obligatorio: no hace falta abrir
     * el formulario, una confirmación simple alcanza. Un atributo obligatorio siempre abre el
     * formulario aunque traiga default -- se quiere que el usuario lo vea/confirme explícitamente.
     */
    val canSendDirectly: Boolean
        get() = attributes.isEmpty() || attributes.all { !it.required && !it.defaultValue.isNullOrBlank() }
}

enum class CommandFieldType { TEXT, SELECT, INTEGER, DATETIME }

/**
 * fieldType cae a TEXT para cualquier valor no reconocido: el servidor puede sumar tipos de campo
 * nuevos (ver Tobuli\InputFields del backend) sin que la app deje de poder mostrar el atributo.
 * maxValue solo aplica a INTEGER (tope del valor, no de dígitos) y puede venir null.
 */
data class CommandAttribute(
    val name: String,
    val title: String,
    val fieldType: CommandFieldType,
    val defaultValue: String?,
    val description: String?,
    val required: Boolean,
    val options: List<CommandAttributeOption>,
    val maxValue: Int?
)

/** Una opción de un atributo SELECT. value es lo que se reenvía al servidor, title lo que se muestra. */
data class CommandAttributeOption(
    val value: String,
    val title: String
)

/** Resultado de negocio de POST commands -- distinto de ApiError: el servidor responde 200 en ambos casos. */
sealed class SendCommandOutcome {
    data class Success(val message: String) : SendCommandOutcome()
    data class Rejected(val errors: List<String>) : SendCommandOutcome()
}
