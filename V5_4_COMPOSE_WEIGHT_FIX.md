# HeroLens V5.4 — Compose weight import fix

The Android build reached Kotlin compilation but failed because two UI files explicitly imported:

```kotlin
import androidx.compose.foundation.layout.weight
```

With the current Kotlin and Compose toolchain, that import resolves to an internal parent-data property. `Modifier.weight(...)` is already supplied by `RowScope` and `ColumnScope` where it is used, so the explicit import must be removed.

Updated files:

- `app/src/main/java/com/herolens/app/ui/CameraScanScreen.kt`
- `app/src/main/java/com/herolens/app/ui/HeroLensApp.kt`

No UI behavior was changed.
