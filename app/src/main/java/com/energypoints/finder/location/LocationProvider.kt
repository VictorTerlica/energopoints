package com.energypoints.finder.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Получение текущей геопозиции средствами платформы (без Play Services).
 *
 * Сначала используется свежая lastKnownLocation, затем одиночный запрос
 * координат у GPS/Network-провайдера с таймаутом. Если точное разрешение
 * не выдано (Android 12 «Примерно»), используется только Network-провайдер.
 */
class LocationProvider(private val context: Context) {

    fun hasPermission(): Boolean = hasFinePermission() || hasCoarsePermission()

    suspend fun getCurrentLocation(): Location? = withContext(Dispatchers.Main.immediate) {
        if (!hasPermission()) return@withContext null

        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return@withContext null

        val gpsEnabled = runCatching {
            manager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        }.getOrDefault(false)
        val networkEnabled = runCatching {
            manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        }.getOrDefault(false)

        val providers = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER
        )
        val lastKnown = providers
            .mapNotNull { provider ->
                runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
            }
            .maxByOrNull { it.time }

        if (lastKnown != null &&
            System.currentTimeMillis() - lastKnown.time < LAST_KNOWN_MAX_AGE_MS
        ) {
            return@withContext lastKnown
        }

        val provider = when {
            hasFinePermission() && gpsEnabled -> LocationManager.GPS_PROVIDER
            networkEnabled -> LocationManager.NETWORK_PROVIDER
            else -> null
        }

        if (provider == null) {
            return@withContext lastKnown
        }

        val fresh = requestSingleLocation(manager, provider)
        fresh ?: lastKnown
    }

    @Suppress("DEPRECATION")
    private suspend fun requestSingleLocation(
        manager: LocationManager,
        provider: String
    ): Location? {
        return withTimeoutOrNull(LOCATION_TIMEOUT_MS) {
            suspendCancellableCoroutine<Location?> { continuation ->
                val listener = object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        runCatching { manager.removeUpdates(this) }
                        if (continuation.isActive) {
                            continuation.resume(location)
                        }
                    }

                    override fun onProviderDisabled(provider: String) = Unit

                    override fun onProviderEnabled(provider: String) = Unit

                    @Deprecated("Deprecated in Java")
                    override fun onStatusChanged(
                        provider: String?,
                        status: Int,
                        extras: Bundle?
                    ) = Unit
                }

                continuation.invokeOnCancellation {
                    runCatching { manager.removeUpdates(listener) }
                }

                try {
                    manager.requestSingleUpdate(
                        provider,
                        listener,
                        Looper.getMainLooper()
                    )
                } catch (e: SecurityException) {
                    if (continuation.isActive) {
                        continuation.resume(null)
                    }
                } catch (e: Exception) {
                    if (continuation.isActive) {
                        continuation.resume(null)
                    }
                }
            }
        }
    }

    private fun hasFinePermission(): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

    private fun hasCoarsePermission(): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

    private companion object {
        const val LAST_KNOWN_MAX_AGE_MS = 5 * 60 * 1000L
        const val LOCATION_TIMEOUT_MS = 12_000L
    }
}
