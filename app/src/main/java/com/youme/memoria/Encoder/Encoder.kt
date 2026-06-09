package com.youme.memoria.Encoder

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.pow
import kotlin.math.sqrt
import androidx.core.graphics.scale

class MemoriaEncoder(private val context: Context) {
    private var gpuDelegate: org.tensorflow.lite.gpu.GpuDelegate? = null
    private lateinit var imageInterpreter: Interpreter
    private lateinit var textInterpreter: Interpreter
    private lateinit var embeddingTable: Array<FloatArray>

    companion object {
        const val IMAGE_SIZE = 256
        const val EMBED_DIM = 512
        const val CONTEXT_LENGTH = 77
        const val VOCAB_SIZE = 49408

    }
    fun initializeImageEncoder(){
        val options = Interpreter.Options().apply {
            numThreads = 4
            gpuDelegate = org.tensorflow.lite.gpu.GpuDelegate()
            addDelegate(gpuDelegate)
        }

        imageInterpreter = Interpreter(loadModel("mobileclip_s0_image_float32.tflite"), options)
    }
    fun initializeTextEncoder() {
        val options = Interpreter.Options().apply {
            numThreads = 4
            gpuDelegate = org.tensorflow.lite.gpu.GpuDelegate()
            addDelegate(gpuDelegate)
        }
        textInterpreter  = Interpreter(loadModel("mobileclip_s0_text_transformer_float32.tflite"), options)

        loadEmbeddingTable()
        CLIPTokenizer.init(context)
    }


    fun encodeImage(bitmap: Bitmap): FloatArray {
        val scaled = bitmap.scale(IMAGE_SIZE, IMAGE_SIZE)

        val input = ByteBuffer.allocateDirect(1 * IMAGE_SIZE * IMAGE_SIZE * 3 * 4)
            .order(ByteOrder.nativeOrder())

        val pixels = IntArray(IMAGE_SIZE * IMAGE_SIZE)
        scaled.getPixels(pixels, 0, IMAGE_SIZE, 0, 0, IMAGE_SIZE, IMAGE_SIZE)

        val mean = floatArrayOf(0.485f, 0.456f, 0.406f)
        val std  = floatArrayOf(0.229f, 0.224f, 0.225f)

        for (pixel in pixels) {
            val r = ((pixel shr 16 and 0xFF) / 255f - mean[0]) / std[0]
            val g = ((pixel shr  8 and 0xFF) / 255f - mean[1]) / std[1]
            val b = ((pixel       and 0xFF) / 255f - mean[2]) / std[2]
            input.putFloat(r)
            input.putFloat(g)
            input.putFloat(b)
        }

        val output = Array(1) { FloatArray(EMBED_DIM) }
        imageInterpreter.run(input, output)

        return l2Normalize(output[0])
    }


    fun encodeText(query: String): FloatArray {
        val tokens = CLIPTokenizer.tokenize(query)
        val eosPos = CLIPTokenizer.eosPosition(tokens)


        val tokenEmbeds = Array(1) { Array(CONTEXT_LENGTH) { FloatArray(EMBED_DIM) } }
        for (i in 0 until CONTEXT_LENGTH) {
            val tokenId = tokens[i].toInt().coerceIn(0, VOCAB_SIZE - 1)
            embeddingTable[tokenId].copyInto(tokenEmbeds[0][i])
        }

        val transposed = ByteBuffer.allocateDirect(1 * EMBED_DIM * CONTEXT_LENGTH * 4)
            .order(ByteOrder.nativeOrder())
        for (d in 0 until EMBED_DIM) {
            for (t in 0 until CONTEXT_LENGTH) {
                transposed.putFloat(tokenEmbeds[0][t][d])
            }
        }

        val eosInput  = longArrayOf(eosPos)
        val textOutput = Array(1) { FloatArray(EMBED_DIM) }

        val inputs  = mapOf(0 to transposed, 1 to eosInput)
        val outputs = mapOf(0 to textOutput)
        textInterpreter.runForMultipleInputsOutputs(
            inputs.values.toTypedArray(),
            outputs
        )

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
    private fun loadEmbeddingTable() {
        val stream = context.assets.open("token_embeddings_f16.npy")
        val bytes = stream.readBytes()
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        buf.position(128)

        embeddingTable = Array(VOCAB_SIZE) { FloatArray(EMBED_DIM) }
        for (i in 0 until VOCAB_SIZE) {
            for (j in 0 until EMBED_DIM) {
                embeddingTable[i][j] = fp16ToFp32(buf.short)
            }
        }
    }

    private fun fp16ToFp32(half: Short): Float {
        val h = half.toInt() and 0xFFFF
        val exp = (h shr 10) and 0x1F
        val man = h and 0x3FF
        return when {
            exp == 0 && man == 0 -> 0f
            exp == 31 -> if (man == 0) Float.POSITIVE_INFINITY else Float.NaN
            else -> {
                val sign = if (h and 0x8000 != 0) -1f else 1f
                val e = if (exp == 0) -14 else exp - 15
                val m = if (exp == 0) man / 1024f else 1f + man / 1024f
                sign * m * 2.0.pow(e.toDouble()).toFloat()
            }
        }
    }
    fun freeImageEncoder() {
        if (::imageInterpreter.isInitialized)  imageInterpreter.close()
    }
    fun freeTextEncoder() {
        if (::textInterpreter.isInitialized) textInterpreter.close()
        embeddingTable = emptyArray()
    }
    fun close() {
        imageInterpreter.close()
        textInterpreter.close()
        gpuDelegate?.close()
    }
}