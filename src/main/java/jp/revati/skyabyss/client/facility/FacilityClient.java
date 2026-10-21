package jp.revati.skyabyss.client.facility;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.client.map.MapClient;
import jp.revati.skyabyss.facility.FacilityNetwork;
import jp.revati.skyabyss.facility.guild.GuildBadge;
import jp.revati.skyabyss.facility.guild.Guilds;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderNameTagEvent;

// 都の施設のクライアント側の受け口 (店・ギルドの画面を開く / 中身を替える / 閉じる、地図を開く) と、名札のギルドの印
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class FacilityClient {
    private FacilityClient() {
    }

    public static void shop(FacilityNetwork.ShopView view) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof ShopScreen s && s.shop().equals(view.shop())) {
            s.update(view);
        } else {
            mc.setScreen(new ShopScreen(view));
        }
    }

    public static void guild(FacilityNetwork.GuildView view) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof GuildScreen s) {
            s.update(view);
        } else {
            mc.setScreen(new GuildScreen(view));
        }
    }

    public static void records(jp.revati.skyabyss.facility.KeepRecords.View view) {
        Minecraft.getInstance().setScreen(new RecordsScreen(view));
    }

    public static void close() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof ShopScreen s) {
            s.closeFromServer();
        } else if (mc.screen instanceof GuildScreen) {
            mc.setScreen(null);
        }
    }

    public static void openMap() {
        MapClient.open();
    }

    // 名札: 名前の頭に「[印]」(旗の色)。転生の飾り (RebirthClient) のあとでも前でも、頭に足すだけ
    @SubscribeEvent
    static void onNameTag(RenderNameTagEvent event) {
        if (!(event.getEntity() instanceof Player p)) {
            return;
        }
        GuildBadge b = Guilds.badge(p);
        if (!b.present()) {
            return;
        }
        MutableComponent out = Component.empty();
        out.append(Guilds.prefix(b));
        out.append(event.getContent());
        event.setContent(out);
    }
}
