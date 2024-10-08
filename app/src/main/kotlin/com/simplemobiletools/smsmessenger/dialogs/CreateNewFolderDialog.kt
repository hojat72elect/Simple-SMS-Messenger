package com.simplemobiletools.smsmessenger.dialogs

import android.annotation.SuppressLint
import android.os.Build
import android.view.View
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.simplemobiletools.smsmessenger.R
import com.simplemobiletools.smsmessenger.compose.alert_dialog.AlertDialogState
import com.simplemobiletools.smsmessenger.compose.alert_dialog.rememberAlertDialogState
import com.simplemobiletools.smsmessenger.compose.extensions.MyDevices
import com.simplemobiletools.smsmessenger.activities.BaseSimpleActivity
import com.simplemobiletools.smsmessenger.compose.theme.AppThemeSurface
import com.simplemobiletools.smsmessenger.compose.theme.SimpleTheme
import com.simplemobiletools.smsmessenger.databinding.DialogCreateNewFolderBinding
import com.simplemobiletools.smsmessenger.extensions.createAndroidSAFDirectory
import com.simplemobiletools.smsmessenger.extensions.createSAFDirectorySdk30
import com.simplemobiletools.smsmessenger.extensions.getAlertDialogBuilder
import com.simplemobiletools.smsmessenger.extensions.getDocumentFile
import com.simplemobiletools.smsmessenger.extensions.getFilenameFromPath
import com.simplemobiletools.smsmessenger.extensions.getParentPath
import com.simplemobiletools.smsmessenger.extensions.humanizePath
import com.simplemobiletools.smsmessenger.extensions.isAStorageRootFolder
import com.simplemobiletools.smsmessenger.extensions.isAValidFilename
import com.simplemobiletools.smsmessenger.extensions.isAccessibleWithSAFSdk30
import com.simplemobiletools.smsmessenger.extensions.isRestrictedSAFOnlyRoot
import com.simplemobiletools.smsmessenger.extensions.needsStupidWritePermissions
import com.simplemobiletools.smsmessenger.extensions.setupDialogStuff
import com.simplemobiletools.smsmessenger.extensions.showErrorToast
import com.simplemobiletools.smsmessenger.extensions.showKeyboard
import com.simplemobiletools.smsmessenger.extensions.toast
import com.simplemobiletools.smsmessenger.extensions.value
import com.simplemobiletools.smsmessenger.helpers.isRPlus
import java.io.File

@SuppressLint("SetTextI18n")
@RequiresApi(Build.VERSION_CODES.O)
class CreateNewFolderDialog(
    val activity: BaseSimpleActivity,
    val path: String,
    val callback: (path: String) -> Unit
) {
    init {
        val view = DialogCreateNewFolderBinding.inflate(activity.layoutInflater, null, false)
        view.folderPath.setText("${activity.humanizePath(path).trimEnd('/')}/")

        activity.getAlertDialogBuilder()
            .setPositiveButton(R.string.ok, null)
            .setNegativeButton(R.string.cancel, null)
            .apply {
                activity.setupDialogStuff(
                    view.root,
                    this,
                    R.string.create_new_folder
                ) { alertDialog ->
                    alertDialog.showKeyboard(view.folderName)
                    alertDialog.getButton(AlertDialog.BUTTON_POSITIVE)
                        .setOnClickListener(View.OnClickListener {
                            val name = view.folderName.value
                            when {
                                name.isEmpty() -> activity.toast(R.string.empty_name)
                                name.isAValidFilename() -> {
                                    val file = File(path, name)
                                    if (file.exists()) {
                                        activity.toast(R.string.name_taken)
                                        return@OnClickListener
                                    }

                                    createFolder("$path/$name", alertDialog)
                                }

                                else -> activity.toast(R.string.invalid_name)
                            }
                        })
                }
            }
    }


    private fun createFolder(path: String, alertDialog: AlertDialog) {
        try {
            when {
                activity.isRestrictedSAFOnlyRoot(path) && activity.createAndroidSAFDirectory(path) -> sendSuccess(
                    alertDialog,
                    path
                )

                activity.isAccessibleWithSAFSdk30(path) -> activity.handleSAFDialogSdk30(path) {
                    if (it && activity.createSAFDirectorySdk30(path)) {
                        sendSuccess(alertDialog, path)
                    }
                }

                activity.needsStupidWritePermissions(path) -> activity.handleSAFDialog(path) {
                    if (it) {
                        try {
                            val documentFile = activity.getDocumentFile(path.getParentPath())
                            val newDir = documentFile?.createDirectory(path.getFilenameFromPath())
                                ?: activity.getDocumentFile(path)
                            if (newDir != null) {
                                sendSuccess(alertDialog, path)
                            } else {
                                activity.toast(R.string.unknown_error_occurred)
                            }
                        } catch (e: SecurityException) {
                            activity.showErrorToast(e)
                        }
                    }
                }

                File(path).mkdirs() -> sendSuccess(alertDialog, path)
                isRPlus() && activity.isAStorageRootFolder(path.getParentPath()) -> activity.handleSAFCreateDocumentDialogSdk30(
                    path
                ) {
                    if (it) {
                        sendSuccess(alertDialog, path)
                    }
                }

                else -> activity.toast(
                    activity.getString(
                        R.string.could_not_create_folder,
                        path.getFilenameFromPath()
                    )
                )
            }
        } catch (e: Exception) {
            activity.showErrorToast(e)
        }
    }

    private fun sendSuccess(alertDialog: AlertDialog, path: String) {
        callback(path.trimEnd('/'))
        alertDialog.dismiss()
    }
}

@Composable
fun CreateNewFolderAlertDialog(
    alertDialogState: AlertDialogState,
    path: String,
    modifier: Modifier = Modifier,
    callback: (path: String) -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    val context = LocalContext.current
    val view = LocalView.current
    var title by remember { mutableStateOf("") }

    AlertDialog(
        modifier = modifier.dialogBorder,
        shape = dialogShape,
        containerColor = dialogContainerColor,
        tonalElevation = dialogElevation,
        onDismissRequest = alertDialogState::hide,
        confirmButton = {
            TextButton(
                onClick = {
                    alertDialogState.hide()
                    //add callback
                    val name = title
                    when {
                        name.isEmpty() -> context.toast(R.string.empty_name)
                        name.isAValidFilename() -> {
                            val file = File(path, name)
                            if (file.exists()) {
                                context.toast(R.string.name_taken)
                                return@TextButton
                            }

                            callback("$path/$name")
                        }

                        else -> context.toast(R.string.invalid_name)
                    }
                }
            ) {
                Text(text = stringResource(id = R.string.ok))
            }
        },
        dismissButton = {
            TextButton(
                onClick = alertDialogState::hide
            ) {
                Text(text = stringResource(id = R.string.cancel))
            }
        },
        title = {
            Text(
                text = stringResource(id = R.string.create_new_folder),
                color = dialogTextColor,
                fontSize = 21.sp,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(
                Modifier.fillMaxWidth(),
            ) {
                OutlinedTextField(
                    modifier = Modifier
                        .fillMaxWidth(),
                    value = if (!view.isInEditMode) "${
                        context.humanizePath(path).trimEnd('/')
                    }/" else path,
                    onValueChange = {},
                    label = {
                        Text(text = stringResource(id = R.string.folder))
                    },
                    enabled = false,
                    colors = OutlinedTextFieldDefaults.colors(
                        disabledTextColor = dialogTextColor,
                        disabledBorderColor = SimpleTheme.colorScheme.primary,
                        disabledLabelColor = SimpleTheme.colorScheme.primary,
                    )
                )

                Spacer(modifier = Modifier.padding(vertical = SimpleTheme.dimens.padding.medium))

                OutlinedTextField(
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    value = title,
                    onValueChange = {
                        title = it
                    },
                    label = {
                        Text(text = stringResource(id = R.string.title))
                    },
                )
            }
        }
    )
    ShowKeyboardWhenDialogIsOpenedAndRequestFocus(focusRequester = focusRequester)
}

@MyDevices
@Composable
private fun CreateNewFolderAlertDialogPreview() {
    AppThemeSurface {
        CreateNewFolderAlertDialog(
            alertDialogState = rememberAlertDialogState(),
            path = "Internal/"
        ) {}
    }
}
