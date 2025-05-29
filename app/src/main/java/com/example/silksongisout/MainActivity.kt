package com.example.silksongisout

import android.os.Bundle
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.silksongisout.ui.theme.SilksongisoutTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONException
import org.json.JSONObject

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

// --- Network and Parsing Logic (can be in a separate file or ViewModel later) ---
suspend fun fetchSilksongDataFromApi(): String? {
    val client = OkHttpClient()
    val request = Request.Builder()
        .url("https://store.steampowered.com/api/appdetails?appids=1030300&cc=us&l=en") // Silksong App ID
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

fun parseSilksongReleaseStatus(jsonString: String?): SilksongStatus {
    if (jsonString == null) {
        return SilksongStatus.Error("Failed to fetch data. Check network connection.")
    }
    return try {
        val root = JSONObject(jsonString)
        // The API nests the actual app data under its app ID
        val appData = root.optJSONObject("1030300") // Use optJSONObject for safety

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

    fun loadStatus() {
        statusResult = SilksongStatus.Loading // Set to loading before each fetch
        coroutineScope.launch {
            val jsonData = fetchSilksongDataFromApi()
            statusResult = parseSilksongReleaseStatus(jsonData)
        }
    }

    // Fetch data when the screen is first composed
    LaunchedEffect(Unit) { // Unit means this runs once when the composable enters the composition
        loadStatus()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Is Silksong Out?",
            fontSize = 28.sp,
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