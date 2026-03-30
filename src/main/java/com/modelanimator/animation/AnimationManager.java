package com.modelanimator.animation;

import com.modelanimator.ModelAnimatorPlugin;
import com.modelanimator.display.DisplayModelManager;
import com.modelanimator.display.SpawnedModel;
import com.modelanimator.model.ManimModel;
import com.modelanimator.model.ManimModel.AnimationData;
import com.modelanimator.model.ManimModel.BoneAnimData;
import com.modelanimator.model.ManimModel.GroupData;
import com.modelanimator.model.ManimModel.KeyframeData;
import com.modelanimator.util.MathUtil;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.util.Transformation;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.*;

public class AnimationManager {

    private static final float TICK_DELTA = 1f / 20f;
    private static final float BB = 1f / 16f;

    private final ModelAnimatorPlugin plugin;
    private final DisplayModelManager displayManager;

    public AnimationManager(ModelAnimatorPlugin plugin) {
        this.plugin         = plugin;
        this.displayManager = plugin.getDisplayModelManager();
    }

    public void tick() {
        for (SpawnedModel spawned : displayManager.all()) {
            if (!spawned.isPlaying() || spawned.getCurrentAnimation() == null) continue;
            tickSpawnedModel(spawned);
        }
    }

    private void tickSpawnedModel(SpawnedModel spawned) {
        ManimModel def = spawned.getDefinition();
        AnimationData anim = findAnimation(def, spawned.getCurrentAnimation());
        if (anim == null) { spawned.setPlaying(false); return; }

        float t = spawned.getAnimationTime() - TICK_DELTA;
        if (anim.length > 0) {
            if (t > anim.length) { // forward overflow
                t = switch (anim.loop != null ? anim.loop : "once") {
                    case "loop" -> t % anim.length;
                    case "hold" -> anim.length;
                    default     -> { spawned.setPlaying(false); yield 0f; }
                };
            } else if (t < 0) { // reverse underflow
                t = switch (anim.loop != null ? anim.loop : "once") {
                    case "loop" -> (t % anim.length + anim.length) % anim.length; // wrap negative
                    case "hold" -> 0f;
                    default     -> { spawned.setPlaying(false); yield 0f; }
                };
            }
        }
        
        spawned.setAnimationTime(t);

        if (anim.bones == null || def.groups == null) return;

        // Build uuid → group map
        Map<String, GroupData> byUuid = new HashMap<>();
        for (GroupData g : def.groups) byUuid.put(g.uuid, g);

        // Compute world-space matrix for every bone, walking parent → child
        // Matrix = parent_matrix * local_transform
        // local_transform = translate(pivot) * rotate(anim) * translate(-pivot) * translate(animPos)
        Map<String, Matrix4f> boneWorldMatrix = new LinkedHashMap<>();

        // Process in order (depth-first, parents before children — guaranteed by exporter)
        for (GroupData group : def.groups) {
            BoneAnimData boneAnim = anim.bones.get(group.name);

            // This bone's pivot in blocks
            float[] o;
            if (group.parent != null && byUuid.containsKey(group.parent) && byUuid.get(group.parent).origin != null) {
                o = byUuid.get(group.parent).origin; // use parent's origin
            } else {
                o = new float[]{8, 8, 8}; // fallback
            }
            Vector3f pivot = new Vector3f(o[0] * BB, o[1] * BB, o[2] * BB);

            // Animated rotation (identity if no keyframes)
            Quaternionf animRot = new Quaternionf();
            if (boneAnim != null && boneAnim.rotation != null && !boneAnim.rotation.isEmpty()) {
                animRot = interpolateQuat(sorted(boneAnim.rotation), t);
            }

            // Animated position delta in blocks (0 if no keyframes)
            Vector3f animPos = new Vector3f();
            if (boneAnim != null && boneAnim.position != null && !boneAnim.position.isEmpty()) {
                animPos = interpolateVec(sorted(boneAnim.position), t, new Vector3f(0, 0, 0));
                animPos.mul(BB);
            }

            // Local matrix:
            // 1. Move to pivot
            // 2. Apply animation rotation
            // 3. Move back from pivot
            // 4. Add animation position offset
            Matrix4f local = new Matrix4f()
                    .translate(pivot)
                    .rotate(animRot)
                    .translate(-pivot.x, -pivot.y, -pivot.z)
                    .translate(animPos);

            // World matrix = parent world matrix * local
            Matrix4f world;
            if (group.parent != null && boneWorldMatrix.containsKey(group.parent)) {
                world = new Matrix4f(boneWorldMatrix.get(group.parent)).mul(local);
            } else {
                world = local;
            }
            boneWorldMatrix.put(group.uuid, world);

            // Now apply to this bone's mesh
            ItemDisplay mesh = spawned.getBoneMeshes().get(group.uuid);
            if (mesh == null || !mesh.isValid()) continue;

            // The world matrix encodes: all parent animations applied at their pivots.
            // To get the final mesh position we transform the bone's own pivot point
            // through the world matrix — this gives us where the pivot ends up after
            // all parent (and this bone's own) animations are applied.
            Vector3f finalPos = new Vector3f(pivot);
            world.transformPosition(finalPos);

            // Rotation: extract from world matrix, then apply rest rotation on top
            Quaternionf worldRot = world.getUnnormalizedRotation(new Quaternionf()).normalize();
            float[] restRot = group.rotation != null ? group.rotation : new float[]{0, 0, 0};
            Quaternionf restRotation = MathUtil.eulerToQuaternion(restRot[0], restRot[1], restRot[2]);
            Quaternionf finalRot = new Quaternionf(worldRot).mul(restRotation);

            // Scale
            Vector3f scale = new Vector3f(1, 1, 1);
            if (boneAnim != null && boneAnim.scale != null && !boneAnim.scale.isEmpty()) {
                scale = interpolateVec(sorted(boneAnim.scale), t, new Vector3f(1, 1, 1));
            }
            scale.x = Math.max(scale.x, 0.001f);
            scale.y = Math.max(scale.y, 0.001f);
            scale.z = Math.max(scale.z, 0.001f);

            mesh.setInterpolationDelay(0);
            mesh.setInterpolationDuration(2);
            mesh.setTransformation(new Transformation(finalPos, finalRot, scale, new Quaternionf()));
        }
    }

    // ── Interpolation ─────────────────────────────────────────────────────────

    private List<KeyframeData> sorted(List<KeyframeData> kfs) {
        if (kfs == null || kfs.isEmpty()) return List.of();
        return kfs.stream().sorted(Comparator.comparingDouble(k -> k.time)).toList();
    }

    private Vector3f interpolateVec(List<KeyframeData> kfs, float t, Vector3f def) {
        if (kfs.isEmpty()) return def;
        if (t <= kfs.get(0).time) return kf2vec(kfs.get(0));
        if (t >= kfs.get(kfs.size() - 1).time) return kf2vec(kfs.get(kfs.size() - 1));
        for (int i = 0; i < kfs.size() - 1; i++) {
            KeyframeData a = kfs.get(i), b = kfs.get(i + 1);
            if (a.time <= t && b.time >= t) {
                float alpha = (t - a.time) / (b.time - a.time);
                return switch (a.interpolation != null ? a.interpolation : "linear") {
                    case "step"       -> kf2vec(a);
                    case "catmullrom" -> catmullrom(kfs, i, alpha);
                    default           -> lerp(a, b, alpha);
                };
            }
        }
        return def;
    }

    private Quaternionf interpolateQuat(List<KeyframeData> kfs, float t) {
        if (kfs.isEmpty()) return new Quaternionf();
        if (t <= kfs.get(0).time) return toQuat(kfs.get(0));
        if (t >= kfs.get(kfs.size() - 1).time) return toQuat(kfs.get(kfs.size() - 1));
        for (int i = 0; i < kfs.size() - 1; i++) {
            KeyframeData a = kfs.get(i), b = kfs.get(i + 1);
            if (a.time <= t && b.time >= t) {
                float alpha = (t - a.time) / (b.time - a.time);
                if ("step".equals(a.interpolation)) return toQuat(a);
                return toQuat(a).slerp(toQuat(b), alpha);
            }
        }
        return new Quaternionf();
    }

    private static Vector3f kf2vec(KeyframeData kf) { return new Vector3f(kf.x, kf.y, kf.z); }

    private static Vector3f lerp(KeyframeData a, KeyframeData b, float alpha) {
        return new Vector3f(
                a.x + (b.x - a.x) * alpha,
                a.y + (b.y - a.y) * alpha,
                a.z + (b.z - a.z) * alpha);
    }

    private static Vector3f catmullrom(List<KeyframeData> all, int i1, float alpha) {
        int i0 = Math.max(0, i1 - 1), i2 = i1 + 1, i3 = Math.min(all.size() - 1, i1 + 2);
        KeyframeData p0 = all.get(i0), p1 = all.get(i1), p2 = all.get(i2), p3 = all.get(i3);
        float t = alpha, t2 = t*t, t3 = t2*t;
        float c0=-t3+2*t2-t, c1=3*t3-5*t2+2, c2=-3*t3+4*t2+t, c3=t3-t2;
        return new Vector3f(
                0.5f*(c0*p0.x+c1*p1.x+c2*p2.x+c3*p3.x),
                0.5f*(c0*p0.y+c1*p1.y+c2*p2.y+c3*p3.y),
                0.5f*(c0*p0.z+c1*p1.z+c2*p2.z+c3*p3.z));
    }

    private Quaternionf toQuat(KeyframeData kf) {
        return MathUtil.eulerToQuaternion(kf.x, kf.y, kf.z);
    }

    private AnimationData findAnimation(ManimModel model, String name) {
        if (model.animations == null) return null;
        for (AnimationData a : model.animations)
            if (name.equalsIgnoreCase(a.name)) return a;
        return null;
    }

    public boolean play(SpawnedModel spawned, String animationName) {
        AnimationData anim = findAnimation(spawned.getDefinition(), animationName);
        if (anim == null) {
            plugin.getLogger().warning("[MA] Animation not found: '" + animationName + "'");
            return false;
        }
        spawned.setCurrentAnimation(animationName);
        spawned.setPlaying(true);
        spawned.setAnimationTime(0f);
        plugin.getLogger().info("[MA] Playing '" + animationName + "'");
        return true;
    }

    public void stop(SpawnedModel spawned) {
        spawned.setPlaying(false);
        spawned.setAnimationTime(0f);
    }
}
