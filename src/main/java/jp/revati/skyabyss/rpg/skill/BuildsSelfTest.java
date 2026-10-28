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
        freedom(server, f);
        legendary(server, f);
        marks(server, f);
        DeepSkillsSelfTest.run(server, f);
    }

    private static ItemStack legendStack(String path) {
        return new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, path)));
    }

    // ---------------------------------------------------------------- 逸品の書き換え (4 番目の刻印)
    private static void legendary(MinecraftServer server, List<String> f) {
        ServerLevel level = server.overworld();
        BlockPos spawn = level.getSharedSpawnPos();
        FakePlayer p = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "builds_test_l"));
        p.moveTo(spawn.getX() + 0.5, 300, spawn.getZ() + 0.5, 0f, 0f);
        Growth.get(p).level = 30;
        SkillData d = SkillManager.get(p);
        d.bonusPoints = 60;
        // 書き換えは 32 (従来 8 + 深淵のアーティファクト 4 + 巨神ごとの 6 職の伝説 20)。武器の品は実在し、書き換える技は使う技。同じ武器に二つはない
        int count = 0;
        for (Rune r : Rune.values()) {
            if (!r.legendary()) {
                continue;
            }
            count++;
            check(f, (r.index == 4 || r.index == 6 || r.index == 7) && r.skill.active && !legendStack(r.weapon).isEmpty(), "legendary rune " + r.id + " points at nothing");
            check(f, Rune.of(r.skill).stream().noneMatch(Rune::legendary), "ordinary rune list holds a legendary rune for " + r.skill.id);
        }
        // 32 = 逸品 8・深淵 4・伝説 20、17 = 巨神のヒロイック武器 (item/HeroicGear)
        check(f, count == 49, "legendary rune count is " + count);
        java.util.Set<String> weapons = new java.util.HashSet<>();
        for (Rune r : Rune.values()) {
            if (r.legendary()) {
                check(f, weapons.add(r.weapon), "two legendary rewrites on the weapon " + r.weapon);
            }
        }
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
        abyssLegends(p, f);
        titanLegends(p, f);
        SkillManager.forgetAll(p);
    }

    // 巨神ごとの 6 職の伝説 (20)。持てばその書き換えが効き (番号は 4 か 6)、同じ技の別の伝説を持てばそちらに替わる。技を出せる
    private static void titanLegends(FakePlayer p, List<String> f) {
        SkillData d = SkillManager.get(p);
        ServerLevel level = p.serverLevel();
        net.minecraft.world.entity.monster.Zombie foe = new net.minecraft.world.entity.monster.Zombie(net.minecraft.world.entity.EntityType.ZOMBIE, level);
        int cast = 0;
        for (Rune r : Rune.values()) {
            if (!r.legendary() || !jp.revati.skyabyss.abyss.LegendGear.legendIds().contains(jp.revati.skyabyss.SkyAbyss.MODID + ":" + r.weapon)) {
                continue;
            }
            SkillManager.forceRank(p, r.skill, r.skill.maxRank);
            d.runes.put(r.skill.id, 4);
            p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, legendStack(r.weapon));
            check(f, SkillManager.runeOf(p, r.skill) == r.index && SkillManager.legendRune(p, r.skill) == r, "the rewrite " + r.id + " does not follow its weapon");
            foe.moveTo(p.getX(), p.getY(), p.getZ() + 2.5, 0, 0);
            if (level.getEntity(foe.getUUID()) == null) {
                level.addFreshEntity(foe);
            }
            foe.setHealth(foe.getMaxHealth());
            p.setYRot(0);
            p.setXRot(10);
            p.setOnGround(true);
            d.mana = 100;
            d.cooldowns.clear();
            d.globalCooldown = 0;
            d.slots[0] = r.skill.id;
            try {
                if (SkillManager.cast(p, 0) == SkillManager.CastResult.OK) {
                    cast++;
                } else {
                    String why = SkillActions.perform(p, r.skill, r.skill.maxRank);
                    jp.revati.skyabyss.SkyAbyss.LOGGER.info("SELFTEST builds note: rewrite {} on {} not cast: {}", r.id, r.skill.id, why);
                }
            } catch (RuntimeException e) {
                f.add("the rewrite " + r.id + " crashed: " + e);
            }
            SkillEffects.clear();
        }
        check(f, cast == 20, "only " + cast + " of 20 titan legend rewrites could be cast");
        // 同じ技の二つの書き換え: 持っている武器のほうが効く (巨神斬り = 奈落断ち 4 / 雷断ち 6)
        d.runes.put(Skill.TITAN_CLEAVE.id, 4);
        p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, legendStack("abyss_greatsword"));
        boolean abyss = SkillManager.runeOf(p, Skill.TITAN_CLEAVE) == 4;
        p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, legendStack("abyss_storm_sword"));
        check(f, abyss && SkillManager.runeOf(p, Skill.TITAN_CLEAVE) == 6 && SkillManager.legendRune(p, Skill.TITAN_CLEAVE) == Rune.STORM_CLEAVE,
                "two rewrites of one skill do not follow the held weapon");
        foe.discard();
    }

    // 深淵の心臓の四つのアーティファクト (灰喰い・雷淵・沈み鐘・底なし)。持つと、その一つの書き換えだけが選べて、書き換えた技が出る
    private static void abyssLegends(FakePlayer p, List<String> f) {
        SkillData d = SkillManager.get(p);
        String[][] pairs = {
            {"abyss_greatsword", Skill.TITAN_CLEAVE.id},
            {"abyss_bell_staff", Skill.WARD_SONG.id},
            {"abyss_storm_bow", Skill.CLOUD_PIERCE.id},
            {"abyss_ash_scythe", Skill.ASH_SCATTER.id}};
        for (String[] pair : pairs) {
            Skill skill = Skill.byId(pair[1]);
            SkillManager.forceRank(p, skill, skill.maxRank);
            d.cooldowns.clear();
            d.globalCooldown = 0;
            d.mana = 100;
            p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
            check(f, SkillManager.chooseRune(p, skill, 4) == SkillManager.RuneResult.NOT_AVAILABLE, pair[0] + ": chosen without the weapon");
            p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, legendStack(pair[0]));
            check(f, SkillManager.chooseRune(p, skill, 4) == SkillManager.RuneResult.OK && SkillManager.runeOf(p, skill) == 4,
                    pair[0] + ": the rewrite was not chosen with the weapon");
            d.slots[0] = skill.id;
            // 弓の書き換えは矢が要り、灰の書き換えは近くに敵が要る (技の中身の前提。書き換えの当たりとは別)
            p.getInventory().clearContent();
            p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, legendStack(pair[0]));
            if (pair[0].equals("abyss_storm_bow")) {
                p.getInventory().add(new ItemStack(Items.ARROW, 16));
            }
            net.minecraft.world.entity.monster.Zombie foe = null;
            if (skill == Skill.ASH_SCATTER) {
                foe = new net.minecraft.world.entity.monster.Zombie(net.minecraft.world.entity.EntityType.ZOMBIE, p.level());
                foe.setPos(p.getX() + 2, p.getY(), p.getZ());
                p.level().addFreshEntity(foe);
            }
            SkillManager.CastResult cast = SkillManager.cast(p, 0);
            if (foe != null) {
                foe.discard();
            }
            // 放った矢は消す (後の段が、同じ場所の矢の数を数えるため)
            for (net.minecraft.world.entity.projectile.AbstractArrow a : p.level().getEntitiesOfClass(net.minecraft.world.entity.projectile.AbstractArrow.class,
                    p.getBoundingBox().inflate(40), e -> true)) {
                a.discard();
            }
            check(f, cast == SkillManager.CastResult.OK, pair[0] + ": the rewritten skill did not cast (" + cast + ", skill " + skill.id + ")");
            d.cooldowns.clear();
            d.globalCooldown = 0;
            d.runes.remove(skill.id);
            SkillEffects.clear();
        }
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
                    jp.revati.skyabyss.item.GearTier.HEROIC, rnd);
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
        // 基礎の要求値 (48): 届いていないと印は効かない
        Growth.get(p).stats[GrowthRules.Stat.STRENGTH.ordinal()] = 0;
        check(f, SkillMarks.power(p, SkillTree.WIND) == 0, "a mark worked below the weapon's stat requirement");
        Growth.get(p).stats[GrowthRules.Stat.STRENGTH.ordinal()] = GearRequirement.STONE_WHALE;
        check(f, Math.abs(SkillMarks.power(p, SkillTree.WIND) - 0.08) < 1e-9 && SkillMarks.power(p, SkillTree.ROCK) == 0, "mark power is wrong");
        double rock = SkillEffects.damagePower(p, Skill.TITAN_CLEAVE);
        double wind = SkillEffects.damagePower(p, Skill.WIND_REND);
        check(f, Math.abs(wind - rock - 0.08) < 1e-9, "the mark (+8%) did not raise the wind skill power: " + wind + " vs " + rock);
        Growth.get(p).stats[GrowthRules.Stat.STRENGTH.ordinal()] = 0;
        p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        check(f, SkillMarks.power(p, SkillTree.WIND) == 0, "mark worked from an empty hand");
    }

    // ---------------------------------------------------------------- 自由度 (docs/design/31 の A 案)
    private static boolean testModOn;

    private static void freedom(MinecraftServer server, List<String> f) {
        ServerLevel level = server.overworld();
        BlockPos spawn = level.getSharedSpawnPos();
        FakePlayer p = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "builds_test_f"));
        p.moveTo(spawn.getX() + 0.5, 300, spawn.getZ() + 0.5, 0f, 0f);
        Growth.get(p).level = 50;
        p.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE).setBaseValue(7);
        SkillData d = SkillManager.get(p);
        p.getAttribute(jp.revati.skyabyss.rpg.Rpg.MAX_MANA).setBaseValue(200);
        d.bonusPoints = 0;
        long now = level.getGameTime();
        try {
            SkillManager.forgetAll(p);
            // ---- A01: 点の総数は 45 のまま
            check(f, d.totalPoints(50) == 50, "total points changed: " + d.totalPoints(50));
            SkillManager.forceRank(p, Skill.WHALE_SWEEP, 2);
            check(f, d.canLearn(Skill.STONE_OATH, 50) == SkillData.LearnResult.OK, "a keystone row did not open from the start");
            // ---- A03: タグは数えるだけ (淵の鏡・誓いと紋の条件)。力の足し・木のセット・条件の特性は 48 で木の節へ
            for (Skill s : Skill.values()) {
                check(f, SkillTag.of(s) != null, "skill without a tag: " + s.id);
            }
            SkillManager.forgetAll(p);
            SkillManager.forceRank(p, Skill.TITAN_CLEAVE, 1);
            SkillManager.forceRank(p, Skill.RIPOSTE, 1);
            SkillManager.forceRank(p, Skill.ROCK_CALL, 1);
            check(f, SkillBuild.tagCount(p, SkillTag.ROCK) == 3 && SkillBuild.tagCount(p, SkillTag.WIND) == 0, "tag count is wrong");
            check(f, Math.abs(SkillEffects.damagePower(p, Skill.TITAN_CLEAVE) - SkillEffects.damagePower(p, Skill.WIND_REND)) < 1e-9, "tags still change skill power");
            SkillManager.forceRank(p, Skill.TITAN_CLEAVE, 3);
            SkillManager.forceRank(p, Skill.STONE_STANCE, 3);
            SkillManager.applyModifiers(p);
            var armor = p.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR).getModifier(SkillManager.MOD_B_ARMOR);
            check(f, armor == null || Math.abs(armor.amount()) < 1e-9, "tree sets still give armor: " + (armor == null ? "none" : armor.amount()));
            p.setHealth(4f);
            check(f, Math.abs(SkillBuild.attrs(p).armor()) < 1e-9, "low hp armor applied without a node: " + SkillBuild.attrs(p).armor());
            p.setHealth(20f);
            SkillManager.forceRank(p, Skill.NIMBLE, 1);
            SkillManager.forceRank(p, Skill.LUNGE, 1);
            SkillManager.forceRank(p, Skill.BACKSTEP, 1);
            check(f, SkillBuild.tagCount(p, SkillTag.WIND) == 3, "wind tag count is " + SkillBuild.tagCount(p, SkillTag.WIND));
            // ---- A20: キーストーン (その木に点 4・2 つまで・外すのは戦いの外)
            check(f, SkillManager.toggleKeystone(p, SkillKeystone.FULL_TIDE) == SkillManager.KeystoneResult.NOT_REACHED, "a keystone opened without tree points");
            SkillManager.forceRank(p, Skill.WIND_REND, 2);
            Growth.get(p).level = 50;
            check(f, SkillManager.toggleKeystone(p, SkillKeystone.BULWARK) == SkillManager.KeystoneResult.OK && d.keystones.contains("bulwark"), "the first keystone was refused");
            SkillManager.applyModifiers(p);
            armor = p.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR).getModifier(SkillManager.MOD_B_ARMOR);
            check(f, armor != null && Math.abs(armor.amount() - SkillRules.KEY_BULWARK_ARMOR) < 1e-9, "the bulwark keystone armor is wrong: " + (armor == null ? "none" : armor.amount()));
            check(f, SkillManager.toggleKeystone(p, SkillKeystone.GLASS_BLADE) == SkillManager.KeystoneResult.OK, "the second keystone was refused");
            check(f, SkillManager.toggleKeystone(p, SkillKeystone.WIND_DANCE) == SkillManager.KeystoneResult.FULL, "a third keystone was allowed");
            check(f, Math.abs(SkillBuild.generalDamage(p, false) - SkillRules.KEY_GLASS_DAMAGE) < 1e-9, "keystone damage is wrong: " + SkillBuild.generalDamage(p, false));
            d.lastCombat = level.getGameTime();
            check(f, SkillManager.toggleKeystone(p, SkillKeystone.GLASS_BLADE) == SkillManager.KeystoneResult.NOT_QUIET, "a keystone was removed in combat");
            d.lastCombat = level.getGameTime() - SkillRules.QUIET_TICKS - 1;
            check(f, SkillManager.toggleKeystone(p, SkillKeystone.GLASS_BLADE) == SkillManager.KeystoneResult.OK && d.keystones.size() == 1, "a keystone was not removed");
            // 技の点が足りなくなったら外れる (検め)
            d.keystones.add("glass_blade");
            d.bonusPoints = 0;
            Growth.get(p).level = 1;
            d.sanitize(1);
            check(f, d.ranks.isEmpty() && d.keystones.isEmpty(), "points over the limit did not clear keystones");
            Growth.get(p).level = 50;
            // ---- 外からの修飾 (装備の側の口): 力は +100% で頭打ち
            SkillBuild.register(new SkillModifier() {
                @Override
                public double power(net.minecraft.world.entity.player.Player player, Skill skill) {
                    return testModOn && skill == Skill.LUNGE ? 5.0 : 0;
                }

                @Override
                public int extraTags(net.minecraft.world.entity.player.Player player, SkillTag tag) {
                    return testModOn && tag == SkillTag.BONE ? 2 : 0;
                }
            });
            testModOn = true;
            check(f, SkillBuild.skillPower(p, Skill.LUNGE) <= SkillRules.POWER_CAP + 1e-9 && SkillBuild.tagCount(p, SkillTag.BONE) == 2, "a skill modifier did not apply or was not capped");
            testModOn = false;
            // ---- A05: 振り直しは結晶 1・型は職ごとに 2 つ
            check(f, SkillRules.LOADOUTS == 12 && d.loadouts.length == 12, "loadout count is wrong (two per job)");
            check(f, SkillRules.respecCost(9, 30) == 1 && SkillRules.respecCost(9, 10) == 0, "respec cost is wrong");
            // ---- A12: 武器の持ち替えで枠が入れ替わる (待ちは共有)
            SkillManager.forgetAll(p);
            d.bonusPoints = 40;
            SkillManager.forceRank(p, Skill.LUNGE, 1);
            SkillManager.forceRank(p, Skill.CLOUD_PIERCE, 1);
            SkillManager.forceRank(p, Skill.TITAN_CLEAVE, 1);
            d.bar = 0;
            d.slots[0] = Skill.LUNGE.id;
            d.slots[1] = Skill.TITAN_CLEAVE.id;
            p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
            SkillManager.syncBar(p);
            check(f, d.bar == 0 && d.slots[0].equals("lunge"), "a sword changed the bar");
            p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Items.BOW));
            SkillManager.syncBar(p);
            check(f, d.bar == 1 && d.slots[0].equals("lunge"), "the first bow swap should keep the slots");
            d.slots[0] = Skill.CLOUD_PIERCE.id;
            d.slots[1] = "";
            d.cooldowns.put(Skill.LUNGE.id, 100);
            p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
            SkillManager.syncBar(p);
            check(f, d.bar == 0 && d.slots[0].equals("lunge") && d.slots[1].equals("titan_cleave"), "the melee bar did not come back");
            check(f, d.cooldown(Skill.LUNGE) == 100, "a bar swap reset a cooldown");
            p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Items.BOW));
            SkillManager.syncBar(p);
            check(f, d.bar == 1 && d.slots[0].equals("cloud_pierce") && d.slots[1].isEmpty(), "the bow bar did not come back");
            p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            SkillManager.syncBar(p);
            check(f, d.bar == 1, "an empty hand changed the bar");
            d.cooldowns.clear();
            // ---- A10: 連鎖は 43 (6 職) で削った
            // ---- A13: 熟練 (使う・当てる・予告をよけて当てる。3 で 4 つ目の刻印、転生でも残る)
            SkillManager.forgetAll(p);
            d.bonusPoints = 40;
            d.mastery.clear();
            SkillManager.forceRank(p, Skill.TEMPER, 3);
            check(f, d.masteryLevel(Skill.TEMPER) == 0 && SkillRules.masteryLevel(14) == 0 && SkillRules.masteryLevel(15) == 1 && SkillRules.masteryLevel(300) == 5,
                    "mastery levels are wrong");
            check(f, SkillManager.chooseRune(p, Skill.TEMPER, Rune.MASTERY) == SkillManager.RuneResult.NOT_AVAILABLE, "the mastery rune opened early");
            SkillManager.addMastery(p, Skill.TEMPER, 100);
            check(f, d.masteryLevel(Skill.TEMPER) == 3 && d.runeUsable(Skill.TEMPER, Rune.MASTERY), "mastery 3 did not open the 4th rune");
            check(f, SkillManager.chooseRune(p, Skill.TEMPER, Rune.MASTERY) == SkillManager.RuneResult.OK && SkillManager.runeOf(p, Skill.TEMPER) == Rune.MASTERY, "the mastery rune was not chosen");
            check(f, Math.abs(SkillBuild.skillPower(p, Skill.TEMPER) - SkillRules.MASTERY_RUNE_POWER) < 1e-9
                    && Math.abs(SkillBuild.cooldownScale(p, Skill.TEMPER) - (1 + SkillRules.MASTERY_RUNE_COOLDOWN)) < 1e-9, "the mastery rune numbers are wrong");
            // ---- 実際の傷: 傷のキーストーンが技の傷に乗り、熟練が数えられる (広い技が何体に当たっても 10 tick に 1 度)
            SkillManager.forgetAll(p);
            d.bonusPoints = 40;
            d.mastery.clear();
            d.masteryHitAt.clear();
            SkillManager.forceRank(p, Skill.WIND_REND, 2);
            Zombie zombie = new Zombie(EntityType.ZOMBIE, level);
            zombie.moveTo(spawn.getX() + 0.5, 300, spawn.getZ() + 2.5, 180f, 0f);
            zombie.setNoAi(true);
            zombie.setNoGravity(true);
            zombie.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(500);
            level.addFreshEntity(zombie);
            double[] dealt = new double[3];
            for (int run = 0; run < 3; run++) {
                if (run == 1) {
                    d.keystones.add("glass_blade");
                }
                if (run == 2) {
                    d.dodgeUntil = level.getGameTime() + 60;
                }
                zombie.setHealth(500);
                d.mana = 100;
                d.cooldowns.clear();
                d.globalCooldown = 0;
                d.slots[0] = Skill.WIND_REND.id;
                d.masteryHitAt.clear();
                int xp = d.masteryXp(Skill.WIND_REND);
                check(f, SkillManager.cast(p, 0) == SkillManager.CastResult.OK, "wind rend could not be cast for the damage test");
                dealt[run] = 500 - zombie.getHealth();
                int gained = d.masteryXp(Skill.WIND_REND) - xp;
                // 使う 1 + 当てる 1 (予告をよけた直後は当てる 3)
                check(f, gained == (run == 2 ? 1 + SkillRules.MASTERY_DODGE_HIT : 2), "mastery gain is " + gained + " in run " + run);
            }
            check(f, dealt[0] > 0 && Math.abs(dealt[1] / dealt[0] - (1 + SkillRules.KEY_GLASS_DAMAGE)) < 0.02, "the glass blade keystone did not raise skill damage: " + dealt[0] + " -> " + dealt[1]);
            d.keystones.clear();
            d.dodgeUntil = 0;
            zombie.discard();
            // ---- A18: 試着の確定 (途中で通らなければそこで止める)
            SkillManager.forgetAll(p);
            d.bonusPoints = 0;
            Growth.get(p).level = 20;
            int learned = SkillManager.learnAll(p, List.of("titan_cleave", "titan_cleave", "rock_call", "lone_wind", "riposte"));
            check(f, learned == 3 && d.rank(Skill.TITAN_CLEAVE) == 2 && d.knows(Skill.ROCK_CALL) && !d.knows(Skill.RIPOSTE), "the try-on batch stopped in the wrong place: " + learned);
            check(f, SkillManager.learnAll(p, List.of("no_such_skill")) == 0, "an unknown skill was learned from a try-on");
            SkillData copy = d.copyOf();
            copy.ranks.put(Skill.RIPOSTE.id, 1);
            check(f, !d.knows(Skill.RIPOSTE) && copy.knows(Skill.RIPOSTE), "a try-on copy touched the real record");
            // ---- 保存・同期・転生 (型・熟練は残る)
            SkillManager.forgetAll(p);
            Growth.get(p).level = 50;
            d.bonusPoints = 20;
            SkillManager.forceRank(p, Skill.TITAN_CLEAVE, 3);
            SkillManager.forceRank(p, Skill.ROCK_CALL, 2);
            SkillManager.setSlot(p, 0, Skill.TITAN_CLEAVE);
            SkillManager.setSlot(p, 1, Skill.ROCK_CALL);
            d.mastery.put(Skill.TITAN_CLEAVE.id, 70);
            d.keystones.add("bulwark");
            SkillManager.saveLoadout(p, 3);
            check(f, d.loadouts[3].saved, "the fourth loadout was not saved");
            CompoundTag nbt = d.serializeNBT(server.registryAccess());
            SkillData back = new SkillData();
            back.deserializeNBT(server.registryAccess(), nbt);
            // キーストーンは 43 (6 職) で削った (読み直すと外れる)。熟練と型の保存は残る
            check(f, back.mastery.equals(d.mastery) && back.keystones.isEmpty() && back.loadouts[3].saved,
                    "new fields did not survive the save");
            net.minecraft.network.RegistryFriendlyByteBuf buf = new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), server.registryAccess());
            SkillData.STREAM_CODEC.encode(buf, d);
            SkillData synced = SkillData.STREAM_CODEC.decode(buf);
            buf.release();
            check(f, synced.mastery.equals(d.mastery) && synced.keystones.equals(d.keystones) && synced.bars[0].length == SkillRules.SLOTS,
                    "new fields did not survive the sync codec");
            SkillReset.forRebirth(p);
            check(f, d.ranks.isEmpty() && d.runes.isEmpty() && d.keystones.isEmpty() && d.bonusPoints == 0
                    && d.unspentPoints(50) == 50, "the rebirth reset left something behind");
            check(f, d.loadouts[3].saved && d.mastery.getOrDefault(Skill.TITAN_CLEAVE.id, 0) == 70, "the rebirth reset removed loadouts or mastery");
            // ---- 前の版 (v2) の記録: 天職の要を自分で取っていた分は点が戻る
            CompoundTag old = new CompoundTag();
            old.putInt("v", 2);
            CompoundTag ranks = new CompoundTag();
            ranks.putInt("titan_cleave", 3);
            ranks.putInt("rock_call", 2);
            ranks.putInt("whale_sweep", 2);
            ranks.putInt("stone_oath", 1);
            old.put("ranks", ranks);
            old.putString("calling", "rock");
            SkillData m = new SkillData();
            m.deserializeNBT(server.registryAccess(), old);
            // 天職は削ったので、要は授からず自分で取った分のまま (点も戻らない)
            check(f, m.version == SkillData.VERSION && m.knows(Skill.STONE_OATH) && m.rank(Skill.WHALE_SWEEP) == 2
                    && m.jobSpent(SkillTree.ROCK) == 8, "a v2 record with a calling did not migrate: " + m.ranks + " spent " + m.jobSpent(SkillTree.ROCK));
            check(f, m.loadouts.length == 12 && !m.loadouts[3].saved, "a v2 record did not get the job loadouts");
        } finally {
            testModOn = false;
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
        check(f, d.respecs == 2 && d.mana == 12f && d.runes.isEmpty() && !d.loadouts[0].saved, "migration lost the plain fields");
        // 点は戻る (使った点が減る)
        check(f, d.spentPointsAllTrees() == 5 && d.spentPoints() == 3, "migration points: " + d.spentPointsAllTrees() + " / " + d.spentPoints());
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
        check(f, SkillRules.affinity(Skill.TITAN_CLEAVE) == WeaponType.HEAVY && SkillRules.affinity(Skill.CALM_HEART) == null, "affinity table");
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
        // 精神 20 でマナを全部技へ回しても、ふつうの攻撃の +10〜65% (04 の 7.7。章 1 のころは 15〜20% の増加、章 2 以降で本数が増える)
        check(f, bonus >= 0.10 && bonus <= 0.65, "skill rotation bonus is out of range: " + bonus);
        // 心臓 (体力 450)・番兵長 (体力 150 とみなす) を、攻撃力 8 の一人が倒す時間 (秒)
        double heart = 450 / (8 * (basic + skillDps));
        double captain = 150 / (8 * (basic + skillDps));
        check(f, heart >= 20 && heart <= 40, "heart kill time is out of range: " + heart);
        check(f, captain >= 6 && captain <= 14, "captain kill time is out of range: " + captain);
        // 組み立ての上限: 傷の型の要 2 つ + 木の節の与える傷 (+0.3 ほど) + 技の力 (節 +0.2・熟練の刻印) をすべて足した人。
        // 心臓は 15 秒、番兵長は 5 秒を割らない (全部を同時に満たすのは難しいので、ふつうは基準と上限の間)
        // 型の要は今の職の木のものだけ (戦士なら硝子の刃と疾風の舞)。与える傷の節は 1 本の木で +0.25 ほど、技の力の節は +0.15 ほど
        double general = SkillRules.KEY_GLASS_DAMAGE + SkillRules.KEY_DANCE_DAMAGE + 0.25;
        double skillPart = 0.15 + SkillRules.MASTERY_RUNE_POWER;
        double manaCost = 1 + SkillRules.KEY_ASH_MANA_COST;
        double maxDps = basic * (1 + general) + skillDps / manaCost * (1 + general) * (1 + skillPart);
        double heartMax = 450 / (8 * maxDps);
        double captainMax = 150 / (8 * maxDps);
        check(f, heartMax >= 15 && captainMax >= 5, "the strongest build kills the heart / captain too fast: " + heartMax + " / " + captainMax);
        SkyAbyss.LOGGER.info("[selftest] skills balance (strongest build): heart {} s, captain {} s", Math.round(heartMax), Math.round(captainMax));
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
        // 刻印の付け替えの場所: 焚き火を足もとのそばに置く (旅の手帖は 43 で削った)。前の回の焚き火が残っていれば先に消す
        BlockPos fire = BlockPos.containing(p.getX() + 1, p.getY() - 1, p.getZ());
        level.setBlock(fire, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
        check(f, SkillManager.chooseRune(p, Skill.TITAN_CLEAVE, 2) == SkillManager.RuneResult.NO_PLACE, "a rune was swapped away from a forge or fire");
        level.setBlock(fire, net.minecraft.world.level.block.Blocks.CAMPFIRE.defaultBlockState(), 3);
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
        level.setBlock(fire, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);

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
        check(f, SkillRules.respecCost(3, 16) == 1 && SkillRules.respecCost(1, 16) == 1 && SkillRules.respecCost(3, 14) == 0, "respec cost early");

        // ---- 常時の技の修飾子
        SkillManager.forgetAll(p);
        d.bonusPoints = 40;
        SkillManager.forceRank(p, Skill.NIMBLE, 3);
        var attrs = net.minecraft.world.entity.ai.attributes.Attributes.class;
        check(f, Math.abs(p.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.SAFE_FALL_DISTANCE) - 6) < 1e-6, "nimble did not raise the safe fall distance: "
                + p.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.SAFE_FALL_DISTANCE));
        check(f, attrs != null && p.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED).getModifier(SkillManager.MOD_NIMBLE_MOVE) != null,
                "nimble did not add a speed modifier");

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
        for (Skill s : new Skill[] {Skill.ROCK_CALL, Skill.WARD_SONG, Skill.TEMPER, Skill.EBB_TIDE, Skill.ASH_SCATTER, Skill.WIND_REND,
                Skill.EMBER_HAMMER}) {
            SkillManager.forceRank(p, s, s.maxRank);
        }
        Object[][] casts = {{Skill.ROCK_CALL}, {Skill.WARD_SONG}, {Skill.TEMPER}, {Skill.EBB_TIDE}, {Skill.ASH_SCATTER}, {Skill.WIND_REND},
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
                case WARD_SONG -> check(f, Math.abs(p.getAbsorptionAmount() - 5f) < 1e-4, "ward song absorption was " + p.getAbsorptionAmount());
                case TEMPER -> check(f, d.temperUntil > level.getGameTime() && d.temperMult > 0, "temper did not light the weapon");
                case EBB_TIDE -> check(f, SkillEffects.slowed(zombie), "ebb tide did not slow the zombie");
                case ASH_SCATTER -> check(f, SkillEffects.ashed(zombie), "ash scatter did not cover the zombie");
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
