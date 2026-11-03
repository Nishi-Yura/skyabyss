package jp.revati.skyabyss.endgame;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

// 狩人の手帳 (HunterNotes): 数・頁の開き方・慣れの倍率・見た技・出る所・画面へ送る中身
public final class HunterNotesSelfTest {
    private HunterNotesSelfTest() {
    }

    public static void runSelfTest(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        FakePlayer p = FakePlayerFactory.get(server.overworld(), new GameProfile(UUID.fromString("7a0b1d00-0000-4000-8000-00000000a0c7"), "hunt_test"));
        HunterNotes.clearForTest(p);
        try {
            String foe = Codex.continentFoes().iterator().next();
            HunterNotes.Habitat h = HunterNotes.habitat(foe);
            check(f, !h.regions().isEmpty(), foe + " (a continent foe) has no habitat");
            check(f, HunterNotes.killsOf(p, foe) == 0 && HunterNotes.bonus(p, foe) == 1.0, "a fresh player already knows " + foe);
            CompoundTag before = entry(HunterNotes.snapshot(p), foe);
            check(f, before != null && !before.contains("regions") && !before.contains("hp"), "an unmet foe showed its pages");
            HunterNotes.addKill(p, foe, 30f, 4f);
            CompoundTag one = entry(HunterNotes.snapshot(p), foe);
            check(f, one != null && one.getInt("n") == 1 && one.contains("regions") && !one.contains("hp"), "one kill did not open only the habitat page");
            for (int i = 1; i < HunterNotes.STATS; i++) {
                HunterNotes.addKill(p, foe, 32f, 5f);
            }
            CompoundTag five = entry(HunterNotes.snapshot(p), foe);
            check(f, five != null && Math.abs(five.getFloat("hp") - 32f) < 0.01f && Math.abs(five.getFloat("atk") - 5f) < 0.01f, "five kills did not open the strength page with the last foe's numbers");
            check(f, HunterNotes.bonus(p, foe) == 1.0, "familiarity came before " + HunterNotes.FAMILIAR + " kills");
            for (int i = HunterNotes.STATS; i < HunterNotes.FAMILIAR; i++) {
                HunterNotes.addKill(p, foe, 32f, 5f);
            }
            check(f, HunterNotes.killsOf(p, foe) == HunterNotes.FAMILIAR && HunterNotes.bonus(p, foe) == HunterNotes.FAMILIAR_BONUS, "no familiarity at " + HunterNotes.FAMILIAR + " kills");
            // ボスの技は型ごとに一度だけ記す。図鑑にない型でも手帳に載る
            HunterNotes.sawMove(p, "skyabyss:ember_heart_test", "ember.slam");
            HunterNotes.sawMove(p, "skyabyss:ember_heart_test", "ember.slam");
            HunterNotes.sawMove(p, "skyabyss:ember_heart_test", "ember.rain");
            check(f, HunterNotes.movesOf(p, "skyabyss:ember_heart_test").equals(List.of("ember.slam", "ember.rain")), "seen moves were not kept once each: " + HunterNotes.movesOf(p, "skyabyss:ember_heart_test"));
            CompoundTag cue = entry(HunterNotes.snapshot(p), "skyabyss:ember_heart_test");
            check(f, cue != null && cue.getList("moves", Tag.TAG_STRING).size() == 2, "a boss seen only by its moves is missing from the notes");
        } finally {
            HunterNotes.clearForTest(p);
        }
        f.forEach(m -> SkyAbyss.LOGGER.error("SELFTEST hunter notes FAILED: {}", m));
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST hunter notes PASSED (kills per foe, pages at {}/{}/{}, familiarity x{}, boss moves once each, habitat from the spawn tables)",
                    HunterNotes.HABITAT, HunterNotes.STATS, HunterNotes.FAMILIAR, HunterNotes.FAMILIAR_BONUS);
        }
    }

    private static CompoundTag entry(CompoundTag snap, String id) {
        var l = snap.getList("foes", Tag.TAG_COMPOUND);
        for (int i = 0; i < l.size(); i++) {
            if (l.getCompound(i).getString("id").equals(id)) {
                return l.getCompound(i);
            }
        }
        return null;
    }

    private static void check(List<String> f, boolean ok, String msg) {
        if (!ok) {
            f.add(msg);
        }
    }
}
