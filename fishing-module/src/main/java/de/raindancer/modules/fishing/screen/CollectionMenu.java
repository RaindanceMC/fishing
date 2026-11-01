package de.raindancer.modules.fishing.screen;

import de.raindancer.core.ui.menu.Icons;
import de.raindancer.core.ui.menu.Menu;
import de.raindancer.core.ui.menu.PaginatedMenu;
import de.raindancer.modules.fishing.FishingServices;
import de.raindancer.modules.fishing.model.FishKind;
import de.raindancer.modules.fishing.model.FisherProfile;
import de.raindancer.modules.fishing.model.Water;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/** Every fish there is, commonest first: the ones you caught as themselves, the rest as a hint where to look. */
public final class CollectionMenu extends PaginatedMenu<FishKind> implements IFishingScreen {

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private final FishingServices services;
    private final FisherProfile profile;

    public CollectionMenu(FishingServices services, Player viewer, Menu parent) {
        super(viewer, services.brand(), parent);
        this.services = services;
        this.profile = services.profiles().of(viewer.getUniqueId());
    }

    @Override
    protected Component title() {
        return MINI.deserialize("<dark_gray>Fish collection");
    }

    @Override
    public String breadcrumb() {
        return "Collection";
    }

    @Override
    protected List<FishKind> entries() {
        return services.catalog().fish().stream()
                .sorted(Comparator.comparing(FishKind::tier).thenComparing(FishKind::name)).toList();
    }

    @Override
    protected ItemStack icon(FishKind kind) {
        String waters = kind.where();
        int caught = profile.caught(kind.id());
        String colour = "<" + kind.tier().hex() + ">";
        if (caught > 0) {
            return Icons.showing(services.forge().fish(kind, 1), colour + kind.name(), List.of(
                    colour + kind.tier().title(),
                    "<gray>Caught: <white>" + caught,
                    "<gray>Heaviest: <white>" + de.raindancer.modules.fishing.rules.WeightRule.format(
                            profile.heaviest(kind.id())),
                    "<gray>Bites in: <white>" + waters));
        }
        return Icons.of(Material.GRAY_DYE, "<dark_gray>Not caught yet", List.of(
                colour + kind.tier().title() + " fish",
                "<gray>Bites in: <white>" + waters));
    }

    @Override
    protected void onClick(FishKind kind, InventoryClickEvent event) {
        // A page to look at; the fish themselves are in the water.
    }

    @Override
    protected List<String> helpLines() {
        return List.of(
                "Every fish there is, from common to mythic.",
                "One you have not caught yet shows where it bites.");
    }

    @Override
    public String describe() {
        return "every fish there is, and which of them this fisher has caught";
    }
}
