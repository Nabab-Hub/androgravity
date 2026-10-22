package dev.pocketagent.core.model

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class Project(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val path: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Serializable
data class FileNode(
    val name: String,
    val relativePath: String,
    val isDirectory: Boolean,
    val children: List<FileNode> = emptyList(),
    val sizeBytes: Long = 0,
    val lastModified: Long = 0
)

@Serializable
data class SearchMatch(
    val relativePath: String,
    val lineNumber: Int,
    val lineContent: String
)
