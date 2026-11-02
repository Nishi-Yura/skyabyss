package jp.revati.skyabyss.upgrade;

import java.util.List;
import java.util.function.Consumer;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

// 版が上がったときの、人ごとのデータの移し替え (WorldUpgrade の人の側)。人の永続データ skyabyss_data_version に版を持ち
// (倒れても残す: util/LifeKeep)、入ったときに、それより新しい段 (STEPS) を古い順に一度ずつ流す。
// 段は何度流しても同じ結果になるように書く。新しい段を足すとき: CURRENT を一つ上げ、STEPS の終わりに足す (docs/release.md)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class PlayerUpgrade {
    public static final int CURRENT = 2;
    public static final String KEY = "skyabyss_data_version";

    public record Step(int version, String name, Consumer<ServerPlayer> run) {
    }

    static final List<Step> STEPS = List.of(
            // 1.0.0: 置く品 (松明など。冒険の決まりでは使えない) を持ち物とエンダーチェストから外す (rpg/rules/NoPlaceables。2 秒ごとの見回りでも外す)
            new Step(1, "remove placeable items", p -> {
                if (!jp.revati.skyabyss.rpg.rules.NoPlaceables.applies(p)) {
                    return;
                }
                int n = jp.revati.skyabyss.rpg.rules.NoPlaceables.sweep(p);
                if (n > 0) {
                    SkyAbyss.LOGGER.info("Player upgrade: removed {} placeable item(s) from {}", n, p.getGameProfile().getName());
                }
            }),
            // 1.0.0: 乗り物の体力の段をやめた (mount/MountStats)。前に買った段の鐘銭を返して、段を 0 にする
            new Step(2, "refund mount health tiers", p -> {
                jp.revati.skyabyss.mount.MountData d = jp.revati.skyabyss.mount.MountService.data(p);
                int refund = jp.revati.skyabyss.mount.MountStats.healthRefund(d.tier(jp.revati.skyabyss.mount.MountStats.HEALTH));
                if (refund > 0) {
                    d.setTier(jp.revati.skyabyss.mount.MountStats.HEALTH, 0);
                    jp.revati.skyabyss.facility.Coins.give(p, refund);
                    jp.revati.skyabyss.notice.Notice.hint(p, "message.skyabyss.mount.health_refund", refund);
                    SkyAbyss.LOGGER.info("Player upgrade: refunded {} bell coin(s) of mount health tiers to {}", refund, p.getGameProfile().getName());
                }
            }));

    private PlayerUpgrade() {
    }

    public static int version(ServerPlayer p) {
        return p.getPersistentData().getInt(KEY);
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && !(p instanceof FakePlayer)) {
            run(p);
            // 新しい Mod の版で保存した世界を古い版で開いているとき、管理者に知らせる
            if (WorldUpgrade.newerThanMod(p.server) && p.hasPermissions(2)) {
                p.sendSystemMessage(Component.translatable("message.skyabyss.upgrade.newer_world", WorldUpgrade.savedWith(p.server), WorldUpgrade.modVersion())
                        .withStyle(net.minecraft.ChatFormatting.GOLD));
            }
        }
    }

    // 済んでいない段を流す。流した段の数
    public static int run(ServerPlayer p) {
        int have = version(p);
        if (have > CURRENT) {
            return 0;
        }
        int n = 0;
        for (Step s : STEPS) {
            if (s.version() <= have) {
                continue;
            }
            try {
                s.run().accept(p);
            } catch (RuntimeException e) {
                SkyAbyss.LOGGER.error("Player upgrade: step {} ({}) failed for {}; it will run again next login", s.version(), s.name(), p.getGameProfile().getName(), e);
                break;
            }
            have = s.version();
            p.getPersistentData().putInt(KEY, have);
            n++;
        }
        return n;
    }
}
