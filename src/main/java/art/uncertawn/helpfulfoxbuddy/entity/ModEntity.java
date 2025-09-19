package art.uncertawn.helpfulfoxbuddy.entity;

import art.uncertawn.helpfulfoxbuddy.Helpfulfoxbuddy;
import art.uncertawn.helpfulfoxbuddy.entity.HelpfulFox.HelpfulFoxEntity;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.client.render.entity.FoxEntityRenderer;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

public class ModEntity {
    public static final EntityType<HelpfulFoxEntity> FOXY = Registry.register(
            Registries.ENTITY_TYPE,
            Identifier.of(Helpfulfoxbuddy.MOD_ID, "helpful_fox"),
            EntityType.Builder.create(HelpfulFoxEntity::new, SpawnGroup.CREATURE)
                    .dimensions(0.75f, 0.75f)
                    .build(RegistryKey.of(RegistryKeys.ENTITY_TYPE, Identifier.of(Helpfulfoxbuddy.MOD_ID, "helpful_fox")))
    );

    public static void register() {
        System.out.println("ENTITY REGISTER : HELPFUL FOX");
        FabricDefaultAttributeRegistry.register(FOXY, HelpfulFoxEntity.createAttributes());

    }
}
