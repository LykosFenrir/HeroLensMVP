# Deploy HeroLens V6 PWA with camera access

Mobile browser camera access normally requires HTTPS. Opening `index.html` directly from Downloads can be blocked even after the user has enabled camera permission.

## GitHub Pages

1. Upload the full HeroLens project to GitHub.
2. Open the repository **Settings → Pages**.
3. Set the source to **GitHub Actions**.
4. Open **Actions → Deploy HeroLens PWA**.
5. Run the workflow from the `main` branch.
6. Open the resulting HTTPS site on the phone.
7. Tap **Allow & start camera**, then choose **Allow while using the site**.

The Android APK remains the preferred camera experience because it has direct CameraX control over frame analysis, focus, exposure and permission recovery.
