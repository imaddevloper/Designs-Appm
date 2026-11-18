# DisguiseApp — Android starter

This is a minimal Kotlin + XML Android project that preserves the requested
package layout and source filenames.

## Build requirements
- Android Studio with JDK 17
- Android SDK Platform 35
- Android Gradle Plugin 8.7.3 / Gradle 8.9

Open this folder in Android Studio and allow Gradle sync. If Android Studio
asks to create or update the Gradle Wrapper, use Gradle 8.9.

## Safety scope
The C2, Telegram, Accessibility, screen-reading, file-transfer, and boot
persistence classes are placeholders only. They do not implement covert
control, background command polling, screen capture, data collection/upload,
or automatic persistence. The manifest intentionally declares no sensitive
permissions or background receivers.
