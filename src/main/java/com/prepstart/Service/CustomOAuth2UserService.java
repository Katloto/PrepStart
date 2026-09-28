package com.prepstart.Service;

import com.prepstart.Repository.UserRepository;
import com.prepstart.model.User;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
public class CustomOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    private final UserRepository userRepository;

    public CustomOAuth2UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {

        // 1. Fetch the user's profile from Google
        OAuth2User oauthUser = new DefaultOAuth2UserService().loadUser(userRequest);

        String provider = userRequest.getClientRegistration().getRegistrationId();
        System.out.println("🔐 OAuth2 login attempt from provider: " + provider);

        // 2. Extract email
        String email = oauthUser.getAttribute("email");
        if (email == null || email.isEmpty()) {
            throw new OAuth2AuthenticationException("Email not provided by " + provider);
        }

        // 3. Only allow login if the admin already created this account
        Optional<User> existingUser = userRepository.findByEmail(email);

        if (existingUser.isEmpty()) {
            System.out.println(" Login rejected — no PrepStart account for: " + email);
            throw new OAuth2AuthenticationException(
                    "No PrepStart account found for " + email +
                            ". Please contact your administrator to request access.");
        }

        System.out.println(" Existing user logged in via " + provider + ": " + email);

        // 4. Return an OAuth2User whose getName() returns the email
        Map<String, Object> attributes = new HashMap<>(oauthUser.getAttributes());
        attributes.put("email", email);

        return new DefaultOAuth2User(
                oauthUser.getAuthorities(),
                attributes,
                "email"
        );
    }
}