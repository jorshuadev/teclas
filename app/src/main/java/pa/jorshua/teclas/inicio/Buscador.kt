package pa.jorshua.teclas.inicio

import android.Manifest
import android.app.SearchManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.provider.ContactsContract
import android.provider.Settings
import java.text.Normalizer

class Resultado(
    val tipo: Tipo,
    val titulo: String,
    val subtitulo: String? = null,
    val componente: ComponentName? = null,
    /** Número de teléfono, resultado de la cuenta, etc. */
    val dato: String? = null,
    val intent: Intent? = null,
    val iconoSistema: Int = 0,
) {
    enum class Tipo { APP, CONTACTO, LLAMAR, SMS, CALCULO, AJUSTE, LINTERNA, WEB }
}

/**
 * Búsqueda universal del inicio: con escribir unas letras salen apps, contactos, ajustes,
 * la cuenta resuelta o el número para llamar — como el "escribe para buscar" de los teléfonos con teclado.
 */
class Buscador(private val ctx: Context) {

    class App(val etiqueta: String, val norm: String, val iniciales: String, val componente: ComponentName)

    var apps: List<App> = emptyList()
        private set
    private val iconos = HashMap<ComponentName, Drawable>()

    fun cargarApps() {
        val pm = ctx.packageManager
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        apps = pm.queryIntentActivities(i, 0)
            .filter { it.activityInfo.name != HomeActivity::class.java.name }
            .map {
                val etiqueta = it.loadLabel(pm).toString()
                val norm = normalizar(etiqueta)
                App(etiqueta, norm, norm.split(' ', '-', '.').filter { p -> p.isNotEmpty() }.joinToString("") { p -> p.take(1) },
                    ComponentName(it.activityInfo.packageName, it.activityInfo.name))
            }
            .distinctBy { it.componente }
            .sortedBy { it.norm }
    }

    fun icono(c: ComponentName): Drawable? = iconos.getOrPut(c) {
        try {
            ctx.packageManager.getActivityIcon(c)
        } catch (e: PackageManager.NameNotFoundException) {
            return null
        }
    }

    fun etiqueta(c: ComponentName): String =
        apps.firstOrNull { it.componente == c }?.etiqueta ?: c.packageName

    fun todas(): List<Resultado> = apps.map { Resultado(Resultado.Tipo.APP, it.etiqueta, componente = it.componente) }

    /** [numero] es lo tecleado leído como números de las teclas (W=1, E=2…), si se puede. */
    fun buscar(consulta: String, numero: String?, linternaEncendida: Boolean): List<Resultado> {
        val q = normalizar(consulta).trim()
        if (q.isEmpty()) return emptyList()
        val salida = ArrayList<Resultado>()

        Calculadora.evaluar(consulta)?.let {
            val r = Calculadora.formatear(it)
            salida += Resultado(Resultado.Tipo.CALCULO, "= $r", "$consulta · pulsa para copiar", dato = r,
                iconoSistema = android.R.drawable.ic_menu_edit)
        }

        if (numero != null && numero.length >= 3) {
            salida += Resultado(Resultado.Tipo.LLAMAR, "Llamar al $numero", "Tecla verde", dato = numero,
                iconoSistema = android.R.drawable.sym_action_call)
            salida += Resultado(Resultado.Tipo.SMS, "Mensaje al $numero", dato = numero,
                iconoSistema = android.R.drawable.sym_action_chat)
        }

        salida += apps
            .map { it to puntuar(it, q) }
            .filter { it.second > 0 }
            .sortedWith(compareByDescending<Pair<App, Int>> { it.second }.thenBy { it.first.norm })
            .take(8)
            .map { Resultado(Resultado.Tipo.APP, it.first.etiqueta, componente = it.first.componente) }

        if (q.length >= 2) salida += contactos(consulta.trim())

        if (q.length >= 2) {
            if ("linterna".startsWith(q) || "luz".startsWith(q)) {
                salida += Resultado(Resultado.Tipo.LINTERNA,
                    if (linternaEncendida) "Apagar la linterna" else "Encender la linterna",
                    iconoSistema = android.R.drawable.ic_menu_view)
            }
            for ((claves, titulo, accion) in AJUSTES) {
                if (claves.any { it.startsWith(q) }) {
                    salida += Resultado(Resultado.Tipo.AJUSTE, titulo, "Ajustes", intent = Intent(accion),
                        iconoSistema = android.R.drawable.ic_menu_preferences)
                }
            }
        }

        salida += Resultado(Resultado.Tipo.WEB, "Buscar «${consulta.trim()}» en la web",
            intent = Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, consulta.trim()),
            iconoSistema = android.R.drawable.ic_menu_search)
        return salida
    }

    private fun puntuar(a: App, q: String): Int = when {
        a.norm.startsWith(q) -> 100
        a.norm.split(' ', '-', '.').any { it.startsWith(q) } -> 80
        q.length >= 2 && a.iniciales.startsWith(q) -> 60
        q.length >= 3 && a.norm.contains(q) -> 40
        else -> 0
    }

    private fun contactos(q: String): List<Resultado> {
        if (ctx.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return emptyList()
        val salida = ArrayList<Resultado>()
        val vistos = HashSet<String>()
        val p = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val nombre = ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
        val num = ContactsContract.CommonDataKinds.Phone.NUMBER
        try {
            ctx.contentResolver.query(p, arrayOf(nombre, num), "$nombre LIKE ? OR $nombre LIKE ?",
                arrayOf("$q%", "% $q%"), "$nombre ASC")?.use { c ->
                while (c.moveToNext() && salida.size < 6) {
                    val n = c.getString(0) ?: continue
                    val t = c.getString(1) ?: continue
                    if (vistos.add(n + t.filter { it.isDigit() })) {
                        salida += Resultado(Resultado.Tipo.CONTACTO, n, t, dato = t,
                            iconoSistema = android.R.drawable.sym_action_call)
                    }
                }
            }
        } catch (e: SecurityException) {
            return emptyList()
        }
        return salida
    }

    companion object {
        private val AJUSTES = listOf(
            Triple(listOf("wifi", "wi-fi", "red"), "Wi-Fi", Settings.ACTION_WIFI_SETTINGS),
            Triple(listOf("bluetooth"), "Bluetooth", Settings.ACTION_BLUETOOTH_SETTINGS),
            Triple(listOf("datos", "movil", "sim", "apn"), "Redes móviles", Settings.ACTION_WIRELESS_SETTINGS),
            Triple(listOf("avion", "modo avion"), "Modo avión", Settings.ACTION_AIRPLANE_MODE_SETTINGS),
            Triple(listOf("brillo", "pantalla"), "Pantalla y brillo", Settings.ACTION_DISPLAY_SETTINGS),
            Triple(listOf("sonido", "volumen", "tono"), "Sonido", Settings.ACTION_SOUND_SETTINGS),
            Triple(listOf("bateria", "ahorro"), "Batería", Settings.ACTION_BATTERY_SAVER_SETTINGS),
            Triple(listOf("ubicacion", "gps"), "Ubicación", Settings.ACTION_LOCATION_SOURCE_SETTINGS),
            Triple(listOf("almacenamiento", "espacio"), "Almacenamiento", Settings.ACTION_INTERNAL_STORAGE_SETTINGS),
            Triple(listOf("aplicaciones", "apps"), "Aplicaciones", Settings.ACTION_APPLICATION_SETTINGS),
            Triple(listOf("teclado", "idioma"), "Teclados e idioma", Settings.ACTION_INPUT_METHOD_SETTINGS),
            Triple(listOf("ajustes", "configuracion"), "Ajustes del teléfono", Settings.ACTION_SETTINGS),
        )

        fun normalizar(s: String): String =
            Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
    }
}
