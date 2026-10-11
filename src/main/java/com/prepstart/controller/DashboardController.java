package com.prepstart.controller;

import com.prepstart.model.Content;
import com.prepstart.model.Enrollment;
import com.prepstart.model.User;
import com.prepstart.repository.ContentRepository;
import com.prepstart.repository.EnrollmentRepository;
import com.prepstart.repository.UserRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

@Controller
public class DashboardController {

    private final UserRepository userRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final ContentRepository contentRepository;

    public DashboardController(UserRepository userRepository,
                               EnrollmentRepository enrollmentRepository,
                               ContentRepository contentRepository) {
        this.userRepository = userRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.contentRepository = contentRepository;
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model) {

        User user = userRepository.findAll()
                .stream()
                .findFirst()
                .orElse(new User());

        model.addAttribute("user", user);

        if (user != null) {

            List<Enrollment> enrollments =
                    enrollmentRepository.findByUserId(user.getUserId());

            model.addAttribute("courseCount", enrollments.size());

            long studyMaterialCount = contentRepository.count();

            model.addAttribute("studyMaterialCount", studyMaterialCount);

        } else {

            model.addAttribute("courseCount", 0);
            model.addAttribute("studyMaterialCount", 0);

        }

        return "dashboard";
    }
}