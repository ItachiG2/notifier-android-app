# Notifier: A Cross-Device Notification Mirroring App

Notifier is a powerful yet lightweight Android application designed to mirror call and SMS notifications from one device to another. It's built with modern Android development practices and is optimized for reliability and low battery consumption.

## Features

- **Real-time Notification Mirroring:** Instantly mirrors incoming phone calls and SMS messages to a connected device.
- **Background Operation:** Runs as a persistent foreground service, ensuring it works reliably even when the app is closed.
- **Contact Resolution:** Looks up incoming numbers in the device's contacts to display caller and sender names.
- **Rich, Full-Screen Notifications:** Displays incoming calls as a full-screen notification on the lock screen, mimicking the native call experience.
- **Dynamic Status Updates:** Provides real-time feedback on the connection status, both in-app and via a persistent notification.
- **Missed Call Handling:** Correctly handles missed calls, dismissing the incoming call UI and posting a persistent "Missed call" notification.
- **Intelligent Troubleshooting:** Proactively detects connection issues and guides users to a dedicated Help screen with step-by-step, brand-specific instructions and shortcuts to the correct settings.

## Architecture

The app follows a modern, service-oriented architecture designed for background reliability.

- **`MainActivity.kt`:** The single entry point for the UI. Its primary responsibilities are requesting permissions and binding to the `NotifierService`.
- **`NotifierService.kt`:** The heart of the application. This foreground service is responsible for:
    - Managing the `NearbyConnectionsManager`.
    - Listening for incoming payloads (calls/SMS).
    - Performing contact lookups.
    - Displaying and managing all system notifications.
- **`NearbyConnectionsManager.kt`:** A robust, self-contained manager for handling all aspects of the Google Nearby Connections API, including advertising, discovery, and connection state.
- **`CallReceiver.kt`:** A programmatically registered `BroadcastReceiver` that efficiently listens for phone state changes and incoming SMS messages.
- **`NotificationHelper.kt`:** A dedicated helper class for creating and managing all system notifications, including the high-priority, full-screen incoming call notifications.
- **`SettingsHelper.kt`:** A utility to help users on restrictive operating systems find and enable the necessary background permissions.

## Manual Setup for Restrictive Operating Systems

Due to aggressive battery-saving measures on some Android devices (most notably **Xiaomi, Vivo, iQOO, Samsung, Oppo, OnePlus, and Realme**), you may need to manually grant the app special permissions for it to run reliably in the background.

**This app is designed to help you.** If it appears to be having trouble connecting for more than 30 seconds, a prompt will appear on the "Status" screen guiding you to the **Help** tab.

The **Help** screen provides a step-by-step guide with **brand-specific instructions** and buttons that will take you directly to the correct settings page for your device.

- The **"Open Battery Settings"** button uses the official Android API to ask the system to whitelist the app from battery optimizations. This is the most reliable method.
- The **"Open Autostart Settings"** button will attempt to open the manufacturer-specific screen for autostart permissions.

If a shortcut doesn't work, the app will automatically open the standard "App Info" screen, where you can manually adjust all necessary permissions.
