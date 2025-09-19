package art.uncertawn.helpfulfoxbuddy.entity.HelpfulFox;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.entity.state.ItemHolderEntityRenderState;
import net.minecraft.entity.passive.FoxEntity;

@Environment(EnvType.CLIENT)
public class HelpfulFoxEntityRenderState extends ItemHolderEntityRenderState {
    public float headRoll;
    public float bodyRotationHeightOffset;
    public boolean inSneakingPose;
    public boolean sleeping;
    public boolean sitting;
    public boolean walking;
    public boolean chasing;
    public HelpfulFoxEntity.Variant type;

    public HelpfulFoxEntityRenderState() {
        this.type = HelpfulFoxEntity.Variant.DEFAULT;
    }
}
