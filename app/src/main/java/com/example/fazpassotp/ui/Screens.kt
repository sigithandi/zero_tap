package com.example.fazpassotp.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun AppContent(viewModel: MainViewModel = viewModel()) {
    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { viewModel.clear() }) {
                Text("CLEAR")
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(text = "Fazpass OTP Demo", style = MaterialTheme.typography.headlineMedium)

            when (viewModel.currentScreen) {
                ScreenState.PhoneInput -> PhoneInputScreen(viewModel)
                ScreenState.OtpInput -> OtpInputScreen(viewModel)
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (viewModel.isLoading) {
                CircularProgressIndicator()
            }

            // Status / Response Display
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f) // Takes remaining space
            ) {
                Column(modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
                ) {
                    Text(text = "Response / Status:", style = MaterialTheme.typography.titleSmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = viewModel.statusMessage)
                }
            }
            
            // Hash Display
             Text(
                text = "App Hash: ${viewModel.appHash}",
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray
            )
        }
    }
}

@Composable
fun PhoneInputScreen(viewModel: MainViewModel) {
    TextField(
        value = viewModel.phoneNumber,
        onValueChange = { viewModel.phoneNumber = it },
        label = { Text("Phone Number") },
        placeholder = { Text("e.g. 628123456789") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
    
    Button(
        onClick = { viewModel.requestOtp() },
        modifier = Modifier.fillMaxWidth(),
        enabled = !viewModel.isLoading
    ) {
        Text("Request OTP")
    }
}

@Composable
fun OtpInputScreen(viewModel: MainViewModel) {
    TextField(
        value = viewModel.otpInput,
        onValueChange = { viewModel.otpInput = it },
        label = { Text("Enter OTP") },
        placeholder = { Text("4 digit code") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
    
    Button(
        onClick = { viewModel.validateOtp() },
        modifier = Modifier.fillMaxWidth(),
        enabled = !viewModel.isLoading
    ) {
        Text("Validate")
    }
}
