"""Record the exact disposable lab PID/window, without mouse or keyboard input.

Run with the universal-modder tool's Python environment after launch_lab.py.
The capture is WASAPI process audio + one Windows graphics-capture window.
"""
import json
from pathlib import Path
import time
import um.win as win

root=Path(__file__).resolve().parents[1]
state=json.loads((root/'run/client/process.json').read_text())
target_pid=state['pid']
deadline=time.monotonic()+60
target=None
while time.monotonic()<deadline:
    log=(root/'run/client/launch.log').read_text(errors='replace')
    if 'SamTester joined the game' in log:
        target=next((w for w in win.processes('javaw') if w['Id']==target_pid),None)
        if target:break
    time.sleep(.3)
if not target:raise RuntimeError('No running lab game window for PID '+str(target_pid))
# Recorder accepts HWND for video but its audio interface only accepts an exe.
# Resolve that one lookup to the already verified lab PID in this process so the
# unrelated ATLauncher javaw process cannot be picked for audio.
win.pid_of=lambda executable:target_pid
take=root/'artifacts/sam-gameplay-take'
recorder=win.Recorder(exe='javaw.exe',hwnd=str(target['Hwnd']),out=str(take),fps=30,audio=True)
print('Recording lab window',target_pid,target['Hwnd'],flush=True)
recorder.start()
try:time.sleep(35)
finally:metadata=recorder.stop()
print(json.dumps(metadata),flush=True)
