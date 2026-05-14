# MCQ Pro - Competition-Ready Offline Quiz App

MCQ Pro is a comprehensive offline Multiple Choice Question (MCQ) application designed for students and educators. It provides a structured environment for creating question banks, conducting exams, and tracking student performance with role-based access control.

## 🚀 Features

### 🎓 Student Portal
- **Secure Onboarding**: User registration and secure login system.
- **Subject-Based Exams**: Browse available subjects and load specific question papers.
- **Interactive Quiz Interface**: 
  - Clean, modern UI for answering multiple-choice questions.
  - Support for 4-option formats (A, B, C, D).
- **Instant Grading**: 
  - Automatic score calculation upon submission.
  - Percentage-based performance analysis.
  - Motivational feedback based on score tiers (Excellent, Good, Practice, etc.).
- **History Tracking**: Exam results are persisted in the local database for future reference.

### 🛠️ Admin Dashboard (Teacher Mode)
- **Global Statistics**: At-a-glance view of total subjects, questions, and registered students.
- **Course Management**: 
  - Create and manage multiple subjects/courses.
  - Seamlessly switch between subjects to manage their respective question banks.
- **Question Bank Management**:
  - **Bulk Addition**: Add multiple questions simultaneously with options and correct answers.
  - **Precise Deletion**: Remove outdated or incorrect questions from the bank.
- **Student Performance Analytics**:
  - Comprehensive list of all registered students.
  - Detailed subject-wise mark sheets for individual student tracking.
- **Exam Reporting**: High-level overview of subject reports.

## 🔐 Security & Access
- **Role-Based Access Control (RBAC)**: Distinct interfaces and permissions for `Admin` and `Student` roles.
- **Secure Authentication**: Implementation of bcrypt password hashing to ensure user data privacy.
- **Offline Architecture**: All data is stored locally, ensuring the app works without internet connectivity.

## 🛠️ Technical Stack
- **Language**: Kotlin
- **Platform**: Android
- **Database**: SQLite (via custom helper classes)
- **UI Framework**: Android XML with Material Design components
- **Architecture**: MVVM (Model-View-ViewModel) for separation of concerns

## 📖 Getting Started
1. Clone the repository.
2. Open the project in Android Studio.
3. Build and run on an Android emulator or physical device.
4. **Default Admin Credentials**: 
   - **Username**: `admin`
   - **Password**: `admin123`

---
*Designed for speed, accuracy, and efficiency in competition preparation.*
