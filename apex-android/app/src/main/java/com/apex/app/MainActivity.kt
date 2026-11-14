package com.apex.app

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.viewmodel.compose.viewModel
import com.apex.app.data.bluetooth.ClassicBluetoothTransport
import com.apex.app.data.obd.ObdInitializer
import com.apex.app.ui.LiveScreen
import com.apex.app.ui.LiveViewModel
import com.apex.app.ui.theme.ApexTheme

class MainActivity : ComponentActivity() {

    private val permissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { /* El usuario responde; la UI se adapta según el resultado. */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestBluetoothPermissions()

        // Inyección manual simple: creamos el transporte y el inicializador
        // y los pasamos al ViewModel mediante su fábrica.
        val manager = getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = manager?.adapter
        val transport = ClassicBluetoothTransport(adapter)
        val initializer = ObdInitializer(transport)
        val factory = LiveViewModel.Factory(transport, initializer)

        setContent {
            ApexTheme {
                val vm: LiveViewModel = viewModel(factory = factory)
                LiveScreen(vm)
            }
        }
    }

    private fun requestBluetoothPermissions() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN
            )
        }
        permissionLauncher.launch(permissions)
    }
}
