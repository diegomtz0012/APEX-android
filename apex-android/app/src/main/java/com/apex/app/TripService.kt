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
import com.apex.app.data.obd.commands.MafCommand
import com.apex.app.data.trip.LatLng
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

        routePoints.clear()
        distanceKm = 0.0; maxKmh = 0; last = null
        fuelLiters = 0.0; hasFuel = false; lastMafNanos = 0L
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
                pollMaf()
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

    private suspend fun pollMaf() {
        if (ServiceGraph.transport.connectionState.value !is ConnectionState.Connected) return
        val cmd = MafCommand()
        when (val r = ServiceGraph.transport.send(cmd.command)) {
            is Result.Success -> {
                val parsed = cmd.parse(r.data)
                if (parsed is Result.Success) {
                    val maf = parsed.data
                    val now = System.nanoTime()
                    if (lastMafNanos != 0L) {
                        val dt = (now - lastMafNanos) / 1_000_000_000.0
                        fuelLiters += maf * dt / afr / density
                        hasFuel = true
                        val kmPerL = if (fuelLiters > 0.0) distanceKm / fuelLiters else null
                        _state.value = _state.value.copy(fuelLiters = fuelLiters, kmPerL = kmPerL)
                    }
                    lastMafNanos = now
                }
            }
            is Result.Failure -> { /* sin MAF */ }
        }
    }

    private fun finishRecording() {
        collectJob?.cancel(); tickJob?.cancel(); mafJob?.cancel()
        val durationMs = System.currentTimeMillis() - startMillis
        val hours = durationMs / 3_600_000.0
        val avg = if (hours > 0.0) distanceKm / hours else 0.0
        val fuel = if (hasFuel) fuelLiters else null
        val kmPerL = if (hasFuel && fuelLiters > 0.0) distanceKm / fuelLiters else null
        val trip = Trip(
            id = System.currentTimeMillis(),
            dateMillis = System.currentTimeMillis(),
            distanceKm = distanceKm,
            durationMs = durationMs,
            avgKmh = avg,
            maxKmh = maxKmh,
            fuelLiters = fuel,
            kmPerL = kmPerL,
            route = ArrayList(routePoints)
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
