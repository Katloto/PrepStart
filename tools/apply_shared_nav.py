#!/usr/bin/env python3
"""Patch only authenticated PrepStart templates' navbars.

Usage (from project root): py tools/apply_shared_nav.py
Safe to rerun; original HTMLs saved in .prepstart-nav-backup/ before modification.
Never updates landing/login/signup/verify/recovery/error templates.
"""
from __future__ import annotations

import re
import shutil
from datetime import datetime
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
TEMPLATES = ROOT / "src" / "main" / "resources" / "templates"
STYLESHEET = '<link rel="stylesheet" th:href="@{/css/prepstart-nav.css}" href="/css/prepstart-nav.css">'
PUBLIC = {
    "landing", "index", "login", "register", "registration", "signup", "sign-up",
    "verify", "forgot-password", "reset-password", "error", "403", "404", "500",
    "verify-email", "email-verified", "password-reset", "password-reset-success",
}
KNOWN_AUTHENTICATED = {
    "dashboard", "home", "mymodules", "moduledetails", "quizme", "quizmeplay",
    "quizmeresult", "progress", "upload", "uploadcontent", "upload-content",
}
HEADER = re.compile(r"<header\b[^>]*>.*?</header\s*>", re.I | re.S)
NAV_HINT = re.compile(r"<nav\b|class\s*=\s*['\"][^'\"]*(?:navbar|site-header|main-header)", re.I)
HEAD_CLOSE = re.compile(r"</head\s*>", re.I)
BODY_OPEN = re.compile(r"<body\b[^>]*>", re.I)
HTML_OPEN = re.compile(r"<html\b[^>]*>", re.I)


def active_name(stem: str) -> str:
    s = stem.lower().replace("_", "").replace("-", "")
    if s in {"dashboard", "home"}: return "dashboard"
    if "module" in s: return "modules"
    if "quiz" in s: return "quiz"
    if "progress" in s: return "progress"
    if "upload" in s: return "upload"
    return ""


def migrate(page: Path, backup_root: Path):
    if page.parent.name.lower() == "fragments" or page.stem.lower() in PUBLIC:
        return "public/fragment"
    text = page.read_text(encoding="utf-8-sig")
    if not re.search(r"<html\b", text, re.I):
        return "not a full HTML page"
    is_known = page.stem.lower() in KNOWN_AUTHENTICATED or active_name(page.stem) != ""
    headers = list(HEADER.finditer(text))
    if any("fragments/navbar :: navbar(" in m.group(0) for m in headers):
        return "already current"
    header = next((m for m in headers if NAV_HINT.search(m.group(0))), None)
    if not header and not is_known:
        return "unrecognized page without navbar (review manually)"
    if not header and headers:
        return "page has a different header (review manually)"

    replacement = '<header th:replace="~{fragments/navbar :: navbar(\'' + active_name(page.stem) + '\')}"></header>'
    if header:
        # Even when run again on a page already patched, do not duplicate navbar.
        if 'fragments/navbar :: navbar(' in header.group(0):
            new = text
        else:
            new = text[:header.start()] + replacement + text[header.end():]
    else:
        m = BODY_OPEN.search(text)
        if not m: return "no body tag (review manually)"
        new = text[:m.end()] + '\n' + replacement + text[m.end():]
    if "prepstart-nav.css" not in new:
        m = HEAD_CLOSE.search(new)
        if not m: return "no head close tag (review manually)"
        new = new[:m.start()] + '    ' + STYLESHEET + '\n' + new[m.start():]
    m = HTML_OPEN.search(new)
    if m and 'xmlns:th=' not in m.group(0):
        new = new[:m.end()-1] + ' xmlns:th="http://www.thymeleaf.org">' + new[m.end():]

    # Correct known obsolete browser hyperlinks; do not touch form submissions.
    known_old = [("/my-modules", "/modules"), ("/upload-content", "/uploadcontent"),
                 ("/quiz", "/quiz-me")]
    for old, correct in known_old:
        new = new.replace(f'href="{old}"', f'href="{correct}"')
        new = new.replace(f"href='{old}'", f"href='{correct}'")
        new = new.replace(f'th:href="@{{{old}}}"', f'th:href="@{{{correct}}}"')

    if new == text: return "already current"
    backup = backup_root / page.relative_to(ROOT)
    backup.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(page, backup)
    page.write_text(new, encoding="utf-8")
    return "updated (backup saved)"


def main():
    if not (TEMPLATES / "fragments" / "navbar.html").exists():
        raise SystemExit("Missing fragments/navbar.html; copy the supplied src folder into project root first.")
    if not (ROOT / "src/main/resources/static/css/prepstart-nav.css").exists():
        raise SystemExit("Missing prepstart-nav.css; copy the supplied src folder into project root first.")
    stamp = datetime.now().strftime("%Y%m%d_%H%M%S")
    backup_root = ROOT / ".prepstart-nav-backup" / stamp
    print("PrepStart Shared Navigation Migration")
    print("Project:", ROOT)
    pages = sorted(TEMPLATES.glob("*.html"))
    edited = 0
    for page in pages:
        status = migrate(page, backup_root)
        print(" ", page.name, "=>", status)
        edited += status.startswith("updated")
    print(f"Done. Updated {edited} template(s).")
    if edited:
        print("Original files saved to:", backup_root)
    print("Important: Verify actual Upload Content route in your controller. Navbar currently uses /uploadcontent.")


if __name__ == "__main__":
    main()
