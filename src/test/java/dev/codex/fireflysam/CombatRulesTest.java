package dev.codex.fireflysam;

/** Standalone executable scenarios; no JUnit download needed. */
public final class CombatRulesTest {
    private static int assertions;
    public static void main(String[]args){
        check(CombatRules.REQUIRED_HITS==10,"ultimate keeps the requested ten-hit charge");
        check(CombatRules.COMBO_WINDOW==120,"partial ten-hit charge keeps its six-second window");
        var c=new CombatRules.Combo();
        check(!c.cast(false),"cannot cast outside mech");
        check(!c.hit(0,false,true,true),"normal form cannot charge");
        check(!c.hit(0,true,false,true),"uncharged attack cannot charge");
        check(!c.hit(0,true,true,false),"sweep/air/unconfirmed damage cannot charge");
        for(int i=0;i<9;i++)check(c.hit(i*16,true,true,true),"charged primary hit counted");
        check(c.hits()==9&&!c.cast(true),"nine hits insufficient");
        check(!c.hit(128,true,true,true),"duplicate primary hit does not double count");
        check(c.hit(144,true,true,true)&&c.ready(),"tenth hit unlocks ultimate");
        c.tick(10000);check(c.ready(),"complete charge survives idle");
        check(!c.cast(false),"ready ultimate still requires SAM");
        check(c.cast(true)&&c.hits()==0&&c.casting(),"cast atomically consumes charge");
        check(!c.cast(true)&&!c.hit(10001,true,true,true),"cannot recast or recharge during animation");
        c.finish();check(!c.ready()&&!c.casting(),"finish requires a fresh combo");
        c.hit(11000,true,true,true);c.tick(11120);check(c.hits()==1,"six second boundary inclusive");
        c.tick(11121);check(c.hits()==0,"incomplete combo expires");
        c.hit(11200,true,true,true);c.reset();check(c.hits()==0&&!c.casting(),"switching off clears combat state");
        check(CombatRules.toughness(1,0)>=24,"minimum toughness");
        check(CombatRules.toughness(1000000,200)<=240,"boss toughness bounded");
        check(CombatRules.breakDamage(100,150)>CombatRules.breakDamage(100,0),"Break Effect improves break burst");
        check(CombatRules.superBreakDamage(24,150)>CombatRules.superBreakDamage(16,150),"SAM break efficiency increases super break");
        check(CombatRules.ultimateDamage(70,10,false)==140,"ultimate keeps its doubled base damage against unbroken enemies");
        check(CombatRules.ultimateDamage(70,10,true)==1400,"ultimate deals ten times its base hit against weakness-broken enemies");
        check(CombatRules.craterFloor(0,24,10)==-10,"center has configured crater depth");
        check(CombatRules.craterFloor(24,24,10)==0&&CombatRules.craterFloor(25,24,10)==0,"crater does not extend beyond boundary");
        check(CombatRules.craterFloor(12,24,10)>-10&&CombatRules.craterFloor(12,24,10)<0,"crater slopes toward edge");
        System.out.println("PASS: "+assertions+" combat assertions");
    }
    private static void check(boolean condition,String message){assertions++;if(!condition)throw new AssertionError(message);}
}
