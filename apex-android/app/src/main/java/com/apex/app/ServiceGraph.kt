package com.apex.app

import android.bluetooth.BluetoothManager
import android.content.Context
import com.apex.app.data.bluetooth.ClassicBluetoothTransport
import com.apex.app.data.bluetooth.ObdTransport
import com.apex.app.data.location.SpeedProvider
import com.apex.app.data.trip.TripRepository
import com.apex.app.data.vehicle.VehicleProfileRepository

/**
 * Dependencias compartidas por toda la app (Activity + Servicio), en un solo
 * lugar, para que usen la MISMA conexión OBD y los mismos repositorios.
 * Inyección manual simple (sin Hilt por ahora).
 */
object ServiceGraph {

    lateinit var transport: ObdTransport
        private set
    lateinit var speedProvider: SpeedProvider
        private set
    lateinit var tripRepo: TripRepository
        private set
    lateinit var vehicleRepo: VehicleProfileRepository
        private set

    @Volatile
    private var initialized = false

    fun init(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            val app = context.applicationContext
            val bt = app.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            transport = ClassicBluetoothTransport(bt?.adapter)
            speedProvider = SpeedProvider(app)
            tripRepo = TripRepository(app)
            vehicleRepo = VehicleProfileRepository(app)
            initialized = true
        }
    }
}
