package uk.gov.justice.digital.hmpps.incentivesapi.security

import io.netty.buffer.Unpooled
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.handler.codec.http.HttpRequest
import io.netty.handler.codec.http.HttpServerCodec
import io.netty.util.ReferenceCountUtil
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.charset.StandardCharsets

class NettyTransferEncodingSecurityTest {
  @Test
  fun `decoder rejects ambiguous transfer encodings without parsing a second request`() {
    listOf(
      "Transfer-Encoding: chunked\r\nTransfer-Encoding: gzip",
      "Transfer-Encoding: chunked\r\nTransfer-Encoding: deflate",
      "Transfer-Encoding: chunked, xchunked",
    ).forEach { headers ->
      listOf(false, true).forEach { fragmented ->
        val requests = decodeRequests(chunkedPayload(headers), fragmented)
        assertThat(requests).describedAs("%s, fragmented=%s", headers, fragmented).hasSize(1)
        assertThat(requests.single().second).isFalse()
      }
    }
  }

  @Test
  fun `decoder accepts valid chunked framing and subsequent requests`() {
    listOf(
      "Transfer-Encoding: chunked",
      "Transfer-Encoding: gzip, chunked",
      "Transfer-Encoding: gzip\r\nTransfer-Encoding: ChUnKeD",
    ).forEach { headers ->
      listOf(false, true).forEach { fragmented ->
        assertThat(decodeRequests(chunkedPayload(headers), fragmented)).containsExactly("/one" to true, "/two" to true)
      }
    }
    val contentLengthPayload =
      "POST /one HTTP/1.1\r\nHost: localhost\r\nContent-Length: 3\r\n\r\nabc" +
        "GET /two HTTP/1.1\r\nHost: localhost\r\n\r\n"
    assertThat(decodeRequests(contentLengthPayload, true)).containsExactly("/one" to true, "/two" to true)
  }

  private fun chunkedPayload(headers: String) =
    "POST /one HTTP/1.1\r\nHost: localhost\r\n$headers\r\n\r\n3\r\nabc\r\n0\r\n\r\n" +
      "GET /two HTTP/1.1\r\nHost: localhost\r\n\r\n"

  private fun decodeRequests(payload: String, fragmented: Boolean): List<Pair<String, Boolean>> {
    val channel = EmbeddedChannel(HttpServerCodec())
    return try {
      val parts = if (fragmented) payload.chunked(17) else listOf(payload)
      parts.forEach { channel.writeInbound(Unpooled.copiedBuffer(it, StandardCharsets.US_ASCII)) }
      val requests = mutableListOf<Pair<String, Boolean>>()
      while (true) {
        val message = channel.readInbound<Any>() ?: break
        if (message is HttpRequest) requests.add(message.uri() to message.decoderResult().isSuccess)
        ReferenceCountUtil.release(message)
      }
      requests
    } finally {
      channel.finishAndReleaseAll()
    }
  }
}
