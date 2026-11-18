package pa.jorshua.teclas

import android.content.Context

/** Ajustes del usuario: tacto, altura del teclado, atajos por letra y apps fijadas en el inicio. */
class Preferencias(ctx: Context) {
    private val sp = ctx.applicationContext.getSharedPreferences("teclas", Context.MODE_PRIVATE)

    var vibrar: Boolean
        get() = sp.getBoolean("vibrar", true)
        set(v) = sp.edit().putBoolean("vibrar", v).apply()

    var sonido: Boolean
        get() = sp.getBoolean("sonido", true)
        set(v) = sp.edit().putBoolean("sonido", v).apply()

    /** Porcentaje de la pantalla que ocupa el teclado (barra de navegación + teclas). */
    var alturaPct: Int
        get() = sp.getInt("altura", 42)
        set(v) = sp.edit().putInt("altura", v.coerceIn(30, 55)).apply()

    /** Acabado del teclado (ver Pieles). */
    var piel: String
        get() = sp.getString("piel", "brillante")!!
        set(v) = sp.edit().putString("piel", v).apply()

    /**
     * Interruptor general. Apagado: sin teclado fijo ni pantalla dividida, el teclado del sistema vuelve
     * al anterior y el botón Inicio lleva al inicio normal del teléfono. Nada se desinstala.
     */
    var activo: Boolean
        get() = sp.getBoolean("activo", true)
        set(v) = sp.edit().putBoolean("activo", v).apply()

    /** Teclado en español: tecla Ñ y acentos con sym. */
    var espanol: Boolean
        get() = sp.getBoolean("espanol", true)
        set(v) = sp.edit().putBoolean("espanol", v).apply()

    /** qwerty, compacto o numerico (ver TipoTeclado). */
    var tipoTeclado: String
        get() = sp.getString("tipo_teclado", "qwerty")!!
        set(v) = sp.edit().putString("tipo_teclado", v).apply()

    /** Experimental: el teclado como app en la mitad de abajo de la pantalla dividida. */
    var modoDividido: Boolean
        get() = sp.getBoolean("modo_dividido", false)
        set(v) = sp.edit().putBoolean("modo_dividido", v).apply()

    var atajosListos: Boolean
        get() = sp.getBoolean("atajos_listos", false)
        set(v) = sp.edit().putBoolean("atajos_listos", v).apply()

    var pidioPermisos: Boolean
        get() = sp.getBoolean("pidio_permisos", false)
        set(v) = sp.edit().putBoolean("pidio_permisos", v).apply()

    /** Componente (ComponentName aplanado) que abre la letra al mantenerla pulsada en el inicio. */
    fun atajo(letra: Char): String? = sp.getString("atajo_${letra.lowercaseChar()}", null)

    fun ponerAtajo(letra: Char, componente: String?) {
        val clave = "atajo_${letra.lowercaseChar()}"
        sp.edit().apply { if (componente == null) remove(clave) else putString(clave, componente) }.apply()
    }

    var fijadas: List<String>
        get() = sp.getString("fijadas", "")!!.split('|').filter { it.isNotEmpty() }
        set(v) = sp.edit().putString("fijadas", v.joinToString("|")).apply()
}
