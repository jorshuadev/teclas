package pa.jorshua.teclas.fijo

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import kotlin.math.abs

/**
 * La rueda del centro como en los teléfonos con teclado: mueve un recuadro azul de un elemento
 * tocable al siguiente (en la dirección del dedo) en cualquier app, hace scroll sola al llegar
 * al borde y al pulsarla abre lo seleccionado.
 */
class Navegador(
    private val servicio: AccessibilityService,
    private val wm: WindowManager,
    /** La raíz de la app que se controla (en pantalla dividida, la de arriba aunque no tenga el foco). */
    private val raiz: () -> AccessibilityNodeInfo?,
) {

    /** Alto (en px desde abajo) que tapa el teclado fijo; lo de ahí abajo no se puede seleccionar. */
    var tapadoAbajo = 0

    private var sel: AccessibilityNodeInfo? = null
    private val marco = Marco(servicio)
    private val manejador = Handler(Looper.getMainLooper())
    private val dp = servicio.resources.displayMetrics.density

    init {
        wm.addView(marco, WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ))
    }

    fun destruir() {
        try { wm.removeView(marco) } catch (_: Exception) {}
    }

    /** Cambió de pantalla o de app: la selección anterior ya no vale. */
    fun olvidar() {
        sel = null; marco.ocultar()
    }

    /** Tras un scroll de la app el elemento se movió: redibujar el recuadro donde está ahora. */
    fun reubicar() {
        val s = sel ?: return
        if (!s.refresh() || !s.isVisibleToUser) { olvidar(); return }
        val r = limites(s)
        if (r.bottom <= 0 || r.top >= area().bottom) marco.ocultar() else marco.mostrar(r)
    }

    private var ultimoPaso = 0L

    fun mover(tecla: Int) {
        // Como mucho un salto cada 120 ms: un deslizamiento rápido no se come varios elementos.
        val ahora = android.os.SystemClock.uptimeMillis()
        if (ahora - ultimoPaso < 120) return
        ultimoPaso = ahora
        val visibles = candidatos()
        val actual = sel?.takeIf { it.refresh() && it.isVisibleToUser }
        if (actual == null) {
            // Primera vez: arriba del todo (o abajo del todo si se sube).
            val primero = if (tecla == KeyEvent.KEYCODE_DPAD_UP || tecla == KeyEvent.KEYCODE_DPAD_LEFT)
                visibles.maxByOrNull { limites(it).top } else visibles.minByOrNull { limites(it).top * 10000 + limites(it).left }
            primero?.let { elegir(it) }
            return
        }
        val desde = limites(actual)
        val siguiente = mejorEnDireccion(desde, visibles.filter { !mismo(it, actual) }, tecla)
        if (siguiente != null) { elegir(siguiente); return }
        // Nada más en esa dirección a la vista: deslizar la lista y volver a buscar.
        val adelante = tecla == KeyEvent.KEYCODE_DPAD_DOWN || tecla == KeyEvent.KEYCODE_DPAD_RIGHT
        if (desplazar(actual, adelante)) {
            manejador.postDelayed({
                actual.refresh()
                val r = limites(actual)
                mejorEnDireccion(r, candidatos().filter { !mismo(it, actual) }, tecla)?.let { elegir(it) } ?: reubicar()
            }, 280)
        }
    }

    /** Pulsar la rueda: abre lo seleccionado. Devuelve false si no hay nada seleccionado. */
    fun pulsar(): Boolean {
        val s = sel?.takeIf { it.refresh() } ?: return false
        pulsarNodo(s)
        marco.destello()
        return true
    }

    /**
     * Toca un elemento como lo haría el dedo, para que su ventana reciba el foco (en pantalla
     * dividida la app de arriba no lo tiene). Si el elemento está tapado por el teclado, ACTION_CLICK.
     */
    fun pulsarNodo(n: AccessibilityNodeInfo) {
        val r = limites(n)
        if (area().contains(r.centerX(), r.centerY()) && tocar(r)) return
        var objetivo: AccessibilityNodeInfo? = n
        while (objetivo != null && !objetivo.isClickable) objetivo = objetivo.parent
        objetivo?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    // ---- Interno -----------------------------------------------------------------------------

    private fun elegir(n: AccessibilityNodeInfo) {
        sel = n
        val r = limites(n)
        // Si queda medio tapado por el teclado, subir la lista un poco para que se vea entero.
        if (r.bottom > area().bottom) n.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.id)
        marco.mostrar(limites(n))
    }

    private fun area(): Rect {
        val m = servicio.resources.displayMetrics
        return Rect(0, 0, m.widthPixels, m.heightPixels - tapadoAbajo)
    }

    private fun limites(n: AccessibilityNodeInfo) = Rect().also { n.getBoundsInScreen(it) }

    private fun mismo(a: AccessibilityNodeInfo, b: AccessibilityNodeInfo) = limites(a) == limites(b)

    /** Todo lo tocable que se ve (botones, chats, enlaces, campos), sin contenedores enormes ni duplicados. */
    private fun candidatos(): List<AccessibilityNodeInfo> {
        val inicio = raiz() ?: return emptyList()
        val zona = area()
        val pantalla = zona.width().toLong() * zona.height()
        val salida = ArrayList<AccessibilityNodeInfo>()
        val vistos = HashSet<Rect>()
        val pendientes = ArrayDeque<AccessibilityNodeInfo>().apply { add(inicio) }
        while (pendientes.isNotEmpty()) {
            val n = pendientes.removeFirst()
            for (i in 0 until n.childCount) n.getChild(i)?.let { pendientes.add(it) }
            if (!n.isVisibleToUser || !n.isEnabled) continue
            if (!(n.isClickable || n.isCheckable || n.isEditable)) continue
            val r = limites(n)
            if (r.width() < 8 * dp || r.height() < 8 * dp) continue
            if (r.width().toLong() * r.height() > pantalla * 0.6) continue
            val centroY = r.centerY()
            if (centroY < zona.top || centroY > zona.bottom) continue
            if (vistos.add(r)) salida += n
        }
        return salida
    }

    /** El elemento más cercano en la dirección pedida, premiando que esté alineado. */
    private fun mejorEnDireccion(desde: Rect, nodos: List<AccessibilityNodeInfo>, tecla: Int): AccessibilityNodeInfo? {
        val margen = (4 * dp).toInt()
        return nodos.map { it to limites(it) }.filter { (_, r) ->
            when (tecla) {
                KeyEvent.KEYCODE_DPAD_DOWN -> r.centerY() > desde.centerY() + margen && r.top >= desde.top + margen
                KeyEvent.KEYCODE_DPAD_UP -> r.centerY() < desde.centerY() - margen && r.bottom <= desde.bottom - margen
                KeyEvent.KEYCODE_DPAD_RIGHT -> r.centerX() > desde.centerX() + margen && abs(r.centerY() - desde.centerY()) < desde.height()
                else -> r.centerX() < desde.centerX() - margen && abs(r.centerY() - desde.centerY()) < desde.height()
            }
        }.minByOrNull { (_, r) ->
            // Primero la fila (o columna) más cercana; dentro de ella, lo que más se solape con la selección.
            val vertical = tecla == KeyEvent.KEYCODE_DPAD_DOWN || tecla == KeyEvent.KEYCODE_DPAD_UP
            val salto = if (vertical) maxOf(0, maxOf(r.top - desde.bottom, desde.top - r.bottom))
                        else maxOf(0, maxOf(r.left - desde.right, desde.left - r.right))
            val lateral = if (vertical) maxOf(0, maxOf(r.left - desde.right, desde.left - r.right))
                          else maxOf(0, maxOf(r.top - desde.bottom, desde.top - r.bottom))
            val centro = if (vertical) abs(r.centerX() - desde.centerX()) else abs(r.centerY() - desde.centerY())
            salto * 4 + lateral + centro / 8
        }?.first
    }

    private fun desplazar(desde: AccessibilityNodeInfo, adelante: Boolean): Boolean {
        val accion = if (adelante) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        var n: AccessibilityNodeInfo? = desde
        while (n != null) {
            if (n.isScrollable && n.performAction(accion)) return true
            n = n.parent
        }
        // Sin lista que contenga la selección: el primer contenedor deslizable de la pantalla.
        val pendientes = ArrayDeque<AccessibilityNodeInfo>().apply { raiz()?.let { add(it) } }
        while (pendientes.isNotEmpty()) {
            val x = pendientes.removeFirst()
            if (x.isScrollable && x.performAction(accion)) return true
            for (i in 0 until x.childCount) x.getChild(i)?.let { pendientes.add(it) }
        }
        return false
    }

    /** Toque real (gesto): a diferencia de ACTION_CLICK, le da el foco a la ventana tocada. */
    fun tocar(r: Rect): Boolean {
        val p = Path().apply { moveTo(r.exactCenterX(), r.exactCenterY()) }
        return servicio.dispatchGesture(GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(p, 0, 60)).build(), null, null)
    }

    /** El recuadro azul de selección, dibujado encima de la app sin bloquear los toques. */
    private class Marco(ctx: Context) : View(ctx) {
        private val dp = resources.displayMetrics.density
        private val borde = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 3 * dp; color = 0xFF4AA3FF.toInt()
        }
        private val relleno = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x334AA3FF }
        private var rect: RectF? = null
        private val enPantalla = IntArray(2)

        fun mostrar(r: Rect) {
            getLocationOnScreen(enPantalla)
            rect = RectF(r).apply {
                offset(-enPantalla[0].toFloat(), -enPantalla[1].toFloat()); inset(-2 * dp, -2 * dp)
            }
            relleno.alpha = 0x33
            invalidate()
        }

        fun ocultar() {
            rect = null; invalidate()
        }

        /** Destello al pulsar, para que se note que "hizo clic". */
        fun destello() {
            relleno.alpha = 0x88; invalidate()
            postDelayed({ relleno.alpha = 0x33; invalidate() }, 140)
        }

        override fun onDraw(c: Canvas) {
            val r = rect ?: return
            c.drawRoundRect(r, 10 * dp, 10 * dp, relleno)
            c.drawRoundRect(r, 10 * dp, 10 * dp, borde)
        }
    }
}
