package com.apex.app.data.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothSocket
import com.apex.app.core.AppError
import com.apex.app.core.Constants
import com.apex.app.core.Result
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

/**
 * Transporte OBD sobre Bluetooth CLÁSICO (SPP/RFCOMM).
 * Sirve para los ELM327 clásicos (como el azul genérico).
 * Toda la E/S corre en Dispatchers.IO: nunca bloquea el hilo principal.
 */
class ClassicBluetoothTransport(
    private val adapter: BluetoothAdapter?
) : ObdTransport {

    private companion object {
        const val READ_BUFFER_SIZE = 256
        const val READ_POLL_INTERVAL_MS = 10L
        const val CARRIAGE_RETURN = "\r"
        val SPP_UUID: UUID = UUID.fromString(Constants.SPP_UUID)
    }

    private var socket: BluetoothSocket? = null
    private var input: InputStream? = null
    private var output: OutputStream? = null

    private val _connectionState =
        MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val connectionState: StateFlow<ConnectionState> =
        _connectionState.asStateFlow()

    @SuppressLint("MissingPermission")
    override fun pairedDevices(): Result<List<BluetoothDeviceInfo>> {
        val currentAdapter = adapter ?: return Result.Failure(AppError.DeviceNotFound)
        if (!currentAdapter.isEnabled) return Result.Failure(AppError.BluetoothDisabled)
        return try {
            val devices = currentAdapter.bondedDevices.orEmpty().map { device ->
                BluetoothDeviceInfo(
                    name = device.name ?: "Adaptador OBD",
                    address = device.address
                )
            }
            Result.Success(devices)
        } catch (e: SecurityException) {
            Result.Failure(AppError.BluetoothPermissionDenied)
        }
    }

    @SuppressLint("MissingPermission")
    override suspend fun connect(address: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            val currentAdapter = adapter ?: return@withContext Result.Failure(AppError.DeviceNotFound)
            if (!currentAdapter.isEnabled) return@withContext Result.Failure(AppError.BluetoothDisabled)

            _connectionState.value = ConnectionState.Connecting

            var lastError: AppError = AppError.ConnectionFailed
            repeat(Constants.Timeouts.MAX_RECONNECT_ATTEMPTS) {
                when (val attempt = tryConnectOnce(currentAdapter, address)) {
                    is Result.Success -> return@withContext attempt
                    is Result.Failure -> {
                        lastError = attempt.error
                        delay(Constants.Timeouts.RECONNECT_DELAY_MS)
                    }
                }
            }
            _connectionState.value = ConnectionState.Failed(lastError)
            Result.Failure(lastError)
        }

    @SuppressLint("MissingPermission")
    private fun tryConnectOnce(
        currentAdapter: BluetoothAdapter,
        address: String
    ): Result<Unit> {
        return try {
            currentAdapter.cancelDiscovery()
            val device = currentAdapter.getRemoteDevice(address)
            val newSocket = device.createRfcommSocketToServiceRecord(SPP_UUID)
            newSocket.connect()

            socket = newSocket
            input = newSocket.inputStream
            output = newSocket.outputStream

            _connectionState.value = ConnectionState.Connected(
                BluetoothDeviceInfo(
                    name = device.name ?: "Adaptador OBD",
                    address = device.address
                )
            )
            Result.Success(Unit)
        } catch (e: SecurityException) {
            closeQuietly()
            Result.Failure(AppError.BluetoothPermissionDenied)
        } catch (e: Exception) {
            closeQuietly()
            Result.Failure(AppError.ConnectionFailed)
        }
    }

    override suspend fun send(command: String): Result<String> =
        withContext(Dispatchers.IO) {
            val out = output
            val inp = input
            if (out == null || inp == null) {
                return@withContext Result.Failure(AppError.ConnectionLost)
            }
            try {
                out.write((command + CARRIAGE_RETURN).toByteArray(Charsets.US_ASCII))
                out.flush()
            } catch (e: Exception) {
                markConnectionLost()
                return@withContext Result.Failure(AppError.ConnectionLost)
            }
            readUntilPrompt(inp)
        }

    private suspend fun readUntilPrompt(inp: InputStream): Result<String> {
        val builder = StringBuilder()
        val buffer = ByteArray(READ_BUFFER_SIZE)

        val raw = withTimeoutOrNull(Constants.Timeouts.COMMAND_RESPONSE_MS) {
            while (isActive) {
                try {
                    if (inp.available() > 0) {
                        val count = inp.read(buffer)
                        if (count > 0) {
                            builder.append(String(buffer, 0, count, Charsets.US_ASCII))
                            if (builder.contains(Constants.ElmResponse.PROMPT)) {
                                return@withTimeoutOrNull builder.toString()
                            }
                        }
                    } else {
                        delay(READ_POLL_INTERVAL_MS)
                    }
                } catch (e: Exception) {
                    return@withTimeoutOrNull null
                }
            }
            null
        }

        return when {
            raw == null && builder.isEmpty() -> Result.Failure(AppError.Timeout)
            raw == null -> Result.Failure(AppError.ConnectionLost)
            else -> Result.Success(raw)
        }
    }

    override suspend fun disconnect() = withContext(Dispatchers.IO) {
        closeQuietly()
        _connectionState.value = ConnectionState.Disconnected
    }

    private fun markConnectionLost() {
        closeQuietly()
        _connectionState.value = ConnectionState.Failed(AppError.ConnectionLost)
    }

    private fun closeQuietly() {
        runCatching { input?.close() }
        runCatching { output?.close() }
        runCatching { socket?.close() }
        input = null
        output = null
        socket = null
    }
}
