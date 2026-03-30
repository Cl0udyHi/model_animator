package com.modelanimator.display;

import com.modelanimator.ModelAnimatorPlugin;
import com.modelanimator.model.ManimModel;
import com.modelanimator.model.ManimModel.GroupData;
import com.modelanimator.util.MathUtil;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.*;

public class DisplayModelManager {

    private final ModelAnimatorPlugin plugin;
    private final Map<UUID, SpawnedModel> spawnedModels = new LinkedHashMap<>();
    private static final float BB = 1f / 16f;

    public DisplayModelManager(ModelAnimatorPlugin plugin) {
        this.plugin = plugin;
    }

    // ── Public API ─────────────────────────────────────────────────────────────

    public SpawnedModel spawn(ManimModel model, Location location) {
        SpawnedModel spawned = new SpawnedModel(model.meta.name, model, location);

        // ── One invisible ROOT entity at the spawn point.
        // Teleport this to move the whole model.
        ItemDisplay root = spawnEmpty(location);
        spawned.setRootEntity(root);

        // ── Build uuid → GroupData map for parent lookups
        Map<String, GroupData> byUuid = new HashMap<>();
        if (model.groups != null)
            for (GroupData g : model.groups) byUuid.put(g.uuid, g);

        // ── jointMap: groupUuid → the empty "joint" ItemDisplay for that bone
        // After this loop every bone has a joint, and every joint rides its parent joint (or root).
        Map<String, ItemDisplay> jointMap = new HashMap<>();

        List<GroupData> groups = model.groups != null ? model.groups : Collections.emptyList();

        for (GroupData group : groups) {

            // Determine parent joint — either another bone's joint, or the root
            ItemDisplay parentJoint;
            if (group.parent != null && jointMap.containsKey(group.parent)) {
                parentJoint = jointMap.get(group.parent);
            } else {
                parentJoint = root;
            }

            // Get this bone's absolute pivot in model space (pixels)
            float[] thisOrigin = (group.origin != null && group.origin.length >= 3)
                    ? group.origin : new float[]{8, 8, 8};

            // Get parent's absolute pivot in model space (pixels)
            float[] parentOrigin;
            if (group.parent != null) {
                GroupData parentGroup = byUuid.get(group.parent);
                parentOrigin = (parentGroup != null && parentGroup.origin != null && parentGroup.origin.length >= 3)
                        ? parentGroup.origin : new float[]{8, 8, 8};
            } else {
                // Top-level bone: relative to model centre [8,8,8]
                parentOrigin = new float[]{8, 8, 8};
            }

            // Relative pivot = this pivot minus parent pivot, converted to blocks
            // This is the translation applied to the joint so it sits at the right
            // position relative to its parent joint
            Vector3f relativePivot = new Vector3f(
                    (thisOrigin[0] - parentOrigin[0]) * BB,
                    (thisOrigin[1] - parentOrigin[1]) * BB,
                    (thisOrigin[2] - parentOrigin[2]) * BB
            ); // relative pivot is already correct - this minus parent, both absolute

            // Rest rotation of this bone
            float[] rot = group.rotation != null ? group.rotation : new float[]{0, 0, 0};
            Quaternionf restRotation = MathUtil.eulerToQuaternion(rot[0], rot[1], rot[2]);

            // ── Spawn the JOINT: empty invisible entity
            // Rides its parent joint. Translation = relative pivot offset.
            // Animation will modify this joint's translation+rotation every tick.
            ItemDisplay joint = spawnEmpty(location);
            joint.setTransformation(new Transformation(
                    relativePivot,    // offset from parent joint
                    restRotation,     // rest rotation
                    new Vector3f(1, 1, 1),
                    new Quaternionf()
            ));
            parentJoint.addPassenger(joint);
            jointMap.put(group.uuid, joint);

            // ── Spawn the MESH: visible item, rides ROOT only (not the joint)
            // Passengers cannot have setTransformation applied client-side while riding.
            // So mesh rides root and gets its full world-relative transform every tick.
            ItemDisplay mesh = spawnMesh(location, model.meta.name, group);
            // Set rest transform = absolute position relative to root (in blocks)
            Vector3f absTranslation = new Vector3f(
                    parentOrigin[0] * BB,
                    parentOrigin[1] * BB,
                    parentOrigin[2] * BB
            );
            Quaternionf meshRestRot = MathUtil.eulerToQuaternion(
                    group.rotation != null ? group.rotation[0] : 0f,
                    group.rotation != null ? group.rotation[1] : 0f,
                    group.rotation != null ? group.rotation[2] : 0f
            );
            mesh.setTransformation(new Transformation(
                    absTranslation, meshRestRot, new Vector3f(1, 1, 1), new Quaternionf()
            ));
            root.addPassenger(mesh);

            // Store both — joints for hierarchy, meshes for animation
            spawned.putBoneJoint(group.uuid, joint);
            spawned.putBoneMesh(group.uuid, mesh);

            plugin.getLogger().info("[MA] Bone '" + group.name + "'"
                    + " parent=" + (group.parent != null ? group.parent.substring(0, 8) : "ROOT")
                    + " relativePivot=" + relativePivot);
        }

        spawnedModels.put(spawned.getId(), spawned);
        plugin.getLogger().info("[MA] Spawned '" + model.meta.name + "' — "
                + groups.size() + " bones.");
        return spawned;
    }

    public boolean remove(UUID instanceId) {
        SpawnedModel m = spawnedModels.remove(instanceId);
        if (m == null) return false;
        m.removeEntities();
        return true;
    }

    public void removeAll() {
        for (SpawnedModel m : spawnedModels.values()) m.removeEntities();
        spawnedModels.clear();
    }

    public SpawnedModel get(UUID id) { return spawnedModels.get(id); }
    public Collection<SpawnedModel> all() { return Collections.unmodifiableCollection(spawnedModels.values()); }

    // ── Helpers ────────────────────────────────────────────────────────────────

    /** Spawn an invisible empty ItemDisplay (used for root and joints) */
    private ItemDisplay spawnEmpty(Location loc) {
        ItemDisplay d = (ItemDisplay) loc.getWorld().spawnEntity(loc, EntityType.ITEM_DISPLAY);
        d.setItemStack(new ItemStack(Material.AIR));
        d.setPersistent(false);
        d.setCustomNameVisible(false);
        d.setInterpolationDuration(2);
        return d;
    }

    /** Spawn the visible mesh ItemDisplay with the correct item_model */
    private ItemDisplay spawnMesh(Location loc, String modelName, GroupData group) {
        ItemDisplay d = (ItemDisplay) loc.getWorld().spawnEntity(loc, EntityType.ITEM_DISPLAY);
        d.setItemStack(resolveItem(modelName, group));
        d.setDisplayWidth(0f);
        d.setDisplayHeight(0f);
        d.setPersistent(false);
        d.setCustomNameVisible(false);
        d.setInterpolationDuration(2);
        return d;
    }

    /** Resolve item using 1.21.4+ item_model data component */
    private ItemStack resolveItem(String modelName, GroupData group) {
        String key;
        if (group.item_model != null && !group.item_model.isBlank()) {
            key = group.item_model;
        } else if (group.namespace != null && !group.namespace.isBlank()) {
            key = group.namespace + ":" + group.name.toLowerCase();
        } else {
            String m = modelName.toLowerCase().replaceAll("[^a-z0-9_.-]", "_");
            String b = group.name.toLowerCase().replaceAll("[^a-z0-9_.-]", "_");
            key = "modelanimator:" + m + "/" + b;
        }

        String[] parts = key.contains(":") ? key.split(":", 2) : new String[]{"modelanimator", key};
        ItemStack item = new ItemStack(Material.LEATHER_HORSE_ARMOR);

        try {
            NamespacedKey nk = new NamespacedKey(parts[0], parts[1]);
            item.setData(io.papermc.paper.datacomponent.DataComponentTypes.ITEM_MODEL, nk);
            plugin.getLogger().info("[MA] ✅ item_model=" + nk);
        } catch (Throwable e1) {
            try {
                NamespacedKey nk = new NamespacedKey(parts[0], parts[1]);
                ItemMeta meta = item.getItemMeta();
                meta.getClass().getMethod("setItemModel", NamespacedKey.class).invoke(meta, nk);
                item.setItemMeta(meta);
                plugin.getLogger().info("[MA] ✅ item_model via reflection=" + nk);
            } catch (Throwable e2) {
                plugin.getLogger().severe("[MA] ❌ item_model failed: " + e2.getMessage());
            }
        }
        return item;
    }

    /**
     * Apply animation transform to a joint.
     * The translation here is the RELATIVE pivot offset + animation delta.
     * Because the joint rides its parent, this is already in parent-local space.
     */
    public void applyTransformation(ItemDisplay joint, Vector3f translation,
                                    Quaternionf rotation, Vector3f scale) {
        joint.setTransformation(new Transformation(translation, rotation, scale, new Quaternionf()));
    }
}
