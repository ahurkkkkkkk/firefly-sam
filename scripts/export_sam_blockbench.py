"""Build local GeckoLib resources and an editable Blockbench skeleton project.

The BBModel/geo files describe only the target rig; they are generated locally
from the user-provided PMX and must not be redistributed with the public code.
"""
import json
import math
import struct
import uuid
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ASSET_ROOT = ROOT / "src/main/resources/assets/firefly_sam"
RIG_PATH = ASSET_ROOT / "models/sam_rig.fmesh"
WALK_PATH = ASSET_ROOT / "walk_data/sam_walk_cycle.json"
SPRINT_PATH = ROOT / "artifacts/animations/quaternius-ual/sam_sprint_cycle.json"
GEO_PATH = ASSET_ROOT / "geo/sam_rig.geo.json"
ANIM_PATH = ASSET_ROOT / "animations/sam_rig.animation.json"
BBMODEL_PATH = ROOT / "artifacts/Blockbench/SAM_rig.bbmodel"

def read_string(data, offset):
    size = struct.unpack_from(">H", data, offset)[0]
    offset += 2
    return data[offset:offset + size].decode("utf-8"), offset + size

def read_rig():
    data = RIG_PATH.read_bytes()
    if data[:4] != b"FMSH" or struct.unpack_from(">I", data, 4)[0] != 3:
        raise ValueError("SAM mesh must be generated as weighted FMSH v3 first")
    offset = 8
    material_count = struct.unpack_from(">I", data, offset)[0]
    offset += 4
    for _ in range(material_count):
        for _ in range(3):
            _, offset = read_string(data, offset)
        offset += 1
    bone_count = struct.unpack_from(">I", data, offset)[0]
    offset += 4
    bones = []
    for _ in range(bone_count):
        name, offset = read_string(data, offset)
        parent = struct.unpack_from(">i", data, offset)[0]
        offset += 4
        pivot = struct.unpack_from(">3f", data, offset)
        offset += 12 + 16
        batch_count = struct.unpack_from(">I", data, offset)[0]
        offset += 4
        if batch_count:
            raise ValueError("FMSH v3 bone batches must be empty")
        bones.append({"name": name, "parent": parent, "pivot": tuple(pivot)})
    return bones, data, offset

def radians_to_degrees(value):
    return [round(math.degrees(component), 5) for component in value]

def base_pose():
    return {
        "root": [0, 0, 0], "hips": [0, 0, 0], "torso": [0, 0, 0], "head": [0, 0, 0],
        "right_arm": [0, 0, .65], "right_forearm": [0, 0, .20], "right_hand": [-.20, 0, .05],
        "left_arm": [0, 0, -.65], "left_forearm": [0, 0, -.20], "left_hand": [-.20, 0, -.05],
        "right_leg": [0, 0, 0], "right_shin": [0, 0, 0], "right_foot": [0, 0, 0],
        "left_leg": [0, 0, 0], "left_shin": [0, 0, 0], "left_foot": [0, 0, 0],
        "right_blade": [-math.pi / 2, 0, 0], "left_blade": [-math.pi / 2, 0, 0],
    }

def read_cycle(path):
    value = json.loads(path.read_text(encoding="utf-8"))
    return float(value["duration"]), value["frames"]

def merge_cycle(path, mirror=False, shift=0.0, lean=0.0):
    duration, source_frames = read_cycle(path)
    frame_count = len(source_frames) - 1
    output = []
    for frame in range(frame_count):
        time = duration * frame / frame_count
        sample_time = (time + shift * duration) % duration
        position = sample_time / duration * frame_count
        before = int(position) % frame_count
        after = (before + 1) % frame_count
        alpha = position - int(position)
        source_before = source_frames[before]["bones"]
        source_after = source_frames[after]["bones"]
        rotations = base_pose()
        for bone in rotations:
            source_name = bone
            if mirror:
                if bone.startswith("right_"):
                    source_name = "left_" + bone[6:]
                elif bone.startswith("left_"):
                    source_name = "right_" + bone[5:]
            a, b = source_before.get(source_name, [0, 0, 0]), source_after.get(source_name, [0, 0, 0])
            retarget = [a[index] + (b[index] - a[index]) * alpha for index in range(3)]
            strength = .35 if bone in ("right_leg", "left_leg") else .22 if bone in ("right_shin", "left_shin") \
                else .7 if bone in ("right_foot", "left_foot") else .75 if bone in ("hips", "torso") \
                else .4 if bone == "head" else 1.0
            if mirror:
                retarget = [retarget[0], -retarget[1], -retarget[2]]
            rotations[bone] = [rotations[bone][i] + retarget[i] * strength for i in range(3)]
        rotations["torso"][0] += lean
        rotations["torso"][1] += (0.08 if mirror else -0.08) * math.sin(2 * math.pi * time / duration)
        rotations["hips"][1] += (0.06 if mirror else -0.06) * math.sin(2 * math.pi * time / duration)
        output.append((time, rotations))
    output.append((duration, {name: list(value) for name, value in output[0][1].items()}))
    return duration, output

def pose(**updates):
    result = base_pose()
    for name, values in updates.items():
        result[name] = list(values)
    return result

def attack_clip(name, frames):
    return {"length": .6, "loop": False, "frames": [(time, pose(**values)) for time, values in frames]}

def original_attacks():
    right = attack_clip("attack_right", [
        (0.00, {"torso": [0, -.16, -.03], "right_arm": [-.20, -.12, .36], "right_forearm": [.06, -.05, .05], "right_hand": [-.25, .03, .04], "right_blade": [-1.34, -.30, .62], "right_leg": [.12, 0, .02], "left_leg": [-.08, 0, -.02]}),
        (.10, {"torso": [-.06, -.26, -.04], "right_arm": [-.45, -.10, .57], "right_forearm": [.30, -.04, .12], "right_hand": [-.34, .02, .08], "right_blade": [-1.65, -.08, .78], "right_leg": [.20, 0, .04], "left_leg": [-.10, 0, -.03]}),
        (.22, {"torso": [.03, .22, .06], "right_arm": [.18, .08, -.12], "right_forearm": [-.22, .04, -.18], "right_hand": [-.12, -.02, -.10], "right_blade": [-1.03, .32, -.44], "right_leg": [-.16, 0, -.04], "left_leg": [.08, 0, .04]}),
        (.38, {"torso": [.02, .10, .02], "right_arm": [.12, .03, .12], "right_forearm": [-.12, 0, -.04], "right_blade": [-1.30, .14, -.16], "right_leg": [-.06, 0, 0], "left_leg": [.04, 0, 0]}),
        (.60, {}),
    ])
    left = attack_clip("attack_left", [
        (0.00, {"torso": [0, .16, .03], "left_arm": [-.20, .12, -.36], "left_forearm": [.06, .05, -.05], "left_hand": [-.25, -.03, -.04], "left_blade": [-1.34, .30, -.62], "right_leg": [-.08, 0, .02], "left_leg": [.12, 0, -.02]}),
        (.10, {"torso": [-.06, .26, .04], "left_arm": [-.45, .10, -.57], "left_forearm": [.30, .04, -.12], "left_hand": [-.34, -.02, -.08], "left_blade": [-1.65, .08, -.78], "right_leg": [-.10, 0, .03], "left_leg": [.20, 0, -.04]}),
        (.22, {"torso": [.03, -.22, -.06], "left_arm": [.18, -.08, .12], "left_forearm": [-.22, -.04, .18], "left_hand": [-.12, .02, .10], "left_blade": [-1.03, -.32, .44], "right_leg": [.08, 0, -.04], "left_leg": [-.16, 0, .04]}),
        (.38, {"torso": [.02, -.10, -.02], "left_arm": [.12, -.03, -.12], "left_forearm": [-.12, 0, .04], "left_blade": [-1.30, -.14, .16], "right_leg": [.04, 0, 0], "left_leg": [-.06, 0, 0]}),
        (.60, {}),
    ])
    pair = attack_clip("attack_pair", [
        (0.00, {"torso": [0, 0, 0], "right_arm": [-.20, -.12, .36], "left_arm": [-.20, .12, -.36], "right_forearm": [.06, -.05, .05], "left_forearm": [.06, .05, -.05], "right_blade": [-1.34, -.30, .62], "left_blade": [-1.34, .30, -.62], "right_leg": [.12, 0, .02], "left_leg": [.12, 0, -.02]}),
        (.10, {"torso": [-.10, 0, 0], "right_arm": [-.44, -.12, .55], "left_arm": [-.44, .12, -.55], "right_forearm": [.24, -.05, .12], "left_forearm": [.24, .05, -.12], "right_blade": [-1.58, -.10, .78], "left_blade": [-1.58, .10, -.78], "right_leg": [.18, 0, .05], "left_leg": [.18, 0, -.05]}),
        (.23, {"torso": [.05, 0, 0], "right_arm": [.14, .06, -.10], "left_arm": [.14, -.06, .10], "right_forearm": [-.20, .03, -.18], "left_forearm": [-.20, -.03, .18], "right_blade": [-1.00, .30, -.40], "left_blade": [-1.00, -.30, .40], "right_leg": [-.18, 0, -.04], "left_leg": [-.18, 0, .04]}),
        (.40, {"torso": [.02, 0, 0], "right_arm": [.10, .02, .10], "left_arm": [.10, -.02, -.10], "right_forearm": [-.10, 0, -.04], "left_forearm": [-.10, 0, .04], "right_blade": [-1.28, .12, -.12], "left_blade": [-1.28, -.12, .12]}),
        (.60, {}),
    ])
    return {"attack_right": right, "attack_left": left, "attack_pair": pair}

def ultimate_clip():
    length = 8.0
    frames = [
        (0.0, pose(torso=[-.04, 0, 0], right_arm=[-.25, 0, .45], left_arm=[-.25, 0, -.45], right_blade=[-math.pi/2, 0, 0], left_blade=[-math.pi/2, 0, 0])),
        (.6, pose(torso=[-.02, 0, 0], right_arm=[-.15, 0, .95], left_arm=[-.15, 0, -.95], right_forearm=[-.10, 0, .14], left_forearm=[-.10, 0, -.14], right_blade=[-math.pi/2, 0, .12], left_blade=[-math.pi/2, 0, -.12])),
        (1.7, pose(torso=[.02, 0, 0], right_arm=[-.05, 0, 1.05], left_arm=[-.05, 0, -1.05], right_forearm=[-.08, 0, .12], left_forearm=[-.08, 0, -.12], right_blade=[-math.pi/2, 0, 0], left_blade=[-math.pi/2, 0, 0])),
        (4.3, pose(torso=[-.10, 0, 0], right_arm=[-.45, 0, .50], left_arm=[-.45, 0, -.50], right_forearm=[-.20, 0, .20], left_forearm=[-.20, 0, -.20], right_leg=[.18, 0, .03], left_leg=[-.12, 0, -.03], right_shin=[.24, 0, 0], left_shin=[.20, 0, 0], right_blade=[-1.25, -.10, .40], left_blade=[-1.25, .10, -.40])),
        (5.6, pose(torso=[.62, 0, 0], right_arm=[1.45, 0, -.55], left_arm=[1.45, 0, .55], right_forearm=[-.18, 0, .18], left_forearm=[-.18, 0, -.18], right_leg=[-.34, 0, -.03], left_leg=[.24, 0, .03], right_shin=[0, 0, 0], left_shin=[0, 0, 0], right_blade=[-math.pi/2, 0, -.18], left_blade=[-math.pi/2, 0, .18])),
        (6.6, pose(torso=[.60, 0, 0], right_arm=[1.20, 0, -.35], left_arm=[1.20, 0, .35], right_leg=[-.30, 0, -.03], left_leg=[.22, 0, .03], right_blade=[-math.pi/2, 0, -.12], left_blade=[-math.pi/2, 0, .12])),
        (7.05, pose(torso=[.35, 0, 0], right_arm=[.10, 0, .65], left_arm=[.10, 0, -.65], right_leg=[0, 0, 0], left_leg=[0, 0, 0])),
        (length, base_pose()),
    ]
    return {"ultimate": {"length": length, "loop": False, "frames": frames}}

def frames_to_clip(frames, length, loop):
    bones = {}
    names = sorted({name for _, frame in frames for name in frame})
    for name in names:
        rotations = {}
        for time, frame in frames:
            values = radians_to_degrees(frame.get(name, [0, 0, 0]))
            rotations[round(time, 6)] = {"vector": values, "easing": "catmullrom"}
        bones[name] = {"rotation": {str(time): value for time, value in rotations.items()}}
    return {"animation_length": round(length, 6), "loop": loop, "bones": bones}

def outliner_tree(bones, ids):
    children = {index: [] for index in range(len(bones))}
    roots = []
    for index, bone in enumerate(bones):
        if bone["parent"] < 0:
            roots.append(index)
        else:
            children[bone["parent"]].append(index)
    def build(index):
        bone = bones[index]
        parent_pivot = [0, 0, 0] if bone["parent"] < 0 else bones[bone["parent"]]["world"]
        origin = [round(value * 16, 4) for value in bone["world"]]
        return {"name": bone["name"], "origin": origin, "color": index % 8, "uuid": ids[bone["name"]],
                "export": True, "mirror_uv": False, "isOpen": True, "locked": False,
                "visibility": True, "autouv": 0, "children": [build(child) for child in children[index]]}
    return [build(index) for index in roots]

def main():
    if not WALK_PATH.exists():
        raise FileNotFoundError("Run scripts/bake_walk_cycle.py first")
    bones, mesh_data, mesh_offset = read_rig()
    walk_duration, walk_frames = merge_cycle(WALK_PATH)
    clips = {"idle": {"length": 1.0, "loop": True, "frames": [(0.0, base_pose()), (1.0, base_pose())]},
             "walk": {"length": walk_duration, "loop": True, "frames": walk_frames}}
    if SPRINT_PATH.exists():
        sprint_duration, sprint_frames = merge_cycle(SPRINT_PATH)
        clips["sprint"] = {"length": sprint_duration, "loop": True, "frames": sprint_frames}
    clips["backpedal"] = {"length": walk_duration, "loop": True, "frames": merge_cycle(WALK_PATH, mirror=True, shift=.5, lean=-.08)[1]}
    clips["strafe_left"] = {"length": walk_duration, "loop": True, "frames": merge_cycle(WALK_PATH, mirror=True, shift=.25, lean=.05)[1]}
    clips["strafe_right"] = {"length": walk_duration, "loop": True, "frames": merge_cycle(WALK_PATH, shift=.25, lean=.05)[1]}
    # Solve the ankle path in SAM's proportions. During support the foot
    # travels backward at ground speed. The return arc lifts only the free foot.
    for name in ("walk", "backpedal", "strafe_left", "strafe_right", "sprint"):
        clip = clips[name]
        half_step = .49 if name == "sprint" else .36
        thigh, shin = .843, .938
        for time, frame in clip["frames"]:
            for side, offset in (("right", 0), ("left", .5)):
                phase = (time / clip["length"] + offset) % 1
                if phase < .5:
                    travel = half_step * (1 - 4 * phase)
                    lift = 0
                else:
                    t = 2 * phase - 1
                    travel = half_step * (-1 - 2*t + 12*t*t - 8*t*t*t)
                    lift = (.115 if name == "sprint" else .075) * math.sin(math.pi*t)**2
                drop = thigh + shin - .09 - lift
                lateral = name.startswith("strafe")
                fore = 0 if lateral else travel * (-1 if name == "backpedal" else 1)
                roll = math.atan2(travel, drop) * (1 if name == "strafe_left" else -1) if lateral else 0
                vertical = math.hypot(drop, travel) if lateral else drop
                cosine = (fore*fore + vertical*vertical - thigh*thigh - shin*shin)/(2*thigh*shin)
                knee = -math.acos(max(-1, min(1, cosine)))
                hip = math.atan2(fore, vertical) - math.atan2(shin*math.sin(knee), thigh+shin*math.cos(knee))
                frame[side+"_leg"] = [-hip, 0, roll]
                frame[side+"_shin"] = [-knee, 0, 0]
                frame[side+"_foot"] = [hip+knee, 0, -roll]
            frame["hips"] = [0, 0, 0]
            frame["torso"] = [0, .035*math.sin(2*math.pi*time/clip["length"]), 0]
    clips.update(original_attacks())
    clips.update(ultimate_clip())
    # The PMX sword's blade runs from its palm grip along local +Y.  Its SAM
    # palm bone is authored with X=-90 degrees, which turns that axis backward
    # in Minecraft.  Add a half-turn about the rig's vertical Y axis so both
    # equipped blades point in the model's forward direction in every clip.
    for clip in clips.values():
        for _, frame in clip["frames"]:
            for blade in ("right_blade", "left_blade"):
                if blade in frame:
                    frame[blade][1] += math.pi
            for side in ("right", "left"):
                for joint in ("arm", "forearm", "hand"):
                    frame[side + "_" + joint][2] *= -1
    animation_json = {"format_version": "1.8.0", "animations": {
        "animation.sam." + name: frames_to_clip(clip["frames"], clip["length"], clip["loop"])
        for name, clip in clips.items()
    }, "geckolib_format_version": 2}

    GEO_PATH.parent.mkdir(parents=True, exist_ok=True)
    ANIM_PATH.parent.mkdir(parents=True, exist_ok=True)
    BBMODEL_PATH.parent.mkdir(parents=True, exist_ok=True)
    geo_bones = []
    world = []
    for bone in bones:
        parent = bone["parent"]
        world_pos = list(bone["pivot"]) if parent < 0 else [world[parent][i] + bone["pivot"][i] for i in range(3)]
        world.append(world_pos)
        geo = {"name": bone["name"], "pivot": [round(value * 16, 5) for value in world_pos], "cubes": []}
        if parent >= 0:
            geo["parent"] = bones[parent]["name"]
        geo_bones.append(geo)
        bone["world"] = world_pos
    geo_json = {"format_version": "1.12.0", "minecraft:geometry": [{
        "description": {"identifier": "geometry.firefly_sam.rig", "texture_width": 64, "texture_height": 64,
                        "visible_bounds_width": 5, "visible_bounds_height": 5, "visible_bounds_offset": [0, 1.8, 0]},
        "bones": geo_bones,
    }]}
    GEO_PATH.write_text(json.dumps(geo_json, indent=2) + "\n", encoding="utf-8")
    ANIM_PATH.write_text(json.dumps(animation_json, indent=2) + "\n", encoding="utf-8")

    ids = {bone["name"]: str(uuid.uuid4()) for bone in bones}
    bb_animations = []
    for name, clip in clips.items():
        animators = {}
        for bone_name in sorted({bone for _, frame in clip["frames"] for bone in frame}):
            keyframes = []
            for time, frame in clip["frames"]:
                value = radians_to_degrees(frame.get(bone_name, [0, 0, 0]))
                keyframes.append({"channel": "rotation", "data_points": [{"x": str(value[0]), "y": str(value[1]), "z": str(value[2])}],
                                  "uuid": str(uuid.uuid4()), "time": round(time, 6), "color": -1, "interpolation": "catmullrom"})
            animators[ids[bone_name]] = {"name": bone_name, "type": "bone", "keyframes": keyframes}
        bb_animations.append({"uuid": str(uuid.uuid4()), "name": name, "loop": "loop" if clip["loop"] else "once",
                              "override": False, "length": clip["length"], "snapping": 20, "selected": False,
                              "saved": True, "animators": animators})
    # Blockbench mesh groups preview the dominant bone of each triangle.
    # Minecraft uses the complete four-weight stream at the joints.
    groups = {}
    count, = struct.unpack_from(">I", mesh_data, mesh_offset)
    mesh_offset += 4
    for batch in range(count):
        material, corners = struct.unpack_from(">II", mesh_data, mesh_offset)
        mesh_offset += 8
        for _ in range(corners // 4):
            corners_at = [mesh_offset + i * 60 for i in range(3)]
            mesh_offset += 240
            weights = {}
            for at in corners_at:
                for i in range(4):
                    bone, weight = struct.unpack_from(">Hf", mesh_data, at + 36 + i * 6)
                    weights[bone] = weights.get(bone, 0) + weight
            owner = max(weights, key=weights.get)
            group = groups.setdefault((owner, material), {"type": "mesh", "name": bones[owner]["name"] + "_mesh",
                "origin": [0,0,0], "uuid": str(uuid.uuid4()), "vertices": {}, "faces": {}, "color": owner % 8})
            keys = []
            uv = {}
            for at in corners_at:
                x,y,z,u,v = struct.unpack_from(">5f", mesh_data, at)
                key = str(len(group["vertices"]))
                group["vertices"][key] = [round(c*16,4) for c in (x,y,z)]
                keys.append(key)
                uv[key] = [u*64,v*64]
            group["faces"][str(len(group["faces"]))] = {"vertices": keys, "uv": uv}
    tree = outliner_tree(bones, ids)
    def attach(node):
        for child in node["children"]:
            attach(child)
        node["children"].extend(g["uuid"] for (owner,_),g in groups.items() if bones[owner]["name"] == node["name"])
    for root in tree:
        attach(root)
    bbmodel = {
        "meta": {"format_version": "4.10", "model_format": "geckolib_model", "box_uv": False},
        "name": "SAM_rig", "model_identifier": "sam_rig", "geckolib_model_type": "Entity",
        "geckolib_format_version": 2, "visible_box": [5, 5, 2], "variable_placeholders": "",
        "variable_placeholder_buttons": [], "timeline_setups": [], "unhandled_root_fields": {},
        "resolution": {"width": 64, "height": 64}, "elements": list(groups.values()), "outliner": tree,
        "textures": [], "animations": bb_animations,
    }
    BBMODEL_PATH.write_text(json.dumps(bbmodel,separators=(",",":")) + "\n", encoding="utf-8")
    print(json.dumps({"model": str(GEO_PATH), "animations": str(ANIM_PATH), "blockbench_source": str(BBMODEL_PATH),
                      "bones": len(bones), "clips": list(clips), "walk_clip_source": "Quaternius UAL1 Walk_Loop (CC0)",
                      "sprint_clip_source": "Quaternius UAL1 Sprint_Loop (CC0)" if SPRINT_PATH.exists() else "not baked"}, indent=2))

if __name__ == "__main__":
    main()
