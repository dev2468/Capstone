package com.devc010.mcpapp

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel()
) {
    val tailscaleIp by viewModel.tailscaleIp.collectAsState()
    val groqApiKey by viewModel.groqApiKey.collectAsState()
    val zaiApiKey by viewModel.zaiApiKey.collectAsState()
    val connectionStatus by viewModel.connectionStatus.collectAsState()
    val devmcpApiKey by viewModel.devmcpApiKey.collectAsState()

    var groqPasswordVisible by remember { mutableStateOf(false) }
    var zaiKeyVisible by remember { mutableStateOf(false) }
    var devmcpKeyVisible by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(connectionStatus) {
        when (connectionStatus) {
            ConnectionStatus.SUCCESS -> {
                snackbarHostState.showSnackbar("Connection successful! {\"status\": \"online\"}")
                viewModel.resetConnectionStatus()
            }
            ConnectionStatus.ERROR -> {
                snackbarHostState.showSnackbar("Connection failed or timed out.")
                viewModel.resetConnectionStatus()
            }
            else -> {}
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Settings", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0A0A0A)
                )
            )
        },
        containerColor = Color(0xFF0A0A0A)
    ) { paddingValues: PaddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.Start
        ) {
            val textFieldColors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedContainerColor = Color(0xFF1E1E1E),
                unfocusedContainerColor = Color(0xFF1E1E1E),
                focusedBorderColor = Color(0xFF4285F4),
                unfocusedBorderColor = Color.DarkGray,
                focusedLabelColor = Color(0xFF4285F4),
                unfocusedLabelColor = Color.LightGray,
                cursorColor = Color(0xFF4285F4)
            )

            // Tailscale IP Input
            OutlinedTextField(
                value = tailscaleIp,
                onValueChange = { newValue: String -> viewModel.updateTailscaleIp(newValue) },
                label = { Text("Tailscale IP") },
                placeholder = { Text("e.g. 100.114.238.70", color = Color.Gray) },
                modifier = Modifier.fillMaxWidth(),
                colors = textFieldColors,
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Groq API Key Input
            OutlinedTextField(
                value = groqApiKey,
                onValueChange = { newValue: String -> viewModel.updateGroqApiKey(newValue) },
                label = { Text("Groq API Key") },
                placeholder = { Text("Enter your Groq API Key", color = Color.Gray) },
                modifier = Modifier.fillMaxWidth(),
                colors = textFieldColors,
                singleLine = true,
                visualTransformation = if (groqPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    val image = if (groqPasswordVisible)
                        Icons.Filled.Visibility
                    else Icons.Filled.VisibilityOff
                    val description = if (groqPasswordVisible) "Hide password" else "Show password"

                    IconButton(onClick = { groqPasswordVisible = !groqPasswordVisible }) {
                        Icon(imageVector = image, contentDescription = description, tint = Color.LightGray)
                    }
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // ZAI API Key Input
            OutlinedTextField(
                value = zaiApiKey,
                onValueChange = { newValue: String -> viewModel.updateZaiApiKey(newValue) },
                label = { Text("ZAI API Key (optional)") },
                placeholder = { Text("Enter your Z.AI API Key", color = Color.Gray) },
                modifier = Modifier.fillMaxWidth(),
                colors = textFieldColors,
                singleLine = true,
                visualTransformation = if (zaiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    val image = if (zaiKeyVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                    val description = if (zaiKeyVisible) "Hide" else "Show"
                    IconButton(onClick = { zaiKeyVisible = !zaiKeyVisible }) {
                        Icon(imageVector = image, contentDescription = description, tint = Color.LightGray)
                    }
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // DevMCP API Key Input
            OutlinedTextField(
                value = devmcpApiKey,
                onValueChange = { viewModel.updateDevmcpApiKey(it) },
                label = { Text("DevMCP API Key") },
                placeholder = { Text("Shared secret for the MCP server", color = Color.Gray) },
                modifier = Modifier.fillMaxWidth(),
                colors = textFieldColors,
                singleLine = true,
                visualTransformation = if (devmcpKeyVisible) VisualTransformation.None
                                       else PasswordVisualTransformation(),
                trailingIcon = {
                    val image = if (devmcpKeyVisible)
                        Icons.Filled.Visibility
                    else Icons.Filled.VisibilityOff
                    val description = if (devmcpKeyVisible) "Hide" else "Show"
                    IconButton(onClick = { devmcpKeyVisible = !devmcpKeyVisible }) {
                        Icon(imageVector = image, contentDescription = description,
                             tint = Color.LightGray)
                    }
                }
            )

            Spacer(modifier = Modifier.weight(1f))

            // Test Connection Button
            Button(
                onClick = { viewModel.testConnection() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF4285F4),
                    contentColor = Color.White,
                    disabledContainerColor = Color(0xFF1E1E1E)
                ),
                enabled = connectionStatus != ConnectionStatus.LOADING
            ) {
                if (connectionStatus == ConnectionStatus.LOADING) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                } else {
                    Text("Test Connection", fontWeight = FontWeight.Bold)
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
