package com.itachitech.notifier

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telephony.TelephonyManager
import com.google.android.gms.nearby.connection.Payload

class CallReceiver : BroadcastReceiver() {

    // Simple state tracking for calls
    private var isRinging = false
    private var incomingNumber: String? = null

    override fun onReceive(context: Context, intent: Intent) {
        val nearbyConnectionsManager = NearbyConnectionsManager.getInstance(context)

        when (intent.action) {
            TelephonyManager.ACTION_PHONE_STATE_CHANGED -> {
                val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
                when (state) {
                    TelephonyManager.EXTRA_STATE_RINGING -> {
                        isRinging = true
                        incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
                        if (incomingNumber != null) {
                            val message = "call:$incomingNumber"
                            val payload = Payload.fromBytes(message.toByteArray(Charsets.UTF_8))
                            nearbyConnectionsManager.sendPayload(payload)
                        }
                    }
                    TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                        // Call was answered, dismiss the notification
                        isRinging = false
                        val message = "action:dismiss"
                        val payload = Payload.fromBytes(message.toByteArray(Charsets.UTF_8))
                        nearbyConnectionsManager.sendPayload(payload)
                    }
                    TelephonyManager.EXTRA_STATE_IDLE -> {
                        if (isRinging) {
                            // Phone was ringing and now it's idle, so it's a missed call
                            val missedCallNumber = incomingNumber
                            if (missedCallNumber != null) {
                                val message = "action:missed_call:$missedCallNumber"
                                val payload = Payload.fromBytes(message.toByteArray(Charsets.UTF_8))
                                nearbyConnectionsManager.sendPayload(payload)
                            }
                        }
                        isRinging = false
                        incomingNumber = null
                    }
                }
            }
            Telephony.Sms.Intents.SMS_RECEIVED_ACTION -> {
                val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
                for (smsMessage in messages) {
                    val message = "sms:${smsMessage.displayOriginatingAddress}:${smsMessage.messageBody}"
                    val payload = Payload.fromBytes(message.toByteArray(Charsets.UTF_8))
                    nearbyConnectionsManager.sendPayload(payload)
                }
            }
        }
    }
}
