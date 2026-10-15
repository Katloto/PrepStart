package com.prepstart.ai;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/** Provides consistent profile initials for navbar on all MVC pages. */
@ControllerAdvice
public class NavbarIdentityAdvice {
    @ModelAttribute("navbarInitial")
    public String initial(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getName())) return "U";
        String name = authentication.getName();
        if (authentication.getPrincipal() instanceof OAuth2User oauth) {
            String email = oauth.getAttribute("email");
            if (email != null && !email.isBlank()) name = email;
        }
        if (name == null || name.isBlank()) return "U";
        return name.substring(0, 1).toUpperCase(java.util.Locale.ROOT);
    }
}
