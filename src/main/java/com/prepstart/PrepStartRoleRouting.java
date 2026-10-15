package com.prepstart;

import com.prepstart.Repository.UserRepository;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Locale;

/**
 * Uses the role associated with the authenticated PrepStart account.
 * Works with form login and OAuth2/OIDC sign-ins that expose the user's email.
 */
@Component
public class PrepStartRoleRouting implements AuthenticationSuccessHandler {

    private final UserRepository userRepository;

    public PrepStartRoleRouting(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    private String emailFor(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof OAuth2User oauth2User) {
            String email = oauth2User.getAttribute("email");
            if (email != null && !email.isBlank()) return email;
        }
        if (principal instanceof UserDetails details) {
            return details.getUsername();
        }
        return authentication.getName();
    }

    private String accountRole(Authentication authentication) {
        String email = emailFor(authentication);
        if (email == null || email.isBlank()) return "";

        return userRepository.findByEmail(email)
                .map(user -> user.getRole())
                .map(role -> role.getName())
                .map(name -> name.trim().toUpperCase(Locale.ROOT))
                .map(name -> name.startsWith("ROLE_") ? name.substring(5) : name)
                .orElse("");
    }

    public boolean isRole(Authentication authentication, String requiredRole) {
        return requiredRole.equalsIgnoreCase(accountRole(authentication));
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication)
            throws IOException, ServletException {
        String role = accountRole(authentication);
        String destination;
        if ("ADMIN".equals(role)) {
            destination = "/admin/users";
        } else if ("STUDENT".equals(role)) {
            destination = "/home";
        } else {
            // Do not silently grant access to the student area to unknown roles.
            response.sendError(HttpServletResponse.SC_FORBIDDEN,
                    "No supported PrepStart role is assigned to this account.");
            return;
        }
        response.sendRedirect(response.encodeRedirectURL(request.getContextPath() + destination));
    }
}
