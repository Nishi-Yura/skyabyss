package jp.revati.skyabyss.rebirth;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import jp.revati.skyabyss.SkyAbyss;

// 名前の飾り (サーバー側。C14 チャットの接頭辞・C19 Tab の色)。
// NeoForge の PlayerEvent.NameFormat / TabListNameFormat に、ほかの Mod が付けた名前の「前に足す」だけ (置き換えない。順は後ろ = LOW)。
// チャット・倒れた知らせ・Tab は、この名前をそのまま使う (チャットの形式そのものは変えない)。クライアントでは何もしない (名札は client 側の別の仕組み)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class RebirthNames {
    private RebirthNames() {
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    static void onNameFormat(PlayerEvent.NameFormat event) {
        Player p = event.getEntity();
        if (p.level().isClientSide || !RebirthConfig.on(RebirthConfig.CHAT_PREFIX)) {
            return;
        }
        int n = RebirthView.count(p);
        if (n > 0) {
            event.setDisplayname(prefixed(n, event.getDisplayname()));
        }
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    static void onTabName(PlayerEvent.TabListNameFormat event) {
        Player p = event.getEntity();
        if (p.level().isClientSide) {
            return;
        }
        int n = RebirthView.count(p);
        if (n > 0) {
            event.setDisplayName(tabName(n, event.getDisplayName() != null ? event.getDisplayName() : p.getName()));
        }
    }

    // 「✦3 名前」 (接頭辞は段の色、名前は元のまま)
    public static MutableComponent prefixed(int count, Component name) {
        return RebirthText.chatPrefix(count).append(name);
    }

    // Tab: 記号と名前を段の色で (プリズムは止めた並び。Tab は動かせない)
    public static MutableComponent tabName(int count, Component name) {
        int rgb = RebirthTier.color(count, 0L, true);
        MutableComponent out = RebirthText.symbols(count, 0L, true).append(Component.literal(" "));
        out.append(name.copy().withStyle(Style.EMPTY.withColor(TextColor.fromRgb(rgb))));
        return out;
    }
}
