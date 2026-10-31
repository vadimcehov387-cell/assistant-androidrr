# VK Assistant Android — 3.3

Development branch: `release/3.3`

## Upgrade base

Version 3.3 is being prepared from the secure 3.1 source baseline. The existing `main` branch is intentionally left unchanged.

## Planned work

- Refresh the Russian-language interface and remove stale 3.0 labels.
- Improve dashboard, community management, AI draft handling, event history, and settings.
- Preserve encrypted credential storage and fail-closed behavior.
- Preserve HTTPS-only VK requests, token-safe request handling, and response validation.
- Keep automatic publishing disabled by default, with explicit per-community consent, duplicate protection, and conservative rate limits.
- Document Android background-execution limitations; monitoring is not guaranteed to run continuously under OS power management.

## Verification status

This branch is a development workspace. No APK build or device test is claimed until the project is imported, compiled, and tested.
