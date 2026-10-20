package dev.codex.fireflysam.client;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public final class FireflyItemRenderer extends BlockEntityWithoutLevelRenderer {
    public FireflyItemRenderer(){super(Minecraft.getInstance().getBlockEntityRenderDispatcher(),Minecraft.getInstance().getEntityModels());}
    @Override
    public void renderByItem(ItemStack stack,ItemDisplayContext context,PoseStack poses,MultiBufferSource buffers,int light,int overlay) {
        poses.pushPose();poses.translate(.5,-.08,.5);poses.scale(.67f,.67f,.67f);
        SamMesh.get("firefly_blade").render(poses,buffers,light,Map.of(),1);
        poses.popPose();
    }
}
