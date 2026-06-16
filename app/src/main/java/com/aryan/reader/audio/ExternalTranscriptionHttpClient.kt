package com.aryan.reader.audio

import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface ExternalTranscriptionHttpClient {
    suspend fun postMultipart(
        url: String,
        headers: Map<String, String>,
        fields: Map<String, String>,
        fileFieldName: String,
        fileName: String,
        contentType: String,
        bytes: ByteArray
    ): String

    suspend fun postBytes(
        url: String,
        headers: Map<String, String>,
        contentType: String,
        bytes: ByteArray
    ): String
}

class UrlConnectionExternalTranscriptionHttpClient : ExternalTranscriptionHttpClient {
    override suspend fun postMultipart(
        url: String,
        headers: Map<String, String>,
        fields: Map<String, String>,
        fileFieldName: String,
        fileName: String,
        contentType: String,
        bytes: ByteArray
    ): String = withContext(Dispatchers.IO) {
        val boundary = "EpistemeAudioSync${System.nanoTime()}"
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 30_000
            readTimeout = 180_000
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            headers.forEach { (key, value) -> setRequestProperty(key, value) }
        }
        try {
            connection.outputStream.use { output ->
                fun write(value: String) = output.write(value.toByteArray(Charsets.UTF_8))
                fields.forEach { (name, value) ->
                    write("--$boundary\r\n")
                    write("Content-Disposition: form-data; name=\"$name\"\r\n\r\n")
                    write(value)
                    write("\r\n")
                }
                write("--$boundary\r\n")
                write("Content-Disposition: form-data; name=\"$fileFieldName\"; filename=\"${fileName.replace("\"", "_")}\"\r\n")
                write("Content-Type: $contentType\r\n\r\n")
                output.write(bytes)
                write("\r\n--$boundary--\r\n")
            }
            connection.readResponse()
        } finally {
            connection.disconnect()
        }
    }

    override suspend fun postBytes(
        url: String,
        headers: Map<String, String>,
        contentType: String,
        bytes: ByteArray
    ): String = withContext(Dispatchers.IO) {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 30_000
            readTimeout = 180_000
            setRequestProperty("Content-Type", contentType)
            headers.forEach { (key, value) -> setRequestProperty(key, value) }
        }
        try {
            connection.outputStream.use { it.write(bytes) }
            connection.readResponse()
        } finally {
            connection.disconnect()
        }
    }

    private fun HttpURLConnection.readResponse(): String {
        val stream = if (responseCode in 200..299) inputStream else errorStream
        val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
        if (responseCode !in 200..299) error("HTTP $responseCode: $body")
        return body
    }
}
