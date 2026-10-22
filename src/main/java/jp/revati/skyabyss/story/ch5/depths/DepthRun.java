package jp.revati.skyabyss.story.ch5.depths;

import java.util.List;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.story.ch5.AbyssBuild;
import jp.revati.skyabyss.story.ch5.AbyssWorld;
import jp.revati.skyabyss.story.ch5.warden.WardenArena;
import jp.revati.skyabyss.titan.group.Groups;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
//   入口     心臓の間の裂け目 (番人を倒したあと開く)。落ちると、組の再開の階 (1 階か、王を倒した次の階) の入口へ
//   階       下り口を探して穴に落ちると次の階へ。組の人は一緒に下りる (同じ階の 30 マス以内にいる人。離れていた人は、
//            自分で穴に入ると組の今の階へ着く)。階は組ごとの場所に建つ。次の階は先に用意される
//   王       5 階ごとの王の階。王を倒すと下り口が開き、次の階が再開の階になる (DepthBoss)
//   休み場   灯籠 (蝋燭) に火を点け、右クリックで体力と状態が戻る (一人 60 秒に一度)。鎖をしゃがんで右クリックで心臓の間へ戻る
//   倒れたら 組の今の階の入口で目を覚ます (階はそのまま)
//   帰り     鎖で戻ると、組に誰も残らなければ潜りは終わり。次に入るときは再開の階から、新しい階で
//   順位     心臓の間の順位の石を右クリックで、組と一人の上位を出す
//   深さ     深さの数字は心臓の間の底から数えた数字 (AbyssPressure.depth が読む)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class DepthRun {
    public static final int HEART_HOLE_DX = 6;
    static final String FLOOR = "skyabyss_depth_floor";
    static final String REST_AT = "skyabyss_depth_rest_at";
    static final double HOLE_R = 1.6;
    static final double FOLLOW_R = 30;

    private DepthRun() {
    }

    // 階の箱の中か (敵の湧きなど、ほかの仕組みが「深みの中」を知るため)
    public static boolean inDepths(ServerPlayer p) {
        return AbyssWorld.in(p) && DepthBuilder.inRegion(p.blockPosition());
    }

    // 表示の深さ (心臓の間の底からの数字)。階の外なら -1 (AbyssPressure の深さを使う)
    public static int totalDepth(ServerPlayer p) {
        if (!AbyssWorld.in(p)) {
            return -1;
        }
        DepthBuilder.Inst i = DepthBuilder.at(p.blockPosition());
        return i == null ? -1 : DepthPlan.totalDepth(DepthPlan.beyondOfFloor(i.floor));
    }

    public static boolean riftOpen(MinecraftServer server) {
        return DepthData.get(server).rift;
    }

    // 灯籠の近く (上昇の重みをゆるめる休み場と同じ扱い。AbyssPressure が見る)
    public static boolean nearRest(ServerPlayer p) {
        DepthBuilder.Inst i = AbyssWorld.in(p) ? DepthBuilder.at(p.blockPosition()) : null;
        if (i == null || !i.ready || i.lantern() == null) {
            return false;
        }
        return p.blockPosition().closerThan(i.lantern(), 6);
    }

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

    // 裂け目を開く (番人を倒したあと。一度だけ)
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
        if (!(event.getEntity() instanceof ServerPlayer p) || !AbyssWorld.in(p) || p.tickCount % 2 != 0 || !p.isAlive() || p.isSpectator()) {
            return;
        }
        handle(p);
    }

    // ---------------------------------------------------------------- 組の潜り
    // 組の潜りを返す (なければ作って場所をふやす)
    static DepthData.Run runOf(MinecraftServer server, UUID group) {
        DepthData d = DepthData.get(server);
        DepthData.Run r = d.runs.get(group);
        if (r == null) {
            r = new DepthData.Run();
            r.slot = d.nextSlot++;
            d.runs.put(group, r);
            d.setDirty();
        }
        return r;
    }

    // 潜りを始める (誰も中にいなければ、通し番号を進めて再開の階から。新しい階になる)
    static DepthData.Run begin(MinecraftServer server, UUID group) {
        DepthData.Run r = runOf(server, group);
        if (r.floor <= 0) {
            r.salt++;
            r.floor = r.checkpoint;
            DepthData.get(server).setDirty();
        }
        DepthBuilder.request(server, group, r.slot, r.floor, r.salt);
        return r;
    }

    static boolean insideAny(ServerLevel level, UUID group, @Nullable ServerPlayer except) {
        for (ServerPlayer m : Groups.online(level, group)) {
            if (m != except && DepthBuilder.at(m.blockPosition()) != null) {
                return true;
            }
        }
        return false;
    }

    // 一人ぶんの見回り
    static void handle(ServerPlayer p) {
        MinecraftServer server = p.server;
        BlockPos pos = p.blockPosition();
        DepthBuilder.Inst i = DepthBuilder.at(pos);
        if (i == null) {
            if (DepthBuilder.inRegion(pos)) {
                recover(p);
                return;
            }
            BlockPos hole = heartHole();
            if (hole != null && riftOpen(server)) {
                double dist = Math.sqrt(p.distanceToSqr(hole.getX() + 0.5, hole.getY(), hole.getZ() + 0.5));
                if (dist < 48 && p.tickCount % 20 == 0 && !isHeartFar(p, hole)) {
                    begin(server, Groups.keyOf(p));
                }
                if (nearHole(p, hole)) {
                    enter(p);
                }
            }
            return;
        }
        if (!i.ready) {
            return;
        }
        ServerLevel level = p.serverLevel();
        if (!i.populated) {
            DepthMobs.populate(level, i);
            DepthTraps.spawnMerchant(level, i);
        }
        if (p.getPersistentData().getInt(FLOOR) != i.floor) {
            arrive(p, i);
        }
        DepthTraps.tick(level, i, p);
        if (i.stairsOpen && nearStairs(p, i)) {
            advance(p, i);
        }
    }

    // 心臓の間の穴から遠い (同じ層ではない) 人は先読みしない
    private static boolean isHeartFar(ServerPlayer p, BlockPos hole) {
        return Math.abs(p.getY() - hole.getY()) > 30;
    }

    static boolean nearHole(ServerPlayer p, BlockPos hole) {
        double dx = p.getX() - (hole.getX() + 0.5);
        double dz = p.getZ() - (hole.getZ() + 0.5);
        return dx * dx + dz * dz <= HOLE_R * HOLE_R && p.getY() < hole.getY() + 0.5;
    }

    static boolean nearStairs(ServerPlayer p, DepthBuilder.Inst i) {
        BlockPos s = i.stairsFeet();
        double dx = p.getX() - (s.getX() + 0.5);
        double dz = p.getZ() - (s.getZ() + 0.5);
        return dx * dx + dz * dz <= HOLE_R * HOLE_R && p.getY() < DepthPlan.FLOOR_Y + 0.6;
    }

    // 心臓の間の穴に落ちた: 組の今の階 (なければ再開の階) の入口へ。まだ建っていなければ縁へ押し戻す
    static void enter(ServerPlayer p) {
        MinecraftServer server = p.server;
        ServerLevel level = p.serverLevel();
        UUID group = Groups.keyOf(p);
        DepthData.Run r = insideAny(level, group, p) ? runOf(server, group) : begin(server, group);
        if (r.floor <= 0) {
            r = begin(server, group);
        }
        DepthBuilder.Inst t = DepthBuilder.request(server, group, r.slot, r.floor, r.salt);
        if (!t.ready) {
            BlockPos hole = heartHole();
            if (hole != null) {
                p.teleportTo(level, hole.getX() + 2.5, hole.getY() + 1.0, hole.getZ() + 0.5, p.getYRot(), 0f);
            }
            Notice.hint(p, "message.skyabyss.depths.not_yet");
            return;
        }
        place(p, t);
        arrive(p, t);
    }

    // 階の入口へ連れていく
    static void place(ServerPlayer p, DepthBuilder.Inst t) {
        ServerLevel level = p.server.getLevel(AbyssWorld.LEVEL);
        BlockPos at = t.entryFeet();
        level.getChunk(at);
        p.teleportTo(level, at.getX() + 0.5, at.getY(), at.getZ() + 0.5, p.getYRot(), 0f);
        p.setDeltaMovement(0, 0, 0);
        p.fallDistance = 0;
    }

    // 階に着いた (一人ごとに一度): 記録・知らせ・目的・次の階の用意
    static void arrive(ServerPlayer p, DepthBuilder.Inst i) {
        MinecraftServer server = p.server;
        p.getPersistentData().putInt(FLOOR, i.floor);
        DepthRecords.reach(server, p, DepthPlan.beyondOfFloor(i.floor));
        Component kind = Component.translatable("message.skyabyss.depths.kind." + i.plan.kind().name().toLowerCase(java.util.Locale.ROOT));
        Notice.narrate(p, "message.skyabyss.depths.descend", i.floor, kind);
        if (i.plan.boss()) {
            Notice.objective(p, i.bossDeadAt >= 0 ? "message.skyabyss.depths.obj.stairs_open" : "message.skyabyss.depths.obj.king_floor");
        } else {
            Notice.objective(p, "message.skyabyss.depths.obj.find_stairs");
        }
        DepthData.Run r = DepthData.get(server).runs.get(i.group);
        if (r != null && r.floor == i.floor) {
            DepthBuilder.request(server, i.group, r.slot, i.floor + 1, r.salt);
        }
    }

    // 下り口に入った: 同じ階の組の人 (近くにいる人) と一緒に次の階へ。離れていた人は組の今の階へ
    static void advance(ServerPlayer p, DepthBuilder.Inst i) {
        MinecraftServer server = p.server;
        ServerLevel level = p.serverLevel();
        DepthData data = DepthData.get(server);
        DepthData.Run r = data.runs.get(i.group);
        if (r == null) {
            return;
        }
        int target = r.floor > i.floor ? r.floor : i.floor + 1;
        DepthBuilder.Inst t = DepthBuilder.request(server, i.group, r.slot, target, r.salt);
        if (!t.ready) {
            BlockPos s = i.stairsFeet();
            p.teleportTo(level, s.getX() + 2.5, s.getY(), s.getZ() + 0.5, p.getYRot(), 0f);
            Notice.hint(p, "message.skyabyss.depths.not_yet");
            return;
        }
        if (target > r.floor) {
            r.floor = target;
            data.setDirty();
        }
        List<ServerPlayer> movers = new java.util.ArrayList<>();
        movers.add(p);
        if (target == i.floor + 1) {
            BlockPos s = i.stairsFeet();
            for (ServerPlayer m : Groups.alive(level, i.group)) {
                if (m != p && DepthBuilder.at(m.blockPosition()) == i && Math.sqrt(m.distanceToSqr(s.getX() + 0.5, s.getY(), s.getZ() + 0.5)) <= FOLLOW_R) {
                    movers.add(m);
                }
            }
        }
        for (ServerPlayer m : movers) {
            place(m, t);
            arrive(m, t);
        }
    }

    // 箱の中なのに階が分からない (ワールドを開き直した・外れた): 組の今の階を頼む。ここが今の階の場所でなければ戻す
    static void recover(ServerPlayer p) {
        MinecraftServer server = p.server;
        UUID group = Groups.keyOf(p);
        DepthData.Run r = DepthData.get(server).runs.get(group);
        if (r == null || r.floor <= 0 || DepthPlan.originZ(r.slot) > p.getZ() || p.getZ() >= DepthPlan.originZ(r.slot) + DepthPlan.SIZE + 4) {
            returnHome(p);
            return;
        }
        DepthBuilder.request(server, group, r.slot, r.floor, r.salt);
        DepthBuilder.Inst now = DepthBuilder.get(r.slot, DepthPlan.parityOf(r.floor));
        if (now != null && now.ready && DepthBuilder.at(p.blockPosition()) == null) {
            place(p, now);
        }
    }

    // 組の今の階の入口へ。建てている最中や階がなければ心臓の間へ (階の建て替えで中にいた人も)
    static void evacuate(ServerPlayer p) {
        UUID group = Groups.keyOf(p);
        DepthData.Run r = DepthData.get(p.server).runs.get(group);
        if (r != null && r.floor > 0) {
            DepthBuilder.Inst now = DepthBuilder.get(r.slot, DepthPlan.parityOf(r.floor));
            if (now != null && now.ready && now.floor == r.floor && !now.contains(p.blockPosition())) {
                place(p, now);
                arrive(p, now);
                return;
            }
        }
        returnHome(p);
    }

    // 王を倒した: 組の再開の階を進める
    static void checkpointReached(MinecraftServer server, DepthBuilder.Inst i) {
        DepthData.Run r = DepthData.get(server).runs.get(i.group);
        if (r != null && DepthPlan.checkpointFor(i.floor) > r.checkpoint) {
            r.checkpoint = DepthPlan.checkpointFor(i.floor);
            DepthData.get(server).setDirty();
        }
    }

    // 倒れたら、組の今の階の入口で目を覚ます。なければ false (AbyssRespawn の通常の戻りへ)
    public static boolean respawn(ServerPlayer p) {
        if (p.getPersistentData().getInt(FLOOR) <= 0) {
            return false;
        }
        UUID group = Groups.keyOf(p);
        DepthData.Run r = DepthData.get(p.server).runs.get(group);
        if (r == null || r.floor <= 0) {
            return false;
        }
        DepthBuilder.Inst now = DepthBuilder.get(r.slot, DepthPlan.parityOf(r.floor));
        if (now == null || !now.ready || now.floor != r.floor) {
            DepthBuilder.request(p.server, group, r.slot, r.floor, r.salt);
            return false;
        }
        ServerLevel level = p.server.getLevel(AbyssWorld.LEVEL);
        if (level == null) {
            return false;
        }
        BlockPos at = now.entryFeet();
        level.getChunk(at);
        p.teleportTo(level, at.getX() + 0.5, at.getY(), at.getZ() + 0.5, p.getYRot(), 0f);
        p.getPersistentData().putInt(FLOOR, now.floor);
        Notice.hint(p, "message.skyabyss.depths.fell", now.floor);
        return true;
    }

    // 心臓の間へ戻る (鎖)。組に誰も中に残らなければ、潜りは終わる
    public static void returnHome(ServerPlayer p) {
        ServerLevel level = p.server.getLevel(AbyssWorld.LEVEL);
        BlockPos m = AbyssBuild.mark("heart");
        if (level == null || m == null) {
            return;
        }
        p.getPersistentData().remove(FLOOR);
        p.teleportTo(level, m.getX() + 0.5, m.getY(), m.getZ() + 0.5, p.getYRot(), 0f);
        UUID group = Groups.keyOf(p);
        DepthData.Run r = DepthData.get(p.server).runs.get(group);
        if (r != null && r.floor > 0 && !insideAny(level, group, p)) {
            r.floor = 0;
            DepthData.get(p.server).setDirty();
        }
        Notice.hint(p, "message.skyabyss.depths.returned");
    }

    // ---------------------------------------------------------------- 右クリック
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
        DepthBuilder.Inst i = DepthBuilder.at(pos);
        if (i == null || !i.ready) {
            return;
        }
        if (pos.equals(i.lantern())) {
            BlockState st = p.serverLevel().getBlockState(pos);
            if (st.getBlock() == Blocks.CANDLE) {
                if (!st.getValue(CandleBlock.LIT)) {
                    p.serverLevel().setBlock(pos, st.setValue(CandleBlock.LIT, true), 3);
                }
                long now = p.serverLevel().getGameTime();
                if (now - p.getPersistentData().getLong(REST_AT) >= 1200 || p.getPersistentData().getLong(REST_AT) == 0) {
                    p.getPersistentData().putLong(REST_AT, now);
                    p.setHealth(p.getMaxHealth());
                    p.removeAllEffects();
                    Notice.hint(p, "message.skyabyss.depths.rest");
                } else {
                    Notice.hint(p, "message.skyabyss.depths.rest_wait");
                }
            }
            event.setCanceled(true);
        } else if (i.plan.chain() != null && pos.getX() == i.ox + i.plan.chain().x() && pos.getZ() == i.oz + i.plan.chain().z()
                && p.serverLevel().getBlockState(pos).getBlock() == Blocks.CHAIN) {
            if (p.isShiftKeyDown()) {
                returnHome(p);
            } else {
                Notice.hint(p, "message.skyabyss.depths.climb");
            }
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    static void onStopped(ServerStoppedEvent event) {
        DepthBuilder.reset();
    }

    // ---------------------------------------------------------------- 開発用
    //   /skyabyss abyss depth go <階>     その階 (新しい潜り) の入口へ。その場で建てる
    //   /skyabyss abyss depth kind <型>   その型の最初の階へ
    //   /skyabyss abyss depth at <場所>   今の階の場所へ (entry stairs shop rest house treasure arena king)
    //   /skyabyss abyss depth records     順位の文字
    @SubscribeEvent
    static void onRegister(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("skyabyss").requires(s -> s.hasPermission(2))
                .then(Commands.literal("abyss")
                        .then(Commands.literal("depth")
                                .then(Commands.literal("go").then(Commands.argument("n", IntegerArgumentType.integer(1, 999)).executes(c -> {
                                    ServerPlayer p = c.getSource().getPlayerOrException();
                                    return dev(p, IntegerArgumentType.getInteger(c, "n"));
                                })))
                                .then(Commands.literal("kind").then(Commands.argument("name", StringArgumentType.word()).executes(c -> {
                                    ServerPlayer p = c.getSource().getPlayerOrException();
                                    DepthPlan.Kind kind = DepthPlan.Kind.valueOf(StringArgumentType.getString(c, "name").toUpperCase(java.util.Locale.ROOT));
                                    DepthData.Run r = runOf(p.server, Groups.keyOf(p));
                                    int n = 1;
                                    while (n < 200 && (DepthPlan.isBoss(n)
                                            || DepthPlan.kindOf(DepthPlan.floorSeed(DepthBuilder.seed(p.server), r.slot, n, r.salt + 1), n) != kind)) {
                                        n++;
                                    }
                                    return dev(p, n);
                                })))
                                // 撮る台本用: その部屋のある階 (shop house treasure rest) を探して、その部屋の中へ
                                .then(Commands.literal("feature").then(Commands.argument("what", StringArgumentType.word()).executes(c -> {
                                    ServerPlayer p = c.getSource().getPlayerOrException();
                                    String what = StringArgumentType.getString(c, "what");
                                    DepthFloor.RoomType want = switch (what) {
                                        case "shop" -> DepthFloor.RoomType.SHOP;
                                        case "house" -> DepthFloor.RoomType.HOUSE;
                                        case "treasure" -> DepthFloor.RoomType.TREASURE;
                                        default -> DepthFloor.RoomType.REST;
                                    };
                                    DepthData.Run r = runOf(p.server, Groups.keyOf(p));
                                    int n = 2;
                                    while (n < 200 && (DepthPlan.isBoss(n) || n % 5 == 1 && want == DepthFloor.RoomType.REST
                                            || DepthFloor.generate(DepthPlan.floorSeed(DepthBuilder.seed(p.server), r.slot, n, r.salt + 1), n).first(want) == null)) {
                                        n++;
                                    }
                                    dev(p, n);
                                    return devAt(p, what);
                                })))
                                // 撮る台本用: 今の王の階で王を目覚めさせる (観客席から。観戦者は広間に踏み入っても起こさないため)
                                .then(Commands.literal("kingstart").executes(c -> {
                                    ServerPlayer p = c.getSource().getPlayerOrException();
                                    DepthBuilder.Inst i = DepthBuilder.at(p.blockPosition());
                                    if (i == null || !i.plan.boss()) {
                                        return 0;
                                    }
                                    DepthBoss.start(p.serverLevel(), i, List.of());
                                    return 1;
                                }))
                                .then(Commands.literal("at").then(Commands.argument("what", StringArgumentType.word()).executes(c -> {
                                    ServerPlayer p = c.getSource().getPlayerOrException();
                                    return devAt(p, StringArgumentType.getString(c, "what"));
                                })))
                                .then(Commands.literal("records").executes(c -> {
                                    DepthRecords.show(c.getSource().getServer(), c.getSource().getPlayerOrException());
                                    return 1;
                                })))));
    }

    static int dev(ServerPlayer p, int floor) {
        MinecraftServer server = p.server;
        UUID group = Groups.keyOf(p);
        DepthData.Run r = runOf(server, group);
        r.salt++;
        r.floor = floor;
        DepthData.get(server).setDirty();
        DepthBuilder.Inst t = DepthBuilder.request(server, group, r.slot, floor, r.salt);
        DepthBuilder.finishNow(server);
        place(p, t);
        arrive(p, t);
        return floor;
    }

    static int devAt(ServerPlayer p, String what) {
        DepthBuilder.Inst i = DepthBuilder.at(p.blockPosition());
        if (i == null || !i.ready) {
            return 0;
        }
        DepthFloor.Plan pl = i.plan;
        DepthFloor.Room room = switch (what) {
            case "shop" -> pl.first(DepthFloor.RoomType.SHOP);
            case "rest" -> pl.first(DepthFloor.RoomType.REST);
            case "house" -> pl.first(DepthFloor.RoomType.HOUSE);
            case "treasure" -> pl.first(DepthFloor.RoomType.TREASURE);
            case "arena", "king" -> pl.first(DepthFloor.RoomType.BOSS);
            default -> null;
        };
        BlockPos feet;
        if (what.equals("stairs")) {
            feet = i.feet(pl.stairs().x() - 3, pl.stairs().z());
        } else if (what.equals("entry")) {
            feet = i.entryFeet();
        } else if (room != null) {
            feet = i.feet(room.cx() - (what.equals("king") ? 9 : 0), room.cz() + (what.equals("king") ? 0 : 0));
            if (!what.equals("king")) {
                feet = i.feet(room.x0() + 1, room.cz());
            }
        } else {
            return 0;
        }
        p.teleportTo(p.serverLevel(), feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5, what.equals("king") ? -90f : 0f, 8f);
        return 1;
    }
}
