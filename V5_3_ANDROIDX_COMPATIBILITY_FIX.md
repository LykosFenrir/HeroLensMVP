# HeroLens V5.3 AndroidX compatibility fix

The GitHub Actions build reached Android dependency validation but failed because
Lifecycle 2.11.0 requires compileSdk 37 and Android Gradle Plugin 9.1.0 or newer.
This project intentionally remains on the stable, internally consistent build stack:

- Android Gradle Plugin 8.13.2
- compileSdk 36
- targetSdk 36
- Lifecycle 2.10.0
- JDK 17

Changed in `app/build.gradle.kts`:

```kotlin
implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
```

The app version is now 0.5.3 (versionCode 8).
