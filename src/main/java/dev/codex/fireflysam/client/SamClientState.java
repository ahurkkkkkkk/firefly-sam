package dev.codex.fireflysam.client;

import dev.codex.fireflysam.SamNetwork;
import dev.codex.fireflysam.CombatRules;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;

public final class SamClientState {
    public static final Map<Integer,SamNetwork.State> PLAYERS=new HashMap<>();
    public static final Map<Integer,SamNetwork.Toughness> TOUGHNESS=new HashMap<>();
    public static final List<Flash> FLASHES=new ArrayList<>();
    public record Flash(Vec3 pos,float radius,int kind,float damage,long born) {}
    private static ClientLevel world;
    private static CameraType beforeMech,beforeCast;
    private SamClientState() {}
    private static void ensureWorld() {
        Minecraft mc=Minecraft.getInstance();
        if(world!=mc.level) {
            PLAYERS.clear();TOUGHNESS.clear();FLASHES.clear();world=mc.level;
            if(beforeMech!=null)mc.options.setCameraType(beforeMech);
            else if(beforeCast!=null)mc.options.setCameraType(beforeCast);
            beforeMech=null;beforeCast=null;
        }
    }
    public static void tick() {
        ensureWorld();
        if(world!=null) FLASHES.removeIf(f->world.getGameTime()-f.born>65);
        var s=local();
        if(world!=null&&s!=null&&s.ultimateStart()>=0){
            long age=world.getGameTime()-s.ultimateStart();
            Minecraft.getInstance().options.setCameraType(age<CombatRules.DIVE_START?CameraType.THIRD_PERSON_FRONT:CameraType.THIRD_PERSON_BACK);
        }
    }
    public static SamNetwork.State local() {
        Minecraft mc=Minecraft.getInstance();return mc.player==null?null:PLAYERS.get(mc.player.getId());
    }
    public static void accept(SamNetwork.State state) {
        ensureWorld();
        SamNetwork.State old=PLAYERS.put(state.entityId(),state);
        Minecraft mc=Minecraft.getInstance();
        if(mc.player==null||state.entityId()!=mc.player.getId())return;
        if(state.active()&&(old==null||!old.active())) {
            beforeMech=mc.options.getCameraType();mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        }
        if(state.ultimateStart()>=0&&(old==null||old.ultimateStart()<0)) {
            beforeCast=mc.options.getCameraType();mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
        }
        if(state.ultimateStart()<0&&old!=null&&old.ultimateStart()>=0&&beforeCast!=null) {mc.options.setCameraType(beforeCast);beforeCast=null;}
        if(!state.active()&&beforeMech!=null) {mc.options.setCameraType(beforeMech);beforeMech=null;}
    }
    public static void accept(SamNetwork.Toughness data) {ensureWorld();TOUGHNESS.put(data.entityId(),data);}
    public static void impact(SamNetwork.Impact data) {
        ensureWorld();if(world!=null)FLASHES.add(new Flash(data.target(),data.radius(),data.kind(),data.damage(),world.getGameTime()));
    }
}
