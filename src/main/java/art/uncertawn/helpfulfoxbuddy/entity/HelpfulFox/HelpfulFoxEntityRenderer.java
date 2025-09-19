package art.uncertawn.helpfulfoxbuddy.entity.HelpfulFox;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.entity.AgeableMobEntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.feature.FoxHeldItemFeatureRenderer;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.FoxEntityModel;
import net.minecraft.client.render.entity.state.FoxEntityRenderState;
import net.minecraft.client.render.entity.state.ItemHolderEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.passive.FoxEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;

@Environment(EnvType.CLIENT)
public class HelpfulFoxEntityRenderer extends AgeableMobEntityRenderer<HelpfulFoxEntity, HelpfulFoxEntityRenderState, HelpfulFoxEntityModel> {
    private static final Identifier TEXTURE = Identifier.ofVanilla("textures/entity/fox/fox.png");
    private static final Identifier SLEEPING_TEXTURE = Identifier.ofVanilla("textures/entity/fox/fox_sleep.png");
    private static final Identifier SNOW_TEXTURE = Identifier.ofVanilla("textures/entity/fox/snow_fox.png");
    private static final Identifier SLEEPING_SNOW_TEXTURE = Identifier.ofVanilla("textures/entity/fox/snow_fox_sleep.png");

    public HelpfulFoxEntityRenderer(EntityRendererFactory.Context context) {
        super(context, new HelpfulFoxEntityModel(context.getPart(EntityModelLayers.FOX)), new HelpfulFoxEntityModel(context.getPart(EntityModelLayers.FOX_BABY)), 0.4F);
//        this.addFeature(new FoxHeldItemFeatureRenderer(this));
    }

    @Override
    public Identifier getTexture(HelpfulFoxEntityRenderState state) {
        return TEXTURE;
    }

    protected void setupTransforms(HelpfulFoxEntityRenderState foxEntityRenderState, MatrixStack matrixStack, float f, float g) {
        super.setupTransforms(foxEntityRenderState, matrixStack, f, g);
        if (foxEntityRenderState.chasing || foxEntityRenderState.walking) {
            matrixStack.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-foxEntityRenderState.pitch));
        }

    }

    public Identifier getTexture(FoxEntityRenderState foxEntityRenderState) {
        if (foxEntityRenderState.type == FoxEntity.Variant.RED) {
            return foxEntityRenderState.sleeping ? SLEEPING_TEXTURE : TEXTURE;
        } else {
            return foxEntityRenderState.sleeping ? SLEEPING_SNOW_TEXTURE : SNOW_TEXTURE;
        }
    }

    public HelpfulFoxEntityRenderState createRenderState() {
        return new HelpfulFoxEntityRenderState();
    }

    public void updateRenderState(HelpfulFoxEntity foxEntity, HelpfulFoxEntityRenderState foxEntityRenderState, float f) {
        super.updateRenderState(foxEntity, foxEntityRenderState, f);
        ItemHolderEntityRenderState.update(foxEntity, foxEntityRenderState, this.itemModelResolver);
        foxEntityRenderState.headRoll = foxEntity.getHeadRoll(f);
        foxEntityRenderState.inSneakingPose = foxEntity.isInSneakingPose();
        foxEntityRenderState.bodyRotationHeightOffset = foxEntity.getBodyRotationHeightOffset(f);
        foxEntityRenderState.sleeping = foxEntity.isSleeping();
        foxEntityRenderState.sitting = foxEntity.isSitting();
        foxEntityRenderState.walking = foxEntity.isWalking();
        foxEntityRenderState.chasing = foxEntity.isChasing();
        foxEntityRenderState.type = foxEntity.getVariant();
    }
}

