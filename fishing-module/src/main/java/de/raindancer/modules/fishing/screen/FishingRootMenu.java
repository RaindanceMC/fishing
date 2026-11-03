package de.raindancer.modules.fishing.screen;

import de.raindancer.core.ui.menu.Icons;
import de.raindancer.core.ui.menu.Menu;
import de.raindancer.core.ui.menu.MenuLayout;
import de.raindancer.modules.fishing.FishingServices;
import de.raindancer.modules.fishing.model.FisherProfile;
import de.raindancer.modules.fishing.model.Rod;
import de.raindancer.modules.fishing.model.RodPart;
import de.raindancer.modules.fishing.model.Tier;
import de.raindancer.modules.fishing.rules.LevelRule;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/** {@code /fishing}: your level, your rod, what you have caught, and the doors to skills, rod and collection. */
public final class FishingRootMenu extends Menu implements IFishingScreen {

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private final FishingServices services;

    public FishingRootMenu(FishingServices services, Player viewer) {
        super(viewer, services.brand(), null);
        this.services = services;
    }

    @Override
    protected Component title() {
        return MINI.deserialize("<dark_gray>Fishing");
    }

    @Override
    public String breadcrumb() {
        return "Overview";
    }

    @Override
    protected void render() {
        FisherProfile profile = services.profiles().of(viewer.getUniqueId());
        LevelRule levels = services.profiles().levels();
        int level = levels.level(profile.experience());
        int percent = (int) Math.round(levels.progress(profile.experience()) * 100);

        set(MenuLayout.HEADER_LEFT, Icons.of(Material.EXPERIENCE_BOTTLE,
                "<green>Fishing level <white>" + level,
                "<gray>" + percent + "% of the way to the next level",
                "<gray>Skill points to spend: <white>" + services.profiles().tree().left(profile),
                "<gray>Fishing experience to spend: <white>" + profile.available()));

        Rod rod = services.rods().held(viewer);
        List<String> rodLines = new ArrayList<>();
        for (RodPart part : RodPart.values()) {
            rodLines.add("<gray>" + part.rankName(rod.rank(part)));
        }
        set(MenuLayout.HEADER_SUBJECT, Icons.of(Material.FISHING_ROD, "<white>The rod in your hand", rodLines));

        List<String> chests = new ArrayList<>();
        chests.add("<gray>Fish landed: <white>" + profile.totalCaught());
        chests.add("<gray>Kinds found: <white>" + profile.caught().size() + "<gray> of <white>"
                + services.catalog().fish().size());
        for (Tier tier : Tier.values()) {
            int opened = profile.opened(tier);
            if (opened > 0) {
                chests.add("<" + tier.hex() + ">" + tier.chestName() + "s<gray>: <white>" + opened);
            }
        }
        set(MenuLayout.HEADER_RIGHT, Icons.of(Material.CHEST, "<white>Your record", chests));

        band(MenuLayout.RULES, 1, Icons.of(Material.FISHING_ROD, "<green>Rods",
                "<gray>Six rods, one per tier. Unlock them with",
                "<gray>fishing experience, then craft them."), click -> services.screens().rods(viewer));
        band(MenuLayout.RULES, 3, Icons.of(Material.ENCHANTED_BOOK, "<green>Skill tree",
                "<gray>Five branches, sixty nodes: quicker bites,",
                "<gray>better chests, rarer and heavier fish,",
                "<gray>lava and the void, bait and the kitchen.",
                "<gray>Skill points to spend: <white>" + services.profiles().tree().left(profile)),
                click -> services.screens().skills(viewer));
        band(MenuLayout.RULES, 5, Icons.of(Material.STRING, "<green>Rod upgrades",
                "<gray>Hook, chest lure, reel and line —",
                "<gray>the Ember Line fishes lava, the Void Line the void."), click -> services.screens().rod(viewer));
        band(MenuLayout.RULES, 7, Icons.of(Material.COD, "<green>Collection",
                "<gray>Every fish there is, and which you have caught."),
                click -> services.screens().collection(viewer));
    }

    @Override
    protected List<String> helpLines() {
        return List.of(
                "Fishing, with more in the water.",
                "",
                "Fish come in six tiers, from common to mythic,",
                "and every kind of water has its own.",
                "Now and then a sea chest bites instead: right click it to open it.",
                "",
                "Fishing spots — ripples and a big bobber on the water —",
                "bite sooner and give better catches until they are fished out.",
                "",
                "Catching earns fishing experience; every level is a skill point for the skill tree.");
    }

    @Override
    public String describe() {
        return "a fisher's level, rod and record, and the way to skills, upgrades and the collection";
    }
}
