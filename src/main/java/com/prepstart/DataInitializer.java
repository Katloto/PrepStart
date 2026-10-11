package com.prepstart;

import com.prepstart.Repository.RoleRepository;
import com.prepstart.Repository.UserRepository;
import com.prepstart.model.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    // Read admin credentials from application.properties
    @Value("${prepstart.admin.email}")
    private String adminEmail;

    @Value("${prepstart.admin.password}")
    private String adminPassword;

    @Value("${prepstart.admin.first-name}")
    private String adminFirstName;

    @Value("${prepstart.admin.last-name}")
    private String adminLastName;

    public DataInitializer(UserRepository userRepository,
                           RoleRepository roleRepository,
                           PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        // Create default admin account if none exists
        if (userRepository.findByEmail(adminEmail).isEmpty()) {

            User admin = new User();
            admin.setFirstName(adminFirstName);
            admin.setLastName(adminLastName);
            admin.setEmail(adminEmail);
            admin.setPasswordHash(passwordEncoder.encode(adminPassword));
            admin.setEmailVerified(true);
            admin.setStatus("active");

            roleRepository.findById(2).ifPresent(admin::setRole);
            userRepository.save(admin);

            System.out.println("==============================================");
            System.out.println(" DEFAULT ADMIN CREATED");
            System.out.println("   Email:    " + adminEmail);
            System.out.println("   Password: (see application.properties)");
            System.out.println("==============================================");
        }
    }
}