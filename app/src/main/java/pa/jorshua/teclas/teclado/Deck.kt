package pa.jorshua.teclas.teclado

import android.content.Context
import android.widget.LinearLayout

/** La mitad de abajo del teléfono: fila de navegación encima y teclado QWERTY debajo. */
class Deck(ctx: Context, oyente: TecladoView.Oyente, acciones: BarraNavView.Acciones, alturaPx: Int) {
    val teclado = TecladoView(ctx).also { it.oyente = oyente }
    val barra = BarraNavView(ctx).also { it.acciones = acciones }
    val vista = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(0xFF070708.toInt())
        val altoBarra = (alturaPx * 0.19f).toInt()
        addView(barra, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, altoBarra))
        addView(teclado, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, alturaPx - altoBarra))
    }

    companion object {
        /**
         * Alto del deck según el ajuste; en horizontal se limita para que quede pantalla. Se mide con la
         * pantalla entera (contexto de la aplicación): una actividad en pantalla dividida cree que la
         * pantalla es solo su mitad, y el teclado saldría más bajo que en el inicio.
         */
        fun alto(ctx: Context, pct: Int): Int {
            val m = ctx.applicationContext.resources.displayMetrics
            val base = if (m.heightPixels > m.widthPixels) m.heightPixels * pct / 100 else m.heightPixels / 2
            return base
        }
    }
}
