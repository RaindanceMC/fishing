package de.raindancer.modules.fishing.service;

import de.raindancer.core.content.items.CustomItem;
import de.raindancer.core.content.items.CustomItems;
import de.raindancer.core.content.items.ItemFactory;
import de.raindancer.core.content.items.NonIngredients;
import de.raindancer.modules.fishing.FishingSettings;
import de.raindancer.modules.fishing.model.Catalog;
import de.raindancer.modules.fishing.model.Dish;
import de.raindancer.modules.fishing.model.FishKind;
import de.raindancer.modules.fishing.model.Rod;
import de.raindancer.modules.fishing.model.RodTier;
import de.raindancer.modules.fishing.store.RodTags;
import de.raindancer.modules.fishing.model.Tier;
import de.raindancer.modules.fishing.model.Water;
import de.raindancer.modules.fishing.store.FishingPack;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Makes the module's items — fish, dishes and sea chests — through Core's custom items, and knows one again by the
 * key in its data rather than by its name, which an anvil could copy.
 *
 * <p>Every item is defined with {@code defineIfAbsent}: an owner who renamed the Mirror Carp keeps their name.
 */
public final class FishForge implements IFishingService {

    public static final String PLUGIN = "fishing";

    private final Catalog catalog;
    private final CustomItems items;
    private final ItemFactory factory;

    public FishForge(Catalog catalog, CustomItems items, ItemFactory factory) {
        this.catalog = catalog;
        this.items = items;
        this.factory = factory;
    }

    @Override
    public void settings(FishingSettings settings) {
        // Nothing here reads the settings: what an item is does not change with them.
    }

    /** Defines everything the module makes; returns how many were new. */
    public int defineAll() {
        int added = 0;
        for (FishKind kind : catalog.fish()) {
            added += items.defineIfAbsent(fishDefinition(kind)) ? 1 : 0;
        }
        for (Dish dish : catalog.dishes()) {
            added += items.defineIfAbsent(dishDefinition(dish)) ? 1 : 0;
            added += items.defineIfAbsent(rawDefinition(dish)) ? 1 : 0;
        }
        for (Tier tier : Tier.values()) {
            added += items.defineIfAbsent(chestDefinition(tier)) ? 1 : 0;
        }
        for (RodTier rod : RodTier.values()) {
            added += items.defineIfAbsent(rodDefinition(rod)) ? 1 : 0;
        }
        return added;
    }

    private static CustomItem fishDefinition(FishKind kind) {
        String colour = "<" + kind.tier().hex() + ">";
        String waters = kind.waters().stream().sorted().map(Water::title).collect(Collectors.joining(", "));
        return CustomItem.builder(PLUGIN, kind.itemId())
                .material(Material.COD)
                .name(colour + kind.name())
                .lore(List.of(colour + kind.tier().title() + " fish",
                        "<gray>Bites in: <white>" + waters,
                        "<dark_gray>Cook it, or bring it to a dish."))
                .itemModel(kind.model())
                .build();
    }

    private static CustomItem dishDefinition(Dish dish) {
        String colour = "<" + dish.tier().hex() + ">";
        List<String> lore = new ArrayList<>();
        lore.add(colour + dish.tier().title() + " dish");
        lore.add("<gray>Restores <white>" + dish.food() + "</white> hunger");
        for (Dish.DishEffect effect : dish.effects()) {
            lore.add("<gray>" + effectTitle(effect.effect()) + " " + roman(effect.amplifier() + 1)
                    + " <dark_gray>(" + seconds(effect.seconds()) + ")");
        }
        return CustomItem.builder(PLUGIN, dish.itemId())
                .material(dish.leavesABowl() ? Material.MUSHROOM_STEW : Material.COOKED_COD)
                .name(colour + dish.name())
                .lore(lore)
                .itemModel(dish.model())
                .build();
    }

    /**
     * A dish as it comes off the crafting table: not yet food. Paper underneath, which nothing smelts or eats, so a
     * raw dish can only become the dish in the furnace recipe meant for it.
     */
    private static CustomItem rawDefinition(Dish dish) {
        String colour = "<" + dish.tier().hex() + ">";
        return CustomItem.builder(PLUGIN, "raw-" + dish.itemId())
                .material(Material.PAPER)
                .name(colour + "Raw " + dish.name())
                .lore(List.of(colour + dish.tier().title() + " dish, uncooked",
                        "<gray>Cook it in a furnace, a smoker",
                        "<gray>or over a campfire to finish it."))
                .itemModel("rainsfishing:dish/raw_" + dish.id())
                .build();
    }

    private static CustomItem rodDefinition(RodTier rod) {
        String colour = "<" + rod.tier().hex() + ">";
        List<String> lore = new ArrayList<>();
        lore.add(colour + rod.tier().title() + " rod");
        lore.add("<gray>Fish bite <white>" + rod.waitPercent() + "%</white> sooner");
        lore.add("<gray>Sea chests: <white>+" + (rod.chestPermille() / 10.0) + "%");
        if (rod.rarePercent() > 0) {
            lore.add("<gray>Rarer catches: <white>+" + rod.rarePercent() + "%");
        }
        if (rod.doublePermille() > 0) {
            lore.add("<gray>Double catches: <white>+" + (rod.doublePermille() / 10.0) + "%");
        }
        rod.special().ifPresent(water -> lore.add("<gold>Fishes " + (water == Water.VOID ? "lava and the void" : "lava")));
        lore.add("<dark_gray>Only for fishers who unlocked it (/fishing rods).");
        return CustomItem.builder(PLUGIN, rod.itemId())
                .material(Material.FISHING_ROD)
                .name(colour + rod.title())
                .lore(lore)
                .itemModel(rod.model())
                .build();
    }

    private static CustomItem chestDefinition(Tier tier) {
        String colour = "<" + tier.hex() + ">";
        // Not a block item: the client would draw a plain chest for a moment before the sea chest stands there.
        return CustomItem.builder(PLUGIN, "chest-" + tier.chest())
                .material(Material.PAPER)
                .name(colour + tier.chestName())
                .lore(List.of(colour + tier.title() + " sea chest",
                        "<gray>Fished up, still dripping.",
                        "<dark_gray>Place it, then right click it to open it."))
                .itemModel(FishingPack.chestModel(tier.chest(), ""))
                .glowing(tier.atLeast(Tier.LEGENDARY))
                .build();
    }

    public ItemStack fish(FishKind kind, int amount) {
        return make(kind.itemId(), () -> fishDefinition(kind), amount);
    }

    public ItemStack dish(Dish dish, int amount) {
        return make(dish.itemId(), () -> dishDefinition(dish), amount);
    }

    public ItemStack raw(Dish dish, int amount) {
        return make("raw-" + dish.itemId(), () -> rawDefinition(dish), amount);
    }

    public ItemStack rod(RodTier rod) {
        return make(rod.itemId(), () -> rodDefinition(rod), 1);
    }

    /** Which of the module's rods this is; empty for a vanilla rod or anything else. */
    public Optional<RodTier> rodTierOf(ItemStack stack) {
        return id(stack).filter(id -> id.startsWith("rod-")).flatMap(id -> RodTier.parse(id.substring(4)));
    }

    /** Everything fishing asks about this rod: parts, enchantments and tier. */
    public Rod rodOf(ItemStack stack) {
        return RodTags.read(stack).withTier(rodTierOf(stack).orElse(null));
    }

    public ItemStack chest(Tier tier, int amount) {
        ItemStack stack = make("chest-" + tier.chest(), () -> chestDefinition(tier), amount);
        // Paper underneath, and a sea chest must not turn into a map or a book in a crafting grid.
        return NonIngredients.mark(stack);
    }

    private ItemStack make(String id, java.util.function.Supplier<CustomItem> fallback, int amount) {
        CustomItem definition = items.byKey(PLUGIN + ":" + id).orElseGet(fallback);
        return factory.create(definition, Math.max(1, amount)).orElseGet(() -> new ItemStack(definition.material()));
    }

    public Optional<FishKind> fishOf(ItemStack stack) {
        return id(stack).filter(id -> id.startsWith("fish-")).flatMap(id -> catalog.fish(id.substring(5)));
    }

    public Optional<Dish> dishOf(ItemStack stack) {
        return id(stack).filter(id -> id.startsWith("dish-")).flatMap(id -> catalog.dish(id.substring(5)));
    }

    public Optional<Tier> chestOf(ItemStack stack) {
        return id(stack).filter(id -> id.startsWith("chest-")).flatMap(id -> Tier.parse(id.substring(6)));
    }

    private Optional<String> id(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return Optional.empty();
        }
        return factory.keyOf(stack).filter(key -> key.startsWith(PLUGIN + ":")).map(key -> key.substring(
                PLUGIN.length() + 1));
    }

    static String effectTitle(String key) {
        String[] words = key.replace("minecraft:", "").split("_");
        StringBuilder out = new StringBuilder();
        for (String word : words) {
            if (!out.isEmpty()) {
                out.append(' ');
            }
            out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return out.toString();
    }

    static String roman(int level) {
        return switch (level) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            default -> String.valueOf(level);
        };
    }

    static String seconds(int seconds) {
        return seconds >= 60 && seconds % 60 == 0 ? seconds / 60 + " min" : seconds + " s";
    }
}
