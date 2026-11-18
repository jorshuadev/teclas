package pa.jorshua.teclas.inicio

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.MediaStore
import android.provider.Settings
import android.provider.Telephony
import pa.jorshua.teclas.Preferencias

/** Atajos por letra de fábrica (T = teléfono, M = mensajes, W = WhatsApp…) y apps fijadas en el inicio. */
object Atajos {

    fun inicializar(ctx: Context, prefs: Preferencias) {
        if (prefs.atajosListos) return
        val pm = ctx.packageManager
        fun lanzable(pkg: String?): ComponentName? = pkg?.let { pm.getLaunchIntentForPackage(it)?.component }
        fun porIntent(i: Intent) = lanzable(pm.resolveActivity(i, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName)

        val telefono = porIntent(Intent(Intent.ACTION_DIAL))
        val mensajes = lanzable(Telephony.Sms.getDefaultSmsPackage(ctx))
        val whatsapp = lanzable("com.whatsapp")
        val camara = porIntent(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))
        val navegador = porIntent(Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")))
        val porLetra = mapOf(
            't' to telefono, 'm' to mensajes, 'w' to whatsapp, 'c' to camara, 'n' to navegador,
            'y' to lanzable("com.google.android.youtube"), 'g' to lanzable("com.google.android.gm"),
            'a' to porIntent(Intent(Settings.ACTION_SETTINGS)),
        )
        for ((letra, comp) in porLetra) comp?.let { prefs.ponerAtajo(letra, it.flattenToString()) }
        prefs.fijadas = listOfNotNull(telefono, mensajes, whatsapp, camara, navegador)
            .map { it.flattenToString() }.distinct()
        prefs.atajosListos = true
    }
}
