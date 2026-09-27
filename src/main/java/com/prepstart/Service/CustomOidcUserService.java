package com.prepstart.Service;

import com.prepstart.Repository.RoleRepository;
import com.prepstart.Repository.UserRepository;
import com.prepstart.model.Role;
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
    private final RoleRepository roleRepository;

    public CustomOidcUserService(UserRepository userRepository, RoleRepository roleRepository) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
    }

    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {

        // 1. Let Spring fetch the user info from the OIDC endpoint
        OidcUser oidcUser = super.loadUser(userRequest);

        // 2. Extract user info (OIDC userinfo uses standard claim names)
        String email = oidcUser.getEmail();
        String firstName = oidcUser.getGivenName();
        String lastName = oidcUser.getFamilyName();

        // Fallbacks in case some claims are missing
        if (email == null) {
            email = oidcUser.getAttribute("preferred_username");
        }
        if (firstName == null) {
            firstName = "User";
        }
        if (lastName == null) {
            lastName = "";
        }

        if (email == null || email.isEmpty()) {
            throw new OAuth2AuthenticationException("Email not provided by Microsoft");
        }

        System.out.println("🔐 Microsoft OIDC login: " + email);

        // 3. Create user in our DB if not exists
        Optional<User> existingUser = userRepository.findByEmail(email);

        if (existingUser.isEmpty()) {
            User newUser = new User();
            newUser.setEmail(email);
            newUser.setFirstName(firstName);
            newUser.setLastName(lastName);

            String baseUsername = email.substring(0, email.indexOf("@"));
            newUser.setUsername(generateUniqueUsername(baseUsername));
            newUser.setPasswordHash("OAUTH2_USER_NO_PASSWORD");
            newUser.setEmailVerified(true);
            newUser.setStatus("active");

            Optional<Role> studentRole = roleRepository.findById(1);
            studentRole.ifPresent(newUser::setRole);

            userRepository.save(newUser);
            System.out.println("✅ New Microsoft user created: " + email);
        } else {
            System.out.println("✅ Existing user logged in via Microsoft: " + email);
        }

        return oidcUser;
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