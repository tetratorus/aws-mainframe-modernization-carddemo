package com.carddemo.auth;

import java.io.Serializable;

/** The user-identity part of CARDDEMO-COMMAREA (app/cpy/COCOM01Y.cpy 24-28). */
public record SessionUser(String userId, String userType) implements Serializable {
  public static final String ATTRIBUTE = "carddemo.user";

  public boolean isAdmin() {
    return "A".equals(userType);
  }
}
