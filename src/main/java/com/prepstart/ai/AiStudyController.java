package com.prepstart.ai;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

/** Readable job dashboard. Original upload form and routes remain unchanged. */
@Controller
public class AiStudyController {
    private final JdbcTemplate jdbc;
    public AiStudyController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    private record User(int id, boolean admin) { }
    private User viewer(Authentication auth) {
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getName()))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        String email = auth.getName();
        if (auth.getPrincipal() instanceof OAuth2User oauth) {
            String claim = oauth.getAttribute("email");
            if (claim != null && !claim.isBlank()) email = claim;
        }
        List<Integer> ids = jdbc.query("SELECT user_id FROM `User` WHERE email=?",
                (rs, i) -> rs.getInt(1), email);
        if (ids.isEmpty()) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        boolean admin = auth.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        return new User(ids.getFirst(), admin);
    }

    @GetMapping("/ai-study")
    public String dashboard(Authentication auth, Model model,
                            @org.springframework.web.bind.annotation.RequestParam(value="uploaded", required=false) String uploaded) {
        User user = viewer(auth);
        // Include new uploads immediately, even before the background worker creates a job row.
        List<Map<String,Object>> jobs = jdbc.queryForList("""
                SELECT c.content_id, c.module_id, c.title, m.code AS module_code,
                       COALESCE(j.status, 'QUEUED') AS status,
                       j.words_scanned, j.sections_done, j.sections_total, j.generated_files,
                       j.generated_questions, j.generated_flashcards, j.last_error,
                       j.updated_at, j.retry_at
                  FROM Content c
                  JOIN `Module` m ON m.module_id=c.module_id
                  LEFT JOIN AiProcessingJob j ON j.content_id=c.content_id
                 WHERE (?=1 OR c.uploaded_by=?)
                   AND c.file_url LIKE '/uploadcontent/file/%'
                   AND c.file_type IN ('pdf','ppt','pptx','doc','docx','txt','xls','xlsx')
                   AND (c.content_type IS NULL OR c.content_type <> 'AI_GENERATED')
                 ORDER BY c.content_id DESC LIMIT 100
                """, user.admin() ? 1 : 0, user.id());
        model.addAttribute("uploaded", "1".equals(uploaded));
        model.addAttribute("jobs", jobs);
        return "ai-study";
    }

    @PostMapping("/ai-study/retry/{contentId}")
    public String retry(Authentication auth, @PathVariable int contentId, RedirectAttributes flash) {
        User user = viewer(auth);
        int changed = jdbc.update("""
                UPDATE AiProcessingJob j JOIN Content c ON c.content_id=j.content_id
                   SET j.status='PENDING', j.retry_at=NULL, j.last_error=NULL, j.updated_at=NOW(),
                       j.sections_done=0
                 WHERE j.content_id=? AND (?=1 OR c.uploaded_by=?)
                   AND j.status IN ('FAILED','RETRY','WAITING_FOR_KEY','SKIPPED')
                """, contentId, user.admin() ? 1 : 0, user.id());
        flash.addFlashAttribute("message", changed > 0 ? "Queued for processing again." : "That job cannot be retried now.");
        return "redirect:/ai-study";
    }
}
