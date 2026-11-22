package de.raindancer.modules.fishing.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * A tank and the fish swimming in it, written as {@code owner|fish[:mutation];…} into the chunk under its block.
 */
public record Aquarium(UUID owner, List<Resident> residents) {

    public static final int CAPACITY = 3;

    /** @param mutation null for a plain fish */
    public record Resident(String fishId, Mutation mutation) {

        String format() {
            return mutation == null ? fishId : fishId + ":" + mutation.id();
        }

        static Resident parse(String text) {
            int colon = text.indexOf(':');
            return colon < 0 ? new Resident(text, null)
                    : new Resident(text.substring(0, colon), Mutation.parse(text.substring(colon + 1)).orElse(null));
        }
    }

    public Aquarium {
        residents = List.copyOf(residents);
    }

    public static Aquarium empty(UUID owner) {
        return new Aquarium(owner, List.of());
    }

    public boolean full() {
        return residents.size() >= CAPACITY;
    }

    /** With one more fish; the same tank when it is full. */
    public Aquarium with(Resident resident) {
        if (full()) {
            return this;
        }
        List<Resident> more = new ArrayList<>(residents);
        more.add(resident);
        return new Aquarium(owner, more);
    }

    public Optional<Resident> last() {
        return residents.isEmpty() ? Optional.empty() : Optional.of(residents.getLast());
    }

    public Aquarium withoutLast() {
        return residents.isEmpty() ? this : new Aquarium(owner, residents.subList(0, residents.size() - 1));
    }

    public String format() {
        StringBuilder out = new StringBuilder(owner.toString()).append('|');
        for (int n = 0; n < residents.size(); n++) {
            out.append(n == 0 ? "" : ";").append(residents.get(n).format());
        }
        return out.toString();
    }

    public static Optional<Aquarium> parse(String text) {
        if (text == null) {
            return Optional.empty();
        }
        int bar = text.indexOf('|');
        if (bar < 0) {
            return Optional.empty();
        }
        UUID owner;
        try {
            owner = UUID.fromString(text.substring(0, bar));
        } catch (IllegalArgumentException broken) {
            return Optional.empty();
        }
        List<Resident> residents = new ArrayList<>();
        String rest = text.substring(bar + 1);
        if (!rest.isEmpty()) {
            for (String each : rest.split(";")) {
                if (!each.isBlank() && residents.size() < CAPACITY) {
                    residents.add(Resident.parse(each.trim()));
                }
            }
        }
        return Optional.of(new Aquarium(owner, residents));
    }
}
