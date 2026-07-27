package com.example.mcqapp

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.example.mcqapp.data.*
import com.example.mcqapp.ui.McqViewModel
import com.google.android.material.button.MaterialButton
import java.text.SimpleDateFormat
import java.util.*

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
    private var lastBackPressTime: Long = 0
    private var currentTab = "Dashboard"
    
    private var adminContentArea: FrameLayout? = null
    private var studentContentArea: FrameLayout? = null
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }
    private var reminderRunnable: Runnable? = null

    // Premium UI Color Palette
    private val bg = Color.parseColor("#F4F4F7")
    private val surface = Color.WHITE
    private val surface2 = Color.parseColor("#FAFAFC")
    private val border = Color.parseColor("#EAEAEF")
    private val ink = Color.parseColor("#16161A")
    private val ink2 = Color.parseColor("#6B6B76")
    private val ink3 = Color.parseColor("#A0A0AB")
    private val indigo = Color.parseColor("#4F46E5")
    private val indigoTint = Color.parseColor("#EEEDFD")
    private val green = Color.parseColor("#12875A")
    private val greenTint = Color.parseColor("#E4F7EE")
    private val red = Color.parseColor("#E5484D")
    private val redTint = Color.parseColor("#FDEBEC")
    private val amber = Color.parseColor("#D97706")
    private val amberTint = Color.parseColor("#FEF3E2")
    private val teal = Color.parseColor("#0D9488")
    private val tealTint = Color.parseColor("#E1F5F3")

    // region Lifecycle & Auth
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        window.statusBarColor = indigo

        val dbHelper = McqDatabase(this)
        val repository = McqRepository(dbHelper)

        viewModel = ViewModelProvider(this, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                return McqViewModel(repository, applicationContext) as T
            }
        }).get(McqViewModel::class.java)

        val user = viewModel.currentUser.value
        if (user != null) {
            if (user.role == "Student") showStudentPanel() else showAdminPanel()
        } else {
            showLoginScreen()
        }
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        val user = viewModel.currentUser.value
        if (user != null) {
            if (user.role == "Student") {
                val appBar = (studentContentArea?.parent as? RelativeLayout)?.getChildAt(0)
                val backBtn = appBar?.findViewWithTag<View>("back_btn")
                if (backBtn?.visibility == View.VISIBLE) {
                    switchStudentTab(currentTab)
                    return
                }
            }
            if (user.role != "Student" && currentTab != "Dashboard") {
                switchTab("Dashboard")
                return
            }
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastBackPressTime < 2000) finish()
            else {
                lastBackPressTime = currentTime
                toast("Press again to exit SPEC MCQ")
            }
        } else super.onBackPressed()
    }

    private fun logout() {
        viewModel.setCurrentUser(null)
        showLoginScreen()
    }

    private fun showLoginScreen() {
        val username = input("Username")
        val password = input("Password", password = true)
        val root = screenRoot()
        root.addView(heroCard("SPEC MCQ", "Admin Panel Login", "WELCOME BACK", true))
        
        root.addView(card().apply {
            addView(sectionTitle("Account Login", "আপনার একাউন্টে প্রবেশ করুন"))
            addView(username); addView(password)
            addView(primaryButton("Login Now", "🔐") {
                val u = username.text.toString()
                val p = password.text.toString()
                if (u.isBlank() || p.isBlank()) { toast("Username এবং Password প্রদান করুন।"); return@primaryButton }
                viewModel.login(u, p) { user, status ->
                    runOnUiThread {
                        when (status) {
                            0 -> { viewModel.setCurrentUser(user); if (user?.role == "Student") showStudentPanel() else showAdminPanel() }
                            1 -> toast("ইউজার পাওয়া যায়নি।")
                            3 -> toast("ভুল পাসওয়ার্ড।")
                            else -> toast("সার্ভার কানেকশন এরর!")
                        }
                    }
                }
            })
            addView(text("Don't have an account?", 13f, false, ink3, Gravity.CENTER).apply { setPadding(0, dp(15), 0, 0) })
            val regLayout = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) }
            }
            regLayout.addView(outlineButton("Teacher Register", "👨‍🏫") { showRegisterScreen("Teacher") }.apply { layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { rightMargin = dp(5) } })
            regLayout.addView(outlineButton("Student Register", "🎓") { showRegisterScreen("Student") }.apply { layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(5) } })
            addView(regLayout)
        })
        setContentView(scroll(root))
    }

    private fun showRegisterScreen(role: String) {
        val fullName = input("Full Name"); val username = input("Username"); val password = input("Password", password = true)
        val root = screenRoot()
        root.addView(heroCard("Join $role", "Create your account", "SIGN UP", false))
        root.addView(card().apply {
            addView(sectionTitle("New Account", "তথ্য দিয়ে রেজিস্ট্রেশন সম্পন্ন করুন"))
            addView(fullName); addView(username); addView(password)
            addView(primaryButton("Complete Registration", "📝") {
                val u = username.text.toString(); val p = password.text.toString()
                if (u.isBlank() || p.isBlank()) return@primaryButton
                viewModel.register(u, p, role, fullName.text.toString()) { status ->
                    runOnUiThread { if (status == 0) { toast("রেজিস্ট্রেশন সফল হয়েছে!"); showLoginScreen() } else toast("ব্যর্থ হয়েছে (Error: $status)") }
                }
            })
            addView(outlineButton("Back to Login", "⬅") { showLoginScreen() })
        })
        setContentView(scroll(root))
    }
    // endregion

    // region Admin Panel
    private fun showAdminPanel() {
        currentTab = "Dashboard"
        val root = RelativeLayout(this).apply { setBackgroundColor(bg) }
        adminContentArea = FrameLayout(this).apply {
            id = View.generateViewId()
            layoutParams = RelativeLayout.LayoutParams(-1, -1).apply {
                addRule(RelativeLayout.ABOVE, View.generateViewId().also { navId ->
                    root.addView(createBottomNav(navId, listOf(Triple("Dashboard", "🏠", "Dashboard"), Triple("Subjects", "📚", "Subjects"), Triple("Reports", "📊", "Reports"), Triple("Profile", "👤", "Profile")), ::switchTab))
                })
            }
        }
        root.addView(adminContentArea)
        setContentView(root)
        switchTab(currentTab)
    }

    private fun switchTab(key: String) {
        currentTab = key
        val user = viewModel.currentUser.value ?: return
        updateBottomNavUI(adminContentArea?.parent as? RelativeLayout, 1, key)
        adminContentArea?.removeAllViews()
        val view = when (key) {
            "Dashboard" -> createDashboardView(user)
            "Subjects" -> createSubjectsView(user)
            "Reports" -> createReportsView()
            "Profile" -> createAdminProfileView(user)
            else -> View(this)
        }
        adminContentArea?.addView(swipeRefresh(view) { switchTab(key) })
    }

    private fun createDashboardView(user: User): View {
        val root = screenRoot()
        root.addView(heroCard(user.username, "Welcome back, 👋", "TEACHER · ADMIN", false))
        viewModel.getStats(user.id) { sCount, qCount, stCount, cCount ->
            runOnUiThread {
                val grid = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL }
                val row1 = LinearLayout(this@MainActivity).apply { weightSum = 2f }
                row1.addView(statCard("Subjects", sCount.toString(), indigo, indigoTint).apply { layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { rightMargin = dp(5) } })
                row1.addView(statCard("Questions", qCount.toString(), teal, tealTint).apply { layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(5) } })
                val row2 = LinearLayout(this@MainActivity).apply { weightSum = 2f; layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) } }
                row2.addView(statCard("Students", stCount.toString(), amber, amberTint).apply { layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { rightMargin = dp(5) } })
                row2.addView(statCard("Contests", cCount.toString(), ink3, Color.parseColor("#F1F1F5")).apply { layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(5) } })
                grid.addView(row1); grid.addView(row2); root.addView(grid)
            }
        }
        root.addView(sectionHeader("Quick Actions"))
        val qa = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; weightSum = 4f }
        qa.addView(quickAction("Add Sub", "➕", indigoTint, indigo) { switchTab("Subjects") }.apply { layoutParams = LinearLayout.LayoutParams(0, -2, 1f) })
        qa.addView(quickAction("Add Que", "✏️", indigoTint, indigo) { 
            viewModel.getSubjects(user.id) { subs -> runOnUiThread { if (subs.isNotEmpty()) showSubjectDetail(subs[0]) else { toast("প্রথমে একটি সাবজেক্ট তৈরি করুন"); switchTab("Subjects") } } }
        }.apply { layoutParams = LinearLayout.LayoutParams(0, -2, 1f) })
        qa.addView(quickAction("Reports", "📊", indigoTint, indigo) { switchTab("Reports") }.apply { layoutParams = LinearLayout.LayoutParams(0, -2, 1f) })
        qa.addView(quickAction("Remind", "🔔", indigoTint, indigo) { 
            viewModel.getSubjects(user.id) { subs -> runOnUiThread { val c = subs.firstOrNull { it.isContest }; if (c != null) showSendRemindersPage(c) else switchTab("Subjects") } }
        }.apply { layoutParams = LinearLayout.LayoutParams(0, -2, 1f) })
        root.addView(qa); root.addView(sectionHeader("Recent Activity"))
        root.addView(card().apply { setPadding(dp(14), dp(6), dp(14), dp(6)); addView(activityRow("Subject created recently", "Today")); addView(activityRow("New student registered", "Yesterday")) })
        return root
    }

    private fun createSubjectsView(user: User): View {
        val root = screenRoot()
        root.addView(text("Subjects", 24f, true, ink, Gravity.START).apply { letterSpacing = -0.05f })
        root.addView(text("সাবজেক্ট সিলেক্ট করলে তার প্রশ্নগুলো দেখা যাবে", 13f, false, ink2, Gravity.START).apply { setPadding(0, 0, 0, dp(15)) })
        root.addView(card().apply {
            addView(chip("NEW SUBJECT", indigo))
            val nIn = input("Subject Name"); val cIn = input("Subject Code")
            addView(nIn); addView(cIn)
            val cSw = CheckBox(this@MainActivity).apply { text = "Enable Contest Mode"; setTextColor(ink); typeface = Typeface.DEFAULT_BOLD; buttonTintList = ColorStateList.valueOf(indigo); layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) } }
            addView(cSw)
            val cFi = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE }
            val dIn = input("Start Date").apply { isFocusable = false }; val tIn = input("Start Time").apply { isFocusable = false }; val drIn = input("Duration (Minutes)").apply { inputType = InputType.TYPE_CLASS_NUMBER }
            val cal = Calendar.getInstance()
            dIn.setOnClickListener { DatePickerDialog(this@MainActivity, { _, y, m, d -> cal.set(y, m, d); dIn.setText(SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)) }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show() }
            tIn.setOnClickListener { TimePickerDialog(this@MainActivity, { _, h, min -> cal.set(Calendar.HOUR_OF_DAY, h); cal.set(Calendar.MINUTE, min); tIn.setText(SimpleDateFormat("hh:mm a", Locale.US).format(cal.time)) }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), false).show() }
            cFi.addView(dIn); cFi.addView(tIn); cFi.addView(drIn); addView(cFi)
            cSw.setOnCheckedChangeListener { _, isChecked -> cFi.visibility = if (isChecked) View.VISIBLE else View.GONE }
            addView(primaryButton("Save Subject", "💾") {
                val n = nIn.text.toString().trim(); val c = cIn.text.toString().trim().uppercase()
                if (n.isEmpty() || c.isEmpty()) return@primaryButton
                viewModel.addSubject(user.id, n, c, cSw.isChecked, if (cSw.isChecked) cal.timeInMillis else 0, drIn.text.toString().toIntOrNull() ?: 0) { ok, _ -> runOnUiThread { if (ok) switchTab("Subjects") } }
            })
        })
        root.addView(sectionHeader("All Subjects"))
        viewModel.getSubjects(user.id) { subs -> runOnUiThread { val listCard = card().apply { setPadding(dp(4), dp(12), dp(4), dp(12)) }; subs.forEachIndexed { i, s -> listCard.addView(createSubjectRow(s, i + 1)) }; root.addView(listCard) } }
        return root
    }

    private fun createSubjectRow(sub: SubjectItem, index: Int): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; setPadding(dp(12), dp(13), dp(12), dp(13)); gravity = Gravity.CENTER_VERTICAL
        addView(text(index.toString(), 12f, true, ink3, Gravity.CENTER).apply { layoutParams = LinearLayout.LayoutParams(dp(20), -2) })
        val left = LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; layoutParams = LinearLayout.LayoutParams(0, -2, 1f); setOnClickListener { showSubjectDetail(sub) }
            addView(text("📚", 18f, false, indigo, Gravity.CENTER).apply { background = round(indigoTint, dp(10).toFloat()); setPadding(dp(8), dp(8), dp(8), dp(8)); layoutParams = LinearLayout.LayoutParams(dp(34), dp(34)) })
            addView(LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(11), 0, 0, 0); addView(text(sub.name, 13.5f, true, ink, Gravity.START)); addView(text(sub.code, 11.5f, false, ink3, Gravity.START)) })
        }
        addView(left)
        if (sub.isContest) addView(TextView(this@MainActivity).apply { text = "🔔"; textSize = 16f; setPadding(dp(10), dp(10), dp(10), dp(10)); setOnClickListener { showSendRemindersPage(sub) } })
        addView(TextView(this@MainActivity).apply { text = "✏️"; textSize = 16f; setPadding(dp(10), dp(10), dp(10), dp(10)); setOnClickListener { showEditSubjectPage(sub) } })
        addView(text("→", 18f, true, ink3, Gravity.CENTER).apply { setPadding(dp(10), dp(10), dp(10), dp(10)); setOnClickListener { showSubjectDetail(sub) } })
    }

    private fun showSubjectDetail(sub: SubjectItem) {
        adminContentArea?.removeAllViews(); val root = screenRoot()
        root.addView(createAppBar(sub.name, "${sub.name} · Questions", "Subjects", ::switchTab))
        root.addView(text(sub.name, 24f, true, ink, Gravity.START))
        root.addView(text("এই সাবজেক্টের প্রশ্ন যোগ ও পরিচালনা করুন", 13f, false, ink2, Gravity.START).apply { setPadding(0, dp(4), 0, dp(18)) })
        val container = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL }
        val drafts = mutableListOf<QuestionDraftViews>()
        fun addRow() {
            container.addView(card().apply {
                val row = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, 0, 0, dp(12)) }
                row.addView(text("✎", 14f, true, indigo, Gravity.CENTER)); row.addView(text("QUESTION #${drafts.size + 1}", 11.5f, true, indigo, Gravity.START).apply { setPadding(dp(6), 0, 0, 0); letterSpacing = 0.04f })
                addView(row); val q = input("Question Text"); val a = input("Option A"); val b = input("Option B"); val c = input("Option C"); val d = input("Option D"); val cor = input("Correct (A/B/C/D)")
                addView(q); addView(a); addView(b); addView(c); addView(d); addView(cor)
                addView(TextView(this@MainActivity).apply { text = "✖ Remove This"; setTextColor(red); textSize = 12f; gravity = Gravity.END; setPadding(0, dp(8), 0, 0); setOnClickListener { container.removeView(this@apply) } })
                drafts.add(QuestionDraftViews(q, a, b, c, d, cor))
            })
        }
        addRow(); root.addView(container)
        root.addView(LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL; addView(outlineButton("Add Another Question", "➕") { addRow() }.apply { layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) } }); addView(primaryButton("Save All Questions", "💾") {
            val valid = drafts.mapNotNull { d -> if (d.question.text.isNotEmpty()) Triple(d.question.text.toString(), listOf(d.optionA.text.toString(), d.optionB.text.toString(), d.optionC.text.toString(), d.optionD.text.toString()), d.correct.text.toString().uppercase()) else null }
            if (valid.isEmpty()) { toast("প্রশ্ন লিখুন"); return@primaryButton }
            viewModel.addQuestions(sub.id, valid) { s, _ -> runOnUiThread { if (s > 0) { toast("$s টি প্রশ্ন সেভ হয়েছে"); showSubjectDetail(sub) } } }
        }) })
        root.addView(sectionHeader("Questions in ${sub.name}"))
        viewModel.getQuestions(sub.id) { qs -> runOnUiThread { qs.forEach { q -> root.addView(card().apply {
            val r = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            r.addView(TextView(this@MainActivity).apply { text = "🕒"; gravity = Gravity.CENTER; background = round(indigoTint, dp(9).toFloat()); layoutParams = LinearLayout.LayoutParams(dp(30), dp(30)) })
            r.addView(LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, -2, 1f); setPadding(dp(12), 0, dp(12), 0); addView(text(q.text, 14f, true, ink, Gravity.START)); addView(text("MCQ · Answer: ${q.correctOption}", 11.5f, false, ink3, Gravity.START)) })
            r.addView(MaterialButton(this@MainActivity, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply { text = "🗑 Delete"; textSize = 11f; setTextColor(red); cornerRadius = dp(8); setPadding(dp(8), 0, dp(8), 0); backgroundTintList = ColorStateList.valueOf(redTint); strokeWidth = 0; layoutParams = LinearLayout.LayoutParams(-2, dp(36)); setOnClickListener { viewModel.deleteQuestion(sub.id, q.id) { runOnUiThread { showSubjectDetail(sub) } } } })
            addView(r)
        }) } } }
        adminContentArea?.addView(scroll(root))
    }

    private fun showSendRemindersPage(sub: SubjectItem) {
        adminContentArea?.removeAllViews(); val root = screenRoot()
        root.addView(createAppBar("SPEC MCQ", "Send Reminders", "Subjects", ::switchTab))
        root.addView(text("Send Reminders", 24f, true, ink, Gravity.START))
        root.addView(text("পরীক্ষা শুরুর আগে স্টুডেন্টদের রিমাইন্ডার পাঠান", 13f, false, ink2, Gravity.START).apply { setPadding(0, dp(4), 0, dp(18)) })
        root.addView(card().apply {
            addView(reminderInfoRow("Subject", sub.name))
            addView(reminderInfoRow("Contest Start", if (sub.isContest) SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.US).format(Date(sub.startTime)) else "Contest Mode বন্ধ আছে").apply { if (!sub.isContest) (getChildAt(1) as TextView).typeface = Typeface.DEFAULT_BOLD })
            addView(LinearLayout(this@MainActivity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(20) }; addView(text("AUTO REMINDER TIER", 11f, true, indigo, Gravity.START).apply { letterSpacing = 0.05f }); addView(View(this@MainActivity).apply { layoutParams = LinearLayout.LayoutParams(dp(8), 1) }); addView(text("সার্ভার টাইম", 9f, true, green, Gravity.CENTER).apply { background = round(greenTint, dp(6).toFloat()); setPadding(dp(6), dp(2), dp(6), dp(2)) }) })
            val tLayout = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.HORIZONTAL; layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) } }
            val tChips = listOf(10, 5, 2, 1).map { min -> text("${min}m আগে", 12f, false, ink3, Gravity.CENTER).apply { background = round(surface2, dp(10).toFloat(), border, dp(1)); layoutParams = LinearLayout.LayoutParams(0, dp(45), 1f).apply { marginEnd = dp(8) }; tag = min } }
            tChips.forEach { tLayout.addView(it) }; addView(tLayout)
            if (!sub.isContest) addView(outlineButton("Contest Mode চালু করুন", "⏰") { showEditSubjectPage(sub) }.apply { layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(20) } })
            addView(primaryButton("Send Reminder Now", "🔔") { viewModel.sendReminder(viewModel.currentUser.value?.id ?: 0, sub.id, "Contest '${sub.name}' is starting!") { count -> runOnUiThread { toast("$count জন স্টুডেন্টকে রিমাইন্ডার পাঠানো হয়েছে") } } }.apply { layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(16) } })
            if (sub.isContest) { reminderRunnable?.let { mainHandler.removeCallbacks(it) }; reminderRunnable = object : Runnable { override fun run() {
                val diffMin = (sub.startTime - System.currentTimeMillis()) / 60000f
                val active = when { diffMin > 5 && diffMin <= 10 -> 10; diffMin > 2 && diffMin <= 5 -> 5; diffMin > 1 && diffMin <= 2 -> 2; diffMin > 0 && diffMin <= 1 -> 1; else -> -1 }
                tChips.forEach { c -> val m = c.tag as Int; if (m == active) { c.background = round(indigo, dp(10).toFloat()); c.setTextColor(Color.WHITE); c.typeface = Typeface.DEFAULT_BOLD } else { c.background = round(surface2, dp(10).toFloat(), border, dp(1)); c.setTextColor(ink3); c.typeface = Typeface.DEFAULT } }
                mainHandler.postDelayed(this, 5000)
            } }; mainHandler.post(reminderRunnable!!) }
        })
        adminContentArea?.addView(scroll(root))
    }

    private fun createReportsView(): View {
        val root = screenRoot()
        root.addView(text("Reports", 24f, true, ink, Gravity.START).apply { letterSpacing = -0.05f })
        root.addView(text("প্রতিটি সাবজেক্টের পারফরম্যান্স সামারি", 13f, false, ink2, Gravity.START).apply { setPadding(0,0,0,dp(18)) })
        viewModel.getSubjects(viewModel.currentUser.value?.id ?: 0) { subs -> runOnUiThread { val listCard = card().apply { setPadding(dp(4), dp(12), dp(4), dp(12)) }; subs.forEach { s -> listCard.addView(LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.HORIZONTAL; setPadding(dp(12), dp(13), dp(12), dp(13)); gravity = Gravity.CENTER_VERTICAL; setOnClickListener { showSubjectReport(s) }
            addView(text("📊", 18f, false, indigo, Gravity.CENTER).apply { background = round(indigoTint, dp(10).toFloat()); setPadding(dp(8), dp(8), dp(8), dp(8)); layoutParams = LinearLayout.LayoutParams(dp(34), dp(34)) })
            addView(LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), 0, 0, 0); layoutParams = LinearLayout.LayoutParams(0, -2, 1f); addView(text(s.name, 13.5f, true, ink, Gravity.START)); addView(text("View performance report", 11.5f, false, ink3, Gravity.START)) })
            addView(text("→", 18f, true, ink3, Gravity.CENTER))
        }) }; root.addView(listCard) } }
        return root
    }

    private fun showSubjectReport(sub: SubjectItem) {
        adminContentArea?.removeAllViews(); val root = screenRoot()
        root.addView(createAppBar("SPEC MCQ", "${sub.name} · Results", "Reports", ::switchTab))
        root.addView(text("${sub.name} Results", 24f, true, ink, Gravity.START))
        root.addView(text("এই সাবজেক্টের সকল স্টুডেন্টের রেজাল্ট", 13f, false, ink2, Gravity.START).apply { setPadding(0, dp(4), 0, dp(18)) })
        viewModel.getResultsForSubject(sub.id) { results -> runOnUiThread {
            if (results.isEmpty()) root.addView(card().apply { gravity = Gravity.CENTER; addView(text("কোন রেজাল্ট পাওয়া যায়নি", 14f, true, ink3, Gravity.CENTER)) })
            else {
                root.addView(card().apply { val row = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.HORIZONTAL }; row.addView(statCard("Participants", results.size.toString(), indigo, indigoTint).apply { layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { rightMargin = dp(5) } }); row.addView(statCard("Avg Score", String.format(Locale.US, "%.1f%%", results.map { it.percent }.average()), teal, tealTint).apply { layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(5) } }); addView(row) })
                root.addView(sectionHeader("Student Performance List"))
                results.forEach { res -> root.addView(card().apply {
                    val row = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
                    row.addView(text(res.username.take(1).uppercase(), 14f, true, indigo, Gravity.CENTER).apply { background = round(indigoTint, dp(15).toFloat()); layoutParams = LinearLayout.LayoutParams(dp(30), dp(30)) })
                    row.addView(LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, -2, 1f); setPadding(dp(12), 0, dp(12), 0); addView(text(res.username, 14f, true, ink, Gravity.START)); addView(text("Submitted: ${SimpleDateFormat("dd MMM, hh:mm a", Locale.US).format(Date(res.submittedAt.toLongOrNull() ?: 0L))}", 11.5f, false, ink3, Gravity.START)) })
                    row.addView(LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.END; addView(text("${res.correct}/${res.total}", 16f, true, if (res.percent >= 40) green else red, Gravity.END)) })
                    addView(row)
                }) }
            }
        } }
        adminContentArea?.addView(scroll(root))
    }

    private fun showEditSubjectPage(sub: SubjectItem) {
        adminContentArea?.removeAllViews(); val root = screenRoot()
        root.addView(createAppBar("SPEC MCQ", "Edit Subject", "Subjects", ::switchTab))
        root.addView(text("Edit Subject", 24f, true, ink, Gravity.START))
        root.addView(text("ID: #${sub.id}", 13f, false, ink3, Gravity.START).apply { setPadding(0, dp(4), 0, dp(18)) })
        val nIn = input("Subject Name").apply { setText(sub.name) }; val cIn = input("Subject Code").apply { setText(sub.code) }
        root.addView(card().apply {
            addView(chip("SUBJECT DETAILS", indigo)); addView(nIn); addView(cIn)
            val cSw = CheckBox(this@MainActivity).apply { text = "Enable Contest Mode"; setTextColor(ink); typeface = Typeface.DEFAULT_BOLD; buttonTintList = ColorStateList.valueOf(indigo); isChecked = sub.isContest; layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) } }
            addView(cSw)
            val cFi = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL; visibility = if (sub.isContest) View.VISIBLE else View.GONE }
            val cal = Calendar.getInstance().apply { if (sub.isContest) timeInMillis = sub.startTime }
            val dIn = input("Start Date").apply { isFocusable = false; if (sub.isContest) setText(SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)) }
            val tIn = input("Start Time").apply { isFocusable = false; if (sub.isContest) setText(SimpleDateFormat("hh:mm a", Locale.US).format(cal.time)) }
            val drIn = input("Duration (Minutes)").apply { inputType = InputType.TYPE_CLASS_NUMBER; if (sub.isContest) setText(sub.durationMin.toString()) }
            dIn.setOnClickListener { DatePickerDialog(this@MainActivity, { _, y, m, d -> cal.set(y, m, d); dIn.setText(SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)) }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show() }
            tIn.setOnClickListener { TimePickerDialog(this@MainActivity, { _, h, min -> cal.set(Calendar.HOUR_OF_DAY, h); cal.set(Calendar.MINUTE, min); tIn.setText(SimpleDateFormat("hh:mm a", Locale.US).format(cal.time)) }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), false).show() }
            cFi.addView(dIn); cFi.addView(tIn); cFi.addView(drIn); addView(cFi)
            cSw.setOnCheckedChangeListener { _, isChecked -> cFi.visibility = if (isChecked) View.VISIBLE else View.GONE }
            addView(primaryButton("Save Changes", "✓") {
                val n = nIn.text.toString().trim(); val c = cIn.text.toString().trim().uppercase()
                if (n.isEmpty() || c.isEmpty()) { toast("দয়া করে নাম এবং কোড পূরণ করুন"); return@primaryButton }
                viewModel.updateSubject(sub.id, n, c, cSw.isChecked, if (cSw.isChecked) cal.timeInMillis else 0, drIn.text.toString().toIntOrNull() ?: 0) { ok -> runOnUiThread { if (ok) { toast("পরিবর্তনগুলো সেভ হয়েছে"); switchTab("Subjects") } else toast("সেভ করতে ব্যর্থ হয়েছে") } }
            })
            addView(TextView(this@MainActivity).apply { text = "✕  Cancel"; textSize = 14f; setTextColor(indigo); gravity = Gravity.CENTER; setPadding(0, dp(16), 0, dp(8)); typeface = Typeface.DEFAULT_BOLD; setOnClickListener { switchTab("Subjects") } })
        })
        root.addView(sectionHeader("Danger Zone"))
        root.addView(dangerGhostButton("Delete Subject") { viewModel.deleteSubject(sub.id) { ok -> runOnUiThread { if (ok) { toast("সাবজেক্টটি ডিলিট করা হয়েছে"); switchTab("Subjects") } else toast("ডিলিট করতে ব্যর্থ হয়েছে") } } })
        adminContentArea?.addView(scroll(root))
    }

    private fun createAdminProfileView(user: User): View {
        val root = screenRoot()
        root.addView(text("Profile", 24f, true, ink, Gravity.START).apply { letterSpacing = -0.05f })
        root.addView(text("অ্যাকাউন্ট সংক্রান্ত তথ্য", 13f, false, ink2, Gravity.START).apply { setPadding(0,0,0,dp(18)) })
        root.addView(card().apply {
            val header = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            header.addView(text(user.username.take(1).uppercase(), 16f, true, indigo, Gravity.CENTER).apply { background = round(indigoTint, dp(21).toFloat()); layoutParams = LinearLayout.LayoutParams(dp(42), dp(42)) })
            header.addView(LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(13), 0, 0, 0); addView(text(user.username, 13.5f, true, ink, Gravity.START)); addView(text("Teacher", 11.5f, false, ink3, Gravity.START)) })
            addView(header)
        })
        root.addView(dangerGhostButton("Logout") { logout() }.apply { layoutParams = LinearLayout.LayoutParams(-1, dp(50)).apply { topMargin = dp(6) } })
        return root
    }
    // endregion

    // region Student Panel
    private fun showStudentPanel() {
        currentTab = "Home"
        if (viewModel.currentUser.value == null) return showLoginScreen()
        val root = RelativeLayout(this).apply { setBackgroundColor(bg) }
        // Top App Bar
        val appBar = LinearLayout(this).apply {
            id = View.generateViewId()
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(22), dp(20), dp(22), dp(14))
            layoutParams = RelativeLayout.LayoutParams(-1, -2)
            
            // Left Container (Back Button + Title) - TAKES UP REMAINING SPACE
            val titleContainer = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
                
                addView(FrameLayout(this@MainActivity).apply { 
                    id = View.generateViewId()
                    tag = "back_btn" 
                    background = round(surface, dp(15).toFloat())
                    elevation = dp(1).toFloat()
                    layoutParams = LinearLayout.LayoutParams(dp(30), dp(30))
                    visibility = View.GONE
                    setOnClickListener { switchStudentTab(currentTab) }
                    addView(text("‹", 20f, true, ink, Gravity.CENTER).apply { setPadding(0, 0, 0, dp(2)) }) 
                })
                
                addView(LinearLayout(this@MainActivity).apply { 
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(10), 0, 0, 0)
                    addView(text("SPEC MCQ", 21f, true, ink, Gravity.START).apply { letterSpacing = -0.02f })
                    addView(text("Student Portal", 12f, false, ink3, Gravity.START).apply { 
                        id = View.generateViewId()
                        tag = "app_bar_sub"
                    }) 
                })
            }
            addView(titleContainer)

            // Right part: Notification Bell - PINNED TO RIGHT
            addView(FrameLayout(this@MainActivity).apply { 
                background = round(surface, dp(18).toFloat())
                elevation = dp(1).toFloat()
                layoutParams = LinearLayout.LayoutParams(dp(36), dp(36))
                setOnClickListener { showStudentNotifications() }
                addView(text("🔔", 16f, false, ink2, Gravity.CENTER))
                addView(View(this@MainActivity).apply { 
                    background = round(red, dp(4).toFloat(), surface, dp(1))
                    layoutParams = FrameLayout.LayoutParams(dp(8), dp(8)).apply { gravity = Gravity.TOP or Gravity.END; topMargin = dp(7); marginEnd = dp(8) }
                }) 
            })
        }
        root.addView(appBar)
        studentContentArea = FrameLayout(this).apply {
            id = View.generateViewId()
            layoutParams = RelativeLayout.LayoutParams(-1, -1).apply {
                addRule(RelativeLayout.BELOW, appBar.id)
                addRule(RelativeLayout.ABOVE, View.generateViewId().also { navId ->
                    root.addView(createBottomNav(navId, listOf(Triple("Home", "🏠", "Home"), Triple("Contests", "📋", "Contests"), Triple("Profile", "👤", "Profile")), ::switchStudentTab))
                })
            }
        }
        root.addView(studentContentArea)
        setContentView(root); currentTab = "Home"; switchStudentTab(currentTab)
    }

    private var studentHomeContainer: ScrollView? = null
    private var studentContestsContainer: ScrollView? = null
    private var studentProfileContainer: ScrollView? = null
    private var studentSubPageContainer: FrameLayout? = null
    private var lastContestListState: List<SubjectItem>? = null
    private var contestTimerRunnable: Runnable? = null

    private fun switchStudentTab(key: String) {
        val user = viewModel.currentUser.value ?: return
        val root = studentContentArea?.parent as? RelativeLayout
        updateBottomNavUI(root, 2, key)
        
        val appBar = root?.getChildAt(0)
        val backBtn = appBar?.findViewWithTag<View>("back_btn")
        val subTitle = appBar?.findViewWithTag<TextView>("app_bar_sub")
        
        backBtn?.visibility = View.GONE
        subTitle?.text = when(key) { "Home" -> "Student Portal"; "Contests" -> "My Contests"; "Profile" -> "Profile"; else -> "Student" }
        
        val container = studentContentArea ?: return
        
        // Safety: Ensure SwipeRefreshLayout is present and properly managed
        var swipe = container.getChildAt(0) as? androidx.swiperefreshlayout.widget.SwipeRefreshLayout
        if (swipe == null) {
            swipe = swipeRefresh(FrameLayout(this@MainActivity).apply { 
                layoutParams = FrameLayout.LayoutParams(-1, -1)
            }) { 
                viewModel.prefetchStudentData(viewModel.currentUser.value?.id ?: 0, true)
                switchStudentTab(currentTab) 
            }
            container.removeAllViews()
            container.addView(swipe)
        }

        val tabContentArea = swipe.getChildAt(0) as? FrameLayout ?: return

        // Ultra-Fast Visibility Switching without adding/removing views
        if (studentHomeContainer == null) {
            studentHomeContainer = scroll(createStudentHomeView(user))
            studentContestsContainer = scroll(createStudentContestsView())
            studentProfileContainer = scroll(createStudentProfileView(user))
            studentSubPageContainer = FrameLayout(this).apply { layoutParams = FrameLayout.LayoutParams(-1, -1) }
            
            tabContentArea.addView(studentHomeContainer)
            tabContentArea.addView(studentContestsContainer)
            tabContentArea.addView(studentProfileContainer)
            tabContentArea.addView(studentSubPageContainer)
        }

        currentTab = key
        studentHomeContainer?.visibility = if (key == "Home") View.VISIBLE else View.GONE
        studentContestsContainer?.visibility = if (key == "Contests") View.VISIBLE else View.GONE
        studentProfileContainer?.visibility = if (key == "Profile") View.VISIBLE else View.GONE
        studentSubPageContainer?.visibility = View.GONE
        
        // Trigger background refresh for dynamic data without blocking
        if (key == "Contests") {
            (studentContestsContainer?.getChildAt(0) as? LinearLayout)?.findViewWithTag<LinearLayout>("contest_list_container")?.let { 
                updateContestListUI(it)
            }
        }
        swipe.isRefreshing = false
    }

    private fun createStudentHomeView(user: User): View {
        val root = screenRoot()
        root.addView(heroCard(user.fullName, "স্বাগতম ফিরে,", "STUDENT", false))
        root.addView(card().apply {
            addView(LinearLayout(this@MainActivity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, 0, 0, dp(12)); addView(text("🔍", 14f, true, indigo, Gravity.CENTER)); addView(text("FIND EXAM", 11.5f, true, indigo, Gravity.START).apply { setPadding(dp(6), 0, 0, 0); letterSpacing = 0.04f }) })
            addView(text("সাবজেক্ট কোড দিয়ে সার্চ করুন", 13f, false, ink2, Gravity.START).apply { setPadding(0, 0, 0, dp(12)) })
            val cIn = input("Subject Code"); addView(cIn)
            val res = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL }
            addView(primaryButton("Search Now", "🔍") {
                val q = cIn.text.toString().trim()
                if (q.isNotEmpty()) {
                    res.removeAllViews()
                    res.addView(text("Searching...", 13f, true, indigo, Gravity.CENTER).apply { setPadding(0, dp(15), 0, dp(10)) })
                    viewModel.searchSubject(user.id, q) { s -> 
                        runOnUiThread { 
                            res.removeAllViews()
                            if (s != null) res.addView(createSearchQueryResult(s)) 
                            else toast("Subject পাওয়া যায়নি।") 
                        } 
                    }
                }
            }); addView(res)
        })
        root.addView(primaryButton("Registered Contests", "📝") { switchStudentTab("Contests") }.apply { backgroundTintList = ColorStateList.valueOf(teal); layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) } })
        return root
    }

    private fun createSearchQueryResult(sub: SubjectItem): View = card().apply {
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14); bottomMargin = 0 }; background = round(surface2, dp(14).toFloat(), border, dp(1))
        val top = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.HORIZONTAL }
        val info = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, -2, 1f) }
        info.addView(text(sub.name, 16f, true, ink, Gravity.START)); info.addView(text("Code: ${sub.code}", 12.5f, false, ink2, Gravity.START))
        
        val statusText = when {
            sub.hasSubmitted -> "SUBMITTED"
            sub.isRegistered -> "REGISTERED"
            else -> "OPEN"
        }
        val statusColor = if (sub.hasSubmitted) teal else green
        val statusBg = if (sub.hasSubmitted) tealTint else greenTint
        
        top.addView(info); top.addView(text(statusText, 10f, true, statusColor, Gravity.CENTER).apply { background = round(statusBg, dp(10).toFloat()); setPadding(dp(9), dp(4), dp(9), dp(4)) }); addView(top)
        
        val footerMsg = when {
            sub.hasSubmitted -> "আপনি এই পরীক্ষায় ইতিমধ্যে অংশগ্রহণ করেছেন"
            sub.isRegistered -> "আপনি নিবন্ধিত আছেন, পরীক্ষার সময় জয়েন করুন"
            else -> "কনটেস্ট শুরু হয়নি, রেজিস্ট্রেশন করে অপেক্ষা করুন"
        }
        addView(text(footerMsg, 11.5f, false, ink3, Gravity.START).apply { setPadding(0, dp(4), 0, dp(12)) })
        
        if (!sub.isRegistered && !sub.hasSubmitted) {
            addView(primaryButton("Register", "✓") { viewModel.registerForContest(viewModel.currentUser.value?.id ?: 0, sub.id) { ok -> runOnUiThread { if (ok) { toast("Registration সফল!"); switchStudentTab("Contests") } else { toast("রেজিস্ট্রেশন করতে সমস্যা হয়েছে। আবার চেষ্টা করুন।") } } } }.apply { backgroundTintList = ColorStateList.valueOf(green) })
        }
    }

    private fun createStudentContestsView(): View {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(10), dp(20), dp(32)) }
        root.addView(text("My Contests", 26f, true, ink, Gravity.START).apply { letterSpacing = -0.04f })
        root.addView(text("আপনার রেজিস্টার্ড সব পরীক্ষা", 14f, false, ink2, Gravity.START).apply { setPadding(0, dp(2), 0, dp(22)) })
        
        val listContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; tag = "contest_list_container" }
        root.addView(listContainer)
        updateContestListUI(listContainer)

        // Start Global Countdown Engine
        startContestCountdownEngine(listContainer)
        
        return root
    }

    private fun startContestCountdownEngine(container: LinearLayout) {
        contestTimerRunnable?.let { mainHandler.removeCallbacks(it) }
        contestTimerRunnable = object : Runnable {
            override fun run() {
                val list = viewModel.studentContests.value
                if (container.childCount == list.size) {
                    list.forEachIndexed { i, s ->
                        val card = container.getChildAt(i) as? LinearLayout
                        if (card != null) bindContestCard(card, s)
                    }
                }
                mainHandler.postDelayed(this, 1000)
            }
        }
        mainHandler.post(contestTimerRunnable!!)
    }

    private fun updateContestListUI(container: LinearLayout) {
        val newList = viewModel.studentContests.value
        if (lastContestListState != null && lastContestListState == newList && container.childCount > 0) return
        lastContestListState = newList
        
        // Optimized recycling: instead of removeAll, we match size and update
        val targetSize = newList.size
        if (targetSize == 0) {
            container.removeAllViews()
            container.addView(emptyState("No Contests", "আপনি এখনো কোনো পরীক্ষায় রেজিস্ট্রেশন করেননি", "📋"))
            return
        }

        // Remove empty state if present
        if (container.childCount > 0 && container.getChildAt(0).tag == "empty_state") container.removeAllViews()

        // Match child count to data size
        while (container.childCount < targetSize) container.addView(createContestCardPlaceholder())
        while (container.childCount > targetSize) container.removeViewAt(container.childCount - 1)

        // Update each card with new data (God-Speed recycling)
        newList.forEachIndexed { i, s ->
            val card = container.getChildAt(i) as LinearLayout
            bindContestCard(card, s)
        }
    }

    private fun createContestCardPlaceholder(): View = card().apply {
        val top = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.TOP }
        val info = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, -2, 1f) }
        info.addView(text("", 17f, true, ink, Gravity.START).apply { tag = "name" })
        info.addView(text("", 13f, false, ink2, Gravity.START).apply { tag = "code" })
        top.addView(info)
        top.addView(text("", 10f, true, Color.WHITE, Gravity.CENTER).apply { tag = "status_chip"; setPadding(dp(10), dp(5), dp(10), dp(5)) })
        addView(top)
        addView(text("", 12.5f, false, ink3, Gravity.START).apply { tag = "sub_text"; setPadding(0, dp(6), 0, dp(14)) })
        addView(primaryButton("", "") {}.apply { tag = "action_btn" })
    }

    private fun bindContestCard(card: LinearLayout, s: SubjectItem) {
        card.findViewWithTag<TextView>("name")?.text = s.name
        card.findViewWithTag<TextView>("code")?.text = "Code: ${s.code}"
        val chip = card.findViewWithTag<TextView>("status_chip")
        val subText = card.findViewWithTag<TextView>("sub_text")
        val btn = card.findViewWithTag<MaterialButton>("action_btn")

        val now = System.currentTimeMillis()
        val startTime = s.startTime
        val endTime = startTime + (s.durationMin * 60000)

        if (s.hasSubmitted) {
            chip?.text = "SUBMITTED"
            chip?.setTextColor(teal)
            chip?.background = round(tealTint, dp(10).toFloat())
            subText?.text = "Already Submitted"
            
            if (now > endTime) {
                btn?.text = "📈  Result Published"
                btn?.backgroundTintList = ColorStateList.valueOf(teal)
                btn?.setOnClickListener { 
                    viewModel.getStudentResult(viewModel.currentUser.value?.id ?: 0, s.id) { res ->
                        runOnUiThread { if (res != null) showIndividualResult(res, s.name) else toast("রেজাল্ট লোড করা যায়নি") }
                    }
                }
            } else {
                btn?.text = "🔒 Result Locked"
                btn?.backgroundTintList = ColorStateList.valueOf(ink3)
                btn?.setOnClickListener { toast("কনটেস্ট শেষ হওয়ার পর রেজাল্ট দেখতে পারবেন।") }
            }
        } else if (now < startTime) {
            // Waiting to start
            val diff = startTime - now
            val h = diff / 3600000; val m = (diff % 3600000) / 60000; val sec = (diff % 60000) / 1000
            chip?.text = "UPCOMING"
            chip?.setTextColor(amber)
            chip?.background = round(amberTint, dp(10).toFloat())
            subText?.text = String.format(Locale.US, "Starts in: %02d:%02d:%02d", h, m, sec)
            btn?.text = "⏳ Waiting..."
            btn?.backgroundTintList = ColorStateList.valueOf(ink3)
            btn?.setOnClickListener { toast("পরীক্ষা এখনো শুরু হয়নি। দয়া করে অপেক্ষা করুন।") }
        } else if (now in startTime..endTime) {
            // Running
            val diff = endTime - now
            val h = diff / 3600000; val m = (diff % 3600000) / 60000; val sec = (diff % 60000) / 1000
            chip?.text = "LIVE"
            chip?.setTextColor(green)
            chip?.background = round(greenTint, dp(10).toFloat())
            subText?.text = String.format(Locale.US, "Ends in: %02d:%02d:%02d", h, m, sec)
            btn?.text = "✎  Join Now"
            btn?.backgroundTintList = ColorStateList.valueOf(indigo)
            btn?.setOnClickListener { showExamPage(s) }
        } else {
            // Ended but not submitted
            chip?.text = "CLOSED"
            chip?.setTextColor(red)
            chip?.background = round(redTint, dp(10).toFloat())
            subText?.text = "Time is over"
            btn?.text = "🔒 Closed"
            btn?.backgroundTintList = ColorStateList.valueOf(ink3)
            btn?.setOnClickListener { toast("এই পরীক্ষার সময় শেষ হয়ে গেছে।") }
        }
    }

    private fun showIndividualResult(res: ExamResultRow, subjectName: String) {
        studentSubPageContainer?.removeAllViews()
        val root = screenRoot()
        
        val appBar = (studentContentArea?.parent as? RelativeLayout)?.getChildAt(0)
        val backBtn = appBar?.findViewWithTag<View>("back_btn")
        val subTitle = appBar?.findViewWithTag<TextView>("app_bar_sub")
        
        backBtn?.visibility = View.VISIBLE
        backBtn?.setOnClickListener { switchStudentTab(currentTab) }
        subTitle?.text = "Exam Result"
        
        root.addView(text("Performance Report", 24f, true, ink, Gravity.START))
        root.addView(text("আপনার পরীক্ষার ফলাফল বিশ্লেষণ", 13f, false, ink2, Gravity.START).apply { setPadding(0, 0, 0, dp(18)) })

        root.addView(card().apply {
            gravity = Gravity.CENTER
            setPadding(dp(20), dp(30), dp(20), dp(30))
            
            addView(text(subjectName, 18f, true, ink, Gravity.CENTER))
            addView(text("OVERALL SCORE", 11f, true, ink3, Gravity.CENTER).apply { setPadding(0, dp(15), 0, dp(5)); letterSpacing = 0.05f })
            
            val scoreLayout = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
            scoreLayout.addView(text(res.correct.toString(), 48f, true, if (res.percent >= 40) green else red, Gravity.CENTER))
            scoreLayout.addView(text("/", 24f, false, ink3, Gravity.CENTER).apply { setPadding(dp(4), dp(8), dp(4), 0) })
            scoreLayout.addView(text(res.total.toString(), 28f, true, ink2, Gravity.CENTER).apply { setPadding(0, dp(6), 0, 0) })
            addView(scoreLayout)
            
            addView(text(String.format(Locale.US, "%.1f%% Percentage", res.percent), 14f, true, ink2, Gravity.CENTER))
            
            val statusChip = text(if (res.percent >= 40) "PASSED" else "FAILED", 12f, true, Color.WHITE, Gravity.CENTER).apply {
                background = round(if (res.percent >= 40) green else red, dp(15).toFloat())
                setPadding(dp(20), dp(6), dp(20), dp(6))
                layoutParams = LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(20) }
            }
            addView(statusChip)
        })
        
        root.addView(card().apply {
            addView(reminderInfoRow("Submitted At", SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.US).format(Date(res.submittedAt.toLongOrNull() ?: 0L))))
            addView(reminderInfoRow("Accuracy", "${res.percent.toInt()}%"))
            addView(reminderInfoRow("Result Status", if (res.percent >= 40) "Satisfactory" else "Needs Improvement"))
        })

        root.addView(primaryButton("Back to Contests", "⬅") { switchStudentTab("Contests") }.apply { layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) } })
        
        studentSubPageContainer?.addView(scroll(root))
        
        // Toggle Visibility to show sub-page
        studentHomeContainer?.visibility = View.GONE
        studentContestsContainer?.visibility = View.GONE
        studentProfileContainer?.visibility = View.GONE
        studentSubPageContainer?.visibility = View.VISIBLE
    }

    private fun showExamPage(sub: SubjectItem) {
        if (sub.hasSubmitted) {
            toast("আপনি ইতিমধ্যে এই পরীক্ষায় অংশগ্রহণ করেছেন।")
            return
        }
        studentSubPageContainer?.removeAllViews()
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(bg) }
        
        // Timer Text View Reference
        var examTimerTxt: TextView? = null

        // Sticky Header with Timer
        val header = card().apply { 
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(6) }
            val tRow = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            tRow.addView(text("⏳", 18f, false, indigo, Gravity.CENTER))
            tRow.addView(text("TIME REMAINING", 12f, true, ink2, Gravity.START).apply { setPadding(dp(8), 0, 0, 0); letterSpacing = 0.05f })
            examTimerTxt = text("00:00", 22f, true, red, Gravity.END).apply { layoutParams = LinearLayout.LayoutParams(0, -2, 1f) }
            tRow.addView(examTimerTxt); addView(tRow)
            addView(text(sub.name, 16f, true, ink, Gravity.START).apply { setPadding(0, dp(10), 0, dp(2)) })
        }
        root.addView(header)

        val qContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(10), dp(20), dp(32)) }
        val userAnswers = mutableMapOf<Long, String>()
        
        viewModel.getQuestions(sub.id) { questions ->
            runOnUiThread {
                if (questions.isEmpty()) {
                    qContainer.addView(emptyState("No Questions", "এই পরীক্ষায় কোনো প্রশ্ন খুঁজে পাওয়া যায়নি", "❓"))
                } else {
                    questions.forEachIndexed { i, q ->
                        qContainer.addView(card().apply {
                            addView(text("QUESTION ${i+1}", 11f, true, indigo, Gravity.START).apply { letterSpacing = 0.05f; setPadding(0, 0, 0, dp(8)) })
                            addView(text(q.text, 16f, true, ink, Gravity.START).apply { setPadding(0, 0, 0, dp(15)) })
                            
                            val options = listOf(Triple("A", q.optionA, "A"), Triple("B", q.optionB, "B"), Triple("C", q.optionC, "C"), Triple("D", q.optionD, "D"))
                            val radioGroup = RadioGroup(this@MainActivity)
                            options.forEach { (label, optText, code) ->
                                val rb = RadioButton(this@MainActivity).apply {
                                    text = "$label. $optText"
                                    setTextColor(ink2)
                                    textSize = 14.5f
                                    setPadding(dp(10), dp(12), dp(10), dp(12))
                                    buttonTintList = ColorStateList.valueOf(indigo)
                                    layoutParams = RadioGroup.LayoutParams(-1, -2)
                                    setOnCheckedChangeListener { _, isChecked -> if (isChecked) userAnswers[q.id] = code }
                                }
                                radioGroup.addView(rb)
                            }
                            addView(radioGroup)
                        })
                    }
                    qContainer.addView(primaryButton("Submit Exam Now", "📤") { 
                        submitExam(sub, questions, userAnswers)
                    }.apply { layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) } })
                }
            }
        }

        val scrollArea = scroll(qContainer)
        root.addView(scrollArea)
        studentSubPageContainer?.addView(root)
        studentSubPageContainer?.visibility = View.VISIBLE
        studentHomeContainer?.visibility = View.GONE; studentContestsContainer?.visibility = View.GONE; studentProfileContainer?.visibility = View.GONE

        // Live Exam Timer
        val endTime = sub.startTime + (sub.durationMin * 60000)
        val timerRunnable = object : Runnable {
            override fun run() {
                val diff = endTime - System.currentTimeMillis()
                if (diff <= 0) {
                    examTimerTxt?.text = "00:00"
                    toast("সময় শেষ! পরীক্ষাটি অটোমেটিক সাবমিট হচ্ছে।")
                    viewModel.getQuestions(sub.id) { qs -> runOnUiThread { submitExam(sub, qs, userAnswers) } }
                    return
                }
                val m = (diff / 60000); val s = (diff % 60000) / 1000
                examTimerTxt?.text = String.format(Locale.US, "%02d:%02d", m, s)
                if (studentSubPageContainer?.visibility == View.VISIBLE) mainHandler.postDelayed(this, 1000)
            }
        }
        mainHandler.post(timerRunnable)
    }

    private fun submitExam(sub: SubjectItem, questions: List<Question>, answers: Map<Long, String>) {
        var correct = 0
        val finalAnswers = questions.map { q ->
            val selected = answers[q.id] ?: ""
            if (selected == q.correctOption) correct++
            q.id to selected
        }
        viewModel.saveExamResult(viewModel.currentUser.value?.id ?: 0, sub.id, questions.size, correct, finalAnswers) { _ ->
            runOnUiThread {
                toast("পরীক্ষা সফলভাবে জমা হয়েছে! কনটেস্ট শেষ হওয়ার পর রেজাল্ট দেখতে পারবেন।")
                switchStudentTab("Contests")
            }
        }
    }

    private fun createStudentProfileView(user: User): View {
        val root = screenRoot(); val hero = card().apply { background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(Color.parseColor("#6C63F5"), indigo, Color.parseColor("#3E35C9"))).apply { cornerRadius = dp(20).toFloat() }; setPadding(dp(20), dp(26), dp(20), dp(26)); gravity = Gravity.CENTER; addView(text("@${user.username.uppercase()}", 10.5f, true, Color.WHITE, Gravity.CENTER).apply { background = round(Color.parseColor("#2EFFFFFF"), dp(10).toFloat()); setPadding(dp(12), dp(5), dp(12), dp(5)) }); addView(text(user.fullName, 21f, true, Color.WHITE, Gravity.CENTER).apply { setPadding(0, dp(10), 0, 0) }); addView(text("Personal Profile Info", 12f, false, Color.parseColor("#BFFFFFFF"), Gravity.CENTER)) }; root.addView(hero)
        root.addView(card().apply { addView(LinearLayout(this@MainActivity).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 0, 0, dp(12)); addView(text("👤", 13f, false, indigo, Gravity.CENTER)); addView(text("PROFILE DETAILS", 11.5f, true, indigo, Gravity.START).apply { setPadding(dp(6), 0, 0, 0) }) }); addView(fieldLabel("Full Name")); addView(staticField(user.fullName)); addView(fieldLabel("Username")); addView(staticField(user.username))
            val ph = input("Phone Number").apply { setText(user.phone) }; val em = input("Email Address").apply { setText(user.email) }
            addView(fieldLabel("Phone Number")); addView(ph); addView(fieldLabel("Email")); addView(em)
            addView(primaryButton("Update Profile", "💾") { viewModel.updateUserProfile(user.id, ph.text.toString(), em.text.toString()) { ok -> runOnUiThread { if (ok) toast("Profile আপডেট হয়েছে") } } })
        })
        root.addView(dangerGhostButton("Logout") { logout() }.apply { layoutParams = LinearLayout.LayoutParams(-1, dp(50)).apply { topMargin = dp(8) } })
        return root
    }

    private fun showStudentNotifications() {
        if (viewModel.currentUser.value == null) return
        studentSubPageContainer?.removeAllViews()
        val root = screenRoot()
        
        val appBar = (studentContentArea?.parent as? RelativeLayout)?.getChildAt(0)
        val backBtn = appBar?.findViewWithTag<View>("back_btn")
        val subTitle = appBar?.findViewWithTag<TextView>("app_bar_sub")
        
        backBtn?.visibility = View.VISIBLE
        backBtn?.setOnClickListener { switchStudentTab(currentTab) }
        subTitle?.text = "Notifications"
        
        root.addView(text("Notifications", 24f, true, ink, Gravity.START))
        root.addView(text("আপনার সব রিমাইন্ডার এবং রেজাল্ট", 13f, false, ink2, Gravity.START).apply { setPadding(0, 0, 0, dp(18)) })

        // Use pre-fetched reminders for instant display
        val list = viewModel.studentReminders.value
        
        if (list.isEmpty()) root.addView(emptyState("No Notifications", "আপনার জন্য কোনো নতুন বার্তা নেই", "🔔"))
        else { 
            val container = card().apply { setPadding(dp(12), dp(4), dp(12), dp(4)) }
            list.forEach { rem -> container.addView(LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(13), 0, dp(13))
                addView(text("📊", 16f, false, indigo, Gravity.CENTER).apply { background = round(indigoTint, dp(10).toFloat()); layoutParams = LinearLayout.LayoutParams(dp(34), dp(34)) })
                addView(LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(11), 0, 0, 0); layoutParams = LinearLayout.LayoutParams(0, -2, 1f); addView(text(rem.subjectName, 13.5f, true, ink, Gravity.START)); addView(text(rem.message, 12f, false, ink2, Gravity.START)); addView(text("View Result →", 12f, true, indigo, Gravity.START).apply { 
                    setPadding(0, dp(7), 0, 0)
                    setOnClickListener { 
                        val userId = viewModel.currentUser.value?.id ?: 0
                        viewModel.getSubjectById(userId, rem.subjectId) { sub ->
                            runOnUiThread {
                                if (sub == null) { toast("Subject found error"); return@runOnUiThread }
                                val now = System.currentTimeMillis()
                                val endTime = sub.startTime + (sub.durationMin * 60000)
                                if (now > endTime) {
                                    viewModel.getStudentResult(userId, rem.subjectId) { res ->
                                        runOnUiThread { if (res != null) showIndividualResult(res, rem.subjectName) else toast("রেজাল্ট পাওয়া যায়নি") }
                                    }
                                } else {
                                    toast("কনটেস্ট শেষ হওয়ার পর রেজাল্ট দেখতে পারবেন।")
                                }
                            }
                        }
                    } 
                }) })
            }) }
            root.addView(container) 
        }
        studentSubPageContainer?.addView(scroll(root))
        
        // Toggle Visibility to show sub-page
        studentHomeContainer?.visibility = View.GONE
        studentContestsContainer?.visibility = View.GONE
        studentProfileContainer?.visibility = View.GONE
        studentSubPageContainer?.visibility = View.VISIBLE
    }
    // endregion

    // region UI Helpers
    private fun createBottomNav(id: Int, tabs: List<Triple<String, String, String>>, onTabSwitch: (String) -> Unit): LinearLayout = LinearLayout(this).apply {
        this.id = id; orientation = LinearLayout.HORIZONTAL; setBackgroundColor(surface); elevation = dp(8).toFloat(); layoutParams = RelativeLayout.LayoutParams(-1, dp(65)).apply { addRule(RelativeLayout.ALIGN_PARENT_BOTTOM) }
        tabs.forEach { (label, icon, key) -> addView(createNavItem(label, icon, key, onTabSwitch).apply { layoutParams = LinearLayout.LayoutParams(0, -1, 1f) }) }
    }

    private fun createNavItem(label: String, icon: String, key: String, onTabSwitch: (String) -> Unit): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setOnClickListener { onTabSwitch(key) }; tag = key
        val isActive = currentTab == key
        val pill = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER; setPadding(dp(12), dp(4), dp(12), dp(4)); background = round(if (isActive) indigoTint else Color.TRANSPARENT, dp(13).toFloat()); layoutParams = LinearLayout.LayoutParams(dp(40), dp(26)) }
        pill.addView(text(icon, 18f, isActive, if (isActive) indigo else ink3, Gravity.CENTER))
        addView(pill)
        addView(text(label, 10f, isActive, if (isActive) indigo else ink3, Gravity.CENTER).apply { if (isActive) typeface = Typeface.DEFAULT_BOLD; setPadding(0, dp(3), 0, 0) })
    }

    private fun updateBottomNavUI(parent: RelativeLayout?, navIndex: Int, key: String) {
        val nav = parent?.getChildAt(navIndex) as? LinearLayout ?: return
        for (i in 0 until nav.childCount) {
            val item = nav.getChildAt(i) as? LinearLayout ?: continue
            val isActive = (item.tag as? String) == key
            val pill = item.getChildAt(0) as LinearLayout
            val ic = pill.getChildAt(0) as TextView
            val lbl = item.getChildAt(1) as TextView
            pill.background = round(if (isActive) indigoTint else Color.TRANSPARENT, dp(13).toFloat())
            ic.setTextColor(if (isActive) indigo else ink3)
            lbl.setTextColor(if (isActive) indigo else ink3)
            lbl.typeface = if (isActive) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }
    }

    private fun createAppBar(title: String, subtitle: String, backKey: String, onBack: (String) -> Unit): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, 0, 0, dp(24))
        addView(FrameLayout(this@MainActivity).apply { background = round(surface, dp(15).toFloat(), border, dp(1)); layoutParams = LinearLayout.LayoutParams(dp(30), dp(30)); setOnClickListener { onBack(backKey) }; addView(text("‹", 20f, true, ink, Gravity.CENTER).apply { setPadding(0, 0, 0, dp(2)) }) })
        addView(LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), 0, 0, 0); addView(text(title, 18f, true, ink, Gravity.START)); addView(text(subtitle, 12f, false, ink3, Gravity.START)) })
    }

    private fun screenRoot() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(10), dp(20), dp(32)); setBackgroundColor(bg) }
    private fun card() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; background = round(surface, dp(14).toFloat(), border, dp(1)); layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) }; setPadding(dp(16), dp(16), dp(16), dp(16)); elevation = dp(1).toFloat() }
    private fun heroCard(t: String, s: String, b: String, l: Boolean) = card().apply { background = GradientDrawable(GradientDrawable.Orientation.TL_BR, if (l) intArrayOf(indigo, Color.parseColor("#3E35C9")) else intArrayOf(Color.parseColor("#6C63F5"), indigo, Color.parseColor("#3E35C9"))).apply { cornerRadius = dp(20).toFloat() }; setPadding(dp(22), dp(22), dp(22), dp(22)); val i = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL }; i.addView(text(b, 10.5f, true, Color.WHITE, Gravity.START).apply { alpha = 0.8f; letterSpacing = 0.03f }); i.addView(text(t, 23f, true, Color.WHITE, Gravity.START).apply { layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(2) }; letterSpacing = -0.04f }); i.addView(text(s, 12f, false, Color.WHITE, Gravity.START).apply { alpha = 0.7f }); addView(i) }
    private fun statCard(l: String, v: String, ic: Int, ib: Int) = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; background = round(surface, dp(14).toFloat(), border, dp(1)); setPadding(dp(15), dp(15), dp(15), dp(15)); addView(TextView(this@MainActivity).apply { text = "📁"; gravity = Gravity.CENTER; setTextColor(ic); background = round(ib, dp(9).toFloat()); layoutParams = LinearLayout.LayoutParams(dp(30), dp(30)).apply { bottomMargin = dp(10) } }); addView(text(v, 24f, true, ink, Gravity.START).apply { typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); fontFeatureSettings = "tnum" }); addView(text(l, 11.5f, false, ink2, Gravity.START)) }
    private fun quickAction(l: String, i: String, ib: Int, ic: Int, a: () -> Unit) = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; background = round(surface, dp(14).toFloat(), border, dp(1)); setPadding(dp(6), dp(13), dp(6), dp(13)); setOnClickListener { a() }; addView(TextView(this@MainActivity).apply { text = i; textSize = 14f; setTextColor(ic); gravity = Gravity.CENTER; background = round(ib, dp(9).toFloat()); layoutParams = LinearLayout.LayoutParams(dp(30), dp(30)).apply { bottomMargin = dp(7) } }); addView(text(l, 10f, true, ink2, Gravity.CENTER)) }
    private fun activityRow(m: String, t: String) = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(10), 0, dp(10)); addView(View(this@MainActivity).apply { background = round(indigoTint, dp(13).toFloat()); layoutParams = LinearLayout.LayoutParams(dp(26), dp(26)).apply { rightMargin = dp(11) } }); addView(LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL; addView(text(m, 12.5f, false, ink, Gravity.START)); addView(text(t, 10.5f, false, ink3, Gravity.START)) }) }
    private fun sectionTitle(t: String, s: String) = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; addView(text(t, 20f, true, ink, Gravity.START)); addView(text(s, 13f, false, ink2, Gravity.START).apply { setPadding(0, dp(2), 0, dp(12)) }) }
    private fun sectionHeader(t: String) = LinearLayout(this).apply { layoutParams = LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, dp(4), 0, dp(10)) }; addView(text(t, 13f, true, ink, Gravity.START)) }
    private fun input(h: String, password: Boolean = false) = EditText(this).apply { hint = h; textSize = 13.5f; setPadding(dp(13), dp(12), dp(13), dp(12)); background = round(surface2, dp(10).toFloat(), border, dp(1)); inputType = if (password) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD else InputType.TYPE_CLASS_TEXT; isSingleLine = true; layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(9) } }
    private fun primaryButton(l: String, i: String, a: () -> Unit) = MaterialButton(this).apply { text = "$i  $l"; textSize = 13.5f; typeface = Typeface.DEFAULT_BOLD; cornerRadius = dp(10); backgroundTintList = ColorStateList.valueOf(indigo); elevation = dp(6).toFloat(); setOnClickListener { a() }; layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(3) } }
    private fun outlineButton(l: String, i: String, a: () -> Unit) = MaterialButton(this).apply { text = "$i  $l"; textSize = 12f; cornerRadius = dp(10); isAllCaps = false; setTextColor(indigo); backgroundTintList = ColorStateList.valueOf(surface); strokeColor = ColorStateList.valueOf(indigoTint); strokeWidth = dp(1); setOnClickListener { a() } }
    private fun dangerGhostButton(l: String, a: () -> Unit) = MaterialButton(this).apply { text = l; textSize = 12f; cornerRadius = dp(10); setTextColor(red); backgroundTintList = ColorStateList.valueOf(redTint); setOnClickListener { a() }; elevation = 0f }
    private fun reminderInfoRow(k: String, v: String) = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(9), 0, dp(9)); addView(text(k, 12.5f, false, ink2, Gravity.START).apply { layoutParams = LinearLayout.LayoutParams(0, -2, 1f) }); addView(text(v, 12.5f, true, ink, Gravity.END)) }
    private fun chip(v: String, c: Int) = text(v, 11.5f, true, Color.WHITE, Gravity.START).apply { background = round(c, dp(8).toFloat()); setPadding(dp(12), dp(4), dp(12), dp(4)); letterSpacing = 0.04f; layoutParams = LinearLayout.LayoutParams(-2, -2).apply { bottomMargin = dp(12) } }
    private fun fieldLabel(t: String) = text(t, 11.5f, true, ink2, Gravity.START).apply { setPadding(0, 0, 0, dp(6)) }
    private fun staticField(t: String) = text(t, 13.5f, true, ink, Gravity.START).apply { background = round(surface2, dp(10).toFloat(), border, dp(1)); setPadding(dp(13), dp(12), dp(13), dp(12)); layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(14) } }
    private fun emptyState(t: String, d: String, ic: String) = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(0, dp(40), 0, dp(40)); addView(text(ic, 22f, false, ink3, Gravity.CENTER).apply { background = round(surface2, dp(26).toFloat(), border, dp(1)); layoutParams = LinearLayout.LayoutParams(dp(52), dp(52)) }); addView(text(t, 13.5f, true, ink2, Gravity.CENTER).apply { setPadding(0, dp(14), 0, dp(4)) }); addView(text(d, 12f, false, ink3, Gravity.CENTER)) }
    private fun text(v: String, s: Float, b: Boolean, c: Int, g: Int) = TextView(this).apply { text = v; textSize = s; setTextColor(c); gravity = g; if (b) typeface = Typeface.DEFAULT_BOLD }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun round(c: Int, r: Float, sc: Int? = null, sw: Int = 0) = GradientDrawable().apply { setColor(c); cornerRadius = r; if (sc != null) setStroke(sw, sc) }
    private fun swipeRefresh(child: View, onRefresh: () -> Unit) = androidx.swiperefreshlayout.widget.SwipeRefreshLayout(this).apply { addView(child); setOnRefreshListener { onRefresh(); isRefreshing = false }; setColorSchemeColors(indigo) }
    private fun scroll(child: View) = ScrollView(this).apply { isFillViewport = true; addView(child) }
    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_SHORT).show()
    // endregion
}
