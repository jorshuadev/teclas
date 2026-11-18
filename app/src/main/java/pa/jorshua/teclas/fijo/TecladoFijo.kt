package pa.jorshua.teclas.fijo

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.TextView
import android.widget.Toast
import pa.jorshua.teclas.AjustesActivity
import pa.jorshua.teclas.Preferencias
import pa.jorshua.teclas.ime.TecladoIme
import pa.jorshua.teclas.teclado.BarraNavView
import pa.jorshua.teclas.teclado.Deck
import pa.jorshua.teclas.teclado.Tecla
import pa.jorshua.teclas.teclado.TecladoView

/**
 * El teclado fijo encima de todas las apps (capa de accesibilidad). Se esconde solo cuando la app
 * abre el teclado del sistema (que es el mismo diseño), en nuestro inicio y en la pantalla de bloqueo.
 * Android no deja encoger las apps, así que mientras está a la vista tapa la parte de abajo;
 * tocar el trackpad lo pliega a una pastilla.
 */
class TecladoFijo : AccessibilityService(), TecladoView.Oyente, BarraNavView.Acciones {

    companion object {
        var activo: TecladoFijo? = null
            private set

        /**
         * El inicio, mientras está delante. La capa no se esconde en el inicio (está justo encima de su
         * teclado, idéntica): le reenvía las teclas. Así hay un solo teclado que nunca desaparece al
         * abrir una app ni al volver.
         */
        var inicio: pa.jorshua.teclas.inicio.HomeActivity? = null
    }

    private lateinit var wm: WindowManager
    private var deck: Deck? = null
    private var pastilla: TextView? = null
    private var alturaUsada = 0
    private var plegado = false
    private var navegador: Navegador? = null
    private val dp get() = resources.displayMetrics.density

    private val pantalla = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) = actualizar()
    }

    override fun onServiceConnected() {
        activo = this
        wm = getSystemService(WindowManager::class.java)
        crearVistas()
        registerReceiver(pantalla, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_USER_PRESENT); addAction(Intent.ACTION_SCREEN_ON)
        })
        actualizar()
    }

    override fun onDestroy() {
        if (activo === this) activo = null
        try { unregisterReceiver(pantalla) } catch (_: Exception) {}
        quitarVistas()
        navegador?.destruir(); navegador = null
        tapa?.let { try { wm.removeView(it) } catch (_: Exception) {} }; tapa = null
        super.onDestroy()
    }

    override fun onInterrupt() {}

    private fun horizontal() =
        resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    override fun onConfigurationChanged(nueva: android.content.res.Configuration) {
        super.onConfigurationChanged(nueva)
        actualizar()
    }

    /** Deshacer la pantalla dividida sin ir al inicio ni plegar el teclado (la app queda entera). */
    private fun deshacerDivision() {
        armando = true
        performGlobalAction(GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN)
        manejador.postDelayed({ armando = false; actualizar() }, 900)
    }

    /** La app que quedó arriba la última vez que se dividió la pantalla. */
    private var appDividida: String? = null

    /** Ya se deshizo la división por estar en horizontal: no volver a tocarla hasta volver a vertical. */
    private var salidaHorizontal = false

    override fun onAccessibilityEvent(e: AccessibilityEvent) {
        when (e.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> { navegador?.olvidar(); actualizar() }
            AccessibilityEvent.TYPE_WINDOWS_CHANGED -> actualizar()
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> navegador?.reubicar()
            // La app enfocó un campo: en dividido, preparar la mayúscula de la primera letra.
            AccessibilityEvent.TYPE_VIEW_FOCUSED -> if (DeckActivity.visible) campoDividido()
        }
    }

    private fun crearVistas() {
        if (navegador == null) navegador = Navegador(this, wm) { raizApp() }
        alturaUsada = Preferencias(this).alturaPct
        val d = Deck(this, this, this, Deck.alto(this, alturaUsada))
        deck = d
        wm.addView(d.vista, parametros(WindowManager.LayoutParams.MATCH_PARENT, Gravity.BOTTOM))
        TecladoIme.tecladoExterno = d.teclado   // mayúscula automática también en modo dividido

        val p = TextView(this).apply {
            text = "▲  Teclado"
            textSize = 13f
            setTextColor(Color.WHITE)
            setPadding((14 * dp).toInt(), (8 * dp).toInt(), (14 * dp).toInt(), (8 * dp).toInt())
            background = GradientDrawable().apply {
                cornerRadius = 20 * dp
                colors = intArrayOf(0xEE3A3A3E.toInt(), 0xEE111113.toInt())
                setStroke((1.5f * dp).toInt(), 0xFF9A9A9E.toInt())
            }
            setOnClickListener {
                plegado = false; actualizar()
            }
        }
        pastilla = p
        wm.addView(p, parametros(WindowManager.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.END).apply {
            x = (12 * dp).toInt(); y = (12 * dp).toInt()
        })
    }

    private fun quitarVistas() {
        deck?.let { try { wm.removeView(it.vista) } catch (_: Exception) {} }
        pastilla?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        deck = null; pastilla = null
    }

    private fun parametros(ancho: Int, gravedad: Int) = WindowManager.LayoutParams(
        ancho, WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
        PixelFormat.TRANSLUCENT,
    ).apply { gravity = gravedad }

    /** Decide si el teclado fijo se ve, se pliega o se esconde según lo que hay en pantalla. */
    fun actualizar() {
        val prefs = Preferencias(this)
        if (prefs.alturaPct != alturaUsada) {
            quitarVistas(); crearVistas()
        }
        val d = deck ?: return
        d.teclado.recargar(); d.barra.recargar()
        val ventanas = try { windows } catch (_: Exception) { emptyList<AccessibilityWindowInfo>() }
        val tecladoSistema = ventanas.any { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }
        val divisor = ventanas.any { it.type == AccessibilityWindowInfo.TYPE_SPLIT_SCREEN_DIVIDER }
        val paquete = ventanas.firstOrNull { it.type == AccessibilityWindowInfo.TYPE_APPLICATION && it.isActive }
            ?.root?.packageName?.toString()
        val bloqueado = getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true
        if (!divisor && !armando) { divisorAjustado = false; intentosDivisor = 0 }
        // Teclas apagado: deshacer la división (si la hay) y no mostrar nada.
        if (!prefs.activo) {
            if (!salidaHorizontal && divisor && DeckActivity.visible && !armando) {
                salidaHorizontal = true; deshacerDivision()   // una sola vez (alternarla otra vez la reactivaría)
            }
            d.vista.visibility = View.GONE
            pastilla?.visibility = View.GONE
            ponerTapaDivisor(false)
            navegador?.olvidar()
            return
        }
        // En horizontal la pantalla dividida va lado a lado y el teclado fijo no cabe: Teclas se aparta
        // (deshace la división y quita la capa); al volver a vertical todo regresa solo.
        if (horizontal()) {
            // Una sola vez: alternar la división otra vez la volvería a activar.
            if (!salidaHorizontal && divisor && DeckActivity.visible && !armando) {
                salidaHorizontal = true; deshacerDivision()
            }
            d.vista.visibility = View.GONE
            pastilla?.visibility = View.GONE
            ponerTapaDivisor(false)
            navegador?.olvidar()
            return
        }
        salidaHorizontal = false
        val dividido = prefs.modoDividido && !bloqueado && gestionarDividido(paquete, divisor)
        // En modo dividido el teclado que se ve es SIEMPRE esta capa, en el mismo sitio que en el inicio:
        // la división y el ajuste del divisor ocurren por debajo, y la mitad de abajo es solo un hueco.
        // Un aviso del sistema ("no responde", permisos…) nunca debe quedar debajo del teclado.
        val aviso = ventanas.any { it.type == AccessibilityWindowInfo.TYPE_SYSTEM && it.isFocused }
        val enRecientes = paquete != null && paquete != packageName && paquete in lanzadores
        val esconder = bloqueado || aviso || enRecientes ||
            (!dividido && (tecladoSistema || pa.jorshua.teclas.AjustesActivity.delante))
        d.vista.visibility = if (esconder || plegado) View.GONE else View.VISIBLE
        navegador?.tapadoAbajo = if (d.vista.visibility == View.VISIBLE) d.vista.height.takeIf { it > 0 }
            ?: Deck.alto(this, alturaUsada) else 0
        if (esconder && !dividido) navegador?.olvidar()
        pastilla?.visibility = if (!esconder && plegado) View.VISIBLE else View.GONE
        ponerTapaDivisor(dividido && divisor)
        // Recién dividido con un campo ya enfocado (p. ej. un chat): preparar la mayúscula.
        if (dividido && divisor && DeckActivity.visible && campoActual == null) campoDividido()
    }

    // La línea azul y los tres puntos del divisor de Samsung, tapados con el color del teclado:
    // así el final se ve igual que el inicio (y nadie arrastra el divisor sin querer).
    private var tapa: View? = null

    private fun ponerTapaDivisor(mostrar: Boolean) {
        if (!mostrar || arribaTeclado < 0) { tapa?.visibility = View.GONE; return }
        // Desde el borde de arriba del divisor hasta donde empieza el teclado (la capa), sin taparlo:
        // el borde azul de Samsung queda debajo de la capa del teclado.
        val arribaCapa = resources.displayMetrics.heightPixels - Deck.alto(this, Preferencias(this).alturaPct)
        val alto = arribaCapa - (arribaTeclado - 22)
        if (alto <= 0) { tapa?.visibility = View.GONE; return }
        val piel = pa.jorshua.teclas.teclado.Pieles.actual(this)
        val v = tapa ?: View(this).also {
            tapa = it
            wm.addView(it, WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, alto,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                // NOT_TOUCHABLE: solo tapa a la vista; los toques (y nuestros arrastres) pasan al divisor.
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.OPAQUE,
            ).apply { gravity = Gravity.TOP })
        }
        v.setBackgroundColor(piel.fondoArriba)
        val lp = v.layoutParams as WindowManager.LayoutParams
        // Pegada al borde de arriba del teclado, cubriendo el divisor.
        val y = arribaTeclado - 22
        if (lp.y != y || lp.height != alto) { lp.y = y; lp.height = alto; wm.updateViewLayout(v, lp) }
        v.visibility = View.VISIBLE
    }

    // ---- Modo dividido ------------------------------------------------------------------------

    private var armando = false
    private val noDivisibles = HashSet<String>()

    private val lanzadores: Set<String> by lazy {
        packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)
            .map { it.activityInfo.packageName }.toSet() + "com.android.systemui"
    }

    private fun hayDivisor() = try {
        windows.any { it.type == AccessibilityWindowInfo.TYPE_SPLIT_SCREEN_DIVIDER }
    } catch (_: Exception) { false }

    /**
     * Deja la app arriba y el teclado ([DeckActivity]) abajo en pantalla dividida. Devuelve true si el
     * teclado ya está (o se está poniendo) abajo, para esconder la capa fija; false si esta app va con
     * la capa fija (inicio, sistema, apps que no aceptan dividirse).
     */
    private fun gestionarDividido(paquete: String?, divisor: Boolean): Boolean {
        if (armando) return true
        if (paquete == null) return divisor && DeckActivity.visible
        // La ventana activa es el hueco de abajo (es nuestro y acepta el foco): seguimos divididos.
        if (paquete == packageName && divisor && DeckActivity.visible && inicio == null) return true
        if (paquete == packageName) {
            // Se pulsó Inicio con la pantalla dividida: deshacerla; el inicio ya trae su teclado.
            if (divisor) salirDividido(irAlInicio = true)
            return false
        }
        // Recientes / otro lanzador: no tocar la división ni plegar el teclado (al volver a la app
        // tiene que seguir igual); la capa solo se aparta mientras se ven las tarjetas.
        if (paquete in lanzadores) return false
        if (paquete in noDivisibles) return false
        if (divisor) {
            if (DeckActivity.visible && !tecladoArriba()) return true
            // El teclado quedó arriba o desapareció (se abrió otra app): deshacer y volver a armar.
            armando = true
            performGlobalAction(GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN)
            manejador.postDelayed({ armando = false; actualizar() }, 700)
            return true
        }
        // App normal a pantalla completa: anclarla arriba; la mitad de abajo queda con el foco, así que
        // el teclado se abre normal (sin LAUNCH_ADJACENT, que lo mandaría arriba, junto a la app).
        appDividida = paquete
        armando = true
        performGlobalAction(GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN)
        manejador.postDelayed({
            try {
                startActivity(Intent(this, DeckActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
            } catch (_: Exception) {}
        }, 550)
        manejador.postDelayed({
            armando = false
            if (!hayDivisor() || !DeckActivity.visible) noDivisibles += paquete   // esta app va con la capa fija
            actualizar()
        }, 1800)
        return true
    }

    private var divisorAjustado = false

    private var intentosDivisor = 0
    private var arribaTeclado = -1
    private var arrastrando = false
    private var ultimoArrastre = 0L

    /**
     * Android divide a la mitad; el teclado del inicio es más bajo. [DeckActivity] avisa dónde quedó
     * en pantalla y aquí se arrastra el divisor hasta que el hueco mida lo mismo que el teclado. Si
     * después Samsung lo mueve (al volver de la multitarea, por ejemplo), se vuelve a ajustar.
     */
    fun ajustarDivisor(arriba: Int, abajo: Int) {
        // En horizontal no hay división (y las posiciones guardadas son de vertical): nunca arrastrar.
        if (horizontal()) return
        arribaTeclado = arriba
        abajoTeclado = abajo
        val objetivo = abajo - Deck.alto(this, Preferencias(this).alturaPct)
        // Unos pocos píxeles de diferencia ya los tapa el teclado: darlo por bueno.
        if (kotlin.math.abs(objetivo - arriba) <= 16) {
            actualizar()   // ya está: recolocar la franja que tapa el divisor
            return
        }
        // Hasta dos arrastres seguidos (el segundo corrige lo que se quede corto el primero); pasado un
        // rato, si el divisor vuelve a estar fuera de sitio, se empieza de nuevo.
        val ahora = android.os.SystemClock.uptimeMillis()
        if (ahora - ultimoArrastre > 2000) intentosDivisor = 0
        if (arrastrando || intentosDivisor >= 2) return
        if (!hayDivisor()) {
            // El divisor todavía no está en pantalla: reintentar enseguida.
            manejador.postDelayed({ ajustarDivisor(arriba, abajo) }, 100); return
        }
        intentosDivisor++
        ultimoArrastre = ahora
        arrastrando = true
        divisorAjustado = true
        val x = resources.displayMetrics.widthPixels / 2f
        val y0 = arriba - 11f   // el centro del divisor (21 px), justo encima del hueco
        // Si el divisor ya está debajo del teclado, "agarrarlo" tocaría el teclado (la rueda, que está
        // en el centro): eso hacía que el teclado pareciera tocarse solo cada pocos segundos. No arrastrar.
        val arribaCapa = resources.displayMetrics.heightPixels - Deck.alto(this, Preferencias(this).alturaPct)
        if (y0 >= arribaCapa - 4) { arrastrando = false; return }
        // Agarrar (120 ms quieto, como un dedo que se apoya) y llevarlo rápido a su sitio.
        val agarre = GestureDescription.StrokeDescription(Path().apply { moveTo(x, y0) }, 0, 120, true)
        val fin = object : GestureResultCallback() {
            override fun onCompleted(g: GestureDescription) {
                manejador.postDelayed({ arrastrando = false }, 250); revisarDivisor()
            }
            override fun onCancelled(g: GestureDescription) { arrastrando = false; revisarDivisor() }
        }
        dispatchGesture(GestureDescription.Builder().addStroke(agarre).build(), object : GestureResultCallback() {
            override fun onCompleted(g: GestureDescription) {
                // Primero un tramo claro (50 px hacia abajo) para que Samsung lo tome como arrastre
                // aunque la corrección sea de pocos píxeles; luego al punto exacto.
                val mover = Path().apply { moveTo(x, y0); lineTo(x, y0 + 50f); lineTo(x, objetivo - 10f) }
                dispatchGesture(GestureDescription.Builder()
                    .addStroke(agarre.continueStroke(mover, 0, 200, false)).build(), fin, null)
            }
            override fun onCancelled(g: GestureDescription) { arrastrando = false; revisarDivisor() }
        }, null)
    }

    private var abajoTeclado = -1

    /**
     * Si el arrastre no llegó (p. ej. lo canceló un toque del dedo a la vez), volver a mirar un poco
     * después con la última posición que avisó el hueco, y reintentar mientras siga fuera de sitio.
     */
    private fun revisarDivisor() {
        manejador.postDelayed({
            if (DeckActivity.visible && hayDivisor() && arribaTeclado >= 0) ajustarDivisor(arribaTeclado, abajoTeclado)
        }, 2100)
    }

    // ---- Pantalla dividida: el foco lo tiene la mitad de abajo, así que se escribe y se va "atrás"
    // sobre la app de arriba por accesibilidad (sin teclado del sistema y sin necesitar su foco).

    private var campoActual: AccessibilityNodeInfo? = null
    /** El campo cambió y aún no escribimos en él (para reconocer textos de ejemplo no declarados). */
    private var campoNuevo = true
    private val pendienteDividido = StringBuilder()

    /** El campo de texto donde se está escribiendo en la app de arriba, si hay uno. */
    private fun campoDividido(): AccessibilityNodeInfo? {
        campoActual?.let { if (it.refresh() && it.isEditable && it.isVisibleToUser) return it }
        val n = raizApp()?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)?.takeIf { it.isEditable }
        if (n != campoActual) {
            campoNuevo = true
            // Campo recién elegido: mayúscula si está vacío o termina una frase.
            n?.let { deck?.teclado?.ponerAutoMayuscula(debeMayuscula(textoReal(it))) }
        }
        campoActual = n
        return n
    }

    private fun debeMayuscula(s: String) = s.isEmpty() || Regex("[.!?¡¿]\\s+$").containsMatchIn(s)

    private fun escribirDividido(texto: String) {
        campoDividido()?.let { n ->
            val actual = textoActual(n)
            // Doble espacio = punto y espacio, como en los teléfonos con teclado.
            val nuevo = if (texto == " " && actual.length >= 2 && actual.last() == ' ' && actual[actual.length - 2].isLetterOrDigit())
                actual.dropLast(1) + ". " else actual + texto
            cambiarTexto(n, nuevo)
            return
        }
        // Sin campo abierto: abrir el campo (o el buscador) de la app y escribir cuando aparezca.
        val primera = pendienteDividido.isEmpty()
        pendienteDividido.append(texto)
        if (!primera) return
        val objetivo = campoEditable() ?: botonBuscar()
        if (objetivo == null) {
            pendienteDividido.clear()
            Toast.makeText(this, "Aquí no hay dónde escribir", Toast.LENGTH_SHORT).show(); return
        }
        pistaAbierta = (objetivo.text ?: objetivo.hintText ?: objetivo.contentDescription)
            ?.toString()?.replace("​", "")?.takeIf { it.isNotBlank() }
        var tocable: AccessibilityNodeInfo? = objetivo
        while (tocable != null && !tocable.isClickable) tocable = tocable.parent
        (tocable ?: objetivo).performAction(AccessibilityNodeInfo.ACTION_CLICK)
        objetivo.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        esperarCampo(10)
    }

    private fun esperarCampo(veces: Int) {
        manejador.postDelayed({
            val n = campoDividido() ?: campoEditable()?.also { it.performAction(AccessibilityNodeInfo.ACTION_FOCUS); campoActual = it }
            when {
                n != null -> { cambiarTexto(n, textoActual(n) + pendienteDividido); pendienteDividido.clear() }
                veces > 0 -> esperarCampo(veces - 1)
                else -> pendienteDividido.clear()
            }
        }, 150)
    }

    /** Pone el texto, deja el cursor al final y decide la mayúscula automática de la siguiente letra. */
    private fun cambiarTexto(n: AccessibilityNodeInfo, s: String) {
        campoNuevo = false
        val clave = n.viewIdResourceName ?: "?"
        if (s.isEmpty()) vaciados += clave else vaciados -= clave
        textoLocal = s; claveLocal = clave; momentoLocal = android.os.SystemClock.uptimeMillis()
        ponerTexto(n, s)
        deck?.teclado?.ponerAutoMayuscula(debeMayuscula(s))
    }

    /**
     * "Atrás" para la app de arriba: pulsa su flecha de atrás / subir si la tiene; si no (está en su
     * pantalla principal), deshace la división y vuelve al inicio.
     */
    fun atrasDividido() {
        campoActual = null
        val raiz = raizApp()
        if (raiz != null) {
            val claves = listOf("navegar hacia arriba", "navigate up", "atrás", "atras", "back", "volver", "regresar")
            val pendientes = ArrayDeque<AccessibilityNodeInfo>().apply { add(raiz) }
            while (pendientes.isNotEmpty()) {
                val n = pendientes.removeFirst()
                for (i in 0 until n.childCount) n.getChild(i)?.let { pendientes.add(it) }
                val desc = n.contentDescription?.toString()?.lowercase()?.trim() ?: continue
                if (!n.isVisibleToUser || claves.none { desc == it || desc.startsWith("$it ") }) continue
                var c: AccessibilityNodeInfo? = n
                while (c != null && !c.isClickable) c = c.parent
                if (c != null && c.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return
            }
        }
        salirDividido(irAlInicio = true)
    }

    private fun tecladoArriba(): Boolean {
        val ventana = try {
            windows.firstOrNull {
                it.type == AccessibilityWindowInfo.TYPE_APPLICATION && !it.isActive &&
                    it.root?.packageName?.toString() == packageName
            }
        } catch (_: Exception) { null } ?: return false
        val r = android.graphics.Rect()
        ventana.getBoundsInScreen(r)
        return r.top < resources.displayMetrics.heightPixels / 3
    }

    /** Tecla roja / mantener menú en modo dividido: deshacer la división (y opcionalmente ir al inicio). */
    fun salirDividido(irAlInicio: Boolean) {
        armando = true
        if (hayDivisor()) performGlobalAction(GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN)
        manejador.postDelayed({
            if (irAlInicio) performGlobalAction(GLOBAL_ACTION_HOME) else plegado = true
            manejador.postDelayed({ armando = false; actualizar() }, 500)
        }, 450)
    }

    // ---- Teclas: sin campo activo, abren el campo de la app y escriben ahí --------------------

    /**
     * Si el teclado Teclas ya está escribiendo, la tecla va directo a él. Si no, se toca el campo
     * (o el botón de buscar) y la letra espera en [TecladoIme.pendiente] hasta que el teclado se abra.
     * Si quien se abre es otro teclado, se escribe por accesibilidad como respaldo.
     */
    override fun alEscribir(texto: String, tecla: Tecla?) {
        inicio?.let { it.alEscribir(texto, tecla); return }
        if (DeckActivity.visible) { escribirDividido(texto); return }
        val ime = TecladoIme.activo
        if (ime != null && ime.escribiendo) {
            ime.alEscribir(texto, tecla); return
        }
        val pendiente = TecladoIme.pendiente
        val primera = pendiente.isEmpty()
        pendiente.append(texto)
        if (!primera) return   // el campo se está abriendo: mantener el orden
        val objetivo = campoEditable() ?: botonBuscar()
        if (objetivo == null) {
            pendiente.clear()
            Toast.makeText(this, "Aquí no hay dónde escribir", Toast.LENGTH_SHORT).show(); return
        }
        // Toque real: en pantalla dividida así la app de arriba recibe el foco y el teclado puede escribir.
        navegador?.pulsarNodo(objetivo) ?: objetivo.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        respaldo(8)
    }

    /**
     * La app que se controla: en pantalla dividida, la de arriba aunque el foco lo tenga el teclado;
     * si no, la ventana activa.
     */
    fun raizApp(): AccessibilityNodeInfo? {
        val apps = try {
            windows.filter {
                it.type == AccessibilityWindowInfo.TYPE_APPLICATION && it.root?.packageName?.toString() != packageName
            }
        } catch (_: Exception) { emptyList() }
        val elegida = apps.firstOrNull { it.isActive } ?: apps.minByOrNull {
            android.graphics.Rect().also { r -> it.getBoundsInScreen(r) }.top
        }
        return elegida?.root ?: rootInActiveWindow
    }

    private val manejador = android.os.Handler(android.os.Looper.getMainLooper())

    private fun respaldo(veces: Int) {
        manejador.postDelayed({
            val pendiente = TecladoIme.pendiente
            if (pendiente.isEmpty()) return@postDelayed   // ya lo escribió el teclado Teclas
            if (veces > 0) { respaldo(veces - 1); return@postDelayed }
            val n = raizApp()?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)?.takeIf { it.isEditable }
            if (n != null) ponerTexto(n, textoReal(n) + pendiente)
            pendiente.clear()
        }, 150)
    }

    /** Algunas apps (WhatsApp) devuelven el texto de ejemplo, con un carácter invisible delante. */
    private fun textoReal(nodo: AccessibilityNodeInfo): String {
        val t = nodo.text?.toString()?.replace("\u200B", "") ?: return ""
        if (t.isEmpty() || nodo.isShowingHintText) return ""
        val pista = nodo.hintText?.toString()?.replace("\u200B", "")?.trim()?.takeIf { it.isNotEmpty() }
        val clave = nodo.viewIdResourceName ?: "?"
        if (pista != null) pistasConocidas[clave] = pista
        val limpio = t.trim()
        if (limpio == pista || limpio == pistaAbierta) return ""
        // Vac\u00EDo, WhatsApp devuelve el texto de ejemplo como texto, sin declararlo como tal y sin cursor
        // (-1). Se reconoce por el ejemplo que s\u00ED declar\u00F3 antes, o porque lo acabamos de vaciar nosotros.
        val sinCursor = nodo.textSelectionStart < 0 && nodo.textSelectionEnd < 0
        if (pista == null && limpio == pistasConocidas[clave]) return ""
        // Texto real siempre trae la posición del cursor; el de ejemplo viene "sin cursor".
        if (sinCursor && pista == null && (campoNuevo || clave in vaciados)) return ""
        // Texto de ejemplo no declarado en un campo reci\u00E9n abierto: cursor en 0 aunque "haya" texto
        // (nosotros siempre dejamos el cursor al final de lo que escribimos).
        if (pista == null && nodo.textSelectionStart == 0 && nodo.textSelectionEnd == 0 && campoNuevo) return ""
        return t
    }

    /** \u00DAltimo texto de ejemplo que declar\u00F3 cada campo (por su id: el nodo cambia, el id no). */
    private val pistasConocidas = HashMap<String, String>()
    /** Campos que dejamos vac\u00EDos (para reconocer el texto de ejemplo que reaparece). */
    private val vaciados = HashSet<String>()

    /** El texto de ejemplo del buscador que abrimos nosotros (WhatsApp lo devuelve como si fuera texto). */
    private var pistaAbierta: String? = null

    /** Un botón o barra que dice "buscar" / "search" (texto, descripción o id). */
    private fun botonBuscar(): AccessibilityNodeInfo? {
        val raiz = raizApp() ?: return null
        val pendientes = ArrayDeque<AccessibilityNodeInfo>().apply { add(raiz) }
        while (pendientes.isNotEmpty()) {
            val n = pendientes.removeFirst()
            val pistas = listOfNotNull(n.text, n.contentDescription, n.viewIdResourceName, n.hintText)
                .joinToString(" ").lowercase()
            if (n.isVisibleToUser && ("busc" in pistas || "search" in pistas)) {
                var c: AccessibilityNodeInfo? = n
                while (c != null && !c.isClickable) c = c.parent
                if (c != null) return c
            }
            for (i in 0 until n.childCount) n.getChild(i)?.let { pendientes.add(it) }
        }
        return null
    }

    override fun alBorrar() {
        inicio?.let { it.alBorrar(); return }
        if (DeckActivity.visible) {
            campoDividido()?.let { n -> val t = textoActual(n); if (t.isNotEmpty()) cambiarTexto(n, t.dropLast(1)) }
            return
        }
        TecladoIme.activo?.takeIf { it.escribiendo }?.let { it.alBorrar(); return }
        val nodo = raizApp()?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)?.takeIf { it.isEditable } ?: return
        val actual = textoReal(nodo)
        if (actual.isNotEmpty()) ponerTexto(nodo, actual.dropLast(1))
    }

    override fun alEnter() {
        inicio?.let { it.alEnter(); return }
        if (DeckActivity.visible) { enterDividido(); return }
        TecladoIme.activo?.takeIf { it.escribiendo }?.alEnter()
    }

    /**
     * Enter en la app de arriba: en un chat, pulsar su botón Enviar (como en los teléfonos con
     * teclado); si no hay, salto de línea en campos de varias líneas o la acción del campo (buscar, ir…).
     */
    private fun enterDividido() {
        val campo = campoDividido()
        // Sin campo de texto: Enter pulsa lo que esté seleccionado con la rueda (el recuadro azul).
        if (campo == null) { navegador?.pulsar(); return }
        // Campo vacío: nada que enviar (en un chat, el botón de enviar ni siquiera está).
        if (textoActual(campo).isBlank()) return
        val raiz = raizApp()
        if (raiz != null) {
            val pendientes = ArrayDeque<AccessibilityNodeInfo>().apply { add(raiz) }
            while (pendientes.isNotEmpty()) {
                val n = pendientes.removeFirst()
                for (i in 0 until n.childCount) n.getChild(i)?.let { pendientes.add(it) }
                if (!n.isVisibleToUser) continue
                val desc = n.contentDescription?.toString()?.lowercase()?.trim() ?: ""
                val id = n.viewIdResourceName?.lowercase() ?: ""
                if (desc == "enviar" || desc == "send" || id.endsWith("/send")) {
                    var c: AccessibilityNodeInfo? = n
                    while (c != null && !c.isClickable) c = c.parent
                    if (c != null && c.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                        campoNuevo = true; textoLocal = null
                        deck?.teclado?.ponerAutoMayuscula(true); return
                    }
                }
            }
        }
        // Sin botón Enviar: la acción del campo (buscar, ir, siguiente…).
        campo.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id)
    }

    override fun alEnterConShift() {
        inicio?.let { it.alEnterConShift(); return }
        if (DeckActivity.visible) { campoDividido()?.let { cambiarTexto(it, textoActual(it) + "\n") }; return }
        TecladoIme.activo?.takeIf { it.escribiendo }?.let { it.alEscribir("\n", null); return }
        alEnter()
    }

    // Lo último que escribimos en el campo: al teclear rápido, la app todavía no se ha actualizado y
    // leer su texto daría uno viejo (letras perdidas o mezcladas). Durante 1,5 s manda lo nuestro.
    private var textoLocal: String? = null
    private var claveLocal: String? = null
    private var momentoLocal = 0L

    private fun textoActual(n: AccessibilityNodeInfo): String {
        val reciente = android.os.SystemClock.uptimeMillis() - momentoLocal < 1500
        val local = textoLocal
        if (local != null && reciente && claveLocal == (n.viewIdResourceName ?: "?")) return local
        return textoReal(n)
    }

    private fun ponerTexto(nodo: AccessibilityNodeInfo, s: String) {
        nodo.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, s)
        })
        nodo.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, Bundle().apply {
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, s.length)
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, s.length)
        })
    }

    /** El campo con foco o, si no hay, el primer campo editable visible. */
    private fun campoEditable(): AccessibilityNodeInfo? {
        val raiz = raizApp() ?: return null
        raiz.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)?.takeIf { it.isEditable }?.let { return it }
        val pendientes = ArrayDeque<AccessibilityNodeInfo>().apply { add(raiz) }
        while (pendientes.isNotEmpty()) {
            val n = pendientes.removeFirst()
            if (n.isEditable && n.isVisibleToUser) return n
            for (i in 0 until n.childCount) n.getChild(i)?.let { pendientes.add(it) }
        }
        return null
    }

    override fun alPulsarLargo(letra: Char): Boolean = inicio?.alPulsarLargo(letra) ?: false

    override fun alReemplazar(texto: String, tecla: Tecla?) {
        inicio?.let { it.alReemplazar(texto, tecla); return }
        if (DeckActivity.visible) {
            campoDividido()?.let { n -> cambiarTexto(n, textoActual(n).dropLast(1) + texto) }
            return
        }
        TecladoIme.activo?.takeIf { it.escribiendo }?.let { it.alReemplazar(texto, tecla); return }
        alBorrar(); alEscribir(texto, tecla)
    }

    // ---- Fila de navegación = botones de Android ----------------------------------------------

    override fun llamar() {
        inicio?.let { it.llamar(); return }
        abrir(Intent(Intent.ACTION_DIAL))
    }

    override fun menu() {
        inicio?.let { it.menu(); return }
        performGlobalAction(GLOBAL_ACTION_RECENTS)
    }

    /** Mantener menú pliega el teclado para ver la app completa (en dividido, deshace la división). */
    override fun menuLargo() {
        inicio?.let { it.menuLargo(); return }
        if (DeckActivity.visible) { salirDividido(irAlInicio = false); return }
        plegado = true; actualizar()
    }

    override fun atras() {
        inicio?.let { it.atras(); return }
        if (DeckActivity.visible) { atrasDividido(); return }
        performGlobalAction(GLOBAL_ACTION_BACK)
    }

    override fun colgar() {
        inicio?.let { it.colgar(); return }
        if (Preferencias(this).modoDividido) salirDividido(irAlInicio = true)
        else performGlobalAction(GLOBAL_ACTION_HOME)
    }

    /** Pulsar la rueda: abre lo seleccionado; sin selección, selecciona lo primero. */
    override fun pulsarTrackpad() {
        inicio?.let { it.pulsarTrackpad(); return }
        val n = navegador ?: return
        if (!n.pulsar()) n.mover(KeyEvent.KEYCODE_DPAD_DOWN)
    }

    override fun mover(tecla: Int) {
        inicio?.let { it.mover(tecla); return }
        val ime = TecladoIme.activo
        if (ime != null && ime.escribiendo) ime.mover(tecla) else navegador?.mover(tecla)
    }

    private fun abrir(i: Intent) {
        try { startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) {}
    }
}
