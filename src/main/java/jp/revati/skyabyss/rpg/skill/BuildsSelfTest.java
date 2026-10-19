package jp.revati.skyabyss.rpg.skill;

import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.registry.ModItems;
import jp.revati.skyabyss.rpg.Growth;
import jp.revati.skyabyss.rpg.GrowthRules;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.util.FakePlayer;

// ビルド (5 つの木・刻印・型の保存・天職・刻み目・技の新しい 16 本) の自己テスト。SkillSelfTest.run の最後から呼ぶ
final class BuildsSelfTest {
    private BuildsSelfTest() {
    }

    private static void check(List<String> f, boolean ok, String what) {
        if (!ok) {
            f.add(what);
        }
    }

    static void run(MinecraftServer server, List<String> f) {
        migration(server, f);
        weaponTypes(f);
        balance(f);
        player(server, f);
        callings(server, f);
        legendary(server, f);
        marks(server, f);
    }

    private static ItemStack legendStack(String path) {
        return new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, path)));
    }

    // ---------------------------------------------------------------- 伝説の書き換え (4 番目の刻印)
    private static void legendary(MinecraftServer server, List<String> f) {
        ServerLevel level = server.overworld();
        BlockPos spawn = level.getSharedSpawnPos();
        FakePlayer p = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "builds_test_l"));
        p.moveTo(spawn.getX() + 0.5, 300, spawn.getZ() + 0.5, 0f, 0f);
        Growth.get(p).level = 30;
        SkillData d = SkillManager.get(p);
        d.bonusPoints = 60;
        d.calling = "";
        // 書き換えは 8 つ。武器の品は実在し、書き換える技は使う技
        int count = 0;
        for (Rune r : Rune.values()) {
            if (!r.legendary()) {
                continue;
            }
            count++;
            check(f, r.index == 4 && r.skill.active && !legendStack(r.weapon).isEmpty(), "legendary rune " + r.id + " points at nothing");
            check(f, Rune.of(r.skill).stream().noneMatch(Rune::legendary), "ordinary rune list holds a legendary rune for " + r.skill.id);
        }
        check(f, count == 8, "legendary rune count is " + count);
        // 持っていなければ選べない・持てば選べる・外せば効かない (記録には残る)
        SkillManager.forceRank(p, Skill.TITAN_CLEAVE, 3);
        SkillManager.forceRank(p, Skill.LUNGE, 2);
        p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
        check(f, SkillManager.chooseRune(p, Skill.LUNGE, 4) == SkillManager.RuneResult.NOT_AVAILABLE, "a legendary rune was chosen without the weapon");
        p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, legendStack("warden_glaive"));
        check(f, SkillManager.chooseRune(p, Skill.LUNGE, 4) == SkillManager.RuneResult.NOT_AVAILABLE, "a legendary rune was chosen with the wrong weapon");
        p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, legendStack("captain_pike"));
        check(f, SkillManager.chooseRune(p, Skill.LUNGE, 4) == SkillManager.RuneResult.OK && SkillManager.runeOf(p, Skill.LUNGE) == 4, "the legendary rune was not chosen");
        p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
        check(f, d.rune(Skill.LUNGE) == 4 && SkillManager.runeOf(p, Skill.LUNGE) == 0, "the legendary rune still worked without the weapon");
        // 技の側: 火床は熾火が 8 秒で弱らせる、火移しは仲間 2 人まで (一人でも通る)
        SkillManager.forceRank(p, Skill.EMBER_HAMMER, 2);
        SkillManager.forceRank(p, Skill.TEMPER, 3);
        d.runes.put(Skill.EMBER_HAMMER.id, 4);
        d.runes.put(Skill.TEMPER.id, 4);
        p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, legendStack("smith_hammer"));
        check(f, SkillManager.runeOf(p, Skill.EMBER_HAMMER) == 4 && SkillManager.runeOf(p, Skill.TEMPER) == 0, "hammer / poker rune held checks are wrong");
        SkillEffects.clear();
        d.mana = 100;
        d.cooldowns.clear();
        d.globalCooldown = 0;
        d.slots[0] = Skill.EMBER_HAMMER.id;
        check(f, SkillManager.cast(p, 0) == SkillManager.CastResult.OK, "the hearth hammer could not be cast");
        var zone = SkillEffects.zonesOf(SkillEffects.ZoneKind.EMBER, p.getUUID());
        check(f, zone == 1, "the hearth hammer left no embers");
        p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, legendStack("thrall_poker"));
        d.mana = 100;
        d.cooldowns.clear();
        d.globalCooldown = 0;
        d.slots[0] = Skill.TEMPER.id;
        check(f, SkillManager.cast(p, 0) == SkillManager.CastResult.OK && d.temperUntil > level.getGameTime(), "the poker temper did not work");
        SkillEffects.clear();
        SkillManager.forgetAll(p);
    }

    // ---------------------------------------------------------------- 木の印
    private static void marks(MinecraftServer server, List<String> f) {
        ServerLevel level = server.overworld();
        BlockPos spawn = level.getSharedSpawnPos();
        FakePlayer p = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "builds_test_m"));
        p.moveTo(spawn.getX() + 0.5, 300, spawn.getZ() + 0.5, 0f, 0f);
        // 種類: 5 つ・武器・重み 10 の合計・幅 5〜10%
        int marks = 0;
        int weight = 0;
        for (jp.revati.skyabyss.item.affix.AffixType t : jp.revati.skyabyss.item.affix.AffixType.values()) {
            if (t.isMark()) {
                marks++;
                weight += t.weight;
                check(f, t.slot == jp.revati.skyabyss.item.affix.AffixType.Slot.WEAPON && t.min == 0.05 && t.max == 0.10, "mark " + t.id + " is wrong");
            }
        }
        check(f, marks == 5 && weight == 10, "mark count/weight: " + marks + "/" + weight);
        for (SkillTree t : SkillTree.values()) {
            check(f, t.callable() == (SkillMarks.typeOf(t) != null), "mark type of " + t.id);
        }
        // 抽選: 1 装備に 1 つまで
        net.minecraft.util.RandomSource rnd = net.minecraft.util.RandomSource.create(42);
        int seen = 0;
        for (int i = 0; i < 4000; i++) {
            List<jp.revati.skyabyss.item.affix.Affix> list = jp.revati.skyabyss.item.affix.AffixRules.roll(jp.revati.skyabyss.item.affix.AffixType.Slot.WEAPON,
                    jp.revati.skyabyss.item.GearTier.LEGENDARY, rnd);
            long n = list.stream().filter(a -> a.kind() != null && a.kind().isMark()).count();
            check(f, n <= 1, "a weapon rolled two marks");
            seen += (int) n;
        }
        check(f, seen > 100 && seen < 1000, "mark frequency is off: " + seen + " / 4000");
        // 効き目: 手の武器の印が、その木の技の力にだけ足される
        ItemStack sword = legendStack("captain_pike");
        sword.set(jp.revati.skyabyss.registry.ModDataComponents.AFFIXES.get(), List.of(new jp.revati.skyabyss.item.affix.Affix(jp.revati.skyabyss.item.affix.AffixType.MARK_WIND, 0.08)));
        p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, sword);
        Growth.get(p).level = 30;
        check(f, Math.abs(SkillMarks.power(p, SkillTree.WIND) - 0.08) < 1e-9 && SkillMarks.power(p, SkillTree.ROCK) == 0 && SkillMarks.power(p, SkillTree.TRAVELER) == 0, "mark power is wrong");
        double rock = SkillEffects.damagePower(p, Skill.TITAN_CLEAVE);
        double wind = SkillEffects.damagePower(p, Skill.WIND_REND);
        check(f, Math.abs(wind - rock - 0.08) < 1e-9, "mark did not raise the wind skill power: " + wind + " vs " + rock);
        p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        check(f, SkillMarks.power(p, SkillTree.WIND) == 0, "mark worked from an empty hand");
    }

    // ---------------------------------------------------------------- 天職の層 (docs/design/27 の 10 節)
    private static void callings(MinecraftServer server, List<String> f) {
        ServerLevel level = server.overworld();
        BlockPos spawn = level.getSharedSpawnPos();
        FakePlayer p = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "builds_test_c"));
        p.moveTo(spawn.getX() + 0.5, 300, spawn.getZ() + 0.5, 0f, 0f);
        Growth.get(p).level = 30;
        SkillData d = SkillManager.get(p);
        d.bonusPoints = 60;
        boolean before = SkillRules.CALLINGS;
        SkillRules.CALLINGS = true;
        try {
            // 天職の前: 1 行目だけ・刻印なし
            check(f, d.canLearn(Skill.TITAN_CLEAVE, 30) == SkillData.LearnResult.OK, "a first-row skill was refused before the calling");
            SkillManager.forceRank(p, Skill.TITAN_CLEAVE, 3);
            check(f, d.canLearn(Skill.ROCK_CALL, 30) == SkillData.LearnResult.CALLING, "a second-row skill opened before the calling");
            check(f, SkillCalling.which(p) == 0, "a calling was offered before the whale heart");
            check(f, SkillCalling.choose(p, "rock") == SkillCalling.Result.NOT_READY, "a calling was chosen before the whale heart");
            check(f, SkillCalling.choose(p, "traveler", true) == SkillCalling.Result.BAD_TREE, "the traveler tree was chosen as a calling");
            check(f, !d.runeAvailable(Skill.TITAN_CLEAVE), "a rune opened before the calling");
            // 一つ目: 岩守り
            check(f, SkillCalling.choose(p, "rock", true) == SkillCalling.Result.OK && d.calling.equals("rock") && d.calling2.isEmpty(), "the first calling was not set");
            check(f, d.canLearn(Skill.ROCK_CALL, 30) == SkillData.LearnResult.OK, "the calling tree did not open");
            check(f, d.canLearn(Skill.BACKSTEP, 30) == SkillData.LearnResult.CALLING || d.canLearn(Skill.BACKSTEP, 30) == SkillData.LearnResult.REQUIRES,
                    "another tree's second row opened");
            check(f, d.rankLimit(Skill.NIMBLE) == 2 && d.rankLimit(Skill.STONE_STANCE) == 3, "rank limits of other trees are wrong");
            check(f, d.runeAvailable(Skill.TITAN_CLEAVE), "a rune of the calling tree did not open");
            SkillManager.applyModifiers(p);
            var tough = p.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR_TOUGHNESS).getModifier(SkillManager.MOD_TRAIT_TOUGH);
            check(f, tough != null && Math.abs(tough.amount() - SkillRules.TRAIT_ROCK_TOUGHNESS) < 1e-9, "the rock trait was not applied");
            check(f, d.slotCount() == SkillRules.BASE_SLOTS, "slot count before the second calling");
            // 二つ目: 同じ木は選べない。風読みなら 3 行目まで・要なし・天性なし・枠 +1
            check(f, SkillCalling.which(p) == 0 && SkillCalling.choose(p, "wind") == SkillCalling.Result.NOT_READY, "the second calling opened early");
            check(f, SkillCalling.choose(p, "rock", true) == SkillCalling.Result.TAKEN, "the same tree was chosen twice");
            check(f, SkillCalling.choose(p, "wind", true) == SkillCalling.Result.OK && d.calling2.equals("wind"), "the second calling was not set");
            check(f, d.slotCount() == SkillRules.SLOTS, "the fifth slot did not open");
            check(f, d.callingAllows(Skill.BACKSTEP) && d.callingAllows(Skill.WIND_REND) && !d.callingAllows(Skill.LONE_WIND), "second calling rows are wrong");
            check(f, d.rankLimit(Skill.NIMBLE) == 3, "second calling tree has a rank limit");
            check(f, SkillCalling.choose(p, "tide", true) == SkillCalling.Result.TAKEN, "a third calling was set");
            var move = p.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED).getModifier(SkillManager.MOD_TRAIT_MOVE);
            check(f, move == null, "the second calling gave a trait");
            // 記録を保存して読み直しても残る
            SkillData copy = new SkillData();
            copy.deserializeNBT(server.registryAccess(), d.serializeNBT(server.registryAccess()));
            check(f, copy.calling.equals("rock") && copy.calling2.equals("wind"), "callings were lost in save/load");
        } finally {
            SkillRules.CALLINGS = before;
            SkillManager.forgetAll(p);
        }
    }

    // ---------------------------------------------------------------- 前の版の記録の移行
    private static void migration(MinecraftServer server, List<String> f) {
        CompoundTag old = new CompoundTag();
        CompoundTag ranks = new CompoundTag();
        ranks.putInt("titan_cleave", 2);
        ranks.putInt("cloud_pierce", 3);
        ranks.putInt("backstep", 2); // 前は弓使いの木の 2 段目。いまは風読みで、踏み込みが要る
        old.put("ranks", ranks);
        ListTag slots = new ListTag();
        slots.add(StringTag.valueOf("backstep"));
        slots.add(StringTag.valueOf("cloud_pierce"));
        slots.add(StringTag.valueOf("titan_cleave"));
        slots.add(StringTag.valueOf(""));
        old.put("slots", slots);
        old.putInt("respecs", 2);
        old.putFloat("mana", 12f);
        SkillData d = new SkillData();
        d.deserializeNBT(server.registryAccess(), old);
        check(f, d.version == SkillData.VERSION, "an old record was not moved to the new version");
        check(f, d.rank(Skill.TITAN_CLEAVE) == 2 && d.rank(Skill.CLOUD_PIERCE) == 3 && !d.knows(Skill.BACKSTEP),
                "migration kept or dropped the wrong skills: " + d.ranks);
        check(f, d.slots[0].isEmpty() && d.slots[1].equals("cloud_pierce") && d.slots[2].equals("titan_cleave"), "migration slots are wrong: " + String.join(",", d.slots));
        check(f, d.respecs == 2 && d.mana == 12f && d.runes.isEmpty() && !d.loadouts[0].saved && d.calling.isEmpty(), "migration lost the plain fields");
        // 点は戻る (使った点が減る)
        check(f, d.spentPoints() == 5, "migration points: " + d.spentPoints());
        // 新しい記録は往復できる
        d.runes.clear();
        CompoundTag again = d.serializeNBT(server.registryAccess());
        SkillData e = new SkillData();
        e.deserializeNBT(server.registryAccess(), again);
        check(f, e.version == SkillData.VERSION && e.ranks.equals(d.ranks), "a new record did not round trip");
    }

    // ---------------------------------------------------------------- 武器の型
    private static void weaponTypes(List<String> f) {
        check(f, WeaponType.of(ModItems.WHALEBONE_GREATSWORD.get()) == WeaponType.HEAVY, "greatsword is not heavy");
        check(f, WeaponType.of(Items.BOW) == WeaponType.RANGED && WeaponType.of(Items.SHIELD) == WeaponType.GUARD
                && WeaponType.of(Items.IRON_AXE) == WeaponType.HEAVY && WeaponType.of(Items.TRIDENT) == WeaponType.LONG
                && WeaponType.of(Items.STICK) == null, "vanilla weapon types are wrong");
        for (var holder : net.minecraft.core.registries.BuiltInRegistries.ITEM) {
            String path = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(holder).getPath();
            if (path.equals("captain_pike")) {
                check(f, WeaponType.of(holder) == WeaponType.LONG, "captain pike is not long");
            } else if (path.equals("waterway_fang")) {
                check(f, WeaponType.of(holder) == WeaponType.FAST, "waterway fang is not fast");
            } else if (path.equals("bone_spike_arrow")) {
                check(f, WeaponType.of(holder) == null, "an arrow was read as a weapon type");
            }
        }
        check(f, SkillRules.affinity(Skill.TITAN_CLEAVE) == WeaponType.HEAVY && SkillRules.affinity(Skill.SIGNAL) == null, "affinity table");
    }

    // ---------------------------------------------------------------- 数のつり合い (心臓・番兵長を倒す時間)
    // 技 1 本あたり、マナと待ちの両方に縛られた「攻撃力の何倍 / 秒」。ふつうの攻撃は 1 / 0.625 = 1.6 倍 / 秒
    static double perMana(Skill s, int rank) {
        return switch (s) {
            case TITAN_CLEAVE -> SkillRules.cleaveMultiplier(rank) * SkillRules.CLEAVE_LARGE_BONUS;
            case WHALE_SWEEP -> SkillRules.sweepMultiplier(rank);
            case WIND_REND -> SkillRules.rendBack(rank) * 2;
            case EMBER_HAMMER -> SkillRules.hammerMultiplier(rank);
            case LUNGE -> SkillRules.LUNGE_MULTIPLIER;
            case BONE_TRAP -> SkillRules.TRAP_LARGE_MULTIPLIER;
            default -> 0;
        } / SkillRules.manaCost(s, rank);
    }

    private static void balance(List<String> f) {
        double basic = 1.6;
        // 精神 20 (最大マナ 60) で、戦いの最中のマナの回復で回せる技の最大の力
        double regen = SkillRules.regenPerSecond(20 + GrowthRules.bonusMana(20), false);
        double best = 0;
        for (Skill s : new Skill[] {Skill.TITAN_CLEAVE, Skill.WHALE_SWEEP, Skill.WIND_REND, Skill.EMBER_HAMMER, Skill.LUNGE, Skill.BONE_TRAP}) {
            double mana = perMana(s, s.maxRank);
            double cooldownLimit = mana * SkillRules.manaCost(s, s.maxRank) / (SkillRules.cooldownTicks(s, s.maxRank) / 20.0);
            check(f, cooldownLimit / basic <= 0.45, s.id + " alone is too strong (" + cooldownLimit + " per second vs " + basic + ")");
            best = Math.max(best, mana);
        }
        double skillDps = regen * best;
        double bonus = skillDps / basic;
        // 精神 20 でマナを全部技へ回しても、ふつうの攻撃の +10〜45% (04 の 7.7。章 1 のころは 15〜20% の増加、章 2 以降で本数が増える)
        check(f, bonus >= 0.10 && bonus <= 0.45, "skill rotation bonus is out of range: " + bonus);
        // 心臓 (体力 450)・番兵長 (体力 150 とみなす) を、攻撃力 8 の一人が倒す時間 (秒)
        double heart = 450 / (8 * (basic + skillDps));
        double captain = 150 / (8 * (basic + skillDps));
        check(f, heart >= 20 && heart <= 40, "heart kill time is out of range: " + heart);
        check(f, captain >= 6 && captain <= 14, "captain kill time is out of range: " + captain);
        SkyAbyss.LOGGER.info("[selftest] skills balance: rotation +{}%, heart {} s, captain {} s", Math.round(bonus * 100), Math.round(heart), Math.round(captain));
    }

    // ---------------------------------------------------------------- 実際のプレイヤー
    private static void player(MinecraftServer server, List<String> f) {
        ServerLevel level = server.overworld();
        BlockPos spawn = level.getSharedSpawnPos();
        double px = spawn.getX() + 0.5;
        double py = 300;
        double pz = spawn.getZ() + 0.5;
        FakePlayer p = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "builds_test_a"));
        p.moveTo(px, py, pz, 0f, 0f);
        Growth.get(p).level = 16;
        p.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE).setBaseValue(7);
        SkillData d = SkillManager.get(p);
        d.bonusPoints = 40;

        // ---- 刻印: 極めた技にだけ。はじめは 1 点、付け替えは場所と結晶
        SkillManager.forceRank(p, Skill.TITAN_CLEAVE, 2);
        check(f, SkillManager.chooseRune(p, Skill.TITAN_CLEAVE, 1) == SkillManager.RuneResult.NOT_AVAILABLE, "a rune was chosen before mastering the skill");
        SkillManager.forceRank(p, Skill.TITAN_CLEAVE, 3);
        int spent = d.spentPoints();
        check(f, SkillManager.chooseRune(p, Skill.TITAN_CLEAVE, 1) == SkillManager.RuneResult.OK && d.rune(Skill.TITAN_CLEAVE) == 1
                && d.spentPoints() == spent + 1, "choosing the first rune did not cost one point");
        check(f, SkillManager.chooseRune(p, Skill.TITAN_CLEAVE, 1) == SkillManager.RuneResult.SAME, "the same rune was chosen twice");
        check(f, SkillManager.chooseRune(p, Skill.TITAN_CLEAVE, 4) == SkillManager.RuneResult.NOT_AVAILABLE, "an unknown rune was chosen");
        check(f, SkillManager.chooseRune(p, Skill.ROCK_CALL, 1) == SkillManager.RuneResult.NOT_AVAILABLE, "a rune of an unlearned skill was chosen");
        check(f, SkillManager.chooseRune(p, Skill.TITAN_CLEAVE, 2) == SkillManager.RuneResult.NO_PLACE, "a rune was swapped away from a forge or fire");
        SkillManager.forceRank(p, Skill.JOURNAL, 1);
        spent = d.spentPoints();
        d.lastCombat = level.getGameTime();
        check(f, SkillManager.chooseRune(p, Skill.TITAN_CLEAVE, 2) == SkillManager.RuneResult.NOT_QUIET, "a rune was swapped in combat");
        d.lastCombat = level.getGameTime() - SkillRules.QUIET_TICKS - 1;
        check(f, SkillManager.chooseRune(p, Skill.TITAN_CLEAVE, 2) == SkillManager.RuneResult.OK && d.rune(Skill.TITAN_CLEAVE) == 2 && d.runeSwaps == 1
                && d.spentPoints() == spent, "the first swap was not free");
        check(f, SkillManager.chooseRune(p, Skill.TITAN_CLEAVE, 3) == SkillManager.RuneResult.NO_CRYSTALS, "a second swap was free");
        check(f, SkillManager.runeOf(p, Skill.TITAN_CLEAVE) == 2, "runeOf is wrong");
        // 刻印の待ちへの効き目
        check(f, SkillRules.cooldownTicks(Skill.ROCK_CALL, 2, 1) == SkillRules.cooldownTicks(Skill.ROCK_CALL, 2) + SkillRules.LONG_VOICE_COOLDOWN, "long voice cooldown");

        // ---- 型の保存
        SkillManager.forceRank(p, Skill.ROCK_CALL, 2);
        SkillManager.setSlot(p, 0, Skill.TITAN_CLEAVE);
        SkillManager.setSlot(p, 1, Skill.ROCK_CALL);
        check(f, SkillManager.loadLoadout(p, 0) == SkillManager.LoadoutResult.EMPTY, "an empty loadout was loaded");
        check(f, SkillManager.saveLoadout(p, 0) && d.loadouts[0].saved, "a loadout was not saved");
        int pointsBefore = d.spentPoints();
        SkillManager.chooseRune(p, Skill.TITAN_CLEAVE, 3);
        SkillManager.setSlot(p, 0, null);
        SkillManager.setSlot(p, 1, null);
        check(f, SkillManager.loadLoadout(p, 0) == SkillManager.LoadoutResult.OK && d.slots[0].equals("titan_cleave") && d.slots[1].equals("rock_call")
                && d.rune(Skill.TITAN_CLEAVE) == 2 && d.spentPoints() == pointsBefore, "loading a loadout did not restore slots and runes");
        CompoundTag saved = d.serializeNBT(server.registryAccess());
        SkillData back = new SkillData();
        back.deserializeNBT(server.registryAccess(), saved);
        check(f, back.loadouts[0].saved && back.loadouts[0].runes.equals(d.loadouts[0].runes) && back.runes.equals(d.runes) && back.runeSwaps == d.runeSwaps,
                "runes or loadouts did not survive the save");
        net.minecraft.network.RegistryFriendlyByteBuf buf = new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), server.registryAccess());
        SkillData.STREAM_CODEC.encode(buf, d);
        SkillData synced = SkillData.STREAM_CODEC.decode(buf);
        buf.release();
        check(f, synced.runes.equals(d.runes) && synced.loadouts[0].saved && synced.runeSwaps == d.runeSwaps, "runes or loadouts did not survive the sync codec");

        // ---- 振り直し: Lv 15 になるまで無料 (どこでも・回数を数えない)、そのあとは炉のそばで
        Growth.get(p).level = 10;
        d.bonusPoints = 0;
        d.respecs = 0;
        SkillManager.forceRank(p, Skill.TITAN_CLEAVE, 1);
        check(f, SkillManager.respecCostFor(p) == 0 && SkillManager.respec(p) == SkillManager.RespecResult.OK && d.respecs == 0 && d.ranks.isEmpty()
                && d.runes.isEmpty(), "an early respec was not free");
        Growth.get(p).level = 16;
        d.bonusPoints = 40;
        SkillManager.forceRank(p, Skill.TITAN_CLEAVE, 1);
        check(f, SkillManager.respec(p) == SkillManager.RespecResult.NO_FORGE, "a late respec worked away from a forge");
        check(f, SkillRules.respecCost(3, 16, true) == 2 && SkillRules.respecCost(1, 16, true) == 1 && SkillRules.respecCost(3, 14, false) == 0, "respec cost with appraiser / early");

        // ---- 常時の技の修飾子と刻み目
        SkillManager.forgetAll(p);
        d.bonusPoints = 40;
        SkillManager.forceRank(p, Skill.NIMBLE, 3);
        var attrs = net.minecraft.world.entity.ai.attributes.Attributes.class;
        check(f, Math.abs(p.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.SAFE_FALL_DISTANCE) - 6) < 1e-6, "nimble did not raise the safe fall distance: "
                + p.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.SAFE_FALL_DISTANCE));
        check(f, attrs != null && p.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED).getModifier(SkillManager.MOD_NIMBLE_MOVE) != null,
                "nimble did not add a speed modifier");
        Growth.get(p).stats[GrowthRules.Stat.VITALITY.ordinal()] = 15;
        SkillManager.applyModifiers(p);
        check(f, p.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE).getModifier(SkillManager.MOD_BP_KNOCKBACK) != null, "vitality 15 gave no knockback resistance");
        Growth.get(p).stats[GrowthRules.Stat.VITALITY.ordinal()] = 14;
        SkillManager.applyModifiers(p);
        check(f, p.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE).getModifier(SkillManager.MOD_BP_KNOCKBACK) == null, "vitality 14 gave knockback resistance");
        Growth.get(p).stats[GrowthRules.Stat.VITALITY.ordinal()] = 0;
        check(f, Breakpoints.reached(15, 15) && !Breakpoints.reached(14, 15) && Breakpoints.Mark.values().length == 8, "breakpoints");

        // ---- 技を使う
        SkillManager.forgetAll(p);
        d.bonusPoints = 60;
        d.cooldowns.clear();
        Zombie zombie = new Zombie(EntityType.ZOMBIE, level);
        zombie.moveTo(px, py, pz + 2.0, 180f, 0f);
        zombie.setNoAi(true);
        zombie.setNoGravity(true);
        zombie.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(500);
        zombie.setHealth(500);
        level.addFreshEntity(zombie);
        int slot = 0;
        for (Skill s : new Skill[] {Skill.ROCK_CALL, Skill.WARD_SONG, Skill.TEMPER, Skill.EBB_TIDE, Skill.ASH_SCATTER, Skill.SIGNAL, Skill.WIND_REND,
                Skill.EMBER_HAMMER}) {
            SkillManager.forceRank(p, s, s.maxRank);
        }
        Object[][] casts = {{Skill.ROCK_CALL}, {Skill.WARD_SONG}, {Skill.TEMPER}, {Skill.EBB_TIDE}, {Skill.ASH_SCATTER}, {Skill.SIGNAL}, {Skill.WIND_REND},
                {Skill.EMBER_HAMMER}};
        for (Object[] c : casts) {
            Skill s = (Skill) c[0];
            d.mana = 100;
            d.cooldowns.clear();
            d.globalCooldown = 0;
            d.slots[0] = s.id;
            zombie.moveTo(px, py, pz + 2.0, 180f, 0f);
            float hp = zombie.getHealth();
            var r = SkillManager.cast(p, 0);
            check(f, r == SkillManager.CastResult.OK, s.id + " cast returned " + r);
            switch (s) {
                case ROCK_CALL -> check(f, SkillEffects.tauntedBy(zombie, p) && zombie.getTarget() == p && d.tauntUntil > level.getGameTime(), "rock call did not draw the zombie's aim");
                case WARD_SONG -> check(f, Math.abs(p.getAbsorptionAmount() - 4f) < 1e-4, "ward song absorption was " + p.getAbsorptionAmount());
                case TEMPER -> check(f, d.temperUntil > level.getGameTime() && d.temperMult > 0, "temper did not light the weapon");
                case EBB_TIDE -> check(f, SkillEffects.slowed(zombie), "ebb tide did not slow the zombie");
                case ASH_SCATTER -> check(f, SkillEffects.ashed(zombie), "ash scatter did not cover the zombie");
                case SIGNAL -> check(f, SkillEffects.zonesOf(SkillEffects.ZoneKind.SIGNAL, p.getUUID()) == 1 || SkillEffects.zonesOf(SkillEffects.ZoneKind.SIGNAL, p.getUUID()) >= 1, "signal made no beacon");
                case WIND_REND -> check(f, zombie.getHealth() < hp, "wind rend did not hurt");
                case EMBER_HAMMER -> check(f, zombie.getHealth() < hp && SkillEffects.zonesOf(SkillEffects.ZoneKind.EMBER, p.getUUID()) == 1, "ember hammer did not slam or leave embers");
                default -> {
                }
            }
            check(f, d.cooldown(s) > 0, s.id + " started no cooldown");
        }
        // 炉の記憶: 技を使うと熾が溜まる (段の数まで)
        SkillManager.forceRank(p, Skill.FORGE_MEMORY, 2);
        d.mana = 100;
        d.cooldowns.clear();
        d.globalCooldown = 0;
        d.embers = 0;
        d.slots[0] = Skill.ROCK_CALL.id;
        SkillManager.cast(p, 0);
        check(f, d.embers == 1, "forge memory gathered no ember");
        // 守りの唄は重ねがけで増えない・黄ハートは 2 ハートまで
        p.setAbsorptionAmount(0);
        SkillEffects.absorb(p, 9, 100, false);
        check(f, p.getAbsorptionAmount() <= SkillRules.ABSORB_CAP + 1e-4, "absorption went over the cap");
        // 引き潮の鈍足は期限で消える
        SkillEffects.clear();
        check(f, !SkillEffects.slowed(zombie), "clear left a slow behind");
        zombie.discard();
        SkillManager.forgetAll(p);
        p.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED).removeModifier(SkillManager.MOD_NIMBLE_MOVE);
        check(f, new ItemStack(Items.AIR).isEmpty(), "sanity");
    }
}
