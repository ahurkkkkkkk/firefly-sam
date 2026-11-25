"""Original SAM fan geometry. Deterministic, editable, no ripped game assets.

Coordinates are metres, Y up and -Z forward. Armor is made from faceted polygon
plates on an articulated rig rather than a reskinned Minecraft player.
"""
import json
import math
from pathlib import Path
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'src/main/resources/assets/firefly_sam'
MODELS = ASSETS / 'models'
MODELS.mkdir(parents=True, exist_ok=True)
WHITE='FFE4EBEA'; SILVER='FFC3D4D8'; SHADOW='FF657D86'; DARK='FF17252E'; BLACK='FF0B151D'
GOLD='FFE5BA63'; GOLD_DARK='FF9C713D'; TEAL='FF64FFE2'; MINT='FFCBFFF0'; LIME='FFD1FF89'
bones=[]

def bone(name, pivot, parent=None):
    b={'name':name,'pivot':pivot,'faces':[]}
    if parent: b['parent']=parent
    bones.append(b)
    return b

def face(b, vertices, color, render_pass=0):
    if len(vertices)==3: vertices=vertices+[vertices[-1]]
    b['faces'].append({'v':[round(x,5) for v in vertices for x in v], 'color':color,'pass':render_pass})

def shade(color, f):
    return color[:2]+''.join(f'{min(255,int(int(color[i:i+2],16)*f)):02X}' for i in (2,4,6))

def triangulate(outline):
    # Ear clipping also handles the concave sword guard. A triangle fan creates
    # overlapping coplanar triangles there, which flicker in first person.
    def cross(a,b,c):return (b[0]-a[0])*(c[1]-a[1])-(b[1]-a[1])*(c[0]-a[0])
    signed=sum(outline[i][0]*outline[(i+1)%len(outline)][1]-outline[(i+1)%len(outline)][0]*outline[i][1] for i in range(len(outline)))
    orientation=1 if signed>0 else -1
    remaining=list(range(len(outline)));triangles=[]
    while len(remaining)>3:
        found=False
        for k,idx in enumerate(remaining):
            prev=remaining[k-1];nxt=remaining[(k+1)%len(remaining)]
            a,b,c=outline[prev],outline[idx],outline[nxt]
            if orientation*cross(a,b,c)<=1e-10:continue
            interior=False
            for other in remaining:
                if other in (prev,idx,nxt):continue
                p=outline[other]
                if min(orientation*cross(a,b,p),orientation*cross(b,c,p),orientation*cross(c,a,p))>=-1e-10:
                    interior=True;break
            if interior:continue
            triangles.append((prev,idx,nxt));remaining.pop(k);found=True;break
        if not found:raise ValueError('Non-simple armor polygon: '+str(outline))
    triangles.append(tuple(remaining))
    return triangles

def plate(b, outline, z, thickness, color, render_pass=0):
    # XY profile extruded along Z; triangulate concave outlines explicitly.
    front=[(x,y,z) for x,y in outline]; back=[(x,y,z+thickness) for x,y in outline]
    for a,c,d in triangulate(outline):
        face(b,[front[a],front[d],front[c]],color,render_pass)
        face(b,[back[a],back[c],back[d]],shade(color,.72),render_pass)
    for i in range(len(front)):
        j=(i+1)%len(front)
        face(b,[front[i],front[j],back[j],back[i]],shade(color,.60 if i%2 else .85),render_pass)

def box(b, center, size, color, render_pass=0):
    x,y,z=center;w,h,d=(n/2 for n in size)
    plate(b,[(x-w,y-h),(x+w,y-h),(x+w,y+h),(x-w,y+h)],z-d,d*2,color,render_pass)

def loft(b, low, high, color):
    # Two chamfered rectangular sections produce sloped armor, not cubes.
    def ring(section):
        x,y,z,w,d,c=section
        return [(x-w+c,y,z-d),(x+w-c,y,z-d),(x+w,y,z-d+c),(x+w,y,z+d-c),
                (x+w-c,y,z+d),(x-w+c,y,z+d),(x-w,y,z+d-c),(x-w,y,z-d+c)]
    a=ring(low);c=ring(high)
    for i in range(8):face(b,[a[i],a[(i+1)%8],c[(i+1)%8],c[i]],shade(color,[.93,.7,.64,.58,.62,.78,.86,1][i]))
    for i in range(1,7):face(b,[a[0],a[i+1],a[i]],shade(color,.65));face(b,[c[0],c[i],c[i+1]],color)

def sword(b):
    box(b,(0,.09,0),(.085,.32,.105),DARK)
    for y in (-.015,.055,.125,.195):box(b,(0,y,-.055),(.097,.019,.014),GOLD)
    plate(b,[(-.09,-.07),(0,-.13),(.09,-.07),(.055,.0),(-.055,.0)],-.065,.13,GOLD)
    plate(b,[(-.31,.22),(-.17,.35),(-.09,.30),(0,.38),(.09,.30),(.17,.35),(.31,.22),(.14,.23),(0,.29),(-.14,.23)],-.08,.16,WHITE)
    plate(b,[(-.19,.25),(0,.41),(.19,.25),(.08,.24),(0,.31),(-.08,.24)],-.095,.035,GOLD)
    # Firefly's long asymmetric lance-like energy blade, mint core and green edge.
    profile=[(-.092,.31),(-.15,.54),(-.13,1.05),(-.07,1.38),(.045,1.76),(.13,1.12),(.18,.58),(.075,.32)]
    plate(b,profile,-.036,.072,TEAL,1)
    plate(b,[(-.025,.34),(-.07,.66),(-.04,1.15),(.045,1.73),(.069,1.12),(.06,.62),(.025,.34)],-.045,.02,MINT,1)
    plate(b,[(-.15,.53),(-.13,1.05),(-.07,1.38),(.045,1.76),(-.01,1.32),(-.08,.88),(-.08,.51)],-.049,.009,LIME,1)
    plate(b,[(-.20,.40),(-.24,.76),(-.15,1.30),(.045,1.89),(.22,1.16),(.25,.59),(.14,.38)],-.018,.036,'345DFFC8',2)
    for side in (-1,1):
        plate(b,[(side*.09,.33),(side*.18,.50),(side*.15,.76),(side*.075,.59)],-.061,.02,WHITE)
        plate(b,[(side*.10,.37),(side*.155,.51),(side*.131,.60)],-.075,.014,GOLD)

root=bone('root',[0,0,0]);hips=bone('hips',[0,1.55,0],'root')
loft(hips,(0,-.15,0,.34,.21,.06),(0,.23,0,.24,.17,.04),DARK)
for sign in (-1,1):
    plate(hips,[(sign*.07,.18),(sign*.37,.14),(sign*.45,-.31),(sign*.24,-.23),(sign*.09,-.08)],-.245,.17,WHITE)
    plate(hips,[(sign*.28,.115),(sign*.37,.09),(sign*.415,-.23),(sign*.36,-.17)],-.265,.025,GOLD)
    plate(hips,[(sign*.08,.14),(sign*.17,.10),(sign*.22,-.21),(sign*.09,-.09)],-.272,.04,SHADOW)
torso=bone('torso',[0,.43,0],'hips')
loft(torso,(0,-.15,.015,.23,.16,.04),(0,.59,.015,.51,.235,.075),DARK)
for y in (-.08,.04,.16):
    loft(torso,(0,y-.05,-.02,.25,.18,.04),(0,y+.035,-.02,.28,.18,.04),SHADOW)
    box(torso,(0,y,-.212),(.12,.025,.016),TEAL,1)
for sign in (-1,1):
    plate(torso,[(sign*.02,.28),(sign*.10,.66),(sign*.41,.74),(sign*.61,.55),(sign*.48,.23),(sign*.22,.19)],-.27,.19,WHITE)
    plate(torso,[(sign*.07,.44),(sign*.17,.65),(sign*.44,.67),(sign*.53,.53),(sign*.32,.43)],-.304,.032,SILVER)
    plate(torso,[(sign*.09,.37),(sign*.31,.27),(sign*.50,.36),(sign*.48,.25),(sign*.24,.17)],-.313,.03,GOLD)
    plate(torso,[(sign*.13,.52),(sign*.22,.62),(sign*.46,.59),(sign*.31,.53)],-.337,.012,TEAL,1)
    plate(torso,[(sign*.04,.21),(sign*.13,.08),(sign*.21,.17),(sign*.17,.31)],-.25,.055,WHITE)
    box(torso,(sign*.25,.47,.27),(.27,.42,.18),SHADOW)
    plate(torso,[(sign*.13,.32),(sign*.16,.69),(sign*.29,.72),(sign*.40,.39)],.34,.09,WHITE)
plate(torso,[(0,.18),(-.075,.33),(0,.54),(.075,.33)],-.345,.045,GOLD)
plate(torso,[(0,.25),(-.035,.34),(0,.45),(.035,.34)],-.374,.015,MINT,1)
box(torso,(0,.79,0),(.18,.16,.17),BLACK)
head=bone('head',[0,.86,0],'torso')
loft(head,(0,-.05,-.025,.17,.17,.06),(0,.26,.015,.25,.19,.07),SILVER)
plate(head,[(-.22,.22),(-.15,.37),(0,.40),(.15,.37),(.22,.22),(0,.13)],-.14,.24,WHITE)
plate(head,[(-.225,.18),(-.12,.11),(0,.075),(.12,.11),(.225,.18),(.15,.0),(0,-.12),(-.15,.0)],-.216,.09,DARK)
plate(head,[(-.195,.175),(-.04,.105),(0,.14),(.04,.105),(.195,.175),(.12,.065),(0,.035),(-.12,.065)],-.239,.015,TEAL,1)
plate(head,[(-.095,.04),(0,-.06),(.095,.04),(0,-.125)],-.247,.095,WHITE)
plate(head,[(-.055,.26),(0,.38),(.055,.26),(0,.13)],-.228,.024,GOLD)
for sign in (-1,1):
    plate(head,[(sign*.13,.20),(sign*.25,.33),(sign*.36,.53),(sign*.28,.08),(sign*.19,-.035)],-.055,.13,WHITE)
    plate(head,[(sign*.215,.23),(sign*.30,.42),(sign*.25,.095)],-.071,.022,GOLD)
    plate(head,[(sign*.20,.30),(sign*.26,.54),(sign*.25,.82),(sign*.16,.46)],.02,.035,'759EFFD9',2)
    plate(head,[(sign*.20,.33),(sign*.235,.64),(sign*.185,.47)],-.002,.017,MINT,1)
for sign,name in ((1,'right'),(-1,'left')):
    leg=bone(name+'_leg',[sign*.265,-.075,.015],'hips')
    loft(leg,(0,-.59,0,.15,.14,.04),(0,-.02,0,.19,.18,.05),DARK)
    plate(leg,[(-.18,.01),(.18,.01),(.135,-.55),(0,-.65),(-.15,-.50)],-.193,.13,WHITE)
    plate(leg,[(sign*.08,-.02),(sign*.17,-.06),(sign*.13,-.49),(sign*.055,-.57)],-.214,.02,GOLD)
    plate(leg,[(-.055,-.10),(.045,-.13),(.065,-.42),(-.025,-.48)],-.219,.015,SILVER)
    shin=bone(name+'_shin',[0,-.64,0],name+'_leg')
    box(shin,(0,0,0),(.21,.18,.21),DARK)
    loft(shin,(0,-.57,.025,.13,.13,.04),(0,-.12,.01,.19,.19,.05),SHADOW)
    plate(shin,[(-.19,.015),(0,.13),(.19,.015),(.17,-.25),(.105,-.64),(0,-.69),(-.125,-.56),(-.17,-.22)],-.21,.17,WHITE)
    plate(shin,[(-.085,-.10),(0,.045),(.085,-.10),(.055,-.46),(0,-.57),(-.055,-.43)],-.236,.026,SILVER)
    plate(shin,[(-.055,-.13),(0,-.02),(.055,-.13),(0,-.34)],-.265,.015,TEAL,1)
    plate(shin,[(sign*.125,-.18),(sign*.17,-.20),(sign*.10,-.57),(sign*.06,-.64)],-.235,.022,GOLD)
    foot=bone(name+'_foot',[0,-.67,0],name+'_shin')
    loft(foot,(0,-.11,-.105,.16,.31,.055),(0,.10,.015,.12,.15,.035),WHITE)
    box(foot,(0,-.105,-.09),(.30,.055,.55),DARK)
    plate(foot,[(-.13,-.07),(0,.02),(.13,-.07),(.08,-.12),(-.08,-.12)],-.41,.035,GOLD)
    arm=bone(name+'_arm',[sign*.67,.64,0],'torso')
    loft(arm,(sign*.07,-.52,0,.12,.13,.04),(sign*.02,.015,0,.19,.18,.04),DARK)
    plate(arm,[(-.24,.10),(0,.27),(.25,.18),(.32,-.11),(.20,-.36),(-.15,-.28),(-.26,-.08)],-.25,.45,WHITE)
    plate(arm,[(sign*-.16,.075),(sign*.04,.17),(sign*.23,.085),(sign*.25,-.075),(sign*.08,-.17)],-.277,.03,SILVER)
    plate(arm,[(sign*.04,.20),(sign*.23,.14),(sign*.31,-.08),(sign*.24,-.19),(sign*.16,.025)],-.291,.023,GOLD)
    plate(arm,[(sign*.24,.04),(sign*.34,.32),(sign*.36,.60),(sign*.19,.16)],.0,.06,'809FFFD7',2)
    plate(arm,[(sign*.24,.095),(sign*.30,.35),(sign*.24,.22)],-.035,.02,MINT,1)
    plate(arm,[(-.13,-.30),(.14,-.31),(.12,-.55),(0,-.61),(-.13,-.50)],-.14,.23,SILVER)
    fore=bone(name+'_forearm',[sign*.07,-.60,0],name+'_arm')
    box(fore,(0,0,0),(.20,.18,.21),BLACK)
    loft(fore,(0,-.52,-.02,.14,.13,.04),(0,-.08,0,.21,.21,.055),SILVER)
    plate(fore,[(-.20,-.07),(0,.025),(.20,-.07),(.13,-.49),(0,-.60),(-.14,-.48)],-.25,.18,WHITE)
    plate(fore,[(sign*.11,-.065),(sign*.20,-.10),(sign*.12,-.47),(sign*.045,-.54)],-.276,.026,GOLD)
    plate(fore,[(-.06,-.14),(0,-.06),(.06,-.14),(.035,-.43),(0,-.50),(-.035,-.43)],-.281,.015,TEAL,1)
    hand=bone(name+'_hand',[0,-.57,-.015],name+'_forearm')
    box(hand,(0,-.07,0),(.19,.21,.19),DARK)
    for i in range(3):box(hand,(-.065+i*.065,-.085,-.107),(.05,.15,.045),SILVER)
    blade=bone(name+'_blade',[0,-.12,-.03],name+'_hand');sword(blade)
    wing=bone(name+'_wing',[sign*.25,.55,.29],'torso')
    box(wing,(0,0,0),(.17,.31,.17),GOLD)
    plate(wing,[(0,-.08),(sign*.12,.26),(sign*.50,.40),(sign*.42,.12),(sign*.25,-.19)],.01,.09,WHITE)
    for offset,size in ((0,1),(-.32,.78)):
        points=[(sign*.15,-.02+offset),(sign*.44,.40+offset),(sign*1.20,.98*size+offset),
                (sign*.91,.28+offset),(sign*.60,-.20+offset),(sign*.30,-.36+offset)]
        plate(wing,points,.09,.025,'4565FFD2',2)
        plate(wing,[(sign*.20,offset),(sign*.49,.36+offset),(sign*1.20,.98*size+offset),(sign*.72,.35+offset)],.06,.02,TEAL,1)
        plate(wing,[(sign*.22,-.09+offset),(sign*.45,.05+offset),(sign*.84,.36+offset),(sign*.54,-.02+offset)],.055,.013,LIME,1)
    plate(wing,[(0,.12),(sign*.22,.57),(sign*.35,.98),(sign*.20,1.32),(sign*.08,.72)],.12,.025,'8062FFD5',2)

def save(name, rig):
    data={'format':'firefly_sam_rig_1','author':'Original fan model by Codex and player',
          'license':'MIT; character design belongs to HoYoverse', 'bones':rig}
    (MODELS/(name+'.json')).write_text(json.dumps(data,separators=(',',':'))+'\n',encoding='utf-8')
    print(name,len(rig),'bones',sum(len(b['faces']) for b in rig),'faces')

save('sam_rig',bones)
main_bones=bones;bones=[];s=sword(bone('blade',[0,0,0]));save('firefly_blade',bones)
bones=[];g=bone('gauntlet',[0,0,0]);
loft(g,(0,0,0,.18,.17,.04),(0,.77,0,.24,.21,.05),SILVER)
plate(g,[(-.24,.76),(0,.92),(.24,.76),(.16,.12),(0,-.04),(-.16,.12)],-.24,.15,WHITE)
plate(g,[(-.055,.60),(0,.73),(.055,.60),(.04,.17),(0,.10),(-.04,.17)],-.263,.02,TEAL,1)
for side in (-1,1):plate(g,[(side*.13,.76),(side*.23,.71),(side*.16,.16),(side*.09,.10)],-.265,.02,GOLD)
box(g,(0,.9,0),(.23,.24,.23),DARK);save('gauntlet',bones)
(ASSETS/'textures').mkdir(exist_ok=True)
Image.new('RGBA',(2,2),(255,255,255,255)).save(ASSETS/'textures/white.png')

# Inventory icon is an original pixel depiction. Actual held weapon uses polygon geometry.
(ASSETS/'textures/item').mkdir(exist_ok=True)
icon=Image.new('RGBA',(32,32));d=ImageDraw.Draw(icon)
d.polygon([(6,27),(8,29),(13,23),(11,21)],fill='#273B42')
d.line([(6,26),(10,28)],fill='#E5BA63',width=2)
d.polygon([(9,20),(12,17),(15,18),(17,21),(14,24),(13,21)],fill='#E4EBEA')
d.polygon([(13,19),(16,19),(26,4),(25,2),(17,11)],fill='#64FFE2')
d.line([(14,19),(25,3)],fill='#D1FF89',width=2)
icon.save(ASSETS/'textures/item/firefly_blade.png')
item={'parent':'minecraft:builtin/entity','textures':{'particle':'firefly_sam:item/firefly_blade'},'gui_light':'front','display':{
 'gui':{'rotation':[0,0,-40],'translation':[0,0,0],'scale':[.8,.8,.8]},
 'firstperson_righthand':{'rotation':[0,-85,15],'translation':[1,1,0],'scale':[.8,.8,.8]},
 'firstperson_lefthand':{'rotation':[0,85,-15],'translation':[1,1,0],'scale':[.8,.8,.8]},
 'thirdperson_righthand':{'rotation':[0,90,-150],'translation':[0,2,0],'scale':[1,1,1]},
 'thirdperson_lefthand':{'rotation':[0,-90,150],'translation':[0,2,0],'scale':[1,1,1]},
 'ground':{'rotation':[0,0,70],'translation':[0,3,0],'scale':[.6,.6,.6]},
 'fixed':{'rotation':[0,0,-40],'translation':[0,0,0],'scale':[.8,.8,.8]}}}
(MODELS/'item').mkdir(exist_ok=True)
(MODELS/'item/firefly_blade.json').write_text(json.dumps(item,indent=2)+'\n')
lang={
 'item.firefly_sam.firefly_blade':'Firefly Blade',
 'key.categories.firefly_sam':'Firefly / SAM',
 'key.firefly_sam.transform':'Toggle SAM mech',
 'key.firefly_sam.ultimate':'Deathstar Overload (10-hit combo)',
 'tooltip.firefly_sam.blade':'Fyrefly Type-IV · Energy Blade',
 'tooltip.firefly_sam.transform':'R: transform into SAM · Shift + right-click also transforms',
 'tooltip.firefly_sam.combo':'SAM: 2x damage · land 10 charged sword hits within 6s of each other',
 'tooltip.firefly_sam.ultimate':'G / right-click: unleash Deathstar Overload when ready',
 'tooltip.firefly_sam.terrain':'Ultimate destroys terrain in a 48-block-wide crater!',
 'message.firefly_sam.hold_blade':'Equip the Firefly Blade in your main hand first.',
 'message.firefly_sam.no_room':'SAM needs open space: at least 4 blocks of headroom.',
 'message.firefly_sam.online':'SAM SYSTEM ONLINE · Damage x2 · Toggle off with R · Build a 10-hit combo',
 'message.firefly_sam.ready':'COMPLETE COMBUSTION READY · Press your ultimate key!',
 'message.firefly_sam.need_combo':'Land 10 charged sword hits in SAM to unlock the ultimate.',
 'message.firefly_sam.target_unloaded':'Aim at nearby loaded ground.'}
(ASSETS/'lang').mkdir(exist_ok=True)
(ASSETS/'lang/en_us.json').write_text(json.dumps(lang,indent=2)+'\n',encoding='utf-8')
recipe={'type':'minecraft:crafting_shaped','pattern':[' EA',' NE','N  '],
 'key':{'E':{'item':'minecraft:emerald'},'A':{'item':'minecraft:amethyst_shard'},'N':{'item':'minecraft:netherite_ingot'}},
 'result':{'id':'firefly_sam:firefly_blade','count':1}}
recipe_path=ROOT/'src/main/resources/data/firefly_sam/recipe/firefly_blade.json';recipe_path.parent.mkdir(parents=True,exist_ok=True)
recipe_path.write_text(json.dumps(recipe,indent=2)+'\n')
(ROOT/'src/main/resources/pack.mcmeta').write_text(json.dumps({'pack':{'pack_format':34,'description':'Firefly / SAM original models and effects'}})+'\n')
