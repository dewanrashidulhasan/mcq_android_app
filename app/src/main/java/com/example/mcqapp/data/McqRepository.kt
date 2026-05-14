package com.example.mcqapp.data

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import com.example.mcqapp.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.mindrot.jbcrypt.BCrypt
import java.util.Locale

class McqRepository(private val dbHelper: McqDatabase) {

    suspend fun createUser(username: String, password: String, role: String = "student"): Boolean = withContext(Dispatchers.IO) {
        if (username.isBlank() || password.length < 4 || role !in setOf("admin", "student")) return@withContext false
        val values = ContentValues().apply {
            put("username", username.trim())
            put("password_hash", BCrypt.hashpw(password, BCrypt.gensalt(12)))
            put("role", role)
        }
        runCatching {
            val db = dbHelper.writableDatabase
            db.insertOrThrow("users", null, values) > 0
        }.getOrDefault(false)
    }

    suspend fun login(username: String, password: String): User? = withContext(Dispatchers.IO) {
        dbHelper.readableDatabase.rawQuery(
            "SELECT id, username, password_hash, role FROM users WHERE username = ?",
            arrayOf(username.trim())
        ).use { cursor ->
            if (!cursor.moveToFirst()) return@withContext null
            val hash = cursor.getString(2)
            if (!BCrypt.checkpw(password, hash)) return@withContext null
            User(cursor.getLong(0), cursor.getString(1), cursor.getString(3))
        }
    }

    suspend fun addSubject(name: String): Boolean = withContext(Dispatchers.IO) {
        if (name.isBlank()) return@withContext false
        val values = ContentValues().apply { put("name", name.trim()) }
        runCatching {
            val db = dbHelper.writableDatabase
            db.insertOrThrow("subjects", null, values) > 0
        }.getOrDefault(false)
    }

    suspend fun getSubjects(): List<SubjectItem> = withContext(Dispatchers.IO) {
        val items = mutableListOf<SubjectItem>()
        dbHelper.readableDatabase.rawQuery("SELECT id, name FROM subjects ORDER BY id", null).use { cursor ->
            while (cursor.moveToNext()) items += SubjectItem(cursor.getLong(0), cursor.getString(1))
        }
        items
    }

    suspend fun getSubjectCount(): Int = withContext(Dispatchers.IO) {
        countRows("subjects")
    }

    suspend fun getQuestionCount(): Int = withContext(Dispatchers.IO) {
        countRows("questions")
    }

    suspend fun getStudentCount(): Int = withContext(Dispatchers.IO) {
        dbHelper.readableDatabase.rawQuery("SELECT COUNT(*) FROM users WHERE role = 'student'", null).use { cursor ->
            if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }
    }

    suspend fun getStudents(): List<StudentRow> = withContext(Dispatchers.IO) {
        val students = mutableListOf<StudentRow>()
        dbHelper.readableDatabase.rawQuery(
            """
            SELECT users.id, users.username, COUNT(exam_results.id) AS exam_count
            FROM users
            LEFT JOIN exam_results ON exam_results.user_id = users.id
            WHERE users.role = 'student'
            GROUP BY users.id, users.username
            ORDER BY users.username COLLATE NOCASE
            """.trimIndent(),
            null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                students += StudentRow(cursor.getLong(0), cursor.getString(1), cursor.getInt(2))
            }
        }
        students
    }

    suspend fun getResultCountForUser(userId: Long): Int = withContext(Dispatchers.IO) {
        dbHelper.readableDatabase.rawQuery("SELECT COUNT(*) FROM exam_results WHERE user_id = ?", arrayOf(userId.toString())).use { cursor ->
            if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }
    }

    suspend fun getResultsForSubject(subjectId: Long): List<ExamResultRow> = withContext(Dispatchers.IO) {
        val results = mutableListOf<ExamResultRow>()
        dbHelper.readableDatabase.rawQuery(
            """
            SELECT users.username, exam_results.total, exam_results.correct, exam_results.percent, exam_results.submitted_at
            FROM exam_results
            INNER JOIN users ON users.id = exam_results.user_id
            WHERE exam_results.subject_id = ?
            ORDER BY exam_results.submitted_at DESC
            """.trimIndent(),
            arrayOf(subjectId.toString())
        ).use { cursor ->
            while (cursor.moveToNext()) {
                results += ExamResultRow(
                    cursor.getString(0),
                    cursor.getInt(1),
                    cursor.getInt(2),
                    cursor.getDouble(3),
                    cursor.getString(4)
                )
            }
        }
        results
    }

    suspend fun getStudentSubjectResults(userId: Long): List<Pair<String, Double>> = withContext(Dispatchers.IO) {
        val results = mutableListOf<Pair<String, Double>>()
        dbHelper.readableDatabase.rawQuery(
            """
            SELECT subjects.name, exam_results.percent
            FROM exam_results
            INNER JOIN subjects ON subjects.id = exam_results.subject_id
            WHERE exam_results.user_id = ?
            ORDER BY subjects.name COLLATE NOCASE
            """.trimIndent(),
            arrayOf(userId.toString())
        ).use { cursor ->
            while (cursor.moveToNext()) {
                results += (cursor.getString(0) to cursor.getDouble(1))
            }
        }
        results
    }

    suspend fun addQuestion(subjectId: Long, text: String, options: List<String>, correct: String): Boolean = withContext(Dispatchers.IO) {
        val normalizedCorrect = correct.trim().uppercase(Locale.US)
        if (subjectId <= 0 || text.isBlank() || options.size != 4 || options.any { it.isBlank() } || normalizedCorrect !in listOf("A", "B", "C", "D")) {
            return@withContext false
        }
        val values = ContentValues().apply {
            put("subject_id", subjectId)
            put("question_text", text.trim())
            put("option_a", options[0].trim())
            put("option_b", options[1].trim())
            put("option_c", options[2].trim())
            put("option_d", options[3].trim())
            put("correct_option", normalizedCorrect)
        }
        runCatching {
            val db = dbHelper.writableDatabase
            db.insertOrThrow("questions", null, values) > 0
        }.getOrDefault(false)
    }

    suspend fun deleteQuestion(questionId: Long): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val db = dbHelper.writableDatabase
            db.delete("questions", "id = ?", arrayOf(questionId.toString())) > 0
        }.getOrDefault(false)
    }

    suspend fun getQuestions(subjectId: Long): List<Question> = withContext(Dispatchers.IO) {
        val questions = mutableListOf<Question>()
        dbHelper.readableDatabase.rawQuery(
            """
            SELECT id, question_text, option_a, option_b, option_c, option_d, correct_option
            FROM questions
            WHERE subject_id = ?
            ORDER BY id
            """.trimIndent(),
            arrayOf(subjectId.toString())
        ).use { cursor ->
            while (cursor.moveToNext()) {
                questions += Question(
                    cursor.getLong(0), cursor.getString(1), cursor.getString(2), cursor.getString(3),
                    cursor.getString(4), cursor.getString(5), cursor.getString(6)
                )
            }
        }
        questions
    }

    suspend fun saveResult(userId: Long, subjectId: Long, total: Int, correct: Int): Double = withContext(Dispatchers.IO) {
        val percent = if (total == 0) 0.0 else (correct * 100.0) / total
        val values = ContentValues().apply {
            put("user_id", userId)
            put("subject_id", subjectId)
            put("total", total)
            put("correct", correct)
            put("percent", percent)
        }
        runCatching {
            val db = dbHelper.writableDatabase
            db.insert("exam_results", null, values)
        }
        percent
    }

    private fun countRows(tableName: String): Int {
        return dbHelper.readableDatabase.rawQuery("SELECT COUNT(*) FROM $tableName", null).use { cursor ->
            if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }
    }

    suspend fun seedAdminData() = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        val count = db.rawQuery("SELECT COUNT(*) FROM users", null).use {
            if (it.moveToFirst()) it.getInt(0) else 0
        }
        if (count == 0) {
            val adminValues = ContentValues().apply {
                put("username", "admin")
                put("password_hash", BCrypt.hashpw("admin123", BCrypt.gensalt(12)))
                put("role", "admin")
            }
            db.insert("users", null, adminValues)

            val subjectValues = ContentValues().apply { put("name", "General Knowledge") }
            val subjectId = db.insert("subjects", null, subjectValues)

            val demoQuestions = listOf(
                listOf("Android app কোন ভাষা দিয়ে বানানো যায়?", "Kotlin", "HTML only", "SQL only", "Photoshop", "A"),
                listOf("SQLite কী ধরনের database?", "Cloud only", "Local relational database", "Image editor", "Operating system", "B"),
                listOf("MCQ-এর full form কী?", "Multiple Choice Question", "Main Code Query", "Mobile Class Queue", "Modern Cloud Quiz", "A")
            )
            demoQuestions.forEach { q ->
                val values = ContentValues().apply {
                    put("subject_id", subjectId)
                    put("question_text", q[0])
                    put("option_a", q[1])
                    put("option_b", q[2])
                    put("option_c", q[3])
                    put("option_d", q[4])
                    put("correct_option", q[5])
                }
                db.insert("questions", null, values)
            }
        }
    }
}
