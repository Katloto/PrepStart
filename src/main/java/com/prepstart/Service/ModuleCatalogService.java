package com.prepstart.Service;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

/** Uses the existing User / Enrollment / Module / Course / Content schema, not duplicate entities. */
@Service
public class ModuleCatalogService {
    private final JdbcTemplate jdbc;

    public ModuleCatalogService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Map<String, Object> studentFromLogin(String login) {
        if (login == null || login.isBlank() || "anonymousUser".equals(login)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Please sign in");
        }
        try {
            return jdbc.queryForObject("""
                    SELECT user_id AS id, first_name AS firstName, last_name AS lastName,
                           email AS email FROM `User`
                    WHERE email = ? AND status = 'active'
                    """, (rs, i) -> {
                Map<String,Object> s = new HashMap<>();
                s.put("id", rs.getInt("id"));
                s.put("firstName", rs.getString("firstName"));
                s.put("lastName", rs.getString("lastName"));
                s.put("email", rs.getString("email"));
                String f = rs.getString("firstName");
                String l = rs.getString("lastName");
                s.put("initials", (f.isEmpty()?"":f.substring(0,1)) + (l.isEmpty()?"":l.substring(0,1)));
                return s;
            }, login);
        } catch (EmptyResultDataAccessException ex) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Your login is not linked to an active PrepStart user");
        }
    }

    /** Only enrolled modules, never the full catalogue. DISTINCT handles duplicated enrolment rows. */
    public List<Map<String, Object>> enrolledModules(int userId) {
        return jdbc.query("""
                SELECT DISTINCT m.module_id, m.code, m.name, m.description,
                       m.academic_year, c.name AS course_name, c.code AS course_code,
                       (SELECT COUNT(*) FROM Content ct WHERE ct.module_id=m.module_id) AS file_count
                  FROM Enrollment en
                  JOIN `Module` m ON m.module_id = en.module_id
                  JOIN Course c ON c.course_id = m.course_id
                 WHERE en.user_id=?
                 ORDER BY m.name, m.code
                """, (rs, i) -> moduleMap(rs), userId);
    }

    /** The ID plus enrolment condition prevents students viewing another student's modules by guessing URLs. */
    public Map<String, Object> enrolledModule(int moduleId, int userId) {
        List<Map<String, Object>> rows = jdbc.query("""
                SELECT m.module_id,m.code,m.name,m.description,m.academic_year,
                       c.name AS course_name,c.code AS course_code,
                       (SELECT COUNT(*) FROM Content ct WHERE ct.module_id=m.module_id) AS file_count
                FROM `Module` m
                JOIN Course c ON c.course_id=m.course_id
                WHERE m.module_id=? AND EXISTS
                   (SELECT 1 FROM Enrollment en WHERE en.module_id=m.module_id AND en.user_id=?)
                """, (rs,i) -> moduleMap(rs), moduleId, userId);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Module not found in your enrolments");
        return rows.getFirst();
    }

    public Map<String, Object> enrolledModuleByCode(String code, int userId) {
        List<Map<String,Object>> rows = jdbc.query("""
                SELECT m.module_id,m.code,m.name,m.description,m.academic_year,
                       c.name AS course_name,c.code AS course_code,
                       (SELECT COUNT(*) FROM Content ct WHERE ct.module_id=m.module_id) AS file_count
                  FROM `Module` m JOIN Course c ON c.course_id=m.course_id
                 WHERE m.code=? AND EXISTS
                   (SELECT 1 FROM Enrollment en WHERE en.module_id=m.module_id AND en.user_id=?)
                 ORDER BY m.module_id LIMIT 1
                """,(rs,i)->moduleMap(rs),code,userId);
        if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Module not found in your enrolments");
        return rows.getFirst();
    }

    private Map<String,Object> moduleMap(ResultSet rs) throws SQLException {
        Map<String,Object> m = new HashMap<>();
        m.put("id",rs.getInt("module_id"));
        m.put("code",rs.getString("code"));
        m.put("name",rs.getString("name"));
        m.put("description",rs.getString("description"));
        m.put("academicYear",rs.getString("academic_year"));
        m.put("courseName",rs.getString("course_name"));
        m.put("courseCode",rs.getString("course_code"));
        m.put("fileCount",rs.getInt("file_count"));
        return m;
    }

    /** Uploaded documents belong to exactly the selected module. Slide-type uploads go to Slides instead. */
    public List<Map<String,Object>> documents(int moduleId) {
        return jdbc.query("""
                SELECT ct.content_id,ct.title,ct.description,ct.content_type,ct.file_type,ct.file_url,
                       ct.created_at,COALESCE(CONCAT(u.first_name,' ',u.last_name),'PrepStart') AS author
                FROM Content ct LEFT JOIN `User` u ON u.user_id=COALESCE(ct.uploaded_by,ct.user_id)
                WHERE ct.module_id=? AND NOT (
                    UPPER(COALESCE(ct.content_type,'')) IN ('SLIDE','SLIDES','PRESENTATION','PPT','PPTX')
                    OR LOWER(COALESCE(ct.file_type,'')) IN ('ppt','pptx','.ppt','.pptx')
                ) ORDER BY ct.created_at DESC, ct.content_id DESC
                """, (rs,i)->contentMap(rs),moduleId);
    }

    /** Includes direct presentation uploads and generated slides stored in the existing Slide table. */
    public List<Map<String,Object>> slides(int moduleId) {
        List<Map<String,Object>> result = new ArrayList<>(jdbc.query("""
                SELECT ct.content_id,ct.title,ct.description,ct.content_type,ct.file_type,ct.file_url,
                       ct.created_at,COALESCE(CONCAT(u.first_name,' ',u.last_name),'PrepStart') AS author
                FROM Content ct LEFT JOIN `User` u ON u.user_id=COALESCE(ct.uploaded_by,ct.user_id)
                WHERE ct.module_id=? AND (
                    UPPER(COALESCE(ct.content_type,'')) IN ('SLIDE','SLIDES','PRESENTATION','PPT','PPTX')
                    OR LOWER(COALESCE(ct.file_type,'')) IN ('ppt','pptx','.ppt','.pptx')
                ) ORDER BY ct.created_at DESC
                """,(rs,i)->contentMap(rs),moduleId));
        List<Map<String,Object>> processed = jdbc.query("""
                SELECT sl.slide_id,sl.title,sl.description,
                       COALESCE(CONCAT(u.first_name,' ',u.last_name),'PrepStart') AS author
                FROM Slide sl
                JOIN ProcessedContent pc ON pc.ai_content_id=sl.ai_content_id
                LEFT JOIN Content ct ON ct.content_id=pc.content_id
                LEFT JOIN `User` u ON u.user_id=sl.user_id
                WHERE COALESCE(pc.module_id,ct.module_id)=?
                ORDER BY sl.slide_id DESC
                """,(rs,i)->{
                    Map<String,Object> m=new HashMap<>();
                    m.put("id",rs.getInt("slide_id"));
                    m.put("title",rs.getString("title") == null ? "Generated slides" : rs.getString("title"));
                    m.put("description",rs.getString("description"));
                    m.put("author",rs.getString("author"));
                    m.put("url",""); // Slide table has NO file_url; display content, don't invent link.
                    m.put("kind","Generated slides");
                    return m;
                },moduleId);
        result.addAll(processed);
        return result;
    }

    public List<Map<String,Object>> flashcards(int moduleId) {
        return jdbc.query("""
                SELECT fc.flashcard_id,fc.question,fc.answer
                FROM Flashcards fc
                JOIN ProcessedContent pc ON pc.ai_content_id=fc.ai_content_id
                LEFT JOIN Content ct ON ct.content_id=pc.content_id
                WHERE COALESCE(pc.module_id,ct.module_id)=?
                ORDER BY fc.flashcard_id DESC
                """,(rs,i)->{
                    Map<String,Object> m=new HashMap<>();
                    m.put("id",rs.getInt("flashcard_id"));
                    m.put("question",rs.getString("question"));
                    m.put("answer",rs.getString("answer"));
                    return m;
                },moduleId);
    }

    private Map<String,Object> contentMap(ResultSet rs) throws SQLException {
        Map<String,Object> m=new HashMap<>();
        m.put("id",rs.getInt("content_id"));
        m.put("title",rs.getString("title"));
        m.put("description",rs.getString("description"));
        m.put("author",rs.getString("author"));
        String type=rs.getString("file_type");
        m.put("kind",type==null || type.isBlank() ? (rs.getString("content_type")==null?"Document":rs.getString("content_type")) : type.toUpperCase(Locale.ROOT));
        String url=rs.getString("file_url");
        // Prevent javascript: and protocol-relative links from stored values.
        m.put("url",url!=null && (url.startsWith("https://") || url.matches("^/(?!/)[^\\s]*$")) ? url : "");
        return m;
    }
}
