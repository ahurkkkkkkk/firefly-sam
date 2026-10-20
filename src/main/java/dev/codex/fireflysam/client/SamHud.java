package dev.codex.fireflysam.client;

import dev.codex.fireflysam.CombatRules;
import dev.codex.fireflysam.FireflySam;
import dev.codex.fireflysam.SamNetwork;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

@EventBusSubscriber(modid=FireflySam.ID,value=Dist.CLIENT)
public final class SamHud {
    private static final int TEAL=0xFF7CFFE2, MUTED=0xFF91ABB0, GOLD=0xFFFFD580, WHITE=0xFFE9F5F1;
    private static final int PANEL=0x9208151B;
    private SamHud() {}
    @SubscribeEvent public static void cinematicLayers(RenderGuiLayerEvent.Pre event){
        var s=SamClientState.local();if(s==null||s.ultimateStart()<0)return;
        var layer=event.getName();
        if(layer.equals(VanillaGuiLayers.HOTBAR)||layer.equals(VanillaGuiLayers.CROSSHAIR)
            ||layer.equals(VanillaGuiLayers.SELECTED_ITEM_NAME)||layer.equals(VanillaGuiLayers.EXPERIENCE_BAR)
            ||layer.equals(VanillaGuiLayers.EXPERIENCE_LEVEL)||layer.equals(VanillaGuiLayers.PLAYER_HEALTH)
            ||layer.equals(VanillaGuiLayers.ARMOR_LEVEL)||layer.equals(VanillaGuiLayers.FOOD_LEVEL)
            ||layer.equals(VanillaGuiLayers.EFFECTS)||layer.equals(VanillaGuiLayers.OVERLAY_MESSAGE))event.setCanceled(true);
    }
    @SubscribeEvent
    public static void render(RenderGuiEvent.Post event) {
        Minecraft mc=Minecraft.getInstance();if(mc.player==null||mc.level==null||mc.options.hideGui)return;
        var s=SamClientState.local();boolean active=s!=null&&s.active();
        if(!active&&!mc.player.getMainHandItem().is(FireflySam.SWORD.get()))return;
        GuiGraphics g=event.getGuiGraphics();int w=g.guiWidth(),h=g.guiHeight();float now=mc.level.getGameTime()+event.getPartialTick().getGameTimeDeltaPartialTick(false);
        boolean cinematic=active&&s.ultimateStart()>=0;
        if(!cinematic){
        int x=8,y=h-60;
        panel(g,x,y,146,52,now);
        text(g,"FIREFLY // TYPE-IV",x+8,y+5,MUTED);
        text(g,active?"SAM ONLINE":"FIREFLY BLADE",x+8,y+16,active?TEAL:WHITE);
        if(active) {
            String damage="DMG x2";
            text(g,damage,x+138-Minecraft.getInstance().font.width(damage),y+16,GOLD);
            for(int i=0;i<10;i++){
                int px=x+8+i*12;g.fill(px,y+26,px+9,y+30,0xAA2B4549);
                if(i<s.combo())g.fill(px,y+26,px+9,y+30,s.combo()==10?GOLD:TEAL);
            }
            text(g,String.format(Locale.ROOT,"%02d / 10",s.combo()),x+8,y+37,WHITE);
            String key=SamClient.TRANSFORM.getTranslatedKeyMessage().getString();
            text(g,"["+key+"] OFF",x+66,y+37,MUTED);
            if(s.combo()>0&&s.combo()<10){int remain=(int)(130*Mth.clamp(1-(now-s.lastHit())/120f,0,1));g.fill(x+8,y+47,x+8+remain,y+48,0xCC7CFFE2);}
        }else {
            text(g,"READY WHEN YOU ARE",x+8,y+30,WHITE);
            String key=SamClient.TRANSFORM.getTranslatedKeyMessage().getString();
            text(g,"["+key+"] ACTIVATE SAM",x+8,y+41,TEAL);
        }
        // The ultimate remains tied to SAM mode and the original ten-hit charge.
        if(active&&s.combo()==10&&s.ultimateStart()<0){
            int ux=w-162,uy=h-57;panel(g,ux,uy,154,49,now);
            text(g,"COMPLETE COMBUSTION",ux+8,uy+6,GOLD);
            text(g,"DEATHSTAR OVERLOAD",ux+8,uy+18,WHITE);
            text(g,"["+SamClient.ULTIMATE.getTranslatedKeyMessage().getString()+"] UNLEASH",ux+8,uy+34,TEAL);
        }
        if(mc.hitResult instanceof EntityHitResult hit&&hit.getEntity() instanceof LivingEntity target&&!(target instanceof Player))target(g,target,w,now);
        }
        if(active&&s.ultimateStart()>=0){
            float age=now-s.ultimateStart();int bar=Math.min(21,h/12);g.fill(0,0,w,bar,0xF500090D);g.fill(0,h-bar,w,h,0xF500090D);
            centered(g,age<CombatRules.DIVE_START?"FYREFLY TYPE-IV : COMPLETE COMBUSTION":"DEATHSTAR OVERLOAD",w/2,bar+10,age<CombatRules.DIVE_START?TEAL:GOLD);
            if(age<24)centered(g,"IGNITION SEQUENCE",w/2,h/2-35,WHITE);
        }
        for(var f:SamClientState.FLASHES){
            if(mc.player.distanceToSqr(f.pos())>6400)continue;float age=now-f.born();
            if(f.kind()==0){
                if(age<7){int alpha=(int)(155*(1-age/7));g.fill(0,0,w,h,(alpha<<24)|0xD9FFF0);}
            }else if(age<34){
                int alpha=(int)(255*Math.min(1,(34-age)/12));
                String label=f.kind()==1?"WEAKNESS BREAK":"SUPER BREAK";
                centered(g,label,w/2,h/2-55-(int)(age*.45),(alpha<<24)|(f.kind()==1?0xFFD580:0x7CFFE2));
                centered(g,"+"+Math.round(f.damage()),w/2,h/2-42-(int)(age*.45),(alpha<<24)|0xEFFFF7);
            }
        }
    }
    private static void target(GuiGraphics g,LivingEntity target,int w,float now){
        var data=SamClientState.TOUGHNESS.get(target.getId());float maximum=CombatRules.toughness(target.getMaxHealth(),target.getArmorValue());
        float amount=data==null?maximum:data.remaining();boolean broken=data!=null&&data.brokenUntil()>now;
        int x=w/2-91,y=38;panel(g,x-6,y-7,196,47,now);
        String name=target.getDisplayName().getString();centered(g,name,w/2,y-1,WHITE);
        g.fill(x,y+12,x+182,y+14,0xAA402F30);g.fill(x,y+12,x+(int)(182*target.getHealth()/target.getMaxHealth()),y+14,0xFFD27A70);
        g.fill(x,y+18,x+182,y+23,0xAA263B40);
        if(!broken)g.fill(x,y+18,x+(int)(182*amount/maximum),y+23,0xFFE8EDE5);
        else for(int i=0;i<12;i++)g.fill(x+i*15,y+20,x+i*15+8,y+22,GOLD);
        text(g,broken?"FIRE // WEAKNESS BROKEN":"FIRE // TOUGHNESS",x,y+29,broken?GOLD:TEAL);
        String value=broken?String.format(Locale.ROOT,"%.1fs",Math.max(0,(data.brokenUntil()-now)/20f)):Math.round(amount)+" / "+Math.round(maximum);
        text(g,value,x+182-Minecraft.getInstance().font.width(value),y+29,MUTED);
    }
    private static void panel(GuiGraphics g,int x,int y,int w,int h,float now){
        g.fill(x,y,x+w,y+h,PANEL);
        int pulse=(int)(115+55*(.5+.5*Math.sin(now*.24)));
        int edge=(pulse<<24)|0x007CFFE2, dim=(0x75<<24)|0x003B817B;
        g.fill(x+2,y,x+w-2,y+1,edge);g.fill(x+2,y+h-1,x+w-2,y+h,dim);
        g.fill(x,y+2,x+1,y+h-2,dim);g.fill(x+w-1,y+2,x+w,y+h-2,dim);
        g.fill(x,y,x+4,y+1,TEAL);g.fill(x+w-4,y+h-1,x+w,y+h,dim);
        int phase=(int)(now*1.7f);
        int firstY=y+2+Math.floorMod(phase*7+x,h-4);
        int secondY=y+2+Math.floorMod(phase*5+w,h-4);
        int shift=Math.floorMod(phase,3)-1;
        int glitch=(0xE0<<24)|0x0064FFE2;
        g.fill(x+1+shift,firstY,x+7+shift,firstY+1,glitch);
        g.fill(x+w-7-shift,secondY,x+w-1-shift,secondY+1,glitch);
        if((phase&3)==0){
            int cutX=x+12+Math.floorMod(phase*11,w-24);
            g.fill(cutX,y,Math.min(x+w-2,cutX+4),y+1,0xFF14242A);
        }
    }
    private static void text(GuiGraphics g,String s,int x,int y,int color){g.drawString(Minecraft.getInstance().font,s,x,y,color,false);}
    private static void centered(GuiGraphics g,String s,int x,int y,int color){text(g,s,x-Minecraft.getInstance().font.width(s)/2,y,color);}
}
