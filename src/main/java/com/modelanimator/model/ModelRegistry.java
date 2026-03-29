package com.modelanimator.model;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.modelanimator.util.FlexibleIntDeserializer;
import com.modelanimator.ModelAnimatorPlugin;

import java.io.File;
import java.io.FileReader;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

/**
 * Loads .manim.json files from disk and keeps them in memory by name.
 */
public class ModelRegistry {

    private final ModelAnimatorPlugin plugin;
    private final Gson gson = new GsonBuilder()
            .registerTypeAdapter(Integer.class, new FlexibleIntDeserializer())
            .create();
    private final Map<String, ManimModel> models = new HashMap<>();

    public ModelRegistry(ModelAnimatorPlugin plugin) {
        this.plugin = plugin;
    }

    /** Load every *.manim.json (or *.json) from the given directory. */
    public void loadAll(File dir) {
        models.clear();
        if (!dir.isDirectory()) return;

        File[] files = dir.listFiles((d, name) ->
                name.endsWith(".manim.json") || name.endsWith(".json"));
        if (files == null) return;

        for (File f : files) {
            try (FileReader reader = new FileReader(f)) {
                ManimModel model = gson.fromJson(reader, ManimModel.class);
                if (model == null || model.meta == null) {
                    plugin.getLogger().warning("Skipping invalid file: " + f.getName());
                    continue;
                }
                String key = model.meta.name != null ? model.meta.name : f.getName();
                models.put(key.toLowerCase(), model);
                plugin.getLogger().info("Loaded model: " + key);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to load " + f.getName(), e);
            }
        }
    }

    /** Reload a single file. */
    public boolean reload(File file) {
        try (FileReader reader = new FileReader(file)) {
            ManimModel model = gson.fromJson(reader, ManimModel.class);
            if (model == null || model.meta == null) return false;
            String key = model.meta.name != null ? model.meta.name : file.getName();
            models.put(key.toLowerCase(), model);
            return true;
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to reload " + file.getName(), e);
            return false;
        }
    }

    public ManimModel get(String name) {
        return models.get(name.toLowerCase());
    }

    public Collection<ManimModel> all() {
        return Collections.unmodifiableCollection(models.values());
    }

    public Collection<String> names() {
        return Collections.unmodifiableCollection(models.keySet());
    }

    public int count() {
        return models.size();
    }
}
