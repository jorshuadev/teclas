package pa.jorshua.teclas

import android.Manifest
import android.app.Activity
import android.app.role.RoleManager
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.WindowInsets
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import pa.jorshua.teclas.teclado.Piel
import pa.jorshua.teclas.teclado.Pieles
import pa.jorshua.teclas.teclado.TecladoView
import pa.jorshua.teclas.teclado.TipoTeclado

/** Puesta en marcha en tres pasos (teclado, elegirlo, inicio) y los ajustes de tacto y altura. */
class AjustesActivity : Activity() {

    companion object {
        /** La capa del teclado se esconde aquí: los ajustes se usan con el teclado del sistema. */
        var delante = false
            private set

        /** Abierto desde el botón de ajustes rápidos al encender: terminar de activar. */
        const val EXTRA_ACTIVAR = "activar"
    }

    /**
     * Al encender: si el teclado del sistema ya no es Teclas (al apagar volvió al anterior), mostrar el
     * selector para elegirlo; y pedir de nuevo ser el inicio si hace falta.
     */
    private fun completarActivacion() {
        val elegido = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
            ?.startsWith(packageName) == true
        if (!elegido) getSystemService(InputMethodManager::class.java)?.showInputMethodPicker()
        val inicio = getSystemService(RoleManager::class.java)?.isRoleHeld(RoleManager.ROLE_HOME) == true
        if (!inicio) pedirInicio()
    }

    override fun onWindowFocusChanged(foco: Boolean) {
        super.onWindowFocusChanged(foco)
        // Desde el botón rápido: el selector de teclado solo se puede abrir con la ventana ya enfocada.
        if (foco && intent.getBooleanExtra(EXTRA_ACTIVAR, false)) {
            intent.removeExtra(EXTRA_ACTIVAR)
            completarActivacion()
        }
        if (foco) actualizarEstado()
    }

    override fun onPause() {
        delante = false
        super.onPause()
        pa.jorshua.teclas.fijo.TecladoFijo.activo?.actualizar()
    }

    private lateinit var prefs: Preferencias
    private lateinit var estadoTeclado: TextView
    private lateinit var estadoElegido: TextView
    private lateinit var estadoInicio: TextView
    private lateinit var estadoFijo: TextView
    private val dp get() = resources.displayMetrics.density
    private fun px(v: Int) = (v * dp).toInt()

    override fun onCreate(estado: Bundle?) {
        super.onCreate(estado)
        prefs = Preferencias(this)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(20), px(20), px(20), px(32))
        }
        col.addView(TextView(this).apply {
            text = "Teclas"; textSize = 30f; setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        })
        col.addView(TextView(this).apply {
            text = "Tu teléfono con teclado de los de antes."
            textSize = 15f; setTextColor(0xFFB0B0B8.toInt())
        }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = px(18) })

        // Interruptor general: apagar todo sin desinstalar, y volver a encenderlo cuando se quiera.
        val explicacion = TextView(this).apply { textSize = 13f; setTextColor(0xFF9A9AA2.toInt()) }
        fun explicar(activo: Boolean) {
            explicacion.text = if (activo)
                "Encendido. Apágalo para usar el celular como siempre: teclado normal, inicio normal y sin " +
                    "teclado fijo. No se desinstala nada. También puedes usar el botón «Teclas» de los ajustes rápidos."
            else
                "Apagado: el celular funciona como siempre. Enciéndelo para volver a tener el teclado."
        }
        val tarjetaInterruptor = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(14), px(10), px(14), px(12))
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = 14 * dp; setColor(0xFF1C2433.toInt()); setStroke(px(2), 0xFF4AA3FF.toInt())
            }
            addView(Switch(this@AjustesActivity).apply {
                text = "Teclas activado"; textSize = 19f; setTextColor(Color.WHITE)
                isChecked = prefs.activo
                setOnCheckedChangeListener { _, v ->
                    Interruptor.poner(this@AjustesActivity, v)
                    explicar(v)
                    if (v) completarActivacion()
                }
            }, LinearLayout.LayoutParams(-1, px(52)))
            addView(explicacion)
        }
        explicar(prefs.activo)
        col.addView(tarjetaInterruptor, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = px(18) })

        estadoTeclado = paso(col, "1. Activar el teclado",
            "Enciende «Teclas (teclado físico)» en la lista de teclados.") {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        }
        estadoElegido = paso(col, "2. Usarlo para escribir", "Elígelo como teclado actual.") {
            getSystemService(InputMethodManager::class.java)?.showInputMethodPicker()
        }
        estadoInicio = paso(col, "3. Usarlo como pantalla de inicio",
            "Al pulsar Inicio verás siempre el teclado.") { pedirInicio() }
        estadoFijo = paso(col, "4. Teclado fijo en todas las apps",
            "En Accesibilidad → Aplicaciones instaladas, enciende «Teclas · teclado fijo». " +
                "Tapa la parte de abajo de la app; mantén el botón menú para plegarlo.") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        col.addView(Switch(this).apply {
            text = "Modo dividido (experimental): el teclado como app en la mitad de abajo; la app de arriba se encoge en vez de quedar tapada"
            textSize = 15f; setTextColor(Color.WHITE)
            isChecked = prefs.modoDividido
            setOnCheckedChangeListener { _, v -> prefs.modoDividido = v }
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = px(6) })

        titulo(col, "Acabado del teclado")
        val tarjetasPiel = ArrayList<Pair<String, LinearLayout>>()
        val tarjetasTipo = ArrayList<Pair<String, LinearLayout>>()
        val previasTipo = ArrayList<Pair<TecladoView, TipoTeclado>>()
        fun marcarElegidas() {
            for ((id, t) in tarjetasPiel) t.background = marco(id == prefs.piel)
            for ((id, t) in tarjetasTipo) t.background = marco(id == prefs.tipoTeclado)
            val piel = Pieles.actual(this)
            for ((v, tipo) in previasTipo) v.mostrar(piel, tipo)
        }
        for (piel in Pieles.todas) {
            val tarjeta = tarjeta(piel.nombre, piel.descripcion, previa(piel, TipoTeclado.actual(this)))
            tarjeta.setOnClickListener { prefs.piel = piel.id; marcarElegidas() }
            tarjetasPiel += piel.id to tarjeta
            col.addView(tarjeta, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = px(10) })
        }

        titulo(col, "Tipo de teclado")
        for (tipo in TipoTeclado.values()) {
            val previa = previa(Pieles.actual(this), tipo)
            previasTipo += previa to tipo
            val tarjeta = tarjeta(tipo.nombre, tipo.descripcion, previa)
            tarjeta.setOnClickListener {
                prefs.tipoTeclado = tipo.id
                // Las vistas previas de las pieles enseñan el tipo elegido.
                for ((id, t) in tarjetasPiel) (t.getChildAt(2) as TecladoView).mostrar(Pieles.porId(id), tipo)
                marcarElegidas()
            }
            tarjetasTipo += tipo.id to tarjeta
            col.addView(tarjeta, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = px(10) })
        }
        marcarElegidas()

        titulo(col, "Idioma del teclado")
        col.addView(Switch(this).apply {
            text = "Español: tecla Ñ, y acentos con sym + vocal (á é í ó ú, sym + Y = ü)"
            textSize = 15f; setTextColor(Color.WHITE)
            isChecked = prefs.espanol
            setOnCheckedChangeListener { _, v ->
                prefs.espanol = v
                val tipo = TipoTeclado.actual(this@AjustesActivity)
                for ((id, t) in tarjetasPiel) (t.getChildAt(2) as TecladoView).mostrar(Pieles.porId(id), tipo, v)
                for ((vista, tp) in previasTipo) vista.mostrar(Pieles.actual(this@AjustesActivity), tp, v)
            }
        }, LinearLayout.LayoutParams(-1, -2))

        titulo(col, "Tacto")
        col.addView(Switch(this).apply {
            text = "Vibrar al pulsar"; textSize = 16f; setTextColor(Color.WHITE)
            isChecked = prefs.vibrar
            setOnCheckedChangeListener { _, v -> prefs.vibrar = v }
        }, LinearLayout.LayoutParams(-1, px(48)))
        col.addView(Switch(this).apply {
            text = "Sonido de tecla"; textSize = 16f; setTextColor(Color.WHITE)
            isChecked = prefs.sonido
            setOnCheckedChangeListener { _, v -> prefs.sonido = v }
        }, LinearLayout.LayoutParams(-1, px(48)))

        titulo(col, "Altura del teclado")
        val etiquetaAltura = TextView(this).apply { textSize = 15f; setTextColor(0xFFDDDDDD.toInt()) }
        fun ponerEtiqueta(v: Int) {
            etiquetaAltura.text = "$v % de la pantalla"
        }
        ponerEtiqueta(prefs.alturaPct)
        col.addView(etiquetaAltura)
        col.addView(SeekBar(this).apply {
            max = 25
            progress = prefs.alturaPct - 30
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar, p: Int, deUsuario: Boolean) {
                    prefs.alturaPct = p + 30; ponerEtiqueta(p + 30)
                }
                override fun onStartTrackingTouch(s: SeekBar) {}
                override fun onStopTrackingTouch(s: SeekBar) {}
            })
        })

        titulo(col, "Permisos")
        col.addView(Button(this).apply {
            text = "Contactos y llamadas directas"
            setOnClickListener {
                requestPermissions(arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.CALL_PHONE), 1)
            }
        })

        titulo(col, "Pruébalo aquí")
        col.addView(EditText(this).apply {
            hint = "Escribe algo…"; setTextColor(Color.WHITE); minLines = 2
        })
        col.addView(TextView(this).apply {
            textSize = 13f; setTextColor(0xFF9A9AA2.toInt())
            text = """
                • alt: una vez = un número o signo; dos veces = bloqueado.
                • shift: igual. Mantener una letra = mayúscula.
                • sym: signos extra (€, ¿, ¡, ©…).
                • Doble espacio pone punto y espacio.
                • Rueda (trackpad): en cualquier app mueve el recuadro azul por botones,
                  chats y enlaces; púlsala para abrir. Escribiendo, mueve el cursor.
                • Teclado fijo: menú = recientes, atrás, rojo = inicio, mantener menú = plegar.
                • Pulsar una letra en una app abre su campo o su buscador y escribe ahí.
                • En el inicio: escribe para buscar apps, contactos, ajustes o hacer cuentas
                  (5+3); mantén una letra para abrir su atajo; tecla verde para llamar.
            """.trimIndent()
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = px(14) })

        val scroll = ScrollView(this).apply {
            setBackgroundColor(0xFF111113.toInt())
            addView(col)
            setOnApplyWindowInsetsListener { v, ins ->
                val b = ins.getInsets(WindowInsets.Type.systemBars())
                v.setPadding(0, b.top, 0, b.bottom); ins
            }
        }
        setContentView(scroll)
    }

    override fun onResume() {
        super.onResume()
        delante = true
        pa.jorshua.teclas.fijo.TecladoFijo.activo?.actualizar()
        actualizarEstado()
    }

    private fun actualizarEstado() {
        val imm = getSystemService(InputMethodManager::class.java)
        val activado = imm.enabledInputMethodList.any { it.packageName == packageName }
        val elegido = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
            ?.startsWith(packageName) == true
        val inicio = getSystemService(RoleManager::class.java)?.isRoleHeld(RoleManager.ROLE_HOME) == true
        marcar(estadoTeclado, activado)
        marcar(estadoElegido, elegido)
        marcar(estadoInicio, inicio)
        val servicios = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
        marcar(estadoFijo, servicios.contains("$packageName/"))
    }

    private fun marcar(t: TextView, listo: Boolean) {
        t.text = if (listo) "✓ Listo" else "Pendiente"
        t.setTextColor(if (listo) 0xFF3FCB5A.toInt() else 0xFFFFB84D.toInt())
    }

    private fun pedirInicio() {
        val rm = getSystemService(RoleManager::class.java)
        try {
            if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_HOME) && !rm.isRoleHeld(RoleManager.ROLE_HOME)) {
                startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_HOME), 2)
            } else {
                startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
            }
        } catch (e: Exception) {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }

    /** Teclado de muestra (no escribe) con la piel y el tipo dados. */
    private fun previa(piel: Piel, tipo: TipoTeclado) = TecladoView(this).apply {
        interactivo = false
        mostrar(piel, tipo)
    }

    /** Tarjeta tocable: nombre, explicación y la vista previa. Hijo 2 = la previa. */
    private fun tarjeta(nombre: String, detalle: String, previa: TecladoView) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(px(12), px(10), px(12), px(12))
        isClickable = true
        addView(TextView(this@AjustesActivity).apply { text = nombre; textSize = 16f; setTextColor(Color.WHITE) })
        addView(TextView(this@AjustesActivity).apply {
            text = detalle; textSize = 12.5f; setTextColor(0xFF9A9AA2.toInt())
        }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = px(8) })
        addView(previa, LinearLayout.LayoutParams(-1, px(118)))
    }

    private fun marco(elegida: Boolean) = android.graphics.drawable.GradientDrawable().apply {
        cornerRadius = 14 * dp
        setColor(if (elegida) 0xFF1C2433.toInt() else 0xFF1A1A1D.toInt())
        setStroke(px(if (elegida) 2 else 1), if (elegida) 0xFF4AA3FF.toInt() else 0xFF2C2C30.toInt())
    }

    private fun titulo(col: LinearLayout, s: String) {
        col.addView(TextView(this).apply {
            text = s.uppercase(); textSize = 12f; setTextColor(0xFF8FA6C8.toInt())
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = px(22); bottomMargin = px(6) })
    }

    /** Un paso de la puesta en marcha: título, explicación, estado y botón. Devuelve el estado. */
    private fun paso(col: LinearLayout, titulo: String, detalle: String, accion: () -> Unit): TextView {
        val fila = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, px(8), 0, px(8))
        }
        val textos = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        textos.addView(TextView(this).apply { text = titulo; textSize = 17f; setTextColor(Color.WHITE) })
        textos.addView(TextView(this).apply { text = detalle; textSize = 13f; setTextColor(0xFF9A9AA2.toInt()) })
        val estado = TextView(this).apply { textSize = 13f }
        textos.addView(estado)
        fila.addView(textos, LinearLayout.LayoutParams(0, -2, 1f))
        fila.addView(Button(this).apply { text = "Abrir"; setOnClickListener { accion() } })
        col.addView(fila)
        return estado
    }
}
