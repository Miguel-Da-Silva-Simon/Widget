package com.example.widget_android.ui

import android.content.Intent
import android.net.Uri
import android.widget.ImageView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.widget_android.R
import com.example.widget_android.data.AttendanceAction
import com.example.widget_android.data.AttendanceDurations
import com.example.widget_android.data.AttendanceState
import com.example.widget_android.data.AttendanceTimeUtils
import com.example.widget_android.data.canChangeMode
import com.example.widget_android.data.ClockingApiRepository
import com.example.widget_android.data.ClockingMode
import com.example.widget_android.data.ClockingState
import com.example.widget_android.data.resolveActionBindings
import com.example.widget_android.network.toUserMessage
import com.example.widget_android.theme.Gray100
import com.example.widget_android.theme.Gray300
import com.example.widget_android.theme.Gray500
import com.example.widget_android.theme.SygnaBlue
import com.example.widget_android.theme.SygnaBlueLight
import com.example.widget_android.theme.White
import com.example.widget_android.theme.WorkGreen
import com.example.widget_android.widget.FichajeWidgetUpdater
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun DashboardScreen(
    repository: ClockingApiRepository,
    onLoggedOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var state by remember { mutableStateOf<ClockingState?>(null) }
    var userName by remember { mutableStateOf("") }
    var profilePhotoUri by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var tick by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val scroll = rememberScrollState()
    val photoPicker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            scope.launch {
                runCatching {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }
                repository.saveProfilePhotoUri(uri.toString())
                profilePhotoUri = uri.toString()
            }
        }

    suspend fun refreshWidget() {
        FichajeWidgetUpdater.updateAll(context.applicationContext)
    }

    suspend fun runAttendanceAction(action: AttendanceAction?) {
        if (action == null) return
        repository.doAction(action).fold(
            onSuccess = {
                state = it
                refreshWidget()
            },
            onFailure = { error = it.toUserMessage() }
        )
    }

    suspend fun startNewWorkday() {
        repository.reset().fold(
            onSuccess = {
                state = it
                refreshWidget()
                runAttendanceAction(AttendanceAction.CLOCK_IN)
            },
            onFailure = { error = it.toUserMessage() }
        )
    }

    suspend fun reload() {
        loading = true
        error = null
        var today = repository.today()
        if (today.isFailure) {
            val e = today.exceptionOrNull()
            if (e is SocketTimeoutException || e is ConnectException) {
                delay(500)
                today = repository.today()
            }
        }
        today.fold(
            onSuccess = {
                state = it
                loading = false
            },
            onFailure = { e ->
                loading = false
                error = e.toUserMessage()
            }
        )
        if (today.isFailure) {
            if (userName.isBlank()) {
                repository.readUserName()?.let { userName = it }
            }
            return
        }
        repository.refreshMe().onSuccess { userName = it }
        if (userName.isBlank()) {
            repository.readUserName()?.let { userName = it }
        }
    }

    LaunchedEffect(Unit) {
        userName = repository.readUserName().orEmpty()
        profilePhotoUri = repository.readProfilePhotoUri()
        reload()
    }

    val s = state
    LaunchedEffect(s?.isFinished, s?.currentState) {
        tick = 0
        while (s != null && !s.isFinished && s.currentState != AttendanceState.NOT_STARTED) {
            delay(1000)
            tick++
        }
    }

    var breakStartMs by remember { mutableLongStateOf(-1L) }
    var mealStartMs by remember { mutableLongStateOf(-1L) }
    LaunchedEffect(s?.currentState, s?.lastActionTime, s?.lastActionLabel) {
        if (s != null) {
            breakStartMs = repository.readBreakStartMs()
            mealStartMs = repository.readMealStartMs()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Gray100)
            .verticalScroll(scroll)
            .padding(horizontal = 22.dp, vertical = 18.dp)
    ) {
        when {
            loading && s == null -> {
                Box(Modifier.fillMaxWidth().height(320.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = SygnaBlue)
                }
                return@Column
            }
        }

        error?.let { msg ->
            Card(
                colors = CardDefaults.cardColors(containerColor = White),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(text = msg, color = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(onClick = { scope.launch { reload() } }) {
                        Text("Reintentar")
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        if (s == null) return@Column

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = White),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.widget_brand_icon),
                            contentDescription = "Sygna",
                            modifier = Modifier.size(40.dp)
                        )
                        Text(
                            text = "Sygna",
                            color = SygnaBlue,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 18.sp
                        )
                    }
                    Surface(
                        onClick = {
                            scope.launch {
                                repository.logout()
                                refreshWidget()
                                onLoggedOut()
                            }
                        },
                        modifier = Modifier.size(34.dp),
                        shape = CircleShape,
                        border = BorderStroke(1.dp, SygnaBlue.copy(alpha = 0.35f)),
                        color = White,
                        tonalElevation = 0.dp,
                        shadowElevation = 0.dp
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                                contentDescription = "Cerrar sesión",
                                modifier = Modifier.size(18.dp),
                                tint = SygnaBlue
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                ProfilePhotoAvatar(
                    photoUri = profilePhotoUri,
                    userName = userName,
                    modifier = Modifier.size(72.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = { photoPicker.launch(arrayOf("image/*")) },
                    modifier = Modifier.height(34.dp),
                    shape = RoundedCornerShape(999.dp),
                    border = BorderStroke(1.dp, SygnaBlue.copy(alpha = 0.35f)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = White,
                        contentColor = SygnaBlue
                    )
                ) {
                    Text("Actualizar foto", fontWeight = FontWeight.Medium, fontSize = 12.sp)
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Bienvenido, ${userName.ifBlank { "Usuario" }}",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = SygnaBlue,
                        fontSize = 18.sp
                    ),
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.height(22.dp))

        val totalSeconds = remember(tick, s.elapsedSeconds, s.currentState, s.isFinished) {
            when {
                s.currentState == AttendanceState.NOT_STARTED -> 0L
                s.isFinished -> s.elapsedSeconds
                else -> s.elapsedSeconds + tick
            }
        }
        val breakElapsedSeconds = remember(tick, s.currentState, breakStartMs) {
            if (s.currentState == AttendanceState.BREAK_ACTIVE && breakStartMs > 0L) {
                maxOf(0L, (System.currentTimeMillis() - breakStartMs) / 1000)
            } else {
                null
            }
        }
        val mealElapsedSeconds = remember(tick, s.currentState, mealStartMs) {
            if (s.currentState == AttendanceState.MEAL_ACTIVE && mealStartMs > 0L) {
                maxOf(0L, (System.currentTimeMillis() - mealStartMs) / 1000)
            } else {
                null
            }
        }
        val timerCapsuleColor = when (s.currentState) {
            AttendanceState.BREAK_ACTIVE -> Color(0xFFFFF7E8)
            AttendanceState.MEAL_ACTIVE -> Color(0xFFFFF3E0)
            else -> White
        }
        val statusDotColor = when (s.currentState) {
            AttendanceState.WORKING -> WorkGreen
            AttendanceState.BREAK_ACTIVE -> Color(0xFFF59E0B)
            AttendanceState.MEAL_ACTIVE -> Color(0xFFFB923C)
            else -> Gray300
        }
        val actions = s.resolveActionBindings()
        val entradaHit = actions.clockInEnabled
        val coffeeHit = actions.breakEnabled
        val mealHit = actions.mealEnabled
        val salidaHit = actions.clockOutEnabled
        val nextAction = s.nextAllowedAction

        val hiEntrada = entradaHit && nextAction == actions.clockInAction
        val hiBreak = coffeeHit && nextAction == actions.breakAction
        val hiMeal = mealHit && nextAction == actions.mealAction
        val hiSalida = salidaHit && nextAction == actions.clockOutAction
        val canChangeMode = s.canChangeMode()
        val workedSummary = "Has trabajado ${formatElapsed(totalSeconds)}"

        Column(modifier = Modifier.fillMaxWidth()) {
            val timerShape = RoundedCornerShape(10.dp)
            val timerBorder = BorderStroke(1.dp, Color(0xFFD2ECFF))
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(6.dp, timerShape),
                shape = timerShape,
                color = timerCapsuleColor,
                border = timerBorder,
                tonalElevation = 0.dp,
                shadowElevation = 0.dp
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(statusDotColor)
                        )
                        Text(
                            text = formatElapsed(totalSeconds),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    if (s.currentState == AttendanceState.BREAK_ACTIVE && breakStartMs > 0L) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Volver a las ${
                                AttendanceTimeUtils.formatClockHHmm(
                                    breakStartMs + AttendanceDurations.BREAK_MS
                                )
                            } · descanso 30 min",
                            fontSize = 11.sp,
                            color = Gray500,
                            textAlign = TextAlign.Center
                        )
                        breakElapsedSeconds?.let { br ->
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "En descanso: ${formatElapsed(br)}",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = Gray500,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                    if (s.currentState == AttendanceState.MEAL_ACTIVE && mealStartMs > 0L) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Volver a las ${
                                AttendanceTimeUtils.formatClockHHmm(
                                    mealStartMs + AttendanceDurations.MEAL_MS
                                )
                            } · comida 1 h",
                            fontSize = 11.sp,
                            color = Gray500,
                            textAlign = TextAlign.Center
                        )
                        mealElapsedSeconds?.let { ml ->
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "En comida: ${formatElapsed(ml)}",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = Gray500,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                    if (s.isFinished) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = workedSummary,
                            fontSize = 11.sp,
                            color = Gray500,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ActionCircleDrawable(
                        title = "Entrada",
                        caption = "Entrada",
                        enabled = entradaHit && !s.isFinished,
                        highlighted = hiEntrada,
                        iconOn = R.drawable.ic_widget_entrada,
                        iconOff = R.drawable.ic_widget_entrada_dim,
                        compact = true,
                        onClick = {
                            scope.launch { runAttendanceAction(actions.clockInAction) }
                        }
                    )
                    ActionCircleDrawable(
                        title = if (actions.breakAction == AttendanceAction.BREAK_END) "Fin desc." else "Descanso",
                        caption = "Desc.",
                        enabled = coffeeHit && !s.isFinished,
                        highlighted = hiBreak,
                        iconOn = R.drawable.ic_widget_descanso,
                        iconOff = R.drawable.ic_widget_descanso_dim,
                        compact = true,
                        onClick = {
                            scope.launch { runAttendanceAction(actions.breakAction) }
                        }
                    )
                    ActionCircleDrawable(
                        title = if (actions.mealAction == AttendanceAction.MEAL_END) "Fin comida" else "Comida",
                        caption = "Comida",
                        enabled = mealHit && !s.isFinished,
                        highlighted = hiMeal,
                        iconOn = R.drawable.ic_widget_comida,
                        iconOff = R.drawable.ic_widget_comida_dim,
                        compact = true,
                        onClick = {
                            scope.launch { runAttendanceAction(actions.mealAction) }
                        }
                    )
                    ActionCircleDrawable(
                        title = "Salida",
                        caption = "Salida",
                        enabled = salidaHit && !s.isFinished,
                        highlighted = hiSalida,
                        iconOn = R.drawable.ic_widget_salida,
                        iconOff = R.drawable.ic_widget_salida_dim,
                        compact = true,
                        onClick = {
                            scope.launch { runAttendanceAction(actions.clockOutAction) }
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = White),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Último fichaje", style = MaterialTheme.typography.labelMedium, color = Gray500)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "${s.lastActionLabel} · ${s.lastActionTime}",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    if (s.isFinished) "Tiempo trabajado" else "Siguiente",
                    style = MaterialTheme.typography.labelMedium,
                    color = Gray500
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (s.isFinished) workedSummary else s.nextStepLabel,
                    style = MaterialTheme.typography.titleMedium,
                    color = SygnaBlue,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ModeOutlineChip(
                label = "Con comida",
                selected = s.mode == ClockingMode.WITH_MEAL,
                enabled = canChangeMode,
                onClick = {
                    scope.launch {
                        repository.setMode(ClockingMode.WITH_MEAL).fold(
                            onSuccess = {
                                state = it
                                refreshWidget()
                            },
                            onFailure = { error = it.toUserMessage() }
                        )
                    }
                },
                modifier = Modifier.weight(1f)
            )
            ModeOutlineChip(
                label = "2 descansos",
                selected = s.mode == ClockingMode.TWO_BREAKS,
                enabled = canChangeMode,
                onClick = {
                    scope.launch {
                        repository.setMode(ClockingMode.TWO_BREAKS).fold(
                            onSuccess = {
                                state = it
                                refreshWidget()
                            },
                            onFailure = { error = it.toUserMessage() }
                        )
                    }
                },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = {
                scope.launch {
                    if (s.isFinished) {
                        startNewWorkday()
                    } else {
                        val action = nextAction ?: AttendanceAction.CLOCK_IN
                        repository.doAction(action).fold(
                            onSuccess = {
                                state = it
                                refreshWidget()
                            },
                            onFailure = { error = it.toUserMessage() }
                        )
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = s.isFinished || nextAction != null,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(2.dp, SygnaBlue),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = SygnaBlue,
                disabledContentColor = Gray300
            )
        ) {
            Text(
                if (s.isFinished) "Empezar nueva jornada" else "Fichar siguiente",
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(vertical = 6.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun ModeOutlineChip(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (selected) SygnaBlueLight else White,
            contentColor = if (selected) SygnaBlue else Gray500,
            disabledContainerColor = Gray100,
            disabledContentColor = Gray300
        ),
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) SygnaBlue else Gray300
        )
    ) {
        Text(label, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
    }
}

@Composable
private fun ActionCircleDrawable(
    title: String,
    caption: String? = null,
    enabled: Boolean,
    highlighted: Boolean,
    iconOn: Int,
    iconOff: Int,
    compact: Boolean = false,
    onClick: () -> Unit
) {
    val size = if (compact) 52.dp else 68.dp
    val iconSize = if (compact) 26.dp else 34.dp

    val bg = when {
        !enabled -> Gray100
        highlighted -> White
        else -> SygnaBlueLight
    }
    val borderW = when {
        !enabled -> 1.dp
        highlighted -> 2.dp
        else -> 1.dp
    }
    val borderC = when {
        !enabled -> Gray300
        highlighted -> SygnaBlue
        else -> SygnaBlue.copy(alpha = 0.45f)
    }
    val iconRes = if (enabled) iconOn else iconOff

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 6.dp)
    ) {
        Surface(
            onClick = onClick,
            enabled = enabled,
            shape = CircleShape,
            color = bg,
            modifier = Modifier
                .size(size)
                .border(borderW, borderC, CircleShape)
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(
                    painter = painterResource(id = iconRes),
                    contentDescription = title,
                    modifier = Modifier.size(iconSize),
                    tint = Color.Unspecified
                )
            }
        }
        when {
            compact && caption != null -> {
                val fg = when {
                    !enabled -> Gray500
                    highlighted -> SygnaBlue
                    else -> Gray500
                }
                Text(
                    caption,
                    fontSize = 9.sp,
                    color = fg,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
            }
            !compact -> {
                val fg = when {
                    !enabled -> Gray500
                    highlighted -> SygnaBlue
                    else -> SygnaBlue.copy(alpha = 0.85f)
                }
                Text(title, fontSize = 11.sp, color = fg, textAlign = TextAlign.Center, maxLines = 2)
            }
        }
    }
}

private fun formatElapsed(totalSeconds: Long): String {
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val sec = totalSeconds % 60
    return String.format(Locale.getDefault(), "%02d:%02d:%02d", h, m, sec)
}

@Composable
private fun ProfilePhotoAvatar(
    photoUri: String?,
    userName: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = SygnaBlueLight,
        border = BorderStroke(2.dp, SygnaBlue.copy(alpha = 0.18f)),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        if (photoUri.isNullOrBlank()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = profileInitials(userName),
                    color = SygnaBlue,
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp
                )
            }
        } else {
            AndroidView(
                factory = { viewContext ->
                    ImageView(viewContext).apply {
                        scaleType = ImageView.ScaleType.CENTER_CROP
                    }
                },
                update = { imageView ->
                    imageView.setImageURI(null)
                    imageView.setImageURI(Uri.parse(photoUri))
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

private fun profileInitials(userName: String): String {
    val parts = userName
        .trim()
        .split(Regex("\\s+"))
        .filter { it.isNotBlank() }
    return when {
        parts.isEmpty() -> "U"
        parts.size == 1 -> parts.first().take(1).uppercase(Locale.getDefault())
        else -> (parts.first().take(1) + parts.last().take(1)).uppercase(Locale.getDefault())
    }
}
