package com.itachitech.notifier

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
        val message = intent.getStringExtra("message") ?: "Incoming call"

        setContent {
            NotifierTheme {
                IncomingCallScreen(message = message, onDismiss = { finish() })
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
            Row {
                Button(onClick = { /* TODO: Implement answer functionality */ }) {
                    Text(text = "Answer")
                }
                Spacer(modifier = Modifier.width(16.dp))
                Button(onClick = onDismiss) {
                    Text(text = "Dismiss")
                }
            }
        }
    }
}