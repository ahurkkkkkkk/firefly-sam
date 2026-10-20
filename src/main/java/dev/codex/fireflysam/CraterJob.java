package dev.codex.fireflysam;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;

/** Incremental crater: bounded work, no chain of synchronous vanilla explosions. */
final class CraterJob {
    private final ServerLevel level;
    private final BlockPos center;
    private final int radius,depth;
    private int x,z,y,removed;
    CraterJob(ServerLevel level,BlockPos center,int radius,int depth) {
        this.level=level;this.center=center;this.radius=radius;this.depth=depth;x=-radius;z=-radius;y=8;
    }
    boolean tick(int budget) {
        int changed=0,scanned=0;
        BlockPos.MutableBlockPos pos=new BlockPos.MutableBlockPos();
        while(x<=radius && changed<budget && scanned<budget*8) {
            double distance=Math.sqrt(x*x+z*z);
            int floor=(int)Math.ceil(CombatRules.craterFloor(distance,radius,depth));
            if(distance<radius && y>=floor) {
                pos.set(center.getX()+x,center.getY()+y,center.getZ()+z);
                if(level.hasChunkAt(pos) && level.getWorldBorder().isWithinBounds(pos) && pos.getY()>level.getMinBuildHeight()) {
                    var state=level.getBlockState(pos);
                    if(!state.isAir() && !state.hasBlockEntity() && state.getFluidState().isEmpty() && state.getDestroySpeed(level,pos)>=0 && state.getDestroySpeed(level,pos)<=50) {
                        level.setBlock(pos,Blocks.AIR.defaultBlockState(),2);changed++;removed++;
                    }
                }
                y--;scanned++;
            } else {
                // A thin scorched basin makes the physical impact visible after particles fade.
                if(distance<radius && distance>1) {
                    pos.set(center.getX()+x,center.getY()+floor-1,center.getZ()+z);
                    if(level.hasChunkAt(pos) && level.getWorldBorder().isWithinBounds(pos)) {
                        var state=level.getBlockState(pos);
                        if(!state.isAir() && !state.hasBlockEntity() && state.getFluidState().isEmpty() && state.getDestroySpeed(level,pos)>=0 && state.getDestroySpeed(level,pos)<50) {
                            var scar=((x*31+z*17)&7)==0?Blocks.MAGMA_BLOCK:((x+z)&3)==0?Blocks.BASALT:Blocks.BLACKSTONE;
                            level.setBlock(pos,scar.defaultBlockState(),2);changed++;
                        }
                    }
                }
                y=8;z++;
                if(z>radius) {z=-radius;x++;}
                scanned++;
            }
        }
        if(x>radius) FireflySam.LOGGER.log(System.Logger.Level.INFO,"SAM crater complete: {0} blocks removed",removed);
        return x>radius;
    }
}
