# Notifier: A Cross-Device Notification Mirroring App

Notifier is a powerful yet lightweight Android application designed to mirror call and SMS notifications securely between your devices. It's built with modern Android development practices and is optimized for reliability and low battery consumption.

## Features

- **Secure, Credential-Based Connections:** Devices can only discover and connect to each other if they share the exact same username and PIN, ensuring your notifications are kept private.
- **Real-time Notification Mirroring:** Instantly mirrors incoming phone calls and SMS messages to a connected device.
- **Resilient Background Operation:** Runs as a persistent foreground service with a `WakeLock` to ensure a stable, long-lasting connection.
- **Contact Resolution:** Looks up incoming numbers in the device's contacts to display caller and sender names.
- **Rich, Full-Screen Notifications:** Displays incoming calls as a full-screen notification on the lock screen, compliant with modern Android 14+ permissions.
- **Dynamic Status Updates:** Provides real-time feedback on the connection status, both in-app and via a state-aware persistent notification.
- **Intelligent Troubleshooting:** Proactively detects connection issues and guides users to a dedicated Help screen with a step-by-step, brand-specific guide and direct shortcuts to system settings.

## Getting Started

For the app to function, you must first set a matching username and PIN on **both** of your devices.

1.  Navigate to the **Security** tab.
2.  Enter a username and a PIN that you will use on both devices.
3.  Click **Save**.

Once credentials are saved, the app will automatically begin searching for another device that is using the same username and PIN.

## Architecture

The app follows a modern, service-oriented architecture designed for background reliability and security.

- **`MainActivity.kt`:** The single entry point for the UI. It enforces the credential-first workflow and is responsible for requesting permissions and binding to the `NotifierService`.
- **`CredentialsManager.kt`:** A helper object for securely saving, loading, and hashing the user's credentials. It generates a unique service ID from the username and PIN.
- **`NotifierService.kt`:** The heart of the application. This foreground service uses the secure service ID to manage the connection, listen for payloads, perform contact lookups, and display all system notifications.
- **`NearbyConnectionsManager.kt`:** A robust, self-contained manager for handling all aspects of the Google Nearby Connections API, using a dynamic, secure service ID.
- **`CallReceiver.kt`:** A programmatically registered `BroadcastReceiver` that efficiently listens for phone state changes and incoming SMS messages.
- **`NotificationHelper.kt`:** A dedicated helper class for creating and managing all system notifications.
- **`SettingsHelper.kt`:** A powerful utility to help users on restrictive operating systems find and enable the necessary background permissions.

## Manual Setup for Restrictive Operating Systems

Due to aggressive battery-saving measures on some Android devices (most notably **Xiaomi, Vivo, iQOO, Samsung, Oppo, OnePlus, and Realme**), you may need to manually grant the app special permissions for it to run reliably in the background.

**This app is designed to help you.** If it appears to be having trouble connecting for more than 30 seconds, a prompt will appear on the "Status" screen guiding you to the **Help** tab.

The **Help** screen provides a step-by-step guide with brand-specific instructions and buttons that will take you directly to the correct settings page for your device.
