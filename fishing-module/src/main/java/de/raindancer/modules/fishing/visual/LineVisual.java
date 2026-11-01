package de.raindancer.modules.fishing.visual;

import de.raindancer.modules.fishing.model.Water;
import de.raindancer.modules.fishing.store.FishingPack;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.concurrent.ThreadLocalRandom;

/**
 * A line out in lava or the void, made visible: the hook rests on the surface — on lava, or on a shimmering ring that
 * stands in for one in the void — and bobs there; before a bite a wake comes at it across the surface, the way a fish
 * comes at a float in water; at the bite it dips. Call everything on the hook's own thread.
 */
public final class LineVisual {

    /** How long before the bite the wake starts coming, in ticks. */
    public static final int WAKE = 40;
    private static final float RING = 1.4f;

    private final FishHook hook;
    private final Water water;
    private final Location rest;
    private final ItemDisplay ring;
    private final double angle = ThreadLocalRandom.current().nextDouble(Math.PI * 2);
    private int step;

    private LineVisual(FishHook hook, Water water, Location rest, ItemDisplay ring) {
        this.hook = hook;
        this.water = water;
        this.rest = rest;
        this.ring = ring;
    }

    /** Puts the hook to rest at {@code rest} and lays the surface under it. */
    public static LineVisual settle(FishHook hook, Water water, Location rest) {
        World world = rest.getWorld();
        ItemDisplay ring = world.spawn(rest.clone().add(0, -0.08, 0), ItemDisplay.class, display -> {
            display.setPersistent(false);
            display.setItemStack(Looks.wearing(FishingPack.rippleModel(), water.ripple()));
            display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            display.setTransformation(new Transformation(new Vector3f(0, 0.5f, 0), new AxisAngle4f(),
                    new Vector3f(RING, 1f, RING), new AxisAngle4f()));
            display.setBrightness(new Display.Brightness(15, 15));
        });
        LineVisual visual = new LineVisual(hook, water, rest, ring);
        hook.teleport(rest);
        return visual;
    }

    /**
     * One step of waiting, every {@code period} ticks.
     *
     * @param untilBite ticks left until the fish bites
     */
    public void waiting(int untilBite, int period) {
        step++;
        double bob = Math.sin(step * period * 0.18) * 0.05;
        hook.teleport(rest.clone().add(0, bob, 0));
        if (untilBite <= WAKE) {
            // the wake: from three blocks out, straight at the float
            double far = 0.3 + 2.7 * untilBite / (double) WAKE;
            Location at = rest.clone().add(Math.cos(angle) * far, 0.05, Math.sin(angle) * far);
            World world = rest.getWorld();
            switch (water) {
                case LAVA -> {
                    world.spawnParticle(Particle.SMOKE, at, 2, 0.05, 0.02, 0.05, 0.005);
                    if (step % 2 == 0) {
                        world.spawnParticle(Particle.LAVA, at, 1);
                    }
                }
                case VOID -> world.spawnParticle(Particle.REVERSE_PORTAL, at, 4, 0.06, 0.04, 0.06, 0.01);
                default -> world.spawnParticle(Particle.BUBBLE_POP, at, 3, 0.06, 0.02, 0.06, 0.01);
            }
        } else if (step % 6 == 0) {
            // a little life on the surface around it while nothing comes
            double a = ThreadLocalRandom.current().nextDouble(Math.PI * 2);
            Location at = rest.clone().add(Math.cos(a) * 0.9, 0.05, Math.sin(a) * 0.9);
            rest.getWorld().spawnParticle(water == Water.VOID ? Particle.PORTAL : Particle.SMOKE, at, 1, 0.02, 0.02,
                    0.02, 0);
        }
    }

    /** The bite: the float is pulled under. */
    public void bite() {
        hook.teleport(rest.clone().add(0, -0.2, 0));
    }

    /** The fish got away again: the float comes back up. */
    public void missed() {
        hook.teleport(rest);
    }

    public void remove() {
        ring.remove();
    }

    public Location rest() {
        return rest.clone();
    }
}
