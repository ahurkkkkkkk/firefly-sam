package dev.codex.fireflysam.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;

/** A restrained Firefly-green energy field that intensifies with SAM's planted walk. */
public final class SamAura {
    private static final int RIBBONS=3;
    private static final int RING_SEGMENTS=72;
    private static final int RIBBON_SEGMENTS=56;
    private static final float TAU=(float)(Math.PI*2);

    private SamAura(){}

    public static int ribbonCount(){return RIBBONS;}
    public static int sparkCount(){return 18;}
    public static float intensity(float movement,float time){
        return Mth.clamp(.73f+.22f*Mth.clamp(movement,0,1)+.035f*Mth.sin(time*2.1f),.68f,1);
    }

    public static void render(PoseStack poses,MultiBufferSource buffers,float time,float stride,float movement){
        VertexConsumer out=buffers.getBuffer(SamRenderTypes.WINGS);
        float energy=intensity(movement,time);
        float footPulse=(.5f+.5f*Mth.cos(stride*2))*movement;
        poses.pushPose();
        groundRing(poses,out,time,energy,footPulse);
        for(int ribbon=0;ribbon<RIBBONS;ribbon++)helix(poses,out,time,stride,movement,energy,ribbon);
        sparks(poses,out,time,stride,movement,energy);
        poses.popPose();
    }

    private static void groundRing(PoseStack poses,VertexConsumer out,float time,float energy,float footPulse){
        float radiusX=1.02f+.045f*footPulse+.025f*Mth.sin(time*1.4f);
        float radiusZ=.69f+.035f*footPulse;
        float thickness=.035f+.018f*footPulse;
        int color=argb(0x56FFE0,energy*(.44f+.34f*footPulse));
        float rotation=time*.23f;
        for(int i=0;i<RING_SEGMENTS;i++){
            float a=rotation+TAU*i/RING_SEGMENTS,b=rotation+TAU*(i+1)/RING_SEGMENTS;
            float ax=(float)Math.cos(a),az=(float)Math.sin(a),bx=(float)Math.cos(b),bz=(float)Math.sin(b);
            float u0=i/(float)RING_SEGMENTS,u1=(i+1)/(float)RING_SEGMENTS;
            vertex(poses,out,ax*(radiusX+thickness),.035f,az*(radiusZ+thickness),u0,.25f,color);
            vertex(poses,out,bx*(radiusX+thickness),.035f,bz*(radiusZ+thickness),u1,.25f,color);
            vertex(poses,out,bx*(radiusX-thickness),.040f,bz*(radiusZ-thickness),u1,.75f,color);
            vertex(poses,out,ax*(radiusX-thickness),.040f,az*(radiusZ-thickness),u0,.75f,color);
        }
    }

    private static void helix(PoseStack poses,VertexConsumer out,float time,float stride,float movement,float energy,int ribbon){
        float phase=time*(.72f+.44f*movement)+ribbon*TAU/RIBBONS;
        float turns=TAU*(1.28f+.08f*(ribbon%2));
        float widthScale=.82f+.18f*Mth.sin(time*2.4f+stride*.45f+ribbon);
        int rgb=switch(ribbon){case 0->0x39FFD1;case 1->0x8DFF8B;default->0xE6FFC0;};
        for(int i=0;i<RIBBON_SEGMENTS;i++){
            float t0=i/(float)RIBBON_SEGMENTS,t1=(i+1)/(float)RIBBON_SEGMENTS;
            float a0=phase+turns*t0+.12f*Mth.sin(t0*11+time*1.25f+ribbon);
            float a1=phase+turns*t1+.12f*Mth.sin(t1*11+time*1.25f+ribbon);
            float y0=.07f+3.38f*t0,y1=.07f+3.38f*t1;
            float rx0=.63f+.16f*Mth.sin(t0*Mth.PI),rx1=.63f+.16f*Mth.sin(t1*Mth.PI);
            float rz0=.47f+.13f*Mth.sin(t0*Mth.PI),rz1=.47f+.13f*Mth.sin(t1*Mth.PI);
            float x0=rx0*Mth.cos(a0),z0=rz0*Mth.sin(a0),x1=rx1*Mth.cos(a1),z1=rz1*Mth.sin(a1);
            float fade=(float)Math.sqrt(Math.max(.015f,Mth.sin((t0+t1)*.5f*Mth.PI)));
            float shimmer=.72f+.28f*Mth.sin(time*2.8f+t0*9+ribbon*1.7f);
            float halfWidth=(.055f+.13f*Mth.sin((t0+t1)*.5f*Mth.PI))*widthScale;
            int color=argb(rgb,energy*fade*shimmer*(.55f+.35f*movement));
            float u0=.12f+.76f*t0,u1=.12f+.76f*t1;
            vertex(poses,out,x0,y0-halfWidth,z0,u0,.08f,color);
            vertex(poses,out,x1,y1-halfWidth,z1,u1,.08f,color);
            vertex(poses,out,x1,y1+halfWidth,z1,u1,.92f,color);
            vertex(poses,out,x0,y0+halfWidth,z0,u0,.92f,color);
        }
    }

    private static void sparks(PoseStack poses,VertexConsumer out,float time,float stride,float movement,float energy){
        for(int i=0;i<18;i++){
            float seed=i*.6180339f;
            float travel=(time*.055f+seed)%1;
            float y=.18f+travel*3.18f;
            float angle=i*2.399963f+time*(.42f+.30f*movement)-stride*.11f;
            float x=(.80f+.10f*Mth.sin(time+i))*Mth.cos(angle);
            float z=(.59f+.08f*Mth.cos(time*.8f+i))*Mth.sin(angle);
            float size=.018f+.018f*(.5f+.5f*Mth.sin(time*3+i));
            float fade=.45f+.45f*Mth.sin(time*2.1f+i*1.7f);
            int rgb=(i%3==0)?0xD4FF91:0x77FFD5;
            int color=argb(rgb,energy*fade*(.52f+.30f*movement));
            vertex(poses,out,x-size,y-size,z,.20f,.20f,color);
            vertex(poses,out,x+size,y-size,z,.80f,.20f,color);
            vertex(poses,out,x+size,y+size,z,.80f,.80f,color);
            vertex(poses,out,x-size,y+size,z,.20f,.80f,color);
        }
    }

    private static int argb(int rgb,float alpha){
        int a=Mth.clamp((int)(alpha*255),0,255);
        int r=((rgb>>16)&255)*a/255,g=((rgb>>8)&255)*a/255,b=(rgb&255)*a/255;
        // SamRenderTypes uses additive blending; pre-tint the RGB and leave
        // vertex alpha opaque so the brightness is not attenuated twice.
        return 0xFF000000|(r<<16)|(g<<8)|b;
    }

    private static void vertex(PoseStack poses,VertexConsumer out,float x,float y,float z,float u,float v,int color){
        out.addVertex(poses.last(),x,y,z).setColor(color).setUv(u,v).setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(LightTexture.FULL_BRIGHT).setNormal(poses.last(),0,0,-1);
    }
}
