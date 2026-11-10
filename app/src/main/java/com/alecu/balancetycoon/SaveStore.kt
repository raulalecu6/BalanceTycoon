package com.alecu.balancetycoon

import android.content.Context
import android.util.Log
import com.alecu.balancetycoon.core.game.GameState
import com.alecu.balancetycoon.core.save.IncompatibleSaveException
import com.alecu.balancetycoon.core.save.SaveCodec
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

/** Resultado de leer la partida: el estado (si lo hay) y si se ha apartado una partida antigua. */
class LoadResult(val state: GameState?, val discardedOldVersion: Boolean)

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

    fun load(): LoadResult {
        if (!file.exists()) return LoadResult(null, false)
        return try {
            LoadResult(SaveCodec.decode(file.readText(Charsets.UTF_8)), false)
        } catch (e: IncompatibleSaveException) {
            // Partida del prototipo de tiendas: se guarda aparte y se empieza la gestoría desde cero.
            file.renameTo(File(dir, "partida-prototipo-0.1.json"))
            LoadResult(null, true)
        } catch (e: Exception) {
            // Se aparta el archivo dañado en lugar de borrarlo, por si hiciera falta recuperarlo.
            Log.e("SaveStore", "No se pudo leer la partida", e)
            file.renameTo(File(dir, "partida-danada-${System.currentTimeMillis()}.json"))
            LoadResult(null, false)
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
