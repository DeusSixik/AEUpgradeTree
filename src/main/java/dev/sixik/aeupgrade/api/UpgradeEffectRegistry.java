package dev.sixik.aeupgrade.api;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Pattern;

import org.jetbrains.annotations.Nullable;

/**
 * Registry of add-on upgrade effects shared by the tree loader, editor and grid.
 */
public final class UpgradeEffectRegistry {
    private static final Pattern ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");
    private static final ConcurrentMap<String, UpgradeEffectType> TYPES = new ConcurrentHashMap<>();

    private UpgradeEffectRegistry() {
    }

    /**
     * Registers a type before worlds are loaded, usually from the add-on's common mod constructor.
     *
     * @param type numeric effect definition
     * @return the same type, for use as the handle when querying a grid
     * @throws IllegalArgumentException when the ID is already registered
     */
    public static UpgradeEffectType register(UpgradeEffectType type) {
        if (TYPES.putIfAbsent(type.id(), type) != null) {
            throw new IllegalArgumentException("Duplicate upgrade effect ID: " + type.id());
        }
        return type;
    }

    /**
     * @return the definition for this ID, or null if the add-on is unavailable
     */
    public static @Nullable UpgradeEffectType get(String id) {
        return TYPES.get(id);
    }

    /**
     * @return whether the ID explicitly declares a valid namespace and path
     */
    public static boolean validId(@Nullable String id) {
        return id != null && ID.matcher(id).matches();
    }

    /**
     * @return a snapshot of the registered types ordered by namespace and path
     */
    public static List<UpgradeEffectType> types() {
        return TYPES.values().stream().sorted(Comparator.comparing(UpgradeEffectType::id)).toList();
    }
}
