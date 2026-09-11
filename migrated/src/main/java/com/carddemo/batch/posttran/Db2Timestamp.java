package com.carddemo.batch.posttran;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Z-GET-DB2-FORMAT-TIMESTAMP ({@code CBTRN02C.cbl:692-705}): {@code YYYY-MM-DD-HH.MM.SS.hh0000}
 * built from {@code FUNCTION CURRENT-DATE}, whose sub-second component is hundredths of a second (2
 * digits) followed by a literal {@code 0000}.
 */
public final class Db2Timestamp {

  private static final DateTimeFormatter FORMAT =
      DateTimeFormatter.ofPattern("yyyy-MM-dd-HH.mm.ss.SS'0000'");

  private Db2Timestamp() {}

  public static String now(Clock clock) {
    return format(LocalDateTime.now(clock));
  }

  public static String format(LocalDateTime t) {
    return FORMAT.format(t);
  }
}
