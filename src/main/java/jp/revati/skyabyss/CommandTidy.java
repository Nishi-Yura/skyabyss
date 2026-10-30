package jp.revati.skyabyss;

import com.mojang.brigadier.ParseResults;

import net.minecraft.commands.CommandSourceStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.CommandEvent;

// この作品のコマンド (/skyabyss…・/skyabyss_…) の打ち間違いを整える (2026-10-30 の声「コマンドで雲いけないよ」)。
// 後ろに空白が残る・日本語入力の全角の空白が混ざると、Minecraft のコマンドは「引数が正しくありません」で止まる。
// 全角の空白を半角に、続く空白を一つに、前後の空白を取って読み直す (読み直して通るときだけ差し替える)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class CommandTidy {
    private CommandTidy() {
    }

    // 整えた文 (この作品のコマンドでなければ、そのまま)
    public static String tidy(String input) {
        if (!input.startsWith(SkyAbyss.MODID)) {
            return input;
        }
        return input.replace('　', ' ').replaceAll(" {2,}", " ").strip();
    }

    @SubscribeEvent
    static void onCommand(CommandEvent event) {
        ParseResults<CommandSourceStack> parse = event.getParseResults();
        String input = parse.getReader().getString();
        String tidy = tidy(input);
        if (tidy.equals(input) || !parse.getReader().canRead() && parse.getExceptions().isEmpty()) {
            return;
        }
        ParseResults<CommandSourceStack> again = parse.getContext().getDispatcher().parse(tidy, parse.getContext().getSource());
        if (!again.getReader().canRead() && again.getExceptions().isEmpty()) {
            event.setParseResults(again);
        }
    }
}
