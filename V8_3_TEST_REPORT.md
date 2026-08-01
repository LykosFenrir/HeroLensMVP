# HeroLens V8.3 test report

## Completed locally

- Kotlin recommendation core compiled with `kotlinc`.
- Selected-role smoke test returned only heroes from the requested role.
- All-roles smoke test returned the full hero catalog across Tank, Damage and Support.
- Python training and dataset-import scripts passed syntax compilation.
- Source search confirmed that runtime labels no longer equate PS5, console, Open Queue or 6v6.
- Existing ONNX labels and model input shape were not changed by the V8.3 rule update.

## Still required on GitHub / Android

- Run **Train Hero AI Model** because this package also includes the V8.2 six-row and progression-badge training updates.
- Run **Build Android APK** after training succeeds.
- Retest Auto Scan and Picture Scan on both five-row and six-row scoreboards.
- Verify **Selected role** and **All roles / mixed** recommendation pools on the phone.

The local environment does not include the Android SDK, so the final Compose/Android
compilation must be verified by the repository workflow.
