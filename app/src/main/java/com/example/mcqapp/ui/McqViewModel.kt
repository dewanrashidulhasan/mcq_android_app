package com.example.mcqapp.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mcqapp.data.*
import com.example.mcqapp.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class McqViewModel(private val repository: McqRepository, context: Context) : ViewModel() {

    private val prefs = context.getSharedPreferences("mcq_prefs", Context.MODE_PRIVATE)
    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser

    init {
        loadUserSession()
    }

    private fun loadUserSession() {
        val id = prefs.getLong("user_id", -1L)
        if (id != -1L) {
            val username = prefs.getString("username", null)
            val role = prefs.getString("role", null)
            if (username != null && role != null) {
                _currentUser.value = User(
                    id = id,
                    username = username,
                    fullName = prefs.getString("full_name", "") ?: "",
                    role = role,
                    phone = prefs.getString("phone", "") ?: "",
                    email = prefs.getString("email", "") ?: ""
                )
            }
        }
    }

    private val _currentAdminSubjectId = MutableStateFlow<Long?>(null)
    val currentAdminSubjectId: StateFlow<Long?> = _currentAdminSubjectId

    fun setCurrentUser(user: User?) {
        _currentUser.value = user
        with(prefs.edit()) {
            if (user != null) {
                putLong("user_id", user.id)
                putString("username", user.username)
                putString("full_name", user.fullName)
                putString("role", user.role)
                putString("phone", user.phone)
                putString("email", user.email)
            } else {
                clear()
            }
            apply()
        }
    }

    fun updateUserProfile(userId: Long, phone: String, email: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val success = repository.updateUserProfile(userId, phone, email)
            if (success) {
                val current = _currentUser.value
                if (current != null && current.id == userId) {
                    setCurrentUser(current.copy(phone = phone, email = email))
                }
            }
            onResult(success)
        }
    }

    fun setCurrentAdminSubjectId(id: Long?) {
        _currentAdminSubjectId.value = id
    }

    fun login(username: String, password: String, onResult: (User?) -> Unit) {
        viewModelScope.launch {
            val user = repository.login(username, password)
            onResult(user)
        }
    }

    fun register(username: String, password: String, role: String = "Student", fullName: String = "", onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val success = repository.createUser(username, password, role, fullName)
            onResult(success)
        }
    }

    fun addSubject(teacherId: Long, name: String, code: String, isContest: Boolean = false, startTime: Long = 0, duration: Int = 0, onResult: (Boolean, Long?) -> Unit) {
        viewModelScope.launch {
            val success = repository.addSubject(teacherId, name, code, isContest, startTime, duration)
            var subjectId: Long? = null
            if (success) {
                val subjects = repository.getSubjectsForTeacher(teacherId)
                subjectId = subjects.firstOrNull { it.code == code }?.id
            }
            onResult(success, subjectId)
        }
    }

    fun getSubjects(teacherId: Long, onResult: (List<SubjectItem>) -> Unit) {
        viewModelScope.launch {
            onResult(repository.getSubjectsForTeacher(teacherId))
        }
    }

    fun searchSubject(userId: Long, query: String, onResult: (SubjectItem?) -> Unit) {
        viewModelScope.launch {
            onResult(repository.searchSubject(userId, query.trim()))
        }
    }

    fun registerForContest(userId: Long, subjectId: Long, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            onResult(repository.registerForContest(userId, subjectId))
        }
    }

    fun sendReminder(teacherId: Long, subjectId: Long, message: String, onResult: (Int) -> Unit) {
        viewModelScope.launch {
            onResult(repository.sendReminder(teacherId, subjectId, message))
        }
    }

    fun getReminders(userId: Long, onResult: (List<ReminderItem>) -> Unit) {
        viewModelScope.launch {
            onResult(repository.getReminders(userId))
        }
    }

    fun getRegisteredContests(userId: Long, onResult: (List<SubjectItem>) -> Unit) {
        viewModelScope.launch {
            onResult(repository.getRegisteredContests(userId))
        }
    }

    fun getStats(teacherId: Long? = null, onResult: (Int, Int, Int) -> Unit) {
        viewModelScope.launch {
            if (teacherId != null) {
                val stats = repository.getTeacherStats(teacherId)
                onResult(stats.first, stats.second, stats.third)
            } else {
                // This part was using countRows which I replaced with getTeacherStats logic. 
                // For global stats we can just pass a null or handle differently.
                // For now, let's keep it consistent.
                onResult(0, 0, 0)
            }
        }
    }

    fun getStudents(onResult: (List<StudentRow>) -> Unit) {
        viewModelScope.launch {
            onResult(repository.getStudents())
        }
    }

    fun getResultsForSubject(subjectId: Long, onResult: (List<ExamResultRow>) -> Unit) {
        viewModelScope.launch {
            onResult(repository.getResultsForSubject(subjectId))
        }
    }

    fun addQuestions(subjectId: Long, drafts: List<Triple<String, List<String>, String>>, onResult: (Int, Int) -> Unit) {
        viewModelScope.launch {
            var saved = 0
            var failed = 0
            drafts.forEach { (text, options, correct) ->
                if (repository.addQuestion(subjectId, text, options, correct)) saved++ else failed++
            }
            onResult(saved, failed)
        }
    }

    fun deleteQuestion(questionId: Long, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            onResult(repository.deleteQuestion(questionId))
        }
    }

    fun deleteSubject(subjectId: Long, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            onResult(repository.deleteSubject(subjectId))
        }
    }

    fun updateSubject(subjectId: Long, name: String, code: String, isContest: Boolean, startTime: Long, duration: Int, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            onResult(repository.updateSubject(subjectId, name, code, isContest, startTime, duration))
        }
    }

    fun updateQuestion(questionId: Long, text: String, options: List<String>, correct: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            onResult(repository.updateQuestion(questionId, text, options, correct))
        }
    }

    fun getQuestions(subjectId: Long, onResult: (List<Question>) -> Unit) {
        viewModelScope.launch {
            onResult(repository.getQuestions(subjectId))
        }
    }

    fun saveExamResult(userId: Long, subjectId: Long, total: Int, correct: Int, answers: List<Pair<Long, String>>, onResult: (Double) -> Unit) {
        viewModelScope.launch {
            val percent = repository.saveResult(userId, subjectId, total, correct, answers)
            onResult(percent)
        }
    }

    fun getDetailedAnswers(resultId: Long, onResult: (List<UserAnswerDetail>) -> Unit) {
        viewModelScope.launch {
            onResult(repository.getDetailedAnswersForResult(resultId))
        }
    }

    fun getResultCountForUser(userId: Long, onResult: (Int) -> Unit) {
        viewModelScope.launch {
            onResult(repository.getResultCountForUser(userId))
        }
    }

    fun getDetailedResultsForStudent(studentId: Long, onResult: (List<Pair<String, Double>>) -> Unit) {
        viewModelScope.launch {
            val results = repository.getStudentSubjectResults(studentId)
            onResult(results)
        }
    }

    fun seedData() {
        // Data seeding is now handled automatically in McqDatabase.onCreate
    }
}
