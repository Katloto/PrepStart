package com.prepstart.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    @GetMapping("/")
    public String landing() {
        return "landing";
    }

    // Page after a successful form or OAuth2 login.
    @GetMapping("/home")
    public String showAuthenticatedHome() {
        return "home";
    }
}