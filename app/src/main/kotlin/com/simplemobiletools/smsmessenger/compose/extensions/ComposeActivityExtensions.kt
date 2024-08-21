package com.simplemobiletools.smsmessenger.compose.extensions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import com.simplemobiletools.commons.R
import com.simplemobiletools.commons.compose.extensions.getActivity
import com.simplemobiletools.commons.compose.extensions.getComponentActivity
import com.simplemobiletools.smsmessenger.extensions.launchViewIntent
import com.simplemobiletools.smsmessenger.compose.alert_dialog.rememberAlertDialogState
import com.simplemobiletools.smsmessenger.dialogs.ConfirmationAlertDialog

@Composable
fun FakeVersionCheck() {
    val context = LocalContext.current
    val confirmationDialogAlertDialogState = rememberAlertDialogState().apply {
        DialogMember {
            ConfirmationAlertDialog(
                alertDialogState = this,
                message = FAKE_VERSION_APP_LABEL,
                positive = R.string.ok,
                negative = null
            ) {
                context.getActivity().launchViewIntent(DEVELOPER_PLAY_STORE_URL)
            }
        }
    }
    LaunchedEffect(Unit) {
        context.fakeVersionCheck(confirmationDialogAlertDialogState::show)
    }
}

@Composable
fun CheckAppOnSdCard() {
    val context = LocalContext.current.getComponentActivity()
    val confirmationDialogAlertDialogState = rememberAlertDialogState().apply {
        DialogMember {
            ConfirmationAlertDialog(
                alertDialogState = this,
                messageId = R.string.app_on_sd_card,
                positive = R.string.ok,
                negative = null
            ) {}
        }
    }
    LaunchedEffect(Unit) {
        context.appOnSdCardCheckCompose(confirmationDialogAlertDialogState::show)
    }
}
