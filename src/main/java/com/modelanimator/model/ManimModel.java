package com.modelanimator.model;

import java.util.List;
import java.util.Map;

/**
 * Plain-data representation of a loaded .manim.json file.
 * Gson populates these fields directly from JSON.
 */
public class ManimModel {

    // ── top-level ──────────────────────────────────────────────────────────────
    public Meta meta;
    public List<TextureData>  textures;
    public List<CubeData>     cubes;
    public List<GroupData>    groups;
    public List<AnimationData> animations;

    // ── nested types ───────────────────────────────────────────────────────────

    public static class Meta {
        public String format;
        public String format_version;
        public String model_format;
        public String name;
        public String exported_at;
    }

    public static class TextureData {
        public String uuid;
        public String name;
        public String namespace;
        public String folder;
        public boolean particle;
        public String source; // base64 data URI (may be null)
    }

    public static class CubeData {
        public String  uuid;
        public String  name;
        public float[] from;     // [x, y, z]
        public float[] to;       // [x, y, z]
        public float[] origin;   // pivot
        public float[] rotation; // euler degrees
        public float   inflate;
        public boolean mirror_uv;
        public Map<String, FaceData> faces;
    }

    public static class FaceData {
        public float[]  uv;       // [u1,v1,u2,v2]
        public Integer  texture;  // texture index — null or false in JSON = no texture
        public float    rotation;
        public int      tint;
    }

    public static class GroupData {
        public String uuid;
        public String name;
        public String parent;           // parent group uuid (null = root)
        public float[] origin;
        public float[] rotation;
        public boolean reset;
        public List<String> children_cubes; // cube uuids
        public int    cmd        = -1;  // legacy CustomModelData (-1 = not set)
        public String namespace;        // resource pack namespace e.g. "mymod"
        public String item_model;       // full item_model key e.g. "mymod:amongus/head"
    }

    public static class AnimationData {
        public String uuid;
        public String name;
        public String loop;   // "once" | "loop" | "hold"
        public float  length; // seconds
        public float  snapping;
        public Map<String, BoneAnimData> bones;
    }

    public static class BoneAnimData {
        public String uuid;
        public List<KeyframeData> rotation;
        public List<KeyframeData> position;
        public List<KeyframeData> scale;
    }

    public static class KeyframeData {
        public float  time;          // seconds
        public String channel;       // rotation | position | scale
        public String interpolation; // linear | catmullrom | step
        public float  x, y, z;
    }
}
