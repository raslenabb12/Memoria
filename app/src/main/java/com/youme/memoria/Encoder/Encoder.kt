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
import kotlin.math.roundToInt

class MemoriaEncoder(private val context: Context) {
    private var imageNnDelegate: NnApiDelegate? = null
    private var textNnDelegate: NnApiDelegate? = null

    private lateinit var imageInterpreter: Interpreter
    private lateinit var textInterpreter: Interpreter
    private lateinit var embeddingTable: Array<FloatArray>

    companion object {
        const val IMAGE_SIZE = 256
        const val EMBED_DIM = 512
        const val CONTEXT_LENGTH = 77
        const val VOCAB_SIZE = 49408
    }

    fun initializeImageEncoder() {
        val options = Interpreter.Options().apply {
            numThreads = 4
            useXNNPACK = true
        }
        imageInterpreter = Interpreter(loadModel("mobileclip_s0_image_v2.tflite"), options)
        Log.d("MemoriaEncoder", "Image Shape: ${imageInterpreter.getInputTensor(0).shape().joinToString()}")
    }

    fun initializeTextEncoder() {
        val options = Interpreter.Options().apply {
            numThreads = 4
        }

        textInterpreter = Interpreter(loadModel("mobileclip_text_reimpl_v2.tflite"), options)

        loadEmbeddingTable()
        CLIPTokenizer.init(context)
    }

    fun encodeImage(bitmap: Bitmap): FloatArray {
        val scaled = centerCropAndScale(bitmap, IMAGE_SIZE)

        val pixels = IntArray(IMAGE_SIZE * IMAGE_SIZE)
        scaled.getPixels(pixels, 0, IMAGE_SIZE, 0, 0, IMAGE_SIZE, IMAGE_SIZE)

        val mean = floatArrayOf(0.48145467f, 0.4578275f, 0.40821072f)
        val std  = floatArrayOf(0.26862955f, 0.2613026f, 0.2757771f)

        val input = ByteBuffer.allocateDirect(1 * 3 * IMAGE_SIZE * IMAGE_SIZE * 4)
            .order(ByteOrder.nativeOrder())


        for (pixel in pixels) {
            input.putFloat(((pixel shr 16 and 0xFF) / 255f - mean[0]) / std[0])
        }

        for (pixel in pixels) {
            input.putFloat(((pixel shr 8 and 0xFF) / 255f - mean[1]) / std[1])
        }

        for (pixel in pixels) {
            input.putFloat(((pixel and 0xFF) / 255f - mean[2]) / std[2])
        }

        val output = Array(1) { FloatArray(EMBED_DIM) }
        imageInterpreter.run(input, output)

        val result = l2Normalize(output[0])

        return result
    }

    fun encodeText(query: String): FloatArray {
        val tokens = CLIPTokenizer.tokenize(query)


        var eosPos = tokens.indexOf(49407)

        Log.d("CLIP_DEBUG", "Token IDs: ${tokens.take(10).joinToString()}")
        Log.d("CLIP_DEBUG", "EOS pos: $eosPos")
        Log.d("CLIP_DEBUG", "Token 0 embed first 5: ${embeddingTable[tokens[0].toInt()].take(5).joinToString()}")

        if (eosPos == -1) eosPos = tokens.indexOf(0)
        if (eosPos == -1) eosPos = 76

        val textInput = ByteBuffer.allocateDirect(1 * CONTEXT_LENGTH * EMBED_DIM * 4)
            .order(ByteOrder.nativeOrder())

        for (i in 0 until CONTEXT_LENGTH) {
            val tokenId = tokens[i].toInt().coerceIn(0, VOCAB_SIZE - 1)
            val embed = embeddingTable[tokenId]
            for (value in embed) {
                textInput.putFloat(value)
            }
        }
        textInput.rewind()


        val eosInput = intArrayOf(eosPos)


        val textOutput = Array(1) { FloatArray(EMBED_DIM) }


        val embedIdx = textInterpreter.getInputIndex("serving_default_token_embeds:0")
        val eosIdx = textInterpreter.getInputIndex("serving_default_eos_positions:0")

        val inputs = arrayOfNulls<Any>(2)
        inputs[embedIdx] = textInput
        inputs[eosIdx] = eosInput

        val outputs = mutableMapOf<Int, Any>()
        outputs[0] = textOutput

        try {
            textInterpreter.runForMultipleInputsOutputs(inputs, outputs)
        } catch (e: Exception) {
            // next
        }

        return l2Normalize(textOutput[0])
    }

    fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        var dotProduct = 0f
        for (i in a.indices) {
            dotProduct += a[i] * b[i]
        }
        return dotProduct
    }

    private fun l2Normalize(v: FloatArray): FloatArray {
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

    private fun loadEmbeddingTable() {
        val stream = context.assets.open("token_embeddings_f32.bin")
        val bytes = stream.readBytes()

        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

        embeddingTable = Array(VOCAB_SIZE) { FloatArray(EMBED_DIM) }

        for (i in 0 until VOCAB_SIZE) {
            for (j in 0 until EMBED_DIM) {
                embeddingTable[i][j] = buf.float
            }
        }

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