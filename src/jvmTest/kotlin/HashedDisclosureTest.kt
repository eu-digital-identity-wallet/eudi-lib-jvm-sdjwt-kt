import niscy.eudiw.sdjwt.Disclosure
import niscy.eudiw.sdjwt.HashAlgorithm
import niscy.eudiw.sdjwt.HashedDisclosure
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class HashedDisclosureTest {

    @Test
    fun simple() {
        val disclosure = Disclosure.wrap("WyI2cU1RdlJMNWhhaiIsICJmYW1pbHlfbmFtZSIsICJNw7ZiaXVzIl0").getOrThrow()

        val expectedHash = "uutlBuYeMDyjLLTpf6Jxi7yNkEF35jdyWMn9U7b_RYY"
        val hashed = HashedDisclosure.create(HashAlgorithm.SHA_256, disclosure).getOrThrow()

        assertEquals(expectedHash, hashed.value)
    }
}