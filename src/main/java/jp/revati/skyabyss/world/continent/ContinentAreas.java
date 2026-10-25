package jp.revati.skyabyss.world.continent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

// 土地と町に入ったときの名前の札 (Wynncraft の地域の名前。docs/design/40 の 6 章)。
// 町 (中心から TOWN_IN 以内。出るのは TOWN_OUT より外) > 鐘の都 (CAPITAL_IN / CAPITAL_OUT) > 土地 (ContinentMap の土地の番号)。
// 境目で何度も出ないよう、同じ読みが STABLE 回続いたら変わったとみなす。入ったばかり (ログイン) は今いる所を覚えるだけで出さない
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class ContinentAreas {
    static final int CHECK = 20;
    static final int STABLE = 2;
    static final int TOWN_IN = 45;
    static final int TOWN_OUT = 60;
    static final int CAPITAL_IN = 170;
    static final int CAPITAL_OUT = 200;
    // 土地の番号 (ContinentMap) → 名前の鍵と目安のレベル
    static final String[] REGION_KEYS = {"meadow", "outskirts", "whale", "tide", "storm", "ember"};
    static final String[] REGION_LEVELS = {"1-4", "1", "4-12", "12-18", "18-24", "24-30"};
    static final String[] TOWNS = {"whale", "tide", "storm", "ember"};

    private record Seen(String area, String pending, int count) {
    }

    private static final Map<UUID, Seen> SEEN = new HashMap<>();

    private ContinentAreas() {
    }

    // いまいる所の名前 ("town:whale"・"capital"・"region:2")。前にいた所で出入りの幅を変える
    static String area(int x, int z, String before) {
        ContinentLayout lay = ContinentLayout.get();
        for (String t : TOWNS) {
            BlockPos c = lay.town(t);
            if (c != null) {
                double d = Math.hypot(x - c.getX(), z - c.getZ());
                if (d <= TOWN_IN || d <= TOWN_OUT && ("town:" + t).equals(before)) {
                    return "town:" + t;
                }
            }
        }
        BlockPos cap = lay.capital();
        double dc = Math.hypot(x - cap.getX(), z - cap.getZ());
        if (dc <= CAPITAL_IN || dc <= CAPITAL_OUT && "capital".equals(before)) {
            return "capital";
        }
        return "region:" + ContinentMap.get().region(x, z);
    }

    @SubscribeEvent
    static void onTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || p.isSpectator() || p.level().dimension() != Level.OVERWORLD
                || (p.tickCount + p.getId()) % CHECK != 0 || !ContinentPois.in(p.level())) {
            return;
        }
        Seen s = SEEN.get(p.getUUID());
        String now = area(p.getBlockX(), p.getBlockZ(), s == null ? "" : s.area());
        if (s == null) {
            SEEN.put(p.getUUID(), new Seen(now, now, 0));
            return;
        }
        if (now.equals(s.area())) {
            if (!now.equals(s.pending())) {
                SEEN.put(p.getUUID(), new Seen(s.area(), now, 0));
            }
            return;
        }
        int count = now.equals(s.pending()) ? s.count() + 1 : 1;
        if (count < STABLE) {
            SEEN.put(p.getUUID(), new Seen(s.area(), now, count));
            return;
        }
        SEEN.put(p.getUUID(), new Seen(now, now, 0));
        announce(p, now);
    }

    // 行ったことのある土地の町 (倒れても残る。util/LifeKeep)。地図の跡地の噂は、その町に着いたら本当の位置になる (MapMarks)
    public static final String TOWNS_TAG = "skyabyss_towns_visited";

    public static boolean visited(net.minecraft.world.entity.player.Player p, String town) {
        return java.util.Arrays.asList(p.getPersistentData().getString(TOWNS_TAG).split(",")).contains(town);
    }

    // 初めて着いたら true
    static boolean markVisited(ServerPlayer p, String town) {
        if (!visited(p, town)) {
            String now = p.getPersistentData().getString(TOWNS_TAG);
            p.getPersistentData().putString(TOWNS_TAG, now.isEmpty() ? town : now + "," + town);
            return true;
        }
        return false;
    }

    static void announce(ServerPlayer p, String area) {
        Component title;
        Component sub;
        if (area.startsWith("town:")) {
            String t = area.substring(5);
            if (markVisited(p, t)) {
                // 初めて着いた町: 広場を見渡す短い場面 (2026-10-26 「演出を増やしたい」。Scenes.townFirst)
                BlockPos c = ContinentLayout.get().town(t);
                // 自己テストの偽の人には見せない (場面の間は無敵で動けず、終わりの返事も来ない)
                if (c != null && !(p instanceof net.neoforged.neoforge.common.util.FakePlayer)) {
                    jp.revati.skyabyss.cinematic.Cinematics.startOnce(p, jp.revati.skyabyss.cinematic.Scenes.TOWN_PREFIX + t, null,
                            net.minecraft.world.phys.Vec3.atCenterOf(c));
                }
            }
            title = Component.translatable("map.skyabyss.town." + t);
            sub = Component.translatable("map.skyabyss.continent." + t);
        } else if (area.equals("capital")) {
            title = Component.translatable("signpost.skyabyss.capital");
            sub = Component.translatable("message.skyabyss.area.capital");
        } else {
            int r = Math.max(0, Math.min(REGION_KEYS.length - 1, Integer.parseInt(area.substring(7))));
            title = Component.translatable("map.skyabyss.continent." + REGION_KEYS[r]);
            sub = Component.translatable("message.skyabyss.area.level", REGION_LEVELS[r]);
        }
        jp.revati.skyabyss.notice.Notice.place(p, title, sub);
    }

    @SubscribeEvent
    static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        SEEN.remove(event.getEntity().getUUID());
    }
}
