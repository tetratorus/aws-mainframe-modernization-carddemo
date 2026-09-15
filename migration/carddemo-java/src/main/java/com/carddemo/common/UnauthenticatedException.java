package com.carddemo.common;

public class UnauthenticatedException extends RuntimeException {
  public UnauthenticatedException() {
    super("Please sign on first...");
  }
}
