"""Run portable JVM protocol tests; no Android device or credentials required."""
import os,subprocess,shutil
from pathlib import Path
root=Path(__file__).resolve().parents[1]; output=root/'test-out';output.mkdir(exist_ok=True)
def tool(name):
    home=os.environ.get('JAVA_HOME');candidate=Path(home)/'bin'/(name+('.exe' if os.name=='nt' else '')) if home else None
    return str(candidate) if candidate and candidate.is_file() else shutil.which(name) or name
sources=['app/src/main/java/local/akenori/remote/AdbClient.java','app/src/main/java/local/akenori/remote/HidReports.java','tests/AdbClientTest.java','tests/HidReportsTest.java']
subprocess.run([tool('javac'),'-encoding','UTF-8','-d',str(output),*sources],cwd=root,check=True)
for test in ('AdbClientTest','HidReportsTest'):
    subprocess.run([tool('java'),'-cp',str(output),'local.akenori.remote.'+test],cwd=root,check=True)
