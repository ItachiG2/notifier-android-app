package com.itachitech.notifier

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.Telephony
import android.telephony.TelephonyManager
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.itachitech.notifier.ui.theme.NotifierTheme
import kotlinx.coroutines.delay
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.ui.graphics.Color

class MainActivity : ComponentActivity() {

    private var notifierService: NotifierService? by mutableStateOf(null)
    private var isBound by mutableStateOf(false)

    private val callReceiver = CallReceiver()

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as NotifierService.LocalBinder
            notifierService = binder.getService()
            isBound = true
            Log.d(TAG, "Service connected")
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            notifierService = null
            isBound = false
            Log.d(TAG, "Service disconnected")
        }
    }

    private val requestMultiplePermissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        if (permissions.all { it.value }) {
            Log.d(TAG, "All permissions granted, starting and binding service.")
            startAndBindService()
        } else {
            Log.w(TAG, "Not all permissions are granted. The app may not work correctly.")
            Toast.makeText(this, "Not all permissions are granted. The app may not work correctly.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val intentFilter = IntentFilter().apply {
            addAction(TelephonyManager.ACTION_PHONE_STATE_CHANGED)
            addAction(Telephony.Sms.Intents.SMS_RECEIVED_ACTION)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(callReceiver, intentFilter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(callReceiver, intentFilter)
        }

        enableEdgeToEdge()
        setContent {
            var hasCredentials by remember { mutableStateOf(CredentialsManager.hasCredentials(this)) }

            NotifierTheme {
                if (hasCredentials) {
                    val service = notifierService
                    if (isBound && service != null) {
                        val notificationMessage by service.notificationMessage.collectAsState()
                        val connectedDeviceNames by service.connectedDeviceNames.collectAsState()

                        NotifierApp(
                            notificationMessage = notificationMessage,
                            connectedDeviceNames = connectedDeviceNames,
                            onDestinationChanged = { destination ->
                                if (destination != AppDestinations.HOME) {
                                    service.clearLatestNotification()
                                }
                            },
                            onLogout = {
                                hasCredentials = false
                                if (isBound) {
                                    unbindService(serviceConnection)
                                    isBound = false
                                }
                                stopService(Intent(this, NotifierService::class.java))
                            }
                        )
                    } else {
                        Greeting(name = "Initializing...")
                    }
                } else {
                    SecurityScreen(onSave = {
                        hasCredentials = true
                        requestPermissionsAndStartService()
                    })
                }
            }
        }
    }

    private fun requestPermissionsAndStartService() {
        val permissionsToRequest = getRequiredPermissions()
        if (permissionsToRequest.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }) {
            startAndBindService()
        } else {
            requestMultiplePermissions.launch(permissionsToRequest)
        }
    }

    override fun onStart() {
        super.onStart()
        if (CredentialsManager.hasCredentials(this)) {
            requestPermissionsAndStartService()
        }
    }

    override fun onStop() {
        super.onStop()
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
            Log.d(TAG, "Unbinding service")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(callReceiver)
    }

    private fun startAndBindService() {
        if (!isBound && CredentialsManager.hasCredentials(this)) {
            val intent = Intent(this, NotifierService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }
    }

    private fun getRequiredPermissions(): Array<String> {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                arrayOf(
                    Manifest.permission.BLUETOOTH_ADVERTISE, Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.NEARBY_WIFI_DEVICES, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.READ_PHONE_STATE, Manifest.permission.READ_CALL_LOG, Manifest.permission.RECEIVE_SMS,
                    Manifest.permission.POST_NOTIFICATIONS, Manifest.permission.READ_CONTACTS
                )
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                arrayOf(
                    Manifest.permission.BLUETOOTH_ADVERTISE, Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.READ_PHONE_STATE, Manifest.permission.READ_CALL_LOG, Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_CONTACTS
                )
            }
            else -> {
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.READ_PHONE_STATE, Manifest.permission.READ_CALL_LOG, Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_CONTACTS
                )
            }
        }
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}

@Composable
fun NotifierApp(
    notificationMessage: String? = null,
    connectedDeviceNames: List<String> = emptyList(),
    onDestinationChanged: (AppDestinations) -> Unit = {},
    onLogout: () -> Unit
) {
    var currentDestination by rememberSaveable { mutableStateOf(AppDestinations.HOME) }

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            AppDestinations.entries.forEach { destination ->
                item(
                    icon = { Icon(destination.icon, contentDescription = destination.label) },
                    label = { Text(destination.label) },
                    selected = destination == currentDestination,
                    onClick = { currentDestination = destination; onDestinationChanged(destination) }
                )
            }
        }
    ) {
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            when (currentDestination) {
                AppDestinations.HOME -> {
                    if (notificationMessage != null) {
                        NotificationScreen(message = notificationMessage, modifier = Modifier.padding(innerPadding))
                    } else {
                        Greeting(name = "Waiting for a notification...", modifier = Modifier.padding(innerPadding))
                    }
                }
                AppDestinations.STATUS -> {
                    StatusScreen(connectedDeviceNames = connectedDeviceNames, modifier = Modifier.padding(innerPadding))
                }
                AppDestinations.HELP -> {
                    HelpScreen(modifier = Modifier.padding(innerPadding))
                }
                AppDestinations.SECURITY -> {
                    SecurityScreen(modifier = Modifier.padding(innerPadding), onLogout = onLogout)
                }
            }
        }
    }
}

@PreviewScreenSizes
@Composable
private fun NotifierAppPreview() {
    NotifierApp(onLogout = {})
}


enum class AppDestinations(
    val label: String, val icon: ImageVector
) {
    HOME("Home", Icons.Default.Home),
    STATUS("Status", Icons.Default.NetworkCheck),
    SECURITY("Security", Icons.Default.Security),
    HELP("Help", Icons.AutoMirrored.Filled.Help)
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = name)
    }
}

@Composable
fun NotificationScreen(message: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = message)
    }
}

@Composable
fun StatusScreen(connectedDeviceNames: List<String>, modifier: Modifier = Modifier) {
    var showHelpPrompt by remember { mutableStateOf(false) }

    val isConnected = connectedDeviceNames.isNotEmpty()

    LaunchedEffect(isConnected) {
        if (!isConnected) {
            delay(30000) // 30 seconds
            showHelpPrompt = true
        } else {
            showHelpPrompt = false
        }
    }

    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        val statusText = if (isConnected) {
            "Connected to: ${connectedDeviceNames.joinToString()}"
        } else {
            val context = LocalContext.current
            if (CredentialsManager.hasCredentials(context)) {
                "Not connected. Searching for devices..."
            } else {
                "Please set your credentials on the Security screen to begin."
            }
        }
        Text(text = statusText)

        if (showHelpPrompt && !isConnected) {
            Spacer(modifier = Modifier.height(16.dp))
            Text("Having trouble connecting? Check the Help screen for tips.")
        }
    }
}

@Composable
fun HelpScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val brand = Build.BRAND.lowercase()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Troubleshooting Steps", style = MaterialTheme.typography.headlineMedium)
        Text(
            text = "Some phone brands require special permissions for this app to run reliably in the background. Follow these steps to ensure Notifier works correctly on your device.",
            textAlign = TextAlign.Center
        )

        TroubleshootingStep(
            stepNumber = 1,
            title = "Autostart Settings",
            description = when {
                brand == "vivo" || brand == "iqoo" -> "Find Notifier in the list and enable Allow background usage."
                brand == "xiaomi" -> "Find Notifier in the list and enable Background autostart."
                brand == "samsung" -> "Samsung devices usually don\\'t have a specific Autostart setting. Please check the Battery Settings instead."
                else -> "Find a setting for apps that start automatically and ensure Notifier is enabled."
            },
            buttonText = "Open Autostart Settings",
            onClick = { SettingsHelper.openAutostartSettings(context) }
        )

        TroubleshootingStep(
            stepNumber = 2,
            title = "Battery Settings",
            description = when {
                brand == "vivo" || brand == "iqoo" -> "Enable background usage."
                brand == "xiaomi" -> "Find Notifier and set the Battery saver to 'No restrictions'."
                brand == "samsung" -> "Go to 'Background usage limits' and ensure Notifier is not in the 'Sleeping apps' or 'Deep sleeping apps' list."
                else -> "Find your phone\\'s battery optimization settings and exempt Notifier from optimization."
            },
            buttonText = "Open Battery Settings",
            onClick = { SettingsHelper.openBatterySettings(context) }
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            TroubleshootingStep(
                stepNumber = 3,
                title = "Full-Screen Notification Permission",
                description = "On Android 14 and newer, Notifier needs special permission to show full-screen notifications for incoming calls.",
                buttonText = "Open Full-Screen Settings",
                onClick = { SettingsHelper.openFullScreenIntentSettings(context) }
            )
        }

        Text(
            text = "Note: If the shortcuts don\\'t work, the app will try to open the main App Info screen where you can adjust permissions manually.",
            style = MaterialTheme.typography.bodySmall,
            fontStyle = FontStyle.Italic
        )
    }
}

@Composable
fun SecurityScreen(
    modifier: Modifier = Modifier,
    onSave: () -> Unit = {},
    onLogout: () -> Unit = {}
) {
    val context = LocalContext.current
    var hasCredentials by remember { mutableStateOf(CredentialsManager.hasCredentials(context)) }

    // High-contrast colors for text inputs
    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = Color.Black,
        unfocusedTextColor = Color.Black,
        focusedContainerColor = Color(0xFFF0F0F0), // Light gray box so it's visible
        unfocusedContainerColor = Color(0xFFF0F0F0),
        focusedBorderColor = Color.Black,
        unfocusedBorderColor = Color.Gray,
        cursorColor = Color.Black,
        focusedLabelColor = Color.Black,
        unfocusedLabelColor = Color.DarkGray
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (hasCredentials) {
            val username = CredentialsManager.getUsername(context) ?: ""
            Text(
                text = "Logged in as: $username",
                style = MaterialTheme.typography.headlineSmall,
                color = Color.Black
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = {
                    CredentialsManager.logout(context)
                    hasCredentials = false
                    onLogout()
                    Toast.makeText(context, "Logged out.", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Black)
            ) {
                Text("Logout", color = Color.White)
            }
        } else {
            var username by rememberSaveable { mutableStateOf("") }
            var pin by rememberSaveable { mutableStateOf("") }

            Text(
                text = "Set Your Credentials",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.Black
            )

            Spacer(modifier = Modifier.height(32.dp))

            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Username") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = textFieldColors
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = pin,
                onValueChange = { pin = it },
                label = { Text("PIN") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                colors = textFieldColors
            )

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = {
                    if (username.isNotBlank() && pin.isNotBlank()) {
                        CredentialsManager.saveCredentials(context, username, pin)
                        Toast.makeText(context, "Credentials saved!", Toast.LENGTH_SHORT).show()
                        onSave()
                    } else {
                        Toast.makeText(context, "Fields cannot be empty.", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Black)
            ) {
                Text("Save Credentials", color = Color.White)
            }
        }
    }
}

@Composable
fun TroubleshootingStep(
    stepNumber: Int,
    title: String,
    description: String,
    buttonText: String,
    onClick: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "$stepNumber. $title", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = description, style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onClick, modifier = Modifier.align(Alignment.End)) {
                Text(buttonText)
            }
        }
    }
}


@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    NotifierTheme { Greeting("Android") }
}

@Preview(showBackground = true)
@Composable
fun IncomingCallPreview() {
    NotifierTheme { NotificationScreen(message = "Incoming call from: 123-456-7890") }
}

@Preview(showBackground = true)
@Composable
fun StatusConnectedPreview() {
    NotifierTheme { StatusScreen(connectedDeviceNames = listOf("Pixel 8", "Galaxy S23")) }
}

@Preview(showBackground = true)
@Composable
fun StatusDisconnectedPreview() {
    NotifierTheme { StatusScreen(connectedDeviceNames = emptyList()) }
}

@Preview(showBackground = true)
@Composable
fun HelpScreenPreview() {
    NotifierTheme { HelpScreen() }
}

@Preview(showBackground = true)
@Composable
fun SecurityScreenPreview() {
    NotifierTheme { SecurityScreen() }
}