package dev.sixik.aeupgrade.client.widget;

import dev.sixik.unigui.widgets.feedback.OverlayLayer;
import dev.sixik.unigui.widgets.feedback.WindowWidget;

/**
 * Opens modal overlays while retiring hover on the previous layer.
 */
public final class ModalWindow {
    private ModalWindow() {
    }

    /**
     * Clears the current hover before showing and registering the modal.
     *
     * @param host   overlay that will own the window
     * @param window window to open as a modal
     * @return the opened window
     */
    public static WindowWidget open(OverlayLayer host, WindowWidget window) {
        var context = host.uiContext();
        if (context != null) {
            // Once the modal is registered, its host blocks pointer exit events to the previous layer.
            context.hoverManager().clearHover();
        }
        host.addOverlay(window.openModal());
        return window;
    }
}
