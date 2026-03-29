package com.modelanimator;

import com.modelanimator.animation.AnimationManager;
import com.modelanimator.command.ManimCommand;
import com.modelanimator.display.DisplayModelManager;
import com.modelanimator.model.ModelRegistry;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public final class ModelAnimatorPlugin extends JavaPlugin {

    private static ModelAnimatorPlugin instance;

    private ModelRegistry modelRegistry;
    private DisplayModelManager displayModelManager;
    private AnimationManager animationManager;

    @Override
    public void onEnable() {
        instance = this;

        // Save default config if it doesn't exist yet
        saveDefaultConfig();

        // Create data folder
        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }

        // Create models sub-folder
        File modelsDir = new File(getDataFolder(), "models");
        if (!modelsDir.exists()) {
            modelsDir.mkdirs();
        }

        // Init systems
        modelRegistry      = new ModelRegistry(this);
        displayModelManager = new DisplayModelManager(this);
        animationManager   = new AnimationManager(this);

        // Load all .manim.json files from plugins/ModelAnimator/models/
        modelRegistry.loadAll(modelsDir);

        // Register command
        ManimCommand cmd = new ManimCommand(this);
        getCommand("manim").setExecutor(cmd);
        getCommand("manim").setTabCompleter(cmd);

        // Schedule animation ticker (every tick)
        getServer().getScheduler().runTaskTimer(this, animationManager::tick, 0L, 1L);

        getLogger().info("ModelAnimator enabled. Loaded " + modelRegistry.count() + " model(s).");
    }

    @Override
    public void onDisable() {
        // Remove all spawned display entities cleanly
        if (displayModelManager != null) {
            displayModelManager.removeAll();
        }
        getLogger().info("ModelAnimator disabled.");
    }

    // ─── Accessors ─────────────────────────────────────────────────────────────

    public static ModelAnimatorPlugin get() { return instance; }
    public ModelRegistry getModelRegistry()           { return modelRegistry; }
    public DisplayModelManager getDisplayModelManager() { return displayModelManager; }
    public AnimationManager getAnimationManager()     { return animationManager; }
}
