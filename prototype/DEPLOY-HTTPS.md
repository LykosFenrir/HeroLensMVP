# HeroLens V5 Supreme web camera setup

The browser camera needs permission **and** a secure origin. A downloaded `index.html` opened from the phone's Downloads folder may be treated as a local `file://` page; Samsung Internet and other Android browsers can block live camera access or cross-origin portrait analysis in that context.

## Easiest test: deploy the complete folder over HTTPS

1. Extract the `HeroLens-web-v5-supreme.zip` package on a PC.
2. Use a static HTTPS host such as Netlify Drop, Cloudflare Pages, GitHub Pages or Firebase Hosting.
3. Upload the **contents of the complete folder**, not only `index.html`.
4. Open the generated `https://...` address on the phone.
5. Tap **Allow & start camera**.
6. Choose **Allow while using the site** when the browser asks for Camera permission.
7. Optionally use the browser menu → **Add to Home screen** to install the PWA.

## If permission was denied

In Chrome or Samsung Internet, open the site's permissions/settings, set Camera to Allow, reload the page and tap Start again.

## Native Android build

The Android project is preferred for speed and reliability. It uses CameraX directly, declares Camera permission in the manifest, requests it at runtime, and provides an App Settings recovery button.
