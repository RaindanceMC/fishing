package de.raindancer.modules.fishing.visual;

import de.raindancer.core.platform.util.Scheduling;
import de.raindancer.modules.fishing.model.Spot;
import de.raindancer.modules.fishing.model.Water;
import de.raindancer.modules.fishing.store.FishingPack;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.TextDisplay;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.concurrent.ThreadLocalRandom;

/**
 * A fishing spot as players see it: rings rippling out on the water in the shoal's colour, a big bobber turning
 * slowly over it the way a dropped item does, and a sign above that. Nothing of it is saved with the world — a
 * restart leaves no stray displays behind.
 */
public final class SpotVisual {

    /** As wide as the water a spot covers: twice SpotService.RADIUS, the model being one block across. */
    private static final float RIPPLE_SCALE = 8.0f;
    private static final float BOBBER_SCALE = 1.8f;
    private static final long PERIOD = 10L;

    private final ItemDisplay ripple;
    private final ItemDisplay bobber;
    private final TextDisplay sign;
    private final ScheduledTask timer;

    private SpotVisual(ItemDisplay ripple, ItemDisplay bobber, TextDisplay sign, ScheduledTask timer) {
        this.ripple = ripple;
        this.bobber = bobber;
        this.sign = sign;
        this.timer = timer;
    }

    /** Spawns it; call on the region thread that owns {@code at}. */
    public static SpotVisual spawn(Plugin plugin, Location at, Spot spot, Component text) {
        World world = at.getWorld();
        boolean glows = spot.water() == Water.VOID || spot.water() == Water.LAVA || spot.water() == Water.CAVE;
        Location surface = at.clone().add(0, 0.06, 0);
        ItemDisplay ripple = world.spawn(surface, ItemDisplay.class, display -> {
            display.setPersistent(false);
            display.setItemStack(Looks.wearing(FishingPack.rippleModel(), spot.water().ripple()));
            display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            // NONE draws the model centred on the display, so the quad (at the bottom of its block) is lifted back
            // up to the water line.
            display.setTransformation(new Transformation(new Vector3f(0, 0.5f, 0), new AxisAngle4f(),
                    new Vector3f(RIPPLE_SCALE, 1f, RIPPLE_SCALE), new AxisAngle4f()));
            if (glows) {
                display.setBrightness(new Display.Brightness(15, 15));
            }
        });
        ItemDisplay bobber = world.spawn(at.clone().add(0, 1.4, 0), ItemDisplay.class, display -> {
            display.setPersistent(false);
            display.setItemStack(Looks.wearing(FishingPack.bobberModel(spot.water().bobber())));
            display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            display.setTransformation(bobberPose(0));
            display.setInterpolationDuration((int) PERIOD);
            if (glows) {
                display.setBrightness(new Display.Brightness(15, 15));
            }
        });
        TextDisplay sign = world.spawn(at.clone().add(0, 3.1, 0), TextDisplay.class, display -> {
            display.setPersistent(false);
            display.text(text);
            display.setBillboard(Display.Billboard.CENTER);
            display.setShadowed(true);
            display.setSeeThrough(false);
            display.setBrightness(new Display.Brightness(15, 15));
        });
        int[] step = {0};
        ScheduledTask timer = Scheduling.regionTimer(plugin, at, PERIOD, PERIOD, task -> {
            if (!bobber.isValid()) {
                task.cancel();
                return;
            }
            step[0]++;
            bobber.setInterpolationDelay(0);
            bobber.setTransformation(bobberPose(step[0]));
            sparkle(world, at, spot.water());
        });
        return new SpotVisual(ripple, bobber, sign, timer);
    }

    /** Up and down like a dropped item, turning a little more each step. */
    private static Transformation bobberPose(int step) {
        float lift = (float) Math.sin(step * 0.35) * 0.12f;
        float turn = (float) Math.toRadians(step * 14 % 360);
        return new Transformation(new Vector3f(0, lift, 0), new AxisAngle4f(turn, 0, 1, 0),
                new Vector3f(BOBBER_SCALE, BOBBER_SCALE, BOBBER_SCALE), new AxisAngle4f());
    }

    private static void sparkle(World world, Location at, Water water) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        double dx = random.nextDouble(-1.2, 1.2);
        double dz = random.nextDouble(-1.2, 1.2);
        Location point = at.clone().add(dx, 0.1, dz);
        switch (water) {
            case VOID -> world.spawnParticle(Particle.REVERSE_PORTAL, point, 3, 0.2, 0.3, 0.2, 0.01);
            case LAVA -> world.spawnParticle(Particle.LAVA, point, 1);
            default -> world.spawnParticle(Particle.BUBBLE_POP, point.add(0, 0.05, 0), 2, 0.15, 0.0, 0.15, 0.0);
        }
    }

    public void text(Component text) {
        if (sign.isValid()) {
            sign.text(text);
        }
    }

    /** Takes it down; call on the owning region thread (or on the main thread while stopping). */
    public void remove() {
        if (timer != null) {
            timer.cancel();
        }
        ripple.remove();
        bobber.remove();
        sign.remove();
    }

    public Location location() {
        return ripple.getLocation();
    }
}
