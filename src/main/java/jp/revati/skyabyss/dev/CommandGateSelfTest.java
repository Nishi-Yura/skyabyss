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
                .then(Commands.literal("play").then(Commands.literal("help").executes(c -> 1)))
                .then(Commands.literal("playtest").executes(c -> 1))
                .then(Commands.literal("titan").then(Commands.literal("board").executes(c -> 1))));
        d.register(Commands.literal("titanmemory").executes(c -> 1));
        d.register(Commands.literal("party").executes(c -> 1));
        int hidden = CommandGate.apply(d);
        CommandSourceStack op = server.createCommandSourceStack().withPermission(4).withSuppressedOutput();
        if (hidden != 3) {
            f.add("expected 3 hidden nodes, got " + hidden);
        }
        if (run(d, "skyabyss play help", op) != 1) {
            f.add("play should stay usable");
        }
        if (run(d, "party", op) != 1) {
            f.add("party should stay usable");
        }
        if (run(d, "skyabyss playtest", op) != -1 || run(d, "titanmemory", op) != -1) {
            f.add("playtest and titanmemory should be hidden from players");
        }
        if (CommandGate.inside(() -> run(d, "skyabyss titan board", op)) != 1) {
            f.add("inside calls (Playtest.run) should still reach hidden commands");
        }
        if (run(d, "skyabyss titan board", op) != -1) {
            f.add("titan board should be hidden outside inside()");
        }
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST command gate PASSED (play and party shown, playtest/titan/titanmemory hidden, inside calls pass)");
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
