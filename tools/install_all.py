#!/usr/bin/env python3
"""Apply the existing PrepStart React shell / AI redirect / Home login patches.
Usage: py install_all.py PROJECT_ROOT
"""
from pathlib import Path
import subprocess
import sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else '.').resolve()
patch = Path(__file__).resolve().parents[1]
required = [
    root / 'pom.xml',
    root / 'src/main/resources/templates/home.html',
    root / 'src/main/java/com/prepstart/controller/HomeController.java',
    root / 'src/main/java/com/prepstart/controller/PageController.java',
    root / 'src/main/java/com/prepstart/SecurityConfig.java',
]
missing = [str(p.relative_to(root)) for p in required if not p.is_file()]
if missing:
    sys.exit('STOP: expected files missing in ' + str(root) + ':\n  ' + '\n  '.join(missing))
if (patch/'src/main/resources/templates/fragments/navbar.html').is_file() is False:
    sys.exit('STOP: missing shell files from download. Extract the entire ZIP first.')

for task in ('install_react_shell_ai.py', 'fix_home_login.py'):
    print('\n' + '=' * 70, flush=True)
    print('Running ' + task + ' on ' + str(root), flush=True)
    print('=' * 70, flush=True)
    result = subprocess.run([sys.executable, str(patch/'tools'/task), str(root)], check=False)
    if result.returncode:
        sys.exit(f'STOP: {task} did not complete (exit {result.returncode}). See error above. Backup files remain available.')

print('\nAll installers finished. Review any REVIEW/WARNING messages above.')
print('Check logo at src/main/resources/static/images/logo.png.')
print('Backups are under .prepstart-shell-backup/ (do not commit them).')
print(r'Next: .\mvnw.cmd clean package -DskipTests')
