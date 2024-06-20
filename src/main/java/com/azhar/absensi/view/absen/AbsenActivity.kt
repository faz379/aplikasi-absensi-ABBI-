package com.azhar.absensi.view.absen

import android.Manifest
import android.app.DatePickerDialog
import android.app.ProgressDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.location.Geocoder
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.MenuItem
import android.widget.DatePicker
import android.widget.Toast
import android.util.Base64
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.ViewModelProvider
import com.azhar.absensi.BuildConfig
import com.azhar.absensi.R
import com.azhar.absensi.databinding.ActivityAbsenBinding
import com.azhar.absensi.viewmodel.AbsenViewModel
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.google.android.gms.location.LocationServices
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class AbsenActivity : AppCompatActivity() {
    var REQ_CAMERA = 101
    var strCurrentLatitude = 0.0
    var strCurrentLongitude = 0.0
    var strFilePath: String = ""
    var strLatitude = "0"
    var strLongitude = "0"
    lateinit var fileDirectoty: File
    lateinit var imageFilename: File
    lateinit var exifInterface: ExifInterface
    lateinit var strBase64Photo: String
    lateinit var strCurrentLocation: String
    lateinit var strTitle: String
    lateinit var strTimeStamp: String
    lateinit var strImageName: String
    lateinit var absenViewModel: AbsenViewModel
    lateinit var progressDialog: ProgressDialog
    private lateinit var binding: ActivityAbsenBinding
    private lateinit var database: DatabaseReference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAbsenBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setInitLayout()
        setCurrentLocation()
        setUploadData()
        setUserEmail()
    }

    private fun setUserEmail() {
        val user = FirebaseAuth.getInstance().currentUser
        if (user != null) {
            val userEmail = user.email
            if (userEmail != null) {
                val userName = userEmail.split("@")[0]
                binding.inputNama.setText(userName)
            }
        }
    }

    private fun setCurrentLocation() {
        progressDialog.show()
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
            && ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return
        }
        fusedLocationClient.lastLocation
            .addOnSuccessListener(this) { location ->
                progressDialog.dismiss()
                if (location != null) {
                    strCurrentLatitude = location.latitude
                    strCurrentLongitude = location.longitude
                    val geocoder = Geocoder(this@AbsenActivity, Locale.getDefault())
                    try {
                        val addressList =
                            geocoder.getFromLocation(strCurrentLatitude, strCurrentLongitude, 1)
                        if (addressList != null && addressList.isNotEmpty()) {
                            strCurrentLocation = addressList[0].getAddressLine(0)
                            binding.inputLokasi.setText(strCurrentLocation)
                        }
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }
                } else {
                    progressDialog.dismiss()
                    Toast.makeText(this@AbsenActivity,
                        "Ups, gagal mendapatkan lokasi. Silahkan periksa GPS atau koneksi internet Anda!",
                        Toast.LENGTH_SHORT).show()
                    strLatitude = "0"
                    strLongitude = "0"
                }
            }
    }

    private fun setInitLayout() {
        progressDialog = ProgressDialog(this)
        strTitle = intent.extras?.getString(DATA_TITLE).toString()

        strTitle?.let {
            binding.tvTitle.text = it

            when {
                strTitle.equals("Absen Masuk", ignoreCase = true) -> {
                    binding.inputKeterangan.setText("Masuk")
                    binding.btnAbsen.text = "Masuk"
                }
                strTitle.equals("Absen Keluar", ignoreCase = true) -> {
                    binding.inputKeterangan.setText("Keluar")
                    binding.btnAbsen.text = "Keluar"
                }
                strTitle.equals("Perizinan", ignoreCase = true) -> {
                    binding.btnAbsen.text = "Izin"
                }
            }
        }

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        absenViewModel = ViewModelProvider(this, ViewModelProvider.AndroidViewModelFactory.getInstance(this.application)).get(AbsenViewModel::class.java)

        binding.inputTanggal.setOnClickListener {
            val tanggalAbsen = Calendar.getInstance()
            val date =
                DatePickerDialog.OnDateSetListener { _: DatePicker, year: Int, monthOfYear: Int, dayOfMonth: Int ->
                    tanggalAbsen[Calendar.YEAR] = year
                    tanggalAbsen[Calendar.MONTH] = monthOfYear
                    tanggalAbsen[Calendar.DAY_OF_MONTH] = dayOfMonth
                    val strFormatDefault = "dd MMMM yyyy HH:mm"
                    val simpleDateFormat = SimpleDateFormat(strFormatDefault, Locale.getDefault())
                    binding.inputTanggal.setText(simpleDateFormat.format(tanggalAbsen.time))
                }
            DatePickerDialog(
                this@AbsenActivity, date,
                tanggalAbsen[Calendar.YEAR],
                tanggalAbsen[Calendar.MONTH],
                tanggalAbsen[Calendar.DAY_OF_MONTH]
            ).show()
        }

        binding.layoutImage.setOnClickListener {
            Dexter.withContext(this@AbsenActivity)
                .withPermissions(
                    Manifest.permission.CAMERA,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION
                )
                .withListener(object : MultiplePermissionsListener {
                    override fun onPermissionsChecked(report: MultiplePermissionsReport) {
                        if (report.areAllPermissionsGranted()) {
                            try {
                                val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                                cameraIntent.putExtra(
                                    "com.google.assistant.extra.USE_FRONT_CAMERA",
                                    true
                                )
                                cameraIntent.putExtra("android.intent.extra.USE_FRONT_CAMERA", true)
                                cameraIntent.putExtra("android.intent.extras.LENS_FACING_FRONT", 1)
                                cameraIntent.putExtra("android.intent.extras.CAMERA_FACING", 1)

                                // Samsung
                                cameraIntent.putExtra("camerafacing", "front")
                                cameraIntent.putExtra("previous_mode", "front")

                                // Huawei
                                cameraIntent.putExtra("default_camera", "1")
                                cameraIntent.putExtra(
                                    "default_mode",
                                    "com.huawei.camera2.mode.photo.PhotoMode")
                                cameraIntent.putExtra(
                                    MediaStore.EXTRA_OUTPUT,
                                    FileProvider.getUriForFile(
                                        this@AbsenActivity,
                                        BuildConfig.APPLICATION_ID + ".provider",
                                        createImageFile()
                                    )
                                )
                                startActivityForResult(cameraIntent, REQ_CAMERA)
                            } catch (ex: IOException) {
                                Toast.makeText(this@AbsenActivity,
                                    "Ups, gagal membuka kamera", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }

                    override fun onPermissionRationaleShouldBeShown(
                        permissions: List<PermissionRequest>,
                        token: PermissionToken) {
                        token.continuePermissionRequest()
                    }
                }).check()
        }
    }

    private fun setUploadData() {
        binding.btnAbsen.setOnClickListener {
            val strNama = binding.inputNama.text.toString()
            val strTanggal = binding.inputTanggal.text.toString()
            val strKeterangan = binding.inputKeterangan.text.toString()

            if (strFilePath.isEmpty() || strNama.isEmpty() || strCurrentLocation.isEmpty()
                || strTanggal.isEmpty() || strKeterangan.isEmpty()) {
                Toast.makeText(this@AbsenActivity,
                    "Data tidak boleh ada yang kosong!", Toast.LENGTH_SHORT).show()
            } else {
                val historyAbsen = HistoryAbsen(
                    nama = strNama,
                    tanggal = strTanggal,
                    lokasi = strCurrentLocation,
                    keterangan = strKeterangan,
                    photo = strBase64Photo,
                )

                // Kirim data ke ViewModel
                absenViewModel.addDataAbsen(
                    strBase64Photo,
                    strNama,
                    strTanggal,
                    strCurrentLocation,
                    strKeterangan
                )

                // Kirim data ke Firebase
                database = FirebaseDatabase.getInstance().getReference("HistoryAbsen")
                database.child(strNama).setValue(historyAbsen)
                    .addOnSuccessListener {
                        Toast.makeText(this@AbsenActivity,
                            "Laporan Anda terkirim, tunggu info selanjutnya ya!", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                    .addOnFailureListener { e ->
                        Log.e("AbsenActivity", "Gagal mengirim data ke Firebase", e)
                        Toast.makeText(this@AbsenActivity,
                            "Gagal mengirim laporan, coba lagi!", Toast.LENGTH_SHORT).show()
                    }

                // Beri tahu pengguna bahwa data sedang dikirim
                Toast.makeText(this@AbsenActivity,
                    "Mengirim laporan Anda...", Toast.LENGTH_SHORT).show()
            }
        }
    }




    @Throws(IOException::class)
    private fun createImageFile(): File {
        strTimeStamp = SimpleDateFormat("dd MMMM yyyy HH:mm:ss").format(Date())
        strImageName = "IMG_"
        fileDirectoty = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "")
        imageFilename = File.createTempFile(strImageName, ".jpg", fileDirectoty)
        strFilePath = imageFilename.absolutePath
        return imageFilename
    }

    public override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        convertImage(strFilePath)
    }

    private fun convertImage(imageFilePath: String?) {
        if (imageFilePath.isNullOrEmpty()) return

        val imageFile = File(imageFilePath)
        if (imageFile.exists()) {
            val options = BitmapFactory.Options()
            var bitmapImage = BitmapFactory.decodeFile(strFilePath, options) // Menggunakan imageFilePath di sini

            try {
                exifInterface = ExifInterface(imageFile.absolutePath)
            } catch (e: IOException) {
                e.printStackTrace()
            }
            val orientation = exifInterface.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED)
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> bitmapImage = rotateBitmap(bitmapImage, 90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> bitmapImage = rotateBitmap(bitmapImage, 180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> bitmapImage = rotateBitmap(bitmapImage, 270f)
                ExifInterface.ORIENTATION_NORMAL -> {}
                else -> {}
            }

            // Kompresi gambar sebelum mengonversi ke Base64
            val compressedBitmap: Bitmap = Bitmap.createScaledBitmap(bitmapImage, bitmapImage.width / 2, bitmapImage.height / 2, true)
            val outputStream = ByteArrayOutputStream()
            compressedBitmap.compress(Bitmap.CompressFormat.JPEG, 10, outputStream) // Ubah kualitas kompresi sesuai kebutuhan
            val compressedImageBytes = outputStream.toByteArray()
            strBase64Photo = Base64.encodeToString(compressedImageBytes, Base64.DEFAULT)

            val into = Glide.with(this)
                .load(compressedBitmap)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .placeholder(R.drawable.ic_photo_camera)
                .into(binding.imageSelfie)
        }
    }



    private fun rotateBitmap(source: Bitmap, angle: Float): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(angle)
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String?>,
        grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            recreate()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    companion object {
        const val DATA_TITLE = "TITLE"
    }
}
