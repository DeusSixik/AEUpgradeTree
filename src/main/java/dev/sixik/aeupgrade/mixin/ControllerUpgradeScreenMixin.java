package dev.sixik.aeupgrade.mixin;

import java.util.List;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.me.networktool.NetworkStatusScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.AE2Button;
import appeng.menu.me.networktool.MachineGroup;
import appeng.menu.me.networktool.NetworkStatusMenu;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.sixik.aeupgrade.client.controller.ControllerMachineTooltip;
import dev.sixik.aeupgrade.client.controller.ControllerOverviewPanel;
import dev.sixik.aeupgrade.tree.ControllerUpgradeMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Integrates upgrades and diagnostics into the native ME Controller screen.
 */
@Mixin(value = NetworkStatusScreen.class, remap = false)
public abstract class ControllerUpgradeScreenMixin extends AEBaseScreen<NetworkStatusMenu> {
    /**
     * Existing footer button, positioned by AE2's current screen style.
     */
    @Shadow
    @Final
    private Button exportGridButton;

    /**
     * Scrollable limit list for the current controller.
     */
    @Unique
    private final ControllerOverviewPanel aeupgrade$panel = new ControllerOverviewPanel();

    /**
     * Mirrors AEBaseScreen's constructor for the inherited layout and widget methods.
     *
     * @param menu      AE2 network status menu
     * @param inventory player inventory
     * @param title     native AE2 window title
     * @param style     AE2's network status screen style
     */
    protected ControllerUpgradeScreenMixin(NetworkStatusMenu menu, Inventory inventory, Component title, ScreenStyle style) {
        super(menu, inventory, title, style);
    }

    /**
     * Adds the button after AE2 positions its footer, including when the window is resized.
     */
    @Override
    protected void init() {
        super.init();
        if (menu.getType() == NetworkStatusMenu.CONTROLLER_TYPE) {
            int x = exportGridButton.getX() + exportGridButton.getWidth() + 4;
            int availableWidth = getGuiLeft() + imageWidth - x;
            var button = new AE2Button(x, exportGridButton.getY(), availableWidth, exportGridButton.getHeight(),
                    Component.translatable("aeupgrade.tree.open"),
                    clicked -> ((ControllerUpgradeMenu) menu).aeupgrade$openTree());
            addRenderableWidget(button);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (menu.getType() == NetworkStatusMenu.CONTROLLER_TYPE) {
            int x = getGuiLeft() + imageWidth + ControllerOverviewPanel.GAP;
            int panelWidth = Math.min(ControllerOverviewPanel.WIDTH, width - x - ControllerOverviewPanel.GAP);
            aeupgrade$panel.update(((ControllerUpgradeMenu) menu).aeupgrade$overview().limits(), x, getGuiTop(), panelWidth);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
        aeupgrade$panel.draw(graphics, font, mouseX, mouseY);
    }

    /**
     * Extends the hovered AE2 machine's own tooltip with its effective settings.
     *
     * @param machine  the AE2 machine group currently under the pointer
     * @param original original power-generation getter
     * @param tooltip  native tooltip lines for the group
     * @return AE2's unchanged generation capacity in AE/t
     */
    @WrapOperation(method = "drawFG", at = @At(value = "INVOKE", ordinal = 0,
            target = "Lappeng/menu/me/networktool/MachineGroup;getPowerGenerationCapacity()D"))
    private double aeupgrade$appendMachineDetails(MachineGroup machine, Operation<Double> original,
                                                  @Local(ordinal = 0) List<Component> tooltip) {
        if (menu.getType() == NetworkStatusMenu.CONTROLLER_TYPE) {
            ControllerMachineTooltip.append(((ControllerUpgradeMenu) menu).aeupgrade$overview(), machine, tooltip);
        }
        return original.call(machine);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Rect2i> getExclusionZones() {
        var zones = super.getExclusionZones();
        var bounds = aeupgrade$panel.bounds();
        if (bounds != null) {
            zones.add(new Rect2i(bounds.getX(), bounds.getY(), bounds.getWidth(), bounds.getHeight()));
        }
        return zones;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void containerTick() {
        super.containerTick();
        aeupgrade$panel.tick();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean mouseScrolled(double x, double y, double deltaX, double deltaY) {
        return aeupgrade$panel.scroll(x, y, deltaY) || super.mouseScrolled(x, y, deltaX, deltaY);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean mouseClicked(double x, double y, int button) {
        return aeupgrade$panel.mouseClicked(x, y, button) || super.mouseClicked(x, y, button);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean mouseDragged(double x, double y, int button, double deltaX, double deltaY) {
        return aeupgrade$panel.mouseDragged(x, y, button) || super.mouseDragged(x, y, button, deltaX, deltaY);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean mouseReleased(double x, double y, int button) {
        return aeupgrade$panel.mouseReleased(button) || super.mouseReleased(x, y, button);
    }
}
