package com.stockheatmap.core;

/** The scraped S&P 500 list looks wrong (page layout changed?); the previous metadata is kept. */
public class InvalidScrapeException extends RuntimeException {

  public InvalidScrapeException(String message) {
    super(message);
  }
}
