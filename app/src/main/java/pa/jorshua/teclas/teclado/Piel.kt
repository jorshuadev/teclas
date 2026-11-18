package pa.jorshua.teclas.teclado

import android.content.Context
import pa.jorshua.teclas.Preferencias

/**
 * El acabado del teclado: colores del cuerpo y las teclas, trastes cromados (rectos o en curva),
 * teclas esculpidas o planas. Todo en ARGB.
 */
data class Piel(
    val id: String,
    val nombre: String,
    val descripcion: String,
    val fondoArriba: Int,
    val fondoAbajo: Int,
    /** Degradado de la tecla en reposo: arriba, medio, abajo. */
    val tecla: IntArray,
    /** Degradado de la tecla hundida: arriba, abajo. */
    val hundida: IntArray,
    val letra: Int,
    val alt: Int,
    /** Color de la letra impresa cuando el protagonista es el carácter alt. */
    val apagado: Int,
    /** Trastes cromados entre filas: sombra, brillo, cuerpo, borde. null = sin trastes. */
    val traste: IntArray?,
    /** Cuánto bajan en el centro los trastes (dp): la "sonrisa" del teclado. 0 = rectos. */
    val curva: Float,
    /** Brillo inclinado hacia el borde de cada tecla, como si estuviera tallada. */
    val esculpida: Boolean,
    val radio: Float,
    val brillo: Int,
    val sombra: Int,
    val sombraLetra: Int,
)

object Pieles {

    private val TRASTE_CROMO = intArrayOf(0xFF2A2A2D.toInt(), 0xFFF8F8FA.toInt(), 0xFFA6A7AD.toInt(), 0xFF1C1C1E.toInt())

    val todas = listOf(
        Piel(
            "brillante", "Negro brillante", "Teclas esculpidas y trastes cromados en curva",
            0xFF17171A.toInt(), 0xFF040405.toInt(),
            intArrayOf(0xFF5C5D63.toInt(), 0xFF26272B.toInt(), 0xFF09090A.toInt()),
            intArrayOf(0xFF1F2023.toInt(), 0xFF060607.toInt()),
            0xFFF6F6F8.toInt(), 0xFFC6CCD6.toInt(), 0xFF8A8D94.toInt(),
            TRASTE_CROMO, 7f, true, 11f, 0x80FFFFFF.toInt(), 0xD0000000.toInt(), 0xB0000000.toInt(),
        ),
        Piel(
            "mate", "Negro mate", "El clásico: trastes rectos y signos en ámbar",
            0xFF1C1C1F.toInt(), 0xFF070708.toInt(),
            intArrayOf(0xFF55555A.toInt(), 0xFF2A2A2D.toInt(), 0xFF111113.toInt()),
            intArrayOf(0xFF26262A.toInt(), 0xFF0A0A0B.toInt()),
            0xFFF2F2F2.toInt(), 0xFFFFB84D.toInt(), 0xFF8C8C92.toInt(),
            intArrayOf(0xFF2E2E30.toInt(), 0xFFF2F2F4.toInt(), 0xFF9A9A9E.toInt(), 0xFF2E2E30.toInt()),
            0f, false, 7f, 0x55FFFFFF, 0xC0000000.toInt(), 0x80000000.toInt(),
        ),
        Piel(
            "perla", "Blanco perla", "Cuerpo blanco, teclas nacaradas y signos en azul",
            0xFFF5F5F7.toInt(), 0xFFD5D6DB.toInt(),
            intArrayOf(0xFFFFFFFF.toInt(), 0xFFF0F1F4.toInt(), 0xFFCDCED5.toInt()),
            intArrayOf(0xFFD3D4DA.toInt(), 0xFFB9BAC2.toInt()),
            0xFF25262A.toInt(), 0xFF2F7DE1.toInt(), 0xFF8E9098.toInt(),
            intArrayOf(0xFF8A8B91.toInt(), 0xFFFFFFFF.toInt(), 0xFFC2C3C9.toInt(), 0xFF6E6F75.toInt()),
            6f, true, 11f, 0xE0FFFFFF.toInt(), 0x50000000, 0x30FFFFFF,
        ),
        Piel(
            "plata", "Plata", "Teclas plateadas sobre cuerpo grafito, signos en rojo",
            0xFF4A4C51.toInt(), 0xFF232427.toInt(),
            intArrayOf(0xFFF1F2F5.toInt(), 0xFFC7C9CF.toInt(), 0xFF8F9197.toInt()),
            intArrayOf(0xFFB4B6BC.toInt(), 0xFF7E8086.toInt()),
            0xFF17181B.toInt(), 0xFFC0261E.toInt(), 0xFF5B5D63.toInt(),
            intArrayOf(0xFF151517.toInt(), 0xFF9B9DA3.toInt(), 0xFF4D4F54.toInt(), 0xFF0E0E10.toInt()),
            3f, false, 8f, 0xCCFFFFFF.toInt(), 0xB0000000.toInt(), 0x40FFFFFF,
        ),
        Piel(
            "carbono", "Carbono", "Plano y moderno: teclas mate, sin trastes",
            0xFF121214.toInt(), 0xFF0B0B0C.toInt(),
            intArrayOf(0xFF26262A.toInt(), 0xFF1E1E21.toInt(), 0xFF19191B.toInt()),
            intArrayOf(0xFF121214.toInt(), 0xFF0D0D0E.toInt()),
            0xFFEDEDED.toInt(), 0xFF9A9CA2.toInt(), 0xFF6E7076.toInt(),
            null, 0f, false, 5f, 0x1EFFFFFF, 0x90000000.toInt(), 0x00000000,
        ),
        Piel(
            "granate", "Granate", "Cuerpo rojo vino, teclas negras y trastes cobrizos",
            0xFF4E0F16.toInt(), 0xFF1E0508.toInt(),
            intArrayOf(0xFF4A4B50.toInt(), 0xFF202124.toInt(), 0xFF080809.toInt()),
            intArrayOf(0xFF1C1D20.toInt(), 0xFF060607.toInt()),
            0xFFF8F2F2.toInt(), 0xFFFFB3BD.toInt(), 0xFF9C8C8E.toInt(),
            intArrayOf(0xFF3A0A10.toInt(), 0xFFF2A0A8.toInt(), 0xFFA8414B.toInt(), 0xFF2A0609.toInt()),
            5f, true, 10f, 0x70FFFFFF, 0xD0000000.toInt(), 0xB0000000.toInt(),
        ),
    )

    fun porId(id: String?) = todas.firstOrNull { it.id == id } ?: todas.first()

    fun actual(ctx: Context) = porId(Preferencias(ctx).piel)
}
