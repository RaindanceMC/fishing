package de.raindancer.modules.fishing.service;

import de.raindancer.core.platform.util.Scheduling;
import de.raindancer.core.ui.effect.Cues;
import de.raindancer.core.ui.effect.Effects;
import de.raindancer.core.ui.messages.Messages;
import de.raindancer.modules.fishing.FishingSettings;
import de.raindancer.modules.fishing.model.Rod;
import de.raindancer.modules.fishing.model.RodPart;
import de.raindancer.modules.fishing.rules.UpgradeRule;
import de.raindancer.modules.fishing.store.ProfileStore;
import de.raindancer.modules.fishing.store.RodTags;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Fitting parts to the rod in a player's hand. Upgrades are earned, not bought: they cost fishing experience and
 * need a fishing level, and the ranks are written onto the rod itself.
 */
public final class RodService implements IFishingService {

    private final Plugin plugin;
    private final Messages messages;
    private final Effects effects;
    private final ProfileService profiles;
    private final ProfileStore store;
    private final FishForge forge;
    private volatile FishingSettings settings;

    public RodService(Plugin plugin, Messages messages, Effects effects, ProfileService profiles, ProfileStore store,
                      FishForge forge, FishingSettings settings) {
        this.forge = forge;
        this.plugin = plugin;
        this.messages = messages;
        this.effects = effects;
        this.profiles = profiles;
        this.store = store;
        this.settings = settings;
    }

    @Override
    public void settings(FishingSettings settings) {
        this.settings = settings;
    }

    public UpgradeRule rule() {
        return new UpgradeRule(profiles.levels());
    }

    public UpgradeRule.Costs costs() {
        return settings.costs();
    }

    public Rod held(Player player) {
        return forge.rodOf(player.getInventory().getItemInMainHand());
    }

    /**
     * Upgrades the rod in the main hand by one rank of {@code part}; {@code then} runs on the player's thread once it
     * is settled, either way.
     */
    public void upgrade(Player player, RodPart part, Runnable then) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (!RodTags.isRod(hand)) {
            refuse(player, then, "fishing.rod.hold-a-rod");
            return;
        }
        if (hand.getAmount() != 1) {
            refuse(player, then, "fishing.rod.one-rod");
            return;
        }
        Rod rod = forge.rodOf(hand);
        UpgradeRule upgrades = rule();
        UpgradeRule.Costs costs = costs();
        UUID id = player.getUniqueId();
        var refusal = upgrades.refusal(rod, part, profiles.of(id), costs);
        if (refusal.isPresent()) {
            switch (refusal.get()) {
                case MAXED -> refuse(player, then, "fishing.rod.maxed", "part", part.title());
                case LEVEL_TOO_LOW -> refuse(player, then, "fishing.rod.level-too-low", "level",
                        String.valueOf(upgrades.levelNeeded(rod, part, costs)), "part",
                        part.rankName(rod.rank(part) + 1));
                case NOT_ENOUGH_EXPERIENCE -> refuse(player, then, "fishing.rod.not-enough", "cost",
                        String.valueOf(upgrades.cost(rod, part, costs)), "have",
                        String.valueOf(profiles.of(id).available()));
            }
            return;
        }
        long cost = upgrades.cost(rod, part, costs);
        int slot = player.getInventory().getHeldItemSlot();
        // Experience is spent in the database, off the server's threads, and checked again inside the change so two
        // clicks cannot spend it twice; the rank goes onto the very rod that was priced, still in the same slot.
        Scheduling.async(plugin, () -> {
            AtomicBoolean spent = new AtomicBoolean(false);
            boolean written = store.change(id, profile -> {
                if (profile.available() < cost) {
                    return profile;
                }
                spent.set(true);
                return profile.withSpent(profile.spent() + cost);
            });
            Scheduling.entity(plugin, player, () -> {
                if (!written || !spent.get()) {
                    refuse(player, then, "fishing.rod.not-enough", "cost", String.valueOf(cost), "have",
                            String.valueOf(profiles.of(id).available()));
                    return;
                }
                ItemStack now = player.getInventory().getItem(slot);
                if (!RodTags.isRod(now) || !forge.rodOf(now).equals(rod) || now.getAmount() != 1) {
                    Scheduling.async(plugin, () -> store.change(id,
                            profile -> profile.withSpent(profile.spent() - cost)));
                    refuse(player, then, "fishing.rod.moved");
                    return;
                }
                Rod upgraded = rod.with(part, rod.rank(part) + 1);
                RodTags.write(now, upgraded);
                describe(now, upgraded);
                player.getInventory().setItem(slot, now);
                effects.play(id, Cues.REWARD);
                messages.send(player, "fishing.rod.upgraded", "part", part.rankName(upgraded.rank(part)),
                        "cost", String.valueOf(cost));
                then.run();
            });
        });
    }

    /** Lists the fitted parts in the rod's lore, under whatever lore it already had of its own. */
    static void describe(ItemStack stack, Rod rod) {
        ItemMeta meta = stack.getItemMeta();
        List<Component> lore = new ArrayList<>();
        if (meta.lore() != null) {
            for (Component line : meta.lore()) {
                if (!isOurs(line)) {
                    lore.add(line);
                }
            }
        }
        for (RodPart part : RodPart.values()) {
            int rank = rod.rank(part);
            if (rank > 0) {
                lore.add(Component.text(part.rankName(rank), NamedTextColor.AQUA)
                        .decoration(TextDecoration.ITALIC, false));
            }
        }
        meta.lore(lore.isEmpty() ? null : lore);
        stack.setItemMeta(meta);
    }

    private static boolean isOurs(Component line) {
        String plain = PlainTextComponentSerializer.plainText().serialize(line);
        for (RodPart part : RodPart.values()) {
            if (plain.startsWith(part.title()) || plain.endsWith(" Line")) {
                return true;
            }
        }
        return false;
    }

    private void refuse(Player player, Runnable then, String key, Object... values) {
        effects.play(player.getUniqueId(), Cues.NO);
        messages.send(player, key, values);
        then.run();
    }
}
