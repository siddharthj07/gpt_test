# Secure Camera Vault (Android)

This repository now contains an Android Studio-friendly app that:

- Takes photos using CameraX.
- Encrypts every photo with a key derived from a user-provided passkey (PBKDF2 + AES/GCM).
- Stores only encrypted bytes in app-private internal storage.
- Lets users view photos only after entering the correct passkey inside the app.

## Open in Android Studio

1. Open the `SecureVaultCamera` folder in Android Studio.
2. Let Gradle sync.
3. Run on a device/emulator with camera support.

## Usage

1. Enter a passkey.
2. Tap **Capture & Encrypt** to take a photo.
3. Captured photos appear in the encrypted list.
4. Tap **View** next to a photo and enter the passkey to decrypt and preview in-app.

> Note: decrypted data is handled in memory for preview and not written to shared storage.
