# APK Distribution & Versioning Policy

Whenever building or releasing the Android application:
1. **Current Version in Root:** Always keep the latest/current version of the APK directly outside in the main folder as `ExpenseTracker.apk`.
2. **Version Archive in `apks/`:** Always keep all versions (including the current one and historical versions) together in the `apks/` directory named with their version tag (e.g. `ExpenseTracker-v1.0.0.apk`, `ExpenseTracker-v1.0.1.apk`, etc.).

This is automated via the Gradle `copyApk<Variant>` task in `android/app/build.gradle.kts` which runs automatically whenever `assemble`, `assembleDebug`, or `assembleRelease` is executed.
