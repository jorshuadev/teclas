package pa.jorshua.teclas.teclado

import android.content.Context
import pa.jorshua.teclas.Preferencias

enum class Tipo { LETRA, BORRAR, ENTER, ALT, SHIFT, ESPACIO, SIMBOLOS, CERO }

/**
 * Una tecla física: la letra impresa, el carácter alt pintado encima y su ancho relativo.
 * [letras] con más de una letra = tecla de toques sucesivos (compacto y numérico).
 * [numerica] = se dibuja con el número grande y las letras debajo, como en un teléfono.
 */
data class Tecla(
    val tipo: Tipo,
    val letra: Char = ' ',
    val alt: String = "",
    val peso: Float = 1f,
    val letras: String = "",
    val numerica: Boolean = false,
) {
    val multiple get() = letras.length > 1
}

enum class TipoTeclado(val id: String, val nombre: String, val descripcion: String) {
    QWERTY("qwerty", "QWERTY completo", "Una letra por tecla, con los números y signos pintados encima"),
    COMPACTO("compacto", "Compacto (2 letras por tecla)", "Teclas más grandes: toca dos veces para la segunda letra"),
    NUMERICO("numerico", "Numérico (12 teclas)", "El de teléfono de siempre: toca varias veces cada tecla; mantenla para el número");

    companion object {
        fun porId(id: String?) = values().firstOrNull { it.id == id } ?: QWERTY
        fun actual(ctx: Context) = porId(Preferencias(ctx).tipoTeclado)
    }
}

object Distribucion {

    /** [espanol]: tecla Ñ (y la ñ en las teclas de la N), acentos con sym. */
    fun filas(tipo: TipoTeclado, espanol: Boolean = false): List<List<Tecla>> = when (tipo) {
        TipoTeclado.QWERTY -> if (espanol) QWERTY_ES else QWERTY
        TipoTeclado.COMPACTO -> if (espanol) COMPACTO_ES else COMPACTO
        TipoTeclado.NUMERICO -> if (espanol) NUMERICO_ES else NUMERICO
    }

    /** Como el QWERTY, con la Ñ a la derecha de la L, igual que en los teclados en español. */
    private val QWERTY_ES: List<List<Tecla>> by lazy {
        QWERTY.mapIndexed { i, fila ->
            if (i == 1) letras("asdfghjklñ", "*456/:;'\"~") + Tecla(Tipo.BORRAR) else fila
        }
    }

    /** La ñ como tercera letra de la tecla de la N. */
    private val COMPACTO_ES: List<List<Tecla>> by lazy {
        COMPACTO.map { fila -> fila.map { if (it.letras == "bn") doble("bnñ", it.alt) else it } }
    }

    /** La ñ como cuarta letra del 6 (mnoñ), como en los teléfonos en español. */
    private val NUMERICO_ES: List<List<Tecla>> by lazy {
        NUMERICO.map { fila -> fila.map { if (it.letras == "mno") numero('6', "mnoñ") else it } }
    }

    /** Distribución QWERTY de cuatro filas: números y signos en las letras (con alt), dos shift y sym. */
    private val QWERTY: List<List<Tecla>> = listOf(
        letras("qwertyuiop", "#123()_-+@"),
        letras("asdfghjkl", "*456/:;'\"") + Tecla(Tipo.BORRAR),
        listOf(Tecla(Tipo.ALT)) + letras("zxcvbnm", "789?!,.") + Tecla(Tipo.ENTER, peso = 2f),
        listOf(
            Tecla(Tipo.SHIFT, peso = 1.3f),
            Tecla(Tipo.CERO, alt = "0", peso = 1.1f),
            Tecla(Tipo.ESPACIO, peso = 4.2f),
            Tecla(Tipo.SIMBOLOS, peso = 1.1f),
            Tecla(Tipo.SHIFT, peso = 1.3f),
        ),
    )

    /** Dos letras por tecla: menos teclas y más grandes. */
    private val COMPACTO: List<List<Tecla>> = listOf(
        listOf(doble("qw", "!"), doble("er", "1"), doble("ty", "2"), doble("ui", "3"), doble("op", "?"), Tecla(Tipo.BORRAR)),
        listOf(doble("as", "@"), doble("df", "4"), doble("gh", "5"), doble("jk", "6"), doble("l", "'"), Tecla(Tipo.ENTER)),
        listOf(Tecla(Tipo.ALT), doble("zx", ","), doble("cv", "7"), doble("bn", "8"), doble("m", "9"), Tecla(Tipo.SHIFT)),
        listOf(
            Tecla(Tipo.CERO, alt = "0"),
            Tecla(Tipo.ESPACIO, peso = 3.6f),
            Tecla(Tipo.SIMBOLOS, peso = 1.4f),
        ),
    )

    /** Teclado de teléfono: 1-9, el 0 es el espacio; alt bloquea los números. */
    private val NUMERICO: List<List<Tecla>> = listOf(
        listOf(numero('1', ".,?!'-"), numero('2', "abc"), numero('3', "def"), Tecla(Tipo.BORRAR, peso = 0.8f)),
        listOf(numero('4', "ghi"), numero('5', "jkl"), numero('6', "mno"), Tecla(Tipo.ENTER, peso = 0.8f)),
        listOf(numero('7', "pqrs"), numero('8', "tuv"), numero('9', "wxyz"), Tecla(Tipo.ALT, peso = 0.8f)),
        listOf(
            Tecla(Tipo.SHIFT),
            Tecla(Tipo.ESPACIO, alt = "0", numerica = true),
            Tecla(Tipo.SIMBOLOS),
            Tecla(Tipo.SHIFT, peso = 0.8f),
        ),
    )

    private val simbolos = mapOf(
        'q' to "€", 'w' to "£", 'e' to "¥", 'r' to "$", 't' to "%", 'y' to "=", 'u' to "[", 'i' to "]", 'o' to "{", 'p' to "}",
        'a' to "&", 's' to "\\", 'd' to "|", 'f' to "<", 'g' to ">", 'h' to "^", 'j' to "`", 'k' to "~", 'l' to "°",
        'z' to "¡", 'x' to "¿", 'c' to "©", 'v' to "®", 'b' to "™", 'n' to "…", 'm' to "•",
    )

    /** En español, sym + vocal = vocal con tilde (y sym + Y = ü, sym + N = ñ). */
    private val acentos = mapOf('a' to "á", 'e' to "é", 'i' to "í", 'o' to "ó", 'u' to "ú", 'y' to "ü", 'n' to "ñ")

    fun simbolo(letra: Char, espanol: Boolean = false): String =
        (if (espanol) acentos[letra] else null) ?: simbolos[letra] ?: letra.toString()

    private fun letras(letras: String, alts: String) =
        letras.mapIndexed { i, c -> Tecla(Tipo.LETRA, c, alts[i].toString()) }

    private fun doble(letras: String, alt: String) = Tecla(Tipo.LETRA, letras[0], alt, letras = letras)

    private fun numero(digito: Char, letras: String) =
        Tecla(Tipo.LETRA, letras[0], digito.toString(), letras = letras, numerica = true)
}
