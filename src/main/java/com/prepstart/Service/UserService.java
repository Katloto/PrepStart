package com.prepstart.Service;

import com.prepstart.Repository.UserRepository;
import com.prepstart.Repository.RoleRepository;
import com.prepstart.model.Role;
import com.prepstart.model.User;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
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

    public User registerUser(String firstName, String lastName, String username, String email, String password) {

        // 1. Check if email already exists
        if (userRepository.existsByEmail(email)) {
            throw new RuntimeException("Email already registered!");
        }

        // 2. Check if username already exists
        if (userRepository.existsByUsername(username)) {
            throw new RuntimeException("Username already taken!");
        }

        // 3. Create a new User object
        User user = new User();
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(password));

        // 4. Account starts as "pending" until email is verified
        user.setStatus("pending");
        user.setEmailVerified(false);

        // 5. Generate a random 6-digit verification code
        String verificationCode = generateSixDigitCode();
        user.setVerificationCode(verificationCode);

        // 6. Assign STUDENT role if it exists
        Optional<Role> studentRole = roleRepository.findById(1);
        if (studentRole.isPresent()) {
            user.setRole(studentRole.get());
        } else {
            System.out.println("WARNING: Role ID 1 not found. User saved without a role.");
        }

        // 7. Save to database
        User savedUser = userRepository.save(user);

        // 8. Send the verification email
        emailService.sendVerificationEmail(email, verificationCode);

        return savedUser;
    }

    /**
     * Generates a random 6-digit code (e.g. "482917").
     */
    private String generateSixDigitCode() {
        SecureRandom random = new SecureRandom();
        int code = 100000 + random.nextInt(900000); // 100000 to 999999
        return String.valueOf(code);
    }

    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    /**
     * Verifies the code and activates the account if it matches.
     * Returns true if successful, false otherwise.
     */
    public boolean verifyEmail(String email, String code) {
        Optional<User> userOptional = userRepository.findByEmail(email);
        if (userOptional.isEmpty()) {
            return false;
        }

        User user = userOptional.get();

        // Check that the code matches
        if (user.getVerificationCode() == null || !user.getVerificationCode().equals(code)) {
            return false;
        }

        // Activate the account
        user.setEmailVerified(true);
        user.setStatus("active");
        user.setVerificationCode(null); // Clear the code — it's been used
        userRepository.save(user);

        return true;
    }
    // =============================================================
    // PASSWORD RESET METHODS
    // =============================================================

    /**
     * Generates a reset token, saves it to the user, and returns the reset link.
     * Returns null if no user with that email exists.
     */
    public String createPasswordResetToken(String email) {

        Optional<User> userOptional = userRepository.findByEmail(email);

        if (userOptional.isEmpty()) {
            return null; // Caller decides how to handle "user not found"
        }

        User user = userOptional.get();

        // Generate a secure random token (URL-safe, 32 characters)
        String token = generateResetToken();

        // Save token + expiry (15 minutes from now)
        user.setResetToken(token);
        user.setResetTokenExpiry(java.time.LocalDateTime.now().plusMinutes(15));
        userRepository.save(user);

        // Build the reset link pointing to our own app
        return "http://localhost:8080/reset-password?token=" + token;
    }

    /**
     * Generates a URL-safe random token.
     */
    private String generateResetToken() {
        java.security.SecureRandom random = new java.security.SecureRandom();
        byte[] bytes = new byte[24];
        random.nextBytes(bytes);
        // Base64 URL-safe encoding, no padding
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * Finds a user by reset token, but only if the token hasn't expired.
     */
    public Optional<User> findUserByValidResetToken(String token) {
        Optional<User> userOptional = userRepository.findByResetToken(token);

        if (userOptional.isEmpty()) {
            return Optional.empty();
        }

        User user = userOptional.get();

        // Check expiry
        if (user.getResetTokenExpiry() == null ||
                user.getResetTokenExpiry().isBefore(java.time.LocalDateTime.now())) {
            return Optional.empty(); // Expired
        }

        return Optional.of(user);
    }

    /**
     * Updates the user's password and clears the reset token (single-use).
     */
    public void resetPassword(User user, String newPassword) {
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setResetToken(null);
        user.setResetTokenExpiry(null);
        userRepository.save(user);
    }
}