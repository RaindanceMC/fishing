package de.raindancer.modules.fishing.screen;

import de.raindancer.core.social.economy.Fees;
import de.raindancer.core.ui.effect.Cues;
import de.raindancer.core.ui.menu.Icons;
import de.raindancer.core.ui.menu.Menu;
import de.raindancer.core.ui.menu.MenuLayout;
import de.raindancer.modules.fishing.FishingServices;
import de.raindancer.modules.fishing.model.FisherProfile;
import de.raindancer.modules.fishing.model.Skill;
import de.raindancer.modules.fishing.rules.SkillRule;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Optional;

/** Spending skill points: one rank of a skill per click, and unlearning them all for a fee. */
public final class SkillsMenu extends Menu implements IFishingScreen {

    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private static final Material[] ICONS = {Material.CLOCK, Material.CHEST, Material.AMETHYST_SHARD,
            Material.TROPICAL_FISH};

    private final FishingServices services;

    public SkillsMenu(FishingServices services, Player viewer, Menu parent) {
        super(viewer, services.brand(), parent);
        this.services = services;
    }

    @Override
    protected Component title() {
        return MINI.deserialize("<dark_gray>Fishing skills");
    }

    @Override
    public String breadcrumb() {
        return "Skills";
    }

    @Override
    protected void render() {
        FisherProfile profile = services.profiles().of(viewer.getUniqueId());
        SkillRule rule = services.profiles().skills();
        set(MenuLayout.HEADER_SUBJECT, Icons.of(Material.EXPERIENCE_BOTTLE,
                "<green>Skill points to spend: <white>" + rule.pointsLeft(profile),
                "<gray>One per fishing level. Spent: <white>" + profile.pointsSpent()));

        Skill[] skills = Skill.values();
        for (int n = 0; n < skills.length; n++) {
            Skill skill = skills[n];
            int rank = profile.rank(skill);
            Optional<SkillRule.Refusal> refusal = rule.refusal(profile, skill);
            String reason = refusal.map(why -> why == SkillRule.Refusal.MAXED
                    ? "At its highest rank already." : "No skill point left — level up first.").orElse("");
            band(MenuLayout.RULES, 1 + n * 2, refusal.isEmpty(), Icons.of(ICONS[n],
                    "<green>" + skill.title() + " <white>" + rank + "<gray>/" + Skill.MOST,
                    "<gray>" + skill.effect(),
                    "",
                    "<yellow>Click to learn the next rank."), reason, click -> learn(skill));
        }

        String fee = services.config().skillResetFee();
        toolbar(4, profile.pointsSpent() > 0, Icons.of(Material.WATER_BUCKET, "<red>Unlearn every skill",
                        "<gray>Every point comes back to spend again.",
                        "<gray>Costs <white>" + Fees.format(Fees.amount(fee)) + "</white>."),
                "Nothing learnt yet, so nothing to unlearn.", click -> new ConfirmScreen(services, viewer, this,
                        "Unlearn every fishing skill?",
                        List.of("Every point comes back to spend again.",
                                "It costs " + Fees.format(Fees.amount(fee)) + "."),
                        () -> services.profiles().unlearnAll(viewer, fee, this::reopen)).open());
    }

    private void learn(Skill skill) {
        services.profiles().learn(viewer, skill, refused -> {
            if (refused.isPresent()) {
                services.effects().play(viewer.getUniqueId(), Cues.NO);
                services.messages().send(viewer, refused.get() == SkillRule.Refusal.MAXED
                        ? "fishing.skills.maxed" : "fishing.skills.no-points", "skill", skill.title());
            } else {
                services.effects().play(viewer.getUniqueId(), Cues.OK);
                services.messages().send(viewer, "fishing.skills.learnt", "skill", skill.title(),
                        "rank", String.valueOf(services.profiles().of(viewer.getUniqueId()).rank(skill)));
            }
            refresh();
        });
    }

    @Override
    protected List<String> helpLines() {
        return List.of(
                "Every fishing level gives one skill point.",
                "Each skill has five ranks; click one to learn its next rank.",
                "",
                "Unlearning gives every point back for a fee, and asks first.");
    }

    @Override
    public String describe() {
        return "a fisher's skills and the points to spend on them";
    }
}
