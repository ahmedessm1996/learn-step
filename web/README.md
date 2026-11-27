# StepLearn Web Platform (`web/`)

> **Interactive Browser IDE, Micro-Learning Engine, WebContainers & C# WASM Runner**  
> جزء من مستودع [StepLearn Monorepo](../README.md)

[![Node.js](https://img.shields.io/badge/Node.js-20%2B-339933.svg)](https://nodejs.org/)
[![Vite](https://img.shields.io/badge/Vite-7-646CFF.svg)](https://vitejs.dev/)
[![CodeMirror](https://img.shields.io/badge/CodeMirror-6-blue.svg)](https://codemirror.net/)
[![Web Tests](https://img.shields.io/badge/tests-970%20passed-brightgreen.svg)](#الاختبارات-وضمان-الجودة)

---

## نظرة عامة

توفّر نسخة الويب بيئة تعلّم برمجي متكاملة داخل المتصفح عبر **614 درسًا** في **6 مسارات** (HTML, CSS, JavaScript, Database/SQL, Angular 22, C#)، مع محرر **CodeMirror 6**، ومعاينة حية، ومدرّب ذكي للكود (`smart-code-coach.js`)، ودعم **WebContainers** لتشغيل Angular، إضافة إلى محاكي C# تعليمي مع خيار تجريبي لمترجم .NET عبر WebAssembly.

---

## التشغيل والتطوير المحلي

```bash
# من جذر المستودع
cd web

# تثبيت الاعتماديات
npm ci

# تشغيل خادم التطوير المحلي
npm run dev

# بناء نسخة الإنتاج
npm run build

# معاينة نسخة الإنتاج محليًا
npm run preview
```

---

## الاختبارات وضمان الجودة

```bash
# تشغيل مدقق ملفات المناهج + جميع اختبارات Node (970 اختبارًا)
npm test

# التحقق من صحة ملفات JSON للمناهج فقط
npm run test:json

# اختبارات مسار C#
npm run test:csharp
```

---

## النشر والاستضافة (Netlify / Vercel / Cloudflare)

عند ربط مستودع `ahmedessm1996/learn-step` بمنصات الاستضافة:
- **Base Directory**: `web`
- **Build Command**: `npm run build`
- **Publish Directory**: `dist`
- **Node Version**: `20` (محدد في `.nvmrc` و`netlify.toml`)

الترويسات الأمنية الإلزامية لـ WebContainers (`Cross-Origin-Embedder-Policy: credentialless` و`Cross-Origin-Opener-Policy: same-origin`) مهيأة مسبقًا في:
- [`netlify.toml`](netlify.toml)
- [`vercel.json`](vercel.json)
- [`_headers`](_headers)
- [`vite.config.js`](vite.config.js)
