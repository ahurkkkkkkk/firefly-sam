"""Retarget Quaternius's CC0 humanoid walk loop to the supplied SAM rig.

Run with Blender's Python runtime:
  blender -b --python scripts/bake_walk_cycle.py

The output is a compact, sampled resource consumed by SamWalkCycle at runtime.
"""
import json
import math
import os
from pathlib import Path
import struct
import sys

from mathutils import Matrix, Quaternion, Vector

ROOT = Path(__file__).resolve().parents[1]
ASSET_DIR = ROOT / "artifacts/animations/quaternius-ual"
SOURCE = ASSET_DIR / "AnimationLibrary_Godot_Standard.gltf"
SOURCE_BIN = ASSET_DIR / "AnimationLibrary_Godot_Standard.bin"
RIG = ROOT / "src/main/resources/assets/firefly_sam/models/sam_rig.fmesh"
OUTPUT = Path(os.environ.get(
    "SAM_WALK_OUTPUT",
    str(ROOT / "src/main/resources/assets/firefly_sam/walk_data/sam_walk_cycle.json"),
))
CLIP_NAME = os.environ.get("SAM_WALK_CLIP", "Walk_Loop")
SAMPLE_RATE = 30
SAM_HEIGHT = 3.65

# The original walk is kept full-strength in SAM's hips and legs. Sword-bearing
# arms borrow only a little of the source swing, keeping the grips readable.
TARGET_SOURCE = {
    "hips": ("DEF-hips", 0.90),
    "torso": ("DEF-spine.003", 0.82),
    "head": ("DEF-head", 0.35),
    "right_arm": ("DEF-upper_arm.L", 0.18),
    "right_forearm": ("DEF-forearm.L", 0.16),
    "right_hand": ("DEF-hand.L", 0.10),
    "right_leg": ("DEF-thigh.L", 1.0),
    "right_shin": ("DEF-shin.L", 1.0),
    "right_foot": ("DEF-foot.L", 0.72),
    "left_arm": ("DEF-upper_arm.R", 0.18),
    "left_forearm": ("DEF-forearm.R", 0.16),
    "left_hand": ("DEF-hand.R", 0.10),
    "left_leg": ("DEF-thigh.R", 1.0),
    "left_shin": ("DEF-shin.R", 1.0),
    "left_foot": ("DEF-foot.R", 0.72),
}


def unpack_utf(data, offset):
    length = struct.unpack_from(">H", data, offset)[0]
    offset += 2
    return data[offset:offset + length].decode("utf-8"), offset + length


def read_target_rig(path):
    data = path.read_bytes()
    if data[:4] != b"FMSH" or struct.unpack_from(">I", data, 4)[0] not in (2, 3):
        raise ValueError(f"Unsupported SAM rig: {path}")
    offset = 8
    material_count = struct.unpack_from(">I", data, offset)[0]
    offset += 4
    for _ in range(material_count):
        for _ in range(3):
            _, offset = unpack_utf(data, offset)
        offset += 1
    count = struct.unpack_from(">I", data, offset)[0]
    offset += 4
    bones = []
    for _ in range(count):
        name, offset = unpack_utf(data, offset)
        parent = struct.unpack_from(">i", data, offset)[0]
        offset += 4
        offset += 3 * 4  # pivot
        x, y, z, w = struct.unpack_from(">4f", data, offset)
        offset += 4 * 4
        batch_count = struct.unpack_from(">I", data, offset)[0]
        offset += 4
        for _ in range(batch_count):
            offset += 4  # material index
            vertices = struct.unpack_from(">I", data, offset)[0]
            offset += 4 + vertices * 36  # eight floats and an int color
        bones.append({"name": name, "parent": parent, "bind": Quaternion((w, x, y, z))})
    return bones


def read_accessor(document, binary, index):
    accessor = document["accessors"][index]
    view = document["bufferViews"][accessor["bufferView"]]
    component_type = accessor["componentType"]
    component_count = {"SCALAR": 1, "VEC2": 2, "VEC3": 3, "VEC4": 4}[accessor["type"]]
    format_code = {5126: "f", 5123: "H", 5125: "I"}[component_type]
    component_size = struct.calcsize("<" + format_code)
    stride = view.get("byteStride", component_size * component_count)
    base = view.get("byteOffset", 0) + accessor.get("byteOffset", 0)
    values = []
    for i in range(accessor["count"]):
        components = struct.unpack_from(
            "<" + format_code * component_count,
            binary,
            base + i * stride,
        )
        values.append(components[0] if component_count == 1 else tuple(components))
    return values


def load_clip():
    document = json.loads(SOURCE.read_text(encoding="utf-8"))
    binary = SOURCE_BIN.read_bytes()
    animation = next(item for item in document["animations"] if item["name"] == CLIP_NAME)
    tracks = {}
    duration = 0.0
    for channel in animation["channels"]:
        sampler = animation["samplers"][channel["sampler"]]
        node_name = document["nodes"][channel["target"]["node"]]["name"]
        times = read_accessor(document, binary, sampler["input"])
        values = read_accessor(document, binary, sampler["output"])
        tracks[(node_name, channel["target"]["path"])] = (times, values, sampler.get("interpolation", "LINEAR"))
        duration = max(duration, float(times[-1]))
    return document, tracks, duration


def node_hierarchy(document):
    parents = [-1] * len(document["nodes"])
    for index, node in enumerate(document["nodes"]):
        for child in node.get("children", []):
            parents[child] = index
    return parents


def node_rotation(document, index):
    node = document["nodes"][index]
    value = node.get("rotation", (0, 0, 0, 1))
    return Quaternion((value[3], value[0], value[1], value[2]))


def sample_track(track, time, quaternion=False):
    if track is None:
        return None
    times, values, interpolation = track
    if time <= times[0]:
        value = values[0]
        return Quaternion((value[3], value[0], value[1], value[2])) if quaternion else Vector(value)
    if time >= times[-1]:
        value = values[-1]
        return Quaternion((value[3], value[0], value[1], value[2])) if quaternion else Vector(value)
    right = next(index for index, value in enumerate(times) if value >= time)
    left = right - 1
    if interpolation == "STEP":
        value = values[left]
        return Quaternion((value[3], value[0], value[1], value[2])) if quaternion else Vector(value)
    if interpolation != "LINEAR":
        raise ValueError(f"Unsupported glTF interpolation mode: {interpolation}")
    fraction = (time - times[left]) / (times[right] - times[left])
    a, b = values[left], values[right]
    if quaternion:
        q1 = Quaternion((a[3], a[0], a[1], a[2]))
        q2 = Quaternion((b[3], b[0], b[1], b[2]))
        return q1.slerp(q2, fraction)
    return Vector(a).lerp(Vector(b), fraction)


def local_matrix(document, tracks, index, time, animate):
    node = document["nodes"][index]
    base_t = node.get("translation", (0, 0, 0))
    base_s = node.get("scale", (1, 1, 1))
    t_track = tracks.get((node.get("name"), "translation")) if animate else None
    r_track = tracks.get((node.get("name"), "rotation")) if animate else None
    translation = sample_track(t_track, time) if t_track else Vector(base_t)
    rotation = sample_track(r_track, time, quaternion=True) if r_track else node_rotation(document, index)
    scale = Vector(base_s)
    return Matrix.Translation(translation) @ rotation.to_matrix().to_4x4() @ Matrix.Diagonal((*scale, 1.0))


def source_world(document, tracks, parents, time, animate):
    world = [None] * len(document["nodes"])
    def resolve(index):
        if world[index] is not None:
            return world[index]
        local = local_matrix(document, tracks, index, time, animate)
        parent = parents[index]
        world[index] = (resolve(parent) @ local) if parent >= 0 else local
        return world[index]
    for index in range(len(world)):
        resolve(index)
    return world


def source_world_rotation(document, tracks, parents, time):
    world = [None] * len(document["nodes"])
    def resolve(index):
        if world[index] is not None:
            return world[index]
        node = document["nodes"][index]
        track = tracks.get((node.get("name"), "rotation"))
        local = sample_track(track, time, quaternion=True) if track else node_rotation(document, index)
        parent = parents[index]
        world[index] = (resolve(parent) @ local) if parent >= 0 else local
        return world[index]
    for index in range(len(world)):
        resolve(index)
    return world


def to_json_euler(quaternion, previous):
    # SamMesh applies Z, then Y, then X, so ZYX Euler order matches its pose stack.
    euler = quaternion.to_euler("ZYX")
    if previous is not None:
        euler.make_compatible(previous)
    return euler


def main():
    document, tracks, duration = load_clip()
    parents = node_hierarchy(document)
    node_by_name = {node.get("name"): i for i, node in enumerate(document["nodes"]) if node.get("name")}
    target_bones = read_target_rig(RIG)
    target_index = {bone["name"]: i for i, bone in enumerate(target_bones)}
    target_rest_world = []
    for i, bone in enumerate(target_bones):
        parent = bone["parent"]
        target_rest_world.append((target_rest_world[parent] @ bone["bind"]) if parent >= 0 else bone["bind"])
    name_to_node = {name: node_by_name[source_name] for name, (source_name, _) in TARGET_SOURCE.items()}
    for source_name in (value[0] for value in TARGET_SOURCE.values()):
        if source_name not in node_by_name:
            raise ValueError(f"Source skeleton is missing {source_name}")
    if not all(name in target_index for name in TARGET_SOURCE):
        raise ValueError("SAM rig is missing a mapped bone")

    source_rest = source_world_rotation(document, tracks, parents, 0.0)
    rest_matrices = source_world(document, tracks, parents, 0.0, False)
    animated_matrices = source_world(document, tracks, parents, 0.0, True)
    foot_nodes = [node_by_name["DEF-toe.L"], node_by_name["DEF-toe.R"]]
    source_top = rest_matrices[node_by_name["DEF-head"]].translation.y
    source_floor = min(rest_matrices[index].translation.y for index in foot_nodes)
    source_height = source_top - source_floor
    if source_height <= 0.1:
        raise ValueError(f"Unexpected source rig height: {source_height}")
    unit_scale = SAM_HEIGHT / source_height
    hip_node = node_by_name["DEF-hips"]
    rest_hip_y = rest_matrices[hip_node].translation.y
    frame_count = round(duration * SAMPLE_RATE)
    frames = []
    previous = {}
    for frame in range(frame_count + 1):
        time = duration * frame / frame_count
        source_anim = source_world_rotation(document, tracks, parents, time)
        target_world_anim = []
        pose_values = {}
        for index, bone in enumerate(target_bones):
            parent = bone["parent"]
            parent_anim = target_world_anim[parent] if parent >= 0 else Quaternion()
            mapped = TARGET_SOURCE.get(bone["name"])
            if mapped:
                source_name, weight = mapped
                source_index = name_to_node[bone["name"]]
                delta_world = source_anim[source_index] @ source_rest[source_index].inverted()
                delta_world.normalize()
                delta_world = Quaternion().slerp(delta_world, weight)
                desired_world = delta_world @ target_rest_world[index]
                local_pose = bone["bind"].inverted() @ parent_anim.inverted() @ desired_world
                local_pose.normalize()
                euler = to_json_euler(local_pose, previous.get(bone["name"]))
                previous[bone["name"]] = euler.copy()
                pose_values[bone["name"]] = [round(float(value), 7) for value in euler]
                target_world_anim.append(parent_anim @ bone["bind"] @ local_pose)
            else:
                target_world_anim.append(parent_anim @ bone["bind"])
        hips_matrix = source_world(document, tracks, parents, time, True)[hip_node]
        hip_y = (hips_matrix.translation.y - rest_hip_y) * unit_scale
        frames.append({"time": round(time, 7), "bones": pose_values, "hip_y": round(hip_y, 7)})

    result = {
        "format": 1,
        "clip": CLIP_NAME,
        "source": "Quaternius Universal Animation Library, Standard (2025-06-10 distribution)",
        "license": "CC0-1.0",
        "duration": round(duration, 7),
        "sample_rate": SAMPLE_RATE,
        "source_height": round(source_height, 7),
        "target_height": SAM_HEIGHT,
        "unit_scale": round(unit_scale, 7),
        "retargeted_bones": list(TARGET_SOURCE),
        "frames": frames,
    }
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_text(json.dumps(result, separators=(",", ":")) + "\n", encoding="utf-8")
    print(json.dumps({
        "output": str(OUTPUT), "clip": CLIP_NAME, "duration": duration,
        "sample_rate": SAMPLE_RATE, "frames": len(frames), "source_height": source_height,
        "unit_scale": unit_scale, "retargeted_bones": list(TARGET_SOURCE),
        "hip_y_min": min(frame["hip_y"] for frame in frames),
        "hip_y_max": max(frame["hip_y"] for frame in frames),
    }, indent=2))


main()
