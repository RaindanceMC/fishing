package de.raindancer.modules.fishing.visual;

import de.raindancer.core.platform.util.Scheduling;
import de.raindancer.modules.fishing.model.Water;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * A catch coming up: it breaks the surface where the hook was — a splash in water, a spurt of flame in lava, a flicker
 * in the void — rises slowly, turning, dripping as it goes, and then flies to its fisher. Only a show: the caller
 * hands the catch over in {@code landed}, which runs exactly once, also when the show is cut short.
 */
public final class CatchShow {

    static final int RISE = 30;
    static final int TURN_STEP = 10;
    static final int FLY = 6;
    private static final float HIGH = 1.7f;
    private static final float TURN = (float) (Math.PI * 2 / 3);

    private CatchShow() {
    }

    /** Plays it at {@code from}, the hook's place; call on the region thread that owns it. */
    public static void play(Plugin plugin, Location from, Player fisher, ItemStack item, Water water,
                            Runnable landed) {
        World world = from.getWorld();
        Location surface = from.clone();
        ItemDisplay shown = world.spawn(surface, ItemDisplay.class, display -> {
            display.setPersistent(false);
            display.setItemStack(item.clone());
            display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.GROUND);
            if (water == Water.LAVA || water == Water.VOID) {
                display.setBrightness(new Display.Brightness(15, 15));
            }
            display.setTransformation(new Transformation(new Vector3f(0, -0.3f, 0), new Quaternionf(),
                    new Vector3f(0.4f), new Quaternionf()));
        });
        breakSurface(world, surface, water);
        int[] tick = {0};
        boolean[] over = {false};
        Scheduling.regionTimer(plugin, from, 1L, 1L, task -> {
            int now = tick[0]++;
            if (!shown.isValid() || !fisher.isOnline()) {
                finish(task, shown, over, landed);
                return;
            }
            if (now == 0) {
                move(shown, 0f, 0.9f, 0f, TURN_STEP);
            } else if (now < RISE && now % TURN_STEP == 0) {
                int step = now / TURN_STEP;
                move(shown, HIGH * now / RISE + HIGH / 3, 0.9f, TURN * step, TURN_STEP);
            } else if (now == RISE) {
                move(shown, HIGH, 0.9f, 0f, TURN_STEP);
            }
            if (now < RISE + 4 && now % 3 == 0) {
                drip(world, shown.getLocation().add(0, HIGH * Math.min(1f, now / (float) RISE), 0), water);
            }
            if (now == RISE + 4) {
                // off to its fisher: the display glides to them, then is gone into their bag
                Location to = fisher.getLocation().add(0, 0.9, 0);
                shown.setTeleportDuration(FLY);
                shown.teleportAsync(to.subtract(0, HIGH, 0));
            } else if (now >= RISE + 4 + FLY) {
                finish(task, shown, over, landed);
            }
        });
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
