package jp.revati.skyabyss.story.ch5;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.notice.Notice;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

// 深淵で倒れたときの戻り場所 (docs/design/36 の 7)。
//   覚える   灯籠の休み場 (印 rest_<n> の 6 以内) に来ると、その番号を覚える (人ごと。死んでも引き継ぐ所に)。着く所は 0
//            初めての休み場では一言
//   戻る     深淵で倒れた人は、地上の起きる所は変えずに、覚えた休み場 (無ければ着く所) で目を覚ます
//   持ち物   墓の決まり (grave.DeathEvents) のまま。墓は深淵の倒れた所にできる
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class AbyssRespawn {
    static final String REST = "skyabyss_abyss_rest";
    static final String DIED = "skyabyss_abyss_died";
    static final double NEAR = 6.0;

    private AbyssRespawn() {
    }

    static CompoundTag persisted(Player p) {
        CompoundTag data = p.getPersistentData();
        if (!data.contains(Player.PERSISTED_NBT_TAG)) {
            data.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        }
        return data.getCompound(Player.PERSISTED_NBT_TAG);
    }

    // 覚えている休み場の番号 (0 = 着く所)
    public static int rest(Player p) {
        return persisted(p).getInt(REST);
    }

    // 近くの休み場の番号 (無ければ -1)
    static int restNear(BlockPos at) {
        for (int i = 1; i <= 4; i++) {
            BlockPos r = AbyssBuild.mark("rest_" + i);
            if (r != null && at.closerThan(r, NEAR)) {
                return i;
            }
        }
        return -1;
    }

    // 休み場の立つ所: 焚き火から縦穴の内へ 1.5 (道の上)。0 は着く所
    @Nullable
    public static BlockPos spot(int rest) {
        if (rest <= 0) {
            return AbyssWorld.arrival();
        }
        BlockPos m = AbyssBuild.mark("rest_" + rest);
        if (m == null) {
            return AbyssWorld.arrival();
        }
        double r = Math.hypot(m.getX(), m.getZ());
        double k = r < 1e-6 ? 1 : (r - 1.5) / r;
        return BlockPos.containing(m.getX() * k + 0.5, m.getY(), m.getZ() * k + 0.5);
    }

    // 休み場に来た (見回りと自己テスト)
    public static void touch(ServerPlayer p) {
        int i = restNear(p.blockPosition());
        if (i < 0) {
            return;
        }
        CompoundTag t = persisted(p);
        if (t.getInt(REST) != i) {
            boolean first = !t.getBoolean(REST + "_" + i);
            t.putInt(REST, i);
            t.putBoolean(REST + "_" + i, true);
            if (first) {
                Notice.hint(p, "message.skyabyss.abyss.rest");
            }
        }
    }

    @SubscribeEvent
    static void onTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer p && p.tickCount % 20 == 11 && AbyssWorld.in(p) && p.isAlive()) {
            touch(p);
        }
    }

    @SubscribeEvent
    static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && AbyssWorld.in(p)) {
            persisted(p).putBoolean(DIED, true);
        }
    }

    @SubscribeEvent
    static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || event.isEndConquered()) {
            return;
        }
        CompoundTag t = persisted(p);
        if (!t.getBoolean(DIED)) {
            return;
        }
        t.remove(DIED);
        // 果てない深みの中で倒れたら、覚えた灯籠 (docs/design/36 の 9) へ
        if (jp.revati.skyabyss.story.ch5.depths.DepthRun.respawn(p)) {
            return;
        }
        ServerLevel abyss = p.server.getLevel(AbyssWorld.LEVEL);
        BlockPos at = spot(t.getInt(REST));
        if (abyss == null || at == null) {
            return;
        }
        abyss.getChunk(at);
        p.teleportTo(abyss, at.getX() + 0.5, at.getY(), at.getZ() + 0.5, p.getYRot(), 0f);
        Notice.hint(p, "message.skyabyss.abyss.woke_at_rest");
    }
}
