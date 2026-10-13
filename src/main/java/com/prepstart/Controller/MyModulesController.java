package com.prepstart.controller;

import com.prepstart.Service.EnrolledModulesService;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Map;

/** ONE controller owns the /modules routes. Remove duplicate old mappings. */
@Controller
public class MyModulesController {

    private final EnrolledModulesService modulesService;

    public MyModulesController(EnrolledModulesService modulesService) {
        this.modulesService = modulesService;
    }

    private Map<String, Object> currentStudent(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getName())) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.UNAUTHORIZED, "Please sign in");
        }
        String email = authentication.getName();
        if (authentication.getPrincipal() instanceof OAuth2User oauth) {
            String claim = oauth.getAttribute("email");
            if (claim == null || claim.isBlank()) claim = oauth.getAttribute("preferred_username");
            if (claim != null && !claim.isBlank()) email = claim;
        }
        return modulesService.studentByEmail(email);
    }

    @GetMapping("/modules")
    public String listModules(Authentication auth, Model model) {
        Map<String, Object> student = currentStudent(auth);
        var registered = modulesService.modulesForUser((int) student.get("id"));
        model.addAttribute("student", student);
        model.addAttribute("modules", registered);
        model.addAttribute("moduleCount", registered.size());
        return "MyModules";
    }

    @GetMapping("/modules/id/{moduleId}")
    public String moduleDetails(@PathVariable int moduleId, Authentication auth, Model model) {
        Map<String, Object> student = currentStudent(auth);
        Map<String, Object> module = modulesService.moduleForUser(moduleId, (int) student.get("id"));
        return showDetails(student, module, model);
    }

    // Supports bookmarks created with earlier /modules/NADF511-style links.
    @GetMapping("/modules/{moduleCode}")
    public String moduleByCode(@PathVariable String moduleCode, Authentication auth, Model model) {
        Map<String, Object> student = currentStudent(auth);
        Map<String, Object> module = modulesService.moduleByCodeForUser(moduleCode, (int) student.get("id"));
        return showDetails(student, module, model);
    }

    private String showDetails(Map<String, Object> student, Map<String, Object> module, Model model) {
        var resources = modulesService.materialsForModule((int) module.get("id"));
        model.addAttribute("student", student);
        model.addAttribute("module", module);
        model.addAttribute("pdfs", resources.pdfs());
        model.addAttribute("presentations", resources.presentations());
        model.addAttribute("documents", resources.documents());
        model.addAttribute("flashcards", resources.flashcards());
        model.addAttribute("resourceCount", resources.pdfs().size() + resources.presentations().size()
                + resources.documents().size() + resources.flashcards().size());
        return "ModuleDetails";
    }

    @GetMapping("/modules/materials/{contentId}")
    public String openMaterial(@PathVariable int contentId, Authentication auth) {
        Map<String, Object> student = currentStudent(auth);
        String url = modulesService.approvedContentUrl(contentId, (int) student.get("id"));
        return "redirect:" + url;
    }
}
