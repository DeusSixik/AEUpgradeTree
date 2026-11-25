package dev.sixik.aeupgrade.client.widget;

import dev.sixik.unigui.api.core.InvalidationFlags;
import dev.sixik.unigui.api.render.DrawScope;
import dev.sixik.unigui.backend.minecraft_impl.MinecraftGuiRenderBackend;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Inventory preview drawn with AE2's native slot sprite.
 */
public final class AeItemSlot extends AeSlot {
    /**
     * Item rendered instead of the fallback label, or an empty stack.
     */
    private ItemStack stack = ItemStack.EMPTY;

    /**
     * @param editable whether the slot is a focusable item picker control
     */
    public AeItemSlot(boolean editable) {
        super(editable ? "…" : "");
        focusable(editable);
    }

    /**
     * Resolves a configured item ID into a display stack.
     *
     * @param itemId registry ID, possibly absent or invalid while the operator is typing
     */
    public void item(@Nullable String itemId) {
        var id = itemId == null ? null : ResourceLocation.tryParse(itemId.strip());
        stack = id == null ? ItemStack.EMPTY
                : BuiltInRegistries.ITEM.getOptional(id).map(item -> item.getDefaultInstance()).orElse(ItemStack.EMPTY);
        invalidate(InvalidationFlags.VISUAL);
    }

    /**
     * @return display stack currently rendered in this slot
     */
    public ItemStack stack() {
        return stack;
    }

    /**
     * @param draw draw scope for the centered item preview
     */
    @Override
    protected void renderButtonText(DrawScope draw) {
        if (stack.isEmpty()) {
            super.renderButtonText(draw);
            return;
        }
        var bounds = layoutBounds();
        float size = Math.min(16, Math.min(bounds.width(), bounds.height()) - 2);
        if (size <= 0) {
            return;
        }
        float x = bounds.x() + (bounds.width() - size) / 2;
        float y = bounds.y() + (bounds.height() - size) / 2;
        var item = stack;
        draw.addCallback(backend -> {
            if (backend instanceof MinecraftGuiRenderBackend minecraft && !minecraft.renderItemPreviewLazy(item, x, y, size, 1)) {
                invalidate(InvalidationFlags.VISUAL);
            }
        });
    }
}
