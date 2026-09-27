package com.prepstart.Service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromEmail;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    // ===== EXISTING: Verification code email =====
    public void sendVerificationEmail(String toEmail, String code) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(toEmail);
            message.setSubject("PrepStart - Verify Your Account");

            String body = "Welcome to PrepStart!\n\n"
                    + "Thank you for registering. Your verification code is:\n\n"
                    + "        " + code + "\n\n"
                    + "Enter this code on the verification page to activate your account.\n\n"
                    + "If you did not register for PrepStart, please ignore this email.\n\n"
                    + "— The PrepStart Team";

            message.setText(body);
            mailSender.send(message);

            System.out.println("✅ Verification email sent to: " + toEmail);
        } catch (Exception e) {
            System.err.println("❌ Failed to send verification email: " + e.getMessage());
        }
    }

    // ===== NEW: Password reset email =====
    public void sendPasswordResetEmail(String toEmail, String resetLink) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(toEmail);
            message.setSubject("PrepStart - Reset Your Password");

            String body = "Hello,\n\n"
                    + "We received a request to reset your PrepStart password.\n\n"
                    + "Click the link below to set a new password:\n\n"
                    + resetLink + "\n\n"
                    + "This link will expire in 15 minutes for security reasons.\n\n"
                    + "If you did not request a password reset, you can safely ignore this email — "
                    + "your password will remain unchanged.\n\n"
                    + "— The PrepStart Team";

            message.setText(body);
            mailSender.send(message);

            System.out.println("✅ Password reset email sent to: " + toEmail);
            System.out.println("   Link: " + resetLink);

        } catch (Exception e) {
            System.err.println("❌ Failed to send reset email: " + e.getMessage());
        }
    }
}