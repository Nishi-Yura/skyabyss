package jp.revati.skyabyss.rpg;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import io.netty.buffer.Unpooled;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.rpg.GrowthRules.Stat;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.neoforge.common.util.FakePlayer;

// 成長の自己テスト。SKYABYSS_SELFTEST=1 で専用サーバーを起動したときだけ動く (止めるのは TitanBackSelfTest)。
// レベルの計算、保存 (NBT・同期・死亡時の引き継ぎ・プレイヤーの保存と読み込み)、修飾子の付け外し、振り分けの検めを確かめる
public final class GrowthSelfTest {
    private GrowthSelfTest() {
    }

    // 呼ぶのは SelfTestRunner (SKYABYSS_SELFTEST=1 のとき、1 tick に 1 系統ずつ)
    public static void runSelfTest(MinecraftServer server) {
        List<String> failures = new ArrayList<>();
        try {
            run(server, failures);
        } catch (Exception e) {
            SkyAbyss.LOGGER.error("growth selftest crashed", e);
            failures.add("crashed: " + e);
        }
        if (failures.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST growth PASSED (curve, levels, points, nbt, sync codec, death copy, player save/load, modifiers, allocation, sharing)");
        } else {
            failures.forEach(f -> SkyAbyss.LOGGER.error("SELFTEST growth FAILED: {}", f));
        }
    }

    // ---------------------------------------------------------------- 序盤の進み (17 の 5.2、04 の 3.6)
    // 拍子どおりに進めたときの経験値を、実際のデータ (クエストの段・伝承の表・定数) から積み上げ、レベルの節目を確かめる。
    // 敵と買い物の量は控えめに見積もる (最初の夜に 5 体、日 2 に 5 体、バイオームは 1 つ 10)。
    static void checkOpeningPace(List<String> f) {
        var song = jp.revati.skyabyss.quest.QuestManager.quest("main_song");
        check(f, song != null, "main_song is missing");
        if (song == null) {
            return;
        }
        long xp = 0;
        // B0〜B3: 目覚め (最初のバイオーム)、帳面、野営地、足跡、鐘の宿り
        xp += GrowthRewards.BIOME_XP + lore("prologue/the_book") + lore("prologue/camp") + lore("prologue/footprints") + stage(song, "follow_smoke");
        int lodge = levelAt(xp);
        check(f, lodge == 2, "arriving at the lodge should give level 2, got " + lodge + " (" + xp + " xp)");
        // B4〜B5: 最初の影、語り部
        xp += lore("prologue/shadow") + stage(song, "watch_sky") + stage(song, "ask_storyteller") + 2 * GrowthRewards.BIOME_XP;
        int storyteller = levelAt(xp);
        check(f, storyteller == 3, "after the storyteller should be level 3, got " + storyteller + " (" + xp + " xp)");
        // B6: 最初の夜 (足音と、近づく敵を 5 体)
        xp += lore("prologue/footsteps") + 5 * 4;
        // B7〜B8: イサナ、骨の丘、日 2 の敵
        xp += stage(song, "find_chaser") + lore("prologue/bone_hill") + 5 * 4;
        int hour = levelAt(xp);
        long hourXp = xp;
        check(f, hour == 3, "after the bone hill (about 1 hour) should be level 3, got " + hour + " (" + xp + " xp)");
        // B9〜B10: 歌、岩鯨の初登場
        xp += lore("prologue/sky_song") + lore("chapter1/stone_whale") + stage(song, "see_whale") + 5 * 4;
        int whaleSeen = levelAt(xp);
        check(f, whaleSeen >= 4 && whaleSeen <= 5, "seeing the whale should be level 4-5, got " + whaleSeen + " (" + xp + " xp)");
        // 岩鯨の背の初回クリア: 新しい地、休み場 6 か所、道中の敵、番兵長、心臓、試練 3 つ、段「乗る」「碑文」、物語の報酬
        long back = GrowthRewards.DIMENSION_XP + 6L * GrowthRewards.REST_POINT_XP + 172
                + GrowthRewards.CAPTAIN_XP + GrowthRewards.CAPTAIN_FIRST_BONUS + GrowthRewards.HEART_XP + GrowthRewards.HEART_FIRST_BONUS
                + 3L * GrowthRewards.TRIAL_XP + stage(song, "board") + stage(song, "inscriptions") + song.reward().xp();
        long clear = xp + back;
        int mainOnly = levelAt(clear);
        check(f, mainOnly >= 10 && mainOnly <= 12, "first clear of the stone whale should be level 10-12, got " + mainOnly + " (" + clear + " xp)");
        // 序盤のサブクエスト (イサナと会ったあとに開く): 数え歌 40、影の帳面 60、包み 50、白いところ 150、日替わり 2 つ (薪 30・夜回り 40)
        long subs = 40 + 60 + 50 + 150 + 30 + 40;
        int withSubs = levelAt(clear + subs);
        check(f, withSubs >= 11 && withSubs <= 13, "first clear with the early side quests should be level 11-13, got " + withSubs);
        // 技: Lv1 の 1 点で最初の一つ。Lv3 で 2 つ目、Lv5 で 3 つ目 (二段目の技の必要 Lv も 5)
        check(f, jp.revati.skyabyss.rpg.skill.SkillRules.pointsForLevel(1) == 1 && jp.revati.skyabyss.rpg.skill.SkillRules.pointsForLevel(3) == 2
                && jp.revati.skyabyss.rpg.skill.SkillRules.pointsForLevel(5) == 3, "skill points at level 1, 3, 5 are wrong");
        SkyAbyss.LOGGER.info("[selftest] growth opening pace: lodge Lv{} / storyteller Lv{} / bone hill (about 1 h) Lv{} ({} xp) / whale seen Lv{} / first clear Lv{} ({} xp), with side quests Lv{}",
                lodge, storyteller, hour, hourXp, whaleSeen, mainOnly, clear, withSubs);
    }

    private static int lore(String id) {
        return GrowthRewards.LORE_XP.getOrDefault(id, 0);
    }

    private static int stage(jp.revati.skyabyss.quest.QuestDef quest, String id) {
        var s = quest.stage(id);
        return s == null ? 0 : s.xp();
    }

    private static int levelAt(long totalXp) {
        GrowthData d = new GrowthData();
        Growth.addXpRaw(d, totalXp);
        return d.level;
    }

    private static void check(List<String> failures, boolean ok, String what) {
        if (!ok) {
            failures.add(what);
        }
    }

    static void run(MinecraftServer server, List<String> f) {
        // ---------------------------------------------------------------- 経験値の曲線
        for (int l = 1; l < GrowthRules.MAX_LEVEL - 1; l++) {
            check(f, GrowthRules.xpToNext(l + 1) > GrowthRules.xpToNext(l), "curve is not increasing at " + l);
        }
        check(f, GrowthRules.xpToNext(GrowthRules.MAX_LEVEL) == 0, "max level still needs xp");
        check(f, GrowthRules.xpToNext(1) == 50, "level 1 needs " + GrowthRules.xpToNext(1));
        check(f, GrowthRules.totalXpFor(10) == 1758, "total to 10 is " + GrowthRules.totalXpFor(10));
        check(f, GrowthRules.totalXpFor(GrowthRules.MAX_LEVEL) == 107898, "total to 50 is " + GrowthRules.totalXpFor(50));
        check(f, GrowthRules.pointsForLevel(1) == 0 && GrowthRules.pointsForLevel(50) == 147, "points per level are wrong");
        StringBuilder table = new StringBuilder();
        for (int l : new int[] {1, 5, 10, 15, 20, 25, 30, 35, 40, 45, 49}) {
            table.append(" L").append(l).append('=').append(GrowthRules.xpToNext(l)).append('/').append(GrowthRules.totalXpFor(l + 1));
        }
        SkyAbyss.LOGGER.info("[selftest] growth curve (to next / total to next level):{}", table);

        // ---------------------------------------------------------------- レベルの計算
        GrowthData d = new GrowthData();
        check(f, Growth.addXpRaw(d, 49) == 0 && d.level == 1 && d.xp == 49, "49 xp should stay at level 1");
        check(f, Growth.addXpRaw(d, 1) == 1 && d.level == 2 && d.xp == 0, "50 xp should reach level 2 exactly");
        d = new GrowthData();
        check(f, Growth.addXpRaw(d, GrowthRules.totalXpFor(5) + 7) == 4 && d.level == 5 && d.xp == 7, "multi-level gain is wrong: " + d.level + "/" + d.xp);
        check(f, d.unspentPoints() == 12, "level 5 should have 12 points, has " + d.unspentPoints());
        Growth.addXpRaw(d, 10_000_000L);
        check(f, d.level == GrowthRules.MAX_LEVEL && d.xp == 0, "overflow should stop at the cap with an empty bar");
        check(f, Growth.addXpRaw(d, 500) == 0 && d.xp == 0, "xp at the cap should be discarded");

        // ---------------------------------------------------------------- 振り分けの検め
        d = new GrowthData();
        d.level = 6; // 15 点
        check(f, !Growth.canAllocate(d, new int[] {0, 0, 0, 0}), "an empty allocation was accepted");
        check(f, !Growth.canAllocate(d, new int[] {-1, 2, 0, 0}), "a negative allocation was accepted");
        check(f, !Growth.canAllocate(d, new int[] {16, 0, 0, 0}), "an allocation over the points was accepted");
        check(f, !Growth.canAllocate(d, new int[] {1, 0}), "a short allocation was accepted");
        check(f, Growth.canAllocate(d, new int[] {5, 5, 3, 2}), "a valid allocation was refused");
        // 巨大な値で int があふれても通らない (受け取るのは改造されうるクライアントの値)
        int keep = d.stats[0];
        d.stats[0] = 1;
        check(f, !Growth.canAllocate(d, new int[] {Integer.MAX_VALUE, Integer.MAX_VALUE, 0, 3}), "an overflowing allocation was accepted");
        d.stats[0] = keep;
        d.level = 50;
        d.stats[0] = GrowthRules.STAT_CAP;
        check(f, !Growth.canAllocate(d, new int[] {1, 0, 0, 0}), "an allocation over the stat cap was accepted");
        // レベルを下げられて、使った力が持っている力より多い → 全部戻る
        d.level = 3;
        d.sanitize();
        check(f, d.spentPoints() == 0 && d.unspentPoints() == 6, "overspent stats were not refunded");
        d.level = 99;
        d.xp = -5;
        d.sanitize();
        check(f, d.level == 50 && d.xp == 0, "sanitize did not clamp the level");

        // ---------------------------------------------------------------- 分け方と補正
        check(f, GrowthRules.partyShare(1) == 1.0 && Math.abs(GrowthRules.partyShare(2) - 0.625) < 1e-9
                && Math.abs(GrowthRules.partyShare(4) - 0.4375) < 1e-9, "party share is wrong");
        // 人数で湧きが増える広間 (第一の広間の第一波: 1 人 = 番兵 2・苔這い 2、1 人増えるごとに番兵 +2) で、一人あたりがほぼ同じか
        double solo = 2 * 12 + 2 * 9;
        for (int n = 2; n <= 4; n++) {
            double each = ((2 + 2 * (n - 1)) * 12 + 2 * 9) * GrowthRules.partyShare(n);
            check(f, each >= solo * 0.85 && each <= solo * 1.6, "hall xp per person for " + n + " players is " + each + " (solo " + solo + ")");
            SkyAbyss.LOGGER.info("[selftest] growth hall E1 wave 1 xp per person: {} players -> {} (solo {})", n, String.format("%.1f", each), solo);
        }
        check(f, GrowthRules.levelFalloff(15, 12) == 1.0 && Math.abs(GrowthRules.levelFalloff(20, 12) - 0.5) < 1e-9
                && GrowthRules.levelFalloff(40, 12) == GrowthRules.FALLOFF_FLOOR, "level falloff is wrong");
        check(f, GrowthRules.roundRandom(2.5, 0.4) == 3 && GrowthRules.roundRandom(2.5, 0.6) == 2 && GrowthRules.roundRandom(3, 0.0) == 3,
                "random rounding is wrong");
        checkOpeningPace(f);
        ServerLevel overworld = server.overworld();
        GrowthData budget = new GrowthData();
        // 日ごとの上限は 100 (00 の 26)
        check(f, GrowthRules.VANILLA_DAILY == 100, "vanilla daily cap is " + GrowthRules.VANILLA_DAILY + ", expected 100");
        // 上限 (VANILLA_DAILY) の 10 手前まで使い、次の 20 は 10 が満額・10 が疲れ
        double first = GrowthRewards.vanillaBudget(overworld, budget, GrowthRules.VANILLA_DAILY - 10);
        double second = GrowthRewards.vanillaBudget(overworld, budget, 20);
        check(f, first == GrowthRules.VANILLA_DAILY - 10 && Math.abs(second - (10 + 10 * GrowthRules.VANILLA_TIRED)) < 1e-9,
                "vanilla daily budget is wrong: " + first + ", " + second);

        // ---------------------------------------------------------------- 保存 (NBT) と同期のコーデック
        GrowthData a = sample();
        GrowthData b = new GrowthData();
        b.deserializeNBT(server.registryAccess(), a.serializeNBT(server.registryAccess()));
        check(f, same(a, b, true), "nbt round trip lost data");
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), server.registryAccess());
        GrowthData.STREAM_CODEC.encode(buf, a);
        GrowthData c = GrowthData.STREAM_CODEC.decode(buf);
        check(f, same(a, c, false), "sync codec lost data");
        check(f, c.discoveries.isEmpty(), "sync codec sent the discoveries (server only)");
        buf.release();

        // ---------------------------------------------------------------- プレイヤー: 修飾子、死亡時の引き継ぎ、保存と読み込み
        FakePlayer p = new FakePlayer(overworld, new GameProfile(UUID.randomUUID(), "growth_test_a"));
        AttributeInstance mana = p.getAttribute(Rpg.MAX_MANA);
        check(f, mana != null && mana.getValue() == GrowthRules.BASE_MANA, "players have no max mana attribute");
        double baseSpeed = p.getAttributeValue(Attributes.MOVEMENT_SPEED);
        GrowthData pd = Growth.get(p);
        copy(a, pd);
        pd.stats[Stat.STRENGTH.ordinal()] = 10;
        pd.stats[Stat.AGILITY.ordinal()] = 20;
        pd.stats[Stat.SPIRIT.ordinal()] = 5;
        pd.stats[Stat.VITALITY.ordinal()] = 9;
        p.setHealth(p.getMaxHealth());
        Growth.applyModifiers(p);
        check(f, modifier(p, Attributes.ATTACK_DAMAGE, Growth.MOD_STRENGTH, 0.06, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL),
                "strength modifier missing or wrong");
        check(f, modifier(p, Attributes.MOVEMENT_SPEED, Growth.MOD_AGILITY_MOVE, 0.04, AttributeModifier.Operation.ADD_MULTIPLIED_BASE),
                "agility move modifier missing or wrong");
        check(f, modifier(p, Attributes.ATTACK_SPEED, Growth.MOD_AGILITY_ATTACK, 0.04, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL),
                "agility attack speed modifier missing or wrong");
        check(f, Math.abs(p.getAttributeValue(Attributes.MOVEMENT_SPEED) - baseSpeed * 1.04) < 1e-6, "agility did not add on top of the base speed");
        check(f, Math.abs(p.getMaxHealth() - 22f) < 1e-4, "vitality 9 should give 22 max health, got " + p.getMaxHealth());
        check(f, Math.abs(p.getHealth() - 22f) < 1e-4, "a full-health player was not topped up to the new max");
        check(f, p.getAttributeValue(Rpg.MAX_MANA) == 30, "spirit 5 should give 30 mana, got " + p.getAttributeValue(Rpg.MAX_MANA));
        // 付け直しても重ならない
        Growth.applyModifiers(p);
        check(f, Math.abs(p.getMaxHealth() - 22f) < 1e-4, "applying twice stacked the modifiers");

        // 死亡時の引き継ぎ (PlayerEvent.Clone と同じ呼び方)
        FakePlayer reborn = new FakePlayer(overworld, new GameProfile(UUID.randomUUID(), "growth_test_b"));
        reborn.copyAttachmentsFrom(p, true);
        check(f, same(pd, Growth.get(reborn), true), "growth data was not copied on death");

        // プレイヤーの保存と読み込み (ログアウト・ログイン)。体力がバニラの 20 に切り詰められないこと
        CompoundTag saved = new CompoundTag();
        p.saveWithoutId(saved);
        FakePlayer loaded = new FakePlayer(overworld, new GameProfile(UUID.randomUUID(), "growth_test_c"));
        loaded.load(saved);
        check(f, same(pd, Growth.get(loaded), true), "growth data was not saved with the player");
        check(f, Math.abs(loaded.getMaxHealth() - 22f) < 1e-4 && Math.abs(loaded.getHealth() - 22f) < 1e-4,
                "after loading, health was " + loaded.getHealth() + "/" + loaded.getMaxHealth() + " (expected 22/22)");

        // 振り分けを戻すと修飾子が外れ、体力は新しい上限に収まる
        java.util.Arrays.fill(pd.stats, 0);
        Growth.applyModifiers(p);
        check(f, p.getAttribute(Attributes.ATTACK_DAMAGE).getModifier(Growth.MOD_STRENGTH) == null
                && p.getAttribute(Attributes.MAX_HEALTH).getModifier(Growth.MOD_VITALITY) == null
                && p.getAttribute(Rpg.MAX_MANA).getModifier(Growth.MOD_SPIRIT) == null, "modifiers were not removed after a reset");
        check(f, p.getMaxHealth() == 20f && p.getHealth() <= 20f, "health was not clamped after a reset");

        // ステータスの振り直し (00 の 3 = A): 炉のそばで、技と同じ費用 (初回無料、以後結晶)。回数は技と別
        checkStatRespec(f, overworld, p);

        // 記憶の断片の器: 上限を超えて入らない
        GrowthData frag = new GrowthData();
        frag.fragmentCapacity = 2;
        check(f, frag.addFragments(MemoryFragments.PROLOGUE, 3) == 2 && frag.fragmentCount() == 2, "fragments overflowed the capacity");
        check(f, frag.addFragments(MemoryFragments.STONE_WHALE, 1) == 0 && !frag.fragments.containsKey(MemoryFragments.STONE_WHALE),
                "a full container accepted a fragment");
        check(f, frag.addFragments(MemoryFragments.PROLOGUE, -4) == 0, "a negative fragment count was accepted");
    }

    private static void checkStatRespec(List<String> f, ServerLevel level, FakePlayer p) {
        check(f, GrowthRules.statRespecCost(0) == 0 && GrowthRules.statRespecCost(2) == 2
                && GrowthRules.statRespecCost(99) == jp.revati.skyabyss.rpg.skill.SkillRules.RESPEC_MAX_COST, "stat respec cost is wrong");
        GrowthData pd = Growth.get(p);
        p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        java.util.Arrays.fill(pd.stats, 0);
        pd.statRespecs = 0;
        check(f, Growth.respecStats(p) == jp.revati.skyabyss.rpg.skill.SkillManager.RespecResult.NOTHING, "stat respec with nothing spent did not refuse");
        pd.level = 10;
        pd.stats[Stat.STRENGTH.ordinal()] = 4;
        net.minecraft.core.BlockPos at = level.getSharedSpawnPos().atY(level.getMaxBuildHeight() - 8);
        p.moveTo(at.getX() + 3.5, at.getY(), at.getZ() + 0.5);
        check(f, Growth.respecStats(p) == jp.revati.skyabyss.rpg.skill.SkillManager.RespecResult.NO_FORGE, "stat respec worked away from a forge");
        net.minecraft.world.level.block.state.BlockState old = level.getBlockState(at);
        level.setBlock(at, jp.revati.skyabyss.registry.ModBlocks.RESONANCE_FORGE.get().defaultBlockState(), 3);
        try {
            p.getInventory().clearContent();
            // 初回は無料
            check(f, Growth.respecStats(p) == jp.revati.skyabyss.rpg.skill.SkillManager.RespecResult.OK
                    && pd.spentPoints() == 0 && pd.statRespecs == 1, "the first stat respec near a forge was not free");
            check(f, jp.revati.skyabyss.rpg.skill.SkillManager.get(p).respecs == 0, "a stat respec counted as a skill respec");
            // 2 回目は結晶 1 個
            pd.stats[Stat.SPIRIT.ordinal()] = 3;
            check(f, Growth.respecStats(p) == jp.revati.skyabyss.rpg.skill.SkillManager.RespecResult.NO_CRYSTALS
                    && pd.stats[Stat.SPIRIT.ordinal()] == 3, "the second stat respec did not ask for crystals");
            p.getInventory().add(new net.minecraft.world.item.ItemStack(jp.revati.skyabyss.registry.ModItems.RESONANCE_CRYSTAL.get(), 3));
            check(f, Growth.respecStats(p) == jp.revati.skyabyss.rpg.skill.SkillManager.RespecResult.OK
                    && pd.spentPoints() == 0 && pd.statRespecs == 2
                    && jp.revati.skyabyss.rpg.skill.SkillManager.countCrystals(p.getInventory()) == 2, "the second stat respec did not take 1 crystal");
        } finally {
            level.setBlock(at, old, 3);
            p.getInventory().clearContent();
        }
    }

    private static boolean modifier(FakePlayer p, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute,
            net.minecraft.resources.ResourceLocation id, double amount, AttributeModifier.Operation op) {
        AttributeInstance instance = p.getAttribute(attribute);
        AttributeModifier m = instance == null ? null : instance.getModifier(id);
        return m != null && Math.abs(m.amount() - amount) < 1e-9 && m.operation() == op;
    }

    private static GrowthData sample() {
        GrowthData a = new GrowthData();
        a.level = 23;
        a.xp = 101;
        a.stats[0] = 12;
        a.stats[1] = 20;
        a.stats[2] = 7;
        a.stats[3] = 9;
        a.bonusPoints = 2;
        a.fragments.put(MemoryFragments.PROLOGUE, 1);
        a.fragments.put(MemoryFragments.STONE_WHALE, 2);
        a.fragmentCapacity = 14;
        a.discoveries.add("biome:minecraft:plains");
        a.discoveries.add("first:skyabyss:heart");
        a.vanillaDay = 4;
        a.vanillaToday = 77;
        a.statRespecs = 3;
        return a;
    }

    private static void copy(GrowthData from, GrowthData to) {
        to.level = from.level;
        to.xp = from.xp;
        System.arraycopy(from.stats, 0, to.stats, 0, from.stats.length);
        to.bonusPoints = from.bonusPoints;
        to.fragments.clear();
        to.fragments.putAll(from.fragments);
        to.fragmentCapacity = from.fragmentCapacity;
        to.statRespecs = from.statRespecs;
        to.discoveries.clear();
        to.discoveries.addAll(from.discoveries);
    }

    private static boolean same(GrowthData a, GrowthData b, boolean serverFields) {
        boolean ok = a.level == b.level && a.xp == b.xp && java.util.Arrays.equals(a.stats, b.stats) && a.bonusPoints == b.bonusPoints
                && a.fragments.equals(b.fragments) && a.fragmentCapacity == b.fragmentCapacity
                && a.statRespecs == b.statRespecs;
        if (serverFields) {
            ok &= a.discoveries.equals(b.discoveries);
        }
        return ok;
    }
}
