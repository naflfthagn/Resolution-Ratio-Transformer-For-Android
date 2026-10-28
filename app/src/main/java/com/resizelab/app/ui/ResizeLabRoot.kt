package com.resizelab.app.ui

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.resizelab.app.R
import com.resizelab.app.data.ExportResult
import kotlinx.coroutines.launch

@Composable
fun ResizeLabRoot(viewModel: EditorViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var pendingExportAfterPermission by rememberSaveable { mutableStateOf(false) }

    // ACTION_OPEN_DOCUMENT is used instead of the Photo Picker on purpose.
    // Some OEM Photo Picker providers return virtual content://media/picker/...
    // URIs that cannot be opened again by the app (notably on HyperOS/MIUI).
    val pickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            viewModel.selectMedia(uri)
        }
    }

    val storagePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        val shouldExport = pendingExportAfterPermission
        pendingExportAfterPermission = false
        if (granted) {
            if (shouldExport) viewModel.startExport()
        } else {
            viewModel.notifyStoragePermissionDenied()
        }
    }

    val launchPicker: () -> Unit = {
        runCatching {
            pickerLauncher.launch(arrayOf("image/*", "video/*"))
        }.onFailure {
            scope.launch {
                snackbarHostState.showSnackbar(context.getString(R.string.msg_picker_unavailable))
            }
        }
    }

    val requestExport: () -> Unit = {
        val needsLegacyPermission = Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        if (needsLegacyPermission) {
            pendingExportAfterPermission = true
            storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            viewModel.startExport()
        }
    }

    val openResult: (ExportResult) -> Unit = { result ->
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(result.uri, result.mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            scope.launch {
                snackbarHostState.showSnackbar(context.getString(R.string.msg_open_failed))
            }
        } catch (_: SecurityException) {
            scope.launch {
                snackbarHostState.showSnackbar(context.getString(R.string.msg_open_failed))
            }
        }
    }

    val shareResult: (ExportResult) -> Unit = { result ->
        try {
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = result.mimeType
                putExtra(Intent.EXTRA_STREAM, result.uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(sendIntent, null))
        } catch (_: ActivityNotFoundException) {
            scope.launch {
                snackbarHostState.showSnackbar(context.getString(R.string.msg_share_failed))
            }
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    val activity = context.findActivity()
    DisposableEffect(state.isExporting, activity) {
        if (state.isExporting) {
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (state.media == null) {
            HomeScreen(
                isInspecting = state.isInspecting,
                onPick = launchPicker
            )
        } else {
            EditorScreen(
                state = state,
                onBack = viewModel::clearMedia,
                onPickNew = launchPicker,
                onWidthChange = viewModel::onWidthChanged,
                onHeightChange = viewModel::onHeightChanged,
                onPresetSelected = viewModel::applyPreset,
                onToggleAspectLock = viewModel::toggleAspectLock,
                onModeSelected = viewModel::setMode,
                onFormatSelected = viewModel::setImageFormat,
                onQualityChange = viewModel::setQuality,
                onAudioQualitySelected = viewModel::setAudioQuality,
                onExport = requestExport,
                onCancelExport = viewModel::cancelExport,
                onOpenResult = openResult,
                onShareResult = shareResult,
                onMessage = { message ->
                    scope.launch { snackbarHostState.showSnackbar(message) }
                }
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) { data ->
            Snackbar(
                snackbarData = data,
                shape = RoundedCornerShape(10.dp)
            )
        }
    }
}

private fun Context.findActivity(): Activity? {
    var current = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return current as? Activity
}
