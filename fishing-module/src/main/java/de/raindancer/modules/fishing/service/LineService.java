package de.raindancer.modules.fishing.service;

import de.raindancer.core.platform.util.Scheduling;
import de.raindancer.core.ui.effect.Cues;
import de.raindancer.core.ui.effect.Effects;
import de.raindancer.core.ui.messages.Messages;
import de.raindancer.modules.fishing.FishingSettings;
import de.raindancer.modules.fishing.model.Catch;
import de.raindancer.modules.fishing.model.Rod;
import de.raindancer.modules.fishing.model.Water;
import de.raindancer.modules.fishing.rules.BiteRule;
import de.raindancer.modules.fishing.rules.VoidSurfaceRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fishing where vanilla cannot: lava, and the void under an End island. Vanilla's hook sinks through both, so a rod
 * with the right line has its hook held at the surface (or hanging in the nothing), and the bite is timed here: a
 * wait, a moment of the hook dipping and the place flaring, and a reel-in during that moment lands the catch.
 *
 * <p>An ordinary line in lava burns through; in the void it simply falls, as it always did.
 */
public final class LineService implements IFishingService {

    private enum Phase { FALLING, WAITING, BITING }

    private static final class Line {
        final UUID player;
        Phase phase = Phase.FALLING;
        Water water;
        int ticks;
        boolean warned;
        de.raindancer.modules.fishing.visual.LineVisual visual;

        Line(UUID player) {
            this.player = player;
        }
    }

    private static final long PERIOD = 2L;
    private static final VoidSurfaceRule VOID = new VoidSurfaceRule();

    private final Plugin plugin;
    private final Messages messages;
    private final Effects effects;
    private final CatchService catches;
    private final ProfileService profiles;
    private final BiteRule bites;
    private final Random random;
    private final Map<UUID, Line> lines = new ConcurrentHashMap<>();

    private final BaitService baits;

    public LineService(Plugin plugin, Messages messages, Effects effects, CatchService catches,
                       ProfileService profiles, BiteRule bites, Random random, BaitService baits) {
        this.baits = baits;
        this.plugin = plugin;
        this.messages = messages;
        this.effects = effects;
        this.catches = catches;
        this.profiles = profiles;
        this.bites = bites;
        this.random = random;
    }

    @Override
    public void settings(FishingSettings settings) {
        // Nothing to swap: the line's behaviour is the rod's, and the odds are the catch service's.
    }

    /** A rod was cast: watch its hook for lava and the void. */
    public void cast(Player player, FishHook hook) {
        Line line = new Line(player.getUniqueId());
        lines.put(hook.getUniqueId(), line);
        Rod rod = catches.rodOf(player);
        Scheduling.entityTimer(plugin, hook, PERIOD, PERIOD, task -> {
            if (!hook.isValid()) {
                task.cancel();
                Line gone = lines.remove(hook.getUniqueId());
                if (gone != null && gone.visual != null) {
                    gone.visual.remove();
                }
                baits.take(hook.getUniqueId());
                return;
            }
            watch(player, hook, line, rod);
        }, () -> lines.remove(hook.getUniqueId()));
    }

    private void watch(Player player, FishHook hook, Line line, Rod rod) {
        switch (line.phase) {
            case FALLING -> fall(player, hook, line, rod);
            case WAITING -> {
                line.ticks -= (int) PERIOD;
                if (line.visual != null) {
                    line.visual.waiting(line.ticks, (int) PERIOD);
                }
                if (line.ticks <= 0) {
                    line.phase = Phase.BITING;
                    line.ticks = bites.biteWindowTicks();
                    bite(player, hook, line);
                }
            }
            case BITING -> {
                line.ticks -= (int) PERIOD;
                flare(hook.getLocation(), line.water, 2);
                if (line.ticks <= 0) {
                    line.phase = Phase.WAITING;
                    line.ticks = wait(player, hook);
                    if (line.visual != null) {
                        line.visual.missed();
                    }
                }
            }
        }
    }

    private void fall(Player player, FishHook hook, Line line, Rod rod) {
        Location at = hook.getLocation();
        World world = at.getWorld();
        if (at.getBlock().getType() == Material.LAVA) {
            if (!rod.fishes(Water.LAVA)) {
                messages.send(player, "fishing.line.burnt");
                effects.play(player.getUniqueId(), Cues.NO);
                hook.remove();
                return;
            }
            // on top of the lava, not in it: a float half sunk in lava is a float nobody sees
            Location surface = at.getBlock().getLocation().add(0.5, 0.95, 0.5);
            while (surface.clone().add(0, 1, 0).getBlock().getType() == Material.LAVA) {
                surface.add(0, 1, 0);
            }
            hold(hook, surface);
            line.water = Water.LAVA;
            line.visual = de.raindancer.modules.fishing.visual.LineVisual.settle(hook, Water.LAVA, surface);
        } else if (world.getEnvironment() == World.Environment.THE_END
                && VOID.restAt(true, overVoid(at), at.getY()).isPresent()) {
            if (!rod.fishes(Water.VOID)) {
                if (!line.warned) {
                    line.warned = true;
                    messages.send(player, "fishing.line.void-needed");
                }
                return;
            }
            // The void's surface: the float rests on it like on water, however far past it the hook fell.
            Location surface = new Location(world, at.getX(), VoidSurfaceRule.SURFACE, at.getZ());
            hold(hook, surface);
            line.water = Water.VOID;
            line.visual = de.raindancer.modules.fishing.visual.LineVisual.settle(hook, Water.VOID, surface);
        } else {
            return;
        }
        line.phase = Phase.WAITING;
        line.ticks = wait(player, hook);
    }

    private int wait(Player player, FishHook hook) {
        return bites.customWait(catches.rodOf(player), profiles.of(player.getUniqueId()),
                catches.inSpot(hook.getLocation()), baits.on(hook.getUniqueId()), random);
    }

    private static boolean overVoid(Location at) {
        World world = at.getWorld();
        for (int y = at.getBlockY(); y >= world.getMinHeight(); y--) {
            if (!world.getBlockAt(at.getBlockX(), y, at.getBlockZ()).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private static void hold(FishHook hook, Location where) {
        hook.setGravity(false);
        hook.setVelocity(new Vector());
        hook.setFireTicks(0);
        hook.setInvulnerable(true);
        hook.teleport(where);
    }

    private void bite(Player player, FishHook hook, Line line) {
        Water water = line.water;
        if (line.visual != null) {
            line.visual.bite();
        } else {
            hook.teleport(hook.getLocation().add(0, -0.15, 0));
        }
        flare(hook.getLocation(), water, 12);
        effects.play(player.getUniqueId(), Cues.NOTIFY);
    }

    private static void flare(Location at, Water water, int amount) {
        World world = at.getWorld();
        if (water == Water.LAVA) {
            world.spawnParticle(Particle.LAVA, at, Math.max(1, amount / 4));
            world.spawnParticle(Particle.FLAME, at, amount, 0.2, 0.05, 0.2, 0.01);
        } else {
            world.spawnParticle(Particle.REVERSE_PORTAL, at, amount, 0.2, 0.2, 0.2, 0.02);
            world.spawnParticle(Particle.END_ROD, at, Math.max(1, amount / 4), 0.1, 0.1, 0.1, 0.01);
        }
    }

    /**
     * The rod was reeled in. Lands a catch when a fish was biting; true when this hook was one of ours, so the
     * caller knows vanilla had nothing to do with it.
     */
    public boolean reeled(Player player, FishHook hook) {
        Line line = lines.remove(hook.getUniqueId());
        if (line != null && line.visual != null) {
            line.visual.remove();
        }
        if (line == null || line.phase == Phase.FALLING) {
            return false;
        }
        if (line.phase == Phase.BITING) {
            Location at = hook.getLocation();
            Catch caught = catches.decide(player, at, line.water, true, baits.take(hook.getUniqueId()));
            Optional<ItemStack> stack = catches.stack(caught);
            stack.ifPresent(item -> catches.bringUp(player, at, line.water, item));
            flare(at, line.water, 20);
            catches.credit(player, caught);
            player.giveExp(1 + random.nextInt(6));
        }
        return true;
    }

    public void forget(UUID player) {
        lines.values().removeIf(line -> {
            if (!line.player.equals(player)) {
                return false;
            }
            if (line.visual != null) {
                try {
                    line.visual.remove();
                } catch (RuntimeException notOnItsThread) {
                    // The ring is never saved with the world; it goes with the chunk.
                }
            }
            return true;
        });
    }
}
