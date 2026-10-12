package com.prepstart.controller;

import com.prepstart.Service.ModuleCatalogService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.security.Principal;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import java.util.Map;

@Controller
public class ModuleController {
    private final ModuleCatalogService catalogue;

    public ModuleController(ModuleCatalogService catalogue) { this.catalogue = catalogue; }

    @GetMapping("/modules")
    public String showModules(Model model, Principal principal) {
        if (principal == null) return "redirect:/login";
        Map<String,Object> student = catalogue.studentFromLogin(emailOf(principal));
        model.addAttribute("student",student);
        model.addAttribute("modules",catalogue.enrolledModules((int)student.get("id")));
        return "MyModules";
    }

    // Preferred URL uses primary key: unambiguous if several programmes share a module code.
    @GetMapping("/modules/id/{moduleId}")
    public String byId(@PathVariable int moduleId, Model model, Principal principal) {
        if(principal==null) return "redirect:/login";
        Map<String,Object> student = catalogue.studentFromLogin(emailOf(principal));
        Map<String,Object> module = catalogue.enrolledModule(moduleId,(int)student.get("id"));
        return details(model,student,module);
    }

    // Backward compatibility with the existing /modules/NADF511 links.
    @GetMapping("/modules/{code}")
    public String byCode(@PathVariable String code, Model model, Principal principal) {
        if(principal==null) return "redirect:/login";
        Map<String,Object> student = catalogue.studentFromLogin(emailOf(principal));
        Map<String,Object> module = catalogue.enrolledModuleByCode(code,(int)student.get("id"));
        return details(model,student,module);
    }

    // OAuth2 principal names can be opaque provider IDs; prefer the verified email claim.
    private String emailOf(Principal principal) {
        if (principal instanceof Authentication auth && auth.getPrincipal() instanceof OAuth2User oauth) {
            String email = oauth.getAttribute("email");
            if (email != null && !email.isBlank()) return email;
        }
        return principal.getName();
    }

    private String details(Model model, Map<String,Object> student, Map<String,Object> module) {
        int id=(int)module.get("id");
        model.addAttribute("student",student);
        model.addAttribute("module",module);
        model.addAttribute("documents",catalogue.documents(id));
        model.addAttribute("slides",catalogue.slides(id));
        model.addAttribute("flashcards",catalogue.flashcards(id));
        return "ModuleDetails";
    }
}
