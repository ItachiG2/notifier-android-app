package com.itachitech.notifier

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import kotlin.io.path.name
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class NearbyConnectionsManager private constructor(private val context: Context) {

    private val connectionsClient: ConnectionsClient by lazy { Nearby.getConnectionsClient(context) }
    private var localEndpointName: String = Build.MODEL
    private var serviceId: String = ""

    private val discoveredEndpoints = mutableMapOf<String, String>()
    private val connectedEndpoints = mutableMapOf<String, String>()
    private var payloadListener: PayloadListener? = null

    private val _connectedDeviceNames = MutableStateFlow<List<String>>(emptyList())
    val connectedDeviceNames = _connectedDeviceNames.asStateFlow()

    fun setPayloadListener(listener: PayloadListener) {
        payloadListener = listener
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            Log.d(TAG, "onPayloadReceived: from $endpointId")
            payloadListener?.onPayloadReceived(endpointId, payload)
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {}
    }

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, connectionInfo: ConnectionInfo) {
            Log.d(TAG, "onConnectionInitiated: accepting connection from ${connectionInfo.endpointName} (id: $endpointId)")
            if (!discoveredEndpoints.containsKey(endpointId)) {
                discoveredEndpoints[endpointId] = connectionInfo.endpointName
            }
            connectionsClient.acceptConnection(endpointId, payloadCallback)
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            val endpointName = discoveredEndpoints[endpointId] ?: "Unknown Device"

            when (result.status.statusCode) {
                ConnectionsStatusCodes.STATUS_OK, ConnectionsStatusCodes.STATUS_ALREADY_CONNECTED_TO_ENDPOINT -> {
                    Log.d(TAG, "onConnectionResult: success or already connected for $endpointName (id: $endpointId)")
                    if (!connectedEndpoints.containsKey(endpointId)) {
                        connectedEndpoints[endpointId] = endpointName
                        updateConnectedDevices()
                    }
                }
                else -> {
                    Log.e(TAG, "onConnectionResult: failure for $endpointName (id: $endpointId), status code: ${result.status.statusCode}")
                    discoveredEndpoints.remove(endpointId)
                    if (connectedEndpoints.isEmpty() && serviceId.isNotBlank()) {
                        startAdvertising(serviceId)
                        startDiscovery(serviceId)
                    }
                }
            }
        }

        override fun onDisconnected(endpointId: String) {
            val removedDevice = connectedEndpoints.remove(endpointId)
            Log.d(TAG, "onDisconnected: from ${removedDevice ?: endpointId}")
            updateConnectedDevices()
            // Only restart advertising/discovery if we are fully disconnected AND we have a valid serviceId (i.e., we are not in a logged-out state)
            if (connectedEndpoints.isEmpty() && serviceId.isNotBlank()) {
                startAdvertising(serviceId)
                startDiscovery(serviceId)
            }
        }
    }

    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, discoveredEndpointInfo: DiscoveredEndpointInfo) {
            if (connectedEndpoints.isNotEmpty()) {
                Log.d(TAG, "Already connected, ignoring new endpoint ${discoveredEndpointInfo.endpointName}")
                return
            }

            Log.d(TAG, "onEndpointFound: endpoint ${discoveredEndpointInfo.endpointName} (id: $endpointId) found, requesting connection")
            discoveredEndpoints[endpointId] = discoveredEndpointInfo.endpointName
            connectionsClient.requestConnection(localEndpointName, endpointId, connectionLifecycleCallback)
                .addOnFailureListener { e ->
                    Log.w(TAG, "requestConnection failed for endpoint $endpointId", e)
                    if (connectedEndpoints.isEmpty()) {
                        startDiscovery(serviceId)
                    }
                }
        }

        override fun onEndpointLost(endpointId: String) {
            Log.d(TAG, "onEndpointLost: endpoint $endpointId lost")
        }
    }

    private fun updateConnectedDevices() {
        _connectedDeviceNames.value = connectedEndpoints.values.toList()
    }

    private fun getBluetoothName(): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "BLUETOOTH_CONNECT permission not granted. Falling back to device model.")
            return Build.MODEL
        }
        return try {
            val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            val adapterName = bluetoothManager?.adapter?.name
            if (adapterName.isNullOrBlank()) Build.MODEL else adapterName
        } catch (e: SecurityException) {
            Log.e(TAG, "Failed to get Bluetooth name due to SecurityException, falling back to device model.", e)
            Build.MODEL
        }
    }

    fun startAdvertising(serviceId: String) {
        this.serviceId = serviceId
        localEndpointName = getBluetoothName()
        connectionsClient.stopAdvertising() // Defensive stop
        val advertisingOptions = AdvertisingOptions.Builder()
            .setStrategy(Strategy.P2P_CLUSTER)
            .setLowPower(true)
            .build()
        connectionsClient.startAdvertising(
            localEndpointName, serviceId, connectionLifecycleCallback, advertisingOptions
        ).addOnSuccessListener { Log.d(TAG, "startAdvertising: success with name $localEndpointName") }
            .addOnFailureListener { e -> Log.e(TAG, "startAdvertising: failure", e) }
    }

    fun startDiscovery(serviceId: String) {
        this.serviceId = serviceId
        connectionsClient.stopDiscovery() // Defensive stop
        val discoveryOptions = DiscoveryOptions.Builder()
            .setStrategy(Strategy.P2P_CLUSTER)
            .setLowPower(true)
            .build()
        connectionsClient.startDiscovery(
            serviceId, endpointDiscoveryCallback, discoveryOptions
        ).addOnSuccessListener { Log.d(TAG, "startDiscovery: success") }
            .addOnFailureListener { e -> Log.e(TAG, "startDiscovery: failure", e) }
    }

    fun sendPayload(payload: Payload) {
        if (connectedEndpoints.isEmpty()) {
            Log.w(TAG, "sendPayload: No connected endpoints to send to.")
        }
        connectionsClient.sendPayload(connectedEndpoints.keys.toList(), payload)
    }

    fun stopAll() {
        Log.d(TAG, "Stopping all endpoints, advertising, and discovery.")
        serviceId = "" // Invalidate the service ID to prevent automatic restarts
        connectionsClient.stopAllEndpoints()
        connectionsClient.stopAdvertising()
        connectionsClient.stopDiscovery()
        connectedEndpoints.clear()
        discoveredEndpoints.clear()
        updateConnectedDevices()
    }

    interface PayloadListener {
        fun onPayloadReceived(endpointId: String, payload: Payload)
    }

    companion object {
        private const val TAG = "NearbyConnectionsMgr"

        @SuppressLint("StaticFieldLeak")
        @Volatile
        private var INSTANCE: NearbyConnectionsManager? = null

        fun getInstance(context: Context): NearbyConnectionsManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: NearbyConnectionsManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}