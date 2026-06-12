# Bug Report - P2P WalkieTalkie

Name: Hossein Masoumi  
Date: June 2026

## Fixed Bugs

### 1. WiFi Direct connection was not stable after disconnect

In the original project, the WiFi Direct connection could become unstable after a disconnect. In some cases, after a few minutes or after WiFi Direct was interrupted, the phones still showed old connection information, but the app could not continue communication correctly without restarting.

I improved the connection flow by adding clearer connection states, retry handling, and a simple reconnect flow. When the connection is lost, the app now tries to recover instead of requiring the user to restart the app.

### 2. Voice transmission worked well only in one direction

During testing, voice from phone A to phone B was clear and normal, but voice from phone B to phone A was not clear and had noticeable noise. This made the walkie-talkie experience unreliable because the communication quality was different in each direction.

I fixed this by improving the audio streaming path, using smaller and more stable UDP packets, reducing harsh microphone clipping, and avoiding broadcast audio feedback where possible. The result is more stable two-way voice transmission.

### 3. Recording interfered with live voice streaming

When the Record Transmissions switch was enabled, live voice streaming could become noisy or unstable because recording and audio streaming were happening too closely in the same flow.

I changed the recording logic so that live audio is sent and played first, while recording is handled separately in the background. This keeps the walkie-talkie function active while still saving incoming and outgoing transmissions with timestamps.

## Remaining Issue

The app depends on WiFi Direct behavior, and WiFi Direct can behave differently on different Android devices and manufacturers. In some cases, reconnecting after WiFi is turned off and on may take a few seconds. The app attempts to recover automatically, but the exact speed depends on the device.

## What I Would Improve With Another 8 Hours

With more time, I would improve the reconnect logic further by detecting the peer IP more reliably, add more detailed audio diagnostics for each device, and add automated tests for permission, rotation, and connection state changes. I would also improve the recording implementation to provide better duration metadata and clearer recording status feedback in the UI.