"""Collect installed runtime dependency notices for the portable distribution."""
from importlib.metadata import distribution
from pathlib import Path
import shutil
import sys

root=Path(__file__).resolve().parents[2]
output=root/'.cache/worker-licenses'
output.mkdir(parents=True,exist_ok=True)
names=[line.split('==')[0] for line in (Path(__file__).with_name('requirements.lock.txt')).read_text().splitlines() if '==' in line]
summary=['Runtime: Python '+sys.version.split()[0], 'Dependencies in the local worker:']
for name in names:
    package=distribution(name)
    summary.append(f'{package.metadata["Name"]} {package.version} — {package.metadata.get("License-Expression") or package.metadata.get("License") or "see included project notices"}')
    for file in package.files or []:
        if any(term in file.name.lower() for term in ('license','copying','notice')):
            source=Path(package.locate_file(file))
            if source.is_file():
                target=output/name/str(file)
                target.parent.mkdir(parents=True,exist_ok=True)
                shutil.copy2(source,target)
bootloader_license=root/'.cache/build-tools/pyinstaller-6.16.0.dist-info/licenses/COPYING.txt'
if bootloader_license.is_file():shutil.copy2(bootloader_license,output/'PYINSTALLER-BOOTLOADER-LICENSE.txt')
python_license=Path(sys.base_prefix)/'LICENSE.txt'
if python_license.is_file():shutil.copy2(python_license,output/'PYTHON-LICENSE.txt')
(output/'RUNTIME-NOTICES.txt').write_text('\n'.join(summary)+'\n',encoding='utf-8')
print('Runtime dependency notices collected.')
