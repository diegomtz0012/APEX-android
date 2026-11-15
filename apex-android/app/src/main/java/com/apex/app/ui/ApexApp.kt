package com.apex.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Icon
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.sp
import com.apex.app.data.vehicle.VehicleProfileRepository
import com.apex.app.ui.screens.CarroScreen
import com.apex.app.ui.screens.EnVivoScreen
import com.apex.app.ui.screens.EscanerScreen
import com.apex.app.ui.screens.HistorialScreen
import com.apex.app.ui.screens.InicioScreen
import com.apex.app.ui.screens.MedirScreen
import com.apex.app.ui.theme.ApexColors

private data class NavItem(val label: String, val icon: ImageVector)

@Composable
fun ApexApp(
    viewModel: LiveViewModel,
    measureViewModel: MeasureViewModel,
    scannerViewModel: ScannerViewModel,
    vehicleRepository: VehicleProfileRepository
) {
    var tab by rememberSaveable { mutableStateOf(0) }

    val items = listOf(
        NavItem("Inicio", Icons.Rounded.Home),
        NavItem("En vivo", Icons.Rounded.Speed),
        NavItem("Medir", Icons.Rounded.Timer),
        NavItem("Historial", Icons.Rounded.History),
        NavItem("Escáner", Icons.Rounded.Build),
        NavItem("Carro", Icons.Rounded.DirectionsCar)
    )

    Scaffold(
        containerColor = ApexColors.Bg,
        bottomBar = {
            NavigationBar(containerColor = ApexColors.Surface) {
                items.forEachIndexed { index, item ->
                    NavigationBarItem(
                        selected = tab == index,
                        onClick = { tab = index },
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
                0 -> InicioScreen(viewModel, onGoLive = { tab = 1 })
                1 -> EnVivoScreen(viewModel)
                2 -> MedirScreen(measureViewModel)
                3 -> HistorialScreen(measureViewModel)
                4 -> EscanerScreen(scannerViewModel)
                else -> CarroScreen(vehicleRepository)
            }
        }
    }
}
