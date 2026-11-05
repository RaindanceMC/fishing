package de.raindancer.modules.fishing.screen;

import de.raindancer.core.social.economy.Fees;
import de.raindancer.core.ui.effect.Cues;
import de.raindancer.core.ui.menu.Icons;
import de.raindancer.core.ui.menu.Menu;
import de.raindancer.core.ui.tree.NodeState;
import de.raindancer.core.ui.tree.SkillTree;
import de.raindancer.core.ui.tree.TreeBranch;
import de.raindancer.core.ui.tree.TreeLook;
import de.raindancer.core.ui.tree.TreeMenu;
import de.raindancer.core.ui.tree.TreeNode;
import de.raindancer.modules.fishing.FishingServices;
import de.raindancer.modules.fishing.model.FisherProfile;
import de.raindancer.modules.fishing.model.Perk;
import de.raindancer.modules.fishing.model.Perks;
import de.raindancer.modules.fishing.rules.TreeRule;
import de.raindancer.modules.fishing.store.FishingPack;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** The fishing skill tree: one page per branch, a click takes the next rank of a node. */
public final class SkillTreeMenu extends TreeMenu implements IFishingScreen {

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private final FishingServices services;

    public SkillTreeMenu(FishingServices services, Player viewer, Menu parent) {
        super(viewer, services.brand(), parent);
        this.services = services;
    }

    private TreeRule rule() {
        return services.profiles().tree();
    }

    private SkillTree tree() {
        return rule().tree();
    }

    @Override
    protected int pages() {
        return tree().branches().size();
    }

    @Override
    protected Page page(int index) {
        TreeBranch branch = tree().branches().get(index);
        FisherProfile profile = services.profiles().of(viewer.getUniqueId());
        List<Node> nodes = new ArrayList<>();
        List<Edge> edges = new ArrayList<>();
        for (TreeNode node : tree().nodesOf(branch.id())) {
            NodeState state = rule().state(profile, node);
            nodes.add(new Node(node.id(), node.column(), node.row(), state, icon(profile, node, state), node.mastery()));
            for (String needed : node.requires()) {
                edges.add(new Edge(needed, node.id()));
            }
        }
        return new Page(MINI.deserialize("<dark_gray>" + branch.title()), nodes, edges,
                FishingPack.treeBackdrop(branch.id()), summary(profile, branch));
    }

    @Override
    protected TreeLook look() {
        return FishingPack.TREE_LOOK;
    }

    private ItemStack icon(FisherProfile profile, TreeNode node, NodeState state) {
        int rank = profile.rank(node.id());
        String colour = switch (state) {
            case MAXED -> "<gold>";
            case OWNED -> "<yellow>";
            case OPEN -> "<aqua>";
            case LOCKED -> "<gray>";
            case BARRED -> "<dark_red>";
        };
        List<String> lore = new ArrayList<>();
        String kind = node.mastery() ? "Mastery — free once the rest of the branch is taken"
                : node.capstone() ? "Capstone" : node.choice() ? "Choice" : null;
        if (kind != null) {
            lore.add("<dark_aqua>" + kind);
        }
        int shown = Math.max(1, rank);
        Map<Perk, Double> perks = new EnumMap<>(Perks.of(node.effects()).values());
        perks.forEach((perk, value) -> lore.add((value < 0 ? "<red>" : "<gray>") + perk.describe(value * shown)));
        if (node.ranks() > 1 && rank < node.ranks()) {
            lore.add("<dark_gray>Each rank: " + String.join(", ", perks.entrySet().stream()
                    .map(entry -> entry.getKey().describe(entry.getValue()).toLowerCase(java.util.Locale.ROOT))
                    .toList()));
        }
        lore.add("");
        lore.add("<gray>Rank <white>" + rank + "<gray>/" + node.ranks() + (node.cost() == 0 ? " · <green>free"
                : " · costs <white>" + node.cost() + "<gray> point" + (node.cost() == 1 ? "" : "s") + " a rank"));
        int level = node.levelFor(rank + 1);
        if (level > 0 && rank < node.ranks()) {
            lore.add("<gray>Needs fishing level <white>" + level);
        }
        Optional<SkillTree.Refusal> refusal = rule().refusal(profile, node);
        lore.add(refusal.map(why -> "<red>" + reason(why, node, rank)).orElse("<yellow>Click to learn the next rank."));
        ItemStack icon = Icons.of(material(node.icon()), colour + node.title()
                + (node.ranks() > 1 ? " <white>" + rank + "<gray>/" + node.ranks() : ""), lore);
        if (rank > 0) {
            icon.editMeta(meta -> meta.setEnchantmentGlintOverride(true));
        }
        return icon;
    }

    private String reason(SkillTree.Refusal why, TreeNode node, int rank) {
        return switch (why) {
            case MAXED -> "At its highest rank.";
            case BARRED -> "Shut: you took the other side of this choice.";
            case NEEDS -> "Needs a node next to it first.";
            case UNFINISHED -> "Take every other node of this branch first.";
            case LEVEL -> "Needs fishing level " + node.levelFor(rank + 1) + ".";
            case NO_POINTS -> "Not enough skill points.";
        };
    }

    private ItemStack summary(FisherProfile profile, TreeBranch branch) {
        int spentHere = tree().spentIn(profile.nodes(), branch.id());
        String fee = services.config().skillResetFee();
        List<String> lore = new ArrayList<>();
        lore.add("<gray>" + branch.blurb());
        lore.add("");
        lore.add("<green>Skill points to spend: <white>" + rule().left(profile) + "<gray> of " + rule().points(profile));
        lore.add("<gray>A point per fishing level, one per sea chest");
        lore.add("<gray>tier first opened and per ten kinds of fish.");
        lore.add("<gray>Spent in this branch: <white>" + spentHere);
        lore.add("<gray>Branches mastered: <white>" + rule().mastered(profile) + "<gray> of " + rule().masteryBranches());
        if (!profile.nodes().isEmpty()) {
            lore.add("");
            lore.add("<yellow>Shift-click<gray> to unlearn everything ("
                    + (profile.freeUnlearn() ? "<green>free this once</green>" : Fees.format(Fees.amount(fee))) + "<gray>).");
        }
        return Icons.of(material(branch.icon()), "<aqua>" + branch.title(), lore);
    }

    @Override
    protected void onNode(Node node, InventoryClickEvent event) {
        tree().node(node.id()).ifPresent(chosen -> services.profiles().learn(viewer, chosen, refused -> {
            if (refused.isPresent()) {
                services.effects().play(viewer.getUniqueId(), Cues.NO);
                services.messages().send(viewer, "fishing.tree.refused", "node", chosen.title(),
                        "reason", reason(refused.get(), chosen,
                                services.profiles().of(viewer.getUniqueId()).rank(chosen.id())));
                return;
            }
            services.effects().play(viewer.getUniqueId(), Cues.OK);
            services.messages().send(viewer, "fishing.tree.learnt", "node", chosen.title(),
                    "rank", String.valueOf(services.profiles().of(viewer.getUniqueId()).rank(chosen.id())));
            reopen();
        }));
    }

    @Override
    protected void onSummary(InventoryClickEvent event) {
        FisherProfile profile = services.profiles().of(viewer.getUniqueId());
        if (!event.isShiftClick() || profile.nodes().isEmpty()) {
            return;
        }
        String fee = services.config().skillResetFee();
        String price = profile.freeUnlearn() ? "nothing, this once" : Fees.format(Fees.amount(fee));
        new ConfirmScreen(services, viewer, this, "Unlearn the whole skill tree?",
                List.of("Every point comes back to spend again.", "It costs " + price + "."),
                () -> services.profiles().unlearnAll(viewer, fee, this::reopen)).open();
    }

    private static Material material(String id) {
        Material material = Material.matchMaterial(id);
        return material == null ? Material.PAPER : material;
    }

    @Override
    public String breadcrumb() {
        return "Skill tree";
    }

    @Override
    protected List<String> helpLines() {
        return List.of(
                "Five branches; the arrows below turn between them.",
                "Click a node to learn its next rank. A node needs",
                "one next to it first; a choice shuts its other side.",
                "",
                "Skill points: one per fishing level, one per sea chest",
                "tier first opened, one per ten kinds of fish caught.",
                "Shift-click the middle button to unlearn everything.");
    }

    @Override
    public String describe() {
        return "the fishing skill tree and the points to spend on it";
    }
}
