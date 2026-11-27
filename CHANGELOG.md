# Changelog

All notable changes to **StepLearn** will be documented in this file.

## [1.0.0] - 2026-11-27

### Added
- **Unified Monorepo Structure** separating `web/` and `mobile/` platforms with shared repository standards.
- **Web Platform (`web/`)**:
  - Interactive micro-learning platform with **614 lessons** across 6 tracks: HTML (54), CSS (94), JavaScript (119), Database/SQL (124), Angular 22 (106), and C# (117).
  - CodeMirror 6 editor, WebContainers support, Smart Code Coach (`smart-code-coach.js`), and experimental .NET WebAssembly runner.
  - 970 automated tests (`npm test`) and Vite production build (`npm run build`).
  - Deployment configurations for Netlify, Vercel, and Cloudflare Workers.
- **Android Mobile App (`mobile/`)**:
  - Native Android app built with Kotlin `2.2.21`, Jetpack Compose (BOM `2025.05.00`), and Material 3.
  - Bundled 6-track curriculum assets (614 lessons) with local execution & hint engines (`AngularSimulator`, `CSharpRuntime`, `SqlPlaygroundEngine`, `SmartHintEngine`).
  - Home screen widget (`LearnStepWidgetProvider`), learner profiles (`DataStore`), ambient sound engine, and Robolectric unit tests.
- **Repository & CI Infrastructure**:
  - Bilingual documentation (`README.md`, `web/README.md`, `mobile/README.md`), `LICENSE`, `CONTRIBUTING.md`, `SECURITY.md`.
  - GitHub Actions CI workflows for Web and Android, issue templates, PR template, and `.editorconfig`/`.gitattributes`.
