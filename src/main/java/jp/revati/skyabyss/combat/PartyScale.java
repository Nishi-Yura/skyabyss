package jp.revati.skyabyss.combat;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

// 人数で強くなる主 (docs/design/47 の 2.6。2026-11-02 の全体監査: 月・雲海・名のある敵の主は人数で変わらなかった)。
//   主の体力には 1024 の上限があるので、四つの心臓・始祖と同じく「人から受ける傷を HeartBalance.partyScale(人数) で割る」形にする。
//   湧かせる所 (舞台・名のある敵の棲み家) が apply / applyNearby を呼び、値は永続データ KEY に書く (保存され、読み直しても効く)。
//   自分で割っている主 (四つの心臓・始祖・塔の主・深みの王・淵の杭) には使わない (二重になる)。
//   環境・仕掛けの傷 (人が打ったのでない傷) はそのまま
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class PartyScale {
    public static final String KEY = "skyabyss_party_scale";
    // 主の硬さ (心臓の BASE_RESIST と同じ役)。体力の上限 1024 を越えられない主が、心臓と同じ長さ (一人で 200 秒前後) の戦いになるよう、
    // 人から受ける傷をこの値でも割る。値は 47 の 2.6 の表 (dps(Lv) × 0.55 × 200 ÷ 主の HP ÷ 平均の倍率)。2026-11-02
    public static final String RESIST = "skyabyss_resist";
    // 型ごとの硬さ (中ボス 4 体。崩しゲージと技の木の上乗せ込みで一人 60〜90 秒になるよう。docs/design/47 の 2.1、TtkAuditSelfTest の見積もりで決めた。2026-11-04)
    static final java.util.Map<String, Double> TYPE_RESIST = java.util.Map.of(
            "skyabyss:whalebone_captain", 1.75,
            "skyabyss:tide_beckoner", 1.83,
            "skyabyss:raiju", 2.02,
            "skyabyss:ash_smith", 1.86);

    private PartyScale() {
    }

    // 人数を決めて書く (1 人なら何もしない)
    public static void apply(LivingEntity boss, int players) {
        // 2026-11-09: 受ける傷を割るのをやめ、体力に掛ける (combat/Hardness)
        Hardness.set(boss, "party", HeartBalance.partyScale(players));
    }

    // 主のまわり radius の内にいる人 (観戦者を除く) の数で書く。棲み家に現れる名のある敵など、舞台の一覧がない所用
    public static int applyNearby(LivingEntity boss, double radius) {
        if (!(boss.level() instanceof ServerLevel level)) {
            return 1;
        }
        BlockPos at = boss.blockPosition();
        int n = level.getEntitiesOfClass(ServerPlayer.class, new AABB(at).inflate(radius), p -> !p.isSpectator() && p.isAlive()).size();
        apply(boss, n);
        return Math.max(1, n);
    }

    // 硬さを書く (1 なら消す)
    public static void resist(LivingEntity boss, double k) {
        Hardness.set(boss, "resist", k);
    }

    // 型ごとの硬さ (なければ 1)
    public static double typeResist(LivingEntity e) {
        Double r = TYPE_RESIST.get(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString());
        return r == null ? 1 : r;
    }

    // 書いてある倍率 (人数 × 硬さ × 型ごとの硬さ。なければ 1)
    public static double of(LivingEntity e) {
        return Hardness.get(e, "party") * Hardness.get(e, "resist") * Hardness.get(e, "type");
    }

    // 自己テスト用: 人数と硬さを外して素の体力を見る
    public static void clearForTest(LivingEntity e) {
        Hardness.set(e, "party", 1);
        Hardness.set(e, "resist", 1);
    }

    // 型ごとの硬さは、湧いたときに体力へ掛ける
    @SubscribeEvent
    static void onJoin(net.neoforged.neoforge.event.entity.EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof LivingEntity e && !(e instanceof Player)) {
            double t = typeResist(e);
            if (t > 1) {
                Hardness.set(e, "type", t);
            }
        }
    }
}
