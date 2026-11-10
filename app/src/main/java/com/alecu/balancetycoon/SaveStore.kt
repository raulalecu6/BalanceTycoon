package com.alecu.balancetycoon

import android.content.Context
import android.util.Log
import com.alecu.balancetycoon.core.game.GameState
import com.alecu.balancetycoon.core.save.SaveCodec
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

/**
 * Archivo de partida en el almacenamiento interno de la app.
 * Se escribe primero en un temporal y después se renombra, para que un cierre
 * inesperado a mitad de escritura no deje la partida corrupta.
 */
class SaveStore(context: Context) {
    private val dir: File = context.filesDir
    private val file = File(dir, "partida.json")
    private val tmp = File(dir, "partida.json.tmp")
    private val mutex = Mutex()

    fun load(): GameState? {
        if (!file.exists()) return null
        return try {
            SaveCodec.decode(file.readText(Charsets.UTF_8))
        } catch (e: Exception) {
            // Se aparta el archivo dañado en lugar de borrarlo, por si hiciera falta recuperarlo.
            Log.e("SaveStore", "No se pudo leer la partida", e)
            file.renameTo(File(dir, "partida-danada-${System.currentTimeMillis()}.json"))
            null
        }
    }

    suspend fun write(json: String) = mutex.withLock {
        tmp.writeText(json, Charsets.UTF_8)
        if (!tmp.renameTo(file)) {
            file.delete()
            tmp.renameTo(file)
        }
    }
}
