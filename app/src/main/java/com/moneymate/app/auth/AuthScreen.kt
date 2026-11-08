package com.moneymate.app.auth

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val MIN_PASSWORD = 4

/**
 * Shows the login / register screen until the user is in, then [content].
 * [onAccountReplaced] runs on a background thread, before the new account is saved,
 * when "Create a new account" replaces an existing one; use it to wipe the old user's data.
 */
@Composable
fun AuthGate(onAccountReplaced: () -> Unit = {}, content: @Composable (onLogout: () -> Unit) -> Unit) {
    val context = LocalContext.current
    val store = remember { AuthStore(context.applicationContext) }
    var hasAccount by remember { mutableStateOf(store.hasAccount()) }
    var displayName by remember { mutableStateOf(store.displayName) }
    var loggedIn by rememberSaveable { mutableStateOf(false) }  // Survives rotation and system-restored process death; a cold start asks for login again
    if (loggedIn) {
        content { loggedIn = false }
    } else {
        AuthScreen(
            hasAccount = hasAccount,
            greetingName = displayName,
            onRegister = { name, password ->
                val replacing = hasAccount
                val ok = withContext(Dispatchers.Default) {
                    runCatching {
                        if (replacing) onAccountReplaced()
                        store.register(name, password)
                    }.isSuccess
                }
                if (ok) {
                    hasAccount = true
                    displayName = store.displayName
                    loggedIn = true
                }
                ok
            },
            onLogin = { password ->
                withContext(Dispatchers.Default) { store.verify(password) }.also { ok -> if (ok) loggedIn = true }
            }
        )
    }
}

@Composable
private fun AuthScreen(
    hasAccount: Boolean,
    greetingName: String,
    onRegister: suspend (String, String) -> Boolean,
    onLogin: suspend (String) -> Boolean
) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color(0xFF267552), background = Color(0xFFF7FAF8),
            surface = Color.White, onSurface = Color(0xFF17251F)
        )
    ) {
        var registering by rememberSaveable { mutableStateOf(!hasAccount) }
        var confirmReplace by rememberSaveable { mutableStateOf(false) }
        var name by rememberSaveable { mutableStateOf("") }
        // Passwords use plain remember so they never land in the saved-state Bundle
        var password by remember { mutableStateOf("") }
        var confirm by remember { mutableStateOf("") }
        var error by rememberSaveable { mutableStateOf<String?>(null) }
        var busy by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()

        fun switchMode(toRegister: Boolean) {
            registering = toRegister
            password = ""
            confirm = ""
            error = null
        }

        fun submit() {
            if (busy) return
            if (!registering) {
                if (password.isBlank()) { error = "Enter your password"; return }
                busy = true
                scope.launch {
                    if (!onLogin(password)) {
                        busy = false
                        password = ""
                        error = "Incorrect password"
                    }
                }
            } else {
                val problem = when {
                    name.isBlank() -> "Enter your name"
                    password.isBlank() -> "Enter a password"
                    password.length < MIN_PASSWORD -> "Password must be at least $MIN_PASSWORD characters"
                    password != confirm -> "Passwords don't match"
                    else -> null
                }
                if (problem != null) { error = problem; return }
                busy = true
                scope.launch {
                    if (!onRegister(name, password)) {
                        busy = false
                        error = "Couldn't create the account. Try again."
                    }
                }
            }
        }

        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                Modifier.fillMaxSize().padding(28.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("MoneyMate", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Text(
                    when {
                        registering && hasAccount -> "Create a new account"
                        registering -> "Create your account"
                        greetingName.isNotBlank() -> "Welcome back, $greetingName"
                        else -> "Welcome back"
                    },
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 4.dp, bottom = 24.dp)
                )

                if (registering) {
                    OutlinedTextField(
                        value = name, onValueChange = { name = it; error = null },
                        label = { Text("Name") }, singleLine = true, enabled = !busy,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(12.dp))
                }
                OutlinedTextField(
                    value = password, onValueChange = { password = it; error = null },
                    label = { Text("Password") }, singleLine = true, enabled = !busy,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = if (registering) ImeAction.Next else ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    modifier = Modifier.fillMaxWidth()
                )
                if (registering) {
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = confirm, onValueChange = { confirm = it; error = null },
                        label = { Text("Confirm password") }, singleLine = true, enabled = !busy,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { submit() }),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                error?.let {
                    Text(it, color = Color(0xFFC6434D), fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp))
                }
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = { submit() },
                    enabled = !busy && (registering || password.isNotBlank()),
                    modifier = Modifier.fillMaxWidth().height(50.dp)
                ) {
                    if (busy) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
                    } else {
                        Text(
                            when {
                                registering -> "Create account"
                                greetingName.isNotBlank() -> "Log in as $greetingName"
                                else -> "Log in"
                            }
                        )
                    }
                }
                if (hasAccount) {
                    Spacer(Modifier.height(8.dp))
                    if (registering) {
                        TextButton(onClick = { switchMode(false) }, enabled = !busy) { Text("Back to log in") }
                    } else {
                        OutlinedButton(
                            onClick = { confirmReplace = true }, enabled = !busy,
                            modifier = Modifier.fillMaxWidth().height(50.dp)
                        ) { Text("Create a new account") }
                    }
                }
            }
        }

        if (confirmReplace) {
            val who = if (greetingName.isNotBlank()) "$greetingName's" else "the saved"
            AlertDialog(
                onDismissRequest = { confirmReplace = false },
                title = { Text("Replace $who account?") },
                text = { Text("This removes the saved account and all its transactions on this device. It can't be undone.") },
                confirmButton = {
                    TextButton(onClick = { confirmReplace = false; switchMode(true) }) {
                        Text("Continue", color = Color(0xFFC6434D))
                    }
                },
                dismissButton = { TextButton(onClick = { confirmReplace = false }) { Text("Cancel") } }
            )
        }
    }
}
