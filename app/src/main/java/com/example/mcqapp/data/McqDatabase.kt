package com.example.mcqapp.data

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.example.mcqapp.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.mindrot.jbcrypt.BCrypt
import java.util.Locale

// =============================================================================
// MCQ Database Class - SQLite Database Handler for Quiz App
// Total Tables: 4 (users | subjects | questions | exam_results)
// Total Sample Data: 1 user | 1 subject | 3 questions | 0 exam_results
// =============================================================================

class McqDatabase(context: android.content.Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    // ===========================================================================
    // TABLE 1: USERS - Stores user accounts (Admin & Students)
    // Total Records: 1 (1 Admin, 0 Students)
    // Fields: id | username | password_hash | role | created_at
    // Role Values: 'admin' | 'student'
    // ===========================================================================
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

    // ===========================================================================
    // TABLE 2: SUBJECTS - Stores subject/category names
    // Total Records: 1 (General Knowledge)
    // Fields: id | name | created_at
    // ===========================================================================
        db.execSQL(
            """
            CREATE TABLE subjects (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL UNIQUE,
                created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
            )
            """.trimIndent()
        )

    // ===========================================================================
    // TABLE 3: QUESTIONS - Stores MCQ questions with 4 options
    // Total Records: 3 (Demo questions)
    // Fields: id | subject_id | question_text | option_a | option_b | option_c | option_d | correct_option | created_at
    // Correct Option Values: 'A' | 'B' | 'C' | 'D'
    // Foreign Key: subject_id -> subjects(id)
    // ===========================================================================
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

    // ===========================================================================
    // TABLE 4: EXAM_RESULTS - Stores user exam attempt results
    // Total Records: 0 (No exams taken yet)
    // Fields: id | user_id | subject_id | total | correct | percent | submitted_at
    // Foreign Keys: user_id -> users(id) | subject_id -> subjects(id)
    // ===========================================================================
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

    // ===========================================================================
    // SEED DATA - Initial sample data for demo/testing
    // Creates: 1 Admin User | 1 Subject | 3 Questions
    // ===========================================================================
    private fun seedInitialData(db: SQLiteDatabase) {

        // --- USER SEED: 1 Admin Account ---
        // Username: admin | Password: admin123 (BCrypt hashed)
        // Role: admin
        val adminValues = ContentValues().apply {
            put("username", "admin")
            put("password_hash", BCrypt.hashpw("admin123", BCrypt.gensalt(12)))
            put("role", "admin")
        }
        db.insert("users", null, adminValues)

        // --- SUBJECT SEED: 1 Subject ---
        // Name: General Knowledge
        val subjectValues = ContentValues().apply { put("name", "General Knowledge") }
        val subjectId = db.insert("subjects", null, subjectValues)

        // --- QUESTIONS SEED: 3 Demo Questions ---
        // Question Format: [question_text, option_a, option_b, option_c, option_d, correct_answer]
        val demoQuestions = listOf(
            listOf("Android app কোন ভাষা দিয়ে বানানো যায়?", "Kotlin", "HTML only", "SQL only", "Photoshop", "A"),
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
        // Database file name stored in: data/data/com.example.mcqapp/databases/mcq_app.db
        private const val DATABASE_NAME = "mcq_app.db"
        private const val DATABASE_VERSION = 1
    }
}