# P2P Walkie Talkie - Entrance Test Submission

Name: Hossein Masoumi  
Date: June 2026

## Overview

This project is a reworked version of the original P2P Walkie-Talkie Android app. The goal was to make the app easier to use, more stable, and closer to a real walkie-talkie experience using WiFi Direct.

The app lets two Android devices discover each other with WiFi Direct, connect without internet, and exchange live voice using a Push-to-Talk button.

## Main Changes

- Added runtime permission handling for location, nearby WiFi devices, and microphone access.
- Added WiFi Direct discovery, connection callbacks, connection state handling, and retry logic.
- Replaced the old audio flow with a simple `AudioRecord` and `AudioTrack` streaming pipeline.
- Added Push-to-Talk visual feedback and an audio level meter.
- Added connected-device status text.
- Added a `Record Transmissions` switch.
- Added timestamped local recordings for outgoing and incoming transmissions.
- Added a Recordings screen with file list, playback, delete, and sharing support.
- Added clearer user messages with Snackbar where possible.

## Recording

When `Record Transmissions` is enabled, the app saves transmission files in:

```text
Music/P2PWalkieTalkie/
```

File names use this format:

```text
YYYYMMDD_HHMMSS_DIRECTION.amr
```

Examples:

```text
20241215_143022_OUT.amr
20241215_143045_IN.amr
```

`OUT` means the local user was speaking.  
`IN` means audio was received from the other device.

## Build Instructions

Required environment:

- Android Studio Ladybug 2024.2.1 or newer
- Min SDK: 24
- Target SDK: 34
- Two Android devices are recommended for testing WiFi Direct

Build steps:

1. Open the project in Android Studio.
2. Let Gradle sync finish.
3. Connect an Android device.
4. Build the debug APK from Android Studio.
5. Install the APK on two Android devices.

## How to Test

1. Install the app on two Android devices.
2. Open the app on both devices.
3. Grant all required permissions.
4. Make sure WiFi and Location are enabled.
5. Open the Devices screen and discover nearby devices.
6. Tap a device to connect.
7. Open the Talk screen.
8. Hold the PTT button and speak on one device.
9. Listen for the audio on the other device.
10. Enable `Record Transmissions`.
11. Make a short transmission.
12. Open the Recordings screen and play the saved file.
13. Long press a recording to share it.

## Known Limitations

- WiFi Direct behavior can be different across Android devices and manufacturers.
- Reconnection is simple and tries to connect again to the last selected device.
- Real latency depends on the device, WiFi Direct signal quality, and Android version.
- The app should be tested on two real Android devices before final submission.

## Files to Submit

- Full Android Studio project or GitHub repository link
- Signed debug APK named `P2PWalkieTalkie_HosseinMasoumi.apk`
- 3 to 5 minute demo video
- 1 page bug report PDF

## Original Project

Original repository: https://github.com/fajf/P2P-WalkieTalkie

The original project was created as a WiFi P2P demo and was not intended to be production ready. This submission focuses on stabilizing and extending it for the entrance test.