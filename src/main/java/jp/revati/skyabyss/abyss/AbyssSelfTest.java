package jp.revati.skyabyss.abyss;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import jp.revati.skyabyss.SelfTestRunner.Step;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.entity.HeartBoss;
import jp.revati.skyabyss.entity.StoneWhaleHeart;
import jp.revati.skyabyss.item.GearTier;
import jp.revati.skyabyss.item.ModGear;
import jp.revati.skyabyss.loot.TitanMemoryData;
import jp.revati.skyabyss.progress.Titans;
import jp.revati.skyabyss.registry.ModEntities;
import jp.revati.skyabyss.rpg.Growth;
import jp.revati.skyabyss.rpg.skill.Rune;
import jp.revati.skyabyss.rpg.skill.Skill;
import jp.revati.skyabyss.rpg.skill.SkillActions;
import jp.revati.skyabyss.rpg.skill.SkillData;
import jp.revati.skyabyss.rpg.skill.SkillEffects;
import jp.revati.skyabyss.rpg.skill.SkillManager;
import jp.revati.skyabyss.titan.group.BackGroupData;
import jp.revati.skyabyss.titan.group.GroupProgress;
import jp.revati.skyabyss.trial.Trial;
import jp.revati.skyabyss.trial.Trial.Def;
import jp.revati.skyabyss.trial.Trial.Mods;
import jp.revati.skyabyss.trial.Trial.Rarity;
import jp.revati.skyabyss.trial.TrialBoard;
import jp.revati.skyabyss.trial.TrialCatalog;
import jp.revati.skyabyss.trial.TrialCell;
import jp.revati.skyabyss.trial.TrialData;
import jp.revati.skyabyss.trial.TrialDraw;
import jp.revati.skyabyss.trial.whale.W06Score;
import jp.revati.skyabyss.trial.whale.W07Gates;
import jp.revati.skyabyss.trial.whale.W10Fort;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.util.FakePlayer;

// 深淵 (docs/design/31 の B04) の自己テスト。SKYABYSS_SELFTEST_ONLY="abyss"。
//   転生での開閉・つまみの決まり / 修飾の段の数式 (全試練 × 乱数) / 深淵の心臓の湧き (つまみ → 心臓) / 専用伝説の落とし表 /
//   4 つの書き換えの効き (生きた敵と時間を使う。複数 tick の段)
// 心臓の「予告を読む人形」の生き残りは combat/HeartBotSelfTest の checkAbyss (SELFTEST heart bots) が見る
public final class AbyssSelfTest {
    private static final List<String> F = new ArrayList<>();
    private static final List<String> NOTES = new ArrayList<>();
    private static final double CX = 2000.5;
    private static final double CZ = 2000.5;
    private static final int FLOOR = 220;

    private AbyssSelfTest() {
    }

    private static void check(boolean ok, String what) {
        if (!ok) {
            F.add(what);
        }
    }

    public static void load(MinecraftServer server) {
        hold(server, true);
    }

    // 読み込みを固定したチャンクは、テストのあとに外す (外し忘れると、止めるときに保存が終わらないことがある)
    private static void hold(MinecraftServer server, boolean on) {
        ServerLevel level = server.overworld();
        for (int cx = 123; cx <= 127; cx++) {
            for (int cz = 123; cz <= 127; cz++) {
                level.setChunkForced(cx, cz, on);
            }
        }
    }

    public static List<Step> steps() {
        return List.of(
                Step.once("abyss load", AbyssSelfTest::load),
                Step.once("abyss (sync)", 20, AbyssSelfTest::syncPart),
                new Step("abyss (rewrites)", 0, AbyssSelfTest::rewrites),
                Step.once("abyss (unload)", server -> hold(server, false)));
    }

    // ---------------------------------------------------------------- 同期の部分
    private static void syncPart(MinecraftServer server) {
        F.clear();
        NOTES.clear();
        try {
            gating(server);
            tierMath(server);
            heartSpawn(server);
            dropTables(server);
            assets();
        } catch (RuntimeException e) {
            SkyAbyss.LOGGER.error("SELFTEST abyss crashed", e);
            F.add("crashed: " + e);
        }
    }

    private static FakePlayer fake(ServerLevel level, String name, int rebirths) {
        FakePlayer p = new FakePlayer(level, new GameProfile(UUID.nameUUIDFromBytes(("abyss_" + name).getBytes()), "[" + name + "]"));
        p.moveTo(CX, FLOOR + 1, CZ, 0f, 0f);
        jp.revati.skyabyss.rebirth.Rebirths.setCount(p, rebirths);
        return p;
    }

    private static BackGroupData.Record record(ServerLevel level) {
        return GroupProgress.of(level, UUID.randomUUID());
    }

    // ---- 転生での開閉・つまみ
    private static void gating(MinecraftServer server) {
        ServerLevel level = server.overworld();
        for (int n = 0; n <= 2; n++) {
            check(!Abyss.open(n), "abyss opened at rebirth " + n);
        }
        for (int n : new int[] {3, 4, 10, 20}) {
            check(Abyss.open(n), "abyss closed at rebirth " + n);
        }
        check(Abyss.OPEN_AT == 3, "abyss opens at " + Abyss.OPEN_AT + " (design: 3)");
        // 本物の人 (転生の数は RebirthView 経由)
        check(!Abyss.open(fake(level, "r0", 0)) && !Abyss.open(fake(level, "r2", 2)), "players with 0/2 rebirths could open the abyss");
        check(Abyss.open(fake(level, "r3", 3)) && Abyss.open(fake(level, "r9", 9)), "players with 3/9 rebirths could not open the abyss");
        // つまみ: 入るには 3 回以上、切るのは誰でも、同じ値は変わらない
        BackGroupData.Record r = record(level);
        check(TrialBoard.applyToggle(r, true, 2) == TrialBoard.Toggle.LOCKED && !Abyss.on(r), "rebirth 2 turned the toggle on");
        check(TrialBoard.applyToggle(r, true, 0) == TrialBoard.Toggle.LOCKED && !Abyss.on(r), "rebirth 0 turned the toggle on");
        check(TrialBoard.applyToggle(r, true, 3) == TrialBoard.Toggle.CHANGED && Abyss.on(r), "rebirth 3 could not turn the toggle on");
        check(TrialBoard.applyToggle(r, true, 3) == TrialBoard.Toggle.SAME, "turning on twice changed something");
        check(TrialBoard.applyToggle(r, false, 0) == TrialBoard.Toggle.CHANGED && !Abyss.on(r), "an unreborn ally could not turn the abyss off");
        check(TrialBoard.applyToggle(r, false, 0) == TrialBoard.Toggle.SAME, "turning off twice changed something");
        // 選んでいるグループには、未転生の仲間も入れる (状態の鍵ではない): 記録は個人の転生を見ない
        Abyss.set(r, true);
        check(Abyss.groupOn(level, r.id), "the group flag was not read back");
        NOTES.add("gating: opens at rebirth 3, toggle is per group");
    }

    // ---- 修飾の段の数式
    private static void tierMath(MinecraftServer server) {
        ServerLevel level = server.overworld();
        int defs = 0;
        int seeds = 0;
        for (Def def : TrialCatalog.all()) {
            defs++;
            Trial.Space sp = def.space();
            for (int seed = 0; seed < 160; seed++) {
                seeds++;
                Random rng = new Random(seed * 7919L + def.id().hashCode());
                Mods base = TrialDraw.rollMods(def, rng);
                Mods ab = TrialDraw.abyssify(def, base, new Random(seed));
                String tag = def.id() + "#" + seed;
                check(ab.abyss() && ab.rarity() == Rarity.ABYSS, tag + ": not an abyss tier");
                check(ab.layout() == base.layout() && ab.enemy() == base.enemy() && ab.quiet() == base.quiet(), tag + ": abyss changed the layout/enemy/quiet (only numbers may rise)");
                check(ab.numbers().length == base.numbers().length, tag + ": number count changed");
                for (int v : ab.numbers()) {
                    check(v == 2, tag + ": a number is not at its hardest (" + v + ")");
                }
                // 障害: 重ならない・範囲内・禁止の組なし・できれば二つ
                Set<Integer> hz = new HashSet<>();
                for (int h : ab.hazards()) {
                    check(hz.add(h) && h >= 0 && h < sp.hazards().size(), tag + ": bad hazard " + h);
                    check(!sp.forbids(ab.layout(), h) || base.hasHazard(h), tag + ": forbidden hazard " + h + " for layout " + ab.layout());
                }
                int allowed = 0;
                for (int h = 0; h < sp.hazards().size(); h++) {
                    if (!sp.forbids(ab.layout(), h)) {
                        allowed++;
                    }
                }
                check(ab.hazards().length >= Math.min(2, allowed), tag + ": only " + ab.hazards().length + " hazards of " + allowed + " allowed");
                check(sp.goals().isEmpty() ? ab.goal() == -1 : ab.goal() >= 0 && ab.goal() < sp.goals().size(), tag + ": goal " + ab.goal());
                for (int h : base.hazards()) {
                    check(ab.hasHazard(h), tag + ": abyss dropped a hazard the draw had");
                }
                check(ab.extreme() == (ab.hazards().length >= 2 ? Trial.Extreme.DOUBLE_HAZARD : ab.numbers().length > 0 ? Trial.Extreme.GAUNTLET : Trial.Extreme.NONE), tag + ": extreme kind");
                // 往復・冪等
                check(Mods.decode(ab.encode()).encode().equals(ab.encode()) && Mods.decode(ab.encode()).abyss(), tag + ": encode/decode lost the abyss tier");
                check(TrialDraw.abyssify(def, ab, new Random(seed + 1)).encode().equals(ab.encode()), tag + ": abyssify is not idempotent");
                // 報い: 極 (+2) より大きい (+3)。目標で +1
                Mods ext = new Mods(ab.layout(), ab.numbers(), ab.enemy(), ab.hazards(), ab.goal(), ab.extreme(), false, Rarity.EXTREME);
                int a0 = TrialDraw.materialCount(def, ab, false);
                int e0 = TrialDraw.materialCount(def, ext, false);
                check(a0 == e0 - 2 + Abyss.MATERIAL_BONUS && a0 > e0, tag + ": abyss materials " + a0 + " vs extreme " + e0);
                check(TrialDraw.materialCount(def, ab, true) == a0 + 1, tag + ": goal math");
            }
        }
        // 引き全体: つまみで重なり、戻せば元に戻る (保存しない)。同じ引きの配置・試練は変わらない
        BackGroupData.Record r = record(level);
        UUID group = r.id;
        r.putInt(TrialData.DONE, 3);
        TrialDraw.Draw off = TrialData.current(Titans.STONE_WHALE, r, group, 4242L);
        Abyss.set(r, true);
        TrialDraw.Draw on = TrialData.current(Titans.STONE_WHALE, r, group, 4242L);
        check(off.picks().size() == on.picks().size() && off.picks().size() == 3, "the draw size changed with the abyss");
        for (int i = 0; i < off.picks().size(); i++) {
            check(off.picks().get(i).cellId().equals(on.picks().get(i).cellId()), "the abyss changed which trials were drawn");
            check(on.picks().get(i).mods().abyss(), "pick " + i + " is not abyss while the group has the abyss on");
            check(!off.picks().get(i).mods().abyss(), "pick " + i + " is abyss while the group has it off");
        }
        check(TrialData.current(Titans.STONE_WHALE, r, group, 4242L).encode().equals(on.encode()), "the abyss draw is not reproducible");
        Abyss.set(r, false);
        check(TrialData.current(Titans.STONE_WHALE, r, group, 4242L).encode().equals(off.encode()), "turning the abyss off did not restore the draw");
        // 数字の底上げ: 極 (段 2) より厳しく、解ける線の下へは行かない。使われる 4 つの試練の式
        Mods hard = new Mods(0, new int[] {2, 2}, -1, new int[0], -1, Trial.Extreme.GAUNTLET, false, Rarity.EXTREME);
        Mods deep = new Mods(0, new int[] {2, 2}, -1, new int[0], -1, Trial.Extreme.GAUNTLET, false, Rarity.ABYSS);
        TrialCell.Run runHard = new TrialCell.Run(UUID.randomUUID(), null, hard, 1L, 0L);
        TrialCell.Run runDeep = new TrialCell.Run(UUID.randomUUID(), null, deep, 1L, 0L);
        check(W07Gates.graceOf(runDeep) < W07Gates.graceOf(runHard) && W07Gates.graceOf(runDeep) >= Abyss.FLOOR_TICKS, "gate grace " + W07Gates.graceOf(runHard) + " -> " + W07Gates.graceOf(runDeep));
        check(W06Score.length(runDeep) == W06Score.length(runHard) + 1, "score length did not grow by one");
        check(W10Fort.gunners(runDeep, 1) == W10Fort.gunners(runHard, 1) + 1, "fort gunners did not grow by one");
        Mods plain = Mods.PLAIN;
        check(Abyss.shorter(plain, 100) == 100 && Abyss.narrower(plain, 0.5) == 0.5 && Abyss.longer(plain, 60) == 60 && Abyss.plusOne(plain, 3) == 3, "non-abyss numbers changed");
        check(Abyss.shorter(deep, 65) == Abyss.FLOOR_TICKS && Abyss.narrower(deep, 0.42) == Abyss.FLOOR_WINDOW, "the floors do not hold");
        NOTES.add("tier math: " + defs + " trials x " + (seeds / Math.max(1, defs)) + " seeds, materials +" + Abyss.MATERIAL_BONUS);
    }

    // ---- 深淵の心臓の湧き (つまみ → 心臓)
    private static void heartSpawn(MinecraftServer server) {
        ServerLevel level = server.overworld();
        Difficulty before = server.getWorldData().getDifficulty();
        try {
            if (before == Difficulty.PEACEFUL) {
                server.setDifficulty(Difficulty.NORMAL, true);
            }
            // 床
            for (int x = -12; x <= 12; x++) {
                for (int z = -12; z <= 12; z++) {
                    level.setBlockAndUpdate(new BlockPos((int) CX + x, FLOOR, (int) CZ + z), Blocks.STONE.defaultBlockState());
                }
            }
            float[] dealt = new float[2];
            for (boolean on : new boolean[] {false, true}) {
                UUID group = UUID.randomUUID();
                Abyss.set(GroupProgress.of(level, group), on);
                StoneWhaleHeart h = ModEntities.STONE_WHALE_HEART.get().create(level);
                h.moveTo(CX, FLOOR + 1, CZ, 0f, 0f);
                h.setPersistenceRequired();
                h.setGroupId(group);
                h.skipIntro();
                level.addFreshEntity(h);
                h.tickCount++;
                h.tick();
                check(h.isAbyss() == on, "a heart of a group with abyss " + on + " reports abyss " + h.isAbyss());
                // 実質の体力 ×1.2: 同じ傷 (防具を通らない魔法の傷) で削れる量が 1/1.2
                h.invulnerableTime = 0;
                float h0 = h.getHealth();
                h.hurt(h.damageSources().magic(), 30f);
                float lost = h0 - h.getHealth();
                dealt[on ? 1 : 0] = lost;
                if (on) {
                    check(h.abyssDamage() > 1f && h.abyssThemeId().equals("whale"), "abyss heart damage / theme");
                }
                // 保存して読み戻すと印が残る
                net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
                h.addAdditionalSaveData(tag);
                check(tag.getBoolean("Abyss") == on, "the abyss flag was not saved");
                h.discard();
            }
            check(dealt[0] > 1f && Math.abs(dealt[0] / dealt[1] - Abyss.HEART_HP) < 0.02, "the same hit took " + dealt[0] + " from a normal heart and " + dealt[1] + " from an abyss heart (expected ratio " + Abyss.HEART_HP + ")");
            // 各心臓の 4 つが深淵の技の語・題字を持つ (日英)
            java.util.Map<String, String> ja;
            java.util.Map<String, String> en;
            try {
                ja = jp.revati.skyabyss.LangSelfTest.table("ja_jp");
                en = jp.revati.skyabyss.LangSelfTest.table("en_us");
            } catch (java.io.IOException e) {
                F.add("lang tables could not be read: " + e);
                return;
            }
            for (String theme : new String[] {"whale", "tide", "storm", "ember"}) {
                String key = "gui.skyabyss.attack." + theme + "." + AbyssAttacks.cueOf(theme);
                check(ja.containsKey(key) && en.containsKey(key), "no cue name for " + key);
                check(ja.containsKey("abyss.skyabyss.subtitle." + theme) && en.containsKey("abyss.skyabyss.subtitle." + theme), "no subtitle for " + theme);
            }
            check(ja.containsKey("abyss.skyabyss.title") && ja.get("abyss.skyabyss.title").equals("深淵の心臓"), "the abyss title is not 「深淵の心臓」");
            check(Abyss.ATTACK_WARN >= jp.revati.skyabyss.combat.Telegraph.MIN_WARN_TICKS, "the abyss attack warns less than the telegraph minimum");
            // 心臓の数字は帯の中: 傷 ×1.12 の最大の一撃が、想定の体力の帯の上限を超えない (四体の最も重い技の想定で)
            check(Abyss.ATTACK_DAMAGE * Abyss.HEART_DAMAGE <= 22f * 1.0f, "the abyss attack hits harder than the heaviest heart attack (22)");
            NOTES.add("abyss heart spawns only for abyss groups (hp x" + Abyss.HEART_HP + ")");
        } finally {
            server.setDifficulty(before, true);
        }
    }

    // ---- 落とし表
    private static void dropTables(MinecraftServer server) {
        // 四体 → 四つの専用伝説 (登録済み・伝説・その巨神の由来・書き換えの武器と一致)
        Set<String> ids = new HashSet<>();
        for (String titan : new String[] {Titans.STONE_WHALE, Titans.TIDE_SHELL, Titans.THUNDER_WING, Titans.EMBER}) {
            String id = AbyssSetup.legendOf(titan);
            check(ids.add(id), "two titans share the legend " + id);
            Item item = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, id));
            check(item != net.minecraft.world.item.Items.AIR, "legend " + id + " is not registered");
            check(item instanceof ModGear g && g.tier() == GearTier.LEGENDARY && g.dungeon().equals(titan), "legend " + id + " is not a legendary of " + titan);
            boolean rune = false;
            for (Rune r : Rune.values()) {
                if (r.weapon.equals(id)) {
                    rune = true;
                    check(r.legendary() && r.index == 4 && r.skill.active, "rune of " + id + " is not a legendary rewrite of an active skill");
                }
            }
            check(rune, "legend " + id + " rewrites nothing");
        }
        check(ids.equals(new HashSet<>(AbyssSetup.legends())), "legends() differs from legendOf()");
        check(AbyssLoot.titanOf("whale").equals(Titans.STONE_WHALE) && AbyssLoot.titanOf("tide").equals(Titans.TIDE_SHELL)
                && AbyssLoot.titanOf("storm").equals(Titans.THUNDER_WING) && AbyssLoot.titanOf("ember").equals(Titans.EMBER), "theme -> titan mapping");
        // 12 の書き換え (従来 8 + 深淵 4)。同じ技に二つはない
        int legends = 0;
        Set<Skill> skills = new HashSet<>();
        for (Rune r : Rune.values()) {
            if (r.legendary()) {
                legends++;
                check(skills.add(r.skill), "two legendary rewrites on " + r.skill.id);
            }
        }
        check(legends == 12, "legendary rewrites: " + legends + " (expected 12)");
        // 抽選: 天井 (LEGEND_PITY 回続けて出なければ保証)・確率・重複は欠片へ
        RandomSource rng = RandomSource.create(4242);
        int rolls = 20000;
        int drops = 0;
        int maxGap = 0;
        int gap = 0;
        TitanMemoryData.Record rec = new TitanMemoryData.Record();
        for (int i = 0; i < rolls; i++) {
            AbyssLoot.Roll roll = AbyssLoot.roll(rec, Titans.TIDE_SHELL, rng);
            gap++;
            if (roll.dropped()) {
                drops++;
                maxGap = Math.max(maxGap, gap);
                gap = 0;
                check(roll.dupe() == (drops > 1), "dupe flag wrong at drop " + drops);
            }
        }
        check(maxGap <= Abyss.LEGEND_PITY, "gap between abyss legends reached " + maxGap + " (pity " + Abyss.LEGEND_PITY + ")");
        double rate = drops / (double) rolls;
        // 期待値: 1 回の確率 p のとき、天井つきの 1 回あたり = 1 / (1 + (1-p) + (1-p)^2 + (1-p)^3)
        double p = Abyss.LEGEND_CHANCE;
        double expected = 1.0 / (1 + (1 - p) + Math.pow(1 - p, 2) + Math.pow(1 - p, 3));
        check(Math.abs(rate - expected) < 0.02, "drop rate " + rate + " vs expected " + expected);
        check(rec.clears == rolls && rec.owned.size() == 1, "record bookkeeping: clears " + rec.clears + " owned " + rec.owned);
        // 巨神ごとの記録は別 (別の巨神は新しく出る)
        TitanMemoryData.Record other = new TitanMemoryData.Record();
        int firstOther = 0;
        for (int i = 1; i <= Abyss.LEGEND_PITY && firstOther == 0; i++) {
            AbyssLoot.Roll roll = AbyssLoot.roll(other, Titans.EMBER, rng);
            if (roll.dropped()) {
                firstOther = i;
                check(!roll.dupe() && roll.legend().endsWith(AbyssSetup.ASH_SCYTHE), "the first ember drop was " + roll);
            }
        }
        check(firstOther > 0 && firstOther <= Abyss.LEGEND_PITY, "no ember legend within the pity");
        NOTES.add(String.format(java.util.Locale.ROOT, "drops: %.3f per heart (expected %.3f), max gap %d, 12 rewrites", rate, expected, maxGap));
    }

    private static void assets() {
        for (String id : AbyssSetup.legends()) {
            check(AbyssSelfTest.class.getResource("/assets/skyabyss/textures/item/" + id + ".png") != null, "no texture for " + id);
            check(AbyssSelfTest.class.getResource("/assets/skyabyss/models/item/" + id + ".json") != null, "no model for " + id);
        }
    }

    // ---------------------------------------------------------------- 書き換えの効き (時間を使う。複数 tick)
    private static final class State {
        ServerLevel level;
        long t0;
        FakePlayer sword;
        Husk cleaveFoe;
        float cleaveAfterHit;
        FakePlayer singer;
        long songUntil;
        float singerHealthBefore;
        FakePlayer archer;
        Husk fallFoe;
        float fallBefore;
        FakePlayer reaper;
        Husk ashNear;
        Husk ashFar;
        boolean farAshed;
    }

    private static State state;
    private static int phase;
    private static java.util.function.Consumer<net.neoforged.neoforge.event.tick.ServerTickEvent.Post> poll;

    private static Husk husk(ServerLevel level, double x, double z) {
        Husk h = EntityType.HUSK.create(level);
        h.moveTo(x, FLOOR + 1, z, 0f, 0f);
        h.setNoAi(true);
        h.setPersistenceRequired();
        h.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(400);
        h.setHealth(400);
        level.addFreshEntity(h);
        return h;
    }

    private static FakePlayer wielder(ServerLevel level, String name, String weapon, double x, double z) {
        FakePlayer p = fake(level, name, 3);
        p.moveTo(x, FLOOR + 1, z, 0f, 0f);
        Growth.get(p).level = 40;
        SkillData d = SkillManager.get(p);
        d.bonusPoints = 80;
        d.calling = "";
        d.mana = 200;
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, weapon))));
        level.addFreshEntity(p);
        return p;
    }

    private static boolean rewrites(MinecraftServer server) {
        ServerLevel level = server.overworld();
        try {
            if (state == null) {
                state = new State();
                state.level = level;
                phase = 0;
            }
            State s = state;
            long now = level.getGameTime();
            if (phase == 0) {
                SkillEffects.clear();
                // 巨神斬り: 奈落断ち。選べる (大剣を持つとき)・外せば効かない・振って 1.2 秒後にもう一度
                s.sword = wielder(level, "sword", AbyssSetup.GREATSWORD, CX, CZ);
                SkillManager.forceRank(s.sword, Skill.TITAN_CLEAVE, 3);
                check(SkillManager.chooseRune(s.sword, Skill.TITAN_CLEAVE, 4) == SkillManager.RuneResult.OK && SkillManager.runeOf(s.sword, Skill.TITAN_CLEAVE) == 4, "abyss cleave rune could not be chosen with the greatsword");
                s.cleaveFoe = husk(level, CX, CZ + 2.2);
                s.sword.setYRot(0f);
                s.sword.getLookAngle();
                SkillData d = SkillManager.get(s.sword);
                d.slots[0] = Skill.TITAN_CLEAVE.id;
                d.cooldowns.clear();
                d.globalCooldown = 0;
                float h0 = s.cleaveFoe.getHealth();
                check(SkillManager.cast(s.sword, 0) == SkillManager.CastResult.OK, "the abyss cleave could not be cast");
                s.cleaveAfterHit = s.cleaveFoe.getHealth();
                check(s.cleaveAfterHit < h0, "the abyss cleave did not hit at once");
                s.t0 = now;
                // 沈む唄: 唄の終わりに残った吸収が体力へ
                s.singer = wielder(level, "singer", AbyssSetup.BELL_STAFF, CX + 8, CZ);
                SkillManager.forceRank(s.singer, Skill.WARD_SONG, 3);
                check(SkillManager.chooseRune(s.singer, Skill.WARD_SONG, 4) == SkillManager.RuneResult.OK, "sunk song rune could not be chosen with the staff");
                s.singer.setHealth(8f);
                s.singerHealthBefore = 8f;
                SkillData sd = SkillManager.get(s.singer);
                sd.slots[0] = Skill.WARD_SONG.id;
                sd.cooldowns.clear();
                sd.globalCooldown = 0;
                check(SkillManager.cast(s.singer, 0) == SkillManager.CastResult.OK, "the sunk song could not be cast");
                s.songUntil = now + jp.revati.skyabyss.rpg.skill.SkillRules.WARD_SONG_TICKS + 20;
                // 天落とし: 矢の止まった所へ雷
                s.archer = wielder(level, "archer", AbyssSetup.STORM_BOW, CX - 8, CZ);
                SkillManager.forceRank(s.archer, Skill.CLOUD_PIERCE, 3);
                check(SkillManager.chooseRune(s.archer, Skill.CLOUD_PIERCE, 4) == SkillManager.RuneResult.OK, "sky fall rune could not be chosen with the bow");
                s.fallFoe = husk(level, CX - 8, CZ + 8);
                Arrow arrow = new Arrow(level, s.archer, new ItemStack(net.minecraft.world.item.Items.ARROW), s.archer.getMainHandItem());
                arrow.moveTo(CX - 8, FLOOR + 1, CZ + 8);
                arrow.addTag(SkillActions.SKY_FALL_TAG);
                level.addFreshEntity(arrow);
                s.fallBefore = s.fallFoe.getHealth();
                SkillActions.abyssSkyFall(arrow);
                check(!arrow.getTags().contains(SkillActions.SKY_FALL_TAG), "sky fall did not consume its tag (it would fall twice)");
                arrow.discard();
                // 這う灰: 灰かぶりが隣の敵へ移る
                s.reaper = wielder(level, "reaper", AbyssSetup.ASH_SCYTHE, CX, CZ - 8);
                SkillManager.forceRank(s.reaper, Skill.ASH_SCATTER, 2);
                check(SkillManager.chooseRune(s.reaper, Skill.ASH_SCATTER, 4) == SkillManager.RuneResult.OK, "creeping ash rune could not be chosen with the scythe");
                s.ashNear = husk(level, CX + 3, CZ - 8);
                s.ashFar = husk(level, CX + 3 + Abyss.ASH_RADIUS - 0.5, CZ - 8);
                SkillData rd = SkillManager.get(s.reaper);
                rd.slots[0] = Skill.ASH_SCATTER.id;
                rd.cooldowns.clear();
                rd.globalCooldown = 0;
                check(SkillManager.cast(s.reaper, 0) == SkillManager.CastResult.OK, "creeping ash could not be cast");
                check(SkillEffects.ashed(s.ashNear) && !SkillEffects.ashed(s.ashFar), "ash scatter reached the wrong foes at the start (near " + SkillEffects.ashed(s.ashNear) + ", far " + SkillEffects.ashed(s.ashFar) + ")");
                // 灰かぶりは数秒で切れる。自己テストの段は tick ごとには呼ばれないので、毎 tick 自分で見て、一度でも移ったかを覚える
                poll = e -> {
                    if (state != null && state.ashFar != null && SkillEffects.ashed(state.ashFar)) {
                        state.farAshed = true;
                    }
                };
                net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(poll);
                phase = 1;
                return false;
            }
            // 奈落断ち: 1.2 秒後の二撃目。天落とし
            if (phase == 1 && now - s.t0 >= Abyss.ECHO_DELAY + 6) {
                check(s.cleaveFoe.getHealth() < s.cleaveAfterHit - 0.5f, "the abyss cleave echo did not hit (" + s.cleaveAfterHit + " -> " + s.cleaveFoe.getHealth() + ")");
                check(s.fallFoe.getHealth() < s.fallBefore - 0.5f, "sky fall did not hit the foe where the arrow stopped (" + s.fallBefore + " -> " + s.fallFoe.getHealth() + ")");
                phase = 2;
            }
            if (phase == 2 && now - s.t0 >= Abyss.ASH_EVERY * 3 + 10) {
                phase = 3;
            }
            // 沈む唄: 唄の終わり
            if (phase == 3 && now >= s.songUntil) {
                check(s.farAshed, "creeping ash did not spread to the far foe");
                check(s.singer.getHealth() > s.singerHealthBefore + 0.5f, "the sunk song returned no health (" + s.singerHealthBefore + " -> " + s.singer.getHealth() + ")");
                finish(server);
                return true;
            }
            return false;
        } catch (RuntimeException e) {
            SkyAbyss.LOGGER.error("SELFTEST abyss rewrites crashed", e);
            F.add("rewrites crashed: " + e);
            finish(server);
            return true;
        }
    }

    private static void finish(MinecraftServer server) {
        State s = state;
        if (poll != null) {
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(poll);
            poll = null;
        }
        if (s != null) {
            for (LivingEntity e : new LivingEntity[] {s.cleaveFoe, s.fallFoe, s.ashNear, s.ashFar}) {
                if (e != null) {
                    e.discard();
                }
            }
            for (FakePlayer p : new FakePlayer[] {s.sword, s.singer, s.archer, s.reaper}) {
                if (p != null && !p.isRemoved()) {
                    p.discard();
                }
            }
            SkillEffects.clear();
            SkillManager.forgetAll(s.sword);
        }
        state = null;
        NOTES.add("rewrites: echo / sunk / sky fall / creeping ash all fire");
        NOTES.forEach(n -> SkyAbyss.LOGGER.info("SELFTEST abyss note: {}", n));
        if (F.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST abyss PASSED ({})", String.join("; ", NOTES));
        } else {
            F.stream().limit(30).forEach(x -> SkyAbyss.LOGGER.error("SELFTEST abyss FAILED: {}", x));
        }
    }
}
