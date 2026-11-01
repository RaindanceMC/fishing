package de.raindancer.modules.fishing.rules;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NetRuleTest {

    private final NetRule rule = new NetRule();

    @Test
    @DisplayName("fish go onto their own kind first, then into a free slot, 64 to a slot")
    void stacks() {
        NetRule.Added first = rule.add(List.of(), "herring", 40, 3);
        NetRule.Added second = rule.add(first.contents(), "herring", 40, 3);
        assertThat(second.contents()).containsExactly(new NetRule.Slot("herring", 64), new NetRule.Slot("herring", 16));
        assertThat(second.left()).isZero();
        NetRule.Added third = rule.add(second.contents(), "trout", 5, 3);
        assertThat(third.contents()).hasSize(3).last().isEqualTo(new NetRule.Slot("trout", 5));
    }

    @Test
    @DisplayName("a full net keeps what does not fit out — the rest stays with the fisher")
    void full() {
        NetRule.Added full = rule.add(List.of(), "herring", 200, 2);
        assertThat(full.contents()).containsExactly(new NetRule.Slot("herring", 64), new NetRule.Slot("herring", 64));
        assertThat(full.left()).isEqualTo(72);
        assertThat(rule.add(full.contents(), "trout", 3, 2).left()).isEqualTo(3);
        assertThat(rule.room(full.contents(), "herring", 2)).isZero();
        assertThat(rule.room(List.of(new NetRule.Slot("herring", 60)), "herring", 2)).isEqualTo(68);
    }

    @Test
    @DisplayName("taking out takes from that slot only, never more than is there; an emptied slot goes")
    void take() {
        List<NetRule.Slot> net = List.of(new NetRule.Slot("herring", 64), new NetRule.Slot("trout", 5));
        NetRule.Taken some = rule.take(net, 0, 10);
        assertThat(some.taken()).isEqualTo(10);
        assertThat(some.contents()).containsExactly(new NetRule.Slot("herring", 54), new NetRule.Slot("trout", 5));
        NetRule.Taken all = rule.take(net, 1, 64);
        assertThat(all.taken()).isEqualTo(5);
        assertThat(all.contents()).containsExactly(new NetRule.Slot("herring", 64));
        assertThat(rule.take(net, 7, 1).taken()).as("a slot that is not there").isZero();
    }
}
