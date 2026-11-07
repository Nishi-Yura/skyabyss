package jp.revati.skyabyss.client;

import java.util.List;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.ChatFormatting;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

// この mod の品の説明の行 (lang の <品の鍵>.desc と .lore) を、品のクラスが自分で出していないときに名前の下へ足す。
// ただの Item で登録した素材・食べ物・道具は説明を出す仕組みを持たず、lang に文があっても見えなかった
// (2026-11-03、公式サイトの wiki を作っていて、説明が名前だけの品が 30 あった)。
// 品のクラスが同じ文を出しているとき (折り返していても、頭の 8 字が見つかれば) は足さない
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class ItemDescTooltip {
    private ItemDescTooltip() {
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    static void onTooltip(ItemTooltipEvent event) {
        Item item = event.getItemStack().getItem();
        if (!BuiltInRegistries.ITEM.getKey(item).getNamespace().startsWith(SkyAbyss.MODID)) {
            return;
        }
        List<Component> tip = event.getToolTip();
        if (tip.isEmpty()) {
            return;
        }
        int at = 1;
        // ブロックの品 (月の錘など) の名前の鍵は block.… なので、item.<名前空間>.<名前> の説明も探す
        net.minecraft.resources.ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        String itemKey = "item." + id.getNamespace() + "." + id.getPath();
        for (String suffix : new String[] {".desc", ".lore"}) {
            String base = I18n.exists(item.getDescriptionId() + suffix) ? item.getDescriptionId() : itemKey;
            if (!I18n.exists(base + suffix)) {
                continue;
            }
            // I18n.get は String.format を通すので、「+15% 速さ」のような % を含む説明が「Format error: …」になっていた
            // (2026-11-06 の声)。訳文をそのまま読み、%% だけを % に戻す
            String text = net.minecraft.locale.Language.getInstance().getOrDefault(base + suffix).replace("%%", "%");
            String head = text.substring(0, Math.min(8, text.length()));
            if (tip.stream().anyMatch(c -> c.getString().contains(head))) {
                continue;
            }
            for (String line : text.split("\n")) {
                tip.add(Math.min(at, tip.size()), suffix.equals(".lore")
                        ? Component.literal(line).withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC)
                        : Component.literal(line).withStyle(ChatFormatting.GRAY));
                at++;
            }
        }
    }
}
