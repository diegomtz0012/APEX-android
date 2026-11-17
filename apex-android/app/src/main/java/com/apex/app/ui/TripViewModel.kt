package com.apex.app.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.apex.app.TripService
import com.apex.app.data.trip.LatLng
import com.apex.app.data.trip.Trip
import com.apex.app.data.vehicle.VehicleProfileRepository
import kotlinx.coroutines.flow.StateFlow

data class TripUiState(
    val recording: Boolean = false,
    val route: List<LatLng> = emptyList(),
    val distanceKm: Double = 0.0,
    val durationMs: Long = 0L,
    val curSpeedKmh: Int = 0,
    val maxKmh: Int = 0,
    val fuelLiters: Double? = null,
    val kmPerL: Double? = null,
    val finished: Boolean = false,
    val message: String? = null,
    val mode: String = "",
    val curRpm: Int = 0,
    val curCoolant: Int? = null,
    val finishedTripId: Long = 0L
)

/**
 * ViewModel delgado para Viajes: la grabación la hace TripService (en primer
 * plano, sobrevive a la pantalla apagada). Aquí solo observamos el estado
 * compartido y mandamos las órdenes de iniciar/terminar.
 */
class TripViewModel(private val appContext: Context) : ViewModel() {

    val ui: StateFlow<TripUiState> = TripService.state
    val trips: StateFlow<List<Trip>> = TripService.trips

    init {
        TripService.refreshTrips()
    }

    fun start(mode: String) = TripService.startTrip(appContext, mode)
    fun stop() = TripService.stopTrip(appContext)
    fun clearTrips() = TripService.clearTrips()
    fun dismissSummary() = TripService.dismissSummary()

    /** Precio del combustible por galón (del perfil), para calcular el costo. 0 si no está. */
    fun fuelPricePerGallon(): Double =
        VehicleProfileRepository(appContext).load().fuelPricePerGallon.toDoubleOrNull() ?: 0.0

    class Factory(private val appContext: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            TripViewModel(appContext.applicationContext) as T
    }
}
