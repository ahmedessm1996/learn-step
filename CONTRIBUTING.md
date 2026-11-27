# Contributing to StepLearn

Thank you for your interest in improving **StepLearn (خطوة للتعلّم)**.

## Repository Layout

- `web/` — Interactive Web platform (Vite, Vanilla JS, CodeMirror 6, WebContainers, C# WASM).
- `mobile/` — Native Android application (Kotlin, Jetpack Compose, Material 3).

## Development Workflow

1. Create a focused branch from `main` (e.g. `feat/sql-hints`, `fix/android-theme`, `docs/readme-update`).
2. Follow `.editorconfig` formatting conventions:
   - `2` spaces for JS, JSON, HTML, CSS, YAML, Markdown.
   - `4` spaces for Kotlin, Gradle `.kts`, XML.
   - UTF-8 encoding and LF line endings.
3. Run the relevant checks before opening a Pull Request:

### Web Checks
```bash
cd web
npm ci
npm test
npm run build
```

### Android Checks
Open `mobile/` in Android Studio (AGP `9.1.1`, Kotlin `2.2.21`, JDK `17`) or run Gradle unit tests (`Robolectric`) via Android Studio / Gradle.

## Commit Message Guidelines

Use clear, descriptive commit messages following Conventional Commits:
- `feat(web): ...`
- `fix(mobile): ...`
- `docs: ...`
- `chore(ci): ...`
