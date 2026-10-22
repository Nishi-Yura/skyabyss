package jp.revati.skyabyss.story.ch5.depths;

import org.jetbrains.annotations.Nullable;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.story.ch5.AbyssBuild;
import jp.revati.skyabyss.story.ch5.AbyssWorld;
import jp.revati.skyabyss.story.ch5.warden.WardenArena;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

// 心臓の下の果てない深み (docs/design/36 の 9) の、潜りの流れ。
//   入口     心臓の間の裂け目 (番人を倒したあと開く。床の穴に落ちると区画 1 の上へ)
//   区画     落ちた先の区画の上。底の広い足場の穴に落ちると次の区画の上へ (次が建つまで待つ)
//   灯籠     区画の真ん中の足場の灯籠に火を点けると、そこが再開の所 (倒れたら、そこで目を覚ます)
//   帰り     灯籠の横の鎖をしゃがんで右クリックすると、心臓の間へ戻る。潜りの記録はそこで止まる (深みの記録は永久)
//   順位     心臓の間の順位の石を右クリックで、組と一人の上位を出す
//   深さ     深さの数字は心臓の間の底から数えた数字 (AbyssPressure.depth が読む)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class DepthRun {
    public static final int HEART_HOLE_DX = 6;
    static final String SEC = "skyabyss_depth_sec";
    static final String RUN = "skyabyss_depth_run";
    static final String RES_SEC = "skyabyss_depth_res_sec";
    static final String RES_POS = "skyabyss_depth_res_pos";
    static final double HOLE_R = 1.6;

    private DepthRun() {
    }

    // 区画の番号 (世界の座標の上。区画の外なら 0)
    public static int sectionOf(BlockPos b) {
        int idx = Math.floorDiv(b.getX() + DepthPlan.SLOT / 2, DepthPlan.SLOT);
        if (idx < 1 || Math.abs(b.getX() - idx * DepthPlan.SLOT) > DepthPlan.ROOM_R + 2) {
            return 0;
        }
        if (b.getY() > DepthPlan.TOP_Y + 4 || b.getY() < DepthPlan.TOP_Y - DepthPlan.HEIGHT - 4) {
            return 0;
        }
        return idx;
    }

    public static int sectionOf(ServerPlayer p) {
        if (!AbyssWorld.in(p)) {
            return 0;
        }
        return sectionOf(p.blockPosition());
    }

    // 表示の深さ (心臓の間の底からの数字)。区画の外なら -1 (AbyssPressure の深さを使う)
    public static int totalDepth(ServerPlayer p) {
        int s = sectionOf(p);
        return s == 0 ? -1 : DepthPlan.totalDepth(DepthPlan.beyond(s, Mth.floor(p.getY())));
    }

    public static boolean riftOpen(MinecraftServer server) {
        return DepthData.get(server).rift;
    }

    // 灯籠の近く (上昇の重みをゆるめる休み場と同じ扱い。AbyssPressure が見る)
    public static boolean nearRest(ServerPlayer p) {
        int sec = sectionOf(p);
        if (sec == 0 || !DepthBuilder.built(p.server, sec)) {
            return false;
        }
        return p.blockPosition().closerThan(DepthBuilder.geometry(p.server, sec).restFeet(), 6);
    }

    // 灯籠の横の鎖と、灯籠の印 (区画の決まりから)。区画 0 (心臓の間) は裂け目と石
    @Nullable
    public static BlockPos heartHole() {
        BlockPos m = AbyssBuild.mark("heart");
        return m == null ? null : m.offset(HEART_HOLE_DX, -2, 0);
    }

    @Nullable
    public static BlockPos heartStone() {
        BlockPos m = AbyssBuild.mark("heart");
        return m == null ? null : m.offset(-6, -1, 0);
    }

    // 裂け目を開く (番人を倒したあと。一度だけ。区画 1 を頼む)
    public static void tryOpenRift(MinecraftServer server) {
        DepthData d = DepthData.get(server);
        if (d.rift || !WardenArena.cleared(server)) {
            return;
        }
        ServerLevel level = server.getLevel(AbyssWorld.LEVEL);
        BlockPos hole = heartHole();
        BlockPos stone = heartStone();
        if (level == null || hole == null || stone == null) {
            return;
        }
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                double r = Math.hypot(dx, dz);
                if (r <= 1.6) {
                    level.setBlock(hole.offset(dx, 0, dz), Blocks.AIR.defaultBlockState(), 3);
                } else if (r <= 2.6) {
                    level.setBlock(hole.offset(dx, 0, dz), Blocks.SCULK.defaultBlockState(), 3);
                }
            }
        }
        level.setBlock(stone, Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState(), 3);
        level.setBlock(stone.above(), Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState(), 3);
        d.rift = true;
        d.setDirty();
        DepthBuilder.request(server, 1);
        SkyAbyss.LOGGER.info("[abyss depth] the rift opened under the heart");
    }

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % 40 == 0) {
            tryOpenRift(server);
        }
    }

    @SubscribeEvent
    static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || !AbyssWorld.in(p) || p.tickCount % 4 != 0 || !p.isAlive() || p.isSpectator()) {
            return;
        }
        handle(p);
    }

    // 一人ぶんの見回り (区画の中の深さ・記録・次の区画の用意・穴・底の抜け)
    static void handle(ServerPlayer p) {
        MinecraftServer server = p.server;
        int sec = sectionOf(p);
        if (sec == 0) {
            BlockPos hole = heartHole();
            if (hole != null && nearHole(p, hole)) {
                descend(p, 1);
            }
            return;
        }
        p.getPersistentData().putInt(SEC, sec);
        int beyond = DepthPlan.beyond(sec, Mth.floor(p.getY()));
        if (beyond > p.getPersistentData().getInt(RUN)) {
            p.getPersistentData().putInt(RUN, beyond);
            DepthRecords.reach(server, p, beyond);
        }
        DepthBuilder.request(server, sec + 1);
        if (p.getY() < DepthPlan.TOP_Y - DepthPlan.HEIGHT - 3) {
            // 区画の底の下へ抜けた (足場から外れた): 覚えた灯籠で目を覚ます
            if (respawn(p)) {
                Notice.hint(p, "message.skyabyss.depths.fell");
            }
            return;
        }
        if (!DepthBuilder.built(server, sec)) {
            return;
        }
        DepthLayout.Result g = DepthBuilder.geometry(server, sec);
        BlockPos hole = g.hole();
        if (nearHole(p, hole)) {
            descend(p, sec + 1);
        }
    }

    static boolean nearHole(ServerPlayer p, BlockPos hole) {
        double dx = p.getX() - (hole.getX() + 0.5);
        double dz = p.getZ() - (hole.getZ() + 0.5);
        return dx * dx + dz * dz <= HOLE_R * HOLE_R && p.getY() < hole.getY() + 0.5;
    }

    // 区画 next の上へ落とす。まだ建っていなければ、穴の縁へ押し戻す (次の頼みを出す)
    static void descend(ServerPlayer p, int next) {
        MinecraftServer server = p.server;
        ServerLevel level = server.getLevel(AbyssWorld.LEVEL);
        if (level == null) {
            return;
        }
        DepthBuilder.request(server, next);
        if (!DepthBuilder.built(server, next)) {
            BlockPos hole = next == 1 ? heartHole() : null;
            DepthLayout.Result cur = null;
            int here = sectionOf(p);
            if (here > 0) {
                cur = DepthBuilder.geometry(server, here);
                hole = cur.hole();
            }
            if (hole != null) {
                p.teleportTo(level, hole.getX() + 2.5, hole.getY() + 1.0, hole.getZ() + 0.5, p.getYRot(), 0f);
            }
            Notice.hint(p, "message.skyabyss.depths.not_yet");
            return;
        }
        DepthLayout.Result g = DepthBuilder.geometry(server, next);
        BlockPos at = g.entryFeet();
        level.getChunk(at);
        p.teleportTo(level, at.getX() + 0.5, at.getY(), at.getZ() + 0.5, p.getYRot(), 0f);
        p.getPersistentData().putInt(SEC, next);
        setResume(p, next, g.restFeet());
        Notice.narrate(p, "message.skyabyss.depths.descend", next);
    }

    // 再開の所を覚える (灯籠に火を点けた所、または区画の上。深い方を覚える)
    static void setResume(ServerPlayer p, int sec, BlockPos at) {
        int cur = p.getPersistentData().getInt(RES_SEC);
        if (sec < cur) {
            return;
        }
        p.getPersistentData().putInt(RES_SEC, sec);
        p.getPersistentData().putLong(RES_POS, at.asLong());
    }

    // 覚えた再開の所へ。覚えがなければ false (AbyssRespawn の通常の戻りへ)
    public static boolean respawn(ServerPlayer p) {
        int sec = p.getPersistentData().getInt(RES_SEC);
        if (sec <= 0 || !p.getPersistentData().contains(RES_POS)) {
            return false;
        }
        ServerLevel level = p.server.getLevel(AbyssWorld.LEVEL);
        if (level == null) {
            return false;
        }
        BlockPos at = BlockPos.of(p.getPersistentData().getLong(RES_POS));
        if (!DepthBuilder.built(p.server, sec)) {
            DepthBuilder.request(p.server, sec);
            return false;
        }
        level.getChunk(at);
        p.teleportTo(level, at.getX() + 0.5, at.getY(), at.getZ() + 0.5, p.getYRot(), 0f);
        return true;
    }

    // 心臓の間へ戻る (鎖)。潜りの記録はここで止まる
    public static void returnHome(ServerPlayer p) {
        ServerLevel level = p.server.getLevel(AbyssWorld.LEVEL);
        BlockPos m = AbyssBuild.mark("heart");
        if (level == null || m == null) {
            return;
        }
        p.getPersistentData().remove(SEC);
        p.getPersistentData().remove(RUN);
        p.getPersistentData().remove(RES_SEC);
        p.getPersistentData().remove(RES_POS);
        p.teleportTo(level, m.getX() + 0.5, m.getY(), m.getZ() + 0.5, p.getYRot(), 0f);
        Notice.hint(p, "message.skyabyss.depths.returned");
    }

    // 区画の中の灯籠・鎖・石を右クリック
    @SubscribeEvent
    static void onRightClick(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || event.getLevel().isClientSide() || !AbyssWorld.in(p)) {
            return;
        }
        BlockPos pos = event.getPos();
        BlockPos stone = heartStone();
        if (stone != null && pos.equals(stone)) {
            DepthRecords.show(p.server, p);
            event.setCanceled(true);
            return;
        }
        int sec = sectionOf(pos);
        if (sec == 0 || !DepthBuilder.built(p.server, sec)) {
            return;
        }
        DepthLayout.Result g = DepthBuilder.geometry(p.server, sec);
        if (pos.equals(g.lantern())) {
            BlockState st = p.serverLevel().getBlockState(pos);
            if (st.getBlock() == Blocks.CANDLE && !st.getValue(CandleBlock.LIT)) {
                p.serverLevel().setBlock(pos, st.setValue(CandleBlock.LIT, true), 3);
                Notice.narrate(p, "message.skyabyss.depths.lit", sec);
            }
            setResume(p, sec, g.restFeet());
            Notice.hint(p, "message.skyabyss.depths.rest");
            event.setCanceled(true);
        } else if (pos.equals(g.exit())) {
            if (p.isShiftKeyDown()) {
                returnHome(p);
            } else {
                Notice.hint(p, "message.skyabyss.depths.climb");
            }
            event.setCanceled(true);
        }
    }

    // 記録の止まり (ワールドを閉じるとき): 次に開くときに建て直しを頼めるように、待ち行列を捨てる
    @SubscribeEvent
    static void onStopped(ServerStoppedEvent event) {
        DepthBuilder.reset();
    }

    // 開発用 (/skyabyss abyss depth go <番号> で区画の上へ。建てていなければその場で建てる。records で順位の文字)
    @SubscribeEvent
    static void onRegister(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("skyabyss").requires(s -> s.hasPermission(2))
                .then(Commands.literal("abyss")
                        .then(Commands.literal("depth")
                                .then(Commands.literal("go").then(Commands.argument("n", IntegerArgumentType.integer(1, 999)).executes(c -> {
                                    ServerPlayer p = c.getSource().getPlayerOrException();
                                    int n = IntegerArgumentType.getInteger(c, "n");
                                    if (!DepthBuilder.built(p.server, n)) {
                                        DepthBuilder.finishNow(p.server, n);
                                    }
                                    descend(p, n);
                                    return 1;
                                })))
                                // 型で選ぶ (撮る台本用): その型の最初の区画の上へ (世界の種で決まる番号を、サーバーが探す)
                                .then(Commands.literal("kind").then(Commands.argument("name", StringArgumentType.word()).executes(c -> {
                                    ServerPlayer p = c.getSource().getPlayerOrException();
                                    String name = StringArgumentType.getString(c, "name").toUpperCase(java.util.Locale.ROOT);
                                    DepthPlan.Kind kind = DepthPlan.Kind.valueOf(name);
                                    int n = 1;
                                    while (DepthPlan.plan(DepthBuilder.seed(p.server), n).kind() != kind && n < 200) {
                                        n++;
                                    }
                                    if (!DepthBuilder.built(p.server, n)) {
                                        DepthBuilder.finishNow(p.server, n);
                                    }
                                    descend(p, n);
                                    return n;
                                })))
                                .then(Commands.literal("records").executes(c -> {
                                    DepthRecords.show(c.getSource().getServer(), c.getSource().getPlayerOrException());
                                    return 1;
                                })))));
    }
}
