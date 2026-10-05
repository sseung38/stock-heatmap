package com.stockheatmap.integrations.http;

import java.net.URI;
import java.util.Map;
import java.util.function.Function;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * GET requests through Spring's RestClient, retried 3 times (after 500, 1000, 2000 ms) on 429,
 * 5xx and network errors. Other 4xx responses fail immediately.
 */
public class RetryingHttp {

  static final long[] RETRY_DELAYS_MS = {500, 1000, 2000};

  private final RestClient restClient;
  private final Sleeper sleeper;

  public RetryingHttp(RestClient restClient, Sleeper sleeper) {
    this.restClient = restClient;
    this.sleeper = sleeper;
  }

  public <T> T getJson(String url, Map<String, String> headers, Class<T> type) {
    return get(url, headers, response -> response.body(type));
  }

  public <T> T getJson(String url, Map<String, String> headers, ParameterizedTypeReference<T> type) {
    return get(url, headers, response -> response.body(type));
  }

  public String getText(String url, Map<String, String> headers) {
    return get(url, headers, response -> response.body(String.class));
  }

  private <T> T get(String url, Map<String, String> headers, Function<RestClient.ResponseSpec, T> read) {
    URI uri = URI.create(url);
    for (int attempt = 0; ; attempt++) {
      try {
        return read.apply(restClient.get().uri(uri).headers(h -> headers.forEach(h::set)).retrieve());
      } catch (RestClientResponseException e) {
        int status = e.getStatusCode().value();
        boolean retryable = status == 429 || status >= 500;
        if (!retryable || attempt == RETRY_DELAYS_MS.length) {
          throw new HttpStatusException(status, uri.getHost() + uri.getPath());
        }
      } catch (ResourceAccessException e) {
        if (attempt == RETRY_DELAYS_MS.length) {
          throw e;
        }
      }
      sleeper.sleep(RETRY_DELAYS_MS[attempt]);
    }
  }
}
