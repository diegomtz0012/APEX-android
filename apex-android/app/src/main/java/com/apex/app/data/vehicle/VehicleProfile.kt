package com.apex.app.data.vehicle

/** Perfil del vehículo del usuario. Campos como texto para el formulario. */
data class VehicleProfile(
    val nickname: String = "",
    val make: String = "",
    val model: String = "",
    val year: String = "",
    val weightKg: String = "",
    val diesel: Boolean = false,
    val vin: String = ""
)
