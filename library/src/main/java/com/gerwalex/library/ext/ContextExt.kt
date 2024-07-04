package com.gerwalex.library.ext

import android.app.ActivityManager
import android.app.PendingIntent
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


fun Context.toast(message: String, duration: Int = Toast.LENGTH_LONG) {
    MainScope().launch {
        Toast.makeText(this@toast, message, duration).show()
    }
}

fun Context.restart() {
    packageManager.getLaunchIntentForPackage(packageName)?.let {
        val mainIntent = Intent.makeRestartActivityTask(it.component)
        startActivity(mainIntent)
    }
}

val Context.activity: AppCompatActivity?
    get() {
        return when (this) {
            is AppCompatActivity -> this
            is ContextWrapper -> baseContext.activity
            else -> null
        }
    }

fun Context.toast(@StringRes message: Int, duration: Int = Toast.LENGTH_LONG) {
    toast(getString(message), duration)
}

/**
 * Usage:
 *
 * launchActivity<ActivityToStart> {
 * putExtra(INTENT_USER_ID, user.id)
 * }
 * or
 * launchActivity<ActivityToStart> {
 * putExtra(INTENT_USER_ID, user.id)
 * addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
 * }
 */

inline fun <reified T : Any> Context.launchActivity(
    options: Bundle? = null,
    noinline init: Intent.() -> Unit = {}
) {
    val intent = newIntent<T>(this)
    intent.init()
    startActivity(intent, options)
}

inline fun <reified T : Any> newIntent(context: Context): Intent =
    Intent(context, T::class.java)

/**
 * Color from ColorRes
 */
fun Context.getCompatColor(@ColorRes colorId: Int) =
    ResourcesCompat.getColor(resources, colorId, null)

/**
 * Drawable from DrawableRes
 */
fun Context.getCompatDrawable(@DrawableRes drawableId: Int) =
    AppCompatResources.getDrawable(this, drawableId)!!

/**
 * Check for Permission
 */
fun Context.hasPermissions(vararg permissions: String) = permissions.all { permission ->
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
}

/**
 * Prüft auf einen Job im Workmanager
 * @param workName Name of Work
 * @return WorkInfo.State state of Work or null, when not found
 */
suspend fun Context.getUniqueWorkInfoState(workName: String): WorkInfo.State? {
    val workManager = WorkManager.getInstance(this)
    return withContext(Dispatchers.IO) {
        val workInfos =
            workManager.getWorkInfosForUniqueWork(workName).get()
        val result = if (workInfos.size == 1) {
            // for (workInfo in workInfos) {
            val workInfo = workInfos[0]
            Log.d("WorkManager", "workInfo.state=${workInfo.state}, id=${workInfo.id}")
            when (workInfo.state) {
                WorkInfo.State.ENQUEUED -> Log.d("WorkManager", "$workName is enqueued and alive")
                WorkInfo.State.RUNNING -> Log.d("WorkManager", "$workName is running and alive")
                WorkInfo.State.SUCCEEDED -> Log.d("WorkManager", "$workName has succeded")
                WorkInfo.State.FAILED -> Log.d("WorkManager", "$workName has failed")
                WorkInfo.State.BLOCKED -> Log.d("WorkManager", "$workName is blocked and Alive")
                WorkInfo.State.CANCELLED -> Log.d("WorkManager", "$workName is cancelled")
            }
            workInfo.state
        } else {
            null
        }
        result
    }
}

/**
 * Prüft auf einen Job im Workmanager
 * @param workName Name of Work
 * @return WorkInfo.State state of Work or null, when not found
 */
suspend fun Context.isWorkRunning(workName: String): Boolean {
    val workManager = WorkManager.getInstance(this)
    return withContext(Dispatchers.IO) {
        val workInfos =
            workManager.getWorkInfosForUniqueWork(workName).get()

        var running = false
        workInfos.forEach { workInfo ->
            Log.d("WorkManager", "workInfo.state=${workInfo.state}, id=${workInfo.id}")
            running = running
                    //                || workInfo.state == WorkInfo.State.ENQUEUED
                    || workInfo.state == WorkInfo.State.RUNNING
        }
        Log.d("WorkRunning", "$workName is running = $running")
        running
    }
}

val Context.batteryIntent: Intent?
    get() {
        return registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    }
val Context.batteryManager: BatteryManager
    get() {
        return getSystemService(Context.BATTERY_SERVICE) as BatteryManager
    }

val Context.powerManager: PowerManager
    get() {
        return getSystemService(Context.POWER_SERVICE) as PowerManager
    }


val Context.connectivityManager: ConnectivityManager
    get() {
        return getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    }
val Context.activityManager: ActivityManager
    get() {
        return getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    }

fun Context.isNetworkConnected(): Boolean {
    val hasInternet: Boolean

    val networkCapabilities = connectivityManager.activeNetwork ?: return false
    val actNw =
        connectivityManager.getNetworkCapabilities(networkCapabilities) ?: return false
    hasInternet = when {
        actNw.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
        actNw.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
        actNw.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> true
        else -> false
    }
    return hasInternet
}

/**
 * Vibriert
 * @param Dauer, default 500ms
 */
@Suppress("DEPRECATION")
fun Context.vibrate(time: Long = 500) {
    val vibrator =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        vibrator.vibrate(VibrationEffect.createOneShot(time, VibrationEffect.DEFAULT_AMPLITUDE))
    } else {
        vibrator.vibrate(time)
    }
}

/**
 * Liefert einen PendingIntent mit Flag Mutable
 */
fun Context.pendingIntent(intent: Intent, requestCode: Int = 0, flags: Int = 0): PendingIntent {
    val piFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
        PendingIntent.FLAG_MUTABLE or flags else flags
    return PendingIntent.getActivity(this, requestCode, intent, piFlags)

}

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

fun Context.isInNightMode(): Boolean {
    val nightModeFlags: Int = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
    return when (nightModeFlags) {
        Configuration.UI_MODE_NIGHT_YES -> true
        Configuration.UI_MODE_NIGHT_NO -> false
        Configuration.UI_MODE_NIGHT_UNDEFINED -> false
        else -> {
            false
        }
    }
}

fun Context.openAppSystemSettings() {
    startActivity(Intent().apply {
        action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
        data = Uri.fromParts("package", packageName, null)
    })
}