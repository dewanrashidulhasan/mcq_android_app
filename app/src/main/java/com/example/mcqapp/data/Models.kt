package com.example.mcqapp.data

data class User(
    val id: Long,
    val username: String,
    val fullName: String = "",
    val role: String
)

data class SubjectItem(
    val id: Long, 
    val name: String, 
    val code: String = "", 
    val teacherName: String = "",
    val isContest: Boolean = false,
    val startTime: Long = 0, // Timestamp
    val durationMin: Int = 0,
    val isRegistered: Boolean = false
) {
    override fun toString(): String = "[$code] $name"
}

data class ReminderItem(
    val id: Long,
    val subjectId: Long,
    val subjectName: String,
    val message: String,
    val type: String, // "CONTEST_START", "RESULT_PUBLISHED"
    val timestamp: String
)

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
    val resultId: Long,
    val username: String,
    val total: Int,
    val correct: Int,
    val percent: Double,
    val submittedAt: String
)

data class UserAnswerDetail(
    val questionText: String,
    val selectedOption: String,
    val correctOption: String,
    val isCorrect: Boolean
)

data class StudentRow(
    val id: Long,
    val username: String,
    val examCount: Int
)
