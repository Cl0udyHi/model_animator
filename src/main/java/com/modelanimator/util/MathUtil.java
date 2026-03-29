package com.modelanimator.util;

import org.joml.Quaternionf;

/**
 * Math helpers for ModelAnimator.
 */
public final class MathUtil {

    private MathUtil() {}

    /**
     * Convert Blockbench Euler angles (degrees, XYZ order) to a quaternion.
     * Blockbench uses ZYX intrinsic (same as XYZ extrinsic in reverse).
     */
    public static Quaternionf eulerToQuaternion(float xDeg, float yDeg, float zDeg) {
        float xRad = (float) Math.toRadians(xDeg);
        float yRad = (float) Math.toRadians(yDeg);
        float zRad = (float) Math.toRadians(zDeg);

        // JOML uses ZYX intrinsic order when you call rotateXYZ
        return new Quaternionf()
                .rotateZ(zRad)
                .rotateY(yRad)
                .rotateX(xRad);
    }

    public static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    public static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }
}
