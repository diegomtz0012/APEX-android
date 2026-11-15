package com.apex.app.ui.share

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

/** Utilidades para compartir resultados (texto) y exportar historial (CSV). */
object ShareUtils {

    fun shareText(context: Context, text: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(intent, "Compartir"))
    }

    fun shareCsv(context: Context, fileName: String, content: String) {
        try {
            val dir = File(context.cacheDir, "exports").apply { mkdirs() }
            val file = File(dir, fileName)
            file.writeText(content)
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Exportar CSV"))
        } catch (e: Exception) {
            // Si algo falla, no hacemos nada (mejor que tumbar la app).
        }
    }
}
