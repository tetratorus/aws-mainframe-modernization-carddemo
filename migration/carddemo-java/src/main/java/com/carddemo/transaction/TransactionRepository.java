package com.carddemo.transaction;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransactionRepository extends JpaRepository<Transaction, String> {
  Optional<Transaction> findFirstByOrderByTranIdDesc();
}
