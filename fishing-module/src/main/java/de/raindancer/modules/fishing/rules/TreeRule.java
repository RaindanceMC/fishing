package de.raindancer.modules.fishing.rules;

import de.raindancer.core.ui.tree.NodeState;
import de.raindancer.core.ui.tree.SkillTree;
import de.raindancer.core.ui.tree.TreeNode;
import de.raindancer.modules.fishing.model.FisherProfile;
import de.raindancer.modules.fishing.model.Perks;
import de.raindancer.modules.fishing.model.Tier;

import java.util.Optional;

/**
 * Skill points and the skill tree: a point per fishing level, one more for each sea chest tier first opened and for
 * every ten kinds of fish caught; what a node costs, and whether a fisher may take another rank of it.
 */
public final class TreeRule implements IFishingRule {

    /** Kinds of fish caught per bonus point. */
    static final int KINDS_PER_POINT = 10;

    private final LevelRule levels;
    private final SkillTree tree;

    public TreeRule(LevelRule levels, SkillTree tree) {
        this.levels = levels;
        this.tree = tree;
    }

    public SkillTree tree() {
        return tree;
    }

    public LevelRule levels() {
        return levels;
    }

    public int bonus(FisherProfile profile) {
        int tiers = 0;
        for (Tier tier : Tier.values()) {
            tiers += profile.opened(tier) > 0 ? 1 : 0;
        }
        return tiers + profile.caught().size() / KINDS_PER_POINT;
    }

    public int points(FisherProfile profile) {
        return levels.level(profile.experience()) + bonus(profile);
    }

    public int spent(FisherProfile profile) {
        return tree.spent(profile.nodes());
    }

    public int left(FisherProfile profile) {
        return Math.max(0, points(profile) - spent(profile));
    }

    public Perks perks(FisherProfile profile) {
        return Perks.from(tree, profile.nodes());
    }

    public NodeState state(FisherProfile profile, TreeNode node) {
        return tree.state(profile.nodes(), node);
    }

    public Optional<SkillTree.Refusal> refusal(FisherProfile profile, TreeNode node) {
        return tree.refusal(profile.nodes(), node, levels.level(profile.experience()), left(profile));
    }

    @Override
    public String describe() {
        return "skill points, and whether a node of the fishing skill tree may be taken";
    }
}
