package com.apex.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.apex.app.data.bluetooth.ConnectionState
import com.apex.app.data.location.GpsPoint
import com.apex.app.data.obd.ObdCommand
import com.apex.app.data.obd.commands.FuelRateCommand
import com.apex.app.data.obd.commands.IntakeTempCommand
import com.apex.app.data.obd.commands.LongFuelTrimCommand
import com.apex.app.data.obd.commands.MafCommand
import com.apex.app.data.obd.commands.MapCommand
import com.apex.app.data.obd.commands.RpmCommand
import com.apex.app.data.obd.commands.ShortFuelTrimCommand
import com.apex.app.data.obd.commands.ThrottleCommand
import com.apex.app.data.trip.LatLng
import com.apex.app.data.trip.RpmBand
import com.apex.app.data.trip.Trip
import com.apex.app.ui.TripUiState
import com.apex.app.core.Result
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Servicio en primer plano que graba el viaje (ruta GPS + consumo) aunque la
 * pantalla esté apagada o la app en segundo plano. El estado es compartido
 * (companion) para que la UI lo observe.
 */
class TripService : Service() {

    companion object {
        private const val ACTION_START = "com.apex.app.TRIP_START"
        private const val ACTION_STOP = "com.apex.app.TRIP_STOP"
        private const val CHANNEL_ID = "apex_trip"
        private const val NOTIF_ID = 1001

        private const val EARTH_KM = 6371.0
        private const val MIN_SEGMENT_KM = 0.0025
        private const val MAX_SEGMENT_KM = 0.5

        private val _state = MutableStateFlow(TripUiState())
        val state: StateFlow<TripUiState> = _state.asStateFlow()

        private val _trips = MutableStateFlow<List<Trip>>(emptyList())
        val trips: StateFlow<List<Trip>> = _trips.asStateFlow()

        fun startTrip(context: Context) {
            val i = Intent(context, TripService::class.java).setAction(ACTION_START)
            ContextCompat.startForegroundService(context, i)
        }

        fun stopTrip(context: Context) {
            val i = Intent(context, TripService::class.java).setAction(ACTION_STOP)
            context.startService(i)
        }

        fun refreshTrips() {
            _trips.value = ServiceGraph.tripRepo.load()
        }

        fun clearTrips() {
            ServiceGraph.tripRepo.clear()
            _trips.value = emptyList()
        }

        fun dismissSummary() {
            if (!_state.value.recording) _state.value = TripUiState()
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var collectJob: Job? = null
    private var tickJob: Job? = null
    private var mafJob: Job? = null

    private val routePoints = ArrayList<LatLng>()
    private var distanceKm = 0.0
    private var maxKmh = 0
    private var startMillis = 0L
    private var last: LatLng? = null
    private var fuelLiters = 0.0
    private var hasFuel = false
    private var lastMafNanos = 0L
    private var afr = 14.7
    private var density = 745.0
    private var displacementL = 0.0
    private val bandFuel = HashMap<Int, Double>() // banda RPM -> litros
    private val bandTime = HashMap<Int, Double>() // banda RPM -> segundos

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> beginRecording()
            ACTION_STOP -> finishRecording()
        }
        return START_STICKY
    }

    private fun beginRecording() {
        ServiceGraph.init(applicationContext)
        createChannel()

        // Sin permiso de ubicación o GPS apagado: no se puede grabar.
        if (!ServiceGraph.speedProvider.hasLocationPermission()) {
            ServiceCompat.startForeground(this, NOTIF_ID, buildNotification(), 0)
            _state.value = TripUiState(message = "Falta el permiso de ubicación (GPS).")
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }
        if (!ServiceGraph.speedProvider.isGpsEnabled()) {
            ServiceCompat.startForeground(this, NOTIF_ID, buildNotification(), 0)
            _state.value = TripUiState(message = "Activa el GPS (ubicación) del teléfono.")
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }

        startForegroundSafe()

        val profile = ServiceGraph.vehicleRepo.load()
        if (profile.diesel) { afr = 14.5; density = 832.0 } else { afr = 14.7; density = 745.0 }
        displacementL = profile.displacementL.toDoubleOrNull() ?: 0.0

        routePoints.clear()
        distanceKm = 0.0; maxKmh = 0; last = null
        fuelLiters = 0.0; hasFuel = false; lastMafNanos = 0L
        bandFuel.clear(); bandTime.clear()
        startMillis = System.currentTimeMillis()
        _state.value = TripUiState(recording = true, message = "Grabando viaje…")

        collectJob = scope.launch {
            ServiceGraph.speedProvider.locationFlow().collect { onPoint(it) }
        }
        tickJob = scope.launch {
            while (isActive && _state.value.recording) {
                _state.value = _state.value.copy(durationMs = System.currentTimeMillis() - startMillis)
                updateNotification()
                delay(1000L)
            }
        }
        mafJob = scope.launch {
            while (isActive && _state.value.recording) {
                pollFuel()
                delay(1000L)
            }
        }
    }

    private fun onPoint(p: GpsPoint) {
        val cur = LatLng(p.lat, p.lon)
        val speedInt = p.kmh.roundToInt()
        if (speedInt > maxKmh) maxKmh = speedInt
        val prev = last
        if (prev == null) {
            routePoints.add(cur); last = cur
        } else {
            val seg = haversineKm(prev, cur)
            if (seg in MIN_SEGMENT_KM..MAX_SEGMENT_KM) {
                distanceKm += seg; routePoints.add(cur); last = cur
            } else if (seg > MAX_SEGMENT_KM) {
                last = cur
            }
        }
        _state.value = _state.value.copy(
            route = ArrayList(routePoints),
            distanceKm = distanceKm,
            curSpeedKmh = speedInt,
            maxKmh = maxKmh
        )
    }

    private suspend fun pollFuel() {
        if (ServiceGraph.transport.connectionState.value !is ConnectionState.Connected) return
        val now = System.nanoTime()
        val rpm = readInt(RpmCommand()) ?: 0

        // Caudal instantáneo (L/h): directo (015E) o estimado por aire.
        var rateLh: Float? = readFloat(FuelRateCommand())
        if (rateLh == null || rateLh <= 0f) {
            val mafGps = readMafGps()
            if (mafGps != null) {
                var lPerS = mafGps / afr / density      // L/s (mezcla ideal)
                lPerS *= fuelTrimFactor()               // corrección por ajustes de mezcla
                rateLh = (lPerS * 3600.0).toFloat()
                // Corte en desaceleración (DFCO): acelerador suelto y en marcha → 0.
                val throttle = readInt(ThrottleCommand())
                if (throttle != null && throttle <= 2 && rpm > 1200 && _state.value.curSpeedKmh > 0) {
                    rateLh = 0f
                }
            }
        }
        if (rateLh == null) return

        if (lastMafNanos != 0L) {
            val dtSec = (now - lastMafNanos) / 1_000_000_000.0
            val inc = rateLh * (dtSec / 3600.0) // litros
            fuelLiters += inc
            if (rpm > 0) {
                val band = (rpm / 500) * 500
                bandFuel[band] = (bandFuel[band] ?: 0.0) + inc
                bandTime[band] = (bandTime[band] ?: 0.0) + dtSec
            }
            commitFuel()
        }
        lastMafNanos = now
    }

    /** Factor de corrección por ajustes de mezcla del ECU (corto + largo plazo). */
    private suspend fun fuelTrimFactor(): Double {
        val st = readInt(ShortFuelTrimCommand()) ?: 0
        val lt = readInt(LongFuelTrimCommand()) ?: 0
        return (1.0 + (st + lt) / 100.0).coerceIn(0.5, 1.5)
    }

    private fun commitFuel() {
        hasFuel = true
        val kmPerL = if (fuelLiters > 0.0) distanceKm / fuelLiters else null
        _state.value = _state.value.copy(fuelLiters = fuelLiters, kmPerL = kmPerL)
    }

    private suspend fun readFloat(cmd: ObdCommand<Float>): Float? {
        val r = ServiceGraph.transport.send(cmd.command)
        if (r is Result.Success) {
            val p = cmd.parse(r.data)
            if (p is Result.Success) return p.data
        }
        return null
    }

    /** Flujo de aire en g/s: directo (MAF) o estimado por densidad-velocidad. */
    private suspend fun readMafGps(): Double? {
        // 1) MAF directo (PID 10) si el carro lo da.
        val mafCmd = MafCommand()
        val r = ServiceGraph.transport.send(mafCmd.command)
        if (r is Result.Success) {
            val p = mafCmd.parse(r.data)
            if (p is Result.Success && p.data > 0f) return p.data.toDouble()
        }
        // 2) Densidad-velocidad: necesita cilindrada + MAP + IAT + RPM.
        if (displacementL <= 0.0) return null
        val rpm = readInt(RpmCommand()) ?: return null
        val mapKpa = readInt(MapCommand()) ?: return null
        val iatC = readInt(IntakeTempCommand()) ?: return null
        if (rpm <= 0 || mapKpa <= 0) return null
        val iatK = iatC + 273.15
        val ve = 0.85 // eficiencia volumétrica aproximada
        val airDensity = (mapKpa * 1000.0) / (287.0 * iatK)         // kg/m³
        val volPerSec = (rpm / 2.0 / 60.0) * (displacementL / 1000.0) * ve // m³/s (4 tiempos)
        val gps = airDensity * volPerSec * 1000.0                    // g/s
        return if (gps > 0.0) gps else null
    }

    private suspend fun readInt(cmd: ObdCommand<Int>): Int? {
        val r = ServiceGraph.transport.send(cmd.command)
        if (r is Result.Success) {
            val p = cmd.parse(r.data)
            if (p is Result.Success) return p.data
        }
        return null
    }

    private fun finishRecording() {
        collectJob?.cancel(); tickJob?.cancel(); mafJob?.cancel()
        val durationMs = System.currentTimeMillis() - startMillis
        val hours = durationMs / 3_600_000.0
        val avg = if (hours > 0.0) distanceKm / hours else 0.0
        val fuel = if (hasFuel) fuelLiters else null
        val kmPerL = if (hasFuel && fuelLiters > 0.0) distanceKm / fuelLiters else null
        val bands = bandFuel.keys.sorted().mapNotNull { b ->
            val t = bandTime[b] ?: 0.0
            val f = bandFuel[b] ?: 0.0
            if (t > 0.5) RpmBand(b, f / (t / 3600.0)) else null // L/h promedio en la banda
        }
        val trip = Trip(
            id = System.currentTimeMillis(),
            dateMillis = System.currentTimeMillis(),
            distanceKm = distanceKm,
            durationMs = durationMs,
            avgKmh = avg,
            maxKmh = maxKmh,
            fuelLiters = fuel,
            kmPerL = kmPerL,
            route = ArrayList(routePoints),
            rpmBands = bands
        )
        _trips.value = ServiceGraph.tripRepo.add(trip)
        _state.value = _state.value.copy(
            recording = false, finished = true, durationMs = durationMs,
            fuelLiters = fuel, kmPerL = kmPerL, message = null
        )
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun startForegroundSafe() {
        createChannel()
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0
        ServiceCompat.startForeground(this, NOTIF_ID, buildNotification(), type)
    }

    private fun updateNotification() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, buildNotification())
    }

    private fun buildNotification(): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val s = _state.value
        val dist = "%.2f km".format(s.distanceKm)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("APEX · grabando viaje")
            .setContentText("$dist · ${s.curSpeedKmh} km/h")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .setContentIntent(openIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, "Viajes", NotificationManager.IMPORTANCE_LOW)
                )
            }
        }
    }

    private fun haversineKm(a: LatLng, b: LatLng): Double {
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLon = Math.toRadians(b.lon - a.lon)
        val lat1 = Math.toRadians(a.lat)
        val lat2 = Math.toRadians(b.lat)
        val h = sin(dLat / 2) * sin(dLat / 2) + cos(lat1) * cos(lat2) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * EARTH_KM * asin(sqrt(h))
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
