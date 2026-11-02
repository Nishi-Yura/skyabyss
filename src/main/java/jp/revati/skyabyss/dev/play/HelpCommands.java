package jp.revati.skyabyss.dev.play;

import java.util.List;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

// /skyabyss help : 遊ぶ人なら誰でも使えるコマンドの一覧 (2026-11-02 の声「正式版なので、コマンド整理して管理者用も設定して」)。
// 一行ずつ押すと、その入力を入力欄に入れる。管理者 (権限の段を満たす人) には /skyabyss admin help の案内も出す。
// 権限は CommandGate が付ける (help は誰でも)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class HelpCommands {
    private HelpCommands() {
    }

    public static final String HELP = "help";

    // 一覧の並び: {打つ文, 説明の鍵の末尾}
    static final List<String[]> LINES = List.of(
            new String[] {"/party invite ", "party"},
            new String[] {"/guild info", "guild"},
            new String[] {"/trade", "trade"},
            new String[] {"/skyrecord", "record"},
            new String[] {"/growth", "growth"},
            new String[] {"/skill", "skill"},
            new String[] {"/skyabyss_codex", "codex"},
            new String[] {"/skyabyss_journal", "journal"},
            new String[] {"/skyabyss_fishing", "fishing"},
            new String[] {"/skyabyss_look clear", "look"},
            new String[] {"/skyabyss_arts", "arts"},
            new String[] {"/skyabyss_tower menu", "tower"},
            new String[] {"/skyabyss_circuit leave", "circuit"});

    @SubscribeEvent
    static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal(SkyAbyss.MODID).then(Commands.literal(HELP).executes(c -> help(c.getSource()))));
    }

    static int help(CommandSourceStack source) {
        source.sendSystemMessage(Component.translatable("commands.skyabyss.help.title").withStyle(ChatFormatting.AQUA));
        for (String[] line : LINES) {
            MutableComponent row = Component.literal("  " + line[0].trim()).withStyle(s -> s.withColor(0xF2C14E)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, line[0]))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable("commands.skyabyss.help.click"))));
            row.append(Component.literal("  ")).append(Component.translatable("commands.skyabyss.help." + line[1]).withStyle(ChatFormatting.GRAY));
            source.sendSystemMessage(row);
        }
        if (source.hasPermission(jp.revati.skyabyss.Config.adminLevel())) {
            source.sendSystemMessage(Component.translatable("commands.skyabyss.help.admin", "/skyabyss " + PlayCommands.ADMIN + " help")
                    .withStyle(s -> s.withColor(0x9FE8FF).withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/skyabyss " + PlayCommands.ADMIN + " help"))));
        }
        return 1;
    }
}
