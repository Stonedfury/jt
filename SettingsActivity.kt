package com.job.jobtracker

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException

class SettingsActivity : AppCompatActivity() {

    private lateinit var switchUseCustomLogo: SwitchCompat
    private lateinit var layoutLogoUpload: LinearLayout
    private lateinit var buttonUploadLogo: Button
    private lateinit var imageLogoPreview: ImageView
    private lateinit var switchUseCustomPrefix: SwitchCompat
    private lateinit var editTextCustomPrefix: EditText
    private lateinit var switchTrackMileage: SwitchCompat
    private lateinit var buttonSaveSettings: Button
    private lateinit var mainMenuButton: Button
    private lateinit var noSettingsMessage: TextView

    private var selectedLogoUri: Uri? = null
    private lateinit var pickImageLauncher: ActivityResultLauncher<Intent>
    private lateinit var captureImageLauncher: ActivityResultLauncher<Intent>

    private lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        // Set up the custom ActionBar
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowCustomEnabled(true)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        supportActionBar?.setCustomView(R.layout.custom_action_bar)

        // Initialize SessionManager
        sessionManager = SessionManager(this)

        switchUseCustomLogo = findViewById(R.id.switch_use_custom_logo)
        layoutLogoUpload = findViewById(R.id.layout_logo_upload)
        buttonUploadLogo = findViewById(R.id.button_upload_logo)
        imageLogoPreview = findViewById(R.id.image_logo_preview)
        switchUseCustomPrefix = findViewById(R.id.switch_use_custom_prefix)
        editTextCustomPrefix = findViewById(R.id.edit_text_custom_prefix)
        switchTrackMileage = findViewById(R.id.switch_track_mileage)
        buttonSaveSettings = findViewById(R.id.button_save_settings)
        mainMenuButton = findViewById(R.id.mainmenubutton)
        noSettingsMessage = findViewById(R.id.no_settings_message)

        switchUseCustomLogo.setOnCheckedChangeListener { _, isChecked ->
            layoutLogoUpload.visibility = if (isChecked) View.VISIBLE else View.GONE
        }

        buttonUploadLogo.setOnClickListener {
            if (checkAndRequestPermissions()) {
                val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.INTERNAL_CONTENT_URI)
                pickImageLauncher.launch(intent)
            }
        }

        switchUseCustomPrefix.setOnCheckedChangeListener { _, isChecked ->
            editTextCustomPrefix.visibility = if (isChecked) View.VISIBLE else View.GONE
        }

        buttonSaveSettings.setOnClickListener {
            saveSettings()
        }

        mainMenuButton.setOnClickListener {
            val intent = Intent(this, LoginSuccessfulActivity::class.java)
            startActivity(intent)
        }

        pickImageLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                selectedLogoUri = result.data?.data
                imageLogoPreview.setImageURI(selectedLogoUri)
                imageLogoPreview.visibility = View.VISIBLE
            }
        }

        captureImageLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val photo = result.data?.extras?.get("data") as Bitmap
                val stream = ByteArrayOutputStream()
                photo.compress(Bitmap.CompressFormat.PNG, 100, stream)
                val byteArray = stream.toByteArray()
                val bitmapUri = Uri.parse(MediaStore.Images.Media.insertImage(contentResolver, photo, "Title", null))
                selectedLogoUri = bitmapUri
                imageLogoPreview.setImageURI(selectedLogoUri)
                imageLogoPreview.visibility = View.VISIBLE
            }
        }

        loadSettings()
    }

    private fun checkAndRequestPermissions(): Boolean {
        val permissionRead = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
        val listPermissionsNeeded = ArrayList<String>()
        if (permissionRead != PackageManager.PERMISSION_GRANTED) {
            listPermissionsNeeded.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        if (listPermissionsNeeded.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, listPermissionsNeeded.toTypedArray(), 1)
            return false
        }
        return true
    }

    private fun loadSettings() {
        val tenantID = sessionManager.getTenantID()
        val url = "https://jobtracker.pineflatshandyservices.com/tenant_settings.php?TenantID=$tenantID"

        val client = OkHttpClient()
        val request = Request.Builder().url(url).get().build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(this@SettingsActivity, "Failed to load settings: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()
                runOnUiThread {
                    if (response.isSuccessful && responseBody != null) {
                        try {
                            val jsonResponse = JSONObject(responseBody)
                            if (jsonResponse.getBoolean("success")) {
                                val settings = jsonResponse.getJSONObject("settings")

                                switchUseCustomLogo.isChecked = settings.getInt("UseCustomLogo") == 1
                                switchUseCustomPrefix.isChecked = settings.getInt("UseInvoicePrefix") == 1
                                editTextCustomPrefix.setText(settings.getString("InvoicePrefix"))
                                switchTrackMileage.isChecked = settings.getInt("TrackMileage") == 1

                                if (settings.getInt("UseCustomLogo") == 1) {
                                    val logoUrl = settings.getString("LogoURL")
                                    layoutLogoUpload.visibility = View.VISIBLE
                                    imageLogoPreview.visibility = View.VISIBLE
                                    // Load the logo image into the ImageView using Glide
                                    Glide.with(this@SettingsActivity).load(logoUrl).into(imageLogoPreview)
                                } else {
                                    imageLogoPreview.visibility = View.GONE
                                }
                                noSettingsMessage.visibility = View.GONE
                            } else {
                                noSettingsMessage.visibility = View.VISIBLE
                                switchUseCustomLogo.isChecked = false
                                switchUseCustomPrefix.isChecked = false
                                editTextCustomPrefix.text.clear()
                                switchTrackMileage.isChecked = false
                                layoutLogoUpload.visibility = View.GONE
                                imageLogoPreview.visibility = View.GONE
                                Toast.makeText(this@SettingsActivity, jsonResponse.getString("message"), Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            Toast.makeText(this@SettingsActivity, "Failed to parse settings: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(this@SettingsActivity, "Failed to load settings.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }

    private fun saveSettings() {
        val tenantID = sessionManager.getTenantID()
        val useCustomLogo = if (switchUseCustomLogo.isChecked) "1" else "0"
        val useInvoicePrefix = if (switchUseCustomPrefix.isChecked) "1" else "0"
        val customPrefix = if (useInvoicePrefix == "1") editTextCustomPrefix.text.toString().trim() else ""
        val trackMileage = if (switchTrackMileage.isChecked) "1" else "0"

        Log.d("SettingsActivity", "TenantID: $tenantID, UseCustomLogo: $useCustomLogo, UseInvoicePrefix: $useInvoicePrefix, InvoicePrefix: $customPrefix, TrackMileage: $trackMileage")

        val url = "https://jobtracker.pineflatshandyservices.com/tenant_settings.php"
        val client = OkHttpClient()

        val formBodyBuilder = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("TenantID", tenantID.toString())
            .addFormDataPart("UseCustomLogo", useCustomLogo)
            .addFormDataPart("UseInvoicePrefix", useInvoicePrefix)
            .addFormDataPart("InvoicePrefix", customPrefix)
            .addFormDataPart("TrackMileage", trackMileage)

        if (selectedLogoUri != null && useCustomLogo == "1") {
            val inputStream = contentResolver.openInputStream(selectedLogoUri!!)
            val logoBytes = inputStream?.readBytes()
            logoBytes?.let {
                formBodyBuilder.addFormDataPart(
                    "LogoURL",
                    "custom_logo.png",
                    it.toRequestBody("image/*".toMediaType())
                )
            }
        } else {
            formBodyBuilder.addFormDataPart("LogoURL", "")
        }

        val requestBody = formBodyBuilder.build()
        val request = Request.Builder().url(url).post(requestBody).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(this@SettingsActivity, "Failed to save settings: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                runOnUiThread {
                    if (response.isSuccessful) {
                        Toast.makeText(this@SettingsActivity, "Settings saved successfully!", Toast.LENGTH_SHORT).show()
                        loadSettings() // Refresh the settings after saving
                    } else {
                        Toast.makeText(this@SettingsActivity, "Failed to save settings.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            1 -> {
                if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.INTERNAL_CONTENT_URI)
                    pickImageLauncher.launch(intent)
                } else {
                    Toast.makeText(this, "Permission denied to read your External storage", Toast.LENGTH_SHORT).show()
                }
                return
            }
        }
    }
}
