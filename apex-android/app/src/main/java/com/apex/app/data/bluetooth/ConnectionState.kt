package com.apex.app.data.bluetooth

import com.apex.app.core.AppError

/** Estados posibles de la conexión con el adaptador OBD. */
sealed interface ConnectionState {
    data object Disconnected : ConnectionState
    data object Connecting : ConnectionState
    data class Connected(val device: BluetoothDeviceInfo) : ConnectionState
    data class Failed(val error: AppError) : ConnectionState
}
