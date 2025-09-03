# CameraK - Android Camera App

A modern Android camera application built with Jetpack Compose and Camera2 API.

## Features

### Camera Modes
- **Photo Mode**: Take high-quality photos with various aspect ratios
- **Video Mode**: Record videos with professional quality settings

### Photo Features
- Multiple aspect ratios: Full, 16:9, 4:3, 1:1
- Flash control (torch mode)
- Camera switching (front/back)
- Photo gallery integration
- Thumbnail preview of last captured photo

### Video Features
- HD video recording (1920x1080, 30fps, 10Mbps)
- Recording timer display
- Video gallery integration
- Thumbnail preview of last recorded video
- Automatic focus for video recording

### UI Features
- Modern Material Design 3 interface
- Mode selection with intuitive icons
- Recording indicator with timer
- Responsive layout with proper aspect ratio handling
- Edge-to-edge design

## Technical Details

### Architecture
- **UI**: Jetpack Compose with Material 3
- **Camera**: Camera2 API
- **Video**: MediaRecorder
- **State Management**: Kotlin Flow with StateFlow
- **Architecture**: MVVM with ViewModel

### Permissions Required
- `CAMERA`: For camera access
- `RECORD_AUDIO`: For video recording
- `READ_MEDIA_IMAGES`: For photo gallery access
- `READ_MEDIA_VIDEO`: For video gallery access

### Camera Capabilities
- Automatic focus (continuous for photos, continuous for videos)
- Flash control (torch mode for photos)
- Multiple camera support (front/back)
- High-quality image capture with EXIF data
- Professional video recording settings

## Usage

1. **Switching Modes**: Tap the mode selector at the top to switch between Photo and Video modes
2. **Taking Photos**: In Photo mode, tap the white capture button
3. **Recording Videos**: In Video mode, tap the red record button to start/stop recording
4. **Changing Aspect Ratio**: Tap the aspect ratio button next to the mode selector
5. **Flash Control**: In Photo mode, tap the flash icon in the top-right corner
6. **Switching Cameras**: Tap the camera switch icon in the bottom-right corner
7. **Viewing Media**: Tap the thumbnail to view the last captured photo or video

## Development

### Building
```bash
./gradlew assembleDebug
```

### Key Files
- `CameraViewModel.kt`: Main camera logic and state management
- `CameraScreen.kt`: UI components and layout
- `MainActivity.kt`: Permission handling and app lifecycle
- `AndroidManifest.xml`: Permissions and app configuration

### Dependencies
- Jetpack Compose
- Camera2 API
- MediaRecorder
- Coil for image loading
- ExifInterface for image metadata
