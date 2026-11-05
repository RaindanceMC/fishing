package de.raindancer.modules.fishing.rules;

import de.raindancer.core.ui.tree.TreeGrid;
import de.raindancer.modules.fishing.model.Perk;
import de.raindancer.core.ui.tree.SkillTree;
import de.raindancer.modules.fishing.model.FishingTree;
import de.raindancer.core.ui.tree.TreeNode;
import de.raindancer.modules.fishing.model.Perks;
import org.bukkit.Material;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SkillTreeTest {

    private final SkillTree tree = FishingTree.shipped();

    @Test
    @DisplayName("five branches of fifteen nodes filling a five by three grid, and a mastery badge past the capstone")
    void shape() {
        assertThat(tree.branches()).hasSize(5);
        for (var branch : tree.branches()) {
            List<TreeNode> nodes = tree.nodesOf(branch.id());
            assertThat(nodes).as(branch.id()).hasSize(16);
            Set<Integer> places = new HashSet<>();
            for (TreeNode node : nodes) {
                assertThat(places.add(node.column() * 10 + node.row())).as(node.id()).isTrue();
                if (!node.mastery()) {
                    assertThat(node.column()).as(node.id()).isBetween(0, 4);
                    assertThat(node.row()).as(node.id()).isBetween(0, 2);
                }
            }
        }
    }

    @Test
    @DisplayName("every branch ends in one free mastery badge, right of its capstone and needing it")
    void masteries() {
        for (var branch : tree.branches()) {
            List<TreeNode> badges = tree.masteries(branch.id());
            assertThat(badges).as(branch.id()).hasSize(1);
            TreeNode badge = badges.getFirst();
            TreeNode capstone = tree.nodesOf(branch.id()).stream().filter(TreeNode::capstone).findFirst().orElseThrow();
            assertThat(badge.cost()).as("earned, not bought").isZero();
            assertThat(badge.ranks()).isEqualTo(1);
            assertThat(badge.column()).isEqualTo(capstone.column() + 1);
            assertThat(badge.row()).isEqualTo(capstone.row());
            assertThat(badge.requires()).containsExactly(capstone.id());
            assertThat(badge.effects()).as(badge.id()).hasSizeGreaterThanOrEqualTo(2);
        }
    }

    @Test
    @DisplayName("what a node needs is a neighbour in its own branch, so the menu can draw the line")
    void requirementsAreNeighbours() {
        for (TreeNode node : tree.nodes()) {
            for (String needed : node.requires()) {
                TreeNode other = tree.node(needed).orElseThrow(() -> new AssertionError(node.id() + " needs " + needed));
                assertThat(other.branch()).as(node.id()).isEqualTo(node.branch());
                TreeGrid.link(other.column(), other.row(), node.column(), node.row());
            }
        }
    }

    @Test
    @DisplayName("no two lines of a branch share a slot, so no line is drawn over another")
    void linesNeverCross() {
        for (var branch : tree.branches()) {
            Set<Integer> used = new HashSet<>();
            for (TreeNode node : tree.nodesOf(branch.id())) {
                for (String needed : node.requires()) {
                    TreeNode other = tree.node(needed).orElseThrow();
                    var link = TreeGrid.link(other.column(), other.row(), node.column(), node.row());
                    assertThat(used.add(link.slotRow() * 100 + link.slotColumn()))
                            .as(branch.id() + ": " + needed + " to " + node.id()).isTrue();
                }
            }
        }
    }

    @Test
    @DisplayName("auto-reel and auto-rethrow are Tidecaller nodes behind a fishing level, one rank each")
    void autoFishing() {
        TreeNode reel = tree.node("auto_reel").orElseThrow();
        TreeNode rethrow = tree.node("auto_rethrow").orElseThrow();
        for (TreeNode node : List.of(reel, rethrow)) {
            assertThat(node.branch()).isEqualTo("tidecaller");
            assertThat(node.ranks()).isEqualTo(1);
            assertThat(node.level()).isPositive();
        }
        assertThat(Perks.from(tree, Map.of("auto_reel", 1)).has(Perk.AUTO_REEL)).isTrue();
        assertThat(Perks.from(tree, Map.of("auto_rethrow", 1)).has(Perk.AUTO_RETHROW)).isTrue();
        assertThat(Perks.from(tree, Map.of("auto_reel", 1)).has(Perk.AUTO_RETHROW)).isFalse();
    }

    @Test
    @DisplayName("every perk there is is given by some node, so none is dead code")
    void everyPerkIsUsed() {
        Set<Perk> given = new HashSet<>();
        tree.nodes().forEach(node -> node.effects().keySet().forEach(id -> given.add(Perk.parse(id).orElseThrow())));
        assertThat(given).containsExactlyInAnyOrder(Perk.values());
    }

    @Test
    @DisplayName("a choice shuts its other side and the other side shuts it")
    void choicesAreMutual() {
        for (TreeNode node : tree.nodes()) {
            for (String shut : node.excludes()) {
                assertThat(tree.node(shut).orElseThrow().excludes()).as(node.id() + " / " + shut).contains(node.id());
            }
        }
        assertThat(tree.nodes().stream().filter(TreeNode::choice)).hasSize(10);
    }

    @Test
    @DisplayName("every branch starts at one node that needs nothing, and every other node can be reached from it")
    void reachable() {
        for (var branch : tree.branches()) {
            List<TreeNode> roots = tree.nodesOf(branch.id()).stream().filter(node -> node.requires().isEmpty()).toList();
            assertThat(roots).as(branch.id()).hasSize(1);
            Set<String> reached = new HashSet<>(Set.of(roots.getFirst().id()));
            boolean grew = true;
            while (grew) {
                grew = false;
                for (TreeNode node : tree.nodesOf(branch.id())) {
                    if (!reached.contains(node.id()) && node.requires().stream().anyMatch(reached::contains)) {
                        reached.add(node.id());
                        grew = true;
                    }
                }
            }
            assertThat(reached).as(branch.id()).hasSize(16);
        }
    }

    @Test
    @DisplayName("the four old skills are nodes of the same name with five ranks, so nobody loses a rank")
    void oldSkillsCarryOver() {
        for (String old : List.of("quick_bite", "treasure_sense", "rare_instinct", "double_catch")) {
            TreeNode node = tree.node(old).orElseThrow();
            assertThat(node.ranks()).isEqualTo(5);
            assertThat(node.cost()).isEqualTo(1);
            assertThat(node.requires()).isEmpty();
        }
    }

    @Test
    @DisplayName("every icon is a real item, and every node does something")
    void iconsAndPerks() {
        for (TreeNode node : tree.nodes()) {
            assertThat(Material.matchMaterial(node.icon())).as(node.id() + " " + node.icon()).isNotNull();
            assertThat(node.effects()).as(node.id()).isNotEmpty();
        }
        assertThat(tree.branches()).allSatisfy(branch ->
                assertThat(Material.matchMaterial(branch.icon())).as(branch.id()).isNotNull());
    }

    @Test
    @DisplayName("a capstone costs three points, needs level 60 and ends its branch")
    void capstones() {
        List<TreeNode> capstones = tree.nodes().stream().filter(TreeNode::capstone).toList();
        assertThat(capstones).hasSize(5).allSatisfy(node -> {
            assertThat(node.cost()).isEqualTo(3);
            assertThat(node.level()).isEqualTo(60);
            assertThat(node.column()).isEqualTo(4);
        });
    }

    @Test
    @DisplayName("perks add up rank by rank across the tree")
    void perksAddUp() {
        var perks = Perks.from(tree, Map.of("quick_bite", 3, "patience", 1, "rare_instinct", 2));
        assertThat(perks.get(Perk.WAIT)).isEqualTo(18 - 20);
        assertThat(perks.get(Perk.RARE)).isEqualTo(25 + 20);
        assertThat(perks.get(Perk.CHEST)).isZero();
        assertThat(Perks.from(tree, Map.of("gone", 3, "quick_bite", 9)).get(Perk.WAIT)).isEqualTo(30);
    }

    @Test
    @DisplayName("effects become perks by id; an id that is no perk counts for nothing")
    void effectsToPerks() {
        Perks perks = Perks.of(Map.of("wait", 5.0, "Chest", 20.0, "nonsense", 9.0));
        assertThat(perks.values()).containsOnlyKeys(Perk.WAIT, Perk.CHEST);
        assertThat(perks.get(Perk.CHEST)).isEqualTo(20);
    }

    @Test
    @DisplayName("a broken tree is refused when read, with what is wrong")
    void brokenTreeRefused() {
        assertThatThrownBy(() -> SkillTree.read("""
                branches: {a: {title: A, icon: stone}}
                nodes:
                  x: {branch: a, at: [0, 0], title: X, icon: stone, effects: {wait: 1}}
                  y: {branch: a, at: [2, 0], title: Y, icon: stone, requires: [x], effects: {wait: 1}}
                """, Perk::known)).hasMessageContaining("y");
        assertThatThrownBy(() -> SkillTree.read("""
                branches: {a: {title: A, icon: stone}}
                nodes:
                  x: {branch: a, at: [0, 0], title: X, icon: stone, effects: {nonsense: 1}}
                """, Perk::known)).hasMessageContaining("nonsense");
    }
}
