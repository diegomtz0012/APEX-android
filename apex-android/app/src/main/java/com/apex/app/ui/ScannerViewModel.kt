package com.apex.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.apex.app.core.Constants
import com.apex.app.core.Result
import com.apex.app.data.bluetooth.ConnectionState
import com.apex.app.data.bluetooth.ObdTransport
import com.apex.app.data.obd.Dtc
import com.apex.app.data.obd.DtcDecoder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ScanUiState(
    val scanning: Boolean = false,
    val codes: List<Dtc> = emptyList(),
    val scanned: Boolean = false,
    val message: String? = null
)

/**
 * Escáner de códigos de falla (DTC). SOLO lee (modo 03) y borra (modo 04).
 * Nunca reprograma ni escribe en la ECU.
 */
class ScannerViewModel(
    private val transport: ObdTransport
) : ViewModel() {

    private val _ui = MutableStateFlow(ScanUiState())
    val ui: StateFlow<ScanUiState> = _ui.asStateFlow()

    val connection: StateFlow<ConnectionState> = transport.connectionState

    private fun isConnected(): Boolean =
        transport.connectionState.value is ConnectionState.Connected

    fun scan() {
        if (!isConnected()) {
            _ui.value = _ui.value.copy(message = "Conéctate primero en la pestaña “En vivo”.")
            return
        }
        viewModelScope.launch {
            _ui.value = _ui.value.copy(scanning = true, message = null)
            when (val r = transport.send(Constants.ObdMode.READ_DTC)) {
                is Result.Success -> {
                    val codes = DtcDecoder.decodeMode03(r.data)
                    _ui.value = _ui.value.copy(
                        scanning = false,
                        codes = codes,
                        scanned = true,
                        message = if (codes.isEmpty()) "Sin códigos de falla. Todo limpio." else null
                    )
                }
                is Result.Failure -> _ui.value = _ui.value.copy(
                    scanning = false,
                    message = "No se pudo leer: ${r.error.message}"
                )
            }
        }
    }

    fun clearCodes() {
        if (!isConnected()) {
            _ui.value = _ui.value.copy(message = "Conéctate primero en la pestaña “En vivo”.")
            return
        }
        viewModelScope.launch {
            _ui.value = _ui.value.copy(scanning = true, message = null)
            when (val r = transport.send(Constants.ObdMode.CLEAR_DTC)) {
                is Result.Success -> _ui.value = _ui.value.copy(
                    scanning = false,
                    codes = emptyList(),
                    scanned = true,
                    message = "Códigos borrados. La luz del motor puede tardar unos km en apagarse."
                )
                is Result.Failure -> _ui.value = _ui.value.copy(
                    scanning = false,
                    message = "No se pudo borrar: ${r.error.message}"
                )
            }
        }
    }

    class Factory(
        private val transport: ObdTransport
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ScannerViewModel(transport) as T
    }
}
