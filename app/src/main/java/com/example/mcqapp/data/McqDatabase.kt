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
// =============================================================================

class McqDatabase(context: android.content.Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        // TABLE 1: USERS
        db.execSQL(
            """
            CREATE TABLE users (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                full_name TEXT,
                username TEXT NOT NULL UNIQUE,
                password_hash TEXT NOT NULL,
                role TEXT NOT NULL,
                phone TEXT DEFAULT '',
                email TEXT DEFAULT '',
                unique_code TEXT UNIQUE,
                created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
            )
            """.trimIndent()
        )

        // TABLE 2: SUBJECTS
        db.execSQL(
            """
            CREATE TABLE subjects (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                teacher_id INTEGER NOT NULL,
                name TEXT NOT NULL,
                subject_code TEXT NOT NULL,
                is_contest INTEGER DEFAULT 0,
                start_time INTEGER DEFAULT 0,
                duration_min INTEGER DEFAULT 0,
                created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                UNIQUE(teacher_id, subject_code),
                FOREIGN KEY(teacher_id) REFERENCES users(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )

        // TABLE 6: CONTEST_REGISTRATIONS
        db.execSQL(
            """
            CREATE TABLE contest_registrations (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                user_id INTEGER NOT NULL,
                subject_id INTEGER NOT NULL,
                registered_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                UNIQUE(user_id, subject_id),
                FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE,
                FOREIGN KEY(subject_id) REFERENCES subjects(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )

        // TABLE 7: REMINDERS/NOTIFICATIONS
        db.execSQL(
            """
            CREATE TABLE reminders (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                user_id INTEGER NOT NULL,
                subject_id INTEGER NOT NULL,
                message TEXT NOT NULL,
                type TEXT NOT NULL,
                is_read INTEGER DEFAULT 0,
                created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE,
                FOREIGN KEY(subject_id) REFERENCES subjects(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )

        // TABLE 3: QUESTIONS
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

        // TABLE 4: EXAM_RESULTS
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

        // TABLE 5: USER_ANSWERS - Stores individual question responses
        db.execSQL(
            """
            CREATE TABLE user_answers (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                result_id INTEGER NOT NULL,
                question_id INTEGER NOT NULL,
                selected_option TEXT NOT NULL,
                is_correct INTEGER NOT NULL,
                FOREIGN KEY(result_id) REFERENCES exam_results(id) ON DELETE CASCADE,
                FOREIGN KEY(question_id) REFERENCES questions(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )
        seedInitialData(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // For this modernization phase, we reset the DB to apply the new schema.
        db.execSQL("DROP TABLE IF EXISTS user_answers")
        db.execSQL("DROP TABLE IF EXISTS exam_results")
        db.execSQL("DROP TABLE IF EXISTS reminders")
        db.execSQL("DROP TABLE IF EXISTS contest_registrations")
        db.execSQL("DROP TABLE IF EXISTS questions")
        db.execSQL("DROP TABLE IF EXISTS subjects")
        db.execSQL("DROP TABLE IF EXISTS users")
        onCreate(db)
    }

    private fun seedInitialData(db: SQLiteDatabase) {
        // Seed Super Admin
        val superAdminValues = ContentValues().apply {
            put("full_name", "System Super Admin")
            put("username", "superadmin")
            put("password_hash", BCrypt.hashpw("superadmin123", BCrypt.gensalt(12)))
            put("role", "SuperAdmin")
        }
        db.insert("users", null, superAdminValues)

        // Seed Default Admin
        val adminValues = ContentValues().apply {
            put("full_name", "Default Admin")
            put("username", "admin")
            put("password_hash", BCrypt.hashpw("admin123", BCrypt.gensalt(12)))
            put("role", "Admin")
        }
        db.insert("users", null, adminValues)
    }

    companion object {
        private const val DATABASE_NAME = "mcq_app.db"
        private const val DATABASE_VERSION = 5
    }
}
