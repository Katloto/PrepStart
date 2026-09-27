package com.prepstart.Service;

import com.prepstart.Repository.RoleRepository;
import com.prepstart.Repository.UserRepository;
import com.prepstart.model.Role;
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
    private final RoleRepository roleRepository;

    public CustomOAuth2UserService(UserRepository userRepository, RoleRepository roleRepository) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
    }

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {

        // 1. Let Spring fetch the user's profile from the OAuth provider
        OAuth2User oauthUser = new DefaultOAuth2UserService().loadUser(userRequest);

        // 2. Find out which provider this login came from
        String provider = userRequest.getClientRegistration().getRegistrationId();
        System.out.println("🔐 OAuth2 login from provider: " + provider);

        // 3. Extract the user's info (each provider uses different attribute names)
        String email, firstName, lastName;

        if ("google".equals(provider)) {
            email = oauthUser.getAttribute("email");
            firstName = oauthUser.getAttribute("given_name");
            lastName = oauthUser.getAttribute("family_name");

        } else if ("microsoft".equals(provider)) {
            email = oauthUser.getAttribute("mail");
            if (email == null) {
                email = oauthUser.getAttribute("userPrincipalName");
            }
            String fullName = oauthUser.getAttribute("displayName");
            if (fullName != null && !fullName.isEmpty()) {
                String[] parts = fullName.split(" ", 2);
                firstName = parts[0];
                lastName = (parts.length > 1) ? parts[1] : "";
            } else {
                firstName = "User";
                lastName = "";
            }

        } else {
            throw new OAuth2AuthenticationException("Unsupported provider: " + provider);
        }

        // 4. Sanity check
        if (email == null || email.isEmpty()) {
            throw new OAuth2AuthenticationException("Email not provided by " + provider);
        }

        // 5. Do we already have this user?
        Optional<User> existingUser = userRepository.findByEmail(email);

        if (existingUser.isEmpty()) {
            User newUser = new User();
            newUser.setEmail(email);
            newUser.setFirstName(firstName != null ? firstName : "User");
            newUser.setLastName(lastName != null ? lastName : "");

            String baseUsername = email.substring(0, email.indexOf("@"));
            String uniqueUsername = generateUniqueUsername(baseUsername);
            newUser.setUsername(uniqueUsername);

            newUser.setPasswordHash("OAUTH2_USER_NO_PASSWORD");
            newUser.setEmailVerified(true);
            newUser.setStatus("active");

            Optional<Role> studentRole = roleRepository.findById(1);
            studentRole.ifPresent(newUser::setRole);

            userRepository.save(newUser);

            System.out.println("✅ New " + provider + " user created: " + email
                    + " (username: " + uniqueUsername + ")");

        } else {
            System.out.println("✅ Existing user logged in via " + provider + ": " + email);
        }

        Map<String, Object> attributes = new HashMap<>(oauthUser.getAttributes());
        attributes.put("email", email);
        return new DefaultOAuth2User(
                oauthUser.getAuthorities(),
                attributes,
                "email"
        );
    }

    private String generateUniqueUsername(String baseUsername) {
        String candidate = baseUsername;
        int suffix = 1;
        while (userRepository.existsByUsername(candidate)) {
            candidate = baseUsername + suffix;
            suffix++;
        }
        return candidate;
    }
}