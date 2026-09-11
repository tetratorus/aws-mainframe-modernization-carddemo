package com.carddemo.io;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Sequential (QSAM-style) fixed-width record files. Records are stored one per line, as in {@code
 * app/data/ASCII}; short lines are space-padded to the record length by the record parsers.
 */
public final class RecordFiles {

  /** Byte-transparent charset so that every byte of a legacy record survives a round trip. */
  public static final Charset CHARSET = StandardCharsets.ISO_8859_1;

  private RecordFiles() {}

  public static <T> List<T> readAll(Path file, Function<String, T> parser) throws IOException {
    try (Stream<String> lines = Files.lines(file, CHARSET)) {
      return lines.filter(l -> !l.isEmpty()).map(parser).toList();
    }
  }

  /** Streams records one at a time (the legacy program processes DALYTRAN sequentially). */
  public static <T> Stream<T> stream(Path file, Function<String, T> parser) throws IOException {
    return Files.lines(file, CHARSET).filter(l -> !l.isEmpty()).map(parser);
  }

  /** Writer that appends one fixed-width record image per line. */
  public static final class Writer implements AutoCloseable {
    private final BufferedWriter out;

    public Writer(Path file) throws IOException {
      this.out = Files.newBufferedWriter(file, CHARSET);
    }

    public void write(String image) throws IOException {
      out.write(image);
      out.newLine();
    }

    @Override
    public void close() throws IOException {
      out.close();
    }
  }
}
