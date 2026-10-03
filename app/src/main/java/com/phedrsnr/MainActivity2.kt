package com.phedrsnr

import android.app.AlertDialog
import android.app.DownloadManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Base64
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.google.firebase.firestore.FirebaseFirestore
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class MainActivity2 : AppCompatActivity() {

    private var selectedBitmap: Bitmap? = null

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            val inputStream = contentResolver.openInputStream(it)
            selectedBitmap = BitmapFactory.decodeStream(inputStream)
            findViewById<ImageView>(R.id.ivCitizenPreview).apply {
                setImageBitmap(selectedBitmap)
                visibility = View.VISIBLE
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main2)

        // Views Bindings
        val spTanki = findViewById<Spinner>(R.id.spTanki)
        val spZone = findViewById<Spinner>(R.id.spZone)
        val btnSetAlarmZone = findViewById<Button>(R.id.btnSetAlarmZone)
        val tvSelectedZoneStatus = findViewById<TextView?>(R.id.tvSelectedZoneStatus)
        val tvLiveStatus = findViewById<TextView?>(R.id.tvLiveStatus)

        val etTrackId = findViewById<EditText>(R.id.etTrackId)
        val btnTrackComplaint = findViewById<Button>(R.id.btnTrackComplaint)
        val tvTrackResult = findViewById<TextView?>(R.id.tvTrackResult)

        val etCitizenName = findViewById<EditText>(R.id.etCitizenName)
        val etCitizenMessage = findViewById<EditText>(R.id.etCitizenMessage)
        val btnSelectCitizenPhoto = findViewById<Button>(R.id.btnSelectCitizenPhoto)
        val ivCitizenPreview = findViewById<ImageView>(R.id.ivCitizenPreview)
        val btnSubmitComplaint = findViewById<Button>(R.id.btnSubmitComplaint)

        val btnCallHelpline = findViewById<Button>(R.id.btnCallHelpline)
        val btnOpenLocation = findViewById<Button>(R.id.btnOpenLocation)

        val db = FirebaseFirestore.getInstance()
        val prefs = getSharedPreferences("PHED_PREFS", Context.MODE_PRIVATE)

        // 1. Android 13+ Notification Permission & Channel
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }
        createNotificationChannel()

        // 2. Auto-Update Check (Crash-safe)
        checkForAppUpdate(db)

        // 3. Admin Panel Mapping (टंकी और उसके अंदर के ज़ोन)
        val tankiList = listOf(
            "-- टंकी चुनें --",
            "हेडवर्क्स टंकी",
            "खाली स्कूल टंकी",
            "बस स्टैण्ड टंकी",
            "11 TK टंकी"
        )

        val tankiZoneMap = mapOf(
            "हेडवर्क्स टंकी" to listOf(
                "-- ज़ोन चुनें --",
                "रिफ्यूजी ज़ोन",
                "मण्डी ज़ोन",
                "शास्त्री कॉलोनी वेयर हाउस जोन",
                "ढांडा कॉलोनी",
                "वाल्मीकि बस्ती",
                "धानक पार्क ज़ोन",
                "गुरुद्वारा ज़ोन"
            ),
            "खाली स्कूल टंकी" to listOf(
                "-- ज़ोन चुनें --",
                "हनुमान मन्दिर A",
                "हनुमान मन्दिर B",
                "मस्जिद एरिया"
            ),
            "बस स्टैण्ड टंकी" to listOf(
                "-- ज़ोन चुनें --",
                "गौशाला ज़ोन",
                "23 PS गाँव",
                "वार्ड न. 19",
                "राजू सिंघल हॉस्पीटल",
                "राममन्दिर ज़ोन",
                "छात्रावास",
                "मस्जिद ज़ोन एरिया",
                "अनूपगढ़ रोड",
                "विजयनगर रोड",
                "MD कॉलेज",
                "वार्ड न. 9, 10, 11 ज़ोन"
            ),
            "11 TK टंकी" to listOf(
                "-- ज़ोन चुनें --",
                "ज़ोन 1",
                "ज़ोन 2",
                "ज़ोन 3",
                "11 TK गाँव"
            )
        )

        val tankiAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, tankiList)
        spTanki.adapter = tankiAdapter

        // Saved Zone Display
        val savedTanki = prefs.getString("saved_tanki", "") ?: ""
        val savedZone = prefs.getString("saved_zone", "") ?: ""
        if (savedZone.isNotEmpty()) {
            tvSelectedZoneStatus?.text = "चुना गया क्षेत्र: $savedTanki - $savedZone (अलार्म एक्टिव)"
        }

        // Dropdown selection listener
        spTanki.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedTanki = tankiList[position]
                val zones = tankiZoneMap[selectedTanki] ?: listOf("-- ज़ोन चुनें --")
                val zoneAdapter = ArrayAdapter(this@MainActivity2, android.R.layout.simple_spinner_dropdown_item, zones)
                spZone.adapter = zoneAdapter
            }


            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // Save Zone Button
        btnSetAlarmZone.setOnClickListener {
            val selTanki = spTanki.selectedItem?.toString() ?: ""
            val selZone = spZone.selectedItem?.toString() ?: ""

            if (selTanki.startsWith("--") || selZone.startsWith("--")) {
                Toast.makeText(this, "कृपया टंकी और ज़ोन दोनों चुनें!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            prefs.edit()
                .putString("saved_tanki", selTanki)
                .putString("saved_zone", selZone)
                .apply()

            tvSelectedZoneStatus?.text = "चुना गया क्षेत्र: $selTanki - $selZone (अलार्म एक्टिव)"
            Toast.makeText(this, "अलार्म ज़ोन सेट हो गया!", Toast.LENGTH_SHORT).show()
        }

        // Live Status Listener
        db.collection("water_supply").document("current_status")
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null && snapshot.exists()) {
                    val status = snapshot.getString("status") ?: "अज्ञात"
                    val activeZone = snapshot.getString("zone") ?: "कोई नहीं"
                    val note = snapshot.getString("note") ?: ""

                    tvLiveStatus?.text = "वर्तमान स्थिति: $activeZone में $status ($note)"

                    val mySavedZone = prefs.getString("saved_zone", "") ?: ""
                    if (mySavedZone.isNotEmpty() && activeZone == mySavedZone && status.contains("सप्लाई शुरू")) {
                        triggerAlarmAndNotification(activeZone, status, note)
                    }
                }
            }

        // Track Complaint
        btnTrackComplaint.setOnClickListener {
            val trackId = etTrackId.text.toString().trim()
            if (trackId.isEmpty()) {
                Toast.makeText(this, "कृपया शिकायत क्रमांक दर्ज करें!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            db.collection("complaints").document(trackId).get()
                .addOnSuccessListener { doc ->
                    if (doc != null && doc.exists()) {
                        val st = doc.getString("status") ?: "Pending"
                        val reply = doc.getString("reply") ?: "जांच जारी है"
                        tvTrackResult?.text = "स्थिति: $st\nविभाग का जवाब: $reply"
                    } else {
                        tvTrackResult?.text = "इस क्रमांक से कोई शिकायत नहीं मिली।"
                    }
                }
                .addOnFailureListener {
                    tvTrackResult?.text = "शिकायत खोजने में त्रुटि हुई।"
                }
        }

        // Citizen Complaint Actions
        btnSelectCitizenPhoto.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }

        btnSubmitComplaint.setOnClickListener {
            val name = etCitizenName.text.toString().trim()
            val msg = etCitizenMessage.text.toString().trim()

            if (name.isEmpty() || msg.isEmpty()) {
                Toast.makeText(this, "कृपया नाम और समस्या का विवरण लिखें!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            var base64Image = ""
            selectedBitmap?.let { bmp ->
                val stream = ByteArrayOutputStream()
                bmp.compress(Bitmap.CompressFormat.JPEG, 70, stream)
                base64Image = Base64.encodeToString(stream.toByteArray(), Base64.DEFAULT)
            }

            val complaintId = "PHED-" + System.currentTimeMillis().toString().takeLast(6)
            val complaintData = hashMapOf(
                "complaintId" to complaintId,
                "name" to name,
                "message" to msg,
                "image" to base64Image,
                "status" to "Pending",
                "reply" to "",
                "timestamp" to SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
            )

            db.collection("complaints").document(complaintId).set(complaintData)
                .addOnSuccessListener {
                    etCitizenName.text.clear()
                    etCitizenMessage.text.clear()
                    ivCitizenPreview.visibility = View.GONE
                    selectedBitmap = null
                    AlertDialog.Builder(this)
                        .setTitle("शिकायत दर्ज सफल")
                        .setMessage("आपकी शिकायत दर्ज कर ली गई है!\nशिकायत क्रमांक: $complaintId\n(कृपया इस नंबर को सुरक्षित रख लें)")
                        .setPositiveButton("ठीक है", null)
                        .show()
                }
                .addOnFailureListener {
                    Toast.makeText(this, "शिकायत दर्ज करने में विफल!", Toast.LENGTH_SHORT).show()
                }
        }

        // Helpline & Location Buttons
        btnCallHelpline.setOnClickListener {
            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:01507357243"))
            startActivity(intent)
        }

        btnOpenLocation.setOnClickListener {
            val gmmIntentUri = Uri.parse("geo:0,0?q=PHED+Office+Raisinghnagar")
            val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
            mapIntent.setPackage("com.google.android.apps.maps")
            if (mapIntent.resolveActivity(packageManager) != null) {
                startActivity(mapIntent)
            } else {
                startActivity(Intent(Intent.ACTION_VIEW, gmmIntentUri))
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "phed_supply_channel",
                "Water Supply Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Water supply status alerts"
                enableVibration(true)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun triggerAlarmAndNotification(zoneName: String, status: String, timeNote: String) {
        try {
            val prefs = getSharedPreferences("phed_alarm_prefs", Context.MODE_PRIVATE)
            val lastAlarmTime = prefs.getLong("last_alarm_time", 0L)
            val currentTime = System.currentTimeMillis()
            val fiveMinutesInMillis = 5 * 60 * 1000L

            if (currentTime - lastAlarmTime >= fiveMinutesInMillis) {
                val alertUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                val ringtone = RingtoneManager.getRingtone(applicationContext, alertUri)
                ringtone.play()

                prefs.edit().putLong("last_alarm_time", currentTime).apply()
            }

            val builder = NotificationCompat.Builder(this, "phed_supply_channel")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("🚰 पानी सप्लाई शुरू: $zoneName")
                .setContentText("स्थिति: $status | समय: $timeNote")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(1001, builder.build())
        } catch (_: Exception) {}
    }

    private fun checkForAppUpdate(db: FirebaseFirestore) {
        try {
            db.collection("stats").document("app_update").get()
                .addOnSuccessListener { doc ->
                    try {
                        if (doc != null && doc.exists()) {
                            val latestVersion = doc.getLong("latestVersionCode")?.toInt() ?: 1
                            val pInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
                            } else {
                                @Suppress("DEPRECATION")
                                packageManager.getPackageInfo(packageName, 0)
                            }
                            val currentVersion = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                pInfo.longVersionCode.toInt()
                            } else {
                                @Suppress("DEPRECATION")
                                pInfo.versionCode
                            }

                            if (latestVersion > currentVersion) {
                                val downloadUrl = doc.getString("downloadUrl") ?: ""
                                val msg = doc.getString("message") ?: "नया वर्ज़न उपलब्ध है! कृपया अपडेट करें"
                                AlertDialog.Builder(this)
                                    .setTitle("🚀 नया अपडेट उपलब्ध है!")
                                    .setMessage(msg)
                                    .setCancelable(false)
                                    .setPositiveButton("अपडेट करें") { _, _ ->
                                        if (downloadUrl.isNotEmpty()) {
                                            downloadAndInstallApk(downloadUrl)
                                        }
                                    }
                                    .show()
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                .addOnFailureListener { e ->
                    e.printStackTrace()
                }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun downloadAndInstallApk(url: String) {
        try {
            val fileName = "phed_update.apk"
            val destination = File(getExternalFilesDir(null), fileName)
            if (destination.exists()) destination.delete()

            val request = DownloadManager.Request(Uri.parse(url))
                .setTitle("PHED App Update")
                .setDescription("नया वर्ज़न डाउनलोड हो रहा है...")
                .setDestinationUri(Uri.fromFile(destination))
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)

            val manager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val downloadId = manager.enqueue(request)

            val onComplete = object : BroadcastReceiver() {
                override fun onReceive(ctxt: Context?, intent: Intent?) {
                    val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
                    if (id == downloadId) {
                        try {
                            val apkUri = FileProvider.getUriForFile(
                                this@MainActivity2,
                                "com.phedrsnr.provider",
                                destination
                            )
                            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(apkUri, "application/vnd.android.package-archive")
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            startActivity(installIntent)
                        } catch (e: Exception) {
                            Toast.makeText(this@MainActivity2, "अपडेट इंस्टॉल नहीं हो सका: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                        try {
                            unregisterReceiver(this)
                        } catch (_: Exception) {}
                    }
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(
                    onComplete,
                    IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                    Context.RECEIVER_EXPORTED
                )
            } else {
                registerReceiver(
                    onComplete,
                    IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
                )
            }
        } catch (e: Exception) {
            Toast.makeText(this, "डाउनलोड शुरू नहीं हो सका: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}