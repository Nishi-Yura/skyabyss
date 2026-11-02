package jp.revati.skyabyss.rebirth;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import io.netty.buffer.Unpooled;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.item.GearLevel;
import jp.revati.skyabyss.item.ModGear;
import jp.revati.skyabyss.party.PartyData;
import jp.revati.skyabyss.pouch.PouchLayout;
import jp.revati.skyabyss.pouch.PouchMenu;
import jp.revati.skyabyss.progress.Progression;
import jp.revati.skyabyss.registry.ModItems;
import jp.revati.skyabyss.rpg.Growth;
import jp.revati.skyabyss.rpg.GrowthData;
import jp.revati.skyabyss.rpg.GrowthRules;
import jp.revati.skyabyss.rpg.Rpg;
import jp.revati.skyabyss.rpg.skill.Skill;
import jp.revati.skyabyss.rpg.skill.SkillData;
import jp.revati.skyabyss.rpg.skill.SkillManager;
import jp.revati.skyabyss.rpg.skill.SkillTree;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

// 転生の自己テスト (SKYABYSS_SELFTEST_ONLY="soul")。docs/design/31
//  - 数の計算: 経験値 −6%・下限 −40%、逓減 (10 まで線形・半分・1/4)、祖の力、10 回で全ダメージ約 +8%、色と記号、余白・絆の段、共闘の倍率、恩恵の候補
//  - 転生の流れ: 戻るもの (レベル・技・点・天職) と残るもの (型の保存・解いた木・追加の点・装備の使える資格・物語の進み)、代価の結晶 5、
//    戦いの最中・Lv 50 未満の拒否、20 回の上限、恩恵の 3 択 (候補にないものは断る)
//  - 保存: プレイヤーの保存と読み込み (ログアウト・ログイン)・死亡時の引き継ぎ・同期の符号化
//  - ポーチの余白 (2・6・12 回で枠が増え、預ける・出す・倒れても残る)、導きの欠片 (1 日 3 回・同じ人は 1 日 1 回・節 6 まで)、共闘の合わせ (傷だけ)
public final class SoulSelfTest {
    private SoulSelfTest() {
    }

    public static void runSelfTest(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        try {
            rules(f);
            flow(server, f);
            persistence(server, f);
            spare(server, f);
            guide(server, f);
            sync(server, f);
        } catch (RuntimeException e) {
            SkyAbyss.LOGGER.error("SELFTEST soul crashed", e);
            f.add("crashed: " + e);
        } finally {
            RebirthEvents.testLookup = null;
            Rebirth.today = () -> System.currentTimeMillis() / 86_400_000L;
        }
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST soul PASSED (rules, reset/keep scope, story unlocked, cap 20, cost, boon choice, pouch spare, persistence, guide shards, party sync)");
        } else {
            f.forEach(m -> SkyAbyss.LOGGER.error("SELFTEST soul FAILED: {}", m));
        }
    }

    private static void check(List<String> f, boolean ok, String message) {
        if (!ok) {
            f.add(message);
        }
    }

    private static boolean near(double a, double b) {
        return Math.abs(a - b) < 1e-6;
    }

    private static FakePlayer player(ServerLevel level, String name) {
        FakePlayer p = new FakePlayer(level, new GameProfile(UUID.randomUUID(), name));
        p.setGameMode(GameType.SURVIVAL);
        p.setPos(0.5, 100, 0.5);
        return p;
    }

    private static List<String> repeat(String id, int n) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            out.add(id);
        }
        return out;
    }

    // ---------------------------------------------------------------- 数
    private static void rules(List<String> f) {
        check(f, RebirthRules.MAX_REBIRTHS == 20 && RebirthRules.COST_CRYSTALS == 5 && RebirthRules.MIN_LEVEL == 50, "decisions changed (cap 20, cost 5, level 50)");
        // 経験値: 転生ごとに −6%、下限 −40%
        check(f, near(RebirthRules.xpScale(0), 1.0) && near(RebirthRules.xpScale(1), 0.94) && near(RebirthRules.xpScale(3), 0.82), "xp scale 0/1/3 is wrong");
        check(f, near(RebirthRules.xpScale(7), 0.6) && near(RebirthRules.xpScale(20), 0.6), "xp scale floor (-40%) is wrong");
        check(f, GrowthRules.xpToNext(1, 0) == 50 && GrowthRules.xpToNext(1, 3) == 41 && GrowthRules.xpToNext(1, 10) == 30, "xpToNext with rebirths is wrong");
        check(f, GrowthRules.xpToNext(50, 5) == 0, "max level still needs xp after a rebirth");
        GrowthData gd = new GrowthData();
        gd.rebirths = 3;
        check(f, gd.xpNeed() == 41 && Growth.addXpRaw(gd, 41) == 1 && gd.level == 2, "the cheaper level 1 -> 2 was not used");
        // 逓減 (10 まで線形、そのあと半分、20 を超えたら 1/4)
        check(f, near(RebirthRules.scale(1), 1) && near(RebirthRules.scale(10), 1) && near(RebirthRules.scale(11), 0.5)
                && near(RebirthRules.scale(20), 0.5) && near(RebirthRules.scale(21), 0.25), "diminishing curve is wrong");
        check(f, RebirthRules.isKey(10) && RebirthRules.isKey(20) && !RebirthRules.isKey(9) && !RebirthRules.isKey(0), "keystone nodes are not at 10/20");
        // 祖の力
        check(f, near(RebirthRules.ancestor(4), 0) && near(RebirthRules.ancestor(5), 0.01) && near(RebirthRules.ancestor(10), 0.02)
                && near(RebirthRules.ancestor(20), 0.04) && near(RebirthRules.ancestor(99), 0.04), "ancestral power is wrong");
        // 10 回で全ダメージ約 +8% (灰の腕に寄せたとき。祖の力 2% + 節 6%)、20 回で約 +13%
        RebirthRules.Effects ten = RebirthRules.effects(repeat("ash", 10), List.of(), 10);
        check(f, near(ten.damage(), 0.02 + 0.005 * 9 + 0.015), "10 ash nodes should give +8% damage: " + ten.damage());
        RebirthRules.Effects twenty = RebirthRules.effects(repeat("ash", 20), List.of(), 20);
        check(f, near(twenty.damage(), 0.04 + 0.06 + 0.005 * 0.5 * 9 + 0.005 * 0.5 * 3), "20 ash nodes (diminished) are wrong: " + twenty.damage());
        check(f, twenty.damage() - ten.damage() < ten.damage(), "the second ten rebirths gave more power than the first ten");
        check(f, RebirthRules.effects(repeat("ash", 200), repeat("ashen", 200), 20).damage() <= RebirthRules.DAMAGE_CAP + 1e-9, "damage is not capped");
        // 腕ごとの効き目
        RebirthRules.Effects mixed = RebirthRules.effects(List.of("rock", "wind", "tide", "bone", "journey"), List.of(), 5);
        check(f, near(mixed.reduction(), 0.004) && near(mixed.cooldown(), 0.006) && near(mixed.maxMana(), 2) && near(mixed.projectile(), 0.008)
                && near(mixed.pickup(), 0.4) && near(mixed.crystal(), 0.02) && near(mixed.maxHealth(), 0.5), "arm effects are wrong");
        // 色と記号 (白 → 青緑 → 金 → プリズム)
        check(f, RebirthRules.tier(0) == 0 && RebirthRules.tier(1) == 1 && RebirthRules.tier(4) == 1 && RebirthRules.tier(5) == 2
                && RebirthRules.tier(14) == 2 && RebirthRules.tier(15) == 3 && RebirthRules.tier(20) == 3, "colour tiers are wrong");
        check(f, RebirthRules.tierColor(0) == RebirthRules.COLOR_WHITE && RebirthRules.tierColor(2) == RebirthRules.COLOR_TEAL
                && RebirthRules.tierColor(5) == RebirthRules.COLOR_GOLD, "tier colours are wrong");
        check(f, RebirthRules.tierColor(16, 0) != RebirthRules.tierColor(16, 3000), "the prism colour does not cycle");
        check(f, RebirthRules.symbol(0).isEmpty() && RebirthRules.symbol(3).length() == 3 && RebirthRules.symbol(5).equals("✧")
                && RebirthRules.symbol(10).equals("✧✧") && RebirthRules.symbol(20).equals("✺"), "symbols are wrong");
        check(f, RebirthRules.levelLabel(50, 0).equals("Lv 50") && RebirthRules.levelLabel(50, 3).equals("Lv 50+3")
                && RebirthRules.levelLabel(12, 2).startsWith("Lv 12 "), "level labels are wrong");
        // ポーチの余白 (2・6・12)、運び手との絆 (3・8・15)
        check(f, RebirthRules.spareSlots(1) == 0 && RebirthRules.spareSlots(2) == 1 && RebirthRules.spareSlots(5) == 1
                && RebirthRules.spareSlots(6) == 2 && RebirthRules.spareSlots(12) == 3 && RebirthRules.spareSlots(20) == 3, "spare slots are wrong");
        check(f, RebirthRules.bondTier(2) == 0 && RebirthRules.bondTier(3) == 1 && RebirthRules.bondTier(8) == 2 && RebirthRules.bondTier(15) == 3, "bond tiers are wrong");
        check(f, near(RebirthRules.bondSpeed(2), 1.0) && RebirthRules.bondSpeed(3) > 1.0 && RebirthRules.bondSpeed(15) > RebirthRules.bondSpeed(8)
                && RebirthRules.bondRange(15) > RebirthRules.bondRange(8) && near(RebirthRules.bondRange(2), 0), "bond perks do not grow");
        // 共闘のレベル合わせ: 差が小さければそのまま、広がるほど下がり、下限 0.6
        check(f, near(RebirthRules.syncMultiplier(50, 50), 1) && near(RebirthRules.syncMultiplier(50, 47), 1) && RebirthRules.syncMultiplier(50, 30) < 1
                && near(RebirthRules.syncMultiplier(50, 1), 0.6), "party sync multiplier is wrong");
        // 恩恵の候補: 3 枚とも違う・同じ人の同じ回なら同じ・回が変わると変わる・数回のうちに同じ候補が再び出る
        List<Boon> a = RebirthRules.offer(777L, 4);
        check(f, a.size() == 3 && a.stream().distinct().count() == 3, "boon offer is not three distinct cards");
        check(f, a.equals(RebirthRules.offer(777L, 4)), "the boon offer changed on reload");
        boolean differs = false;
        java.util.Set<Boon> seen = new java.util.HashSet<>();
        for (int n = 0; n < 6; n++) {
            seen.addAll(RebirthRules.offer(777L, n));
            differs |= !RebirthRules.offer(777L, n).equals(a);
        }
        check(f, differs, "the boon offer never changes between rebirths");
        check(f, seen.size() < 6 * 3, "boon offers never repeat across 6 rebirths");
        // 腕・恩恵の名前の鍵がそろっているか
        for (SoulArm arm : SoulArm.values()) {
            check(f, SoulArm.byId(arm.id) == arm && arm.nameKey().endsWith(arm.id), "arm lookup is wrong: " + arm);
        }
        for (Boon b : Boon.values()) {
            check(f, Boon.byId(b.id) == b, "boon lookup is wrong: " + b);
        }
        // 余白は説明の線を越えない
        var layout = PouchLayout.compute(jp.revati.skyabyss.pouch.PouchEntries.enabled());
        check(f, layout.bottom() <= PouchLayout.SEPARATOR_Y - 2, "the spare row runs into the info area: bottom " + layout.bottom() + " spare " + java.util.Arrays.toString(layout.spare().get(0)) + " last spot "
                + layout.spots().get(layout.spots().size() - 1).x() + "," + layout.spots().get(layout.spots().size() - 1).y() + " labels " + layout.labels().size());
        check(f, layout.spare().size() == 3 && layout.spare().get(2)[0] + 18 <= PouchLayout.W - PouchLayout.X0 + 2, "the spare slots leave the panel");
        for (var sp : layout.spare()) {
            for (var spot : layout.spots()) {
                check(f, Math.abs(spot.x() - sp[0]) >= 18 || Math.abs(spot.y() - sp[1]) >= 18, "a spare slot overlaps a pouch slot at " + sp[0] + "," + sp[1]);
            }
        }
        check(f, RebirthEvents.isWhalePedestal(new BlockPos(0, 104, -35)) && !RebirthEvents.isWhalePedestal(new BlockPos(1, 104, -35)), "the altar block is wrong");
    }

    // ---------------------------------------------------------------- 転生の流れ
    private static void flow(MinecraftServer server, List<String> f) {
        ServerLevel level = server.overworld();
        FakePlayer p = player(level, "rebirth_a");
        GrowthData g = Growth.get(p);
        SkillData sd = SkillManager.get(p);
        SoulData rd = Rebirth.data(p);

        // 準備: Lv 50・技と点・天職・型の保存・解いた木・追加の点・発見の記録
        g.level = 50;
        g.stats[0] = 20;
        g.stats[3] = 10;
        g.bonusPoints = 4;
        g.statRespecs = 2;
        g.discoveries.add("biome:test");
        SkillManager.forceRank(p, Skill.TITAN_CLEAVE, 2);
        SkillManager.forceRank(p, Skill.LUNGE, 1);
        sd.respecs = 3;
        sd.loadouts[0].saved = true;
        sd.loadouts[0].slots[0] = Skill.LUNGE.id;
        sd.unlockedTrees.add("star");
        int skillBonus = sd.bonusPoints;
        int stageBefore = Progression.stage(p);
        int capBefore = Progression.levelCap(p);
        ItemStack gear = highLevelGear();
        check(f, gear.isEmpty() || GearLevel.meets(p, gear), "level 50 cannot use the highest-level gear before rebirth");

        // 拒否: 結晶が足りない・戦いの最中・Lv 50 未満・選びが合わない・恩恵が候補にない
        List<Boon> offer = Rebirth.offer(p);
        Boon pick = offer.get(1);
        check(f, Rebirth.perform(p, "ash", pick.id, false) == Rebirth.Result.NO_CRYSTALS && g.level == 50 && g.rebirths == 0, "a rebirth with no crystals went through");
        p.getInventory().add(new ItemStack(ModItems.RESONANCE_CRYSTAL.get(), 4));
        check(f, Rebirth.perform(p, "ash", pick.id, false) == Rebirth.Result.NO_CRYSTALS, "4 crystals were enough for a rebirth that costs 5");
        p.getInventory().add(new ItemStack(ModItems.RESONANCE_CRYSTAL.get(), 3));
        sd.lastCombat = level.getGameTime();
        check(f, Rebirth.perform(p, "ash", pick.id, false) == Rebirth.Result.IN_COMBAT && g.level == 50, "a rebirth during combat went through");
        sd.lastCombat = Long.MIN_VALUE / 2;
        g.level = 49;
        check(f, Rebirth.perform(p, "ash", pick.id, false) == Rebirth.Result.NOT_MAX_LEVEL, "a rebirth below level 50 went through");
        g.level = 50;
        check(f, Rebirth.perform(p, "nope", pick.id, false) == Rebirth.Result.BAD_ARM, "an unknown arm was accepted");
        Boon outside = null;
        for (Boon b : Boon.values()) {
            if (!offer.contains(b)) {
                outside = b;
                break;
            }
        }
        check(f, outside != null && Rebirth.perform(p, "ash", outside.id, false) == Rebirth.Result.BAD_BOON && g.rebirths == 0, "a boon outside the offer was accepted");
        check(f, Rebirth.perform(p, "ash", pick.id, true) == Rebirth.Result.NOT_AT_ALTAR && g.rebirths == 0, "the altar was not required away from the hub");
        check(f, SkillManager.countCrystals(p) == 7 && rd.nodes.isEmpty() && rd.boons.isEmpty(), "refused rebirths changed state or took crystals");

        // 転生する (結晶 5 を払う)
        check(f, Rebirth.perform(p, "ash", pick.id, false) == Rebirth.Result.OK, "the rebirth did not go through");
        check(f, g.rebirths == 1 && g.level == 1 && g.xp == 0, "level did not reset: " + g.level + " rebirths " + g.rebirths);
        check(f, g.spentPoints() == 0 && g.statRespecs == 0, "stat points did not reset");
        check(f, sd.ranks.isEmpty() && sd.runes.isEmpty() && sd.spentPoints() == 0 && sd.respecs == 0, "skills did not reset");
        check(f, SkillManager.countCrystals(p) == 2, "the rebirth did not cost exactly 5 crystals: " + SkillManager.countCrystals(p));
        check(f, rd.nodes.equals(List.of("ash")) && rd.boons.equals(List.of(pick.id)), "the chosen arm/boon were not recorded: " + rd.nodes + rd.boons);
        // 残るもの
        check(f, sd.loadouts[0].saved && Skill.LUNGE.id.equals(sd.loadouts[0].slots[0]), "saved loadouts did not stay");
        check(f, sd.unlockedTrees.contains("star") && sd.bonusPoints == 0 && g.bonusPoints == 4, "unlocked trees / bonus points did not stay");
        check(f, g.discoveries.contains("biome:test"), "discoveries did not stay");
        // 物語・できること: 巨神の進み・レベルの上限・装備の資格は変わらない
        check(f, Progression.stage(p) == stageBefore && Progression.levelCap(p) == capBefore, "the story progress or level cap changed");
        check(f, Rebirth.gearLevel(p) == 50 && (gear.isEmpty() || GearLevel.meets(p, gear)), "gear became unusable after a rebirth (level " + g.level + ")");
        check(f, GearLevel.level(p) == 50, "the gear requirement level is not 50 after a rebirth");
        // 必要経験値が減る (転生 1 回 −6%)、最大マナなどの修飾子 (潮ではない灰の腕は修飾子なし)
        check(f, g.xpNeed() == 47, "xp need after one rebirth is " + g.xpNeed());
        // 灰の腕 1 つ + 祖の力 0 → 全ダメージ +0.5% 以上 (恩恵が攻めなら少し多い)
        check(f, Rebirth.effects(p).damage() >= 0.005 - 1e-9, "the ash node did not add damage");

        // 恩恵の 3 択は転生の回が進むと別の候補になる (同じ回なら同じ)
        List<Boon> next = Rebirth.offer(p);
        check(f, next.size() == 3 && next.equals(Rebirth.offer(p)), "the next offer is not stable");

        // 20 回の上限: 20 回まで転生でき、21 回目は断られて何も変わらない
        for (int i = 1; i < RebirthRules.MAX_REBIRTHS; i++) {
            g.level = 50;
            p.getInventory().add(new ItemStack(ModItems.RESONANCE_CRYSTAL.get(), 5));
            Boon b = Rebirth.offer(p).get(i % 3);
            SoulArm arm = SoulArm.values()[i % 6];
            check(f, Rebirth.perform(p, arm.id, b.id, false) == Rebirth.Result.OK, "rebirth " + (i + 1) + " did not go through");
        }
        check(f, g.rebirths == 20 && rd.nodes.size() == 20 && rd.boons.size() == 20, "after 20 rebirths: " + g.rebirths + "/" + rd.nodes.size() + "/" + rd.boons.size());
        g.level = 50;
        p.getInventory().add(new ItemStack(ModItems.RESONANCE_CRYSTAL.get(), 5));
        int crystals = SkillManager.countCrystals(p);
        check(f, Rebirth.perform(p, "ash", Boon.STRIDE.id, false) == Rebirth.Result.AT_CAP && g.rebirths == 20 && g.level == 50
                && SkillManager.countCrystals(p) == crystals, "the 21st rebirth was not refused cleanly");
        check(f, g.xpNeed() == 0 || g.level < 50, "max level still needs xp");
        g.level = 1;
        check(f, g.xpNeed() == 30, "xp floor at 20 rebirths is " + g.xpNeed());
        g.rebirths = 25;
        g.sanitize();
        check(f, g.rebirths == 20, "a hand-edited rebirth count was not clamped");

        // 修飾子: 20 回ぶんの力がマナ・体力・足の速さの修飾子として付き、付け直しても重ならない
        g.rebirths = 20;
        Growth.applyModifiers(p);
        double mana1 = p.getAttributeValue(Rpg.MAX_MANA);
        Growth.applyModifiers(p);
        check(f, near(mana1, p.getAttributeValue(Rpg.MAX_MANA)) && mana1 >= GrowthRules.BASE_MANA, "rebirth modifiers stack or vanish");
        check(f, p.getAttribute(Attributes.MAX_HEALTH).getModifier(Rebirth.MOD_HEALTH) != null || Rebirth.effects(p).maxHealth() == 0, "health modifier missing");
        check(f, Math.abs(Rebirth.scaleCooldown(p, 1000) / 1000.0 - (1 - Rebirth.effects(p).cooldown())) < 1e-3 || Rebirth.effects(p).cooldown() == 0, "cooldown scaling is wrong");
        // 学びの手: 経験値の補正
        SoulData xpData = Rebirth.data(p);
        xpData.boons.clear();
        xpData.boons.add(Boon.SCHOLAR.id);
        check(f, Rebirth.scaleXp(p, 100) == 103, "scholar boon xp is " + Rebirth.scaleXp(p, 100));
        xpData.boons.clear();
        check(f, Rebirth.scaleXp(p, 100) == 100, "no boon should not change xp");
        // 与える傷: 祖の力・節の分が掛かる
        Rebirth.data(p).nodes.clear();
        Rebirth.data(p).nodes.addAll(repeat("ash", 20));
        g.rebirths = 20;
        LivingEntity zombie = EntityType.ZOMBIE.create(level);
        zombie.setPos(1.5, 100, 1.5);
        LivingIncomingDamageEvent ev = new LivingIncomingDamageEvent(zombie, new DamageContainer(level.damageSources().playerAttack(p), 10f));
        RebirthEvents.onIncoming(ev);
        double expect = 10 * (1 + Rebirth.effects(p).damage());
        check(f, Math.abs(ev.getAmount() - expect) < 1e-3 && ev.getAmount() > 10f, "outgoing damage is not boosted: " + ev.getAmount() + " expected " + expect);
        // 転生していない人の傷は変わらない
        FakePlayer plain = player(level, "rebirth_plain");
        LivingIncomingDamageEvent ev2 = new LivingIncomingDamageEvent(zombie, new DamageContainer(level.damageSources().playerAttack(plain), 10f));
        RebirthEvents.onIncoming(ev2);
        check(f, Math.abs(ev2.getAmount() - 10f) < 1e-6, "an unreborn player's damage changed: " + ev2.getAmount());
        // 受ける傷: 岩の腕・守りの膜
        Rebirth.data(p).nodes.clear();
        Rebirth.data(p).nodes.addAll(repeat("rock", 5));
        LivingIncomingDamageEvent ev3 = new LivingIncomingDamageEvent(p, new DamageContainer(level.damageSources().mobAttack(zombie), 10f));
        RebirthEvents.onIncoming(ev3);
        check(f, Math.abs(ev3.getAmount() - 10f * (1 - Rebirth.effects(p).reduction())) < 1e-3 && ev3.getAmount() < 10f, "damage taken is not reduced: " + ev3.getAmount());

        // 共闘のレベル合わせ: 低レベルの仲間が近くにいるときだけ、転生者の傷の倍率が下がる (仲間の傷・転生していない人は変わらない)
        partySync(server, f, level, p, plain);
        zombie.discard();
    }

    private static ItemStack highLevelGear() {
        Item best = null;
        int bestLevel = 0;
        for (Item item : BuiltInRegistries.ITEM) {
            if (item instanceof ModGear) {
                int r = GearLevel.required(item);
                if (r > bestLevel && r <= 50) {
                    bestLevel = r;
                    best = item;
                }
            }
        }
        return best == null ? ItemStack.EMPTY : new ItemStack(best);
    }

    private static void partySync(MinecraftServer server, List<String> f, ServerLevel level, FakePlayer reborn, FakePlayer plain) {
        FakePlayer low = player(level, "rebirth_low");
        Map<UUID, FakePlayer> by = new HashMap<>();
        by.put(reborn.getUUID(), reborn);
        by.put(low.getUUID(), low);
        by.put(plain.getUUID(), plain);
        RebirthEvents.testLookup = by::get;
        PartyData data = PartyData.get(server);
        PartyData.Party party = data.create(reborn.getUUID(), "rebirth_a");
        data.add(party, low.getUUID(), "rebirth_low");
        try {
            GrowthData gr = Growth.get(reborn);
            gr.rebirths = 3;
            gr.level = 50;
            Growth.get(low).level = 10;
            check(f, RebirthEvents.lowestAllyLevel(reborn) == 10, "the lowest ally level is " + RebirthEvents.lowestAllyLevel(reborn));
            double m = RebirthEvents.syncMultiplier(reborn);
            check(f, near(m, RebirthRules.syncMultiplier(50, 10)) && m < 1 && m >= RebirthRules.SYNC_FLOOR, "the sync multiplier is wrong: " + m);
            // 傷の倍率だけが下がる (体力・最大値は触らない)
            check(f, near(RebirthEvents.outgoingMultiplier(reborn, false), (1 + Rebirth.effects(reborn).damage()) * m), "outgoing multiplier ignores the sync");
            // 仲間が近いレベルなら変わらない
            Growth.get(low).level = 48;
            check(f, near(RebirthEvents.syncMultiplier(reborn), 1.0), "a close-level ally lowered the damage");
            // 仲間が遠いときは変わらない (64 ブロック以上)
            Growth.get(low).level = 5;
            low.setPos(200, 100, 200);
            check(f, near(RebirthEvents.syncMultiplier(reborn), 1.0), "a far ally lowered the damage");
            low.setPos(0.5, 100, 0.5);
            check(f, RebirthEvents.syncMultiplier(reborn) < 1.0, "a near low ally did not lower the damage");
            // 転生していない人には効かない・パーティにいない転生者にも効かない
            gr.rebirths = 0;
            check(f, near(RebirthEvents.syncMultiplier(reborn), 1.0), "the sync applied to an unreborn player");
            gr.rebirths = 3;
            data.remove(low.getUUID());
            check(f, near(RebirthEvents.syncMultiplier(reborn), 1.0), "the sync applied without a party");
        } finally {
            data.remove(low.getUUID());
            data.remove(reborn.getUUID());
            RebirthEvents.testLookup = null;
        }
    }

    // ---------------------------------------------------------------- 保存
    private static void persistence(MinecraftServer server, List<String> f) {
        ServerLevel level = server.overworld();
        FakePlayer p = player(level, "rebirth_save");
        GrowthData g = Growth.get(p);
        SoulData rd = Rebirth.data(p);
        g.rebirths = 6;
        g.level = 33;
        rd.nodes.addAll(List.of("ash", "rock", "tide", "wind", "bone", "journey", "ash"));
        rd.boons.addAll(List.of("stride", "scholar", "hardy", "calm", "haul", "ward"));
        rd.shards = 3;
        rd.shardNodes = 1;
        rd.guideDay = 42;
        rd.guideToday = 2;
        rd.guideMet.add("someone");
        rd.spareItem[0] = "minecraft:cobblestone";
        rd.spareCount[0] = 321;
        rd.spareItem[1] = "minecraft:dirt";
        rd.spareCount[1] = 7;

        // ログアウト・ログイン (プレイヤーの保存と読み込み)
        CompoundTag saved = new CompoundTag();
        p.saveWithoutId(saved);
        FakePlayer loaded = player(level, "rebirth_load");
        loaded.load(saved);
        check(f, sameData(rd, Rebirth.data(loaded)) && Growth.get(loaded).rebirths == 6 && Growth.get(loaded).level == 33, "rebirth data was not saved with the player");
        // 死んでも引き継ぐ
        FakePlayer reborn = player(level, "rebirth_clone");
        reborn.copyAttachmentsFrom(p, true);
        check(f, sameData(rd, Rebirth.data(reborn)) && Growth.get(reborn).rebirths == 6, "rebirth data was not copied on death");
        check(f, Rebirth.data(reborn).spareCount[0] == 321, "the spare slot contents were lost on death");
        // 同期の符号化 (持ち主のクライアントへ)
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), server.registryAccess());
        SoulData.STREAM_CODEC.encode(buf, rd);
        SoulData wire = SoulData.STREAM_CODEC.decode(buf);
        check(f, sameData(rd, wire), "the sync codec lost rebirth data");
        RegistryFriendlyByteBuf gb = new RegistryFriendlyByteBuf(Unpooled.buffer(), server.registryAccess());
        GrowthData.STREAM_CODEC.encode(gb, g);
        check(f, GrowthData.STREAM_CODEC.decode(gb).rebirths == 6, "the growth sync codec lost the rebirth count");
        // 古い保存 (rebirths なし) は 0 回として読む / 壊れた値は丸める
        GrowthData old = new GrowthData();
        CompoundTag oldTag = new CompoundTag();
        oldTag.putInt("level", 50);
        old.deserializeNBT(server.registryAccess(), oldTag);
        check(f, old.rebirths == 0 && old.level == 50, "an old save was not migrated to 0 rebirths");
        SoulData broken = new SoulData();
        broken.nodes.addAll(List.of("ash", "zzz", "rock", "ash"));
        broken.boons.addAll(List.of("stride", "zzz"));
        broken.spareItem[2] = "minecraft:stone";
        broken.spareCount[2] = 5000;
        broken.sanitize(2);
        check(f, broken.nodes.equals(List.of("ash", "rock")) && broken.boons.equals(List.of("stride")) && broken.spareCount[2] == SoulData.SPARE_LIMIT,
                "a broken record was not sanitized: " + broken.nodes + broken.boons + broken.spareCount[2]);
        // 回数がないのに節がある保存 (手で書き換え) は回数に合わせる
        SoulData extra = new SoulData();
        extra.nodes.addAll(repeat("ash", 5));
        extra.sanitize(2);
        check(f, extra.nodes.size() == 2, "nodes beyond the rebirth count were kept");
    }

    private static boolean sameData(SoulData a, SoulData b) {
        return a.nodes.equals(b.nodes) && a.boons.equals(b.boons) && a.shards == b.shards && a.shardNodes == b.shardNodes
                && java.util.Arrays.equals(a.spareItem, b.spareItem) && java.util.Arrays.equals(a.spareCount, b.spareCount);
    }

    // ---------------------------------------------------------------- ポーチの余白
    private static void spare(MinecraftServer server, List<String> f) {
        ServerLevel level = server.overworld();
        FakePlayer p = player(level, "rebirth_spare");
        GrowthData g = Growth.get(p);
        SoulData rd = Rebirth.data(p);
        g.rebirths = 1;
        check(f, RebirthSpare.slots(p) == 0 && RebirthSpare.deposit(p, 0, new ItemStack(Items.COBBLESTONE, 10), 10) == 0, "a spare slot opened before 2 rebirths");
        g.rebirths = 2;
        check(f, RebirthSpare.slots(p) == 1, "no spare slot at 2 rebirths");
        ItemStack cobble = new ItemStack(Items.COBBLESTONE, 64);
        check(f, RebirthSpare.deposit(p, 0, cobble, 64) == 64 && cobble.isEmpty() && rd.spareCount[0] == 64, "the spare slot did not take cobblestone");
        check(f, RebirthSpare.deposit(p, 1, new ItemStack(Items.DIRT, 5), 5) == 0, "a locked spare slot took an item");
        check(f, RebirthSpare.deposit(p, 0, new ItemStack(Items.DIRT, 5), 5) == 0, "a slot holding cobblestone took dirt");
        check(f, RebirthSpare.deposit(p, 0, new ItemStack(Items.IRON_PICKAXE), 1) == 0, "a tool went into a spare slot");
        ItemStack named = new ItemStack(Items.COBBLESTONE, 3);
        named.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal("x"));
        check(f, RebirthSpare.deposit(p, 0, named, 3) == 0, "a renamed stack went into a spare slot");
        check(f, RebirthSpare.deposit(p, 0, new ItemStack(ModItems.RESONANCE_CRYSTAL.get(), 2), 2) == 0, "a pouch item took a spare slot");
        // 取り出す: 1 スタックまで
        ItemStack out = RebirthSpare.take(p, 0, 200);
        check(f, out.getCount() == 64 && out.is(Items.COBBLESTONE) && rd.spareCount[0] == 0 && rd.spareItem[0].isEmpty(), "taking from the spare slot is wrong");
        // 上限 999
        rd.spareItem[0] = "minecraft:cobblestone";
        rd.spareCount[0] = 990;
        ItemStack more = new ItemStack(Items.COBBLESTONE, 64);
        check(f, RebirthSpare.deposit(p, 0, more, 64) == 9 && more.getCount() == 55 && rd.spareCount[0] == 999, "the spare limit 999 is wrong");
        // 6 回で 2 つ目: 同じ品は 2 枠に割れない
        g.rebirths = 6;
        check(f, RebirthSpare.slots(p) == 2 && RebirthSpare.deposit(p, 1, new ItemStack(Items.COBBLESTONE, 1), 1) == 0, "the same item went into two spare slots");
        check(f, RebirthSpare.deposit(p, 1, new ItemStack(Items.DIRT, 4), 4) == 4, "the second spare slot did not take dirt");
        g.rebirths = 12;
        check(f, RebirthSpare.slots(p) == 3, "no third spare slot at 12 rebirths");
        g.rebirths = 20;
        check(f, RebirthSpare.slots(p) == 3, "more than 3 spare slots at 20 rebirths");

        // 画面のメニュー: 枠が増え、預ける・出すが効く
        g.rebirths = 2;
        rd.spareItem[0] = "";
        rd.spareCount[0] = 0;
        rd.spareItem[1] = "";
        rd.spareCount[1] = 0;
        PouchMenu menu = new PouchMenu(55, p.getInventory());
        int base = menu.pouchSlotCount();
        check(f, menu.spareCount() == 1 && menu.slots.size() == base + 1, "the pouch menu has the wrong slot count: " + menu.slots.size() + "/" + base);
        menu.setCarried(new ItemStack(Items.COBBLESTONE, 10));
        menu.clicked(base, 0, ClickType.PICKUP, p);
        check(f, menu.getCarried().isEmpty() && rd.spareCount[0] == 10, "the menu did not deposit into the spare slot");
        check(f, menu.getSlot(base).getItem().getCount() == 10, "the spare slot is not shown in the menu");
        menu.clicked(base, 0, ClickType.PICKUP, p);
        check(f, menu.getCarried().getCount() == 10 && rd.spareCount[0] == 0, "the menu did not take from the spare slot");
        menu.setCarried(ItemStack.EMPTY);
    }

    // ---------------------------------------------------------------- 導きの欠片
    private static void guide(MinecraftServer server, List<String> f) {
        ServerLevel level = server.overworld();
        FakePlayer guide = player(level, "rebirth_guide");
        Growth.get(guide).rebirths = 2;
        SoulData rd = Rebirth.data(guide);
        FakePlayer n1 = player(level, "rebirth_n1");
        FakePlayer n2 = player(level, "rebirth_n2");
        FakePlayer n3 = player(level, "rebirth_n3");
        FakePlayer n4 = player(level, "rebirth_n4");
        Rebirth.today = () -> 100L;
        check(f, Rebirth.giveGuideShard(guide, List.of(n1)) && rd.shards == 1, "the first guide shard was not given");
        check(f, !Rebirth.giveGuideShard(guide, List.of(n1)) && rd.shards == 1, "the same person gave a second shard in one day");
        check(f, Rebirth.giveGuideShard(guide, List.of(n1, n2)) && rd.shards == 2, "a second person did not give a shard");
        check(f, Rebirth.giveGuideShard(guide, List.of(n3)) && rd.shards == 3, "the third shard of the day was not given");
        check(f, !Rebirth.giveGuideShard(guide, List.of(n4)) && rd.shards == 3, "more than 3 shards in one day");
        Rebirth.today = () -> 101L;
        check(f, Rebirth.giveGuideShard(guide, List.of(n1)) && rd.shards == 4, "the next day did not give a shard again");
        // 試練を越えた: 転生者に欠片、未転生の仲間に少しの経験値。転生者どうし・未転生どうしでは何もない
        Growth.get(n1).xp = 0;
        Growth.get(n1).level = 5;
        Rebirth.today = () -> 102L;
        Rebirth.onTrialCleared(List.of(guide, n1));
        check(f, rd.shards == 5 && Growth.get(n1).xp > 0, "a trial clear did not give the guide shard and ally xp: " + rd.shards + "/" + Growth.get(n1).xp);
        int before = rd.shards;
        Rebirth.onTrialCleared(List.of(n2, n3));
        Growth.get(n3).rebirths = 1;
        Rebirth.onTrialCleared(List.of(guide, n3));
        check(f, rd.shards == before, "shards came from a pair with no unreborn ally");
        // 5 個で節を 1 つ (好きな腕に)、一生で 6 つまで
        Growth.get(guide).rebirths = 2;
        rd.nodes.clear();
        rd.shards = 5;
        rd.shardNodes = 0;
        check(f, Rebirth.claimShardNode(guide, "bone") && rd.nodes.equals(List.of("bone")) && rd.shards == 0 && rd.shardNodes == 1, "the shard node was not added");
        check(f, !Rebirth.claimShardNode(guide, "bone") && rd.nodes.size() == 1, "a node was added without enough shards");
        rd.shards = 99;
        rd.shardNodes = RebirthRules.MAX_SHARD_NODES;
        check(f, !Rebirth.claimShardNode(guide, "bone"), "more than 6 shard nodes");
        check(f, !Rebirth.claimShardNode(guide, "zzz"), "an unknown arm took a shard node");
    }

    // ---------------------------------------------------------------- 若い運び手
    private static void sync(MinecraftServer server, List<String> f) {
        check(f, near(Rebirth.youngSpeed(server, UUID.randomUUID()), 1.0) && near(Rebirth.youngRange(server, UUID.randomUUID()), 0), "an unknown owner got a bond");
        check(f, Rebirth.youngSpeed(null, UUID.randomUUID()) == 1.0, "a null server broke the bond lookup");
    }
}
