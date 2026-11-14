package com.apex.app.data.bluetooth

import com.apex.app.core.Result
import kotlinx.coroutines.flow.StateFlow

/**
 * Contrato de transporte hacia el adaptador OBD.
 * Hoy lo implementa el Bluetooth clásico (SPP); mañana una clase
 * BLE podrá implementar esta misma interfaz sin cambiar el resto.
 */
interface ObdTransport {
    val connectionState: StateFlow<ConnectionState>
    fun pairedDevices(): Result<List<BluetoothDeviceInfo>>
    suspend fun connect(address: String): Result<Unit>
    suspend fun send(command: String): Result<String>
    suspend fun disconnect()
}
