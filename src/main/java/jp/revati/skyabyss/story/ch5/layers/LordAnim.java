package jp.revati.skyabyss.story.ch5.layers;

import net.minecraft.world.entity.LivingEntity;

// 層の主・縄張りの主の動きの合図 (戦いの決まり → 見た目)。見た目の担当 (枝 wip/abyss-boss-models の abyss.boss.BossLook) がここに一つの受け口をつなぐ。
//   名前: idle・move はふだんの動き (描画の側で決める)。技の溜め windup("attack_<技>", 予告の tick)、当たり strike("attack_<技>")、段が変わる phase(n)、倒れる death
//   技の名前 (layers/LordMoves の Attack.anim):
//     骨 rib_sweep・bone_spikes・marrow_quake・bone_rain・snuff
//     塩 shell_slam・brine_spray・salt_pillars・tide_ring・shell_roll・crush
//     雷 jolt_line・static_field・thunder_cross・chain_burst・split
//     灰 ember_claw・ash_breath・cinder_rain・magma_burst・lunge・roar
//     縄張り (塩・灰の縄張りの主) elite_slam・elite_charge・elite_spit
//   つないでいない間は何もしない (戦いの決まりは見た目に左右されない)
public final class LordAnim {
    public interface Hook {
        void windup(LivingEntity e, String anim, int ticks);

        void strike(LivingEntity e, String anim);

        void phase(LivingEntity e, int phase);

        void death(LivingEntity e);
    }

    public static volatile Hook hook;

    private LordAnim() {
    }

    public static void windup(LivingEntity e, String anim, int ticks) {
        Hook h = hook;
        if (h != null && e != null) {
            h.windup(e, anim, ticks);
        }
    }

    public static void strike(LivingEntity e, String anim) {
        Hook h = hook;
        if (h != null && e != null) {
            h.strike(e, anim);
        }
    }

    public static void phase(LivingEntity e, int phase) {
        Hook h = hook;
        if (h != null && e != null) {
            h.phase(e, phase);
        }
    }

    public static void death(LivingEntity e) {
        Hook h = hook;
        if (h != null && e != null) {
            h.death(e);
        }
    }
}
