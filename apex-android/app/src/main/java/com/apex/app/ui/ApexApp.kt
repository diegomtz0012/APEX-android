package com.apex.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apex.app.data.vehicle.VehicleProfileRepository
import com.apex.app.ui.screens.CarroScreen
import com.apex.app.ui.screens.DynoScreen
import com.apex.app.ui.screens.EnVivoScreen
import com.apex.app.ui.screens.EscanerScreen
import com.apex.app.ui.screens.HistorialScreen
import com.apex.app.ui.screens.InicioScreen
import com.apex.app.ui.screens.MasScreen
import com.apex.app.ui.screens.MedirScreen
import com.apex.app.ui.screens.ViajesScreen
import com.apex.app.ui.theme.ApexColors

private data class NavItem(val label: String, val icon: ImageVector)

@Composable
fun ApexApp(
    liveViewModel: LiveViewModel,
    measureViewModel: MeasureViewModel,
    scannerViewModel: ScannerViewModel,
    tripViewModel: TripViewModel,
    dynoViewModel: DynoViewModel,
    vehicleRepository: VehicleProfileRepository
) {
    var tab by rememberSaveable { mutableStateOf(0) }
    var moreDest by rememberSaveable { mutableStateOf<String?>(null) }

    val items = listOf(
        NavItem("Inicio", Icons.Rounded.Home),
        NavItem("En vivo", Icons.Rounded.Speed),
        NavItem("Medir", Icons.Rounded.Timer),
        NavItem("Viajes", Icons.Rounded.Map),
        NavItem("Más", Icons.Rounded.MoreHoriz)
    )

    Scaffold(
        containerColor = ApexColors.Bg,
        bottomBar = {
            NavigationBar(containerColor = ApexColors.Surface) {
                items.forEachIndexed { index, item ->
                    NavigationBarItem(
                        selected = tab == index,
                        onClick = { tab = index; moreDest = null },
                        icon = { Icon(item.icon, contentDescription = item.label) },
                        label = { Text(item.label, fontSize = 10.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = ApexColors.OnOrange,
                            selectedTextColor = ApexColors.Orange,
                            indicatorColor = ApexColors.Orange,
                            unselectedIconColor = ApexColors.Muted,
                            unselectedTextColor = ApexColors.Muted
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (tab) {
                0 -> InicioScreen(liveViewModel, onGoLive = { tab = 1 })
                1 -> EnVivoScreen(liveViewModel)
                2 -> MedirScreen(measureViewModel)
                3 -> ViajesScreen(tripViewModel)
                else -> MoreSection(
                    dest = moreDest,
                    onOpen = { moreDest = it },
                    onBack = { moreDest = null },
                    measureViewModel = measureViewModel,
                    scannerViewModel = scannerViewModel,
                    dynoViewModel = dynoViewModel,
                    vehicleRepository = vehicleRepository
                )
            }
        }
    }
}

@Composable
private fun MoreSection(
    dest: String?,
    onOpen: (String) -> Unit,
    onBack: () -> Unit,
    measureViewModel: MeasureViewModel,
    scannerViewModel: ScannerViewModel,
    dynoViewModel: DynoViewModel,
    vehicleRepository: VehicleProfileRepository
) {
    if (dest == null) {
        MasScreen(onSelect = onOpen)
        return
    }
    Column(modifier = Modifier.fillMaxSize()) {
        BackBar(
            title = when (dest) {
                "historial" -> "Historial"
                "escaner" -> "Escáner de fallas"
                "dyno" -> "Dinamómetro"
                else -> "Tu carro"
            },
            onBack = onBack
        )
        when (dest) {
            "historial" -> HistorialScreen(measureViewModel)
            "escaner" -> EscanerScreen(scannerViewModel)
            "dyno" -> DynoScreen(dynoViewModel)
            else -> CarroScreen(vehicleRepository)
        }
    }
}

@Composable
private fun BackBar(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, top = 8.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.Rounded.ArrowBack,
                contentDescription = "Atrás",
                tint = ApexColors.Orange
            )
        }
        Spacer(Modifier.width(4.dp))
        Text(title, color = ApexColors.Text, fontSize = 17.sp, fontWeight = FontWeight.Bold)
    }
}
