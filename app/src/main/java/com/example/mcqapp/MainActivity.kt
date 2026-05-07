package com.example.mcqapp

import android.content.ContentValues
import android.content.Context
import android.content.res.ColorStateList
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
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

    fun getSubjectCount(): Int = countRows("subjects")

    fun getQuestionCount(): Int = countRows("questions")

    fun getResultCountForUser(userId: Long): Int {
        readableDatabase.rawQuery("SELECT COUNT(*) FROM exam_results WHERE user_id = ?", arrayOf(userId.toString())).use { cursor ->
            cursor.moveToFirst()
            return cursor.getInt(0)
        }
    }

    fun addQuestion(subjectId: Long, text: String, options: List<String>, correct: String): Boolean {
        val normalizedCorrect = correct.trim().uppercase(Locale.US)
        if (subjectId <= 0 || text.isBlank() || options.size != 4 || options.any { it.isBlank() } || normalizedCorrect !in listOf("A", "B", "C", "D")) {
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

    private fun countRows(tableName: String): Int {
        readableDatabase.rawQuery("SELECT COUNT(*) FROM $tableName", null).use { cursor ->
            cursor.moveToFirst()
            return cursor.getInt(0)
        }
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

    private val primary = Color.parseColor("#6C5CE7")
    private val primaryDark = Color.parseColor("#201A52")
    private val accent = Color.parseColor("#00D2D3")
    private val success = Color.parseColor("#00B894")
    private val danger = Color.parseColor("#FF7675")
    private val warning = Color.parseColor("#FDCB6E")
    private val ink = Color.parseColor("#17202A")
    private val muted = Color.parseColor("#6B7280")
    private val surface = Color.WHITE
    private val softSurface = Color.parseColor("#F6F7FB")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = primaryDark
        db = McqDatabase(this)
        showLoginScreen()
    }

    private fun showLoginScreen() {
        val username = input("Username")
        val password = input("Password", password = true)
        val root = screenRoot()
        root.addView(heroCard("MCQ Pro", "Competition-ready offline quiz app", "Admin + Student + Result", true))
        root.addView(
            card().apply {
                addView(sectionTitle("Secure Login", "bcrypt password hash সহ role-based access"))
                addView(username)
                addView(password)
                addView(primaryButton("Login", "🔐") {
                    val user = db.login(username.text.toString(), password.text.toString())
                    if (user == null) {
                        toast("Username/password ভুল।")
                    } else {
                        currentUser = user
                        if (user.role == "admin") showAdminPanel() else showExamScreen()
                    }
                })
                addView(outlineButton("Register as Student", "🎓") {
                    val ok = db.createUser(username.text.toString(), password.text.toString())
                    toast(if (ok) "Student account তৈরি হয়েছে। এখন login করো।" else "Register failed। username unique এবং password ৪+ character হতে হবে।")
                })
                addView(infoStrip("Default admin", "admin / admin123", warning))
            }
        )
        setContentView(scroll(root))
    }

    private fun showAdminPanel() {
        val subjects = db.getSubjects()
        val root = screenRoot()
        root.addView(heroCard("Admin Dashboard", "Question bank সুন্দরভাবে manage করো", "${currentUser?.username ?: "admin"} • Teacher Mode", false))
        root.addView(statsRow(listOf("Subjects" to db.getSubjectCount().toString(), "Questions" to db.getQuestionCount().toString())))

        val subjectName = input("New subject name")
        root.addView(
            card().apply {
                addView(sectionTitle("Add Subject", "নতুন course/subject তৈরি করো"))
                addView(subjectName)
                addView(primaryButton("Save Subject", "➕") {
                    toast(if (db.addSubject(subjectName.text.toString())) "Subject save হয়েছে।" else "Subject save failed। নাম খালি/duplicate হতে পারে।")
                    showAdminPanel()
                })
            }
        )

        val subjectSpinner = spinner(subjects)
        val question = input("Question")
        val optionA = input("Option A")
        val optionB = input("Option B")
        val optionC = input("Option C")
        val optionD = input("Option D")
        val correct = input("Correct option (A/B/C/D)")
        root.addView(
            card().apply {
                addView(sectionTitle("Add MCQ Question", "চারটি option ও correct answer দাও"))
                addView(label("Subject"))
                addView(subjectSpinner)
                listOf(question, optionA, optionB, optionC, optionD, correct).forEach { addView(it) }
                addView(primaryButton("Save Question", "✅") {
                    val subject = subjectSpinner.selectedItem as? SubjectItem
                    val ok = subject != null && db.addQuestion(
                        subject.id,
                        question.text.toString(),
                        listOf(optionA.text.toString(), optionB.text.toString(), optionC.text.toString(), optionD.text.toString()),
                        correct.text.toString()
                    )
                    toast(if (ok) "Question save হয়েছে।" else "Question save failed। subject/correct option/field check করো।")
                })
            }
        )
        root.addView(dangerButton("Logout", "🚪") { logout() })
        setContentView(scroll(root))
    }

    private fun showExamScreen() {
        val user = currentUser ?: return showLoginScreen()
        val subjects = db.getSubjects()
        val root = screenRoot()
        root.addView(heroCard("Student Portal", "Subject select করে exam শুরু করো", "Welcome, ${user.username}", false))
        root.addView(statsRow(listOf("Subjects" to db.getSubjectCount().toString(), "My Exams" to db.getResultCountForUser(user.id).toString())))

        val subjectSpinner = spinner(subjects)
        root.addView(
            card().apply {
                addView(sectionTitle("Choose Exam", "Available subject থেকে question load করো"))
                addView(label("Subject"))
                addView(subjectSpinner)
                addView(primaryButton("Load Questions", "🚀") {
                    val subject = subjectSpinner.selectedItem as? SubjectItem
                    if (subject == null) toast("আগে subject যোগ করো।") else showQuestionPaper(subject)
                })
            }
        )
        root.addView(dangerButton("Logout", "🚪") { logout() })
        setContentView(scroll(root))
    }

    private fun showQuestionPaper(subject: SubjectItem) {
        val questions = db.getQuestions(subject.id)
        if (questions.isEmpty()) {
            toast("এই subject-এ question নেই।")
            showExamScreen()
            return
        }
        val root = screenRoot()
        root.addView(heroCard("Exam: ${subject.name}", "সব question answer করে submit করো", "${questions.size} Questions", false))
        val answers = mutableMapOf<Long, RadioGroup>()
        questions.forEachIndexed { index, q ->
            root.addView(questionCard(index + 1, q, answers))
        }
        root.addView(primaryButton("Submit Exam", "🏁") {
            var correct = 0
            questions.forEach { q ->
                val group = answers[q.id]
                val checked = group?.findViewById<RadioButton>(group.checkedRadioButtonId)
                if (checked?.tag == q.correctOption) correct++
            }
            val percent = db.saveResult(currentUser!!.id, subject.id, questions.size, correct)
            showResultScreen(questions.size, correct, percent)
        })
        root.addView(outlineButton("Back to Subjects", "⬅") { showExamScreen() })
        setContentView(scroll(root))
    }

    private fun showResultScreen(total: Int, correct: Int, percent: Double) {
        val root = screenRoot()
        root.addView(heroCard("Result Published", "তোমার score database-এ save হয়েছে", resultEmoji(percent), false))
        root.addView(
            card().apply {
                val percentText = String.format(Locale.US, "%.2f%%", percent)
                addView(bigScore(percentText))
                addView(statsRow(listOf("Total" to total.toString(), "Correct" to correct.toString())))
                addView(infoStrip("Performance", performanceMessage(percent), if (percent >= 60.0) success else danger))
            }
        )
        root.addView(primaryButton("Take Another Exam", "🔁") { showExamScreen() })
        root.addView(dangerButton("Logout", "🚪") { logout() })
        setContentView(scroll(root))
    }

    private fun logout() {
        currentUser = null
        showLoginScreen()
    }

    private fun screenRoot(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(18), dp(20), dp(18), dp(28))
        background = gradient(intArrayOf(Color.parseColor("#EEF2FF"), Color.parseColor("#FDFBFF")), GradientDrawable.Orientation.TOP_BOTTOM, dp(0).toFloat())
    }

    private fun heroCard(title: String, subtitle: String, badge: String, showPicture: Boolean): LinearLayout = card(primaryDark).apply {
        addView(
            LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(dp(20), dp(22), dp(20), dp(22))
                if (showPicture) addView(pictureSection())
                addView(chip(badge, accent))
                addView(text(title, 30f, true, Color.WHITE, Gravity.CENTER))
                addView(text(subtitle, 15f, false, Color.parseColor("#DCE6FF"), Gravity.CENTER))
            }
        )
    }

    private fun pictureSection(): FrameLayout = FrameLayout(this).apply {
        layoutParams = LinearLayout.LayoutParams(dp(150), dp(118)).apply { bottomMargin = dp(14) }
        background = gradient(intArrayOf(Color.parseColor("#8E7CFF"), Color.parseColor("#00D2D3")), GradientDrawable.Orientation.TL_BR, dp(30).toFloat())
        addView(
            ImageView(this@MainActivity).apply {
                setImageResource(R.drawable.ic_quiz_hero)
                alpha = 0.95f
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                setPadding(dp(20), dp(14), dp(20), dp(14))
                layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            }
        )
        addView(
            TextView(this@MainActivity).apply {
                text = "MCQ"
                textSize = 13f
                setTextColor(primaryDark)
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                background = round(Color.WHITE, dp(18).toFloat())
                layoutParams = FrameLayout.LayoutParams(dp(64), dp(34), Gravity.BOTTOM or Gravity.END).apply {
                    rightMargin = dp(8)
                    bottomMargin = dp(8)
                }
            }
        )
    }

    private fun card(color: Int = surface): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        elevation = dp(8).toFloat()
        background = round(color, dp(26).toFloat(), if (color == surface) Color.parseColor("#E7E9F5") else color, dp(1))
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(14)
        }
        setPadding(dp(18), dp(18), dp(18), dp(18))
    }

    private fun sectionTitle(title: String, subtitle: String): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        addView(text(title, 22f, true, ink, Gravity.START))
        addView(text(subtitle, 13f, false, muted, Gravity.START))
    }

    private fun questionCard(number: Int, question: Question, answers: MutableMap<Long, RadioGroup>): LinearLayout = card().apply {
        addView(chip("Question $number", primary))
        addView(text(question.text, 18f, true, ink, Gravity.START))
        val group = RadioGroup(this@MainActivity).apply {
            orientation = RadioGroup.VERTICAL
            setPadding(0, dp(6), 0, 0)
        }
        mapOf("A" to question.optionA, "B" to question.optionB, "C" to question.optionC, "D" to question.optionD).forEach { (key, value) ->
            group.addView(optionButton(key, value))
        }
        answers[question.id] = group
        addView(group)
    }

    private fun optionButton(key: String, value: String): RadioButton = RadioButton(this).apply {
        text = "$key. $value"
        tag = key
        textSize = 15f
        setTextColor(ink)
        buttonTintList = ColorStateList.valueOf(primary)
        background = round(softSurface, dp(18).toFloat())
        setPadding(dp(14), dp(10), dp(14), dp(10))
        layoutParams = RadioGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(8)
        }
    }

    private fun statsRow(items: List<Pair<String, String>>): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(14) }
        items.forEachIndexed { index, item ->
            addView(statBox(item.first, item.second).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    if (index > 0) leftMargin = dp(8)
                }
            })
        }
    }

    private fun statBox(label: String, value: String): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        elevation = dp(4).toFloat()
        background = round(surface, dp(20).toFloat(), Color.parseColor("#E7E9F5"), dp(1))
        setPadding(dp(14), dp(14), dp(14), dp(14))
        addView(
            LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                addView(text(value, 26f, true, primary, Gravity.CENTER))
                addView(text(label, 12f, false, muted, Gravity.CENTER))
            }
        )
    }

    private fun bigScore(value: String): TextView = text(value, 46f, true, primary, Gravity.CENTER).apply {
        background = gradient(intArrayOf(Color.parseColor("#F4F1FF"), Color.parseColor("#E9FBFF")), GradientDrawable.Orientation.LEFT_RIGHT, dp(26).toFloat())
        setPadding(dp(16), dp(18), dp(16), dp(18))
    }

    private fun input(hintText: String, password: Boolean = false): EditText = EditText(this).apply {
        hint = hintText
        textSize = 15f
        setTextColor(ink)
        setHintTextColor(muted)
        setSingleLine(false)
        minHeight = dp(54)
        background = round(softSurface, dp(16).toFloat(), Color.parseColor("#E2E8F0"), dp(1))
        setPadding(dp(16), dp(10), dp(16), dp(10))
        inputType = if (password) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD else InputType.TYPE_CLASS_TEXT
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(10)
        }
    }

    private fun spinner(items: List<SubjectItem>): Spinner = Spinner(this).apply {
        adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, items)
        background = round(softSurface, dp(16).toFloat(), Color.parseColor("#E2E8F0"), dp(1))
        setPadding(dp(12), dp(8), dp(12), dp(8))
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)).apply { topMargin = dp(8) }
    }

    private fun primaryButton(label: String, icon: String, action: () -> Unit): MaterialButton = fancyButton(label, icon, primary, Color.WHITE, action)

    private fun dangerButton(label: String, icon: String, action: () -> Unit): MaterialButton = fancyButton(label, icon, danger, Color.WHITE, action)

    private fun outlineButton(label: String, icon: String, action: () -> Unit): MaterialButton = fancyButton(label, icon, Color.TRANSPARENT, primary, action).apply {
        strokeWidth = dp(1)
        strokeColor = ColorStateList.valueOf(primary)
        backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
    }

    private fun fancyButton(label: String, icon: String, backgroundColor: Int, textColor: Int, action: () -> Unit): MaterialButton = MaterialButton(this).apply {
        text = "$icon  $label"
        textSize = 14f
        typeface = Typeface.DEFAULT_BOLD
        isAllCaps = false
        cornerRadius = dp(18)
        setTextColor(textColor)
        backgroundTintList = ColorStateList.valueOf(backgroundColor)
        minHeight = dp(54)
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)).apply { topMargin = dp(12) }
        setOnClickListener { action() }
    }

    private fun label(value: String): TextView = text(value, 13f, true, muted, Gravity.START).apply {
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(12) }
    }

    private fun chip(value: String, color: Int): TextView = text(value, 12f, true, Color.WHITE, Gravity.CENTER).apply {
        background = round(color, dp(18).toFloat())
        setPadding(dp(12), dp(6), dp(12), dp(6))
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(10) }
    }

    private fun infoStrip(label: String, value: String, color: Int): TextView = text("$label: $value", 13f, true, ink, Gravity.CENTER).apply {
        background = round(adjustAlpha(color, 0.18f), dp(16).toFloat(), adjustAlpha(color, 0.7f), dp(1))
        setPadding(dp(12), dp(10), dp(12), dp(10))
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(12) }
    }

    private fun text(value: String, size: Float, bold: Boolean, color: Int, gravityValue: Int): TextView = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        gravity = gravityValue
        includeFontPadding = true
        if (bold) typeface = Typeface.DEFAULT_BOLD
        setPadding(0, dp(4), 0, dp(4))
    }

    private fun scroll(child: View): ScrollView = ScrollView(this).apply {
        isFillViewport = true
        addView(child)
    }

    private fun resultEmoji(percent: Double): String = when {
        percent >= 80.0 -> "🏆 Excellent"
        percent >= 60.0 -> "🌟 Good Job"
        percent >= 40.0 -> "📘 Keep Practicing"
        else -> "💪 Try Again"
    }

    private fun performanceMessage(percent: Double): String = when {
        percent >= 80.0 -> "Competition level preparation ভালো চলছে।"
        percent >= 60.0 -> "ভালো, তবে আরও practice করলে score বাড়বে।"
        percent >= 40.0 -> "Basic clear হচ্ছে, ভুল question review দরকার।"
        else -> "Foundation weak, topic ধরে ধরে revise করো।"
    }

    private fun gradient(colors: IntArray, orientation: GradientDrawable.Orientation, radius: Float): GradientDrawable = GradientDrawable(orientation, colors).apply {
        cornerRadius = radius
    }

    private fun round(color: Int, radius: Float, strokeColor: Int? = null, strokeWidth: Int = 0): GradientDrawable = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radius
        if (strokeColor != null) setStroke(strokeWidth, strokeColor)
    }

    private fun adjustAlpha(color: Int, factor: Float): Int {
        val alpha = (Color.alpha(color) * factor).toInt()
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()
}
