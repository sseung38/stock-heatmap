package com.stockheatmap.functions;

import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.GZIPOutputStream;
import tools.jackson.databind.ObjectMapper;

/**
 * Builds HTTP API responses. API Gateway does not compress, so we gzip ourselves when the browser
 * accepts it: market data shrinks from ~400 KB to well under 100 KB. Binary bodies must be base64
 * encoded in the Lambda response, with isBase64Encoded telling API Gateway to decode them.
 */
final class ApiResponses {

  private ApiResponses() {}

  static APIGatewayV2HTTPResponse json(int status, Object body, String acceptEncoding, ObjectMapper mapper) {
    byte[] bytes = mapper.writeValueAsBytes(body);
    Map<String, String> headers = new HashMap<>();
    headers.put("content-type", "application/json");
    headers.put("cache-control", "private, max-age=30");
    var response = APIGatewayV2HTTPResponse.builder().withStatusCode(status).withHeaders(headers);

    if (acceptEncoding != null && acceptEncoding.contains("gzip")) {
      headers.put("content-encoding", "gzip");
      return response.withBody(Base64.getEncoder().encodeToString(gzip(bytes))).withIsBase64Encoded(true).build();
    }
    return response.withBody(new String(bytes, StandardCharsets.UTF_8)).withIsBase64Encoded(false).build();
  }

  /** Header names arrive lower-cased from HTTP APIs (payload format 2.0). */
  static String header(Map<String, String> headers, String name) {
    return headers == null ? null : headers.get(name);
  }

  private static byte[] gzip(byte[] bytes) {
    var out = new ByteArrayOutputStream(bytes.length / 4);
    try (var gzip = new GZIPOutputStream(out)) {
      gzip.write(bytes);
    } catch (IOException e) {
      throw new UncheckedIOException(e); // in-memory streams don't throw
    }
    return out.toByteArray();
  }
}
