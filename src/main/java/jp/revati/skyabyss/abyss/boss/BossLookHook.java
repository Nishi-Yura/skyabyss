package jp.revati.skyabyss.abyss.boss;

import java.util.Map;

import jp.revati.skyabyss.story.ch5.layers.LordAnim;
import net.minecraft.world.entity.LivingEntity;

// 層の主の戦いの合図 (story/ch5/layers/LordAnim の名前) を、見た目 (BossLook の技の番号) へ渡す。
// 技の名前は主ごとに細かいが、見た目の動きは九つなので、形の近いものへ寄せる
public final class BossLookHook implements LordAnim.Hook {
    private static final Map<String, Integer> ACT = Map.ofEntries(
            // 骨
            Map.entry("attack_rib_sweep", BossLook.SWEEP), Map.entry("attack_bone_spikes", BossLook.SLAM), Map.entry("attack_marrow_quake", BossLook.SLAM),
            Map.entry("attack_bone_rain", BossLook.BOLTS), Map.entry("attack_snuff", BossLook.SNUFF),
            // 塩
            Map.entry("attack_shell_slam", BossLook.SLAM), Map.entry("attack_brine_spray", BossLook.BOLTS), Map.entry("attack_salt_pillars", BossLook.SLAM),
            Map.entry("attack_tide_ring", BossLook.SWEEP), Map.entry("attack_shell_roll", BossLook.CHARGE), Map.entry("attack_crush", BossLook.CRUSH),
            // 雷
            Map.entry("attack_jolt_line", BossLook.CHARGE), Map.entry("attack_static_field", BossLook.BOLTS), Map.entry("attack_thunder_cross", BossLook.BOLTS),
            Map.entry("attack_chain_burst", BossLook.SWEEP), Map.entry("attack_split", BossLook.SPLIT),
            // 灰
            Map.entry("attack_ember_claw", BossLook.SWEEP), Map.entry("attack_ash_breath", BossLook.BOLTS), Map.entry("attack_cinder_rain", BossLook.BOLTS),
            Map.entry("attack_magma_burst", BossLook.SLAM), Map.entry("attack_lunge", BossLook.CHARGE), Map.entry("attack_roar", BossLook.ROAR),
            // 縄張りの主
            Map.entry("attack_elite_slam", BossLook.SLAM), Map.entry("attack_elite_charge", BossLook.CHARGE), Map.entry("attack_elite_spit", BossLook.BOLTS));

    private BossLookHook() {
    }

    public static void install() {
        LordAnim.hook = new BossLookHook();
    }

    private static int code(String anim) {
        return ACT.getOrDefault(anim, BossLook.SLAM);
    }

    @Override
    public void windup(LivingEntity e, String anim, int ticks) {
        BossLook.windup(e, code(anim), ticks);
    }

    @Override
    public void strike(LivingEntity e, String anim) {
        BossLook.strike(e, code(anim));
    }

    @Override
    public void phase(LivingEntity e, int phase) {
        BossLook.setPhase(e, phase);
    }

    @Override
    public void death(LivingEntity e) {
        // 倒れる動きは BossLook の描画が体の死で流す (DEATH_TICKS)
    }
}
