package dev.sixik.aeupgrade.channel;

import java.util.function.Supplier;

import appeng.blockentity.crafting.MolecularAssemblerBlockEntity;
import appeng.blockentity.crafting.PatternProviderBlockEntity;
import appeng.blockentity.misc.InterfaceBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.parts.crafting.PatternProviderPart;
import appeng.parts.misc.InterfacePart;
import dev.sixik.aeupgrade.tree.UpgradeEffect.Target;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Device families that support configurable channel costs.
 */
public enum DeviceChannelType {
    /**
     * Molecular Assembler block.
     */
    MOLECULAR_ASSEMBLER("molecular_assembler", "Molecular Assembler", () -> AEBlocks.MOLECULAR_ASSEMBLER.stack()),
    /**
     * Both block and panel versions of the ME Pattern Provider.
     */
    PATTERN_PROVIDER("pattern_provider", "ME Pattern Provider", () -> AEBlocks.PATTERN_PROVIDER.stack()),
    /**
     * Both block and panel versions of the ME Interface.
     */
    INTERFACE("interface", "ME Interface", () -> AEBlocks.INTERFACE.stack());

    /**
     * Stable key used in world data.
     */
    private final String id;

    /**
     * English device name shown by the development commands.
     */
    private final String displayName;

    /**
     * Creates the representative item after the AE2 item registry is ready.
     */
    private final Supplier<ItemStack> displayItem;

    /**
     * Defines a device family.
     *
     * @param id          stable key for persistence
     * @param displayName name shown in command responses
     * @param displayItem factory for the item shown in controller limit rows
     */
    DeviceChannelType(String id, String displayName, Supplier<ItemStack> displayItem) {
        this.id = id;
        this.displayName = displayName;
        this.displayItem = displayItem;
    }

    /**
     * Returns the key used to save costs for this family.
     *
     * @return stable world data key
     */
    public String id() {
        return id;
    }

    /**
     * Returns the label for this family.
     *
     * @return name shown to the operator
     */
    public String displayName() {
        return displayName;
    }

    /**
     * @return a new stack of the item representing this family's limit
     */
    public ItemStack displayItem() {
        return displayItem.get();
    }

    /**
     * @return device family for scoped tree effects
     */
    public Target effectTarget() {
        return switch (this) {
            case MOLECULAR_ASSEMBLER -> Target.MOLECULAR_ASSEMBLER;
            case PATTERN_PROVIDER -> Target.PATTERN_PROVIDER;
            case INTERFACE -> Target.INTERFACE;
        };
    }

    /**
     * Finds the device family from the ID used in menu snapshots.
     *
     * @param id stable family ID
     * @return matching family, or null for an unknown ID
     */
    public static @Nullable DeviceChannelType byId(String id) {
        for (var type : values()) {
            if (type.id.equals(id)) {
                return type;
            }
        }
        return null;
    }

    /**
     * Identifies a configurable device from the owner of its AE2 node.
     *
     * @param owner block entity or part that owns the node
     * @return its device family, or null for an unsupported owner
     */
    public static @Nullable DeviceChannelType of(Object owner) {
        return switch (owner) {
            case MolecularAssemblerBlockEntity ignored -> MOLECULAR_ASSEMBLER;
            case PatternProviderBlockEntity ignored -> PATTERN_PROVIDER;
            case PatternProviderPart ignored -> PATTERN_PROVIDER;
            case InterfaceBlockEntity ignored -> INTERFACE;
            case InterfacePart ignored -> INTERFACE;
            default -> null;
        };
    }
}
