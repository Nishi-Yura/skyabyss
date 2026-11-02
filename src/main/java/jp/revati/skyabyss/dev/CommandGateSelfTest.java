package jp.revati.skyabyss.dev;

import java.util.ArrayList;
import java.util.List;

import com.mojang.brigadier.CommandDispatcher;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.MinecraftServer;

// 製品版でコマンドを絞る仕組み (CommandGate) を、別の dispatcher で確かめる。SKYABYSS_SELFTEST_ONLY="command gate"
public final class CommandGateSelfTest {
    private CommandGateSelfTest() {
    }

    public static void runSelfTest(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        CommandDispatcher<CommandSourceStack> d = new CommandDispatcher<>();
        d.register(Commands.literal(SkyAbyss.MODID).requires(s -> s.hasPermission(2))
                .then(Commands.literal("admin").then(Commands.literal("help").executes(c -> 1)))
                .then(Commands.literal("play").then(Commands.literal("help").executes(c -> 1)))
                .then(Commands.literal("playtest").executes(c -> 1))
                .then(Commands.literal("titan").then(Commands.literal("board").executes(c -> 1))));
        d.register(Commands.literal(SkyAbyss.MODID).then(Commands.literal("help").executes(c -> 1)));
        d.register(Commands.literal("titanmemory").executes(c -> 1));
        d.register(Commands.literal("party").executes(c -> 1));
        int hidden = CommandGate.apply(d, true);
        CommandSourceStack op = server.createCommandSourceStack().withPermission(4).withSuppressedOutput();
        CommandSourceStack admin = server.createCommandSourceStack().withPermission(jp.revati.skyabyss.Config.adminLevel()).withSuppressedOutput();
        CommandSourceStack player = server.createCommandSourceStack().withPermission(0).withSuppressedOutput();
        // 隠すのは play・playtest・titan・titanmemory
        if (hidden != 4) {
            f.add("expected 4 hidden nodes, got " + hidden);
        }
        if (run(d, "skyabyss help", player) != 1) {
            f.add("help should be usable by every player");
        }
        if (run(d, "skyabyss admin help", admin) != 1 || run(d, "skyabyss admin help", op) != 1) {
            f.add("admin should be usable at the admin level");
        }
        if (run(d, "skyabyss admin help", player) != -1) {
            f.add("admin should be refused to a plain player");
        }
        if (run(d, "skyabyss play help", op) != -1) {
            f.add("the play alias should be hidden in the product");
        }
        if (run(d, "party", player) != 1) {
            f.add("party should stay usable");
        }
        if (run(d, "skyabyss playtest", op) != -1 || run(d, "titanmemory", op) != -1 || run(d, "skyabyss playtest", player) != -1) {
            f.add("playtest and titanmemory should be hidden from players");
        }
        if (CommandGate.inside(() -> run(d, "skyabyss titan board", op)) != 1) {
            f.add("inside calls (Playtest.run) should still reach hidden commands");
        }
        if (run(d, "skyabyss titan board", op) != -1) {
            f.add("titan board should be hidden outside inside()");
        }
        // 開発環境 (隠さない): 開発の節は権限 2、help は誰でも
        CommandDispatcher<CommandSourceStack> dev = new CommandDispatcher<>();
        dev.register(Commands.literal(SkyAbyss.MODID).requires(s -> s.hasPermission(2)).then(Commands.literal("titan").executes(c -> 1)));
        dev.register(Commands.literal(SkyAbyss.MODID).then(Commands.literal("help").executes(c -> 1)));
        CommandGate.apply(dev, false);
        if (run(dev, "skyabyss titan", op) != 1 || run(dev, "skyabyss titan", player) != -1 || run(dev, "skyabyss help", player) != 1) {
            f.add("in development the dev nodes should need permission 2 and help should be open");
        }
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST command gate PASSED (help for everyone, admin at the admin level, play/playtest/titan/titanmemory hidden in the product, inside calls pass)");
        } else {
            f.forEach(s -> SkyAbyss.LOGGER.error("SELFTEST command gate FAILED: {}", s));
        }
    }

    private static int run(CommandDispatcher<CommandSourceStack> d, String cmd, CommandSourceStack s) {
        try {
            return d.execute(cmd, s);
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            return -1;
        }
    }
}
