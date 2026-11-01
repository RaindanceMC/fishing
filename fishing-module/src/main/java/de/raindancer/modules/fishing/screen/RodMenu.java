package de.raindancer.modules.fishing.screen;

import de.raindancer.core.ui.menu.Icons;
import de.raindancer.core.ui.menu.Menu;
import de.raindancer.core.ui.menu.MenuLayout;
import de.raindancer.modules.fishing.FishingServices;
import de.raindancer.modules.fishing.model.FisherProfile;
import de.raindancer.modules.fishing.model.Rod;
import de.raindancer.modules.fishing.model.RodPart;
import de.raindancer.modules.fishing.rules.UpgradeRule;
import de.raindancer.modules.fishing.store.RodTags;
import de.raindancer.modules.fishing.util.PermissionNodes;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Optional;

/** Upgrading the rod in hand, one rank of one part per click, paid for there and then. */
public final class RodMenu extends Menu implements IFishingScreen {

    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private static final Material[] ICONS = {Material.TRIPWIRE_HOOK, Material.CHEST, Material.STRING,
            Material.BLAZE_ROD};

    private final FishingServices services;

    public RodMenu(FishingServices services, Player viewer, Menu parent) {
        super(viewer, services.brand(), parent);
        this.services = services;
    }

    @Override
    protected Component title() {
        return MINI.deserialize("<dark_gray>Rod upgrades");
    }

    @Override
    public String breadcrumb() {
        return "Rod upgrades";
    }

    @Override
    protected void render() {
        boolean holding = RodTags.isRod(viewer.getInventory().getItemInMainHand());
        Rod rod = services.rods().held(viewer);
        FisherProfile profile = services.profiles().of(viewer.getUniqueId());
        UpgradeRule rule = services.rods().rule();
        UpgradeRule.Costs costs = services.rods().costs();
        boolean mayUpgrade = viewer.hasPermission(PermissionNodes.UPGRADE);

        List<String> about = new java.util.ArrayList<>();
        if (!holding) {
            about.add("<gray>Hold the rod you want to upgrade.");
        } else {
            about.add(rod.tier() == null ? "<gray>A plain fishing rod"
                    : "<" + rod.tier().tier().hex() + ">" + rod.tier().title());
            about.add("<gray>Upgrades are written onto this rod.");
            enchantLine(about, rod, Rod.DEEP_BITE, "Deep Bite", "fish bite " + 7 * rod.enchant(Rod.DEEP_BITE)
                    + "% sooner");
            enchantLine(about, rod, Rod.TWIN_CATCH, "Twin Catch", "+" + 5 * rod.enchant(Rod.TWIN_CATCH)
                    + "% double catches");
            enchantLine(about, rod, Rod.TREASURE_HUNTER, "Treasure Hunter", "+" + rod.enchant(Rod.TREASURE_HUNTER)
                    + "% sea chests");
            enchantLine(about, rod, Rod.MYTHIC_LURE, "Mythic Lure", "+" + 8 * rod.enchant(Rod.MYTHIC_LURE)
                    + "% rarer catches");
        }
        set(MenuLayout.HEADER_SUBJECT, Icons.of(Material.FISHING_ROD,
                holding ? "<white>The rod in your hand" : "<red>You are not holding a rod", about));
        set(MenuLayout.HEADER_RIGHT, Icons.of(Material.EXPERIENCE_BOTTLE,
                "<green>Fishing experience to spend: <white>" + profile.available(),
                "<gray>Upgrades are earned by fishing, never bought.",
                "<gray>Spending experience never lowers your level."));

        RodPart[] parts = RodPart.values();
        for (int n = 0; n < parts.length; n++) {
            RodPart part = parts[n];
            int rank = rod.rank(part);
            Optional<UpgradeRule.Refusal> refusal = rule.refusal(rod, part, profile, costs);
            boolean maxed = refusal.isPresent() && refusal.get() == UpgradeRule.Refusal.MAXED;
            long cost = rule.cost(rod, part, costs);
            int level = rule.levelNeeded(rod, part, costs);
            String reason;
            if (!mayUpgrade) {
                reason = "Rod upgrades are switched off for you here.";
            } else if (!holding) {
                reason = "Hold a fishing rod first.";
            } else if (maxed) {
                reason = "At its highest rank already.";
            } else if (refusal.isPresent() && refusal.get() == UpgradeRule.Refusal.LEVEL_TOO_LOW) {
                reason = "Needs fishing level " + level + ".";
            } else if (refusal.isPresent()) {
                reason = "Needs " + cost + " fishing experience; you have " + profile.available() + ".";
            } else {
                reason = "";
            }
            boolean allowed = mayUpgrade && holding && refusal.isEmpty();
            band(MenuLayout.RULES, 1 + n * 2, allowed, Icons.of(ICONS[n],
                    "<green>" + part.title() + " <gray>— now <white>" + part.rankName(rank),
                    "<gray>" + part.effect(),
                    "",
                    maxed ? "<gray>Fully upgraded." : "<gray>Next: <white>" + part.rankName(rank + 1)
                            + "<gray> for <white>" + cost + "</white> fishing experience",
                    maxed ? "" : "<gray>Needs fishing level <white>" + level,
                    "<yellow>Click to buy the next rank."), reason, click -> {
                        services.rods().upgrade(viewer, part, this::refresh);
                    });
        }
    }

    private static void enchantLine(List<String> lines, Rod rod, String key, String name, String effect) {
        int level = rod.enchant(key);
        if (level > 0) {
            lines.add("<aqua>" + name + " " + RodPart.numeral(level) + "<gray>: " + effect);
        }
    }

    @Override
    protected List<String> helpLines() {
        return List.of(
                "Upgrades are earned, not bought: they cost fishing experience",
                "and need a fishing level. Spending never lowers your level.",
                "",
                "Upgrades go onto the rod in your main hand,",
                "and stay with that rod wherever it goes.",
                "",
                "The hook makes fish bite sooner, the chest lure brings",
                "more sea chests, the reel brings double catches.",
                "The Ember Line fishes lava; the Void Line fishes the void,",
                "cast off the edge of an End island.");
    }

    @Override
    public String describe() {
        return "the upgrades fitted to the rod in hand, and buying the next rank of each";
    }
}
