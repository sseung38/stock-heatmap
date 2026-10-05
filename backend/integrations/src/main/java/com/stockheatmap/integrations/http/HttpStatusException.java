package com.stockheatmap.integrations.http;

/** An HTTP error response. The message holds only the path, never the query (which may carry API keys). */
public class HttpStatusException extends RuntimeException {

  private final int status;

  public HttpStatusException(int status, String path) {
    super("HTTP " + status + " from " + path);
    this.status = status;
  }

  public int status() {
    return status;
  }
}
