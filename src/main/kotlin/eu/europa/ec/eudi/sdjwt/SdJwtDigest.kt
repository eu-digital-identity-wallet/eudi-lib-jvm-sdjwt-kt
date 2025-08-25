/*
 * Copyright (c) 2023 European Commission
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
package eu.europa.ec.eudi.sdjwt

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * The digest of a [presentation][SdJwt].
 * It contains the base64-url encoded digest of a presentation with all padding characters removed.
 */
@Serializable(with = SdJwtDigestSerializer::class)
@JvmInline
value class SdJwtDigest private constructor(val value: String) {

    companion object {

        /**
         * Wraps the given [value] to a [SdJwtDigest].
         * The [value] is expected to be base64-url encoded.
         *
         * @param value the base64-url encoded without padding digest value to wrap
         * @return the wrapped value
         */
        fun wrap(value: String): Result<SdJwtDigest> = runCatchingCancellable {
            Base64UrlNoPadding.decode(value)
            SdJwtDigest(value)
        }

        /**
         * Calculates the [integrity][SdJwtDigest] of serialized presentation
         *
         * @param hashAlgorithm the [HashAlgorithm] to be used for the calculation of the digest
         * @param value the serialized SD-JWT to calculate the digest for
         * @return the calculated digest
         */
        fun digest(hashAlgorithm: HashAlgorithm, value: String): Result<SdJwtDigest> =
            digestInternal(platform().hashes, hashAlgorithm, value)

        /**
         * Internal version of digest that takes a Platform parameter
         */
        internal fun digestInternal(
            hashes: Hashes,
            hashAlgorithm: HashAlgorithm,
            value: String,
        ): Result<SdJwtDigest> = runCatchingCancellable {
            require(value.contains(SdJwtSpec.DISCLOSURE_SEPARATOR))
            fun String.noKeyBinding() =
                if (endsWith(SdJwtSpec.DISCLOSURE_SEPARATOR)) {
                    this
                } else {
                    removeRange(lastIndexOf(SdJwtSpec.DISCLOSURE_SEPARATOR) + 1, length)
                }

            val input = value.noKeyBinding().encodeToByteArray()
            val digest = hashes.digest(hashAlgorithm, input)
            SdJwtDigest(Base64UrlNoPadding.encode(digest))
        }
    }
}

object SdJwtDigestSerializer : KSerializer<SdJwtDigest> {
    override val descriptor: SerialDescriptor
        get() = PrimitiveSerialDescriptor("SdJwtDigest", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: SdJwtDigest) {
        encoder.encodeString(value.value)
    }

    override fun deserialize(decoder: Decoder): SdJwtDigest =
        decoder.decodeString().let {
            SdJwtDigest.wrap(it).getOrElse { cause ->
                throw SerializationException("Failed to deserialize SdJwtDigest", cause)
            }
        }
}
