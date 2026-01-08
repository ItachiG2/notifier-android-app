package com.itachitech.notifier

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Binder
import android.os.IBinder
import android.os.PowerManager
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
    private var wakeLock: PowerManager.WakeLock? = null

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

    @SuppressLint("WakelockTimeout")
    override fun onCreate() {
        super.onCreate()
        nearbyConnectionsManager = NearbyConnectionsManager.getInstance(this)
        nearbyConnectionsManager.setPayloadListener(this)
        notificationHelper = NotificationHelper(this)
        notificationHelper.createServiceChannel()

        // Acquire a wake lock to keep the network hardware active
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Notifier::NetworkWakeLock")
        wakeLock?.acquire()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "ACTION_DISMISS_CALL") {
            notificationHelper.cancelIncomingCallNotification()
            return START_STICKY
        }

        startForeground(NotificationHelper.SERVICE_NOTIFICATION_ID, notificationHelper.getServiceNotification("Searching for devices...", R.drawable.baseline_sync_disabled_24))

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
                val isConnected = names.isNotEmpty()
                val contentText = if (isConnected) {
                    "Connected to: ${names.joinToString()}"
                } else {
                    "Not connected. Searching for devices..."
                }
                val iconResId = if (isConnected) R.drawable.baseline_sync_24 else R.drawable.baseline_sync_disabled_24

                startForeground(NotificationHelper.SERVICE_NOTIFICATION_ID, notificationHelper.getServiceNotification(contentText, iconResId))
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
        wakeLock?.release()
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
            var notificationTitle = ""
            val finalMessage = when (type) {
                "call" -> {
                    notificationTitle = "Incoming Call"
                    val contactName = getContactDisplayName(content)
                    if (contactName != null) {
                        "$contactName ($content)"
                    } else {
                        content
                    }
                }
                "sms" -> {
                    notificationTitle = "New Message"
                    val smsParts = content.split(":", limit = 2)
                    val sender = smsParts.getOrNull(0) ?: "Unknown"
                    val body = smsParts.getOrNull(1) ?: ""
                    val contactName = getContactDisplayName(sender)
                    if (contactName != null) {
                        "From: $contactName ($sender)\n$body"
                    } else {
                        "From: $sender\n$body"
                    }
                }
                else -> rawMessage
            }

            _notificationMessage.value = "$notificationTitle\n$finalMessage"
            notificationHelper.showIncomingCallNotification(notificationTitle, finalMessage)
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