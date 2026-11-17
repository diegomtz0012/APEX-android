package com.apex.app.data.location

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** Una muestra de velocidad del GPS, con su marca de tiempo en nanosegundos. */
data class SpeedSample(val kmh: Float, val timeNanos: Long)

/** Un punto del GPS con ubicación, velocidad, altitud y tiempo (para la ruta de viajes). */
data class GpsPoint(
    val lat: Double,
    val lon: Double,
    val kmh: Float,
    val altM: Double,
    val timeMillis: Long
)

/**
 * Entrega la velocidad del vehículo usando el GPS del teléfono (efecto Doppler).
 * Es el mismo principio de un Dragy. La precisión depende del GPS del equipo.
 */
class SpeedProvider(private val context: Context) {

    fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

    fun isGpsEnabled(): Boolean {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        return lm?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true
    }

    /**
     * Flujo de muestras de velocidad. Pide actualizaciones lo más rápido que
     * el GPS permita (minTime = 0). Se cancela solo al dejar de observarlo.
     */
    @SuppressLint("MissingPermission")
    fun speedFlow(): Flow<SpeedSample> = callbackFlow {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                val kmh = if (location.hasSpeed()) location.speed * 3.6f else 0f
                trySend(SpeedSample(kmh = kmh, timeNanos = System.nanoTime()))
            }

            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }

        if (lm == null) {
            close()
        } else {
            try {
                lm.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    0L,
                    0f,
                    listener,
                    Looper.getMainLooper()
                )
            } catch (e: SecurityException) {
                close(e)
            }
        }

        // callbackFlow EXIGE awaitClose al final (siempre se alcanza).
        awaitClose { lm?.removeUpdates(listener) }
    }

    /** Flujo con la ubicación completa (lat/lon + velocidad), para grabar rutas. */
    @SuppressLint("MissingPermission")
    fun locationFlow(): Flow<GpsPoint> = callbackFlow {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                val kmh = if (location.hasSpeed()) location.speed * 3.6f else 0f
                trySend(
                    GpsPoint(
                        lat = location.latitude,
                        lon = location.longitude,
                        kmh = kmh,
                        altM = if (location.hasAltitude()) location.altitude else 0.0,
                        timeMillis = System.currentTimeMillis()
                    )
                )
            }

            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }

        if (lm == null) {
            close()
        } else {
            try {
                lm.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    0L,
                    0f,
                    listener,
                    Looper.getMainLooper()
                )
            } catch (e: SecurityException) {
                close(e)
            }
        }

        awaitClose { lm?.removeUpdates(listener) }
    }
}
