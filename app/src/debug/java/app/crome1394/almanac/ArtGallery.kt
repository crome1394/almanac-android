package app.crome1394.almanac

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.crome1394.almanac.data.EventTheme
import app.crome1394.almanac.ui.EventArt
import app.crome1394.almanac.ui.MonthScene

/**
 * Debug only: every month banner, or with `--ez events true` every event picture, for checking
 * the artwork. `adb shell am start -n app.crome1394.almanac.debug/app.crome1394.almanac.ArtGallery [--ez dark true]`
 */
class ArtGallery : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dark = intent.getBooleanExtra("dark", false)
        val events = intent.getBooleanExtra("events", false)
        setContent {
            LazyVerticalGrid(GridCells.Fixed(2), Modifier.fillMaxSize().background(if (dark) Color(0xFF131314) else Color.White).padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!events) items(12) { m ->
                    Box(Modifier.fillMaxWidth().height(96.dp).clip(RoundedCornerShape(12.dp))) {
                        MonthScene(m + 1, dark, { 0f }, Modifier.fillMaxSize())
                        Text("${m + 1}", Modifier.padding(6.dp), color = if (dark || m == 11) Color.White else Color.Black)
                    }
                } else items(EventTheme.entries.size) { k ->
                    val t = EventTheme.entries[k]
                    Box(Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(8.dp))) {
                        EventArt(t, Color(0xFF039BE5), dark, Modifier.fillMaxSize())
                        Text(t.name, Modifier.padding(6.dp), fontSize = 11.sp, color = if (dark) Color.White else Color.Black)
                    }
                }
            }
        }
    }
}
