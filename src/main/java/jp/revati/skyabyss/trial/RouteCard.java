package jp.revati.skyabyss.trial;

import java.util.List;
import java.util.UUID;

import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.trial.Trial.Def;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

// 段の札 (docs/design/32 の 6 章): 段が始まるとき (入口の休み場へ運ばれたとき)、上の帯に試練の名前・枠と、段の星・稀さ・修飾のチップを出す。
// 周回は「〇周目」を添える。中ボスの段は中ボスの名前、心臓の段は「心臓へ」
public final class RouteCard {
    private RouteCard() {
    }

    public static void show(ServerPlayer p, ServerLevel back, UUID group, String titan, TrialDraw.Draw draw, int step, boolean mid) {
        Notice.place(p, title(titan, draw, step, mid), subtitle(draw, step, mid));
    }

    public static Component title(String titan, TrialDraw.Draw draw, int step, boolean mid) {
        if (mid) {
            return Component.translatable("message.skyabyss.route.card_mid", TrialMidBoss.name(titan));
        }
        if (step >= TrialRoute.LAST_STEP) {
            return Component.translatable("message.skyabyss.route.card_heart");
        }
        Def d = TrialRoute.defOf(draw, step);
        if (d == null) {
            return Component.empty();
        }
        return Component.translatable("message.skyabyss.route.card_step" + step, Component.translatable(d.nameKey()),
                Component.translatable("trial.skyabyss.slot." + d.slot().id));
    }

    public static Component subtitle(TrialDraw.Draw draw, int step, boolean mid) {
        MutableComponent out = Component.empty();
        if (!mid && step < TrialRoute.LAST_STEP) {
            TrialDraw.Pick pick = step >= 1 && step <= draw.picks().size() ? draw.picks().get(step - 1) : null;
            Def d = pick == null ? null : pick.def();
            if (d != null) {
                out.append(TrialChips.stars(d.tier()));
                if (pick.mods().rarity() != Trial.Rarity.COMMON) {
                    out.append("　").append(Component.translatable("trial.skyabyss.rarity." + pick.mods().rarity().id()));
                }
                // 入った段の札なので、祠の画面で「？」だった極もここで分かる
                List<TrialBoardNet.Chip> chips = TrialBoard.chips(d, pick.mods(), false);
                int n = 0;
                for (TrialBoardNet.Chip c : chips) {
                    if (n >= 4) {
                        break;
                    }
                    out.append(n == 0 ? "　" : "・").append(TrialChips.text(c));
                    n++;
                }
            }
        }
        if (draw.cycle() >= 2) {
            out.append("　").append(Component.translatable("message.skyabyss.route.card_cycle", draw.cycle()));
        }
        return out;
    }
}
