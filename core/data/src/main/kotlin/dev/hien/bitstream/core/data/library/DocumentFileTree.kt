package dev.hien.bitstream.core.data.library

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.FileNotFoundException
import java.io.InputStream

/** [FileTree] over a SAF tree document. */
class DocumentFileTree(private val context: Context) : FileTree {
    fun root(treeUri: String): FileNode? =
        DocumentFile.fromTreeUri(context, Uri.parse(treeUri))?.let(::DocumentNode)

    override fun children(node: FileNode): List<FileNode> =
        (node as DocumentNode).file.listFiles().map(::DocumentNode)

    override fun open(node: FileNode): InputStream {
        val uri = Uri.parse(node.uri)
        return context.contentResolver.openInputStream(uri)
            ?: throw FileNotFoundException("Cannot open $uri")
    }

    private class DocumentNode(val file: DocumentFile) : FileNode {
        override val name: String = file.name ?: ""
        override val uri: String = file.uri.toString()
        override val size: Long = file.length()
        override val lastModified: Long = file.lastModified()
        override val isDirectory: Boolean = file.isDirectory
    }
}
