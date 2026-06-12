# Bug Report - P2P WalkieTalkie

Name: Hossein Masoumi  
Date: June 2026

## Fixed Bugs

### 1. WiFi Direct connection was unstable after disconnects

In the original project, the WiFi Direct connection could become unstable after a disconnect. In some cases, after a few minutes of use or after WiFi Direct was interrupted, the phones still kept old connection information, but the app could not continue communication correctly without restarting.

I improved the connection flow by adding clearer connection states, connection retry handling, and a simple reconnect flow. When the connection is lost, the app now attempts to recover automatically instead of requiring the user to restart the app.

### 2. Voice transmission quality was different in each direction

During testing, voice from phone A to phone B was clear and normal, but voice from phone B to phone A had noticeable and uncomfortable noise. This made the walkie-talkie experience unreliable because the communication quality was not consistent in both directions.

I improved the audio streaming path by using smaller and more stable UDP packets, reducing harsh microphone clipping, and avoiding broadcast audio feedback where possible. This made two-way voice transmission more stable and easier to understand.

### 3. Recording interfered with live voice streaming

When the Record Transmissions switch was enabled, live voice streaming could become noisy or unstable. The issue was caused by recording work being too close to the live audio path, which could disturb real-time transmission on some devices.

I changed the recording logic so that live audio is sent and played first, while recording is handled separately in the background. This keeps the walkie-talkie function active while still saving incoming and outgoing transmissions with timestamps.

## Remaining Issue

The app depends on WiFi Direct behavior, and WiFi Direct can behave differently across Android devices and manufacturers. In some cases, reconnecting after WiFi is turned off and on may take a few seconds. The app attempts to recover automatically, but the exact reconnect speed depends on the device and Android version.

## What I Would Improve With Another 8 Hours

With more time, I would improve the reconnect logic further by detecting the peer IP more reliably, add more detailed audio diagnostics for each device, and add automated tests for permissions, screen rotation, and connection state changes. I would also improve the recording screen by showing more accurate duration metadata and clearer recording status feedback in the UI.