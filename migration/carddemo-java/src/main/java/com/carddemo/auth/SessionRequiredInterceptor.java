package com.carddemo.auth;

import com.carddemo.common.UnauthenticatedException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Every CICS screen program requires a COMMAREA (EIBCALEN > 0) and otherwise XCTLs back to COSGN00C
 * (COBIL00C.cbl 107-109). Here: any /api/** call other than sign-on needs a session.
 */
@Configuration
public class SessionRequiredInterceptor implements WebMvcConfigurer, HandlerInterceptor {

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(this).addPathPatterns("/api/**").excludePathPatterns("/api/session");
  }

  @Override
  public boolean preHandle(
      HttpServletRequest request, HttpServletResponse response, Object handler) {
    HttpSession session = request.getSession(false);
    if (session == null || !(session.getAttribute(SessionUser.ATTRIBUTE) instanceof SessionUser)) {
      throw new UnauthenticatedException();
    }
    return true;
  }
}
