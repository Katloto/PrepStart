package com.prepstart.Controller;

import com.prepstart.Service.UserService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/admin")
public class AdminController {

    private final UserService userService;

    public AdminController(UserService userService) {
        this.userService = userService;
    }

    // List all users
    @GetMapping("/users")
    public String listUsers(Model model) {
        model.addAttribute("users", userService.getAllUsers());
        return "admin/users";
    }

    // Show the create-user form
    @GetMapping("/users/create")
    public String showCreateUserForm() {
        return "admin/create-user";
    }

    // Handle the create-user form submission
    @PostMapping("/users/create")
    public String createUser(
            @RequestParam("firstName") String firstName,
            @RequestParam("lastName") String lastName,
            @RequestParam("email") String email,
            @RequestParam(value = "studentNumber", required = false) String studentNumber,
            Model model) {

        try {
            userService.adminCreateUser(firstName, lastName, email, studentNumber);
            model.addAttribute("success",
                    "Account created! Login credentials have been emailed to " + email);
            return "admin/create-user";

        } catch (RuntimeException e) {
            model.addAttribute("error", e.getMessage());
            return "admin/create-user";
        }
    }
}