package com.notexample.silksongisout

import android.media.MediaPlayer
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.silksongisout.ui.theme.SilksongisoutTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONException
import org.json.JSONObject
import androidx.compose.foundation.layout.Box // Import Box
import androidx.compose.foundation.layout.fillMaxWidth // To make the Box take full width

// Data class to represent the result
sealed class SilksongStatus {
    object Loading : SilksongStatus()
    data class Success(val isOut: Boolean) : SilksongStatus()
    data class Error(val message: String) : SilksongStatus()
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SilksongisoutTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    SilksongStatusScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}
var appid = "1030300" // Silksong App ID
// --- Network and Parsing Logic (can be in a separate file or ViewModel later) ---
suspend fun fetchSilksongDataFromApi(): String? {

    val client = OkHttpClient()
    val request = Request.Builder()
        .url("https://store.steampowered.com/api/appdetails?appids=${appid}&cc=us&l=en") // Silksong App ID
        .build()

    return try {
        withContext(Dispatchers.IO) {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    response.body?.string()
                } else {
                    // Log error or handle specific HTTP error codes
                    null
                }
            }
        }
    } catch (e: Exception) {
        // Log network exception
        e.printStackTrace()
        null
    }
}

fun parseSilksongname(jsonString: String?): String? {
    if (jsonString == null) {
        return "Failed to fetch data. Check network connection."
    }
    return try {
        val root = JSONObject(jsonString)
        // The API nests the actual app data under its app ID
        val appData = root.optJSONObject(appid) // Use optJSONObject for safety

        val data = appData?.optJSONObject("data") // Access 'data' then 'name'
        if (data == null) {
            return "Missing 'data' field in API response."
        }

        val gameName = data.optString("name")

        if (gameName.isNullOrEmpty()) { // Check if the extracted name is null or empty
            return "Missing 'name' field in API response."
        }

        return gameName // Return the extracted string name

    } catch (e: JSONException) {
        e.printStackTrace()
        return "Error parsing game name."
    }
}

fun parseSilksongReleaseStatus(jsonString: String?): SilksongStatus {
    if (jsonString == null) {
        return SilksongStatus.Error("Failed to fetch data. Check network connection.")
    }
    return try {
        val root = JSONObject(jsonString)
        // The API nests the actual app data under its app ID
        val appData = root.optJSONObject(appid) // Use optJSONObject for safety

        if (appData == null || !appData.optBoolean("success", false)) {
            return SilksongStatus.Error("Invalid data received from API or app not found.")
        }

        val data = appData.optJSONObject("data")
        if (data == null) {
            return SilksongStatus.Error("Missing 'data' field in API response.")
        }

        val releaseDateObject = data.optJSONObject("release_date")
        if (releaseDateObject == null) {
            return SilksongStatus.Error("Missing 'release_date' field in API response.")
        }

        // If "coming_soon" is true, it's not out. Otherwise, assume it is (or details are missing).
        val comingSoon =
            releaseDateObject.optBoolean("coming_soon", true) // Default to true if missing

        SilksongStatus.Success(isOut = !comingSoon)

    } catch (e: JSONException) {
        e.printStackTrace()
        SilksongStatus.Error("Error parsing game data.")
    }
}

// --- End of Network and Parsing Logic ---
@Composable
fun SilksongStatusScreen(modifier: Modifier = Modifier) {
    var statusResult by remember { mutableStateOf<SilksongStatus>(SilksongStatus.Loading) }
    val coroutineScope = rememberCoroutineScope()

    val context = LocalContext.current
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var gameName by remember { mutableStateOf<String?>("Silksong") } //parseSilksongname(fetchSilksongDataFromApi())

    // Function to play sound
    fun playAlarmSound() {
        mediaPlayer?.release() // Release any existing player
        // Ensure R.raw.alarm exists in your res/raw folder (e.g., res/raw/alarm.mp3)
        mediaPlayer = MediaPlayer.create(context, R.raw.alarm)

        mediaPlayer?.setOnPreparedListener {
            Log.d("SilksongStatusScreen", "MediaPlayer prepared, starting alarm.mp3.")
            it.start()
        }
        mediaPlayer?.setOnErrorListener { mp, what, extra ->
            Log.e("SilksongStatusScreen", "MediaPlayer error: what $what, extra $extra")
            mp.release()
            mediaPlayer = null // Reset state
            true // Error handled
        }
        mediaPlayer?.setOnCompletionListener {
            Log.d("SilksongStatusScreen", "MediaPlayer playback (alarm.mp3) completed.")
            it.release()
            mediaPlayer = null // Reset state
        }
    }

    // Function to stop and release sound
    fun stopAndReleaseSound() {
        if (mediaPlayer?.isPlaying == true) {
            mediaPlayer?.stop()
        }
        mediaPlayer?.release()
        mediaPlayer = null
    }

    fun loadStatus() {
        statusResult = SilksongStatus.Loading // Set to loading before each fetch
        coroutineScope.launch {
            val jsonData = fetchSilksongDataFromApi()
            statusResult = parseSilksongReleaseStatus(jsonData)
            gameName = parseSilksongname(jsonData)
        }
    }

    LaunchedEffect(Unit) { // Unit means this runs once when the composable enters the composition
        while (true) {
            Log.d("SilksongStatusScreen", "Periodic update: Loading status...")
            loadStatus() // Call the suspend function
            delay(60000L) // Delay for 60000 milliseconds (1 minute)
        }
    }

    // Effect to react to status changes for sound playback
    LaunchedEffect(statusResult) {

        if (statusResult is SilksongStatus.Success) {
            val successStatus = statusResult as SilksongStatus.Success
            if (successStatus.isOut) {
                // Only play if it's "YES"
                playAlarmSound()
            } else {
                // If it's "NO" or any other state after being "YES", stop the sound
                stopAndReleaseSound()
            }
        } else if (statusResult is SilksongStatus.Loading || statusResult is SilksongStatus.Error) {
            // Stop sound if loading or error occurs (e.g., during a refresh)
            stopAndReleaseSound()
        }
    }

    // Cleanup MediaPlayer when the composable is disposed
    DisposableEffect(Unit) {
        onDispose {
            Log.d("SilksongStatusScreen", "Disposing SilksongStatusScreen, releasing MediaPlayer.")
            stopAndReleaseSound()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize() // Make the Box fill the available space
    ) {

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {




        Text(
            text = "Is ${gameName} Out?",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(32.dp))

        when (val currentStatus = statusResult) {
            is SilksongStatus.Loading -> {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text("Checking...", fontSize = 20.sp)
            }

            is SilksongStatus.Success -> {
                Text(
                    text = if (currentStatus.isOut) "YES" else "NO",
                    fontSize = 80.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (currentStatus.isOut) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error
                )

            }

            is SilksongStatus.Error -> {
                Text(
                    text = "Error",
                    fontSize = 60.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(currentStatus.message, fontSize = 16.sp)
            }
        }

        Spacer(modifier = Modifier.height(48.dp))
        Button(onClick = { loadStatus() }) { // Allow manual refresh
            Text("Refresh Status")
        }



    }

    Button(
        onClick = {
            // Action for the new button
            Log.d("SilksongStatusScreen", "New Button Clicked!")
            appid="3677050"//"427520"
            loadStatus()
            // You can add any other logic here, like navigating to another screen,
            // showing a dialog, or performing another action.
        },
        modifier =  Modifier
            .align(Alignment.TopEnd) // Align this IconButton to the top-end of the Box
            .padding(16.dp) // Add some padding so it's not flush against the edges
    ) {
        Text("Settings") // Text for the new button
    }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun SilksongStatusScreenPreviewLoading() {
    SilksongisoutTheme {
        // To preview loading, we can directly set the state if we extract the Column
        // For simplicity, this preview will also trigger the LaunchedEffect.
        SilksongStatusScreen()
    }
}

@Preview(showBackground = true)
@Composable
fun SilksongStatusScreenPreviewYes() {
    SilksongisoutTheme {
        // Manually create the state for previewing specific scenarios
        val statusResult = SilksongStatus.Success(isOut = true)
        // This is a simplified version of the screen for previewing:
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("Is Silksong Out?", fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(32.dp))
            Text(
                "YES",
                fontSize = 80.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.tertiary
            )
            Spacer(modifier = Modifier.height(48.dp))
            Button(onClick = {}) { Text("Refresh Status") }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun SilksongStatusScreenPreviewNo() {
    SilksongisoutTheme {
        val statusResult = SilksongStatus.Success(isOut = false)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("Is Silksong Out?", fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(32.dp))
            Text(
                "NO",
                fontSize = 80.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(48.dp))
            Button(onClick = {}) { Text("Refresh Status") }
        }
    }
}