# Smart Bubble - Floating Multitasking Browser for Android

  <!-- Optional: Replace with a URL to a GIF of your app in action -->

Smart Bubble is a sophisticated multitasking utility for Android that allows users to open web pages in a movable and resizable floating window. It provides seamless, on-the-fly access to essential web apps like YouTube, ChatGPT, and Google Translate without forcing the user to leave their current application.

---

## ✨ Core Features

*   **Persistent Floating Bubble:** An elegant, movable bubble on the side of the screen provides instant access to the app's features.
*   **Animated Side Panel:** A swipe or tap on the bubble reveals a smooth, animated side panel with shortcuts to favorite web apps.
*   **Resizable & Movable Windows:** Each web app opens in its own fully independent floating window that can be moved and resized with intuitive touch controls.
*   **Advanced YouTube Integration:**
    *   Features a custom-built JavaScript bridge to intercept video clicks on the YouTube mobile site, providing a cleaner player experience.
    *   Includes a seamless Picture-in-Picture (PiP) "mini-player" mode that maintains video playback during transitions.
*   **Freemium Monetization Model:**
    *   **Free Tier:** Ad-supported, allowing one floating window at a time.
    *   **Premium Tier:** An ad-free experience with the ability to open multiple floating windows simultaneously, managed via in-app subscriptions.
*   **Robust Security:** Implemented code obfuscation and anti-tampering checks to protect the application from reverse engineering.

---

## 🛠 Technical Stack & Libraries

This project showcases a range of modern Android development techniques and libraries.

*   **Language:** **Kotlin**
*   **Architecture:** MVVM-like structure with Singleton Managers for handling core services (Ads, Subscriptions, etc.).
*   **Core Android Components:** `Foreground Service`, `WindowManager`, `WebView` with a custom `JavascriptInterface`, and View Binding.
*   **Key Libraries:**
    *   **[RevenueCat](https://www.revenuecat.com/):** For robust in-app subscription management.
    *   **[Google AdMob](https://admob.google.com/):** For banner and interstitial advertising.
    *   **[Spotlight](https://github.com/TakuSemba/Spotlight):** For a highly customizable and intuitive user onboarding tutorial.
    *   **[Material Components for Android](https://material.io/develop/android):** For modern UI elements like Cards, Buttons, and Switches.
*   **Security:** **R8/ProGuard** for code obfuscation and shrinking in release builds.

---

## 🚀 Key Technical Challenges & Solutions

The development of Smart Bubble involved solving several complex Android challenges to achieve a polished, OS-level feel.

### 1. The Floating UI & Seamless Playback

*   **Challenge:** The most significant hurdle was ensuring uninterrupted YouTube video playback when switching between the main floating window and the "mini-player". Simply moving the `WebView` between layouts caused the video to pause or stutter.
*   **Solution:** A three-part strategy was implemented:
    1.  **Cross-Fade Animation:** A quick fade-out/fade-in animation was used to visually mask the view transition, providing a smooth user experience.
    2.  **Correct Lifecycle Management:** The `WebView`'s `onPause()` and `onResume()` methods are called at the exact right moments during the invisible phase of the animation to preserve the video's playback state.
    3.  **JavaScript Bridge:** A robust `JavascriptInterface` is injected into the YouTube mobile site to intercept clicks on all video types (including Shorts), ensuring reliable control and playback.

### 2. Premium Features & Security

*   **Challenge:** The app needed a way to offer premium features and protect itself from being easily cracked to unlock those features for free.
*   **Solution:** A layered security approach was implemented:
    *   **R8/ProGuard:** The release build is heavily obfuscated, making the compiled code extremely difficult for an attacker to read and understand. Custom ProGuard rules were written to ensure compatibility with all libraries.
    *   **APK Signature Check:** A runtime check in the `Application` class verifies the app's digital signature on startup. If the APK has been tampered with and re-signed, the app will detect the forgery and refuse to run.

---

## ⚙️ How to Set Up and Run the Project

1.  **Clone the repository:**
    ```bash
    git clone https://github.com/your-username/smart-bubble-app.git
    ```
2.  **Open in Android Studio:** Open the cloned project in Android Studio.
3.  **API Keys (Optional):** The project uses placeholder keys for AdMob and RevenueCat. To use your own, you would add them to the `app/build.gradle.kts` file:
    ```kotlin
    android {
        defaultConfig {
            // ...
            buildConfigField("String", "REVENUECAT_API_KEY", "\"your_revenuecat_key\"")
        }
    }
    ```
4.  **Build and Run:** Sync the Gradle files and run the app on an emulator or a physical device.

---

## 📄 License

This project is licensed under the MIT License - see the [LICENSE.md](LICENSE.md) file for details.
