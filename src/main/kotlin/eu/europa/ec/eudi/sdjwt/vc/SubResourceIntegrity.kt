/*
 * Copyright (c) 2023-2026 European Commission
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package eu.europa.ec.eudi.sdjwt.vc

import eu.europa.ec.eudi.sdjwt.platform
import kotlinx.serialization.Serializable
import kotlin.io.encoding.Base64

@Serializable
@JvmInline
value class DocumentIntegrity(val value: String) {
    init {
        require(value.matches(SRIPattern)) { "not a valid sub-resource integrity value" }
    }

    val hashes: List<DocumentHash>
        get() {
            val hashesWithOptions = value.replace("\\s+".toRegex(), " ")
                .trim()
                .split(" ")

            return hashesWithOptions.map {
                val (algorithmAndEncodedHash, options) =
                    if ("?" in it) {
                        val split = it.split("?", limit = 2)
                        split[0] to split[1]
                    } else it to null

                val (algorithm, encodedHash) = algorithmAndEncodedHash.split("-")
                val integrityAlgorithm = IntegrityAlgorithm.fromString(algorithm)!!

                DocumentHash(integrityAlgorithm, encodedHash, options)
            }
        }

    companion object {
        val SRIPattern: Regex = Regex(
            """
                ^\s*(sha(?:256|384|512)-[A-Za-z0-9+/]+={0,2}(?:\?[\x21-\x7E]*)?)(?:\s+(sha(?:256|384|512)-[A-Za-z0-9+/]+={0,2}(?:\?[\x21-\x7E]*)?))*\s*$
            """.trimIndent(),
        )
    }
}

data class DocumentHash internal constructor(
    val algorithm: IntegrityAlgorithm,
    val encodedHash: String,
    val options: String?,
)

enum class IntegrityAlgorithm(val alias: String) {
    SHA256("sha256"),
    SHA384("sha384"),
    SHA512("sha512"),
    ;

    init {
        require(alias.isNotBlank()) { "alias cannot be blank" }
    }

    companion object {
        fun fromString(alias: String): IntegrityAlgorithm? = entries.find { it.alias == alias }
    }
}

/**
 * Performs integrity validation according to [Subresource Integrity](https://www.w3.org/TR/sri/).
 *
 * @param allowedAlgorithms Hash algorithms that are allowed for integrity validation. Defaults to [IntegrityAlgorithm.entries].
 */
class SRIValidator(private val allowedAlgorithms: Set<IntegrityAlgorithm> = IntegrityAlgorithm.entries.toSet()) {
    private val base64Padding = Base64.withPadding(Base64.PaddingOption.PRESENT)

    init {
        require(allowedAlgorithms.isNotEmpty()) { "At least one integrity algorithm must be provided" }
    }

    fun isValid(expectedIntegrity: DocumentIntegrity, contentToValidate: ByteArray): Boolean {
        val expectedHashesByAlgorithm = expectedIntegrity.hashes.groupBy { it.algorithm }
        val maybeStrongestAlgorithm = expectedHashesByAlgorithm.keys
            .filter { it in allowedAlgorithms }
            .maxByOrNull { it.strength }

        return maybeStrongestAlgorithm?.let { strongestAlgorithm ->
            val strongestExpectedHashes = checkNotNull(expectedHashesByAlgorithm[strongestAlgorithm])

            val actualEncodedHash = run {
                val digest = when (strongestAlgorithm) {
                    IntegrityAlgorithm.SHA256 -> platform().hashes.sha256(contentToValidate)
                    IntegrityAlgorithm.SHA384 -> platform().hashes.sha384(contentToValidate)
                    IntegrityAlgorithm.SHA512 -> platform().hashes.sha512(contentToValidate)
                }
                base64Padding.encode(digest)
            }

            strongestExpectedHashes.any { actualEncodedHash == it.encodedHash }
        } ?: false
    }
}

private val IntegrityAlgorithm.strength: Int
    get() = when (this) {
        IntegrityAlgorithm.SHA256 -> 1
        IntegrityAlgorithm.SHA384 -> 2
        IntegrityAlgorithm.SHA512 -> 3
    }
