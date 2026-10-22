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
        T found = mc.level.getEntitiesOfClass(type, mc.player.getBoundingBox().inflate(radius), e -> e.isAlive() && e.getDisplayName().getString().equals(name))
                .stream().min(Comparator.comparingDouble(e -> e.distanceToSqr(mc.player))).orElse(null);
        if (CACHE.size() > 64) {
            CACHE.clear();
        }
        CACHE.put(key, new Hit(tick, levelId, name, found));
        return found;
    }

    // この体が、いま画面上のボスの帯の持ち主か (直前の数 tick に見つかった持ち主。頭の上の帯と二重に出さないため)
    public static boolean owns(LivingEntity e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return false;
        }
        long tick = mc.level.getGameTime();
        for (Hit hit : CACHE.values()) {
            if (hit.owner == e && tick - hit.tick <= 5) {
                return true;
            }
        }
        return false;
    }
}
