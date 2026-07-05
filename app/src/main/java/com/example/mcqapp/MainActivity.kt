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
    private var lastBackPressTime: Long = 0

    // Modern Color Palette
    private val primary = Color.parseColor("#4338CA") // Indigo 700
    private val primaryLight = Color.parseColor("#EEF2FF") // Indigo 50
    private val primaryDark = Color.parseColor("#312E81") // Indigo 900
    private val accent = Color.parseColor("#0ea5e9") // Sky 500
    private val success = Color.parseColor("#10b981") // Emerald 500
    private val danger = Color.parseColor("#f43f5e") // Rose 500
    private val warning = Color.parseColor("#f59e0b") // Amber 500
    private val ink = Color.parseColor("#0f172a") // Slate 900
    private val muted = Color.parseColor("#64748b") // Slate 500
    private val surface = Color.WHITE
    private val softSurface = Color.parseColor("#f8fafc") // Slate 50

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = primaryDark

        val dbHelper = McqDatabase(this)
        val repository = McqRepository(dbHelper)

        viewModel = ViewModelProvider(this, object : ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                return McqViewModel(repository, applicationContext) as T
            }
        }).get(McqViewModel::class.java)

        viewModel.seedData()
        
        val user = viewModel.currentUser.value
        if (user != null) {
            if (user.role == "Student") showExamScreen() else showAdminPanel()
        } else {
            showLoginScreen()
        }
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        val user = viewModel.currentUser.value
        if (user != null) {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastBackPressTime < 2000) {
                finish() // Exit app without logout
            } else {
                lastBackPressTime = currentTime
                toast("Press again to exit SPEC MCQ")
            }
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
        root.addView(heroCard("SPEC MCQ", "Empowering Your Academic Journey", "Modern Exam System", true))
        
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

                val forgetPass = TextView(this@MainActivity).apply {
                    text = "Forget Password?"
                    textSize = 14f
                    setTextColor(primary)
                    typeface = Typeface.DEFAULT_BOLD
                    gravity = Gravity.CENTER
                    setPadding(0, dp(15), 0, dp(5))
                    setOnClickListener { toast("Feature coming soon!") }
                }
                addView(forgetPass)
                
                addView(View(this@MainActivity).apply { 
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)).apply { 
                        setMargins(0, dp(20), 0, dp(15))
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
            }
        )
        setContentView(scroll(root))
    }

    private fun showRegisterScreen(role: String) {
        val fullName = input("Full Name")
        val username = input("Username")
        val password = input("Password", password = true)
        
        val root = screenRoot()
        root.addView(heroCard("Register", "Create a new $role account", "SPEC MCQ Join", false))
        
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

                // Modern Navigation Bar
                val navBar = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(18), dp(10), dp(18), dp(10))
                    background = round(primaryDark, dp(16).toFloat())
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(65))

                    addView(text("SPEC MCQ Admin", 17f, true, Color.WHITE, Gravity.START))

                    val reminderIcon = TextView(this@MainActivity).apply {
                        text = "🔔 Reminders"
                        textSize = 13f
                        setTextColor(Color.WHITE)
                        typeface = Typeface.DEFAULT_BOLD
                        setPadding(dp(12), dp(6), dp(12), dp(6))
                        background = round(Color.parseColor("#4338CA"), dp(12).toFloat())
                        setOnClickListener { showReminderManagementPage() }
                    }

                    addView(View(this@MainActivity).apply {
                        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
                    })
                    addView(reminderIcon)
                }
                root.addView(navBar)

                root.addView(heroCard(user.username, "Admin Dashboard", user.role, false))

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
                    buttonTintList = ColorStateList.valueOf(primary)
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(10) }
                }
                val contestLayout = LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    visibility = View.GONE
                }
                val startTimeInput = input("Start Time")
                val durationInput = input("Duration (Minutes)")
                val dateInput = input("Start Date")
                
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

    private fun showExamScreen() {
        val user = viewModel.currentUser.value ?: return showLoginScreen()
        val searchInput = input("Subject Code (e.g. PHY101)")
        
        val root = screenRoot()
        
        // Modern Student Header
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(20))
            
            val infoLayout = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
                addView(text("Welcome, ${user.fullName}", 20f, true, ink, Gravity.START))
                addView(text("@${user.username}", 14f, false, muted, Gravity.START))
            }
            addView(infoLayout)
            
            val bellIcon = TextView(this@MainActivity).apply {
                text = "🔔"
                textSize = 22f
                setPadding(dp(12), dp(8), dp(12), dp(8))
                setOnClickListener { showNotificationsPage() }
            }
            addView(bellIcon)

            val profileIcon = TextView(this@MainActivity).apply {
                text = "👤"
                textSize = 22f
                setPadding(dp(12), dp(8), dp(12), dp(8))
                setOnClickListener { showProfilePage() }
            }
            addView(profileIcon)
        }
        root.addView(header)

        root.addView(card().apply {
            addView(sectionTitle("Find Exam", "সাবজেক্ট কোড দিয়ে সার্চ করুন"))
            addView(searchInput)
            addView(primaryButton("Search Now", "🔍") {
                val query = searchInput.text.toString().trim()
                viewModel.searchSubject(user.id, query) { subject ->
                    runOnUiThread {
                        if (subject != null) {
                            showContestOrExamInfo(subject)
                        } else {
                            toast("Subject পাওয়া যায়নি।")
                        }
                    }
                }
            })
            
            addView(outlineButton("My Registered Contests", "📋") { showRegisteredContestsPage() })
        })
        
        root.addView(dangerButton("Logout", "🚪") { logout() })
        setContentView(swipeRefresh(root) { showExamScreen() })
    }

    private fun showProfilePage() {
        val user = viewModel.currentUser.value ?: return
        val root = screenRoot()
        
        root.addView(heroCard(user.fullName, "Personal Profile Info", "@${user.username}", false))
        
        val phoneInput = input("Phone Number").apply { setText(user.phone) }
        val emailInput = input("Email Address").apply { setText(user.email) }
        
        root.addView(card().apply {
            addView(sectionTitle("Profile Details", "আপনার তথ্য আপডেট করুন"))
            addView(label("Full Name"))
            addView(text(user.fullName, 16f, true, ink, Gravity.START))
            addView(label("Username"))
            addView(text(user.username, 16f, true, ink, Gravity.START))
            
            addView(label("Phone Number"))
            addView(phoneInput)
            addView(label("Email"))
            addView(emailInput)
            
            addView(primaryButton("Update Profile", "💾") {
                val p = phoneInput.text.toString()
                val e = emailInput.text.toString()
                viewModel.updateUserProfile(user.id, p, e) { ok ->
                    runOnUiThread {
                        if (ok) {
                            toast("Profile updated successfully!")
                            showExamScreen()
                        } else {
                            toast("Update failed.")
                        }
                    }
                }
            })
            
            addView(outlineButton("Back to Portal", "⬅") { showExamScreen() })
        })
        
        setContentView(scroll(root))
    }

    private fun showReminderManagementPage() {
        val user = viewModel.currentUser.value ?: return
        viewModel.getSubjects(user.id) { subjects ->
            runOnUiThread {
                val root = screenRoot()
                
                val navBar = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(18), dp(10), dp(18), dp(10))
                    background = round(primaryDark, dp(16).toFloat())
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(60))
                    addView(text("Send Reminders", 16f, true, Color.WHITE, Gravity.START))
                    val backBtn = TextView(this@MainActivity).apply {
                        text = "⬅ Back"
                        textSize = 14f
                        setTextColor(Color.WHITE)
                        typeface = Typeface.DEFAULT_BOLD
                        setPadding(dp(12), dp(4), dp(12), dp(4))
                        background = round(Color.parseColor("#4338CA"), dp(12).toFloat())
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
                        
                        val reminderBtn = primaryButton("Send Reminder", "🔔") {}
                        reminderBtn.setOnClickListener {
                            val now = System.currentTimeMillis()
                            val mins = ((subject.startTime - now) / 60000).toInt()
                            val msg = if (mins > 0) "Starts in $mins minutes!" else "LIVE now!"
                            viewModel.sendReminder(user.id, subject.id, "Contest '${subject.name}' $msg") { count ->
                                runOnUiThread {
                                    toast("$count students notified.")
                                    reminderBtn.isEnabled = false
                                }
                            }
                        }
                        addView(reminderBtn)
                    })
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
                    root.addView(card().apply {
                        addView(sectionTitle(subject.name, "Code: ${subject.code}"))
                        addView(primaryButton("Join Exam", "🚀") { showQuestionPaper(subject) })
                    })
                }
                root.addView(outlineButton("Back", "⬅") { showExamScreen() })
                setContentView(scroll(root))
            }
        }
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
                    })
                }
                root.addView(outlineButton("Back", "⬅") { showExamScreen() })
                setContentView(scroll(root))
            }
        }
    }

    private fun showContestOrExamInfo(subject: SubjectItem) {
        val user = viewModel.currentUser.value ?: return
        val root = screenRoot()
        root.addView(heroCard(subject.name, "Contest Mode Enabled", "Duration: ${subject.durationMin}m", false))
        root.addView(card().apply {
            addView(sectionTitle("Exam Info", "সাবজেক্ট কোড: ${subject.code}"))
            if (subject.isRegistered) {
                addView(primaryButton("Join Now", "🚀") { showQuestionPaper(subject) })
            } else {
                addView(primaryButton("Register Now", "📝") {
                    viewModel.registerForContest(user.id, subject.id) { ok ->
                        runOnUiThread { if (ok) showExamScreen() }
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
                val root = screenRoot()
                root.addView(heroCard(subject.name, "সব প্রশ্নের উত্তর দিন", "${questions.size} Items", false))
                val answers = mutableMapOf<Long, RadioGroup>()
                questions.forEachIndexed { i, q -> root.addView(questionCard(i + 1, q, answers)) }
                root.addView(primaryButton("Submit Now", "🏁") {
                    submitExam(subject, questions, answers)
                })
                setContentView(scroll(root))
            }
        }
    }

    private fun submitExam(subject: SubjectItem, questions: List<Question>, answers: Map<Long, RadioGroup>) {
        var correct = 0
        val ansList = questions.map { q ->
            val group = answers[q.id]
            val sel = group?.findViewById<RadioButton>(group.checkedRadioButtonId)?.tag?.toString() ?: ""
            if (sel == q.correctOption) correct++
            q.id to sel
        }
        val user = viewModel.currentUser.value ?: return
        viewModel.saveExamResult(user.id, subject.id, questions.size, correct, ansList) { percent ->
            runOnUiThread { showResultScreen(questions.size, correct, percent) }
        }
    }

    private fun showResultScreen(total: Int, correct: Int, percent: Double) {
        val root = screenRoot()
        root.addView(heroCard("Result", "Score: ${String.format("%.2f", percent)}%", "Done", false))
        root.addView(card().apply {
            addView(bigScore("${String.format("%.1f", percent)}%"))
            addView(statsRow(listOf("Total" to total.toString(), "Correct" to correct.toString())))
        })
        root.addView(primaryButton("Back to Portal", "🔁") { showExamScreen() })
        setContentView(scroll(root))
    }

    // Helper UI Methods
    private fun screenRoot(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(18), dp(20), dp(18), dp(32))
        setBackgroundColor(softSurface)
    }

    private fun card(color: Int = surface): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        elevation = dp(4).toFloat()
        background = round(color, dp(20).toFloat(), if (color == surface) Color.parseColor("#f1f5f9") else color, dp(1))
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(18) }
        setPadding(dp(22), dp(22), dp(22), dp(22))
    }

    private fun heroCard(title: String, subtitle: String, badge: String, showPicture: Boolean): LinearLayout = card(primaryDark).apply {
        elevation = dp(8).toFloat()
        val inner = LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(15), dp(10), dp(15))
            if (showPicture) addView(pictureSection())
            addView(chip(badge, accent))
            addView(text(title, 30f, true, Color.WHITE, Gravity.CENTER))
            addView(text(subtitle, 14f, false, Color.parseColor("#c7d2fe"), Gravity.CENTER))
        }
        addView(inner)
    }

    private fun pictureSection(): FrameLayout = FrameLayout(this).apply {
        layoutParams = LinearLayout.LayoutParams(dp(160), dp(160)).apply { bottomMargin = dp(20) }
        
        // Circular background for the logo
        val shape = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.WHITE)
            setStroke(dp(3), primary)
        }
        background = shape
        
        addView(ImageView(this@MainActivity).apply {
            setImageResource(R.drawable.spec_logo)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(dp(8), dp(8), dp(8), dp(8))
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        })
    }

    private fun sectionTitle(title: String, subtitle: String): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        addView(text(title, 21f, true, ink, Gravity.START))
        addView(text(subtitle, 13f, false, muted, Gravity.START))
    }

    private fun input(hintText: String, password: Boolean = false): EditText = EditText(this).apply {
        hint = hintText
        textSize = 15f
        setTextColor(ink)
        setHintTextColor(muted)
        setSingleLine(true)
        minHeight = dp(58)
        background = round(surface, dp(14).toFloat(), Color.parseColor("#cbd5e1"), dp(1))
        setPadding(dp(18), dp(12), dp(18), dp(12))
        inputType = if (password) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD else InputType.TYPE_CLASS_TEXT
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) }
    }

    private fun primaryButton(label: String, icon: String, action: () -> Unit): MaterialButton = fancyButton(label, icon, primary, Color.WHITE, action)
    private fun dangerButton(label: String, icon: String, action: () -> Unit): MaterialButton = fancyButton(label, icon, danger, Color.WHITE, action)
    private fun outlineButton(label: String, icon: String, action: () -> Unit): MaterialButton = fancyButton(label, icon, Color.TRANSPARENT, primary, action).apply {
        strokeWidth = dp(1)
        strokeColor = ColorStateList.valueOf(primary)
        backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
    }

    private fun fancyButton(label: String, icon: String, bgColor: Int, txtColor: Int, action: () -> Unit): MaterialButton = MaterialButton(this).apply {
        text = "$icon  $label"
        textSize = 14f
        typeface = Typeface.DEFAULT_BOLD
        isAllCaps = false
        cornerRadius = dp(14)
        setTextColor(txtColor)
        backgroundTintList = ColorStateList.valueOf(bgColor)
        minHeight = dp(58)
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(15) }
        setOnClickListener { action() }
    }

    private fun statsRow(items: List<Pair<String, String>>): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(15) }
        items.forEachIndexed { i, item ->
            addView(statBox(item.first, item.second).apply {
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { if (i > 0) leftMargin = dp(10) }
            })
        }
    }

    private fun statBox(label: String, value: String): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = round(surface, dp(18).toFloat(), Color.parseColor("#e2e8f0"), dp(1))
        setPadding(dp(16), dp(16), dp(16), dp(16))
        addView(text(value, 26f, true, primary, Gravity.CENTER))
        addView(text(label, 12f, false, muted, Gravity.CENTER))
    }

    private fun bigScore(value: String): TextView = text(value, 48f, true, primary, Gravity.CENTER).apply {
        background = round(primaryLight, dp(24).toFloat())
        setPadding(dp(20), dp(25), dp(20), dp(25))
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(20) }
    }

    private fun questionCard(num: Int, q: Question, answers: MutableMap<Long, RadioGroup>): LinearLayout = card().apply {
        addView(chip("Question $num", primary))
        addView(text(q.text, 18f, true, ink, Gravity.START))
        val group = RadioGroup(this@MainActivity).apply { orientation = RadioGroup.VERTICAL; setPadding(0, dp(10), 0, 0) }
        mapOf("A" to q.optionA, "B" to q.optionB, "C" to q.optionC, "D" to q.optionD).forEach { (k, v) ->
            group.addView(RadioButton(this@MainActivity).apply {
                text = "$k. $v"
                tag = k
                textSize = 15f
                setTextColor(ink)
                buttonTintList = ColorStateList.valueOf(primary)
                setPadding(dp(12), dp(12), dp(12), dp(12))
                layoutParams = RadioGroup.LayoutParams(-1, -2).apply { topMargin = dp(8) }
            })
        }
        answers[q.id] = group
        addView(group)
    }

    private fun label(value: String): TextView = text(value, 13f, true, muted, Gravity.START).apply {
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) }
    }

    private fun chip(value: String, color: Int): TextView = text(value, 12f, true, Color.WHITE, Gravity.CENTER).apply {
        background = round(color, dp(12).toFloat())
        setPadding(dp(14), dp(6), dp(14), dp(6))
        layoutParams = LinearLayout.LayoutParams(-2, -2).apply { bottomMargin = dp(10) }
    }

    private fun text(v: String, s: Float, b: Boolean, c: Int, g: Int): TextView = TextView(this).apply {
        text = v; textSize = s; setTextColor(c); gravity = g
        if (b) typeface = Typeface.DEFAULT_BOLD
    }

    private fun swipeRefresh(child: View, onRefresh: () -> Unit) = androidx.swiperefreshlayout.widget.SwipeRefreshLayout(this).apply {
        addView(scroll(child))
        setOnRefreshListener { onRefresh(); isRefreshing = false }
        setColorSchemeColors(primary)
    }

    private fun scroll(child: View) = ScrollView(this).apply { isFillViewport = true; addView(child) }

    private fun round(c: Int, r: Float, sc: Int? = null, sw: Int = 0) = GradientDrawable().apply {
        setColor(c); cornerRadius = r
        if (sc != null) setStroke(sw, sc)
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_SHORT).show()
    
    private fun spinner(items: List<SubjectItem>): Spinner = Spinner(this).apply {
        adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, items)
        background = round(softSurface, dp(14).toFloat(), Color.parseColor("#CBD5E1"), dp(1))
        setPadding(dp(16), dp(10), dp(16), dp(10))
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)).apply { topMargin = dp(10) }
    }
    
    private fun subjectSelectorCard(subjects: List<SubjectItem>, selected: SubjectItem?): LinearLayout = card().apply {
        addView(sectionTitle("Manage Subject", "Hold to delete or click to select"))
        val spinner = spinner(subjects)
        selected?.let {
            val index = subjects.indexOf(it)
            if (index >= 0) spinner.setSelection(index)
        }
        addView(spinner)
        
        spinner.setOnLongClickListener {
            val item = spinner.selectedItem as? SubjectItem
            if (item != null) {
                android.app.AlertDialog.Builder(this@MainActivity)
                    .setTitle("Delete Subject?")
                    .setMessage("Are you sure you want to delete '${item.name}'?")
                    .setPositiveButton("Delete") { _, _ ->
                        viewModel.deleteSubject(item.id) { ok ->
                            runOnUiThread { if (ok) showAdminPanel() }
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

        buttonLayout.addView(outlineButton("Edit Info", "✏️") {
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
        
        root.addView(card().apply {
            addView(nameInput); addView(codeInput)
            addView(primaryButton("Save Changes", "💾") {
                viewModel.updateSubject(subject.id, nameInput.text.toString(), codeInput.text.toString(), subject.isContest, subject.startTime, subject.durationMin) { ok ->
                    runOnUiThread { if (ok) showAdminPanel() }
                }
            })
            addView(outlineButton("Cancel", "❌") { showAdminPanel() })
        })
        setContentView(scroll(root))
    }
    
    private fun bulkQuestionBuilderCard(subject: SubjectItem): LinearLayout = card().apply {
        addView(sectionTitle("Add Questions", "Bulk add questions to ${subject.name}"))
        val container = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL }
        val qInput = input("Question Text")
        val optA = input("Option A"); val optB = input("Option B")
        val optC = input("Option C"); val optD = input("Option D")
        val correct = input("Correct (A/B/C/D)")
        
        addView(qInput); addView(optA); addView(optB); addView(optC); addView(optD); addView(correct)
        
        addView(primaryButton("Save Question", "💾") {
            val drafts = listOf(Triple(qInput.text.toString(), listOf(optA.text.toString(), optB.text.toString(), optC.text.toString(), optD.text.toString()), correct.text.toString()))
            viewModel.addQuestions(subject.id, drafts) { saved, _ ->
                runOnUiThread { if (saved > 0) { toast("Saved!"); showAdminPanel() } }
            }
        })
    }
    
    private fun questionDeleteCard(subject: SubjectItem): LinearLayout = card().apply {
        addView(sectionTitle("Questions List", "Remove questions from ${subject.name}"))
        viewModel.getQuestions(subject.id) { questions ->
            runOnUiThread {
                questions.forEach { q ->
                    addView(card().apply {
                        addView(text(q.text, 14f, true, ink, Gravity.START))
                        addView(dangerButton("Delete", "🗑️") {
                            viewModel.deleteQuestion(q.id) { ok -> runOnUiThread { if (ok) showAdminPanel() } }
                        })
                    })
                }
            }
        }
    }
    
    private fun resultReportCard(subjects: List<SubjectItem>): LinearLayout = card().apply {
        addView(sectionTitle("Reports", "Performance summary"))
        subjects.forEach { s ->
            addView(outlineButton(s.name, "📊") { showSubjectReportPage(s) })
        }
    }
    
    private fun showSubjectReportPage(subject: SubjectItem) {
        viewModel.getResultsForSubject(subject.id) { results ->
            runOnUiThread {
                val root = screenRoot()
                root.addView(heroCard("Report", subject.name, "Results: ${results.size}", false))
                results.forEach { r ->
                    root.addView(card().apply {
                        addView(text("${r.username}: ${r.percent}%", 16f, true, ink, Gravity.START))
                    })
                }
                root.addView(outlineButton("Back", "⬅") { showAdminPanel() })
                setContentView(scroll(root))
            }
        }
    }
}
