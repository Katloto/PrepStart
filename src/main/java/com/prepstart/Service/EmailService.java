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

    // PASSWORD RESET EMAIL (used by ForgotPasswordController)
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

    // WELCOME EMAIL WITH LOGIN CREDENTIALS
    // (used when admin creates a new account)
    public void sendCredentialsEmail(String toEmail, String firstName,
                                     String loginEmail, String plainPassword) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(toEmail);
            message.setSubject("Your PrepStart Account Credentials");

            String body = "Hi " + firstName + ",\n\n"
                    + "Your PrepStart account has been created by the administrator.\n\n"
                    + "Here are your login credentials:\n\n"
                    + "   Email:    " + loginEmail + "\n"
                    + "   Password: " + plainPassword + "\n\n"
                    + "You can log in here:\n"
                    + "http://localhost:8080/login\n\n"
                    + "For security, we recommend you change your password after your first login "
                    + "using the 'Forgot Password' link on the login page.\n\n"
                    + "— The PrepStart Team";

            message.setText(body);
            mailSender.send(message);

            System.out.println(" Credentials email sent to: " + toEmail);

        } catch (Exception e) {
            System.err.println(" Failed to send credentials email: " + e.getMessage());
        }
    }
}