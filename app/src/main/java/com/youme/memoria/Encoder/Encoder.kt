package com.youme.memoria.Encoder

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.nnapi.NnApiDelegate
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.pow
import kotlin.math.sqrt
import androidx.core.graphics.scale
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.roundToInt

class MemoriaEncoder(private val context: Context) {
    private var imageNnDelegate: NnApiDelegate? = null
    private var textNnDelegate: NnApiDelegate? = null

    private lateinit var embeddingBuffer: MappedByteBuffer


    private lateinit var imageInterpreter: Interpreter
    private lateinit var textInterpreter: Interpreter
    private lateinit var embeddingTable: Array<FloatArray>
    private val modelMutex = Mutex()

    companion object {
        const val IMAGE_SIZE = 256
        const val EMBED_DIM = 512
        const val CONTEXT_LENGTH = 77
    }

    fun initializeImageEncoder(){
        val options = Interpreter.Options().apply {
            numThreads = 4
            useXNNPACK = true
            try {
                imageNnDelegate = NnApiDelegate()
                addDelegate(imageNnDelegate)
            } catch (e: Exception) {
                "test"
            }
        }
        imageInterpreter = Interpreter(loadModel("mobileclip_s0_image_v2.tflite"), options)

    }

    fun initializeTextEncoder() {
        val options = Interpreter.Options().apply {
            numThreads = 4
            useXNNPACK=true
        }

        textInterpreter = Interpreter(loadModel("mobileclip_s0_text_int8.tflite"), options)

        CLIPTokenizer.init(context)
    }

    private val tokenBuffer: ByteBuffer by lazy {
        ByteBuffer.allocateDirect(CONTEXT_LENGTH * 4).order(ByteOrder.nativeOrder())
    }
    suspend fun encodeText(query: String): FloatArray = modelMutex.withLock {
        val tokens = CLIPTokenizer.tokenize(query)
        tokenBuffer.clear()
        for (i in 0 until CONTEXT_LENGTH) tokenBuffer.putInt(tokens[i].toInt())
        tokenBuffer.rewind()

        val out = Array(1) { FloatArray(EMBED_DIM) }
        textInterpreter.run(tokenBuffer, out)
        l2Normalize(out[0])
    }
    private val imageInputBuffer: ByteBuffer by lazy {
        ByteBuffer.allocateDirect(1 * 3 * IMAGE_SIZE * IMAGE_SIZE * 4).order(ByteOrder.nativeOrder())
    }

    suspend fun encodeImage(bitmap: Bitmap): FloatArray {
        val scaled = centerCropAndScale(bitmap, IMAGE_SIZE)
        try {
            val pixels = IntArray(IMAGE_SIZE * IMAGE_SIZE)
            scaled.getPixels(pixels, 0, IMAGE_SIZE, 0, 0, IMAGE_SIZE, IMAGE_SIZE)

            imageInputBuffer.clear()

            for (pixel in pixels) {
                imageInputBuffer.putFloat((pixel shr 16 and 0xFF) / 255f)
            }
            for (pixel in pixels) {
                imageInputBuffer.putFloat((pixel shr 8 and 0xFF) / 255f)
            }
            for (pixel in pixels) {
                imageInputBuffer.putFloat((pixel and 0xFF) / 255f)
            }

            imageInputBuffer.rewind()

            val output = Array(1) { FloatArray(EMBED_DIM) }

            imageInterpreter.run(imageInputBuffer, output)

            val result = l2Normalize(output[0])


            return result
        }
        finally {
            scaled.recycle()
        }


    }
    private val textInputBuffer: ByteBuffer by lazy {
        ByteBuffer.allocateDirect(1 * CONTEXT_LENGTH * EMBED_DIM * 4).order(ByteOrder.nativeOrder())
    }

    fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        var dotProduct = 0f
        for (i in a.indices) {
            dotProduct += a[i] * b[i]
        }
        return dotProduct
    }

    fun l2Normalize(v: FloatArray): FloatArray {
        var sumSq = 0f
        for (x in v) {
            sumSq += x * x
        }
        val norm = sqrt(sumSq)
        if (norm > 0f) {
            for (i in v.indices) {
                v[i] = v[i] / norm
            }
        }
        return v
    }

    private fun loadModel(fileName: String): MappedByteBuffer {
        return context.assets.openFd(fileName).use { fd ->
            FileInputStream(fd.fileDescriptor).channel.map(
                FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength
            )
        }
    }
    private fun centerCropAndScale(bitmap: Bitmap, targetSize: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height

        val scale = if (width < height) {
            targetSize.toFloat() / width.toFloat()
        } else {
            targetSize.toFloat() / height.toFloat()
        }

        val scaledWidth = (width * scale).roundToInt()
        val scaledHeight = (height * scale).roundToInt()

        val scaledBitmap = bitmap.scale(scaledWidth, scaledHeight)

        val xOffset = (scaledWidth - targetSize) / 2
        val yOffset = (scaledHeight - targetSize) / 2

        val croppedBitmap = Bitmap.createBitmap(scaledBitmap, xOffset, yOffset, targetSize, targetSize)

        if (scaledBitmap != bitmap && scaledBitmap != croppedBitmap) {
            scaledBitmap.recycle()
        }

        return croppedBitmap
    }

    fun freeImageEncoder() {
        if (::imageInterpreter.isInitialized) imageInterpreter.close()
        imageNnDelegate?.close()
        imageNnDelegate = null
    }

    fun freeTextEncoder() {
        if (::textInterpreter.isInitialized) textInterpreter.close()
        textNnDelegate?.close()
        textNnDelegate = null
        embeddingTable = emptyArray()
    }

    fun close() {
        freeImageEncoder()
        freeTextEncoder()
    }
}