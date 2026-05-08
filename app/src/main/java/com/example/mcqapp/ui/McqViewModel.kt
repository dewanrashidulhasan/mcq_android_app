package com.example.mcqapp.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mcqapp.data.McqRepository
import com.example.mcqapp.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class McqViewModel(private val repository: McqRepository) : ViewModel() {

    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser

    private val _currentAdminSubjectId = MutableStateFlow<Long?>(null)
    val currentAdminSubjectId: StateFlow<Long?> = _currentAdminSubjectId

    fun setCurrentUser(user: User?) {
        _currentUser.value = user
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

    fun register(username: String, password: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val success = repository.createUser(username, password)
            onResult(success)
        }
    }

    fun addSubject(name: String, onResult: (Boolean, Long?) -> Unit) {
        viewModelScope.launch {
            val success = repository.addSubject(name)
            var subjectId: Long? = null
            if (success) {
                val subjects = repository.getSubjects()
                subjectId = subjects.firstOrNull { it.name == name }?.id
            }
            onResult(success, subjectId)
        }
    }

    fun getSubjects(onResult: (List<SubjectItem>) -> Unit) {
        viewModelScope.launch {
            onResult(repository.getSubjects())
        }
    }

    fun getStats(onResult: (Int, Int, Int) -> Unit) {
        viewModelScope.launch {
            onResult(
                repository.getSubjectCount(),
                repository.getQuestionCount(),
                repository.getStudentCount()
            )
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

    fun getQuestions(subjectId: Long, onResult: (List<Question>) -> Unit) {
        viewModelScope.launch {
            onResult(repository.getQuestions(subjectId))
        }
    }

    fun saveExamResult(userId: Long, subjectId: Long, total: Int, correct: Int, onResult: (Double) -> Unit) {
        viewModelScope.launch {
            val percent = repository.saveResult(userId, subjectId, total, correct)
            onResult(percent)
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
        viewModelScope.launch {
            repository.seedAdminData()
        }
    }
}
