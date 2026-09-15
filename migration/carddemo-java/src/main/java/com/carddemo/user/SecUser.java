package com.carddemo.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** SEC-USER-DATA, app/cpy/CSUSR01Y.cpy. Password is stored hashed rather than in clear text. */
@Entity
@Table(name = "sec_user")
public class SecUser {

  public static final String TYPE_ADMIN = "A";
  public static final String TYPE_USER = "U";

  @Id
  @Column(name = "usr_id", length = 8)
  private String usrId;

  @Column(name = "first_name", nullable = false)
  private String firstName;

  @Column(name = "last_name", nullable = false)
  private String lastName;

  @Column(name = "password_hash", nullable = false)
  private String passwordHash;

  @Column(name = "usr_type", nullable = false)
  private String usrType;

  protected SecUser() {}

  public SecUser(
      String usrId, String firstName, String lastName, String passwordHash, String usrType) {
    this.usrId = usrId;
    this.firstName = firstName;
    this.lastName = lastName;
    this.passwordHash = passwordHash;
    this.usrType = usrType;
  }

  public String getUsrId() {
    return usrId;
  }

  public String getFirstName() {
    return firstName;
  }

  public String getLastName() {
    return lastName;
  }

  public String getPasswordHash() {
    return passwordHash;
  }

  public String getUsrType() {
    return usrType;
  }

  public boolean isAdmin() {
    return TYPE_ADMIN.equals(usrType);
  }
}
