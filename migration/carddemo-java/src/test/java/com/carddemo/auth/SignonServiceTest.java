package com.carddemo.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.carddemo.common.BusinessRuleException;
import com.carddemo.user.SecUser;
import com.carddemo.user.SecUserRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Rules BL-1 / V-1 of migration_plan.md, source COSGN00C.cbl 111-139, 209-252. */
class SignonServiceTest {

  private final SecUserRepository users = mock(SecUserRepository.class);
  private final PasswordEncoder encoder = new BCryptPasswordEncoder(4);
  private final SignonService service = new SignonService(users, encoder);

  private void givenUser(String id, String password, String type) {
    when(users.findById(id))
        .thenReturn(Optional.of(new SecUser(id, "F", "L", encoder.encode(password), type)));
  }

  @Test
  void requiresUserIdAndPassword() {
    assertThatThrownBy(() -> service.signon("", "x")).hasMessage("Please enter User ID ...");
    assertThatThrownBy(() -> service.signon("ADMIN001", " "))
        .hasMessage("Please enter Password ...");
  }

  @Test
  void upperCasesUserIdAndPasswordBeforeCompare() {
    givenUser("ADMIN001", "PASSWORD", "A");
    SessionUser user = service.signon("admin001", "password");
    assertThat(user.userId()).isEqualTo("ADMIN001");
    assertThat(user.isAdmin()).isTrue();
  }

  @Test
  void distinguishesUnknownUserFromWrongPassword() {
    when(users.findById("NOBODY")).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.signon("NOBODY", "x"))
        .isInstanceOf(BusinessRuleException.class)
        .hasMessage("User not found. Try again ...");

    givenUser("USER0001", "PASSWORD", "U");
    assertThatThrownBy(() -> service.signon("USER0001", "WRONG"))
        .hasMessage("Wrong Password. Try again ...");
  }
}
