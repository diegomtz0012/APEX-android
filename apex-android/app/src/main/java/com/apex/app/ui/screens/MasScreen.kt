package com.apex.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.ShowChart
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apex.app.ui.components.ApexCard
import com.apex.app.ui.components.SectionLabel
import com.apex.app.ui.theme.ApexColors

@Composable
fun MasScreen(onSelect: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        SectionLabel("Más")
        Spacer(Modifier.height(14.dp))

        MasItem(Icons.Rounded.Lightbulb, "Asistente", "Consejos a partir de tus datos") {
            onSelect("asistente")
        }
        Spacer(Modifier.height(10.dp))
        MasItem(Icons.Rounded.ShowChart, "Dinamómetro", "Estima la potencia y el torque de tu carro") {
            onSelect("dyno")
        }
        Spacer(Modifier.height(10.dp))
        MasItem(Icons.Rounded.History, "Historial de mediciones", "Tus piques y pruebas guardados") {
            onSelect("historial")
        }
        Spacer(Modifier.height(10.dp))
        MasItem(Icons.Rounded.Build, "Escáner de fallas", "Leer y borrar códigos de falla") {
            onSelect("escaner")
        }
        Spacer(Modifier.height(10.dp))
        MasItem(Icons.Rounded.DirectionsCar, "Tu carro", "Perfil del vehículo") {
            onSelect("carro")
        }
    }
}

@Composable
private fun MasItem(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    ApexCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = ApexColors.Orange,
                modifier = Modifier.size(26.dp)
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = ApexColors.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(subtitle, color = ApexColors.Muted, fontSize = 12.sp)
            }
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = ApexColors.Muted,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}
