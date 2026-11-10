@file:OptIn(ExperimentalMaterial3Api::class)
@file:Suppress("DEPRECATION")

package pt.comandoprojetor

import android.os.Build
import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun ComandoTheme(content: @Composable () -> Unit) {
    val scheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        dynamicDarkColorScheme(LocalContext.current)
    } else {
        darkColorScheme(
            primary = Color(0xFF7DD3FC),
            onPrimary = Color(0xFF0C2A3A),
            secondaryContainer = Color(0xFF334155),
            onSecondaryContainer = Color(0xFFE2E8F0),
            surfaceVariant = Color(0xFF1E293B),
            background = Color(0xFF0F172A),
            surface = Color(0xFF0F172A),
        )
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

@Composable
fun App(vm: RemoteViewModel) {
    val s by vm.state.collectAsStateWithLifecycle()
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .imePadding()
                .padding(20.dp)
        ) {
            when (s.ecra) {
                Ecra.CONFIG -> EcraConfig(s, vm)
                Ecra.CODIGO -> EcraCodigo(s, vm)
                Ecra.COMANDO -> EcraComando(s, vm)
            }
        }
    }
}

// ---------------- Configuração ----------------

@Composable
private fun EcraConfig(s: UiState, vm: RemoteViewModel) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            "Comando do Projetor",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            "Liga o telemóvel à mesma rede Wi-Fi do projetor. Procura-o na rede ou " +
                "escreve o IP (no projetor: Definições → Rede e Internet).",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OutlinedButton(
            onClick = { if (s.aProcurar) vm.pararProcura() else vm.procurar() },
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (s.aProcurar) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text("A procurar… (toca para parar)")
            } else {
                Text("Procurar na rede")
            }
        }

        s.dispositivos.forEach { d ->
            val selecionado = d.ip == s.ip
            Card(
                onClick = { vm.setIp(d.ip) },
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (selecionado) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant
                ),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(d.nome, fontWeight = FontWeight.SemiBold)
                    Text(d.ip, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        OutlinedTextField(
            value = s.ip,
            onValueChange = vm::setIp,
            label = { Text("IP do projetor") },
            placeholder = { Text("ex.: 192.168.1.50") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth(),
        )

        Button(
            onClick = vm::emparelhar,
            enabled = !s.ocupado && s.ip.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
        ) {
            if (s.ocupado) {
                CircularProgressIndicator(
                    Modifier.size(20.dp), strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Text("Emparelhar")
            }
        }

        s.mensagem?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

// ---------------- Código ----------------

@Composable
private fun EcraCodigo(s: UiState, vm: RemoteViewModel) {
    var codigo by rememberSaveable { mutableStateOf("") }
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(
            "Código de emparelhamento",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            "Apareceu um código de 6 caracteres no projetor. Escreve-o aqui.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = codigo,
            onValueChange = { v ->
                codigo = v.uppercase().filter { it in "0123456789ABCDEF" }.take(6)
            },
            singleLine = true,
            textStyle = MaterialTheme.typography.headlineMedium.copy(
                letterSpacing = 8.sp,
                textAlign = TextAlign.Center,
            ),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Characters,
                keyboardType = KeyboardType.Ascii,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = { vm.confirmarCodigo(codigo) },
            enabled = codigo.length == 6 && !s.ocupado,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
        ) {
            if (s.ocupado) {
                CircularProgressIndicator(
                    Modifier.size(20.dp), strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Text("Confirmar")
            }
        }
        TextButton(onClick = vm::cancelarEmparelhamento, modifier = Modifier.fillMaxWidth()) {
            Text("Cancelar")
        }
        s.mensagem?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

// ---------------- Comando ----------------

@Composable
private fun EcraComando(s: UiState, vm: RemoteViewModel) {
    var menu by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                BotaoTecla(
                    Icons.Filled.PowerSettingsNew, "Ligar/desligar",
                    size = 52.dp, cor = Color(0xFFB3261E),
                ) { vm.tecla(KeyEvent.KEYCODE_POWER) }

                Spacer(Modifier.weight(1f))
                Box(
                    Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(if (s.ligado) Color(0xFF4ADE80) else Color(0xFFFBBF24))
                )
                Spacer(Modifier.width(8.dp))
                Text(if (s.ligado) "Ligado" else "A ligar…", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.weight(1f))

                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Mais opções") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text("Religar") },
                            onClick = { menu = false; vm.religar() },
                        )
                        DropdownMenuItem(
                            text = { Text("Emparelhar de novo") },
                            onClick = { menu = false; vm.esquecer() },
                        )
                    }
                }
            }
            s.mensagem?.let {
                Spacer(Modifier.height(8.dp))
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                )
            }
        }

        Dpad(vm)

        Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                BotaoTecla(Icons.Filled.ArrowBack, "Voltar") { vm.tecla(KeyEvent.KEYCODE_BACK) }
                BotaoTecla(Icons.Filled.Home, "Início") { vm.tecla(KeyEvent.KEYCODE_HOME) }
                BotaoTecla(Icons.Filled.Settings, "Definições") { vm.tecla(KeyEvent.KEYCODE_SETTINGS) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                BotaoTecla(Icons.Filled.VolumeDown, "Baixar volume") { vm.tecla(KeyEvent.KEYCODE_VOLUME_DOWN) }
                BotaoTecla(Icons.Filled.VolumeOff, "Silenciar") { vm.tecla(KeyEvent.KEYCODE_VOLUME_MUTE) }
                BotaoTecla(Icons.Filled.VolumeUp, "Subir volume") { vm.tecla(KeyEvent.KEYCODE_VOLUME_UP) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                BotaoTecla(Icons.Filled.FastRewind, "Recuar") { vm.tecla(KeyEvent.KEYCODE_MEDIA_REWIND) }
                BotaoTecla(Icons.Filled.PlayArrow, "Reproduzir/pausa") { vm.tecla(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) }
                BotaoTecla(Icons.Filled.FastForward, "Avançar") { vm.tecla(KeyEvent.KEYCODE_MEDIA_FAST_FORWARD) }
            }
        }
    }
}

@Composable
private fun Dpad(vm: RemoteViewModel) {
    val haptic = LocalHapticFeedback.current
    Box(
        Modifier
            .size(270.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Seta(Icons.Filled.KeyboardArrowUp, "Cima", Alignment.TopCenter) { vm.tecla(KeyEvent.KEYCODE_DPAD_UP) }
        Seta(Icons.Filled.KeyboardArrowDown, "Baixo", Alignment.BottomCenter) { vm.tecla(KeyEvent.KEYCODE_DPAD_DOWN) }
        Seta(Icons.Filled.KeyboardArrowLeft, "Esquerda", Alignment.CenterStart) { vm.tecla(KeyEvent.KEYCODE_DPAD_LEFT) }
        Seta(Icons.Filled.KeyboardArrowRight, "Direita", Alignment.CenterEnd) { vm.tecla(KeyEvent.KEYCODE_DPAD_RIGHT) }
        Button(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                vm.tecla(KeyEvent.KEYCODE_DPAD_CENTER)
            },
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(),
            modifier = Modifier
                .align(Alignment.Center)
                .size(104.dp),
        ) {
            Text("OK", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun BoxScope.Seta(icon: ImageVector, desc: String, align: Alignment, onClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    IconButton(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        },
        modifier = Modifier
            .align(align)
            .padding(4.dp)
            .size(80.dp),
    ) {
        Icon(icon, desc, Modifier.size(54.dp))
    }
}

@Composable
private fun BotaoTecla(
    icon: ImageVector,
    desc: String,
    size: Dp = 64.dp,
    cor: Color? = null,
    onClick: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    FilledTonalIconButton(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        },
        modifier = Modifier.size(size),
        colors = if (cor != null) {
            IconButtonDefaults.filledTonalIconButtonColors(containerColor = cor, contentColor = Color.White)
        } else {
            IconButtonDefaults.filledTonalIconButtonColors()
        },
    ) {
        Icon(icon, desc, Modifier.size(size * 0.45f))
    }
}
