package com.socialvault.app.ui

import android.widget.Toast
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.socialvault.app.data.VaultPrefs

/**
 * First-launch screen: set a 4-digit PIN (with confirm).
 * The PIN is never stored — only its SHA-256 hash. There is no recovery:
 * forgetting the PIN means the vault cannot be opened again.
 */
@Composable
fun PinSetupScreen(onDone: (String) -> Unit) {
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    val canSave = pin.length == 4 && confirm.length == 4

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "🔐 Set your PIN",
                style = TextStyle(
                    brush = TitleGradient,
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Black
                )
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "Set a 4-digit PIN to lock your vault. The app will ask for it every time it opens.",
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 16.sp
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "⚠️ PIN bhool gaye to data recover nahi hoga — PIN reset karne ka koi tareeqa nahi hai.",
                fontWeight = FontWeight.Black,
                color = Color(0xFFB00020),
                fontSize = 15.sp
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "👆 PIN set karne ke baad lock screen par fingerprint se unlock ka button bhi aayega — agar phone ki Settings mein fingerprint add hai to.",
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 15.sp
            )
            Spacer(Modifier.height(20.dp))
            OutlinedTextField(
                value = pin,
                onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) pin = it },
                label = { Text("4-digit PIN") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                visualTransformation = PasswordVisualTransformation()
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = confirm,
                onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) confirm = it },
                label = { Text("Confirm PIN") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                visualTransformation = PasswordVisualTransformation()
            )
            if (error.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text(error, color = Color(0xFFB00020), fontWeight = FontWeight.Black)
            }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    when {
                        pin.length != 4 -> error = "PIN must be 4 digits."
                        pin != confirm -> error = "PINs do not match. Try again."
                        else -> onDone(pin)
                    }
                },
                enabled = canSave,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(999.dp),
                colors = ButtonDefaults.buttonColors(containerColor = GreenStart)
            ) {
                Text("Set PIN & Open Vault", fontWeight = FontWeight.Black, fontSize = 18.sp)
            }
        }
    }
}

/**
 * Unlock screen shown on every cold start (and when the app returns from
 * the background). PIN entry, plus a fingerprint button when the device
 * supports biometric authentication.
 */
@Composable
fun UnlockScreen(
    prefs: VaultPrefs,
    activity: FragmentActivity,
    onUnlocked: () -> Unit
) {
    val context = LocalContext.current
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    // Fingerprint state, re-checked every time the lock screen comes back
    // into view (so enrolling a fingerprint in Settings while the app is
    // open is picked up without a reinstall). STRONG alone hid the button
    // on phones whose fingerprint counts as WEAK, and the old remember{}
    // check ran only once — both made the button silently vanish.
    var biometricStatus by remember {
        mutableStateOf(
            BiometricManager.from(context).canAuthenticate(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.BIOMETRIC_WEAK
            )
        )
    }
    val biometricAvailable =
        biometricStatus == BiometricManager.BIOMETRIC_SUCCESS
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                biometricStatus = BiometricManager.from(context).canAuthenticate(
                    BiometricManager.Authenticators.BIOMETRIC_STRONG or
                        BiometricManager.Authenticators.BIOMETRIC_WEAK
                )
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun tryBiometric() {
        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(
                    result: BiometricPrompt.AuthenticationResult
                ) {
                    onUnlocked()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON &&
                        errorCode != BiometricPrompt.ERROR_USER_CANCELED
                    ) {
                        Toast.makeText(context, errString, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock Vault")
            .setSubtitle("Use your fingerprint to open Social Accounts Vault")
            .setNegativeButtonText("Use PIN")
            .build()
        prompt.authenticate(info)
    }

    // Offer the fingerprint prompt as soon as the lock screen appears, so
    // the user can see fingerprint unlock is there instead of having to
    // hunt for the button. Once per lock-screen appearance; cancelling
    // falls back to PIN and does not loop.
    var biometricPrompted by remember { mutableStateOf(false) }
    LaunchedEffect(biometricAvailable) {
        if (biometricAvailable && !biometricPrompted) {
            biometricPrompted = true
            tryBiometric()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "🔐 Vault Locked",
                style = TextStyle(
                    brush = TitleGradient,
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Black
                )
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "Enter your 4-digit PIN to open the vault.",
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 16.sp
            )
            Spacer(Modifier.height(20.dp))
            OutlinedTextField(
                value = pin,
                onValueChange = {
                    if (it.length <= 4 && it.all { c -> c.isDigit() }) {
                        pin = it
                        error = ""
                        if (it.length == 4) {
                            if (prefs.checkPin(it)) {
                                onUnlocked()
                            } else {
                                error = "Wrong PIN. Try again."
                                pin = ""
                            }
                        }
                    }
                },
                label = { Text("PIN") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                visualTransformation = PasswordVisualTransformation()
            )
            if (error.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text(error, color = Color(0xFFB00020), fontWeight = FontWeight.Black)
            }
            if (biometricAvailable) {
                Spacer(Modifier.height(16.dp))
                OutlinedButton(
                    onClick = { tryBiometric() },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(999.dp)
                ) {
                    Text("👆 Unlock with Fingerprint", fontWeight = FontWeight.Black)
                }
            } else {
                // Never hide fingerprint silently: say why it is missing.
                Spacer(Modifier.height(16.dp))
                Text(
                    when (biometricStatus) {
                        BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED ->
                            "👆 Fingerprint abhi phone mein add nahi hai — Settings → Security → Fingerprint mein ungli add karein, phir app band kar ke dobara kholein."
                        BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE ->
                            "👆 Is phone mein fingerprint sensor nahi hai — PIN se unlock karein."
                        else ->
                            "👆 Fingerprint abhi available nahi hai — PIN se unlock karein."
                    },
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "PIN bhool gaye to data recover nahi hoga.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/** Change-PIN dialog: current PIN, then a new 4-digit PIN with confirm. */
@Composable
fun ChangePinDialog(
    prefs: VaultPrefs,
    onDismiss: () -> Unit,
    onChanged: () -> Unit
) {
    val context = LocalContext.current
    var current by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Change PIN", fontWeight = FontWeight.Black) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = current,
                    onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) current = it },
                    label = { Text("Current PIN") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation()
                )
                OutlinedTextField(
                    value = newPin,
                    onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) newPin = it },
                    label = { Text("New 4-digit PIN") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation()
                )
                OutlinedTextField(
                    value = confirm,
                    onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) confirm = it },
                    label = { Text("Confirm new PIN") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation()
                )
                if (error.isNotEmpty()) {
                    Text(error, color = Color(0xFFB00020), fontWeight = FontWeight.Black)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    when {
                        !prefs.checkPin(current) -> error = "Current PIN is wrong."
                        newPin.length != 4 -> error = "New PIN must be 4 digits."
                        newPin != confirm -> error = "New PINs do not match."
                        else -> {
                            prefs.setPin(newPin)
                            Toast.makeText(context, "PIN changed", Toast.LENGTH_SHORT).show()
                            onChanged()
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = GreenStart)
            ) {
                Text("Change PIN", fontWeight = FontWeight.Black)
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
