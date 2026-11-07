package com.gazna.data.remote.dto

import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/** The server returns ISO dates while local records and requests use epoch milliseconds. */
class EpochMillisAdapter : TypeAdapter<Long>() {
    override fun write(out: JsonWriter, value: Long?) { if (value == null) out.nullValue() else out.value(value) }
    override fun read(reader: JsonReader): Long {
        val text = reader.nextString()
        text.toLongOrNull()?.let { return it }
        for (pattern in listOf("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", "yyyy-MM-dd'T'HH:mm:ssXXX")) {
            val formatter = SimpleDateFormat(pattern, Locale.ROOT).apply { isLenient = false; timeZone = TimeZone.getTimeZone("UTC") }
            val position = ParsePosition(0)
            val date = formatter.parse(text, position)
            if (date != null && position.index == text.length) return date.time
        }
        throw com.google.gson.JsonParseException("Invalid server timestamp")
    }
}
