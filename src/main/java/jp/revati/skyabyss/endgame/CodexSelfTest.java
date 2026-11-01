package jp.revati.skyabyss.endgame;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.facility.Coins;
import jp.revati.skyabyss.item.TitanGearSet;
import jp.revati.skyabyss.item.TitanGearSets;
import jp.revati.skyabyss.loot.TitanMemoryData;
import jp.revati.skyabyss.rebirth.RebirthTitles;
import jp.revati.skyabyss.rebirth.Rebirths;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

// 図鑑の完成の報い (Codex) の自己テスト。SKYABYSS_SELFTEST_ONLY="codex"
//   表のある巨神の数 / 一つの巨神の表をそろえると鐘銭と称号 (一度だけ) / 敵の図鑑 (数える敵・倒すと記す・すべてで報い)
public final class CodexSelfTest {
    private CodexSelfTest() {
    }

    private static void check(List<String> f, boolean ok, String what) {
        if (!ok) {
            f.add(what);
        }
    }

    public static void run(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        FakePlayer p = FakePlayerFactory.get(server.overworld(), new GameProfile(UUID.fromString("7a0b1d00-0000-4000-8000-0000000c0d01"), "codex_test"));
        TitanMemoryData mem = TitanMemoryData.get(server);
        TitanGearSet whale = TitanGearSets.get("stone_whale");
        try {
            Codex.clearForTest(p);
            mem.record(p.getUUID(), "stone_whale").owned.clear();
            int sets = 0;
            for (TitanGearSet s : TitanGearSets.all()) {
                if (!Codex.pieces(s).isEmpty()) {
                    sets++;
                }
            }
            check(f, sets >= 6, "fewer than six titans have a gear codex: " + sets);
            check(f, whale != null && Codex.pieces(whale).size() >= 10, "the stone whale codex is too small");
            check(f, !Codex.setDone(p, whale), "an empty record counts as a complete codex");
            int before = Coins.count(p);
            mem.record(p.getUUID(), "stone_whale").owned.addAll(Codex.pieces(whale));
            Codex.check(p);
            Codex.check(p);
            check(f, Codex.setDone(p, whale) && Coins.count(p) - before == Codex.SET_COINS, "completing the whale codex paid " + (Coins.count(p) - before));
            long mask = RebirthTitles.unlockedMask(Rebirths.facts(p));
            check(f, RebirthTitles.has(mask, "codex_stone_whale") && !RebirthTitles.has(mask, "codex_all"), "the codex titles are wrong");

            var foes = Codex.continentFoes();
            check(f, foes.size() >= 12 && foes.stream().allMatch(s -> s.startsWith(SkyAbyss.MODID + ":")), "the foe codex is too small: " + foes.size());
            check(f, Codex.foes().size() > foes.size() + 40 && Codex.foes().containsAll(foes), "the full foe codex should hold every MONSTER type: " + Codex.foes().size());
            check(f, Codex.defeat(p, foes.iterator().next()) && !Codex.defeat(p, foes.iterator().next()), "defeating a foe twice counted twice");
            before = Coins.count(p);
            for (String e : foes) {
                Codex.defeat(p, e);
            }
            check(f, Codex.continentDefeated(p) == foes.size() && Coins.count(p) - before == Codex.FOES_COINS, "the full foe codex did not pay");
            check(f, RebirthTitles.has(RebirthTitles.unlockedMask(Rebirths.facts(p)), "foes_all"), "the foe codex title is missing");
        } catch (RuntimeException ex) {
            f.add("codex test crashed: " + ex);
            SkyAbyss.LOGGER.error("codex test", ex);
        } finally {
            Codex.clearForTest(p);
            mem.record(p.getUUID(), "stone_whale").owned.clear();
        }
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST codex PASSED (gear codex sets, completion pays once, titles, foe codex)");
        } else {
            f.forEach(m -> SkyAbyss.LOGGER.error("SELFTEST codex FAILED: {}", m));
        }
    }
}
