package com.example.mcqapp

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import org.mindrot.jbcrypt.BCrypt
import java.util.Locale

data class User(val id: Long, val username: String, val role: String)
data class SubjectItem(val id: Long, val name: String) {
    override fun toString(): String = "$id - $name"
}
data class Question(
    val id: Long,
    val text: String,
    val optionA: String,
    val optionB: String,
    val optionC: String,
    val optionD: String,
    val correctOption: String
)

class McqDatabase(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {
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

    fun createUser(username: String, password: String, role: String = "student"): Boolean {
        if (username.isBlank() || password.length < 4 || role !in setOf("admin", "student")) return false
        val values = ContentValues().apply {
            put("username", username.trim())
            put("password_hash", BCrypt.hashpw(password, BCrypt.gensalt(12)))
            put("role", role)
        }
        return runCatching { writableDatabase.insertOrThrow("users", null, values) > 0 }.getOrDefault(false)
    }

    fun login(username: String, password: String): User? {
        readableDatabase.rawQuery(
            "SELECT id, username, password_hash, role FROM users WHERE username = ?",
            arrayOf(username.trim())
        ).use { cursor ->
            if (!cursor.moveToFirst()) return null
            val hash = cursor.getString(2)
            if (!BCrypt.checkpw(password, hash)) return null
            return User(cursor.getLong(0), cursor.getString(1), cursor.getString(3))
        }
    }

    fun addSubject(name: String): Boolean {
        if (name.isBlank()) return false
        val values = ContentValues().apply { put("name", name.trim()) }
        return runCatching { writableDatabase.insertOrThrow("subjects", null, values) > 0 }.getOrDefault(false)
    }

    fun getSubjects(): List<SubjectItem> {
        val items = mutableListOf<SubjectItem>()
        readableDatabase.rawQuery("SELECT id, name FROM subjects ORDER BY id", null).use { cursor ->
            while (cursor.moveToNext()) items += SubjectItem(cursor.getLong(0), cursor.getString(1))
        }
        return items
    }

    fun addQuestion(subjectId: Long, text: String, options: List<String>, correct: String): Boolean {
        val normalizedCorrect = correct.trim().uppercase(Locale.US)
        if (subjectId <= 0 || text.isBlank() || options.any { it.isBlank() } || normalizedCorrect !in listOf("A", "B", "C", "D")) {
            return false
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
        return runCatching { writableDatabase.insertOrThrow("questions", null, values) > 0 }.getOrDefault(false)
    }

    fun getQuestions(subjectId: Long): List<Question> {
        val questions = mutableListOf<Question>()
        readableDatabase.rawQuery(
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
        return questions
    }

    fun saveResult(userId: Long, subjectId: Long, total: Int, correct: Int): Double {
        val percent = if (total == 0) 0.0 else (correct * 100.0) / total
        val values = ContentValues().apply {
            put("user_id", userId)
            put("subject_id", subjectId)
            put("total", total)
            put("correct", correct)
            put("percent", percent)
        }
        writableDatabase.insert("exam_results", null, values)
        return percent
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

class MainActivity : AppCompatActivity() {
    private lateinit var db: McqDatabase
    private var currentUser: User? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db = McqDatabase(this)
        showLoginScreen()
    }

    private fun showLoginScreen() {
        val username = input("Username")
        val password = input("Password", password = true)
        val root = verticalRoot("MCQ App Login")
        root.addView(username)
        root.addView(password)
        root.addView(button("Login") {
            val user = db.login(username.text.toString(), password.text.toString())
            if (user == null) {
                toast("Username/password ভুল।")
            } else {
                currentUser = user
                if (user.role == "admin") showAdminPanel() else showExamScreen()
            }
        })
        root.addView(button("Register as Student") {
            val ok = db.createUser(username.text.toString(), password.text.toString())
            toast(if (ok) "Student account তৈরি হয়েছে। এখন login করো।" else "Register failed। username unique এবং password ৪+ character হতে হবে।")
        })
        root.addView(text("Default admin: admin / admin123", 14f, false))
        setContentView(scroll(root))
    }

    private fun showAdminPanel() {
        val root = verticalRoot("Admin Panel")
        val subjectName = input("New subject name")
        root.addView(subjectName)
        root.addView(button("Add Subject") {
            toast(if (db.addSubject(subjectName.text.toString())) "Subject save হয়েছে।" else "Subject save failed।")
            showAdminPanel()
        })

        root.addView(text("Add Question", 22f, true))
        val subjects = db.getSubjects()
        val subjectSpinner = spinner(subjects)
        val question = input("Question")
        val optionA = input("Option A")
        val optionB = input("Option B")
        val optionC = input("Option C")
        val optionD = input("Option D")
        val correct = input("Correct option (A/B/C/D)")
        listOf(subjectSpinner, question, optionA, optionB, optionC, optionD, correct).forEach { root.addView(it) }
        root.addView(button("Save Question") {
            val subject = subjectSpinner.selectedItem as? SubjectItem
            val ok = subject != null && db.addQuestion(
                subject.id,
                question.text.toString(),
                listOf(optionA.text.toString(), optionB.text.toString(), optionC.text.toString(), optionD.text.toString()),
                correct.text.toString()
            )
            toast(if (ok) "Question save হয়েছে।" else "Question save failed। সব field ঠিক করো।")
        })
        root.addView(button("Logout") { logout() })
        setContentView(scroll(root))
    }

    private fun showExamScreen() {
        val root = verticalRoot("Student Exam")
        root.addView(text("Logged in: ${currentUser?.username}", 16f, false))
        val subjects = db.getSubjects()
        val subjectSpinner = spinner(subjects)
        root.addView(subjectSpinner)
        root.addView(button("Load Questions") {
            val subject = subjectSpinner.selectedItem as? SubjectItem
            if (subject == null) toast("আগে subject যোগ করো।") else showQuestionPaper(subject)
        })
        root.addView(button("Logout") { logout() })
        setContentView(scroll(root))
    }

    private fun showQuestionPaper(subject: SubjectItem) {
        val questions = db.getQuestions(subject.id)
        if (questions.isEmpty()) {
            toast("এই subject-এ question নেই।")
            showExamScreen()
            return
        }
        val root = verticalRoot("Exam: ${subject.name}")
        val answers = mutableMapOf<Long, RadioGroup>()
        questions.forEachIndexed { index, q ->
            root.addView(text("${index + 1}. ${q.text}", 18f, true))
            val group = RadioGroup(this).apply { orientation = RadioGroup.VERTICAL }
            mapOf("A" to q.optionA, "B" to q.optionB, "C" to q.optionC, "D" to q.optionD).forEach { (key, value) ->
                group.addView(RadioButton(this@MainActivity).apply {
                    text = "$key. $value"
                    tag = key
                })
            }
            answers[q.id] = group
            root.addView(group)
        }
        root.addView(button("Submit Exam") {
            var correct = 0
            questions.forEach { q ->
                val group = answers[q.id]
                val checked = group?.findViewById<RadioButton>(group.checkedRadioButtonId)
                if (checked?.tag == q.correctOption) correct++
            }
            val percent = db.saveResult(currentUser!!.id, subject.id, questions.size, correct)
            showResultScreen(questions.size, correct, percent)
        })
        root.addView(button("Back") { showExamScreen() })
        setContentView(scroll(root))
    }

    private fun showResultScreen(total: Int, correct: Int, percent: Double) {
        val root = verticalRoot("Result")
        root.addView(text("Total: $total", 22f, true))
        root.addView(text("Correct: $correct", 22f, true))
        root.addView(text("Percent: ${String.format(Locale.US, "%.2f", percent)}%", 22f, true))
        root.addView(button("Take Another Exam") { showExamScreen() })
        root.addView(button("Logout") { logout() })
        setContentView(scroll(root))
    }

    private fun logout() {
        currentUser = null
        showLoginScreen()
    }

    private fun verticalRoot(title: String): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        setPadding(32, 48, 32, 48)
        addView(text(title, 28f, true))
    }

    private fun scroll(child: View): ScrollView = ScrollView(this).apply { addView(child) }

    private fun text(value: String, size: Float, bold: Boolean): TextView = TextView(this).apply {
        text = value
        textSize = size
        setPadding(0, 12, 0, 12)
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    private fun input(hintText: String, password: Boolean = false): EditText = EditText(this).apply {
        hint = hintText
        setSingleLine(false)
        inputType = if (password) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD else InputType.TYPE_CLASS_TEXT
    }

    private fun button(label: String, action: () -> Unit): Button = Button(this).apply {
        text = label
        setOnClickListener { action() }
    }

    private fun spinner(items: List<SubjectItem>): Spinner = Spinner(this).apply {
        adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, items)
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()
}
