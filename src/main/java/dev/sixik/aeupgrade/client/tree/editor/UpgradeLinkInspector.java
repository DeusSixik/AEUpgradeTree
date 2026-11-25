package dev.sixik.aeupgrade.client.tree.editor;

import static dev.sixik.aeupgrade.client.tree.UpgradeTreeText.tr;
import static dev.sixik.aeupgrade.client.tree.editor.InspectorWidgets.control;
import static dev.sixik.aeupgrade.client.tree.editor.InspectorWidgets.coordinates;
import static dev.sixik.aeupgrade.client.tree.editor.InspectorWidgets.decimal;
import static dev.sixik.aeupgrade.client.tree.editor.InspectorWidgets.field;
import static dev.sixik.aeupgrade.client.tree.editor.InspectorWidgets.formatDecimal;
import static dev.sixik.aeupgrade.client.tree.editor.InspectorWidgets.parameterLabel;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import java.util.function.Predicate;

import dev.sixik.aeupgrade.client.tree.editor.InspectorGroup.Parameter;
import dev.sixik.aeupgrade.client.tree.editor.UpgradeEditorCanvas.LinkChange;
import dev.sixik.aeupgrade.client.tree.editor.UpgradeEditorCanvas.LinkId;
import dev.sixik.aeupgrade.client.tree.editor.UpgradeEditorCanvas.LinkSelection;
import dev.sixik.aeupgrade.client.widget.AeButton;
import dev.sixik.aeupgrade.client.widget.AeStyle;
import dev.sixik.aeupgrade.tree.UpgradeCatalog;
import dev.sixik.aeupgrade.tree.UpgradeLinkRoute;
import dev.sixik.aeupgrade.tree.UpgradeLinkRoute.Point;
import dev.sixik.aeupgrade.tree.UpgradeLinkRoute.Style;
import dev.sixik.aeupgrade.tree.UpgradeNodeStyle.Shape;
import dev.sixik.unigui.api.event.FocusGainedEvent;
import dev.sixik.unigui.api.event.FocusLostEvent;
import dev.sixik.unigui.api.layout.Align;
import dev.sixik.unigui.api.widget.Visibility;
import dev.sixik.unigui.widgets.containers.HBox;
import dev.sixik.unigui.widgets.containers.VBox;
import dev.sixik.unigui.widgets.feedback.OverlayLayer;
import dev.sixik.unigui.widgets.interaction.ComboBox;
import dev.sixik.unigui.widgets.interaction.TextField;
import org.jetbrains.annotations.Nullable;

/**
 * Shape, ordered waypoints, and live coordinate preview for the selected edge.
 */
final class UpgradeLinkInspector {
    private final ComboBox routeShape = new ComboBox();
    private final InspectorGroup appearance = new InspectorGroup("link_route", true, null);
    private final InspectorGroup waypoints = new InspectorGroup("link_points", true, null);
    private final AeButton add = wideButton("link_add_point", this::appendPoint);
    private final AeButton reset = wideButton("link_reset", this::reset);
    private final List<PointRow> rows = new ArrayList<>();
    private @Nullable LinkId link;
    private @Nullable UpgradeLinkRoute route;
    private boolean loading;

    private final UpgradeEditorCanvas canvas;
    private final InspectorHelp help;
    private final BooleanSupplier pending;
    private final BooleanSupplier commitFields;
    private final IntFunction<Point> mapPoint;
    private final Consumer<LinkChange> applyLinkChange;
    private final Consumer<LinkSelection> removeLinkPoint;
    private final Consumer<String> feedback;

    UpgradeLinkInspector(OverlayLayer overlay, InspectorHelp help, VBox properties, UpgradeEditorCanvas canvas, BooleanSupplier pending,
                         BooleanSupplier commitFields, IntFunction<Point> mapPoint, Consumer<LinkChange> applyLinkChange,
                         Consumer<LinkSelection> removeLinkPoint, Consumer<String> feedback) {
        this.canvas = canvas;
        this.help = help;
        this.pending = pending;
        this.commitFields = commitFields;
        this.mapPoint = mapPoint;
        this.applyLinkChange = applyLinkChange;
        this.removeLinkPoint = removeLinkPoint;
        this.feedback = feedback;
        routeShape.useOverlay(overlay).items(Arrays.stream(Style.values())
                .map(style -> tr("editor." + linkStyleKey(style))).toList());
        AeStyle.styleComboBox(routeShape);
        routeShape.onSelectionChanged(event -> selectShape());
        appearance.add(new Parameter(help.row("shape", "link_shape", routeShape),
                () -> parameterLabel("shape") + " " + String.join(" ", routeShape.items())));
        help.attach(reset, "link_reset");
        help.attach(add, "link_add_point");
        appearance.add(new Parameter(reset, () -> parameterLabel("link_reset")));
        waypoints.add(new Parameter(add, () -> parameterLabel("link_add_point")));
        properties.addChild(appearance.widget());
        properties.addChild(waypoints.widget());
        clear();
    }

    @Nullable LinkId link() {
        return link;
    }

    boolean dropdownOpen() {
        return routeShape.opened();
    }

    void show(LinkId link, UpgradeLinkRoute route) {
        loading = true;
        try {
            if (!link.equals(this.link) || rows.size() != route.points().size()) {
                removeRows();
                for (int i = 0; i < route.points().size(); i++) {
                    var row = new PointRow(i, route.points().size());
                    rows.add(row);
                    waypoints.add(row.parameter);
                }
            }
            this.link = link;
            this.route = route;
            for (int i = 0; i < rows.size(); i++) {
                rows.get(i).show(route.points().get(i));
            }
            routeShape.silentSelectedIndex(route.style().ordinal());
            add.enabled(rows.size() < UpgradeLinkRoute.MAX_POINTS);
            reset.enabled(route.style() != Style.POLYLINE || !rows.isEmpty());
            canvas.previewLinkParameters(null);
        } finally {
            loading = false;
        }
    }

    void clear() {
        link = null;
        route = null;
        routeShape.close();
        removeRows();
        appearance.widget().visibility(Visibility.COLLAPSED);
        waypoints.widget().visibility(Visibility.COLLAPSED);
    }

    private void removeRows() {
        for (var row : rows) {
            row.parameter.widget().visibility(Visibility.COLLAPSED);
            waypoints.remove(row.parameter);
        }
        rows.clear();
    }

    boolean filter(Predicate<String> matches, boolean searching) {
        if (link == null) {
            return false;
        }
        boolean shapeFound = appearance.filter(matches, searching, "");
        return waypoints.filter(matches, searching, "") || shapeFound;
    }

    boolean isCoordinate(@Nullable Object focused) {
        return rows.stream().anyMatch(row -> focused == row.x || focused == row.y);
    }

    private UpgradeLinkRoute readCoordinates() {
        var points = rows.stream().map(row -> new Point(decimal(row.x), decimal(row.y))).toList();
        return new UpgradeLinkRoute(route.source(), route.style(), points);
    }

    private void previewCoordinates() {
        if (route == null || loading || pending.getAsBoolean()) {
            return;
        }
        try {
            var edited = readCoordinates();
            canvas.previewLinkParameters(edited.equals(route) ? null : edited);
        } catch (IllegalArgumentException incomplete) {
            canvas.previewLinkParameters(null);
        }
    }

    boolean commitCoordinates() {
        var selectedLink = canvas.selectedLink();
        if (route == null || loading || selectedLink == null || !selectedLink.link().equals(link)) {
            return true;
        }
        try {
            var edited = readCoordinates();
            if (!edited.equals(route)) {
                applyLinkChange.accept(new LinkChange(selectedLink, edited));
            }
            return true;
        } catch (IllegalArgumentException invalid) {
            feedback.accept(tr("editor.position_error", UpgradeCatalog.MAX_COORDINATE));
            return false;
        }
    }

    private void selectShape() {
        int index = routeShape.selectedIndex();
        if (route == null || loading || index < 0) {
            return;
        }
        if (commitFields.getAsBoolean()) {
            applyLinkChange.accept(new LinkChange(canvas.selectedLink(),
                    new UpgradeLinkRoute(route.source(), Style.values()[index], route.points())));
        } else {
            routeShape.silentSelectedIndex(route.style().ordinal());
        }
    }

    private void appendPoint() {
        if (route == null || !commitFields.getAsBoolean() || route.points().size() >= UpgradeLinkRoute.MAX_POINTS) {
            return;
        }
        var points = new ArrayList<>(route.points());
        var from = points.isEmpty() ? mapPoint.apply(link.source()) : points.getLast();
        var end = mapPoint.apply(link.target());
        float x = (from.x() + end.x()) / 2;
        float y = route.style().orthogonal() && from.x() != end.x() ? from.y() : (from.y() + end.y()) / 2;
        points.add(new Point(x, y));
        applyPoints(points, points.size() - 1);
    }

    private void movePoint(int index, int destination) {
        if (route == null || !commitFields.getAsBoolean()) {
            return;
        }
        var points = new ArrayList<>(route.points());
        Collections.swap(points, index, destination);
        applyPoints(points, destination);
    }

    private void applyPoints(List<Point> points, int selectedPoint) {
        applyLinkChange.accept(new LinkChange(new LinkSelection(link, selectedPoint),
                new UpgradeLinkRoute(route.source(), route.style(), points)));
    }

    private void reset() {
        if (link != null && commitFields.getAsBoolean()) {
            applyLinkChange.accept(new LinkChange(new LinkSelection(link, -1), null));
        }
    }

    private AeButton wideButton(String key, Runnable callback) {
        var button = control(key, 100, callback);
        button.layout(style -> style.widthPercent(100).height(18).flexNone());
        return button;
    }

    private final class PointRow {
        private final TextField x = field(16);
        private final TextField y = field(16);
        private final Parameter parameter;

        PointRow(int index, int size) {
            String name = tr("editor.link_point", index + 1);
            var widget = new VBox().spacing(3);
            widget.backgroundTexture(AeStyle.PANEL).backgroundVisible(true).sliceBorders(3);
            widget.layout(style -> style.alignItems(Align.STRETCH).padding(4));
            var header = new HBox().spacing(2);
            header.layout(style -> style.height(18).flexNone().alignItems(Align.CENTER));
            var pick = new AeButton(name);
            pick.layout(style -> style.expand().minWidth(0).height(18));
            pick.onClick(event -> select(index));
            help.attach(pick, "link_point");
            header.addChild(pick);
            header.addChild(action("↑", "link_point_up", index > 0, () -> movePoint(index, index - 1)));
            header.addChild(action("↓", "link_point_down", index + 1 < size, () -> movePoint(index, index + 1)));
            header.addChild(action("×", "link_remove_point", true, () -> removeLinkPoint.accept(new LinkSelection(link, index))));
            widget.addChild(header);
            widget.addChild(coordinates(x, y));
            x.placeholder("X");
            y.placeholder("Y");
            help.attach(x, "link_point_x");
            help.attach(y, "link_point_y");
            for (var field : List.of(x, y)) {
                field.on(FocusGainedEvent.TYPE, event -> select(index));
                field.on(FocusLostEvent.TYPE, event -> {
                    if (!loading) {
                        commitFields.getAsBoolean();
                    }
                });
                field.onTextChanged(event -> previewCoordinates());
            }
            parameter = new Parameter(widget, () -> name + " " + parameterLabel("position")
                    + " X Y " + parameterLabel("link_remove_point"));
        }

        @Nullable LinkId link() {
            return link;
        }

        boolean dropdownOpen() {
            return routeShape.opened();
        }

        void show(Point point) {
            x.text(formatDecimal(point.x()));
            y.text(formatDecimal(point.y()));
        }

        private void select(int index) {
            if (link != null && !loading && !pending.getAsBoolean()) {
                canvas.highlightLink(new LinkSelection(link, index));
            }
        }

        private AeButton action(String caption, String helpKey, boolean enabled, Runnable callback) {
            var button = new AeButton(caption);
            button.layout(style -> style.size(18, 18).flexNone());
            button.enabled(enabled);
            button.onClick(event -> callback.run());
            help.attach(button, helpKey);
            return button;
        }
    }

    static String linkStyleKey(Style style) {
        return "link_style." + style.name().toLowerCase(Locale.ROOT);
    }

}
