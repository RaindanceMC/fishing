package de.raindancer.modules.fishing.rules;

import de.raindancer.core.ui.tree.NodeState;
import de.raindancer.modules.fishing.model.FisherProfile;
import de.raindancer.core.ui.tree.SkillTree;
import de.raindancer.modules.fishing.model.FishingTree;
import de.raindancer.modules.fishing.model.Tier;
import de.raindancer.core.ui.tree.TreeNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class TreeRuleTest {

    private final LevelRule levels = new LevelRule(100);
    private final SkillTree tree = FishingTree.shipped();
    private final TreeRule rule = new TreeRule(levels, tree);

    private FisherProfile atLevel(int level) {
        return FisherProfile.NEW.withExperience(levels.experienceFor(level));
    }

    private TreeNode node(String id) {
        return tree.node(id).orElseThrow();
    }

    @Test
    @DisplayName("a point per level, and one more for each sea chest tier first opened and each ten kinds of fish caught")
    void points() {
        assertThat(rule.points(atLevel(7))).isEqualTo(7);
        FisherProfile seasoned = atLevel(7).withChestOpened(Tier.COMMON).withChestOpened(Tier.COMMON)
                .withChestOpened(Tier.EPIC);
        for (int n = 0; n < 21; n++) {
            seasoned = seasoned.withCatch("fish_" + n, 1);
        }
        assertThat(rule.bonus(seasoned)).isEqualTo(2 + 2);
        assertThat(rule.points(seasoned)).isEqualTo(11);
    }

    @Test
    @DisplayName("points spent are ranks times cost; what is left is the rest")
    void spent() {
        FisherProfile profile = atLevel(10).withNode("quick_bite", 3).withNode("patience", 1);
        assertThat(rule.spent(profile)).isEqualTo(3 + 2);
        assertThat(rule.left(profile)).isEqualTo(5);
    }

    @Test
    @DisplayName("a root is open, a node whose needs are not taken is locked, a taken one owned, then maxed")
    void states() {
        FisherProfile profile = atLevel(10);
        assertThat(rule.state(profile, node("quick_bite"))).isEqualTo(NodeState.OPEN);
        assertThat(rule.state(profile, node("swift_reel"))).isEqualTo(NodeState.LOCKED);
        profile = profile.withNode("quick_bite", 2);
        assertThat(rule.state(profile, node("quick_bite"))).isEqualTo(NodeState.OWNED);
        assertThat(rule.state(profile, node("swift_reel"))).isEqualTo(NodeState.OPEN);
        profile = profile.withNode("quick_bite", 5);
        assertThat(rule.state(profile, node("quick_bite"))).isEqualTo(NodeState.MAXED);
    }

    @Test
    @DisplayName("taking one side of a choice bars the other")
    void choiceBars() {
        FisherProfile profile = atLevel(20).withNode("quick_bite", 1).withNode("swift_reel", 1)
                .withNode("spot_reader", 1).withNode("patience", 1);
        assertThat(rule.state(profile, node("frenzy"))).isEqualTo(NodeState.BARRED);
        assertThat(rule.refusal(profile, node("frenzy"))).contains(SkillTree.Refusal.BARRED);
    }

    @Test
    @DisplayName("refusals: no points, needs, level, maxed — and none when it can be taken")
    void refusals() {
        assertThat(rule.refusal(FisherProfile.NEW, node("quick_bite"))).contains(SkillTree.Refusal.NO_POINTS);
        assertThat(rule.refusal(atLevel(5), node("swift_reel"))).contains(SkillTree.Refusal.NEEDS);
        assertThat(rule.refusal(atLevel(5), node("quick_bite"))).isEqualTo(Optional.empty());
        FisherProfile almost = atLevel(59).withNode("quick_bite", 1).withNode("swift_reel", 1)
                .withNode("shoal_sense", 1).withNode("tide_lore", 1);
        assertThat(rule.refusal(almost, node("stormcaller"))).contains(SkillTree.Refusal.LEVEL);
        assertThat(rule.refusal(atLevel(10).withNode("quick_bite", 5), node("quick_bite")))
                .contains(SkillTree.Refusal.MAXED);
        FisherProfile poor = atLevel(2).withNode("quick_bite", 1).withNode("swift_reel", 1);
        assertThat(rule.refusal(poor, node("patience"))).contains(SkillTree.Refusal.NO_POINTS);
    }

    @Test
    @DisplayName("a fisher's perks are their nodes' perks; nodes they could no longer take still count")
    void perks() {
        FisherProfile profile = atLevel(10).withNode("treasure_sense", 4);
        assertThat(rule.perks(profile).get(de.raindancer.modules.fishing.model.Perk.CHEST)).isEqualTo(40);
    }

    @Test
    @DisplayName("five branches end in a mastery badge; a branch counts as mastered once its badge is taken")
    void mastery() {
        assertThat(rule.masteryBranches()).isEqualTo(5);
        FisherProfile fisher = atLevel(100);
        assertThat(rule.mastered(fisher)).isZero();
        TreeNode badge = tree.masteries("monger").getFirst();
        assertThat(rule.refusal(fisher, badge)).as("the branch is not finished").contains(SkillTree.Refusal.UNFINISHED);
        for (TreeNode node : tree.nodesOf("monger")) {
            if (!node.mastery() && !node.excludes().stream().anyMatch(fisher.nodes()::containsKey)) {
                fisher = fisher.withNode(node.id(), node.ranks());
            }
        }
        assertThat(rule.mastered(fisher)).isZero();
        fisher = fisher.withNode(badge.id(), 1);
        assertThat(rule.mastered(fisher)).isEqualTo(1);
    }
}
