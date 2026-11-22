# Experimental legacy Android client

This separate debug app bundles GeckoView 118 for 32-bit ARM and a snapshot of the PhoneBox panel. It targets Android 4.1 (API 16) and later. It was tried on an Android 4.1 Huawei tablet; it is a prototype, not a replacement for the current Android application. GeckoView is distributed under Mozilla's MPL 2.0.

## Build and pair

Install JDK 17, Android SDK platform 35 and build tools 35.0.0, and put Android platform-tools (`adb`) on your PATH. From this directory:

```sh
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

On Windows, use `gradlew.bat` instead of `./gradlew`. Connect the tablet over USB, open the app once, and enable Wi-Fi access in the desktop agent. Then, on the computer, run:

```sh
python setup.py [computer-lan-address]
```

The optional address must be one advertised by the local PhoneBox agent. The setup script provisions the agent's existing LAN pairing into the app's private storage via `adb` and `run-as`, and then removes its temporary staging file. The debug build is required for `run-as`. Restart the app after pairing. Keep the computer and tablet on the same network.

## Limitations

The bundled panel is a snapshot and can lag behind the main application. The app has its own package ID and does not replace the standard PhoneBox app. Its Java bridge serves the local page from a random loopback port and capability path, keeps the bearer token out of page JavaScript, and pins the agent's TLS certificate. Pairing data is temporarily staged on the device during setup; use a trusted device and computer. This prototype has not received a full security review.

The native Android bridge and long-lived streaming endpoints are unavailable; features such as trackpad, phone sensors, speech, hardware page keys, and streaming widgets may not work. Only the active page's widgets are refreshed, so cached pages may briefly show old data after a swipe. Discord soundboard controls require an agent build containing the corresponding endpoints.
