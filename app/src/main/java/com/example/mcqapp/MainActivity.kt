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
import com.example.mcqapp.data.*
import com.example.mcqapp.ui.McqViewModel
import com.google.android.material.button.MaterialButton
import java.util.Locale

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
        val user = viewModel.currentUser.value
        if (user != null) {
            toast("Log out না করা পর্যন্ত অ্যাপ থেকে বের হওয়া যাবে না।")
            // Instead of super.onBackPressed(), we do nothing or return to the main dashboard
            if (user.role == "Student") showExamScreen() else showAdminPanel()
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
        root.addView(heroCard("MCQ Pro", "Competition-ready offline quiz app", "Secure Login", true))
        
        root.addView(
            card().apply {
                addView(sectionTitle("Account Login", "আপনার একাউন্টে প্রবেশ করুন"))
                addView(username)
                addView(password)
                
                addView(primaryButton("Login to System", "🔐") {
                    val u = username.text.toString()
                    val p = password.text.toString()
                    if (u.isBlank() || p.isBlank()) {
                        toast("Username এবং Password প্রদান করুন।")
                        return@primaryButton
                    }
                    viewModel.login(u, p) { user ->
                        runOnUiThread {
                            if (user == null) {
                                toast("Username/password ভুল।")
                            } else {
                                viewModel.setCurrentUser(user)
                                if (user.role == "Student") showExamScreen() else showAdminPanel()
                            }
                        }
                    }
                })
                
                addView(View(this@MainActivity).apply { 
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)).apply { 
                        setMargins(0, dp(25), 0, dp(15))
                    }
                    setBackgroundColor(Color.parseColor("#E2E8F0"))
                })
                
                addView(text("Don't have an account?", 13f, false, muted, Gravity.CENTER))
                
                val regLayout = LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    weightSum = 2f
                }
                
                regLayout.addView(outlineButton("Student Register", "🎓") {
                    showRegisterScreen("Student")
                }.apply {
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = dp(5) }
                })

                regLayout.addView(outlineButton("Teacher Register", "👨‍🏫") {
                    showRegisterScreen("Teacher")
                }.apply {
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = dp(5) }
                })
                
                addView(regLayout)
                addView(infoStrip("Admin Demo", "admin / admin123", warning))
            }
        )
        setContentView(scroll(root))
    }

    private fun showRegisterScreen(role: String) {
        val fullName = input("Full Name")
        val username = input("Username")
        val password = input("Password", password = true)
        
        val root = screenRoot()
        root.addView(heroCard("Register", "Create a new $role account", role, false))
        
        root.addView(
            card().apply {
                addView(sectionTitle("Sign Up", "তথ্য দিয়ে রেজিস্ট্রেশন সম্পন্ন করুন"))
                addView(fullName)
                addView(username)
                addView(password)
                
                addView(primaryButton("Register Now", "📝") {
                    val f = fullName.text.toString()
                    val u = username.text.toString()
                    val p = password.text.toString()
                    
                    if (u.isBlank() || p.isBlank()) {
                        toast("সবগুলো ঘর পূরণ করুন।")
                        return@primaryButton
                    }
                    
                    viewModel.register(u, p, role, f) { ok ->
                        runOnUiThread {
                            if (ok) {
                                toast("রেজিস্ট্রেশন সফল হয়েছে! এখন লগইন করুন।")
                                showLoginScreen()
                            } else {
                                toast("রেজিস্ট্রেশন ব্যর্থ। ইউজারনেমটি সম্ভবত আগে ব্যবহার করা হয়েছে।")
                            }
                        }
                    }
                })
                
                addView(outlineButton("Back to Login", "⬅") { showLoginScreen() })
            }
        )
        setContentView(scroll(root))
    }

    private fun showAdminPanel() {
        val user = viewModel.currentUser.value ?: return showLoginScreen()
        viewModel.getSubjects(user.id) { subjects ->
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

                    addView(text("Admin Panel", 16f, true, Color.WHITE, Gravity.START))

                    val reminderIcon = TextView(this@MainActivity).apply {
                        text = "🔔 Reminders"
                        textSize = 14f
                        setTextColor(Color.WHITE)
                        typeface = Typeface.DEFAULT_BOLD
                        setPadding(dp(12), dp(4), dp(12), dp(4))
                        background = round(Color.parseColor("#3F37B3"), dp(12).toFloat())
                        setOnClickListener { showReminderManagementPage() }
                    }

                    addView(View(this@MainActivity).apply {
                        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
                    })
                    addView(reminderIcon)
                }
                root.addView(navBar)

                val subTitle = "Admin Mode"
                root.addView(heroCard(user.username, subTitle, user.role, false))

                viewModel.getStats(user.id) { sCount, qCount, stCount ->
                    runOnUiThread {
                        root.addView(statsRow(listOf("Subjects" to sCount.toString(), "Questions" to qCount.toString(), "Students" to stCount.toString())))
                    }
                }

                val subjectName = input("Subject Name (e.g. Physics)")
                val subjectCode = input("Subject Code (e.g. PHY101)")
                val contestSwitch = CheckBox(this@MainActivity).apply {
                    text = "Enable Contest Mode"
                    setTextColor(ink)
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) }
                }
                val contestLayout = LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    visibility = View.GONE
                }
                val startTimeInput = input("Start Time (Click to select)")
                val durationInput = input("Duration (Minutes, e.g. 60)")
                val dateInput = input("Start Date (Click to select)")
                
                val calendar = java.util.Calendar.getInstance()
                
                dateInput.isFocusable = false
                dateInput.setOnClickListener {
                    android.app.DatePickerDialog(this, { _, y, m, d ->
                        calendar.set(java.util.Calendar.YEAR, y)
                        calendar.set(java.util.Calendar.MONTH, m)
                        calendar.set(java.util.Calendar.DAY_OF_MONTH, d)
                        dateInput.setText(String.format(Locale.US, "%04d-%02d-%02d", y, m + 1, d))
                    }, calendar.get(java.util.Calendar.YEAR), calendar.get(java.util.Calendar.MONTH), calendar.get(java.util.Calendar.DAY_OF_MONTH)).show()
                }
                
                startTimeInput.isFocusable = false
                startTimeInput.setOnClickListener {
                    android.app.TimePickerDialog(this, { _, h, min ->
                        calendar.set(java.util.Calendar.HOUR_OF_DAY, h)
                        calendar.set(java.util.Calendar.MINUTE, min)
                        calendar.set(java.util.Calendar.SECOND, 0)
                        calendar.set(java.util.Calendar.MILLISECOND, 0)
                        val amPm = if (h < 12) "AM" else "PM"
                        val h12 = if (h % 12 == 0) 12 else h % 12
                        startTimeInput.setText(String.format(Locale.US, "%02d:%02d %s", h12, min, amPm))
                    }, calendar.get(java.util.Calendar.HOUR_OF_DAY), calendar.get(java.util.Calendar.MINUTE), false).show()
                }

                contestLayout.addView(dateInput)
                contestLayout.addView(startTimeInput)
                contestLayout.addView(durationInput)

                contestSwitch.setOnCheckedChangeListener { _, isChecked ->
                    contestLayout.visibility = if (isChecked) View.VISIBLE else View.GONE
                }
                
                root.addView(
                    card().apply {
                        addView(sectionTitle("Create New Subject", "Unique code সহ সাবজেক্ট তৈরি করুন"))
                        addView(subjectName)
                        addView(subjectCode)
                        addView(contestSwitch)
                        addView(contestLayout)
                        addView(primaryButton("Save Subject", "➕") {
                            val name = subjectName.text.toString().trim()
                            val code = subjectCode.text.toString().trim().uppercase()
                            if (name.isEmpty() || code.isEmpty()) {
                                toast("নাম এবং কোড উভয়ই প্রদান করুন।")
                                return@primaryButton
                            }
                            
                            var startTime: Long = 0
                            var duration = 0
                            if (contestSwitch.isChecked) {
                                if (dateInput.text.isEmpty() || startTimeInput.text.isEmpty() || durationInput.text.isEmpty()) {
                                    toast("তারিখ, সময় এবং ডিউরেশন প্রদান করুন।")
                                    return@primaryButton
                                }
                                startTime = calendar.timeInMillis
                                try {
                                    duration = durationInput.text.toString().toInt()
                                } catch (e: Exception) {
                                    toast("সঠিক ডিউরেশন দিন।")
                                    return@primaryButton
                                }
                            }

                            viewModel.addSubject(user.id, name, code, contestSwitch.isChecked, startTime, duration) { ok, subjectId ->
                                runOnUiThread {
                                    if (ok) {
                                        currentAdminSubjectId = subjectId
                                        toast("Subject তৈরি হয়েছে।")
                                        showAdminPanel()
                                    } else {
                                        toast("ব্যর্থ! কোডটি সম্ভবত ডুপ্লিকেট।")
                                    }
                                }
                            }
                        })
                    }
                )

                if (subjects.isNotEmpty()) {
                    selectedSubject?.let { subject ->
                        root.addView(subjectSelectorCard(subjects, subject))
                        root.addView(bulkQuestionBuilderCard(subject))
                        root.addView(questionDeleteCard(subject))
                        root.addView(resultReportCard(subjects))
                    }
                }

                root.addView(dangerButton("Logout", "🚪") { logout() })
                setContentView(swipeRefresh(root) { showAdminPanel() })
            }
        }
    }

    private fun showReminderManagementPage() {
        val user = viewModel.currentUser.value ?: return
        viewModel.getSubjects(user.id) { subjects ->
            runOnUiThread {
                val root = screenRoot()
                
                // Navigation Bar
                val navBar = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(18), dp(10), dp(18), dp(10))
                    background = round(primaryDark, dp(20).toFloat())
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(60))
                    addView(text("Send Reminders", 16f, true, Color.WHITE, Gravity.START))
                    val backBtn = TextView(this@MainActivity).apply {
                        text = "⬅ Back"
                        textSize = 14f
                        setTextColor(Color.WHITE)
                        typeface = Typeface.DEFAULT_BOLD
                        setPadding(dp(12), dp(4), dp(12), dp(4))
                        background = round(Color.parseColor("#3F37B3"), dp(12).toFloat())
                        setOnClickListener { showAdminPanel() }
                    }
                    addView(View(this@MainActivity).apply { layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f) })
                    addView(backBtn)
                }
                root.addView(navBar)

                subjects.filter { it.isContest }.forEach { subject ->
                    root.addView(card().apply {
                        addView(sectionTitle(subject.name, "Code: ${subject.code}"))
                        val timeStr = java.text.SimpleDateFormat("HH:mm", Locale.US).format(java.util.Date(subject.startTime))
                        addView(text("Scheduled for: $timeStr", 13f, false, muted, Gravity.START))
                        
                    val reminderBtn = primaryButton("Send Reminder", "🔔") {
                        // Captured reference to the button
                    }
                    reminderBtn.setOnClickListener {
                        val now = System.currentTimeMillis()
                        val diff = subject.startTime - now
                        val mins = (diff / 60000).toInt()
                        
                        val msg = if (mins > 0) {
                            "Contest '${subject.name}' starts in $mins minutes!"
                        } else if (mins > -subject.durationMin) {
                            "Contest '${subject.name}' is LIVE now! Join quickly."
                        } else {
                            "Contest '${subject.name}' has ended."
                        }

                        viewModel.sendReminder(user.id, subject.id, msg) { count ->
                            runOnUiThread {
                                toast("$count students notified: $msg")
                                reminderBtn.isEnabled = false
                                reminderBtn.text = "Wait 60s..."
                                reminderBtn.postDelayed({ 
                                    reminderBtn.isEnabled = true
                                    reminderBtn.text = "🔔  Send Reminder"
                                }, 60000)
                            }
                        }
                    }
                    addView(reminderBtn)

                    val publishBtn = outlineButton("Publish Results", "📊") {
                        viewModel.sendReminder(user.id, subject.id, "Results for '${subject.name}' are now available!") { count ->
                            runOnUiThread { toast("$count students notified about results.") }
                        }
                    }
                    addView(publishBtn)
                    })
                }
                
                if (subjects.none { it.isContest }) {
                    root.addView(card().apply { addView(text("No contest subjects found.", 14f, false, muted, Gravity.CENTER)) })
                }

                setContentView(scroll(root))
            }
        }
    }

    private fun showRegisteredContestsPage() {
        val user = viewModel.currentUser.value ?: return
        viewModel.getRegisteredContests(user.id) { contests ->
            runOnUiThread {
                val root = screenRoot()
                root.addView(heroCard("My Contests", "আপনার রেজিস্টার্ড সব পরীক্ষা", "Registered", false))
                
                contests.forEach { subject ->
                    val now = System.currentTimeMillis()
                    val endTime = subject.startTime + (subject.durationMin * 60000)
                    val isFinished = now > endTime
                    val isLive = now in subject.startTime..endTime
                    
                    root.addView(card().apply {
                        val statusColor = if (isFinished) success else danger
                        val statusLabel = if (isFinished) "Finished" else if (isLive) "LIVE" else "Upcoming"
                        
                        addView(chip(statusLabel, statusColor))
                        addView(sectionTitle(subject.name, "Code: ${subject.code}"))
                        
                        val timeStr = java.text.SimpleDateFormat("dd MMM, HH:mm", Locale.US).format(java.util.Date(subject.startTime))
                        addView(text("Time: $timeStr", 13f, false, muted, Gravity.START))

                        if (isFinished) {
                            addView(primaryButton("View Result", "📊") {
                                // Find this specific student's result for this subject
                                viewModel.getResultsForSubject(subject.id) { allResults ->
                                    val myResult = allResults.find { it.username == user.username }
                                    runOnUiThread {
                                        if (myResult != null) {
                                            showStudentResultDetail(myResult)
                                        } else {
                                            toast("আপনি এই পরীক্ষায় অংশগ্রহণ করেননি।")
                                        }
                                    }
                                }
                            })
                        } else {
                            addView(primaryButton(if (isLive) "Join Now" else "Contest Not Started", "🚀") {
                                if (isLive) showQuestionPaper(subject) else toast("অনুগ্রহ করে সময় হওয়া পর্যন্ত অপেক্ষা করুন।")
                            }.apply { isEnabled = isLive })
                        }
                    })
                }
                
                if (contests.isEmpty()) {
                    root.addView(card().apply { addView(text("No registrations found.", 14f, false, muted, Gravity.CENTER)) })
                }
                
                root.addView(outlineButton("Back", "⬅") { showExamScreen() })
                setContentView(swipeRefresh(root) { showRegisteredContestsPage() })
            }
        }
    }

    private fun showStudentResultDetail(result: ExamResultRow) {
        val root = screenRoot()
        root.addView(heroCard("Result Review", result.username, String.format(Locale.US, "%.2f%%", result.percent), false))
        
        val detailsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(10), dp(10), dp(10))
            background = round(surface, dp(16).toFloat())
        }
        
        viewModel.getDetailedAnswers(result.resultId) { answers ->
            runOnUiThread {
                if (answers.isEmpty()) {
                    detailsContainer.addView(text("বিস্তারিত তথ্য পাওয়া যায়নি।", 14f, false, muted, Gravity.CENTER))
                } else {
                    answers.forEach { ans ->
                        detailsContainer.addView(answerDetailRow(ans))
                    }
                }
            }
        }
        
        root.addView(detailsContainer)
        root.addView(outlineButton("Back", "⬅") { showRegisteredContestsPage() })
        setContentView(scroll(root))
    }

    private fun showExamScreen() {
        val user = viewModel.currentUser.value ?: return showLoginScreen()
        val searchInput = input("Subject Code (e.g. PHY101)")
        
        val root = screenRoot()
        
        // Header with Icons
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(15))
            addView(text("Student Portal", 24f, true, ink, Gravity.START).apply { layoutParams = LinearLayout.LayoutParams(0, -2, 1f) })
            
            val regIcon = TextView(this@MainActivity).apply {
                text = "📋 Registered"
                textSize = 14f
                setTextColor(primary)
                typeface = Typeface.DEFAULT_BOLD
                setPadding(dp(8), dp(8), dp(8), dp(8))
                setOnClickListener { showRegisteredContestsPage() }
            }
            addView(regIcon)

            val bellIcon = TextView(this@MainActivity).apply {
                text = "🔔"
                textSize = 24f
                setPadding(dp(8), dp(8), dp(8), dp(8))
                setOnClickListener { showNotificationsPage() }
            }
            addView(bellIcon)
        }
        root.addView(header)

        root.addView(card().apply {
            addView(sectionTitle("Search Exam", "সাবজেক্ট কোড দিয়ে সার্চ করুন"))
            addView(searchInput)
            addView(primaryButton("Find Subject", "🔍") {
                val query = searchInput.text.toString().trim()
                viewModel.searchSubject(user.id, query) { subject ->
                    runOnUiThread {
                        if (subject != null) {
                            showContestOrExamInfo(subject)
                        } else {
                            toast("Subject পাওয়া যায়নি। সঠিক সাবজেক্ট কোড প্রদান করুন।")
                        }
                    }
                }
            })
        })
        
        root.addView(dangerButton("Logout", "🚪") { logout() })
        setContentView(swipeRefresh(root) { showExamScreen() })
    }

    private fun showNotificationsPage() {
        val user = viewModel.currentUser.value ?: return
        viewModel.getReminders(user.id) { reminders ->
            runOnUiThread {
                val root = screenRoot()
                root.addView(heroCard("Notifications", "আপনার সব রিমাইন্ডার এবং রেজাল্ট", "Bell Icon", false))
                
                reminders.forEach { r ->
                    root.addView(card().apply {
                        addView(text(r.subjectName, 14f, true, primary, Gravity.START))
                        addView(text(r.message, 13f, false, ink, Gravity.START))
                        addView(text(r.timestamp, 10f, false, muted, Gravity.END))
                    })
                }
                
                if (reminders.isEmpty()) {
                    root.addView(card().apply { addView(text("No notifications yet.", 14f, false, muted, Gravity.CENTER)) })
                }
                
                root.addView(outlineButton("Back", "⬅") { showExamScreen() })
                setContentView(swipeRefresh(root) { showRegisteredContestsPage() })
            }
        }
    }

    private fun showContestOrExamInfo(subject: SubjectItem) {
        val user = viewModel.currentUser.value ?: return
        val now = System.currentTimeMillis()
        
        if (!subject.isContest) {
            showQuestionPaper(subject)
            return
        }

        val contestEndTime = subject.startTime + (subject.durationMin * 60000)
        
        val root = screenRoot()
        root.addView(heroCard(subject.name, "Contest Mode Enabled", "Duration: ${subject.durationMin}m", false))
        
        root.addView(card().apply {
            val statusText: String
            val showBtn: Boolean
            val btnLabel: String
            
            if (now < subject.startTime) {
                statusText = "Contest has not started yet."
                if (subject.isRegistered) {
                    showBtn = false
                    btnLabel = ""
                    addView(infoStrip("Status", "Registered! We will notify you.", success))
                } else {
                    showBtn = true
                    btnLabel = "Register for Contest"
                }
            } else if (now < contestEndTime) {
                statusText = "Contest is LIVE!"
                showBtn = true
                btnLabel = "Join Now"
            } else {
                statusText = "Contest has ended."
                showBtn = false
                btnLabel = ""
                addView(infoStrip("Status", "Result will be published soon.", warning))
            }
            
            addView(sectionTitle("Contest Status", statusText))
            
            if (showBtn) {
                addView(primaryButton(btnLabel, "🚀") {
                    if (btnLabel == "Register for Contest") {
                        viewModel.registerForContest(user.id, subject.id) { ok ->
                            runOnUiThread { if (ok) showContestOrExamInfo(subject.copy(isRegistered = true)) }
                        }
                    } else {
                        showQuestionPaper(subject)
                    }
                })
            }
            
            addView(outlineButton("Back", "⬅") { showExamScreen() })
        })
        setContentView(scroll(root))
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
                
                // Timer Header for Contests
                var remainingMillis: Long = -1
                val timerText = text("", 20f, true, danger, Gravity.CENTER)
                
                if (subject.isContest) {
                    val now = System.currentTimeMillis()
                    val contestEndTime = subject.startTime + (subject.durationMin * 60000)
                    remainingMillis = contestEndTime - now
                    if (remainingMillis > 0) {
                        root.addView(card().apply {
                            addView(text("Contest Ending In:", 14f, false, muted, Gravity.CENTER))
                            addView(timerText)
                        })
                    }
                }

                root.addView(heroCard("Exam: ${subject.name}", "সব question answer করে submit করো", "${questions.size} Questions", false))
                val answers = mutableMapOf<Long, RadioGroup>()
                questions.forEachIndexed { index, q ->
                    root.addView(questionCard(index + 1, q, answers))
                }
                
                val submitBtn = primaryButton("Submit Exam", "🏁") {
                    submitExam(subject, questions, answers)
                }
                root.addView(submitBtn)
                
                // Countdown logic
                if (remainingMillis > 0) {
                    val timer = object : android.os.CountDownTimer(remainingMillis, 1000) {
                        override fun onTick(millisUntilFinished: Long) {
                            val mins = (millisUntilFinished / 1000) / 60
                            val secs = (millisUntilFinished / 1000) % 60
                            timerText.text = String.format(Locale.US, "%02d:%02d", mins, secs)
                        }
                        override fun onFinish() {
                            toast("Time's up! Submitting automatically...")
                            submitExam(subject, questions, answers)
                        }
                    }
                    timer.start()
                }

                root.addView(outlineButton("Back to Subjects", "⬅") { showExamScreen() })
                setContentView(scroll(root))
            }
        }
    }

    private fun submitExam(subject: SubjectItem, questions: List<Question>, answers: Map<Long, RadioGroup>) {
        var correct = 0
        val userAnswersList = mutableListOf<Pair<Long, String>>()
        questions.forEach { q ->
            val group = answers[q.id]
            val checked = group?.findViewById<RadioButton>(group.checkedRadioButtonId)
            val selectedOption = checked?.tag?.toString() ?: ""
            userAnswersList.add(q.id to selectedOption)
            if (selectedOption == q.correctOption) correct++
        }
        val user = viewModel.currentUser.value ?: return
        viewModel.saveExamResult(user.id, subject.id, questions.size, correct, userAnswersList) { percent ->
            runOnUiThread {
                if (subject.isContest) {
                    toast("Contest submitted! Result will be available in notifications.")
                    showExamScreen()
                } else {
                    showResultScreen(questions.size, correct, percent)
                }
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
        addView(sectionTitle("Manage Subject", "Hold to delete or click to select"))
        val spinner = spinner(subjects)
        selected?.let {
            val index = subjects.indexOf(it)
            if (index >= 0) spinner.setSelection(index)
        }
        addView(spinner)
        
        // Add Long Press behavior to the spinner container
        spinner.setOnLongClickListener {
            val item = spinner.selectedItem as? SubjectItem
            if (item != null) {
                android.app.AlertDialog.Builder(this@MainActivity)
                    .setTitle("Delete Subject?")
                    .setMessage("Are you sure you want to delete '${item.name}'? This will remove all questions and results associated with it.")
                    .setPositiveButton("Delete") { _, _ ->
                        viewModel.deleteSubject(item.id) { ok ->
                            runOnUiThread {
                                if (ok) {
                                    toast("Subject deleted.")
                                    currentAdminSubjectId = null
                                    showAdminPanel()
                                } else toast("Error deleting subject.")
                            }
                        }
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
            true
        }
        
        val buttonLayout = LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 2f
        }
        
        buttonLayout.addView(primaryButton("Update Selection", "✅") {
            val item = spinner.selectedItem as? SubjectItem
            if (item != null) {
                currentAdminSubjectId = item.id
                showAdminPanel()
            }
        }.apply { layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { rightMargin = dp(5) } })

        buttonLayout.addView(outlineButton("Edit Subject Info", "✏️") {
            val item = spinner.selectedItem as? SubjectItem
            if (item != null) showEditSubjectPage(item)
        }.apply { layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(5) } })

        addView(buttonLayout)
    }

    private fun showEditSubjectPage(subject: SubjectItem) {
        val root = screenRoot()
        root.addView(heroCard("Edit Subject", subject.name, "ID: ${subject.id}", false))

        val nameInput = input("Subject Name").apply { setText(subject.name) }
        val codeInput = input("Subject Code").apply { setText(subject.code) }
        val contestSwitch = CheckBox(this).apply {
            text = "Enable Contest Mode"
            isChecked = subject.isContest
            setTextColor(ink)
        }
        
        val contestLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = if (subject.isContest) View.VISIBLE else View.GONE
        }
        
        val calendar = java.util.Calendar.getInstance().apply { timeInMillis = if (subject.startTime > 0) subject.startTime else System.currentTimeMillis() }
        
        val dateInput = input("Start Date (Click to select)").apply {
            isFocusable = false
            setText(String.format(Locale.US, "%04d-%02d-%02d", calendar.get(java.util.Calendar.YEAR), calendar.get(java.util.Calendar.MONTH) + 1, calendar.get(java.util.Calendar.DAY_OF_MONTH)))
            setOnClickListener {
                android.app.DatePickerDialog(this@MainActivity, { _, y, m, d ->
                    calendar.set(java.util.Calendar.YEAR, y)
                    calendar.set(java.util.Calendar.MONTH, m)
                    calendar.set(java.util.Calendar.DAY_OF_MONTH, d)
                    setText(String.format(Locale.US, "%04d-%02d-%02d", y, m + 1, d))
                }, calendar.get(java.util.Calendar.YEAR), calendar.get(java.util.Calendar.MONTH), calendar.get(java.util.Calendar.DAY_OF_MONTH)).show()
            }
        }

        val startTimeInput = input("Start Time (Click to select)").apply {
            isFocusable = false
            val h = calendar.get(java.util.Calendar.HOUR_OF_DAY)
            val min = calendar.get(java.util.Calendar.MINUTE)
            val amPm = if (h < 12) "AM" else "PM"
            val h12 = if (h % 12 == 0) 12 else h % 12
            setText(String.format(Locale.US, "%02d:%02d %s", h12, min, amPm))
            setOnClickListener {
                android.app.TimePickerDialog(this@MainActivity, { _, hSelected, minSelected ->
                    calendar.set(java.util.Calendar.HOUR_OF_DAY, hSelected)
                    calendar.set(java.util.Calendar.MINUTE, minSelected)
                    val amPmS = if (hSelected < 12) "AM" else "PM"
                    val h12S = if (hSelected % 12 == 0) 12 else hSelected % 12
                    setText(String.format(Locale.US, "%02d:%02d %s", h12S, minSelected, amPmS))
                }, calendar.get(java.util.Calendar.HOUR_OF_DAY), calendar.get(java.util.Calendar.MINUTE), false).show()
            }
        }
        
        val durationInput = input("Duration (Minutes)").apply { setText(subject.durationMin.toString()) }

        contestLayout.addView(dateInput)
        contestLayout.addView(startTimeInput)
        contestLayout.addView(durationInput)
        
        contestSwitch.setOnCheckedChangeListener { _, isChecked -> contestLayout.visibility = if (isChecked) View.VISIBLE else View.GONE }

        root.addView(card().apply {
            addView(nameInput)
            addView(codeInput)
            addView(contestSwitch)
            addView(contestLayout)
            addView(primaryButton("Save Changes", "💾") {
                val n = nameInput.text.toString().trim()
                val c = codeInput.text.toString().trim().uppercase()
                if (n.isEmpty() || c.isEmpty()) return@primaryButton toast("Input missing")
                
                var st: Long = 0
                var dur = 0
                if (contestSwitch.isChecked) {
                    st = calendar.timeInMillis
                    dur = durationInput.text.toString().toIntOrNull() ?: 0
                }
                
                viewModel.updateSubject(subject.id, n, c, contestSwitch.isChecked, st, dur) { ok ->
                    runOnUiThread {
                        if (ok) { toast("Subject updated!"); showAdminPanel() }
                        else toast("Update failed")
                    }
                }
            })
            addView(outlineButton("Cancel", "❌") { showAdminPanel() })
        })
        setContentView(scroll(root))
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
        addView(sectionTitle("Questions List", "Edit or remove questions from ${subject.name}"))
        viewModel.getQuestions(subject.id) { questions ->
            runOnUiThread {
                val list = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL }
                questions.forEach { q ->
                    list.addView(card().apply {
                        addView(text(q.text, 14f, true, ink, Gravity.START))
                        val btnRow = LinearLayout(this@MainActivity).apply { 
                            orientation = LinearLayout.HORIZONTAL
                            weightSum = 2f
                            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) }
                        }
                        
                        btnRow.addView(outlineButton("Edit", "✏️") {
                            showEditQuestionPage(subject, q)
                        }.apply { layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { rightMargin = dp(4) } })

                        btnRow.addView(dangerButton("Delete", "🗑️") {
                            viewModel.deleteQuestion(q.id) { ok ->
                                runOnUiThread { toast(if (ok) "Deleted." else "Error."); showAdminPanel() }
                            }
                        }.apply { layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(4) } })
                        
                        addView(btnRow)
                    })
                }
                addView(list)
            }
        }
    }

    private fun showEditQuestionPage(subject: SubjectItem, question: Question) {
        val root = screenRoot()
        root.addView(heroCard("Edit Question", subject.name, "Question ID: ${question.id}", false))
        
        val qInput = input("Question Text").apply { setText(question.text) }
        val optA = input("Option A").apply { setText(question.optionA) }
        val optB = input("Option B").apply { setText(question.optionB) }
        val optC = input("Option C").apply { setText(question.optionC) }
        val optD = input("Option D").apply { setText(question.optionD) }
        val correct = input("Correct Option (A/B/C/D)").apply { setText(question.correctOption) }

        root.addView(card().apply {
            addView(qInput); addView(optA); addView(optB); addView(optC); addView(optD); addView(correct)
            addView(primaryButton("Save Changes", "💾") {
                val opts = listOf(optA.text.toString(), optB.text.toString(), optC.text.toString(), optD.text.toString())
                viewModel.updateQuestion(question.id, qInput.text.toString(), opts, correct.text.toString()) { ok ->
                    runOnUiThread {
                        if (ok) { toast("Question updated!"); showAdminPanel() }
                        else toast("Update failed")
                    }
                }
            })
            addView(outlineButton("Cancel", "❌") { showAdminPanel() })
        })
        setContentView(scroll(root))
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
            showSubjectReportPage(subject)
        }.apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        })
    }

    private fun showSubjectReportPage(subject: SubjectItem) {
        viewModel.getResultsForSubject(subject.id) { results ->
            runOnUiThread {
                val root = screenRoot()
                root.addView(heroCard("Subject Report", subject.name, "Total Exams: ${results.size}", false))

                if (results.isEmpty()) {
                    root.addView(card().apply {
                        addView(text("এই subject-এ এখনও কেউ পরীক্ষা দেয়নি।", 15f, false, muted, Gravity.CENTER))
                    })
                } else {
                    results.forEach { result ->
                        root.addView(studentResultRow(result))
                    }
                }

                root.addView(outlineButton("Back to Dashboard", "⬅") { showAdminPanel() })
                setContentView(scroll(root))
            }
        }
    }

    private fun studentResultRow(result: ExamResultRow): LinearLayout {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(12) }
        }

        val header = card().apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 0 }
            val horizontal = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(text(result.username, 16f, true, ink, Gravity.START).apply {
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                })
                val percentText = String.format(Locale.US, "%.2f%%", result.percent)
                addView(text(percentText, 16f, true, if (result.percent >= 60) success else danger, Gravity.END))
            }
            addView(horizontal)
            addView(text("Submitted: ${result.submittedAt}", 11f, false, muted, Gravity.START))
        }

        val detailsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(dp(10), 0, dp(10), dp(10))
            background = round(Color.parseColor("#F8FAFC"), dp(16).toFloat())
        }

        header.setOnClickListener {
            if (detailsContainer.visibility == View.GONE) {
                if (detailsContainer.childCount == 0) {
                    viewModel.getDetailedAnswers(result.resultId) { answers ->
                        runOnUiThread {
                            answers.forEach { ans ->
                                detailsContainer.addView(answerDetailRow(ans))
                            }
                            detailsContainer.visibility = View.VISIBLE
                        }
                    }
                } else {
                    detailsContainer.visibility = View.VISIBLE
                }
            } else {
                detailsContainer.visibility = View.GONE
            }
        }

        container.addView(header)
        container.addView(detailsContainer)
        return container
    }

    private fun answerDetailRow(ans: UserAnswerDetail): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(0, dp(8), 0, dp(8))
        addView(text(ans.questionText, 13f, true, ink, Gravity.START))
        val statusText = if (ans.isCorrect) {
            "✅ Correct: ${ans.selectedOption}"
        } else {
            "❌ Wrong! Selected: ${ans.selectedOption} | Correct: ${ans.correctOption}"
        }
        addView(text(statusText, 12f, false, if (ans.isCorrect) success else danger, Gravity.START))
        addView(View(this@MainActivity).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1))
            setBackgroundColor(Color.parseColor("#E2E8F0"))
        })
    }

    private fun screenRoot(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(18), dp(20), dp(18), dp(28))
        background = gradient(intArrayOf(Color.parseColor("#EEF2FF"), Color.parseColor("#FDFBFF")), GradientDrawable.Orientation.TOP_BOTTOM, dp(0).toFloat())
    }

    private fun heroCard(title: String, subtitle: String, badge: String, showPicture: Boolean): LinearLayout = card(primaryDark).apply {
        elevation = dp(8).toFloat()
        addView(
            LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(dp(10), dp(10), dp(10), dp(10))
                if (showPicture) addView(pictureSection())
                addView(chip(badge, accent))
                addView(text(title, 32f, true, Color.WHITE, Gravity.CENTER))
                addView(text(subtitle, 16f, false, Color.parseColor("#E0E7FF"), Gravity.CENTER))
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
        elevation = dp(4).toFloat()
        background = round(color, dp(20).toFloat(), if (color == surface) Color.parseColor("#F1F5F9") else color, dp(1))
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(16)
        }
        setPadding(dp(20), dp(20), dp(20), dp(20))
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
        setSingleLine(true)
        minHeight = dp(56)
        background = round(softSurface, dp(14).toFloat(), Color.parseColor("#CBD5E1"), dp(1))
        setPadding(dp(20), dp(12), dp(20), dp(12))
        inputType = if (password) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD else InputType.TYPE_CLASS_TEXT
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(12)
        }
    }

    private fun spinner(items: List<SubjectItem>): Spinner = Spinner(this).apply {
        adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, items)
        background = round(softSurface, dp(14).toFloat(), Color.parseColor("#CBD5E1"), dp(1))
        setPadding(dp(16), dp(10), dp(16), dp(10))
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)).apply { topMargin = dp(10) }
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
        textSize = 15f
        typeface = Typeface.DEFAULT_BOLD
        isAllCaps = false
        cornerRadius = dp(16)
        insetTop = 0
        insetBottom = 0
        setTextColor(textColor)
        backgroundTintList = ColorStateList.valueOf(backgroundColor)
        minHeight = dp(56)
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { 
            topMargin = dp(14) 
        }
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

    private fun swipeRefresh(child: View, onRefresh: () -> Unit): androidx.swiperefreshlayout.widget.SwipeRefreshLayout {
        return androidx.swiperefreshlayout.widget.SwipeRefreshLayout(this).apply {
            addView(scroll(child))
            setOnRefreshListener {
                onRefresh()
                isRefreshing = false
            }
            setColorSchemeColors(primary)
        }
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
