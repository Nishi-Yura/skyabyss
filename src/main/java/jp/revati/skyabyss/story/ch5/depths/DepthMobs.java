package jp.revati.skyabyss.story.ch5.depths;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.abyss.mobs.AbyssMob;
import jp.revati.skyabyss.abyss.mobs.AbyssMobB;
import jp.revati.skyabyss.abyss.mobs.AbyssMobKind;
import jp.revati.skyabyss.abyss.mobs.AbyssMobSpawner;
import jp.revati.skyabyss.abyss.mobs.AbyssMobsA;
import jp.revati.skyabyss.abyss.mobs.AbyssScaling;
import jp.revati.skyabyss.abyss.mobs.NamelessShape;
import jp.revati.skyabyss.abyss.mobs.PastShadow;
import jp.revati.skyabyss.story.ch5.AbyssWorld;
import jp.revati.skyabyss.story.ch5.depths.DepthPlan.Gimmick;
import jp.revati.skyabyss.story.ch5.depths.DepthPlan.Modifier;
import jp.revati.skyabyss.story.ch5.depths.DepthPlan.Platform;
import jp.revati.skyabyss.story.ch5.depths.DepthPlan.Section;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

// 深みの敵 (docs/design/36 の 9・4)。区画に人がいる間、人ごとに EVERY tick に一回、半分の確率で一体。
//   種     深淵の敵 18 種 (A 組 9 と B 組 9) から一様。写しの敵の仕掛けがあれば、前の私の影・名のない形に寄る
//   形     崩れ (3) が六割、歪み (2) が四割
//   数字   湧いた高さの数字 (AbyssScaling) に、心臓の下の深さの段 (AbyssScaling.deepSteps) を上乗せ。修飾の傷が深いは段を一つ足す
//   数     区画の中に十体まで (敵が多いは十五体)。区画の外へは湧かない
//   落とし 落とし物が多い修飾があれば、A 組の素材を一つ足す (確率は段が深いほど上がる)
//   霧     霧が敵を隠す仕掛けは、灯りから六ブロック以上離れた敵の姿を消す (DepthGimmicks が DepthMobs.fog を呼ぶ)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class DepthMobs {
    public static final String TAG = "skyabyss_depth_mob";
    public static final String TAG_RICH = "skyabyss_depth_rich";
    public static final int EVERY = 40;
    static final int CAP = 10;
    static final double FOG_LIGHT_R = 6;
    static final Random RAND = new Random();
    static final String[] ALL;

    static {
        List<String> ids = new ArrayList<>();
        for (AbyssMobKind k : AbyssMobKind.values()) {
            ids.add(k.id);
        }
        for (String id : AbyssMobSpawner.FOES) {
            ids.add(id);
        }
        ALL = ids.toArray(new String[0]);
    }

    private DepthMobs() {
    }

    // 種を一つ選ぶ (写しの敵は、前の私の影・名のない形に寄せる)
    static String pickId(Section s, Random r) {
        if (s.has(Gimmick.COPY_FOES) && r.nextInt(2) == 0) {
            return r.nextBoolean() ? PastShadow.ID : NamelessShape.ID;
        }
        return ALL[r.nextInt(ALL.length)];
    }

    // 区画の中の、この区画の敵の数
    static int countIn(ServerLevel level, int sec) {
        int cx = sec * DepthPlan.SLOT;
        AABB box = new AABB(cx - DepthPlan.ROOM_R - 2, DepthPlan.TOP_Y - DepthPlan.HEIGHT - 2, -DepthPlan.ROOM_R - 2,
                cx + DepthPlan.ROOM_R + 2, DepthPlan.TOP_Y + 2, DepthPlan.ROOM_R + 2);
        int n = 0;
        for (Mob m : level.getEntitiesOfClass(Mob.class, box, e -> e.getTags().contains(TAG))) {
            if (m.isAlive()) {
                n++;
            }
        }
        return n;
    }

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % EVERY != 0) {
            return;
        }
        spawnTick(server);
    }

    static void spawnTick(MinecraftServer server) {
        ServerLevel level = server.getLevel(AbyssWorld.LEVEL);
        if (level == null) {
            return;
        }
        for (ServerPlayer p : java.util.List.copyOf(level.players())) {
            int sec = DepthRun.sectionOf(p);
            if (sec == 0 || !p.isAlive() || p.isCreative() || p.isSpectator() || RAND.nextBoolean()) {
                continue;
            }
            if (!DepthBuilder.built(server, sec)) {
                continue;
            }
            DepthLayout.Result g = DepthBuilder.geometry(server, sec);
            Section s = g.section();
            int cap = s.has(Modifier.MORE_FOES) ? CAP * 3 / 2 : CAP;
            if (countIn(level, sec) >= cap) {
                continue;
            }
            Platform spot = spotNear(g, p);
            if (spot == null) {
                continue;
            }
            BlockPos top = DepthGimmicks.ledgeTop(g, spot);
            BlockPos feet = top.above();
            spawn(level, s, feet, RAND);
        }
    }

    // 人のまわりの道の足場 (水平 6〜14 、上下 12 以内) を一つ。なければ null
    static Platform spotNear(DepthLayout.Result g, ServerPlayer p) {
        int ly = DepthPlan.lyOf(p.blockPosition().getY());
        int cx = g.section().centerX();
        List<Platform> ok = new ArrayList<>();
        for (Platform q : g.route()) {
            double dx = p.getX() - (cx + q.dx());
            double dz = p.getZ() - q.dz();
            double h = Math.sqrt(dx * dx + dz * dz);
            if (h >= 6 && h <= 14 && Math.abs(q.ly() - ly) <= 12 && !q.landing()) {
                ok.add(q);
            }
        }
        return ok.isEmpty() ? null : ok.get(RAND.nextInt(ok.size()));
    }

    // 一体湧かせる (数字と仕掛けの上乗せを入れる)
    public static Entity spawn(ServerLevel level, Section s, BlockPos feet, Random r) {
        String id = pickId(s, r);
        int form = r.nextInt(10) < 6 ? 3 : 2;
        int beyond = DepthPlan.beyond(s.index(), feet.getY());
        double steps = Math.min(AbyssScaling.DEEP_CAP, AbyssScaling.deepSteps(beyond) + (s.has(Modifier.FIERCE) ? 1 : 0));
        Entity e = AbyssMobSpawner.spawnAt(level, id, feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5, form);
        if (e instanceof AbyssMob m) {
            m.setDeep(steps);
        } else if (e instanceof AbyssMobB b) {
            b.setDeep(steps);
        } else {
            return e;
        }
        e.addTag(TAG);
        if (s.has(Modifier.RICH_DROPS)) {
            e.addTag(TAG_RICH);
        }
        return e;
    }

    // 霧が敵を隠す: 灯りから離れた深みの敵は、姿が消える (灯りの近くは見える)
    static void fog(ServerLevel level, DepthLayout.Result g) {
        int cx = g.section().centerX();
        AABB box = new AABB(cx - DepthPlan.ROOM_R, DepthPlan.TOP_Y - DepthPlan.HEIGHT, -DepthPlan.ROOM_R,
                cx + DepthPlan.ROOM_R, DepthPlan.TOP_Y, DepthPlan.ROOM_R);
        for (LivingEntity l : level.getEntitiesOfClass(LivingEntity.class, box, e -> e.getTags().contains(TAG))) {
            BlockPos at = l.blockPosition();
            if (DepthGimmicks.nearLight(level, g, at, (int) FOG_LIGHT_R)) {
                l.removeEffect(MobEffects.INVISIBILITY);
            } else {
                l.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 60, 0, true, false, false));
            }
        }
    }

    // 落とし物 (落とし物が多い修飾の深みの敵は、A 組の素材を一つ足す。段が深いほど確率が上がる)
    @SubscribeEvent
    static void onDrops(LivingDropsEvent event) {
        LivingEntity e = event.getEntity();
        if (!e.getTags().contains(TAG_RICH) || !(e.level() instanceof ServerLevel level)) {
            return;
        }
        if (!(e instanceof AbyssMob m)) {
            return;
        }
        int sec = DepthRun.sectionOf(e.blockPosition());
        double chance = 0.3 + 0.1 * (sec == 0 ? 0 : DepthPlan.tierOf(sec));
        if (RAND.nextDouble() < chance) {
            event.getDrops().add(new ItemEntity(level, e.getX(), e.getY() + 0.5, e.getZ(), new ItemStack(AbyssMobsA.materialOf(m.kind()))));
        }
    }
}
