package com.acalapatih.oneayat.ui.hafalanquran.activity

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.addCallback
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.view.isVisible
import com.acalapatih.oneayat.BaseActivity
import com.acalapatih.oneayat.R
import com.acalapatih.oneayat.core.data.Resource
import com.acalapatih.oneayat.core.data.source.local.entity.*
import com.acalapatih.oneayat.core.domain.model.hafalanquran.HafalanAyatModel
import com.acalapatih.oneayat.databinding.ActivityHafalanAyatBinding
import com.acalapatih.oneayat.ui.bookmark.activity.BookmarkActivity
import com.acalapatih.oneayat.ui.hafalanquran.viewmodel.HafalanAyatViewModel
import com.acalapatih.oneayat.utils.Const
import com.google.api.gax.rpc.ApiStreamObserver
import com.google.cloud.speech.v1.*
import com.google.protobuf.ByteString
import org.apache.commons.text.similarity.JaroWinklerSimilarity
import org.koin.androidx.viewmodel.ext.android.viewModel
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class HafalanActivity : BaseActivity<ActivityHafalanAyatBinding>() {
    private val viewModel by viewModel<HafalanAyatViewModel>()
    private val nomorSurat by lazy { intent.getStringExtra(Const.NOMOR_SURAT) }
    private val nomorAyat by lazy { intent.getStringExtra(Const.NOMOR_AYAT) }
    private lateinit var audioAyatPlayer: MediaPlayer
    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var hasilText: String

    override fun getViewBinding(): ActivityHafalanAyatBinding =
        ActivityHafalanAyatBinding.inflate(layoutInflater)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()

        nomorSurat?.let { nomorAyat?.let { it1 -> viewModel.getAyat(it, it1) } }
        viewModel.getToken()

        initView()
    }

    private fun initView() {
        requestPermissions()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            Const.REQUEST_PERMISSION_CODE -> {
                if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                    initObserver()
                    initListener()
                } else {
                    showToast(
                        this.getString(R.string.toast_permission),
                        Toast.LENGTH_SHORT
                    )
                    onBackPressedDispatcher.onBackPressed()
                    onBackPressedDispatcher.addCallback {
                        finish()
                    }
                }
            }
        }
    }

    private fun requestPermissions() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(
                Manifest.permission.RECORD_AUDIO
            ),
            Const.REQUEST_PERMISSION_CODE
        )
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun initObserver() {
        viewModel.getAyat.observe(this) { model ->
            when (model) {
                is Resource.Loading -> {
                    showLoading(true)
                }
                is Resource.Success -> {
                    showLoading(false)
                    model.data?.let { data ->
                        getAyat(data)
                    }
                }
                is Resource.Error -> {
                    showLoading(false)
                    model.message?.let {
                        println(it)
                        showToast(
                            it, Toast.LENGTH_SHORT
                        )
                    }
                }
            }
        }
    }

    private fun showLoading(value: Boolean) {
        binding.progressBar.isVisible = value
    }

    @RequiresApi(Build.VERSION_CODES.O)
    @SuppressLint("SetTextI18n")
    private fun getAyat(data: HafalanAyatModel) {
        with(binding) {
            tvSurat.text = data.namaSurat
            tvAyatKe.text = "Ayat ${data.nomorAyat}"
            tvAyat.text = data.lafadzAyat

            btnAudio.setOnClickListener {
                cvAudioAyat.isVisible = true
                tvAudioAyat.text = "${data.namaSurat} : ${data.nomorAyat}"

                audioAyatPlayer = MediaPlayer.create(this@HafalanActivity, data.audioAyat.toUri())
                audioAyatPlayer.start()

                btnStop.setOnClickListener {
                    audioAyatPlayer.stop()
                    audioAyatPlayer.release()
                    cvAudioAyat.isVisible = false
                }
            }

            btnRekam.setOnClickListener {
                tvAyat.visibility = View.GONE
                cvHasilHafalan.visibility = View.GONE
                showDialogRekamSuara(
                    onClose = { binding.tvAyat.isVisible = true },
                    onStartRecording = { startRecording(
                        data.namaSurat,
                        data.nomorAyat,
                        data.lafadzAyat) },
                    onStopRecording = { stopRecording() },
                    onStopButtonClicked = {  }
                )
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startRecording(namaSurat: String, nomorAyat: String, lafadzAyat: String) {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        val speechRecognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar-SA") // Bahasa Arab (Saudi Arabia)

        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}

            override fun onBeginningOfSpeech() {}

            override fun onRmsChanged(rmsdB: Float) {}

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {}

            override fun onError(error: Int) {
                Log.e("SpeechRecognition", "Error: $error")
                Toast.makeText(this@HafalanActivity, "Terjadi Kesalahan, Silakan Coba Kembali", Toast.LENGTH_SHORT).show()
                binding.tvAyat.visibility = View.VISIBLE
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (matches != null && matches.isNotEmpty()) {
                    val recognizedText = matches[0]
                    speechToTextResult(namaSurat, nomorAyat, lafadzAyat, recognizedText)
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {}

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        speechRecognizer.startListening(speechRecognizerIntent)
    }

    private fun stopRecording() {
        speechRecognizer.stopListening()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::speechRecognizer.isInitialized) {
            speechRecognizer.destroy()
        }
    }

    @SuppressLint("SetTextI18n")
    private fun speechToTextResult(namaSurat: String, nomorAyat: String, lafadzAyat: String, textResult: String) {
        with(binding) {
            cvHasilHafalan.visibility = View.VISIBLE
            tvAyat.isVisible = true
            tvAyatKesalahan.text = textResult

            val jaroWinklerSimilarity = JaroWinklerSimilarity()
            val similarity = jaroWinklerSimilarity.apply(lafadzAyat, textResult)
            println("Jaro-Winkler Similarity: $similarity")

            when(similarity) {
                in 0.71..1.0 ->
                {
                    tvPersentaseKemiripan.text = "${(similarity * 100).toInt()}"
                    tvPredikatHasil.text = "Sangat Baik"
                    tvPredikatHasil.setTextColor(ContextCompat.getColor(this@HafalanActivity, R.color.green_9ACD32))
                    tvAyatSelanjutnya.isVisible = true
                    tvLetakKesalahan.isVisible = false
                    tvAyatKesalahan.isVisible = false

                    when (namaSurat) {
                        "Al-Fatihah" -> checkAndInsertAlFatihah(namaSurat, nomorAyat.toInt())
                        "An-Nas" -> checkAndInsertAnNas(namaSurat, nomorAyat.toInt())
                        "Al-Falaq" -> checkAndInsertAlFalaq(namaSurat, nomorAyat.toInt())
                        "Al-Ikhlas" -> checkAndInsertAlIkhlas(namaSurat, nomorAyat.toInt())
                        "At-Takwir" -> checkAndInsertAtTakwir(namaSurat, nomorAyat.toInt())
                        "An-Naba'" -> checkAndInsertAnNaba(namaSurat, nomorAyat.toInt())
                        "Al-Mulk" -> checkAndInsertAlMulk(namaSurat, nomorAyat.toInt())
                        "Al-Kahf" -> checkAndInsertAlKahfi(namaSurat, nomorAyat.toInt())
                    }

                    val ayatDihafal = nomorSurat?.let { AyatDihafal(1, it, namaSurat, nomorAyat, ambilWaktuHafalan()) }
                    if (ayatDihafal != null) {
                        viewModel.insertAyatDihafal(ayatDihafal)
                    }
                }
                in 0.41..0.7 -> {
                    tvPersentaseKemiripan.text = "${(similarity * 100).toInt()}"
                    tvPredikatHasil.text = "Cukup Baik"
                    tvPredikatHasil.setTextColor(ContextCompat.getColor(this@HafalanActivity, R.color.orange_FF6900))
                    tvLetakKesalahan.visibility = View.VISIBLE
                    tvAyatKesalahan.visibility = View.VISIBLE
                }
                in 0.1..0.4 -> {
                    tvPersentaseKemiripan.text = "${(similarity * 100).toInt()}"
                    tvPredikatHasil.text = "Kurang Baik"
                    tvPredikatHasil.setTextColor(ContextCompat.getColor(this@HafalanActivity, R.color.red_F44336))
                    tvLetakKesalahan.visibility = View.VISIBLE
                    tvAyatKesalahan.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun checkAndInsertAlFatihah(namaSurat: String, nomorAyat: Int) {
        val alFatihah = AlFatihah(nomorAyat, namaSurat, nomorAyat, "dihafal")
        viewModel.insertAlFatihah(alFatihah)
    }

    private fun checkAndInsertAnNas(namaSurat: String, nomorAyat: Int) {
        val anNas = AnNas(nomorAyat, namaSurat, nomorAyat, "dihafal")
        viewModel.insertAnNas(anNas)
    }

    private fun checkAndInsertAlFalaq(namaSurat: String, nomorAyat: Int) {
        val alFalaq = AlFalaq(nomorAyat, namaSurat, nomorAyat, "dihafal")
        viewModel.insertAlFalaq(alFalaq)
    }

    private fun checkAndInsertAlIkhlas(namaSurat: String, nomorAyat: Int) {
        val alIkhlas = AlIkhlas(nomorAyat, namaSurat, nomorAyat, "dihafal")
        viewModel.insertAlIkhlas(alIkhlas)
    }

    private fun checkAndInsertAtTakwir(namaSurat: String, nomorAyat: Int) {
        val atTakwir = AtTakwir(nomorAyat, namaSurat, nomorAyat, "dihafal")
        viewModel.insertAtTakwir(atTakwir)
    }

    private fun checkAndInsertAnNaba(namaSurat: String, nomorAyat: Int) {
        val anNaba = AnNaba(nomorAyat, namaSurat, nomorAyat, "dihafal")
        viewModel.insertAnNaba(anNaba)
    }

    private fun checkAndInsertAlMulk(namaSurat: String, nomorAyat: Int) {
        val alMulk = AlMulk(nomorAyat, namaSurat, nomorAyat, "dihafal")
        viewModel.insertAlMulk(alMulk)
    }

    private fun checkAndInsertAlKahfi(namaSurat: String, nomorAyat: Int) {
        val alKahfi = AlKahfi(nomorAyat, namaSurat, nomorAyat, "dihafal")
        viewModel.insertAlKahfi(alKahfi)
    }

    @SuppressLint("SimpleDateFormat")
    private fun ambilWaktuHafalan(): String {
        val calendar = Calendar.getInstance()

        val waktuFormat = SimpleDateFormat("HH:mm:ss")
        val waktuString = waktuFormat.format(calendar.time)

        val hari = calendar.get(Calendar.DAY_OF_WEEK)

        val hariString = when (hari) {
            Calendar.SUNDAY -> "Sunday"
            Calendar.MONDAY -> "Monday"
            Calendar.TUESDAY -> "Tuesday"
            Calendar.WEDNESDAY -> "Wednesday"
            Calendar.THURSDAY -> "Thursday"
            Calendar.FRIDAY -> "Friday"
            Calendar.SATURDAY -> "Saturday"
            else -> "Unknown"
        }

        val tanggalFormat = SimpleDateFormat("dd/MM/yyyy")
        val tanggalString = tanggalFormat.format(calendar.time)

        return "$waktuString, $hariString, $tanggalString"
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun initListener() {
        with(binding) {
            icBack.setOnClickListener {
                onBackPressedDispatcher.onBackPressed()
            }
            onBackPressedDispatcher.addCallback {
                finish()
            }

            icBookmark.setOnClickListener {
                BookmarkActivity.start(this@HafalanActivity)
            }
            tvAyatSelanjutnya.setOnClickListener {
                nomorSurat?.let { it1 ->
                    start(this@HafalanActivity,
                        it1, (nomorAyat?.toInt()?.plus(1)).toString())
                }
                finish()
            }
        }
    }

    companion object {
        fun start(context: Context, nomorSurat: String, nomorAyat: String) {
            val starter = Intent(context, HafalanActivity::class.java)
                .putExtra(Const.NOMOR_SURAT, nomorSurat)
                .putExtra(Const.NOMOR_AYAT, nomorAyat)
            context.startActivity(starter)
        }
    }
}