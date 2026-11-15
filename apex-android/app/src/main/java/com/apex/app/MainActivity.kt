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
import com.apex.app.data.drag.DragHistoryRepository
import com.apex.app.data.location.SpeedProvider
import com.apex.app.data.obd.ObdInitializer
import com.apex.app.data.trip.TripRepository
import com.apex.app.data.vehicle.VehicleProfileRepository
import com.apex.app.ui.ApexApp
import com.apex.app.ui.DynoViewModel
import com.apex.app.ui.LiveViewModel
import com.apex.app.ui.MeasureViewModel
import com.apex.app.ui.ScannerViewModel
import com.apex.app.ui.TripViewModel
import com.apex.app.ui.theme.ApexTheme
import org.osmdroid.config.Configuration

class MainActivity : ComponentActivity() {

    private val permissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { /* El usuario responde; la UI se adapta según el resultado. */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestPermissions()

        // osmdroid necesita un user-agent para descargar los mosaicos del mapa.
        Configuration.getInstance().userAgentValue = packageName

        // Inyección manual simple.
        // --- Lectura OBD (Bluetooth) ---
        val manager = getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = manager?.adapter
        val transport = ClassicBluetoothTransport(adapter)
        val initializer = ObdInitializer(transport)
        val liveFactory = LiveViewModel.Factory(transport, initializer)

        // --- Medición de pique (GPS) ---
        val speedProvider = SpeedProvider(applicationContext)
        val historyRepo = DragHistoryRepository(applicationContext)
        val measureFactory = MeasureViewModel.Factory(speedProvider, historyRepo)

        // --- Escáner de fallas (usa el MISMO transporte OBD) ---
        val scannerFactory = ScannerViewModel.Factory(transport)

        // --- Perfil del vehículo ---
        val vehicleRepo = VehicleProfileRepository(applicationContext)

        // --- Viajes (GPS + mapa + consumo opcional vía OBD) ---
        val tripRepo = TripRepository(applicationContext)
        val tripFactory = TripViewModel.Factory(speedProvider, transport, tripRepo, vehicleRepo)

        // --- Dinamómetro virtual (GPS + peso + RPM opcional) ---
        val dynoFactory = DynoViewModel.Factory(speedProvider, transport, vehicleRepo)

        setContent {
            ApexTheme {
                val liveVm: LiveViewModel = viewModel(factory = liveFactory)
                val measureVm: MeasureViewModel = viewModel(factory = measureFactory)
                val scannerVm: ScannerViewModel = viewModel(factory = scannerFactory)
                val tripVm: TripViewModel = viewModel(factory = tripFactory)
                val dynoVm: DynoViewModel = viewModel(factory = dynoFactory)
                ApexApp(liveVm, measureVm, scannerVm, tripVm, dynoVm, vehicleRepo)
            }
        }
    }

    private fun requestPermissions() {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            permissions.add(Manifest.permission.BLUETOOTH)
            permissions.add(Manifest.permission.BLUETOOTH_ADMIN)
        }
        // Ubicación para la medición por GPS.
        permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        permissionLauncher.launch(permissions.toTypedArray())
    }
}
