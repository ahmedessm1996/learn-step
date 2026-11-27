# Security Policy

## Reporting a Vulnerability

If you discover a security vulnerability in **StepLearn** (either the Web platform in `web/` or the Android app in `mobile/`), please do **not** open a public GitHub issue with exploit details.

Instead, report it privately via GitHub Security Advisories on [`ahmedessm1996/learn-step`](https://github.com/ahmedessm1996/learn-step/security/advisories/new) or contact the repository owner directly.

Please include:
- Target component (`web/` or `mobile/`)
- Steps to reproduce or minimal proof of concept
- Potential impact and suggested remediation (if known)

## Security Architecture Notes

- **Web (`web/`)**:
  - Cross-Origin Isolation headers (`Cross-Origin-Embedder-Policy: credentialless`, `Cross-Origin-Opener-Policy: same-origin`) are configured in `vite.config.js`, `netlify.toml`, `vercel.json`, and `_headers` to isolate WebContainers execution.
  - Strict CSP and `X-Frame-Options: SAMEORIGIN` headers are enforced for hosted deployments.
- **Mobile (`mobile/`)**:
  - Runs local curriculum engines and state storage offline via Jetpack DataStore Preferences without requiring sensitive runtime permissions.
