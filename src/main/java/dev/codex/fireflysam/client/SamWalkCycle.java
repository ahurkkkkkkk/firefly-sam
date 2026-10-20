package dev.codex.fireflysam.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.codex.fireflysam.FireflySam;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import org.joml.Vector3f;

/** Samples the baked Quaternius walk loop, retargeted to SAM's articulated rig. */
public final class SamWalkCycle {
    private static final String CLIP = "Walk_Loop";
    private static volatile WalkData cached;
    private static volatile boolean loadFailed;

    private SamWalkCycle() {}

    public record Sample(Map<String, Vector3f> bones, float hipY) {
        public Vector3f rotation(String bone) {
            Vector3f value = bones.get(bone);
            return value == null ? new Vector3f() : value;
        }
    }

    private record Frame(float time, Map<String, Vector3f> bones, float hipY) {}
    private record WalkData(float duration, int sampleRate, List<Frame> frames) {}

    public static String clipName() { return CLIP; }

    public static int retargetedBoneCount() {
        WalkData data = load();
        return data == null || data.frames().isEmpty() ? 0 : data.frames().get(0).bones().size();
    }

    public static boolean available() {
        WalkData data = load();
        return data != null && data.frames().size() >= 2;
    }

    public static Sample sample(float phase) {
        WalkData data = load();
        if (data == null || data.frames().size() < 2) return new Sample(Map.of(), 0);

        float loopPhase = phase - Mth.floor(phase);
        float time = loopPhase * data.duration();
        int uniqueFrames = data.frames().size() - 1; // the last baked frame duplicates the loop start
        float framePosition = Math.min(time * data.sampleRate(), uniqueFrames - 0.0001f);
        int firstIndex = Mth.floor(framePosition);
        int secondIndex = (firstIndex + 1) % uniqueFrames;
        float amount = framePosition - firstIndex;
        Frame first = data.frames().get(firstIndex), second = data.frames().get(secondIndex);
        Frame before = data.frames().get((firstIndex - 1 + uniqueFrames) % uniqueFrames);
        Frame after = data.frames().get((secondIndex + 1) % uniqueFrames);
        Map<String, Vector3f> rotations = new HashMap<>(first.bones().size());
        for (var entry : first.bones().entrySet()) {
            Vector3f p0 = before.bones().get(entry.getKey()), a = entry.getValue();
            Vector3f b = second.bones().get(entry.getKey()), p3 = after.bones().get(entry.getKey());
            if (p0 == null || b == null || p3 == null) continue;
            rotations.put(entry.getKey(), new Vector3f(
                catmull(p0.x, a.x, b.x, p3.x, amount),
                catmull(p0.y, a.y, b.y, p3.y, amount),
                catmull(p0.z, a.z, b.z, p3.z, amount)));
        }
        float centerY = 0;
        for (int index = 0; index < uniqueFrames; index++) centerY += data.frames().get(index).hipY();
        centerY /= uniqueFrames;
        return new Sample(rotations, catmull(before.hipY(), first.hipY(), second.hipY(), after.hipY(), amount) - centerY);
    }

    private static float catmull(float p0, float p1, float p2, float p3, float t) {
        float t2 = t * t, t3 = t2 * t;
        return .5f * ((2 * p1) + (-p0 + p2) * t
            + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t2
            + (-p0 + 3 * p1 - 3 * p2 + p3) * t3);
    }

    private static WalkData load() {
        if (loadFailed) return null;
        WalkData data = cached;
        if (data != null) return data;
        synchronized (SamWalkCycle.class) {
            data = cached;
            if (data != null) return data;
            try (var resource = Minecraft.getInstance().getResourceManager()
                    .open(FireflySam.id("animations/sam_walk_cycle.json"));
                 var reader = new BufferedReader(new InputStreamReader(resource, StandardCharsets.UTF_8))) {
                JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                if (root.get("format").getAsInt() != 1 || !CLIP.equals(root.get("clip").getAsString())) {
                    throw new IllegalArgumentException("Unexpected SAM walk cycle resource format");
                }
                float duration = root.get("duration").getAsFloat();
                int sampleRate = root.get("sample_rate").getAsInt();
                JsonArray sourceFrames = root.getAsJsonArray("frames");
                List<Frame> frames = new ArrayList<>(sourceFrames.size());
                for (var element : sourceFrames) {
                    JsonObject sourceFrame = element.getAsJsonObject();
                    Map<String, Vector3f> bones = new HashMap<>();
                    for (var boneEntry : sourceFrame.getAsJsonObject("bones").entrySet()) {
                        JsonArray rotation = boneEntry.getValue().getAsJsonArray();
                        bones.put(boneEntry.getKey(), new Vector3f(
                            rotation.get(0).getAsFloat(), rotation.get(1).getAsFloat(), rotation.get(2).getAsFloat()));
                    }
                    frames.add(new Frame(sourceFrame.get("time").getAsFloat(), bones,
                        sourceFrame.get("hip_y").getAsFloat()));
                }
                if (duration <= 0 || sampleRate <= 0 || frames.size() < 2) {
                    throw new IllegalArgumentException("The SAM walk cycle has no usable frames");
                }
                data = new WalkData(duration, sampleRate, List.copyOf(frames));
                cached = data;
                FireflySam.LOGGER.log(System.Logger.Level.INFO,
                    "Loaded retargeted {0} walk cycle: {1} frames at {2} Hz across {3} SAM bones",
                    CLIP, frames.size(), sampleRate, frames.get(0).bones().size());
                return data;
            } catch (Exception error) {
                FireflySam.LOGGER.log(System.Logger.Level.ERROR, "Could not load retargeted SAM walk cycle", error);
                // Remember failure so a bad pack does not spam the client log every render frame.
                loadFailed = true;
                return null;
            }
        }
    }
}
