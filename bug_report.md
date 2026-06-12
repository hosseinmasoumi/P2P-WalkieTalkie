# Bug Report - P2P WalkieTalkie

Name: Hossein Masoumi  
Date: June 2026

## Fixed Bugs

### 1. WiFi Direct connection was unstable after disconnects

In the original project, WiFi Direct could become unstable after a disconnect or after a few minutes of use. The devices sometimes kept old connection information, but the app could not continue communication correctly without being restarted.

I improved the connection flow by adding clearer connection states, retry handling, and a simple reconnect mechanism. Now, when the connection is lost, the app attempts to recover automatically.

### 2. Voice transmission quality was inconsistent between devices

During testing, voice transmission quality was not consistent between the two devices. This made the walkie-talkie experience less reliable.

I improved the audio streaming path by using smaller UDP packets, reducing microphone clipping, and avoiding broadcast audio feedback where possible. These changes made voice transmission more stable and easier to understand.

### 3. Recording affected live voice streaming

When the Record Transmissions switch was enabled, live voice streaming could become noisy or unstable because recording work was too close to the live audio path.

I changed the recording logic so live audio is sent and played first, while recording is handled separately in the background. This keeps voice transmission active while still saving incoming and outgoing recordings with timestamps.

## Remaining Issue

WiFi Direct behavior can vary across Android devices and manufacturers. The app attempts to reconnect automatically, but after WiFi is turned off and on, reconnect speed may depend on the device and Android version.

## What I Would Improve With Another 8 Hours

During testing, voice from phone A to phone B was clear, but voice from phone B to phone A had noticeable noise, making two-way communication less reliable. With more time, I would improve peer IP detection, add better audio diagnostics for each device, and create automated tests for permissions, screen rotation, and connection state changes. I would also improve recording duration metadata and recording status feedback in the UI.
