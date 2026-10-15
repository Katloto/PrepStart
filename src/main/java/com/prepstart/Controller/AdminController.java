package com.prepstart.Controller;

import com.prepstart.Service.AdminEnrollmentService;
import com.prepstart.Service.UserService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/admin")
public class AdminController {
    private final UserService userService;
    private final AdminEnrollmentService enrollmentService;

    public AdminController(UserService userService, AdminEnrollmentService enrollmentService) {
        this.userService = userService;
        this.enrollmentService = enrollmentService;
    }

    @GetMapping({"", "/"})
    public String index() {
        return "redirect:/admin/users";
    }

    @GetMapping("/users")
    public String listUsers(Model model) {
        List<Map<String, Object>> users = enrollmentService.listUsers();
        long students = users.stream().filter(u -> ("student".equalsIgnoreCase(String.valueOf(u.get("roleName"))) || "ROLE_STUDENT".equalsIgnoreCase(String.valueOf(u.get("roleName"))))).count();
        long admins = users.stream().filter(u -> "admin".equalsIgnoreCase(String.valueOf(u.get("roleName")))).count();
        long assigned = users.stream().filter(u -> !"Not assigned".equals(u.get("universityName"))).count();
        model.addAttribute("users", users);
        model.addAttribute("studentCount", students);
        model.addAttribute("adminCount", admins);
        model.addAttribute("assignedCount", assigned);
        return "admin/users";
    }

    @GetMapping("/users/create")
    public String showCreateUserForm() {
        return "admin/create-user";
    }

    @PostMapping("/users/create")
    public String createUser(@RequestParam("firstName") String firstName,
                             @RequestParam("lastName") String lastName,
                             @RequestParam("email") String email,
                             @RequestParam(value = "studentNumber", required = false) String studentNumber,
                             Model model) {
        try {
            userService.adminCreateUser(firstName, lastName, email, studentNumber);
            model.addAttribute("success", "Account created for " + email + ".");
        } catch (RuntimeException ex) {
            model.addAttribute("error", ex.getMessage());
        }
        return "admin/create-user";
    }

    @GetMapping("/users/{userId}/enrollments")
    public String studentEnrollments(@PathVariable long userId, Model model, RedirectAttributes redirect) {
        try {
            model.addAttribute("student", enrollmentService.student(userId));
            model.addAttribute("universities", enrollmentService.universities());
            model.addAttribute("courses", enrollmentService.availableCourses(userId));
            model.addAttribute("modules", enrollmentService.availableModules(userId));
            model.addAttribute("enrolledCourses", enrollmentService.enrolledCourses(userId));
            model.addAttribute("enrolledModules", enrollmentService.enrolledModules(userId));
            return "admin/student-enrollments";
        } catch (IllegalArgumentException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
            return "redirect:/admin/users";
        }
    }

    @PostMapping("/users/{userId}/university")
    public String assignUniversity(@PathVariable long userId, @RequestParam long universityId, RedirectAttributes redirect) {
        return perform(userId, redirect, () -> {
            enrollmentService.assignUniversity(userId, universityId);
            return "University assigned successfully.";
        });
    }

    @PostMapping("/users/{userId}/courses")
    public String enrollCourse(@PathVariable long userId, @RequestParam long courseId, RedirectAttributes redirect) {
        return perform(userId, redirect, () -> {
            int modulesAdded = enrollmentService.enrollCourse(userId, courseId);
            return modulesAdded > 0
                    ? "Course enrollment complete: " + modulesAdded + " module(s) added."
                    : "Student is already enrolled in every available module of this course.";
        });
    }

    @PostMapping("/users/{userId}/modules")
    public String enrollModule(@PathVariable long userId, @RequestParam long moduleId, RedirectAttributes redirect) {
        return perform(userId, redirect, () -> enrollmentService.enrollModule(userId, moduleId)
                ? "Student enrolled in the module. Its course now appears automatically."
                : "Student is already enrolled in that module.");
    }

    @PostMapping("/users/{userId}/modules/{moduleId}/remove")
    public String removeModule(@PathVariable long userId, @PathVariable long moduleId, RedirectAttributes redirect) {
        return perform(userId, redirect, () -> enrollmentService.removeModule(userId, moduleId)
                ? "Module enrollment removed. Study history was preserved."
                : "That module enrollment was already absent.");
    }

    @PostMapping("/users/{userId}/courses/{courseId}/remove")
    public String removeCourse(@PathVariable long userId, @PathVariable long courseId, RedirectAttributes redirect) {
        return perform(userId, redirect, () -> {
            int removed = enrollmentService.removeCourse(userId, courseId);
            return removed > 0
                    ? "Course removed: " + removed + " module enrollment(s) deleted. Study history was preserved."
                    : "No module enrollments were found for that course.";
        });
    }

    private String perform(long userId, RedirectAttributes redirect, AdminOperation action) {
        try {
            redirect.addFlashAttribute("success", action.run());
        } catch (IllegalArgumentException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/users/" + userId + "/enrollments";
    }

    @FunctionalInterface
    private interface AdminOperation {
        String run();
    }
}
