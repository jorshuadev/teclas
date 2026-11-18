package pa.jorshua.teclas.teclado

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs

/**
 * El teclado dibujado a mano con la piel elegida: teclas abombadas (o esculpidas), trastes cromados
 * entre filas (rectos o en "sonrisa") y el carácter alt pintado en cada tecla. Escribe al SOLTAR
 * para poder distinguir la pulsación larga. Las teclas de varias letras (compacto, numérico)
 * funcionan por toques sucesivos.
 */
class TecladoView(ctx: Context) : View(ctx) {

    interface Oyente {
        fun alEscribir(texto: String, tecla: Tecla?)
        fun alBorrar()
        fun alEnter()
        /** Devuelve true si se encargó él (p. ej. abrir una app); si no, se escribe la mayúscula. */
        fun alPulsarLargo(letra: Char): Boolean = false
        /** Toques sucesivos: cambiar la última letra escrita por la siguiente de la tecla. */
        fun alReemplazar(texto: String, tecla: Tecla?) {
            alBorrar(); alEscribir(texto, tecla)
        }
        /** Shift + Enter: salto de línea en vez de enviar. */
        fun alEnterConShift() {
            alEscribir("\n", null)
        }
    }

    enum class Modo { NO, UNA, FIJO }

    var oyente: Oyente? = null
    /** false en las vistas previas de Ajustes: se ven pero no escriben. */
    var interactivo = true

    var shift = Modo.NO
        private set
    var alt = Modo.NO
        private set
    var sym = false
        private set
    private var shiftAuto = false

    /** Campos numéricos y de teléfono: alt queda bloqueado para escribir números directo. */
    var altForzado = false
        set(v) {
            field = v
            alt = if (v) Modo.FIJO else Modo.NO
            invalidate()
        }

    fun ponerAutoMayuscula(si: Boolean) {
        if (si && shift == Modo.NO) {
            shift = Modo.UNA; shiftAuto = true; invalidate()
        } else if (!si && shiftAuto && shift == Modo.UNA) {
            shift = Modo.NO; shiftAuto = false; invalidate()
        }
    }

    private var piel = Pieles.actual(ctx)
    private var tipo = TipoTeclado.actual(ctx)
    private var espanol = pa.jorshua.teclas.Preferencias(ctx).espanol

    /** Vuelve a leer piel, tipo de teclado e idioma de los ajustes (al volver de Ajustes). */
    fun recargar() = mostrar(Pieles.actual(context), TipoTeclado.actual(context),
        pa.jorshua.teclas.Preferencias(context).espanol)

    fun mostrar(p: Piel, t: TipoTeclado, es: Boolean = espanol) {
        if (p == piel && t == tipo && es == espanol) return
        piel = p; tipo = t; espanol = es
        if (width > 0) calcular(width, height)
        invalidate()
    }

    private class Caja(val tecla: Tecla, val r: RectF, val normal: Shader, val hundida: Shader, val talla: Shader?)
    private class Pulsacion(val caja: Caja, var consumida: Boolean = false, var tarea: Runnable? = null)

    private val retro = Retro(ctx)
    private val dp = resources.displayMetrics.density
    private val cajas = ArrayList<Caja>()
    private val trastes = ArrayList<Float>()
    private val pulsadas = HashMap<Int, Pulsacion>()
    private val tmp = RectF()
    private val trazo = Path()
    private var curva = 0f
    private var tamLetra = 0f

    // Toques sucesivos: qué tecla se tocó la última vez, cuándo y en qué letra va.
    private var ultimaMultiple: Tecla? = null
    private var ultimoToque = 0L
    private var indiceMultiple = 0
    private var mayusMultiple = false

    private val fondo = Paint()
    private val traste = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val cuerpo = Paint(Paint.ANTI_ALIAS_FLAG)
    private val sombra = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tinte = Paint(Paint.ANTI_ALIAS_FLAG)
    private val brillo = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val letra = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER; typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private val secundaria = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.RIGHT; typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private val icono = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) = calcular(w, h)

    /** Desplazamiento vertical de la "sonrisa" en la posición x: 0 en los bordes, [curva] en el centro. */
    private fun sonrisa(x: Float): Float {
        val u = (x - width / 2f) / (width / 2f)
        return curva * (1 - u * u)
    }

    private fun calcular(w: Int, h: Int) {
        cajas.clear(); trastes.clear()
        val filas = Distribucion.filas(tipo, espanol)
        val conTrastes = piel.traste != null
        curva = piel.curva * dp
        val padX = 5 * dp
        val grueso = if (conTrastes) 3.5f * dp else 0f
        val padArriba = if (conTrastes) 2 * dp else 6 * dp
        val padAbajo = 6 * dp
        val gap = if (conTrastes) 4 * dp else 6 * dp
        val altoFila = (h - padArriba - padAbajo - grueso * filas.size - curva) / filas.size
        var y = padArriba
        var anchoMin = Float.MAX_VALUE
        for (fila in filas) {
            trastes += y
            y += grueso
            val pesoTotal = fila.sumOf { it.peso.toDouble() }.toFloat()
            val util = w - 2 * padX - gap * (fila.size - 1)
            var x = padX
            for (t in fila) {
                val ancho = util * t.peso / pesoTotal
                val cx = x + ancho / 2
                val baja = if (w > 0) curva * (1 - ((cx - w / 2f) / (w / 2f)).let { it * it }) else 0f
                val r = RectF(x, y + 2 * dp + baja, x + ancho, y + altoFila - 2 * dp + baja)
                if (t.tipo == Tipo.LETRA) anchoMin = minOf(anchoMin, ancho)
                cajas += Caja(
                    t, r,
                    LinearGradient(0f, r.top, 0f, r.bottom, piel.tecla, floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP),
                    LinearGradient(0f, r.top, 0f, r.bottom, piel.hundida[0], piel.hundida[1], Shader.TileMode.CLAMP),
                    if (piel.esculpida) talla(r, (cx - w / 2f) / (w / 2f)) else null,
                )
                x += ancho + gap
            }
            y += altoFila
        }
        fondo.shader = LinearGradient(0f, 0f, 0f, h.toFloat(), piel.fondoArriba, piel.fondoAbajo, Shader.TileMode.CLAMP)
        // Con el ancho de la tecla de letra más estrecha como tope: si el teclado se estira, no se desborda.
        tamLetra = minOf(altoFila * 0.40f, anchoMin * 0.62f)
        letra.textSize = tamLetra
        secundaria.textSize = minOf(altoFila * 0.24f, anchoMin * 0.36f)
        letra.setShadowLayer(1.5f * dp, 0f, 1f * dp, piel.sombraLetra)
        brillo.strokeWidth = 1.2f * dp
        icono.strokeWidth = 2f * dp
        sombra.color = piel.sombra
    }

    /**
     * El brillo "tallado": entra en diagonal desde el borde exterior del teclado (izquierda para las
     * teclas de la izquierda, derecha para las de la derecha), como si cada tecla mirara hacia afuera.
     */
    private fun talla(r: RectF, u: Float): Shader {
        val haciaIzquierda = u < 0
        val fuerza = (0x22 + abs(u) * 0x38).toInt().coerceIn(0, 255)
        val x0 = if (haciaIzquierda) r.left else r.right
        val x1 = if (haciaIzquierda) r.right else r.left
        return LinearGradient(x0, r.top, x1, r.bottom,
            intArrayOf((fuerza shl 24) or 0xFFFFFF, 0x00FFFFFF, 0x38000000),
            floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
    }

    override fun onDraw(c: Canvas) {
        c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), fondo)
        piel.traste?.let { colores -> for (y in trastes) dibujarTraste(c, y, colores) }
        for (caja in cajas) dibujarTecla(c, caja, pulsadas.values.any { it.caja === caja })
    }

    /** Traste cromado: cuerpo, filo brillante arriba y sombra abajo, siguiendo la sonrisa. */
    private fun dibujarTraste(c: Canvas, y: Float, col: IntArray) {
        val grueso = 3.5f * dp
        trazo.reset()
        val pasos = 24
        for (i in 0..pasos) {
            val x = width * i / pasos.toFloat()
            val yy = y + grueso / 2 + sonrisa(x)
            if (i == 0) trazo.moveTo(x, yy) else trazo.lineTo(x, yy)
        }
        traste.strokeWidth = grueso; traste.color = col[2]; c.drawPath(trazo, traste)
        traste.strokeWidth = 1.1f * dp
        c.save(); c.translate(0f, -grueso * 0.22f); traste.color = col[1]; c.drawPath(trazo, traste); c.restore()
        c.save(); c.translate(0f, grueso * 0.45f); traste.color = col[3]; c.drawPath(trazo, traste); c.restore()
    }

    private fun dibujarTecla(c: Canvas, caja: Caja, hundida: Boolean) {
        val r = caja.r
        val rad = piel.radio * dp
        c.drawRoundRect(r.left, r.top + 2 * dp, r.right, r.bottom + 2 * dp, rad, rad, sombra)
        val dy = if (hundida) 1.8f * dp else 0f
        tmp.set(r.left, r.top + dy, r.right, r.bottom + dy)
        cuerpo.shader = if (hundida) caja.hundida else caja.normal
        c.drawRoundRect(tmp, rad, rad, cuerpo)
        if (!hundida && caja.talla != null) {
            c.save(); c.translate(0f, dy)
            cuerpo.shader = caja.talla
            c.drawRoundRect(r, rad, rad, cuerpo)
            c.restore()
        }

        val t = caja.tecla
        val acento = when (t.tipo) {
            Tipo.SHIFT -> if (shift != Modo.NO) AZUL else 0
            Tipo.ALT -> if (alt != Modo.NO) piel.alt else 0
            Tipo.SIMBOLOS -> if (sym) CIAN else 0
            else -> 0
        }
        if (acento != 0) {
            tinte.color = (acento and 0x00FFFFFF) or 0x48000000
            c.drawRoundRect(tmp, rad, rad, tinte)
        }
        if (!hundida) {
            brillo.color = piel.brillo
            c.drawLine(tmp.left + rad, tmp.top + 1.2f * dp, tmp.right - rad, tmp.top + 1.2f * dp, brillo)
        }

        val cx = tmp.centerX()
        val cy = tmp.centerY()
        val s = minOf(tmp.height() * 0.22f, tamLetra * 0.55f)
        val colorIcono = if (acento != 0) acento else piel.letra
        when (t.tipo) {
            Tipo.LETRA -> dibujarLetra(c, t, cx, cy)
            Tipo.CERO -> texto(c, "0", cx, cy, if (alt != Modo.NO) piel.alt else piel.letra)
            Tipo.ALT -> texto(c, if (tipo == TipoTeclado.NUMERICO) "123" else "alt", cx, cy, colorIcono, 0.72f)
            Tipo.SIMBOLOS -> texto(c, if (tipo == TipoTeclado.NUMERICO) "#" else "sym", cx, cy, colorIcono, 0.78f)
            Tipo.SHIFT -> flecha(c, cx, cy, s, colorIcono, shift == Modo.FIJO)
            Tipo.BORRAR -> borrar(c, cx, cy, s)
            Tipo.ENTER -> enter(c, cx, cy, s)
            Tipo.ESPACIO -> {
                espacio(c, cx, cy + if (t.numerica) s * 0.6f else 0f, s)
                if (t.numerica) texto(c, "0", cx, cy - s * 0.5f, if (alt != Modo.NO) piel.alt else piel.letra, 0.8f)
            }
        }
    }

    private fun dibujarLetra(c: Canvas, t: Tecla, cx: Float, cy: Float) {
        val ancho = tmp.width() * 0.86f
        if (t.numerica) {
            // Teléfono: el número grande y las letras debajo.
            val enNumeros = alt != Modo.NO
            texto(c, t.alt, cx, cy - tamLetra * 0.22f, if (enNumeros) piel.alt else piel.letra, 1f, ancho)
            secundaria.textAlign = Paint.Align.CENTER
            secundaria.color = if (enNumeros) piel.apagado else piel.alt
            val letras = if (t.letra.isLetter()) t.letras.uppercase() else t.letras
            c.drawText(letras, cx, cy + tamLetra * 0.62f, secundaria)
            secundaria.textAlign = Paint.Align.RIGHT
            return
        }
        val impresa = if (t.multiple) t.letras.uppercase() else t.letra.uppercaseChar().toString()
        val (principal, chica, color) = when {
            sym -> Triple(Distribucion.simbolo(t.letra, espanol), impresa, CIAN)
            alt != Modo.NO -> Triple(t.alt, impresa, piel.alt)
            else -> Triple(impresa, t.alt, piel.letra)
        }
        texto(c, principal, cx, cy + tamLetra * 0.08f, color, 1f, ancho)
        secundaria.color = if (sym || alt != Modo.NO) piel.apagado else piel.alt
        c.drawText(chica, tmp.right - 4 * dp, tmp.top + secundaria.textSize + 1.5f * dp, secundaria)
    }

    /** Texto centrado; se encoge si no cabe en [maxAncho]. */
    private fun texto(c: Canvas, s: String, cx: Float, cy: Float, color: Int, escala: Float = 1f, maxAncho: Float = Float.MAX_VALUE) {
        letra.textSize = tamLetra * escala
        val medido = letra.measureText(s)
        if (medido > maxAncho) letra.textSize *= maxAncho / medido
        letra.color = color
        c.drawText(s, cx, cy - (letra.descent() + letra.ascent()) / 2, letra)
        letra.textSize = tamLetra
    }

    private fun flecha(c: Canvas, cx: Float, cy: Float, s: Float, color: Int, rellena: Boolean) {
        trazo.reset()
        trazo.moveTo(cx, cy - s)
        trazo.lineTo(cx + 0.95f * s, cy)
        trazo.lineTo(cx + 0.42f * s, cy)
        trazo.lineTo(cx + 0.42f * s, cy + 0.85f * s)
        trazo.lineTo(cx - 0.42f * s, cy + 0.85f * s)
        trazo.lineTo(cx - 0.42f * s, cy)
        trazo.lineTo(cx - 0.95f * s, cy)
        trazo.close()
        icono.color = color
        icono.style = if (rellena) Paint.Style.FILL_AND_STROKE else Paint.Style.STROKE
        c.drawPath(trazo, icono)
        icono.style = Paint.Style.STROKE
    }

    private fun borrar(c: Canvas, cx: Float, cy: Float, s: Float) {
        trazo.reset()
        trazo.moveTo(cx - 1.1f * s, cy)
        trazo.lineTo(cx - 0.5f * s, cy - 0.65f * s)
        trazo.lineTo(cx + 1.0f * s, cy - 0.65f * s)
        trazo.lineTo(cx + 1.0f * s, cy + 0.65f * s)
        trazo.lineTo(cx - 0.5f * s, cy + 0.65f * s)
        trazo.close()
        val x = cx + 0.25f * s
        val d = 0.28f * s
        trazo.moveTo(x - d, cy - d); trazo.lineTo(x + d, cy + d)
        trazo.moveTo(x + d, cy - d); trazo.lineTo(x - d, cy + d)
        icono.color = piel.letra
        c.drawPath(trazo, icono)
    }

    private fun enter(c: Canvas, cx: Float, cy: Float, s: Float) {
        trazo.reset()
        trazo.moveTo(cx + 0.9f * s, cy - 0.7f * s)
        trazo.lineTo(cx + 0.9f * s, cy + 0.25f * s)
        trazo.lineTo(cx - 0.85f * s, cy + 0.25f * s)
        trazo.moveTo(cx - 0.45f * s, cy - 0.15f * s)
        trazo.lineTo(cx - 0.85f * s, cy + 0.25f * s)
        trazo.lineTo(cx - 0.45f * s, cy + 0.65f * s)
        icono.color = piel.letra
        c.drawPath(trazo, icono)
    }

    private fun espacio(c: Canvas, cx: Float, cy: Float, s: Float) {
        trazo.reset()
        trazo.moveTo(cx - 1.6f * s, cy - 0.1f * s)
        trazo.lineTo(cx - 1.6f * s, cy + 0.35f * s)
        trazo.lineTo(cx + 1.6f * s, cy + 0.35f * s)
        trazo.lineTo(cx + 1.6f * s, cy - 0.1f * s)
        icono.color = piel.apagado
        c.drawPath(trazo, icono)
    }

    // ---- Toques -------------------------------------------------------------------------------

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (!interactivo) return false
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val i = e.actionIndex
                cajaEn(e.getX(i), e.getY(i))?.let { bajar(e.getPointerId(i), it) }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> subir(e.getPointerId(e.actionIndex))
            MotionEvent.ACTION_CANCEL -> {
                pulsadas.values.forEach { p -> p.tarea?.let { removeCallbacks(it) } }
                pulsadas.clear()
                invalidate()
            }
        }
        return true
    }

    /** La tecla más cercana al dedo: un toque en el hueco entre teclas también cuenta. */
    private fun cajaEn(x: Float, y: Float): Caja? = cajas.minByOrNull { k ->
        val dx = maxOf(k.r.left - x, 0f, x - k.r.right)
        val dy = maxOf(k.r.top - y, 0f, y - k.r.bottom)
        dx * dx + dy * dy
    }

    private fun bajar(id: Int, caja: Caja) {
        retro.tecla()
        val p = Pulsacion(caja)
        pulsadas[id] = p
        when (caja.tecla.tipo) {
            Tipo.BORRAR -> {
                p.consumida = true
                ultimaMultiple = null
                oyente?.alBorrar()
                val repetir = object : Runnable {
                    override fun run() {
                        oyente?.alBorrar(); postDelayed(this, 55)
                    }
                }
                p.tarea = repetir
                postDelayed(repetir, 420)
            }
            Tipo.LETRA -> {
                val largo = Runnable {
                    p.consumida = true
                    retro.largo()
                    pulsacionLarga(caja.tecla)
                    invalidate()
                }
                p.tarea = largo
                postDelayed(largo, 380)
            }
            else -> {}
        }
        invalidate()
    }

    /** Mantener: en el teclado numérico, el número; en los demás, la mayúscula (o el atajo del inicio). */
    private fun pulsacionLarga(t: Tecla) {
        ultimaMultiple = null
        if (t.numerica) {
            enviar(t.alt, t); return
        }
        if (oyente?.alPulsarLargo(t.letra) != true) {
            enviar(t.letra.uppercaseChar().toString(), t)
        }
    }

    private fun subir(id: Int) {
        val p = pulsadas.remove(id) ?: return
        p.tarea?.let { removeCallbacks(it) }
        if (!p.consumida) pulsar(p.caja.tecla)
        invalidate()
    }

    private fun pulsar(t: Tecla) {
        if (t.tipo != Tipo.LETRA) ultimaMultiple = null
        when (t.tipo) {
            Tipo.LETRA -> emitir(t)
            Tipo.CERO -> enviar("0", t)
            Tipo.ESPACIO -> enviar(if (alt != Modo.NO && t.alt.isNotEmpty()) t.alt else " ", t)
            Tipo.ENTER -> {
                // Shift pulsado a mano (no la mayúscula automática) + Enter = salto de línea.
                val conShift = shift != Modo.NO && !shiftAuto
                soltarModificadores()
                if (conShift) oyente?.alEnterConShift() else oyente?.alEnter()
            }
            Tipo.ALT -> {
                alt = siguiente(alt); if (alt != Modo.NO) sym = false
            }
            Tipo.SHIFT -> {
                shift = siguiente(shift); shiftAuto = false
            }
            Tipo.SIMBOLOS -> {
                sym = !sym; if (sym && !altForzado) alt = Modo.NO
            }
            Tipo.BORRAR -> {}
        }
    }

    private fun emitir(t: Tecla) {
        when {
            sym -> { ultimaMultiple = null; enviar(Distribucion.simbolo(t.letra, espanol), t) }
            alt != Modo.NO -> { ultimaMultiple = null; enviar(t.alt, t) }
            t.multiple -> multiple(t)
            else -> {
                ultimaMultiple = null
                enviar(if (shift != Modo.NO) t.letra.uppercaseChar().toString() else t.letra.toString(), t)
            }
        }
    }

    /** Toques sucesivos: la misma tecla antes de 0,9 s pasa a la siguiente letra. */
    private fun multiple(t: Tecla) {
        val ahora = SystemClock.uptimeMillis()
        if (ultimaMultiple == t && ahora - ultimoToque < 900) {
            indiceMultiple = (indiceMultiple + 1) % t.letras.length
            ultimoToque = ahora
            val ch = t.letras[indiceMultiple]
            oyente?.alReemplazar((if (mayusMultiple) ch.uppercaseChar() else ch).toString(), t)
            return
        }
        ultimaMultiple = t
        ultimoToque = ahora
        indiceMultiple = 0
        mayusMultiple = shift != Modo.NO
        val ch = t.letras[0]
        enviar((if (mayusMultiple) ch.uppercaseChar() else ch).toString(), t)
    }

    private fun enviar(texto: String, t: Tecla) {
        soltarModificadores()
        oyente?.alEscribir(texto, t)
    }

    /** Los modificadores de un solo uso se apagan después de cada tecla; los fijos se quedan. */
    private fun soltarModificadores() {
        if (shift == Modo.UNA) {
            shift = Modo.NO; shiftAuto = false
        }
        if (alt == Modo.UNA) alt = Modo.NO
        sym = false
        invalidate()
    }

    private fun siguiente(m: Modo) = when (m) {
        Modo.NO -> Modo.UNA
        Modo.UNA -> Modo.FIJO
        Modo.FIJO -> Modo.NO
    }

    companion object {
        const val AZUL = 0xFF4AA3FF.toInt()
        const val CIAN = 0xFF4FE0E0.toInt()
    }
}
