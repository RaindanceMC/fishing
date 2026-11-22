package de.raindancer.modules.fishing.screen;

import de.raindancer.core.social.economy.Fees;
import de.raindancer.core.social.economy.Money;
import de.raindancer.core.ui.menu.Icons;
import de.raindancer.core.ui.menu.Menu;
import de.raindancer.core.ui.menu.MenuLayout;
import de.raindancer.modules.fishing.FishingServices;
import de.raindancer.modules.fishing.model.FishKind;
import de.raindancer.modules.fishing.rules.MerchantRule;
import de.raindancer.modules.fishing.service.MerchantService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/** The fishmonger's counter: what he wants today, and selling the fish in your hand or every fish you carry. */
public final class MerchantMenu extends Menu implements IFishingScreen {

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private final FishingServices services;

    public MerchantMenu(FishingServices services, Player viewer) {
        super(viewer, services.brand(), null);
        this.services = services;
    }

    @Override
    protected Component title() {
        return MINI.deserialize("<dark_gray>Fishmonger");
    }

    @Override
    public String breadcrumb() {
        return "Fishmonger";
    }

    @Override
    protected void render() {
        MerchantService merchant = services.merchant();
        List<String> about = new java.util.ArrayList<>(List.of("<gray>Buys every fish at the shop's price.",
                "<gray>Today's three wanted fish pay <white>" + MerchantRule.WANTED_FACTOR + "×</white>.",
                "<gray>Rare variants pay a multiple:"));
        for (de.raindancer.modules.fishing.model.Mutation mutation
                : de.raindancer.modules.fishing.model.Mutation.values()) {
            about.add("<" + mutation.hex() + ">  " + mutation.title() + " <white>" + mutation.priceFactor() + "×");
        }
        set(MenuLayout.HEADER_SUBJECT, Icons.of(Material.COD, "<aqua>Fishmonger", about));

        List<FishKind> wanted = merchant.wanted();
        for (int n = 0; n < wanted.size(); n++) {
            FishKind kind = wanted.get(n);
            band(MenuLayout.RULES, 2 + n * 2, Icons.showing(services.forge().fish(kind, 1),
                    "<" + kind.tier().hex() + ">" + kind.name(), List.of(
                            "<gold>Wanted today",
                            "<gray>Pays <white>" + money(merchant.price(kind, null)) + "</white> each",
                            "<gray>Bites in: <white>" + kind.where())));
        }

        ItemStack hand = viewer.getInventory().getItemInMainHand();
        MerchantService.Offer inHand = merchant.offer(viewer, stack -> stack.equals(hand));
        band(MenuLayout.LAND, 3, inHand.fish() > 0
                ? Icons.showing(hand.clone(), "<green>Sell what is in your hand", List.of(
                        "<gray>" + inHand.fish() + " fish for <white>" + money(inHand.minor())))
                : Icons.of(Material.BARRIER, "<gray>Sell what is in your hand", "<dark_gray>You hold no fish."),
                click -> sell(merchant.offer(viewer, stack -> stack.equals(viewer.getInventory().getItemInMainHand()))));

        MerchantService.Offer all = merchant.everything(viewer);
        band(MenuLayout.LAND, 5, all.fish() > 0
                ? Icons.of(Material.BARREL, "<green>Sell every fish you carry",
                        "<gray>" + all.fish() + " fish for <white>" + money(all.minor()))
                : Icons.of(Material.BARREL, "<gray>Sell every fish you carry", "<dark_gray>You carry no fish."),
                click -> sell(merchant.everything(viewer)));
    }

    private void sell(MerchantService.Offer offer) {
        services.merchant().sell(viewer, offer);
        refresh();
    }

    private static String money(long minor) {
        return Fees.format(Money.of(minor));
    }

    @Override
    protected List<String> helpLines() {
        return List.of(
                "The fishmonger buys fish, nothing else.",
                "His three wanted fish change every day.",
                "A rare variant fetches a multiple of a plain one.");
    }

    @Override
    public String describe() {
        return "the fishmonger's counter: today's wanted fish and selling fish";
    }
}
