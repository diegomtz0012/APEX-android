package com.apex.app.ui.screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.runtime.Composable
import com.apex.app.ui.components.ComingSoonScreen

@Composable
fun CarroScreen() = ComingSoonScreen(
    icon = Icons.Rounded.DirectionsCar,
    title = "Tu carro",
    description = "Guarda el perfil de tu vehículo: marca, modelo, peso y motor, para cálculos más precisos."
)
