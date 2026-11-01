package de.raindancer.modules.fishing.rules;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VanillaCatchRuleTest {

    private final VanillaCatchRule rule = new VanillaCatchRule();

    @Test
    @DisplayName("vanilla's junk is replaced like its fish; its treasure stays treasure")
    void junkIsReplaced() {
        for (String junk : new String[] {"lily_pad", "bowl", "leather", "leather_boots", "rotten_flesh", "stick",
                "string", "potion", "bone", "ink_sac", "tripwire_hook", "bamboo", "fishing_rod", "saddle"}) {
            assertThat(rule.replaced(junk)).as(junk).isTrue();
        }
        for (String fish : new String[] {"cod", "salmon", "pufferfish", "tropical_fish"}) {
            assertThat(rule.replaced(fish)).as(fish).isTrue();
        }
        for (String treasure : new String[] {"enchanted_book", "bow", "name_tag", "nautilus_shell"}) {
            assertThat(rule.replaced(treasure)).as(treasure).isFalse();
        }
    }
}
