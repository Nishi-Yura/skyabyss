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
//  - 方角の 8 方位
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
            compassSteps(failures);
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

    // ---- 方角
    private static void compassSteps(List<String> failures) {
        // 方角: 北は -z、東は +x
        BlockPos o = BlockPos.ZERO;
        if (OpeningBeats.compass(o, new BlockPos(0, 0, -100)) != 0 || OpeningBeats.compass(o, new BlockPos(100, 0, 0)) != 2
                || OpeningBeats.compass(o, new BlockPos(0, 0, 100)) != 4 || OpeningBeats.compass(o, new BlockPos(-100, 0, 0)) != 6
                || OpeningBeats.compass(o, new BlockPos(100, 0, -100)) != 1) {
            failures.add("the compass directions are off");
        }
    }
}
