# MCQ Android App, Kotlin — কম্পিউটার সেটআপ থেকে 100% রান পর্যন্ত সম্পূর্ণ গাইড

এই প্রজেক্টটি একটি **local offline**, **multi-user**, **role-based MCQ Android app**। এখানে Admin subject ও question যোগ করতে পারে, Student account তৈরি করে exam দিতে পারে, এবং exam submit করার পর score SQLite database-এ save হয়।

---

## 1) Problem Explanation

### সমস্যা কী?
একটি Android app বানাতে হবে যেখানে একই মোবাইলে একাধিক user থাকবে। User দুই ধরনের।

- **Admin:** subject যোগ করবে, MCQ question যোগ করবে।
- **Student:** register/login করবে, subject বেছে exam দেবে, result দেখবে।

### Goal
একটি beginner-friendly কিন্তু practical Android MVP তৈরি করা, যেখানে,

- Kotlin দিয়ে native Android app বানানো হয়েছে।
- SQLite দিয়ে local database রাখা হয়েছে।
- Password plaintext রাখা হয়নি, `bcrypt` hash করা হয়েছে।
- Admin ও Student আলাদা role অনুযায়ী screen পায়।
- Exam result `exam_results` table-এ save হয়।

### Input

| অংশ | Input |
|---|---|
| Login | username, password |
| Register | username, password |
| Admin subject | subject name |
| Admin question | subject, question, option A/B/C/D, correct option |
| Student exam | selected radio button answers |

### Output

| কাজ | Output |
|---|---|
| Login success | Admin panel অথবা Student exam screen |
| Login fail | ভুল username/password message |
| Question save | save confirmation |
| Exam submit | total, correct, percent |

---

## 2) Important vs Not Important

### ✅ অবশ্যই করতে হবে

- Android Studio install করতে হবে।
- Kotlin + Android Gradle project sync করতে হবে।
- SQLite schema ঠিকভাবে তৈরি করতে হবে।
- Password bcrypt hash করে save করতে হবে।
- `admin` এবং `student` role আলাদা রাখতে হবে।
- Student answer submit করলে score calculate ও database save করতে হবে।

### ❌ এখন না করলেও চলবে

- Firebase/cloud login।
- Online exam sync।
- Payment, leaderboard, analytics।
- Complex animation বা advanced Material Design screen।
- Production-level migration framework।

### Alternative approaches, কেন ব্যবহার করা হয়নি

| Approach | কেন নেওয়া হয়নি |
|---|---|
| Jetpack Compose | modern, কিন্তু একদম beginner-এর জন্য setup ও state management একটু বেশি কঠিন। |
| Room Database | production-friendly, কিন্তু MVP শেখার জন্য raw SQLiteOpenHelper বেশি সরাসরি বোঝা যায়। |
| Firebase Auth | online dependency লাগে, কিন্তু এই app local offline হওয়া দরকার। |
| Plaintext password | সহজ, কিন্তু security ভুল; তাই bcrypt ব্যবহার করা হয়েছে। |

---

## 3) A-Z Computer Setup

### Step 1: যা যা লাগবে

| Software | কেন লাগবে |
|---|---|
| Android Studio | Android project open, run, debug, APK build করার জন্য। |
| JDK 17 | Android Gradle Plugin run করার জন্য। Android Studio সাধারণত bundled JDK দেয়। |
| Android SDK Platform 35 | এই project `compileSdk = 35` ব্যবহার করে। |
| Android Emulator অথবা real Android phone | app test করার জন্য। |
| Git | project clone/version control করার জন্য। |
| Internet connection | প্রথমবার Gradle dependency download করার জন্য। |

### Step 2: Android Studio install

1. Android Studio download করে install করো।
2. First launch wizard-এ **Standard setup** বেছে নাও।
3. SDK Manager থেকে এগুলো install আছে কি না দেখো।
   - Android SDK Platform 35
   - Android SDK Build-Tools
   - Android Emulator
   - Android SDK Platform-Tools

### Step 3: Project open

```bash
cd mcq_android_app
```

তারপর Android Studio থেকে,

```text
File > Open > mcq_android_app folder select করো
```

### Step 4: Gradle sync

Android Studio project open করলে automatic sync হবে। না হলে,

```text
File > Sync Project with Gradle Files
```

### Step 5: Emulator setup

1. Android Studio থেকে **Device Manager** open করো।
2. **Create Virtual Device** চাপো।
3. Pixel device select করো।
4. API 35 image download/select করো।
5. Emulator start করো।

### Step 6: Real phone setup, optional

1. Phone-এর **Developer options** enable করো।
2. **USB debugging** on করো।
3. USB cable দিয়ে computer-এ connect করো।
4. Phone-এ allow debugging popup এলে allow করো।

---

## 4) Project Structure

```text
mcq_android_app/
├── settings.gradle.kts
├── build.gradle.kts
├── README_bn.md
└── app/
    ├── build.gradle.kts
    └── src/main/
        ├── AndroidManifest.xml
        ├── java/com/example/mcqapp/MainActivity.kt
        └── res/values/styles.xml
```

### File গুলোর কাজ

| File | কাজ |
|---|---|
| `settings.gradle.kts` | Gradle plugin ও repository setup। |
| `build.gradle.kts` | Android/Kotlin plugin version define। |
| `app/build.gradle.kts` | app id, SDK version, dependency define। |
| `AndroidManifest.xml` | app entry point `MainActivity` declare। |
| `MainActivity.kt` | database, auth, admin panel, exam, result—সব MVP logic। |
| `styles.xml` | basic Material theme। |

---

## 5) Step-by-Step Logic

### Phase A: App start

1. `MainActivity` চালু হয়।
2. `McqDatabase` initialize হয়।
3. প্রথমবার app run হলে SQLite database তৈরি হয়।
4. Default admin account তৈরি হয়।
5. Demo subject ও demo question seed হয়।
6. Login screen দেখানো হয়।

### Phase B: Login/Register

1. Student নতুন account তৈরি করতে পারে।
2. Password `bcrypt` hash হয়ে `users.password_hash` column-এ save হয়।
3. Login করলে database থেকে hash বের হয়।
4. `BCrypt.checkpw()` দিয়ে password verify হয়।
5. Role অনুযায়ী routing হয়।

### Phase C: Admin flow

1. Admin `admin/admin123` দিয়ে login করে।
2. Subject name লিখে subject যোগ করে।
3. Subject select করে question, চারটি option, correct option save করে।
4. Question `questions` table-এ save হয়।

### Phase D: Student flow

1. Student login করে।
2. Subject select করে question load করে।
3. প্রতিটি question-এর জন্য radio button থেকে answer দেয়।
4. Submit করলে correct answer count হয়।
5. `percent = correct * 100 / total` formula দিয়ে result বের হয়।
6. Result database-এ save হয়।

---

## 6) Visual Explanation

### Login থেকে Role Routing

| Username/Password | DB verify | Role | Next screen |
|---|---:|---|---|
| ভুল credential | fail | - | Login screen |
| `admin/admin123` | success | admin | Admin Panel |
| student account | success | student | Exam Screen |

### Database relationship

```text
users ───── exam_results ───── subjects ───── questions
  │              │                 │              │
  │              │                 │              └─ correct option
  │              │                 └─ subject name
  │              └─ score history
  └─ username, password_hash, role
```

### Score calculation example

ধরি Student ৩টি question submit করেছে।

| Question | Correct | Student answer | Match |
|---|---|---|---|
| Q1 | A | A | ✅ |
| Q2 | B | C | ❌ |
| Q3 | A | A | ✅ |

তাহলে,

```text
Total = 3
Correct = 2
Percent = 2 * 100 / 3 = 66.67%
```

---

## 7) Test Case Walkthrough

### Test Case 1: Admin login

| Step | কাজ | Expected result |
|---|---|---|
| 1 | username `admin` লিখো | input ready |
| 2 | password `admin123` লিখো | input ready |
| 3 | Login চাপো | Admin Panel open হবে |

### Test Case 2: Student register + login

| Step | কাজ | Expected result |
|---|---|---|
| 1 | username `rahim` লিখো | input ready |
| 2 | password `1234` লিখো | input ready |
| 3 | Register as Student চাপো | account তৈরি হবে |
| 4 | একই username/password দিয়ে Login | Student Exam screen open হবে |

### Test Case 3: Admin question add

| Step | কাজ | Expected result |
|---|---|---|
| 1 | Admin panel open করো | screen ready |
| 2 | subject name `Bangla` দিয়ে Add Subject | subject save হবে |
| 3 | subject select করো | dropdown থেকে পাওয়া যাবে |
| 4 | question + A/B/C/D + correct option দাও | input complete |
| 5 | Save Question চাপো | question save হবে |

### Test Case 4: Student exam submit

| Step | কাজ | Expected result |
|---|---|---|
| 1 | Student login করো | exam screen open |
| 2 | subject select করো | selected |
| 3 | Load Questions চাপো | question paper open |
| 4 | radio button answer দাও | answers selected |
| 5 | Submit Exam চাপো | result screen show, DB save |

---

## 8) Run Commands

### Android Studio দিয়ে run

```text
Open project > Select emulator/phone > Run ▶
```

### Terminal দিয়ে Gradle sync/build, যদি Gradle wrapper বা Gradle installed থাকে

```bash
cd mcq_android_app
./gradlew assembleDebug
```

Windows হলে,

```powershell
cd mcq_android_app
.\gradlew.bat assembleDebug
```

> Note: এই repository-তে Gradle wrapper না থাকলে Android Studio দিয়ে project open করলে wrapper generate করা যায়, অথবা system Gradle ব্যবহার করতে হবে।

### Debug APK location

Build success হলে APK সাধারণত এখানে থাকবে।

```text
app/build/outputs/apk/debug/app-debug.apk
```

---

## 9) Default Login

```text
Admin username: admin
Admin password: admin123
```

প্রথমবার app install/run করার সময় এই account auto-create হয়।

---

## 10) Build APK, AAB, Release ধারণা

### Debug APK

Development/test করার জন্য।

```bash
./gradlew assembleDebug
```

### Release APK

Production release-এর জন্য signing key দরকার।

```bash
./gradlew assembleRelease
```

### Play Store AAB

Google Play Store-এ upload করতে সাধারণত `.aab` লাগে।

```bash
./gradlew bundleRelease
```

### Reality

- Release build করতে signing config লাগবে।
- Keystore হারালে app update দিতে সমস্যা হবে।
- Play Store publishing করতে Google Play Console account লাগবে।

---

## 11) Common Error + Fix

| Error | কারণ | Fix |
|---|---|---|
| Gradle sync failed | internet/dependency issue | internet check, Sync Project again |
| SDK not found | Android SDK path missing | Android Studio SDK Manager থেকে SDK install |
| compileSdk not installed | SDK 35 missing | SDK Platform 35 install |
| Emulator slow | RAM/virtualization issue | BIOS virtualization enable, real phone ব্যবহার |
| Duplicate username | username unique | নতুন username দাও |
| Login failed | password ভুল | correct password দাও |

---

## 12) Why This Method is Better

### Brute force approach
সব logic একসাথে hardcoded করলে,

- user save করা কঠিন।
- result history রাখা কঠিন।
- admin/student আলাদা করা কঠিন।
- app বন্ধ করলে data হারানোর risk থাকে।

### Current method
এই project-এ,

- SQLite table দিয়ে permanent local storage আছে।
- bcrypt password hashing আছে।
- role-based routing আছে।
- subject/question/result data আলাদা table-এ আছে।
- future-এ Room, Compose, Firebase, timer, leaderboard add করা সহজ।

---

## 13) Intuition

এই app-টাকে coaching center ভাবো।

| App part | Coaching center analogy |
|---|---|
| `users` table | student/admin register book |
| Admin panel | teacher’s desk |
| Subject | course name |
| Question table | question bank |
| Exam screen | exam hall |
| Result table | marksheet archive |

যেমন coaching center-এ teacher question বানায়, student exam দেয়, office marksheet রাখে, ঠিক একইভাবে app-এর database সব data সাজিয়ে রাখে।

---

## 14) Reality Check, No Sugar-Coating

- এই app একটি **working MVP**। enterprise final product না।
- UI programmatic XML ছাড়া বানানো, তাই শেখার জন্য সহজ, কিন্তু বড় project হলে XML/Compose structure ভালো।
- `SQLiteOpenHelper` শেখার জন্য ভালো, কিন্তু production app-এ Room ব্যবহার করা বেশি maintainable।
- Admin password default আছে, real production app-এ first-run forced password change লাগবে।
- Database local, তাই app uninstall করলে data delete হতে পারে। backup feature নেই।
- Full security চাইলে input validation, audit log, encrypted database, migration strategy, backup/restore, test automation যোগ করতে হবে।

---

## 15) Code Map

সব MVP logic এই file-এ আছে।

```text
app/src/main/java/com/example/mcqapp/MainActivity.kt
```

- `McqDatabase`: SQLite schema, seed, CRUD, bcrypt login।
- `MainActivity`: Login, Admin Panel, Student Exam, Result UI।
- `Question`, `User`, `SubjectItem`: simple data classes।

