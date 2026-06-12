# P2P WalkieTalkie
Name: Hossein Masoumi  
Date: June 2026
This project is an Android WiFi Direct walkie-talkie application. It allows two Android devices to discover each other, connect directly without internet, and exchange live voice using a Push-to-Talk button.
The project was updated according to the provided Android development test instructions. The main connection, audio streaming, permission handling, UI feedback, and local recording issues have been fixed. The application has been tested as a working demo and now performs the required core flow in practice.

## Application Features
- Discover nearby Android devices using WiFi Direct.
- Connect two devices directly without using an external network.
- Send live voice from one device to another with Push-to-Talk.
- Receive and play incoming voice in real time.
- Show clear connection status for disconnected, discovering, connecting, and connected states.
- Show whether the device is connected as Group Owner or Client.
- Show the number of connected devices.
- Display visual feedback when the Push-to-Talk button is pressed.
- Show a simple audio level meter while speaking.
- Handle required Android permissions for microphone, location, and WiFi Direct.
- Keep the Push-to-Talk button disabled when required permissions or connection state are missing.
- Save outgoing and incoming transmissions when recording is enabled.
- Store recordings with timestamped file names.
- Show saved recordings in a separate Recordings screen.
- Play saved recordings using the device speaker.
- Share recordings using the Android system share sheet.

## Architecture (MVVM)

The project follows the MVVM (Model–View–ViewModel) architecture pattern, integrated with Jetpack Compose for the UI layer.

### Layers

**View (UI Layer)**
- Compose screens: `DevicesScreen`, `TalkScreen`, `RecordingsScreen`.
- Stateless composables that observe ViewModel state via `collectAsStateWithLifecycle()`.
- No business logic; only renders state and forwards user actions to the ViewModel.

**ViewModel Layer**
- `DeviceDiscoveryViewModel` — manages WiFi Direct discovery, peer list, and connection state (`StateFlow<ConnectionState>`), Group Owner / Client role, connected device count.
- `TalkViewModel` — manages Push-to-Talk state, audio streaming lifecycle, mic permission checks, and audio level meter values.
- `RecordingsViewModel` — manages the list of saved recordings, playback state, and share-intent triggering.

**Model / Repository Layer**
- `WifiDirectRepository` — wraps `WifiP2pManager` and the broadcast receiver, exposes peer list and connection info as `Flow`.
- `AudioStreamRepository` — handles `AudioRecord` / `AudioTrack` streaming over sockets on background threads.
- `RecordingRepository` — handles file I/O for `Music/P2PWalkieTalkie/`, including timestamped naming and metadata.

### State Management
- `MutableStateFlow` exposed as `StateFlow` from each ViewModel.
- `viewModelScope` coroutines handle discovery, streaming, and recording tasks off the main thread.
- UI reacts to connection/permission state changes (e.g., Push-to-Talk button enabled/disabled, status indicators).

### Lifecycle Handling
- ViewModels survive configuration changes (screen rotation, etc.).
- WiFi Direct broadcast receiver registration/unregistration is tied to lifecycle events using `DisposableEffect` in Compose.
- Audio streaming and recording coroutines are cancelled automatically when the relevant ViewModel is cleared.

## Working Flow
1. Install the APK on two Android devices.
2. Open the app on both devices.
3. Grant the required permissions.
4. Make sure WiFi and Location are enabled.
5. Use the Devices screen to discover the second device.
6. Tap the device to connect.
7. After connection, open the Talk screen.
8. Hold the Push-to-Talk button and speak.
9. The other device receives and plays the voice.
10. Enable Record Transmissions to save voice transmissions.
11. Open the Recordings screen to view, play, or share saved files.

## Recording
The app supports local recording of transmissions.
Recorded files are stored in:
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

## Fixed Issues
The app was improved to address the main problems described in the test instructions:
- Runtime permissions are handled properly.
- WiFi Direct discovery and connection handling were improved.
- Connection retry and reconnect behavior were added.
- Audio streaming was moved to background threads.
- Live voice transmission now uses `AudioRecord` and `AudioTrack`.
- One-way audio quality problems were reduced.
- Microphone clipping and harsh noise were reduced.
- Recording was separated from the live audio path to avoid disturbing voice transmission.
- The Push-to-Talk button now gives clear visual feedback.
- The Recordings screen was cleaned up and only shows saved transmission files.
- Unused screens and unnecessary UI elements were removed.
- Error and permission messages were improved.

## Final Status
The application now follows the requested test requirements and works as a practical demo. Two devices can connect through WiFi Direct, exchange live voice, and save incoming and outgoing voice transmissions locally with timestamps.
