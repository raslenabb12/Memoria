package com.youme.memoria.Encoder

import android.content.Context
import org.json.JSONObject


object CLIPTokenizer {
    private const val CONTEXT_LENGTH = 77
    private const val SOT_TOKEN = 49406
    private const val EOT_TOKEN = 49407
    private lateinit var encoder: Map<String, Int>

    fun init(context: Context) {
        val vocabJson = context.assets.open("vocab.json")
            .bufferedReader().readText()
        encoder = JSONObject(vocabJson).let { json ->
            json.keys().asSequence().associateWith { json.getInt(it) }
        }
    }

    fun tokenize(text: String): LongArray {
        val tokens = LongArray(CONTEXT_LENGTH)
        tokens[0] = SOT_TOKEN.toLong()
        var pos = 1
        text.lowercase().split(" ").forEach { word ->
            val id = encoder["$word</w>"] ?: encoder[word] ?: 0
            if (pos < CONTEXT_LENGTH - 1) tokens[pos++] = id.toLong()
        }
        tokens[pos] = EOT_TOKEN.toLong()
        return tokens
    }

    fun eosPosition(tokens: LongArray): Long {
        return tokens.indices.maxByOrNull { tokens[it] }?.toLong() ?: 76L
    }
}