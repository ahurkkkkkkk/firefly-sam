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
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Material-batched renderer for local MMD imports and their weighted PMX skeleton. */
public final class SamMesh {
    public static final ResourceLocation WHITE=FireflySam.id("textures/white.png");
    private static final Map<String,SamMesh> CACHE=new HashMap<>();
    private static final Map<String,Vector3f> LAST_BLADE_DIRECTIONS=new HashMap<>();
    private final List<Bone> roots=new ArrayList<>();
    private final List<Bone> allBones=new ArrayList<>();
    private final List<Material> materials=new ArrayList<>();
    private final List<SkinBatch> skinned=new ArrayList<>();
    private int triangleCount;
    private record Material(String name,ResourceLocation texture,ResourceLocation emissive,boolean fullbright) {}
    private record Batch(int material,float[] vertices,int[] colors) {}
    private record SkinBatch(int material,float[] vertices,int[] colors,int[] boneIndices,float[] weights) {}
    private static final class Bone {
        String name;int index;Vector3f pivot,worldPivot;Quaternionf bind;final List<Batch> batches=new ArrayList<>();final List<Bone> children=new ArrayList<>();
    }
    private SamMesh(){}
    public static void clear(){CACHE.clear();}
    public static SamMesh get(String name){return CACHE.computeIfAbsent(name,SamMesh::load);}
    public static Vector3f lastBladeDirection(String name){Vector3f direction=LAST_BLADE_DIRECTIONS.get(name);return direction==null?null:new Vector3f(direction);}
    public int triangleCount(){return triangleCount;}
    public boolean suppliedModel(){return triangleCount>3000;}
    public int boneCount(){return allBones.size();}
    public boolean weightedSkinning(){return !skinned.isEmpty();}
    private static SamMesh load(String name){
        SamMesh model=new SamMesh();
        try(var resource=Minecraft.getInstance().getResourceManager().open(FireflySam.id("models/"+name+".fmesh"));
            var input=new DataInputStream(new BufferedInputStream(resource))){
            if(input.readInt()!=0x464D5348)throw new IllegalArgumentException("Unsupported supplied model format");
            int version=input.readInt();if(version!=2&&version!=3)throw new IllegalArgumentException("Unsupported supplied model version: "+version);
            int materialCount=bounded(input.readInt(),1,32);
            for(int i=0;i<materialCount;i++){
                String label=input.readUTF(),texture=input.readUTF(),emissive=input.readUTF();boolean bright=input.readBoolean();
                model.materials.add(new Material(label,FireflySam.id(texture),emissive.isEmpty()?null:FireflySam.id(emissive),bright));
            }
            int count=bounded(input.readInt(),1,128);List<Bone>bones=new ArrayList<>(count);
            for(int i=0;i<count;i++){
                Bone bone=new Bone();bone.index=i;bone.name=input.readUTF();int parent=input.readInt();
                bone.pivot=new Vector3f(input.readFloat(),input.readFloat(),input.readFloat());
                bone.bind=new Quaternionf(input.readFloat(),input.readFloat(),input.readFloat(),input.readFloat()).normalize();
                bone.worldPivot=parent<0?new Vector3f(bone.pivot):new Vector3f(bones.get(parent).worldPivot).add(bone.pivot);
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
                bones.add(bone);model.allBones.add(bone);
            }
            if(version==3){
                int batchCount=bounded(input.readInt(),1,materialCount);
                for(int b=0;b<batchCount;b++){
                    int material=bounded(input.readInt(),0,materialCount-1),vertices=bounded(input.readInt(),0,2000000);
                    if(vertices%4!=0)throw new IllegalArgumentException("Invalid skinned triangle batch");
                    float[] data=new float[vertices*8],weights=new float[vertices*4];int[] colors=new int[vertices],indices=new int[vertices*4];
                    for(int vertex=0;vertex<vertices;vertex++){
                        for(int component=0;component<8;component++){
                            float value=input.readFloat();if(!Float.isFinite(value))throw new IllegalArgumentException("Non-finite skinned vertex");data[vertex*8+component]=value;
                        }
                        colors[vertex]=input.readInt();float total=0;
                        for(int influence=0;influence<4;influence++){
                            int boneIndex=input.readUnsignedShort();float weight=input.readFloat();
                            if(boneIndex>=count||!Float.isFinite(weight)||weight<0)throw new IllegalArgumentException("Invalid skin influence");
                            indices[vertex*4+influence]=boneIndex;weights[vertex*4+influence]=weight;total+=weight;
                        }
                        if(Math.abs(total-1)>0.025f)throw new IllegalArgumentException("Skin weights do not sum to one");
                    }
                    model.skinned.add(new SkinBatch(material,data,colors,indices,weights));model.triangleCount+=vertices/4;
                }
            }
            FireflySam.LOGGER.log(System.Logger.Level.INFO,"Loaded supplied {0}: {1} bones, {2} triangles, {3} material sets",name,count,model.triangleCount,materialCount);
        }catch(Exception error){throw new IllegalStateException("Could not load supplied model "+name,error);}
        return model;
    }
    private static int bounded(int value,int min,int max){if(value<min||value>max)throw new IllegalArgumentException("Invalid supplied mesh count: "+value);return value;}
    public void render(PoseStack poses,MultiBufferSource buffers,int light,Map<String,Vector3f> rotations,float glow){
        float alignment=rotations.containsKey("__neutral_bind")?rotations.get("__neutral_bind").x:0;
        if(!skinned.isEmpty()){
            PoseStack rigPose=new PoseStack();Map<Integer,Matrix4f> transforms=new HashMap<>();Map<Integer,Matrix3f> normalTransforms=new HashMap<>();
            for(Bone bone:roots)buildTransforms(bone,rigPose,rotations,alignment,transforms,normalTransforms);
            var outer=poses.last();
            for(Bone bone:allBones)if(bone.name.endsWith("_blade")){
                Matrix4f matrix=transforms.get(bone.index);Vector3f origin=matrix.transformPosition(new Vector3f());
                Vector3f tip=matrix.transformPosition(new Vector3f(0,1.835f,0));outer.pose().transformPosition(origin);outer.pose().transformPosition(tip);
                LAST_BLADE_DIRECTIONS.put(bone.name,tip.sub(origin).normalize());
            }
            for(int material=0;material<materials.size();material++){
                Material m=materials.get(material);VertexConsumer base=buffers.getBuffer(RenderType.entityCutoutNoCull(m.texture));
                for(SkinBatch batch:skinned)if(batch.material==material)drawSkinned(batch,outer,base,m.fullbright?LightTexture.FULL_BRIGHT:light,transforms,normalTransforms,1);
                if(m.emissive!=null){VertexConsumer emissive=buffers.getBuffer(RenderType.eyes(m.emissive));for(SkinBatch batch:skinned)if(batch.material==material)drawSkinned(batch,outer,emissive,LightTexture.FULL_BRIGHT,transforms,normalTransforms,glow);}
            }
            return;
        }
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
    private static void buildTransforms(Bone bone,PoseStack poses,Map<String,Vector3f> rotations,float alignment,
                                       Map<Integer,Matrix4f> transforms,Map<Integer,Matrix3f> normalTransforms){
        poses.pushPose();Vector3f offset=rotations.get("__offset_"+bone.name);
        poses.translate(bone.pivot.x+(offset==null?0:offset.x),bone.pivot.y+(offset==null?0:offset.y),bone.pivot.z+(offset==null?0:offset.z));
        poses.mulPose(alignment>0?new Quaternionf(bone.bind).nlerp(new Quaternionf(),Math.min(1,alignment)):bone.bind);
        Vector3f rotation=rotations.get(bone.name);if(rotation!=null){poses.mulPose(Axis.ZP.rotation(rotation.z));poses.mulPose(Axis.YP.rotation(rotation.y));poses.mulPose(Axis.XP.rotation(rotation.x));}
        transforms.put(bone.index,new Matrix4f(poses.last().pose()));normalTransforms.put(bone.index,new Matrix3f(poses.last().normal()));
        for(Bone child:bone.children)buildTransforms(child,poses,rotations,alignment,transforms,normalTransforms);
        poses.popPose();
    }
    private void drawSkinned(SkinBatch batch,PoseStack.Pose outer,VertexConsumer out,int light,
                             Map<Integer,Matrix4f> transforms,Map<Integer,Matrix3f> normalTransforms,float alpha){
        float[] vertices=batch.vertices,weights=batch.weights;int[] indices=batch.boneIndices,colors=batch.colors;
        Vector3f position=new Vector3f(),normal=new Vector3f(),local=new Vector3f(),localNormal=new Vector3f();
        Vector3f transformed=new Vector3f(),transformedNormal=new Vector3f();
        for(int i=0;i<colors.length;i++){
            int offset=i*8,influenceOffset=i*4;position.zero();normal.zero();
            for(int influence=0;influence<4;influence++){
                float weight=weights[influenceOffset+influence];if(weight<=0)continue;
                int boneIndex=indices[influenceOffset+influence];Bone bone=allBones.get(boneIndex);
                local.set(vertices[offset],vertices[offset+1],vertices[offset+2]).sub(bone.worldPivot);
                transforms.get(boneIndex).transformPosition(local,transformed);
                localNormal.set(vertices[offset+5],vertices[offset+6],vertices[offset+7]);
                normalTransforms.get(boneIndex).transform(localNormal,transformedNormal);
                position.fma(weight,transformed);normal.fma(weight,transformedNormal);
            }
            normal.normalize();int color=colors[i];if(alpha<.999f)color=(color&0xFFFFFF)|((int)((color>>>24)*alpha)<<24);
            out.addVertex(outer,position.x,position.y,position.z).setColor(color).setUv(vertices[offset+3],vertices[offset+4])
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(outer,normal.x,normal.y,normal.z);
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
