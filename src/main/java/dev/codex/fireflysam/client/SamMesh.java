package dev.codex.fireflysam.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.codex.fireflysam.FireflySam;
import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Material-batched, smooth-normal renderer for the supplied OBJ / FBX meshes. */
public final class SamMesh {
    public static final ResourceLocation WHITE=FireflySam.id("textures/white.png");
    private static final Map<String,SamMesh> CACHE=new HashMap<>();
    private static final Map<String,Vector3f> LAST_BLADE_DIRECTIONS=new HashMap<>();
    private final List<Bone> roots=new ArrayList<>();
    private final List<Material> materials=new ArrayList<>();
    private int triangleCount;
    private record Material(String name,ResourceLocation texture,ResourceLocation emissive,boolean fullbright) {}
    private record Batch(int material,float[] vertices,int[] colors) {}
    private static final class Bone {
        String name;Vector3f pivot;Quaternionf bind;final List<Batch> batches=new ArrayList<>();final List<Bone> children=new ArrayList<>();
    }
    private SamMesh(){}
    public static void clear(){CACHE.clear();}
    public static SamMesh get(String name){return CACHE.computeIfAbsent(name,SamMesh::load);}
    public static Vector3f lastBladeDirection(String name){Vector3f direction=LAST_BLADE_DIRECTIONS.get(name);return direction==null?null:new Vector3f(direction);}
    public int triangleCount(){return triangleCount;}
    public boolean suppliedModel(){return triangleCount>5000;}
    private static SamMesh load(String name){
        SamMesh model=new SamMesh();
        try(var resource=Minecraft.getInstance().getResourceManager().open(FireflySam.id("models/"+name+".fmesh"));
            var input=new DataInputStream(new BufferedInputStream(resource))){
            if(input.readInt()!=0x464D5348||input.readInt()!=2)throw new IllegalArgumentException("Unsupported supplied model format");
            int materialCount=bounded(input.readInt(),1,32);
            for(int i=0;i<materialCount;i++){
                String label=input.readUTF(),texture=input.readUTF(),emissive=input.readUTF();boolean bright=input.readBoolean();
                model.materials.add(new Material(label,FireflySam.id(texture),emissive.isEmpty()?null:FireflySam.id(emissive),bright));
            }
            int count=bounded(input.readInt(),1,128);List<Bone>bones=new ArrayList<>(count);
            for(int i=0;i<count;i++){
                Bone bone=new Bone();bone.name=input.readUTF();int parent=input.readInt();
                bone.pivot=new Vector3f(input.readFloat(),input.readFloat(),input.readFloat());
                bone.bind=new Quaternionf(input.readFloat(),input.readFloat(),input.readFloat(),input.readFloat()).normalize();
                int batches=bounded(input.readInt(),0,materialCount);
                for(int b=0;b<batches;b++){
                    int material=bounded(input.readInt(),0,materialCount-1),vertices=bounded(input.readInt(),0,2000000);
                    if(vertices%4!=0)throw new IllegalArgumentException("Invalid triangle batch");
                    float[] data=new float[vertices*8];int[]colors=new int[vertices];
                    for(int vertex=0;vertex<vertices;vertex++){
                        for(int component=0;component<8;component++){
                            float value=input.readFloat();if(!Float.isFinite(value))throw new IllegalArgumentException("Non-finite supplied vertex");data[vertex*8+component]=value;
                        }
                        colors[vertex]=input.readInt();
                    }
                    bone.batches.add(new Batch(material,data,colors));model.triangleCount+=vertices/4;
                }
                if(parent<0)model.roots.add(bone);else bones.get(bounded(parent,0,i-1)).children.add(bone);
                bones.add(bone);
            }
            FireflySam.LOGGER.log(System.Logger.Level.INFO,"Loaded supplied {0}: {1} bones, {2} triangles, {3} material sets",name,count,model.triangleCount,materialCount);
        }catch(Exception error){throw new IllegalStateException("Could not load supplied model "+name,error);}
        return model;
    }
    private static int bounded(int value,int min,int max){if(value<min||value>max)throw new IllegalArgumentException("Invalid supplied mesh count: "+value);return value;}
    public void render(PoseStack poses,MultiBufferSource buffers,int light,Map<String,Vector3f> rotations,float glow){
        float alignment=rotations.containsKey("__neutral_bind")?rotations.get("__neutral_bind").x:0;
        for(int material=0;material<materials.size();material++){
            Material m=materials.get(material);
            VertexConsumer base=buffers.getBuffer(RenderType.entityCutoutNoCull(m.texture));
            for(Bone bone:roots)draw(bone,poses,base,m.fullbright?LightTexture.FULL_BRIGHT:light,rotations,material,alignment,1);
            if(m.emissive!=null){
                VertexConsumer emissive=buffers.getBuffer(RenderType.eyes(m.emissive));
                for(Bone bone:roots)draw(bone,poses,emissive,LightTexture.FULL_BRIGHT,rotations,material,alignment,glow);
            }
        }
    }
    private static void draw(Bone bone,PoseStack poses,VertexConsumer out,int light,Map<String,Vector3f> rotations,int material,float alignment,float alpha){
        poses.pushPose();
        Vector3f boneOffset=rotations.get("__offset_"+bone.name);
        poses.translate(bone.pivot.x+(boneOffset==null?0:boneOffset.x),bone.pivot.y+(boneOffset==null?0:boneOffset.y),bone.pivot.z+(boneOffset==null?0:boneOffset.z));
        if(alignment>0)poses.mulPose(new Quaternionf(bone.bind).nlerp(new Quaternionf(),Math.min(1,alignment)));
        else poses.mulPose(bone.bind);
        Vector3f r=rotations.get(bone.name);
        if(r!=null){poses.mulPose(Axis.ZP.rotation(r.z));poses.mulPose(Axis.YP.rotation(r.y));poses.mulPose(Axis.XP.rotation(r.x));}
        var last=poses.last();
        if(Boolean.getBoolean("fireflysam.test")&&(bone.name.equals("right_blade")||bone.name.equals("left_blade"))){
            Vector3f origin=last.pose().transformPosition(new Vector3f(0,0,0));
            Vector3f tip=last.pose().transformPosition(new Vector3f(0,1.835f,0));
            LAST_BLADE_DIRECTIONS.put(bone.name,tip.sub(origin).normalize());
        }
        for(Batch batch:bone.batches)if(batch.material==material){
            float[] v=batch.vertices;int[]colors=batch.colors;
            for(int i=0;i<colors.length;i++){
                int offset=i*8,color=colors[i];
                if(alpha<.999f)color=(color&0xFFFFFF)|((int)((color>>>24)*alpha)<<24);
                out.addVertex(last,v[offset],v[offset+1],v[offset+2]).setColor(color).setUv(v[offset+3],v[offset+4])
                    .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(last,v[offset+5],v[offset+6],v[offset+7]);
            }
        }
        for(Bone child:bone.children)draw(child,poses,out,light,rotations,material,alignment,alpha);
        poses.popPose();
    }
}
