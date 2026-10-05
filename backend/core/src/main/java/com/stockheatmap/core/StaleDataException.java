package com.stockheatmap.core;

/** Thrown when too little of a refresh succeeded to be worth overwriting the last good file. */
public class StaleDataException extends RuntimeException {

  public StaleDataException(String message) {
    super(message);
  }
}
