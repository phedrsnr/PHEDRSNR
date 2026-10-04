package com.phedrsnr

import android.app.AlertDialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.util.Base64
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.firebase.firestore.FirebaseFirestore
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.*

class MainActivity2 : AppCompatActivity() {

    private var selectedBitmap: Bitmap? = null

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            try {
                val inputStream = contentResolver.openInputStream(it)
                val originalBitmap = BitmapFactory.decodeStream(inputStream)
                selectedBitmap = Bitmap.createScaledBitmap(originalBitmap, 480, 640, true)
                findViewById<ImageView>(R.id.ivCitizenPreview).apply {
                    setImageBitmap(selectedBitmap)
                    visibility = View.VISIBLE
                }
            } catch (e: Exception) {
                Toast.makeText(this, "फोटो लोड करने में समस्या हुई", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main2)

        val spTanki = findViewById<Spinner>(R.id.spTanki)
        val spZone = findViewById<Spinner>(R.id.spZone)
        val btnSetAlarmZone = findViewById<Button>(R.id.btnSetAlarmZone)
        val tvSelectedZoneStatus = findViewById<TextView?>(R.id.tvSelectedZoneStatus)
        val tvLiveStatus = findViewById<TextView?>(R.id.tvLiveStatus)

        // Multiple zones scroll karke dekhne ke liye
        tvLiveStatus?.movementMethod = ScrollingMovementMethod()

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

        // Notification Permission & Channel
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }
        createNotificationChannel()
        checkForAppUpdate(db)

        // Sabhi Tankiyan aur Unke Zones
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
                "हाण्डा कॉलोनी",
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

        val savedTanki = prefs.getString("saved_tanki", "") ?: ""
        val savedZone = prefs.getString("saved_zone", "") ?: ""
        if (savedZone.isNotEmpty()) {
            tvSelectedZoneStatus?.text = "चुना गया क्षेत्र: $savedTanki - $savedZone (अलार्म एक्टिव)"
        }

        spTanki.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedTanki = tankiList[position]
                val zones = tankiZoneMap[selectedTanki] ?: listOf("-- ज़ोन चुनें --")
                val zoneAdapter = ArrayAdapter(this@MainActivity2, android.R.layout.simple_spinner_dropdown_item, zones)
                spZone.adapter = zoneAdapter
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // Zone Set Button Listener
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

            tvSelectedZoneStatus?.text = "जाँच हो रही है..."

            // Turant Firebase se check karein
            db.collection("zones").get().addOnSuccessListener { snapshot ->
                var found = false
                val searchKey = normalizeZoneName(selZone)

                for (doc in snapshot.documents) {
                    val rawName = doc.getString("name") ?: doc.id
                    val docTank = doc.getString("tank") ?: ""
                    val status = doc.getString("status") ?: ""
                    val time = doc.getString("timeNote") ?: ""

                    val normDocName = normalizeZoneName(rawName)
                    val normDocId = normalizeZoneName(doc.id)

                    if (normDocName.contains(searchKey) || searchKey.contains(normDocName) || normDocId.contains(searchKey)) {
                        tvSelectedZoneStatus?.text = "चुना गया: $selTanki - $selZone\nस्थिति: $status" + if (time.isNotEmpty()) " ($time)" else ""
                        found = true

                        if (status.contains("चालू") || status.contains("शुरू")) {
                            triggerAlarmAndNotification(selZone, status, time)
                        }
                        break
                    }
                }
                if (!found) {
                    tvSelectedZoneStatus?.text = "चुना गया: $selTanki - $selZone\nस्थिति: अभी सप्लाई बंद है (अलार्म सेट)"
                }
            }
        }

        // LIVE SUPPLY STATUS LISTENER (Sabhi Zones aur Tankiyon ko Scan Karega)
        tvLiveStatus?.text = "लाइव स्थिति लोड हो रही है..."
        db.collection("zones")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    tvLiveStatus?.text = "लाइव स्थिति: लोड में समस्या (${error.localizedMessage})"
                    return@addSnapshotListener
                }

                if (snapshot != null && !snapshot.isEmpty) {
                    val activeList = mutableListOf<String>()
                    val mySavedZone = prefs.getString("saved_zone", "") ?: ""
                    val searchKey = if (mySavedZone.isNotEmpty()) normalizeZoneName(mySavedZone) else ""
                    var isMyZoneActive = false
                    var matchedTime = ""

                    for (doc in snapshot.documents) {
                        // 1. Name ya ID nikalein
                        val zoneName = doc.getString("name") ?: doc.id
                        val docTank = doc.getString("tank") ?: ""
                        val statusText = doc.getString("status") ?: ""
                        val time = doc.getString("timeNote") ?: ""

                        // 2. Agar status 'चालू' ya 'शुरू' hai
                        if (statusText.contains("चालू") || statusText.contains("शुरू")) {
                            val displayHeader = if (docTank.isNotEmpty() && !zoneName.contains(docTank)) "$docTank - $zoneName" else zoneName
                            val line = "💧 $displayHeader: $statusText" + if (time.isNotEmpty()) " ($time)" else ""
                            activeList.add(line)

                            // 3. User ke chune huye zone se match
                            if (searchKey.isNotEmpty()) {
                                val normName = normalizeZoneName(zoneName)
                                val normId = normalizeZoneName(doc.id)
                                if (normName.contains(searchKey) || searchKey.contains(normName) || normId.contains(searchKey)) {
                                    isMyZoneActive = true
                                    matchedTime = time
                                }
                            }
                        }
                    }

                    if (activeList.isNotEmpty()) {
                        tvLiveStatus?.text = "🔴 चालू सप्लाई स्थिति:\n\n" + activeList.joinToString("\n\n")

                        if (isMyZoneActive) {
                            triggerAlarmAndNotification(mySavedZone, "सप्लाई चालू", matchedTime)
                        }
                    } else {
                        tvLiveStatus?.text = "वर्तमान स्थिति: अभी किसी भी टंकी/ज़ोन में सप्लाई चालू नहीं है।"
                    }
                } else {
                    tvLiveStatus?.text = "वर्तमान स्थिति: कोई ज़ोन डेटा नहीं मिला"
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
                        val reply = doc.getString("reply") ?: "जाँच जारी है"
                        tvTrackResult?.text = "स्थिति: $st\nविभाग का जवाब: $reply"
                    } else {
                        tvTrackResult?.text = "इस क्रमांक से कोई शिकायत नहीं मिली।"
                    }
                }
                .addOnFailureListener {
                    tvTrackResult?.text = "शिकायत खोजने में त्रुटि हुई।"
                }
        }

        // Submit Complaint (Optimized Image Compression)
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
                bmp.compress(Bitmap.CompressFormat.JPEG, 60, stream)
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
                .addOnFailureListener { exception ->
                    AlertDialog.Builder(this)
                        .setTitle("शिकायत दर्ज नहीं हुई")
                        .setMessage("Error: ${exception.localizedMessage}")
                        .setPositiveButton("ठीक है", null)
                        .show()
                }
        }

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

    // Zone Matching Helper Function (Hindi & English Sabhi Ko Match Karega)
    private fun normalizeZoneName(input: String): String {
        return input.lowercase(Locale.ROOT)
            .replace("टंकी", "")
            .replace("हेडवर्क्स", "")
            .replace("ज़ोन", "")
            .replace("जोन", "")
            .replace("jone", "")
            .replace("zone", "")
            .replace("area", "")
            .replace("एरिया", "")
            .replace("-", "")
            .replace(" ", "")
            .trim()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val audioAttributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_ALARM)
                .build()

            val channel = NotificationChannel(
                "phed_supply_channel",
                "Water Supply Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Water supply status alerts"
                enableVibration(true)
                setSound(soundUri, audioAttributes)
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

            if (currentTime - lastAlarmTime >= 3 * 60 * 1000L) {
                val alertUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                val ringtone = RingtoneManager.getRingtone(applicationContext, alertUri)
                ringtone?.play()

                prefs.edit().putLong("last_alarm_time", currentTime).apply()
            }

            val builder = NotificationCompat.Builder(this, "phed_supply_channel")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("🚰 पानी सप्लाई शुरू: $zoneName")
                .setContentText("स्थिति: $status" + if (timeNote.isNotEmpty()) " ($timeNote)" else "")
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
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
                                            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl)).apply {
                                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            }
                                            startActivity(browserIntent)
                                        }
                                    }
                                    .show()
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}