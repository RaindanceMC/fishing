package de.raindancer.modules.fishing.service;

import de.raindancer.core.content.items.CustomItem;
import de.raindancer.core.content.items.CustomItems;
import de.raindancer.core.content.items.ItemFactory;
import de.raindancer.core.content.items.NonIngredients;
import de.raindancer.modules.fishing.FishingSettings;
import de.raindancer.modules.fishing.model.Bait;
import de.raindancer.modules.fishing.model.Catalog;
import de.raindancer.modules.fishing.model.Dish;
import de.raindancer.modules.fishing.model.FishKind;
import de.raindancer.modules.fishing.model.Rod;
import de.raindancer.modules.fishing.model.RodSkin;
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
        for (Bait bait : Bait.values()) {
            added += items.defineIfAbsent(baitDefinition(bait)) ? 1 : 0;
        }
        added += items.defineIfAbsent(netDefinition()) ? 1 : 0;
        for (RodSkin skin : RodSkin.values()) {
            added += items.defineIfAbsent(tokenDefinition(skin)) ? 1 : 0;
        }
        return added;
    }

    private static CustomItem fishDefinition(FishKind kind) {
        String colour = "<" + kind.tier().hex() + ">";
        String waters = kind.where();
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
        ItemStack made = make(rod.itemId(), () -> rodDefinition(rod), 1);
        describe(made);
        return made;
    }

    /** Which of the module's rods this is; empty for a vanilla rod or anything else. */
    public Optional<RodTier> rodTierOf(ItemStack stack) {
        return id(stack).filter(id -> id.startsWith("rod-")).flatMap(id -> RodTier.parse(id.substring(4)));
    }

    /** Everything fishing asks about this rod: parts, enchantments and tier. */
    public Rod rodOf(ItemStack stack) {
        return RodTags.read(stack).withTier(rodTierOf(stack).orElse(null));
    }

    private static CustomItem baitDefinition(Bait bait) {
        String colour = "<" + bait.tier().hex() + ">";
        return CustomItem.builder(PLUGIN, bait.itemId())
                .material(Material.PAPER)
                .name(colour + bait.title())
                .lore(List.of("<gray>" + bait.promise(),
                        "<dark_gray>Hold it in your off hand: every cast takes one."))
                .itemModel(FishingPack.NAMESPACE + ":bait/" + bait.id())
                .build();
    }

    private static CustomItem netDefinition() {
        return CustomItem.builder(PLUGIN, "net")
                .material(Material.PAPER)
                .name("<#7ec8e3>Fishing Net")
                .lore(List.of("<gray>A bag for fish: a chest's worth of them.",
                        "<gray>While you carry it, what you catch goes in.",
                        "<dark_gray>Right click it — in your hand or in your inventory — to open it."))
                .itemModel(FishingPack.NAMESPACE + ":net")
                .build();
    }

    private static CustomItem tokenDefinition(RodSkin skin) {
        String colour = "<" + skin.hex() + ">";
        return CustomItem.builder(PLUGIN, "skin-" + skin.id())
                .material(Material.PAPER)
                .name(colour + skin.title() + " Rod Skin")
                .lore(List.of("<gray>A look for any of your rods.",
                        "<dark_gray>Right click to add it to your skins for good;",
                        "<dark_gray>wear it from /fishing rod."))
                .itemModel(skin.model())
                .glowing(true)
                .build();
    }

    /** A skin's token, as it comes out of a sea chest. */
    public ItemStack skinToken(RodSkin skin) {
        return NonIngredients.mark(make("skin-" + skin.id(), () -> tokenDefinition(skin), 1));
    }

    public Optional<RodSkin> skinTokenOf(ItemStack stack) {
        return id(stack).filter(id -> id.startsWith("skin-")).flatMap(id -> RodSkin.parse(id.substring(5)));
    }

    /** Puts a skin on a rod — or, with null, takes it off and shows the rod as itself again. */
    public void wear(ItemStack rod, RodSkin skin) {
        org.bukkit.inventory.meta.ItemMeta meta = rod.getItemMeta();
        if (skin == null) {
            meta.getPersistentDataContainer().remove(SKIN);
        } else {
            meta.getPersistentDataContainer().set(SKIN, org.bukkit.persistence.PersistentDataType.STRING, skin.id());
        }
        rod.setItemMeta(meta);
        describe(rod);
        String model = skin != null ? skin.model() : rodTierOf(rod).map(RodTier::model).orElse(null);
        if (model == null) {
            rod.unsetData(io.papermc.paper.datacomponent.DataComponentTypes.ITEM_MODEL);
        } else {
            rod.setData(io.papermc.paper.datacomponent.DataComponentTypes.ITEM_MODEL, net.kyori.adventure.key.Key.key(model));
        }
    }

    /** The skin a rod wears, if any. */
    public Optional<RodSkin> skinOf(ItemStack rod) {
        if (rod == null || !rod.hasItemMeta()) {
            return Optional.empty();
        }
        return RodSkin.parse(rod.getItemMeta().getPersistentDataContainer().get(SKIN,
                org.bukkit.persistence.PersistentDataType.STRING));
    }

    private static final org.bukkit.NamespacedKey LORE_LINES =
            new org.bukkit.NamespacedKey(FishingPack.NAMESPACE, "rod_lore_lines");
    private static final de.raindancer.modules.fishing.rules.RodStatsRule STATS =
            new de.raindancer.modules.fishing.rules.RodStatsRule();

    /**
     * Writes what a rod does against a plain one at the top of its lore — tier, upgrades, enchantments and skin added
     * up. The lines are ours by count (kept on the rod), so whatever other plugins put below them stays.
     */
    public void describe(ItemStack stack) {
        if (!RodTags.isRod(stack)) {
            return;
        }
        Rod rod = rodOf(stack);
        org.bukkit.inventory.meta.ItemMeta meta = stack.getItemMeta();
        List<net.kyori.adventure.text.Component> old = meta.lore() == null ? List.of() : meta.lore();
        Integer ours = meta.getPersistentDataContainer().get(LORE_LINES, org.bukkit.persistence.PersistentDataType.INTEGER);
        List<net.kyori.adventure.text.Component> others = ours != null
                ? new ArrayList<>(old.subList(Math.min(ours, old.size()), old.size()))
                : new ArrayList<>(old.stream().filter(line -> !writtenBefore(line)).toList());
        List<String> mine = new ArrayList<>();
        Optional<RodTier> tier = rodTierOf(stack);
        tier.ifPresent(own -> mine.add("<" + own.tier().hex() + ">" + own.tier().title() + " rod"));
        mine.addAll(STATS.lines(rod));
        skinOf(stack).ifPresent(skin -> mine.add("<" + skin.hex() + ">Skin: " + skin.title()));
        if (tier.isPresent()) {
            mine.add("<dark_gray>Only for fishers who unlocked it (/fishing rods).");
        }
        List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
        net.kyori.adventure.text.minimessage.MiniMessage mini = net.kyori.adventure.text.minimessage.MiniMessage.miniMessage();
        for (String line : mine) {
            lore.add(mini.deserialize(line).decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
        }
        lore.addAll(others);
        meta.lore(lore.isEmpty() ? null : lore);
        meta.getPersistentDataContainer().set(LORE_LINES, org.bukkit.persistence.PersistentDataType.INTEGER, mine.size());
        stack.setItemMeta(meta);
    }

    /** A line an older version of this module wrote, before it counted its lines. */
    private static boolean writtenBefore(net.kyori.adventure.text.Component line) {
        String plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(line);
        if (plain.startsWith("Fish bite ") || plain.startsWith("Sea chests: ") || plain.startsWith("Rarer catches: ")
                || plain.startsWith("Double catches: ") || plain.startsWith("Fishes ") || plain.startsWith("Only for fishers")
                || plain.endsWith(" Line") || plain.endsWith(" rod")) {
            return true;
        }
        for (de.raindancer.modules.fishing.model.RodPart part : de.raindancer.modules.fishing.model.RodPart.values()) {
            if (plain.startsWith(part.title())) {
                return true;
            }
        }
        return false;
    }

    private static final org.bukkit.NamespacedKey SKIN = new org.bukkit.NamespacedKey(FishingPack.NAMESPACE, "rod_skin");

    /** A new, empty net; it gets its own id the first time it is used. */
    public ItemStack net() {
        ItemStack net = NonIngredients.mark(make("net", FishForge::netDefinition, 1));
        net.setData(io.papermc.paper.datacomponent.DataComponentTypes.MAX_STACK_SIZE, 1);
        return net;
    }

    public boolean isNet(ItemStack stack) {
        return id(stack).filter("net"::equals).isPresent();
    }

    public ItemStack bait(Bait bait, int amount) {
        // Paper underneath, so it must not become a map or a rocket in a crafting grid.
        return NonIngredients.mark(make(bait.itemId(), () -> baitDefinition(bait), amount));
    }

    public Optional<Bait> baitOf(ItemStack stack) {
        return id(stack).filter(id -> id.startsWith("bait-")).flatMap(id -> Bait.parse(id.substring(5)));
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
