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
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

// 成長の自己テスト。SKYABYSS_SELFTEST=1 で専用サーバーを起動したときだけ動く (止めるのは TitanBackSelfTest)。
// レベルの計算、保存 (NBT・同期・死亡時の引き継ぎ・プレイヤーの保存と読み込み)、修飾子の付け外し、振り分けの検めを確かめる
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class GrowthSelfTest {
    private GrowthSelfTest() {
    }

    @SubscribeEvent
    static void onServerStarted(ServerStartedEvent event) {
        if (System.getenv("SKYABYSS_SELFTEST") == null) {
            return;
        }
        List<String> failures = new ArrayList<>();
        try {
            run(event.getServer(), failures);
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
        ServerLevel overworld = server.overworld();
        GrowthData budget = new GrowthData();
        double first = GrowthRewards.vanillaBudget(overworld, budget, 140);
        double second = GrowthRewards.vanillaBudget(overworld, budget, 20);
        check(f, first == 140 && Math.abs(second - (10 + 10 * GrowthRules.VANILLA_TIRED)) < 1e-9,
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

        // 記憶の断片の器: 上限を超えて入らない
        GrowthData frag = new GrowthData();
        frag.fragmentCapacity = 2;
        check(f, frag.addFragments(MemoryFragments.PROLOGUE, 3) == 2 && frag.fragmentCount() == 2, "fragments overflowed the capacity");
        check(f, frag.addFragments(MemoryFragments.STONE_WHALE, 1) == 0 && !frag.fragments.containsKey(MemoryFragments.STONE_WHALE),
                "a full container accepted a fragment");
        check(f, frag.addFragments(MemoryFragments.PROLOGUE, -4) == 0, "a negative fragment count was accepted");
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
        to.discoveries.clear();
        to.discoveries.addAll(from.discoveries);
    }

    private static boolean same(GrowthData a, GrowthData b, boolean serverFields) {
        boolean ok = a.level == b.level && a.xp == b.xp && java.util.Arrays.equals(a.stats, b.stats) && a.bonusPoints == b.bonusPoints
                && a.fragments.equals(b.fragments) && a.fragmentCapacity == b.fragmentCapacity;
        if (serverFields) {
            ok &= a.discoveries.equals(b.discoveries);
        }
        return ok;
    }
}
