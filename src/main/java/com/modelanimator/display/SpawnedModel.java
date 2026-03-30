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

    private ItemDisplay rootEntity;

    // uuid → joint (empty entity, defines position hierarchy)
    private final Map<String, ItemDisplay> boneJoints = new HashMap<>();
    // uuid → mesh (visible entity, gets animated directly)
    private final Map<String, ItemDisplay> boneMeshes = new HashMap<>();

    private String  currentAnimation = null;
    private float   animationTime    = 0f;
    private boolean playing          = false;

    public SpawnedModel(String modelName, ManimModel definition, Location origin) {
        this.id         = UUID.randomUUID();
        this.modelName  = modelName;
        this.definition = definition;
        this.origin     = origin;
    }

    public UUID getId()               { return id; }
    public String getModelName()      { return modelName; }
    public ManimModel getDefinition() { return definition; }
    public Location getOrigin()       { return origin; }

    public ItemDisplay getRootEntity()          { return rootEntity; }
    public void setRootEntity(ItemDisplay root) { this.rootEntity = root; }

    public Map<String, ItemDisplay> getBoneJoints() { return boneJoints; }
    public Map<String, ItemDisplay> getBoneMeshes() { return boneMeshes; }

    // Legacy — joints are used by AnimationManager for bone lookup
    public Map<String, ItemDisplay> getBoneDisplays() { return boneJoints; }

    public void putBoneJoint(String uuid, ItemDisplay joint) { boneJoints.put(uuid, joint); }
    public void putBoneMesh(String uuid, ItemDisplay mesh)   { boneMeshes.put(uuid, mesh); }

    public String  getCurrentAnimation()           { return currentAnimation; }
    public void    setCurrentAnimation(String a)   { this.currentAnimation = a; }
    public float   getAnimationTime()              { return animationTime; }
    public void    setAnimationTime(float t)       { this.animationTime = t; }
    public boolean isPlaying()                     { return playing; }
    public void    setPlaying(boolean b)           { this.playing = b; }

    public void removeEntities() {
        if (rootEntity != null && rootEntity.isValid()) rootEntity.remove();
        for (ItemDisplay d : boneJoints.values()) if (d != null && d.isValid()) d.remove();
        for (ItemDisplay d : boneMeshes.values()) if (d != null && d.isValid()) d.remove();
        boneJoints.clear();
        boneMeshes.clear();
    }
}
