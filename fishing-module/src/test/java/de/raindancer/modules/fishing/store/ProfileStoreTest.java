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
                .withSkin(de.raindancer.modules.fishing.model.RodSkin.STARFALL)
                .withVoidCatch(Tier.LEGENDARY, 4);
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

    @org.junit.jupiter.api.io.TempDir
    java.nio.file.Path dir;

    @Test
    @DisplayName("a catch is kept in memory and written in one batch, not once per catch")
    void catchesAreWrittenBehind() {
        java.nio.file.Path file = dir.resolve("fishers.yml");
        ProfileStore store = new ProfileStore(new de.raindancer.core.data.store.YamlStore(file));
        java.util.UUID ada = java.util.UUID.randomUUID();
        java.util.UUID bob = java.util.UUID.randomUUID();
        for (int i = 0; i < 50; i++) {
            store.queue(ada, p -> p.withCatch("pebble_trout", 1));
        }
        store.queue(bob, p -> p.withExperience(7));
        assertThat(store.of(ada).caught("pebble_trout")).as("reads see it at once").isEqualTo(50);
        assertThat(file).as("nothing written yet").doesNotExist();
        assertThat(store.pending()).isEqualTo(2);

        assertThat(store.flush()).isTrue();
        assertThat(store.pending()).isZero();
        ProfileStore other = new ProfileStore(new de.raindancer.core.data.store.YamlStore(file));
        other.load();
        assertThat(other.of(ada).caught("pebble_trout")).isEqualTo(50);
        assertThat(other.of(bob).experience()).isEqualTo(7);
    }

    @Test
    @DisplayName("what another server wrote in between is kept: queued changes are added to what the database holds")
    void queuedChangesAddToTheDatabase() {
        java.nio.file.Path file = dir.resolve("fishers.yml");
        ProfileStore here = new ProfileStore(new de.raindancer.core.data.store.YamlStore(file));
        ProfileStore there = new ProfileStore(new de.raindancer.core.data.store.YamlStore(file));
        java.util.UUID ada = java.util.UUID.randomUUID();
        here.queue(ada, p -> p.withCatch("pebble_trout", 1));
        there.change(ada, p -> p.withCatch("pebble_trout", 5));
        assertThat(here.flush()).isTrue();
        ProfileStore reread = new ProfileStore(new de.raindancer.core.data.store.YamlStore(file));
        reread.load();
        assertThat(reread.of(ada).caught("pebble_trout")).isEqualTo(6);
    }

    @Test
    @DisplayName("a catch is never lost: an unreadable document keeps it queued until a flush can write it")
    void unreadableKeepsThemQueued() throws Exception {
        java.nio.file.Path file = dir.resolve("fishers.yml");
        java.nio.file.Files.writeString(file, "fishers: [unclosed\n  : :");
        ProfileStore store = new ProfileStore(new de.raindancer.core.data.store.YamlStore(file));
        java.util.UUID ada = java.util.UUID.randomUUID();
        store.queue(ada, p -> p.withCatch("pebble_trout", 3));
        assertThat(store.flush()).isFalse();
        assertThat(store.pending()).isEqualTo(1);
        assertThat(store.of(ada).caught("pebble_trout")).isEqualTo(3);

        java.nio.file.Files.writeString(file, "fishers: {}\n");
        assertThat(store.flush()).isTrue();
        assertThat(store.pending()).isZero();
    }

    @Test
    @DisplayName("a direct change starts from the queued ones, so a spent point never meets a stale profile")
    void directChangeFlushesFirst() {
        java.nio.file.Path file = dir.resolve("fishers.yml");
        ProfileStore store = new ProfileStore(new de.raindancer.core.data.store.YamlStore(file));
        java.util.UUID ada = java.util.UUID.randomUUID();
        store.queue(ada, p -> p.withExperience(100));
        assertThat(store.change(ada, p -> {
            assertThat(p.experience()).isEqualTo(100);
            return p.withNode("quick_bite", 1);
        })).isTrue();
        assertThat(store.of(ada).experience()).isEqualTo(100);
        assertThat(store.of(ada).rank("quick_bite")).isEqualTo(1);
    }
}
