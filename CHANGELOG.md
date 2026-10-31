# Changelog

## 3.3 — in development

No release APK is published yet. This branch contains ongoing integration work.

### Implemented in the current development branch
- Added Material 3 Compose dashboard and a Compose Activity entry point.
- Added Android Gradle configuration for Kotlin 2.0.20, Compose and Material 3.
- Added an Android debug-build GitHub Actions workflow.
- Hardened VK and Telegram HTTP response handling, timeouts and input validation.
- Added defensive community preference parsing and duplicate filtering.
- Added encrypted preferences with migration from the previous plaintext preference file.
- Hardened the monitoring worker lifecycle against rapid stop/restart races.
- Advance a post cursor only after Telegram confirms notification delivery.
- Validate quiet-hour values and keep conservative automatic-publishing limits.
- Hardened AI response parsing and bounded generated comment length.

### Still incomplete
- The Communities, AI, History and Settings tabs are not fully implemented.
- The current Activity does not yet provide the original 3.0 settings editor and community-management form.
- Full device testing and a successful debug APK build have not yet been confirmed.
- Android background-execution behavior still requires testing on a real device.
