package com.modelanimator.util;

import com.google.gson.*;
import java.lang.reflect.Type;

/**
 * Gson deserializer that handles the texture field in Blockbench face data.
 * Blockbench exports texture as:
 *   - an integer (0, 1, 2...) when a texture is assigned
 *   - false (boolean) when no texture is assigned
 * Standard Gson int parsing crashes on boolean, so we handle it here.
 */
public class FlexibleIntDeserializer implements JsonDeserializer<Integer> {
    @Override
    public Integer deserialize(JsonElement json, Type type, JsonDeserializationContext ctx)
            throws JsonParseException {
        if (json.isJsonNull()) return null;
        if (json.isJsonPrimitive()) {
            JsonPrimitive p = json.getAsJsonPrimitive();
            if (p.isBoolean()) return null; // false = no texture
            if (p.isNumber()) return p.getAsInt();
        }
        return null;
    }
}
