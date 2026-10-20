package dev.codex.fireflysam.client;

import dev.codex.fireflysam.CombatRules;
import dev.codex.fireflysam.SamNetwork;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import org.joml.Vector3f;

public final class SamAnimation {
    private SamAnimation(){}
    private static void put(Map<String,Vector3f> pose,String bone,float x,float y,float z){pose.put(bone,new Vector3f(x,y,z));}
    private static final SwingPose REST = new SwingPose(0,0,0);
    public record SwingPose(float windup,float strike,float followThrough) {}

    /** The vanilla six-tick swing clock, normalized for both the body and first-person hands. */
    public static float swingPhase(Player player,float partial){
        if(!player.swinging)return -1;
        return Mth.clamp((player.swingTime+1+partial)/6f,0,1);
    }
    public static SwingPose swingPose(Player player,InteractionHand hand,float partial){
        float phase=swingPhase(player,partial);
        boolean lead=player.swingingArm==null||player.swingingArm==hand;
        return swingPose(phase,lead);
    }
    public static SwingPose swingPose(float phase,boolean lead){
        if(phase<0)return REST;
        // Let the lead blade clear first; the support blade follows through
        // lower and slightly later so the pair reads as one flowing combo.
        float delay=lead?0:.07f;
        float t=Mth.clamp((phase-delay)/(1-delay),0,1);
        float strength=lead?1:.90f;
        return new SwingPose(
            pulse(t,0,.18f,.48f)*strength,
            pulse(t,.10f,.43f,.84f)*strength,
            pulse(t,.38f,.76f,1)*strength);
    }
    public static float attackAmount(Player player,float partial){
        InteractionHand leadHand=player.swingingArm==null?InteractionHand.MAIN_HAND:player.swingingArm;
        SwingPose motion=swingPose(player,leadHand,partial);
        return Mth.clamp(motion.strike()+motion.followThrough()*.55f+motion.windup()*.1f,0,1);
    }
    public static Map<String,Vector3f> pose(Player player,SamNetwork.State state,float partial){
        Map<String,Vector3f> pose=new HashMap<>();float time=player.tickCount+partial;
        float stride=walkStride(player,partial);
        float movement=walkAmount(player,partial);
        // Preserve the authored Quaternius step timing but soften the retargeted
        // rotations for SAM's heavier armor. The source's high knee lift and
        // pelvis drop make the mech hunch, so keep a short stride and steady core.
        SamWalkCycle.Sample walk=SamWalkCycle.sample(stride/((float)Math.PI*2));
        Vector3f torsoWalk=walk.rotation("torso"),hipsWalk=walk.rotation("hips"),headWalk=walk.rotation("head");
        Vector3f rightArmWalk=walk.rotation("right_arm"),leftArmWalk=walk.rotation("left_arm");
        Vector3f rightForearmWalk=walk.rotation("right_forearm"),leftForearmWalk=walk.rotation("left_forearm");
        Vector3f rightHandWalk=walk.rotation("right_hand"),leftHandWalk=walk.rotation("left_hand");
        Vector3f rightLegWalk=walk.rotation("right_leg"),leftLegWalk=walk.rotation("left_leg");
        Vector3f rightShinWalk=walk.rotation("right_shin"),leftShinWalk=walk.rotation("left_shin");
        Vector3f rightFootWalk=walk.rotation("right_foot"),leftFootWalk=walk.rotation("left_foot");
        boolean rightMain=player.getMainArm()==HumanoidArm.RIGHT;
        boolean mainHandSwing=player.swingingArm==null||player.swingingArm==InteractionHand.MAIN_HAND;
        boolean rightLead=mainHandSwing==rightMain;
        float phase=swingPhase(player,partial);
        SwingPose right=swingPose(phase,rightLead),left=swingPose(phase,!rightLead);
        float rightWind=right.windup(),rightStrike=right.strike(),rightFollow=right.followThrough();
        float leftWind=left.windup(),leftStrike=left.strike(),leftFollow=left.followThrough();
        float turn=rightLead?1:-1;
        float idle=Mth.sin(time*.07f);
        put(pose,"torso",torsoWalk.x*movement*.24f+.012f*idle-.055f*(rightWind+leftWind)+.085f*(rightStrike+leftStrike)+.025f*(rightFollow+leftFollow),
            torsoWalk.y*movement*.24f+turn*(.11f*(rightStrike-leftStrike)-.055f*(rightFollow-leftFollow)),torsoWalk.z*movement*.24f+.018f*idle);
        put(pose,"hips",hipsWalk.x*movement*.22f,-turn*.035f*(rightStrike-leftStrike)+hipsWalk.y*movement*.22f,hipsWalk.z*movement*.22f);
        put(pose,"__offset_hips",0,walk.hipY()*movement*.35f,0);
        put(pose,"head",player.getXRot()*Mth.DEG_TO_RAD*.55f+headWalk.x*movement*.18f,
            Mth.wrapDegrees(player.getYHeadRot()-player.yBodyRot)*Mth.DEG_TO_RAD*.6f+headWalk.y*movement*.18f,headWalk.z*movement*.18f);
        // Each blade now follows one eased wind-up / cut / follow-through arc.
        // Mirrored shoulder, wrist and blade angles keep both swords moving
        // through their existing hand-parented bone chains.
        put(pose,"right_arm",rightArmWalk.x*movement*.12f-.08f-.13f*rightWind+.72f*rightStrike+.18f*rightFollow,
            rightArmWalk.y*movement*.12f-.05f*rightWind-.10f*rightStrike+.035f*rightFollow,
            .65f+rightArmWalk.z*movement*.12f+.09f*rightWind-.22f*rightStrike+.10f*rightFollow);
        put(pose,"left_arm",leftArmWalk.x*movement*.12f-.08f-.13f*leftWind+.72f*leftStrike+.18f*leftFollow,
            leftArmWalk.y*movement*.12f+.05f*leftWind+.10f*leftStrike-.035f*leftFollow,
            -.65f+leftArmWalk.z*movement*.12f-.09f*leftWind+.22f*leftStrike-.10f*leftFollow);
        put(pose,"right_forearm",rightForearmWalk.x*movement*.10f-.10f+.07f*rightWind-.29f*rightStrike+.17f*rightFollow,
            rightForearmWalk.y*movement*.10f-.05f*rightStrike,
            .20f+rightForearmWalk.z*movement*.10f-.07f*rightStrike+.05f*rightFollow);
        put(pose,"left_forearm",leftForearmWalk.x*movement*.10f-.10f+.07f*leftWind-.29f*leftStrike+.17f*leftFollow,
            leftForearmWalk.y*movement*.10f+.05f*leftStrike,
            -.20f+leftForearmWalk.z*movement*.10f+.07f*leftStrike-.05f*leftFollow);
        put(pose,"right_hand",rightHandWalk.x*movement*.08f-.20f-.03f*rightWind+.10f*rightStrike-.04f*rightFollow,
            rightHandWalk.y*movement*.08f-.025f*rightStrike,
            .05f+rightHandWalk.z*movement*.08f+.07f*rightWind-.12f*rightStrike+.06f*rightFollow);
        put(pose,"left_hand",leftHandWalk.x*movement*.08f-.20f-.03f*leftWind+.10f*leftStrike-.04f*leftFollow,
            leftHandWalk.y*movement*.08f+.025f*leftStrike,
            -.05f+leftHandWalk.z*movement*.08f-.07f*leftWind+.12f*leftStrike-.06f*leftFollow);
        // A restrained shoulder translation adds reach without pulling the
        // hand bones away from the imported sword grips.
        put(pose,"__offset_right_arm",-.025f*rightStrike,.01f*rightStrike,-.035f*rightStrike);
        put(pose,"__offset_left_arm",.025f*leftStrike,.01f*leftStrike,-.035f*leftStrike);
        put(pose,"right_leg",rightLegWalk.x*movement*.34f,rightLegWalk.y*movement*.32f,rightLegWalk.z*movement*.30f);
        put(pose,"left_leg",leftLegWalk.x*movement*.34f,leftLegWalk.y*movement*.32f,leftLegWalk.z*movement*.30f);
        put(pose,"right_shin",rightShinWalk.x*movement*.22f,rightShinWalk.y*movement*.18f,rightShinWalk.z*movement*.18f);
        put(pose,"left_shin",leftShinWalk.x*movement*.22f,leftShinWalk.y*movement*.18f,leftShinWalk.z*movement*.18f);
        put(pose,"right_foot",rightFootWalk.x*movement*.30f,rightFootWalk.y*movement*.28f,rightFootWalk.z*movement*.25f);
        put(pose,"left_foot",leftFootWalk.x*movement*.30f,leftFootWalk.y*movement*.28f,leftFootWalk.z*movement*.25f);
        // The imported blades run along local +Y. These bind corrections keep
        // both tips pointed forward at rest while the wrist carries each cut.
        put(pose,"right_blade",-1.237f-.08f*rightStrike+.06f*rightFollow,-.03f*rightWind-.06f*rightStrike,
            .722f+.12f*rightWind-.27f*rightStrike+.16f*rightFollow);
        // The support blade takes a lower backhand rather than mirroring the
        // lead blade. Keeping its wrist roll separate prevents the meshes from
        // collapsing into one parallel, clashing-looking line.
        put(pose,"left_blade",-1.406f+.12f*leftStrike-.04f*leftFollow,.03f*leftWind-.04f*leftStrike,
            .258f-.12f*leftWind-.18f*leftStrike+.10f*leftFollow);
        float now=player.level().getGameTime()+partial;
        if(state.ultimateStart()>=0){
            float age=now-state.ultimateStart();
            float open=ease((age-12)/22),windup=ease((age-86)/22),dive=ease((age-CombatRules.DIVE_START)/20f),recover=ease((age-141)/19f),weight=1-recover;
            put(pose,"__neutral_bind",open*weight,0,0);
            put(pose,"torso",(-.08f*windup+.75f*dive)*weight,0,0);
            put(pose,"head",(.10f*windup+.12f*dive)*weight,0,0);
            put(pose,"right_arm",(-.35f*(1-open)-2.3f*windup+1.55f*dive)*weight,0,(.72f*open-.55f*windup-.9f*dive)*weight);
            put(pose,"left_arm",(-.35f*(1-open)-2.3f*windup+1.55f*dive)*weight,0,(-.72f*open+.55f*windup+.9f*dive)*weight);
            put(pose,"right_forearm",(-.15f*(1-open)-.25f*windup)*weight,0,.18f*open*weight);
            put(pose,"left_forearm",(-.15f*(1-open)-.25f*windup)*weight,0,-.18f*open*weight);
            put(pose,"right_hand",0,0,.08f*open*weight);put(pose,"left_hand",0,0,-.08f*open*weight);
            put(pose,"right_leg",(.12f*windup-.35f*dive)*weight,0,-.025f*open*weight);
            put(pose,"left_leg",(-.10f*windup+.25f*dive)*weight,0,.025f*open*weight);
            put(pose,"right_shin",.30f*windup*weight,0,0);put(pose,"left_shin",.25f*windup*weight,0,0);
            put(pose,"right_foot",-.14f*open*weight,0,0);put(pose,"left_foot",-.14f*open*weight,0,0);
            // The wing reveal neutralizes the imported bind quaternions. Blend
            // from the normal grip correction to a straight-forward blade as
            // that bind alignment opens, then restore the normal grip on exit.
            float forwardBlend=open*weight;
            float rightBladeX=Mth.lerp(forwardBlend,-1.237f,-Mth.HALF_PI);
            float leftBladeX=Mth.lerp(forwardBlend,-1.406f,-Mth.HALF_PI);
            put(pose,"right_blade",rightBladeX,0,.722f*(1-forwardBlend)-.35f*windup);
            put(pose,"left_blade",leftBladeX,0,.258f*(1-forwardBlend)+.35f*windup);
        }else if(now-state.transformStart()<18){
            float amount=1-ease((now-state.transformStart())/18);put(pose,"right_arm",-.35f*amount,0,.5f*amount);put(pose,"left_arm",-.35f*amount,0,-.5f*amount);
        }
        return pose;
    }
    public static float walkAmount(Player player,float partial){
        // WalkAnimationState.speed tracks blocks moved per tick (typically
        // about 0.2 while walking), so normalize it before applying mech poses.
        return Mth.clamp(player.walkAnimation.speed(partial)*5f,0,1);
    }
    public static float walkStride(Player player,float partial){return player.walkAnimation.position(partial)*.6662f;}
    public static float wingAmount(float age){return ease((age-10)/27)*(1-ease((age-132)/18));}
    private static float pulse(float t,float start,float peak,float end){
        if(t<=start||t>=end)return 0;
        if(t<peak)return ease((t-start)/(peak-start));
        return 1-ease((t-peak)/(end-peak));
    }
    public static float ease(float value){float t=Mth.clamp(value,0,1);return t*t*(3-2*t);}
}
