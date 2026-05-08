package com.example.mcqapp.data

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import com.example.mcqapp.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.mindrot.jbcrypt.BCrypt
import java.util.Locale

class McqDatabase(context: android.content.Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE users (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                username TEXT NOT NULL UNIQUE,
                password_hash TEXT NOT NULL,
                role TEXT NOT NULL CHECK(role IN ('admin', 'student')),
                created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE subjects (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL UNIQUE,
                created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE questions (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                subject_id INTEGER NOT NULL,
                question_text TEXT NOT NULL,
                option_a TEXT NOT NULL,
                option_b TEXT NOT NULL,
                option_c TEXT NOT NULL,
                option_d TEXT NOT NULL,
                correct_option TEXT NOT NULL CHECK(correct_option IN ('A','B','C','D')),
                created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                FOREIGN KEY(subject_id) REFERENCES subjects(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE exam_results (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                user_id INTEGER NOT NULL,
                subject_id INTEGER NOT NULL,
                total INTEGER NOT NULL,
                correct INTEGER NOT NULL,
                percent REAL NOT NULL,
                submitted_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE,
                FOREIGN KEY(subject_id) REFERENCES subjects(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )
        seedInitialData(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS exam_results")
        db.execSQL("DROP TABLE IF EXISTS questions")
        db.execSQL("DROP TABLE IF EXISTS subjects")
        db.execSQL("DROP TABLE IF EXISTS users")
        onCreate(db)
    }

    private fun seedInitialData(db: SQLiteDatabase) {
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

    companion object {
        private const val DATABASE_NAME = "mcq_app.db"
        private const val DATABASE_VERSION = 1
    }
}
