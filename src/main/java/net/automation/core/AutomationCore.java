package net.automation.core;

import com.gregtechceu.gtceu.api.GTCEuAPI;
import com.gregtechceu.gtceu.api.data.chemical.material.event.MaterialEvent;
import com.gregtechceu.gtceu.api.data.chemical.material.event.MaterialRegistryEvent;
import com.gregtechceu.gtceu.api.data.chemical.material.event.PostMaterialEvent;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.registry.registrate.GTRegistrate;
import com.gregtechceu.gtceu.api.sound.SoundEntry;

import net.automation.core.network.AutomationNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(AutomationCore.MOD_ID)
@SuppressWarnings("removal")
public class AutomationCore {

    public static final String MOD_ID = "automation_core";
    public static final Logger LOGGER = LogManager.getLogger();
    public static GTRegistrate AUTOMATION_REGISTRATE = GTRegistrate.create(AutomationCore.MOD_ID);

    public AutomationCore() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::clientSetup);

        modEventBus.addListener(this::addMaterialRegistries);
        modEventBus.addListener(this::addMaterials);
        modEventBus.addListener(this::modifyMaterials);

        modEventBus.addGenericListener(GTRecipeType.class, this::registerRecipeTypes);
        modEventBus.addGenericListener(MachineDefinition.class, this::registerMachines);
        modEventBus.addGenericListener(SoundEntry.class, this::registerSounds);

        MinecraftForge.EVENT_BUS.register(this);

        AUTOMATION_REGISTRATE.registerRegistrate();
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(AutomationNetwork::register);
    }

    private void clientSetup(final FMLClientSetupEvent event) {
        LOGGER.info("Hey, we're on Minecraft version {}!", Minecraft.getInstance().getLaunchedVersion());
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MOD_ID, path);
    }

    private void addMaterialRegistries(MaterialRegistryEvent event) {
        GTCEuAPI.materialManager.createRegistry(AutomationCore.MOD_ID);
    }

    private void addMaterials(MaterialEvent event) {
        
    }

    private void modifyMaterials(PostMaterialEvent event) {
        
    }

    private void registerRecipeTypes(GTCEuAPI.RegisterEvent<ResourceLocation, GTRecipeType> event) {
        
    }

    private void registerMachines(GTCEuAPI.RegisterEvent<ResourceLocation, MachineDefinition> event) {
        
    }

    public void registerSounds(GTCEuAPI.RegisterEvent<ResourceLocation, SoundEntry> event) {
        
    }
}
