package pa.jorshua.teclas.inicio

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextClock
import android.widget.TextView
import android.widget.Toast
import pa.jorshua.teclas.AjustesActivity
import pa.jorshua.teclas.Preferencias
import pa.jorshua.teclas.teclado.BarraNavView
import pa.jorshua.teclas.teclado.Deck
import pa.jorshua.teclas.teclado.Tecla
import pa.jorshua.teclas.teclado.TecladoView
import pa.jorshua.teclas.teclado.Tipo

/**
 * Pantalla de inicio: arriba el reloj y las apps fijadas sobre el fondo de pantalla, abajo el
 * teclado siempre a la vista. Teclear busca; mantener una letra abre su atajo; la tecla verde llama.
 */
class HomeActivity : Activity(), TecladoView.Oyente, BarraNavView.Acciones {

    private lateinit var prefs: Preferencias
    private lateinit var buscador: Buscador
    private lateinit var deck: Deck
    private lateinit var inicio: View
    private lateinit var panel: LinearLayout
    private lateinit var campo: TextView
    private lateinit var titulo: TextView
    private lateinit var lista: ListView
    private lateinit var dock: LinearLayout

    private val consulta = StringBuilder()
    /** La tecla que produjo cada carácter de la consulta, para leerla también como número. */
    private val crudas = ArrayList<Tecla?>()
    private var todas = false
    private var resultados: List<Resultado> = emptyList()
    private var sel = 0
    private var selDock = 0
    private var alturaUsada = 0
    private var linterna = false
    private val adaptador = Adaptador()

    private val dp get() = resources.displayMetrics.density
    private fun px(v: Int) = (v * dp).toInt()

    override fun onCreate(estado: Bundle?) {
        super.onCreate(estado)
        prefs = Preferencias(this)
        buscador = Buscador(this)
        Atajos.inicializar(this, prefs)

        window.setDecorFitsSystemWindows(false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.BLACK

        val raiz = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val pantalla = FrameLayout(this)
        inicio = crearInicio()
        panel = crearPanel()
        pantalla.addView(inicio, FrameLayout.LayoutParams(-1, -1))
        pantalla.addView(panel, FrameLayout.LayoutParams(-1, -1))
        raiz.addView(pantalla, LinearLayout.LayoutParams(-1, 0, 1f))

        alturaUsada = prefs.alturaPct
        deck = Deck(this, this, this, Deck.alto(this, alturaUsada))
        raiz.addView(deck.vista, LinearLayout.LayoutParams(-1, -2))
        raiz.setOnApplyWindowInsetsListener { _, ins ->
            val b = ins.getInsets(WindowInsets.Type.systemBars())
            pantalla.setPadding(b.left, b.top, b.right, 0)
            deck.vista.setPadding(0, 0, 0, b.bottom)
            ins
        }
        setContentView(raiz)

        getSystemService(CameraManager::class.java)?.registerTorchCallback(object : CameraManager.TorchCallback() {
            override fun onTorchModeChanged(id: String, encendida: Boolean) {
                linterna = encendida
            }
        }, null)

        if (!prefs.pidioPermisos) {
            prefs.pidioPermisos = true
            requestPermissions(arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.CALL_PHONE), 1)
        }
    }

    override fun onResume() {
        super.onResume()
        // Teclas apagado: el botón Inicio lleva al inicio normal del teléfono.
        if (!prefs.activo) {
            pa.jorshua.teclas.Interruptor.otroInicio(this)?.let { startActivity(it); return }
        }
        if (prefs.alturaPct != alturaUsada) {
            recreate(); return
        }
        deck.teclado.recargar(); deck.barra.recargar()
        pa.jorshua.teclas.fijo.TecladoFijo.inicio = this
        pa.jorshua.teclas.fijo.TecladoFijo.activo?.actualizar()
        buscador.cargarApps()
        refrescarDock()
        refrescar()
    }

    override fun onPause() {
        super.onPause()
        if (pa.jorshua.teclas.fijo.TecladoFijo.inicio === this) pa.jorshua.teclas.fijo.TecladoFijo.inicio = null
    }

    override fun onStop() {
        super.onStop()
        reiniciar()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        reiniciar()   // pulsar Inicio estando en el inicio deja todo limpio
    }

    @Deprecated("Activity sin AndroidX")
    override fun onBackPressed() = atras()

    // ---- Vistas -------------------------------------------------------------------------------

    private fun crearInicio(): View {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        val sombra = 0xAA000000.toInt()
        col.addView(TextClock(this).apply {
            format12Hour = "h:mm"; format24Hour = "H:mm"
            textSize = 66f; setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            setShadowLayer(10f, 0f, 2f, sombra)
        }, LinearLayout.LayoutParams(-2, -2).apply { topMargin = px(28) })
        col.addView(TextClock(this).apply {
            format12Hour = "EEEE d 'de' MMMM"; format24Hour = "EEEE d 'de' MMMM"
            textSize = 16f; setTextColor(0xFFE6E6E6.toInt())
            setShadowLayer(6f, 0f, 1f, sombra)
        }, LinearLayout.LayoutParams(-2, -2))
        col.addView(View(this), LinearLayout.LayoutParams(-1, 0, 1f))
        col.addView(TextView(this).apply {
            text = "Escribe para buscar · mantén una letra para su atajo"
            textSize = 12f; setTextColor(0xCCFFFFFF.toInt()); gravity = Gravity.CENTER
            setShadowLayer(6f, 0f, 1f, sombra)
        }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = px(8) })
        dock = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(px(6), px(8), px(6), px(8))
            setBackgroundColor(0x99000000.toInt())
        }
        col.addView(dock, LinearLayout.LayoutParams(-1, -2))
        return col
    }

    private fun crearPanel(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(0xEE0E0E10.toInt())
        visibility = View.GONE
        campo = TextView(this@HomeActivity).apply {
            textSize = 19f; setTextColor(0xFF111111.toInt())
            setPadding(px(14), px(9), px(14), px(9))
            isSingleLine = true; ellipsize = TextUtils.TruncateAt.START
            background = GradientDrawable().apply {
                cornerRadius = 22 * dp
                colors = intArrayOf(0xFFFFFFFF.toInt(), 0xFFDCDCE0.toInt())
            }
        }
        addView(campo, LinearLayout.LayoutParams(-1, -2).apply { setMargins(px(10), px(10), px(10), px(4)) })
        titulo = TextView(this@HomeActivity).apply {
            textSize = 12f; setTextColor(0xFF8FA6C8.toInt()); isAllCaps = true
            setPadding(px(16), px(6), px(16), px(4))
        }
        addView(titulo)
        lista = ListView(this@HomeActivity).apply {
            divider = null
            adapter = adaptador
            setOnItemClickListener { _, _, pos, _ -> activar(resultados.getOrNull(pos)) }
            setOnItemLongClickListener { _, _, pos, _ ->
                resultados.getOrNull(pos)?.componente?.let { opciones(it) }; true
            }
        }
        addView(lista, LinearLayout.LayoutParams(-1, 0, 1f))
    }

    private fun refrescarDock() {
        dock.removeAllViews()
        val fijadas = prefs.fijadas.mapNotNull { ComponentName.unflattenFromString(it) }
            .filter { c -> buscador.apps.any { it.componente == c } }
        if (fijadas.isEmpty()) {
            dock.addView(TextView(this).apply {
                text = "Mantén pulsada una app en la lista (tecla menú) para fijarla aquí"
                setTextColor(0xCCFFFFFF.toInt()); textSize = 12f; gravity = Gravity.CENTER
            }, LinearLayout.LayoutParams(-1, px(56)))
            return
        }
        selDock = selDock.coerceIn(0, fijadas.size - 1)
        fijadas.forEachIndexed { i, c ->
            val celda = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(0, px(4), 0, px(4))
                if (i == selDock) background = GradientDrawable().apply {
                    cornerRadius = 10 * dp
                    setColor(0x553D8BFF)
                    setStroke(px(2), 0xFF4AA3FF.toInt())
                }
                addView(ImageView(this@HomeActivity).apply { setImageDrawable(buscador.icono(c)) },
                    LinearLayout.LayoutParams(px(44), px(44)))
                addView(TextView(this@HomeActivity).apply {
                    text = buscador.etiqueta(c); textSize = 11f; setTextColor(Color.WHITE)
                    isSingleLine = true; ellipsize = TextUtils.TruncateAt.END; gravity = Gravity.CENTER
                    setShadowLayer(4f, 0f, 1f, 0xFF000000.toInt())
                }, LinearLayout.LayoutParams(-1, -2))
                setOnClickListener { abrirApp(c) }
                setOnLongClickListener { opciones(c); true }
            }
            dock.addView(celda, LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(px(2), 0, px(2), 0) })
        }
    }

    private fun panelVisible() = consulta.isNotEmpty() || todas

    private fun refrescar() {
        if (!panelVisible()) {
            panel.visibility = View.GONE
            inicio.visibility = View.VISIBLE
            return
        }
        panel.visibility = View.VISIBLE
        inicio.visibility = View.INVISIBLE
        val q = consulta.toString()
        if (q.isEmpty()) {
            campo.text = "▏"
            titulo.text = "Todas las apps (${buscador.apps.size})"
            resultados = buscador.todas()
        } else {
            campo.text = "$q▏"
            resultados = buscador.buscar(q, numeroDeTeclas(), linterna)
            titulo.text = "Resultados"
        }
        sel = 0
        adaptador.notifyDataSetChanged()
        lista.setSelection(0)
    }

    private fun reiniciar() {
        consulta.clear(); crudas.clear(); todas = false
        if (::panel.isInitialized) refrescar()
    }

    /**
     * Lo tecleado leído con los números pintados en las teclas: "wer" es 123. Así se marca
     * desde el inicio sin pulsar alt, como en los teléfonos con teclado.
     */
    private fun numeroDeTeclas(): String? {
        val sb = StringBuilder()
        for ((i, ch) in consulta.withIndex()) {
            val t = crudas.getOrNull(i)
            when {
                ch.isDigit() || ch == '*' || ch == '#' || (ch == '+' && i == 0) -> sb.append(ch)
                t?.tipo == Tipo.LETRA && t.alt.length == 1 && t.alt[0].isDigit() -> sb.append(t.alt)
                else -> return null
            }
        }
        return sb.toString().takeIf { it.isNotEmpty() }
    }

    // ---- Teclado ------------------------------------------------------------------------------

    override fun alEscribir(texto: String, tecla: Tecla?) {
        if (consulta.isEmpty() && texto == " ") return
        for (ch in texto) {
            consulta.append(ch); crudas.add(tecla)
        }
        refrescar()
    }

    override fun alBorrar() {
        if (consulta.isEmpty()) return
        consulta.deleteCharAt(consulta.length - 1)
        crudas.removeAt(crudas.size - 1)
        refrescar()
    }

    override fun alEnter() = pulsarTrackpad()

    /** En la búsqueda no hay saltos de línea: Shift + Enter abre lo seleccionado igual que Enter. */
    override fun alEnterConShift() = alEnter()

    override fun alPulsarLargo(letra: Char): Boolean {
        if (panelVisible()) return false
        val c = prefs.atajo(letra)?.let { ComponentName.unflattenFromString(it) }
        if (c == null) {
            Toast.makeText(this, "La ${letra.uppercaseChar()} no tiene atajo. Mantén pulsada una app en la lista para asignársela.",
                Toast.LENGTH_LONG).show()
        } else {
            abrirApp(c)
        }
        return true
    }

    /** Teclado físico de verdad (o `adb shell input`): se comporta igual que el dibujado. */
    override fun onKeyDown(code: Int, e: KeyEvent): Boolean {
        when (code) {
            KeyEvent.KEYCODE_DEL -> alBorrar()
            KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_DPAD_CENTER -> pulsarTrackpad()
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> mover(code)
            KeyEvent.KEYCODE_CALL -> llamar()
            KeyEvent.KEYCODE_MENU -> menu()
            else -> {
                val u = e.unicodeChar
                if (u == 0) return super.onKeyDown(code, e)
                val ch = u.toChar()
                val tecla = pa.jorshua.teclas.teclado.Distribucion.filas(pa.jorshua.teclas.teclado.TipoTeclado.QWERTY).flatten()
                    .firstOrNull { it.tipo == Tipo.LETRA && it.letra == ch.lowercaseChar() }
                alEscribir(ch.toString(), tecla)
            }
        }
        return true
    }

    // ---- Fila de navegación -------------------------------------------------------------------

    override fun llamar() {
        val r = resultados.getOrNull(sel)
        val numero = when {
            panelVisible() && (r?.tipo == Resultado.Tipo.CONTACTO || r?.tipo == Resultado.Tipo.LLAMAR) -> r.dato
            consulta.isNotEmpty() -> numeroDeTeclas()
            else -> null
        }
        if (numero != null) llamarA(numero)
        else abrir(Intent(Intent.ACTION_DIAL))
    }

    override fun menu() {
        todas = !todas
        if (todas) {
            consulta.clear(); crudas.clear()
        }
        refrescar()
    }

    override fun menuLargo() {
        abrir(Intent(this, AjustesActivity::class.java))
    }

    override fun atras() {
        if (panelVisible()) reiniciar()
    }

    override fun colgar() = reiniciar()

    override fun mover(tecla: Int) {
        if (panelVisible()) {
            if (resultados.isEmpty()) return
            sel = when (tecla) {
                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_LEFT -> (sel - 1).coerceAtLeast(0)
                else -> (sel + 1).coerceAtMost(resultados.size - 1)
            }
            adaptador.notifyDataSetChanged()
            lista.smoothScrollToPosition(sel)
        } else {
            val n = dock.childCount
            if (n == 0) return
            selDock = when (tecla) {
                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_LEFT -> (selDock - 1 + n) % n
                else -> (selDock + 1) % n
            }
            refrescarDock()
        }
    }

    override fun pulsarTrackpad() {
        if (panelVisible()) {
            activar(resultados.getOrNull(sel))
        } else {
            prefs.fijadas.mapNotNull { ComponentName.unflattenFromString(it) }
                .filter { c -> buscador.apps.any { it.componente == c } }
                .getOrNull(selDock)?.let { abrirApp(it) }
        }
    }

    // ---- Acciones -----------------------------------------------------------------------------

    private fun activar(r: Resultado?) {
        r ?: return
        when (r.tipo) {
            Resultado.Tipo.APP -> abrirApp(r.componente!!)
            Resultado.Tipo.CONTACTO, Resultado.Tipo.LLAMAR -> llamarA(r.dato!!)
            Resultado.Tipo.SMS -> abrir(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + r.dato)))
            Resultado.Tipo.CALCULO -> {
                getSystemService(ClipboardManager::class.java)
                    ?.setPrimaryClip(ClipData.newPlainText("resultado", r.dato))
                Toast.makeText(this, "${r.dato} copiado", Toast.LENGTH_SHORT).show()
            }
            Resultado.Tipo.LINTERNA -> {
                try {
                    val cm = getSystemService(CameraManager::class.java)
                    val id = cm.cameraIdList.firstOrNull() ?: return
                    cm.setTorchMode(id, !linterna)
                    linterna = !linterna
                    reiniciar()
                } catch (e: Exception) {
                    Toast.makeText(this, "No se pudo usar la linterna", Toast.LENGTH_SHORT).show()
                }
            }
            Resultado.Tipo.AJUSTE, Resultado.Tipo.WEB -> r.intent?.let { abrir(it) }
        }
    }

    private fun abrirApp(c: ComponentName) {
        abrir(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(c)
            .addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED))
    }

    private fun llamarA(numero: String) {
        val uri = Uri.parse("tel:" + Uri.encode(numero))
        val directa = checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
        abrir(Intent(if (directa) Intent.ACTION_CALL else Intent.ACTION_DIAL, uri))
    }

    private fun abrir(i: Intent) {
        try {
            startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "No hay ninguna app para eso", Toast.LENGTH_SHORT).show()
        } catch (e: SecurityException) {
            Toast.makeText(this, "Falta un permiso para eso", Toast.LENGTH_SHORT).show()
        }
    }

    private fun opciones(c: ComponentName) {
        val fijada = c.flattenToString() in prefs.fijadas
        val items = arrayOf(
            "Asignar a una tecla…",
            if (fijada) "Quitar del inicio" else "Fijar en el inicio",
            "Información de la app",
            "Desinstalar",
        )
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(buscador.etiqueta(c))
            .setItems(items) { _, i ->
                val pkg = Uri.fromParts("package", c.packageName, null)
                when (i) {
                    0 -> elegirTecla(c)
                    1 -> {
                        prefs.fijadas = if (fijada) prefs.fijadas - c.flattenToString() else prefs.fijadas + c.flattenToString()
                        refrescarDock()
                    }
                    2 -> abrir(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg))
                    3 -> abrir(Intent(Intent.ACTION_DELETE, pkg))
                }
            }
            .show()
    }

    private fun elegirTecla(c: ComponentName) {
        val letras = ('a'..'z').toList()
        val etiquetas = letras.map { l ->
            val actual = prefs.atajo(l)?.let { ComponentName.unflattenFromString(it) }
            "${l.uppercaseChar()}" + (actual?.let { "   →  ${buscador.etiqueta(it)}" } ?: "")
        }.toTypedArray()
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Tecla para ${buscador.etiqueta(c)}")
            .setItems(etiquetas) { _, i ->
                prefs.ponerAtajo(letras[i], c.flattenToString())
                Toast.makeText(this, "Mantén la ${letras[i].uppercaseChar()} en el inicio para abrirla", Toast.LENGTH_LONG).show()
            }
            .show()
    }

    // ---- Lista --------------------------------------------------------------------------------

    private inner class Adaptador : BaseAdapter() {
        override fun getCount() = resultados.size
        override fun getItem(p: Int) = resultados[p]
        override fun getItemId(p: Int) = p.toLong()

        override fun getView(p: Int, reciclada: View?, padre: ViewGroup): View {
            val fila = (reciclada as? LinearLayout) ?: crearFila()
            val r = resultados[p]
            val icono = fila.getChildAt(0) as ImageView
            val textos = fila.getChildAt(1) as LinearLayout
            if (r.componente != null) {
                icono.setImageDrawable(buscador.icono(r.componente)); icono.imageTintList = null
            } else {
                icono.setImageResource(r.iconoSistema)
                icono.imageTintList = android.content.res.ColorStateList.valueOf(0xFFDDE6F5.toInt())
            }
            (textos.getChildAt(0) as TextView).text = r.titulo
            (textos.getChildAt(1) as TextView).apply {
                text = r.subtitulo ?: ""
                visibility = if (r.subtitulo == null) View.GONE else View.VISIBLE
            }
            fila.background = if (p == sel) GradientDrawable().apply {
                cornerRadius = 8 * dp
                colors = intArrayOf(0xFF3A86F0.toInt(), 0xFF1B4FA6.toInt())
            } else null
            return fila
        }

        private fun crearFila() = LinearLayout(this@HomeActivity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(px(12), px(7), px(12), px(7))
            addView(ImageView(this@HomeActivity), LinearLayout.LayoutParams(px(40), px(40)))
            addView(LinearLayout(this@HomeActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(px(14), 0, 0, 0)
                addView(TextView(this@HomeActivity).apply {
                    textSize = 17f; setTextColor(Color.WHITE); isSingleLine = true
                    ellipsize = TextUtils.TruncateAt.END
                })
                addView(TextView(this@HomeActivity).apply {
                    textSize = 13f; setTextColor(0xFFA8B4C6.toInt()); isSingleLine = true
                })
            }, LinearLayout.LayoutParams(0, -2, 1f))
        }
    }
}
