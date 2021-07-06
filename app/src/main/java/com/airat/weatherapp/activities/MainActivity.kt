package com.airat.weatherapp.activities

import android.Manifest
import android.annotation.SuppressLint
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.airat.weatherapp.R
import com.airat.weatherapp.databinding.ActivityMainBinding
import com.airat.weatherapp.network.WeatherService
import com.airat.weatherapp.utils.Constants
import com.google.android.gms.location.*
import com.google.gson.Gson
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import com.weatherapp.models.WeatherResponse
import retrofit2.*
import retrofit2.converter.gson.GsonConverterFactory
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var mFusedLocationClient : FusedLocationProviderClient
    private var mProgressDialog: Dialog? = null
    private var latitude: Double = 0.toDouble()
    private var longitude : Double = 0.toDouble()
    private lateinit var sp : SharedPreferences
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        sp = getSharedPreferences(Constants.PREFERENCE_NAME, Context.MODE_PRIVATE)
        setupUI()

        initLocationsAndPermissions()
    }

    private fun initLocationsAndPermissions() {
        if (!isLocationEnabled()) {
            Toast.makeText(
                this,
                "Your location provider is turned off. Please turn it on",
                Toast.LENGTH_SHORT
            ).show()
            val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            startActivity(intent)
        } else {
            Dexter.withActivity(this).withPermissions(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ).withListener(object : MultiplePermissionsListener {
                override fun onPermissionsChecked(report: MultiplePermissionsReport?) {
                    if (report!!.areAllPermissionsGranted()) {
                        requestLocationData()
                    }
                    if (report.isAnyPermissionPermanentlyDenied) {
                        Toast.makeText(
                            this@MainActivity,
                            "You've denied location permission",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                override fun onPermissionRationaleShouldBeShown(
                    permissions: MutableList<PermissionRequest>?,
                    token: PermissionToken?
                ) {
                    showRationalDialogForPermissions()
                }

            }).onSameThread().check()
        }
    }


    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_refresh -> {
                getLocationWeatherDetails()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }


    private fun getLocationWeatherDetails(){


        if(Constants.isNetworkAvailable(this)){
            showCustomProgressDialog()

            val retrofit: Retrofit = Retrofit.Builder()
                .baseUrl(Constants.BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
            val service: WeatherService = retrofit.create(WeatherService::class.java)
            val listCall: Call<WeatherResponse> = service.getWeather(latitude, longitude, Constants.METRIC_UNIT, Constants.APP_ID)
            listCall.enqueue(object : Callback<WeatherResponse>{
                override fun onResponse(call: Call<WeatherResponse>, response: Response<WeatherResponse>) {
                   if (response.isSuccessful){
                       hideCustomProgressDialog()
                       val weatherList: WeatherResponse = response.body()!!
                       val weatherResponseJsonString = Gson().toJson(weatherList)
                       val editor = sp.edit()
                       editor.putString(Constants.WEATHER_RESPONSE_DATA, weatherResponseJsonString)
                       editor.apply()
                       setupUI()
                       Log.d("Response Result", "$weatherList")

                   } else{
                       val rc = response.code()
                       when(rc){
                           400 -> Log.e("Error 400", "Bad Connection")
                           404 -> Log.e("Error 404", "Not Found")
                           else -> Log.e("Error", "Generic error")
                       }
                   }
                }

                override fun onFailure(call: Call<WeatherResponse>, t: Throwable) {
                    hideCustomProgressDialog()

                    Log.e("Errorrrrrr", t.message.toString())
                }

            })

        } else{
            Toast.makeText(this@MainActivity, "No internet connection available", Toast.LENGTH_SHORT).show()
        }
    }





    private fun showRationalDialogForPermissions() {
        AlertDialog.Builder(this)
                .setMessage("It looks like you've turned off permissions required for this feature. It can be enabled under Application Settings")

                .setPositiveButton("GO TO SETTINGS"){ _, _ ->
                    try{
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        val uri = Uri.fromParts("package", packageName, null)
                        intent.data = uri
                        startActivity(intent)
                    }catch (e: ActivityNotFoundException){
                        e.printStackTrace()
                    }
                }
                .setNegativeButton("Cancel"){ dialog, _ ->
                    dialog.dismiss()

                }.show()


    }

    @SuppressLint("MissingPermission")
    private fun requestLocationData() {
        val mLocationRequest = LocationRequest()
        mLocationRequest.priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        mFusedLocationClient.requestLocationUpdates(
             mLocationRequest,
            mLocationCallback,
            Looper.myLooper()
        )

    }

    private val mLocationCallback = object : LocationCallback(){
        override fun onLocationResult(locationResult: LocationResult) {
            val mLastLocation: Location = locationResult.lastLocation
            latitude = mLastLocation.latitude
            longitude = mLastLocation.longitude
            getLocationWeatherDetails()
        }
    }

    private fun isLocationEnabled(): Boolean {
        val locationManager: LocationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) || locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    private fun showCustomProgressDialog(){
        mProgressDialog = Dialog(this)
        mProgressDialog!!.setContentView(R.layout.dialog_custom_progress)
        mProgressDialog!!.show()
    }
    private fun hideCustomProgressDialog(){
        if (mProgressDialog != null){
            mProgressDialog!!.dismiss()
        }
    }

    private fun setupUI(){
        val weatherResponseJsonString = sp.getString(Constants.WEATHER_RESPONSE_DATA, "")
        if (!weatherResponseJsonString.isNullOrEmpty()){
            val weatherList = Gson().fromJson(weatherResponseJsonString, WeatherResponse::class.java)
            for(i in weatherList.weather.indices){
                binding.run {
                    tvMain.text = weatherList.weather[i].main
                    tvMainDescription.text = weatherList.weather[i].description
                    tvTemp.text = weatherList.main.temp.toString() + getUnit(application.resources.configuration.locales.toString())
                    tvSunriseTime.text = unixTime(weatherList.sys.sunrise.toLong())
                    tvSunsetTime.text = unixTime(weatherList.sys.sunset.toLong())
                    tvHumidity.text = weatherList.main.humidity.toString() + " per cent"
                    tvMin.text = weatherList.main.temp_min.toString() + " min"
                    tvMax.text = weatherList.main.temp_max.toString() + " max"
                    tvSpeed.text = weatherList.wind.speed.toString()
                    tvName.text = weatherList.name
                    tvCountry.text = weatherList.sys.country


                    // Here we update the main icon
                    when (weatherList.weather[i].icon) {
                        "01d" -> ivMain.setImageResource(R.drawable.sunny)
                        "02d" -> ivMain.setImageResource(R.drawable.cloud)
                        "03d" -> ivMain.setImageResource(R.drawable.cloud)
                        "04d" -> ivMain.setImageResource(R.drawable.cloud)
                        "04n" -> ivMain.setImageResource(R.drawable.cloud)
                        "10d" -> ivMain.setImageResource(R.drawable.rain)
                        "11d" -> ivMain.setImageResource(R.drawable.storm)
                        "13d" -> ivMain.setImageResource(R.drawable.snowflake)
                        "01n" -> ivMain.setImageResource(R.drawable.cloud)
                        "02n" -> ivMain.setImageResource(R.drawable.cloud)
                        "03n" -> ivMain.setImageResource(R.drawable.cloud)
                        "10n" -> ivMain.setImageResource(R.drawable.cloud)
                        "11n" -> ivMain.setImageResource(R.drawable.rain)
                        "13n" -> ivMain.setImageResource(R.drawable.snowflake)
                    }

                }
            }
        }

    }

    private fun getUnit(value: String): String{
        var value = "°С"
        if ("US" == value || "LR" == value || "MM" == value){
            value = "°F"
        }
        return value
    }

    private fun unixTime(timex: Long): String? {
        val date = Date(timex * 1000L)
        @SuppressLint("SimpleDateFormat")
        val sdf = SimpleDateFormat("HH:mm")
        sdf.timeZone = TimeZone.getDefault()
        return sdf.format(date)
    }

}
