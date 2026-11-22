# UniMeal

An Android app that reserves your lunch and dinner on SAMAD (University of Urmia) for you, so a reservation is never forgotten.

You press **Start**. UniMeal signs in, reads the week, reserves the missing lunches and dinners at the *Veterinary Taak* self (id 12), moves on to the next week, and repeats until everything that is published is reserved (or you press **Stop**). If food is sold out or credit is too low it only notifies you; it never picks another self and never cancels anything.

- **Preview only** is on by default: the first run just reports what it *would* reserve.
- **Wait for next week's menu** keeps checking (every 40–70 s, up to 12 h) until the next week opens.
- Optional spending limit per run.
- Several accounts (yours first, then friends who asked you), each saved only when you press Save.
- A Log tab shows every step (no passwords) to make problems easy to diagnose.
- Notifications that need you to finish by hand have an **Open SAMAD** button.

## Privacy and security
- Your student number and password are stored encrypted (AES-256-GCM, key in the Android Keystore) on this phone only, and sent only to `nds.urmia.ac.ir` over HTTPS.
- Standard certificate validation only. If the certificate is not trusted the app stops and sends nothing.
- No server, analytics or ads. Backups are disabled.
- A wrong password stops the run immediately (repeated failures cause captchas or lockouts).
- Change your SAMAD password if you ever shared it anywhere.

## Build
`gradle :app:testDebugUnitTest :app:assembleDebug` (Gradle 8.9, JDK 17). The parser, form builder, planner and runner are plain Kotlin with unit tests against a saved SAMAD page.
Releases are built by GitHub Actions when you push a `v*` tag; they are published only when the signing secrets `UNIMEAL_KEYSTORE_B64`, `UNIMEAL_KEYSTORE_PASSWORD`, `UNIMEAL_KEY_ALIAS`, `UNIMEAL_KEY_PASSWORD` exist.

Unofficial; not affiliated with the university. Use at your own risk.
