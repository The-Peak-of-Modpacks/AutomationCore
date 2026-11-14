package net.automation.core;

import com.gregtechceu.gtceu.api.addon.GTAddon;
import com.gregtechceu.gtceu.api.addon.IGTAddon;
import com.gregtechceu.gtceu.api.registry.registrate.GTRegistrate;

import net.minecraft.data.recipes.FinishedRecipe;

import java.util.function.Consumer;

@SuppressWarnings("unused")
@GTAddon
public class AutomationGTAddon implements IGTAddon {

    @Override
    public GTRegistrate getRegistrate() {
        return AutomationCore.AUTOMATION_REGISTRATE;
    }

    @Override
    public void initializeAddon() {}

    @Override
    public String addonModId() {
        return AutomationCore.MOD_ID;
    }

    @Override
    public void registerTagPrefixes() {
        
    }

    @Override
    public void addRecipes(Consumer<FinishedRecipe> provider) {
        
    }

    @Override
    public void registerElements() {
        
    }

}
