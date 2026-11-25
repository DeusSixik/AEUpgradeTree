package dev.sixik.aeupgrade.client.tree;

import static dev.sixik.aeupgrade.client.tree.UpgradeTreeText.tr;
import static dev.sixik.aeupgrade.client.widget.AeStyle.TEXT_COLOR;
import static dev.sixik.aeupgrade.client.widget.AeStyle.text;

import java.util.List;

import dev.sixik.aeupgrade.client.widget.AeButton;
import dev.sixik.aeupgrade.client.widget.AeScrollbar;
import dev.sixik.aeupgrade.client.widget.AeStyle;
import dev.sixik.unigui.api.layout.Align;
import dev.sixik.unigui.api.layout.Justify;
import dev.sixik.unigui.api.layout.Overflow;
import dev.sixik.unigui.api.math.MutableColor;
import dev.sixik.unigui.api.render.NineSlice;
import dev.sixik.unigui.api.render.Paint;
import dev.sixik.unigui.widgets.containers.HBox;
import dev.sixik.unigui.widgets.containers.ScrollView;
import dev.sixik.unigui.widgets.containers.VBox;
import dev.sixik.unigui.widgets.feedback.OverlayLayer;
import dev.sixik.unigui.widgets.feedback.WindowWidget;

/**
 * AE2-styled confirmations for branch choices and refunds.
 */
final class UpgradeTreeConfirmation {
    private static final NineSlice PANEL = NineSlice.of(AeStyle.PANEL, 3);
    private static final Paint UNTINTED = Paint.fill(MutableColor.fromHex("#FFFFFF"));

    private UpgradeTreeConfirmation() {
    }

    static WindowWidget purchase(OverlayLayer host, String node, int cost, List<String> branches, Runnable confirm) {
        String title = tr("confirm.title");
        var panel = panel(title);
        addLine(panel, tr("confirm.purchase", node, cost));
        addLine(panel, tr("confirm.closed_branches"));

        var entries = new VBox();
        entries.spacing(3);
        entries.layout(style -> style.widthPercent(100).padding(3, 6).alignItems(Align.STRETCH));
        for (var branch : branches) {
            addLine(entries, "• " + branch);
        }
        var scroll = new ScrollView(entries);
        scroll.layout(style -> style.expand().minHeight(0).overflowX(Overflow.HIDDEN).overflowY(Overflow.AUTO));
        AeScrollbar.install(scroll);
        panel.addChild(scroll);

        return window(host, title, panel, tr("confirm.accept"), Math.min(105 + 15 * branches.size(), 216), confirm);
    }

    static WindowWidget refund(OverlayLayer host, String title, String operation, int removed, int returned, int lost,
                               Runnable confirm) {
        var panel = panel(title);
        var details = new VBox();
        details.layout(style -> style.widthPercent(100).padding(2).alignItems(Align.STRETCH));
        addLine(details, operation);
        var scroll = new ScrollView(details);
        scroll.layout(style -> style.expand().minHeight(0).overflowX(Overflow.HIDDEN).overflowY(Overflow.AUTO));
        AeScrollbar.install(scroll);
        panel.addChild(scroll);
        var totals = new VBox();
        totals.spacing(3);
        totals.layout(style -> style.flexNone().alignItems(Align.STRETCH));
        addLine(totals, tr("confirm.refund_removed", removed));
        addLine(totals, tr("confirm.refund_returned", returned));
        addLine(totals, tr("confirm.refund_lost", lost));
        panel.addChild(totals);
        return window(host, title, panel, tr("confirm.refund_accept"), 137, confirm);
    }

    private static VBox panel(String title) {
        var panel = new VBox();
        panel.spacing(5);
        panel.layout(style -> style.fill().padding(9).alignItems(Align.STRETCH));
        panel.addChild(text(title, 11, TEXT_COLOR).clipOverflow().layout(style -> style.flexNone()));
        return panel;
    }

    private static void addLine(VBox panel, String line) {
        panel.addChild(text(line, 9, TEXT_COLOR).wrapText().layout(style -> style.minWidth(0).flexNone()));
    }

    private static WindowWidget window(OverlayLayer host, String title, VBox panel, String acceptLabel, float desiredHeight,
                                       Runnable confirm) {
        var window = new WindowWidget(title, panel) {
            @Override
            public boolean captionless() {
                return true;
            }
        };
        var footer = new HBox().spacing(5);
        footer.layout(style -> style.height(18).flexNone().justifyContent(Justify.END));
        var cancel = new AeButton(tr("confirm.cancel"));
        cancel.layout(style -> style.size(65, 18).flexNone());
        cancel.onClick(event -> window.close());
        footer.addChild(cancel);
        var accept = new AeButton(acceptLabel);
        accept.layout(style -> style.size(94, 18).flexNone());
        accept.onClick(event -> {
            window.close();
            confirm.run();
        });
        footer.addChild(accept);
        panel.addChild(footer);

        var bounds = host.layoutBounds();
        float width = Math.min(266, bounds.width() - 16);
        float height = Math.min(desiredHeight, bounds.height() - 16);
        window.position((bounds.width() - width) / 2, (bounds.height() - height) / 2);
        window.layout(style -> style.size(width, height));
        window.fixedModal(true).dockingEnabled(false).closeOnOutsideClick(false);
        window.focusScope(true);
        window.renderer((draw, widget) -> {
            var frame = widget.layoutBounds();
            draw.nineSlice(PANEL, frame.x(), frame.y(), frame.width(), frame.height(), UNTINTED);
        });
        window.onActivated(event -> host.uiContext().focusManager().requestFocus(cancel));
        return window;
    }
}
