package dev.codex.fireflysam.client;

import dev.codex.fireflysam.FireflySam;
import dev.codex.fireflysam.CombatRules;
import dev.codex.fireflysam.SamNetwork;
import java.util.HashSet;
import java.util.Set;
import java.util.ArrayList;
import java.util.List;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.util.Mth;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

/** Opt-in development harness. Never creates or changes a world during normal play. */
final class SamAutoTest {
    private static boolean opened,sentTransform,sentUltimate,tutorialDisabled,walkReported,testFinished;private static int frames,lastObservedCombo=-1;private static long joined=-1,impact=-1,attackShot=-1,attackFirstPersonShot=-1,attackFollowShot=-1;
    private static boolean cameraOverrideInjected,cameraEnforcedReveal,cameraEnforcedDive;
    private static final Set<String> SHOTS=new HashSet<>();
    private static final List<String> PENDING=new ArrayList<>();
    static boolean cameraAssist(){return Boolean.getBoolean("fireflysam.test")&&!testFinished;}
    static void tick(Minecraft mc){
        if(!Boolean.getBoolean("fireflysam.test")||testFinished)return;
        if(!tutorialDisabled){mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);tutorialDisabled=true;}
        if(!opened&&mc.level==null&&mc.getOverlay()==null&&(mc.screen instanceof TitleScreen||mc.screen instanceof AccessibilityOnboardingScreen)&&++frames>20){
            opened=true;String name="SAM-Lab-"+System.currentTimeMillis();
            mc.options.pauseOnLostFocus=false;mc.options.renderDistance().set(8);mc.options.simulationDistance().set(6);mc.options.guiScale().set(2);
            mc.createWorldOpenFlows().createFreshLevel(name,new LevelSettings(name,GameType.CREATIVE,false,Difficulty.NORMAL,true,new GameRules(),WorldDataConfiguration.DEFAULT),new WorldOptions(71,false,false),
                registries->registries.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),mc.screen);
        }
        if(mc.level==null||mc.player==null)return;if(joined<0)joined=mc.level.getGameTime();
        long age=mc.level.getGameTime()-joined;var s=SamClientState.local();
        if(age<280){mc.player.setYRot(0);mc.player.setXRot(12);}
        if(age>=43&&!sentTransform){KeyMapping.click(InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_R));sentTransform=true;}
        if(age>=274&&!sentUltimate&&s!=null&&s.combo()==10){KeyMapping.click(InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_G));sentUltimate=true;}
        if(s!=null&&s.active()&&s.ultimateStart()<0&&age<260){
            // Server-side test attacks do not echo an animate packet to their
            // own client. Mirror the confirmed hit into the local swing state
            // so this screenshot exercises the same client pose as mouse input.
            if(lastObservedCombo<0)lastObservedCombo=s.combo();
            else if(s.combo()>lastObservedCombo){mc.player.swing(net.minecraft.world.InteractionHand.MAIN_HAND);lastObservedCombo=s.combo();}
            mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
            // Exercise the real walk-animation input in the isolated test world
            // and capture it before the combat sequence starts.
            if(age>=74&&age<84)mc.player.setDeltaMovement(0,mc.player.getDeltaMovement().y,-.12);
            if(age==77)shot(mc,"02a-sam-walking-stance");
            if(age==82){shot(mc,"02-sam-walking-animation");walkingPoseReport(mc);}
            if(age==118)shot(mc,"02c-sam-forward-pointing-swords");
            if(age>72)shot(mc,"01-sam-transformation");
            if(s.combo()>=6)shot(mc,"02-combo-and-break-bar");
            if(s.combo()==10)shot(mc,"03-ultimate-unlocked");
            if(s.combo()>0&&attackShot==-1)attackShot=age+2;
            if(attackShot>=0&&age>=attackShot){shot(mc,"10-sam-attack-animation");attackPoseReport(mc,s);attackShot=-2;attackFirstPersonShot=age+1;attackFollowShot=age+4;}
            if(attackFirstPersonShot>=0&&age>=attackFirstPersonShot){mc.options.setCameraType(CameraType.FIRST_PERSON);shot(mc,"10b-sam-first-person-dual-blade-attack");attackFirstPersonShot=-2;}
            if(attackFollowShot>=0&&age>=attackFollowShot){mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);shot(mc,"11-sam-attack-follow-through");attackFollowShot=-2;}
        }
        if(s!=null&&s.ultimateStart()>=0){
            long castAge=mc.level.getGameTime()-s.ultimateStart();
            if(castAge>=30&&!cameraOverrideInjected){mc.options.setCameraType(CameraType.FIRST_PERSON);cameraOverrideInjected=true;}
            if(cameraOverrideInjected&&castAge>=31&&castAge<CombatRules.DIVE_START&&!cameraEnforcedReveal)
                cameraEnforcedReveal=mc.options.getCameraType()==CameraType.THIRD_PERSON_FRONT;
            if(castAge>=CombatRules.DIVE_START+2&&!cameraEnforcedDive)
                cameraEnforcedDive=mc.options.getCameraType()==CameraType.THIRD_PERSON_BACK;
            if(castAge>=14)shot(mc,"04-crossed-blade-ignition");
            if(castAge>=54)shot(mc,"05-sam-aerial-ultimate");
            if(castAge>=68){shot(mc,"05b-complete-combustion-wings");modelReport(mc);}
            if(castAge>=CombatRules.DIVE_START+10)shot(mc,"06-deathstar-dive");
        }
        for(var f:SamClientState.FLASHES)if(f.kind()==0&&impact<0)impact=f.born();
        if(impact>=0&&mc.level.getGameTime()-impact>12)shot(mc,"07-impact-shockwave");
        if(impact>=0&&s!=null&&s.ultimateStart()<0&&mc.level.getGameTime()-impact>42)restoreReport(mc);
        if(impact>=0&&mc.level.getGameTime()-impact>115){mc.options.setCameraType(CameraType.FIRST_PERSON);mc.player.setYRot(0);mc.player.setXRot(38);shot(mc,"08-terrain-crater");}
        if(impact>=0&&s!=null&&!s.active()&&mc.level.getGameTime()-impact>155){
            mc.options.setCameraType(CameraType.FIRST_PERSON);shot(mc,"09-supplied-sword-first-person");testFinished=true;
        }
    }
    private static boolean restoredReported;
    private static void restoreReport(Minecraft mc){
        if(restoredReported)return;restoredReported=true;
        try{
            var file=mc.gameDirectory.toPath().resolve("sam-render-report.json");
            var report=com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(file)).getAsJsonObject();
            report.addProperty("first_person_override_attempted",cameraOverrideInjected);
            report.addProperty("camera_forced_third_person_reveal",cameraEnforcedReveal);
            report.addProperty("camera_forced_third_person_dive",cameraEnforcedDive);
            report.addProperty("camera_restored_after_cast",mc.options.getCameraType()==CameraType.THIRD_PERSON_FRONT);
            report.addProperty("cinematic_state_cleared",SamClientState.local().ultimateStart()<0);
            java.nio.file.Files.writeString(file,new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(report));
        }catch(Exception error){FireflySam.LOGGER.log(System.Logger.Level.ERROR,"Cannot write camera restoration report",error);}
    }
    private static boolean reported;
    private static void walkingPoseReport(Minecraft mc){
        if(walkReported)return;walkReported=true;
        try{
            var player=mc.player;float movement=SamAnimation.walkAmount(player,0),stride=SamAnimation.walkStride(player,0);
            var pose=SamAnimation.pose(player,SamClientState.local(),0);
            float walkPhase=stride/((float)Math.PI*2);
            var current=SamWalkCycle.sample(walkPhase);var opposite=SamWalkCycle.sample(walkPhase+.5f);
            float right=pose.get("right_leg").x,left=pose.get("left_leg").x;
            float time=(player.tickCount+0)*.05f;
            var report=new java.util.LinkedHashMap<String,Object>();
            report.put("movement_amount",movement);report.put("stride_phase_radians",stride);
            report.put("right_leg_swing_radians",right);report.put("left_leg_swing_radians",left);
            report.put("walk_source_clip",SamWalkCycle.clipName());
            report.put("authored_clip_loaded",SamWalkCycle.available());
            report.put("retargeted_bone_count",SamWalkCycle.retargetedBoneCount());
            report.put("walk_cycle_phase",walkPhase-(float)Math.floor(walkPhase));
            report.put("hip_bob_y",current.hipY()*movement*.35f);
            report.put("torso_moves_with_cycle",Math.abs(current.rotation("torso").y)>.02f);
            report.put("cycle_changes_with_phase",Math.abs(current.rotation("right_leg").x-opposite.rotation("right_leg").x)>.25f);
            float kneePeak=Math.max(Math.abs(pose.get("right_shin").x),Math.abs(pose.get("left_shin").x));
            var torso=current.rotation("torso");
            report.put("stride_is_controlled",Math.abs(right)<.45f&&Math.abs(left)<.45f);
            report.put("knee_lift_is_subtle",kneePeak<.32f);
            report.put("body_bounce_is_centered",Math.abs(current.hipY())<.07f);
            report.put("torso_stays_upright",Math.abs(torso.x*.24f)<.04f&&Math.abs(torso.y*.24f)<.04f&&Math.abs(torso.z*.24f)<.04f);
            report.put("arms_are_braced",Math.abs(pose.get("right_arm").x)<.30f&&Math.abs(pose.get("left_arm").x)<.30f);
            report.put("aura_ribbon_count",SamAura.ribbonCount());report.put("aura_spark_count",SamAura.sparkCount());
            report.put("aura_renders_in_sam_mode",SamClientState.local()!=null&&SamClientState.local().active());
            report.put("walking_aura_intensity",SamAura.intensity(movement,time));
            report.put("aura_strengthens_with_movement",SamAura.intensity(1,time)>SamAura.intensity(0,time));
            java.nio.file.Files.writeString(mc.gameDirectory.toPath().resolve("sam-walk-report.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(report));
        }catch(Exception error){FireflySam.LOGGER.log(System.Logger.Level.ERROR,"Cannot write walk and aura report",error);}
    }
    private static void modelReport(Minecraft mc){
        if(reported)return;reported=true;
        try{
            var sam=SamMesh.get("sam_rig");var sword=SamMesh.get("firefly_blade");
            var report=new java.util.LinkedHashMap<String,Object>();
            report.put("sam_triangles",sam.triangleCount());report.put("sword_triangles",sword.triangleCount());
            report.put("supplied_sam_loaded",sam.suppliedModel()&&sam.triangleCount()==55874);
            report.put("supplied_sword_loaded",sword.suppliedModel()&&sword.triangleCount()==9180);
            int mobKills=mc.player.getStats().getValue(net.minecraft.stats.Stats.CUSTOM.get(net.minecraft.stats.Stats.MOB_KILLS));
            report.put("test_mob_kills",mobKills);
            report.put("sam_available_at_zero_kills",mobKills==0&&SamClientState.local().active());
            report.put("wing_reveal_front_camera",mc.options.getCameraType()==CameraType.THIRD_PERSON_FRONT);
            report.put("camera_forced_third_person_reveal",cameraEnforcedReveal);
            report.put("fps_during_wing_reveal",mc.getFps());
            java.nio.file.Files.writeString(mc.gameDirectory.toPath().resolve("sam-render-report.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(report));
        }catch(Exception error){FireflySam.LOGGER.log(System.Logger.Level.ERROR,"Cannot write supplied model render report",error);}
    }
    private static void attackPoseReport(Minecraft mc,SamNetwork.State state){
        try{
            var player=mc.player;var pose=SamAnimation.pose(player,state,0);
            var rightArm=pose.get("right_arm");var leftArm=pose.get("left_arm");
            float progress=player.getAttackAnim(0),attack=SamAnimation.attackAmount(player,0);
            var mainHandPose=SamAnimation.swingPose(player,net.minecraft.world.InteractionHand.MAIN_HAND,0);
            var offHandPose=SamAnimation.swingPose(player,net.minecraft.world.InteractionHand.OFF_HAND,0);
            float stride=SamAnimation.walkStride(player,0);
            var walk=SamWalkCycle.sample(stride/((float)Math.PI*2));
            float movement=SamAnimation.walkAmount(player,0);
            var report=new java.util.LinkedHashMap<String,Object>();
            report.put("minecraft_attack_progress",progress);report.put("swing_time",player.swingTime);
            report.put("attack_pose_amount",attack);report.put("vanilla_swing_active",player.swinging);
            report.put("swinging_arm",player.swingingArm==null?"none":player.swingingArm.name());
            float rightDelta=rightArm.x-(walk.rotation("right_arm").x*movement-.08f);
            float leftDelta=leftArm.x-(walk.rotation("left_arm").x*movement-.08f);
            float rightBladeDelta=pose.get("right_blade").z-.722f,leftBladeDelta=pose.get("left_blade").z-.258f;
            report.put("right_shoulder_attack_delta_radians",rightDelta);
            report.put("left_shoulder_attack_delta_radians",leftDelta);
            report.put("right_blade_attack_delta_radians",rightBladeDelta);
            report.put("left_blade_attack_delta_radians",leftBladeDelta);
            report.put("right_fist_animated",attack>.25f&&Math.abs(rightDelta)>.15f);
            report.put("left_fist_animated",attack>.25f&&Math.abs(leftDelta)>.15f);
            report.put("right_blade_animated",attack>.25f&&Math.abs(rightBladeDelta)>.05f);
            report.put("left_blade_animated",attack>.25f&&Math.abs(leftBladeDelta)>.05f);
            report.put("both_fists_animated",Boolean.TRUE.equals(report.get("right_fist_animated"))&&Boolean.TRUE.equals(report.get("left_fist_animated")));
            report.put("both_blades_animated",Boolean.TRUE.equals(report.get("right_blade_animated"))&&Boolean.TRUE.equals(report.get("left_blade_animated")));
            report.put("first_person_main_blade_animated",attack>.25f&&(mainHandPose.strike()+mainHandPose.followThrough())>.25f);
            report.put("first_person_offhand_blade_animated",attack>.25f&&(offHandPose.strike()+offHandPose.followThrough())>.25f);
            report.put("first_person_both_blades_animated",Boolean.TRUE.equals(report.get("first_person_main_blade_animated"))&&Boolean.TRUE.equals(report.get("first_person_offhand_blade_animated")));
            java.nio.file.Files.writeString(mc.gameDirectory.toPath().resolve("sam-attack-report.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(report));
        }catch(Exception error){FireflySam.LOGGER.log(System.Logger.Level.ERROR,"Cannot write attack pose report",error);}
    }
    private static void shot(Minecraft mc,String name){if(SHOTS.add(name))PENDING.add(name);}
    static void afterFrame(Minecraft mc){
        if(!Boolean.getBoolean("fireflysam.test")||PENDING.isEmpty())return;
        for(String name:PENDING){
            Screenshot.grab(mc.gameDirectory,name+".png",mc.getMainRenderTarget(),message->FireflySam.LOGGER.log(System.Logger.Level.INFO,"SAM screenshot: "+message.getString()));
            if(name.equals("02c-sam-forward-pointing-swords")||name.equals("05b-complete-combustion-wings"))reportSwordDirection(mc,name);
        }
        PENDING.clear();
    }
    private static void reportSwordDirection(Minecraft mc,String frame){
        var right=SamMesh.lastBladeDirection("right_blade");var left=SamMesh.lastBladeDirection("left_blade");
        if(right==null||left==null)return;
        float yaw=mc.player.yBodyRot*Mth.DEG_TO_RAD;
        var forward=new org.joml.Vector3f(-Mth.sin(yaw),0,Mth.cos(yaw));
        FireflySam.LOGGER.log(System.Logger.Level.INFO,"SAM_SWORD_DIRECTION {0} yaw={1} right={2} left={3} rightDot={4} leftDot={5}",
            frame,yaw,right,left,right.dot(forward),left.dot(forward));
    }
}
