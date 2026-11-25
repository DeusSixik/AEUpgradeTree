package dev.sixik.aeupgrade.tree;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import dev.sixik.aeupgrade.api.UpgradeEffectType;
import dev.sixik.aeupgrade.tree.UpgradeEffect.Kind;
import dev.sixik.aeupgrade.tree.UpgradeEffect.Target;

/**
 * Cached network effects from the unlocked nodes of an allocation.
 */
public final class UpgradeEffects {
    /**
     * Supported device scopes per property.
     */
    private static final Target[] DEVICE_TARGETS = Target.values();
    private static final int TARGETS = DEVICE_TARGETS.length;
    /**
     * Zero bonuses and unrestricted caps.
     */
    static final UpgradeEffects EMPTY = new UpgradeEffects(new double[Kind.values().length * TARGETS], -1, Map.of());
    /**
     * Summed adjustments by property and target. Caps store their positive encoded value plus one.
     */
    private final double[] values;
    /**
     * Highest purchased node ID that assigns a base speed bonus, or the core ID for the initial value.
     */
    private final int speedBaseId;
    private final Map<UpgradeEffectType, Map<Target, Contribution>> custom;

    /**
     * @param values      owned table of effects
     * @param speedBaseId node controlling the base speed modifier
     * @param custom      owned add-on modifier snapshot
     */
    private UpgradeEffects(double[] values, int speedBaseId, Map<UpgradeEffectType, Map<Target, Contribution>> custom) {
        this.values = values;
        this.speedBaseId = speedBaseId;
        this.custom = custom;
    }

    /**
     * @param nodeId  stable ID of the new purchase
     * @param effects effects contributed by that purchase
     * @return combined effects
     */
    UpgradeEffects add(int nodeId, List<UpgradeEffect> effects) {
        if (effects.isEmpty()) {
            return this;
        }
        var next = values.clone();
        int baseId = speedBaseId;
        Map<UpgradeEffectType, Map<Target, Contribution>> nextCustom = custom;
        for (int order = 0; order < effects.size(); order++) {
            var effect = effects.get(order);
            var kind = effect.kind();
            if (kind == null) {
                var type = effect.registeredType();
                if (type != null && type.accepts(effect.target(), effect.value())) {
                    if (nextCustom == custom) {
                        nextCustom = new HashMap<>(custom);
                    }
                    var scopes = new EnumMap<Target, Contribution>(Target.class);
                    scopes.putAll(nextCustom.getOrDefault(type, Map.of()));
                    // Node ID sorts first, followed by the position within the node's effect list.
                    var added = new Contribution(effect.value(), ((long) nodeId << Integer.SIZE) | order);
                    var previous = scopes.get(effect.target());
                    scopes.put(effect.target(), previous == null ? added
                            : new Contribution(combine(type, previous, added), Math.max(previous.order(), added.order())));
                    nextCustom.put(type, scopes);
                }
                continue;
            }
            int i = index(kind, effect.target());
            if (kind == Kind.SPEED_BASE) {
                if (nodeId >= baseId) {
                    baseId = nodeId;
                    next[i] = effect.value();
                }
            } else if (kind == Kind.DEVICE_CAP) {
                double encoded = effect.value() + 1;
                next[i] = next[i] == 0 ? encoded : Math.min(next[i], encoded);
            } else {
                next[i] += effect.value();
            }
        }
        return new UpgradeEffects(next, baseId, nextCustom);
    }

    /**
     * @param kind unscoped property
     * @return sum of its adjustments
     */
    public double amount(Kind kind) {
        return values[index(kind, Target.ALL)];
    }

    /**
     * @param kind   property to resolve
     * @param target device family
     * @return sum of general and family-specific adjustments
     */
    public double amount(Kind kind, Target target) {
        return target == Target.ALL ? amount(kind) : amount(kind) + values[index(kind, target)];
    }

    /**
     * Resolves the global value and any value for the requested scope using the registered stacking rule.
     *
     * @param type     registered add-on effect
     * @param target   device family or the global scope
     * @param fallback value to use when no relevant effect is present
     * @return effective amount, including explicit zero values
     */
    public double amount(UpgradeEffectType type, Target target, double fallback) {
        Objects.requireNonNull(target, "target");
        var scopes = custom.get(type);
        if (scopes == null) {
            return fallback;
        }
        var general = scopes.get(Target.ALL);
        var local = target == Target.ALL ? null : scopes.get(target);
        if (general == null) {
            return local == null ? fallback : local.amount();
        }
        return local == null ? general.amount() : combine(type, general, local);
    }

    private static double combine(UpgradeEffectType type, Contribution first, Contribution second) {
        return switch (type.stacking()) {
            case SUM -> first.amount() + second.amount();
            case MINIMUM -> Math.min(first.amount(), second.amount());
            case MAXIMUM -> Math.max(first.amount(), second.amount());
            case HIGHEST_NODE_ID -> first.order() > second.order() ? first.amount() : second.amount();
        };
    }

    /**
     * @return whether the effective amount for any device scope of this add-on effect differs
     */
    public boolean changed(UpgradeEffects previous, UpgradeEffectType type) {
        for (var target : DEVICE_TARGETS) {
            if (Double.compare(amount(type, target, Double.NaN), previous.amount(type, target, Double.NaN)) != 0) {
                return true;
            }
        }
        return false;
    }

    boolean customChanged(UpgradeEffects previous) {
        if (custom == previous.custom) {
            return false;
        }
        for (var type : custom.keySet()) {
            if (changed(previous, type)) {
                return true;
            }
        }
        return !custom.keySet().containsAll(previous.custom.keySet());
    }

    /**
     * Takes the lowest finite base limit, then applies the accumulated slot bonus or penalty.
     *
     * @param target device family
     * @param base   operator quota, or -1 for unlimited
     * @return adjusted device count in [0, Integer.MAX_VALUE], or -1 if there is no finite base limit
     */
    public int deviceLimit(Target target, int base) {
        double common = amount(Kind.DEVICE_CAP);
        double local = values[index(Kind.DEVICE_CAP, target)];
        double cap = common == 0 ? local : local == 0 ? common : Math.min(common, local);
        int baseline = base;
        if (cap > 0) {
            int ceiling = (int) (cap - 1);
            baseline = base < 0 ? ceiling : Math.min(base, ceiling);
        }
        return baseline < 0 ? -1 : (int) Math.clamp(baseline + amount(Kind.DEVICE_LIMIT, target), 0, Integer.MAX_VALUE);
    }

    /**
     * @param other last published effects
     * @param kind  property checked for cache invalidation
     * @return true if any target's adjustment changed
     */
    boolean changed(UpgradeEffects other, Kind kind) {
        int start = index(kind, Target.ALL);
        return !Arrays.equals(values, start, start + TARGETS, other.values, start, start + TARGETS);
    }

    /**
     * @param kind   property
     * @param target scope
     * @return index into the row-major effects table
     */
    private static int index(Kind kind, Target target) {
        return kind.ordinal() * TARGETS + target.ordinal();
    }

    private record Contribution(double amount, long order) {
    }
}
