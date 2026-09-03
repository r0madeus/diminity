# Diminity

**Diminity** is a lightweight, privacy-first Android utility designed to reduce screen brightness below the hardware's minimum threshold and apply a soothing blue light filter. Built with modern Android architecture and a strict zero-permission philosophy, Diminity ensures absolute system security while combating eye strain during night reading.

---

## Key Features

* **Hardware-Breaking Extra Dimming:** Darkens the screen beyond system limits using a secure overlay mechanism.
* **Advanced Blue Light Filter:** Applies a soft, scientifically balanced warm hue to minimize eye fatigue.
* **Quick Settings Tile:** Toggle dimming instantly right from your notification panel.
* **Custom Presets:** Quick-access modes for *Balanced*, *Night Reading*, *Sleep*, and *Reset*.
* **Dynamic Material You Theme:** Seamlessly adapts to your system theme with support for System, Light, and Dark modes.
* **Android 13+ Per-App Language:** Change the app's language independently without altering system settings.

---

## Security & Privacy Guarantee (Air-Gapped)

Diminity is engineered with a **zero-trust, local-only** security model:

1. **Zero Internet Permission:** Diminity requests **no internet access (`android.permission.INTERNET`)**. It is physically incapable of transmitting telemetry, crash reports, or user data anywhere.
2. **Tapjacking & Keylogging Protection:** The dimming window uses `FLAG_NOT_TOUCHABLE` and `FLAG_NOT_FOCUSABLE` layout parameters. The overlay cannot intercept touch inputs, read keystrokes, or interfere with banking and password management apps.
3. **Open Source & Transparent:** Fully auditable codebase licensed under the GPLv3.

---

## Technical Architecture

* **UI Framework:** Jetpack Compose & Material 3 (Dynamic Color)
* **State Management:** Jetpack DataStore Preferences (Single Source of Truth)
* **Background Processing:** Foreground Service (`specialUse` type compliant with Android 14+)
* **Compatibility:** Fully optimized for API level 26 (Android 8.0) up to API level 37.

---

## Permissions Explained

Diminity requests only the bare minimum permissions required for core functionality:

* `SYSTEM_ALERT_WINDOW`: Required to draw the screen-dimming overlay.
* `FOREGROUND_SERVICE` & `FOREGROUND_SERVICE_SPECIAL_USE`: Prevents the system from unexpectedly terminating the dimming overlay in the background.
* `POST_NOTIFICATIONS`: Displays an ongoing, low-priority status notification with a direct "Stop" action (required for Android 13+).

---

## Building from Source

1. Clone the repository:
```bash
git clone https://github.com/melihucgun/diminity.git

```


2. Open the project in **Android Studio** (Koala or newer recommended).
3. Build the debug APK:
```bash
gradlew app:assembleDebug

```



---

## License

This project is open-source software licensed under the **GNU General Public License v3.0 (GPLv3)**. See the [LICENSE](https://www.google.com/search?q=LICENSE) file for details.