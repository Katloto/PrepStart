#!/usr/bin/env python3
r"""One-run PrepStart login -> Home update.

Reads and safely updates existing FULL Java files, preserving unrelated logic.
No GitHub access, IntelliJ, Java package replacement, or manual line edits needed.
Usage in PowerShell (from Capstone, directory with pom.xml):
    py .\tools\fix_home_login.py .
Or from Downloads:
    py "$env:USERPROFILE\Downloads\fix_home_login.py" .
"""
from __future__ import annotations

import re
import sys
import shutil
from datetime import datetime
from pathlib import Path


def fail(message: str) -> None:
    print(f'ERROR: {message}')
    print('Nothing has been changed unless a SUCCESS report is printed.')
    sys.exit(1)


def normalize(path: Path) -> str:
    return path.read_text(encoding='utf-8-sig')


def remove_old_home_redirect(java: str) -> tuple[str, bool]:
    """Remove only one PageController @GetMapping('/home') method that only redirects to dashboard."""
    annotation = re.compile(r'(?m)^[ \t]*@GetMapping\s*\(\s*"/home"\s*\)\s*\r?\n')
    matches = list(annotation.finditer(java))
    if not matches:
        return java, False
    if len(matches) != 1:
        fail('PageController has more than one /home annotation. No automatic change.')
    start = matches[0].start()
    from_annot = matches[0].end()
    next_brace = java.find('{', from_annot)
    if next_brace == -1:
        fail('Could not identify PageController /home method body.')
    before_brace = java[from_annot:next_brace]
    # Require an ordinary handler method. Don't strip custom annotations or non-handler code.
    if not re.search(r'\bpublic\s+String\s+\w+\s*\([^;{}]*\)\s*$', before_brace, re.S):
        fail('PageController /home handler is more complex than expected. No automatic change.')
    level = 0
    stop = None
    for i in range(next_brace,len(java)):
        if java[i] == '{':
            level += 1
        elif java[i] == '}':
            level -= 1
            if level == 0:
                stop = i + 1
                break
    if stop is None:
        fail('PageController /home method braces do not balance.')
    body = java[next_brace+1:stop-1].strip()
    if not re.fullmatch(r'return\s+"redirect:/dashboard"\s*;',body):
        fail('PageController /home method does more than redirect to Dashboard. No change made.')
    # Remove method and one blank line if present, leaving all other endpoints intact.
    suffix = java[stop:]
    if suffix.startswith('\n'):
        suffix = suffix[1:]
    return java[:start] + suffix,True


def append_home_handler(java: str) -> tuple[str,bool]:
    if re.search(r'@(?:GetMapping|RequestMapping)\s*\(\s*"/home"\s*\)',java):
        return java,False
    # Refuse class-level prefixed mappings so /home is not accidentally scoped elsewhere.
    cls = re.search(r'(?m)^\s*public\s+class\s+HomeController\b[^\{]*\{', java)
    if not cls:
        fail('HomeController class declaration not found. No automatic change.')
    declarations = java[:cls.start()]
    cls_mapping = re.findall(r'@RequestMapping\s*\(\s*"([^"]+)"',declarations)
    if cls_mapping and any(p not in ('','/') for p in cls_mapping):
        fail(f'HomeController has a custom class-level prefix {cls_mapping}. No automatic change.')
    close = java.rfind('}')
    if close < cls.end():
        fail('HomeController closing brace not found.')
    if not re.search(r'(?m)^import\s+org\.springframework\.web\.bind\.annotation\.GetMapping\s*;',java):
        imports = list(re.finditer(r'(?m)^import\s+[^;]+;[ \t]*\r?\n',java))
        if imports:
            pos = imports[-1].end()
            java = java[:pos]+'import org.springframework.web.bind.annotation.GetMapping;\n'+java[pos:]
        else:
            package = re.search(r'(?m)^package\s+[^;]+;[ \t]*\r?\n',java)
            if not package:
                fail('HomeController package declaration missing.')
            pos=package.end()
            java=java[:pos]+'\nimport org.springframework.web.bind.annotation.GetMapping;\n'+java[pos:]
    # Need recompute closing offset after imports.
    close = java.rfind('}')
    method = '''\n    // Page after a successful form or OAuth2 login.\n    @GetMapping("/home")\n    public String showAuthenticatedHome() {\n        return "home";\n    }\n'''
    return java[:close].rstrip()+'\n'+method+java[close:],True


def fix_security(java: str) -> tuple[str,int]:
    if 'class SecurityConfig' not in java:
        fail('SecurityConfig class declaration is missing.')
    expr = re.compile(r'(\.defaultSuccessUrl\s*\(\s*)"/dashboard"(\s*,\s*true\s*\))')
    total = len(expr.findall(java))
    java=expr.sub(lambda m: m.group(1)+'"/home"'+m.group(2),java)
    homed = len(re.findall(r'\.defaultSuccessUrl\s*\(\s*"/home"\s*,\s*true\s*\)',java))
    if total == 0 and homed == 0:
        fail('Expected .defaultSuccessUrl("/dashboard", true) not found in SecurityConfig. No change made.')
    if re.search(r'\.successHandler\s*\(',java):
        print('WARNING: Custom .successHandler(...) detected; it may override a defaultSuccessUrl.')
    return java,total


def main() -> None:
    root = Path(sys.argv[1] if len(sys.argv)>1 else '.').expanduser().resolve()
    if not (root/'pom.xml').is_file():
        fail(f'No pom.xml in {root}. Run from the Capstone backend directory.')
    folder = root/'src/main/java/com/prepstart'
    home = folder/'controller/HomeController.java'
    page = folder/'controller/PageController.java'
    security = folder/'SecurityConfig.java'
    targets = [home,page,security]
    missing = [str(p.relative_to(root)) for p in targets if not p.is_file()]
    if missing:
        fail('Expected Java files missing: '+', '.join(missing))
    template = root/'src/main/resources/templates/home.html'
    if not template.is_file():
        fail('Home template is missing: '+str(template.relative_to(root)))
    originals = {p: normalize(p) for p in targets}
    newpage, page_changed = remove_old_home_redirect(originals[page])
    newhome, home_changed = append_home_handler(originals[home])
    newsec, changes = fix_security(originals[security])
    if re.search(r'@GetMapping\s*\(\s*"/home"\s*\)',newpage):
        fail('PageController still has a /home mapping. No changes made.')
    if len(re.findall(r'@GetMapping\s*\(\s*"/home"\s*\)',newhome)) != 1:
        fail('HomeController does not have exactly one /home mapping. No changes made.')
    edited = {home:newhome,page:newpage,security:newsec}
    todo = [p for p in targets if edited[p]!=originals[p]]
    print('Capstone:',root)
    if not todo:
        print('ALREADY INSTALLED: All three Java files already use Home for login.')
        print('Next: .\\mvnw.cmd clean package -DskipTests')
        return
    backup=root/'.prepstart-shell-backup'/('home-login-'+datetime.now().strftime('%Y%m%d_%H%M%S'))
    for p in todo:
        destination=backup/p.relative_to(root)
        destination.parent.mkdir(parents=True,exist_ok=True)
        shutil.copy2(p,destination)
    print('Backup:',backup)
    for p in todo:
        p.write_text(edited[p],encoding='utf-8')
        print('UPDATED:',p.relative_to(root))
    print('RESULT:')
    print('  HomeController serves GET /home as templates/home.html.')
    print('  PageController no longer redirects /home back to Dashboard.')
    print(f'  SecurityConfig changed {changes} login success redirect(s) to /home.')
    if changes != 2:
        print('  NOTE: Expected 2 redirects (form/OAuth2); verify any custom handlers.')
    print('  Existing root landing, login/verification endpoints, and security rules preserved.')
    print('NEXT: .\\mvnw.cmd clean package -DskipTests')
    print('THEN: .\\mvnw.cmd spring-boot:run')


if __name__=='__main__':
    main()
