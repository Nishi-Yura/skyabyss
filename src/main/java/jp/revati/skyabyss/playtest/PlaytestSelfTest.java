package jp.revati.skyabyss.playtest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.tree.CommandNode;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.titan.TitanTravel;
import jp.revati.skyabyss.titan.dungeon.TitanBackTrials;
import jp.revati.skyabyss.titan.group.GroupProgress;
import jp.revati.skyabyss.titan.group.Groups;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.neoforged.neoforge.common.util.FakePlayer;

// 試遊コマンドの自己テスト (SKYABYSS_SELFTEST=1、SKYABYSS_SELFTEST_ONLY=playtest で単独に): 登録・装備・試練・乗る・移る
public final class PlaytestSelfTest {
    private PlaytestSelfTest() {
    }

    public static void runSelfTest(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        try {
            run(server, f);
        } catch (Exception e) {
            SkyAbyss.LOGGER.error("playtest selftest crashed", e);
            f.add("crashed: " + e);
        }
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST playtest PASSED (commands registered at permission 2, gear, trials in every back, board, goto)");
        } else {
            f.forEach(x -> SkyAbyss.LOGGER.error("SELFTEST playtest FAILED: {}", x));
        }
    }

    private static void run(MinecraftServer server, List<String> f) {
        var root = server.getCommands().getDispatcher().getRoot();
        for (String path : List.of("playtest help", "playtest skip opening", "playtest whale", "playtest board", "playtest goto", "playtest trials", "playtest gear", "playtest chapter", "playtest heal", "playtest day", "playtest fast", "playtest reset", "playtest god", "playtest now", "playtest next")) {
            CommandNode<CommandSourceStack> node = root.getChild("skyabyss");
            for (String part : path.split(" ")) {
                node = node == null ? null : node.getChild(part);
            }
            if (node == null) {
                f.add("not registered: " + path);
            }
        }
        CommandNode<CommandSourceStack> sky = root.getChild("skyabyss");
        if (sky.getRequirement().test(server.createCommandSourceStack().withPermission(1))
                || !sky.getRequirement().test(server.createCommandSourceStack().withPermission(2))) {
            f.add("skyabyss commands are not exactly permission 2");
        }
        var disp = server.getCommands().getDispatcher();
        CommandSourceStack op = server.createCommandSourceStack().withPermission(4).withSuppressedOutput();
        for (String c : List.of("skyabyss playtest help", "skyabyss playtest skip opening", "skyabyss playtest board whale", "skyabyss playtest goto whale heart",
                "skyabyss playtest trials tide", "skyabyss playtest gear 2", "skyabyss playtest chapter 2", "skyabyss playtest heal", "skyabyss playtest day 1",
                "skyabyss playtest fast off", "skyabyss playtest god off", "skyabyss playtest reset", "skyabyss playtest now", "skyabyss playtest next")) {
            var parsed = disp.parse(c, op);
            if (parsed.getReader().canRead() || !parsed.getExceptions().isEmpty()) {
                f.add("does not parse: " + c);
            }
        }
        // 実行 (サーバーそのものが実行者なので、自分向きでない・対象なしの物だけ)
        for (String c : List.of("skyabyss playtest help", "skyabyss playtest day 1", "skyabyss playtest fast off")) {
            try {
                if (disp.execute(c, op) < 1) {
                    f.add("did not run: " + c);
                }
            } catch (Exception e) {
                f.add("threw: " + c + " " + e.getMessage());
            }
        }
        ServerLevel level = server.overworld();
        FakePlayer a = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "pt_a"));
        FakePlayer b = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "pt_b"));
        for (FakePlayer p : List.of(a, b)) {
            p.moveTo(level.getSharedSpawnPos().getX() + 0.5, level.getSharedSpawnPos().getY() + 1, level.getSharedSpawnPos().getZ() + 0.5);
        }
        Playtest.gear(a, 1);
        if (a.getItemBySlot(EquipmentSlot.CHEST).isEmpty()) {
            f.add("gear 1 left the chest empty");
        }
        if (a.getMaxHealth() < 29.9f) {
            f.add("gear 1 max health " + a.getMaxHealth());
        }
        Playtest.gear(a, 0);
        if (a.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) {
            f.add("gear 0 left the head empty");
        }
        for (int idx = 0; idx < 4; idx++) {
            if (!Playtest.trials(server, Groups.keyOf(a), idx)) {
                f.add("trials failed for titan " + idx);
            }
            List<String> spots = Playtest.spots(idx);
            if (!spots.contains("heart") || !spots.contains("hub")) {
                f.add("titan " + idx + " has no heart/hub spot: " + spots);
            }
        }
        ServerLevel back = server.getLevel(TitanTravel.TITAN_BACK);
        if (back != null && !GroupProgress.of(back, Groups.keyOf(a)).flag(TitanBackTrials.STONE_FLAG + 0)) {
            f.add("whale stone 0 not marked");
        }
        // 乗る・移る (岩鯨)
        // 偽のプレイヤーは接続が無く別の世界へ移れないので、呼ぶ・降ろすところまでを見る
        Playtest.board(a, 0, pp -> {
        });
        String key = Playtest.now(b, false);
        if (key == null || !key.startsWith("commands.skyabyss.playtest.now.")) {
            f.add("now gave " + key);
        }
        Playtest.god(b, true);
        if (!b.getAbilities().invulnerable) {
            f.add("god on did not stick");
        }
        Playtest.god(b, false);
        if (b.getAbilities().invulnerable) {
            f.add("god off did not stick");
        }
    }
}
