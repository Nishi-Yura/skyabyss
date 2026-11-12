package jp.revati.skyabyss.client;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import net.minecraft.client.Minecraft;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.LivingEntity;

// ボスバーの持ち主 (近くにいる、その名の生き物のうち一番近いもの) を探す。ボスバーを描くたびに毎フレーム広い範囲を探していたので、
// 同じ tick のあいだは前の答えを使う (生き物の位置と名前は tick ごとにしか変わらないので、答えは同じ)
public final class BossOwners {
    private BossOwners() {
    }

    private record Key(java.util.UUID boss, Class<?> type, double radius) {
    }

    private record Hit(long tick, int levelId, String name, LivingEntity owner) {
    }

    private static final Map<Key, Hit> CACHE = new HashMap<>();

    @SuppressWarnings("unchecked")
    public static <T extends LivingEntity> T find(Minecraft mc, BossEvent boss, Class<T> type, double radius) {
        if (mc.level == null || mc.player == null) {
            return null;
        }
        String name = boss.getName().getString();
        long tick = mc.level.getGameTime();
        int levelId = System.identityHashCode(mc.level);
        Key key = new Key(boss.getId(), type, radius);
        Hit hit = CACHE.get(key);
        if (hit != null && hit.tick == tick && hit.levelId == levelId && Objects.equals(hit.name, name)) {
            return (T) hit.owner;
        }
        T found = mc.level.getEntitiesOfClass(type, mc.player.getBoundingBox().inflate(radius), e -> e.isAlive() && sameName(e.getDisplayName().getString(), name))
                .stream().min(Comparator.comparingDouble(e -> e.distanceToSqr(mc.player))).orElse(null);
        if (CACHE.size() > 64) {
            CACHE.clear();
        }
        CACHE.put(key, new Hit(tick, levelId, name, found));
        return found;
    }

    // 帯の名前が「深淵の〜」「封じ 2/3」などで包まれていても、中にその名があれば持ち主とみなす (包むと持ち主が見つからず、バニラの帯に戻っていた)
    static boolean sameName(String entity, String bar) {
        return bar.equals(entity) || (!entity.isEmpty() && bar.contains(entity));
    }

    // 帯の持ち主と分かった体 (体の id → 見つけた tick)。どの描き手の帯でも、帯ごとに client/hud/FoeStatusClient が入れる
    // (前は名前で探す描き手の持ち主だけが分かり、割合で探す主や、自前で描く心臓などの頭の上に帯が二重に出ていた。2026-11-12 の声)
    private static final Map<Integer, Long> CLAIMED = new HashMap<>();

    public static void claim(LivingEntity e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        if (CLAIMED.size() > 64) {
            CLAIMED.clear();
        }
        CLAIMED.put(e.getId(), mc.level.getGameTime());
    }

    // この体が、いま画面上のボスの帯の持ち主か (直前の数 tick に見つかった持ち主。頭の上の帯と二重に出さないため)
    public static boolean owns(LivingEntity e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return false;
        }
        long tick = mc.level.getGameTime();
        Long claimed = CLAIMED.get(e.getId());
        if (claimed != null && tick - claimed <= 5 && tick >= claimed) {
            return true;
        }
        for (Hit hit : CACHE.values()) {
            if (hit.owner == e && tick - hit.tick <= 5) {
                return true;
            }
        }
        return false;
    }
}
