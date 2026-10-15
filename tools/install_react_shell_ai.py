#!/usr/bin/env python3
"""Apply safe, local PrepStart changes. Run from the project with `py tools/install_react_shell_ai.py`.

Keeps originals under .prepstart-shell-backup. Does not modify login security automatically.
"""
from __future__ import annotations
from pathlib import Path
from datetime import datetime
import re
import shutil
import sys

PATCHROOT = Path(__file__).resolve().parents[1]
ROOT = Path(sys.argv[1]).resolve() if len(sys.argv) > 1 else Path.cwd().resolve()
SRC = ROOT / 'src/main/resources'
TMPL = SRC / 'templates'
UPLOAD = ROOT / 'src/main/java/com/prepstart/controller/UploadContentController.java'
BACKUP = ROOT / '.prepstart-shell-backup' / datetime.now().strftime('%Y%m%d_%H%M%S')
CSS = '    <link rel="stylesheet" th:href="@{/css/prepstart-nav.css}" href="/css/prepstart-nav.css">\n'
JS = '    <script th:src="@{/js/prepstart-shell.js}" src="/js/prepstart-shell.js" defer></script>\n'
NAV = lambda current: '<header th:replace="~{fragments/navbar :: navbar(\'' + current + '\')}"></header>'
FOOT = '<footer th:replace="~{fragments/footer :: footer}"></footer>'
PUBLIC = {'index','landing','login','register','registration','signup','sign-up','verify','forgot-password','reset-password','error','403','404','500','verify-email','email-verified','password-reset','password-reset-success'}
# List deliberately limits automatic edits to known authenticated templates.
ACTIVE = {
    'home':'home', 'dashboard':'dashboard','mymodules':'modules',
    'moduledetails':'modules','quizme':'quiz','quizmeplay':'quiz',
    'quizmeresult':'quiz','quizmeresults':'quiz','progress':'progress',
    'uploadcontent':'upload','upload':'upload','ai-study':'upload',
}


def save(path: Path, content: str):
    if path.read_text(encoding='utf-8-sig') == content:
        return False
    dest = BACKUP / path.relative_to(ROOT)
    dest.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(path, dest)
    path.write_text(content, encoding='utf-8')
    return True


def update_page(path: Path):
    name = path.stem.lower()
    if name in PUBLIC or name not in ACTIVE:
        return 'skipped (not a known authenticated page)'
    t = path.read_text(encoding='utf-8-sig')
    if '<html' not in t.lower():
        return 'skipped (not a full HTML page)'
    has_shared = bool(re.search(r'fragments/navbar\s*::\s*navbar\(', t))
    if not has_shared:
        # Replace the existing header that contains the site's navigation.
        m = re.search(r'<header\b[^>]*>.*?</header\s*>', t, re.I | re.S)
        if m and (re.search(r'<nav\b', m.group(0), re.I) or re.search(r'class\s*=\s*["\'][^"\']*navbar', m.group(0), re.I)):
            t = t[:m.start()] + NAV(ACTIVE[name]) + t[m.end():]
        else:
            # No matching header: don't risk removing unrelated page heading/branding.
            if re.search(r'<nav\b', t, re.I):
                return 'REVIEW: unusual navbar markup; manual replacement needed'
            m = re.search(r'<body\b[^>]*>', t, re.I)
            if not m:
                return 'REVIEW: no body tag'
            t = t[:m.end()] + '\n' + NAV(ACTIVE[name]) + t[m.end():]
    # Replace an existing footer, or insert ours before </body>.
    if 'fragments/footer :: footer' not in t:
        t, n = re.subn(r'<footer\b[^>]*>.*?</footer\s*>', FOOT, t, count=1, flags=re.I | re.S)
        if n == 0:
            t = re.sub(r'</body\s*>', FOOT + '\n</body>', t, count=1, flags=re.I)
    if 'prepstart-nav.css' not in t:
        t = re.sub(r'</head\s*>', CSS + '</head>', t, count=1, flags=re.I)
    if 'prepstart-shell.js' not in t:
        t = re.sub(r'</body\s*>', JS + '</body>', t, count=1, flags=re.I)
    if 'xmlns:th=' not in t:
        t = re.sub(r'<html\b([^>]*)>', r'<html\1 xmlns:th="http://www.thymeleaf.org">', t, count=1, flags=re.I)
    return 'updated (backup saved)' if save(path,t) else 'already updated'


def fix_upload_redirect():
    if not UPLOAD.exists():
        return 'REVIEW: upload controller not at expected path; no edit made'
    t = UPLOAD.read_text(encoding='utf-8-sig')
    old = 'return "redirect:/uploadcontent?uploaded=1";'
    new = 'return "redirect:/ai-study?uploaded=1";'
    if new in t:
        return 'already pointing to AI Study'
    if t.count(old) != 1:
        return 'REVIEW: expected success redirect not found exactly; no edit made'
    return 'updated (backup saved)' if save(UPLOAD,t.replace(old,new,1)) else 'no change'


def fix_ai_study_controller():
    p = ROOT / 'src/main/java/com/prepstart/ai/AiStudyController.java'
    if not p.exists():
        return 'REVIEW: AiStudyController not installed; install AI feature first'
    t = p.read_text(encoding='utf-8-sig')
    if 'LEFT JOIN AiProcessingJob j ON j.content_id=c.content_id' in t:
        return 'already displays queued uploads'
    m = re.search(r'(?P<block>        List<Map<String,Object>> jobs = jdbc\.queryForList\(""".*?""", user\.admin\(\) \? 1 : 0, user\.id\(\)\);)', t, re.S)
    if not m or 'FROM AiProcessingJob j' not in m.group('block'):
        return 'REVIEW: custom AI controller differs; no changes made'
    newblock = '''        // Include new uploads immediately, even before the background worker creates a job row.
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
        model.addAttribute("uploaded", "1".equals(uploaded));'''
    if 'String uploaded)' not in t:
        oldsig = 'public String dashboard(Authentication auth, Model model) {'
        newsig = 'public String dashboard(Authentication auth, Model model,\n                            @org.springframework.web.bind.annotation.RequestParam(value="uploaded", required=false) String uploaded) {'
        if oldsig not in t:
            return 'REVIEW: dashboard method signature differs; no changes made'
        t = t.replace(oldsig,newsig,1)
    t = t.replace(m.group('block'),newblock,1)
    return 'updated (backup saved)' if save(p,t) else 'no change'


def check_home_login():
    java = ROOT/'src/main/java'
    files = list(java.rglob('HomeController.java')) + list(java.rglob('PageController.java')) + list(java.rglob('SecurityConfig.java'))
    print('\nHOME/LOGIN ROUTES: inspect these source locations before changing anything:')
    if not files:
        print('  No HomeController.java, PageController.java or SecurityConfig.java found.')
    for p in files:
        print(' ', p.relative_to(ROOT))
        for lineno, line in enumerate(p.read_text(encoding='utf-8-sig').splitlines(),1):
            if any(x in line for x in ('/home','/dashboard','defaultSuccessUrl','successHandler','@GetMapping("/"')):
                print(f'      {lineno}: {line.strip()[:160]}')
    print('  Update the login success handlers to /home, make HomeController render home,')
    print('  and remove any PageController /home -> /dashboard redirect. Avoid duplicate GET /home.')


def copy_payload():
    for rel in [
        'src/main/resources/templates/fragments/navbar.html',
        'src/main/resources/templates/fragments/footer.html',
        'src/main/resources/templates/ai-study.html',
        'src/main/resources/static/css/prepstart-nav.css',
        'src/main/resources/static/js/prepstart-shell.js',
        'src/main/java/com/prepstart/ai/NavbarIdentityAdvice.java',
    ]:
        source = PATCHROOT/rel
        target = ROOT/rel
        if not source.exists():
            print('  ERROR: missing package file', rel)
            sys.exit(1)
        target.parent.mkdir(parents=True, exist_ok=True)
        if target.exists():
            if target.read_bytes() == source.read_bytes():
                print(' ',rel,': already installed')
                continue
            saved = BACKUP/rel
            saved.parent.mkdir(parents=True,exist_ok=True)
            shutil.copy2(target,saved)
        shutil.copy2(source,target)
        print(' ',rel,': copied (existing file backed up if present)')


def main():
    if not TMPL.exists():
        print('ERROR: Put this installer into the PrepStart project root/tools first.')
        sys.exit(1)
    print('Installing shell files into', ROOT)
    copy_payload()
    if not (SRC/'static/images/logo.png').exists():
        print('WARNING: Missing src/main/resources/static/images/logo.png! Copy your actual logo there.')
    print('Updating existing authenticated templates (with backups):')
    for page in sorted(TMPL.glob('*.html')):
        if page.stem.lower() in ACTIVE:
            print(' ',page.name,':',update_page(page))
    print('Upload success redirect:',fix_upload_redirect())
    print('AI dashboard queries:',fix_ai_study_controller())
    check_home_login()
    print('\nDONE. Existing code backed up under', BACKUP if BACKUP.exists() else '(nothing needed backups)')
    print('Run: .\\mvnw.cmd clean package -DskipTests')

if __name__ == '__main__':
    main()
