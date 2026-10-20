package dev.codex.fireflysam.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.codex.fireflysam.FireflySam;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** Curved, layered energy membranes and fine gold filaments; no solid wing boxes. */
public final class SamWings {
    private static final ResourceLocation TEXTURE=FireflySam.id("textures/effect/energy_wing.png");
    private static final RenderType GLOW=SamRenderTypes.WINGS;
    private SamWings(){}
    public static void render(PoseStack pose,MultiBufferSource buffers,float age){
        float amount=SamAnimation.wingAmount(age);if(amount<.005f)return;
        VertexConsumer out=buffers.getBuffer(GLOW);
        pose.pushPose();pose.translate(0,2.66,.26);
        // A small flare at the root becomes a broad, living moth-wing silhouette.
        pose.scale(amount,amount,amount);
        for(int side:new int[]{-1,1}){
            membrane(pose,out,side,age,4.65f,.42f,1.1f,.12f,0xB036FFC9);
            membrane(pose,out,side,age+9,4.05f,-.56f,1.15f,.28f,0x984CF6BE);
            membrane(pose,out,side,age+19,3.34f,-1.43f,.71f,.46f,0x8A3EDAB2);
            membrane(pose,out,side,age,4.65f,.42f,.23f,.08f,0xD0BDFFE5);
            membrane(pose,out,side,age,4.9f,.42f,1.42f,.20f,0x323CFFD4);
            for(int line=0;line<8;line++){
                float reach=4.8f-line*.20f,down=.65f-line*.33f;
                filament(pose,out,side,age+line*13,reach,down,.018f+(line%3)*.009f,line%3==0?0xE4FFFF8F:0xA983FFBB);
            }
            // Short, sharp outer streamers continue the sweep past the membrane.
            filament(pose,out,side,age+36,5.35f,.15f,.025f,0xC5D2FF70);
            filament(pose,out,side,age+78,4.9f,-1.28f,.035f,0x9E65FFBF);
        }
        for(int i=0;i<34;i++){
            float x=Mth.sin(i*73.1f)*5.4f,y=Mth.cos(i*18.7f)*2.35f-.35f;
            float drift=((age*.013f+i*.163f)%1)*.7f;
            float size=.012f+.014f*(.5f+.5f*Mth.sin(age*.10f+i));
            int color=((int)(90+125*(.5f+.5f*Mth.sin(age*.09f+i)))<<24)|0xA4FFBA;
            vertex(pose,out,x-size,y+drift,.55f,.1f,0,color);vertex(pose,out,x+size,y+drift,.55f,.9f,0,color);
            vertex(pose,out,x+size,y+drift+size*3,.55f,.9f,1,color);vertex(pose,out,x-size,y+drift+size*3,.55f,.1f,1,color);
        }
        pose.popPose();
    }
    private static void membrane(PoseStack p,VertexConsumer out,int side,float age,float reach,float tip,float breadth,float depth,int color){
        for(int i=0;i<40;i++){
            float u=i/40f,v=(i+1)/40f;
            float[] a=section(side,age,u,reach,tip,breadth,depth),b=section(side,age,v,reach,tip,breadth,depth);
            vertex(p,out,a[0],a[1],a[2],u,0,color);vertex(p,out,b[0],b[1],b[2],v,0,color);
            vertex(p,out,b[3],b[4],b[5],v,1,color);vertex(p,out,a[3],a[4],a[5],u,1,color);
        }
    }
    private static float[] section(int side,float age,float u,float reach,float tip,float breadth,float depth){
        float wave=Mth.sin(u*8-age*.095f)*u*.12f;
        float x=side*(.16f+reach*u),center=tip*u+.42f*Mth.sin(u*Mth.PI)+wave;
        float width=breadth*(float)Math.pow(Math.max(0,Mth.sin(u*Mth.PI)),.7f);
        float z=depth+Mth.sin(u*5-age*.045f)*u*.27f+u*u*.45f;
        return new float[]{x,center+width*.40f,z,x,center-width*.60f,z+.10f};
    }
    private static void filament(PoseStack p,VertexConsumer out,int side,float age,float reach,float tip,float width,int color){
        for(int i=0;i<44;i++){
            float u=i/44f,v=(i+1)/44f;
            float ax=side*(.12f+u*reach),bx=side*(.12f+v*reach);
            float ay=tip*u+.7f*Mth.sin(u*Mth.PI)+Mth.sin(u*9+age*.075f)*u*.15f;
            float by=tip*v+.7f*Mth.sin(v*Mth.PI)+Mth.sin(v*9+age*.075f)*v*.15f;
            float az=.19f+u*u*.63f,bz=.19f+v*v*.63f;
            vertex(p,out,ax,ay-width,az,u,0,color);vertex(p,out,bx,by-width,bz,v,0,color);
            vertex(p,out,bx,by+width,bz,v,1,color);vertex(p,out,ax,ay+width,az,u,1,color);
        }
    }
    private static void vertex(PoseStack p,VertexConsumer out,float x,float y,float z,float u,float v,int color){
        float a=(color>>>24)/255f;
        color=0xFF000000|((int)(((color>>16)&255)*a)<<16)|((int)(((color>>8)&255)*a)<<8)|(int)((color&255)*a);
        out.addVertex(p.last(),x,y,z).setColor(color).setUv(u,v).setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(LightTexture.FULL_BRIGHT).setNormal(p.last(),0,0,-1);
    }
}
