package com.prepstart.Service;

import com.prepstart.Repository.UserRepository;
import com.prepstart.model.User;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Collections;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {

        // 1. Find the user in our database by email
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + email));

        // 2. Determine the user's role (if any)
        String roleName = "STUDENT"; // Default role if none is set
        if (user.getRole() != null) {
            roleName = user.getRole().getName();
        }

        // 3. Return a Spring Security UserDetails object
        return new org.springframework.security.core.userdetails.User(
                user.getEmail(),           // username (used by Spring)
                user.getPasswordHash(),    // hashed password from DB
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + roleName))
        );
    }
}