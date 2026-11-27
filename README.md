# StepLearn — منصة تعلّم البرمجة بالتجربة (Web & Android)

> **Interactive Micro-Learning Platform (Web + Native Android)**  
> **تصميم وتنفيذ:** المهندس أحمد عصام الدين عاشور (Eng. Ahmed Essam El-Din Ashour)

[![Web CI](https://github.com/ahmedessm1996/learn-step/actions/workflows/web-ci.yml/badge.svg)](https://github.com/ahmedessm1996/learn-step/actions/workflows/web-ci.yml)
[![Android CI](https://github.com/ahmedessm1996/learn-step/actions/workflows/android-ci.yml/badge.svg)](https://github.com/ahmedessm1996/learn-step/actions/workflows/android-ci.yml)
[![Lessons](https://img.shields.io/badge/Curriculum-614%20Lessons-2bb07f.svg)](#المسارات-التعليمية--curriculum-tracks)
[![Web Tests](https://img.shields.io/badge/Web%20Tests-970%20Passed-brightgreen.svg)](web/README.md)
[![Android](https://img.shields.io/badge/Android-Kotlin%20%2B%20Compose-3DDC84.svg)](mobile/README.md)
[![License](https://img.shields.io/badge/License-Proprietary-red.svg)](LICENSE)

---

## 🌟 نظرة عامة | Overview

**StepLearn (خطوة للتعلّم)** هي منصة تعليمية تفاعلية مبنية على مبدأ **الممارسة العملية خطوة بخطوة (Learning by Doing)** والتعلّم الدقيق (Micro-Learning) باللغة العربية.  
يضم هذا المستودع **النسختين الكاملتين للمنصة** في هيكلية موحدة (Monorepo):

| النسخة | المسار | التقنيات الأساسية | الوصف |
| :--- | :--- | :--- | :--- |
| 🌐 **نسخة الويب (Web Platform)** | [`web/`](web/README.md) | Vite 7, Vanilla JS, CodeMirror 6, WebContainers, C#/.NET WASM | بيئة تطوير تفاعلية داخل المتصفح مع معاينة فورية، محرر كود متقدم، وموجه ذكي (`smart-code-coach.js`)، و970 اختبارًا مؤتمتًا. |
| 📱 **نسخة الموبايل (Android App)** | [`mobile/`](mobile/README.md) | Kotlin 2.2, Jetpack Compose, Material 3, DataStore, Robolectric | تطبيق أندرويد أصلي يعمل دون إنترنت مع محاكيات محلية (Angular / SQL / C#)، وويدجت للشاشة الرئيسية، وتعدد ملفات المتعلمين. |

---

## 📚 المسارات التعليمية | Curriculum Tracks

تغطي المنصة **6 مسارات متكاملة** بإجمالي **614 درسًا** موزعًا على النحو التالي (متطابقة البيانات بين الويب والموبايل):

| المسار (Track) | عدد الدروس | أبرز المحاور |
| :--- | :---: | :--- |
| **HTML** | 54 | بنية الصفحة، النصوص، الروابط والوسائط، الجداول، النماذج، الدلالات الحديثة |
| **CSS** | 94 | التنسيقات، الألوان، الصناديق، Flexbox، Grid، التصميم المتجاوب |
| **JavaScript** | 119 | الأساسيات، الدوال، المصفوفات والكائنات، DOM والأحداث، البرمجة غير المتزامنة |
| **Database & SQL** | 124 | الاستعلامات، الفلترة والربط (JOIN)، التجميع، CTEs، دوال النوافذ، التصميم والفهارس |
| **Angular 22** | 106 | TypeScript، Standalone Components، Control Flow، Signals، DI/Http، ومشروع تخرج *Khotwa Cinema Pro* |
| **C# Foundations** | 117 | الأساسيات، الشروط والحلقات، الدوال والمصفوفات، OOP، المجموعات، الخوارزميات وهياكل البيانات |
| **المجموع** | **614 درسًا** | — |

---

## 🗂️ هيكل المستودع | Repository Structure

```text
learn-step/
├── .github/                  # GitHub Actions CI, Issue & PR Templates, CODEOWNERS
├── web/                      # نسخة الويب الكاملة (Vite + CodeMirror 6 + WebContainers + Tests)
│   ├── curriculum/           # مصادر وبُناة المناهج
│   ├── public/               # الأصول الثابتة ومشغّل .NET WebAssembly
│   ├── runtime/              # محركات التشغيل والتوثيق
│   ├── tests/                # حزمة الاختبارات الشاملة (970 اختبارًا)
│   ├── index.html
│   ├── package.json
│   └── vite.config.js
├── mobile/                   # نسخة تطبيق الأندرويد الأصلي (Kotlin + Jetpack Compose)
│   ├── app/
│   │   └── src/
│   │       ├── main/java/com/example/   # الواجهات والمحاكيات والويدجت وViewModel
│   │       ├── main/assets/             # ملفات JSON للمناهج الـ 6
│   │       └── test/                    # اختبارات Robolectric
│   ├── gradle/libs.versions.toml
│   ├── build.gradle.kts
│   └── settings.gradle.kts
├── README.md
├── LICENSE
├── CONTRIBUTING.md
├── SECURITY.md
├── CHANGELOG.md
├── .gitignore
├── .editorconfig
└── .gitattributes
```

---

## 🚀 التشغيل السريع | Quick Start

### 1. استنساخ المستودع
```bash
git clone https://github.com/ahmedessm1996/learn-step.git
cd learn-step
```

### 2. تشغيل واختبار نسخة الويب (`web/`)
المتطلبات: **Node.js 20+** و **npm 10+**.

```bash
cd web
npm ci
npm test       # التحقق من صحة المناهج وتشغيل جميع الاختبارات (970 اختبارًا)
npm run dev    # تشغيل خادم التطوير المحلي عبر Vite
npm run build  # بناء نسخة الإنتاج في web/dist/
```

### 3. فتح وبناء نسخة الأندرويد (`mobile/`)
المتطلبات: **Android Studio** (متوافق مع **AGP 9.1.1** و **Kotlin 2.2.21**) و **JDK 17**.

1. افتح مجلد `mobile/` مباشرة من داخل **Android Studio** (`File > Open > learn-step/mobile`).
2. انتظر مزامنة Gradle (يستخدم الكتالوج [`gradle/libs.versions.toml`](mobile/gradle/libs.versions.toml) مع `compileSdk = 36` و `minSdk = 26`).
3. شغّل التطبيق على المحاكي أو الهاتف، أو نفّذ اختبارات الوحدة (`LearnStepRedesignRobolectricTest`).

---

## 🌐 النشر والاستضافة (Web Deployment)

نسخة الويب داخل `web/` جاهزة للنشر عبر **Netlify** أو **Vercel** أو **Cloudflare Workers**:
- **Base Directory**: `web`
- **Build Command**: `npm run build`
- **Publish Directory**: `dist` (أي `web/dist` عند النشر من الجذر)
- تتضمن الإعدادات ترويسات العزل الأمني المطلوبة لـ WebContainers (`COEP: credentialless`, `COOP: same-origin`) في `web/netlify.toml` و`web/vercel.json` و`web/_headers`.

---

## 👤 المؤلف وحقوق الملكية | Author & Copyright

**تصميم وتنفيذ وإشراف:**  
**المهندس أحمد عصام الدين عاشور (Eng. Ahmed Essam El-Din Ashour)**  
- GitHub: [@ahmedessm1996](https://github.com/ahmedessm1996)  
- Repository: [ahmedessm1996/learn-step](https://github.com/ahmedessm1996/learn-step)

> راجع ملف [`LICENSE`](LICENSE) لتفاصيل حقوق الملكية الفكرية، وملف [`SECURITY.md`](SECURITY.md) لسياسة الأمان.
