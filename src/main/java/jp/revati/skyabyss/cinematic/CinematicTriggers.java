package jp.revati.skyabyss.cinematic;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.lore.Lore;
import jp.revati.skyabyss.titan.StoneWhaleEntity;
import jp.revati.skyabyss.titan.WhaleKeeper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

// 場面を始める条件 (サーバー)。人ごとに 1 秒に 1 回見て、条件がそろった人にだけ始める。
//   岩鯨の初見 : 空に見える岩鯨が近づいたとき (伝承 chapter1/stone_whale が記される 160 ブロックより手前)。一度だけ
// 開幕は OpeningDirector (拍子 B0) が Cinematics.start を呼ぶ。巨神の心臓は TitanCinematics.heartAppears (ボスの担当が呼ぶ)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class CinematicTriggers {
    // 岩鯨がこの水平距離より近くに来たら (これより手前で伝承 160 が記されるので、それより先に)
    static final double WHALE_NEAR = 250.0;
    // 近すぎる (真上に来ている) と全身が収まらないので見せない
    static final double WHALE_TOO_CLOSE = 70.0;
    private static final int INTERVAL = 20;
    // 敵が近い・傷を受けた直後は後ろへずらす
    private static final double HOSTILE_NEAR = 12.0;
    private static final int HURT_TICKS = 200;

    private CinematicTriggers() {
    }

    @SubscribeEvent
    static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player instanceof FakePlayer) {
            return;
        }
        if ((player.tickCount + player.getId()) % INTERVAL != 0) {
            return;
        }
        run(player);
    }

    // 一人の見回り (テストからも呼べる)。始めたら true
    public static boolean run(ServerPlayer player) {
        if (!player.isAlive() || player.isSpectator() || Cinematics.active(player) || player.isPassenger()) {
            return false;
        }
        ServerLevel level = player.serverLevel();
        if (level.dimension() != Level.OVERWORLD) {
            return false;
        }
        if (!Cinematics.state(player).seen(Scenes.WHALE_FIRST_SIGHT)) {
            return whaleFirstSight(level, player);
        }
        return false;
    }

    private static boolean whaleFirstSight(ServerLevel level, ServerPlayer player) {
        // すでに伝承で岩鯨を知っている人 (見たことがある・背に乗った) には、改めて見せない
        if (Lore.state(player).has("chapter1/stone_whale")) {
            Cinematics.state(player).markSeen(Scenes.WHALE_FIRST_SIGHT);
            return false;
        }
        StoneWhaleEntity whale = WhaleKeeper.current(level);
        if (whale == null || !whale.isAlive()) {
            return false;
        }
        double dx = whale.getX() - player.getX();
        double dz = whale.getZ() - player.getZ();
        double d = Math.sqrt(dx * dx + dz * dz);
        if (d > WHALE_NEAR || d < WHALE_TOO_CLOSE || !level.canSeeSky(player.blockPosition().above()) || player.isUnderWater() || player.isSleeping()
                || player.getHealth() < player.getMaxHealth() * 0.5f || (player.getLastHurtByMobTimestamp() > 0 && player.tickCount - player.getLastHurtByMobTimestamp() < HURT_TICKS)
                || !level.getEntitiesOfClass(Monster.class, new AABB(player.blockPosition()).inflate(HOSTILE_NEAR), m -> m.isAlive()).isEmpty()) {
            return false;
        }
        return Cinematics.startOnce(player, Scenes.WHALE_FIRST_SIGHT, whale, whale.position());
    }
}
