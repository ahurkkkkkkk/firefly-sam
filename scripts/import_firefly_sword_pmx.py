"""Import the locally supplied MMD Firefly sword as the mod's runtime mesh.

Requires Blender with the MMD Tools add-on and `Firefly.zip` extracted to
artifacts/sam-mmd-source. The licensed/user supplied asset stays ignored by git.
"""
import bpy
import json
import os
import shutil
import struct
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "artifacts/sam-mmd-source/Sword.pmx"
ASSETS = ROOT / "src/main/resources/assets/firefly_sam"
MODEL = ASSETS / "models/firefly_blade.fmesh"
TEXTURE = ASSETS / "textures/imported/sword_pmx.png"
ADDON = ROOT / "artifacts/mmd_tools"
PYTHON_DEPS = ROOT / "artifacts/blender-python"

import sys
sys.path[:0] = [str(PYTHON_DEPS), str(ADDON)]
import addon_utils
addon_utils.enable("mmd_tools", default_set=False)

if not SOURCE.exists():
    raise FileNotFoundError(f"Extract Firefly.zip to {SOURCE.parent}")

bpy.ops.object.select_all(action="SELECT")
bpy.ops.object.delete(use_global=False)
bpy.ops.mmd_tools.import_model(filepath=str(SOURCE))
meshes = [item for item in bpy.context.scene.objects if item.type == "MESH" and item.name != "Cube"]
if not meshes:
    raise RuntimeError("Sword.pmx contained no mesh objects")

for material in bpy.data.materials:
    if not material.use_nodes or material.node_tree is None:
        continue
    for node in material.node_tree.nodes:
        if node.type == "TEX_IMAGE" and node.image and Path(node.image.filepath).name == "武器.png":
            source_texture = Path(bpy.path.abspath(node.image.filepath))
            if source_texture.exists():
                TEXTURE.parent.mkdir(parents=True, exist_ok=True)
                shutil.copyfile(source_texture, TEXTURE)
            break
if not TEXTURE.exists():
    raise RuntimeError("Could not find 武器.png referenced by Sword.pmx")

# The MMD blade runs along Blender +Z. Rotate it into the renderer's local +Y
# blade axis and anchor the lowest end at the player's grip.
vertices_world = [obj.matrix_world @ vertex.co for obj in meshes for vertex in obj.data.vertices]
min_z = min(vertex.z for vertex in vertices_world)
max_z = max(vertex.z for vertex in vertices_world)
height = max_z - min_z
if height <= 0.1:
    raise RuntimeError(f"Unexpected sword length {height}")
scale = 2.08 / height
materials = [{"name": "pmx_sword", "texture": "textures/imported/sword_pmx.png",
              "emissive": "", "fullbright": False}]
triangles = []
for obj in meshes:
    mesh = obj.data
    mesh.calc_loop_triangles()
    uv_layer = mesh.uv_layers.active
    for tri in mesh.loop_triangles:
        source_material = mesh.materials[tri.material_index] if mesh.materials else None
        diffuse = tuple(source_material.diffuse_color) if source_material else (1, 1, 1, 1)
        color = sum(max(0, min(255, round(channel * 255))) << shift for channel, shift in zip(diffuse, (0, 8, 16, 24)))
        verts = []
        for loop_index, vertex_index in zip(tri.loops, tri.vertices):
            vertex = obj.matrix_world @ mesh.vertices[vertex_index].co
            # Blade tip follows local +Y and faces Minecraft -Z.
            pos = (vertex.x * scale, (vertex.z - min_z) * scale, -vertex.y * scale)
            normal_matrix = obj.matrix_world.to_3x3().inverted().transposed()
            normal = normal_matrix @ mesh.corner_normals[loop_index].vector
            normal.normalize()
            normal = (normal.x, normal.z, -normal.y)
            uv = uv_layer.data[loop_index].uv if uv_layer else (0.5, 0.5)
            verts.append((pos, (float(uv[0]), 1.0 - float(uv[1])), normal, color))
        triangles.append(verts)

with MODEL.open("wb") as stream:
    def write_int(value): stream.write(struct.pack(">i", value))
    def write_string(value):
        encoded = value.encode("utf-8")
        stream.write(struct.pack(">H", len(encoded)))
        stream.write(encoded)
    def write_floats(values): stream.write(struct.pack(">" + str(len(values)) + "f", *values))
    stream.write(b"FMSH")
    write_int(2)
    write_int(len(materials))
    for material in materials:
        write_string(material["name"])
        write_string(material["texture"])
        write_string(material["emissive"])
        stream.write(bytes([material["fullbright"]]))
    write_int(1)
    write_string("blade")
    write_int(-1)
    write_floats((0.0, 0.0, 0.0))
    write_floats((0.0, 0.0, 0.0, 1.0))
    write_int(1)
    write_int(0)
    write_int(len(triangles) * 4)
    for triangle in triangles:
        for pos, uv, normal, color in (triangle[0], triangle[1], triangle[2], triangle[2]):
            write_floats((*pos, *uv, *normal))
            stream.write(struct.pack(">I", color))

summary = {"source": str(SOURCE), "vertices": len(vertices_world), "triangles": sum(len(obj.data.loop_triangles) for obj in meshes),
           "original_length": height, "runtime_length": height * scale, "texture": str(TEXTURE), "runtime_mesh": str(MODEL)}
(ROOT / "artifacts/sword-pmx-import.json").write_text(json.dumps(summary, indent=2) + "\n", encoding="utf-8")
print(json.dumps(summary, indent=2))
