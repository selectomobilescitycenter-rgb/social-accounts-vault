package com.socialvault.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentActivity
import com.socialvault.app.data.AppDatabase
import com.socialvault.app.data.VaultPrefs
import com.socialvault.app.data.VaultRepository
import com.socialvault.app.ui.ChangePinDialog
import com.socialvault.app.ui.PinSetupScreen
import com.socialvault.app.ui.UnlockScreen
import com.socialvault.app.ui.VaultScreen
import com.socialvault.app.ui.VaultTheme

/**
 * Entry point. Builds the local Room database and shows the vault screen.
 * No permissions are requested (only the normal USE_BIOMETRIC permission,
 * needed for fingerprint unlock) and no data ever leaves the device.
 *
 * PIN lock: on first launch the user sets a 4-digit PIN; afterwards the
 * vault shows an unlock screen on every cold start and whenever the app
 * returns from the background.
 */
class MainActivity : FragmentActivity() {

    private lateinit var prefs: VaultPrefs

    // Hoisted so onPause() can re-lock the vault from outside composition.
    private val lockedState = mutableStateOf(false)
    private val setupNeededState = mutableStateOf(false)
    private val darkState = mutableStateOf(false)
    private val changePinState = mutableStateOf(false)

    /** Set before launching an external intent (browser / file picker) so the
     *  resulting onPause() does not lock the vault while the user is away. */
    private var suppressLockOnce = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = VaultPrefs(this)
        lockedState.value = prefs.isPinSet
        setupNeededState.value = !prefs.isPinSet
        darkState.value = prefs.darkMode
        val repository = VaultRepository(AppDatabase.get(this).accountDao())
        setContent {
            val locked by lockedState
            val setupNeeded by setupNeededState
            val darkMode by darkState
            val showChangePin by changePinState
            VaultTheme(darkTheme = darkMode) {
                when {
                    setupNeeded -> PinSetupScreen(
                        onDone = { pin ->
                            prefs.setPin(pin)
                            setupNeededState.value = false
                            lockedState.value = false
                        }
                    )
                    locked -> UnlockScreen(
                        prefs = prefs,
                        activity = this,
                        onUnlocked = { lockedState.value = false }
                    )
                    else -> VaultScreen(
                        repository = repository,
                        prefs = prefs,
                        darkMode = darkMode,
                        onToggleDark = {
                            val next = !darkMode
                            darkState.value = next
                            prefs.darkMode = next
                        },
                        onChangePin = { changePinState.value = true },
                        onExternalIntent = { suppressLockOnce = true }
                    )
                }
                if (showChangePin) {
                    ChangePinDialog(
                        prefs = prefs,
                        onDismiss = { changePinState.value = false },
                        onChanged = { changePinState.value = false }
                    )
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        // Re-lock when the app goes to the background. Rotation (config
        // change) and external intents (browser, file picker) do not lock.
        if (!isChangingConfigurations && prefs.isPinSet && !suppressLockOnce) {
            lockedState.value = true
        }
    }

    override fun onResume() {
        super.onResume()
        suppressLockOnce = false
    }
}
