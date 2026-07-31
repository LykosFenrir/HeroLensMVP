# HeroLens V5.2 build fix

This update replaces the removed `android.kotlinOptions.jvmTarget` string assignment with the Kotlin compiler options DSL required by Kotlin 2.3.20:

```kotlin
kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}
```

It also increments the debug app version to 0.5.2 (version code 7).
