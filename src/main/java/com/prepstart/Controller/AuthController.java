package com.prepstart.Controller;

import com.prepstart.Service.UserService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/register")
    public String showRegisterPage() {
        return "register";
    }

    @PostMapping("/register")
    public String registerUser(
            @RequestParam("fullName") String fullName,
            @RequestParam("username") String username,
            @RequestParam("email") String email,
            @RequestParam("password") String password,
            @RequestParam("confirmPassword") String confirmPassword,
            Model model) {

        if (!password.equals(confirmPassword)) {
            model.addAttribute("error", "Passwords do not match!");
            return "register";
        }

        try {
            String[] names = fullName.split(" ", 2);
            String firstName = names[0];
            String lastName = (names.length > 1) ? names[1] : "";

            userService.registerUser(firstName, lastName, username, email, password);

            // Redirect to the verify page with the email in the URL
            return "redirect:/verify?email=" + email;

        } catch (RuntimeException e) {
            model.addAttribute("error", e.getMessage());
            return "register";
        }
    }

    // ===== NEW: Handle verification code submission =====
    @PostMapping("/verify")
    public String verifyCode(
            @RequestParam("email") String email,
            @RequestParam(value = "code", required = false) String code,
            Model model) {

        // Combine the 6 individual digit inputs into one string
        // (They come in as separate params: digit1, digit2, etc. — but we'll send them as "code" directly)

        if (code == null || code.length() != 6) {
            model.addAttribute("error", "Please enter all 6 digits.");
            model.addAttribute("email", email);
            return "verify";
        }

        boolean verified = userService.verifyEmail(email, code);

        if (verified) {
            return "redirect:/login?verified=true";
        } else {
            model.addAttribute("error", "Invalid or expired verification code.");
            model.addAttribute("email", email);
            return "verify";
        }
    }
}