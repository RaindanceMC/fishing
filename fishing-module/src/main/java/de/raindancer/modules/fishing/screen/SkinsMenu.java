package de.raindancer.modules.fishing.screen;

import de.raindancer.core.ui.menu.Icons;
import de.raindancer.core.ui.menu.Menu;
import de.raindancer.core.ui.menu.PaginatedMenu;
import de.raindancer.modules.fishing.FishingServices;
import de.raindancer.modules.fishing.model.FisherProfile;
import de.raindancer.modules.fishing.model.RodSkin;
import de.raindancer.modules.fishing.rules.SkinRule;
import de.raindancer.modules.fishing.store.RodTags;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Optional;

/** Every rod skin: the ones this fisher has go onto the rod in their hand with a click; the others say how to get them. */
public final class SkinsMenu extends PaginatedMenu<RodSkin> implements IFishingScreen {

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private final FishingServices services;
    private final SkinRule skins;

    public SkinsMenu(FishingServices services, Player viewer, Menu parent) {
        super(viewer, services.brand(), parent);
        this.services = services;
        this.skins = new SkinRule(services.catalog(), services.profiles().levels());
    }

    @Override
    protected Component title() {
        return MINI.deserialize("<dark_gray>Rod skins");
    }

    @Override
    public String breadcrumb() {
        return "Skins";
    }

    @Override
    protected List<RodSkin> entries() {
        return List.of(RodSkin.values());
    }

    private ItemStack held() {
        ItemStack main = viewer.getInventory().getItemInMainHand();
        return RodTags.isRod(main) ? main : null;
    }

    @Override
    protected ItemStack icon(RodSkin skin) {
        FisherProfile profile = services.profiles().of(viewer.getUniqueId());
        String colour = "<" + skin.hex() + ">";
        if (!skins.has(skin, profile)) {
            return Icons.of(Material.GRAY_DYE, "<dark_gray>" + skin.title() + " — not yours yet",
                    List.of("<gray>" + skins.how(skin)));
        }
        ItemStack rod = held();
        boolean wearing = rod != null && services.forge().skinOf(rod).filter(skin::equals).isPresent();
        String state = !supporter() ? "<light_purple>Yours — wearing it is a thank-you for supporters."
                : wearing ? "<green>Your rod wears it." : rod == null ? "<gray>Hold a rod to put it on."
                : "<yellow>Click to put it on the rod in your hand.";
        return Icons.showing(services.forge().skinToken(skin), colour + skin.title(), List.of(state,
                "<dark_gray>Only a look: the rod fishes as itself."));
    }

    @Override
    protected void onClick(RodSkin skin, InventoryClickEvent event) {
        if (!skins.has(skin, services.profiles().of(viewer.getUniqueId()))) {
            services.messages().send(viewer, "fishing.skin.not-yours", "skin", skin.title(), "how", skins.how(skin));
            return;
        }
        wear(skin);
    }

    @Override
    protected void decorate() {
        super.decorate();
        toolbar(4, Icons.of(Material.FISHING_ROD, "<gray>Take the skin off",
                "<dark_gray>The rod in your hand shows as itself again."), click -> wear(null));
    }

    private boolean supporter() {
        return viewer.hasPermission(de.raindancer.modules.fishing.util.PermissionNodes.SKINS);
    }

    private void wear(RodSkin skin) {
        if (skin != null && !supporter()) {
            services.messages().send(viewer, "fishing.skin.supporters");
            return;
        }
        ItemStack rod = held();
        if (rod == null) {
            services.messages().send(viewer, "fishing.skin.hold-a-rod");
            return;
        }
        services.forge().wear(rod, skin);
        Optional.ofNullable(skin).ifPresentOrElse(
                on -> services.messages().send(viewer, "fishing.skin.worn", "skin", on.title()),
                () -> services.messages().send(viewer, "fishing.skin.off"));
        refresh();
    }

    @Override
    protected List<String> helpLines() {
        return List.of(
                "A skin is a look for a rod; it fishes as itself —",
                "and wearing one is a thank-you for supporters.",
                "Most are earned by fishing — a level, or every",
                "fish of one water. The rarest come out of sea",
                "chests as tokens: right click one to keep it.");
    }

    @Override
    public String describe() {
        return "the rod skins, and putting one on the rod in hand";
    }
}
