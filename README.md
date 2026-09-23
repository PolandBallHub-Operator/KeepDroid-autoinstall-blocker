# KeepDroid Auto-installation blocker Maker

**KeepDroid Auto-installation blocker Maker** is an Android 9+ Material 3 utility for creating a small, user-confirmed device-admin APK on the phone itself.

You can create a blocker for an app of your choice by entering its package name and a display name. The generated APK is intended to help users defend against adware installed by device-side software and unauthorized post-installation or automatic installation flows. It does not replace system apps, use root, perform silent installation, or use privileged OEM APIs.

## Features

- Choose the generated package name.
- Choose the generated app name shown in Android settings.
- Use a selected JKS or PKCS12 keystore, or the bundled test-only debug keystore.
- Generate and sign the APK on the Android device.
- Launch Android's standard installation confirmation screen.
- Save generated APKs to `/sdcard/Documents/KeepDroid/apk`.
- Use a minimal `com.app.base` template containing no MainActivity and no application UI; it contains only the device-admin receiver and required device-admin policy XML. Its complete project source is included in `template_source/KeepDroidMinimal/`.
- Include FreeDroidWarn as an in-app warning component. Its warning is shown on a maker version upgrade and links to the upstream information and solutions pages.
- Activate device-admin status only through visible Android system settings.

## Build method

Install Android SDK platform 35 and use a JDK supported by Android Gradle Plugin 8.6.1. Then run:

```bash
export ANDROID_HOME=/path/to/android-sdk
export ANDROID_SDK_ROOT=$ANDROID_HOME
./gradlew clean assembleDebug
```

The debug APK is generated at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Project metadata:

| Item | Value |
|---|---|
| Maker application ID | `com.devtangle.manager` |
| Maker version | `1.1.0-minbase-storage` |
| Minimum Android version | Android 9 / API 28 |
| Target SDK | 35 |
| Embedded base package | `com.app.base` |

## Generated APK storage

On Android 10 and newer, the maker uses MediaStore scoped storage. On Android 9, it requests `WRITE_EXTERNAL_STORAGE` at runtime. The resulting APK is saved under:

```text
/sdcard/Documents/KeepDroid/apk
```

The APK is then passed to Android's standard package installer. No silent installation is performed.

## Test-only keystore

The repository contains a debug keystore for forks and functional tests:

```text
keystore/keepdroidmaker-debug.keystore
```

The bundled fallback key is not for production use:

```text
Alias: keepdroidmaker-debug
Password: keepdroidmaker-debug
```

For a release build, remove the test key and use a private keystore. Never reuse the bundled debug key for a sensitive or widely distributed application.

## Safety and scope

Use this tool only on devices that you own or administer. The generated APK is not a system-app replacement and cannot guarantee that every OEM installer or system component will honor the intended protection. Android package-signature verification and device-admin rules remain authoritative.

Do not use arbitrary package names or display names to impersonate another app, system component, company, or developer. The maker does not perform silent installation or bypass Android confirmation screens.

## Third-party licenses

The project uses open-source libraries. License information is listed in [`THIRD_PARTY_LICENSES.txt`](THIRD_PARTY_LICENSES.txt), and the full GPLv3 text for the embedded `aXML` library is included at [`licenses/aXML-GPL-3.0.txt`](licenses/aXML-GPL-3.0.txt).

The important distinction is that `aXML` is GPLv3, not Apache-2.0. The complete aXML license text and corresponding aXML source are included in `licenses/GPL-3.0-aXML.txt` and `third_party_source/aXML/`. Any redistribution or modification that includes this library must comply with GPLv3, including applicable source-code and license-notice obligations. Other listed dependencies use Apache-2.0 or their respective upstream licenses; the complete Apache-2.0 text is included in `licenses/Apache-2.0.txt`.
