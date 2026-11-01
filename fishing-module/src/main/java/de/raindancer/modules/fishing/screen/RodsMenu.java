package de.raindancer.modules.fishing.screen;

import de.raindancer.core.ui.menu.Icons;
import de.raindancer.core.ui.menu.Menu;
import de.raindancer.core.ui.menu.MenuLayout;
import de.raindancer.modules.fishing.FishingServices;
import de.raindancer.modules.fishing.model.FisherProfile;
import de.raindancer.modules.fishing.model.RodTier;
import de.raindancer.modules.fishing.rules.RodTierRule;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** The rod workshop: every rod there is, what it does, and unlocking the next one with fishing experience. */
public final class RodsMenu extends Menu implements IFishingScreen {

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private final FishingServices services;

    public RodsMenu(FishingServices services, Player viewer, Menu parent) {
        super(viewer, services.brand(), parent);
        this.services = services;
    }

    @Override
    protected Component title() {
        return MINI.deserialize("<dark_gray>Rods");
    }

    @Override
    public String breadcrumb() {
        return "Rods";
    }

    @Override
    protected void render() {
        FisherProfile profile = services.profiles().of(viewer.getUniqueId());
        RodTierRule rule = services.workshop().rule();
        set(MenuLayout.HEADER_SUBJECT, Icons.of(Material.EXPERIENCE_BOTTLE,
                "<green>Fishing experience to spend: <white>" + profile.available(),
                "<gray>Fishing level <white>" + services.profiles().levels().level(profile.experience()),
                "<gray>Unlock a rod, then craft it — its recipe",
                "<gray>appears in your recipe book."));
        RodTier[] rods = RodTier.values();
        for (int n = 0; n < rods.length; n++) {
            RodTier rod = rods[n];
            RodTier previous = rods[Math.max(0, n - 1)];
            Optional<RodTierRule.Refusal> refusal = rule.refusal(profile, rod);
            boolean unlocked = profile.hasUnlocked(rod);
            List<String> lore = new ArrayList<>();
            lore.add("<" + rod.tier().hex() + ">" + rod.tier().title() + " rod");
            lore.add("<gray>Fish bite <white>" + rod.waitPercent() + "%</white> sooner, sea chests <white>+"
                    + (rod.chestPermille() / 10.0) + "%");
            if (rod.rarePercent() > 0 || rod.doublePermille() > 0) {
                lore.add("<gray>Rarer catches <white>+" + rod.rarePercent() + "%</white>, double catches <white>+"
                        + (rod.doublePermille() / 10.0) + "%");
            }
            rod.special().ifPresent(water -> lore.add("<gold>Fishes " + water.title().toLowerCase(Locale.ROOT)
                    .replace("the void", "lava and the void")));
            lore.add("");
            lore.add("<gray>Craft: <white>" + pretty(rod.shaft()) + "</white> ×3, <white>" + pretty(rod.line())
                    + "</white> ×2, any <white>" + rod.fishIngredient().title().toLowerCase(Locale.ROOT) + "</white> fish");
            if (unlocked) {
                lore.add("<green>Unlocked — craft it whenever you like.");
            } else {
                lore.add("<gray>Unlock: fishing level <white>" + rod.level() + "</white>, <white>" + rod.cost()
                        + "</white> fishing experience");
                lore.add("<yellow>Click to unlock it.");
            }
            ItemStack icon = Icons.showing(services.forge().rod(rod),
                    "<" + rod.tier().hex() + ">" + rod.title() + (unlocked ? " <green>✔" : ""), lore);
            String reason = refusal.map(why -> switch (why) {
                case ALREADY -> "Unlocked already — craft it.";
                case PREVIOUS_FIRST -> "Unlock the " + previous.title() + " first.";
                case LEVEL_TOO_LOW -> "Needs fishing level " + rod.level() + ".";
                case NOT_ENOUGH_EXPERIENCE -> "Needs " + rod.cost() + " fishing experience; you have "
                        + profile.available() + ".";
            }).orElse("");
            if (unlocked) {
                band(MenuLayout.RULES, 1 + n, icon);
            } else {
                band(MenuLayout.RULES, 1 + n, refusal.isEmpty(), icon, reason, click -> new ConfirmScreen(services,
                        viewer, this, "Unlock the " + rod.title() + "?",
                        List.of("It costs " + rod.cost() + " fishing experience.",
                                "Your fishing level stays as it is."),
                        () -> services.workshop().unlock(viewer, rod, this::reopen)).open());
            }
        }
    }

    private static String pretty(String material) {
        String[] words = material.split("_");
        StringBuilder out = new StringBuilder();
        for (String word : words) {
            if (!out.isEmpty()) {
                out.append(' ');
            }
            out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return out.toString();
    }

    @Override
    protected List<String> helpLines() {
        return List.of(
                "Six rods, one per tier, each better than the last.",
                "",
                "Unlock one with fishing experience once you reach its level,",
                "then craft it: its recipe appears in your recipe book.",
                "Only a fisher who unlocked a rod can cast it or craft it.",
                "",
                "Part upgrades and fishing enchantments go on top of any rod.");
    }

    @Override
    public String describe() {
        return "every rod tier, what each does, and unlocking the next";
    }
}
