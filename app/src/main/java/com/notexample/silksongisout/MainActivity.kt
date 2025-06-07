package com.notexample.silksongisout

import android.content.Context
import android.content.SharedPreferences
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
//import androidx.compose.runtime.DisposableEffect
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
//import androidx.compose.foundation.layout.fillMaxWidth // To make the Box take full width


import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import kotlin.text.filter
import kotlin.text.isDigit
import android.widget.Toast

// --- SharedPreferences for appid ---
private const val PREFS_APP_SETTINGS = "AppSettingsPrefs"
private const val PREF_KEY_APP_ID = "steam_app_id"
const val DEFAULT_APP_ID = "1030300" // Default Silksong App ID

fun saveAppId(context: Context, appId: String) {
    val prefs: SharedPreferences = context.getSharedPreferences(PREFS_APP_SETTINGS, Context.MODE_PRIVATE)
    prefs.edit().putString(PREF_KEY_APP_ID, appId).apply()
}

fun getSavedAppId(context: Context): String {
    val prefs: SharedPreferences = context.getSharedPreferences(PREFS_APP_SETTINGS, Context.MODE_PRIVATE)
    return prefs.getString(PREF_KEY_APP_ID, DEFAULT_APP_ID) ?: DEFAULT_APP_ID
}
// --- End of SharedPreferences ---

// Data class to represent the result
sealed class SilksongStatus {
    data object Loading : SilksongStatus()
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

@Composable
fun ScaledText(
    modifier: Modifier = Modifier,
    text: String,
    maxFontSize: TextUnit = 40.sp,
    minFontSize: TextUnit = 10.sp,
    fontWeight: FontWeight = FontWeight.Bold,
    color: Color = MaterialTheme.colorScheme.primary
) {
    BoxWithConstraints(modifier = modifier) {
        val measurer = rememberTextMeasurer()
        var bestFontSize by remember { mutableStateOf(maxFontSize) }

        val constraintsWidth = constraints.maxWidth.toFloat()

        LaunchedEffect(text, maxFontSize, minFontSize, constraintsWidth) {
            var testSize = maxFontSize
            while (testSize > minFontSize) {
                val result = measurer.measure(
                    text = AnnotatedString(text),
                    style = TextStyle(fontSize = testSize, fontWeight = fontWeight)
                )
                if (result.size.width <= constraintsWidth) {
                    break
                }
                testSize *= 0.95f // Fokozatosan csökkenti a méretet
            }
            bestFontSize = testSize
        }

        Text(
            text = text,
            fontSize = bestFontSize*0.95,
            fontWeight = fontWeight,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Clip
        )
    }
}

@Composable
fun SilksongStatusScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current // Get context early

    // State for the current appid, initialized from SharedPreferences
    var currentAppId by remember { mutableStateOf(getSavedAppId(context)) }

    var statusResult by remember { mutableStateOf<SilksongStatus>(SilksongStatus.Loading) }
    val coroutineScope = rememberCoroutineScope()
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var gameName by remember { mutableStateOf<String?>("Silksong") } // Default or load dynamically

    // State for the settings dialog
    var showSettingsDialog by remember { mutableStateOf(false) }
    var tempAppIdInput by remember(currentAppId) { mutableStateOf(currentAppId) } // Initialize with currentAppId

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

    // --- Network and Parsing Logic (modified to use currentAppId) ---
    suspend fun fetchSilksongDataFromApi(appIdToFetch: String): String? { // Pass appid as parameter
        val client = OkHttpClient()
        val request = Request.Builder()
            .url("https://store.steampowered.com/api/appdetails?appids=${appIdToFetch}&cc=us&l=en")
            .build()
        // ... (rest of your fetchSilksongDataFromApi implementation)
        // Make sure to log with appIdToFetch if you have logging inside
        return try {
            withContext(Dispatchers.IO) {
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val bodyString = response.body?.string()
                        Log.d(
                            "fetchSilksongData",
                            "AppID: $appIdToFetch, Success?:${response.isSuccessful} RESPONSE: $bodyString"
                        )
                        bodyString
                    } else {
                        Log.w(
                            "fetchSilksongData",
                            "AppID: $appIdToFetch, Failed request: ${response.code}"
                        )
                        null
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(
                "fetchSilksongData",
                "AppID: $appIdToFetch, Exception in fetchSilksongDataFromApi",
                e
            )
            null
        }
    }

    fun parseSilksongNameFromApi(jsonString: String?, appIdToParse: String): String? { // Pass appid
        if (jsonString == null) {
            return "Failed to fetch. Check connection or AppID."
        }
        return try {
            val root = JSONObject(jsonString)
            val appData = root.optJSONObject(appIdToParse)
            val data = appData?.optJSONObject("data") ?: return "Missing 'data' in API response."
            val name = data.optString("name")
            if (name.isNullOrEmpty()) "Unknown Game" else name
        } catch (e: JSONException) {
            e.printStackTrace()
            "Error parsing name."
        }
    }

    fun parseSilksongReleaseStatusFromApi(
        jsonString: String?,
        appIdToParse: String
    ): SilksongStatus { // Pass appid
        if (jsonString == null) {
            return SilksongStatus.Error("Failed to fetch. Check connection or AppID.")
        }
        return try {
            val root = JSONObject(jsonString)
            val appData = root.optJSONObject(appIdToParse)
            if (appData == null || !appData.optBoolean("success", false)) {
                return SilksongStatus.Error("Invalid AppID or API error.")
            }
            val data = appData.optJSONObject("data")
                ?: return SilksongStatus.Error("Missing 'data' in API response.")
            val releaseDateObject = data.optJSONObject("release_date")
                ?: return SilksongStatus.Error("Missing 'release_date'.")
            val comingSoon = releaseDateObject.optBoolean("coming_soon", true)
            SilksongStatus.Success(isOut = !comingSoon)
        } catch (e: JSONException) {
            e.printStackTrace()
            SilksongStatus.Error("Error parsing data.")
        }
    }
    // --- End of Modified Network and Parsing Logic ---


    fun loadStatus(appIdToLoad: String = currentAppId) { // Use currentAppId by default
        statusResult = SilksongStatus.Loading
        coroutineScope.launch {
            Log.d("SilksongStatusScreen", "Loading status for AppID: $appIdToLoad")
            val jsonData = fetchSilksongDataFromApi(appIdToLoad)
            statusResult = parseSilksongReleaseStatusFromApi(jsonData, appIdToLoad)
            gameName = parseSilksongNameFromApi(jsonData, appIdToLoad) ?: "Game"
            if (statusResult is SilksongStatus.Error) {
                // If loading failed for a new app ID, perhaps revert gameName or handle
                Log.w(
                    "SilksongStatusScreen",
                    "Failed to load status for $appIdToLoad, result: $statusResult"
                )
            }
        }
    }

    // Initial load and periodic refresh using the currentAppId
    LaunchedEffect(currentAppId) { // React to changes in currentAppId
        loadStatus(currentAppId) // Initial load with the current app ID

        // Optional: If you want periodic refresh for the current app ID
        launch {
            while (true) {
                delay(6000L) // Delay for 1 minute
                Log.d("SilksongStatusScreen", "Periodic update for AppID: $currentAppId")
                loadStatus(currentAppId)
            }
        }

    }

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


    // ... (your existing playAlarmSound, stopAndReleaseSound, LaunchedEffect for statusResult, DisposableEffect for mediaPlayer)

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

            ScaledText(text = "Is $gameName Out")
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
            Button(onClick = { loadStatus(currentAppId) }) { // Refresh with current app ID
                Text("Refresh Status")
            }
        }

        Button(
            onClick = {
                tempAppIdInput = currentAppId // Reset input field to current when opening
                showSettingsDialog = true
            },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
        ) {
            Text("Settings")
        }

        // Settings Dialog
        if (showSettingsDialog) {
            AlertDialog(
                onDismissRequest = { showSettingsDialog = false },
                title = { Text("Settings") },
                text = {
                    Column {
                        Text("Enter Steam App ID:")
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = tempAppIdInput,
                            onValueChange = {
                                tempAppIdInput = it.filter { char -> char.isDigit() }
                            }, // Allow only digits
                            label = { Text("App ID") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (tempAppIdInput.isNotBlank()) {
                                saveAppId(context, tempAppIdInput)
                                currentAppId = tempAppIdInput // Update the currentAppId state
                                // loadStatus will be triggered by LaunchedEffect(currentAppId)
                                showSettingsDialog = false
                                Log.d(
                                    "SilksongStatusScreen",
                                    "AppID saved and updated to: $currentAppId"
                                )
                            } else {
                                // Optionally show a toast or error that app ID cannot be empty
                                Toast.makeText(
                                    context,
                                    "App ID cannot be empty",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    ) {
                        Text("Save")
                    }
                },
                dismissButton = {
                    Button(onClick = { showSettingsDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}
//*/

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
        //val statusResult = SilksongStatus.Success(isOut = true)
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
        //val statusResult = SilksongStatus.Success(isOut = false)
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