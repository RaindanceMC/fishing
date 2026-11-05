package de.raindancer.modules.fishing.service;

import de.raindancer.core.ui.tree.SkillTree;
import de.raindancer.core.ui.tree.TreeNode;
import de.raindancer.modules.fishing.model.FisherProfile;
import de.raindancer.modules.fishing.model.FishingTree;
import de.raindancer.modules.fishing.rules.LevelRule;
import de.raindancer.modules.fishing.rules.TreeRule;
import org.bukkit.Material;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class FishingSkillViewTest {

    private final LevelRule levels = new LevelRule(100);
    private final SkillTree tree = FishingTree.shipped();
    private final UUID ada = UUID.randomUUID();

    @Test
    @DisplayName("fishing shows in the skills menu with its own level, points and mastered branches")
    void showsTheFisher() {
        TreeNode badge = tree.masteries("tidecaller").getFirst();
        FisherProfile fisher = FisherProfile.NEW.withExperience(levels.experienceFor(12) + 1)
                .withNode("quick_bite", 2).withNode(badge.id(), 1);
        Map<UUID, FisherProfile> profiles = Map.of(ada, fisher);
        FishingSkillView view = new FishingSkillView(id -> profiles.getOrDefault(id, FisherProfile.NEW),
                new TreeRule(levels, tree), (viewer, parent) -> { });
        assertThat(view.id()).isEqualTo("fishing");
        assertThat(view.title()).isEqualTo("Fishing");
        assertThat(view.icon()).isEqualTo(Material.FISHING_ROD);
        assertThat(view.level(ada)).isEqualTo(12);
        assertThat(view.progress(ada)).isBetween(0.0, 1.0);
        assertThat(view.pointsLeft(ada)).isEqualTo(12 - 2);
        assertThat(view.branches()).isEqualTo(5);
        assertThat(view.mastered(ada)).isEqualTo(1);
        assertThat(view.level(UUID.randomUUID())).as("somebody who never fished").isZero();
    }
}
