package com.prepstart.Service;

import com.prepstart.Repository.UserRepository;
import com.prepstart.model.User;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class CustomOidcUserService extends OidcUserService {

    private final UserRepository userRepository;

    public CustomOidcUserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {

        // 1. Fetch the user's profile from Microsoft
        OidcUser oidcUser = super.loadUser(userRequest);

        System.out.println("🔐 Microsoft OIDC login attempt");

        // 2. Extract email
        String email = oidcUser.getEmail();
        if (email == null) {
            email = oidcUser.getAttribute("preferred_username");
        }
        if (email == null || email.isEmpty()) {
            throw new OAuth2AuthenticationException("Email not provided by Microsoft");
        }

        // 3. Only allow login if the admin already created this account
        Optional<User> existingUser = userRepository.findByEmail(email);

        if (existingUser.isEmpty()) {
            System.out.println(" Login rejected — no PrepStart account for: " + email);
            throw new OAuth2AuthenticationException(
                    "No PrepStart account found for " + email +
                            ". Please contact your administrator to request access.");
        }

        System.out.println(" Existing user logged in via Microsoft: " + email);

        return oidcUser;
    }
}