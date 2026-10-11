package com.prepstart.Controller;

import com.prepstart.Repository.UserRepository;
import com.prepstart.model.User;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;   // NEW

import java.util.Optional;

@Controller
public class PageController {

    private final UserRepository userRepository;

    public PageController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping("/login")
    public String showLoginPage() {
        return "login";
    }

    @GetMapping("/verify")
    public String showVerifyPage(@RequestParam(value = "email", required = false) String email,
                                 Model model) {
        if (email != null) {
            model.addAttribute("email", email);   // NEW
        }
        return "verify";
    }

    @GetMapping("/forgot-password")
    public String showForgotPasswordPage() {
        return "forgot-password";
    }


    @GetMapping("/")
    public String showHomePage(Authentication authentication, Model model) {
        String email = authentication.getName();
        Optional<User> userOptional = userRepository.findByEmail(email);
        if (userOptional.isPresent()) {
            model.addAttribute("user", userOptional.get());
        }
        return "home";
    }
}