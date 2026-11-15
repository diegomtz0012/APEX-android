package com.apex.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apex.app.ui.theme.ApexColors
import com.apex.app.ui.theme.ApexDisplay
import com.apex.app.ui.theme.ApexMono

/** Eyebrow / título de sección en mono, estilo técnico. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        color = ApexColors.Faint,
        fontSize = 11.sp,
        fontFamily = ApexMono,
        letterSpacing = 1.2.sp,
        modifier = modifier
    )
}

/** Etiqueta dentro de una tarjeta, con punto luminoso de acento (estilo "ctag"). */
@Composable
fun CardTag(text: String, accent: Color = ApexColors.Orange, modifier: Modifier = Modifier) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        Box(
            modifier = Modifier
                .size(5.dp)
                .clip(CircleShape)
                .background(accent)
        )
        Spacer(Modifier.width(7.dp))
        Text(
            text = text.uppercase(),
            color = ApexColors.Faint,
            fontSize = 10.sp,
            fontFamily = ApexMono,
            letterSpacing = 1.sp
        )
    }
}

/** Punto + texto de estado. */
@Composable
fun StatusDot(text: String, color: Color, modifier: Modifier = Modifier) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        Box(
            modifier = Modifier
                .size(9.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = text, color = ApexColors.Text, fontSize = 13.sp, fontFamily = ApexMono)
    }
}

/** Tarjeta base: superficie + borde sutil + esquinas 18. Apila en columna. */
@Composable
fun ApexCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(ApexColors.Surface)
            .border(1.dp, ApexColors.LineSoft, RoundedCornerShape(18.dp))
            .padding(16.dp)
    ) {
        content()
    }
}

/** Pantalla de módulo aún no construido. */
@Composable
fun ComingSoonScreen(icon: ImageVector, title: String, description: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(84.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(ApexColors.Surface2),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = ApexColors.Orange,
                modifier = Modifier.size(40.dp)
            )
        }
        Spacer(modifier = Modifier.height(20.dp))
        Text(text = title, color = ApexColors.Text, fontSize = 20.sp, fontFamily = ApexDisplay, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = description,
            color = ApexColors.Muted,
            fontSize = 13.sp,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(18.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(ApexColors.Surface2)
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Lock,
                contentDescription = null,
                tint = ApexColors.Faint,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = "Próximamente", color = ApexColors.Faint, fontSize = 12.sp, fontFamily = ApexMono)
        }
    }
}
