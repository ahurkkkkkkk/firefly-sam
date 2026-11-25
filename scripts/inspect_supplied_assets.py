"""Inspect user-supplied assets in Blender. Raw imports stay outside the source tree."""
import bpy
import json
import os
from pathlib import Path
from mathutils import Vector
import zipfile

ROOT=Path(__file__).resolve().parents[1]
CACHE=Path(os.environ['USERPROFILE'])/'.cache/firefly-sam/supplied'
CACHE.mkdir(parents=True,exist_ok=True)
OUT=ROOT/'artifacts/supplied';OUT.mkdir(parents=True,exist_ok=True)
with zipfile.ZipFile(Path(os.environ['USERPROFILE'])/'Downloads/honkai-star-rail-fire-fly-sword.zip') as archive:
    for member in archive.infolist():
        destination=(CACHE/member.filename).resolve()
        destination.relative_to(CACHE.resolve())
        if not member.is_dir():destination.parent.mkdir(parents=True,exist_ok=True);destination.write_bytes(archive.read(member))
bpy.ops.object.select_all(action='SELECT');bpy.ops.object.delete(use_global=False)
bpy.ops.wm.obj_import(filepath=str(Path(os.environ['USERPROFILE'])/'Downloads/Sam+流萤++萨姆+boss站姿.obj'),forward_axis='NEGATIVE_Z',up_axis='Y')
objects=[o for o in bpy.context.scene.objects if o.type=='MESH']
for obj in objects:
    bpy.context.view_layer.objects.active=obj;obj.select_set(True)
    bpy.ops.object.transform_apply(location=False,rotation=True,scale=True)
bounds=[obj.matrix_world@Vector(corner) for obj in objects for corner in obj.bound_box]
lo=Vector([min(v[a] for v in bounds) for a in range(3)]);hi=Vector([max(v[a] for v in bounds) for a in range(3)])
scale=3.65/(hi.z-lo.z);center=Vector(((lo.x+hi.x)/2,(lo.y+hi.y)/2,lo.z))
for obj in objects:
    for v in obj.data.vertices:v.co=(v.co-center)*scale
    obj.location=(0,0,0)
    obj.data.update()
stats={'sam':{'raw_bounds':[list(lo),list(hi)],'objects':[]}}
for obj in objects:
    stats['sam']['objects'].append({'name':obj.name,'vertices':len(obj.data.vertices),'polygons':len(obj.data.polygons),'materials':[m.name for m in obj.data.materials if m]})
    # Connected pieces guide rigid armor segmentation into bones.
    parent=list(range(len(obj.data.vertices)))
    def find(i):
        while parent[i]!=i:parent[i]=parent[parent[i]];i=parent[i]
        return i
    def union(a,b):
        a=find(a);b=find(b)
        if a!=b:parent[b]=a
    for e in obj.data.edges:union(e.vertices[0],e.vertices[1])
    groups={}
    for v in obj.data.vertices:groups.setdefault(find(v.index),[]).append(v.co.copy())
    comps=[]
    for verts in groups.values():
        if len(verts)<30:continue
        low=[min(v[a] for v in verts) for a in range(3)];high=[max(v[a] for v in verts) for a in range(3)]
        comps.append({'vertices':len(verts),'min':low,'max':high})
    stats['sam']['objects'][-1]['components']=sorted(comps,key=lambda c:-c['vertices'])[:100]

def mat(name,color):
    m=bpy.data.materials.new(name);m.diffuse_color=(*color,1);m.use_nodes=True;n=m.node_tree.nodes.get('Principled BSDF');n.inputs['Base Color'].default_value=(*color,1);n.inputs['Metallic'].default_value=.45;n.inputs['Roughness'].default_value=.32;return m
clay=mat('Inspection silver',(.65,.72,.75))
for obj in objects:obj.data.materials.clear();obj.data.materials.append(clay)
def look(obj,target):obj.rotation_euler=(Vector(target)-obj.location).to_track_quat('-Z','Y').to_euler()
bpy.ops.object.camera_add(location=(0,-8,2.5));camera=bpy.context.object;camera.data.type='ORTHO';camera.data.ortho_scale=4.4;look(camera,(0,0,1.8));bpy.context.scene.camera=camera
for pos,energy,size in [((3,-4,6),1000,5),((-4,-2,3),750,4),((0,4,5),1200,4)]:
    bpy.ops.object.light_add(type='AREA',location=pos);light=bpy.context.object;light.data.energy=energy;light.data.size=size;look(light,(0,0,2))
scene=bpy.context.scene;scene.render.engine='CYCLES';scene.cycles.samples=12;scene.render.resolution_x=800;scene.render.resolution_y=900;scene.render.resolution_percentage=100;scene.world.color=(.05,.06,.08)
for name,location in [('front',(0,-8,2.4)),('back',(0,8,2.4)),('side',(8,0,2.4))]:
    camera.location=location;look(camera,(0,0,1.8));scene.render.filepath=str(OUT/('sam-import-'+name+'.png'));bpy.ops.render.render(write_still=True)
bpy.ops.wm.save_as_mainfile(filepath=str(CACHE/'sam-inspected.blend'))
for obj in list(bpy.context.scene.objects):
    if obj.type=='MESH':bpy.data.objects.remove(obj,do_unlink=True)
bpy.ops.import_scene.fbx(filepath=str(CACHE/'source/FireFly_Sword.fbx'))
stats['sword']={'objects':[]}
for obj in bpy.context.scene.objects:
    if obj.type!='MESH':continue
    coords=[obj.matrix_world@v.co for v in obj.data.vertices]
    stats['sword']['objects'].append({'name':obj.name,'vertices':len(obj.data.vertices),'polygons':len(obj.data.polygons),'materials':[m.name for m in obj.data.materials if m],
        'min':[min(v[a] for v in coords) for a in range(3)],'max':[max(v[a] for v in coords) for a in range(3)]})
stats['sword']['actions']=[a.name for a in bpy.data.actions]
(OUT/'inspection.json').write_text(json.dumps(stats,indent=2),encoding='utf-8')
print('SUPPLIED_ASSET_INSPECTION',json.dumps(stats,ensure_ascii=True))
