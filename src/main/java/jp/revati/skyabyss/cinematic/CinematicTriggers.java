package jp.revati.skyabyss.cinematic;

import javax.annotation.Nullable;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.lore.Lore;
import jp.revati.skyabyss.progress.Progression;
import jp.revati.skyabyss.progress.Titans;
import jp.revati.skyabyss.titan.WhaleKeeper;
import jp.revati.skyabyss.titan.ember.EmberKeeper;
import jp.revati.skyabyss.titan.ember.EmberTitanEntity;
import jp.revati.skyabyss.titan.storm.StormKeeper;
import jp.revati.skyabyss.titan.tide.TideKeeper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

// 場面を始める条件 (サーバー)。人ごとに 1 秒に 1 回見て、条件がそろった人にだけ始める。
//   巨神の初見 : 岩鯨・潮殻・雷翼・余燼が、それぞれ近づいたとき (伝承が記される距離より手前)。人ごとに一度だけ。docs/design/20 の 3.2・3.2.1
// 開幕は OpeningDirector (拍子 B0) が Cinematics.start を呼ぶ。巨神の心臓は TitanCinematics.heartAppears (ボスの担当が呼ぶ)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class CinematicTriggers {
    // 岩鯨がこの水平距離より近くに来たら (これより手前で伝承 160 が記されるので、それより先に)
    static final double WHALE_NEAR = 250.0;
    // 近すぎる (真上に来ている) と全身が収まらないので見せない
    static final double WHALE_TOO_CLOSE = 70.0;
    // 潮殻 (伝承は 200 以内)・雷翼 (96)・余燼 (96) の範囲。伝承より先に出るよう、遠い側は伝承の半径より大きい
    static final double TIDE_FAR = 280.0;
    static final double TIDE_NEAR = 90.0;
    static final double STORM_FAR = 300.0;
    static final double STORM_NEAR = 130.0;
    static final double EMBER_FAR = 240.0;
    static final double EMBER_NEAR = 90.0;
    // 見回る巨神の順 (どの巨神にも一度ずつ)
    private static final String[] TITANS = {Titans.STONE_WHALE, Titans.TIDE_SHELL, Titans.THUNDER_WING, Titans.EMBER};
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
        if (!canWatch(player)) {
            return false;
        }
        ServerLevel level = player.serverLevel();
        if (level.dimension() != Level.OVERWORLD) {
            return false;
        }
        // 巨神ごとに、まだ見ていなければ見回る (始まれば場面の最中になるので、一度に始まるのは一つだけ)
        for (String titan : TITANS) {
            if (Cinematics.state(player).seen(sceneOf(titan))) {
                continue;
            }
            if (firstSight(level, player, titan, currentTitan(level, titan))) {
                return true;
            }
        }
        return false;
    }

    // 見回る対象か。船 (海で潮殻に会いに行く足) に乗っている人は対象。それ以外の騎乗 (馬・巨神の背の乗り物など) は見せない
    static boolean canWatch(ServerPlayer player) {
        return player.isAlive() && !player.isSpectator() && !Cinematics.active(player) && vehicleAllows(player.isPassenger() ? player.getVehicle() : null);
    }

    // 乗り物の条件だけ (null = 徒歩)。船は可、ほかは不可
    static boolean vehicleAllows(@Nullable Entity vehicle) {
        return vehicle == null || vehicle instanceof Boat;
    }

    // 初見の場面の名前 (岩鯨だけ昔からの名前)
    public static String sceneOf(String titan) {
        return Titans.STONE_WHALE.equals(titan) ? Scenes.WHALE_FIRST_SIGHT : Scenes.firstId(titan);
    }

    @Nullable
    static Entity currentTitan(ServerLevel level, String titan) {
        return switch (titan) {
            case Titans.STONE_WHALE -> WhaleKeeper.current(level);
            case Titans.TIDE_SHELL -> TideKeeper.KEEPER.current(level);
            case Titans.THUNDER_WING -> StormKeeper.KEEPER.current(level);
            case Titans.EMBER -> EmberKeeper.KEEPER.current(level);
            default -> null;
        };
    }

    // 距離を測る点 (余燼は地の下を泳ぐので、尾根の点)
    private static Vec3 anchor(Entity entity) {
        return entity instanceof EmberTitanEntity ember ? ember.ridgePoint() : entity.position();
    }

    // この巨神の初見を、この人に始める条件を見る (entity = 今いる巨神。テストが任意の物を渡せるよう、見つけ方と分けてある)。始めたら true。
    // 人ごと: 見た記録も条件も、その人だけのもの (パーティの誰かが見ていても、この人がまだなら見る)
    public static boolean firstSight(ServerLevel level, ServerPlayer player, String titan, @Nullable Entity entity) {
        String scene = sceneOf(titan);
        if (Cinematics.state(player).seen(scene)) {
            return false;
        }
        // すでに知っている人 (旧ワールドで見た・乗った・止めた人) には、改めて見せない
        if (alreadyKnows(player, titan)) {
            Cinematics.state(player).markSeen(scene);
            return false;
        }
        if (entity == null || !entity.isAlive()) {
            return false;
        }
        Vec3 at = anchor(entity);
        double dx = at.x - player.getX();
        double dz = at.z - player.getZ();
        double d = Math.sqrt(dx * dx + dz * dz);
        if (d > far(titan) || d < near(titan) || !level.canSeeSky(player.blockPosition().above()) || player.isUnderWater() || player.isSleeping()
                || player.getHealth() < player.getMaxHealth() * 0.5f || (player.getLastHurtByMobTimestamp() > 0 && player.tickCount - player.getLastHurtByMobTimestamp() < HURT_TICKS)
                || !level.getEntitiesOfClass(Monster.class, new AABB(player.blockPosition()).inflate(HOSTILE_NEAR), m -> m.isAlive()).isEmpty()) {
            return false;
        }
        return Cinematics.startOnce(player, scene, entity, at);
    }

    // 伝承の項目は近づくと記される (岩鯨 160、潮殻 200、雷翼 96)。場面はそれより先に出るので、伝承を根拠にするのは岩鯨 (旧ワールドからの人) だけ。
    // 潮殻・雷翼・余燼は、その巨神の心臓を止めた人 (見たに決まっている) だけを見たことにする
    static boolean alreadyKnows(ServerPlayer player, String titan) {
        if (Titans.STONE_WHALE.equals(titan)) {
            return Lore.state(player).has("chapter1/stone_whale");
        }
        return player.getServer() != null && Progression.stopped(player.getServer(), player.getUUID(), titan);
    }

    // 場面を始める水平距離の範囲 (遠い側・近い側)。近すぎると全身が収まらない
    static double far(String titan) {
        return switch (titan) {
            case Titans.TIDE_SHELL -> TIDE_FAR;
            case Titans.THUNDER_WING -> STORM_FAR;
            case Titans.EMBER -> EMBER_FAR;
            default -> WHALE_NEAR;
        };
    }

    static double near(String titan) {
        return switch (titan) {
            case Titans.TIDE_SHELL -> TIDE_NEAR;
            case Titans.THUNDER_WING -> STORM_NEAR;
            case Titans.EMBER -> EMBER_NEAR;
            default -> WHALE_TOO_CLOSE;
        };
    }
}
