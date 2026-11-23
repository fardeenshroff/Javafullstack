import json,socket,subprocess,time,urllib.request,urllib.parse
from pathlib import Path
import pytest

ROOT=Path(__file__).resolve().parents[1]

def wait_health(port,timeout=15):
    url=f'http://127.0.0.1:{port}/api/health'
    deadline=time.monotonic()+timeout
    while time.monotonic()<deadline:
        try:return json.load(urllib.request.urlopen(url,timeout=.5))
        except (OSError,TimeoutError):time.sleep(.1)
    raise RuntimeError('Server failed to start')

def api(port,path,data=None):
    url=f'http://127.0.0.1:{port}{path}'
    req=urllib.request.Request(url,data=data)
    return json.load(urllib.request.urlopen(req,timeout=4))

def test_api_persistence_and_frontend(tmp_path):
    import shutil
    if not shutil.which('java') or not shutil.which('javac'):pytest.skip('JDK not installed')
    cls=tmp_path/'classes';cls.mkdir()
    subprocess.run(['javac','-encoding','UTF-8','-d',str(cls),'src/main/java/portfolio/TaskServer.java'],cwd=ROOT,check=True)
    with socket.socket() as sock:
        sock.bind(('127.0.0.1',0));port=sock.getsockname()[1]
    db=tmp_path/'tasks.tsv'
    def start():return subprocess.Popen(['java','-cp',str(cls),'portfolio.TaskServer','--port',str(port),'--data',str(db)],cwd=ROOT,stdout=subprocess.DEVNULL,stderr=subprocess.PIPE)
    proc=start()
    try:
        assert wait_health(port)=={'ok':True}
        assert api(port,'/api/tasks')==[]
        tasks=api(port,'/api/tasks',urllib.parse.urlencode({'title':'Review integration tests'}).encode())
        assert len(tasks)==1 and tasks[0]['done'] is False
        changed=api(port,'/api/tasks/1/toggle',b'')
        assert changed[0]['done'] is True
        with urllib.request.urlopen(f'http://127.0.0.1:{port}/') as r:
            assert b'Engineering Task Board' in r.read()
    finally:
        proc.terminate();proc.wait(timeout=10)
    proc=start()
    try:
        wait_health(port)
        assert api(port,'/api/tasks')[0]['done'] is True
    finally:
        proc.terminate();proc.wait(timeout=10)
