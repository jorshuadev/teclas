package pa.jorshua.teclas

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import pa.jorshua.teclas.fijo.TecladoFijo
import pa.jorshua.teclas.ime.TecladoIme

/**
 * Encender / apagar todo Teclas sin desinstalarlo (desde Ajustes o desde el botón de ajustes rápidos).
 * Apagado, el teléfono se usa como siempre; encendido, todo vuelve.
 */
object Interruptor {

    fun poner(ctx: Context, activo: Boolean) {
        Preferencias(ctx).activo = activo
        // Teclado fijo / pantalla dividida: el servicio se aparta (o vuelve) solo.
        TecladoFijo.activo?.actualizar()
        // Teclado del sistema: volver al que se usaba antes.
        if (!activo) TecladoIme.activo?.volverAlAnterior()
    }

    /** El inicio normal del teléfono (One UI u otro), para cuando Teclas está apagado. */
    fun otroInicio(ctx: Context): Intent? {
        val pm = ctx.packageManager
        val inicios = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)
            .map { it.activityInfo }
            .filter { it.packageName != ctx.packageName && it.packageName != "com.android.settings" }
        val elegido = inicios.firstOrNull { it.packageName == "com.sec.android.app.launcher" } ?: inicios.firstOrNull()
            ?: return null
        return Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            .setComponent(ComponentName(elegido.packageName, elegido.name))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
