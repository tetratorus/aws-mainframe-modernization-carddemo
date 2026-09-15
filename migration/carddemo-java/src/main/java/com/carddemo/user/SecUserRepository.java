package com.carddemo.user;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SecUserRepository extends JpaRepository<SecUser, String> {}
