package com.ultron.app

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.os.Build
import android.view.Display
import android.os.Handler
import android.os.Looper
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.Executor

/** Screenshot capture requires an enabled accessibility service and explicit user action. */
object ScreenUnderstanding {
    private val main = Handler(Looper.getMainLooper())
    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    fun captureAndRead(service: AccessibilityService, callback: (Result<String>) -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            callback(Result.failure(UnsupportedOperationException("Visual screen capture requires Android 11 or newer.")))
            return
        }
        service.takeScreenshot(Display.DEFAULT_DISPLAY, main.asExecutor(),
            object : AccessibilityService.TakeScreenshotCallback {
                override fun onSuccess(screenshot: AccessibilityService.ScreenshotResult) {
                    val hardware = screenshot.hardwareBuffer
                    // The screenshot is a HARDWARE bitmap, which ML Kit cannot read pixels from.
                    // Copy it into a normal software bitmap first.
                    val software: Bitmap? = try {
                        Bitmap.wrapHardwareBuffer(hardware, screenshot.colorSpace)
                            ?.let { hw -> hw.copy(Bitmap.Config.ARGB_8888, false).also { hw.recycle() } }
                    } catch (t: Throwable) {
                        null
                    } finally {
                        hardware.close()
                    }
                    if (software == null) {
                        callback(Result.failure(IllegalStateException("Android returned an empty screenshot.")))
                        return
                    }
                    try {
                        recognizer.process(InputImage.fromBitmap(software, 0))
                            .addOnSuccessListener { text ->
                                software.recycle()
                                callback(Result.success(text.text))
                            }
                            .addOnFailureListener { error ->
                                software.recycle()
                                callback(Result.failure(error))
                            }
                    } catch (t: Throwable) {
                        software.recycle()
                        callback(Result.failure(t))
                    }
                }
                override fun onFailure(errorCode: Int) {
                    callback(Result.failure(IllegalStateException("Screenshot capture failed (code $errorCode). Check accessibility permissions and try again.")))
                }
            })
    }

    private fun Handler.asExecutor(): Executor = Executor { command -> post(command) }
}
