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
import net.minecraft.core.BlockPos;
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
    static final double STORM_FAR = 360.0;
    static final double STORM_NEAR = 70.0;
    static final double EMBER_FAR = 240.0;
    static final double EMBER_NEAR = 90.0;
    // 見回る巨神の順 (どの巨神にも一度ずつ)
    private static final String[] TITANS = {Titans.STONE_WHALE, Titans.TIDE_SHELL, Titans.THUNDER_WING, Titans.EMBER};
    private static final int INTERVAL = 20;
    // 敵が近い・傷を受けた直後は後ろへずらす
    private static final double HOSTILE_NEAR = 12.0;
    private static final int HURT_TICKS = 200;
    // 襲ってくる敵が近くても、この間 (tick) 待ったら見せる。「近い」は、この人を狙っているか、この距離以内
    private static final double HOSTILE_ADJACENT = 6.0;
    private static final int DELAY_MAX_TICKS = 600;
    // 柔らかい条件で待たされ始めた時刻 (人 + 場面)
    private static final java.util.Map<String, Long> DELAYED = new java.util.HashMap<>();

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

    private static String delayKey(ServerPlayer player, String scene) {
        return player.getUUID() + "/" + scene;
    }

    // 空が見えるか。葉は数えない (針葉樹の森の中でも、枝のあいだから空は見えるので)
    static boolean openSky(ServerLevel level, ServerPlayer player) {
        BlockPos p = player.blockPosition();
        return level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, p.getX(), p.getZ()) <= p.getY() + 1;
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
        // 巨神が実際にそこにいて、この人から見えること (地の下を泳いでいる余燼・眠っている巨神・読み込まれていない巨神・丘の向こうでは始めない)
        if (!visibleTo(level, player, titan, entity, at)) {
            DELAYED.remove(delayKey(player, scene));
            return false;
        }
        double dx = at.x - player.getX();
        double dz = at.z - player.getZ();
        double d = Math.sqrt(dx * dx + dz * dz);
        // 硬い条件: 距離の範囲・空が見える (葉は数えない)・水の中でない・寝ていない。一つでも欠ければ、待ちの記録も消す
        if (d > far(titan) || d < near(titan) || !openSky(level, player) || player.isUnderWater() || player.isSleeping()) {
            DELAYED.remove(delayKey(player, scene));
            return false;
        }
        // 柔らかい条件: 傷が深い・傷を受けた直後・襲ってくる敵が近い。後ろへずらすが、いつまでも待たせない (雷翼の嵐は敵が湧き続けるので、
        // 待たせ続けると場面が出ないまま通り過ぎてしまう)。DELAY_MAX_TICKS を過ぎたら、柔らかい条件は見ない
        boolean soft = player.getHealth() < player.getMaxHealth() * 0.5f
                || (player.getLastHurtByMobTimestamp() > 0 && player.tickCount - player.getLastHurtByMobTimestamp() < HURT_TICKS)
                || !level.getEntitiesOfClass(Monster.class, new AABB(player.blockPosition()).inflate(HOSTILE_NEAR),
                        m -> m.isAlive() && (m.getTarget() == player || m.distanceToSqr(player) < HOSTILE_ADJACENT * HOSTILE_ADJACENT)).isEmpty();
        if (soft) {
            long now = level.getGameTime();
            long since = DELAYED.computeIfAbsent(delayKey(player, scene), k -> now);
            if (now - since < DELAY_MAX_TICKS) {
                return false;
            }
        }
        DELAYED.remove(delayKey(player, scene));
        return Cinematics.startOnce(player, scene, entity, at);
    }

    // 巨神が「いる」「見える」か (初見の場面の前提)。
    //  - 余燼は、浮かんで (上がる・眠る・沈む) 地上に背を出しているときだけ。地の下を泳いでいる間は、世界にはいても見えない
    //  - 眠って去る途中の巨神 (TitanDormancy) は見せない
    //  - 世界に出ている実体は、この人にチャンクが送られていて、目から巨神の体 (中心・上・さらに上) のどれかへ視線が通ること
    //    (丘の陰・山の向こうでは始めない)。世界に出ていない置物 (自己テスト) は距離と生死の判定だけ
    static boolean visibleTo(ServerLevel level, ServerPlayer player, String titan, Entity entity, Vec3 at) {
        if (entity.isRemoved()) {
            return false;
        }
        if (entity instanceof EmberTitanEntity ember && !ember.isSurfaced()) {
            return false;
        }
        if (player.getServer() != null && jp.revati.skyabyss.titan.end.TitanDormancy.isResting(player.getServer(), titan)) {
            return false;
        }
        if (level.getEntity(entity.getUUID()) != entity) {
            return true;
        }
        BlockPos bp = BlockPos.containing(at);
        if (!level.isLoaded(bp) || !player.getChunkTrackingView().contains(bp.getX() >> 4, bp.getZ() >> 4)) {
            return false;
        }
        net.minecraft.world.phys.Vec3 eye = player.getEyePosition();
        for (double up : new double[] {2, 14, 30}) {
            net.minecraft.world.phys.Vec3 target = at.add(0, up, 0);
            if (level.clip(new net.minecraft.world.level.ClipContext(eye, target, net.minecraft.world.level.ClipContext.Block.VISUAL,
                    net.minecraft.world.level.ClipContext.Fluid.NONE, player)).getType() == net.minecraft.world.phys.HitResult.Type.MISS) {
                return true;
            }
        }
        return false;
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
