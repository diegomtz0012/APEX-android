package com.apex.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apex.app.data.vehicle.VehicleProfile
import com.apex.app.data.vehicle.VehicleProfileRepository
import com.apex.app.ui.components.SectionLabel
import com.apex.app.ui.theme.ApexColors

@Composable
fun CarroScreen(repository: VehicleProfileRepository) {
    val initial = remember { repository.load() }
    var nickname by rememberSaveable { mutableStateOf(initial.nickname) }
    var make by rememberSaveable { mutableStateOf(initial.make) }
    var model by rememberSaveable { mutableStateOf(initial.model) }
    var year by rememberSaveable { mutableStateOf(initial.year) }
    var weight by rememberSaveable { mutableStateOf(initial.weightKg) }
    var diesel by rememberSaveable { mutableStateOf(initial.diesel) }
    var saved by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 16.dp, bottom = 24.dp)
    ) {
        SectionLabel("Tu carro")
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Se autocompleta al conectar al carro. Puedes corregir cualquier dato.",
            color = ApexColors.Muted,
            fontSize = 12.sp
        )
        if (initial.vin.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = "VIN: ${initial.vin}",
                color = ApexColors.Cyan,
                fontSize = 12.sp,
                fontFamily = com.apex.app.ui.theme.ApexMono
            )
        }
        Spacer(Modifier.height(18.dp))

        Field("Apodo (ej. Mi Accent)", nickname) { nickname = it; saved = false }
        Field("Marca (ej. Hyundai)", make) { make = it; saved = false }
        Field("Modelo (ej. Accent)", model) { model = it; saved = false }
        Field("Año", year, numeric = true) { year = it; saved = false }
        Field("Peso (kg)", weight, numeric = true) { weight = it; saved = false }

        Spacer(Modifier.height(6.dp))
        Text("Combustible", color = ApexColors.Muted, fontSize = 12.sp)
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            FuelOption("Gasolina", selected = !diesel, modifier = Modifier.weight(1f)) {
                diesel = false; saved = false
            }
            Spacer(Modifier.width(10.dp))
            FuelOption("Diésel", selected = diesel, modifier = Modifier.weight(1f)) {
                diesel = true; saved = false
            }
        }

        Spacer(Modifier.height(22.dp))
        Button(
            onClick = {
                repository.save(
                    VehicleProfile(nickname, make, model, year, weight, diesel)
                )
                saved = true
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = ApexColors.Orange,
                contentColor = ApexColors.OnOrange
            )
        ) {
            Text(if (saved) "Guardado ✓" else "Guardar", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }

        Spacer(Modifier.height(14.dp))
        Text(
            text = "El peso sirve para los cálculos de potencia que vienen más adelante (dinamómetro virtual).",
            color = ApexColors.Muted,
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun Field(
    label: String,
    value: String,
    numeric: Boolean = false,
    onChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = if (numeric) {
            KeyboardOptions(keyboardType = KeyboardType.Number)
        } else {
            KeyboardOptions.Default
        },
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = ApexColors.Orange,
            unfocusedBorderColor = ApexColors.Border,
            focusedLabelColor = ApexColors.Orange,
            unfocusedLabelColor = ApexColors.Muted,
            focusedTextColor = ApexColors.Text,
            unfocusedTextColor = ApexColors.Text,
            cursorColor = ApexColors.Orange
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
    )
}

@Composable
private fun FuelOption(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) ApexColors.Orange else ApexColors.Surface)
            .border(
                width = 1.dp,
                color = if (selected) ApexColors.Orange else ApexColors.Border,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (selected) ApexColors.OnOrange else ApexColors.Text,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp
        )
    }
}
