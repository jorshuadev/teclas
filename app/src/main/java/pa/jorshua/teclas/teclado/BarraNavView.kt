package pa.jorshua.teclas.teclado

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * La fila de navegación de los teléfonos con teclado: llamar (verde), menú, trackpad óptico,
 * atrás y colgar (rojo). El trackpad convierte el deslizamiento del dedo en flechas.
 */
class BarraNavView(ctx: Context) : View(ctx) {

    interface Acciones {
        fun llamar()
        fun menu()
        fun menuLargo()
        fun atras()
        fun colgar()
        /** Recibe KeyEvent.KEYCODE_DPAD_UP/DOWN/LEFT/RIGHT. */
        fun mover(tecla: Int)
        fun pulsarTrackpad()
    }

    private enum class Zona { LLAMAR, MENU, TRACKPAD, ATRAS, COLGAR }

    var acciones: Acciones? = null

    private val retro = Retro(ctx)
    private val dp = resources.displayMetrics.density
    private val zonas = LinkedHashMap<Zona, RectF>()
    private val trazo = Path()
    private val tmp = RectF()

    private val fondo = Paint()
    private val traste = Paint()
    private val boton = Paint(Paint.ANTI_ALIAS_FLAG)
    private val hundido = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pad = Paint(Paint.ANTI_ALIAS_FLAG)
    private val aro = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val reflejo = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x22FFFFFF }
    private val icono = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }

    private var zonaActiva: Zona? = null
    private var ultX = 0f
    private var ultY = 0f
    private var acumX = 0f
    private var acumY = 0f
    private var recorrido = 0f
    private var inicio = 0L
    private var largoHecho = false
    private val menuLargo = Runnable {
        largoHecho = true; retro.largo(); acciones?.menuLargo()
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        zonas.clear()
        val pesos = listOf(Zona.LLAMAR to 1f, Zona.MENU to 1f, Zona.TRACKPAD to 1.25f, Zona.ATRAS to 1f, Zona.COLGAR to 1f)
        val padX = 6 * dp
        val gap = 6 * dp
        val util = w - 2 * padX - gap * (pesos.size - 1)
        val total = pesos.sumOf { it.second.toDouble() }.toFloat()
        var x = padX
        for ((z, p) in pesos) {
            val ancho = util * p / total
            // Botones de 64 dp como máximo, centrados: si la fila se estira, no se deforman.
            val alto = minOf(h - 13 * dp, 64 * dp)
            val arriba = (h - 7 * dp + 6 * dp - alto) / 2
            zonas[z] = RectF(x, arriba, x + ancho, arriba + alto)
            x += ancho + gap
        }
        colorear()
        val t = zonas[Zona.TRACKPAD]!!
        pad.shader = RadialGradient(t.centerX(), t.centerY() - t.height() * 0.15f, t.width() * 0.6f,
            0xFF4A4A52.toInt(), 0xFF0C0C0F.toInt(), Shader.TileMode.CLAMP)
        aro.shader = LinearGradient(0f, t.top, 0f, t.bottom,
            0xFFE6E6EA.toInt(), 0xFF5A5A5E.toInt(), Shader.TileMode.CLAMP)
        aro.strokeWidth = 2.2f * dp
    }

    /** Colores de la piel: cuerpo, botones y traste inferior. */
    private fun colorear() {
        val h = height.toFloat()
        fondo.shader = LinearGradient(0f, 0f, 0f, h, piel.fondoArriba, piel.fondoAbajo, Shader.TileMode.CLAMP)
        piel.traste?.let {
            traste.shader = LinearGradient(0f, 0f, 0f, 3.5f * dp, intArrayOf(it[0], it[1], it[2], it[3]),
                floatArrayOf(0f, 0.35f, 0.65f, 1f), Shader.TileMode.CLAMP)
        }
        val r = zonas[Zona.LLAMAR] ?: return
        boton.shader = LinearGradient(0f, r.top, 0f, r.bottom, piel.tecla, floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        hundido.shader = LinearGradient(0f, r.top, 0f, r.bottom, piel.hundida[0], piel.hundida[1], Shader.TileMode.CLAMP)
    }

    private var piel = Pieles.actual(ctx)

    /** Vuelve a leer la piel de los ajustes. */
    fun recargar() = mostrar(Pieles.actual(context))

    fun mostrar(p: Piel) {
        if (p == piel) return
        piel = p
        colorear(); invalidate()
    }

    override fun onDraw(c: Canvas) {
        c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), fondo)
        if (piel.traste != null) {
            c.save(); c.translate(0f, height - 3.5f * dp)
            c.drawRect(0f, 0f, width.toFloat(), 3.5f * dp, traste)
            c.restore()
        }
        for ((z, r) in zonas) {
            val activa = z == zonaActiva
            if (z == Zona.TRACKPAD) {
                val rad = r.height() * 0.32f
                c.drawRoundRect(r, rad, rad, pad)
                c.drawRoundRect(r, rad, rad, aro)
                tmp.set(r.left + r.width() * 0.2f, r.top + r.height() * 0.12f, r.right - r.width() * 0.2f, r.top + r.height() * 0.42f)
                c.drawOval(tmp, reflejo)
                if (activa) {
                    reflejo.color = 0x334AA3FF; c.drawRoundRect(r, rad, rad, reflejo); reflejo.color = 0x22FFFFFF
                }
                continue
            }
            val rad = piel.radio * dp
            c.drawRoundRect(r, rad, rad, if (activa) hundido else boton)
            val cx = r.centerX()
            val cy = r.centerY() + if (activa) 1.5f * dp else 0f
            val s = minOf(r.height() * 0.24f, r.width() * 0.17f)
            when (z) {
                Zona.LLAMAR -> auricular(c, cx, cy, s, VERDE, -20f)
                Zona.COLGAR -> auricular(c, cx, cy, s, ROJO, 0f)
                Zona.MENU -> menu(c, cx, cy, s)
                Zona.ATRAS -> atras(c, cx, cy, s)
                else -> {}
            }
        }
    }

    /** Auricular de teléfono: arco grueso con la pieza del oído y la de la boca. */
    private fun auricular(c: Canvas, cx: Float, cy: Float, s: Float, color: Int, giro: Float) {
        c.save(); c.rotate(giro, cx, cy)
        icono.color = color
        icono.style = Paint.Style.STROKE
        icono.strokeWidth = s * 0.42f
        val centroY = cy + s * 0.55f
        tmp.set(cx - s * 1.15f, centroY - s * 1.15f, cx + s * 1.15f, centroY + s * 1.15f)
        c.drawArc(tmp, 205f, 130f, false, icono)
        icono.style = Paint.Style.FILL
        for (ang in listOf(205.0, 335.0)) {
            val rad = Math.toRadians(ang)
            val ex = cx + (s * 1.15f * cos(rad)).toFloat()
            val ey = centroY + (s * 1.15f * sin(rad)).toFloat()
            tmp.set(ex - s * 0.38f, ey - s * 0.1f, ex + s * 0.38f, ey + s * 0.5f)
            c.drawRoundRect(tmp, s * 0.18f, s * 0.18f, icono)
        }
        c.restore()
    }

    private fun menu(c: Canvas, cx: Float, cy: Float, s: Float) {
        icono.color = piel.letra; icono.style = Paint.Style.STROKE; icono.strokeWidth = s * 0.26f
        for (i in -1..1) c.drawLine(cx - s * 0.85f, cy + i * s * 0.55f, cx + s * 0.85f, cy + i * s * 0.55f, icono)
    }

    private fun atras(c: Canvas, cx: Float, cy: Float, s: Float) {
        icono.color = piel.letra; icono.style = Paint.Style.STROKE; icono.strokeWidth = s * 0.26f
        trazo.reset()
        trazo.moveTo(cx - 0.9f * s, cy - 0.3f * s)
        trazo.lineTo(cx + 0.35f * s, cy - 0.3f * s)
        trazo.cubicTo(cx + 1.05f * s, cy - 0.3f * s, cx + 1.05f * s, cy + 0.75f * s, cx + 0.35f * s, cy + 0.75f * s)
        trazo.lineTo(cx - 0.35f * s, cy + 0.75f * s)
        trazo.moveTo(cx - 0.45f * s, cy - 0.75f * s)
        trazo.lineTo(cx - 0.9f * s, cy - 0.3f * s)
        trazo.lineTo(cx - 0.45f * s, cy + 0.15f * s)
        c.drawPath(trazo, icono)
    }

    // ---- Toques -------------------------------------------------------------------------------

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val z = zonas.entries.minByOrNull { (_, r) ->
                    maxOf(r.left - e.x, 0f, e.x - r.right)
                }?.key ?: return true
                zonaActiva = z
                ultX = e.x; ultY = e.y; acumX = 0f; acumY = 0f; recorrido = 0f
                inicio = e.eventTime; largoHecho = false
                if (z == Zona.TRACKPAD) retro.suave() else retro.tecla()
                if (z == Zona.MENU) postDelayed(menuLargo, 500)
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> if (zonaActiva == Zona.TRACKPAD) {
                val dx = e.x - ultX
                val dy = e.y - ultY
                ultX = e.x; ultY = e.y
                acumX += dx; acumY += dy; recorrido += abs(dx) + abs(dy)
                // ~34 dp de recorrido por paso: lo bastante lento para elegir de uno en uno.
                val paso = 34 * dp
                while (abs(acumX) >= paso) {
                    acciones?.mover(if (acumX > 0) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT)
                    acumX -= if (acumX > 0) paso else -paso
                    retro.suave()
                }
                while (abs(acumY) >= paso) {
                    acciones?.mover(if (acumY > 0) KeyEvent.KEYCODE_DPAD_DOWN else KeyEvent.KEYCODE_DPAD_UP)
                    acumY -= if (acumY > 0) paso else -paso
                    retro.suave()
                }
            }
            MotionEvent.ACTION_UP -> {
                removeCallbacks(menuLargo)
                val a = acciones
                when (zonaActiva) {
                    Zona.TRACKPAD -> if (recorrido < 10 * dp && e.eventTime - inicio < 350) {
                        retro.tecla(); a?.pulsarTrackpad()
                    }
                    Zona.LLAMAR -> a?.llamar()
                    Zona.MENU -> if (!largoHecho) a?.menu()
                    Zona.ATRAS -> a?.atras()
                    Zona.COLGAR -> a?.colgar()
                    null -> {}
                }
                zonaActiva = null
                invalidate()
            }
            MotionEvent.ACTION_CANCEL -> {
                removeCallbacks(menuLargo); zonaActiva = null; invalidate()
            }
        }
        return true
    }

    companion object {
        const val VERDE = 0xFF3FCB5A.toInt()
        const val ROJO = 0xFFE5483B.toInt()
        const val BLANCO = 0xFFE8E8EA.toInt()
    }
}
