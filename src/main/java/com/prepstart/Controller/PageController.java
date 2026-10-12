
package com.prepstart.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class PageController {

    // LOGIN PAGE
    @GetMapping("/login")
    public String showLoginPage() {
        return "login";
    }

    // EMAIL VERIFICATION PAGE
    @GetMapping("/verify")
    public String showVerifyPage(
            @RequestParam(value = "email", required = false) String email,
            Model model) {

        if (email != null) {
            model.addAttribute("email", email);
        }

        return "verify";
    }

    // FORGOT PASSWORD PAGE
    @GetMapping("/forgot-password")
    public String showForgotPasswordPage() {
        return "forgot-password";
    }

    // HOME ROUTE - REDIRECT TO DASHBOARD
    @GetMapping("/home")
    public String showHomePage() {
        return "redirect:/dashboard";
    }
}
