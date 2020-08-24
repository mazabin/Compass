package it.zabinska.compass

import android.Manifest
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.RotateAnimation
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.LocationRequest

class MainActivity : AppCompatActivity(), SensorEventListener {
    private lateinit var mSensorManager : SensorManager
    private lateinit var mLocationManager: LocationManager
    private lateinit var mLocationListener : LocationListener
    private lateinit var compass : ImageView
    private lateinit var arrow : ImageView
    private var mHeadingLatitude = 0.0
    private var mHeadingLongitude = 0.0
    private lateinit var btnLatitude : Button
    private lateinit var btnLongitude : Button


    private val accelerometerReading = FloatArray(3)
    private val magnetometerReading = FloatArray(3)

    private val rotationMatrix = FloatArray(9)
    private val mOrientationAngles = FloatArray(9)

    private var currentDegree = 0F

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        mSensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        mLocationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager

        mLocationListener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                modifyHeading(location.latitude, location.longitude)
                println("latitude: " + location.latitude + " longitude: " + location.longitude)
            }

            override fun onStatusChanged(provider: String, status: Int, extras: Bundle) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }

        compass = findViewById(R.id.imageCompass)
        arrow = findViewById(R.id.imageArrow)
        btnLatitude = findViewById(R.id.btnLatitude)
        btnLongitude = findViewById(R.id.btnLongitude)

        btnLongitude.setOnClickListener {
            createDialog(this,
                getString(R.string.longitude_message),
                getString(R.string.longitude),
                Direction.LONGITUDE)
        }

        btnLatitude.setOnClickListener {
            createDialog(this,
                getString(R.string.latitude_message),
                getString(R.string.latitude),
                Direction.LATITUDE)
        }
    }

    override fun onResume() {
        super.onResume()
        mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.also { accelerometer ->
            mSensorManager.registerListener(
                this,
                accelerometer,
                SensorManager.SENSOR_DELAY_NORMAL,
                SensorManager.SENSOR_DELAY_UI
            )
        }
        mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)?.also { magneticField ->
            mSensorManager.registerListener(
                this,
                magneticField,
                SensorManager.SENSOR_DELAY_NORMAL,
                SensorManager.SENSOR_DELAY_UI
            )
        }

        val mLocationRequest = LocationRequest.create()
        mLocationRequest.priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        mLocationRequest.interval = 5000
        mLocationRequest.fastestInterval = 1000
        var lastKnownLocation: Location? = null
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            val info: PackageInfo = packageManager.getPackageInfo(
                this.packageName, PackageManager.GET_PERMISSIONS)
            val permissions = info.requestedPermissions
            shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)
            requestPermissions(permissions, 1)
            return
        }
        mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0F, mLocationListener)
        lastKnownLocation = mLocationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
        if(lastKnownLocation == null) {
            lastKnownLocation =
                mLocationManager.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER);
        }
        modifyHeading(lastKnownLocation.latitude, lastKnownLocation.longitude)

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            val info: PackageInfo = packageManager.getPackageInfo(
                this.packageName, PackageManager.GET_PERMISSIONS)
            val permissions = info.requestedPermissions
            shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)
            requestPermissions(permissions, 1)
            return
        }
    }

    override fun onPause(){
        super.onPause()
        mSensorManager.unregisterListener(this)
    }

    private fun createDialog(context: Context, message : String, title : String, direction : Direction){
        val builder: AlertDialog.Builder? = context.let {
            AlertDialog.Builder(it)
        }

        val input = createDialogInput(this)

        builder
            ?.setMessage(message)
            ?.setTitle(title)
            ?.setCancelable(true)
            ?.setView(input)
            ?.setPositiveButton("Ok") { dialog, id ->
                validateAndSave(input.text.toString(), direction)
            }

        val dialog: AlertDialog? = builder?.create()
        dialog?.show()
    }

    private fun createDialogInput(context: Context): EditText {
        val dialogInput = EditText(context)
        dialogInput.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )

        dialogInput.imeOptions = EditorInfo.IME_ACTION_DONE
        dialogInput.inputType =
            InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED or InputType.TYPE_CLASS_NUMBER
        val coordinateChanged = object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val value = s.toString()
                if (value.length > 1) {
                    val coordinate = value.toDouble()
                    if (coordinate > 180.0) {
                        dialogInput.setText("180.00000")
                    }
                    if (coordinate < -180.0) {
                        dialogInput.setText("-180.00000")
                    }
                }
            }
        }
        dialogInput.addTextChangedListener(coordinateChanged)
        return dialogInput
    }

    private fun validateAndSave(input: String, direction: Direction) {
        if (input.isNotEmpty()) {
            if (input.length == 1 && (input == "-" || input == ".")) {
                if(direction.equals(Direction.LATITUDE)) {
                    mHeadingLatitude = input.toDouble()
                }
                if(direction.equals(Direction.LONGITUDE)){
                    mHeadingLongitude = input.toDouble()
                }
            }
        }
    }

    private fun modifyHeading(latitude: Double, longitude: Double) {
        val latitudeDiff = latitude - mHeadingLatitude
        val longitudeDiff = longitude - mHeadingLongitude

        if(longitudeDiff > 0 && latitudeDiff > 0){ //NE
            val ra = RotateAnimation(
                currentDegree,
                currentDegree + 45.0F)
            ra.duration = 15
            ra.fillAfter = true
            arrow.startAnimation(ra)
        }
        if(longitudeDiff > 0 && latitudeDiff < 0){ //SE
            val ra = RotateAnimation(
                currentDegree,
                currentDegree + 135.0F)
            ra.duration = 15
            ra.fillAfter = true
            arrow.startAnimation(ra)
        }
        if(longitudeDiff < 0 && latitudeDiff > 0){ //NW
            val ra = RotateAnimation(
                currentDegree,
                currentDegree + 225.0F)
            ra.duration = 15
            ra.fillAfter = true
            arrow.startAnimation(ra)
        }
        if(longitudeDiff < 0 && latitudeDiff < 0){ //SW
            val ra = RotateAnimation(
                currentDegree,
                currentDegree + 315.0F)
            ra.duration = 15
            ra.fillAfter = true
            arrow.startAnimation(ra)
        }
    }

    override fun onAccuracyChanged(p0: Sensor?, p1: Int) { //unused
    }

    override fun onSensorChanged(p0: SensorEvent) {
        when (p0.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                System.arraycopy(p0.values, 0, accelerometerReading, 0, accelerometerReading.size)
            }
            Sensor.TYPE_MAGNETIC_FIELD -> {
                System.arraycopy(p0.values, 0, magnetometerReading, 0, magnetometerReading.size)
            }
        }
        updateOrientation()
    }

    private fun updateOrientation(){
        SensorManager.getRotationMatrix(rotationMatrix, null, accelerometerReading, magnetometerReading)
        val orientationArray = SensorManager.getOrientation(rotationMatrix, mOrientationAngles)

        val azimuth: Float = orientationArray[0]*(180/Math.PI.toFloat())

        val ra = RotateAnimation(
            currentDegree,
            -azimuth,
             Animation.RELATIVE_TO_SELF,
            0.5f,
             Animation.RELATIVE_TO_SELF,
            0.5f)
        ra.duration = 15
        ra.fillAfter = true
        compass.startAnimation(ra)
        currentDegree = -azimuth
    }

    enum class Direction{
        LATITUDE, LONGITUDE
    }
}