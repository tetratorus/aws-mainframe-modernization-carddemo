package com.carddemo.auth;

import com.carddemo.common.BusinessRuleException;
import com.carddemo.user.SecUser;
import com.carddemo.user.SecUserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/** COSGN00C.cbl PROCESS-ENTER-KEY / READ-USER-SEC-FILE (lines 111-139, 209-252). */
@Service
public class SignonService {

  static final String MSG_USERID_EMPTY = "Please enter User ID ...";
  static final String MSG_PASSWORD_EMPTY = "Please enter Password ...";
  static final String MSG_WRONG_PASSWORD = "Wrong Password. Try again ...";
  static final String MSG_USER_NOT_FOUND = "User not found. Try again ...";

  private final SecUserRepository users;
  private final PasswordEncoder passwordEncoder;

  public SignonService(SecUserRepository users, PasswordEncoder passwordEncoder) {
    this.users = users;
    this.passwordEncoder = passwordEncoder;
  }

  public SessionUser signon(String userId, String password) {
    if (userId == null || userId.isBlank()) {
      throw new BusinessRuleException(MSG_USERID_EMPTY, "USERID");
    }
    if (password == null || password.isBlank()) {
      throw new BusinessRuleException(MSG_PASSWORD_EMPTY, "PASSWD");
    }
    // COSGN00C.cbl 132-135: both fields are upper-cased before the lookup/compare.
    String id = userId.strip().toUpperCase();
    String pwd = password.strip().toUpperCase();

    SecUser user =
        users
            .findById(id)
            .orElseThrow(() -> new BusinessRuleException(MSG_USER_NOT_FOUND, "USERID"));
    if (!passwordEncoder.matches(pwd, user.getPasswordHash())) {
      throw new BusinessRuleException(MSG_WRONG_PASSWORD, "PASSWD");
    }
    return new SessionUser(user.getUsrId(), user.getUsrType());
  }
}
