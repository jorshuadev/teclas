package pa.jorshua.teclas.ime

import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import pa.jorshua.teclas.AjustesActivity
import pa.jorshua.teclas.Preferencias
import pa.jorshua.teclas.fijo.DeckActivity
import pa.jorshua.teclas.teclado.BarraNavView
import pa.jorshua.teclas.teclado.Deck
import pa.jorshua.teclas.teclado.Tecla
import pa.jorshua.teclas.teclado.TecladoView

/**
 * El mismo teclado del inicio como teclado del sistema: aparece en WhatsApp, Chrome, etc.
 * Mayúscula automática al empezar frase, doble espacio = punto, y en campos numéricos
 * alt queda bloqueado para teclear números directo.
 */
class TecladoIme : InputMethodService(), TecladoView.Oyente, BarraNavView.Acciones {

    private var deck: Deck? = null
    private var alturaUsada = 0
    private var vistaAbierta = false
    private var conectado = false

    /** Hay un campo abierto y este teclado escribe en él (a la vista, o sin vista en modo dividido). */
    val escribiendo get() = vistaAbierta || (conectado && enModoDividido())

    /** En modo dividido el teclado visible es [DeckActivity]: este no se muestra, solo escribe. */
    private fun enModoDividido() = Preferencias(this).modoDividido && DeckActivity.visible

    override fun onEvaluateInputViewShown(): Boolean = super.onEvaluateInputViewShown() && !enModoDividido()

    /** Teclas apagado: devolver el teclado que se usaba antes (Gboard, Samsung…). */
    fun volverAlAnterior() {
        if (!switchToPreviousInputMethod()) switchToNextInputMethod(false)
    }

    override fun onStartInput(info: EditorInfo, reiniciando: Boolean) {
        super.onStartInput(info, reiniciando)
        if (!Preferencias(this).activo) { volverAlAnterior(); return }
        conectado = info.inputType != InputType.TYPE_NULL
        if (conectado && enModoDividido()) {
            if (pendiente.isNotEmpty()) {
                currentInputConnection?.commitText(pendiente.toString(), 1)
                pendiente.clear()
            }
            actualizarMayuscula()
        }
    }

    override fun onFinishInput() {
        conectado = false
        super.onFinishInput()
    }

    companion object {
        /** El teclado fijo le pasa aquí las letras pulsadas mientras se abre el campo de la app. */
        var activo: TecladoIme? = null
            private set
        val pendiente = StringBuilder()
        /** El teclado de [DeckActivity], para que también reciba la mayúscula automática. */
        var tecladoExterno: TecladoView? = null
    }

    override fun onCreate() {
        super.onCreate()
        activo = this
    }

    override fun onDestroy() {
        if (activo === this) activo = null
        super.onDestroy()
    }

    override fun onFinishInputView(terminando: Boolean) {
        vistaAbierta = false
        super.onFinishInputView(terminando)
    }

    override fun onCreateInputView(): View {
        val prefs = Preferencias(this)
        alturaUsada = prefs.alturaPct
        return Deck(this, this, this, Deck.alto(this, alturaUsada)).also { deck = it }.vista
    }

    override fun onEvaluateFullscreenMode() = false

    override fun onStartInputView(info: EditorInfo, reiniciando: Boolean) {
        super.onStartInputView(info, reiniciando)
        if (Preferencias(this).alturaPct != alturaUsada) setInputView(onCreateInputView())
        deck?.teclado?.recargar(); deck?.barra?.recargar()
        val clase = info.inputType and InputType.TYPE_MASK_CLASS
        deck?.teclado?.altForzado = clase == InputType.TYPE_CLASS_NUMBER || clase == InputType.TYPE_CLASS_PHONE
        vistaAbierta = true
        if (pendiente.isNotEmpty()) {
            currentInputConnection?.commitText(pendiente.toString(), 1)
            pendiente.clear()
        }
        actualizarMayuscula()
    }

    override fun onUpdateSelection(oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int,
                                   candidatesStart: Int, candidatesEnd: Int) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        actualizarMayuscula()
    }

    private fun actualizarMayuscula() {
        val ic = currentInputConnection ?: return
        val ei = currentInputEditorInfo ?: return
        val caps = if (ei.inputType != InputType.TYPE_NULL) ic.getCursorCapsMode(ei.inputType) else 0
        deck?.teclado?.ponerAutoMayuscula(caps != 0)
        // En modo dividido la mayúscula la decide el teclado fijo (escribe por accesibilidad, no por aquí).
        if (!enModoDividido()) tecladoExterno?.ponerAutoMayuscula(caps != 0)
    }

    // ---- Teclado ------------------------------------------------------------------------------

    override fun alEscribir(texto: String, tecla: Tecla?) {
        val ic = currentInputConnection ?: return
        if (texto == " ") {
            val antes = ic.getTextBeforeCursor(2, 0)
            if (antes != null && antes.length == 2 && antes[1] == ' ' && antes[0].isLetterOrDigit()) {
                ic.deleteSurroundingText(1, 0)
                ic.commitText(". ", 1)
                return
            }
        }
        ic.commitText(texto, 1)
    }

    override fun alBorrar() = sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)

    /** Toques sucesivos: cambiar la última letra sin pasar por la tecla borrar. */
    override fun alReemplazar(texto: String, tecla: Tecla?) {
        val ic = currentInputConnection ?: return
        ic.beginBatchEdit()
        ic.deleteSurroundingText(1, 0)
        ic.commitText(texto, 1)
        ic.endBatchEdit()
    }

    override fun alEnter() {
        val ic = currentInputConnection ?: return
        val ei = currentInputEditorInfo
        val accion = (ei?.imeOptions ?: 0) and EditorInfo.IME_MASK_ACTION
        val sinAccion = ((ei?.imeOptions ?: 0) and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0
        if (!sinAccion && accion != EditorInfo.IME_ACTION_NONE && accion != EditorInfo.IME_ACTION_UNSPECIFIED) {
            ic.performEditorAction(accion)
        } else {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
        }
    }

    // ---- Fila de navegación -------------------------------------------------------------------

    override fun mover(tecla: Int) = sendDownUpKeyEvents(tecla)

    override fun pulsarTrackpad() = sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_CENTER)

    override fun llamar() = abrir(Intent(Intent.ACTION_DIAL))

    override fun menu() = abrir(Intent(this, AjustesActivity::class.java))

    override fun menuLargo() {
        getSystemService(InputMethodManager::class.java)?.showInputMethodPicker()
    }

    override fun atras() = requestHideSelf(0)

    override fun colgar() {
        requestHideSelf(0)
        abrir(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME))
    }

    private fun abrir(i: Intent) {
        try {
            startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
        }
    }
}
