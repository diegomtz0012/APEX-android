package com.apex.app.ui.screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.runtime.Composable
import com.apex.app.ui.components.ComingSoonScreen

@Composable
fun MedirScreen() = ComingSoonScreen(
    icon = Icons.Rounded.Timer,
    title = "Pique 0-100",
    description = "Mide tu aceleración 0-100 km/h con el GPS del teléfono, estilo Dragy. En construcción."
)

@Composable
fun HistorialScreen() = ComingSoonScreen(
    icon = Icons.Rounded.History,
    title = "Historial",
    description = "Aquí se guardarán tus sesiones, piques y datos de cada salida."
)

@Composable
fun CarroScreen() = ComingSoonScreen(
    icon = Icons.Rounded.DirectionsCar,
    title = "Tu carro",
    description = "Guarda el perfil de tu vehículo: marca, modelo, peso y motor, para cálculos más precisos."
)
