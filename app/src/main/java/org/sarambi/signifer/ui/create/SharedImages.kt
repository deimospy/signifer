package org.sarambi.signifer.ui.create

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

/** Compartir una imagen sin tocar el almacenamiento compartido. */
object SharedImages {
    private const val FOLDER = "shared"

    fun write(context: Context, bitmap: Bitmap, name: String): Uri? = runCatching {
        val folder = File(context.cacheDir, FOLDER)
        folder.mkdirs()
        folder.listFiles()?.forEach { it.delete() }

        val file = File(folder, name)
        FileOutputStream(file).use { stream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        }
        FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    }.getOrNull()

    fun share(context: Context, uri: Uri) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(
            Intent.createChooser(intent, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
