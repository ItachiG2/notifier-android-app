package com.itachitech.notifier

import android.annotation.SuppressLint
import android.app.Service
import android.content.Intent
import android.net.Uri
import android.os.Binder
import android.os.IBinder
import android.provider.ContactsContract
import android.util.Log
import com.google.android.gms.nearby.connection.Payload
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * A long-running service that manages the Nearby Connections lifecycle and handles incoming notifications.
 */
class NotifierService : Service(), NearbyConnectionsManager.PayloadListener {

    /**
     * Binder for the UI to connect to this service.
     */
    inner class LocalBinder : Binder() {
        fun getService(): NotifierService = this@NotifierService
    }

    private val binder = LocalBinder()
    private lateinit var nearbyConnectionsManager: NearbyConnectionsManager
    private lateinit var notificationHelper: NotificationHelper
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    private val _notificationMessage = MutableStateFlow<String?>(null)
    /**
     * The latest notification message to be displayed in the UI.
     */
    val notificationMessage = _notificationMessage.asStateFlow()

    /**
     * A flow of the names of currently connected devices.
     */
    val connectedDeviceNames by lazy {
        nearbyConnectionsManager.connectedDeviceNames
    }

    override fun onCreate() {
        super.onCreate()
        nearbyConnectionsManager = NearbyConnectionsManager.getInstance(this)
        nearbyConnectionsManager.setPayloadListener(this)
        notificationHelper = NotificationHelper(this)
        notificationHelper.createServiceChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "ACTION_DISMISS_CALL") {
            notificationHelper.cancelIncomingCallNotification()
            return START_STICKY
        }

        startForeground(NotificationHelper.SERVICE_NOTIFICATION_ID, notificationHelper.getServiceNotification("Searching for devices..."))

        observeConnectionStatus()

        Log.d(TAG, "Service starting discovery and advertising")
        nearbyConnectionsManager.startAdvertising()
        nearbyConnectionsManager.startDiscovery()

        return START_STICKY
    }

    /**
     * Observes the connection status and updates the persistent service notification.
     */
    private fun observeConnectionStatus() {
        serviceScope.launch {
            connectedDeviceNames.collect { names ->
                val contentText = if (names.isNotEmpty()) {
                    "Connected to: ${names.joinToString()}"
                } else {
                    "Not connected. Searching for devices..."
                }
                startForeground(NotificationHelper.SERVICE_NOTIFICATION_ID, notificationHelper.getServiceNotification(contentText))
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onPayloadReceived(endpointId: String, payload: Payload) {
        if (payload.type == Payload.Type.BYTES) {
            val receivedBytes = payload.asBytes() ?: return
            val rawMessage = String(receivedBytes, Charsets.UTF_8)
            Log.d(TAG, "Payload received: $rawMessage")

            val parts = rawMessage.split(":", limit = 3)
            if (parts.isEmpty()) return

            val type = parts[0]

            if (type == "action") {
                val action = parts.getOrNull(1)
                if (action == "dismiss") {
                    notificationHelper.cancelIncomingCallNotification()
                    clearLatestNotification()
                } else if (action == "missed_call") {
                    notificationHelper.cancelIncomingCallNotification()
                    val number = parts.getOrNull(2)
                    if (number != null) {
                        val contactName = getContactDisplayName(number)
                        val missedCallMessage = if (contactName != null) {
                            "Missed call from: $contactName ($number)"
                        } else {
                            "Missed call from: $number"
                        }
                        _notificationMessage.value = missedCallMessage
                        notificationHelper.showMissedCallNotification(missedCallMessage)
                    }
                }
                return
            }

            val content = parts.getOrElse(1) { "" }

            val finalMessage = when (type) {
                "call" -> {
                    val contactName = getContactDisplayName(content)
                    if (contactName != null) {
                        "Incoming call from: $contactName ($content)"
                    } else {
                        "Incoming call from: $content"
                    }
                }
                "sms" -> {
                    val smsParts = content.split(":", limit = 2)
                    val sender = smsParts.getOrNull(0) ?: "Unknown"
                    val body = smsParts.getOrNull(1) ?: ""
                    val contactName = getContactDisplayName(sender)
                    if (contactName != null) {
                        "SMS from: $contactName ($sender)\n$body"
                    } else {
                        "SMS from: $sender\n$body"
                    }
                }
                else -> rawMessage
            }

            _notificationMessage.value = finalMessage
            notificationHelper.showIncomingCallNotification(finalMessage)
        }
    }

    @SuppressLint("Range")
    private fun getContactDisplayName(number: String): String? {
        if (number.isBlank()) return null

        val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
        val projection = arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME)
        var displayName: String? = null

        try {
            val cursor = contentResolver.query(uri, projection, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    displayName = it.getString(it.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error looking up contact", e)
            return null
        }
        return displayName
    }

    /**
     * Clears the latest notification message, hiding it from the UI.
     */
    fun clearLatestNotification() {
        _notificationMessage.value = null
    }

    companion object {
        private const val TAG = "NotifierService"
    }
}