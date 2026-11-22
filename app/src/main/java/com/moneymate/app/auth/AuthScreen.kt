package com.moneymate.app.auth

import android.util.Patterns
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moneymate.app.BuildConfig
import com.moneymate.app.settings.MoneyMateTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val MIN_PASSWORD = 4
private const val RESEND_SECONDS = 60
private val CODE_LENGTH = 6..10

private val skipEmailCodes = BuildConfig.DEBUG &&
    (BuildConfig.SUPABASE_URL.isBlank() || BuildConfig.SUPABASE_ANON_KEY.isBlank())
@Composable
fun AuthGate(
    onAccountReplaced: () -> Unit = {},
    otp: EmailOtpService = remember { SupabaseEmailOtp() },
    content: @Composable (onLogout: () -> Unit) -> Unit
) {
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
            accountEmail = store.email, // [ADDED] checked at login
            loginCodeEmail = store.email.takeIf { store.emailCodeAtLogin && !skipEmailCodes }, // [CHANGED]
            otp = otp,
            onRegister = { name, email, password ->
                val replacing = hasAccount
                val ok = withContext(Dispatchers.Default) {
                    runCatching {
                        if (replacing) onAccountReplaced()
                        store.register(name, email, password)
                    }.isSuccess
                }
                if (ok) {
                    hasAccount = true
                    displayName = store.displayName
                    loggedIn = true
                }
                ok
            },
            onCheckPassword = { password -> withContext(Dispatchers.Default) { store.verify(password) } },
            onLoggedIn = { loggedIn = true }
        )
    }
}

@Composable
private fun AuthScreen(
    hasAccount: Boolean,
    greetingName: String,
    accountEmail: String,
    loginCodeEmail: String?,
    otp: EmailOtpService,
    onRegister: suspend (String, String, String) -> Boolean,
    onCheckPassword: suspend (String) -> Boolean,
    onLoggedIn: () -> Unit
) {
    val errorColor = MoneyMateTheme.colors.red
    var registering by rememberSaveable { mutableStateOf(false) } // [CHANGED] always opens on login
    var confirmReplace by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") } // [ADDED]
    // Passwords use plain remember so they never land in the saved-state Bundle
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var codeSentTo by remember { mutableStateOf<String?>(null) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun switchMode(toRegister: Boolean) {
        registering = toRegister
        password = ""
        confirm = ""
        error = null
    }

    suspend fun sendCode(to: String): Boolean {
        val problem = otp.sendCode(to)
        if (problem != null) { error = problem; return false }
        codeSentTo = to
        error = null
        return true
    }

    fun submit() {
        if (busy) return
        if (!registering) {
            if (!hasAccount) { error = "No account on this device yet. Tap Register below."; return } // [ADDED]
            if (accountEmail.isNotBlank() && email.isBlank()) { error = "Enter your email"; return } // [ADDED]
            if (password.isBlank()) { error = "Enter your password"; return }
            busy = true
            scope.launch {
                // [CHANGED] checks the email too; one message for both so it doesn't hint which was wrong
                val emailOk = accountEmail.isBlank() || email.trim().equals(accountEmail, ignoreCase = true)
                if (!onCheckPassword(password) || !emailOk) {
                    password = ""
                    error = if (accountEmail.isBlank()) "Incorrect password" else "Incorrect email or password"
                } else if (loginCodeEmail == null) {
                    onLoggedIn()
                } else {
                    sendCode(loginCodeEmail)
                }
                busy = false
            }
        } else {
            val problem = when {
                name.isBlank() -> "Enter your name"
                !Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches() -> "Enter a valid email" // [ADDED]
                password.isBlank() -> "Enter a password"
                password.length < MIN_PASSWORD -> "Password must be at least $MIN_PASSWORD characters"
                password != confirm -> "Passwords don't match"
                else -> null
            }
            if (problem != null) { error = problem; return }
            busy = true
            scope.launch {
                if (skipEmailCodes) {
                    if (!onRegister(name, email.trim(), password)) error = "Couldn't create the account. Try again."
                } else {
                    sendCode(email.trim())
                }
                busy = false
            }
        }
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        val sentTo = codeSentTo
        if (sentTo != null) {
            CodeStep(
                email = sentTo,
                registering = registering,
                otp = otp,
                onVerified = {
                    if (registering) {
                        if (!onRegister(name, sentTo, password)) {
                            codeSentTo = null
                            error = "Couldn't create the account. Try again."
                        }
                    } else {
                        onLoggedIn()
                    }
                },
                onBack = {
                    codeSentTo = null
                    if (!registering) password = ""
                }
            )
        } else {
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
                        !hasAccount -> "Log in to continue" // [ADDED]
                        greetingName.isNotBlank() -> "Welcome back, $greetingName"
                        else -> "Welcome back"
                    },
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 4.dp, bottom = 24.dp)
                )
                if (skipEmailCodes) {
                    Text(
                        "Testing mode: email codes are off until Supabase is set up",
                        color = MoneyMateTheme.colors.muted, fontSize = 12.sp, textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                }

                if (registering) {
                    OutlinedTextField(
                        value = name, onValueChange = { name = it; error = null },
                        label = { Text("Name") }, singleLine = true, enabled = !busy,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(12.dp))
                }
                // [CHANGED] email box shows on login too (hidden only for old accounts without an email)
                if (registering || !hasAccount || accountEmail.isNotBlank()) {
                    OutlinedTextField(
                        value = email, onValueChange = { email = it; error = null },
                        label = { Text("Email") }, singleLine = true, enabled = !busy,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
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
                    Text(it, color = errorColor, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 10.dp))
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
                                registering && skipEmailCodes -> "Create account"
                                registering -> "Send verification code"
                                greetingName.isNotBlank() -> "Log in as $greetingName"
                                else -> "Log in"
                            }
                        )
                    }
                }
                // [CHANGED] "No account? Create one!" + Register button under the login button
                Spacer(Modifier.height(8.dp))
                if (registering) {
                    TextButton(onClick = { switchMode(false) }, enabled = !busy) { Text("Back to log in") }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("No account? Create one!", color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp)
                        Spacer(Modifier.width(8.dp))
                        FilledTonalButton(
                            colors = ButtonDefaults.filledTonalButtonColors( // [CHANGED] app mint + green, not Material purple
                                containerColor = MoneyMateTheme.colors.mint,
                                contentColor = MoneyMateTheme.colors.green
                            ),
                            // Replacing a saved account still asks first, since it deletes its data
                            onClick = { if (hasAccount) confirmReplace = true else switchMode(true) },
                            enabled = !busy
                        ) { Text("Register", fontWeight = FontWeight.Bold) }
                    }
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
                    Text("Continue", color = errorColor)
                }
            },
            dismissButton = { TextButton(onClick = { confirmReplace = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun CodeStep(
    email: String,
    registering: Boolean,
    otp: EmailOtpService,
    onVerified: suspend () -> Unit,
    onBack: () -> Unit
) {
    val errorColor = MoneyMateTheme.colors.red
    var code by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var resendIn by remember { mutableIntStateOf(RESEND_SECONDS) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(resendIn > 0) {
        while (resendIn > 0) { delay(1_000); resendIn-- }
    }

    fun verify() {
        if (busy || code.length !in CODE_LENGTH) return
        busy = true
        error = null
        scope.launch {
            val problem = otp.verifyCode(email, code)
            if (problem == null) {
                onVerified()
            } else {
                error = problem
                code = ""
            }
            busy = false
        }
    }

    Column(
        Modifier.fillMaxSize().padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Check your email", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Text(
            "Enter the code we sent to $email",
            fontSize = 15.sp, textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 6.dp, bottom = 24.dp)
        )
        OutlinedTextField(
            value = code,
            onValueChange = { input -> code = input.filter(Char::isDigit).take(CODE_LENGTH.last); error = null },
            label = { Text("Verification code") }, singleLine = true, enabled = !busy,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { verify() }),
            textStyle = LocalTextStyle.current.copy(fontSize = 22.sp, letterSpacing = 6.sp, textAlign = TextAlign.Center),
            modifier = Modifier.fillMaxWidth()
        )
        error?.let { Text(it, color = errorColor, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 10.dp)) }
        notice?.let { Text(it, color = MoneyMateTheme.colors.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp)) }
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = { verify() },
            enabled = !busy && code.length in CODE_LENGTH,
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) {
            if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
            else Text(if (registering) "Verify and create account" else "Verify and log in")
        }
        Spacer(Modifier.height(8.dp))
        TextButton(
            enabled = !busy && resendIn == 0,
            onClick = {
                busy = true
                scope.launch {
                    val problem = otp.sendCode(email)
                    if (problem == null) {
                        notice = "A new code is on its way."
                        error = null
                        resendIn = RESEND_SECONDS
                    } else {
                        error = problem
                    }
                    busy = false
                }
            }
        ) { Text(if (resendIn > 0) "Resend code in ${resendIn}s" else "Resend code") }
        TextButton(onClick = onBack, enabled = !busy) { Text(if (registering) "Change email" else "Back to log in") }
    }
}