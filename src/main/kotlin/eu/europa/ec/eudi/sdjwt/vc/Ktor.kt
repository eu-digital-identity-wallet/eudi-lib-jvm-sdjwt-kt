package eu.europa.ec.eudi.sdjwt.vc


import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*

/**
 * Alias of a method that creates a [HttpClient]
 */
typealias KtorHttpClientFactory = () -> HttpClient

/**
 * Factory which produces a [Ktor Http client][HttpClient]
 *
 * The actual engine will be peeked up by whatever
 * it is available in classpath
 *
 * @see [Ktor Client]("https://ktor.io/docs/client-dependencies.html#engine-dependency)
 */
val DefaultHttpClientFactory: KtorHttpClientFactory = {
    HttpClient {
        install(ContentNegotiation) { json() }
        expectSuccess = true
    }
}

