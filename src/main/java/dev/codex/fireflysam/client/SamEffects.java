package dev.codex.fireflysam.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.codex.fireflysam.FireflySam;
import dev.codex.fireflysam.CombatRules;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import org.joml.Vector3f;

@EventBusSubscriber(modid=FireflySam.ID,value=Dist.CLIENT)
public final class SamEffects {
    private static final DustParticleOptions TEAL=new DustParticleOptions(new Vector3f(.30f,1,.76f),.65f);
    private SamEffects(){}
    @SubscribeEvent
    public static void particles(ClientTickEvent.Post e){
        Minecraft mc=Minecraft.getInstance();if(mc.level==null||mc.isPaused())return;
        long now=mc.level.getGameTime();
        for(var s:SamClientState.PLAYERS.values()){
            if(!s.active())continue;var entity=mc.level.getEntity(s.entityId());if(entity==null||mc.player==null||entity.distanceToSqr(mc.player)>4096)continue;
            var random=mc.level.random;
            if(s.ultimateStart()<0){
                boolean localFirstPerson=entity==mc.player&&mc.options.getCameraType().isFirstPerson();
                if(now%3==0&&!localFirstPerson)mc.level.addParticle(TEAL,entity.getX()+(random.nextFloat()-.5)*1.5,entity.getY()+.75,entity.getZ()+.3,0,.035,0);
            }
            else {
                long age=now-s.ultimateStart();
                for(int i=0;i<2;i++){
                    double angle=random.nextDouble()*Math.PI*2,r=age<24?1.6:4.2;
                    mc.level.addParticle(TEAL,entity.getX()+Math.cos(angle)*r,entity.getY()+random.nextDouble()*2.8,entity.getZ()+Math.sin(angle)*r,Math.cos(angle)*.025,.025,Math.sin(angle)*.025);
                    if(age>=CombatRules.DIVE_START&&age<CombatRules.IMPACT_TICK)mc.level.addParticle(ParticleTypes.SOUL_FIRE_FLAME,entity.getX()+(random.nextDouble()-.5),entity.getY()+1,entity.getZ()+(random.nextDouble()-.5),0,.22,0);
                }
            }
        }
    }
    @SubscribeEvent
    public static void bars(RenderLivingEvent.Post<?,?> e){
        Minecraft mc=Minecraft.getInstance();LivingEntity entity=e.getEntity();
        if(mc.player==null||entity instanceof Player||entity.distanceToSqr(mc.player)>576||mc.options.hideGui)return;
        var s=SamClientState.local();if((s==null||!s.active())&&!mc.player.getMainHandItem().is(FireflySam.SWORD.get()))return;
        var t=SamClientState.TOUGHNESS.get(entity.getId());boolean targeted=mc.hitResult instanceof EntityHitResult hit&&hit.getEntity()==entity;
        if(t==null&&!targeted)return;
        float maximum=t==null?dev.codex.fireflysam.CombatRules.toughness(entity.getMaxHealth(),entity.getArmorValue()):t.maximum();
        boolean broken=t!=null&&t.brokenUntil()>entity.level().getGameTime();float fraction=t==null?1:t.remaining()/maximum;
        PoseStack p=e.getPoseStack();p.pushPose();p.translate(0,entity.getBbHeight()+.48,0);p.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());p.scale(.025f,-.025f,.025f);
        VertexConsumer out=e.getMultiBufferSource().getBuffer(RenderType.entityTranslucentEmissive(SamMesh.WHITE));
        rect(p,out,-23,-2,23,2,0,0xD0112027);if(!broken)rect(p,out,-22,-1,-22+44*fraction,1,-.01f,0xFFE7F0E7);
        else for(int i=0;i<6;i++)rect(p,out,-22+i*8,-.5f,-17+i*8,.5f,-.01f,0xFFFFCA70);
        p.popPose();
    }
    @SubscribeEvent
    public static void world(RenderLevelStageEvent e){
        if(e.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES)return;
        Minecraft mc=Minecraft.getInstance();if(mc.level==null)return;float now=mc.level.getGameTime()+e.getPartialTick().getGameTimeDeltaPartialTick(false);
        PoseStack p=e.getPoseStack();Vec3 cam=e.getCamera().getPosition();
        var buffers=mc.renderBuffers().bufferSource();
        VertexConsumer out=buffers.getBuffer(RenderType.entityTranslucentEmissive(SamMesh.WHITE));
        for(var s:SamClientState.PLAYERS.values())if(s.active()&&s.ultimateStart()>=0){
            float age=now-s.ultimateStart();if(age>=CombatRules.IMPACT_TICK||age<0)continue;
            p.pushPose();p.translate(s.target().x-cam.x,s.target().y+.10-cam.y,s.target().z-cam.z);
            ring(p,out,4.4f,.06f,0xAA64FFE2,0);ring(p,out,3.9f,.025f,0x885CFFB8,0);
            for(int i=0;i<8;i++){p.pushPose();p.mulPose(Axis.YP.rotationDegrees(i*45+now*.7f));rectGround(p,out,3.7f,-.025f,5.0f,.025f,0xB0D5FFD9);p.popPose();}
            if(age>CombatRules.DIVE_START){float width=(age-CombatRules.DIVE_START)/20f*1.6f;beam(p,out,width,25,0x807CFFE2);}
            p.popPose();
        }
        for(var f:SamClientState.FLASHES){
            float age=now-f.born();if(f.kind()!=0||age>60||age<0)continue;
            p.pushPose();p.translate(f.pos().x-cam.x,f.pos().y+.18-cam.y,f.pos().z-cam.z);
            float fade=Math.max(0,1-age/60);int a=(int)(200*fade);
            ring(p,out,1+age*.85f,Math.max(.08f,1.2f-age*.018f),(a<<24)|0x71FFE0,0);
            if(age>7)ring(p,out,1+(age-7)*.65f,.22f,(a<<24)|0xFFE5A7,.06f);
            if(age<30)beam(p,out,1+age*.08f,32-age*.5f,((int)(120*(1-age/30))<<24)|0xB9FFF0);
            if(age<15){p.mulPose(Axis.YP.rotationDegrees(45));beam(p,out,.2f,50,((int)(180*(1-age/15))<<24)|0xFFFFDA);}
            p.popPose();
        }
        buffers.endBatch(RenderType.entityTranslucentEmissive(SamMesh.WHITE));
    }
    private static void ring(PoseStack p,VertexConsumer out,float radius,float thickness,int color,float y){
        for(int i=0;i<96;i++){
            double a=i*Math.PI/48,b=(i+1)*Math.PI/48;float inner=Math.max(0,radius-thickness);
            quad(p,out,new float[]{(float)Math.cos(a)*inner,y,(float)Math.sin(a)*inner,(float)Math.cos(a)*radius,y,(float)Math.sin(a)*radius,
                    (float)Math.cos(b)*radius,y,(float)Math.sin(b)*radius,(float)Math.cos(b)*inner,y,(float)Math.sin(b)*inner},color);
        }
    }
    private static void beam(PoseStack p,VertexConsumer out,float radius,float height,int color){
        for(int i=0;i<16;i++){
            double a=i*Math.PI/8,b=(i+1)*Math.PI/8;
            quad(p,out,new float[]{(float)Math.cos(a)*radius,0,(float)Math.sin(a)*radius,(float)Math.cos(b)*radius,0,(float)Math.sin(b)*radius,
                    (float)Math.cos(b)*radius*.65f,height,(float)Math.sin(b)*radius*.65f,(float)Math.cos(a)*radius*.65f,height,(float)Math.sin(a)*radius*.65f},color);
        }
    }
    private static void rectGround(PoseStack p,VertexConsumer o,float x,float z,float x2,float z2,int color){quad(p,o,new float[]{x,0,z,x2,0,z,x2,0,z2,x,0,z2},color);}
    private static void rect(PoseStack p,VertexConsumer o,float x,float y,float x2,float y2,float z,int color){quad(p,o,new float[]{x,y,z,x2,y,z,x2,y2,z,x,y2,z},color);}
    private static void quad(PoseStack p,VertexConsumer out,float[]v,int color){
        for(int i=0;i<4;i++)out.addVertex(p.last(),v[3*i],v[3*i+1],v[3*i+2]).setColor(color).setUv(.5f,.5f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(p.last(),0,1,0);
    }
}
