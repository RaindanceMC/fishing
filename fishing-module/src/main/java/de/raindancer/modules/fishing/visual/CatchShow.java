package de.raindancer.modules.fishing.visual;

import de.raindancer.core.platform.util.Scheduling;
import de.raindancer.modules.fishing.model.Water;
import de.raindancer.modules.fishing.rules.EmergeRule;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.function.IntPredicate;

/**
 * A catch coming up: it shows deep under the surface where the hook was, climbs through the water in a trail of
 * bubbles (lava: embers, the void: a flicker), breaks the surface with a splash, rises slowly on, turning and dripping,
 * and then flies to its fisher. Only a show: the caller hands the catch over in {@code landed}, which runs exactly once,
 * also when the show is cut short.
 */
public final class CatchShow {

    static final int EMERGE = 16;
    static final int RISE = 30;
    static final int TURN_STEP = 10;
    static final int FLY = 6;
    private static final float HIGH = 1.7f;
    private static final float TURN = (float) (Math.PI * 2 / 3);
    private static final EmergeRule EMERGING = new EmergeRule();

    private CatchShow() {
    }

    /**
     * Plays it at {@code from}, the hook's place; call on the region thread that owns it.
     *
     * @param surfaced where it breaks the surface, when it does — for the caller's sound
     */
    public static void play(Plugin plugin, Location from, Player fisher, ItemStack item, Water water,
                            java.util.function.Consumer<Location> surfaced, Runnable landed) {
        World world = from.getWorld();
        int column = from.getBlockY();
        IntPredicate liquid = y -> isLiquid(world.getBlockAt(from.getBlockX(), y, from.getBlockZ()), water);
        double top = EMERGING.surface(column, liquid);
        float depth = (float) (Double.isNaN(top) ? EmergeRule.DEPTH : EMERGING.depth(EMERGING.blocksDeep(column, liquid)));
        Location surface = from.clone();
        surface.setY(Double.isNaN(top) ? from.getY() : top);
        ItemDisplay shown = world.spawn(surface, ItemDisplay.class, display -> {
            display.setPersistent(false);
            display.setItemStack(item.clone());
            display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.GROUND);
            if (water == Water.LAVA || water == Water.VOID) {
                display.setBrightness(new Display.Brightness(15, 15));
            }
            display.setTransformation(new Transformation(new Vector3f(0, -depth, 0), new Quaternionf(),
                    new Vector3f(0.75f), new Quaternionf()));
        });
        int[] tick = {0};
        boolean[] over = {false};
        Scheduling.regionTimer(plugin, from, 1L, 1L, task -> {
            int now = tick[0]++;
            if (!shown.isValid() || !fisher.isOnline()) {
                finish(task, shown, over, landed);
                return;
            }
            if (now == 0) {
                // up through the water to just under the surface
                move(shown, 0f, 0.85f, TURN / 2, EMERGE);
            }
            if (now < EMERGE && now % 2 == 0) {
                float under = depth * (1f - now / (float) EMERGE);
                trail(world, surface.clone().add(0, -under, 0), water);
            }
            if (now == EMERGE) {
                breakSurface(world, surface, water);
                surfaced.accept(surface);
            }
            int up = now - EMERGE;
            if (up >= 0 && up < RISE && up % TURN_STEP == 0) {
                int step = up / TURN_STEP + 1;
                move(shown, HIGH * (up + TURN_STEP) / RISE, 0.9f, TURN * step, TURN_STEP);
            } else if (up == RISE) {
                move(shown, HIGH, 0.9f, 0f, TURN_STEP);
            }
            if (up >= 0 && up < RISE + 4 && up % 3 == 0) {
                drip(world, surface.clone().add(0, HIGH * Math.min(1f, up / (float) RISE), 0), water);
            }
            if (up == RISE + 4) {
                // off to its fisher: the display glides to them, then is gone into their bag
                Location to = fisher.getLocation().add(0, 0.9, 0);
                shown.setTeleportDuration(FLY);
                shown.teleportAsync(to.subtract(0, HIGH, 0));
            } else if (up >= RISE + 4 + FLY) {
                finish(task, shown, over, landed);
            }
        });
    }

    /** Whether a block is (or holds) this water's liquid — kelp and seagrass stand in water too. */
    private static boolean isLiquid(Block block, Water water) {
        Material type = block.getType();
        if (water == Water.LAVA) {
            return type == Material.LAVA;
        }
        if (water == Water.VOID) {
            return false;
        }
        return switch (type) {
            case WATER, BUBBLE_COLUMN, KELP, KELP_PLANT, SEAGRASS, TALL_SEAGRASS -> true;
            default -> block.getBlockData() instanceof Waterlogged soaked && soaked.isWaterlogged();
        };
    }

    private static void trail(World world, Location at, Water water) {
        switch (water) {
            case LAVA -> world.spawnParticle(Particle.FLAME, at, 2, 0.1, 0.05, 0.1, 0.01);
            case VOID -> world.spawnParticle(Particle.REVERSE_PORTAL, at, 4, 0.15, 0.1, 0.15, 0.02);
            default -> world.spawnParticle(Particle.BUBBLE, at, 4, 0.15, 0.05, 0.15, 0.05);
        }
    }

    private static void finish(ScheduledTask task, ItemDisplay shown, boolean[] over, Runnable landed) {
        task.cancel();
        shown.remove();
        if (!over[0]) {
            over[0] = true;
            landed.run();
        }
    }

    private static void move(ItemDisplay display, float height, float scale, float turn, int ticks) {
        display.setInterpolationDelay(0);
        display.setInterpolationDuration(ticks);
        display.setTransformation(new Transformation(new Vector3f(0, height, 0), new Quaternionf().rotateY(turn),
                new Vector3f(scale), new Quaternionf()));
    }

    private static void breakSurface(World world, Location at, Water water) {
        switch (water) {
            case LAVA -> {
                world.spawnParticle(Particle.LAVA, at, 6, 0.2, 0.05, 0.2);
                world.spawnParticle(Particle.FLAME, at, 14, 0.25, 0.1, 0.25, 0.03);
            }
            case VOID -> {
                world.spawnParticle(Particle.REVERSE_PORTAL, at, 30, 0.3, 0.2, 0.3, 0.05);
                world.spawnParticle(Particle.END_ROD, at, 6, 0.2, 0.2, 0.2, 0.02);
            }
            default -> {
                world.spawnParticle(Particle.SPLASH, at.clone().add(0, 0.1, 0), 40, 0.35, 0.05, 0.35, 0.1);
                world.spawnParticle(Particle.BUBBLE_POP, at.clone().add(0, 0.05, 0), 12, 0.3, 0.0, 0.3, 0.02);
                world.spawnParticle(Particle.FISHING, at, 10, 0.3, 0.05, 0.3, 0.02);
            }
        }
    }

    private static void drip(World world, Location at, Water water) {
        Particle particle = switch (water) {
            case LAVA -> Particle.DRIPPING_LAVA;
            case VOID -> Particle.REVERSE_PORTAL;
            default -> Particle.DRIPPING_WATER;
        };
        world.spawnParticle(particle, at, 2, 0.12, 0.05, 0.12, 0);
    }
}
