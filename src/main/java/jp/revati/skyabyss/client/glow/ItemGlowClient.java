package jp.revati.skyabyss.client.glow;

import java.util.HashMap;
import java.util.Map;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.glow.ItemGlow;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

// 自分が拾える落とし物だけ、自分の画面で光らせる。サーバーから来た id の実体に、このクライアントのコピーだけ発光の印を付け、
// 色は画面内だけのチーム (頁 = 黄、守りの品 = 水色) で決める。サーバーには何も返らないので他の人には見えない。
// 落ちている間は小さな光の粒を引く
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class ItemGlowClient {
    private static final Map<Integer, Byte> TRACKED = new HashMap<>();
    private static final String TEAM_PAGE = "skyabyss_glow_page";
    private static final String TEAM_KEEPSAKE = "skyabyss_glow_keepsake";

    private ItemGlowClient() {
    }

    public static void receive(ItemGlow.Sync payload) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            Map<Integer, Byte> next = new HashMap<>();
            for (int i = 0; i < payload.ids().length; i++) {
                next.put(payload.ids()[i], payload.kinds()[i]);
            }
            for (Integer id : TRACKED.keySet()) {
                if (!next.containsKey(id)) {
                    release(mc, id);
                }
            }
            TRACKED.clear();
            TRACKED.putAll(next);
        });
    }

    public static boolean isGlowing(int entityId) {
        return TRACKED.containsKey(entityId);
    }

    private static void release(Minecraft mc, int id) {
        if (mc.level != null && mc.level.getEntity(id) instanceof ItemEntity item) {
            item.setGlowingTag(false);
            mc.level.getScoreboard().removePlayerFromTeam(item.getScoreboardName());
        }
    }

    private static PlayerTeam team(Scoreboard board, String name, ChatFormatting color) {
        PlayerTeam team = board.getPlayerTeam(name);
        if (team == null) {
            team = board.addPlayerTeam(name);
            team.setColor(color);
        }
        return team;
    }

    @SubscribeEvent
    static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || TRACKED.isEmpty()) {
            return;
        }
        Scoreboard board = mc.level.getScoreboard();
        PlayerTeam page = team(board, TEAM_PAGE, ChatFormatting.YELLOW);
        PlayerTeam keep = team(board, TEAM_KEEPSAKE, ChatFormatting.AQUA);
        boolean puff = mc.level.getGameTime() % 4 == 0;
        for (Map.Entry<Integer, Byte> e : TRACKED.entrySet()) {
            Entity entity = mc.level.getEntity(e.getKey());
            if (!(entity instanceof ItemEntity item) || item.isRemoved()) {
                continue;
            }
            item.setGlowingTag(true);
            PlayerTeam want = e.getValue() == ItemGlow.KEEPSAKE ? keep : page;
            if (board.getPlayersTeam(item.getScoreboardName()) != want) {
                board.addPlayerToTeam(item.getScoreboardName(), want);
            }
            if (puff && !item.onGround()) {
                mc.level.addParticle(ParticleTypes.END_ROD, item.getX(), item.getY() + 0.2, item.getZ(), 0, 0.02, 0);
            }
        }
    }

    @SubscribeEvent
    static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        TRACKED.clear();
    }
}
