package jp.revati.skyabyss.titan.ember;

import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.progress.Titans;
import jp.revati.skyabyss.titan.TitanGate;
import jp.revati.skyabyss.titan.ember.back.EmberBackLayout;
import jp.revati.skyabyss.titan.ember.back.EmberHeat;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

// 地上と「余燼の背」(ember_back) を行き来させる (13 の 2.4〜2.5節)。
// **骨組み (担当 0) の仮の版**: コマンドの board と leave、見送りの石 (0.5 秒)、遠くへ歩くと到着点へ、だけ。
// 担当 A がこのファイルを持ち、熱の口 (EmberMouthEntity) からの乗り込み、呑みこむ音と画面の沈み (橙)、
// 巨神が眠っていれば口の前 6 ブロックへ戻す、灰の原に 5 秒いると到着点へ、を足す。乗る資格・待ち・0.5 秒は TitanGate
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class EmberTravel {
    // 待ち 3 秒、見送りの石に 0.5 秒 (潮殻と同じ)
    public static final TitanGate PASSAGE = new TitanGate("ember", Titans.EMBER, 60, 10);

    private EmberTravel() {
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
        Vec3 at = arrival(back);
        player.fallDistance = 0;
        player.teleportTo(back, at.x, at.y, at.z, EmberBackLayout.ARRIVAL_YAW, 0f);
        player.sendSystemMessage(ModText.lore(EmberIds.LANG_BACK + "arrived"));
        return true;
    }

    // 到着点 (舌の上)。体がまだ建っていなければ (骨組みのうち)、到着点の真下の灰の原の上
    public static Vec3 arrival(ServerLevel back) {
        BlockPos a = EmberBackLayout.ARRIVAL;
        if (!back.getBlockState(a).isAir()) {
            return new Vec3(a.getX() + 0.5, a.getY() + 1, a.getZ() + 0.5);
        }
        back.getChunk(a.getX() >> 4, a.getZ() >> 4);
        int y = back.getHeight(Heightmap.Types.MOTION_BLOCKING, a.getX(), a.getZ());
        return new Vec3(a.getX() + 0.5, Math.max(y, EmberBackLayout.PLAIN_TOP + 1), a.getZ() + 0.5);
    }

    // 地上へ戻す (Titans.bindBack の道)。今は共有スポーン地点の地表。A が「眠っていれば口の前、潜っていれば最後に眠った場所の口の前」にする
    public static void leave(ServerPlayer player) {
        ServerLevel overworld = player.server.overworld();
        BlockPos spawn = overworld.getSharedSpawnPos();
        overworld.getChunk(spawn.getX() >> 4, spawn.getZ() >> 4);
        int y = overworld.getHeight(Heightmap.Types.MOTION_BLOCKING, spawn.getX(), spawn.getZ());
        PASSAGE.startCooldown(player);
        EmberHeat.reset(player);
        player.fallDistance = 0;
        player.teleportTo(overworld, spawn.getX() + 0.5, y + 1, spawn.getZ() + 0.5, player.getYRot(), 0f);
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
            player.sendSystemMessage(ModText.lore(EmberIds.LANG_BACK + "leaving"));
            leave(player);
            return;
        }
        // 体の中心から 220 ブロックより外へ出ると、到着点へ戻す
        if (player.tickCount % 20 == 0
                && Math.hypot(player.getX() - EmberBackLayout.BODY_CX, player.getZ() - EmberBackLayout.BODY_CZ) > EmberBackLayout.FAR) {
            Vec3 at = arrival(player.serverLevel());
            player.fallDistance = 0;
            player.teleportTo(player.serverLevel(), at.x, at.y, at.z, EmberBackLayout.ARRIVAL_YAW, 0f);
            player.displayClientMessage(ModText.hint(EmberIds.LANG_BACK + "pushed_back"), true);
        }
    }

    @SubscribeEvent
    static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        PASSAGE.forget(event.getEntity().getUUID());
    }

    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        PASSAGE.clear();
    }
}
