package de.raindancer.modules.fishing.service;

import de.raindancer.core.platform.util.Scheduling;
import de.raindancer.core.ui.bossbar.BarPriority;
import de.raindancer.core.ui.bossbar.BarStyle;
import de.raindancer.core.ui.bossbar.BossBars;
import de.raindancer.core.ui.effect.Cues;
import de.raindancer.core.ui.effect.Effects;
import de.raindancer.core.ui.messages.Messages;
import de.raindancer.modules.fishing.FishingSettings;
import de.raindancer.modules.fishing.model.Catch;
import de.raindancer.modules.fishing.model.Water;
import de.raindancer.modules.fishing.rules.HeldInputRule;
import de.raindancer.modules.fishing.rules.TugRule;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * The reel-in battle a legendary or mythic fish sometimes puts up: the hook stays out, holding sneak reels, and a boss
 * bar shows where the rope is, how tight the line is and whether the fish is running. The rod does nothing during the
 * fight, and a right click or shift still held when it ends neither casts again nor leaves the boat. Lost in water,
 * the fish pulls the fisher in after it; in lava or the void it only tears free, as nobody is dragged to their death by
 * a fish.
 */
public final class TugService {

    private static final String BAR = "fishing-tug";
    private static final long PERIOD = 2L;
    /** Segments of the tension gauge. */
    private static final int LINE = 10;
    /** A held right click repeats every 200 ms; this outlasts that with room for a lag spike. */
    private static final long HELD_MILLIS = 750L;
    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private static final de.raindancer.core.ui.effect.Effect HOOKED = sound("entity.fishing_bobber.retrieve", 1f, 0.5f);
    private static final de.raindancer.core.ui.effect.Effect REELED = sound("entity.fishing_bobber.retrieve", 0.6f, 1.2f);
    private static final de.raindancer.core.ui.effect.Effect STRAINED = sound("block.wool.break", 1f, 0.8f);
    private static final de.raindancer.core.ui.effect.Effect DRAGGED = sound("entity.player.splash.high_speed", 1f, 1f);
    private static final de.raindancer.core.ui.effect.Effect SNAPPED = sound("entity.item.break", 1f, 1f);

    private static de.raindancer.core.ui.effect.Effect sound(String key, float volume, float pitch) {
        return de.raindancer.core.ui.effect.Effect.of(new de.raindancer.core.ui.effect.SoundCue(key, volume, pitch));
    }

    private record Running(TugRule.Fight fight, Catch.Fish caught, Water water, FishHook hook,
                           Consumer<Location> land) {
    }

    private final Plugin plugin;
    private final BossBars bars;
    private final Messages messages;
    private final Effects effects;
    private final Random random;
    private final TugRule rule = new TugRule();
    private final HeldInputRule heldCasts = new HeldInputRule(HELD_MILLIS);
    private final HeldInputRule heldShift = new HeldInputRule(HELD_MILLIS);
    private final Map<UUID, Running> fights = new ConcurrentHashMap<>();
    private volatile TugRule.Odds odds;

    public TugService(Plugin plugin, BossBars bars, Messages messages, Effects effects, Random random,
                      FishingSettings settings) {
        this.odds = settings.fightOdds();
        this.plugin = plugin;
        this.bars = bars;
        this.messages = messages;
        this.effects = effects;
        this.random = random;
    }

    public void settings(FishingSettings settings) {
        odds = settings.fightOdds();
    }

    /** Rolled once per catch: whether this one fights before it comes up. */
    public boolean fights(Catch caught) {
        return rule.fights(caught, odds, random);
    }

    /** A cast by hand right after a fight: the right click that reeled is still held, so it is not a new cast. */
    public boolean castHeldOver(UUID player) {
        return heldCasts.blocks(player, System.currentTimeMillis());
    }

    /** Shift in a boat reels during a fight and must not leave it; nor may the shift still held when it ends. */
    public boolean keepsSeated(UUID player) {
        return fights.containsKey(player) || heldShift.blocks(player, System.currentTimeMillis());
    }

    public boolean active(UUID player) {
        return fights.containsKey(player);
    }

    /**
     * Starts the fight; the hook must stay out (the caller cancels the reel that found the fish).
     *
     * @param land what landing it does, given where the hook was — call on the fisher's thread
     */
    public void start(Player player, FishHook hook, Catch.Fish caught, Water water, Consumer<Location> land) {
        UUID id = player.getUniqueId();
        if (fights.containsKey(id)) {
            return;
        }
        Running running = new Running(rule.start(caught.tier(), random), caught, water, hook, land);
        fights.put(id, running);
        effects.play(id, Cues.NOTIFY);
        effects.play(id, HOOKED);
        show(id, running.fight);
        Scheduling.entityTimer(plugin, player, PERIOD, PERIOD, task -> {
            if (fights.get(id) != running) {
                task.cancel();
                return;
            }
            if (!hook.isValid()) {
                finish(player, running, TugRule.Outcome.ESCAPED);
                task.cancel();
                return;
            }
            boolean reeling = player.getCurrentInput().isSneak();
            TugRule.Phase phase = running.fight.phase();
            rule.step(running.fight, reeling, random);
            if (reeling && random.nextInt(phase == TugRule.Phase.SURGE ? 2 : 3) == 0) {
                effects.play(id, phase == TugRule.Phase.SURGE ? STRAINED : REELED);
            }
            tug(player, running);
            if (running.fight.outcome() != TugRule.Outcome.FIGHTING) {
                finish(player, running, running.fight.outcome());
                task.cancel();
                return;
            }
            show(id, running.fight);
        }, () -> {
            fights.remove(id, running);
            bars.clear(id, BAR);
        });
    }

    /** The fish's side of it: the float darts, and in water a run tugs at the fisher too. */
    private void tug(Player player, Running running) {
        FishHook hook = running.hook;
        boolean surge = running.fight.phase() == TugRule.Phase.SURGE;
        Location at = hook.getLocation();
        Scheduling.entity(plugin, hook, () -> {
            if (running.water != Water.LAVA && running.water != Water.VOID) {
                double push = surge ? 0.12 : 0.04;
                hook.setVelocity(hook.getVelocity().add(new Vector(random.nextGaussian() * push, 0,
                        random.nextGaussian() * push)));
            }
            at.getWorld().spawnParticle(running.water == Water.LAVA ? Particle.LAVA
                    : running.water == Water.VOID ? Particle.REVERSE_PORTAL : Particle.SPLASH, at,
                    surge ? 6 : 2, 0.2, 0.05, 0.2, 0.02);
        });
        if (surge && running.water != Water.LAVA && running.water != Water.VOID && random.nextInt(4) == 0) {
            Vector towards = at.toVector().subtract(player.getLocation().toVector()).setY(0);
            if (towards.lengthSquared() > 0.01) {
                player.setVelocity(player.getVelocity().add(towards.normalize().multiply(0.08)));
            }
        }
    }

    private void finish(Player player, Running running, TugRule.Outcome outcome) {
        UUID id = player.getUniqueId();
        if (!fights.remove(id, running)) {
            return;
        }
        long now = System.currentTimeMillis();
        heldCasts.arm(id, now);
        heldShift.arm(id, now);
        bars.clear(id, BAR);
        FishHook hook = running.hook;
        Location at = hook.isValid() ? hook.getLocation() : player.getLocation();
        String fish = running.caught.kind().name();
        Scheduling.entity(plugin, hook, hook::remove);
        boolean deadly = running.water == Water.LAVA || running.water == Water.VOID;
        switch (outcome) {
            case LANDED -> {
                effects.play(id, Cues.REWARD);
                running.land.accept(at);
            }
            case PULLED_IN -> {
                effects.play(id, Cues.NO);
                if (deadly) {
                    messages.send(player, "fishing.tug.tore-free", "fish", fish);
                    return;
                }
                messages.send(player, "fishing.tug.pulled-in", "fish", fish);
                Vector towards = at.toVector().subtract(player.getLocation().toVector()).setY(0);
                Vector fling = (towards.lengthSquared() > 0.01 ? towards.normalize() : player.getLocation()
                        .getDirection().setY(0).normalize()).multiply(1.25).setY(0.5);
                player.setVelocity(fling);
                effects.play(id, DRAGGED);
            }
            case SNAPPED -> {
                effects.play(id, Cues.NO);
                messages.send(player, "fishing.tug.snapped", "fish", fish);
                effects.play(id, SNAPPED);
            }
            default -> {
                effects.play(id, Cues.NO);
                messages.send(player, "fishing.tug.escaped", "fish", fish);
            }
        }
    }

    private void show(UUID player, TugRule.Fight fight) {
        double tension = fight.tension();
        int tight = (int) Math.round(tension * LINE);
        String colour = tension < 0.5 ? "<green>" : tension < 0.8 ? "<yellow>" : "<red>";
        String line = colour + "▮".repeat(tight) + "<dark_gray>" + "▮".repeat(LINE - tight);
        String head = switch (fight.phase()) {
            case CALM -> "<green>Reel! <gray>Hold sneak";
            case WARNING -> "<yellow>It is about to run…";
            case SURGE -> "<red><bold>It is running! Let go!</bold>";
        };
        Component text = MINI.deserialize(head + " <dark_gray>· <gray>Line " + line
                + " <dark_gray>· <gray>" + fight.secondsLeft() + "s");
        BossBar.Color bar = switch (fight.phase()) {
            case CALM -> BossBar.Color.GREEN;
            case WARNING -> BossBar.Color.YELLOW;
            case SURGE -> BossBar.Color.RED;
        };
        bars.show(player, BAR, BarStyle.of(text).progress((float) fight.rope()).colour(bar)
                .overlay(BossBar.Overlay.NOTCHED_10), BarPriority.HIGH);
    }

    /** A fisher who left mid-fight: the fish is gone. */
    public void forget(UUID player) {
        heldCasts.forget(player);
        heldShift.forget(player);
        Running running = fights.remove(player);
        if (running != null) {
            bars.clear(player, BAR);
        }
    }
}
