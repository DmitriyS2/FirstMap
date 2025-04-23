package com.example.firstapp

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Geocoder
import android.os.Bundle
import android.util.Log
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.firstapp.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.osmdroid.bonuspack.routing.OSRMRoadManager
import org.osmdroid.bonuspack.routing.Road
import org.osmdroid.bonuspack.routing.RoadManager
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.MinimapOverlay
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.compass.CompassOverlay
import org.osmdroid.views.overlay.compass.InternalCompassOrientationProvider
import org.osmdroid.views.overlay.gestures.RotationGestureOverlay
import org.osmdroid.views.overlay.infowindow.InfoWindow
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay


class MainActivity : AppCompatActivity(), MapEventsReceiver {

    private lateinit var mapView: MapView
    private lateinit var binding: ActivityMainBinding
    private var myLocationOverlay: MyLocationNewOverlay? = null
     private var geocoder:Geocoder? = null
    private var startPoint = GeoPoint(53.246859, 50.217432)
    private var endPoint = GeoPoint(55.787715, 49.122197)

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            when {
                granted -> {
                    setMyLocation()
                }

                else -> {
                    Toast.makeText(
                        this,
                        "Необходимо разрешение",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    private val osrmRoadManager = OSRMRoadManager(this@MainActivity, "MY_USER_AGENT")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        Configuration.getInstance().userAgentValue = "MAPNIK"

        geocoder = Geocoder(this)
        setContentView(binding.root)

        mapView = binding.mapView
        mapView.apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.ALWAYS)
            controller.setZoom(15.0)

            val rotationGestureOverlay = RotationGestureOverlay(this) // поворот карты
            rotationGestureOverlay.isEnabled
            overlays.add(rotationGestureOverlay)

            val minimapOverlay = MinimapOverlay(this@MainActivity, this.tileRequestCompleteHandler) //миникарта
//        minimapOverlay.setWidth(dm.widthPixels / 5)
//        minimapOverlay.setHeight(dm.heightPixels / 5)
            overlays.add(minimapOverlay)

            val mapEventsOverlay = MapEventsOverlay(this@MainActivity) //нажатие и долгое нажатие
            overlays.add(0, mapEventsOverlay)

            myLocationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(this@MainActivity), mapView)
            overlays.add(myLocationOverlay)

//            val startMarker = Marker(this)
//            startMarker.position = startPoint
//            startMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
//            //  startMarker.icon = getResources().getDrawable(R.drawable.ic_launcher);
//            startMarker.title = "Start point";
//            overlays.add(startMarker)
     //       controller.setCenter(startPoint)
          clickBtnMyGeo()

            binding.btnCompass.setOnClickListener {
                getCompass()
            }
            binding.btnGeo.setOnClickListener {
                clickBtnMyGeo()
            }
            binding.btnRoad.setOnClickListener {
                binding.etAddress.clearFocus()
                getRoad()
            }
            binding.btnSearch.setOnClickListener{
                imm.hideSoftInputFromWindow(rootView.windowToken, 0)
                binding.etAddress.clearFocus()
                if(binding.etAddress.text.toString().isNotEmpty()) {
                    val resp = geocoder?.getFromLocationName(binding.etAddress.text.toString(), 1)
                    if(!resp.isNullOrEmpty()) {
                        val point = GeoPoint(resp[0].latitude, resp[0].longitude)
                        Toast.makeText(this@MainActivity, resp[0].getAddressLine(0), Toast.LENGTH_SHORT).show()
                        setMarker(resp[0].getAddressLine(0), point)
                        this.controller.animateTo(point)
                        this.controller.setZoom(16.0)
                        endPoint = point
                    } else {
                        Toast.makeText(this@MainActivity, "Не найдено", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this@MainActivity, "Введите адрес", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    private fun setMarker(title:String, p:GeoPoint) {
        val marker = Marker(mapView)
        marker.position = p
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        //  startMarker.icon = getResources().getDrawable(R.drawable.ic_launcher);
        marker.title = title;
        mapView.overlays.add(marker)
    }

    private fun clearMarkers() {
        mapView.overlays?.let {
            it.filterIsInstance<Marker>().forEach { marker ->
                it.remove(marker)
            }
        }
    }

    private fun clickBtnMyGeo() {
        binding.etAddress.clearFocus()
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        } else {
            setMyLocation()
        }
    }

    private fun getRoad() {
        lifecycleScope.launch(Dispatchers.IO) {
            osrmRoadManager.setMean(OSRMRoadManager.MEAN_BY_CAR)
            val waypoints = ArrayList<GeoPoint>()
            waypoints.add(startPoint)
            waypoints.add(endPoint)
            mapView.overlays?.let {
                it.filterIsInstance<Marker>().forEach { marker ->
                    it.remove(marker)
                }
                it.filterIsInstance<Polyline>().forEach { pl ->
                    it.remove(pl)
                }
            }
            myLocationOverlay?.disableFollowLocation()
            val road = osrmRoadManager.getRoad(waypoints)
            if (road.mStatus != Road.STATUS_OK) {
                //          Toast.makeText(this, "Error when loading the road - status=" + road.mStatus, Toast.LENGTH_SHORT).show();
            } else {
                val roadOverlay = RoadManager.buildRoadOverlay(road, Color.BLUE, 5.0f)
                roadOverlay.setOnClickListener { polyline, mapView, eventPos ->
                    Log.d("MyLog", "infowindowLocation=${polyline.infoWindowLocation}")
                    true
                }

                mapView.overlays.add(roadOverlay)
                val nodeIcon = resources.getDrawable(R.drawable.marker_node, null)
                for (i in road.mNodes.indices) {
                    val node = road.mNodes[i]
                    val nodeMarker = Marker(mapView)
                    nodeMarker.position = node.mLocation
                    nodeMarker.icon = nodeIcon
                    nodeMarker.title = "Step $i ${node.mManeuverType}"
                    nodeMarker.snippet = node.mInstructions;
                    nodeMarker.subDescription =
                        Road.getLengthDurationText(this@MainActivity, node.mLength, node.mDuration)
                    mapView.overlays.add(nodeMarker)
                }
                launch(Dispatchers.Main) {
                    mapView.mapOrientation = 0.0f
                    mapView.zoomToBoundingBox(road.mBoundingBox.increaseByScale(1.3f), false)
                    binding.txRoad.text = Road.getLengthDurationText(this@MainActivity, road.mLength, road.mDuration)

                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }

    override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
        Toast.makeText(this, "EndPoint (" + p?.latitude + "," + p?.longitude + ")", Toast.LENGTH_SHORT).show()
        endPoint = p ?: endPoint
        InfoWindow.closeAllInfoWindowsOn(mapView);
        p?.run{
            mapView.controller.animateTo(endPoint)
        }
        lifecycleScope.launch(Dispatchers.IO) {
            geocoder?.let {
                try {
                    val listAddress = it.getFromLocation(endPoint.latitude, endPoint.longitude, 1)

                    listAddress?.let {
                        val addLine = listAddress[0].getAddressLine(0)
                        val phone = listAddress[0].phone
                        val url = listAddress[0].url
                        Log.d("MyLog", "addLine=$addLine, url=$url, phone=$phone")
                        launch(Dispatchers.Main) {
                            binding.etAddress.setText(addLine)
                            clearMarkers()
                            setMarker(addLine, endPoint)
                        }
                    }
                } catch (e: Exception) {
                    Log.d("MyLog", "address error $e")
                }
            }
        }

        return true
    }

    override fun longPressHelper(p: GeoPoint?): Boolean {
        Toast.makeText(this, "StartPoint (" + p?.latitude + "," + p?.longitude + ")", Toast.LENGTH_SHORT).show()
        startPoint = p ?: startPoint
        return true
    }

    private fun getCompass() {
        val compassOverlay =
            CompassOverlay(this, InternalCompassOrientationProvider(this), mapView)
        compassOverlay.enableCompass()
        val t = compassOverlay.azimuthOffset

        mapView.overlays.add(compassOverlay)
        //      mapView.rotation = t
        mapView.invalidate()
    }

    private fun setMyLocation() {
        myLocationOverlay?.enableMyLocation()
        myLocationOverlay?.enableFollowLocation()
        mapView.overlays.filterIsInstance<MyLocationNewOverlay>().forEach {
            Log.d("MyLog", "instance=$it, loc=${it?.myLocation}")
        }
        mapView.controller.setZoom(16.0)
        InfoWindow.closeAllInfoWindowsOn(mapView)
        startPoint = myLocationOverlay?.myLocation ?: startPoint
        Log.d("MyLog", "startPoint=$startPoint, myLoc=${myLocationOverlay?.myLocation}")
    }
}
