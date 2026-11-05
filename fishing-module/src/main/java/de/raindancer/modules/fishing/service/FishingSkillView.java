package de.raindancer.modules.fishing.service;

import de.raindancer.core.ui.menu.Menu;
import de.raindancer.core.ui.tree.SkillView;
import de.raindancer.modules.fishing.model.FisherProfile;
import de.raindancer.modules.fishing.rules.TreeRule;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Fishing as one skill in Core's skills registry, so the skills plugin's /skills lists it beside its own — with
 * fishing's own level, points and tree.
 */
public final class FishingSkillView implements SkillView {

    /** Among the skills of the land, after taming. */
    static final int ORDER = 29;

    private final Function<UUID, FisherProfile> profiles;
    private final TreeRule rule;
    private final BiConsumer<Player, Menu> open;

    public FishingSkillView(Function<UUID, FisherProfile> profiles, TreeRule rule, BiConsumer<Player, Menu> open) {
        this.profiles = profiles;
        this.rule = rule;
        this.open = open;
    }

    @Override
    public String id() {
        return "fishing";
    }

    @Override
    public String title() {
        return "Fishing";
    }

    @Override
    public Material icon() {
        return Material.FISHING_ROD;
    }

    @Override
    public int order() {
        return ORDER;
    }

    @Override
    public int level(UUID player) {
        return rule.levels().level(profiles.apply(player).experience());
    }

    @Override
    public double progress(UUID player) {
        return Math.max(0, Math.min(1, rule.levels().progress(profiles.apply(player).experience())));
    }

    @Override
    public int pointsLeft(UUID player) {
        return rule.left(profiles.apply(player));
    }

    @Override
    public int branches() {
        return rule.masteryBranches();
    }

    @Override
    public int mastered(UUID player) {
        return rule.mastered(profiles.apply(player));
    }

    @Override
    public void open(Player viewer, Menu parent) {
        open.accept(viewer, parent);
    }
}
