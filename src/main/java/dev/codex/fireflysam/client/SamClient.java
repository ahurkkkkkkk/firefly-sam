package dev.codex.fireflysam.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.codex.fireflysam.FireflySam;
import dev.codex.fireflysam.CombatRules;
import dev.codex.fireflysam.SamNetwork;
import java.util.Map;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid=FireflySam.ID,value=Dist.CLIENT,bus=EventBusSubscriber.Bus.GAME)
public final class SamClient {
    public static final KeyMapping TRANSFORM=new KeyMapping("key.firefly_sam.transform",InputConstants.Type.KEYSYM,GLFW.GLFW_KEY_R,"key.categories.firefly_sam");
    public static final KeyMapping ULTIMATE=new KeyMapping("key.firefly_sam.ultimate",InputConstants.Type.KEYSYM,GLFW.GLFW_KEY_G,"key.categories.firefly_sam");
    private SamClient() {}
    @EventBusSubscriber(modid=FireflySam.ID,value=Dist.CLIENT,bus=EventBusSubscriber.Bus.MOD)
    public static final class ModEvents {
        @SubscribeEvent public static void keys(RegisterKeyMappingsEvent e){e.register(TRANSFORM);e.register(ULTIMATE);}
        @SubscribeEvent public static void extensions(RegisterClientExtensionsEvent e){
            e.registerItem(new IClientItemExtensions(){
                private FireflyItemRenderer renderer;
                @Override public net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer getCustomRenderer(){if(renderer==null)renderer=new FireflyItemRenderer();return renderer;}
            },FireflySam.SWORD.get());
        }
        @SubscribeEvent public static void reload(RegisterClientReloadListenersEvent e){e.registerReloadListener((ResourceManagerReloadListener)resources->SamMesh.clear());}
    }
    @SubscribeEvent
    public static void tick(ClientTickEvent.Post e){
        SamClientState.tick();Minecraft mc=Minecraft.getInstance();
        if(mc.player!=null&&mc.screen==null){
            while(TRANSFORM.consumeClick())PacketDistributor.sendToServer(new SamNetwork.Action(0));
            while(ULTIMATE.consumeClick()){
                var s=SamClientState.local();if(s!=null&&s.active()&&s.combo()>=10&&s.ultimateStart()<0)PacketDistributor.sendToServer(new SamNetwork.Action(1));
            }
        }else{while(TRANSFORM.consumeClick()){}while(ULTIMATE.consumeClick()){}}
        SamAutoTest.tick(mc);
    }
    @SubscribeEvent
    public static void renderPlayer(RenderPlayerEvent.Pre e){
        var s=SamClientState.PLAYERS.get(e.getEntity().getId());if(s==null||!s.active())return;
        e.setCanceled(true);PoseStack p=e.getPoseStack();p.pushPose();
        float yaw=Mth.rotLerp(e.getPartialTick(),e.getEntity().yBodyRotO,e.getEntity().yBodyRot);
        p.mulPose(Axis.YP.rotationDegrees(180-yaw));float scale=e.getEntity().getScale()/1.75f;p.scale(scale,scale,scale);
        float movement=SamAnimation.walkAmount(e.getEntity(),e.getPartialTick());
        float stride=SamAnimation.walkStride(e.getEntity(),e.getPartialTick());
        float time=(e.getEntity().tickCount+e.getPartialTick())*.05f;
        SamAura.render(p,e.getMultiBufferSource(),time,stride,movement);
        float pulse=.91f+.09f*Mth.sin((e.getEntity().tickCount+e.getPartialTick())*.16f);
        SamMesh.get("sam_rig").render(p,e.getMultiBufferSource(),e.getPackedLight(),SamAnimation.pose(e.getEntity(),s,e.getPartialTick()),pulse);
        if(s.ultimateStart()>=0)SamWings.render(p,e.getMultiBufferSource(),e.getEntity().level().getGameTime()+e.getPartialTick()-s.ultimateStart());
        p.popPose();
    }
    @SubscribeEvent
    public static void hand(RenderHandEvent e){
        var s=SamClientState.local();Minecraft mc=Minecraft.getInstance();if(s==null||!s.active()||mc.player==null)return;e.setCanceled(true);
        PoseStack p=e.getPoseStack();p.pushPose();boolean main=e.getHand()==InteractionHand.MAIN_HAND;
        boolean right=(mc.player.getMainArm()==HumanoidArm.RIGHT)==main;
        var motion=SamAnimation.swingPose(mc.player,e.getHand(),.5f);
        float load=motion.windup(),strike=motion.strike(),follow=motion.followThrough();float side=right?-1:1;
        p.translate((right?.63:-.63)+side*.03f*strike,
            -1.08f-.02f*load+.06f*strike-.01f*follow,
            -.98f+.07f*load-.22f*strike+.10f*follow);
        p.mulPose(Axis.ZP.rotationDegrees((right?-15:15)+side*(3*load-16*strike+8*follow)));
        p.mulPose(Axis.XP.rotationDegrees(-50+8*load-24*strike+16*follow));
        p.mulPose(Axis.YP.rotationDegrees(side*(3*load+12*strike-8*follow)));
        p.scale(.7f,.7f,.7f);
        // SAM hides the vanilla arms and gauntlets in first person; keep only the moving blades.
        p.translate(0,.9,0);SamMesh.get("firefly_blade").render(p,e.getMultiBufferSource(),e.getPackedLight(),Map.of(),1);
        p.popPose();
    }
    @SubscribeEvent
    public static void camera(ViewportEvent.ComputeCameraAngles e){
        var s=SamClientState.local();Minecraft mc=Minecraft.getInstance();
        if(mc.level==null)return;float now=mc.level.getGameTime()+(float)e.getPartialTick();
        if(SamAutoTest.cameraAssist()&&s!=null&&s.active()&&s.ultimateStart()<0&&!mc.options.getCameraType().isFirstPerson()){e.setYaw(e.getYaw()+38);e.setPitch(-8);}
        if(s!=null&&s.ultimateStart()>=0){float age=now-s.ultimateStart();
            if(age<CombatRules.DIVE_START){
                e.setPitch(-4);e.setYaw(e.getYaw()+Mth.sin(age*.022f)*5);
                e.setRoll(Mth.sin(age*.035f)*.65f);
            }else if(age<CombatRules.IMPACT_TICK)e.setPitch(40);
        }
        for(var f:SamClientState.FLASHES)if(f.kind()==0&&mc.player!=null&&mc.player.distanceToSqr(f.pos())<6400){
            float age=now-f.born();float power=Math.max(0,1-age/26)*2.3f;
            e.setYaw(e.getYaw()+Mth.sin(age*2.8f)*power);e.setPitch(e.getPitch()+Mth.cos(age*3.5f)*power);e.setRoll(Mth.sin(age*2.0f)*power*.6f);
        }
    }
    @SubscribeEvent
    public static void fov(ViewportEvent.ComputeFov e){
        var s=SamClientState.local();Minecraft mc=Minecraft.getInstance();if(s==null||s.ultimateStart()<0||mc.level==null)return;
        float age=mc.level.getGameTime()+(float)e.getPartialTick()-s.ultimateStart();
        float reveal=SamAnimation.ease(age/18)*(1-SamAnimation.ease((age-104)/12));
        e.setFOV(Mth.lerp(reveal,e.getFOV(),58));
    }
    @SubscribeEvent public static void afterFrame(RenderFrameEvent.Post event){SamAutoTest.afterFrame(Minecraft.getInstance());}
}
