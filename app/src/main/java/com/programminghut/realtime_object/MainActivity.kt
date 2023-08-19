package com.programminghut.realtime_object

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.*
import android.hardware.camera2.*
import android.hardware.camera2.CameraCaptureSession.CaptureCallback
import android.hardware.camera2.CameraCaptureSession.StateCallback
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.view.MotionEvent
import android.view.Surface
import android.view.TextureView
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.programminghut.realtime_object.ml.SsdMobilenetV11Metadata1
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import android.speech.tts.TextToSpeech
import android.view.View
import java.util.*

class MainActivity : AppCompatActivity() {
    // กำหนดตัวแปรเพื่อเก็บสถานะการถ่ายภาพ
    private var isCapturing = false
    // กำหนดตัวแปรเพื่อใช้ในการส่งข้อความด้วย TextToSpeech
    private lateinit var textToSpeech: TextToSpeech
    // กำหนดตัวแปรเพื่อเก็บ TextView ที่ใช้แสดงผลลัพธ์ของการตรวจจับวัตถุ
    lateinit var resultTextView: TextView
    // กำหนดตัวแปรเพื่อเก็บรายการของชื่อวัตถุที่ตรวจจับได้
    lateinit var labels: List<String>
    // กำหนดรายการสีที่ใช้ในการวาดสี่เหลี่ยมรอบวัตถุ
    var colors = listOf<Int>(
        Color.BLUE, Color.GREEN, Color.RED, Color.CYAN, Color.GRAY, Color.BLACK,
        Color.DKGRAY, Color.MAGENTA, Color.YELLOW, Color.RED
    )
    // กำหนดตัวแปรสำหรับวาดรูปภาพ
    val paint = Paint()
    // กำหนดตัวแปร ImageProcessor เพื่อประมวลผลภาพด้วย TensorFlow Lite
    lateinit var imageProcessor: ImageProcessor
    // กำหนดตัวแปรเพื่อเก็บภาพที่จับได้จากกล้อง
    lateinit var bitmap: Bitmap
    // กำหนดตัวแปร ImageView ที่ใช้แสดงภาพตรวจจับวัตถุ
    lateinit var imageView: ImageView
    // กำหนดตัวแปร CameraDevice สำหรับควบคุมกล้อง
    lateinit var cameraDevice: CameraDevice
    // กำหนดตัวแปร Handler เพื่อใช้ในการทำงานในหลาย Thread
    lateinit var handler: Handler
    // กำหนดตัวแปร CameraManager เพื่อเข้าถึงคุณสมบัติของกล้อง
    lateinit var cameraManager: CameraManager
    // กำหนดตัวแปร TextureView ที่ใช้สำหรับแสดงภาพจากกล้อง
    lateinit var textureView: TextureView
    // กำหนดตัวแปรสำหรับโมเดลตรวจจับวัตถุ
    lateinit var model: SsdMobilenetV11Metadata1
    // กำหนดตัวแปรเพื่อกำหนดให้สามารถถ่ายภาพได้หลายครั้งต่อเนื่อง
    private var isCaptureEnabled = true

    // เมื่อ Activity ถูกสร้างขึ้น
    @SuppressLint("ClickableViewAccessibility", "MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        // ตรวจสอบและขออนุญาตใช้งานกล้อง
        getPermission()

        // โหลดรายการชื่อวัตถุที่ตรวจจับได้จากไฟล์ labels.txt
        labels = FileUtil.loadLabels(this, "labels.txt")
        // กำหนด ImageProcessor เพื่อประมวลผลภาพด้วย TensorFlow Lite
        imageProcessor = ImageProcessor.Builder().add(ResizeOp(300, 300, ResizeOp.ResizeMethod.BILINEAR)).build()
        // สร้างโมเดลตรวจจับวัตถุ
        model = SsdMobilenetV11Metadata1.newInstance(this)
        // สร้าง HandlerThread และ Handler สำหรับการทำงานใน Thread ที่แยกไว้เพื่อประมวลผลภาพจากกล้อง
        val handlerThread = HandlerThread("videoThread")
        handlerThread.start()
        handler = Handler(handlerThread.looper)

        // กำหนดตัวแปรเพื่อเก็บ ImageView และ TextView ที่ใช้แสดงผลลัพธ์ของการตรวจจับวัตถุ
        imageView = findViewById(R.id.imageView)
        resultTextView = findViewById(R.id.bottomTextView) // Update resultTextView to the bottomTextView ID

        // กำหนดตัวแปรเพื่อเก็บ TextureView ที่ใช้สำหรับแสดงภาพจากกล้อง
        textureView = findViewById(R.id.textureView)
        // กำหนด Listener สำหรับ TextureView เพื่อเมื่อ SurfaceTexture เตรียมใช้งานได้
        textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
                // เมื่อ SurfaceTexture เตรียมใช้งานได้ ให้เปิดกล้อง
                openCamera()
            }

            override fun onSurfaceTextureSizeChanged(surfaceTexture: SurfaceTexture, width: Int, height: Int) {}

            override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture): Boolean {
                return false
            }

            override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) {
                // เมื่อภาพใน TextureView อัปเดต ให้นำภาพไปประมวลผลตรวจจับวัตถุและแสดงผลลัพธ์
                bitmap = textureView.bitmap!!
                var image = TensorImage.fromBitmap(bitmap)
                image = imageProcessor.process(image)

                val outputs = model.process(image)
                val locations = outputs.locationsAsTensorBuffer.floatArray
                val classes = outputs.classesAsTensorBuffer.floatArray
                val scores = outputs.scoresAsTensorBuffer.floatArray

                val mutable = bitmap.copy(Bitmap.Config.ARGB_8888, true)
                val canvas = Canvas(mutable)

                val h = mutable.height
                val w = mutable.width
                paint.textSize = h / 15f
                paint.strokeWidth = h / 85f
                var x = 0
                scores.forEachIndexed { index, fl ->
                    x = index
                    x *= 4
                    if (fl > 0.5) {
                        paint.color = colors[index]
                        paint.style = Paint.Style.STROKE
                        canvas.drawRect(
                            RectF(
                                locations[x + 1] * w,
                                locations[x] * h,
                                locations[x + 3] * w,
                                locations[x + 2] * h
                            ), paint
                        )
                        paint.style = Paint.Style.FILL
                        canvas.drawText(
                            "${labels[classes[x].toInt()]} ${fl.toString()}",
                            locations[x + 1] * w,
                            locations[x] * h,
                            paint
                        )
                    }
                }

                imageView.setImageBitmap(mutable)

                val resultMap = mutableMapOf<String, Int>()

                scores.forEachIndexed { index, fl ->
                    x = index
                    x *= 4
                    if (fl > 0.5) {
                        // คำนวณชื่อวัตถุและคะแนนของวัตถุที่ตรวจจับได้
                        val detectedLabel = labels[classes[x].toInt()]
                        val score = fl.toString()

                        // เก็บจำนวนของวัตถุแต่ละชื่อใน resultMap
                        if (resultMap.containsKey(detectedLabel)) {
                            resultMap[detectedLabel] = resultMap[detectedLabel]!! + 1
                        } else {
                            resultMap[detectedLabel] = 1
                        }
                    }
                }

                // อัปเดต TextView ที่ใช้แสดงผลลัพธ์ของการตรวจจับวัตถุ
                showDetectionResults(resultMap)
            }
        }

        // กำหนด CameraManager สำหรับเข้าถึงคุณสมบัติของกล้อง
        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager

        // เพิ่ม Listener สำหรับการสัมผัส (Touch Event) บน TextureView
        textureView.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                // เมื่อมีการกดสัมผัสในส่วนของ TextureView ให้เปิดหรือปิดฟังก์ชันถ่ายภาพ
                toggleCapture()
            }
            true
        }
    }

    // เมื่อกดปุ่มที่ใช้สำหรับเปิดหรือปิดฟังก์ชันถ่ายภาพ
    private fun toggleCapture() {
        isCapturing = !isCapturing
        if (isCapturing) {
            // เมื่อเปิดฟังก์ชันถ่ายภาพ ให้เริ่มต้นถ่ายภาพ
            startImageCapture()
        } else {
            // เมื่อปิดฟังก์ชันถ่ายภาพ ให้หยุดถ่ายภาพ
            stopImageCapture()
        }
    }

    // เมื่อหยุดถ่ายภาพ
    private fun stopImageCapture() {
        // ปิดกล้องเพื่อหยุดถ่ายภาพ
        if (this::cameraDevice.isInitialized) {
            cameraDevice.close()
        }
        // คืนทรัพยากรที่ใช้งานของภาพ
        bitmap.recycle()
        textureView.surfaceTextureListener = null

        // ตั้งค่า UI กลับไปที่เริ่มต้น
        resetUI()
    }

    // ตั้งค่า UI กลับไปที่เริ่มต้น
    private fun resetUI() {
        // ... (Existing code)

        // ตั้งค่าค่า isCapturing ให้เป็น false เพื่อรองรับการถ่ายภาพใหม่
        isCapturing = false
    }

    // เมื่อเริ่มต้นถ่ายภาพ
    private fun startImageCapture() {
        // เปิดกล้องอีกครั้งและกำหนดค่าใหม่ให้กับทรัพยากรที่เคยถูกคืบควบคุมใน stopImageCapture()
        openCamera()
    }

    // แสดงผลลัพธ์ของการตรวจจับวัตถุใน TextView
    private fun showDetectionResults(resultMap: Map<String, Int>) {
        // สร้างข้อความที่ประกอบด้วยจำนวนวัตถุที่ตรวจจับได้ในแต่ละชื่อวัตถุ
        var resultText = ""
        resultMap.forEach { (label, count) ->
            resultText += "$label: $count\n"
        }

        // แสดงผลลัพธ์ใน TextView
        resultTextView.text = resultText

        // ถ้ากำลังทำงานในโหมดถ่ายภาพ (isCapturing == true) ให้เริ่มการอ่านข้อความโดยใช้ TextToSpeech
        if (isCapturing) {
            // สร้าง TextToSpeech และกำหนด Listener ให้กับมัน
            textToSpeech = TextToSpeech(this) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    val result = textToSpeech.setLanguage(Locale.US)

                    // หากภาษาไม่รองรับหรือไม่มีข้อมูลภาษา ให้อ่านข้อความ "Language not supported, Try again"
                    if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                        textToSpeech.speak("Language not supported, Try again", TextToSpeech.QUEUE_FLUSH, null, null)
                    }
                } else {
                    // หากการสร้าง TextToSpeech ไม่สำเร็จ ให้อ่านข้อความ "Try again"
                    textToSpeech.speak("Try again", TextToSpeech.QUEUE_FLUSH, null, null)
                }
                // เริ่มอ่านข้อความที่มีใน resultText
                speakOut(resultText)
            }
        }
    }

    // อ่านข้อความด้วย TextToSpeech
    private fun speakOut(text: String) {
        textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    // หยุดใช้งานโมเดลตรวจจับวัตถุและ TextToSpeech เมื่อปิดแอป
    override fun onDestroy() {
        super.onDestroy()
        model.close()
        textToSpeech.stop()
        textToSpeech.shutdown()
    }

    // เปิดกล้องเพื่อให้สามารถถ่ายภาพได้
    @SuppressLint("MissingPermission")
    fun openCamera() {
        // เปิดกล้องด้วย CameraManager และกำหนด StateCallback สำหรับตรวจสอบสถานะ
        cameraManager.openCamera(cameraManager.cameraIdList[0], object : CameraDevice.StateCallback() {
            // เมื่อกล้องเปิดสำเร็จ
            override fun onOpened(camera: CameraDevice) {
                // เก็บ CameraDevice เพื่อใช้ในการควบคุมกล้อง
                cameraDevice = camera

                // สร้าง SurfaceTexture จาก TextureView และกำหนด Surface ของนั้นให้กับ CameraDevice
                val surfaceTexture = textureView.surfaceTexture
                val surface = Surface(surfaceTexture)

                // สร้าง CaptureRequest สำหรับการแสดงภาพในกล้อง (TEMPLATE_PREVIEW) และเพิ่ม Surface เข้าไปใน CaptureRequest
                val captureRequest = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                captureRequest.addTarget(surface)

                // สร้าง CameraCaptureSession สำหรับควบคุมการเชื่อมต่อกับกล้อง
                cameraDevice.createCaptureSession(listOf(surface), object : StateCallback() {
                    // เมื่อ CameraCaptureSession ถูกกำหนดค่าสำเร็จ
                    override fun onConfigured(session: CameraCaptureSession) {
                        // กำหนดให้กล้องแสดงภาพตาม CaptureRequest ที่กำหนดไว้
                        session.setRepeatingRequest(captureRequest.build(), null, null)
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {}
                }, handler)
            }

            override fun onDisconnected(camera: CameraDevice) {}

            override fun onError(camera: CameraDevice, error: Int) {}
        }, handler)
    }

    // ถ่ายภาพ
    private fun captureImage() {
        // หยุดถ่ายภาพกรณีก่อนหน้านี้ทำงานไม่สำเร็จ
        if (!isCaptureEnabled) {
            return // Prevent capturing while processing previous image
        }

        // สร้าง CameraCaptureRequest สำหรับการถ่ายภาพ (TEMPLATE_STILL_CAPTURE)
        val captureRequestBuilder =
            cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)

        // สร้าง SurfaceTexture จาก TextureView และกำหนด Surface ของนั้นให้กับ CameraCaptureRequest
        val surfaceTexture = textureView.surfaceTexture
        val surface = Surface(surfaceTexture)
        captureRequestBuilder.addTarget(surface)

        // กำหนดการหมุนภาพให้เป็นตามรูปที่เคยคำนวณไว้
        val rotation = windowManager.defaultDisplay.rotation
        captureRequestBuilder.set(CaptureRequest.JPEG_ORIENTATION, getOrientation(rotation))

        // สร้าง CaptureCallback สำหรับตรวจสอบสถานะการถ่ายภาพ
        val captureCallback = object : CaptureCallback() {
            override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
                // Image captured successfully
                isCaptureEnabled = true // Enable capturing again
            }

            override fun onCaptureFailed(session: CameraCaptureSession, request: CaptureRequest, failure: CaptureFailure) {
                // Image capture failed
                isCaptureEnabled = true
            }
        }

        // สร้าง HandlerThread และ Handler สำหรับการถ่ายภาพ
        val handlerThread = HandlerThread("ImageCapture")
        handlerThread.start()
        val captureHandler = Handler(handlerThread.looper)

        // สร้าง CameraCaptureSession สำหรับควบคุมการถ่ายภาพ
        cameraDevice.createCaptureSession(listOf(surface), object : CameraCaptureSession.StateCallback() {
            override fun onConfigured(session: CameraCaptureSession) {
                try {
                    // เริ่มถ่ายภาพ
                    session.capture(captureRequestBuilder.build(), captureCallback, captureHandler)
                } catch (e: CameraAccessException) {
                    e.printStackTrace()
                }
            }

            override fun onConfigureFailed(session: CameraCaptureSession) {
                // ถ้าการกำหนดค่า CameraCaptureSession ไม่สำเร็จ ให้เปิดใช้งานการถ่ายภาพอีกครั้ง
                isCaptureEnabled = true // Enable capturing again
            }
        }, captureHandler)
    }

    // คำนวณค่าการหมุนภาพของกล้องในแต่ละทิศทาง
    private fun getOrientation(rotation: Int): Int {
        return when (rotation) {
            Surface.ROTATION_0 -> 90
            Surface.ROTATION_90 -> 0
            Surface.ROTATION_180 -> 270
            Surface.ROTATION_270 -> 180
            else -> 90
        }
    }

    // ขออนุญาตใช้งานกล้อง
    private val requestCode = 101

    fun getPermission() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(android.Manifest.permission.CAMERA), 101)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults[0] != PackageManager.PERMISSION_GRANTED) {
            getPermission()
        }
    }
}
