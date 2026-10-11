package com.prepstart.Controller;

import com.prepstart.Service.EmailService;
import com.prepstart.Service.UserService;
import com.prepstart.model.User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Optional;

@Controller
public class ForgotPasswordController {

    private final UserService userService;
    private final EmailService emailService;

    public ForgotPasswordController(UserService userService, EmailService emailService) {
        this.userService = userService;
        this.emailService = emailService;
    }

    // ===== STEP 1: Request a reset link =====
    @PostMapping("/forgot-password")
    public String handleForgotPassword(
            @RequestParam("email") String email,
            Model model) {

        String resetLink = userService.createPasswordResetToken(email);

        if (resetLink == null) {
            // User-friendly message: email not found
            model.addAttribute("error", "No account found with that email address.");
            return "forgot-password";
        }

        // Send the email
        emailService.sendPasswordResetEmail(email, resetLink);

        // Show success message
        model.addAttribute("success",
                "We've sent a password reset link to " + email + ". Please check your inbox.");
        return "forgot-password";
    }

    // STEP 2: Show the reset password form
    @GetMapping("/reset-password")
    public String showResetPasswordPage(
            @RequestParam(value = "token", required = false) String token,
            Model model) {

        if (token == null || token.isEmpty()) {
            model.addAttribute("error", "Invalid reset link.");
            return "set-password";
        }

        Optional<User> userOptional = userService.findUserByValidResetToken(token);

        if (userOptional.isEmpty()) {
            model.addAttribute("error", "This reset link is invalid or has expired. Please request a new one.");
            return "set-password";
        }

        // Pass the token to the form so it can be submitted with the new password
        model.addAttribute("token", token);
        return "set-password";
    }

    // STEP 3: Submit new password
    @PostMapping("/reset-password")
    public String handleResetPassword(
            @RequestParam("token") String token,
            @RequestParam("password") String password,
            @RequestParam("confirmPassword") String confirmPassword,
            Model model) {

        // Validate password match
        if (!password.equals(confirmPassword)) {
            model.addAttribute("error", "Passwords do not match.");
            model.addAttribute("token", token);
            return "set-password";
        }

        // Find user by valid token
        Optional<User> userOptional = userService.findUserByValidResetToken(token);

        if (userOptional.isEmpty()) {
            model.addAttribute("error", "This reset link is invalid or has expired.");
            return "set-password";
        }

        // Update password
        userService.resetPassword(userOptional.get(), password);

        // Redirect to log in with success message
        return "redirect:/login?reset=true";
    }
}