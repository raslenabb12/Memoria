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
    private lateinit var embeddingTable: Array<FloatArray>  // [49408, 512]

    companion object {
        const val IMAGE_SIZE = 256
        const val EMBED_DIM = 512
        const val CONTEXT_LENGTH = 77
        const val VOCAB_SIZE = 49408
    }

    fun initialize() {
        val options = Interpreter.Options().apply {
            numThreads = 4
                 gpuDelegate = org.tensorflow.lite.gpu.GpuDelegate()
                addDelegate(gpuDelegate)
        }

        imageInterpreter = Interpreter(loadModel("mobileclip_s0_image_float32.tflite"), options)
        textInterpreter  = Interpreter(loadModel("mobileclip_s0_text_transformer_float32.tflite"), options)

        loadEmbeddingTable()
        CLIPTokenizer.init(context)
    }

    // ── Image → embedding ─────────────────────────────────────────────────────

    fun encodeImage(bitmap: Bitmap): FloatArray {
        // Resize to 256×256
        val scaled = bitmap.scale(IMAGE_SIZE, IMAGE_SIZE)

        // Fill input buffer [1, 256, 256, 3] NHWC float32
        val input = ByteBuffer.allocateDirect(1 * IMAGE_SIZE * IMAGE_SIZE * 3 * 4)
            .order(ByteOrder.nativeOrder())

        val pixels = IntArray(IMAGE_SIZE * IMAGE_SIZE)
        scaled.getPixels(pixels, 0, IMAGE_SIZE, 0, 0, IMAGE_SIZE, IMAGE_SIZE)

        // CLIP normalization: mean=[0.485,0.456,0.406] std=[0.229,0.224,0.225]
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

    // ── Text → embedding ──────────────────────────────────────────────────────

    fun encodeText(query: String): FloatArray {
        val tokens = CLIPTokenizer.tokenize(query)  // [77] longs
        val eosPos = CLIPTokenizer.eosPosition(tokens)

        // Look up embeddings: tokens → [1, 77, 512]
        val tokenEmbeds = Array(1) { Array(CONTEXT_LENGTH) { FloatArray(EMBED_DIM) } }
        for (i in 0 until CONTEXT_LENGTH) {
            val tokenId = tokens[i].toInt().coerceIn(0, VOCAB_SIZE - 1)
            embeddingTable[tokenId].copyInto(tokenEmbeds[0][i])
        }

        // Transpose to [1, 512, 77] — onnx2tf transposed the input
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

    // ── Cosine similarity ─────────────────────────────────────────────────────

    fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        return a.zip(b.toTypedArray()).sumOf { (x, y) -> (x * y).toDouble() }.toFloat()
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun l2Normalize(v: FloatArray): FloatArray {
        val norm = sqrt(v.map { it * it }.sum())
        return if (norm > 0) v.map { it / norm }.toFloatArray() else v
    }

    private fun loadModel(fileName: String): MappedByteBuffer {
        val fd = context.assets.openFd(fileName)
        return FileInputStream(fd.fileDescriptor).channel.map(
            FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength
        )
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
    fun close() {
        imageInterpreter.close()
        textInterpreter.close()
        gpuDelegate?.close()
    }
}