package dev.shantoislam.agenticwebview.webview.protocol

import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
internal class RuntimeProtocolCodec(
    private val maximumMessageBytes: Int,
    private val json: Json = Json {
        ignoreUnknownKeys = false
        explicitNulls = false
        encodeDefaults = true
    },
) {
    init { require(maximumMessageBytes > 0) { "maximumMessageBytes must be positive" } }

    fun encodeRequest(request: RuntimeRequestEnvelope): CodecResult<String> = encode {
        json.encodeToString(request)
    }

    fun encodeResponse(response: RuntimeResponseEnvelope): CodecResult<String> = encode {
        json.encodeToString(response)
    }

    fun decodeResponse(raw: String): CodecResult<RuntimeResponseEnvelope> {
        val byteCount = raw.toByteArray(Charsets.UTF_8).size
        if (byteCount > maximumMessageBytes) {
            return CodecResult.Failure("Runtime response is $byteCount bytes; limit is $maximumMessageBytes")
        }
        return try {
            CodecResult.Success(json.decodeFromString<RuntimeResponseEnvelope>(raw))
        } catch (error: SerializationException) {
            CodecResult.Failure("Malformed runtime response: ${error.message}")
        } catch (error: IllegalArgumentException) {
            CodecResult.Failure("Invalid runtime response: ${error.message}")
        }
    }

    private inline fun encode(block: () -> String): CodecResult<String> = try {
        val encoded = block()
        val byteCount = encoded.toByteArray(Charsets.UTF_8).size
        if (byteCount > maximumMessageBytes) {
            CodecResult.Failure("Encoded runtime message is $byteCount bytes; limit is $maximumMessageBytes")
        } else {
            CodecResult.Success(encoded)
        }
    } catch (error: SerializationException) {
        CodecResult.Failure("Failed to encode runtime message: ${error.message}")
    } catch (error: IllegalArgumentException) {
        CodecResult.Failure("Invalid runtime message: ${error.message}")
    }
}

internal sealed interface CodecResult<out T> {
    data class Success<T>(val value: T) : CodecResult<T>
    data class Failure(val reason: String) : CodecResult<Nothing>
}
