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
import jp.revati.skyabyss.story.ch5.AbyssWorld;
import jp.revati.skyabyss.story.ch5.depths.DepthFloor.Room;
import jp.revati.skyabyss.story.ch5.depths.DepthFloor.RoomType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

// 深みの敵 (docs/design/36 の 9・6)。階の中の湧き。
//   種     深淵の敵 18 種 (A 組 9・B 組 9) から一様
//   形     歪み (2) と崩れ (3)。階が深いほど崩れの割合が増える (DepthPlan.warpedChance)。深いほど歪んだ姿になる
//   数字   AbyssScaling の深みの段 (DepthPlan.stepsOf)。1 階で 0.1 段。急な段差はない
//   置き   階が整って最初の人が着いたとき、敵の巣・休み場・行商・入口の部屋を除く部屋に 1〜3 体 (DepthPlan.roomMobs)
//   湧き直し  人のいる階に、EVERY tick ごと半分の確率で一体 (上限 DepthPlan.mobCap)。人から 14 マス以上離れた部屋に
//   敵の巣  部屋の中心に踏み入ると、部屋を囲んで一度に湧く (DepthTraps)
//   王の階は湧き直さない (王と、王の呼ぶ手下だけ)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class DepthMobs {
    public static final String TAG = "skyabyss_depth_mob";
    public static final int EVERY = 100;
    static final Random RAND = new Random();
    static final String[] ALL;

    static {
        List<String> ids = new ArrayList<>();
        for (AbyssMobKind k : AbyssMobKind.values()) {
            ids.add(k.id);
        }
        ids.addAll(List.of(AbyssMobSpawner.FOES));
        ALL = ids.toArray(new String[0]);
    }

    private DepthMobs() {
    }

    static String pickId(Random r) {
        return ALL[r.nextInt(ALL.length)];
    }

    // 階の中の、湧かせた敵の数
    static int countIn(ServerLevel level, DepthBuilder.Inst i) {
        int n = 0;
        for (Mob m : level.getEntitiesOfClass(Mob.class, i.box(), e -> e.getTags().contains(TAG))) {
            if (m.isAlive()) {
                n++;
            }
        }
        return n;
    }

    // 一体湧かせる (階の数字を入れる)
    public static Entity spawn(ServerLevel level, int floor, BlockPos feet, Random r) {
        return spawnId(level, floor, feet, r, pickId(r));
    }

    public static Entity spawnId(ServerLevel level, int floor, BlockPos feet, Random r, String id) {
        int form = r.nextDouble() < DepthPlan.warpedChance(floor) ? 3 : 2;
        double steps = DepthPlan.stepsOf(floor);
        Entity e = AbyssMobSpawner.spawnAt(level, id, feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5, form);
        if (e instanceof AbyssMob m) {
            m.setDeep(steps);
        } else if (e instanceof AbyssMobB b) {
            b.setDeep(steps);
        } else {
            return e;
        }
        e.addTag(TAG);
        return e;
    }

    // 部屋の中の、立てる床を一つ (飾りの置かれた床・罠は避ける)。なければ null
    static BlockPos spotIn(ServerLevel level, DepthBuilder.Inst i, Room room, Random r, int margin) {
        for (int t = 0; t < 30; t++) {
            int tx = room.x0() + margin + r.nextInt(Math.max(1, room.w() - 2 * margin));
            int tz = room.z0() + margin + r.nextInt(Math.max(1, room.d() - 2 * margin));
            if (!i.plan.open()[tx][tz]) {
                continue;
            }
            BlockPos feet = i.feet(tx, tz);
            if (level.getBlockState(feet).isAir() && level.getBlockState(feet.above()).isAir() && level.getBlockState(feet.below()).canOcclude()
                    && !isTrap(i, tx, tz)) {
                return feet;
            }
        }
        return null;
    }

    static boolean isTrap(DepthBuilder.Inst i, int tx, int tz) {
        for (DepthFloor.Trap t : i.plan.traps()) {
            if (t.x() == tx && t.z() == tz) {
                return true;
            }
        }
        return false;
    }

    // 階が整って最初の人が着いたとき、一度だけ
    static void populate(ServerLevel level, DepthBuilder.Inst i) {
        if (i.populated || i.plan.boss()) {
            i.populated = true;
            return;
        }
        i.populated = true;
        Random r = new Random(i.seed ^ 0x90B);
        int per = DepthPlan.roomMobs(i.floor);
        int total = 0;
        for (Room room : i.plan.rooms()) {
            if (room.entry() || room.type() == RoomType.REST || room.type() == RoomType.SHOP || room.type() == RoomType.HOUSE) {
                continue;
            }
            int n = per - (r.nextInt(3) == 0 ? 1 : 0) + (room.type() == RoomType.TREASURE ? 1 : 0);
            for (int k = 0; k < n; k++) {
                BlockPos at = spotIn(level, i, room, r, 1);
                if (at != null) {
                    spawn(level, i.floor, at, r);
                    total++;
                }
            }
        }
        SkyAbyss.LOGGER.info("[abyss depth] floor {}: {} foes placed", i.floor, total);
    }

    // 敵の巣: 部屋の周りに一度に湧く
    static int spawnHouse(ServerLevel level, DepthBuilder.Inst i, Room room) {
        int n = Math.min(14, 7 + i.floor / 6);
        int made = 0;
        for (int k = 0; k < n; k++) {
            BlockPos at = spotIn(level, i, room, RAND, 1);
            if (at != null) {
                spawn(level, i.floor, at, RAND);
                made++;
            }
        }
        return made;
    }

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % EVERY != 0) {
            return;
        }
        ServerLevel level = server.getLevel(AbyssWorld.LEVEL);
        if (level == null) {
            return;
        }
        for (ServerPlayer p : List.copyOf(level.players())) {
            DepthBuilder.Inst i = DepthBuilder.at(p.blockPosition());
            if (i == null || !i.ready || !i.populated || i.plan.boss() || !p.isAlive() || p.isCreative() || p.isSpectator() || RAND.nextBoolean()) {
                continue;
            }
            if (countIn(level, i) >= DepthPlan.mobCap(i.floor)) {
                continue;
            }
            List<Room> rooms = new ArrayList<>();
            for (Room room : i.plan.rooms()) {
                if (room.entry() || room.type() == RoomType.REST || room.type() == RoomType.SHOP) {
                    continue;
                }
                double dx = p.getX() - (i.ox + room.cx() + 0.5);
                double dz = p.getZ() - (i.oz + room.cz() + 0.5);
                if (Math.sqrt(dx * dx + dz * dz) >= 14) {
                    rooms.add(room);
                }
            }
            if (rooms.isEmpty()) {
                continue;
            }
            BlockPos at = spotIn(level, i, rooms.get(RAND.nextInt(rooms.size())), RAND, 1);
            if (at != null) {
                spawn(level, i.floor, at, RAND);
            }
        }
    }

    // 王の呼ぶ手下の体力・傷を決める (DepthBoss が使う)。階の数字に、手下らしく控えめに
    static void tuneAdd(LivingEntity e, double hpMul) {
        double hp = e.getAttributeValue(Attributes.MAX_HEALTH) * hpMul;
        e.getAttribute(Attributes.MAX_HEALTH).setBaseValue(hp);
        e.setHealth((float) hp);
    }

    // 落とし物: 深みの敵は A 組の素材を落とすことがある (階が深いほど確率が少し上がる)
    @SubscribeEvent
    static void onDrops(LivingDropsEvent event) {
        LivingEntity e = event.getEntity();
        if (!e.getTags().contains(TAG) || !(e.level() instanceof ServerLevel level) || !(e instanceof AbyssMob m)) {
            return;
        }
        DepthBuilder.Inst i = DepthBuilder.at(e.blockPosition());
        double chance = 0.12 + 0.004 * (i == null ? 0 : Math.min(60, i.floor));
        if (RAND.nextDouble() < chance) {
            event.getDrops().add(new ItemEntity(level, e.getX(), e.getY() + 0.5, e.getZ(), new ItemStack(AbyssMobsA.materialOf(m.kind()))));
        }
    }
}
