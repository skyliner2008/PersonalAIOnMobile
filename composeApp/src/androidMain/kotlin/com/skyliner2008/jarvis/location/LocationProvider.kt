package com.skyliner2008.jarvis.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

/**
 * LocationProvider — อ่านพิกัด GPS ปัจจุบันและแปลงเป็นที่อยู่/ตำบล/จังหวัด สำหรับโหมดนำทางและ AI Grounding
 *
 * ใช้ Android standard LocationManager ไม่ต้องพึ่งพา Google Play Services ภายนอก
 */
class LocationProvider(private val context: Context) {

    companion object {
        private const val TAG = "JarvisLocation"
    }

    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

    data class LocationResult(
        val latitude: Double,
        val longitude: Double,
        val accuracy: Float,
        val address: String?,
        val speedKmh: Float = 0f
    ) {
        fun formatSummary(): String {
            val addrText = if (!address.isNullOrBlank()) "\n📍 ที่อยู่: $address" else ""
            val speedText = if (speedKmh > 1f) "\n🚗 ความเร็ว: ${speedKmh.toInt()} กม./ชม." else ""
            return "🌐 พิกัดปัจจุบัน: Lat %.5f, Lng %.5f (ความแม่นยำ %.0f ม.)$addrText$speedText".format(latitude, longitude, accuracy)
        }
    }

    fun hasPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
        return fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED
    }

    fun isGpsEnabled(): Boolean {
        return locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true ||
               locationManager?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) == true
    }

    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(): LocationResult? {
        if (!hasPermission()) {
            Log.w(TAG, "Location permission not granted")
            return null
        }
        val lm = locationManager ?: return null

        // 1. ลอง Last Known Location ก่อน (ได้ผลทันที ไม่ต้องรอ fix)
        val lastGps = try { lm.getLastKnownLocation(LocationManager.GPS_PROVIDER) } catch (_: Exception) { null }
        val lastNetwork = try { lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER) } catch (_: Exception) { null }

        val bestLast = when {
            lastGps != null && lastNetwork != null -> if (lastGps.time >= lastNetwork.time) lastGps else lastNetwork
            lastGps != null -> lastGps
            else -> lastNetwork
        }

        // หาก last known สดพอ (ไม่เกิน 2 นาที) ใช้ได้เลย
        val now = System.currentTimeMillis()
        if (bestLast != null && (now - bestLast.time) < 120_000) {
            val address = getAddress(bestLast.latitude, bestLast.longitude)
            val speedKmh = if (bestLast.hasSpeed()) bestLast.speed * 3.6f else 0f
            return LocationResult(bestLast.latitude, bestLast.longitude, bestLast.accuracy, address, speedKmh)
        }

        // 2. ถ้าไม่มีหรือเก่าเกินไป ขอ fresh location update รอสูงสุด 5 วินาที
        val freshLoc = withTimeoutOrNull(5000L) {
            requestSingleUpdate()
        } ?: bestLast

        if (freshLoc != null) {
            val address = getAddress(freshLoc.latitude, freshLoc.longitude)
            val speedKmh = if (freshLoc.hasSpeed()) freshLoc.speed * 3.6f else 0f
            return LocationResult(freshLoc.latitude, freshLoc.longitude, freshLoc.accuracy, address, speedKmh)
        }

        return null
    }

    @SuppressLint("MissingPermission")
    private suspend fun requestSingleUpdate(): Location? = suspendCancellableCoroutine { cont ->
        val lm = locationManager
        if (lm == null) {
            cont.resume(null)
            return@suspendCancellableCoroutine
        }

        val provider = when {
            lm.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            else -> {
                cont.resume(null)
                return@suspendCancellableCoroutine
            }
        }

        val listener = object : LocationListener {
            override fun onLocationChanged(loc: Location) {
                try { lm.removeUpdates(this) } catch (_: Exception) {}
                if (cont.isActive) cont.resume(loc)
            }
            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {
                if (cont.isActive) cont.resume(null)
            }
        }

        cont.invokeOnCancellation {
            try { lm.removeUpdates(listener) } catch (_: Exception) {}
        }

        try {
            lm.requestSingleUpdate(provider, listener, null)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to request single update: ${e.message}", e)
            if (cont.isActive) cont.resume(null)
        }
    }

    private suspend fun getAddress(latitude: Double, longitude: Double): String? = withContext(Dispatchers.IO) {
        try {
            if (!Geocoder.isPresent()) return@withContext null
            val geocoder = Geocoder(context, Locale("th", "TH"))
            val list = geocoder.getFromLocation(latitude, longitude, 1)
            val addr = list?.firstOrNull() ?: return@withContext null

            val parts = mutableListOf<String>()
            addr.subLocality?.let { parts.add(it) }
            addr.locality?.let { parts.add(it) }
            addr.adminArea?.let { parts.add(it) }
            addr.countryName?.let { parts.add(it) }

            if (parts.isNotEmpty()) parts.joinToString(", ") else addr.getAddressLine(0)
        } catch (e: Exception) {
            Log.w(TAG, "Geocoder failed: ${e.message}")
            null
        }
    }
}
