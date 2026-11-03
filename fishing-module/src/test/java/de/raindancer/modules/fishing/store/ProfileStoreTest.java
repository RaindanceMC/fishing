package de.raindancer.modules.fishing.store;

import de.raindancer.modules.fishing.model.FisherProfile;
import de.raindancer.modules.fishing.model.Tier;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProfileStoreTest {

    @Test
    @DisplayName("a profile written and read back is the same profile")
    void roundTrip() throws Exception {
        FisherProfile profile = FisherProfile.NEW.withExperience(1234).withSpent(300)
                .withNode("quick_bite", 3).withNode("patience", 1).withFreeUnlearn(true)
                .withCatch("pebble_trout", 7).withCatch("astral_leviathan", 1)
                .withChestOpened(Tier.MYTHIC).withChestOpened(Tier.MYTHIC).withChestOpened(Tier.COMMON)
                .withRod(de.raindancer.modules.fishing.model.RodTier.REEF)
                .withRod(de.raindancer.modules.fishing.model.RodTier.VOID)
                .withWeight("pebble_trout", 2.5).withWeight("pebble_trout", 1.0)
                .withSkin(de.raindancer.modules.fishing.model.RodSkin.STARFALL);
        YamlConfiguration yaml = new YamlConfiguration();
        ProfileStore.write(yaml, "fishers.someone", profile);
        YamlConfiguration reread = new YamlConfiguration();
        reread.loadFromString(yaml.saveToString());
        assertThat(ProfileStore.read(reread, "fishers.someone")).isEqualTo(profile);
        assertThat(profile.heaviest("pebble_trout")).as("a lighter one is no record").isEqualTo(2.5);
    }

    @Test
    @DisplayName("nothing written is nothing read")
    void absent() {
        assertThat(ProfileStore.read(new YamlConfiguration(), "fishers.nobody")).isNull();
    }

    @Test
    @DisplayName("the four old skills become the tree's nodes of the same name, with one free unlearning")
    void oldSkillsBecomeTheTree() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString("fishers:\n  x:\n    experience: -5\n    skills:\n      quick_bite: 4\n      double_catch: 2\n");
        FisherProfile read = ProfileStore.read(yaml, "fishers.x");
        assertThat(read.experience()).isZero();
        assertThat(read.nodes()).containsExactlyInAnyOrderEntriesOf(java.util.Map.of("quick_bite", 4, "double_catch", 2));
        assertThat(read.freeUnlearn()).isTrue();
    }

    @Test
    @DisplayName("once written in the tree's shape, old skills left behind are not read again, nor is a free unlearning given twice")
    void migratedOnce() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString("fishers:\n  x:\n    tree-version: 2\n    skills:\n      quick_bite: 4\n");
        FisherProfile read = ProfileStore.read(yaml, "fishers.x");
        assertThat(read.nodes()).isEmpty();
        assertThat(read.freeUnlearn()).isFalse();
    }

    @Test
    @DisplayName("somebody who never had a skill gets no free unlearning")
    void noSkillsNoGift() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString("fishers:\n  x:\n    experience: 50\n");
        assertThat(ProfileStore.read(yaml, "fishers.x").freeUnlearn()).isFalse();
    }
}
