package com.sconcept.mirrordash.clock

import android.content.Context
import com.sconcept.mirrordash.nas.SmbPaths
import com.sconcept.mirrordash.nas.SmbRepository
import com.sconcept.mirrordash.nas.model.SmbShare
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TasksFileRepository(context: Context) {
    private val smbRepository = SmbRepository(context.applicationContext)

    suspend fun loadCsv(share: SmbShare, relativePath: String): List<TaskItem> = withContext(Dispatchers.IO) {
        val normalizedPath = SmbPaths.normalizeRelativePath(relativePath)
        val url = share.rootUrl() + normalizedPath
        smbRepository.openStream(share, url).bufferedReader(Charsets.UTF_8).use { reader ->
            TasksCsvParser.parse(reader.readText(), normalizedPath)
        }
    }
}
