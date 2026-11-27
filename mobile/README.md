# StepLearn Android App (`mobile/`)

> **Native Android Micro-Learning Client (Kotlin + Jetpack Compose + Material 3)**  
> جزء من مستودع [StepLearn Monorepo](../README.md)

[![Kotlin](https://img.shields.io/badge/Kotlin-2.2.21-7F52FF.svg)](https://kotlinlang.org/)
[![AGP](https://img.shields.io/badge/AGP-9.1.1-3DDC84.svg)](https://developer.android.com/)
[![Compose](https://img.shields.io/badge/Jetpack%20Compose-BOM%202025.05.00-4285F4.svg)](https://developer.android.com/jetpack/compose)
[![MinSDK](https://img.shields.io/badge/minSdk-26-informational.svg)]()
[![TargetSDK](https://img.shields.io/badge/targetSdk-36-informational.svg)]()

---

## نظرة عامة

نسخة أندرويد أصلية بالكامل من منصة **StepLearn** مكتوبة بلغة **Kotlin** وواجهة **Jetpack Compose (Material 3)**، وتضم نفس المسارات الستة (**614 درسًا**) مدمجة محليًا داخل `app/src/main/assets/` لتعمل بسلاسة حتى دون اتصال بالإنترنت.

---

## المعمارية والمكونات الرئيسية

- **تطبيق Android**: المعرّف `com.aistudio.steplearn.khtwaq` ([`app/build.gradle.kts`](app/build.gradle.kts))
- **الشاشات (`ui/screens/`)**:
  - `HomeScreen` — الرئيسية والتحديات اليومية واستكمال التعلم
  - `CoursesScreen` — استعراض المسارات الستة والدروس
  - `LessonIntroScreen` — التمهيد الفضولي وتوقّع المخرجات (`CuriosityFraming`)
  - `GuidedStepScreen` — التنفيذ الخطوي التفاعلي مع محرر الكود المخصص (`MobileCodeEditor`)
  - `BuildsScreen` — التجارب الحرة والمشاريع المحفوظة
  - `LessonCompletionScreen` — ملخص الإتمام والتقدّم
- **المحاكيات المحلية (`engine/`)**:
  - `AngularSimulator.kt` — محاكاة تفاعلية لمكونات ومسارات Angular
  - `CSharpRuntime.kt` و`CSharpCollections.kt` — تتبّع وتنفيذ خطوي لأكواد C#
  - `SqlPlaygroundEngine.kt` — تنفيذ استعلامات SQL محليًا
  - `SmartHintEngine.kt` — تلميحات ذكية متدرجة أثناء كتابة الكود
  - `PreviewHtmlBuilder.kt` — بناء معاينة HTML/CSS/JS
- **التخزين والوسائط والويدجت**:
  - `LearnerDatabase.kt` (`DataStore Preferences`) — حفظ تقدّم وتعدد ملفات المتعلمين
  - `AmbientSoundEngine.kt` — مؤثرات صوتية هادئة أثناء التعلّم
  - `LearnStepWidgetProvider.kt` — ويدجت للشاشة الرئيسية يفتح التجربة مباشرة

---

## البناء والتشغيل

### المتطلبات
- **Android Studio** (إصدار حديث يدعم **AGP 9.1.1** و**Gradle 9.3+**)
- **JDK 17** (`JavaVersion.VERSION_17`)
- **Android SDK 36** (`compileSdk = 36`, `targetSdk = 36`, `minSdk = 26`)

### الخطوات
1. افتح مجلد `mobile/` من Android Studio.
2. شغّل Gradle Sync لتنزيل الاعتماديات المعرّفة في [`gradle/libs.versions.toml`](gradle/libs.versions.toml).
3. لتشغيل اختبارات الوحدة (`Robolectric` في [`LearnStepRedesignRobolectricTest.kt`](app/src/test/java/com/example/LearnStepRedesignRobolectricTest.kt)) عبر Android Studio أو عبر Gradle:
   ```bash
   gradle testDebugUnitTest
   ```
   > **ملاحظة:** لا يتضمن المجلد ملفات `gradlew` / `gradle-wrapper.jar` حاليًا؛ يمكنك توليدها من Android Studio أو تشغيل `gradle wrapper` محليًا إذا رغبت بإضافتها لاحقًا.
