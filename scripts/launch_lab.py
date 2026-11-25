"""Launch the locally installed NeoForge runtime in a separate, disposable gameDir.

Uses only installed files and an offline development identity. Never reads launcher
accounts, authentication tokens, user saves, or private mod configuration.
"""
import argparse
import json
import os
from pathlib import Path
import re
import subprocess
import uuid
import zipfile

parser=argparse.ArgumentParser()
parser.add_argument('--test',action='store_true')
parser.add_argument('--socket-dir',default='')
parser.add_argument('--game-dir',default='run/client')
parser.add_argument('--max-heap',default='3G')
args=parser.parse_args()
root=Path(__file__).resolve().parents[1]
launcher=Path(os.environ['APPDATA'])/'ATLauncher'
instance=launcher/'instances/Minecraft1211withNeoForge'
lab=Path(args.game_dir)
if not lab.is_absolute():lab=root/lab
lab=lab.resolve()
for folder in ('mods','natives','screenshots','saves'):(lab/folder).mkdir(parents=True,exist_ok=True)
jar=root/'build/libs/firefly-sam-2.0.0.jar'
for old in (lab/'mods').glob('firefly-sam-*.jar'):
    assert old.resolve().parent==(lab/'mods').resolve()
    if old.name!=jar.name:old.replace(old.with_suffix('.jar.disabled'))
(lab/'mods'/jar.name).write_bytes(jar.read_bytes())
geckolib=root/'artifacts/geckolib-neoforge-1.21.1-4.9.4.jar'
if not geckolib.exists():raise FileNotFoundError('Local GeckoLib 4.9.4 runtime JAR is required for the test lab.')
(lab/'mods'/geckolib.name).write_bytes(geckolib.read_bytes())
options=lab/'options.txt'
if not options.exists():options.write_text('autoJump:false\nfullscreen:false\noverrideWidth:1280\noverrideHeight:800\nguiScale:2\nrenderDistance:8\nsimulationDistance:6\nmaxFps:60\npauseOnLostFocus:false\n')

def permitted(rules):
    if not rules:return True
    allowed=False
    for rule in rules:
        os_rule=rule.get('os',{})
        if os_rule.get('name','windows')!='windows':continue
        if os_rule.get('arch')=='x86':continue
        if rule.get('features'):continue
        allowed=rule['action']=='allow'
    return allowed

def expand(items):
    result=[]
    for item in items:
        if isinstance(item,dict):
            if not permitted(item.get('rules')):continue
            # Skip optional launch features; no demo, public server, or Realms routes.
            if any('features' in rule for rule in item.get('rules',[])):continue
            item=item['value']
        if not isinstance(item,list):item=[item]
        for text in item:
            text=re.sub(r'\$\{([^}]+)\}',lambda m:values[m.group(1)],text)
            result.append(text)
    return result

try:
    metadata=json.loads((instance/'instance.json').read_text())
except (OSError,json.JSONDecodeError):
    # A separate lab may already have a verified offline launch vector even
    # when the launcher profile metadata is unavailable. Reuse it only after
    # confirming the executable, isolated gameDir, and credential-free identity.
    cached=root/'build/lab-command.json'
    if not cached.exists():raise RuntimeError('Launcher metadata is unavailable and no cached offline lab command exists.')
    command=json.loads(cached.read_text())
    assert Path(command[0]).name.lower() in ('java.exe','javaw.exe'), 'Cached lab command is not Java.'
    expected_lab=(root/'run/client').resolve()
    assert Path(command[command.index('--gameDir')+1]).resolve()==expected_lab, 'Cached lab command targets an unrecognized game directory.'
    assert command[command.index('--username')+1]=='SamTester', 'Cached lab command is not the offline test identity.'
    assert command[command.index('--accessToken')+1]=='0', 'Cached lab command contains an unexpected access token.'
    command[command.index('--gameDir')+1]=str(lab)
    command=[item for item in command if item!='-Dfireflysam.test=true']
    if args.socket_dir:
        socket_option='-Djdk.net.unixdomain.tmpdir='+args.socket_dir
        positions=[i for i,item in enumerate(command) if item.startswith('-Djdk.net.unixdomain.tmpdir=')]
        if positions:
            command[positions[0]]=socket_option
            for position in reversed(positions[1:]):del command[position]
        else:command.insert(command.index('-Djava.net.preferIPv4Stack=true')+1,socket_option)
    if args.test:command.insert(command.index('-Djava.net.preferIPv4Stack=true')+1,'-Dfireflysam.test=true')
else:
    libs=[]
    for lib in metadata['libraries']:
        if not permitted(lib.get('rules')):continue
        artifact=lib.get('downloads',{}).get('artifact')
        if not artifact:continue
        path=launcher/'libraries'/artifact['path']
        if not path.exists():raise FileNotFoundError(path)
        libs.append(str(path))
        if 'natives-windows' in path.name:
            with zipfile.ZipFile(path) as archive:
                for name in archive.namelist():
                    if name.lower().endswith('.dll'):(lab/'natives'/Path(name).name).write_bytes(archive.read(name))

    libs=list(dict.fromkeys(libs))
    values={'natives_directory':str(lab/'natives'),'launcher_name':'SAM development lab','launcher_version':'1.0',
     'classpath':';'.join(libs),'classpath_separator':';','library_directory':str(launcher/'libraries'),
     'version_name':metadata['id'],'game_directory':str(lab),'assets_root':str(launcher/'assets'),
     'assets_index_name':metadata['assetIndex']['id'],'auth_player_name':'SamTester','auth_uuid':str(uuid.uuid3(uuid.NAMESPACE_DNS,'firefly-sam-development')),
     'auth_access_token':'0','clientid':'0','auth_xuid':'0','user_type':'legacy','version_type':'release','resolution_width':'1280','resolution_height':'800'}
    java=launcher/'runtimes/minecraft/java-runtime-delta/windows-x64/java-runtime-delta/bin/javaw.exe'
    jvm=expand(metadata['arguments']['jvm'])
    command=[str(java),'-Xms512M','-Xmx3G','-Djava.net.preferIPv4Stack=true']
    if args.socket_dir:command.append('-Djdk.net.unixdomain.tmpdir='+args.socket_dir)
    if args.test:command.append('-Dfireflysam.test=true')
    command+=jvm+[metadata['mainClass']]+expand(metadata['arguments']['game'])+['--width','1280','--height','800']
for index,item in enumerate(command):
    if item.startswith('-Xmx'):
        command[index]='-Xmx'+args.max_heap
        break
# No credentials are present in these arguments. Keep the exact invocation locally for reproduction.
(root/'build/lab-command.json').write_text(json.dumps(command,indent=2))
output=(lab/'launch.log').open('wb')
flags=subprocess.CREATE_NO_WINDOW if os.name=='nt' else 0
process=subprocess.Popen(command,cwd=lab,stdout=output,stderr=subprocess.STDOUT,creationflags=flags)
(lab/'process.json').write_text(json.dumps({'pid':process.pid,'gameDir':str(lab),'test':args.test},indent=2))
print(json.dumps({'pid':process.pid,'gameDir':str(lab),'log':str(lab/'launch.log'),'test':args.test}))
