"""Technical VFX textures plus runtime copies of the supplied sword textures."""
from pathlib import Path
import os
import math
import random
from PIL import Image, ImageFilter, ImageDraw
ROOT=Path(__file__).resolve().parents[1]
assets=ROOT/'src/main/resources/assets/firefly_sam'
cache=Path(os.environ['USERPROFILE'])/'.cache/firefly-sam/supplied/textures'
out=assets/'textures/imported';out.mkdir(parents=True,exist_ok=True)
for source,name in [('FireFly_low_lambert5_BaseColor.1001.png','sword_frame'),('FireFly_low_lambert5_Emissive.1001.png','sword_frame_emissive'),
                    ('FireFly_low_lambert6_BaseColor.1001.png','sword_blade'),('FireFly_low_lambert6_Emissive.1001.png','sword_blade_emissive')]:
    image=Image.open(cache/source).convert('RGBA');image.thumbnail((2048,2048),Image.Resampling.LANCZOS);image.save(out/(name+'.png'))
    print(name,image.size)
effects=assets/'textures/effect';effects.mkdir(parents=True,exist_ok=True)
image=Image.new('RGBA',(128,128));pixels=image.load()
for y in range(128):
    for x in range(128):
        u=x/127;v=y/127
        edge=math.sin(math.pi*v)**.8
        alpha=int(210*edge*(.95-.65*u))
        pixels[x,y]=(255,255,255,alpha)
image.save(effects/'energy_wing.png')
# Additive shaders use RGB intensity. Bake the same soft falloff into RGB.
glow=Image.new('RGBA',image.size)
glow.putdata([(pixels[x,y][3],)*3+(255,) for y in range(128) for x in range(128)])
glow.save(effects/'energy_wing_glow.png')
random.seed(1310);size=1024;space=Image.new('RGB',(size,size));pixels=space.load()
for y in range(size):
    for x in range(size):
        u=x/size;v=y/size;wave=math.exp(-((v-.52-.12*math.sin(u*9))/.17)**2)
        fog=wave*(.5+.5*math.sin(u*17+v*11))
        pixels[x,y]=(int(2+fog*4),int(7+fog*19),int(15+fog*30))
draw=ImageDraw.Draw(space)
for _ in range(1500):
    x=random.randrange(size);y=random.randrange(size);a=random.randrange(60,220)
    draw.point((x,y),fill=(int(a*.5),int(a*.85),a))
for _ in range(35):
    x=random.randrange(size);y=random.randrange(size)
    draw.line((x-2,y,x+2,y),fill=(110,240,210));draw.line((x,y-2,x,y+2),fill=(110,240,210))
space.save(effects/'combustion_space.png')
