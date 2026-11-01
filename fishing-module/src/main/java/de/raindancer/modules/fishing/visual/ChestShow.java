package de.raindancer.modules.fishing.visual;

import de.raindancer.core.platform.util.Scheduling;
import de.raindancer.modules.fishing.model.Tier;
import de.raindancer.modules.fishing.store.FishingPack;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;

/**
 * A placed sea chest opening where it stands: it shakes harder and harder in its tier's colour, the lid bursts open
 * on its hinge, and what was inside comes out one thing at a time — each rises out of the chest in a puff of smoke,
 * turns once in the air and sinks back down as the next one comes up. Empty, the chest shuts, wobbles, shrinks and is
 * gone in a puff.
 *
 * <p>The show only shows: the caller hands each thing over at {@link Moments#revealed}, and {@link Moments#done}
 * tells it where a show cut short stopped, so nothing is ever lost to an unloaded chunk.
 */
public final class ChestShow {

    /** What the caller does at the show's moments. */
    public interface Moments {
        /** The lid is open. */
        void opened();

        /** Thing {@code index} has turned in the air and is sinking back: hand it over. */
        void revealed(int index);

        /** Everything is taken down; {@code next} is the first thing never revealed (the count when all were). */
        void done(int next);
    }

    static final int SHAKE_UNTIL = 30;
    static final int OPEN_FOR = 6;
    /** One thing's turn: up, then a full turn in three steps; it sinks while the next one rises. */
    static final int RISE = 8;
    static final int TURN_STEP = 4;
    static final int EACH = RISE + 3 * TURN_STEP;
    static final int SINK = 7;
    static final int WOBBLE = 12;
    static final int VANISH = 10;
    /**
     * The lid's hinge at the back, from the model's centre, as the display shows it: an item display turns the model
     * half round, so the back (south in Blockbench) is to the north.
     */
    private static final Vector3f HINGE = new Vector3f(0f, 1f / 16f, -5f / 16f);
    private static final float LID_OPEN = (float) Math.toRadians(110);
    private static final float HIGH = 1.35f;
    private static final float TURN = (float) (Math.PI * 2 / 3);

    private ChestShow() {
    }

    /** How many ticks the whole show runs for this many things. */
    public static int length(int things) {
        return SHAKE_UNTIL + OPEN_FOR + things * EACH + SINK + WOBBLE + VANISH + 1;
    }

    /**
     * Plays it; call on the region thread that owns {@code at}.
     *
     * @param at    the chest's block, its floor corner
     * @param yaw   radians about the vertical: which way its front looks
     * @param items what comes out, in order
     */
    public static void play(Plugin plugin, Location at, float yaw, Tier tier, List<ItemStack> items, Moments moments) {
        World world = at.getWorld();
        Location centre = at.clone().add(0.5, 0.5, 0.5);
        ItemDisplay base = part(world, centre, FishingPack.chestModel(tier.chest(), "base"), tier);
        ItemDisplay lid = part(world, centre, FishingPack.chestModel(tier.chest(), "lid"), tier);
        pose(base, lid, yaw, 1f, 0f, 0f, 0f, 0);
        Color colour = Color.fromRGB(tier.colour());
        int revealStart = SHAKE_UNTIL + OPEN_FOR;
        int lastStart = revealStart + items.size() * EACH;
        int wobbleAt = lastStart + SINK;
        int vanishAt = wobbleAt + WOBBLE;
        int ending = vanishAt + VANISH;
        int[] tick = {0};
        int[] next = {0};
        ItemDisplay[] showing = {null};
        boolean[] over = {false};

        Scheduling.regionTimer(plugin, at, 1L, 1L, task -> {
            int now = tick[0]++;
            if (!base.isValid() || !lid.isValid()) {
                finish(task, base, lid, showing, over, moments, next[0]);
                return;
            }
            if (now > 0 && now < SHAKE_UNTIL && now % 2 == 0) {
                float strength = now / (float) SHAKE_UNTIL;
                float shake = (float) Math.toRadians((now % 4 == 0 ? 1 : -1) * (2 + 8 * strength));
                pose(base, lid, yaw, 1f, 0.05f * strength, shake, 0f, 2);
                world.spawnParticle(Particle.DUST, centre.clone().add(0, 0.2, 0), 2 + (int) (10 * strength),
                        0.5, 0.4, 0.5, new Particle.DustOptions(colour, 1.2f));
            } else if (now == SHAKE_UNTIL) {
                pose(base, lid, yaw, 1f, 0f, 0f, LID_OPEN, OPEN_FOR);
                burst(world, centre, tier, colour);
                moments.opened();
            } else if (now >= revealStart && now <= lastStart) {
                int since = now - revealStart;
                int index = since / EACH;
                int phase = since % EACH;
                if (phase == 0) {
                    if (showing[0] != null) {
                        // the one before sinks back into the chest as this one comes up, and is handed over
                        ItemDisplay sinking = showing[0];
                        move(sinking, 0.05f, 0f, 0.25f, SINK);
                        Scheduling.regionLater(plugin, at, SINK, sinking::remove);
                        showing[0] = null;
                        next[0] = index;
                        moments.revealed(index - 1);
                    }
                    if (index < items.size()) {
                        showing[0] = appear(world, centre, items.get(index));
                        world.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, centre.clone().add(0, 0.6, 0), 5,
                                0.15, 0.05, 0.15, 0.02);
                        world.spawnParticle(Particle.CLOUD, centre.clone().add(0, 0.55, 0), 8, 0.2, 0.1, 0.2,
                                0.02);
                    }
                } else if (showing[0] != null) {
                    if (phase == 1) {
                        move(showing[0], HIGH, 0f, 0.75f, RISE - 1);
                    } else if (phase >= RISE && (phase - RISE) % TURN_STEP == 0) {
                        int step = (phase - RISE) / TURN_STEP + 1;
                        move(showing[0], HIGH, TURN * step, 0.75f, TURN_STEP);
                        if (step == 1 && tier.atLeast(Tier.EPIC)) {
                            world.spawnParticle(Particle.END_ROD, centre.clone().add(0, HIGH, 0), 6,
                                    0.25, 0.25, 0.25, 0.02);
                        }
                    }
                    if (phase % 3 == 0) {
                        world.spawnParticle(Particle.SMOKE, centre.clone().add(0, 0.6 + phase * 0.04, 0), 2,
                                0.08, 0.05, 0.08, 0.01);
                    }
                }
            } else if (now >= wobbleAt && now < vanishAt && (now - wobbleAt) % 2 == 0) {
                // empty: the lid shuts and the chest wobbles before it goes
                int step = (now - wobbleAt) / 2;
                float wobble = (float) Math.toRadians((step % 2 == 0 ? 1 : -1) * 6);
                pose(base, lid, yaw, 1f, 0f, wobble, 0f, 2);
            } else if (now == vanishAt) {
                pose(base, lid, yaw, 0.05f, 0f, 0f, 0f, VANISH);
                world.spawnParticle(Particle.CLOUD, centre, 20, 0.35, 0.3, 0.35, 0.03);
                world.spawnParticle(Particle.DUST, centre, 20, 0.4, 0.4, 0.4, new Particle.DustOptions(colour, 1.5f));
            } else if (now >= ending) {
                finish(task, base, lid, showing, over, moments, next[0]);
            }
        });
    }

    private static void finish(ScheduledTask task, ItemDisplay base, ItemDisplay lid, ItemDisplay[] showing,
                               boolean[] over, Moments moments, int next) {
        task.cancel();
        base.remove();
        lid.remove();
        if (showing[0] != null) {
            showing[0].remove();
        }
        if (!over[0]) {
            over[0] = true;
            moments.done(next);
        }
    }

    private static ItemDisplay part(World world, Location centre, String model, Tier tier) {
        return world.spawn(centre, ItemDisplay.class, display -> {
            display.setPersistent(false);
            display.setItemStack(Looks.wearing(model));
            display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            if (tier.atLeast(Tier.EPIC)) {
                display.setBrightness(new Display.Brightness(15, 15));
            }
        });
    }

    /**
     * Sets both parts, interpolating over {@code ticks}.
     *
     * @param shake a sway about the chest's front-to-back axis, radians
     * @param open  how far the lid stands open on its hinge, radians
     */
    private static void pose(ItemDisplay base, ItemDisplay lid, float yaw, float scale, float lift, float shake,
                             float open, int ticks) {
        Quaternionf turn = new Quaternionf().rotateY(yaw).rotateZ(shake);
        Transformation body = new Transformation(new Vector3f(0, lift, 0), turn, new Vector3f(scale), new Quaternionf());
        Quaternionf hinged = new Quaternionf(turn).rotateX(-open);
        Vector3f hinge = new Vector3f(HINGE).mul(scale);
        // Turning about the hinge rather than the centre: move the hinge back to where it was.
        Vector3f swung = new Quaternionf().rotateX(-open).transform(new Vector3f(hinge));
        Vector3f offset = turn.transform(new Vector3f(hinge).sub(swung)).add(0, lift, 0);
        Transformation top = new Transformation(offset, hinged, new Vector3f(scale), new Quaternionf());
        for (ItemDisplay part : List.of(base, lid)) {
            part.setInterpolationDelay(0);
            part.setInterpolationDuration(ticks);
        }
        base.setTransformation(body);
        lid.setTransformation(top);
    }

    private static void burst(World world, Location centre, Tier tier, Color colour) {
        Location mouth = centre.clone().add(0, 0.6, 0);
        world.spawnParticle(Particle.DUST, mouth, 40, 0.4, 0.4, 0.4, new Particle.DustOptions(colour, 2.0f));
        world.spawnParticle(Particle.CLOUD, mouth, 8, 0.3, 0.2, 0.3, 0.02);
        if (tier.atLeast(Tier.RARE)) {
            world.spawnParticle(Particle.END_ROD, mouth, 12 + 6 * tier.ordinal(), 0.2, 0.6, 0.2, 0.08);
        }
        if (tier.atLeast(Tier.LEGENDARY)) {
            world.spawnParticle(Particle.FIREWORK, mouth, 30, 0.3, 0.8, 0.3, 0.12);
        }
        if (tier == Tier.MYTHIC) {
            world.spawnParticle(Particle.REVERSE_PORTAL, mouth, 80, 0.6, 1.2, 0.6, 0.05);
            world.strikeLightningEffect(centre.clone().add(0, -0.5, 0));
        }
    }

    /** A thing appearing small at the chest's mouth; it is sent up the tick after, or the client would not see it move. */
    private static ItemDisplay appear(World world, Location centre, ItemStack item) {
        return world.spawn(centre.clone().add(0, 0.25, 0), ItemDisplay.class, shown -> {
            shown.setPersistent(false);
            shown.setItemStack(item.clone());
            shown.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.GROUND);
            shown.setBrightness(new Display.Brightness(15, 15));
            shown.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(0.3f),
                    new Quaternionf()));
        });
    }

    private static void move(ItemDisplay display, float height, float turn, float scale, int ticks) {
        display.setInterpolationDelay(0);
        display.setInterpolationDuration(ticks);
        display.setTransformation(new Transformation(new Vector3f(0, height, 0), new Quaternionf().rotateY(turn),
                new Vector3f(scale), new Quaternionf()));
    }
}
