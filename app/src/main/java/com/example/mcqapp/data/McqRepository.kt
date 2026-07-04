package com.example.mcqapp.data

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import com.example.mcqapp.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.mindrot.jbcrypt.BCrypt
import java.util.Locale

class McqRepository(private val dbHelper: McqDatabase) {

    suspend fun createUser(
        username: String,
        password: String,
        role: String,
        fullName: String = ""
    ): Boolean = withContext(Dispatchers.IO) {
        if (username.isBlank() || password.length < 4) return@withContext false

        val values = ContentValues().apply {
            put("full_name", fullName.trim())
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
            "SELECT id, username, full_name, role FROM users WHERE username = ?",
            arrayOf(username.trim())
        ).use { cursor ->
            if (!cursor.moveToFirst()) return@withContext null

            val hash = dbHelper.readableDatabase.rawQuery("SELECT password_hash FROM users WHERE username = ?", arrayOf(username.trim())).use { hCursor ->
                if (hCursor.moveToFirst()) hCursor.getString(0) else null
            } ?: return@withContext null
            
            if (!BCrypt.checkpw(password, hash)) return@withContext null

            User(
                id = cursor.getLong(0),
                username = cursor.getString(1),
                fullName = cursor.getString(2) ?: "",
                role = cursor.getString(3)
            )
        }
    }

    suspend fun addSubject(teacherId: Long, name: String, code: String, isContest: Boolean = false, startTime: Long = 0, duration: Int = 0): Boolean = withContext(Dispatchers.IO) {
        if (name.isBlank() || code.isBlank()) return@withContext false
        val values = ContentValues().apply { 
            put("teacher_id", teacherId)
            put("name", name.trim()) 
            put("subject_code", code.trim().uppercase())
            put("is_contest", if (isContest) 1 else 0)
            put("start_time", startTime)
            put("duration_min", duration)
        }
        runCatching {
            val db = dbHelper.writableDatabase
            db.insertOrThrow("subjects", null, values) > 0
        }.getOrDefault(false)
    }

    suspend fun updateSubject(subjectId: Long, name: String, code: String, isContest: Boolean, startTime: Long, duration: Int): Boolean = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put("name", name.trim())
            put("subject_code", code.trim().uppercase())
            put("is_contest", if (isContest) 1 else 0)
            put("start_time", startTime)
            put("duration_min", duration)
        }
        runCatching {
            dbHelper.writableDatabase.update("subjects", values, "id = ?", arrayOf(subjectId.toString())) > 0
        }.getOrDefault(false)
    }

    suspend fun getSubjectsForTeacher(teacherId: Long): List<SubjectItem> = withContext(Dispatchers.IO) {
        val items = mutableListOf<SubjectItem>()
        dbHelper.readableDatabase.rawQuery(
            "SELECT id, name, subject_code, is_contest, start_time, duration_min FROM subjects WHERE teacher_id = ? ORDER BY id", 
            arrayOf(teacherId.toString())
        ).use { cursor ->
            while (cursor.moveToNext()) {
                items += SubjectItem(
                    cursor.getLong(0), cursor.getString(1), cursor.getString(2), "",
                    cursor.getInt(3) == 1, cursor.getLong(4), cursor.getInt(5)
                )
            }
        }
        items
    }

    suspend fun searchSubject(userId: Long, subjectCode: String): SubjectItem? = withContext(Dispatchers.IO) {
        val db = dbHelper.readableDatabase
        db.rawQuery(
            """
            SELECT s.id, s.name, s.subject_code, u.full_name, s.is_contest, s.start_time, s.duration_min,
            (SELECT COUNT(*) FROM contest_registrations cr WHERE cr.user_id = ? AND cr.subject_id = s.id) as is_reg
            FROM subjects s
            JOIN users u ON s.teacher_id = u.id
            WHERE s.subject_code = ?
            """.trimIndent(),
            arrayOf(userId.toString(), subjectCode.trim().uppercase())
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                SubjectItem(
                    cursor.getLong(0), cursor.getString(1), cursor.getString(2), cursor.getString(3) ?: "",
                    cursor.getInt(4) == 1, cursor.getLong(5), cursor.getInt(6), cursor.getInt(7) > 0
                )
            } else null
        }
    }

    suspend fun registerForContest(userId: Long, subjectId: Long): Boolean = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put("user_id", userId)
            put("subject_id", subjectId)
        }
        runCatching {
            dbHelper.writableDatabase.insertOrThrow("contest_registrations", null, values) > 0
        }.getOrDefault(false)
    }

    suspend fun sendReminder(teacherId: Long, subjectId: Long, message: String): Int = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        var count = 0
        db.rawQuery(
            "SELECT user_id FROM contest_registrations WHERE subject_id = ?",
            arrayOf(subjectId.toString())
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val values = ContentValues().apply {
                    put("user_id", cursor.getLong(0))
                    put("subject_id", subjectId)
                    put("message", message)
                    put("type", "REMINDER")
                }
                if (db.insert("reminders", null, values) > 0) count++
            }
        }
        count
    }

    suspend fun getReminders(userId: Long): List<ReminderItem> = withContext(Dispatchers.IO) {
        val items = mutableListOf<ReminderItem>()
        dbHelper.readableDatabase.rawQuery(
            """
            SELECT r.id, r.subject_id, s.name, r.message, r.type, r.created_at
            FROM reminders r
            JOIN subjects s ON r.subject_id = s.id
            WHERE r.user_id = ?
            ORDER BY r.created_at DESC
            """.trimIndent(),
            arrayOf(userId.toString())
        ).use { cursor ->
            while (cursor.moveToNext()) {
                items += ReminderItem(
                    cursor.getLong(0), cursor.getLong(1), cursor.getString(2),
                    cursor.getString(3), cursor.getString(4), cursor.getString(5)
                )
            }
        }
        items
    }

    suspend fun getRegisteredContests(userId: Long): List<SubjectItem> = withContext(Dispatchers.IO) {
        val items = mutableListOf<SubjectItem>()
        dbHelper.readableDatabase.rawQuery(
            """
            SELECT s.id, s.name, s.subject_code, u.full_name, s.is_contest, s.start_time, s.duration_min
            FROM subjects s
            JOIN users u ON s.teacher_id = u.id
            JOIN contest_registrations cr ON cr.subject_id = s.id
            WHERE cr.user_id = ?
            ORDER BY s.start_time DESC
            """.trimIndent(),
            arrayOf(userId.toString())
        ).use { cursor ->
            while (cursor.moveToNext()) {
                items += SubjectItem(
                    cursor.getLong(0), cursor.getString(1), cursor.getString(2), cursor.getString(3) ?: "",
                    cursor.getInt(4) == 1, cursor.getLong(5), cursor.getInt(6), true
                )
            }
        }
        items
    }

    suspend fun getTeacherStats(teacherId: Long): Triple<Int, Int, Int> = withContext(Dispatchers.IO) {
        val db = dbHelper.readableDatabase
        val subjectCount = db.rawQuery("SELECT COUNT(*) FROM subjects WHERE teacher_id = ?", arrayOf(teacherId.toString())).use { cursor ->
            if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }
        val questionCount = db.rawQuery(
            "SELECT COUNT(*) FROM questions q JOIN subjects s ON q.subject_id = s.id WHERE s.teacher_id = ?",
            arrayOf(teacherId.toString())
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }
        val studentCount = db.rawQuery(
            "SELECT COUNT(DISTINCT user_id) FROM exam_results er JOIN subjects s ON er.subject_id = s.id WHERE s.teacher_id = ?",
            arrayOf(teacherId.toString())
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }
         Triple(subjectCount, questionCount, studentCount)
    }

    suspend fun getStudents(): List<StudentRow> = withContext(Dispatchers.IO) {
        val students = mutableListOf<StudentRow>()
        dbHelper.readableDatabase.rawQuery(
            """
            SELECT users.id, users.username, COUNT(exam_results.id) AS exam_count
            FROM users
            LEFT JOIN exam_results ON exam_results.user_id = users.id
            WHERE users.role = 'Student'
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
            SELECT exam_results.id, users.username, exam_results.total, exam_results.correct, exam_results.percent, exam_results.submitted_at
            FROM exam_results
            INNER JOIN users ON users.id = exam_results.user_id
            WHERE exam_results.subject_id = ?
            ORDER BY exam_results.submitted_at DESC
            """.trimIndent(),
            arrayOf(subjectId.toString())
        ).use { cursor ->
            while (cursor.moveToNext()) {
                results += ExamResultRow(
                    cursor.getLong(0),
                    cursor.getString(1),
                    cursor.getInt(2),
                    cursor.getInt(3),
                    cursor.getDouble(4),
                    cursor.getString(5)
                )
            }
        }
        results
    }

    suspend fun getDetailedAnswersForResult(resultId: Long): List<UserAnswerDetail> = withContext(Dispatchers.IO) {
        val answers = mutableListOf<UserAnswerDetail>()
        dbHelper.readableDatabase.rawQuery(
            """
            SELECT q.question_text, ua.selected_option, q.correct_option, ua.is_correct
            FROM user_answers ua
            JOIN questions q ON ua.question_id = q.id
            WHERE ua.result_id = ?
            ORDER BY ua.id
            """.trimIndent(),
            arrayOf(resultId.toString())
        ).use { cursor ->
            while (cursor.moveToNext()) {
                answers += UserAnswerDetail(
                    cursor.getString(0),
                    cursor.getString(1),
                    cursor.getString(2),
                    cursor.getInt(3) == 1
                )
            }
        }
        answers
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

    suspend fun deleteSubject(subjectId: Long): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val db = dbHelper.writableDatabase
            db.delete("subjects", "id = ?", arrayOf(subjectId.toString())) > 0
        }.getOrDefault(false)
    }

    suspend fun updateQuestion(questionId: Long, text: String, options: List<String>, correct: String): Boolean = withContext(Dispatchers.IO) {
        val normalizedCorrect = correct.trim().uppercase(Locale.US)
        val values = ContentValues().apply {
            put("question_text", text.trim())
            put("option_a", options[0].trim())
            put("option_b", options[1].trim())
            put("option_c", options[2].trim())
            put("option_d", options[3].trim())
            put("correct_option", normalizedCorrect)
        }
        runCatching {
            dbHelper.writableDatabase.update("questions", values, "id = ?", arrayOf(questionId.toString())) > 0
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

    suspend fun saveResult(userId: Long, subjectId: Long, total: Int, correct: Int, answers: List<Pair<Long, String>>): Double = withContext(Dispatchers.IO) {
        val percent = if (total == 0) 0.0 else (correct * 100.0) / total
        val db = dbHelper.writableDatabase
        db.beginTransaction()
        try {
            val values = ContentValues().apply {
                put("user_id", userId)
                put("subject_id", subjectId)
                put("total", total)
                put("correct", correct)
                put("percent", percent)
            }
            val resultId = db.insert("exam_results", null, values)
            
            if (resultId > 0) {
                answers.forEach { (qId, selected) ->
                    val qCorrect = db.rawQuery("SELECT correct_option FROM questions WHERE id = ?", arrayOf(qId.toString())).use { cursor ->
                        if (cursor.moveToFirst()) cursor.getString(0) else ""
                    }
                    val uaValues = ContentValues().apply {
                        put("result_id", resultId)
                        put("question_id", qId)
                        put("selected_option", selected)
                        put("is_correct", if (selected == qCorrect) 1 else 0)
                    }
                    db.insert("user_answers", null, uaValues)
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        percent
    }

    private fun countRows(tableName: String): Int {
        return dbHelper.readableDatabase.rawQuery("SELECT COUNT(*) FROM $tableName", null).use { cursor ->
            if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }
    }
}
