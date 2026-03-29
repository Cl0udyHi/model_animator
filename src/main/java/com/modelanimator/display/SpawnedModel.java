package com.modelanimator.display;

import com.modelanimator.model.ManimModel;
import org.bukkit.Location;
import org.bukkit.entity.ItemDisplay;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SpawnedModel {

    private final UUID id;
    private final String modelName;
    private final ManimModel definition;
    private final Location origin;

    /** The invisible root entity — move this to move the whole model */
    private ItemDisplay rootEntity;

    /** bone group uuid → ItemDisplay */
    private final Map<String, ItemDisplay> boneDisplays = new HashMap<>();

    // Animation state
    private String  currentAnimation = null;
    private float   animationTime    = 0f;
    private boolean playing          = false;

    public SpawnedModel(String modelName, ManimModel definition, Location origin) {
        this.id         = UUID.randomUUID();
        this.modelName  = modelName;
        this.definition = definition;
        this.origin     = origin.clone();
    }

    // ── Accessors ──────────────────────────────────────────────────────────────

    public UUID getId()               { return id; }
    public String getModelName()      { return modelName; }
    public ManimModel getDefinition() { return definition; }
    public Location getOrigin()       { return origin; }

    public ItemDisplay getRootEntity()             { return rootEntity; }
    public void setRootEntity(ItemDisplay root)    { this.rootEntity = root; }

    public Map<String, ItemDisplay> getBoneDisplays()            { return boneDisplays; }
    public void putBoneDisplay(String groupUuid, ItemDisplay d)  { boneDisplays.put(groupUuid, d); }

    public String getCurrentAnimation()            { return currentAnimation; }
    public void setCurrentAnimation(String name)   { this.currentAnimation = name; animationTime = 0f; }

    public float getAnimationTime()                { return animationTime; }
    public void advanceTime(float delta)           { animationTime += delta; }
    public void setAnimationTime(float t)          { animationTime = t; }

    public boolean isPlaying()                     { return playing; }
    public void setPlaying(boolean b)              { playing = b; }

    /** Remove all entities including the root. */
    public void removeEntities() {
        for (ItemDisplay d : boneDisplays.values())
            if (d != null && d.isValid()) d.remove();
        boneDisplays.clear();
        if (rootEntity != null && rootEntity.isValid()) rootEntity.remove();
        rootEntity = null;
    }
}
