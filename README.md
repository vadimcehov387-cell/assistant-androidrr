# VK Assistant Android — 3.3

Development branch: `release/3.3`

## Upgrade base

Version 3.3 is being prepared from the available **3.0 source project** (`VKAssistantAndroid_3.0_source.zip`). Version 3.1 is not required. The existing `main` branch is intentionally left unchanged.

## Planned work

- Import the complete 3.0 Android project into this branch.
- Connect the prepared Jetpack Compose / Material 3 interface to the existing Activity and application logic.
- Refresh the Russian-language interface and remove stale 3.0 labels.
- Improve dashboard, community management, AI draft handling, event history, and settings.
- Preserve the existing VK, Telegram, AI, preferences, and monitoring behavior during UI migration.
- Preserve encrypted credential storage and fail-closed behavior from the source being integrated.
- Preserve HTTPS-only VK requests, token-safe request handling, and response validation.
- Keep automatic publishing disabled by default, with explicit per-community consent, duplicate protection, and conservative rate limits.
- Document Android background-execution limitations; monitoring is not guaranteed to run continuously under OS power management.

## Verification status

This branch is a development workspace. The Compose files are not yet connected to the original Activity. No APK build or device test is claimed until the full project is imported, integrated, compiled, and tested.
