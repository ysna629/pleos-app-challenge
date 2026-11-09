/*
 * 주행 중에 보여 줄 전용 화면과 차량의 주행 제한 신호를 다룬다.
 *
 * -Author: Haneol Kim
 * -Contact: bestwook7@gmail.com
 */
package ai.pleos.challenge.arrival

import android.car.Car
import android.car.drivingstate.CarUxRestrictionsManager
import android.content.Context
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The manifest declares MainActivity distraction optimized, so the system no longer hides the app while driving.
 * Everything except [DrivingScreen] must therefore disappear here.
 */
fun showsDrivingScreen(restricted: Boolean, parked: Boolean?): Boolean = restricted || parked == false

/** The car's own driving restriction. It reveals no personal data, so it also protects the consent form. */
class DrivingRestriction(private val context: Context, private val onChange: (Boolean) -> Unit) {
    private var car: Car? = null
    private var manager: CarUxRestrictionsManager? = null

    fun start() {
        runCatching {
            car = Car.createCar(context)
            manager = car?.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as? CarUxRestrictionsManager
            manager?.registerListener { onChange(it.isRequiresDistractionOptimization) }
            manager?.currentCarUxRestrictions?.let { onChange(it.isRequiresDistractionOptimization) }
        }.onFailure { Log.w(TAG, "driving restriction", it) }
    }

    fun stop() {
        runCatching { manager?.unregisterListener() }
        runCatching { car?.disconnect() }
    }

    private companion object {
        const val TAG = "DrivingRestriction"
    }
}

/** Two large actions and one line of feedback: nothing to read, type or scan while driving. */
@Composable
fun DrivingScreen(consented: Boolean, listening: Boolean, heard: String, status: String, onSave: () -> Unit, onSpeak: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (!consented) {
            Text("정차한 뒤 개인정보 동의를 진행해 주세요.", fontSize = 36.sp, fontWeight = FontWeight.Bold, color = Color.White, textAlign = TextAlign.Center)
            return@Column
        }
        Text(
            if (listening) heard.ifBlank { "듣고 있어요…" } else status.ifBlank { "마음에 드는 곳을 지나면 저장하세요." },
            fontSize = 36.sp, fontWeight = FontWeight.Bold, color = Color.White, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(48.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
            Button(onClick = onSave, modifier = Modifier.size(width = 420.dp, height = 180.dp)) { Text("★ 여기 저장", fontSize = 44.sp) }
            OutlinedButton(onClick = onSpeak, enabled = !listening, modifier = Modifier.size(width = 420.dp, height = 180.dp)) { Text("🎤 말하기", fontSize = 44.sp) }
        }
    }
}
