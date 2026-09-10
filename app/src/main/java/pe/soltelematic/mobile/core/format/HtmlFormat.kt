package pe.soltelematic.mobile.core.format

private val BR_TAG = Regex("<br\\s*/?>", RegexOption.IGNORE_CASE)
private val ANY_TAG = Regex("<[^>]+>")

/**
 * description de CommandAttribute (ver GET commands) trae HTML simple, solo &lt;br&gt; visto hasta
 * ahora -- se convierte a salto de línea real para un Text de Compose (que no interpreta HTML) en
 * vez de mostrar la etiqueta cruda. Cualquier otra etiqueta se descarta por las dudas, no se
 * renderiza como texto: mejor perder un <b> que mostrarlo literal.
 */
fun String.stripSimpleHtml(): String =
    replace(BR_TAG, "\n").replace(ANY_TAG, "")
