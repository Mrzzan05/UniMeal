# Security

## What UniMeal handles
Your SAMAD student number and password. They are encrypted on the phone (AES-256-GCM, key in the Android Keystore), never leave it except to `nds.urmia.ac.ir` over HTTPS, and are never written to the log, notifications or backups.

## Design rules
- HTTPS only, to `nds.urmia.ac.ir`, with the system's certificate validation. An untrusted certificate stops the run.
- Redirects are followed manually and only inside the same host.
- The in-app SAMAD page receives the session cookie only, never the password; cookies are cleared when it closes.
- No analytics, ads or own server. `allowBackup` is off.
- The only other network call is the manual update check to `api.github.com`; the app only opens links inside this repository.
- A wrong password stops the run (no retries that could lock the account).
- Releases are signed with a private key kept in GitHub secrets; compare the SHA-256 in the release notes.

## Reporting a problem
Open a GitHub issue for ordinary bugs. For a security problem, use the repository's *Security → Report a vulnerability* page instead of a public issue. Never post your password, student number, cookies or a full log.
