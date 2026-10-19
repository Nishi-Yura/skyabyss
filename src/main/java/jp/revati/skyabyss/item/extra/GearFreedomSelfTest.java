package jp.revati.skyabyss.item.extra;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.forge.ForgeRules;
import jp.revati.skyabyss.item.GearTier;
import jp.revati.skyabyss.item.affix.Affix;
import jp.revati.skyabyss.item.affix.AffixRules;
import jp.revati.skyabyss.item.affix.AffixType;
import jp.revati.skyabyss.registry.ModDataComponents;
import jp.revati.skyabyss.registry.ModItems;
import jp.revati.skyabyss.rpg.skill.Rune;
import jp.revati.skyabyss.rpg.skill.Skill;
import jp.revati.skyabyss.rpg.skill.SkillBuild;
import jp.revati.skyabyss.rpg.skill.SkillRules;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.util.FakePlayer;

// 装備の自由度 (アスペクト・ソケット・ルーン・世代) の自己テスト。SKYABYSS_SELFTEST_ONLY="gear freedom" でこれだけ流せる
public final class GearFreedomSelfTest {
    private GearFreedomSelfTest() {
    }

    public static void runSelfTest(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        try {
            run(server, f);
        } catch (Exception e) {
            SkyAbyss.LOGGER.error("gear freedom selftest crashed", e);
            f.add("crashed: " + e);
        }
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST gear freedom PASSED (sockets roll, extract/imprint, socket/unsocket, rune cap, generations, persistence, loot)");
        } else {
            f.forEach(m -> SkyAbyss.LOGGER.error("SELFTEST gear freedom FAILED: {}", m));
        }
    }

    private static void check(List<String> f, boolean ok, String what) {
        if (!ok) {
            f.add(what);
        }
    }

    private static ItemStack rolled(net.minecraft.world.item.Item item, int sockets) {
        ItemStack s = new ItemStack(item);
        s.set(ModDataComponents.AFFIXES.get(), List.of());
        s.set(ModDataComponents.GEAR_EXTRAS.get(), GearExtras.EMPTY.withSockets(sockets));
        return s;
    }

    private static void run(MinecraftServer server, List<String> f) {
        RandomSource random = RandomSource.create(4242);
        ServerLevel level = server.overworld();
        FakePlayer p = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "freedom_test"));
        jp.revati.skyabyss.rpg.Growth.get(p).level = jp.revati.skyabyss.rpg.GrowthRules.MAX_LEVEL;

        // ---- 穴の抽選と、古い装備の移行
        int[] rare = new int[3];
        int[] legend = new int[3];
        for (int i = 0; i < 6000; i++) {
            rare[GearFreedom.rollSockets(GearTier.RARE, random)]++;
            legend[GearFreedom.rollSockets(GearTier.LEGENDARY, random)]++;
        }
        check(f, Math.abs(rare[0] / 6000.0 - 0.50) < 0.03 && Math.abs(rare[2] / 6000.0 - 0.12) < 0.02, "rare socket odds off " + java.util.Arrays.toString(rare));
        check(f, Math.abs(legend[0] / 6000.0 - 0.30) < 0.03 && Math.abs(legend[2] / 6000.0 - 0.25) < 0.03, "legendary socket odds off " + java.util.Arrays.toString(legend));
        ItemStack old = new ItemStack(ModItems.WHALEBONE_GREATSWORD.get());
        old.set(ModDataComponents.AFFIXES.get(), List.of(new Affix(AffixType.SHARP, 0.05)));
        check(f, !GearFreedom.has(old) && GearFreedom.ensure(old, random) && GearFreedom.has(old), "legacy gear did not get its sockets rolled once");
        check(f, !GearFreedom.ensure(old, random), "sockets were rolled twice");
        check(f, AffixRules.affixes(old).size() == 1, "socket roll changed the affixes");
        ItemStack junk = new ItemStack(ModItems.STONE_WHALE_SHARD.get());
        check(f, !GearFreedom.ensure(junk, random), "material got sockets");

        // ---- 抽出・刻印 (アスペクト)
        // 手の枠 (0) と重ならない所へ置く
        p.getInventory().setItem(20, new ItemStack(ModItems.RESONANCE_CRYSTAL.get(), 60));
        ItemStack pike = rolled(ModItems.CAPTAIN_PIKE.get(), 1);
        check(f, GearFreedom.ownRune(pike) == Rune.BONE_PIERCE, "pike rewrite not found");
        check(f, !GearFreedom.canExtract(rolled(ModItems.WHALEBONE_GREATSWORD.get(), 0)), "rare could be extracted");
        ItemStack pikeKeep = pike.copy();
        ItemStack core = ForgeRules.extract(p, pikeKeep, false);
        check(f, GearFreedom.coreRune(core) == Rune.BONE_PIERCE, "extracted core has the wrong rewrite");
        check(f, GearFreedom.isHollow(pikeKeep) && !pikeKeep.isEmpty(), "keep-mark did not leave a hollow legendary");
        check(f, ForgeRules.extract(p, pikeKeep, false).isEmpty(), "hollow legendary extracted twice");
        p.setItemInHand(InteractionHand.MAIN_HAND, pikeKeep);
        check(f, !Rune.BONE_PIERCE.held(p), "hollow pike still rewrote the skill");
        p.setItemInHand(InteractionHand.MAIN_HAND, pike.copy());
        check(f, Rune.BONE_PIERCE.held(p), "intact pike lost its rewrite");
        ItemStack core2 = ForgeRules.extract(p, pike, true);
        check(f, GearFreedom.coreRune(core2) == Rune.BONE_PIERCE, "destroy extraction gave no core");
        ItemStack sword = rolled(ModItems.WHALEBONE_GREATSWORD.get(), 0);
        ItemStack chest = rolled(ModItems.STONE_WHALE_CHESTPLATE.get(), 0);
        check(f, !GearFreedom.canImprint(pike, core), "legendary accepted an aspect");
        check(f, ForgeRules.imprint(p, sword, core) && core.isEmpty(), "imprint on a rare failed or kept the core");
        check(f, GearFreedom.aspect(sword).equals("pike_bone"), "aspect id not stored: " + GearFreedom.aspect(sword));
        check(f, !GearFreedom.canImprint(sword, core2), "second aspect on one item");
        check(f, ForgeRules.imprint(p, chest, core2), "imprint on armor failed");
        // 刻んだ武器を持てば、その技の書き換えが効く。防具でも着ていれば
        p.setItemInHand(InteractionHand.MAIN_HAND, sword);
        check(f, Rune.BONE_PIERCE.held(p), "aspect on a held weapon did not rewrite the skill");
        p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        check(f, !Rune.BONE_PIERCE.held(p), "rewrite stayed with nothing equipped");
        p.setItemSlot(EquipmentSlot.CHEST, chest);
        check(f, Rune.BONE_PIERCE.held(p), "aspect on worn armor did not rewrite the skill");
        p.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);

        // ---- ソケット (欠片) と数字
        ItemStack s2 = rolled(ModItems.WHALEBONE_GREATSWORD.get(), 2);
        double sharp0 = AffixRules.value(s2, AffixType.SHARP);
        ItemStack shard = new ItemStack(ModItems.SOCKET_SHARD_STONE.get(), 3);
        check(f, ForgeRules.insertGem(s2, shard) && shard.getCount() == 2, "shard not socketed or not consumed");
        check(f, Math.abs(AffixRules.value(s2, AffixType.SHARP) - sharp0 - 0.02) > -1 && Math.abs(AffixRules.value(s2, AffixType.SHARP) - (sharp0 + 0.02)) < 1e-9, "stone shard did not add attack");
        check(f, GearFreedom.tags(s2).size() == 1, "tag missing");
        check(f, ForgeRules.insertGem(s2, shard) && !ForgeRules.insertGem(s2, shard), "sockets overflowed (max 2)");
        ItemStack chestShard = rolled(ModItems.STONE_WHALE_CHESTPLATE.get(), 1);
        ForgeRules.insertGem(chestShard, new ItemStack(ModItems.SOCKET_SHARD_WIND.get()));
        check(f, Math.abs(AffixRules.value(chestShard, AffixType.STRIDE) - 0.01) < 1e-9, "armor shard stat wrong");
        int before = ForgeRules.count(p, ModItems.RESONANCE_CRYSTAL.get());
        ItemStack back = ForgeRules.removeGem(p, s2, 0);
        check(f, back.is(ModItems.SOCKET_SHARD_STONE.get()) && GearFreedom.freeSockets(s2) == 1, "unsocket failed");
        check(f, ForgeRules.count(p, ModItems.RESONANCE_CRYSTAL.get()) == before - 1, "unsocket did not cost a crystal");
        // 打ち直しで穴を足す: 必ず付き、2 で止まる
        ItemStack none = rolled(ModItems.WHALEBONE_GREATSWORD.get(), 0);
        p.getInventory().setItem(21, new ItemStack(ForgeRules.shardItem(none), 40));
        check(f, ForgeRules.addSocket(p, none) && GearFreedom.sockets(none) == 1 && ForgeRules.addSocket(p, none) && GearFreedom.sockets(none) == 2
                && !ForgeRules.addSocket(p, none), "guaranteed socket add failed");

        // ---- ルーン: 1 つの技に 1 枚・倍率の上限
        ItemStack runeA = GearFreedomItems.rune(new RuneData("lunge", false, 1200));
        ItemStack runeB = GearFreedomItems.rune(new RuneData("lunge", false, 1300));
        ItemStack runeC = GearFreedomItems.rune(new RuneData("lunge", true, 850));
        ItemStack host = rolled(ModItems.WHALEBONE_GREATSWORD.get(), 2);
        check(f, ForgeRules.insertGem(host, runeA) && !ForgeRules.canInsert(host, runeB), "two runes for one skill on one item");
        ItemStack other = rolled(ModItems.STONE_WHALE_CHESTPLATE.get(), 2);
        check(f, ForgeRules.insertGem(other, runeB), "second item refused a rune");
        p.setItemInHand(InteractionHand.MAIN_HAND, host);
        p.setItemSlot(EquipmentSlot.CHEST, other);
        check(f, Math.abs(GearSkillModifier.INSTANCE.power(p, Skill.LUNGE) - 0.30) < 1e-9, "same-skill runes stacked instead of best-only: " + GearSkillModifier.INSTANCE.power(p, Skill.LUNGE));
        check(f, GearSkillModifier.INSTANCE.power(p, Skill.BACKSTEP) == 0, "rune leaked to another skill");
        ItemStack cdHost = rolled(ModItems.STONE_WHALE_HELMET.get(), 1);
        ForgeRules.insertGem(cdHost, runeC);
        p.setItemSlot(EquipmentSlot.HEAD, cdHost);
        check(f, Math.abs(GearSkillModifier.INSTANCE.cooldown(p, Skill.LUNGE) + 0.15) < 1e-9, "cooldown rune wrong");
        // 欠片をいくつ差しても、技の力の合計は +100% (×2.0) を超えない
        for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack a = rolled(slot == EquipmentSlot.LEGS ? ModItems.STONE_WHALE_LEGGINGS.get() : ModItems.STONE_WHALE_BOOTS.get(), 2);
            ForgeRules.insertGem(a, new ItemStack(ModItems.SOCKET_SHARD_WIND.get()));
            ForgeRules.insertGem(a, new ItemStack(ModItems.SOCKET_SHARD_WIND.get()));
            p.setItemSlot(slot, a);
        }
        check(f, SkillBuild.extraTagCount(p, jp.revati.skyabyss.rpg.skill.SkillTag.WIND) >= 4, "socketed shards were not counted as tags");
        check(f, SkillBuild.skillPower(p, Skill.LUNGE) <= SkillRules.POWER_CAP + 1e-9, "skill power passed the cap: " + SkillBuild.skillPower(p, Skill.LUNGE));
        check(f, SkillRules.POWER_CAP == 1.0, "power cap is no longer +100%");
        p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.CHEST, EquipmentSlot.HEAD, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            p.setItemSlot(slot, ItemStack.EMPTY);
        }

        // ---- 世代: 持ち越しで +1、同じ転生で二度は上がらない、3 ごとに形見の特性、12 で止まる
        ItemStack heirloom = rolled(ModItems.WHALEBONE_GREATSWORD.get(), 1);
        heirloom.set(ModDataComponents.AFFIXES.get(), List.of(new Affix(AffixType.SHARP, 0.05)));
        check(f, GearFreedom.bump(heirloom, 1, random) && GearFreedom.generation(heirloom) == 1, "generation did not rise on the first rebirth");
        check(f, !GearFreedom.bump(heirloom, 1, random) && GearFreedom.generation(heirloom) == 1, "generation rose twice for one rebirth");
        check(f, GearFreedom.extras(heirloom).traits().isEmpty(), "trait opened too early");
        GearFreedom.bump(heirloom, 2, random);
        GearFreedom.bump(heirloom, 3, random);
        GearExtras x = GearFreedom.extras(heirloom);
        check(f, x.generation() == 3 && x.traits().size() == 1, "generation 3 did not open one trait slot: " + x);
        Affix trait = x.traits().isEmpty() ? null : x.traits().get(0);
        check(f, trait != null && trait.kind() != null && trait.kind().slot == AffixType.Slot.WEAPON && !trait.kind().isMark()
                && trait.kind() != AffixType.SHARP, "trait type is wrong: " + trait);
        check(f, trait != null && Math.abs(AffixRules.value(heirloom, trait.kind()) - trait.value()) < 1e-9, "trait did not take effect");
        for (int i = 4; i <= 20; i++) {
            GearFreedom.bump(heirloom, i, random);
        }
        check(f, GearFreedom.generation(heirloom) == GearExtras.MAX_GENERATION && GearFreedom.extras(heirloom).traits().size() == 4, "generation cap/traits wrong");
        ItemStack nonGear = new ItemStack(ModItems.STONE_WHALE_SHARD.get());
        check(f, !GearFreedom.bump(nonGear, 5, random), "material got a generation");
        // 持ち物ぜんぶ: 特性の付く装備だけ数える
        p.getInventory().clearContent();
        p.getInventory().add(rolled(ModItems.WHALEBONE_GREATSWORD.get(), 0));
        p.getInventory().add(rolled(ModItems.STONE_WHALE_CHESTPLATE.get(), 0));
        p.getInventory().add(new ItemStack(ModItems.STONE_WHALE_SHARD.get(), 5));
        check(f, GearFreedom.onRebirth(p, 1) == 2, "rebirth hook counted the wrong number of gear pieces");

        // ---- 保存 (NBT の往復) と、同期のための符号
        ItemStack saved = rolled(ModItems.WHALEBONE_GREATSWORD.get(), 2);
        ForgeRules.insertGem(saved, new ItemStack(ModItems.SOCKET_SHARD_BONE.get()));
        ForgeRules.insertGem(saved, GearFreedomItems.rune(new RuneData("wind_rend", false, 1250)));
        GearFreedom.imprint(saved, GearFreedom.core(Rune.BITE));
        GearFreedom.bump(saved, 1, random);
        var ops = server.registryAccess().createSerializationContext(NbtOps.INSTANCE);
        Tag tag = ItemStack.CODEC.encodeStart(ops, saved).getOrThrow();
        ItemStack loaded = ItemStack.CODEC.parse(ops, tag).getOrThrow();
        check(f, GearFreedom.extras(loaded).equals(GearFreedom.extras(saved)) && GearFreedom.extras(loaded).gems().size() == 2
                && GearFreedom.aspect(loaded).equals("fang_bite") && GearFreedom.generation(loaded) == 1, "extras did not survive a save/load");
        // 古い形 (欠けたフィールド) も読める
        var partial = GearExtras.CODEC.parse(NbtOps.INSTANCE, new net.minecraft.nbt.CompoundTag()).result();
        check(f, partial.isPresent() && partial.get().equals(GearExtras.EMPTY), "empty record did not decode to defaults");
        // ツールチップ
        List<net.minecraft.network.chat.Component> tip = new ArrayList<>();
        GearFreedom.tooltip(saved, tip);
        check(f, tip.size() >= 5, "tooltip lines missing (" + tip.size() + ")");
        // 分解で差した物が戻る
        check(f, ForgeRules.gemItem(GearFreedom.extras(saved).gems().get(1)).is(ModItems.SKILL_RUNE.get()), "rune did not turn back into an item");

        // ---- 落ち方
        int shards = 0;
        int runes = 0;
        int trialRunes = 0;
        for (int i = 0; i < 20000; i++) {
            for (ItemStack s : GearFreedomLoot.heartDrops("ember", random)) {
                if (s.is(ModItems.SOCKET_SHARD_ASH.get())) {
                    shards++;
                } else if (s.is(ModItems.SKILL_RUNE.get())) {
                    runes++;
                    check(f, GearFreedomItems.runeOf(s) != null && GearFreedomItems.runeOf(s).valid(), "dropped rune is invalid");
                }
            }
            for (ItemStack s : GearFreedomLoot.trialDrops("tide_shell", false, false, random)) {
                if (s.is(ModItems.SKILL_RUNE.get())) {
                    trialRunes++;
                }
            }
        }
        check(f, Math.abs(shards / 20000.0 - 0.55) < 0.03, "heart shard rate off: " + shards / 20000.0);
        check(f, Math.abs(runes / 20000.0 - GearFreedomLoot.HEART_RUNE_CHANCE) < 0.01, "heart rune rate off: " + runes / 20000.0);
        check(f, Math.abs(trialRunes / 20000.0 - GearFreedomLoot.TRIAL_RUNE_REPEAT) < 0.006, "trial rune rate off: " + trialRunes / 20000.0);
        check(f, RuneData.skills().size() >= 15 && RuneData.skills().stream().allMatch(s -> s.kind == Skill.Kind.ACTIVE), "rune skill list wrong");
        // 特性の付かない旅装などは対象外
        check(f, !GearFreedom.eligible(new ItemStack(ModItems.STONE_WHALE_SHARD.get())), "material eligible");
    }
}
