package de.raindancer.modules.fishing.service;

import de.raindancer.core.ui.effect.Cues;
import de.raindancer.core.ui.effect.Effects;
import de.raindancer.core.ui.messages.Messages;
import de.raindancer.modules.fishing.FishingSettings;
import de.raindancer.modules.fishing.model.Catch;
import de.raindancer.modules.fishing.model.Rod;
import de.raindancer.modules.fishing.model.Spot;
import de.raindancer.modules.fishing.model.Tier;
import de.raindancer.modules.fishing.model.Water;
import de.raindancer.modules.fishing.rules.CatchRule;
import de.raindancer.modules.fishing.store.RodTags;
import net.kyori.adventure.text.Component;
import de.raindancer.modules.fishing.rules.WeightRule;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.Optional;
import java.util.Random;

/** A catch, from deciding what it is to the fish in the player's hands and the experience on their record. */
public final class CatchService implements IFishingService {

    private final Server server;
    private final org.bukkit.plugin.Plugin plugin;
    private final Messages messages;
    private final Effects effects;
    private final FishForge forge;
    private final ProfileService profiles;
    private final SpotService spots;
    private final CatchRule rule;
    private final Random random;
    private final de.raindancer.core.RainsCore core;
    private final WeightRule weights = new WeightRule();
    private volatile FishingSettings settings;

    public CatchService(org.bukkit.plugin.Plugin plugin, Server server, de.raindancer.core.RainsCore core,
                        Messages messages, Effects effects,
                        FishForge forge, ProfileService profiles, SpotService spots, CatchRule rule, Random random,
                        FishingSettings settings) {
        this.core = core;
        this.plugin = plugin;
        this.server = server;
        this.messages = messages;
        this.effects = effects;
        this.forge = forge;
        this.profiles = profiles;
        this.spots = spots;
        this.rule = rule;
        this.random = random;
        this.settings = settings;
    }

    @Override
    public void settings(FishingSettings settings) {
        this.settings = settings;
    }

    /** The rod the player is fishing with: the main hand's, else the off hand's. */
    public Rod rodOf(Player player) {
        PlayerInventory inventory = player.getInventory();
        ItemStack main = inventory.getItemInMainHand();
        return forge.rodOf(RodTags.isRod(main) ? main : inventory.getItemInOffHand());
    }

    public boolean inSpot(Location hook) {
        return spots.at(hook).isPresent();
    }

    /**
     * Decides a catch; call on the hook's region thread.
     *
     * @param vanillaWasFish vanilla put a fish on the hook (rather than treasure or junk)
     */
    public Catch decide(Player player, Location hook, Water water, boolean vanillaWasFish) {
        FishingSettings live = settings;
        Optional<Spot> spot = spots.at(hook);
        boolean replacing = vanillaWasFish && (live.replaceVanillaFish() || water.needsSpecialLine());
        CatchRule.Cast cast = new CatchRule.Cast(water, rodOf(player), profiles.of(player.getUniqueId()),
                spot.isPresent(), live.chestPermille(), live.spotChestPermille(),
                replacing || water.needsSpecialLine());
        Catch caught = rule.roll(cast, random);
        if (!(caught instanceof Catch.Vanilla)) {
            spot.ifPresent(spots::fished);
        }
        return caught;
    }

    /** What the catch is as an item; empty for vanilla's own. */
    public Optional<ItemStack> stack(Catch caught) {
        return switch (caught) {
            case Catch.Fish fish -> Optional.of(forge.fish(fish.kind(), fish.amount()));
            case Catch.Chest chest -> Optional.of(forge.chest(chest.tier(), 1));
            case Catch.Vanilla vanilla -> Optional.empty();
        };
    }

    /**
     * The catch comes up where the hook was, turns in the air and goes into the fisher's bag (or at their feet when
     * it is full, or where it came up when they left meanwhile); call on the hook's region thread.
     */
    public void bringUp(Player player, org.bukkit.Location hook, Water water, ItemStack item) {
        org.bukkit.Location from = hook.clone();
        de.raindancer.modules.fishing.visual.CatchShow.play(plugin, from, player, item, water, () -> {
            if (!player.isOnline()) {
                from.getWorld().dropItemNaturally(from, item);
                return;
            }
            de.raindancer.core.platform.util.Scheduling.entity(plugin, player, () -> {
                player.getInventory().addItem(item).values()
                        .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
                effects.play(player.getUniqueId(), Cues.CLICK);
            });
        });
    }

    /** Puts it on the player's record and tells them — and, for a whopper of a rare fish, everybody. */
    public void credit(Player player, Catch caught) {
        switch (caught) {
            case Catch.Fish fish -> {
                WeightRule.Weighing weighing = weights.weigh(fish.kind(), random);
                String weight = WeightRule.format(weighing.kilos());
                double best = profiles.of(player.getUniqueId()).heaviest(fish.kind().id());
                profiles.caught(player, fish.kind().id(), fish.amount(), fish.tier(), weighing.kilos());
                core.actionBars().show(player.getUniqueId(), "fishing", messages.get("fishing.caught.weighed",
                                "fish", fish.kind().name(), "weight", weight)
                        .colorIfAbsent(net.kyori.adventure.text.format.TextColor.color(fish.tier().colour())),
                        java.time.Duration.ofSeconds(4), de.raindancer.core.ui.actionbar.ActionBarPriority.NORMAL);
                if (fish.amount() > 1) {
                    messages.send(player, "fishing.caught.double", "fish", fish.kind().name());
                }
                if (fish.tier().atLeast(Tier.RARE)) {
                    effects.play(player.getUniqueId(), fish.tier().atLeast(Tier.EPIC) ? Cues.REWARD : Cues.EARNED);
                    messages.send(player, "fishing.caught.fish", "fish", fish.kind().name(),
                            "tier", fish.tier().title(), "weight", weight);
                }
                if (best > 0 && weighing.kilos() > best) {
                    messages.send(player, "fishing.caught.record", "fish", fish.kind().name(), "weight", weight);
                }
                if (weights.worthTelling(fish.tier(), weighing)) {
                    whopper(player, fish.kind().name(), fish.tier().title(), weight);
                }
            }
            case Catch.Chest chest -> {
                effects.play(player.getUniqueId(), Cues.REWARD);
                messages.send(player, "fishing.caught.chest", "chest", chest.tier().chestName());
            }
            case Catch.Vanilla vanilla -> {
                // vanilla's own catch, vanilla's own experience
            }
        }
    }


    /**
     * A whopper of a rare fish: everybody hears, and everybody but the catcher gets a {@code [W]} of their own to
     * congratulate with — one each, theirs alone, good for ten minutes.
     */
    private void whopper(Player catcher, String fish, String tier, String weight) {
        UUID catcherId = catcher.getUniqueId();
        String catcherName = catcher.getName();
        for (Player each : server.getOnlinePlayers()) {
            Component line = messages.prefixed("fishing.whopper.broadcast", "player", catcherName, "fish", fish,
                    "tier", tier, "weight", weight);
            if (!each.getUniqueId().equals(catcherId)) {
                line = line.append(Component.space()).append(core.buttons()
                        .label(messages.raw("fishing.whopper.w"))
                        .tooltip(messages.raw("fishing.whopper.w-tooltip"))
                        .forOnly(each.getUniqueId())
                        .expiringIn(java.time.Duration.ofMinutes(10))
                        .does(clicker -> congratulate(catcherId, catcherName, clicker, fish))
                        .render());
            }
            each.sendMessage(line);
        }
    }

    private void congratulate(UUID catcherId, String catcherName, UUID clickerId, String fish) {
        Player clicker = server.getPlayer(clickerId);
        String clickerName = clicker == null ? "Somebody" : clicker.getName();
        Player catcher = server.getPlayer(catcherId);
        if (catcher != null) {
            messages.send(catcher, "fishing.whopper.got-w", "player", clickerName, "fish", fish);
            effects.play(catcherId, Cues.REWARD);
        }
        if (clicker != null) {
            messages.send(clicker, "fishing.whopper.gave-w", "player", catcherName);
        }
    }
}
