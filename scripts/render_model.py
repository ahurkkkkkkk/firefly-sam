"""Run with Blender --background --python scripts/render_model.py."""
import bpy
from mathutils import Vector
from pathlib import Path
import json

root=Path(__file__).resolve().parents[1]
out=root/'artifacts';out.mkdir(exist_ok=True)
data=json.loads((root/'src/main/resources/assets/firefly_sam/models/sam_rig.json').read_text())
bpy.ops.object.select_all(action='SELECT');bpy.ops.object.delete(use_global=False)
materials={}
def material(color,render_pass):
    key=(color,render_pass)
    if key in materials:return materials[key]
    m=bpy.data.materials.new('SAM '+color);m.diffuse_color=tuple(int(color[i:i+2],16)/255 for i in (2,4,6))+(1,)
    m.use_nodes=True;n=m.node_tree.nodes.get('Principled BSDF');n.inputs['Base Color'].default_value=m.diffuse_color
    n.inputs['Metallic'].default_value=.6 if render_pass==0 else .15;n.inputs['Roughness'].default_value=.28
    if render_pass:
        n.inputs['Emission Color'].default_value=m.diffuse_color;n.inputs['Emission Strength'].default_value=1.5 if render_pass==1 else .6
        if render_pass==2:n.inputs['Alpha'].default_value=.38;m.surface_render_method='DITHERED'
    materials[key]=m;return m
rig={}
for b in data['bones']:
    empty=bpy.data.objects.new(b['name'],None);bpy.context.collection.objects.link(empty);empty.location=b['pivot'];rig[b['name']]=empty
for b in data['bones']:
    if 'parent' in b:rig[b['name']].parent=rig[b['parent']]
    verts=[];faces=[];mats=[];used=[]
    for f in b['faces']:
        idx=len(verts);verts += [f['v'][i:i+3] for i in range(0,12,3)];faces.append((idx,idx+1,idx+2,idx+3))
        m=material(f['color'],f['pass'])
        if m not in used:used.append(m)
        mats.append(used.index(m))
    mesh=bpy.data.meshes.new(b['name']+' polygons');mesh.from_pydata(verts,[],faces);mesh.update();o=bpy.data.objects.new(b['name']+' armor',mesh);bpy.context.collection.objects.link(o);o.parent=rig[b['name']]
    for m in used:mesh.materials.append(m)
    for polygon,idx in zip(mesh.polygons,mats):polygon.material_index=idx
rig['right_arm'].rotation_euler=(-.18,0,-.14);rig['left_arm'].rotation_euler=(-.18,0,.14)
rig['right_forearm'].rotation_euler=(-.16,0,0);rig['left_forearm'].rotation_euler=(-.16,0,0)
rig['right_blade'].rotation_euler=(0,0,2.75);rig['left_blade'].rotation_euler=(0,0,-2.75)
rig['right_wing'].rotation_euler=(.1,.12,-.15);rig['left_wing'].rotation_euler=(.1,-.12,.15)
# Minecraft uses Y up; Blender's studio uses Z up.
parent=bpy.data.objects.new('Minecraft coordinate system',None);bpy.context.collection.objects.link(parent);parent.rotation_euler=(1.57079632679,0,0);rig['root'].parent=parent
def look(obj,target):obj.rotation_euler=(Vector(target)-obj.location).to_track_quat('-Z','Y').to_euler()
bpy.ops.object.camera_add(location=(5,8,4));camera=bpy.context.object;look(camera,(0,0,1.7));camera.data.type='ORTHO';camera.data.ortho_scale=4.85;bpy.context.scene.camera=camera
for pos,energy,size,color in [((2,3,7),1100,5,(.85,.96,1)),((-4,1,4),900,4,(.18,.8,1)),((0,-4,5),1200,3,(.4,1,.77))]:
    bpy.ops.object.light_add(type='AREA',location=pos);l=bpy.context.object;l.data.energy=energy;l.data.shape='DISK';l.data.size=size;l.data.color=color;look(l,(0,0,1.5))
bpy.ops.mesh.primitive_cylinder_add(vertices=96,radius=1.25,depth=.08,location=(0,0,-.05));ped=bpy.context.object;ped.data.materials.append(material('FF14252B',0))
scene=bpy.context.scene;scene.render.engine='CYCLES';scene.cycles.samples=24
scene.world.color=(.025,.035,.045);scene.render.resolution_x=1024;scene.render.resolution_y=1024;scene.render.resolution_percentage=100
scene.view_settings.view_transform='AgX';scene.render.image_settings.file_format='PNG';scene.render.filepath=str(out/'sam-model-preview.png')
bpy.ops.wm.save_as_mainfile(filepath=str(out/'sam-original-model.blend'));bpy.ops.render.render(write_still=True)
