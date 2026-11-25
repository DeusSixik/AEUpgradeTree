package dev.sixik.aeupgrade.client.tree.editor;

import static dev.sixik.aeupgrade.client.tree.UpgradeTreeText.tr;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

import dev.sixik.aeupgrade.client.tree.map.TreeNodeAssets;
import dev.sixik.aeupgrade.client.widget.AeButton;
import dev.sixik.aeupgrade.tree.UpgradeNodeStyle;
import dev.sixik.unigui.api.event.EventPhase;
import dev.sixik.unigui.api.event.PointerEnteredEvent;
import dev.sixik.unigui.api.layout.Align;
import dev.sixik.unigui.api.layout.EdgeInsets;
import dev.sixik.unigui.api.math.MutableColor;
import dev.sixik.unigui.api.render.DrawScope;
import dev.sixik.unigui.api.render.TextureHandle;
import dev.sixik.unigui.api.text.RichText;
import dev.sixik.unigui.backend.minecraft_impl.MinecraftFonts;
import dev.sixik.unigui.impl.text.TextEngine;
import dev.sixik.unigui.widgets.containers.HBox;
import dev.sixik.unigui.widgets.containers.LinearBox;
import dev.sixik.unigui.widgets.containers.VBox;
import dev.sixik.unigui.widgets.core.Orientation;
import dev.sixik.unigui.widgets.display.Text;
import dev.sixik.unigui.widgets.feedback.OverlayLayer;
import dev.sixik.unigui.widgets.feedback.Tooltip;
import org.jetbrains.annotations.Nullable;

/**
 * Inspector list of the node's background textures in paint order.
 */
final class UpgradeBackgroundList extends LinearBox {
    private static final int ROW_HEIGHT = 19;
    private static final MutableColor INK = MutableColor.fromHex("#413F54");
    private final VBox rows = new VBox();
    private final AeButton add = new AeButton(tr("editor.background_add"));
    private final Tooltip tooltip = new Tooltip().followPointer(true).offset(10, 12).maxWidth(240);
    private final IntConsumer replace;
    private final Consumer<List<String>> apply;

    UpgradeBackgroundList(OverlayLayer overlay, InspectorHelp help, Runnable append, IntConsumer replace,
                          Consumer<List<String>> apply) {
        super(Orientation.VERTICAL);
        this.replace = replace;
        this.apply = apply;
        spacing(3);
        layout(style -> style.flexNone().alignItems(Align.STRETCH));
        var header = new HBox().spacing(4);
        header.layout(style -> style.height(ROW_HEIGHT).flexNone().alignItems(Align.CENTER));
        var caption = new Text(tr("editor.backgrounds")).font(MinecraftFonts.defaultFace(), 9).color(INK).clipOverflow();
        caption.layout(style -> style.expand().minWidth(0));
        add.layout(style -> style.width(94).height(ROW_HEIGHT).flexNone());
        add.onClick(event -> append.run());
        help.attach(caption, "backgrounds");
        help.attach(add, "backgrounds");
        header.addChild(caption);
        header.addChild(add);
        addChild(header);

        rows.spacing(2).layout(style -> style.padding(new EdgeInsets(5, 0, 0, 0)).alignItems(Align.STRETCH));
        addChild(rows);
        tooltip.background(MutableColor.fromHex("#1C2131")).border(INK).borderWidth(1).radius(2);
        overlay.addOverlay(tooltip);
    }

    void showLayers(List<String> layers) {
        tooltip.anchor(null);
        rows.clearChildren();
        add.enabled(layers.size() < UpgradeNodeStyle.MAX_BACKGROUNDS);
        for (int index = 0; index < layers.size(); index++) {
            rows.addChild(layerRow(layers, index));
        }
    }

    private HBox layerRow(List<String> layers, int index) {
        String id = layers.get(index);
        var row = new HBox();
        row.spacing(2).layout(style -> style.height(ROW_HEIGHT).flexNone().alignItems(Align.CENTER));
        var texture = new LayerButton(id);
        texture.layout(style -> style.expand().minWidth(0).height(ROW_HEIGHT));
        texture.onClick(event -> replace.accept(index));
        hint(texture, id + "\n" + tr("editor.background_replace"));
        row.addChild(texture);
        row.addChild(action("↑", "editor.background_up", index > 0, () -> move(layers, index, index - 1)));
        row.addChild(action("↓", "editor.background_down", index + 1 < layers.size(), () -> move(layers, index, index + 1)));
        row.addChild(action("×", "editor.background_remove", true, () -> {
            var updated = new ArrayList<>(layers);
            updated.remove(index);
            apply.accept(updated);
        }));
        return row;
    }

    private void move(List<String> layers, int index, int target) {
        var updated = new ArrayList<>(layers);
        Collections.swap(updated, index, target);
        apply.accept(updated);
    }

    private AeButton action(String icon, String key, boolean enabled, Runnable callback) {
        var button = new AeButton(icon);
        button.layout(style -> style.size(16, ROW_HEIGHT).flexNone());
        button.enabled(enabled);
        button.onClick(event -> callback.run());
        hint(button, tr(key));
        return button;
    }

    private void hint(AeButton button, String caption) {
        button.on(PointerEnteredEvent.TYPE, event -> {
            if (event.phase() == EventPhase.TARGET) {
                tooltip.anchor(button).richText(RichText.of(caption, MinecraftFonts.defaultFace(), 8));
            }
        });
    }

    private static final class LayerButton extends AeButton {
        private final @Nullable TextureHandle texture;

        private LayerButton(String id) {
            super(id.substring(id.lastIndexOf('/') + 1));
            texture = TreeNodeAssets.nodeTexture(id);
        }

        @Override
        protected void renderButtonText(DrawScope draw) {
            var bounds = layoutBounds();
            if (texture != null && bounds.width() >= 22) {
                TreeNodeAssets.drawTexturePreview(draw, texture, bounds.x() + 4, bounds.y() + 2, 14);
            }
            float x = bounds.x() + 22;
            float width = bounds.right() - x - 4;
            var label = richText();
            float height = TextEngine.measureTextHeight(draw.context(), label);
            renderLabel(draw, label, x, bounds.y(), width, bounds.height(), x, bounds.y() + (bounds.height() - height) / 2,
                    width, height, textColor());
        }
    }
}
