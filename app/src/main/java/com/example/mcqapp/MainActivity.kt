package com.example.mcqapp

import android.content.res.ColorStateList
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
import androidx.lifecycle.ViewModelProvider
import com.example.mcqapp.data.McqDatabase
import com.example.mcqapp.data.McqRepository
import com.example.mcqapp.ui.McqViewModel
import com.google.android.material.button.MaterialButton
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
data class ExamResultRow(
    val username: String,
    val total: Int,
    val correct: Int,
    val percent: Double,
    val submittedAt: String
)
data class StudentRow(
    val id: Long,
    val username: String,
    val examCount: Int
)
data class QuestionDraftViews(
    val question: EditText,
    val optionA: EditText,
    val optionB: EditText,
    val optionC: EditText,
    val optionD: EditText,
    val correct: EditText
)

class MainActivity : AppCompatActivity() {
    private lateinit var viewModel: McqViewModel
    private var currentAdminSubjectId: Long? = null

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

        val dbHelper = McqDatabase(this)
        val repository = McqRepository(dbHelper)

        viewModel = ViewModelProvider(this, object : ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                return McqViewModel(repository) as T
            }
        }).get(McqViewModel::class.java)

        viewModel.seedData()
        showLoginScreen()
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (viewModel.currentUser.value?.role == "admin") {
            toast("Admin panel থেকে Back চাপলে app direct বন্ধ হবে না। Login screen-এ নেওয়া হলো।")
            logout()
        } else {
            super.onBackPressed()
        }
    }

    private fun logout() {
        viewModel.setCurrentUser(null)
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
                    viewModel.login(username.text.toString(), password.text.toString()) { user ->
                        runOnUiThread {
                            if (user == null) {
                                toast("Username/password ভুল।")
                            } else {
                                viewModel.setCurrentUser(user)
                                if (user.role == "admin") showAdminPanel() else showExamScreen()
                            }
                        }
                    }
                })
                addView(outlineButton("Register as Student", "🎓") {
                    viewModel.register(username.text.toString(), password.text.toString()) { ok ->
                        runOnUiThread {
                            toast(if (ok) "Student account তৈরি হয়েছে। এখন login করো।" else "Register failed। username unique এবং password ৪+ character হতে হবে।")
                        }
                    }
                })
                addView(infoStrip("Default admin", "admin / admin123", warning))
            }
        )
        setContentView(scroll(root))
    }

    private fun showAdminPanel() {
        viewModel.getSubjects { subjects ->
            runOnUiThread {
                val selectedSubject = subjects.firstOrNull { it.id == currentAdminSubjectId } ?: subjects.firstOrNull()
                currentAdminSubjectId = selectedSubject?.id
                val root = screenRoot()

                // Navigation Bar
                val navBar = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(18), dp(10), dp(18), dp(10))
                    background = round(primaryDark, dp(20).toFloat())
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(60))

                    addView(text("Admin Dashboard", 16f, true, Color.WHITE, Gravity.START))

                    val studentIcon = TextView(this@MainActivity).apply {
                        text = "👤 Students"
                        textSize = 14f
                        setTextColor(Color.WHITE)
                        typeface = Typeface.DEFAULT_BOLD
                        setPadding(dp(12), dp(4), dp(12), dp(4))
                        background = round(Color.parseColor("#3F37B3"), dp(12).toFloat())
                        setOnClickListener { showStudentManagementPage() }
                    }

                    addView(View(this@MainActivity).apply {
                        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
                    })
                    addView(studentIcon)
                }
                root.addView(navBar)

                root.addView(heroCard("Admin Dashboard", "Question bank, students, exam report, delete সব এক জায়গায়", "${viewModel.currentUser.value?.username ?: "admin"} • Teacher Mode", false))

                viewModel.getStats { sCount, qCount, stCount ->
                    runOnUiThread {
                        root.addView(statsRow(listOf("Subjects" to sCount.toString(), "Questions" to qCount.toString(), "Students" to stCount.toString())))
                    }
                }

                val subjectName = input("New subject name")
                root.addView(
                    card().apply {
                        addView(sectionTitle("Add Subject", "নতুন course/subject তৈরি করো"))
                        addView(subjectName)
                        addView(primaryButton("Save Subject", "➕") {
                            val newSubjectName = subjectName.text.toString().trim()
                            viewModel.addSubject(newSubjectName) { ok, subjectId ->
                                runOnUiThread {
                                    if (ok) {
                                        currentAdminSubjectId = subjectId
                                    }
                                    toast(if (ok) "Subject save হয়েছে এবং select করা হয়েছে।" else "Subject save failed। নাম খালি/duplicate হতে পারে।")
                                    showAdminPanel()
                                }
                            }
                        })
                    }
                )

                if (subjects.isEmpty()) {
                    root.addView(infoStrip("No subject", "আগে একটি subject add করো, তারপর MCQ/result manage করা যাবে।", warning))
                    root.addView(dangerButton("Logout", "🚪") { logout() })
                    setContentView(scroll(root))
                    return@runOnUiThread
                }

                selectedSubject?.let { subject ->
                    root.addView(subjectSelectorCard(subjects, subject))
                    root.addView(bulkQuestionBuilderCard(subject))
                    root.addView(questionDeleteCard(subject))
                }
                root.addView(resultReportCard(subjects))
                root.addView(dangerButton("Logout", "🚪") { logout() })
                setContentView(scroll(root))
            }
        }
    }

    private fun showStudentManagementPage() {
        viewModel.getStudents { students ->
            runOnUiThread {
                val root = screenRoot()

                // Navigation Bar
                val navBar = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(18), dp(10), dp(18), dp(10))
                    background = round(primaryDark, dp(20).toFloat())
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(60))

                    addView(text("Student Management", 16f, true, Color.WHITE, Gravity.START))

                    val backBtn = TextView(this@MainActivity).apply {
                        text = "⬅ Back"
                        textSize = 14f
                        setTextColor(Color.WHITE)
                        typeface = Typeface.DEFAULT_BOLD
                        setPadding(dp(12), dp(4), dp(12), dp(4))
                        background = round(Color.parseColor("#3F37B3"), dp(12).toFloat())
                        setOnClickListener { showAdminPanel() }
                    }
                    addView(View(this@MainActivity).apply {
                        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
                    })
                    addView(backBtn)
                }
                root.addView(navBar)

                root.addView(heroCard("Student List", "সব Registered students এবং তাদের subject-wise marks দেখো", "Total Students: ${students.size}", false))

                if (students.isEmpty()) {
                    root.addView(card().apply {
                        addView(text("এখনও কোনো student register করেনি।", 15f, false, muted, Gravity.CENTER))
                    })
                } else {
                    students.forEach { student ->
                        root.addView(studentDetailCard(student))
                    }
                }

                root.addView(dangerButton("Logout", "🚪") { logout() })
                setContentView(scroll(root))
            }
        }
    }

    private fun studentDetailCard(student: StudentRow): LinearLayout = card().apply {
        addView(sectionTitle("👤 ${student.username}", "Student ID: ${student.id}"))

        viewModel.getDetailedResultsForStudent(student.id) { results ->
            runOnUiThread {
                if (results.isEmpty()) {
                    addView(text("এই student এখনও কোনো exam দেয়নি।", 13f, false, muted, Gravity.START))
                } else {
                    results.forEach { (subjectName, percent) ->
                        addView(subjectResultRow(subjectName, percent))
                    }
                }
            }
        }
    }

    private fun subjectResultRow(subjectName: String, percent: Double): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        background = round(softSurface, dp(12).toFloat(), Color.parseColor("#E2E8F0"), dp(1))
        setPadding(dp(12), dp(8), dp(12), dp(8))
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(6)
        }

        addView(text(subjectName, 14f, true, ink, Gravity.START))

        val scoreText = text("${String.format(Locale.US, "%.2f", percent)}%", 14f, true, if (percent >= 60.0) success else danger, Gravity.END)
        scoreText.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            gravity = Gravity.END
        }
        addView(scoreText)
    }

    private fun showExamScreen() {
        val user = viewModel.currentUser.value ?: return showLoginScreen()
        viewModel.getSubjects { subjects ->
            runOnUiThread {
                val root = screenRoot()
                root.addView(heroCard("Student Portal", "Subject select করে exam শুরু করো", "Welcome, ${user.username}", false))

                viewModel.getResultCountForUser(user.id) { myExams ->
                    runOnUiThread {
                        root.addView(statsRow(listOf("Subjects" to subjects.size.toString(), "My Exams" to myExams.toString())))
                    }
                }

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
        }
    }

    private fun showQuestionPaper(subject: SubjectItem) {
        viewModel.getQuestions(subject.id) { questions ->
            runOnUiThread {
                if (questions.isEmpty()) {
                    toast("এই subject-এ question নেই।")
                    showExamScreen()
                    return@runOnUiThread
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
                    val user = viewModel.currentUser.value ?: return@primaryButton
                    viewModel.saveExamResult(user.id, subject.id, questions.size, correct) { percent ->
                        runOnUiThread {
                            showResultScreen(questions.size, correct, percent)
                        }
                    }
                })
                root.addView(outlineButton("Back to Subjects", "⬅") { showExamScreen() })
                setContentView(scroll(root))
            }
        }
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

    private fun subjectSelectorCard(subjects: List<SubjectItem>, selected: SubjectItem?): LinearLayout = card().apply {
        addView(sectionTitle("Select Subject", "Manage questions for this subject"))
        val spinner = spinner(subjects)
        selected?.let {
            val index = subjects.indexOf(it)
            if (index >= 0) spinner.setSelection(index)
        }
        addView(spinner)
        addView(primaryButton("Update Selection", "✅") {
            val item = spinner.selectedItem as? SubjectItem
            if (item != null) {
                currentAdminSubjectId = item.id
                showAdminPanel()
            }
        })
    }

    private fun bulkQuestionBuilderCard(subject: SubjectItem): LinearLayout = card().apply {
        addView(sectionTitle("Add Questions", "Bulk add questions to ${subject.name}"))
        val questions = mutableListOf<QuestionDraftViews>()
        val container = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL }

        fun addDraft() {
            val draft = QuestionDraftViews(
                input("Question Text"),
                input("Option A"),
                input("Option B"),
                input("Option C"),
                input("Option D"),
                input("Correct Option (A/B/C/D)")
            )
            val row = card().apply {
                addView(text("Question", 12f, true, primary, Gravity.START))
                addView(draft.question)
                addView(draft.optionA)
                addView(draft.optionB)
                addView(draft.optionC)
                addView(draft.optionD)
                addView(draft.correct)
            }
            container.addView(row)
            questions.add(draft)
        }

        addDraft()
        addView(container)
        addView(outlineButton("Add Another", "➕") {
            addDraft()
            // Note: this won't refresh the UI since it's just adding to the list.
            // For a real app we'd need a better way to refresh the card.
            toast("Draft added. Please rebuild the screen to see it.")
        })
        addView(primaryButton("Save All", "💾") {
            val drafts = questions.map {
                Triple(it.question.text.toString(),
                      listOf(it.optionA.text.toString(), it.optionB.text.toString(), it.optionC.text.toString(), it.optionD.text.toString()),
                      it.correct.text.toString())
            }
            viewModel.addQuestions(subject.id, drafts) { saved, failed ->
                runOnUiThread {
                    toast("Saved: $saved, Failed: $failed")
                    showAdminPanel()
                }
            }
        })
    }

    private fun questionDeleteCard(subject: SubjectItem): LinearLayout = card().apply {
        addView(sectionTitle("Delete Questions", "Remove questions from ${subject.name}"))
        viewModel.getQuestions(subject.id) { questions ->
            runOnUiThread {
                val list = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL }
                questions.forEach { q ->
                    list.addView(LinearLayout(this@MainActivity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        addView(text(q.text, 13f, false, ink, Gravity.START).apply {
                            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                        })
                        addView(dangerButton("Delete", "🗑️") {
                            viewModel.deleteQuestion(q.id) { ok ->
                                runOnUiThread {
                                    toast(if (ok) "Deleted." else "Error.")
                                    showAdminPanel()
                                }
                            }
                        }.apply {
                            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                        })
                    })
                }
                addView(list)
            }
        }
    }

    private fun resultReportCard(subjects: List<SubjectItem>): LinearLayout = card().apply {
        addView(sectionTitle("Subject Reports", "Overall performance summary"))
        val list = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL }
        subjects.forEach { subject ->
            list.addView(resultRow(subject))
        }
        addView(list)
    }

    private fun resultRow(subject: SubjectItem): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(10), dp(8), dp(10), dp(8))
        addView(text(subject.name, 14f, true, ink, Gravity.START).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        addView(primaryButton("View Report", "📊") {
            // Implementation for viewing report could go here
            toast("Report for ${subject.name} is coming soon.")
        }.apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        })
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
