package pa.jorshua.teclas.fijo

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.view.WindowInsets

/**
 * Modo dividido: el "hueco" de la mitad de abajo de la pantalla dividida. Su único trabajo es
 * ocupar ese sitio para que Android encoja de verdad la app de arriba; el teclado que se ve y se
 * toca es la capa fija de [TecladoFijo], colocada justo encima, igual que en el inicio.
 *
 * No dibuja el teclado aquí a propósito: tras mover el divisor, Samsung a veces deja esta ventana
 * dibujada en su posición vieja (corrida y recortada). Siendo un fondo negro tapado por la capa,
 * eso no se ve nunca.
 *
 * Esta ventana SÍ acepta el foco: Android deja como enfocada la última app abierta (esta), y si no
 * pudiera recibir teclas, la tecla Atrás se quedaría esperando → "Teclas no responde". Atrás se
 * resuelve aquí sobre la app de arriba ([TecladoFijo.atrasDividido]).
 */
class DeckActivity : Activity() {

    companion object {
        var visible = false
            private set
    }

    private var inferior = 0

    override fun onCreate(estado: Bundle?) {
        super.onCreate(estado)
        val hueco = View(this).apply { setBackgroundColor(0xFF000000.toInt()) }
        // La barra de navegación de Android no cuenta: el teclado termina encima de ella, como en el inicio.
        hueco.setOnApplyWindowInsetsListener { _, ins ->
            inferior = ins.getInsets(WindowInsets.Type.navigationBars()).bottom
            ins
        }
        hueco.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
            val util = v.height - inferior
            if (util <= 0 || !isInMultiWindowMode) return@addOnLayoutChangeListener
            // Avisar dónde quedó: el servicio lleva el divisor hasta el borde de arriba del teclado.
            val pos = IntArray(2)
            v.getLocationOnScreen(pos)
            TecladoFijo.activo?.ajustarDivisor(pos[1], pos[1] + util)
        }
        setContentView(hueco)
    }

    @Deprecated("Activity sin AndroidX")
    override fun onBackPressed() {
        TecladoFijo.activo?.atrasDividido()
    }

    override fun onStart() {
        super.onStart()
        if (!isInMultiWindowMode) { cerrar(); return }
        visible = true
        TecladoFijo.activo?.actualizar()
    }

    /** Al deshacerse la pantalla dividida, Android dejaría esta mitad a pantalla completa: cerrarla. */
    override fun onMultiWindowModeChanged(dividida: Boolean, config: android.content.res.Configuration) {
        super.onMultiWindowModeChanged(dividida, config)
        if (!dividida) cerrar()
    }

    private fun cerrar() {
        visible = false
        finishAndRemoveTask()
    }

    override fun onStop() {
        visible = false
        super.onStop()
        // Uno nuevo en cada división: no reutilizar uno viejo con la posición de la vez anterior.
        if (!isFinishing) finishAndRemoveTask()
        TecladoFijo.activo?.actualizar()
    }
}
