package dev.sixik.aeupgrade.client.tree.editor;

import static dev.sixik.aeupgrade.client.tree.UpgradeTreeText.tr;

import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

import dev.sixik.aeupgrade.client.tree.map.TreeNodeAssets;
import dev.sixik.aeupgrade.client.widget.AeButton;
import dev.sixik.aeupgrade.client.widget.AeScrollbar;
import dev.sixik.aeupgrade.client.widget.AeSlot;
import dev.sixik.aeupgrade.client.widget.AeStyle;
import dev.sixik.aeupgrade.client.widget.AeTextField;
import dev.sixik.aeupgrade.client.widget.ModalWindow;
import dev.sixik.aeupgrade.tree.UpgradeNodeStyle;
import dev.sixik.unigui.api.core.InvalidationFlags;
import dev.sixik.unigui.api.event.EventPhase;
import dev.sixik.unigui.api.event.PointerEnteredEvent;
import dev.sixik.unigui.api.layout.Align;
import dev.sixik.unigui.api.layout.Justify;
import dev.sixik.unigui.api.math.MutableColor;
import dev.sixik.unigui.api.render.DrawScope;
import dev.sixik.unigui.api.render.NineSlice;
import dev.sixik.unigui.api.render.Paint;
import dev.sixik.unigui.api.text.RichText;
import dev.sixik.unigui.api.widget.Visibility;
import dev.sixik.unigui.backend.minecraft_impl.MinecraftFonts;
import dev.sixik.unigui.backend.minecraft_impl.MinecraftGuiRenderBackend;
import dev.sixik.unigui.backend.minecraft_impl.MinecraftTextureHandle;
import dev.sixik.unigui.widgets.containers.HBox;
import dev.sixik.unigui.widgets.containers.VBox;
import dev.sixik.unigui.widgets.data.VirtualListView;
import dev.sixik.unigui.widgets.display.Text;
import dev.sixik.unigui.widgets.feedback.OverlayLayer;
import dev.sixik.unigui.widgets.feedback.Tooltip;
import dev.sixik.unigui.widgets.feedback.WindowWidget;
import dev.sixik.unigui.widgets.interaction.ToggleButton;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

/**
 * Searchable item and resource-pack texture grid in an AE2 modal.
 */
final class UpgradeAssetPicker {
    private static final MutableColor INK = MutableColor.fromHex("#413F54");
    private static final MutableColor SLOT_HIGHLIGHT = MutableColor.rgba255(255, 255, 255, 80);
    private static final Paint UNTINTED = Paint.fill(MutableColor.fromHex("#FFFFFF"));
    private static final NineSlice PANEL = NineSlice.of(AeStyle.PANEL, 3);
    private final AeTextField search = new AeTextField(160);
    private final VirtualListView grid = new VirtualListView();
    private final Tooltip tooltip = new Tooltip().offset(8, 6).maxWidth(230);
    private final Text empty = new Text(tr("editor.asset_empty"));
    private final AeButton useId = new AeButton(tr("editor.asset_use_id"));
    private final ToggleButton itemsTab = tab("editor.asset_items");
    private final ToggleButton texturesTab = tab("editor.asset_textures");
    private final Consumer<String> picked;
    private final WindowWidget window;
    private @Nullable List<Asset> items;
    private @Nullable List<Asset> textures;
    private boolean textureMode;

    static WindowWidget open(OverlayLayer host, String title, boolean texturesOnly, boolean startWithTextures, Consumer<String> picked) {
        var picker = new UpgradeAssetPicker(host, title, !texturesOnly, texturesOnly || startWithTextures, picked);
        return ModalWindow.open(host, picker.window);
    }

    /**
     * Opens the searchable inventory grid with an item-only filter.
     *
     * @param host   overlay hosting the picker
     * @param title  picker caption
     * @param picked callback receiving the selected item registry ID
     * @return open item picker modal
     */
    static WindowWidget openItems(OverlayLayer host, String title, Consumer<String> picked) {
        var picker = new UpgradeAssetPicker(host, title, false, false, picked);
        return ModalWindow.open(host, picker.window);
    }

    private UpgradeAssetPicker(OverlayLayer host, String caption, boolean showTabs, boolean startWithTextures, Consumer<String> picked) {
        this.picked = picked;
        var panel = new VBox().spacing(4);
        panel.layout(style -> style.fill().padding(8).alignItems(Align.STRETCH));

        var title = new Text(caption);
        title.font(MinecraftFonts.defaultFace(), 10).color(INK);
        title.layout(style -> style.height(13).flexNone());
        panel.addChild(title);
        if (showTabs) {
            var tabs = new HBox().spacing(4);
            tabs.layout(style -> style.height(18).flexNone());
            itemsTab.onClick(event -> showTextures(false));
            texturesTab.onClick(event -> showTextures(true));
            tabs.addChild(itemsTab);
            tabs.addChild(texturesTab);
            panel.addChild(tabs);
        }
        search.placeholder(tr("editor.asset_search"));
        search.layout(style -> style.height(18).flexNone());
        search.onTextChanged(event -> filter());
        panel.addChild(search);

        grid.grid(true).gridCellWidth(AeSlot.SIZE).gridCellHeight(AeSlot.SIZE)
                .overscan(1).offscreenCacheSize(36).scrollStep(48);
        grid.layout(style -> style.expand().minHeight(0).columnGap(2).rowGap(2));
        AeScrollbar.installCompact(grid.verticalScrollBar());
        panel.addChild(grid);
        empty.font(MinecraftFonts.defaultFace(), 9).color(INK);
        empty.layout(style -> style.expand().minHeight(0));
        panel.addChild(empty);

        var footer = new HBox().spacing(4);
        footer.layout(style -> style.height(18).flexNone().justifyContent(Justify.END));
        useId.layout(style -> style.size(88, 18).flexNone());
        useId.onClick(event -> {
            String id = search.text().strip();
            if (textureMode && UpgradeNodeStyle.validTexture(id)) {
                choose(id);
            }
        });
        footer.addChild(useId);
        var close = new AeButton(tr("editor.asset_close"));
        close.layout(style -> style.size(72, 18).flexNone());
        close.onClick(event -> close());
        footer.addChild(close);
        panel.addChild(footer);

        var content = new OverlayLayer(panel);
        tooltip.background(MutableColor.fromHex("#1C2131")).border(INK).borderWidth(1).radius(2);
        content.addOverlay(tooltip);
        window = new WindowWidget("", content) {
            @Override
            public boolean captionless() {
                return true;
            }
        };
        var bounds = host.layoutBounds();
        float width = Math.min(282, bounds.width() - 16);
        float height = Math.min(252, bounds.height() - 16);
        window.position((bounds.width() - width) / 2, (bounds.height() - height) / 2);
        window.layout(style -> style.size(width, height));
        window.fixedModal(true).dockingEnabled(false).closeOnOutsideClick(false);
        window.renderer((draw, widget) -> {
            var frame = widget.layoutBounds();
            draw.nineSlice(PANEL, frame.x(), frame.y(), frame.width(), frame.height(), UNTINTED);
        });
        window.onClosed(event -> {
            tooltip.anchor(null);
            host.removeOverlay(window);
        });
        showTextures(startWithTextures);
    }

    private void close() {
        window.close();
    }

    private void choose(String id) {
        close();
        picked.accept(id);
    }

    private static ToggleButton tab(String key) {
        var button = new ToggleButton(RichText.of(tr(key), MinecraftFonts.defaultFace(), 9));
        AeStyle.styleComboButton(button);
        button.layout(style -> style.expand().minWidth(0).height(18));
        return button;
    }

    private void showTextures(boolean show) {
        textureMode = show;
        itemsTab.silentChecked(!show);
        texturesTab.silentChecked(show);
        if (show && textures == null) {
            textures = Minecraft.getInstance().getResourceManager()
                    .listResources("textures", id -> UpgradeNodeStyle.validTexture(id.toString())).keySet().stream()
                    .map(id -> new Asset(id.toString(), "", null)).sorted(Comparator.comparing(Asset::id)).toList();
        } else if (!show && items == null) {
            items = BuiltInRegistries.ITEM.stream().filter(item -> item != Items.AIR)
                    .map(item -> new Asset(BuiltInRegistries.ITEM.getKey(item).toString(), item.getDescription().getString(), item))
                    .sorted(Comparator.comparing(Asset::id)).toList();
        }
        filter();
    }

    private void filter() {
        var matches = InspectorParameterSearch.compile(search.text());
        var assets = textureMode ? textures : items;
        if (assets == null) {
            return;
        }
        var results = search.text().isBlank() ? assets : assets.stream()
                .filter(asset -> matches.test(asset.id() + " " + asset.name())).toList();
        tooltip.anchor(null).text("");
        grid.itemFactory(index -> new Tile(results.get(index))).itemCount(results.size()).scrollTo(0);
        grid.visibility(results.isEmpty() ? Visibility.COLLAPSED : Visibility.VISIBLE);
        empty.visibility(results.isEmpty() ? Visibility.VISIBLE : Visibility.COLLAPSED);
        useId.visibility(textureMode && UpgradeNodeStyle.validTexture(search.text().strip())
                ? Visibility.VISIBLE : Visibility.COLLAPSED);
    }

    private record Asset(String id, String name, @Nullable Item item) {
    }

    private final class Tile extends AeSlot {
        private final @Nullable MinecraftTextureHandle texture;
        private final ItemStack stack;

        private Tile(Asset asset) {
            super(asset.id());
            stack = asset.item() == null ? ItemStack.EMPTY : new ItemStack(asset.item());
            texture = asset.item() == null ? TreeNodeAssets.nodeTexture(asset.id()) : null;
            onClick(event -> choose(asset.id()));
            on(PointerEnteredEvent.TYPE, event -> {
                if (event.phase() == EventPhase.TARGET) {
                    String caption = asset.name().isEmpty() ? asset.id() : asset.name() + "\n" + asset.id();
                    tooltip.anchor(this).richText(RichText.of(caption, MinecraftFonts.defaultFace(), 8));
                }
            });
        }

        @Override
        protected void renderButtonText(DrawScope draw) {
            var bounds = layoutBounds();
            float size = Math.min(16, Math.min(bounds.width(), bounds.height()) - 2);
            if (size <= 0) {
                return;
            }
            float x = bounds.x() + (bounds.width() - size) / 2;
            float y = bounds.y() + (bounds.height() - size) / 2;
            if (hovered()) {
                draw.rectFilled(x, y, size, size, SLOT_HIGHLIGHT);
            }
            if (texture != null) {
                TreeNodeAssets.drawTexturePreview(draw, texture, x, y, size);
            } else if (!stack.isEmpty()) {
                draw.addCallback(backend -> {
                    if (backend instanceof MinecraftGuiRenderBackend minecraft
                            && !minecraft.renderItemPreviewLazy(stack, x, y, size, 1)) {
                        invalidate(InvalidationFlags.VISUAL);
                    }
                });
            }
        }
    }
}
