package com.carddemo.common;

/** A legacy screen-level error: the message text is preserved verbatim from the COBOL source. */
public class BusinessRuleException extends RuntimeException {
  private final String field;

  public BusinessRuleException(String message, String field) {
    super(message);
    this.field = field;
  }

  /** Screen field the legacy program positioned the cursor on (e.g. ACTIDIN, CONFIRM). */
  public String getField() {
    return field;
  }
}
