package de.raindancer.modules.fishing.model;

import de.raindancer.core.ui.tree.TreeGrid;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** The fishing skill tree, read from {@code skilltree.yml} and checked as it is read. */
public final class SkillTree {

    private static volatile SkillTree shipped;

    private final List<Branch> branches;
    private final Map<String, TreeNode> nodes;

    private SkillTree(List<Branch> branches, Map<String, TreeNode> nodes) {
        this.branches = List.copyOf(branches);
        this.nodes = nodes;
    }

    /** The tree in the jar. */
    public static SkillTree shipped() {
        SkillTree known = shipped;
        if (known == null) {
            try (InputStream in = SkillTree.class.getResourceAsStream("/de/raindancer/modules/fishing/skilltree.yml")) {
                if (in == null) {
                    throw new IllegalStateException("skilltree.yml is missing from the jar.");
                }
                known = read(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            } catch (IOException unreadable) {
                throw new IllegalStateException("skilltree.yml could not be read.", unreadable);
            }
            shipped = known;
        }
        return known;
    }

    public static SkillTree read(String text) {
        // A read-only resource in the jar, not a store: YamlStore is for what the server writes. Broken YAML reads as
        // empty and is refused below for having no branches.
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(new java.io.StringReader(text));
        List<Branch> branches = new ArrayList<>();
        ConfigurationSection branchSection = section(yaml, "branches");
        for (String id : branchSection.getKeys(false)) {
            ConfigurationSection b = section(branchSection, id);
            branches.add(new Branch(id, b.getString("title", id), b.getString("icon", "stone"),
                    b.getString("blurb", "")));
        }
        Set<String> branchIds = new HashSet<>();
        branches.forEach(branch -> branchIds.add(branch.id()));
        Map<String, TreeNode> nodes = new LinkedHashMap<>();
        ConfigurationSection nodeSection = section(yaml, "nodes");
        for (String id : nodeSection.getKeys(false)) {
            ConfigurationSection n = section(nodeSection, id);
            List<Integer> at = n.getIntegerList("at");
            if (at.size() != 2) {
                throw new IllegalArgumentException("Node " + id + " needs at: [column, row].");
            }
            String branch = n.getString("branch", "");
            if (!branchIds.contains(branch)) {
                throw new IllegalArgumentException("Node " + id + " is in no known branch (" + branch + ").");
            }
            Map<Perk, Double> perks = new EnumMap<>(Perk.class);
            ConfigurationSection perkSection = n.getConfigurationSection("perks");
            if (perkSection != null) {
                for (String key : perkSection.getKeys(false)) {
                    Perk perk = Perk.parse(key).orElseThrow(() -> new IllegalArgumentException(
                            "Node " + id + " has an unknown perk: " + key + "."));
                    perks.put(perk, perkSection.getDouble(key));
                }
            }
            TreeNode node = new TreeNode(id, branch, at.get(0), at.get(1), n.getString("title", id),
                    n.getString("icon", "stone"), Math.max(1, n.getInt("ranks", 1)), Math.max(1, n.getInt("cost", 1)),
                    n.getBoolean("choice"), n.getBoolean("capstone"), Math.max(0, n.getInt("level", 0)),
                    n.getStringList("requires"), n.getStringList("excludes"), perks);
            try {
                TreeGrid.slot(node.column(), node.row());
            } catch (IllegalArgumentException outside) {
                throw new IllegalArgumentException("Node " + id + ": " + outside.getMessage(), outside);
            }
            nodes.put(id, node);
        }
        Map<String, Set<Integer>> taken = new LinkedHashMap<>();
        for (TreeNode node : nodes.values()) {
            if (!taken.computeIfAbsent(node.branch(), b -> new HashSet<>())
                    .add(TreeGrid.slot(node.column(), node.row()))) {
                throw new IllegalArgumentException("Node " + node.id() + " sits where another node of "
                        + node.branch() + " sits.");
            }
            for (String needed : node.requires()) {
                TreeNode other = nodes.get(needed);
                if (other == null || !other.branch().equals(node.branch())) {
                    throw new IllegalArgumentException("Node " + node.id() + " needs " + needed
                            + ", which is not in its branch.");
                }
                try {
                    TreeGrid.link(other.column(), other.row(), node.column(), node.row());
                } catch (IllegalArgumentException far) {
                    throw new IllegalArgumentException("Node " + node.id() + " needs " + needed
                            + ", which is not its neighbour.", far);
                }
            }
            for (String shut : node.excludes()) {
                if (!nodes.containsKey(shut)) {
                    throw new IllegalArgumentException("Node " + node.id() + " shuts " + shut + ", which is no node.");
                }
            }
        }
        return new SkillTree(branches, java.util.Collections.unmodifiableMap(nodes));
    }

    private static ConfigurationSection section(ConfigurationSection parent, String path) {
        ConfigurationSection found = parent.getConfigurationSection(path);
        if (found == null) {
            throw new IllegalArgumentException("The skill tree has no " + path + ".");
        }
        return found;
    }

    public List<Branch> branches() {
        return branches;
    }

    public List<TreeNode> nodes() {
        return List.copyOf(nodes.values());
    }

    public List<TreeNode> nodesOf(String branch) {
        return nodes.values().stream().filter(node -> node.branch().equals(branch)).toList();
    }

    public Optional<TreeNode> node(String id) {
        return Optional.ofNullable(nodes.get(id));
    }

    /** What these ranks add up to; unknown nodes count for nothing, ranks past a node's last for its last. */
    public Perks perks(Map<String, Integer> ranks) {
        Map<Perk, Double> sum = new EnumMap<>(Perk.class);
        ranks.forEach((id, rank) -> {
            TreeNode node = nodes.get(id);
            if (node == null || rank == null || rank <= 0) {
                return;
            }
            int counted = Math.min(rank, node.ranks());
            node.perks().forEach((perk, value) -> sum.merge(perk, value * counted, Double::sum));
        });
        return new Perks(sum);
    }
}
