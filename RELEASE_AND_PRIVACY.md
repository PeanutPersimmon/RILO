# Release, Security and Privacy Baseline

This document was copied from RIDO as historical planning guidance. Its feature
and release-readiness statements have not been audited for RILO Phase 1; use
the actual source and Manifest for current behavior.

## Release identity and build

- Project: `RILO`.
- Application ID / namespace: `io.github.peanutpersimmon.rilo`. Treat this as permanent once the first Google Play artifact is published.
- Versioning begins at `versionCode 1`, `versionName 0.1.0`; every Play upload increments versionCode.
- Production distribution uses Android App Bundle (`.aab`). APK is for local testing only.
- Release build enables R8 minification and resource shrinking. Release behavior, startup, migrations and restore/export must be tested before publication.
- Use Play App Signing or a deliberately managed equivalent. Back up the upload key securely and document recovery ownership outside the repository.

## Secret handling

- Keystores, signing property files, API keys, local SDK paths and generated artifacts are ignored by Git.
- Never commit secrets, include them in resources, or print them to logs.
- A future AI API key must never be hard-coded or shipped as a recoverable secret in the APK. Any remote AI design requires a threat model and a server-side credential boundary.
- Before release, scan the source tree and final AAB for secrets and unexpected endpoints.

## Privacy and data collection

- Local-first is the default. Core planning and recording remain usable without AI or an account.
- Do not add analytics, ads, tracking or crash-reporting SDKs by default.
- No long-term raw voice transcript storage. If speech is later implemented, disclose processing location, retention, recipients and deletion behavior before collection.
- First version requests no location permission. A free-text location field does not justify device location access.
- Use least privilege. Every new permission needs a documented user-facing purpose, feature-triggered request timing and Google Play declaration review.
- Export/import data must include `schemaVersion`, validation, bounded parsing and clear conflict/error handling. Sensitive exports require explicit user action.

## Dependencies and supply chain

- Prefer Android platform and AndroidX/Jetpack APIs over third-party libraries.
- Before adding a dependency, review maintenance activity, license, transitive dependencies, permissions, data collection, network behavior, release-build compatibility and removal cost.
- Pin versions through the version catalog; do not use dynamic versions.
- Current runtime dependencies are AndroidX/Jetpack only. KSP is Google's compile-time processor infrastructure. JUnit/AndroidX Test/Espresso are test-only.
- Review dependency updates deliberately and run compile, unit, lint and release checks; do not update solely because a newer alpha/beta exists.

## Persistence and recovery

- Room database `rilo.db` is currently at version 7. Its canonical exported schema is stored under `app/schemas/` and must be committed with every database-version change.
- Every version after v1 requires an explicit `Migration` plus migration tests. `fallbackToDestructiveMigration()` is forbidden for production data.
- Android backup is disabled at initialization. Re-enable it only after the real data model, encryption/restore behavior and privacy UX are reviewed and explicit extraction rules are added.
- Data deletion and “delete all” must define local files, database, DataStore, exports, remote copies and system-calendar copies before implementation.

## Google Play readiness checklist

Before each production release:

1. Build a signed AAB from a clean checkout using the supported JDK/Gradle/AGP combination.
2. Run unit, instrumentation, accessibility, dark/light, large-font, small-screen, landscape and release-minification smoke tests.
3. Verify target SDK and Play policy deadlines; update only through a tested migration branch.
4. Review Manifest permissions, exported components, network security, backup rules and dependency licenses.
5. Complete Data safety and privacy disclosures from observed app behavior, not assumptions.
6. Verify no secrets, debug endpoints, raw speech, fake production data or verbose sensitive logs are packaged.
7. Exercise install, upgrade, import/export and data deletion paths once they exist.

## Future / not implemented in v1

Privacy policy URL, Play listing, developer verification, signing ceremony, backup behavior, data export encryption, AI processing disclosure and calendar permission flow require concrete features/account decisions. They are not invented in this initialization round.
