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
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class AnimationManager {

    private static final float TICK_DELTA = 1f / 20f;
    // 1 Blockbench pixel = 1/16 block
    private static final float BB = 1f / 16f;

    private final ModelAnimatorPlugin plugin;
    private final DisplayModelManager displayManager;

    public AnimationManager(ModelAnimatorPlugin plugin) {
        this.plugin         = plugin;
        this.displayManager = plugin.getDisplayModelManager();
    }

    // ── Tick ──────────────────────────────────────────────────────────────────

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

        float t = spawned.getAnimationTime() + TICK_DELTA;
        if (anim.length > 0 && t > anim.length) {
            t = switch (anim.loop != null ? anim.loop : "once") {
                case "loop" -> t % anim.length;
                case "hold" -> anim.length;
                default     -> { spawned.setPlaying(false); yield 0f; }
            };
        }
        spawned.setAnimationTime(t);

        if (anim.bones == null) return;

        for (Map.Entry<String, BoneAnimData> entry : anim.bones.entrySet()) {
            GroupData group = findGroup(def, entry.getKey());
            if (group == null) continue;

            ItemDisplay display = spawned.getBoneDisplays().get(group.uuid);
            if (display == null || !display.isValid()) continue;

            applyBoneTransform(display, group, entry.getValue(), t, def);
        }
    }

    // ── Transform ─────────────────────────────────────────────────────────────

    /**
     * Computes the bone's full transform at time t — always from scratch,
     * never reading back from the display (avoids error accumulation).
     *
     * Translation:
     *   restPivot  = bone's origin converted from BB pixels to blocks
     *                (this is where the bone sits at rest relative to root)
     *   animDelta  = position keyframe value (BB pixels → blocks)
     *   final      = restPivot + animDelta
     *
     * Rotation:
     *   Slerp between euler keyframes converted to quaternions.
     *   Applied on top of rest rotation via multiplication.
     *
     * Scale:
     *   Interpolated directly, clamped above 0.
     */
    private void applyBoneTransform(ItemDisplay joint, GroupData group,
                                    BoneAnimData boneAnim, float t,
                                    ManimModel def) {
        // ── Relative pivot offset (joint sits here relative to parent joint) ──
        // Same calculation as DisplayModelManager.spawn() — must match exactly.
        float[] thisOrigin   = (group.origin != null && group.origin.length >= 3)
                ? group.origin : new float[]{8, 8, 8};
        float[] parentOrigin = new float[]{8, 8, 8};
        if (group.parent != null) {
            for (GroupData g : def.groups) {
                if (g.uuid.equals(group.parent) && g.origin != null && g.origin.length >= 3) {
                    parentOrigin = g.origin;
                    break;
                }
            }
        }
        Vector3f relativePivot = new Vector3f(
                (thisOrigin[0] - parentOrigin[0]) * BB,
                (thisOrigin[1] - parentOrigin[1]) * BB,
                (thisOrigin[2] - parentOrigin[2]) * BB
        );

        // ── Animated position delta (BB pixels → blocks) added on top ────────
        Vector3f posDelta = interpolateVec(sorted(boneAnim.position), t, new Vector3f(0, 0, 0));
        posDelta.mul(BB);

        Vector3f translation = new Vector3f(
                relativePivot.x + posDelta.x,
                relativePivot.y + posDelta.y,
                relativePivot.z + posDelta.z
        );

        // ── Rest rotation + animated rotation ─────────────────────────────────
        float[] restRot = group.rotation != null ? group.rotation : new float[]{0, 0, 0};
        Quaternionf restRotation = MathUtil.eulerToQuaternion(restRot[0], restRot[1], restRot[2]);
        Quaternionf animRotation = interpolateQuat(sorted(boneAnim.rotation), t);
        // Apply anim on top of rest
        Quaternionf finalRotation = new Quaternionf(animRotation).mul(restRotation);

        // ── Scale ─────────────────────────────────────────────────────────────
        Vector3f scale = interpolateVec(sorted(boneAnim.scale), t, new Vector3f(1, 1, 1));
        scale.x = Math.max(scale.x, 0.001f);
        scale.y = Math.max(scale.y, 0.001f);
        scale.z = Math.max(scale.z, 0.001f);

        // ── Apply to joint ────────────────────────────────────────────────────
        joint.setInterpolationDelay(0);
        joint.setInterpolationDuration(2);
        joint.setTransformation(new Transformation(
                translation, finalRotation, scale, new Quaternionf()
        ));
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
        if (kfs.isEmpty()) return new Quaternionf(); // identity = no rotation
        if (t <= kfs.get(0).time) return toQuat(kfs.get(0));
        if (t >= kfs.get(kfs.size() - 1).time) return toQuat(kfs.get(kfs.size() - 1));

        for (int i = 0; i < kfs.size() - 1; i++) {
            KeyframeData a = kfs.get(i), b = kfs.get(i + 1);
            if (a.time <= t && b.time >= t) {
                float alpha = (t - a.time) / (b.time - a.time);
                if ("step".equals(a.interpolation)) return toQuat(a);
                return toQuat(a).slerp(toQuat(b), alpha); // smooth quaternion blend
            }
        }
        return new Quaternionf();
    }

    // ── Math helpers ──────────────────────────────────────────────────────────

    private static Vector3f kf2vec(KeyframeData kf) {
        return new Vector3f(kf.x, kf.y, kf.z);
    }

    private static Vector3f lerp(KeyframeData a, KeyframeData b, float alpha) {
        return new Vector3f(
                a.x + (b.x - a.x) * alpha,
                a.y + (b.y - a.y) * alpha,
                a.z + (b.z - a.z) * alpha);
    }

    private static Vector3f catmullrom(List<KeyframeData> all, int i1, float alpha) {
        int i0 = Math.max(0, i1 - 1);
        int i2 = i1 + 1;
        int i3 = Math.min(all.size() - 1, i1 + 2);
        KeyframeData p0 = all.get(i0), p1 = all.get(i1),
                     p2 = all.get(i2), p3 = all.get(i3);
        float t = alpha, t2 = t*t, t3 = t2*t;
        float c0 = -t3+2*t2-t, c1 = 3*t3-5*t2+2, c2 = -3*t3+4*t2+t, c3 = t3-t2;
        return new Vector3f(
                0.5f*(c0*p0.x+c1*p1.x+c2*p2.x+c3*p3.x),
                0.5f*(c0*p0.y+c1*p1.y+c2*p2.y+c3*p3.y),
                0.5f*(c0*p0.z+c1*p1.z+c2*p2.z+c3*p3.z));
    }

    private Quaternionf toQuat(KeyframeData kf) {
        return MathUtil.eulerToQuaternion(kf.x, kf.y, kf.z);
    }

    // ── Lookup helpers ────────────────────────────────────────────────────────

    private AnimationData findAnimation(ManimModel model, String name) {
        if (model.animations == null) return null;
        for (AnimationData a : model.animations)
            if (name.equalsIgnoreCase(a.name)) return a;
        return null;
    }

    private GroupData findGroup(ManimModel model, String nameOrUuid) {
        if (model.groups == null) return null;
        for (GroupData g : model.groups)
            if (nameOrUuid.equalsIgnoreCase(g.name)) return g;
        for (GroupData g : model.groups)
            if (nameOrUuid.equalsIgnoreCase(g.uuid)) return g;
        return null;
    }

    // ── Public control ────────────────────────────────────────────────────────

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
