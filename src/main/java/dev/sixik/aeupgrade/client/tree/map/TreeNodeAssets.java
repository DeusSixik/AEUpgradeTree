package dev.sixik.aeupgrade.client.tree.map;

import java.io.IOException;

import appeng.api.util.AEColor;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.core.definitions.AEParts;
import dev.sixik.aeupgrade.tree.UpgradeCatalog;
import dev.sixik.aeupgrade.tree.UpgradeNode;
import dev.sixik.aeupgrade.tree.UpgradeNode.Kind;
import dev.sixik.aeupgrade.tree.UpgradeNodeStyle;
import dev.sixik.unigui.api.math.ColorView;
import dev.sixik.unigui.api.math.MutableColor;
import dev.sixik.unigui.api.render.DrawScope;
import dev.sixik.unigui.api.render.Paint;
import dev.sixik.unigui.api.render.TextureHandle;
import dev.sixik.unigui.api.render.TextureOptions;
import dev.sixik.unigui.backend.minecraft_impl.MinecraftTextureHandle;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.PngInfo;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Item models, textures and accent colors for upgrade nodes.
 */
public final class TreeNodeAssets {
    private TreeNodeAssets() {
    }

    /**
     * AE2 selection color for the current node and purchased connections.
     */
    static final ColorView SELECTION = MutableColor.fromHex("#ACE9FF");
    /**
     * Accent for crafting nodes and traces.
     */
    private static final ColorView MAP_CRAFTING = MutableColor.fromHex("#C1B4FA");
    /**
     * Accent for energy nodes, traces and lock reasons.
     */
    private static final ColorView MAP_ENERGY = MutableColor.fromHex("#F3C77C");
    /**
     * Contrasting color for a trade-off in a node tooltip.
     */
    static final ColorView MAP_PENALTY = MutableColor.fromHex("#FF9F9F");
    /**
     * Storage region accent.
     */
    private static final ColorView MAP_STORAGE = MutableColor.fromHex("#85E4BA");
    /**
     * Logistics region accent.
     */
    private static final ColorView MAP_LOGISTICS = MutableColor.fromHex("#F4A49C");
    /**
     * Diagnostics and redundancy region accent.
     */
    private static final ColorView MAP_RESILIENCE = MutableColor.fromHex("#D8D9AE");
    private static final Paint UNTINTED = Paint.fill(MutableColor.rgba255(255, 255, 255, 255));

    /**
     * Chooses an existing AE2 item representing each upgrade.
     *
     * @param node upgrade shown in the icon map
     * @return item model to draw in the node
     */
    private static ItemStack nodeIcon(UpgradeNode node) {
        if (node.id() == UpgradeNode.ROOT) {
            return AEBlocks.CONTROLLER.stack();
        }
        if (node.legacyEffectNode()) {
            return switch (node.key()) {
                case "channels" -> AEParts.SMART_CABLE.stack(AEColor.CYAN);
                case "patterns" -> AEBlocks.PATTERN_PROVIDER.stack();
                case "speed" -> AEItems.SPEED_CARD.stack();
                case "overdrive" -> AEItems.ADVANCED_CARD.stack();
                case "batch" -> AEBlocks.MOLECULAR_ASSEMBLER.stack();
                case "idle" -> AEBlocks.ENERGY_CELL.stack();
                default -> AEItems.ENERGY_CARD.stack();
            };
        }
        var route = node.route();
        if (route == null) {
            return switch (node.sector()) {
                case STORAGE -> AEBlocks.DRIVE.stack();
                case LOGISTICS -> AEBlocks.INTERFACE.stack();
                default -> AEBlocks.WIRELESS_ACCESS_POINT.stack();
            };
        }
        if (node.kind() == Kind.PASSIVE && node.prerequisiteCount() <= 1) {
            return switch (node.sector()) {
                case NETWORK -> AEItems.LOGIC_PROCESSOR.stack();
                case CRAFTING -> AEItems.CALCULATION_PROCESSOR.stack();
                case POWER -> AEItems.ENERGY_CARD.stack();
                case STORAGE -> AEItems.CELL_COMPONENT_16K.stack();
                case LOGISTICS -> AEItems.FORMATION_CORE.stack();
                case RESILIENCE -> AEItems.REDSTONE_CARD.stack();
            };
        }
        return switch (route) {
            case PATTERN_LIBRARY -> AEBlocks.PATTERN_PROVIDER.stack();
            case CHANNEL_ROUTING -> AEParts.SMART_DENSE_CABLE.stack(AEColor.CYAN);
            case MASS_PRODUCTION -> AEBlocks.MOLECULAR_ASSEMBLER.stack();
            case OVERCLOCKING -> AEItems.ADVANCED_CARD.stack();
            case EFFICIENCY -> AEItems.ENERGY_CARD.stack();
            case POWER_RESERVE -> AEBlocks.DENSE_ENERGY_CELL.stack();
            case DENSITY -> AEItems.ITEM_CELL_256K.stack();
            case PRECISION -> AEBlocks.CELL_WORKBENCH.stack();
            case THROUGHPUT -> AEBlocks.IO_PORT.stack();
            case STOCKKEEPING -> AEBlocks.INTERFACE.stack();
            case REDUNDANCY -> AEBlocks.QUANTUM_RING.stack();
            case DIAGNOSTICS -> AEBlocks.CRAFTING_MONITOR.stack();
        };
    }

    /**
     * @param entry configured node with optional registry ID
     * @return resolved icon or the original sector icon
     */
    public static ItemStack entryIcon(UpgradeCatalog.Entry entry) {
        if (!entry.icon().isEmpty()) {
            var id = ResourceLocation.tryParse(entry.icon());
            if (id != null && BuiltInRegistries.ITEM.containsKey(id)) {
                return new ItemStack(BuiltInRegistries.ITEM.get(id));
            }
        }
        return nodeIcon(entry.node());
    }

    /**
     * Selects the LED color shared by the nodes and cables of a branch.
     *
     * @param node graph node
     * @return branch's accent color
     */
    static ColorView branchColor(UpgradeNode node) {
        return switch (node.sector()) {
            case NETWORK -> SELECTION;
            case CRAFTING -> MAP_CRAFTING;
            case POWER -> MAP_ENERGY;
            case STORAGE -> MAP_STORAGE;
            case LOGISTICS -> MAP_LOGISTICS;
            case RESILIENCE -> MAP_RESILIENCE;
        };
    }

    /**
     * The configured border color also serves as the endpoint color of each attached link.
     */
    public static ColorView resolveBorderColor(UpgradeNode node, UpgradeNodeStyle style) {
        return style.borderColor() == null ? branchColor(node) : MutableColor.fromHex(style.borderColor());
    }

    /**
     * Resolves a resource-pack PNG used by a node, with an item fallback for missing icons.
     */
    public static @Nullable MinecraftTextureHandle nodeTexture(@Nullable String path) {
        if (path == null) {
            return null;
        }
        var location = ResourceLocation.tryParse(path);
        if (location == null) {
            return null;
        }
        var resource = Minecraft.getInstance().getResourceManager().getResource(location);
        if (resource.isEmpty()) {
            return null;
        }
        try (var input = resource.get().open()) {
            var png = PngInfo.fromStream(input);
            return new MinecraftTextureHandle(location, png.width(), png.height(), TextureOptions.nearest());
        } catch (IOException malformed) {
            return null;
        }
    }

    /**
     * Centers an untinted texture preview without changing its aspect ratio.
     */
    public static void drawTexturePreview(DrawScope draw, TextureHandle texture, float x, float y, float size) {
        float ratio = (float) texture.width() / texture.height();
        float width = size * Math.min(1, ratio);
        float height = size * Math.min(1, 1 / ratio);
        draw.texture(texture, x + (size - width) / 2, y + (size - height) / 2, width, height, UNTINTED);
    }
}
