package com.not2example.silksongisout

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.util.Log
import android.widget.RemoteViews
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

import okhttp3.OkHttpClient
import okhttp3.Request
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject

import android.media.MediaPlayer

import android.content.Intent
import android.app.PendingIntent
import android.content.ComponentName
import java.util.Date
// Constants for broadcasting to MainActivity (if you still want that functionality)
// const val ACTION_UPDATE_SILKSONG_STATUS = "com.example.silksongisout.UPDATE_SILKSONG_STATUS"
// const val EXTRA_SILKSONG_STATUS = "com.example.silksongisout.SILKSONG_STATUS"



class MyWidgetProvider : AppWidgetProvider() {
    private var mediaPlayer: MediaPlayer? = null

    companion object {
        const val ACTION_REFRESH = "com.example.widget.ACTION_REFRESH"
    }


    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val scope = CoroutineScope(Dispatchers.IO)

        scope.launch {
            Log.d("MyWidgetProvider", "Coroutine launched")
            val json = fetchSilksongWidgetData()
            Log.d("MyWidgetProvider", "Fetched JSON: $json")
            val textForWidget = parseSilksongStatusForWidget(context,json)
            Log.d("MyWidgetProvider", "Text for widget: $textForWidget")
            //}
            if (intent.action == ACTION_REFRESH) {
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val componentName = ComponentName(context, MyWidgetProvider::class.java)
                val widgetIds = appWidgetManager.getAppWidgetIds(componentName)

                for (widgetId in widgetIds) {
                    val views = RemoteViews(context.packageName, R.layout.widget_layout)
                    views.setTextViewText(R.id.widget_text, "Refreshed: ${Date()} ${textForWidget}")

                    appWidgetManager.updateAppWidget(widgetId, views)
                }
            }
        }
    }
    // fetchSilksongData remains the same as your previous version or the app's version
    suspend fun fetchSilksongWidgetData(): String? { // Renamed to avoid confusion if you have both
        val client = OkHttpClient()
        val request = Request.Builder()
            .url("https://store.steampowered.com/api/appdetails?appids=1030300&cc=us&l=en")
            .build()
        return try {
            withContext(Dispatchers.IO) {
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val bodyString = response.body?.string()
                        Log.d("fetchSilksongData","Succes?:${response.isSuccessful} RESPONSE: ${bodyString}")
                        bodyString
                    } else {
                        Log.d("fetchSilksongData","Succes?:${response.isSuccessful}")
                        Log.w("fetchSilksongData", "Failed request: ${response.code}")
                        null
                    }
                    }
                }

        } catch (e: Exception) {
            Log.e("fetchSilksongData", "Exception in fetchSilksongWidgetData", e)
            null
        }
    }

    /**
     * Parses the JSON response to determine Silksong's release status for the widget.
     * Returns "YES" if out, "COMING SOON" if not, or an error message.
     */

    fun parseSilksongStatusForWidget(context: Context,jsonString: String?): String {

        if (jsonString == null) {
            return "ERROR by network issue" // Or "N/A", "Failed to load"
        }
        return try {
            val root = JSONObject(jsonString)
            val appData = root.optJSONObject("1030300")

            if (appData == null || !appData.optBoolean("success", false)) {
                return "API ERROR" // Or more descriptive
            }

            val data = appData.optJSONObject("data")
            if (data == null) {
                return "DATA ERR" // Or more descriptive
            }

            val releaseDateObject = data.optJSONObject("release_date")
            if (releaseDateObject == null) {
                return "DATE ERR" // Or more descriptive
            }

            val comingSoon =
                releaseDateObject.optBoolean("coming_soon", true) // Default to true if missing

            if (comingSoon) {
                return "COMING SOON2"
            } else {
                // Check if it has a price or any indication it's actually released
                // For simplicity, if not "coming_soon", we'll assume "YES"
                // You could add more checks here (e.g., presence of price_overview) if needed

                // Ensure R.raw.alarm exists in your res/raw folder
                mediaPlayer?.release() // Release any existing player
                mediaPlayer = MediaPlayer.create(context, R.raw.alarm) // Use the passed context

                mediaPlayer?.setOnPreparedListener {
                    Log.d("MyWidgetProvider", "MediaPlayer prepared, starting playback.")
                    it.start()
                }

                mediaPlayer?.setOnErrorListener { mp, what, extra ->
                    Log.e("MyWidgetProvider", "MediaPlayer error: what $what, extra $extra")
                    // Optionally release here too, or attempt to reset
                    mp.release()
                    mediaPlayer = null
                    true // True if the error has been handled
                }

                mediaPlayer?.setOnCompletionListener {
                    Log.d("MyWidgetProvider", "MediaPlayer playback completed.")
                    it.release()
                    mediaPlayer = null
                }

                return "YES"
            }
        } catch (e: JSONException) {
            e.printStackTrace()
            "PARSE ERR" // Or more descriptive
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        Log.d("MyWidgetProvider", "onUpdate called for IDs: ${appWidgetIds.joinToString()}")
        val scope = CoroutineScope(Dispatchers.Main)

        scope.launch {
            Log.d("MyWidgetProvider", "Coroutine launched")
            val json = fetchSilksongWidgetData()
            Log.d("MyWidgetProvider", "Fetched JSON: $json")
            val textForWidget = parseSilksongStatusForWidget(context,json)
            Log.d("MyWidgetProvider", "Text for widget: $textForWidget")

            for (appWidgetId in appWidgetIds) {
                Log.d("MyWidgetProvider", "Updating widget ID: $appWidgetId")
                val views = RemoteViews(context.packageName, R.layout.widget_layout)
                views.setTextViewText(R.id.widget_text, textForWidget)
                appWidgetManager.updateAppWidget(appWidgetId, views)
                Log.d("MyWidgetProvider", "Widget ID $appWidgetId updated")


                // Setup refresh button intent
                val intent = Intent(context, MyWidgetProvider::class.java).apply {
                    action = ACTION_REFRESH
                }
                val pendingIntent = PendingIntent.getBroadcast(
                    context, 0, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_refresh_button, pendingIntent)

                // Initial text
                views.setTextViewText(R.id.widget_text, "Updated: ${Date()} ${textForWidget}")

                appWidgetManager.updateAppWidget(appWidgetId, views)

            }
        }
    }

/*override fun onUpdate(
    context: Context,
    appWidgetManager: AppWidgetManager,
    appWidgetIds: IntArray
) {
    val scope = CoroutineScope(Dispatchers.Main)

    scope.launch {
        val json = fetchSilksongWidgetData()
        val textForWidget = parseSilksongStatusForWidget(json)

        // Update all instances of this widget
        for (appWidgetId in appWidgetIds) {
            val views = RemoteViews(
                context.packageName,
                R.layout.widget_layout
            ) // Ensure this layout exists
            views.setTextViewText(
                R.id.widget_text,
                textForWidget
            ) // Ensure R.id.widget_text exists in widget_layout.xml
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        // Optional: If you still want to broadcast to MainActivity
        // val updateIntent = Intent(ACTION_UPDATE_SILKSONG_STATUS).apply {
        //     putExtra(EXTRA_SILKSONG_STATUS, textForWidget) // Or a more detailed status
        // }
        // context.sendBroadcast(updateIntent)
    }
}*/
}