package dev.codex.fireflysam.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.codex.fireflysam.FireflySam;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/** Temporary cinematic atmosphere. World geometry retains its normal depth. */
@EventBusSubscriber(modid=FireflySam.ID,value=Dist.CLIENT)
public final class SamSky {
    private static final RenderType SKY=RenderType.entityTranslucentEmissive(FireflySam.id("textures/effect/combustion_space.png"));
    private SamSky(){}
    private static float intensity(float partial){
        var mc=Minecraft.getInstance();var state=SamClientState.local();
        if(mc.level==null||state==null||state.ultimateStart()<0)return 0;
        float age=mc.level.getGameTime()+partial-state.ultimateStart();
        return SamAnimation.ease((age-12)/18)*(1-SamAnimation.ease((age-106)/16));
    }
    @SubscribeEvent public static void fog(ViewportEvent.ComputeFogColor e){
        float k=intensity((float)e.getPartialTick());
        e.setRed(Mth.lerp(k,e.getRed(),.004f));e.setGreen(Mth.lerp(k,e.getGreen(),.035f));e.setBlue(Mth.lerp(k,e.getBlue(),.055f));
    }
    @SubscribeEvent public static void sky(RenderLevelStageEvent e){
        if(e.getStage()!=RenderLevelStageEvent.Stage.AFTER_SKY)return;
        float alpha=intensity(e.getPartialTick().getGameTimeDeltaPartialTick(false));if(alpha<.002)return;
        var mc=Minecraft.getInstance();var buffers=mc.renderBuffers().bufferSource();VertexConsumer out=buffers.getBuffer(SKY);
        PoseStack p=e.getPoseStack();p.pushPose();float r=32;
        face(p,out,new float[]{-r,-r,-r,r,-r,-r,r,r,-r,-r,r,-r},alpha);
        face(p,out,new float[]{r,-r,r,-r,-r,r,-r,r,r,r,r,r},alpha);
        face(p,out,new float[]{-r,-r,r,-r,-r,-r,-r,r,-r,-r,r,r},alpha);
        face(p,out,new float[]{r,-r,-r,r,-r,r,r,r,r,r,r,-r},alpha);
        face(p,out,new float[]{-r,r,-r,r,r,-r,r,r,r,-r,r,r},alpha);
        face(p,out,new float[]{-r,-r,r,r,-r,r,r,-r,-r,-r,-r,-r},alpha);
        p.popPose();
        float fogStart=RenderSystem.getShaderFogStart(),fogEnd=RenderSystem.getShaderFogEnd();
        RenderSystem.setShaderFogStart(10000);RenderSystem.setShaderFogEnd(10001);
        try{buffers.endBatch(SKY);}finally{RenderSystem.setShaderFogStart(fogStart);RenderSystem.setShaderFogEnd(fogEnd);}
    }
    private static void face(PoseStack p,VertexConsumer out,float[] v,float alpha){
        for(int i=0;i<4;i++)out.addVertex(p.last(),v[i*3],v[i*3+1],v[i*3+2]).setColor(1f,1f,1f,alpha)
            .setUv(i==1||i==2?1:0,i<2?1:0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(p.last(),0,1,0);
    }
}
