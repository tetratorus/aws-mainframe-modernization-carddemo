package com.carddemo.auth;

import com.carddemo.common.UnauthenticatedException;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Transaction CC00 (sign-on screen COSGN00). */
@RestController
@RequestMapping("/api/session")
public class AuthController {

  private final SignonService signonService;

  public AuthController(SignonService signonService) {
    this.signonService = signonService;
  }

  @PostMapping
  public SessionUser signon(@RequestBody SignonRequest request, HttpSession session) {
    SessionUser user = signonService.signon(request.userId(), request.password());
    session.setAttribute(SessionUser.ATTRIBUTE, user);
    return user;
  }

  @GetMapping
  public SessionUser current(HttpSession session) {
    return CurrentUser.require(session);
  }

  /** PF3 on the sign-on screen: CCDA-MSG-THANK-YOU. */
  @PostMapping("/signoff")
  public void signoff(HttpSession session) {
    session.invalidate();
  }

  static final class CurrentUser {
    private CurrentUser() {}

    static SessionUser require(HttpSession session) {
      Object user = session.getAttribute(SessionUser.ATTRIBUTE);
      if (user instanceof SessionUser sessionUser) {
        return sessionUser;
      }
      throw new UnauthenticatedException();
    }
  }
}
