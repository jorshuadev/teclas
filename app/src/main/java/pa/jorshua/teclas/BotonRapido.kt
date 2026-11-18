package pa.jorshua.teclas

import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/** Botón "Teclas" en los ajustes rápidos (la cortina): encender o apagar todo con un toque. */
class BotonRapido : TileService() {

    override fun onStartListening() = pintar()

    override fun onClick() {
        val nuevo = !Preferencias(this).activo
        Interruptor.poner(this, nuevo)
        pintar()
        // Al encender, abrir Ajustes para volver a elegir el teclado Teclas si hace falta.
        if (nuevo) {
            startActivityAndCollapse(Intent(this, AjustesActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra(AjustesActivity.EXTRA_ACTIVAR, true))
        }
    }

    private fun pintar() {
        val t = qsTile ?: return
        val activo = Preferencias(this).activo
        t.state = if (activo) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        t.label = "Teclas"
        t.subtitle = if (activo) "Activado" else "Apagado"
        t.updateTile()
    }
}
