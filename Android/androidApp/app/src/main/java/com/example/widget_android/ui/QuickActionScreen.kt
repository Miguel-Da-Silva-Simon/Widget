package com.example.widget_android.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.widget_android.R
import com.example.widget_android.theme.Gray100
import com.example.widget_android.theme.Gray300
import com.example.widget_android.theme.Gray500
import com.example.widget_android.theme.SygnaBlue
import com.example.widget_android.theme.SygnaBlueLight
import com.example.widget_android.theme.White

@Composable
fun QuickActionScreen(
    destination: QuickActionDestination,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Gray100)
            .padding(horizontal = 22.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Surface(
            onClick = onBack,
            modifier = Modifier.size(40.dp),
            shape = CircleShape,
            border = BorderStroke(1.dp, SygnaBlue.copy(alpha = 0.35f)),
            color = White,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.quick_action_cd_back),
                    tint = SygnaBlue
                )
            }
        }

        Text(
            text = stringResource(R.string.quick_actions_widget_title),
            style = MaterialTheme.typography.titleLarge,
            color = SygnaBlue,
            fontWeight = FontWeight.SemiBold
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = White),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            shape = RoundedCornerShape(28.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    modifier = Modifier.size(80.dp),
                    shape = CircleShape,
                    color = SygnaBlueLight,
                    border = BorderStroke(1.dp, SygnaBlue.copy(alpha = 0.18f)),
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = destination.iconRes),
                            contentDescription = null,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                Text(
                    text = stringResource(destination.titleRes),
                    style = MaterialTheme.typography.headlineSmall,
                    color = SygnaBlue,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = stringResource(destination.subtitleRes),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = stringResource(R.string.quick_action_screen_hint),
                    fontSize = 14.sp,
                    color = Gray500,
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = SygnaBlue,
                contentColor = White,
                disabledContainerColor = Gray300,
                disabledContentColor = White
            )
        ) {
            Text(
                text = stringResource(R.string.quick_action_back_to_dashboard),
                modifier = Modifier.padding(vertical = 4.dp),
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
