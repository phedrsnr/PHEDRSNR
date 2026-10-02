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

        // 2. Auto-Update Check
        checkForAppUpdate(db)

        // 3. Admin Panel Mapping (टंकी और उसके अंदर के ज़ोन - अपडेटेड)
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
                "हांडा कॉलोनी",
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

        // जब यूज़र टंकी चुनेगा तो नीचे वाले Spinner में उसी के ज़ोन आएँगे
        spTanki.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedTanki = tankiList[position]
                val currentZones = tankiZoneMap[selectedTanki] ?: listOf("-- ज़ोन चुनें --")
                val zoneAdapter = ArrayAdapter(this@MainActivity2, android.R.layout.simple_spinner_dropdown_item, currentZones)
                spZone.adapter = zoneAdapter

                // अगर पहले से सेव्ड है तो सेलेक्ट कर दें
                if (selectedTanki == savedTanki) {
                    val zoneIdx = currentZones.indexOf(savedZone)
                    if (zoneIdx >= 0) spZone.setSelection(zoneIdx)
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // बटन क्लिक: अलार्म व ज़ोन सेव करना
        btnSetAlarmZone.setOnClickListener {
            val selectedTanki = spTanki.selectedItem?.toString() ?: ""
            val selectedZ = spZone.selectedItem?.toString() ?: ""

            if (selectedTanki.startsWith("--") || selectedZ.startsWith("--")) {
                Toast.makeText(this, "कृपया टंकी और ज़ोन दोनों चुनें", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            prefs.edit()
                .putString("saved_tanki", selectedTanki)
                .putString("saved_zone", selectedZ)
                .apply()

            tvSelectedZoneStatus?.text = "चुना गया क्षेत्र: $selectedTanki - $selectedZ\n(सप्लाई शुरू होने पर अलार्म बजेगा)"
            Toast.makeText(this, "अलार्म सेट किया गया: $selectedZ", Toast.LENGTH_SHORT).show()
        }

        // 4. Helpline & Location
        btnCallHelpline.setOnClickListener {
            startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:01507357243")))
        }

        btnOpenLocation.setOnClickListener {
            val mapIntent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=PHED+Office+Raisinghnagar")).apply {
                setPackage("com.google.android.apps.maps")
            }
            try {
                startActivity(mapIntent)
            } catch (e: Exception) {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://maps.google.com/?q=PHED+Office+Raisinghnagar")))
            }
        }

        // 5. Complaint Photo
        btnSelectCitizenPhoto.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }

        // 6. Submit Complaint
        btnSubmitComplaint.setOnClickListener {
            val citizenName = etCitizenName.text.toString().trim()
            val message = etCitizenMessage.text.toString().trim()

            if (message.isEmpty()) {
                Toast.makeText(this, "कृपया समस्या का विवरण लिखें", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val trackingId = (1000..9999).random().toString()
            val complaintData = hashMapOf(
                "trackingId" to trackingId,
                "name" to if (citizenName.isEmpty()) "नागरिक" else citizenName,
                "message" to message,
                "timestamp" to SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date()),
                "status" to "लंबित (Pending)"
            )

            selectedBitmap?.let { bitmap ->
                val baos = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 70, baos)
                complaintData["photoBase64"] = Base64.encodeToString(baos.toByteArray(), Base64.DEFAULT)
            }

            db.collection("citizen_complaints").document(trackingId)
                .set(complaintData)
                .addOnSuccessListener {
                    AlertDialog.Builder(this)
                        .setTitle("शिकायत दर्ज सफल!")
                        .setMessage("आपकी शिकायत ID है: $trackingId\nकृपया इसे सुरक्षित नोट कर लें।")
                        .setPositiveButton("OK", null)
                        .show()
                    etCitizenName.text.clear()
                    etCitizenMessage.text.clear()
                    ivCitizenPreview.setImageBitmap(null)
                    ivCitizenPreview.visibility = View.GONE
                    selectedBitmap = null
                }
                .addOnFailureListener {
                    Toast.makeText(this, "शिकायत दर्ज करने में विफल: ${it.message}", Toast.LENGTH_LONG).show()
                }
        }

        // 7. Track Complaint
        btnTrackComplaint.setOnClickListener {
            val id = etTrackId.text.toString().trim()
            if (id.isEmpty()) {
                Toast.makeText(this, "कृपया ट्रैकिंग ID दर्ज करें", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            db.collection("citizen_complaints").document(id).get()
                .addOnSuccessListener { doc ->
                    if (doc != null && doc.exists()) {
                        val status = doc.getString("status") ?: "प्रक्रियाधीन"
                        val time = doc.getString("timestamp") ?: ""
                        tvTrackResult?.text = "शिकायत स्थिति: $status\nदिनांक: $time"
                    } else {
                        tvTrackResult?.text = "कोई शिकायत नहीं मिली। कृपया सही ID डालें।"
                    }
                }
                .addOnFailureListener {
                    tvTrackResult?.text = "जाँच करने में समस्या आई।"
                }
        }

        // 8. Live Zones Listener & Trigger Alarm
        db.collection("zones").addSnapshotListener { snapshot, _ ->
            if (snapshot != null) {
                val sb = StringBuilder()
                val currentSavedZone = prefs.getString("saved_zone", "") ?: ""

                for (doc in snapshot.documents) {
                    val name = doc.getString("name") ?: ""
                    val status = doc.getString("status") ?: "बंद"
                    val timeNote = doc.getString("timeNote") ?: ""

                    sb.append("💧 ").append(name).append(": ").append(status)
                    if (timeNote.isNotEmpty()) sb.append(" (").append(timeNote).append(")")
                    sb.append("\n\n")

                    // Alarm check (Matching zone name or part of it)
                    if (currentSavedZone.isNotEmpty() && name.contains(currentSavedZone, ignoreCase = true) &&
                        (status.contains("चालू") || status.contains("सप्लाई शुरू"))
                    ) {
                        triggerAlarmAndNotification(name, status, timeNote)
                    }
                }

                if (sb.isNotEmpty()) {
                    tvLiveStatus?.text = sb.toString().trim()
                } else {
                    tvLiveStatus?.text = "वर्तमान में कोई लाइव सप्लाई अपडेट नहीं है।"
                }
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
            val alertUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val ringtone = RingtoneManager.getRingtone(applicationContext, alertUri)
            ringtone.play()

            val builder = NotificationCompat.Builder(this, "phed_supply_channel")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("🚰 पानी सप्लाई शुरू: $zoneName")
                .setContentText("स्थिति: $status | समय: $timeNote")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(1001, builder.build())
        } catch (e: Exception) {
            // Ignore
        }
    }

    private fun checkForAppUpdate(db: FirebaseFirestore) {
        db.collection("stats").document("app_update").get()
            .addOnSuccessListener { doc ->
                if (doc != null && doc.exists()) {
                    val latestVersion = doc.getLong("latestVersionCode")?.toInt() ?: 1
                    val currentVersion = packageManager.getPackageInfo(packageName, 0).versionCode

                    if (latestVersion > currentVersion) {
                        val downloadUrl = doc.getString("downloadUrl") ?: ""
                        val msg = doc.getString("message") ?: "नया वर्ज़न उपलब्ध है! कृपया अपडेट करें।"
                        AlertDialog.Builder(this)
                            .setTitle("🚀 नया अपडेट उपलब्ध है!")
                            .setMessage(msg)
                            .setCancelable(false)
                            .setPositiveButton("अपडेट करें") { _, _ ->
                                downloadAndInstallApk(downloadUrl)
                            }
                            .show()
                    }
                }
            }
    }

    private fun downloadAndInstallApk(url: String) {
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
            override fun onReceive(context: Context?, intent: Intent?) {
                val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (id == downloadId) {
                    installDownloadedApk(destination)
                    unregisterReceiver(this)
                }
            }
        }

        ContextCompat.registerReceiver(
            this,
            onComplete,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            ContextCompat.RECEIVER_EXPORTED
        )
    }

    private fun installDownloadedApk(file: File) {
        try {
            val apkUri = FileProvider.getUriForFile(this, "$packageName.provider", file)
            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(installIntent)
        } catch (e: Exception) {
            Toast.makeText(this, "इन्स्टॉलेशन में समस्या: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}