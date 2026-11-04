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
            SkyAbyss.LOGGER.info("SELFTEST gear freedom PASSED (sockets by tier incl. uncommon and bows, extract/imprint, free unsocket, combine, powder arts, armor guard, rune cap, generations, persistence, loot)");
        } else {
            f.forEach(m -> SkyAbyss.LOGGER.error("SELFTEST gear freedom FAILED: {}", m));
        }
    }

    private static void check(List<String> f, boolean ok, String what) {
        if (!ok) {
            f.add(what);
        }
    }

    private static void powderArts(List<String> f, ServerLevel level, FakePlayer p) {
        check(f, PowderArts.strength(4) == 1 && PowderArts.strength(8) == 1.5 && PowderArts.strength(12) == 2 && PowderArts.strength(18) == 2,
                "powder art strength should be x1 / x1.5 / x2");
        double px = p.getX(), py = p.getY(), pz = p.getZ();
        List<net.minecraft.world.entity.monster.Zombie> zs = new ArrayList<>();
        try {
            net.minecraft.world.entity.monster.Zombie a = zombie(level, px + 3, py, pz, zs);
            net.minecraft.world.entity.monster.Zombie b = zombie(level, px + 4.5, py, pz, zs);
            // 岩 IV の武器: 4 回目の当たりで地割れ (当てた敵のまわりの敵にも傷)
            ItemStack sword = rolled(ModItems.WHALEBONE_GREATSWORD.get(), 2);
            ForgeRules.insertGem(sword, Powder.withTier(new ItemStack(ModItems.SOCKET_SHARD_STONE.get()), 4));
            p.setItemInHand(InteractionHand.MAIN_HAND, sword);
            PowderArts.reset(p);
            check(f, PowderArts.weaponArts(p).getOrDefault(Element.STONE, 0) == 4, "a tier IV stone powder should wake the quake");
            for (int i = 0; i < 3; i++) {
                PowderArts.onWeaponHit(p, a, 4f);
            }
            check(f, b.getHealth() == b.getMaxHealth(), "the quake fired before the 4th hit");
            PowderArts.onWeaponHit(p, a, 4f);
            check(f, b.getHealth() < b.getMaxHealth() && PowderArts.hitCount(p) == 0, "the 4th hit did not fire the quake on the nearby foe");
            // 骨の呪い: 受ける傷 +12%
            heal(a);
            PowderArts.fireWeapon(Element.BONE, p, a, 4f, 1);
            a.invulnerableTime = 0;
            a.hurt(level.damageSources().generic(), 5f);
            check(f, PowderArts.cursed(a) && Math.abs(a.getMaxHealth() - a.getHealth() - 5.6f) < 0.01, "the bone curse should add 12% damage: " + (a.getMaxHealth() - a.getHealth()));
            // 熾の印: 燃え尽きると、まわりの敵へ爆ぜる
            heal(b);
            PowderArts.fireWeapon(Element.ASH, p, a, 4f, 1);
            check(f, PowderArts.branded(a), "the ember brand was not set");
            PowderArts.burstNow(a);
            check(f, !PowderArts.branded(a) && b.getHealth() < b.getMaxHealth(), "the ember brand did not burst on the nearby foe");
            // 引き潮: 与えた傷の 20% を癒す (そばに人がいなければ自分だけ)
            p.setHealth(p.getMaxHealth() - 6);
            float hp = p.getHealth();
            PowderArts.fireWeapon(Element.TIDE, p, a, 10f, 1);
            check(f, Math.abs(p.getHealth() - hp - 2f) < 0.01, "the ebb should heal 20% of the damage: " + (p.getHealth() - hp));
            // 防具: 岩肌で受ける傷 −30%、8 秒に一度
            ItemStack chest = rolled(ModItems.STONE_WHALE_CHESTPLATE.get(), 2);
            ForgeRules.insertGem(chest, Powder.withTier(new ItemStack(ModItems.SOCKET_SHARD_STONE.get()), 4));
            p.setItemSlot(EquipmentSlot.CHEST, chest);
            check(f, PowderArts.armorArts(p).containsKey(Element.STONE), "a tier IV stone powder in armor should wake stoneskin");
            PowderArts.onArmorHit(p, a, 2f);
            check(f, PowderArts.skinned(p), "stoneskin did not start");
            p.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
            p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            PowderArts.reset(p);
            // 落ちる粉の段: 丘陵 I・浜 II・高地 III・荒野 IV、深淵 V
            check(f, PowderArts.DROP_TIER[2] == 1 && PowderArts.DROP_TIER[3] == 2 && PowderArts.DROP_TIER[4] == 3 && PowderArts.DROP_TIER[5] == 4 && PowderArts.ABYSS_TIER == 5,
                    "land powder tiers are wrong");
            // 防具の強化: 1 段 1 部位 −0.4%、四部位 +10 で −16%
            EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
            net.minecraft.world.item.Item[] pieces = {ModItems.STONE_WHALE_HELMET.get(), ModItems.STONE_WHALE_CHESTPLATE.get(), ModItems.STONE_WHALE_LEGGINGS.get(), ModItems.STONE_WHALE_BOOTS.get()};
            for (int i = 0; i < 4; i++) {
                ItemStack piece = new ItemStack(pieces[i]);
                piece.set(ModDataComponents.ENHANCE.get(), 10);
                p.setItemSlot(slots[i], piece);
            }
            check(f, Math.abs(jp.revati.skyabyss.item.GearEvents.enhanceGuard(p) - 0.16) < 1e-9, "four +10 armor pieces should cut 16%: " + jp.revati.skyabyss.item.GearEvents.enhanceGuard(p));
            for (EquipmentSlot slot : slots) {
                p.setItemSlot(slot, ItemStack.EMPTY);
            }
        } finally {
            zs.forEach(net.minecraft.world.entity.Entity::discard);
        }
    }

    private static net.minecraft.world.entity.monster.Zombie zombie(ServerLevel level, double x, double y, double z, List<net.minecraft.world.entity.monster.Zombie> zs) {
        net.minecraft.world.entity.monster.Zombie zb = new net.minecraft.world.entity.monster.Zombie(level);
        zb.moveTo(x, y, z, 0, 0);
        zb.setNoAi(true);
        zb.setSilent(true);
        zb.setPersistenceRequired();
        level.addFreshEntity(zb);
        zs.add(zb);
        return zb;
    }

    private static void heal(net.minecraft.world.entity.LivingEntity e) {
        e.setHealth(e.getMaxHealth());
        e.invulnerableTime = 0;
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

        // ---- 穴の数 (階級で決まる。抽選なし。2026-11-05 の粉と炉の頁 1-3) と、古い装備の移行
        check(f, GearTier.COMMON.sockets() == 0 && GearTier.UNCOMMON.sockets() == 1 && GearTier.RARE.sockets() == 1 && GearTier.EPIC.sockets() == 2
                && GearTier.HEROIC.sockets() == 2 && GearTier.ARTIFACT.sockets() == 3 && GearTier.MYTHIC.sockets() == 3, "socket counts per tier are wrong");
        ItemStack old = new ItemStack(ModItems.WHALEBONE_GREATSWORD.get());
        old.set(ModDataComponents.AFFIXES.get(), List.of(new Affix(AffixType.SHARP, 0.05)));
        check(f, !GearFreedom.has(old) && GearFreedom.ensure(old, random) && GearFreedom.sockets(old) == 2, "legacy epic gear did not get its 2 sockets");
        check(f, !GearFreedom.ensure(old, random), "sockets were set twice");
        check(f, AffixRules.affixes(old).size() == 1, "socket setup changed the affixes");
        ItemStack few = rolled(ModItems.WHALEBONE_GREATSWORD.get(), 0);
        check(f, GearFreedom.ensure(few, random) && GearFreedom.sockets(few) == 2, "an epic with 0 rolled sockets was not raised to 2");
        ItemStack many = rolled(ModItems.CAPTAIN_PIKE.get(), 3);
        check(f, !GearFreedom.ensure(many, random) && GearFreedom.sockets(many) == 3, "a heroic that rolled 3 sockets lost one");
        ItemStack junk = new ItemStack(ModItems.STONE_WHALE_SHARD.get());
        check(f, !GearFreedom.ensure(junk, random) && !GearFreedom.socketable(junk), "material got sockets");
        // アンコモンと弓にも穴 (特性は付かない)
        ItemStack uncommon = new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "wild_guard_sword")));
        check(f, GearFreedom.socketable(uncommon) && !GearFreedom.eligible(uncommon) && GearFreedom.ensure(uncommon, random) && GearFreedom.sockets(uncommon) == 1,
                "an uncommon sword should have 1 socket and no affixes");
        ItemStack bow = new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "whale_bow")));
        check(f, GearFreedom.socketSlot(bow.getItem()) == AffixType.Slot.WEAPON && GearFreedom.ensure(bow, random) && GearFreedom.sockets(bow) == 2,
                "an epic bow should have 2 sockets: " + GearFreedom.sockets(bow));
        ForgeRules.insertGem(bow, new ItemStack(ModItems.SOCKET_SHARD_WIND.get()));
        check(f, Math.abs(AffixRules.value(bow, AffixType.STRIDE) - 0.01) < 1e-9, "wind powder on a bow should give movement speed");

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
        check(f, ForgeRules.insertGem(s2, shard) && !ForgeRules.insertGem(s2, shard), "sockets overflowed (max 2)");
        ItemStack chestShard = rolled(ModItems.STONE_WHALE_CHESTPLATE.get(), 1);
        ForgeRules.insertGem(chestShard, new ItemStack(ModItems.SOCKET_SHARD_WIND.get()));
        check(f, Math.abs(AffixRules.value(chestShard, AffixType.STRIDE) - 0.01) < 1e-9, "armor shard stat wrong");
        int before = ForgeRules.count(p, ModItems.RESONANCE_CRYSTAL.get());
        ItemStack back = ForgeRules.removeGem(p, s2, 0);
        check(f, back.is(ModItems.SOCKET_SHARD_STONE.get()) && GearFreedom.freeSockets(s2) == 1, "unsocket failed");
        check(f, ForgeRules.count(p, ModItems.RESONANCE_CRYSTAL.get()) == before, "unsocketing should be free");
        // ---- 巨神の粉 (docs/design/41): 段は数字に掛かる・外すと段のまま戻る・段の和 4 で粉の技・三つで一つ上の段
        ItemStack ps = rolled(ModItems.WHALEBONE_GREATSWORD.get(), 2);
        double sharpBase = AffixRules.value(ps, AffixType.SHARP);
        ForgeRules.insertGem(ps, Powder.withTier(new ItemStack(ModItems.SOCKET_SHARD_STONE.get()), 2));
        check(f, Math.abs(AffixRules.value(ps, AffixType.SHARP) - (sharpBase + 0.04)) < 1e-9, "a tier II powder should add twice the attack");
        check(f, Powder.art(ps) == null, "one tier II powder should not wake the powder art");
        ForgeRules.insertGem(ps, Powder.withTier(new ItemStack(ModItems.SOCKET_SHARD_STONE.get()), 2));
        check(f, Powder.art(ps) == Element.STONE, "two tier II stone powders should wake the stone art");
        p.getInventory().setItem(20, new ItemStack(ModItems.RESONANCE_CRYSTAL.get(), 60));
        ItemStack out = ForgeRules.removeGem(p, ps, 0);
        check(f, out.is(ModItems.SOCKET_SHARD_STONE.get()) && Powder.tier(out) == 2, "unsocketed powder lost its tier: " + Powder.tier(out));
        ItemStack three = new ItemStack(ModItems.SOCKET_SHARD_WIND.get(), 3);
        p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, three);
        three.getItem().use(level, p, net.minecraft.world.InteractionHand.MAIN_HAND);
        boolean gotII = false;
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            ItemStack it = p.getInventory().getItem(i);
            gotII |= it.is(ModItems.SOCKET_SHARD_WIND.get()) && Powder.tier(it) == 2;
        }
        check(f, three.isEmpty() && gotII, "three tier I wind powders did not make one tier II");
        p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        // 粉を合わせる (炉の粉のつまみ): 同じ段三つ → 一つ上
        ItemStack tri = Powder.withTier(new ItemStack(ModItems.SOCKET_SHARD_ASH.get(), 4), 2);
        ItemStack up = ForgeRules.combinePowder(tri);
        check(f, up.is(ModItems.SOCKET_SHARD_ASH.get()) && Powder.tier(up) == 3 && tri.getCount() == 1, "combining three tier II powders failed");
        check(f, ForgeRules.combinePowder(tri).isEmpty(), "one powder combined alone");
        check(f, ForgeRules.combinePowder(Powder.withTier(new ItemStack(ModItems.SOCKET_SHARD_ASH.get(), 3), Powder.MAX_TIER)).isEmpty(), "tier VI combined further");

        // 粉の技 (見える技): 強さ・数え方・五つの武器の技・防具の技
        powderArts(f, level, p);

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
        check(f, SkillBuild.extraTagCount(p, jp.revati.skyabyss.rpg.skill.SkillTag.WIND) == 0, "powders should no longer count as skill tags");
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
