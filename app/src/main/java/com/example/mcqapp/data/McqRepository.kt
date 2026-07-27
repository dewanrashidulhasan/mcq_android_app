package com.example.mcqapp.data

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await
import org.mindrot.jbcrypt.BCrypt
import java.util.Locale

class McqRepository(private val dbHelper: McqDatabase) { // dbHelper remains for now to avoid large refactor errors, but we use Firebase

    private val database = FirebaseDatabase.getInstance().reference
    private val teacherNameCache = mutableMapOf<Long, String>()
    
    // Hyper-Optimization Cache
    private var cachedRegisteredContests: List<SubjectItem>? = null
    private var lastContestFetchTime: Long = 0
    private val CACHE_EXPIRY = 60000L // 1 minute cache

    suspend fun createUser(
        username: String,
        password: String,
        role: String,
        fullName: String = ""
    ): Int = withContext(Dispatchers.IO) { // Changed return type to Int for status codes
        try {
            if (username.isBlank() || password.length < 4) return@withContext 2 // Invalid input
            
            val userQuery = database.child("users").orderByChild("username").equalTo(username.trim()).get().await()
            if (userQuery.exists()) return@withContext 1 // Already exists

            val userId = database.child("users").push().key?.hashCode()?.toLong()?.let { if (it < 0) -it else it } ?: System.currentTimeMillis()
            val userMap = mapOf(
                "id" to userId,
                "full_name" to fullName.trim(),
                "username" to username.trim(),
                "password_hash" to BCrypt.hashpw(password, BCrypt.gensalt(10)),
                "role" to role,
                "phone" to "",
                "email" to ""
            )
            
            database.child("users").child(userId.toString()).setValue(userMap).await()
            0 // Success
        } catch (e: Exception) {
            e.printStackTrace()
            -1 // Firebase/Network error
        }
    }

    suspend fun login(username: String, password: String): Pair<User?, Int> = withContext(Dispatchers.IO) {
        try {
            val snapshot = database.child("users").orderByChild("username").equalTo(username.trim()).get().await()
            if (!snapshot.exists() || snapshot.childrenCount == 0L) return@withContext null to 1 // User not found
            
            val userSnap = snapshot.children.firstOrNull() ?: return@withContext null to 1
            
            val hash = userSnap.child("password_hash").value as? String ?: return@withContext null to 2 // Invalid data
            if (!BCrypt.checkpw(password, hash)) return@withContext null to 3 // Wrong password

            val user = User(
                id = (userSnap.child("id").value as? Number)?.toLong() ?: 0L,
                username = userSnap.child("username").value as? String ?: "",
                fullName = userSnap.child("full_name").value as? String ?: "",
                role = userSnap.child("role").value as? String ?: "",
                phone = userSnap.child("phone").value as? String ?: "",
                email = userSnap.child("email").value as? String ?: ""
            )
            user to 0 // Success
        } catch (e: Exception) {
            e.printStackTrace()
            null to -1 // Database error
        }
    }

    suspend fun updateUserProfile(userId: Long, phone: String, email: String): Boolean = withContext(Dispatchers.IO) {
        val updates = mapOf(
            "phone" to phone.trim(),
            "email" to email.trim()
        )
        runCatching {
            database.child("users").child(userId.toString()).updateChildren(updates).await()
            true
        }.getOrDefault(false)
    }

    suspend fun addSubject(teacherId: Long, name: String, code: String, isContest: Boolean = false, startTime: Long = 0, duration: Int = 0): Boolean = withContext(Dispatchers.IO) {
        if (name.isBlank() || code.isBlank()) return@withContext false
        
        val codeQuery = database.child("subjects").orderByChild("subject_code").equalTo(code.trim().uppercase()).get().await()
        if (codeQuery.exists()) return@withContext false

        val subjectId = database.child("subjects").push().key?.hashCode()?.toLong()?.let { if (it < 0) -it else it } ?: System.currentTimeMillis()
        val subjectMap = mapOf(
            "id" to subjectId,
            "teacher_id" to teacherId,
            "name" to name.trim(),
            "subject_code" to code.trim().uppercase(),
            "is_contest" to isContest,
            "start_time" to startTime,
            "duration_min" to duration
        )
        
        runCatching {
            database.child("subjects").child(subjectId.toString()).setValue(subjectMap).await()
            true
        }.getOrDefault(false)
    }

    suspend fun updateSubject(subjectId: Long, name: String, code: String, isContest: Boolean, startTime: Long, duration: Int): Boolean = withContext(Dispatchers.IO) {
        val updates = mapOf(
            "name" to name.trim(),
            "subject_code" to code.trim().uppercase(),
            "is_contest" to isContest,
            "start_time" to startTime,
            "duration_min" to duration
        )
        runCatching {
            database.child("subjects").child(subjectId.toString()).updateChildren(updates).await()
            true
        }.getOrDefault(false)
    }

    suspend fun getSubjectsForTeacher(teacherId: Long): List<SubjectItem> = withContext(Dispatchers.IO) {
        try {
            val teacherName = teacherNameCache[teacherId] ?: database.child("users").child(teacherId.toString()).child("full_name").get().await().value as? String ?: ""
            if (teacherName.isNotEmpty()) teacherNameCache[teacherId] = teacherName

            val snapshot = database.child("subjects").orderByChild("teacher_id").equalTo(teacherId.toDouble()).get().await()
            snapshot.children.mapNotNull { snap ->
                val id = (snap.child("id").value as? Number)?.toLong() ?: return@mapNotNull null
                SubjectItem(
                    id = id,
                    name = snap.child("name").value as? String ?: "Untitled",
                    code = snap.child("subject_code").value as? String ?: "",
                    teacherName = teacherName,
                    isContest = snap.child("is_contest").value as? Boolean ?: false,
                    startTime = (snap.child("start_time").value as? Number)?.toLong() ?: 0L,
                    durationMin = (snap.child("duration_min").value as? Number)?.toInt() ?: 0,
                    isRegistered = false,
                    hasSubmitted = false
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun getSubjectById(userId: Long, subjectId: Long): SubjectItem? = withContext(Dispatchers.IO) {
        val subSnap = database.child("subjects").child(subjectId.toString()).get().await()
        if (!subSnap.exists()) return@withContext null
        
        val teacherId = (subSnap.child("teacher_id").value as? Number)?.toLong() ?: return@withContext null
        val teacherName = database.child("users").child(teacherId.toString()).child("full_name").get().await().value as? String ?: ""
        
        val isReg = database.child("registrations").child(userId.toString()).child(subjectId.toString()).get().await().exists()
        val hasSub = database.child("results").child(userId.toString()).child(subjectId.toString()).get().await().exists()

        SubjectItem(
            id = subjectId,
            name = subSnap.child("name").value as? String ?: "Untitled",
            code = subSnap.child("subject_code").value as? String ?: "",
            teacherName = teacherName,
            isContest = subSnap.child("is_contest").value as? Boolean ?: false,
            startTime = (subSnap.child("start_time").value as? Number)?.toLong() ?: 0L,
            durationMin = (subSnap.child("duration_min").value as? Number)?.toInt() ?: 0,
            isRegistered = isReg,
            hasSubmitted = hasSub
        )
    }

    suspend fun searchSubject(userId: Long, subjectCode: String): SubjectItem? = coroutineScope {
        try {
            val code = subjectCode.trim().uppercase()
            val snapshot = database.child("subjects").orderByChild("subject_code").equalTo(code).get().await()
            val subSnap = snapshot.children.firstOrNull() ?: return@coroutineScope null
            
            val subjectIdStr = subSnap.key ?: return@coroutineScope null
            val teacherId = (subSnap.child("teacher_id").value as? Number)?.toLong() ?: 0L

            // Fetch supplementary info in parallel
            val teacherNameDef = async(Dispatchers.IO) {
                teacherNameCache[teacherId] ?: database.child("users").child(teacherId.toString()).child("full_name").get().await().value as? String ?: ""
            }
            val isRegDef = async(Dispatchers.IO) {
                database.child("registrations").child(userId.toString()).child(subjectIdStr).get().await().exists()
            }
            val hasSubDef = async(Dispatchers.IO) {
                database.child("results").child(userId.toString()).child(subjectIdStr).get().await().exists()
            }

            val teacherName = teacherNameDef.await()
            if (teacherName.isNotEmpty()) teacherNameCache[teacherId] = teacherName

            SubjectItem(
                id = subjectIdStr.toLongOrNull() ?: 0L,
                name = subSnap.child("name").value as? String ?: "Untitled",
                code = subSnap.child("subject_code").value as? String ?: "",
                teacherName = teacherName,
                isContest = subSnap.child("is_contest").value as? Boolean ?: false,
                startTime = (subSnap.child("start_time").value as? Number)?.toLong() ?: 0L,
                durationMin = (subSnap.child("duration_min").value as? Number)?.toInt() ?: 0,
                isRegistered = isRegDef.await(),
                hasSubmitted = hasSubDef.await()
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun registerForContest(userId: Long, subjectId: Long): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            database.child("registrations").child(userId.toString()).child(subjectId.toString()).setValue(true).await()
            true
        }.getOrDefault(false)
    }

    suspend fun sendReminder(teacherId: Long, subjectId: Long, message: String): Int = withContext(Dispatchers.IO) {
        val regs = database.child("registrations").get().await()
        var count = 0
        regs.children.forEach { userRegs ->
            if (userRegs.hasChild(subjectId.toString())) {
                val userId = userRegs.key ?: return@forEach
                val reminderId = database.child("reminders").child(userId).push().key ?: return@forEach
                val reminderMap = mapOf(
                    "id" to reminderId.hashCode().toLong(),
                    "subject_id" to subjectId,
                    "message" to message,
                    "type" to "REMINDER",
                    "created_at" to System.currentTimeMillis().toString()
                )
                database.child("reminders").child(userId).child(reminderId).setValue(reminderMap).await()
                count++
            }
        }
        count
    }

    suspend fun getReminders(userId: Long): List<ReminderItem> = withContext(Dispatchers.IO) {
        try {
            val snapshot = database.child("reminders").child(userId.toString()).get().await()
            if (!snapshot.exists()) return@withContext emptyList()

            // Fetch all subjects once to avoid N+1 requests for subject names
            val subjectsSnapshot = database.child("subjects").get().await()
            val subjectNames = subjectsSnapshot.children.associate { 
                it.key to (it.child("name").value as? String ?: "Unknown")
            }

            snapshot.children.mapNotNull { snap ->
                val sid = (snap.child("subject_id").value as? Number)?.toLong() ?: return@mapNotNull null
                val sname = subjectNames[sid.toString()] ?: "Unknown"
                val id = (snap.child("id").value as? Number)?.toLong() ?: return@mapNotNull null
                ReminderItem(
                    id = id,
                    subjectId = sid,
                    subjectName = sname,
                    message = snap.child("message").value as? String ?: "",
                    type = snap.child("type").value as? String ?: "REMINDER",
                    timestamp = snap.child("created_at").value as? String ?: ""
                )
            }.reversed()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun getRegisteredContests(userId: Long, forceRefresh: Boolean = false): List<SubjectItem> = coroutineScope {
        try {
            // Instant memory return if not force refresh
            if (!forceRefresh && cachedRegisteredContests != null && (System.currentTimeMillis() - lastContestFetchTime < CACHE_EXPIRY)) {
                return@coroutineScope cachedRegisteredContests!!
            }

            val regSnapshot = database.child("registrations").child(userId.toString()).get().await()
            if (!regSnapshot.exists()) {
                cachedRegisteredContests = emptyList()
                return@coroutineScope emptyList<SubjectItem>()
            }
            
            val regIds = regSnapshot.children.mapNotNull { it.key }
            if (regIds.isEmpty()) {
                cachedRegisteredContests = emptyList()
                return@coroutineScope emptyList<SubjectItem>()
            }

            val resultsSnapshot = database.child("results").child(userId.toString()).get().await()

            // Ultra-Fast: Parallel single node fetching
            val deferredSubjects = regIds.map { idStr ->
                async(Dispatchers.IO) {
                    try {
                        val subId = idStr.toLongOrNull() ?: return@async null
                        val subSnap = database.child("subjects").child(idStr).get().await()
                        if (!subSnap.exists()) return@async null
                        
                        val teacherId = (subSnap.child("teacher_id").value as? Number)?.toLong() ?: 0L
                        val teacherName = teacherNameCache[teacherId] ?: database.child("users").child(teacherId.toString()).child("full_name").get().await().value as? String ?: ""
                        if (teacherName.isNotEmpty()) teacherNameCache[teacherId] = teacherName
                        
                        val hasSub = resultsSnapshot.hasChild(idStr)
                        
                        SubjectItem(
                            id = subId,
                            name = subSnap.child("name").value as? String ?: "Untitled",
                            code = subSnap.child("subject_code").value as? String ?: "",
                            teacherName = teacherName,
                            isContest = subSnap.child("is_contest").value as? Boolean ?: false,
                            startTime = (subSnap.child("start_time").value as? Number)?.toLong() ?: 0L,
                            durationMin = (subSnap.child("duration_min").value as? Number)?.toInt() ?: 0,
                            isRegistered = true,
                            hasSubmitted = hasSub
                        )
                    } catch (e: Exception) { null }
                }
            }
            
            val results = deferredSubjects.awaitAll().filterNotNull()
            cachedRegisteredContests = results
            lastContestFetchTime = System.currentTimeMillis()
            results
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList<SubjectItem>()
        }
    }

    fun listenToStudentContests(userId: Long, onUpdate: (List<SubjectItem>) -> Unit) {
        val regRef = database.child("registrations").child(userId.toString())
        regRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                GlobalScope.launch(Dispatchers.IO) {
                    onUpdate(getRegisteredContests(userId, true))
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    suspend fun getTeacherStats(teacherId: Long): List<Int> = withContext(Dispatchers.IO) {
        try {
            val subjects = getSubjectsForTeacher(teacherId)
            val sCount = subjects.size
            val cCount = subjects.count { it.isContest }
            
            // Batch fetch all questions once
            val allQuestionsSnapshot = database.child("questions").get().await()
            var qCount = 0
            subjects.forEach { s ->
                qCount += allQuestionsSnapshot.child(s.id.toString()).childrenCount.toInt()
            }
            
            // Batch fetch all results once
            val resultsSnapshot = database.child("results").get().await()
            val studentIds = mutableSetOf<String>()
            resultsSnapshot.children.forEach { userResults ->
                val userId = userResults.key ?: return@forEach
                subjects.forEach { s ->
                    if (userResults.hasChild(s.id.toString())) {
                        studentIds.add(userId)
                    }
                }
            }
            
            listOf(sCount, qCount, studentIds.size, cCount)
        } catch (e: Exception) {
            e.printStackTrace()
            listOf(0, 0, 0, 0)
        }
    }

    suspend fun getResultsForSubject(subjectId: Long): List<ExamResultRow> = withContext(Dispatchers.IO) {
        try {
            val resultsSnapshot = database.child("results").get().await()
            val allUsersSnapshot = database.child("users").get().await()
            
            val usernames = allUsersSnapshot.children.associate { 
                it.key to (it.child("username").value as? String ?: "Unknown")
            }

            val results = mutableListOf<ExamResultRow>()
            resultsSnapshot.children.forEach { userResults ->
                val resSnap = userResults.child(subjectId.toString())
                if (resSnap.exists()) {
                    val userId = userResults.key ?: return@forEach
                    results += ExamResultRow(
                        resultId = (resSnap.child("id").value as? Number)?.toLong() ?: 0L,
                        username = usernames[userId] ?: "Unknown",
                        total = (resSnap.child("total").value as? Number)?.toInt() ?: 0,
                        correct = (resSnap.child("correct").value as? Number)?.toInt() ?: 0,
                        percent = (resSnap.child("percent").value as? Number)?.toDouble() ?: 0.0,
                        submittedAt = resSnap.child("submitted_at").value as? String ?: ""
                    )
                }
            }
            results.sortedByDescending { it.submittedAt }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun getStudentResult(userId: Long, subjectId: Long): ExamResultRow? = withContext(Dispatchers.IO) {
        try {
            val snap = database.child("results").child(userId.toString()).child(subjectId.toString()).get().await()
            if (!snap.exists()) return@withContext null
            ExamResultRow(
                resultId = (snap.child("id").value as? Number)?.toLong() ?: 0L,
                username = "", // Not needed for individual view
                total = (snap.child("total").value as? Number)?.toInt() ?: 0,
                correct = (snap.child("correct").value as? Number)?.toInt() ?: 0,
                percent = (snap.child("percent").value as? Number)?.toDouble() ?: 0.0,
                submittedAt = snap.child("submitted_at").value as? String ?: ""
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun addQuestion(subjectId: Long, text: String, options: List<String>, correct: String): Boolean = withContext(Dispatchers.IO) {
        val questionId = database.child("questions").child(subjectId.toString()).push().key?.hashCode()?.toLong()?.let { if (it < 0) -it else it } ?: System.currentTimeMillis()
        val questionMap = mapOf(
            "id" to questionId,
            "subject_id" to subjectId,
            "text" to text.trim(),
            "optionA" to options[0],
            "optionB" to options[1],
            "optionC" to options[2],
            "optionD" to options[3],
            "correctOption" to correct.uppercase()
        )
        runCatching {
            database.child("questions").child(subjectId.toString()).child(questionId.toString()).setValue(questionMap).await()
            true
        }.getOrDefault(false)
    }

    suspend fun deleteQuestion(subjectId: Long, questionId: Long): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            database.child("questions").child(subjectId.toString()).child(questionId.toString()).removeValue().await()
            true
        }.getOrDefault(false)
    }

    suspend fun getQuestions(subjectId: Long): List<Question> = withContext(Dispatchers.IO) {
        val snapshot = database.child("questions").child(subjectId.toString()).get().await()
        snapshot.children.mapNotNull { snap ->
            val id = (snap.child("id").value as? Number)?.toLong() ?: return@mapNotNull null
            Question(
                id = id,
                text = snap.child("text").value as? String ?: "",
                optionA = snap.child("optionA").value as? String ?: "",
                optionB = snap.child("optionB").value as? String ?: "",
                optionC = snap.child("optionC").value as? String ?: "",
                optionD = snap.child("optionD").value as? String ?: "",
                correctOption = snap.child("correctOption").value as? String ?: "A"
            )
        }
    }

    suspend fun saveResult(userId: Long, subjectId: Long, total: Int, correct: Int, answers: List<Pair<Long, String>>): Double = withContext(Dispatchers.IO) {
        val percent = if (total == 0) 0.0 else (correct * 100.0) / total
        val resultId = System.currentTimeMillis()
        val resultMap = mapOf(
            "id" to resultId,
            "total" to total,
            "correct" to correct,
            "percent" to percent,
            "submitted_at" to System.currentTimeMillis().toString()
        )
        
        database.child("results").child(userId.toString()).child(subjectId.toString()).setValue(resultMap).await()
        
        // Save detailed answers
        val answersMap = mutableMapOf<String, String>()
        answers.forEach { (qid, ans) -> answersMap[qid.toString()] = ans }
        database.child("user_answers").child(userId.toString()).child(subjectId.toString()).setValue(answersMap).await()
        
        percent
    }

    suspend fun addQuestions(subjectId: Long, drafts: List<Triple<String, List<String>, String>>): Pair<Int, Boolean> = withContext(Dispatchers.IO) {
        var count = 0
        drafts.forEach { (text, opts, cor) ->
            if (addQuestion(subjectId, text, opts, cor)) count++
        }
        count to (count > 0)
    }

    suspend fun deleteSubject(subjectId: Long): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            database.child("subjects").child(subjectId.toString()).removeValue().await()
            database.child("questions").child(subjectId.toString()).removeValue().await()
            
            // Cleanup registrations
            val regs = database.child("registrations").get().await()
            regs.children.forEach { userRegs ->
                if (userRegs.hasChild(subjectId.toString())) {
                    userRegs.child(subjectId.toString()).ref.removeValue().await()
                }
            }
            
            // Cleanup results
            val results = database.child("results").get().await()
            results.children.forEach { userResults ->
                if (userResults.hasChild(subjectId.toString())) {
                    userResults.child(subjectId.toString()).ref.removeValue().await()
                }
            }

            true
        }.getOrDefault(false)
    }
}
