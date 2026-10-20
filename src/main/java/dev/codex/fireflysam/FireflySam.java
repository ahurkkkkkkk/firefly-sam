package dev.codex.fireflysam;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(FireflySam.ID)
public final class FireflySam {
    public static final String ID = "firefly_sam";
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ID);
    public static final DeferredItem<FireflySword> SWORD = ITEMS.register("firefly_blade", () -> new FireflySword(new Item.Properties().fireResistant()));
    public static final System.Logger LOGGER = System.getLogger(ID);
    public static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath(ID, path); }

    public FireflySam(IEventBus bus, ModContainer container) {
        ITEMS.register(bus);
        bus.addListener(SamNetwork::register);
        bus.addListener((BuildCreativeModeTabContentsEvent e) -> {
            if (e.getTabKey() == CreativeModeTabs.COMBAT) e.accept(SWORD.get());
        });
        container.registerConfig(ModConfig.Type.COMMON, SamConfig.SPEC);
        NeoForge.EVENT_BUS.register(SamCombat.class);
        NeoForge.EVENT_BUS.register(SamCommands.class);
        LOGGER.log(System.Logger.Level.INFO, "Firefly / SAM loaded: unlimited SAM mode, ten-hit charge, G Deathstar Overload");
    }
}
