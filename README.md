# OVPN Client

Android app: loads OpenVPN servers from servers.json (on launch and hourly), connects with the
OpenVPN for Android engine (ics-openvpn). Everything is built by GitHub Actions.

Layout: `app/src/main/java/...` (our code) and `.github/workflows/build.yml`.
The workflow downloads the engine, adds our code, builds, and signs.

## Use
1. Upload these files to a GitHub repo (branch `main`).
2. Make a keystore once:
   keytool -genkeypair -v -keystore release.jks -alias upload -keyalg RSA -keysize 2048 -validity 10000
   base64 -w0 release.jks
3. Settings > Secrets and variables > Actions: KEYSTORE_BASE64, KEYSTORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD.
4. Actions > Build Android > Run workflow > download `android-builds`
   (app-debug.apk, app-release.aab signed with your key).

No secrets? You still get the debug APK and an unsigned AAB.

GPL v2: the engine is GPL, so this app must be GPL too. Server list = VPN Gate volunteer relays.
