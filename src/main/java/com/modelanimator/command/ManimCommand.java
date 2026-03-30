package com.modelanimator.command;

import com.modelanimator.ModelAnimatorPlugin;
import com.modelanimator.animation.AnimationManager;
import com.modelanimator.display.DisplayModelManager;
import com.modelanimator.display.SpawnedModel;
import com.modelanimator.model.ManimModel;
import com.modelanimator.model.ManimModel.AnimationData;
import com.modelanimator.model.ModelRegistry;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * /manim <subcommand> [args]
 *
 *   spawn  <model>             — spawn at your feet
 *   remove <id|all>            — remove a spawned instance
 *   play   <id> <animation>    — start an animation
 *   stop   <id>                — stop current animation
 *   list                       — list loaded models
 *   reload                     — reload model files from disk
 */
public class ManimCommand implements CommandExecutor, TabCompleter {

    private final ModelAnimatorPlugin plugin;
    private final ModelRegistry       registry;
    private final DisplayModelManager displayManager;
    private final AnimationManager    animationManager;

    private static final String PERM = "modelanimator.use";
    private static final String PREFIX = ChatColor.GOLD + "[ModelAnimator] " + ChatColor.RESET;

    public ManimCommand(ModelAnimatorPlugin plugin) {
        this.plugin           = plugin;
        this.registry         = plugin.getModelRegistry();
        this.displayManager   = plugin.getDisplayModelManager();
        this.animationManager = plugin.getAnimationManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission(PERM)) {
            sender.sendMessage(PREFIX + ChatColor.RED + "No permission.");
            return true;
        }
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "spawn"  -> cmdSpawn(sender, args);
            case "remove" -> cmdRemove(sender, args);
            case "play"   -> cmdPlay(sender, args);
            case "stop"   -> cmdStop(sender, args);
            case "list"   -> cmdList(sender);
            case "reload" -> cmdReload(sender);
            case "debug" -> cmdDebug(sender, args);
            default       -> sendHelp(sender);
        }
        return true;
    }

    // ── Sub-commands ───────────────────────────────────────────────────────────

    private void cmdSpawn(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(PREFIX + "Must be a player."); return;
        }

        if (args.length < 2) { sender.sendMessage(PREFIX + "Usage: /manim spawn <model>"); return; }

        String name = args[1];
        ManimModel model = registry.get(name);
        if (model == null) {
            p.sendMessage(PREFIX + ChatColor.RED + "Model not found: " + name); return;
        }

        Location loc = p.getLocation();
        SpawnedModel spawned = displayManager.spawn(model, loc);
        p.sendMessage(PREFIX + ChatColor.GREEN + "Spawned \"" + name + "\" (id: "
                + spawned.getId().toString().substring(0, 8) + "…)");
    }

    private void cmdRemove(CommandSender sender, String[] args) {
        if (args.length < 2) { sender.sendMessage(PREFIX + "Usage: /manim remove <id|all>"); return; }

        if (args[1].equalsIgnoreCase("all")) {
            int count = displayManager.all().size();
            displayManager.removeAll();
            sender.sendMessage(PREFIX + ChatColor.YELLOW + "Removed all " + count + " instance(s).");
            return;
        }

        UUID id = matchId(args[1]);
        if (id == null) { sender.sendMessage(PREFIX + ChatColor.RED + "Invalid id."); return; }
        if (displayManager.remove(id)) {
            sender.sendMessage(PREFIX + ChatColor.YELLOW + "Removed instance " + args[1]);
        } else {
            sender.sendMessage(PREFIX + ChatColor.RED + "Instance not found.");
        }
    }

    private void cmdPlay(CommandSender sender, String[] args) {
        if (args.length < 3) { sender.sendMessage(PREFIX + "Usage: /manim play <id> <animation>"); return; }

        UUID id = matchId(args[1]);
        if (id == null) { sender.sendMessage(PREFIX + ChatColor.RED + "Invalid id."); return; }

        SpawnedModel spawned = displayManager.get(id);
        if (spawned == null) { sender.sendMessage(PREFIX + ChatColor.RED + "Instance not found."); return; }

        String animName = args[2];
        if (animationManager.play(spawned, animName)) {
            sender.sendMessage(PREFIX + ChatColor.GREEN + "Playing animation: " + animName);
        } else {
            sender.sendMessage(PREFIX + ChatColor.RED + "Animation not found: " + animName);
        }
    }

    private void cmdStop(CommandSender sender, String[] args) {
        if (args.length < 2) { sender.sendMessage(PREFIX + "Usage: /manim stop <id>"); return; }

        UUID id = matchId(args[1]);
        if (id == null) { sender.sendMessage(PREFIX + ChatColor.RED + "Invalid id."); return; }

        SpawnedModel spawned = displayManager.get(id);
        if (spawned == null) { sender.sendMessage(PREFIX + ChatColor.RED + "Instance not found."); return; }

        animationManager.stop(spawned);
        sender.sendMessage(PREFIX + ChatColor.YELLOW + "Animation stopped.");
    }

    private void cmdList(CommandSender sender) {
        sender.sendMessage(PREFIX + "Loaded models (" + registry.count() + "):");
        for (String name : registry.names()) {
            ManimModel m = registry.get(name);
            int animCount = m.animations != null ? m.animations.size() : 0;
            sender.sendMessage("  " + ChatColor.AQUA + name
                    + ChatColor.GRAY + " — " + animCount + " animation(s)");
            if (m.animations != null) {
                for (AnimationData a : m.animations) {
                    sender.sendMessage("      " + ChatColor.WHITE + a.name
                            + ChatColor.GRAY + " [" + a.loop + ", " + String.format("%.2f", a.length) + "s]");
                }
            }
        }

        sender.sendMessage(PREFIX + "Live instances (" + displayManager.all().size() + "):");
        for (SpawnedModel s : displayManager.all()) {
            sender.sendMessage("  " + ChatColor.AQUA + s.getId().toString().substring(0, 8) + "…"
                    + ChatColor.GRAY + " model=" + s.getModelName()
                    + " anim=" + (s.getCurrentAnimation() != null ? s.getCurrentAnimation() : "—")
                    + " playing=" + s.isPlaying());
        }
    }

    private void cmdReload(CommandSender sender) {
        plugin.reloadConfig(); // reload config.yml too
        plugin.getModelRegistry().loadAll(
                new java.io.File(plugin.getDataFolder(), "models"));
        sender.sendMessage(PREFIX + ChatColor.GREEN + "Reloaded config + " + registry.count() + " model(s).");
    }

    // ── Tab completion ─────────────────────────────────────────────────────────

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.addAll(List.of("spawn", "remove", "play", "stop", "list", "reload"));
        } else if (args.length == 2) {
            switch (args[0].toLowerCase()) {
                case "spawn"  -> completions.addAll(registry.names());
                case "remove" -> {
                    completions.add("all");
                    for (SpawnedModel s : displayManager.all())
                        completions.add(s.getId().toString().substring(0, 8));
                }
                case "debug" -> {
                    for (SpawnedModel s : displayManager.all())
                        completions.add(s.getId().toString().substring(0, 8));
                }
                case "play", "stop" -> {
                    for (SpawnedModel s : displayManager.all())
                        completions.add(s.getId().toString().substring(0, 8));
                }
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("play")) {
            UUID id = matchId(args[1]);
            if (id != null) {
                SpawnedModel s = displayManager.get(id);
                if (s != null && s.getDefinition().animations != null) {
                    for (AnimationData a : s.getDefinition().animations)
                        completions.add(a.name);
                }
            }
        }

        return completions.stream()
                .filter(c -> c.toLowerCase().startsWith(args[args.length - 1].toLowerCase()))
                .toList();
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private UUID matchId(String prefix) {
        for (SpawnedModel s : displayManager.all()) {
            if (s.getId().toString().startsWith(prefix)) return s.getId();
        }
        try { return UUID.fromString(prefix); } catch (IllegalArgumentException e) { return null; }
    }

    private void cmdDebug(CommandSender sender, String[] args) {
        if (args.length < 2) {
            // Print all loaded models and their bone origins
            for (com.modelanimator.model.ManimModel m : registry.all()) {
                sender.sendMessage(ChatColor.GOLD + "Model: " + m.meta.name);
                if (m.groups != null) {
                    for (com.modelanimator.model.ManimModel.GroupData g : m.groups) {
                        float[] o = g.origin;
                        String originStr = o != null
                            ? String.format("[%.1f, %.1f, %.1f]", o[0], o[1], o[2])
                            : "null";
                        float bx = o != null ? (o[0]-8f)/16f : 0;
                        float by = o != null ? (o[1]-8f)/16f : 0;
                        float bz = o != null ? (o[2]-8f)/16f : 0;
                        sender.sendMessage(ChatColor.AQUA + "  bone=" + g.name
                            + ChatColor.GRAY + " origin(px)=" + originStr
                            + " → translation(blocks)=[" + String.format("%.3f,%.3f,%.3f",bx,by,bz) + "]"
                            + " item_model=" + (g.item_model != null ? g.item_model : "auto"));
                    }
                }
            }
            return;
        }
        UUID id = matchId(args[1]);
        if (id == null) { sender.sendMessage(PREFIX + ChatColor.RED + "Invalid id."); return; }
        com.modelanimator.display.SpawnedModel sm = displayManager.get(id);
        if (sm == null) { sender.sendMessage(PREFIX + ChatColor.RED + "Not found."); return; }
        sender.sendMessage(ChatColor.GOLD + "Spawned model: " + sm.getModelName());
        sender.sendMessage("  Root valid: " + (sm.getRootEntity() != null && sm.getRootEntity().isValid()));
        sender.sendMessage("  Bones: " + sm.getBoneDisplays().size());
        for (var e : sm.getBoneDisplays().entrySet()) {
            var disp = e.getValue();
            var t = disp.getTransformation();
            sender.sendMessage(ChatColor.AQUA + "  uuid=" + e.getKey().substring(0,8)
                + " valid=" + disp.isValid()
                + " translation=" + String.format("[%.3f,%.3f,%.3f]",
                    t.getTranslation().x, t.getTranslation().y, t.getTranslation().z));
        }
    }

    private void sendHelp(CommandSender s) {
        s.sendMessage(PREFIX + "Commands:");
        s.sendMessage("  /manim spawn <model>");
        s.sendMessage("  /manim remove <id|all>");
        s.sendMessage("  /manim play <id> <animation>");
        s.sendMessage("  /manim stop <id>");
        s.sendMessage("  /manim list");
        s.sendMessage("  /manim reload");
        s.sendMessage("  /manim debug <id>  ← prints bone origins + translations");
    }
}
