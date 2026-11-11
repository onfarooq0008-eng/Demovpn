# FastVPN 2.0 (OpenVPN)

Kotlin + Jetpack Compose. OpenVPN only. Server list is fetched at runtime from
`https://raw.githubusercontent.com/onfarooq0008-eng/Working-Ovpn/refs/heads/main/servers.json`
(no servers are bundled). Application ID: `com.fastvpn.app7k2q9x`.

## 1. Engines: ics-openvpn and/or OpenVPN 3 core
The tunnel is the official **OpenVPN 3 core** (C++), built in-app through the NDK, with this project's own
`FastVpnService` (VpnService, single TUN, foreground notification) and JNI glue (`app/src/main/cpp/jni_client.cpp`).
The app supports BOTH engines and can be built with either or both (Settings > OpenVPN engine: Auto / ics-openvpn / OpenVPN 3).

**ics-openvpn** (`./scripts/fetch_engine.sh --ics`, cloned to `./ics-openvpn`) is picked up automatically: `settings.gradle.kts` includes
`ics-openvpn/main` as `:openvpn` and `src/engineIcs` is compiled. GPLv2. Auto mode prefers it when present. Not compiled yet: if `main` is an
application module in the revision you cloned it must be forked/built as a library. Its own service and notification are used for this engine.

    ./scripts/fetch_engine.sh --ovpn3   # clones OpenVPN3 + asio into ./third_party (pin OPENVPN3_REF / ASIO_REF)

When `third_party/` exists, Gradle builds `libfastvpn_ovpn3.so` (OpenSSL comes from the Prefab AAR
`com.android.ndk.thirdparty:openssl`). Without it the app still builds but refuses to connect (never fakes Connected).
Needs NDK 26.1.10909125 and CMake 3.22.1.

**Licensing:** the OpenVPN 3 core is AGPLv3 (OpenVPN Inc. also sells a commercial license). Distributing the app
with it means publishing your app's source under AGPLv3, or obtaining that commercial license. Decide before Google Play.

**Known gaps (never compiled yet):** the CMake file and JNI glue are written against the OpenVPN3 API from memory and
will likely need small fixes. LZO/LZ4 compression is not built in, so profiles that force `comp-lzo yes` may fail.

## 2. Build
    gradle :app:testDebugUnitTest :app:assembleDebug        # app/build/outputs/apk/debug
    KEYSTORE_PATH=/path/release.jks KEYSTORE_PASSWORD=... KEY_ALIAS=... KEY_PASSWORD=... \
      gradle :app:assembleRelease :app:bundleRelease        # signed APK + AAB
(Android Studio creates the Gradle wrapper on first sync.)

## 3. GitHub Actions signing secrets
Create a keystore once: `keytool -genkeypair -v -keystore release.jks -alias fastvpn -keyalg RSA -keysize 2048 -validity 10000`
Then `base64 -w0 release.jks` and add these repository secrets (Settings > Secrets and variables > Actions):
`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.
Workflow `.github/workflows/android-build.yml`: debug APK on every push/PR; signed release APK + AAB on `v*` tags or
manual run with "release" ticked. Missing secrets fail with a clear error. Never commit the keystore.

## Architecture
`data/` ServerParser, ServerRepository (OkHttp, ETag, cache, never overwritten by a bad download), OvpnValidator, ServerStore.
`vpn/` VpnSession (state), VpnController (single-flight connect, backoff reconnect), FastestServer, FastVpnService, OvpnBridge/NativeOvpn (JNI), EngineImpl.
`ui/` Home, Locations, Favorites, Details, History, Settings, Diagnostics, About.
