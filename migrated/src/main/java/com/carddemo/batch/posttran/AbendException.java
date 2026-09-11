package com.carddemo.batch.posttran;

/**
 * Equivalent of 9999-ABEND-PROGRAM ({@code CBTRN02C.cbl:707-711}): the legacy program calls {@code
 * CEE3ABD} with abend code 999, i.e. terminates with user abend U0999 and no further processing.
 */
public final class AbendException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public static final int ABEND_CODE = 999;

  public AbendException(String message) {
    super(message);
  }

  public AbendException(String message, Throwable cause) {
    super(message, cause);
  }
}
