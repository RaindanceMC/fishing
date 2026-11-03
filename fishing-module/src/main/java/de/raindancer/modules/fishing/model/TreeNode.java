package de.raindancer.modules.fishing.model;

import java.util.List;
import java.util.Map;

/**
 * A node of the skill tree.
 *
 * @param column   0..4 on its branch's page, left to right
 * @param row      0..2, top to bottom
 * @param cost     skill points per rank
 * @param choice   one side of a choice: taking it shuts what it {@code excludes}
 * @param capstone the end of its branch
 * @param level    the fishing level it needs, 0 for none
 * @param requires one of these must be taken first
 * @param perks    what each rank adds
 */
public record TreeNode(String id, String branch, int column, int row, String title, String icon, int ranks, int cost,
                       boolean choice, boolean capstone, int level, List<String> requires, List<String> excludes,
                       Map<Perk, Double> perks) {

    public TreeNode {
        requires = List.copyOf(requires);
        excludes = List.copyOf(excludes);
        perks = Map.copyOf(perks);
    }
}
