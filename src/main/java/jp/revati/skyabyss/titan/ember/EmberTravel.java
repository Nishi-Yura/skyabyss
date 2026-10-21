package jp.revati.skyabyss.titan.ember;

import jp.revati.skyabyss.notice.Notice;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.progress.Progression;
import jp.revati.skyabyss.progress.Titans;
import jp.revati.skyabyss.titan.TitanGate;
import jp.revati.skyabyss.titan.ember.back.EmberBackLayout;
import jp.revati.skyabyss.titan.ember.back.EmberHeat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

// 地上と「余燼の背」(ember_back) を行き来させる (13 の 2.4〜2.5節)。
// - 乗る: 眠る巨神の熱の口 (EmberMouthEntity) に、吸うか止めるあいだに歩いて入る。画面が橙に沈み、呑みこむ音。
//   乗り物に乗っていれば降ろしてから渡す (乗り物は口の前に残る)。資格のない人は「吐き出した」と押し出す
// - 戻る: 背中の見送りの石に 0.5 秒。巨神が眠っていれば口の前 6 ブロックの地面へ (巨神に背を向ける)、潜っていれば
//   最後に眠った場所の口の前、記録もなければ共有スポーン地点。出る位置の地表を上から探して立たせ、熱は 0 に戻す
// - 灰の原 (体の外の平らな地面) に 5 秒いると到着点へ戻す。体の中心から 220 ブロックより外へ出るとすぐ到着点へ
// 乗る資格・待ち・乗り物から降ろす・0.5 秒は共通の TitanGate
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class EmberTravel {
    // 待ち 3 秒、見送りの石に 0.5 秒 (潮殻と同じ)
    public static final TitanGate PASSAGE = new TitanGate("ember", Titans.EMBER, 60, 10);
    // 灰の原に 5 秒いると到着点へ
    static final int PLAIN_PUSH_AFTER = 100;

    // 灰の原にいる人 → 何 tick いるか
    private static final Map<UUID, Integer> ON_PLAIN = new HashMap<>();

    private EmberTravel() {
    }

    // 熱の口に入った (眠っていて、口が開いているとき)。資格がなければ「吐き出した」と押し出す
    static boolean boardFromMouth(ServerPlayer player, EmberTitanEntity titan) {
        if (PASSAGE.coolingDown(player)) {
            return false;
        }
        if (!Progression.canBoard(player, Titans.EMBER)) {
            PASSAGE.checkBoard(player);
            Notice.warn(player, EmberIds.LANG_SURFACE + "spat_out");
            Vec3 out = EmberTitanEntity.offset(titan.getYRot(), 0, 0, 1);
            player.push(out.x * 1.2, 0.35, out.z * 1.2);
            player.hurtMarked = true;
            return false;
        }
        return PASSAGE.admit(player) && board(player);
    }

    // 背中へ渡す (コマンド・熱の口から)。乗れるのは、自分で雷翼の心臓を止めた人だけ (10 の 3 章)
    public static boolean board(ServerPlayer player) {
        if (!PASSAGE.checkBoard(player)) {
            return false;
        }
        ServerLevel back = player.server.getLevel(EmberIds.BACK);
        if (back == null) {
            SkyAbyss.LOGGER.error("Ember back dimension is missing");
            return false;
        }
        if (player.isPassenger()) {
            player.stopRiding();
        }
        PASSAGE.startCooldown(player);
        ON_PLAIN.remove(player.getUUID());
        if (player.level() instanceof ServerLevel from) {
            from.playSound(null, player.getX(), player.getY(), player.getZ(), EmberTitanSetup.SWALLOW.get(), SoundSource.PLAYERS, 1.5f, 1.0f);
            from.sendParticles(ParticleTypes.WHITE_ASH, player.getX(), player.getY() + 1, player.getZ(), 40, 0.8, 0.8, 0.8, 0.05);
        }
        EmberSurfaceNetwork.send(player, new EmberSurfaceNetwork.Plunge(30));
        Vec3 at = arrival(back);
        player.fallDistance = 0;
        teleport(player, back, at.x, at.y, at.z, EmberBackLayout.ARRIVAL_YAW);
        back.playSound(null, at.x, at.y, at.z, EmberTitanSetup.SWALLOW.get(), SoundSource.PLAYERS, 1.0f, 0.8f);
        showTitle(player, "title.skyabyss.ember_back", "title.skyabyss.ember_back.sub");
        Notice.narrate(player, EmberIds.LANG_BACK + "arrived");
        return true;
    }

    // 次元をまたぐ移動。偽プレイヤー (自己テスト) は接続が無く changeDimension のパケットで落ちるので、世界だけ切り替える
    private static void teleport(ServerPlayer player, ServerLevel to, double x, double y, double z, float yaw) {
        if (player instanceof net.neoforged.neoforge.common.util.FakePlayer) {
            player.setServerLevel(to);
            player.moveTo(x, y, z, yaw, 0f);
            return;
        }
        player.teleportTo(to, x, y, z, yaw, 0f);
    }

    // 到着点 (舌の上)。体がまだ建っていなければ、到着点の真下の灰の原の上
    public static Vec3 arrival(ServerLevel back) {
        BlockPos a = EmberBackLayout.ARRIVAL;
        if (!back.getBlockState(a).isAir()) {
            return new Vec3(a.getX() + 0.5, a.getY() + 1, a.getZ() + 0.5);
        }
        back.getChunk(a.getX() >> 4, a.getZ() >> 4);
        int y = back.getHeight(Heightmap.Types.MOTION_BLOCKING, a.getX(), a.getZ());
        return new Vec3(a.getX() + 0.5, Math.max(y, EmberBackLayout.PLAIN_TOP + 1), a.getZ() + 0.5);
    }

    // 地上へ戻す (Titans.bindBack の道)
    public static void leave(ServerPlayer player) {
        ServerLevel overworld = player.server.overworld();
        Landing landing = landing(overworld);
        float yaw = Float.isNaN(landing.yaw()) ? player.getYRot() : landing.yaw();
        PASSAGE.startCooldown(player);
        ON_PLAIN.remove(player.getUUID());
        EmberHeat.reset(player);
        EmberSurfaceNetwork.send(player, new EmberSurfaceNetwork.Plunge(24));
        if (player.level() instanceof ServerLevel from) {
            from.playSound(null, player.getX(), player.getY(), player.getZ(), EmberTitanSetup.SWALLOW.get(), SoundSource.PLAYERS, 1.0f, 1.2f);
        }
        player.fallDistance = 0;
        Vec3 p = landing.pos();
        teleport(player, overworld, p.x, p.y, p.z, yaw);
        overworld.playSound(null, p.x, p.y, p.z, EmberTitanSetup.SWALLOW.get(), SoundSource.PLAYERS, 1.0f, 1.3f);
    }

    // 戻る先と向き (向きが NaN なら今の向きのまま)
    public record Landing(Vec3 pos, float yaw) {
    }

    // 巨神が眠っていれば (浮かぶ・沈む途中も) 口の前 6 ブロックの地面へ、巨神に背を向けて。潜っていれば最後に眠った場所の口の前。
    // 記録もなければ共有スポーン地点
    public static Landing landing(ServerLevel overworld) {
        EmberTitanEntity titan = EmberKeeper.KEEPER.current(overworld);
        Vec3 exit = null;
        float yaw = Float.NaN;
        if (titan != null && titan.isSurfaced()) {
            exit = titan.exitPoint();
            yaw = titan.getYRot();
        } else {
            Vec3 rec = null;
            if (titan != null && titan.lastSleepSpot() != null) {
                rec = EmberTitanEntity.exitFromRecord(titan.saveState());
            } else {
                rec = EmberTitanEntity.exitFromRecord(EmberKeeper.KEEPER.slot(overworld).state);
            }
            if (rec != null) {
                exit = new Vec3(rec.x, 0, rec.z);
                yaw = (float) rec.y;
            }
        }
        if (exit != null) {
            Vec3 stand = EmberGround.landingAt(overworld, exit.x, exit.z, 16);
            if (stand != null) {
                return new Landing(stand, yaw);
            }
        }
        BlockPos spawn = overworld.getSharedSpawnPos();
        overworld.getChunk(spawn.getX() >> 4, spawn.getZ() >> 4);
        int y = overworld.getHeight(Heightmap.Types.MOTION_BLOCKING, spawn.getX(), spawn.getZ());
        return new Landing(new Vec3(spawn.getX() + 0.5, y + 1, spawn.getZ() + 0.5), Float.NaN);
    }

    // 灰の原の上か: 空が見え、灰の原の高さにいて、足もとが灰の原のいちばん上の層 (滑らかな玄武岩)。体の中・見送りの石は除く
    static boolean onAshPlain(ServerPlayer player) {
        if (player.getY() > EmberBackLayout.PLAIN_TOP + 2 || player.getY() < EmberBackLayout.PLAIN_TOP + 0.5
                || EmberBackLayout.RETURN_STONE.contains(player.position())) {
            return false;
        }
        BlockPos feet = player.blockPosition();
        ServerLevel level = player.serverLevel();
        if (!level.canSeeSky(feet) || !level.getBlockState(feet.below()).is(Blocks.SMOOTH_BASALT)) {
            return false;
        }
        // 到着点のそばは除く (体がまだ建っていないうちは、到着点そのものが灰の原の上にある。戻しても同じ所なので、繰り返さない)
        return player.position().distanceToSqr(arrival(level)) > 4 * 4;
    }

    @SubscribeEvent
    static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.level().dimension() != EmberIds.BACK || player.isSpectator()) {
            return;
        }
        if (player.tickCount % 2 != 0 || (player.isCreative() && player.getAbilities().flying)) {
            return;
        }
        // 見送りの石に 0.5 秒いると地上へ
        if (PASSAGE.dwell(player, EmberBackLayout.RETURN_STONE.contains(player.position()), 2)) {
            Notice.narrate(player, EmberIds.LANG_BACK + "leaving");
            leave(player);
            return;
        }
        // 体の中心から 220 ブロックより外へ出ると、すぐ到着点へ
        if (player.tickCount % 20 == 0
                && Math.hypot(player.getX() - EmberBackLayout.BODY_CX, player.getZ() - EmberBackLayout.BODY_CZ) > EmberBackLayout.FAR) {
            pushBack(player);
            return;
        }
        // 灰の原 (見送りの石のほか) に 5 秒いると、到着点へ戻される (地上へは戻さない)
        if (player.onGround() && onAshPlain(player)) {
            if (ON_PLAIN.merge(player.getUUID(), 2, Integer::sum) >= PLAIN_PUSH_AFTER) {
                pushBack(player);
            }
        } else if (player.onGround()) {
            ON_PLAIN.remove(player.getUUID());
        }
    }

    private static void pushBack(ServerPlayer player) {
        ON_PLAIN.remove(player.getUUID());
        Vec3 at = arrival(player.serverLevel());
        player.fallDistance = 0;
        player.teleportTo(player.serverLevel(), at.x, at.y, at.z, EmberBackLayout.ARRIVAL_YAW, 0f);
        Notice.bar(player, ModText.hint(EmberIds.LANG_BACK + "pushed_back"));
    }

    @SubscribeEvent
    static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        PASSAGE.forget(event.getEntity().getUUID());
        ON_PLAIN.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        PASSAGE.clear();
        ON_PLAIN.clear();
    }

    private static void showTitle(ServerPlayer player, String title, String subtitle) {
        if (player instanceof net.neoforged.neoforge.common.util.FakePlayer) {
            return;
        }
        Notice.place(player, ModText.title(title), ModText.subtitle(subtitle));
    }

    // 自己テスト用
    static int plainTicks(UUID id) {
        return ON_PLAIN.getOrDefault(id, 0);
    }
}
