package de.raindancer.modules.fishing.store;

import de.raindancer.core.content.pack.JarPack;
import de.raindancer.core.content.pack.ResourcePacks;
import org.bukkit.plugin.Plugin;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * The fishing looks — fish, dishes, sea chests, the ripple and the big bobber — shipped in the jar and handed to
 * Core's resource pack. {@code files.txt} lists them; {@code art/pack.py} writes both.
 */
public final class FishingPack {

    public static final String NAMESPACE = "rainsfishing";
    static final String ROOT = "/de/raindancer/modules/fishing/pack/";
    private static final String OWNER = "Fishing";
    private static final String NAME = "looks";

    private FishingPack() {
    }

    public static List<String> files() {
        try (InputStream in = FishingPack.class.getResourceAsStream(ROOT + "files.txt")) {
            if (in == null) {
                return List.of();
            }
            return new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)).lines()
                    .map(String::trim).filter(line -> !line.isEmpty()).toList();
        } catch (IOException unreadable) {
            return List.of();
        }
    }

    /** @throws IOException when the files could not be written out — fishing works on, in vanilla's looks */
    public static void offer(Plugin plugin, ResourcePacks packs) throws IOException {
        JarPack.offer(plugin, packs, FishingPack.class, ROOT, files(), OWNER, NAME,
                "Fish, dishes, sea chests and fishing spots");
    }

    /** Takes the looks back out of Core's pack, when the module stops. */
    public static void withdraw(ResourcePacks packs) {
        packs.withdraw(OWNER.toLowerCase(java.util.Locale.ROOT) + ":" + NAME);
    }

    /** The item model of a sea chest: whole, or the body and lid the opening animates apart. */
    public static String chestModel(String chest, String part) {
        return NAMESPACE + ":chest/" + chest + (part.isEmpty() ? "" : "_" + part);
    }

    public static String rippleModel() {
        return NAMESPACE + ":spot/ripple";
    }

    public static String bobberModel(String kind) {
        return NAMESPACE + ":spot/bobber_" + kind;
    }
}
