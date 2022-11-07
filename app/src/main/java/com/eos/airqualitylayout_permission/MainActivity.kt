package com.eos.airqualitylayout_permission

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.LocationManager
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.eos.airqualitylayout_permission.databinding.ActivityMainBinding
import java.io.IOException
import java.lang.IllegalArgumentException
import java.util.*

// https://www.iqair.com/ko/ -> API key 받기


class MainActivity : AppCompatActivity() {

    lateinit var binding: ActivityMainBinding

    // 런타임 권한 요청 시 필요한 요청 코드
    private val PERMISSION_REQUEST_CODE = 100
    // 요청할 권한 목록
    var REQUIRED_PERMISSION = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )

    // 위치 서비스 요청 시 필요한 런처
    lateinit var getGPSPermissionLauncher: ActivityResultLauncher<Intent>

    // 위도와 경도를 불러올 때 필요한 변수
    lateinit var locationProvider: LocationProvider


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        checkAllPermission()
        updateUI()
    }

    private fun getCurrentAddress(latitude: Double, longitude: Double) : Address? {
        val geocoder = Geocoder(this, Locale.getDefault())
        // Address 객체는 주소와 관련된 여러 정보를 가지고 있습니다
        // android.location.Address

        val addresses: List<Address>? = try {
            // Geocoder 객체를 이용하여 위도와 경도로부터 리스트를 가져오기
            geocoder.getFromLocation(latitude, longitude, 7)
        } catch (ioException: IOException) {
            Toast.makeText(this, "Geocoder 서비스 사용불가", Toast.LENGTH_LONG).show()
            return null
        } catch (illegalArgumentException: IllegalArgumentException) {
            Toast.makeText(this, "잘못된 위도, 경도입니다.", Toast.LENGTH_LONG).show()
            return null
        }

        // 에러는 아니지만 주소가 발견되지 않은 경우
        if (addresses == null || addresses.isEmpty()) {
            Toast.makeText(this, "주소가 발견되지 않았습니다.", Toast.LENGTH_LONG).show()
            return null
        }

        return addresses[0]
    }

    @SuppressLint("SetTextI18n")
    private fun updateUI() {
        locationProvider = LocationProvider(this@MainActivity)

        // 위도와 경도 정보를 가져오기
        val latitude: Double = locationProvider.getLocationLatitude()
        val longitude: Double = locationProvider.getLocationLongitude()

        if (latitude != 0.0 && longitude != 0.0) {
            // 1. 현재 위치를 가져오고 UI 업데이트
            // 현재 위치를 가져오기
            val address = getCurrentAddress(latitude, longitude)
            // 주소가 null이 아닐 경우 UI 업데이트
            address?.let {
                binding.tvLocationTitle.text = it.thoroughfare
                binding.tvLocationSubtitle.text = "${it.countryName} ${it.adminArea}"
            }


            // 2. 현재 미세먼지 농도 가져오고 UI 업데이트
        } else {
            Toast.makeText(
                this@MainActivity,
                "위도, 경도 정보를 가져올 수 없습니다. 새로고침을 눌러주십시오.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun checkAllPermission() {
        if (!isLocationServiceAvailable()) {
            // GPS 가 켜져있지 않은 경우
            showDialogForLocationServiceSetting()
        } else {
            // GPS 가 켜져있는 경우
            isRuntimePermissionGranted()
        }
    }

    private fun showDialogForLocationServiceSetting() {
        // 먼저 ActivityResultLauncher 를 설정해줍니다.
        // 이 런처를 이용하여 결과값을 반환해야 하는 Intent 를 실행할 수 있습니다.
        getGPSPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()) { result ->
            // 결과값을 받았을 때 로직
            if (result.resultCode == Activity.RESULT_OK) {
                // 사용자가 GPS 를 활성화시켰는지 확인
                if (isLocationServiceAvailable()) {
                    isRuntimePermissionGranted()
                } else {
                    // 위치 서비스가 허용되지 않았다면 앱 종료
                    Toast.makeText(
                        this@MainActivity,
                        "위치 서비스를 사용할 수 없습니다.",
                        Toast.LENGTH_LONG
                    ).show()
                    finish()
                }
            }
        }

        val builder: AlertDialog.Builder = AlertDialog.Builder(this@MainActivity)
        builder.setTitle("위치 서비스 비활성화")
        builder.setMessage("위치 서비스가 꺼져 있습니다. 설정해야 앱을 사용할 수 있습니다.")
        builder.setCancelable(true)
        builder.setPositiveButton("설정") { dialog, id ->
            val callGPSSettingIntent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            getGPSPermissionLauncher.launch(callGPSSettingIntent)
        }
        builder.setNegativeButton("취소") { dialog, id ->
            dialog.cancel()
            Toast.makeText(
                this@MainActivity,
                "기기에서 GPS 설정 후 사용해주세요.",
                Toast.LENGTH_LONG
            ).show()
            finish()
        }
        builder.create().show()
    }

    private fun isRuntimePermissionGranted() {
        // 위치 퍼미션을 가지고 있는지 체크
        val hasFineLocationPermission = ContextCompat.checkSelfPermission(
            this@MainActivity,
            Manifest.permission.ACCESS_FINE_LOCATION
        )

        val hashCoarseLocationPermission = ContextCompat.checkSelfPermission(
            this@MainActivity,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        if (hasFineLocationPermission != PackageManager.PERMISSION_GRANTED
            || hashCoarseLocationPermission != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this@MainActivity, REQUIRED_PERMISSION, PERMISSION_REQUEST_CODE)
        }
    }

    private fun isLocationServiceAvailable(): Boolean {
        val locationManager = getSystemService(LOCATION_SERVICE) as LocationManager

        return (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                || locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER))

    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE
            && grantResults.size == REQUIRED_PERMISSION.size) {
            // 요청 코드가 일치하고, 요청한 권한 개수만큼 수신되었다면
            var checkResult = true

            // 모든 권한을 허용했는지 체크
            for (result in grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    checkResult = false
                    break
                }
            }

            if (checkResult) {
                // 위칫값을 가져올 수 있음
                updateUI()
            } else {
                // 거부되었다면
                Toast.makeText(
                    this@MainActivity,
                    "권한이 거부되었습니다. 앱을 다시 실행하여 권한을 허용해주십시오.",
                    Toast.LENGTH_SHORT
                ).show()
                finish()
            }
        }
    }

}