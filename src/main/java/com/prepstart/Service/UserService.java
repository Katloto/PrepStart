package com.prepstart.Service;

import com.prepstart.Repository.UserRepository;
import com.prepstart.Repository.RoleRepository;
import com.prepstart.model.User;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    public UserService(UserRepository userRepository,
                       RoleRepository roleRepository,
                       PasswordEncoder passwordEncoder,
                       EmailService emailService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
    }

    // ADMIN: CREATE USER (with auto-generated password)
    public User adminCreateUser(String firstName, String lastName, String email,
                                String studentNumber) {

        if (userRepository.existsByEmail(email)) {
            throw new RuntimeException("A user with this email already exists.");
        }

        // 1. Generate a random password
        String plainPassword = generateRandomPassword();

        // 2. Create the user
        User user = new User();
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(plainPassword));
        user.setEmailVerified(true);
        user.setStatus("active");

        // 3. Assign STUDENT role
        roleRepository.findById(1).ifPresent(user::setRole);

        // 4. Save
        User saved = userRepository.save(user);

        // 5. Email the credentials to the student
        emailService.sendCredentialsEmail(email, firstName, email, plainPassword);

        // 6. Print to console (admin backup if email fails)
        System.out.println("==============================================");
        System.out.println(" ACCOUNT CREATED");
        System.out.println("   Email:    " + email);
        System.out.println("   Password: " + plainPassword);
        System.out.println("   Credentials emailed to student.");
        System.out.println("==============================================");

        return saved;
    }

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    // PASSWORD RESET (for Forgot Password feature)
    public String createPasswordResetToken(String email) {
        Optional<User> userOptional = userRepository.findByEmail(email);
        if (userOptional.isEmpty()) return null;

        User user = userOptional.get();
        String token = generateResetToken();
        user.setResetToken(token);
        user.setResetTokenExpiry(LocalDateTime.now().plusMinutes(15));
        userRepository.save(user);

        return "http://localhost:8080/reset-password?token=" + token;
    }

    public Optional<User> findUserByValidResetToken(String token) {
        Optional<User> userOptional = userRepository.findByResetToken(token);
        if (userOptional.isEmpty()) return Optional.empty();

        User user = userOptional.get();
        if (user.getResetTokenExpiry() == null ||
                user.getResetTokenExpiry().isBefore(LocalDateTime.now())) {
            return Optional.empty();
        }
        return Optional.of(user);
    }

    public void resetPassword(User user, String newPassword) {
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setResetToken(null);
        user.setResetTokenExpiry(null);
        userRepository.save(user);
    }

    // HELPERS
    private String generateRandomPassword() {
        // Avoids confusing chars: 0/O, 1/l/I
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789!@#$%";
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 12; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }

    private String generateResetToken() {
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[24];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }
}