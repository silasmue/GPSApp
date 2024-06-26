package com.example.gps

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import android.Manifest;
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Binder
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.RemoteException
import android.util.Log
import android.util.Xml
import android.widget.Button
import android.widget.TextView
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import me.bvn13.sdk.android.gpx.ExtensionType
import me.bvn13.sdk.android.gpx.FixType
import me.bvn13.sdk.android.gpx.GpxType
import me.bvn13.sdk.android.gpx.LinkType
import me.bvn13.sdk.android.gpx.MetadataType
import me.bvn13.sdk.android.gpx.RteType
import me.bvn13.sdk.android.gpx.TrkType
import me.bvn13.sdk.android.gpx.TrksegType
import me.bvn13.sdk.android.gpx.WptType
import org.xmlpull.v1.XmlSerializer
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.lang.Exception
import java.time.OffsetDateTime

const val TAG: String = "MainActivity";

class MainActivity : ComponentActivity() {

    private lateinit var locationCallback: LocationCallback
    private lateinit var fusedLucationProviderClient: FusedLocationProviderClient


    private  var gpsService: IGPSService? = null

    private var requestingLocationUpdates = false
    private var bond = false

    val connection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            gpsService = IGPSService.Stub.asInterface(service)
            bond = true
        }

        override fun onServiceDisconnected(className: ComponentName) {
            Log.e(TAG, "Service has unexpectedly disconnected")
            gpsService = null
            bond = false
        }
    }


    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.layout);

        val text: TextView = this.findViewById(R.id.values)
        text.setText("no values")

        val intent = Intent().apply {
            setClassName("com.example.gps", "com.example.gps.GPSService")
        }


        val startButton: Button = findViewById(R.id.start)
        startButton.setOnClickListener {
            if(!bond) {
                applicationContext.bindService(intent, connection, Context.BIND_AUTO_CREATE)
                bond = true
            }


            //startLocationUpdates()
        }


        val stopButton: Button = findViewById(R.id.stop)
        stopButton.setOnClickListener {
            if(bond) {
                applicationContext.unbindService(connection)
                bond = false
            }

        }
        val updateUI: Button = findViewById(R.id.update)
        updateUI.setOnClickListener {
            try {
                text.text = String.format("Latitude: %s | Longitude: %s | Altitude: %s",
                    gpsService?.latitude.toString(), gpsService?.longitude.toString(),
                    gpsService?.altitude.toString())
                Log.w(TAG, gpsService?.latitude.toString())
            } catch (e: RemoteException) {
                e.printStackTrace()
            }
        }

        val exportToGpx: Button = findViewById(R.id.export)
        exportToGpx.setOnClickListener {
            try {
                gpsService?.exportToGpx()
                Log.w(TAG, "exported to file")
            } catch (e: RemoteException) {
                e.printStackTrace()
            }
        }



        val locationPermissionRequest = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            when {
                permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false) -> {
                    Log.w(TAG, "Precise location access granted.")
                    requestingLocationUpdates = true
                // Precise location access granted.
                }
                permissions.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false) -> {
                    Log.w(TAG, "Only approximate location access granted.")
                    requestingLocationUpdates = true
                    // Only approximate location access granted.
                } else -> {
                    requestingLocationUpdates = false
                    Log.w(TAG, "No location access granted.")
                    // No location access granted.
            }
            }
        }

        locationPermissionRequest.launch(arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION))


        fusedLucationProviderClient = LocationServices.getFusedLocationProviderClient(this);

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult ?: return
                for (location in locationResult.locations){
                    Log.w(TAG, location.toString())
                    text.setText(
                        String.format("Latitude: %s | Longitude: %s | Altitude: %s",
                            location.latitude, location.longitude, location.altitude)
                    )

                }
                //Looper.myLooper()!!
            }
        }

    }

    override fun onResume() {
        super.onResume()
    }

    override fun onPause() {
        super.onPause()
    }

    // permission is checked in onCreate...
}