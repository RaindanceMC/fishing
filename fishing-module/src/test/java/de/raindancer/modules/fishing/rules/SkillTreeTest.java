package de.raindancer.modules.fishing.rules;

import de.raindancer.core.ui.tree.TreeGrid;
import de.raindancer.modules.fishing.model.Perk;
import de.raindancer.modules.fishing.model.SkillTree;
import de.raindancer.modules.fishing.model.TreeNode;
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

    private final SkillTree tree = SkillTree.shipped();

    @Test
    @DisplayName("five branches of twelve nodes, each on its own place in its branch's five by three grid")
    void shape() {
        assertThat(tree.branches()).hasSize(5);
        for (var branch : tree.branches()) {
            List<TreeNode> nodes = tree.nodesOf(branch.id());
            assertThat(nodes).as(branch.id()).hasSize(12);
            Set<Integer> places = new HashSet<>();
            for (TreeNode node : nodes) {
                assertThat(places.add(TreeGrid.slot(node.column(), node.row()))).as(node.id()).isTrue();
            }
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
            assertThat(reached).as(branch.id()).hasSize(12);
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
            assertThat(node.perks()).as(node.id()).isNotEmpty();
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
        var perks = tree.perks(Map.of("quick_bite", 3, "patience", 1, "rare_instinct", 2));
        assertThat(perks.get(Perk.WAIT)).isEqualTo(18 - 20);
        assertThat(perks.get(Perk.RARE)).isEqualTo(25 + 20);
        assertThat(perks.get(Perk.CHEST)).isZero();
        assertThat(tree.perks(Map.of("gone", 3, "quick_bite", 9)).get(Perk.WAIT)).isEqualTo(30);
    }

    @Test
    @DisplayName("a broken tree is refused when read, with what is wrong")
    void brokenTreeRefused() {
        assertThatThrownBy(() -> SkillTree.read("""
                branches: {a: {title: A, icon: stone}}
                nodes:
                  x: {branch: a, at: [0, 0], title: X, icon: stone, perks: {wait: 1}}
                  y: {branch: a, at: [2, 0], title: Y, icon: stone, requires: [x], perks: {wait: 1}}
                """)).hasMessageContaining("y");
        assertThatThrownBy(() -> SkillTree.read("""
                branches: {a: {title: A, icon: stone}}
                nodes:
                  x: {branch: a, at: [0, 0], title: X, icon: stone, perks: {nonsense: 1}}
                """)).hasMessageContaining("nonsense");
    }
}
