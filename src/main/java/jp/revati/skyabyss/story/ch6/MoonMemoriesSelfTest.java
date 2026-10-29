package jp.revati.skyabyss.story.ch6;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.lore.Lore;
import jp.revati.skyabyss.lore.LoreManager;
import jp.revati.skyabyss.rpg.skill.Rune;
import jp.revati.skyabyss.story.ch6.core.CoreArena;
import jp.revati.skyabyss.story.ch6.field.FieldArena;
import jp.revati.skyabyss.story.ch6.field.FieldTide;
import jp.revati.skyabyss.story.ch6.mob.MoonMob;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.common.util.FakePlayer;

// 記憶の断片と位相の挑戦の段の自己テスト (docs/design/46 の 4.8・4.9)。段: "moon memories" (先に "moon (load)")
//   伝承の頁 19 がある / 記憶の石が区画ごとに立ち、横に立てる / 前の自分の話・石・主で断片がそろい、十二で見る者の頁 /
//   段 3 で技の間が三分の二・段 5 で位相の重ね (野の満ち引きが速い) / 段 5 を越えると記念の札 / 月のヒロイックの書き換え 6
public final class MoonMemoriesSelfTest {
    private MoonMemoriesSelfTest() {
    }

    private static void check(List<String> f, boolean ok, String what) {
        if (!ok) {
            f.add(what);
        }
    }

    public static void run(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        List<Entity> spawned = new ArrayList<>();
        ServerLevel moon = server.getLevel(MoonWorld.LEVEL);
        try {
            check(f, moon != null, "the moon dimension is missing");
            if (moon == null) {
                report(f);
                return;
            }
            // 頁
            for (int i = 1; i <= MoonMemories.COUNT; i++) {
                check(f, LoreManager.catalog().entry("ch6/memory_" + i) != null, "lore page ch6/memory_" + i + " is missing");
            }
            for (String id : List.of(MoonMemories.TRUTH, MoonMemories.CORE, "ch6/keepsake_valley", "ch6/keepsake_core")) {
                check(f, LoreManager.catalog().entry(id) != null && LoreManager.catalog().entry(id).unlockable(), "lore page " + id + " is missing");
            }
            // 記憶の石
            for (int s = 0; s < MoonMemories.SECTIONS.size(); s++) {
                BlockPos at = MoonMemories.stoneAt(moon, s);
                check(f, at != null, "no memory stone in " + MoonMemories.SECTIONS.get(s));
                if (at != null) {
                    check(f, MoonMemories.standable(moon, at.north()), "nobody can stand by the memory stone in " + MoonMemories.SECTIONS.get(s) + " at " + at);
                }
            }
            // 断片: 話す・石・主
            FakePlayer p = new FakePlayer(moon, new GameProfile(UUID.randomUUID(), "moon_memories"));
            MoonMemories.onTalk(p, "ch6_valley_meet3");
            check(f, Lore.state(p).has("ch6/memory_1") && MoonMemories.count(p) == 1, "talking to the first past self gave no memory");
            MoonMemories.onTalk(p, "ch6_valley_meet2");
            check(f, MoonMemories.count(p) == 1, "a different node gave a memory");
            for (int s = 0; s < 4; s++) {
                MoonMemories.onStone(p, s);
            }
            MoonMemories.onLord(List.<ServerPlayer>of(p), "valley");
            MoonMemories.onLord(List.<ServerPlayer>of(p), "core");
            check(f, MoonMemories.count(p) == 6 && Lore.state(p).has("ch6/memory_3"), "stones and the valley lord should make six memories: " + MoonMemories.count(p));
            check(f, !Lore.state(p).has(MoonMemories.TRUTH), "the watcher page came before twelve memories");
            for (String sec : List.of("cliff", "field", "rift")) {
                MoonMemories.onTalk(p, "ch6_" + sec + "_meet3");
                MoonMemories.onLord(List.<ServerPlayer>of(p), sec);
            }
            check(f, MoonMemories.count(p) == 12 && Lore.state(p).has(MoonMemories.TRUTH), "twelve memories did not open the watcher page: " + MoonMemories.count(p));
            MoonMemories.onCore(p);
            check(f, Lore.state(p).has(MoonMemories.CORE), "the core page did not open");
            // 段 3・段 5 (主を呼んで段を付ける)
            CoreArena.clear(moon);
            FieldArena.lords(moon).forEach(Entity::discard);
            FieldTide.setFast(false);
            MoonMob keeper = MoonTrial.start(p, "field", 3);
            check(f, keeper != null, "the field trial did not summon the keeper");
            if (keeper != null) {
                spawned.add(keeper);
                check(f, keeper.trialTier() == 3 && keeper.paced(60) == 40 && !keeper.overlaid(), "tier 3 should pace 60 -> 40 without the overlay");
                keeper.discard();
            }
            FieldArena.lords(moon).forEach(Entity::discard);
            MoonMob keeper5 = MoonTrial.start(p, "field", 5);
            if (keeper5 != null) {
                spawned.add(keeper5);
                check(f, keeper5.overlaid(), "tier 5 should overlay the phases");
                keeper5.tick();
                check(f, FieldTide.fast(), "the field overlay did not speed up the tide");
                keeper5.kill();
                MoonTrial.finish(moon, keeper5, "field", List.<ServerPlayer>of(p));
                check(f, !FieldTide.fast(), "the tide stayed fast after the keeper fell");
                check(f, Lore.state(p).has("ch6/keepsake_field") && MoonTrial.best(p, "field") == 5, "a tier 5 clear gave no keepsake");
            } else {
                f.add("the tier 5 field trial did not summon the keeper");
            }
            p.removeTag(FieldArena.TAG);
            // 月のヒロイックの書き換え
            for (String w : List.of("moon_waning_blade", "moon_full_hammer", "moon_new_staff", "moon_starfall_bow", "moon_umbra_dagger", "moon_halo_bell_staff")) {
                check(f, java.util.Arrays.stream(Rune.values()).filter(r -> r.weapon.equals(w)).count() == 1, "moon heroic " + w + " has no skill rewrite");
            }
        } catch (Exception e) {
            SkyAbyss.LOGGER.error("moon memories selftest crashed", e);
            f.add("crashed: " + e);
        } finally {
            spawned.forEach(Entity::discard);
            FieldTide.setFast(false);
        }
        report(f);
    }

    private static void report(List<String> f) {
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST moon memories PASSED (19 lore pages, memory stones and standing room, talk/stone/lord memories, watcher page at twelve, core page, tier 3 pace, tier 5 overlay and keepsake, heroic rewrites)");
        } else {
            f.forEach(x -> SkyAbyss.LOGGER.error("SELFTEST moon memories FAILED: {}", x));
        }
    }
}
