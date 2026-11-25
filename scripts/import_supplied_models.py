"""Blender conversion: supplied SAM OBJ + textured sword FBX -> animated FMSH.

The original inputs are read in-place/outside the repository. Mesh data is
optimized, segmented at armor joints, assigned bind transforms and exported with
smooth per-corner normals, UVs and material batches for Minecraft.
"""
import bpy
import bmesh
import hashlib
import json
import math
import os
from pathlib import Path
import shutil
import struct
from mathutils import Vector, Quaternion

ROOT=Path(__file__).resolve().parents[1]
CACHE=Path(os.environ['USERPROFILE'])/'.cache/firefly-sam/supplied'
ASSETS=ROOT/'src/main/resources/assets/firefly_sam'
OUT=ROOT/'artifacts/supplied';OUT.mkdir(parents=True,exist_ok=True)
MODEL=ASSETS/'models';MODEL.mkdir(exist_ok=True)
TEX=ASSETS/'textures/imported';TEX.mkdir(parents=True,exist_ok=True)
SOURCE=Path(os.environ['USERPROFILE'])/'Downloads/Sam+流萤++萨姆+boss站姿.obj'

# Minecraft coordinates: X right, Y up, -Z forward.
def mc(v):return Vector((v.x,v.z,v.y))
materials=[
    {'name':'sam_armor','texture':'textures/white.png','emissive':'','fullbright':False},
    {'name':'sam_reactor','texture':'textures/white.png','emissive':'','fullbright':True},
    {'name':'sword_frame','texture':'textures/imported/sword_frame.png','emissive':'textures/imported/sword_frame_emissive.png','fullbright':False},
    {'name':'sword_blade','texture':'textures/imported/sword_blade.png','emissive':'textures/imported/sword_blade_emissive.png','fullbright':False}
]
class Bone:
    def __init__(self,name,parent,pivot,direction=None):
        self.name=name;self.parent=parent;self.world=Vector(pivot)
        self.bind=Vector((0,-1,0)).rotation_difference(Vector(direction).normalized()) if direction else Quaternion()
        self.faces={}
    def add(self,mat,vertices):self.faces.setdefault(mat,[]).append(vertices)

def write_mesh(name,bones):
    path=MODEL/(name+'.fmesh')
    def i(value):stream.write(struct.pack('>i',value))
    def string(value):
        value=value.encode('utf-8');stream.write(struct.pack('>H',len(value)));stream.write(value)
    def floats(values):stream.write(struct.pack('>'+str(len(values))+'f',*values))
    with path.open('wb') as stream:
        stream.write(b'FMSH');i(2);i(len(materials))
        for material in materials:
            string(material['name']);string(material['texture']);string(material['emissive']);stream.write(bytes([material['fullbright']]))
        i(len(bones))
        for index,bone in enumerate(bones):
            string(bone.name);parent=bones.index(bone.parent) if bone.parent else -1;i(parent)
            pivot=bone.parent.bind.inverted()@(bone.world-bone.parent.world) if bone.parent else bone.world
            rotation=bone.parent.bind.inverted()@bone.bind if bone.parent else bone.bind
            floats(pivot);floats((rotation.x,rotation.y,rotation.z,rotation.w));i(len(bone.faces))
            for mat,faces in sorted(bone.faces.items()):
                i(mat);i(len(faces)*4)
                for triangle in faces:
                    for pos,uv,normal,color in (*triangle,triangle[-1]):
                        local=bone.bind.inverted()@(pos-bone.world);normal=bone.bind.inverted()@normal
                        floats((*local,*uv,*normal));stream.write(struct.pack('>I',color))
    result={'bones':len(bones),'triangles':sum(len(f) for b in bones for f in b.faces.values()),'bytes':path.stat().st_size}
    print('EXPORTED',name,result,flush=True);return result

bpy.ops.object.select_all(action='SELECT');bpy.ops.object.delete(use_global=False)
bpy.ops.wm.obj_import(filepath=str(SOURCE),forward_axis='NEGATIVE_Z',up_axis='Y')
sam=next(o for o in bpy.context.scene.objects if o.type=='MESH')
bpy.context.view_layer.objects.active=sam;bpy.ops.object.transform_apply(location=False,rotation=True,scale=True)
mesh=sam.data
parents=list(range(len(mesh.vertices)))
def find(i):
    while parents[i]!=i:parents[i]=parents[parents[i]];i=parents[i]
    return i
for edge in mesh.edges:
    a,b=map(find,edge.vertices)
    if a!=b:parents[b]=a
counts={}
for index in range(len(parents)):root=find(index);counts[root]=counts.get(root,0)+1
main=max(counts,key=counts.get)
bm=bmesh.new();bm.from_mesh(mesh);bm.verts.ensure_lookup_table()
bmesh.ops.delete(bm,geom=[v for v in bm.verts if find(v.index)!=main],context='VERTS');bm.to_mesh(mesh);bm.free()
lo=Vector([min(v.co[a] for v in mesh.vertices) for a in range(3)]);hi=Vector([max(v.co[a] for v in mesh.vertices) for a in range(3)])
center=Vector(((lo.x+hi.x)/2,(lo.y+hi.y)/2,lo.z));scale=3.65/(hi.z-lo.z)
for v in mesh.vertices:v.co=(v.co-center)*scale
mesh.update()
sam.name='Supplied SAM · optimized and rigged'
decimate=sam.modifiers.new('Preserve supplied silhouette at game resolution','DECIMATE');decimate.ratio=.075;decimate.use_collapse_triangulate=True
bpy.context.view_layer.objects.active=sam;bpy.ops.object.modifier_apply(modifier=decimate.name)
mesh=sam.data
for polygon in mesh.polygons:polygon.use_smooth=True
mesh.calc_loop_triangles()

root=Bone('root',None,(0,0,0));hips=Bone('hips',root,(0,1.70,.03));torso=Bone('torso',hips,(0,2.0,0));head=Bone('head',torso,(0,3.03,-.04))
bones=[root,hips,torso,head]
pivots={
 # The supplied pose has its clenched fists near Y=1.9; placing the hand
 # joints at 1.64 left the swords visibly below the palms.
 'right':{'arm':(.59,2.72,-.05),'forearm':(.77,2.19,-.15),'hand':(.69,1.97,-.37),
          'leg':(.23,1.68,.02),'shin':(.49,.89,-.12),'foot':(.70,.16,-.25)},
 'left':{'arm':(-.57,2.72,.12),'forearm':(-.74,2.19,.18),'hand':(-.67,1.90,.11),
         'leg':(-.22,1.68,.04),'shin':(-.35,.87,.14),'foot':(-.34,.16,.18)}
}
named={b.name:b for b in bones}
for side,points in pivots.items():
    for part,parent,next_part in [('arm','torso','forearm'),('forearm','arm','hand'),('hand','forearm',None),('leg','hips','shin'),('shin','leg','foot'),('foot','shin',None)]:
        name=side+'_'+part;par=named[parent if parent in ('torso','hips') else side+'_'+parent]
        direction=Vector(points[next_part])-Vector(points[part]) if next_part else None
        b=Bone(name,par,points[part],direction);bones.append(b);named[name]=b

def segment(v):
    x,y,z=v;side='right' if x>0 else 'left';points=pivots[side]
    if y>3.035 and abs(x)<.42:return 'head'
    if y>2.47 and abs(x)>.48 or y>1.41 and abs(x)>(.57 if y>2.14 else .61):
        return side+('_arm' if y>2.20 else '_forearm' if y>2.04 else '_hand')
    if y<1.61:
        return side+('_leg' if y>1.01 else '_shin' if y>.27 else '_foot')
    return 'hips' if y<1.92 else 'torso'

WHITE=0xFFDAE4E8;SILVER=0xFFABBFC8;DARK=0xFF27343F;GOLD=0xFFD8B46C;TEAL=0xFF66FFE0
def paint(pos,normal,part):
    x,y,z=pos;ax=abs(x);em=False;color=WHITE
    # The OBJ has no material file. These colors follow SAM's armor landmarks.
    if part=='head':
        if 3.16<y<3.245 and ax<.19 and z<-.11:color=TEAL;em=True
        elif ax<.035 and 3.25<y<3.50 and normal.z<-.1:color=GOLD
        elif z>0.10:color=SILVER
    elif part=='torso':
        if y<2.21:color=DARK if ax<.32 else SILVER
        if normal.z<-.12 and abs(y-(2.32+ax*.72))<.04:color=GOLD
        if ax<.045 and 2.32<y<2.60 and normal.z<-.3:color=TEAL;em=True
        if z>.25:color=SILVER
    elif part=='hips':
        color=SILVER if ax<.12 else WHITE
        if ax<.045 and y>1.72 and normal.z<0:color=GOLD
    elif 'arm' in part:
        if 'forearm' in part:
            side='right' if x>0 else 'left';cx=pivots[side]['forearm'][0]
            center_x=cx+(pivots[side]['hand'][0]-cx)*max(0,min(1,(2.19-y)/.55))
            if abs(x-center_x)<.032 and 1.80<y<2.12 and normal.z<-.25:color=GOLD
            if abs(x-center_x)<.012 and 1.86<y<2.07 and normal.z<-.4:color=TEAL;em=True
        if 2.12<y<2.23:color=DARK
        if y>2.72 and ax>.72 and abs(y-(2.47+ax*.55))<.055:color=GOLD
    elif 'hand' in part:color=SILVER if normal.z<-.2 else DARK
    elif 'leg' in part:
        if ax<.14:color=DARK
        if 1.03<y<1.13:color=GOLD
    elif 'shin' in part:
        side='right' if x>0 else 'left';cx=pivots[side]['shin'][0];fx=pivots[side]['foot'][0]
        center_x=cx+(fx-cx)*max(0,min(1,(.89-y)/.73))
        if abs(x-center_x)<.027 and .36<y<.86 and normal.z<-.25:color=GOLD
        if .91<y<1.01:color=DARK
    elif 'foot' in part:
        if y<.075:color=DARK
        if .14<y<.20 and normal.z<-.25:color=GOLD
    # Soft baked metal variation makes the sculpture readable without a PBR mod.
    shade=.90+.10*max(0,normal.y)
    r=((color>>16)&255);g=((color>>8)&255);b=color&255
    if not em:color=0xFF000000|(int(r*shade)<<16)|(int(g*shade)<<8)|int(b*shade)
    return color,1 if em else 0

preview_faces={}
for triangle in mesh.loop_triangles:
    positions=[mc(mesh.vertices[i].co) for i in triangle.vertices];centroid=sum(positions,Vector())/3
    part=segment(centroid);normal=mc(triangle.normal);color,material=paint(centroid,normal,part)
    vertices=[]
    for loop_index,position in zip(triangle.loops,positions):
        n=mc(mesh.corner_normals[loop_index].vector)
        vertices.append((position,(.5,.5),n,color))
    named[part].add(material,list(reversed(vertices)))

# Load the actual sword mesh, retaining its UV coordinates and both material sets.
bpy.ops.object.select_all(action='DESELECT')
bpy.ops.import_scene.fbx(filepath=str(CACHE/'source/FireFly_Sword.fbx'))
sword_objects=[o for o in bpy.context.selected_objects if o.type=='MESH']
all_coords=[o.matrix_world@v.co for o in sword_objects for v in o.data.vertices]
min_y=min(v.y for v in all_coords);max_y=max(v.y for v in all_coords)
sword_scale=2.08/(max_y-min_y);grip_y=-.080;sword_z=sum(v.z for v in all_coords)/len(all_coords)
sword_faces=[]
for obj in sword_objects:
    source_mesh=obj.data;source_mesh.calc_loop_triangles();normal_matrix=obj.matrix_world.to_3x3().inverted().transposed()
    material=2 if any('lambert5' in m.name for m in source_mesh.materials if m) else 3
    uv_layer=source_mesh.uv_layers.active
    for triangle in source_mesh.loop_triangles:
        vertices=[]
        for loop_index,index in zip(triangle.loops,triangle.vertices):
            v=obj.matrix_world@source_mesh.vertices[index].co
            pos=Vector((v.x*sword_scale,(v.y-grip_y)*sword_scale,(v.z-sword_z)*sword_scale))
            normal=normal_matrix@source_mesh.corner_normals[loop_index].vector;normal.normalize()
            # FBX blade length is Blender Y; export directly into Minecraft Y.
            uv=uv_layer.data[loop_index].uv if uv_layer else (.5,.5)
            vertices.append((pos,(float(uv[0]),1-float(uv[1])),normal,0xFFFFFFFF))
        sword_faces.append((material,vertices))

for side in ('right','left'):
    hand=named[side+'_hand'];blade=Bone(side+'_blade',hand,hand.world)
    # The supplied fists are now part of the hand bones. Place each grip right
    # at the palm joint so it follows the fist during walking and attacks.
    blade.bind=hand.bind.copy();bones.append(blade)
    for material,triangle in sword_faces:
        blade.add(material,[(blade.world+blade.bind@pos,uv,blade.bind@normal,color) for pos,uv,normal,color in triangle])

summary={'sources':{'sam_obj_sha256':hashlib.sha256(SOURCE.read_bytes()).hexdigest(),
                    'sword_zip_sha256':hashlib.sha256((Path(os.environ['USERPROFILE'])/'Downloads/honkai-star-rail-fire-fly-sword.zip').read_bytes()).hexdigest()},
         'sam_original_triangles':501216,'sam_optimized_body_triangles':len(mesh.loop_triangles),'sam_missing_mtl':True,'contains_source_animation':False}
summary['sam_runtime']=write_mesh('sam_rig',bones)
summary['sam_hand_triangles']={side:sum(len(faces) for faces in named[side+'_hand'].faces.values()) for side in ('right','left')}
single=Bone('blade',None,(0,0,0))
for material,triangle in sword_faces:single.add(material,triangle)
summary['sword_runtime']=write_mesh('firefly_blade',[single])
gauntlet=Bone('gauntlet',None,(0,0,0))
for part in ('right_forearm','right_hand'):
    for material,triangles in named[part].faces.items():
        for triangle in triangles:
            gauntlet.add(material,[(Vector(((pos.x-.80)*1.05,(pos.y-1.54)*1.45,pos.z+.18)),uv,normal,color) for pos,uv,normal,color in triangle])
summary['gauntlet_runtime']=write_mesh('gauntlet',[gauntlet])
(OUT/'import-manifest.json').write_text(json.dumps(summary,indent=2)+'\n',encoding='utf-8')

# An editable posed scene from the imported assets, for inspection and handoff.
for obj in sword_objects:bpy.data.objects.remove(obj,do_unlink=True)
for old in list(bpy.context.scene.objects):
    if old!=sam:bpy.data.objects.remove(old,do_unlink=True)
sam.hide_render=True
preview_materials={}
def preview_material(color,em=False):
    key=(color,em)
    if key in preview_materials:return preview_materials[key]
    m=bpy.data.materials.new('Imported SAM '+hex(color));m.use_nodes=True;n=m.node_tree.nodes.get('Principled BSDF')
    rgb=tuple(((color>>shift)&255)/255 for shift in (16,8,0))+(1,)
    n.inputs['Base Color'].default_value=rgb;n.inputs['Metallic'].default_value=.65;n.inputs['Roughness'].default_value=.3
    if em:n.inputs['Emission Color'].default_value=rgb;n.inputs['Emission Strength'].default_value=2
    preview_materials[key]=m;return m
empties={}
for bone in bones:
    empty=bpy.data.objects.new(bone.name,None);bpy.context.collection.objects.link(empty);empties[bone.name]=empty
    # Use the exported hierarchy in Minecraft coordinates, converted as a whole.
    if bone.parent:
        empty.parent=empties[bone.parent.name];empty.location=bone.parent.bind.inverted()@(bone.world-bone.parent.world)
        rotation=bone.parent.bind.inverted()@bone.bind
    else:empty.location=bone.world;rotation=bone.bind
    empty.rotation_mode='QUATERNION';empty.rotation_quaternion=rotation
    for material,triangles in bone.faces.items():
        verts=[];faces=[];colors=[]
        for tri in triangles:
            idx=len(verts);verts.extend([bone.bind.inverted()@(p-bone.world) for p,uv,n,c in tri]);faces.append((idx,idx+1,idx+2));colors.append(tri[0][3])
        data=bpy.data.meshes.new(bone.name+' mesh');data.from_pydata(verts,[],faces);data.update()
        obj=bpy.data.objects.new(bone.name+' supplied mesh',data);bpy.context.collection.objects.link(obj);obj.parent=empty
        if material in (2,3):
            m=bpy.data.materials.new(materials[material]['name']);m.use_nodes=True;node=m.node_tree.nodes.get('Principled BSDF');node.inputs['Metallic'].default_value=.45
            for path,input_name in [(materials[material]['texture'],'Base Color'),(materials[material]['emissive'],'Emission Color')]:
                tex=m.node_tree.nodes.new('ShaderNodeTexImage');tex.image=bpy.data.images.load(str(ASSETS/path));m.node_tree.links.new(tex.outputs['Color'],node.inputs[input_name])
            node.inputs['Emission Strength'].default_value=1.6
            data.materials.append(m);uv=data.uv_layers.new(name='UVMap')
            for poly,tri in zip(data.polygons,triangles):
                for li,vertex in zip(poly.loop_indices,tri):uv.data[li].uv=(vertex[1][0],1-vertex[1][1])
        else:
            slots={}
            for polygon,color in zip(data.polygons,colors):
                if color not in slots:slots[color]=len(data.materials);data.materials.append(preview_material(color,material==1))
                polygon.material_index=slots[color];polygon.use_smooth=True
    if bone.name.endswith('_blade'):
        empty.rotation_quaternion=rotation@Quaternion((0,0,1),2.80 if bone.name.startswith('right') else -2.80)
coordinate=bpy.data.objects.new('Minecraft Y up',None);bpy.context.collection.objects.link(coordinate);coordinate.rotation_euler=(math.pi/2,0,0);empties['root'].parent=coordinate
def look(obj,target):obj.rotation_euler=(Vector(target)-obj.location).to_track_quat('-Z','Y').to_euler()
bpy.ops.object.camera_add(location=(5,9,3.4));camera=bpy.context.object;camera.data.type='ORTHO';camera.data.ortho_scale=4.6;look(camera,(0,0,1.85));bpy.context.scene.camera=camera
for pos,energy,size,color in [((3,4,7),1100,5,(.85,.94,1)),((-4,2,4),850,4,(.4,.8,1)),((0,-4,5),1300,4,(.3,1,.7))]:
    bpy.ops.object.light_add(type='AREA',location=pos);lamp=bpy.context.object;lamp.data.energy=energy;lamp.data.size=size;lamp.data.color=color;look(lamp,(0,0,1.8))
scene=bpy.context.scene;scene.render.engine='CYCLES';scene.cycles.samples=16;scene.render.resolution_x=1000;scene.render.resolution_y=1100;scene.render.resolution_percentage=100;scene.world.color=(.025,.04,.05)
scene.render.filepath=str(OUT/'supplied-models-preview.png')
bpy.ops.wm.save_as_mainfile(filepath=str(OUT/'sam-supplied-rig.blend'));bpy.ops.render.render(write_still=True)
print('SUPPLIED_MODELS_READY',json.dumps(summary),flush=True)
