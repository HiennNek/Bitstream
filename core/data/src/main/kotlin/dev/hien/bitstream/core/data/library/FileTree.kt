package dev.hien.bitstream.core.data.library

import java.io.InputStream

/** A document or directory, independent of the storage backend. */
interface FileNode {
    val name: String
    val uri: String
    val size: Long
    val lastModified: Long
    val isDirectory: Boolean
}

/** The minimal tree operations the scanner needs; SAF and tests both implement it. */
interface FileTree {
    fun children(node: FileNode): List<FileNode>

    fun open(node: FileNode): InputStream
}
