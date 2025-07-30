package com.ai.assistance.operit.api.chat.library

import android.content.Context
import android.util.Log
import androidx.annotation.WorkerThread
import com.ai.assistance.operit.data.database.dao.MemoryDao
import com.ai.assistance.operit.data.model.Memory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * @Deprecated This tool is part of a legacy system for simple problem-solution storage.
 * Use MemoryDao and Memory objects directly for better performance and flexibility.
 * 
 * 建议迁移方案：
 * 1. 使用 MemoryDao 直接操作数据库
 * 2. 创建 Memory 对象存储问题解决方案
 * 3. 使用 AgentScriptGenerator 的内存系统
 */
@Deprecated(
    message = "Use MemoryDao and Memory objects directly instead of this legacy tool",
    replaceWith = ReplaceWith(
        "memoryDao.insertMemory(Memory(...))",
        "com.ai.assistance.operit.data.database.dao.MemoryDao",
        "com.ai.assistance.operit.data.model.Memory"
    ),
    level = DeprecationLevel.WARNING
)
class ProblemLibraryTool(
    private val context: Context,
    private val memoryDao: MemoryDao
) {

    @Deprecated("This tool is part of a legacy system.")
    companion object {
        private const val TAG = "ProblemLibraryTool"
    }

    /**
     * @Deprecated ProblemRecord is a legacy data structure. Use Memory objects directly.
     */
    @Deprecated("ProblemRecord is a legacy data structure. Use Memory objects directly.")
    data class ProblemRecord(
        val id: String,
        val problem: String,
        val solution: String,
        val category: String = "general",
        val keywords: List<String> = emptyList(),
        val timestamp: Long = System.currentTimeMillis(),
        val useCount: Int = 0,
        val rating: Float = 0.0f
    ) {
        fun toMemory(): Memory {
            return Memory(
                id = id,
                title = problem.take(50),
                content = problem,
                solution = solution,
                category = category,
                keywords = keywords.joinToString(","),
                createdAt = timestamp,
                lastUsed = timestamp,
                useCount = useCount,
                rating = rating
            )
        }
    }

    /**
     * @Deprecated This method saves to a legacy data structure.
     * Use memoryDao.insertMemory() directly instead.
     */
    @Deprecated("This method saves to a legacy data structure.")
    @WorkerThread
    suspend fun saveProblemSolution(
        problem: String,
        solution: String,
        category: String = "general",
        keywords: List<String> = emptyList()
    ): String = withContext(Dispatchers.IO) {
        try {
            val memory = Memory(
                id = generateId(),
                title = problem.take(50),
                content = problem,
                solution = solution,
                category = category,
                keywords = keywords.joinToString(","),
                createdAt = System.currentTimeMillis(),
                lastUsed = System.currentTimeMillis(),
                useCount = 0,
                rating = 0.0f
            )
            
            memoryDao.insertMemory(memory)
            Log.d(TAG, "问题解决方案已保存到内存系统: ${memory.id}")
            memory.id
        } catch (e: Exception) {
            Log.e(TAG, "保存问题解决方案失败", e)
            throw e
        }
    }

    /**
     * @Deprecated This method retrieves legacy data.
     * Use memoryDao.getMemoryById() directly instead.
     */
    @Deprecated("This method retrieves legacy data.")
    suspend fun getProblemSolution(problemId: String): ProblemRecord? = withContext(Dispatchers.IO) {
        try {
            val memory = memoryDao.getMemoryById(problemId)
            memory?.let { convertMemoryToProblemRecord(it) }
        } catch (e: Exception) {
            Log.e(TAG, "获取问题解决方案失败", e)
            null
        }
    }

    /**
     * @Deprecated This search method uses a legacy data structure.
     * Use memoryDao.searchMemoriesByKeywords() directly instead.
     */
    @Deprecated("This search method uses a legacy data structure.")
    suspend fun searchProblems(
        query: String,
        category: String? = null,
        limit: Int = 10
    ): List<ProblemRecord> = withContext(Dispatchers.IO) {
        try {
            val keywords = query.split(" ").filter { it.isNotBlank() }
            val memories = if (category != null) {
                memoryDao.searchMemoriesByKeywords(keywords.joinToString(","))
                    .filter { it.category == category }
            } else {
                memoryDao.searchMemoriesByKeywords(keywords.joinToString(","))
            }
            
            memories.take(limit).map { convertMemoryToProblemRecord(it) }
        } catch (e: Exception) {
            Log.e(TAG, "搜索问题失败", e)
            emptyList()
        }
    }

    /**
     * @Deprecated This method deletes legacy data.
     * Use memoryDao.deleteMemory() directly instead.
     */
    @Deprecated("This method deletes legacy data.")
    suspend fun deleteProblemSolution(problemId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            memoryDao.deleteMemoryById(problemId)
            Log.d(TAG, "问题解决方案已删除: $problemId")
            true
        } catch (e: Exception) {
            Log.e(TAG, "删除问题解决方案失败", e)
            false
        }
    }

    /**
     * @Deprecated This query method is for a legacy tool.
     * Use memoryDao query methods directly for better performance.
     */
    @Deprecated("This query method is for a legacy tool.")
    suspend fun getAllProblems(category: String? = null): List<ProblemRecord> = withContext(Dispatchers.IO) {
        try {
            val memories = if (category != null) {
                memoryDao.getAllMemories().filter { it.category == category }
            } else {
                memoryDao.getAllMemories()
            }
            
            memories.map { convertMemoryToProblemRecord(it) }
        } catch (e: Exception) {
            Log.e(TAG, "获取所有问题失败", e)
            emptyList()
        }
    }

    // 私有辅助方法
    private fun convertMemoryToProblemRecord(memory: Memory): ProblemRecord {
        return ProblemRecord(
            id = memory.id,
            problem = memory.content,
            solution = memory.solution ?: "",
            category = memory.category,
            keywords = memory.keywords.split(",").filter { it.isNotBlank() },
            timestamp = memory.createdAt,
            useCount = memory.useCount,
            rating = memory.rating
        )
    }

    private fun generateId(): String {
        return "problem_${System.currentTimeMillis()}_${(1000..9999).random()}"
    }
}
