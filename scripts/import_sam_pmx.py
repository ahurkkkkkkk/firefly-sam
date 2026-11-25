"""Convert the user's local SAM + Firefly sword PMX models to a weighted local rig.

Requires Blender, MMD Tools, OpenCC, and Firefly.zip extracted to artifacts.
The generated PMX-derived mesh and textures are gitignored because the supplied
model readme forbids redistribution.
"""
import bpy
import json
import shutil
import struct
import sys
from pathlib import Path
from mathutils import Vector

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "artifacts/sam-mmd-source/Sam.pmx"
SWORD_MESH = ROOT / "src/main/resources/assets/firefly_sam/models/firefly_blade.fmesh"
ADDON = ROOT / "artifacts/mmd_tools"
PYTHON_DEPS = ROOT / "artifacts/blender-python"
ASSETS = ROOT / "src/main/resources/assets/firefly_sam"
MODEL_PATH = ASSETS / "models/sam_rig.fmesh"
TEXTURE_PATH = ASSETS / "textures/imported/sam_mmd_body.png"

sys.path[:0] = [str(PYTHON_DEPS), str(ADDON)]
import addon_utils
addon_utils.enable("mmd_tools", default_set=False)
if not SOURCE.exists():
    raise FileNotFoundError(f"Extract Firefly.zip to {SOURCE.parent}")
if not SWORD_MESH.exists():
    raise FileNotFoundError("Run scripts/import_firefly_sword_pmx.py first")

bpy.ops.object.select_all(action="SELECT")
bpy.ops.object.delete(use_global=False)
bpy.ops.mmd_tools.import_model(filepath=str(SOURCE))
armature = next((o for o in bpy.context.scene.objects if o.type == "ARMATURE" and o.name == "Sam_arm"), None)
mesh = next((o for o in bpy.context.scene.objects if o.type == "MESH" and o.name == "Sam_mesh"), None)
if armature is None or mesh is None:
    raise RuntimeError("Sam.pmx must provide the Sam_arm skeleton and Sam_mesh body")

source_texture = SOURCE.parent / "身体.png"
if not source_texture.exists():
    raise FileNotFoundError("Firefly.zip is missing the SAM body texture 身体.png")
TEXTURE_PATH.parent.mkdir(parents=True, exist_ok=True)
shutil.copyfile(source_texture, TEXTURE_PATH)

world_vertices = [mesh.matrix_world @ vertex.co for vertex in mesh.data.vertices]
floor = min(vertex.z for vertex in world_vertices)
ceiling = max(vertex.z for vertex in world_vertices)
scale = 3.65 / (ceiling - floor)
if not 0.5 < scale < 10:
    raise RuntimeError(f"Unexpected SAM height scale: {scale}")

def to_game(point):
    # PMX/MMD is Z-up; Minecraft model space uses Y-up. Two sign/axis swaps
    # preserve handedness while keeping the authored front/back direction.
    return Vector((-point.x * scale, (point.z - floor) * scale, point.y * scale))

source_bones = {bone.name: bone for bone in armature.data.bones}
source_pivots = {name: to_game(armature.matrix_world @ bone.head_local) for name, bone in source_bones.items()}

def convert_group(name):
    upper = name.upper()
    side = "right" if name.endswith((".R", "_R", "Right")) else "left" if name.endswith((".L", "_L", "Left")) else None
    if name in ("操作中心", "全ての親", "センター", "グルーブ", "腰", "下半身"):
        return "hips"
    if name in ("上半身", "上半身2") or any(key in name for key in ("Breastplate", "Reactor", "胸")):
        return "torso"
    if name in ("頭", "首", "メガネ", "目.R", "目.L") or name.startswith("bz_"):
        return "head"
    if side is None:
        if name.startswith(("Skirt", "RootArmor")):
            return "hips"
        return None
    if name.startswith(("肩", "腕", "pauldron", "Shoulder", "Arm")):
        return side + "_arm"
    if name.startswith(("ひじ", "手捩", "elbow", "Elbow", "Forearm")):
        return side + "_forearm"
    if name.startswith(("手首", "手先", "指", "親指", "人指", "中指", "小指", "薬指", "Wrist", "Hand", "Finger")):
        return side + "_hand"
    if name.startswith(("足D.", "足.R", "足.L", "FootArmor", "Boot", "Thigh", "Leg")):
        return side + "_leg"
    if name.startswith(("ひざ", "Knne", "Knee", "Shin")):
        return side + "_shin"
    if name.startswith(("足首", "つま先", "足先", "Toe", "Ankle", "Foot")):
        return side + "_foot"
    if name.startswith(("Skirt", "RootArmor")):
        return "hips"
    return None

def source_group_target(name):
    target = convert_group(name)
    bone = source_bones.get(name)
    while target is None and bone is not None and bone.parent is not None:
        bone = bone.parent
        target = convert_group(bone.name)
    return target or "torso"

bones = []
class Bone:
    def __init__(self, name, parent, world):
        self.name, self.parent, self.world = name, parent, Vector(world)

root = Bone("root", None, (0, 0, 0))
hips = Bone("hips", root, source_pivots["腰"])
torso = Bone("torso", hips, source_pivots["上半身2"])
head = Bone("head", torso, source_pivots["頭"])
bones.extend((root, hips, torso, head))
rig = {bone.name: bone for bone in bones}
for side, suffix in (("right", ".R"), ("left", ".L")):
    shoulder = Bone(side + "_arm", torso, source_pivots["腕" + suffix])
    forearm = Bone(side + "_forearm", shoulder, source_pivots["ひじ" + suffix])
    hand = Bone(side + "_hand", forearm, source_pivots["手首" + suffix])
    leg = Bone(side + "_leg", hips, source_pivots["足" + suffix])
    shin = Bone(side + "_shin", leg, source_pivots["ひざ" + suffix])
    foot = Bone(side + "_foot", shin, source_pivots["足首" + suffix])
    bones.extend((shoulder, forearm, hand, leg, shin, foot))
    rig.update({bone.name: bone for bone in (shoulder, forearm, hand, leg, shin, foot)})

group_targets = {group.name: source_group_target(group.name) for group in mesh.vertex_groups}
group_names = {group.index: group.name for group in mesh.vertex_groups}
bone_indices = {bone.name: index for index, bone in enumerate(bones)}
def vertex_weights(vertex):
    totals = {}
    for assignment in vertex.groups:
        name = group_names.get(assignment.group)
        if name not in source_bones:
            continue
        target = group_targets[name]
        totals[target] = totals.get(target, 0.0) + assignment.weight
    ranked = sorted(totals.items(), key=lambda item: item[1], reverse=True)[:4]
    if not ranked or sum(weight for _, weight in ranked) <= 0:
        ranked = [("torso", 1.0)]
    total = sum(weight for _, weight in ranked)
    return [(bone_indices[name], weight / total) for name, weight in ranked]

mesh.data.calc_loop_triangles()
uv_layer = mesh.data.uv_layers.active
normal_matrix = mesh.matrix_world.to_3x3().inverted().transposed()
material_vertices = {}
for triangle in mesh.data.loop_triangles:
    material_index = mesh.data.polygons[triangle.polygon_index].material_index
    batch = material_vertices.setdefault(material_index, [])
    face = []
    for loop_index, vertex_index in zip(triangle.loops, triangle.vertices):
        vertex = mesh.data.vertices[vertex_index]
        pos = to_game(mesh.matrix_world @ vertex.co)
        normal = normal_matrix @ mesh.data.corner_normals[loop_index].vector
        normal.normalize()
        normal = Vector((-normal.x, normal.z, normal.y))
        uv = uv_layer.data[loop_index].uv if uv_layer else (0.5, 0.5)
        weights = vertex_weights(vertex)
        face.append((tuple(pos), (float(uv[0]), 1.0 - float(uv[1])), tuple(normal), weights))
    batch.extend((face[0], face[1], face[2], face[2]))

def read_sword(path):
    data = path.read_bytes()
    offset = 8
    def i32():
        nonlocal offset
        value = struct.unpack_from(">i", data, offset)[0]
        offset += 4
        return value
    def string():
        nonlocal offset
        size = struct.unpack_from(">H", data, offset)[0]
        offset += 2
        value = data[offset:offset + size].decode("utf-8")
        offset += size
        return value
    material_count = i32()
    materials = []
    for _ in range(material_count):
        materials.append((string(), string(), string(), bool(data[offset])))
        offset += 1
    bones_count = i32()
    if bones_count != 1:
        raise ValueError("Expected the imported sword to have one local geometry bone")
    string()
    if i32() != -1:
        raise ValueError("The imported sword's root bone should not have a parent")
    offset += 7 * 4
    batch_count = i32()
    batches = []
    for _ in range(batch_count):
        material_index, vertex_count = i32(), i32()
        vertices = []
        for _ in range(vertex_count):
            values = struct.unpack_from(">8fI", data, offset)
            offset += 36
            vertices.append(values)
        batches.append((material_index, vertices))
    return materials, batches

sword_materials, sword_batches = read_sword(SWORD_MESH)
sam_material_count = max(1, len(mesh.data.materials))
materials = [{"name": "sam_pmx_" + str(index), "texture": "textures/imported/sam_mmd_body.png", "emissive": "", "fullbright": False}
             for index in range(sam_material_count)]
materials.extend({"name": name, "texture": texture, "emissive": emissive, "fullbright": bright}
                 for name, texture, emissive, bright in sword_materials)
for side in ("right", "left"):
    parent = rig[side + "_hand"]
    sword = Bone(side + "_blade", parent, (parent.world + source_pivots["中指１" + (".R" if side == "right" else ".L")]) * .5)
    bones.append(sword)
    rig[sword.name] = sword
    sword_index = bone_indices[sword.name] = len(bones) - 1
    for material_index, vertices in sword_batches:
        out = material_vertices.setdefault(sam_material_count + material_index, [])
        for start in range(0, len(vertices), 4):
            face = []
            for vertex in vertices[start:start + 3]:
                pos = sword.world + Vector((vertex[0], vertex[1] - .26, vertex[2]))
                face.append((tuple(pos), (vertex[3], vertex[4]), tuple(vertex[5:8]), [(sword_index, 1.0)]))
            if len(face) == 3:
                out.extend((face[0], face[1], face[2], face[2]))

if max(material_vertices) >= len(materials):
    raise ValueError("A source mesh refers to a missing material")
with MODEL_PATH.open("wb") as stream:
    def write_int(value):
        stream.write(struct.pack(">i", value))
    def write_string(value):
        encoded = value.encode("utf-8")
        stream.write(struct.pack(">H", len(encoded)))
        stream.write(encoded)
    def write_floats(values):
        stream.write(struct.pack(">" + str(len(values)) + "f", *values))
    stream.write(b"FMSH")
    write_int(3)
    write_int(len(materials))
    for material in materials:
        write_string(material["name"])
        write_string(material["texture"])
        write_string(material["emissive"])
        stream.write(bytes([material["fullbright"]]))
    write_int(len(bones))
    for bone in bones:
        write_string(bone.name)
        write_int(bones.index(bone.parent) if bone.parent else -1)
        pivot_local = bone.world - bone.parent.world if bone.parent else bone.world
        write_floats(pivot_local)
        write_floats((0, 0, 0, 1))
        write_int(0)  # v3 vertices live in material batches and are smoothly skinned.
    write_int(sum(bool(material_vertices.get(index)) for index in range(len(materials))))
    for material_index in range(len(materials)):
        vertices = material_vertices.get(material_index, [])
        if not vertices:
            continue
        write_int(material_index)
        write_int(len(vertices))
        for pos, uv, normal, influences in vertices:
            write_floats((*pos, *uv, *normal))
            stream.write(struct.pack(">I", 0xFFFFFFFF))
            influences = sorted(influences, key=lambda value: value[1], reverse=True)[:4]
            total = sum(weight for _, weight in influences)
            padded = [(index, weight / total) for index, weight in influences]
            padded.extend([(0, 0.0)] * (4 - len(padded)))
            for index, weight in padded:
                stream.write(struct.pack(">Hf", index, weight))

summary = {
    "source_model": str(SOURCE), "source_texture": str(source_texture),
    "sam_mesh_vertices": len(mesh.data.vertices), "sam_triangles": len(mesh.data.loop_triangles),
    "rig_bones": len(bones), "sam_height_source": ceiling - floor, "runtime_height": 3.65,
    "skinning": "four-influence normalized PMX vertex weights", "source_sword": str(SWORD_MESH),
    "sword_triangles_per_hand": sum(len(vertices) // 4 for _, vertices in sword_batches),
    "runtime_mesh": str(MODEL_PATH),
}
(ROOT / "artifacts/sam-pmx-import.json").write_text(json.dumps(summary, indent=2) + "\n", encoding="utf-8")
print(json.dumps(summary, indent=2))
