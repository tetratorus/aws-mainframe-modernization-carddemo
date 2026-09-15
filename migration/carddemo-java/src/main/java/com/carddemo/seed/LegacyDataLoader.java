package com.carddemo.seed;

import com.carddemo.account.AccountRepository;
import com.carddemo.transaction.TransactionRepository;
import com.carddemo.user.SecUser;
import com.carddemo.user.SecUserRepository;
import com.carddemo.xref.CardXrefRepository;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Replaces the IDCAMS REPRO load jobs (app/jcl/ACCTFILE.jcl, XREFFILE.jcl, TRANFILE.jcl,
 * DUSRSECJ.jcl): loads the legacy sample data into empty tables on start-up.
 */
@Component
public class LegacyDataLoader implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(LegacyDataLoader.class);

  private final LegacyDataProperties properties;
  private final AccountRepository accounts;
  private final CardXrefRepository xrefs;
  private final TransactionRepository transactions;
  private final SecUserRepository users;
  private final PasswordEncoder passwordEncoder;

  public LegacyDataLoader(
      LegacyDataProperties properties,
      AccountRepository accounts,
      CardXrefRepository xrefs,
      TransactionRepository transactions,
      SecUserRepository users,
      PasswordEncoder passwordEncoder) {
    this.properties = properties;
    this.accounts = accounts;
    this.xrefs = xrefs;
    this.transactions = transactions;
    this.users = users;
    this.passwordEncoder = passwordEncoder;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments args) throws IOException {
    Path dir = Path.of(properties.legacyDataDir());
    if (accounts.count() == 0) {
      accounts.saveAll(read(dir.resolve("acctdata.txt"), LegacyRecordParser::account));
    }
    if (xrefs.count() == 0) {
      xrefs.saveAll(read(dir.resolve("cardxref.txt"), LegacyRecordParser::cardXref));
    }
    if (transactions.count() == 0) {
      transactions.saveAll(read(dir.resolve("dailytran.txt"), LegacyRecordParser::transaction));
    }
    if (properties.seedUsers() && users.count() == 0) {
      // Sample credentials documented in README.md ("Admin access" / "User access").
      users.saveAll(
          List.of(
              new SecUser("ADMIN001", "Admin", "User", passwordEncoder.encode("PASSWORD"), "A"),
              new SecUser("USER0001", "Regular", "User", passwordEncoder.encode("PASSWORD"), "U")));
    }
  }

  private static <T> List<T> read(Path file, Function<String, T> parser) throws IOException {
    if (!Files.exists(file)) {
      log.warn("Legacy sample file {} not found; table left empty", file);
      return List.of();
    }
    List<T> rows =
        Files.readAllLines(file, StandardCharsets.ISO_8859_1).stream()
            .filter(l -> !l.isBlank())
            .map(parser)
            .toList();
    log.info("Loaded {} records from {}", rows.size(), file.getFileName());
    return rows;
  }
}
