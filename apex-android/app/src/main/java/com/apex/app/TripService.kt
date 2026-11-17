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
import com.apex.app.data.obd.commands.CoolantCommand
import com.apex.app.data.obd.commands.FuelPressureCommand
import com.apex.app.data.obd.commands.FuelRailPressureCommand
import com.apex.app.data.obd.commands.FuelRateCommand
import com.apex.app.data.obd.commands.IntakeTempCommand
import com.apex.app.data.obd.commands.LongFuelTrimCommand
import com.apex.app.data.obd.commands.MafCommand
import com.apex.app.data.obd.commands.MapCommand
import com.apex.app.data.obd.commands.OilTempCommand
import com.apex.app.data.obd.commands.RpmCommand
import com.apex.app.data.obd.commands.ShortFuelTrimCommand
import com.apex.app.data.obd.commands.ThrottleCommand
import com.apex.app.data.trip.Anomaly
import com.apex.app.data.trip.LatLng
import com.apex.app.data.trip.RouteFlag
import com.apex.app.data.trip.RpmBand
import com.apex.app.data.trip.Trip
import com.apex.app.data.trip.TripMode
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
 * Servicio en primer plano que graba el viaje (ruta GPS + datos del carro) aunque
 * la pantalla esté apagada o la app en segundo plano. El estado es compartido
 * (companion) para que la UI lo observe.
 */
class TripService : Service() {

    companion object {
        private const val ACTION_START = "com.apex.app.TRIP_START"
        private const val ACTION_STOP = "com.apex.app.TRIP_STOP"
        private const val EXTRA_MODE = "com.apex.app.TRIP_MODE"
        private const val CHANNEL_ID = "apex_trip"
        private const val NOTIF_ID = 1001

        private const val EARTH_KM = 6371.0
        private const val MIN_SEGMENT_KM = 0.0025
        private const val MAX_SEGMENT_KM = 0.5
        private const val GAL_IN_L = 3.785411784

        // Umbrales para eventos y anomalías.
        private const val RPM_HIGH = 4000
        private const val COOLANT_HIGH = 106       // °C
        private const val HARD_ACCEL_KMHS = 11.0   // km/h por segundo
        private const val HARD_BRAKE_KMHS = 13.0
        private const val ANOMALY_GAP_MS = 12_000L
        private const val PRESSURE_LOW_FRACTION = 0.55

        private val _state = MutableStateFlow(TripUiState())
        val state: StateFlow<TripUiState> = _state.asStateFlow()

        private val _trips = MutableStateFlow<List<Trip>>(emptyList())
        val trips: StateFlow<List<Trip>> = _trips.asStateFlow()

        fun startTrip(context: Context, mode: String) {
            val i = Intent(context, TripService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_MODE, mode)
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
    private val routeFlags = ArrayList<Int>()
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
    private var tripMode = ""
    private val bandFuel = HashMap<Int, Double>() // banda RPM -> litros
    private val bandTime = HashMap<Int, Double>() // banda RPM -> segundos

    // Métricas de salud / conducción.
    private var coolantMax: Int? = null
    private var coolantMin: Int? = null
    private var oilMax: Int? = null
    private var oilMin: Int? = null
    private var pressureMin: Int? = null
    private var pressureMax = 0
    private var pressureSum = 0L
    private var pressureCount = 0
    private var rpmSum = 0L
    private var rpmCount = 0
    private var idleMs = 0L
    private var stops = 0
    private var wasMoving = false
    private var hardAccel = 0
    private var hardBrake = 0
    private var climbM = 0.0
    private var dropM = 0.0
    private var lastAlt: Double? = null
    private var prevKmh = 0f
    private var prevPointMillis = 0L
    private val anomalies = ArrayList<Anomaly>()
    private val lastAnomalyAt = HashMap<String, Long>()
    private var cycle = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> beginRecording(intent.getStringExtra(EXTRA_MODE) ?: "")
            ACTION_STOP -> finishRecording()
        }
        return START_STICKY
    }

    private fun beginRecording(mode: String) {
        ServiceGraph.init(applicationContext)
        createChannel()

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
        tripMode = mode

        routePoints.clear(); routeFlags.clear()
        distanceKm = 0.0; maxKmh = 0; last = null
        fuelLiters = 0.0; hasFuel = false; lastMafNanos = 0L
        bandFuel.clear(); bandTime.clear()
        coolantMax = null; coolantMin = null; oilMax = null; oilMin = null
        pressureMin = null; pressureMax = 0; pressureSum = 0L; pressureCount = 0
        rpmSum = 0L; rpmCount = 0
        idleMs = 0L; stops = 0; wasMoving = false
        hardAccel = 0; hardBrake = 0; climbM = 0.0; dropM = 0.0; lastAlt = null
        prevKmh = 0f; prevPointMillis = 0L
        anomalies.clear(); lastAnomalyAt.clear(); cycle = 0
        startMillis = System.currentTimeMillis()
        _state.value = TripUiState(recording = true, mode = mode, message = "Grabando viaje…")

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
                pollCar()
                delay(1000L)
            }
        }
    }

    private fun onPoint(p: GpsPoint) {
        val cur = LatLng(p.lat, p.lon)
        val speedInt = p.kmh.roundToInt()
        if (speedInt > maxKmh) maxKmh = speedInt

        // Aceleración / frenada brusca (km/h por segundo).
        if (prevPointMillis != 0L) {
            val dt = (p.timeMillis - prevPointMillis) / 1000.0
            if (dt in 0.2..5.0) {
                val a = (p.kmh - prevKmh) / dt
                if (a >= HARD_ACCEL_KMHS) hardAccel++
                else if (a <= -HARD_BRAKE_KMHS) hardBrake++
            }
        }
        prevKmh = p.kmh
        prevPointMillis = p.timeMillis

        // Desnivel (filtra ruido < 1 m).
        val prevAlt = lastAlt
        if (p.altM != 0.0) {
            if (prevAlt != null) {
                val d = p.altM - prevAlt
                if (d >= 1.0) climbM += d else if (d <= -1.0) dropM += -d
            }
            lastAlt = p.altM
        }

        val prev = last
        if (prev == null) {
            routePoints.add(cur); routeFlags.add(RouteFlag.OK); last = cur
        } else {
            val seg = haversineKm(prev, cur)
            if (seg in MIN_SEGMENT_KM..MAX_SEGMENT_KM) {
                distanceKm += seg; routePoints.add(cur); routeFlags.add(RouteFlag.OK); last = cur
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

    /** Marca la calidad del tramo actual (último punto de ruta) sin bajar de nivel. */
    private fun flagCurrent(flag: Int) {
        val idx = routeFlags.lastIndex
        if (idx >= 0 && flag > routeFlags[idx]) routeFlags[idx] = flag
    }

    private fun addAnomaly(type: String, label: String, now: Long) {
        val lastAt = lastAnomalyAt[type] ?: 0L
        if (now - lastAt < ANOMALY_GAP_MS) return
        val here = last ?: return
        anomalies.add(Anomaly(here.lat, here.lon, type, label, now))
        lastAnomalyAt[type] = now
    }

    private suspend fun pollCar() {
        if (ServiceGraph.transport.connectionState.value !is ConnectionState.Connected) return
        val now = System.nanoTime()
        val nowMs = System.currentTimeMillis()
        cycle++

        val rpm = readInt(RpmCommand()) ?: 0
        if (rpm > 0) { rpmSum += rpm; rpmCount++ }
        val throttle = readInt(ThrottleCommand())
        val speed = _state.value.curSpeedKmh

        // Refrigerante (casi todos los carros).
        readInt(CoolantCommand())?.let { c ->
            coolantMax = maxOf(coolantMax ?: c, c)
            coolantMin = minOf(coolantMin ?: c, c)
            _state.value = _state.value.copy(curCoolant = c)
            if (c >= COOLANT_HIGH) { flagCurrent(RouteFlag.ANOMALY); addAnomaly("temp_alta", "Refrigerante ${c}°C", nowMs) }
        }

        // Aceite y presión: cada 2 ciclos (no todos los carros los dan).
        if (cycle % 2 == 0) {
            readInt(OilTempCommand())?.let { o ->
                oilMax = maxOf(oilMax ?: o, o); oilMin = minOf(oilMin ?: o, o)
            }
            val pres = readInt(FuelRailPressureCommand()) ?: readInt(FuelPressureCommand())
            if (pres != null && pres > 0) {
                pressureSum += pres; pressureCount++
                pressureMin = minOf(pressureMin ?: pres, pres)
                if (pres > pressureMax) pressureMax = pres
                if (pressureMax > 0 && rpm > 1500 && pres < pressureMax * PRESSURE_LOW_FRACTION) {
                    flagCurrent(RouteFlag.ANOMALY)
                    addAnomaly("presion_baja", "Presión combustible baja (${pres} kPa)", nowMs)
                }
            }
        }

        // RPM altas innecesarias: alto régimen con acelerador moderado y en marcha.
        if (rpm >= RPM_HIGH && (throttle == null || throttle < 70) && speed > 0) {
            flagCurrent(RouteFlag.ANOMALY)
            addAnomaly("rpm_alta", "RPM $rpm sin necesidad", nowMs)
        }

        // Caudal instantáneo (L/h): directo (015E) o estimado por aire.
        var rateLh: Float? = readFloat(FuelRateCommand())
        if (rateLh == null || rateLh <= 0f) {
            val mafGps = readMafGps()
            if (mafGps != null) {
                var lPerS = mafGps / afr / density
                lPerS *= fuelTrimFactor()
                rateLh = (lPerS * 3600.0).toFloat()
                if (throttle != null && throttle <= 2 && rpm > 1200 && speed > 0) rateLh = 0f
            }
        }

        // Ralentí y paradas (motor encendido, sin moverse).
        if (speed == 0 && rpm > 0) {
            idleMs += 1000L
            if (wasMoving) { stops++; wasMoving = false }
        } else if (speed > 3) {
            wasMoving = true
        }

        if (rateLh != null) {
            if (lastMafNanos != 0L) {
                val dtSec = (now - lastMafNanos) / 1_000_000_000.0
                val inc = rateLh * (dtSec / 3600.0)
                fuelLiters += inc
                if (rpm > 0) {
                    val band = (rpm / 500) * 500
                    bandFuel[band] = (bandFuel[band] ?: 0.0) + inc
                    bandTime[band] = (bandTime[band] ?: 0.0) + dtSec
                }
                commitFuel()
                // Consumo alto: eficiencia instantánea muy por debajo del promedio del viaje.
                if (speed > 10 && rateLh > 0f) {
                    val galPerH = rateLh / GAL_IN_L
                    val kmPerGalNow = if (galPerH > 0f) speed / galPerH else 0.0
                    val tripGal = fuelLiters / GAL_IN_L
                    val kmPerGalTrip = if (tripGal > 0.0) distanceKm / tripGal else 0.0
                    if (kmPerGalTrip > 0.0 && kmPerGalNow < kmPerGalTrip * 0.6) {
                        flagCurrent(RouteFlag.HIGH_FUEL)
                    }
                }
            }
            lastMafNanos = now
        }
        _state.value = _state.value.copy(curRpm = rpm)
    }

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

    private suspend fun readMafGps(): Double? {
        val mafCmd = MafCommand()
        val r = ServiceGraph.transport.send(mafCmd.command)
        if (r is Result.Success) {
            val p = mafCmd.parse(r.data)
            if (p is Result.Success && p.data > 0f) return p.data.toDouble()
        }
        if (displacementL <= 0.0) return null
        val rpm = readInt(RpmCommand()) ?: return null
        val mapKpa = readInt(MapCommand()) ?: return null
        val iatC = readInt(IntakeTempCommand()) ?: return null
        if (rpm <= 0 || mapKpa <= 0) return null
        val iatK = iatC + 273.15
        val ve = 0.85
        val airDensity = (mapKpa * 1000.0) / (287.0 * iatK)
        val volPerSec = (rpm / 2.0 / 60.0) * (displacementL / 1000.0) * ve
        val gps = airDensity * volPerSec * 1000.0
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
            if (t > 0.5) RpmBand(b, f / (t / 3600.0)) else null
        }
        val avgRpm = if (rpmCount > 0) (rpmSum / rpmCount).toInt() else 0
        val pressAvg = if (pressureCount > 0) (pressureSum / pressureCount).toInt() else null
        val eco = computeEcoScore(durationMs)
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
            rpmBands = bands,
            mode = tripMode,
            coolantMax = coolantMax, coolantMin = coolantMin,
            oilMax = oilMax, oilMin = oilMin,
            fuelPressureMin = pressureMin, fuelPressureAvg = pressAvg,
            idleMs = idleMs, stops = stops,
            hardAccel = hardAccel, hardBrake = hardBrake,
            climbM = climbM.roundToInt(), dropM = dropM.roundToInt(),
            avgRpm = avgRpm, ecoScore = eco,
            analysis = buildAnalysis(durationMs, eco, kmPerL),
            routeFlags = ArrayList(routeFlags),
            anomalies = ArrayList(anomalies)
        )
        _trips.value = ServiceGraph.tripRepo.add(trip)
        _state.value = _state.value.copy(
            recording = false, finished = true, durationMs = durationMs,
            fuelLiters = fuel, kmPerL = kmPerL, message = null, finishedTripId = trip.id
        )
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /** Puntuación 0–100: parte de 100 y descuenta por conducción agresiva, ralentí y revoluciones. */
    private fun computeEcoScore(durationMs: Long): Int {
        var s = 100.0
        s -= hardAccel * 4.0
        s -= hardBrake * 4.0
        if (durationMs > 0) s -= (idleMs.toDouble() / durationMs) * 25.0
        s -= (anomalies.count { it.type == "rpm_alta" }) * 3.0
        return s.roundToInt().coerceIn(0, 100)
    }

    /** Texto de "Análisis APEX": lee los datos reales del viaje y da veredicto + consejo. */
    private fun buildAnalysis(durationMs: Long, eco: Int, kmPerL: Double?): String {
        val parts = ArrayList<String>()
        val kmPerGal = kmPerL?.let { it * GAL_IN_L }
        if (kmPerGal != null) {
            val verdict = when {
                kmPerGal >= 50 -> "Rendimiento muy bueno"
                kmPerGal >= 35 -> "Rendimiento correcto"
                else -> "Rendimiento mejorable"
            }
            parts.add("$verdict (%.0f km/galón).".format(kmPerGal))
        }
        if (idleMs > 60_000L && durationMs > 0) {
            val pct = (idleMs.toDouble() / durationMs * 100).roundToInt()
            parts.add("Pasaste ${formatMinSec(idleMs)} en ralentí ($pct% del viaje): ahí se gasta combustible sin avanzar.")
        }
        if (hardBrake >= 3 || hardAccel >= 3) {
            parts.add("Tuviste $hardAccel acelerones y $hardBrake frenazos fuertes; anticiparte sube el rendimiento.")
        }
        coolantMax?.let { if (it >= COOLANT_HIGH) parts.add("El motor llegó a ${it}°C, vigílalo.") else parts.add("Temperaturas normales, el motor no se forzó.") }
        if (tripMode == TripMode.CARRETERA && climbM > 50) {
            parts.add("Subiste ${climbM.roundToInt()} m; parte del consumo se explica por la pendiente.")
        }
        val anomCount = anomalies.size
        if (anomCount > 0) parts.add("Marqué $anomCount zona(s) en el mapa para revisar.")
        parts.add("Eco-score del viaje: $eco/100.")
        return parts.joinToString(" ")
    }

    private fun formatMinSec(ms: Long): String {
        val total = ms / 1000
        return "%d:%02d min".format(total / 60, total % 60)
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
        val tag = when (s.mode) {
            TripMode.CIUDAD -> "Ciudad"
            TripMode.CARRETERA -> "Carretera"
            else -> "Viaje"
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("APEX · grabando ($tag)")
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
