package com.example.gps

import android.annotation.SuppressLint
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.content.ContextCompat
import android.Manifest
import android.app.PendingIntent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.Looper
import android.util.Log
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Environment
import android.os.RemoteException
import android.util.Xml
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import org.xmlpull.v1.XmlSerializer
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter

// see GUIDE: https://medium.com/@vjpawar9974/implementing-foreground-service-for-location-updates-in-android-8b5fd9893189

const val CHANNEL_ID: String = "I-DONT-KNOW"


class GPSService : Service() {
    private lateinit var fusedLucationProviderClient: FusedLocationProviderClient
    private val waypoints = mutableListOf<Waypoint>()

    private var foreground = false
    private lateinit var notification: Notification

    private var lat: Float = 0.0F
    private var lon: Float = 0.0F
    private var alt: Float = 0.0F

    @SuppressLint("ForegroundServiceType")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_NOT_STICKY
    }

    private val binder = object : IGPSService.Stub() {
        @Throws(RemoteException::class)
        override fun stop() {
            stopForeground(true)
            stopSelf()
            Log.w(TAG, "stopped")
        }
        @Throws(RemoteException::class)
        override fun getLatitude(): Float{
            return lat
        }
        @Throws(RemoteException::class)
        override fun getLongitude(): Float{
            return lon
        }
        @Throws(RemoteException::class)
        override fun getAltitude(): Float{
            return alt
        }
        @Throws(RemoteException::class)
        override fun exportToGpx() {
            export()
        }

    }

    override fun onCreate() {
        super.onCreate()
        fusedLucationProviderClient = LocationServices.getFusedLocationProviderClient(this)
        createNotificationChannel()
        notification = getNotification()
        getLocation()

        Log.w(TAG, "Service started...")
    }
    override fun onBind(intent: Intent?): IBinder {
        if(!foreground) {
            startForeground(1, notification)
            Log.w(TAG, "added foreground service")
            foreground = true
        }
        return binder
    }

    override fun onRebind(intent: Intent?) {
        super.onRebind(intent)
        if(!foreground) {
            startForeground(1, notification)
            Log.w(TAG, "rebond foreground service")
            foreground = true
        }
    }

    override fun onUnbind(intent: Intent?): Boolean {
        if(foreground) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            Log.w(TAG, "removed foreground service")
            foreground = false
            fusedLucationProviderClient.removeLocationUpdates(locationCallback)
        }
        super.onUnbind(intent)
        return true
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Location Service Channel",
                NotificationManager.IMPORTANCE_HIGH
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    fun getLocation() {
        val locationRequest = LocationRequest().apply {
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
            interval = 1000L
        }

        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION ) == PackageManager.PERMISSION_GRANTED ) {
            fusedLucationProviderClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.myLooper()!!
            )
        }
        else {
            Log.w(TAG, "Permission not granted.")
        }
    }

    private fun getNotification(): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )
        val builder = NotificationCompat.Builder(this, CHANNEL_ID, )
            .setContentTitle("Location Service")
            .setContentText("Getting location updates")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setChannelId(CHANNEL_ID)
            .setContentIntent(pendingIntent)
            .setPriority(1)
            .setOngoing(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
        }
        //val notificationManager: NotificationManager = applicationContext.getSystemService(
            //Context.NOTIFICATION_SERVICE) as NotificationManager
        //notificationManager.notify(1, builder.build())
        return builder.build()
    }

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(p0: LocationResult) {
            super.onLocationResult(p0)
            val location = p0.lastLocation
            if (location != null) {
                lat = location.latitude.toFloat()
                lon = location.longitude.toFloat()
                alt = location.altitude.toFloat()

                val waypoint = Waypoint(lat.toDouble(), lon.toDouble(), "route", alt.toDouble())
                waypoints.add(waypoint)
                Log.w(TAG, String.format("LAT: %s | LON: %s | ALT: %s", lat, lon, alt))

            }
        }
    }

    private fun export() {
        val fileName = "waypoints.gpx"
        val path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val file = File(path, fileName)

        if (path != null) {
            Log.d("MainActivity", "File path: ${file.absolutePath}")

            try {
                val serializer: XmlSerializer = Xml.newSerializer()
                val fileOutputStream = FileOutputStream(file)
                val writer = OutputStreamWriter(fileOutputStream)

                serializer.setOutput(writer)
                serializer.startDocument("UTF-8", true)
                serializer.startTag("", "gpx")
                serializer.attribute("", "version", "1.1")
                serializer.attribute("", "creator", "MyApp")

                waypoints.forEach { waypoint ->
                    serializer.startTag("", "wpt")
                    serializer.attribute("", "lat", waypoint.latitude.toString())
                    serializer.attribute("", "lon", waypoint.longitude.toString())
                    serializer.startTag("", "name")
                    serializer.text(waypoint.name)
                    serializer.endTag("", "name")
                    serializer.startTag("", "desc")
                    serializer.attribute("", "alt", waypoint.altitude.toString())
                    serializer.endTag("", "desc")
                    serializer.endTag("", "wpt")
                }

                serializer.endTag("", "gpx")
                serializer.endDocument()

                writer.flush()
                writer.close()
                fileOutputStream.close()
                Log.d("MainActivity", "File written successfully")

            } catch (e: Exception) {
                Log.e("MainActivity", "Error writing file", e)
            }
        } else {
            Log.e("MainActivity", "External files directory not available")
        }
    }
}