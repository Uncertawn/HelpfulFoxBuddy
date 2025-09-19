package art.uncertawn.helpfulfoxbuddy.client;

import art.uncertawn.helpfulfoxbuddy.entity.HelpfulFox.HelpfulFoxEntityRenderer;
import art.uncertawn.helpfulfoxbuddy.entity.ModEntity;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

public class HelpfulfoxbuddyClientInitializer implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(ModEntity.FOXY, HelpfulFoxEntityRenderer::new);


//        EntityRendererRegistry.register(ModEntity.FOXY, context ->
//                new FoxEntityRenderer(context) {
//                    // You can override methods here if needed
//                });
    }
}