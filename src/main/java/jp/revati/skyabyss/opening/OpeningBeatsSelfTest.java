package jp.revati.skyabyss.opening;

import java.util.ArrayList;
import java.util.List;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.npc.NpcEntity;
import jp.revati.skyabyss.npc.NpcSetup;
import jp.revati.skyabyss.npc.NpcSpawner;
import jp.revati.skyabyss.npc.NpcWorldData;
import jp.revati.skyabyss.lore.LoreEvents;
import jp.revati.skyabyss.sky.SkyEventData;
import jp.revati.skyabyss.sky.SkyEvents;
import jp.revati.skyabyss.titan.TitanEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

// 序盤の流れが必ず成り立つことの自己テスト (06 の 2.7節、07 の 3.1節):
//  - 新しいワールドの最初の空の影は、初日の正午に予定される
//  - 書は、最初の影が済んだ人に必ず予定される
//  - 岩鯨は、最初の影から半日たつまで自然には現れない
//  - 語り部に会える保証: 案内の手順の判定と、鐘のない所に野営 (焚き火と語り部) が立つこと
public final class OpeningBeatsSelfTest {
    private static final int X = 6000;
    private static final int Z = 6000;
    private static final int Y = 150;

    private OpeningBeatsSelfTest() {
    }

    // 野営を立てる場所のチャンクを読み込む (プレイヤーがいないので強制で)
    public static void load(MinecraftServer server) {
        ServerLevel level = server.overworld();
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                level.setChunkForced((X >> 4) + dx, (Z >> 4) + dz, true);
            }
        }
    }

    public static void runSelfTest(MinecraftServer server) {
        ServerLevel level = server.overworld();
        List<String> failures = new ArrayList<>();
        try {
            firstShadow(level, failures);
            bookGuarantee(failures);
            whaleGate(failures);
            guideSteps(failures);
            camp(level, failures);
        } catch (RuntimeException e) {
            SkyAbyss.LOGGER.error("SELFTEST opening beats exception", e);
            failures.add("exception " + e);
        } finally {
            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    level.setChunkForced((X >> 4) + dx, (Z >> 4) + dz, false);
                }
            }
        }
        if (failures.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST opening beats PASSED");
        } else {
            failures.forEach(f -> SkyAbyss.LOGGER.error("SELFTEST opening beats FAILED: {}", f));
        }
    }

    // ---- 最初の空の影
    private static void firstShadow(ServerLevel level, List<String> failures) {
        // 新しいワールド (時刻 0 から) では、初日の正午。そのときは昼 (isDay は 0〜12999 の時刻)
        long noon = SkyEvents.firstPassTime(0);
        if (noon != 6000 || noon % 24000 >= 12000) {
            failures.add("the first pass of a new world is planned at " + noon + ", not on the first noon");
        }
        // 遅れて始めた (古いワールド・夜に始めた) ときは、2 分後
        if (SkyEvents.firstPassTime(300000) != 302400) {
            failures.add("a late start plans the first pass at " + SkyEvents.firstPassTime(300000));
        }
        // 実際のワールドの記録: まだ通過がなければ、予定は一日以内に入っている (通常の間隔 2〜4 日ではなく)
        SkyEventData data = SkyEventData.get(level);
        long now = level.getGameTime();
        if (data.passCount() == 0 && data.nextPassTick() >= 0 && data.nextPassTick() - now > 24000) {
            failures.add("the first pass is planned " + (data.nextPassTick() - now) + " ticks ahead");
        }
        // 最初の通過の時刻は記録され、二回目では上書きされない
        SkyEventData fresh = new SkyEventData();
        fresh.countPass(5000);
        fresh.countPass(90000);
        if (fresh.passCount() != 2 || fresh.firstPassTick() != 5000) {
            failures.add("the first pass tick was not kept: " + fresh.firstPassTick());
        }
        CompoundTag saved = fresh.save(new CompoundTag(), level.registryAccess());
        SkyEventData loaded = SkyEventData.load(saved, level.registryAccess());
        if (loaded.firstPassTick() != 5000 || loaded.passCount() != 2) {
            failures.add("the first pass tick was not saved");
        }
    }

    // ---- 書
    private static void bookGuarantee(List<String> failures) {
        // 最初の影が済み、書を受け取っておらず、予定もない: 予定する
        if (!LoreEvents.bookOwed(false, -1, 1, false, false)) {
            failures.add("a player who missed the book after the first shadow is not owed one");
        }
        if (LoreEvents.bookOwed(false, -1, 0, false, false)) {
            failures.add("the book is owed before any shadow");
        }
        if (LoreEvents.bookOwed(false, -1, 1, true, false)) {
            failures.add("the book is owed while the shadow is still passing");
        }
        if (LoreEvents.bookOwed(true, -1, 1, false, false) || LoreEvents.bookOwed(false, 100, 1, false, false)
                || LoreEvents.bookOwed(false, -1, 1, false, true)) {
            failures.add("the book is owed again after it was given, planned or dropped");
        }
    }

    // ---- 岩鯨の門
    private static void whaleGate(List<String> failures) {
        long eve = 11500;
        SkyEventData none = new SkyEventData();
        if (TitanEvents.whaleMayAppear(none, false, 2 * 24000 + eve, 1.0, true)) {
            failures.add("the whale may appear before any shadow");
        }
        SkyEventData seen = new SkyEventData();
        seen.countPass(1000);
        if (TitanEvents.whaleMayAppear(seen, true, 2 * 24000 + eve, 1.0, true)) {
            failures.add("the whale may appear while the shadow is passing");
        }
        if (TitanEvents.whaleMayAppear(seen, false, 1000 + 11999, 1.0, true)) {
            failures.add("the whale may appear less than half a day after the first shadow");
        }
        if (!TitanEvents.whaleMayAppear(seen, false, 2 * 24000 + eve, 1.0, true)) {
            failures.add("the whale may not appear on the evening of day 3 after the shadow and the storyteller");
        }
        if (TitanEvents.whaleMayAppear(seen, false, 2 * 24000 + eve, 1.0, false)) {
            failures.add("the whale appears before anyone met the storyteller");
        }
        if (TitanEvents.whaleMayAppear(seen, false, 2 * 24000 + 3000, 1.0, true)) {
            failures.add("the whale appears outside the evening");
        }
        // 詰み止め: 5 日目に入ったら、語り部に会っていなくても出す
        if (!TitanEvents.whaleMayAppear(seen, false, 4 * 24000 + 3000, 1.0, false)) {
            failures.add("the whale never appears for someone who skipped the storyteller");
        }
        // 影を起こさない設定 (頻度 0) では、門は開いている
        if (!TitanEvents.whaleMayAppear(none, false, 0, 0.0, false)) {
            failures.add("the whale never appears when shadows are disabled");
        }
        // 影より前に遊んでいた古いワールド (通過の数だけある) は、最初の影は済んでいるものとして扱う
        CompoundTag old = new CompoundTag();
        old.putInt("passCount", 3);
        SkyEventData oldWorld = SkyEventData.load(old, null);
        if (oldWorld.firstPassTick() >= 0 || !TitanEvents.whaleMayAppear(oldWorld, false, 2 * 24000 + eve, 1.0, true)) {
            failures.add("an older world with past passes is gated");
        }
    }

    // ---- 案内の手順
    private static void guideSteps(List<String> failures) {
        OpeningBeats.Move m;
        // 会った人は何もしない
        if ((m = OpeningBeats.decide(true, false, 2, 999999, 0, -1, 0, -1)) != OpeningBeats.Move.DONE) {
            failures.add("met player: " + m);
        }
        // 村 (鐘か野営) が近ければ任せる
        if ((m = OpeningBeats.decide(false, true, 0, 999999, 0, -1, 0, -1)) != OpeningBeats.Move.WAIT) {
            failures.add("near a base: " + m);
        }
        // 影が去って 1 分: 村を探す
        if (OpeningBeats.decide(false, false, 0, OpeningBeats.HINT_AFTER - 1, 0, -1, 0, -1) != OpeningBeats.Move.WAIT
                || OpeningBeats.decide(false, false, 0, OpeningBeats.HINT_AFTER, 0, -1, 0, -1) != OpeningBeats.Move.SEARCH) {
            failures.add("the village search is not planned at 1 minute");
        }
        // 近くに村が無い: すぐ野営
        if ((m = OpeningBeats.decide(false, false, 2, 1300, 0, -1, 0, -1)) != OpeningBeats.Move.CAMP) {
            failures.add("no village nearby but no camp: " + m);
        }
        // 村が見つかった: 知らせ直し (半日おき、3 回まで)、それでも会えなければ野営
        long hinted = 1300;
        if (OpeningBeats.decide(false, false, 1, hinted + OpeningBeats.REHINT - 1, 0, hinted, 1, -1) != OpeningBeats.Move.WAIT
                || OpeningBeats.decide(false, false, 1, hinted + OpeningBeats.REHINT, 0, hinted, 1, -1) != OpeningBeats.Move.HINT) {
            failures.add("the village hint is not repeated after half a day");
        }
        if ((m = OpeningBeats.decide(false, false, 1, hinted + OpeningBeats.REHINT + OpeningBeats.REHINT / 2, 0, hinted, OpeningBeats.MAX_HINTS, -1)) != OpeningBeats.Move.WAIT) {
            failures.add("hints go past the maximum: " + m);
        }
        if ((m = OpeningBeats.decide(false, false, 1, OpeningBeats.HINT_AFTER + OpeningBeats.CAMP_AFTER, 0, hinted, OpeningBeats.MAX_HINTS, -1))
                != OpeningBeats.Move.CAMP) {
            failures.add("still unmet after the village hints but no camp: " + m);
        }
        // 野営を作ったあとは、1 日待ってから作り直す
        if (OpeningBeats.decide(false, false, 2, 5000 + OpeningBeats.CAMP_AFTER - 1, 0, -1, 0, 5000) != OpeningBeats.Move.WAIT
                || OpeningBeats.decide(false, false, 2, 5000 + OpeningBeats.CAMP_AFTER, 0, -1, 0, 5000) != OpeningBeats.Move.CAMP) {
            failures.add("the camp is not rebuilt after a day");
        }
        // 案内は、最初の影が済んでから (影を起こさない設定なら最初から)
        SkyEventData none = new SkyEventData();
        SkyEventData seen = new SkyEventData();
        seen.countPass(0);
        if (OpeningBeats.started(none, false, 1.0) || !OpeningBeats.started(seen, false, 1.0) || OpeningBeats.started(seen, true, 1.0)
                || !OpeningBeats.started(none, false, 0.0)) {
            failures.add("the guide starts at the wrong time");
        }
        // 方角: 北は -z、東は +x
        BlockPos o = BlockPos.ZERO;
        if (OpeningBeats.compass(o, new BlockPos(0, 0, -100)) != 0 || OpeningBeats.compass(o, new BlockPos(100, 0, 0)) != 2
                || OpeningBeats.compass(o, new BlockPos(0, 0, 100)) != 4 || OpeningBeats.compass(o, new BlockPos(-100, 0, 0)) != 6
                || OpeningBeats.compass(o, new BlockPos(100, 0, -100)) != 1) {
            failures.add("the compass directions are off");
        }
    }

    // ---- 鐘のない所に野営が立つ
    private static void camp(ServerLevel level, List<String> failures) {
        // 平らな草地を作る (地形に左右されないように)。半径 44 ブロック、その上は空
        for (int x = X - 44; x <= X + 44; x++) {
            for (int z = Z - 44; z <= Z + 44; z++) {
                level.setBlock(new BlockPos(x, Y, z), Blocks.GRASS_BLOCK.defaultBlockState(), 2);
                for (int y = Y + 1; y <= Y + 14; y++) {
                    level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }
        BlockPos center = new BlockPos(X, Y + 1, Z);
        NpcWorldData data = NpcWorldData.get(level.getServer().overworld());
        BlockPos fire = NpcSpawner.buildCamp(level, center, level.random);
        if (fire == null) {
            failures.add("no camp could be built on flat grass");
            return;
        }
        double distance = Math.sqrt(fire.distSqr(new BlockPos(X, fire.getY(), Z)));
        if (distance < 16 || distance > 36 || !level.getBlockState(fire).is(Blocks.CAMPFIRE)) {
            failures.add("the campfire is at a strange place: " + fire + " (" + distance + ")");
        }
        if (NpcSpawner.nearestCamp(data, center, 48) == null) {
            failures.add("the camp is not recorded");
        }
        NpcEntity storyteller = NpcSpawner.spawnStoryteller(level, fire);
        if (storyteller == null) {
            failures.add("no storyteller stood by the camp");
        } else {
            double d = storyteller.distanceToSqr(fire.getX() + 0.5, fire.getY(), fire.getZ() + 0.5);
            if (d > 100 || !storyteller.role().equals(NpcSetup.STORYTELLER_ID)) {
                failures.add("the storyteller is not by the fire: " + d);
            }
            if (level.getEntitiesOfClass(NpcEntity.class, new AABB(fire).inflate(12)).isEmpty()) {
                failures.add("the storyteller cannot be found near the camp");
            }
            storyteller.discard();
        }
        // 片付け
        level.setBlock(fire, Blocks.AIR.defaultBlockState(), 3);
        data.camps.remove(fire.asLong());
        data.setDirty();
    }
}
