package pa.jorshua.teclas.teclado

import android.content.Context
import android.media.AudioManager
import android.os.VibrationEffect
import android.os.Vibrator
import pa.jorshua.teclas.Preferencias

/** El "clic" de una tecla de verdad: vibración corta y seca más el sonido de tecla del sistema. */
class Retro(ctx: Context) {
    private val prefs = Preferencias(ctx)
    private val vib = ctx.getSystemService(Vibrator::class.java)
    private val audio = ctx.getSystemService(AudioManager::class.java)

    fun tecla() {
        if (prefs.vibrar) vib?.vibrate(VibrationEffect.createOneShot(16, 200))
        if (prefs.sonido) audio?.playSoundEffect(AudioManager.FX_KEYPRESS_STANDARD, 0.7f)
    }

    /** Cada "paso" del trackpad: apenas un roce. */
    fun suave() {
        if (prefs.vibrar) vib?.vibrate(VibrationEffect.createOneShot(6, 90))
    }

    /** Pulsación larga reconocida. */
    fun largo() {
        if (prefs.vibrar) vib?.vibrate(VibrationEffect.createOneShot(35, 255))
    }
}
