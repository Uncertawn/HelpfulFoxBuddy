package art.uncertawn.helpfulfoxbuddy;

import art.uncertawn.helpfulfoxbuddy.entity.ModEntity;
import net.fabricmc.api.ModInitializer;

public class Helpfulfoxbuddy implements ModInitializer {
    public static String MOD_ID = "helpfulfoxbuddy";

    @Override
    public void onInitialize() {
        ModEntity.register();
    }
}
