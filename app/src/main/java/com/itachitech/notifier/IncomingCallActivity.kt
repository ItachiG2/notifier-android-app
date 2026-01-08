package com.itachitech.notifier

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.itachitech.notifier.ui.theme.NotifierTheme

class IncomingCallActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val message = intent.getStringExtra("message") ?: "Incoming notification"

        setContent {
            NotifierTheme {
                IncomingCallScreen(message = message, onDismiss = {
                    // Send an intent to the service to dismiss the notification
                    val dismissIntent = Intent(this, NotifierService::class.java).apply {
                        action = "ACTION_DISMISS_CALL"
                    }
                    startService(dismissIntent)

                    // Finish the activity
                    finish()
                })
            }
        }
    }
}

@Composable
fun IncomingCallScreen(message: String, onDismiss: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = message, style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(32.dp))
            Button(onClick = onDismiss) {
                Text(text = "Dismiss")
            }
        }
    }
}