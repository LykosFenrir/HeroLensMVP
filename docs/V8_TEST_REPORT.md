# HeroLens V8 local validation report

## Passed

- Python syntax compilation for the model trainer and YOLO dataset importer.
- Generated-full-scoreboard benchmark smoke test: 960×540 scene, ten valid labeled portrait boxes and 96×96 normalized crops.
- Kotlin recommendation engine compiled and returned the expected three Damage recommendations.
- Pure Kotlin vision pipeline compiled and passed a locator + four-frame burst consensus smoke test.
- ONNX classifier source compiled against API-shaped Android/ONNX Runtime stubs.
- Neural/template detector source compiled against API-shaped repository/classifier stubs.
- Kotlin delimiter validation across all production and test source files.
- Android XML parsing.
- GitHub Actions YAML parsing.

## Not claimed

- No final Android APK was compiled in this container because Android SDK 36 and Maven dependencies are unavailable here.
- No ONNX weights were trained locally because portrait/model downloads require internet access. The included GitHub workflow performs the actual training and validation.
- The 600-scoreboard publish gate is synthetic. Production accuracy still requires a separate, unseen real-device set from TVs, PS5, laptops and monitors.

## Required external validation

1. Upload V8 to GitHub.
2. Run **Train Hero AI Model** with the default 600 benchmark scoreboards.
3. Confirm the workflow publishes model metrics and commits the ONNX file.
4. Confirm the automatically triggered Android build succeeds.
5. Test Auto Scan and Picture Scan on the Hisense 55-inch/PS5 and ASUS ROG Strix G15.
