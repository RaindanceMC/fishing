package de.raindancer.modules.fishing.rules;

import java.util.ArrayList;
import java.util.List;

/** A fishing net's slots: what goes in where, what does not fit, and what comes out. */
public final class NetRule implements IFishingRule {

    /** How many of one kind a slot holds. */
    public static final int STACK = 64;

    /**
     * @param item   which kind of fish, as the net knows it (the item, serialised)
     * @param amount how many
     */
    public record Slot(String item, int amount) {
    }

    /** @param left how many did not fit */
    public record Added(List<Slot> contents, int left) {
    }

    public record Taken(List<Slot> contents, int taken) {
    }

    public Added add(List<Slot> contents, String item, int amount, int capacity) {
        List<Slot> slots = new ArrayList<>(contents);
        int left = Math.max(0, amount);
        for (int n = 0; n < slots.size() && left > 0; n++) {
            Slot slot = slots.get(n);
            if (slot.item().equals(item) && slot.amount() < STACK) {
                int moved = Math.min(left, STACK - slot.amount());
                slots.set(n, new Slot(item, slot.amount() + moved));
                left -= moved;
            }
        }
        while (left > 0 && slots.size() < capacity) {
            int moved = Math.min(left, STACK);
            slots.add(new Slot(item, moved));
            left -= moved;
        }
        return new Added(List.copyOf(slots), left);
    }

    /** How many more of this kind would fit. */
    public int room(List<Slot> contents, String item, int capacity) {
        int room = (Math.max(0, capacity - contents.size())) * STACK;
        for (Slot slot : contents) {
            if (slot.item().equals(item)) {
                room += STACK - slot.amount();
            }
        }
        return room;
    }

    public Taken take(List<Slot> contents, int index, int amount) {
        if (index < 0 || index >= contents.size() || amount <= 0) {
            return new Taken(contents, 0);
        }
        List<Slot> slots = new ArrayList<>(contents);
        Slot slot = slots.get(index);
        int taken = Math.min(amount, slot.amount());
        if (taken == slot.amount()) {
            slots.remove(index);
        } else {
            slots.set(index, new Slot(slot.item(), slot.amount() - taken));
        }
        return new Taken(List.copyOf(slots), taken);
    }

    @Override
    public String describe() {
        return "what goes into a fishing net, where, and what comes out";
    }
}
