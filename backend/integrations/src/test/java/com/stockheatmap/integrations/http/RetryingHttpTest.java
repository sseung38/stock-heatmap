package com.stockheatmap.integrations.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

class RetryingHttpTest {

  private static final String URL = "https://api.example.com/data";

  private MockRestServiceServer server;
  private final List<Long> sleeps = new ArrayList<>();
  private RetryingHttp http;

  @BeforeEach
  void setUp() {
    RestClient.Builder builder = RestClient.builder();
    server = MockRestServiceServer.bindTo(builder).build();
    http = new RetryingHttp(builder.build(), sleeps::add);
  }

  @Test
  void retriesServerErrorsThenSucceeds() {
    server.expect(times(2), requestTo(URL)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
    server.expect(once(), requestTo(URL)).andRespond(withSuccess("{\"ok\":1}", MediaType.APPLICATION_JSON));

    JsonNode body = http.getJson(URL, Map.of(), JsonNode.class);

    assertThat(body.get("ok").asInt()).isEqualTo(1);
    assertThat(sleeps).containsExactly(500L, 1000L);
    server.verify();
  }

  @Test
  void sendsHeaders() {
    server.expect(once(), requestTo(URL)).andExpect(header("X-Key", "abc"))
        .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

    http.getJson(URL, Map.of("X-Key", "abc"), JsonNode.class);

    server.verify();
  }

  @Test
  void doesNotRetryClientErrors() {
    server.expect(once(), requestTo(URL)).andRespond(withStatus(HttpStatus.NOT_FOUND));

    assertThatThrownBy(() -> http.getJson(URL, Map.of(), JsonNode.class))
        .isInstanceOfSatisfying(HttpStatusException.class, e -> assertThat(e.status()).isEqualTo(404));
    assertThat(sleeps).isEmpty();
    server.verify();
  }

  @Test
  void givesUpAfterThreeRetries() {
    server.expect(times(4), requestTo(URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

    assertThatThrownBy(() -> http.getJson(URL, Map.of(), JsonNode.class))
        .isInstanceOfSatisfying(HttpStatusException.class, e -> assertThat(e.status()).isEqualTo(429));
    assertThat(sleeps).containsExactly(500L, 1000L, 2000L);
    server.verify();
  }

  @Test
  void readsText() {
    server.expect(once(), requestTo(URL)).andRespond(withSuccess("<html></html>", MediaType.TEXT_HTML));

    assertThat(http.getText(URL, Map.of())).isEqualTo("<html></html>");
  }
}
