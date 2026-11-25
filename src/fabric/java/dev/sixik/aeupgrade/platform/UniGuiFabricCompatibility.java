package dev.sixik.aeupgrade.platform;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;
import org.objectweb.asm.commons.Remapper;
import org.spongepowered.asm.bridge.RemapperAdapter;
import org.spongepowered.asm.mixin.MixinEnvironment;

/** Repairs the tooltip shadow in UniGUI's published Fabric 1.21.1 build. */
public final class UniGuiFabricCompatibility implements PreLaunchEntrypoint {
    /** Minecraft intermediary name of GuiGraphics. */
    private static final String GRAPHICS = "net/minecraft/class_332";
    /** Intermediary signature used by UniGUI's tooltip shadow. */
    private static final String TOOLTIP_DESCRIPTOR = "(Lnet/minecraft/class_327;Ljava/util/List;IILnet/minecraft/class_8000;)V";

    /** Installs the shadow mapping before Mixin transforms GuiGraphics. */
    @Override
    public void onPreLaunch() {
        var loader = FabricLoader.getInstance();
        if (loader.getEnvironmentType() != EnvType.CLIENT || loader.isDevelopmentEnvironment()
                || loader.getModContainer("unigui").stream()
                        .noneMatch(mod -> mod.getMetadata().getVersion().getFriendlyString().equals("1.6.4"))) {
            return;
        }
        MixinEnvironment.getDefaultEnvironment().getRemappers().add(new RemapperAdapter(new Remapper() {
            /** {@inheritDoc} */
            @Override
            public String mapMethodName(String owner, String name, String descriptor) {
                if (GRAPHICS.equals(owner) && "renderTooltipInternal".equals(name) && TOOLTIP_DESCRIPTOR.equals(descriptor)) {
                    return "method_51435";
                }
                return name;
            }
        }) { });
    }
}
