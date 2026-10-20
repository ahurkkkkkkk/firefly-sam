package dev.codex.fireflysam.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.codex.fireflysam.FireflySam;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;

/** Additive light with two-sided curved geometry and no world-depth writes. */
final class SamRenderTypes extends RenderType {
    private SamRenderTypes(){super("sam_internal",DefaultVertexFormat.NEW_ENTITY,VertexFormat.Mode.QUADS,256,false,false,()->{},()->{});}
    static final RenderType WINGS=create("sam_energy_wings",DefaultVertexFormat.NEW_ENTITY,VertexFormat.Mode.QUADS,16384,false,true,
        CompositeState.builder().setShaderState(RENDERTYPE_EYES_SHADER)
            .setTextureState(new RenderStateShard.TextureStateShard(FireflySam.id("textures/effect/energy_wing_glow.png"),true,false))
            .setTransparencyState(ADDITIVE_TRANSPARENCY).setCullState(NO_CULL).setWriteMaskState(COLOR_WRITE).createCompositeState(false));
}
