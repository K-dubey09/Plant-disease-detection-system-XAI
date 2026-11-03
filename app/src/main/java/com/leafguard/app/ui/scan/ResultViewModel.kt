package com.leafguard.app.ui.scan

import androidx.lifecycle.ViewModel
import com.leafguard.app.data.inference.ScanResult
import com.leafguard.app.data.inference.ScanResultHolder
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

/** Reads the most recent scan from [ScanResultHolder]; null until a scan completes. */
@HiltViewModel
class ResultViewModel @Inject constructor(holder: ScanResultHolder) : ViewModel() {
    val result: StateFlow<ScanResult?> = holder.state
}
