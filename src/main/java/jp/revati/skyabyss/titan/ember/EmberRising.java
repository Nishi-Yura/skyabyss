package jp.revati.skyabyss.titan.ember;

import java.util.ArrayList;
import java.util.List;

import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.progress.Progression;
import jp.revati.skyabyss.rpg.Growth;
import jp.revati.skyabyss.story.Chapters;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

// 四章の中盤の出来事「夜の浮上」(docs/design/13 の 2.3、01 の 4.2)。ワールドで最初に余燼の巨神が浮かび上がる夜に、一度だけ起きる。
// 丘が持ち上がって巨神の形になり、地面が一度だけ大きく揺れる。見た目は粒・音・画面の揺れだけで、ブロックは一つも壊さず・置かない
// (体は初めからエンティティだけ。プレイヤーの建物は触らない。自己テストで確かめる)。
// 呼ぶのは EmberTitanEntity.startRise (浮かび始める一回)。ワールドの記録 (EmberChapterData) で二度目からは何もしない。
// 四章を遊んでいる人 (段階 3) だけに語りと経験値を出す。ほかの人にはふだんの浮上のまま (遠くの揺れだけは聞こえる)
public final class EmberRising {
    // 揺れが届く距離 / 語りと経験値の対象 (見ていた人)
    public static final double SHAKE_RANGE = 240;
    public static final double WITNESS_RANGE = 200;
    public static final int SHAKE_TICKS = 100;
    public static final float SHAKE_STRENGTH = 0.8f;
    public static final int WITNESS_XP = 250;
    public static final String LINE_RISE = "message.skyabyss.ember_ch4.rise";
    public static final String DISCOVER_KEY = "ch4:rise";

    private EmberRising() {
    }

    // 浮かび始めるとき。初めての夜なら出来事を起こして true
    public static boolean onRiseStart(ServerLevel level, EmberTitanEntity titan) {
        long day = level.getDayTime() / 24000L;
        if (!EmberChapterData.get(level.getServer()).markRisen(day)) {
            return false;
        }
        perform(level, titan, level.players());
        return true;
    }

    // 出来事の中身 (ブロックには触らない)。語りを出した人 (四章を遊んでいる人) を返す
    static List<ServerPlayer> perform(ServerLevel level, EmberTitanEntity titan, List<ServerPlayer> players) {
        List<ServerPlayer> told = new ArrayList<>();
        Vec3 c = titan.position().add(EmberTitanEntity.offset(titan.getYRot(), 0, 0, 10));
        double groundY = EmberGround.surfaceY(level, c.x, c.z);
        if (Double.isNaN(groundY)) {
            groundY = titan.getY() + EmberTitanEntity.BURROW_SINK;
        }
        BlockState ground = level.getBlockState(BlockPos.containing(c.x, groundY - 1, c.z));
        List<ServerPlayer> near = new ArrayList<>();
        for (ServerPlayer p : players) {
            if (!p.isSpectator() && Math.hypot(p.getX() - c.x, p.getZ() - c.z) < SHAKE_RANGE) {
                near.add(p);
            }
        }
        for (ServerPlayer p : near) {
            EmberSurfaceNetwork.send(p, new EmberSurfaceNetwork.Tremor(SHAKE_TICKS, SHAKE_STRENGTH));
            p.playNotifySound(EmberTitanSetup.RUMBLE.get(), SoundSource.AMBIENT, 1.5f, 0.5f);
            // 丘が持ち上がる: 地面の土が舞い上がる (粒だけ)
            if (!ground.isAir()) {
                level.sendParticles(p, new BlockParticleOption(ParticleTypes.BLOCK, ground), true, c.x, groundY + 0.5, c.z, 160, 28, 1.5, 28, 0.15);
            }
            level.sendParticles(p, ParticleTypes.LARGE_SMOKE, true, c.x, groundY + 1, c.z, 40, 24, 2, 24, 0.03);
            if (Chapters.of(p) == Chapters.EMBER && Progression.stage(p) == Chapters.EMBER.stage()) {
                Notice.narrate(p, LINE_RISE);
                told.add(p);
                if (Math.hypot(p.getX() - c.x, p.getZ() - c.z) < WITNESS_RANGE && Growth.discover(p, DISCOVER_KEY)) {
                    Growth.addXp(p, WITNESS_XP, "growth.skyabyss.reason.new_sight");
                }
            }
        }
        return told;
    }
}