package jp.revati.skyabyss.abyss.mobs;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.abyss.arts.AbyssLight;
import jp.revati.skyabyss.entity.GuardMob;
import jp.revati.skyabyss.story.ch5.AbyssBuild;
import jp.revati.skyabyss.story.ch5.AbyssWorld;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

// 深淵の敵 (A 組 9 種と B 組 9 種) の自然湧き。両方の組をこの一つの場所で決める (docs/design/36 の 4.2)。
//   いつ: 深淵に人がいる間、人ごとに 40 tick に一回、半分の確率で一体。
//   どこ: 人のまわり 12〜22 ブロックの床。35% は溜まり・巣・野営の印の近くを基点にする。
//   数:   人のまわり 40 ブロックに、A と B を合わせて 8 体まで (CAP_NEAR)。
//   湧かない所: 休み場 (rest_1〜6)・着く所 (arrival) から 24 ブロック以内、心臓の間 (大部屋) の中。
//   種:   湧いた高さの層 (AbyssScaling.layerOf) ごとの表 (TABLE)。元の層の種が多く、一つ上の層の種が段 2 で混ざる。
//   暗さ: 種ごとに灯りの上限 (maxLight)。上限以上の明るい所は 60% で見送る。
//   数字: 湧いた高さで決まる (AbyssScaling)。段 (形) は見た目と、崩れからの突き抜け・落とし物。
// 開発用: /skyabyss abyss mob <種> [形 1〜3] (立っている人の前に一体)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class AbyssMobSpawner {
    public static final int EVERY = 40;
    public static final int CAP_NEAR = 8;
    public static final double CAP_RANGE = 40;
    public static final double REST_KEEP = 24;

    static final double SPAWN_MIN = 12;
    static final double SPAWN_MAX = 22;
    static final double ANCHOR_RANGE = 24;
    static final double ANCHOR_CHANCE = 0.35;
    static final int DARK_LIGHT = 10;
    static final double LIGHT_SKIP = 0.6;
    static final double ROPE_MIN = 11;
    static final double ROPE_MAX = 16;
    static final double ROPE_CHANCE = 0.25;
    static final double CEILING_HEIGHT = 1.9;
    // 心臓の間 (中心は印 heart の真下の軸。半径 30 に余白 2。床は y -300 からの三角、天井は y -252)
    static final double HEART_R = 32;
    static final int HEART_ROOM_TOP = -248;

    // 組の番号 (表の列): 0〜2 = その層の元の種 (A 組)、3 = どの種でも (A 組)、4 = 灰の種、5 = 深みの種、6 = 深淵だけ
    static final int G_ANY = 3;
    static final int G_ASH = 4;
    static final int G_DEEP = 5;
    static final int G_ONLY = 6;
    // 層 (行: 0 骨・1 塩・2 雷・3 灰・4 深み) ごとの組の重み (列の合計 100)。
    //   骨: 元の種 94、深淵だけ 6。塩: 元の塩 70、一つ上の骨 24 (段 2)、深淵だけ 6。雷: 元の雷 70、一つ上の塩 24 (段 2)、深淵だけ 6。
    //   灰: 雷の種 (段 2) 36、灰の種 56、深淵だけ 8。深み: どの種でも (段 3) 38、深みの種 50、深淵だけ 12。
    static final int[][] TABLE = {
        {94, 0, 0, 0, 0, 0, 6},
        {24, 70, 0, 0, 0, 0, 6},
        {0, 24, 70, 0, 0, 0, 6},
        {0, 0, 36, 0, 56, 0, 8},
        {0, 0, 0, 38, 0, 50, 12},
    };
    static final String[] ASH = {AshTrail.ID, SootChild.ID, EmberKeeper.ID};
    static final int[] ASH_W = {30, 20, 6};
    static final String[] DEEP = {PastShadow.ID, NamelessShape.ID};
    static final int[] DEEP_W = {32, 18};
    static final String[] ONLY = {LampEater.ID, CeilingWalker.ID, HeartbeatMimic.ID};
    static final String[] LAYER_NAMES = {"bone", "salt", "storm", "ash"};
    static final String[] ROOM_KINDS = {"den", "pool", "camp"};

    // 開発用とテストが見る、深淵の敵の全部の ID (A 組 9 + B 組 9)
    public static final String[] FOES = {AshTrail.ID, EmberKeeper.ID, SootChild.ID, PastShadow.ID, NamelessShape.ID, LampEater.ID,
            CeilingWalker.ID, HeartbeatMimic.ID, RopeHanger.ID};

    static final Random RAND = new Random();

    private AbyssMobSpawner() {
    }

    // 一回ぶんの湧き (まだ湧かせていない。place で湧く)
    public record Plan(String id, int form, double x, double y, double z, int count) {
    }

    @SubscribeEvent
    static void onTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % EVERY != 0 || !AbyssBuild.built(server)) {
            return;
        }
        ServerLevel level = server.getLevel(AbyssWorld.LEVEL);
        if (level == null) {
            return;
        }
        for (ServerPlayer p : level.players()) {
            if (!p.isAlive() || p.isCreative() || p.isSpectator() || RAND.nextInt(2) != 0) {
                continue;
            }
            BlockPos at = p.blockPosition();
            Plan plan = plan(level, at, RAND, population(level, at));
            if (plan != null) {
                place(level, plan, RAND);
            }
        }
    }

    // 人のまわりの湧きの決め方。湧かせないときは null (人数の上限・休み場・心臓の間・床がない・暗さで見送り)
    public static Plan plan(ServerLevel level, BlockPos center, Random r, int population) {
        if (population >= CAP_NEAR) {
            return null;
        }
        BlockPos foot = findSpot(level, center, r);
        if (foot == null || nearRest(foot, REST_KEEP) || inHeartRoom(foot)) {
            return null;
        }
        int layer = AbyssScaling.layerOf(foot.getY());
        String id = pick(level, layer, center, r);
        if (id == null) {
            return null;
        }
        if (AbyssLight.lightAt(level, foot) >= maxLight(id) && r.nextDouble() < LIGHT_SKIP) {
            return null;
        }
        Plan out = site(level, id, foot, center, 1, r);
        if (out == null) {
            return null;
        }
        BlockPos at = BlockPos.containing(out.x(), out.y(), out.z());
        if (nearRest(at, REST_KEEP) || inHeartRoom(at)) {
            return null;
        }
        // 形は湧く実際の高さで決める (縄の人と天井歩きは床と違う高さに立つことがある)
        return new Plan(out.id(), AbyssScaling.form(nativeOf(id), out.y()), out.x(), out.y(), out.z(), out.count());
    }

    // 湧かせる (人数の上限は見ない。plan の結果をそのまま置く)
    public static void place(ServerLevel level, Plan p, Random r) {
        for (int i = 0; i < p.count(); i++) {
            double ox = i == 0 ? 0 : r.nextDouble() * 2 - 1;
            double oz = i == 0 ? 0 : r.nextDouble() * 2 - 1;
            spawnAt(level, p.id(), p.x() + ox, p.y(), p.z() + oz, p.form());
        }
    }

    // 人のまわりの深淵の敵の数 (A と B を合わせる)
    public static int population(ServerLevel level, BlockPos at) {
        return level.getEntitiesOfClass(GuardMob.class, new AABB(at).inflate(CAP_RANGE),
                e -> e instanceof AbyssMob || e instanceof AbyssMobB).size();
    }

    // 種を一つ湧かせる (段と湧いた高さを決めて入れる)。ID が違えば null
    public static Entity spawnAt(ServerLevel level, String id, double x, double y, double z, int form) {
        AbyssMobKind k = AbyssMobKind.byId(id);
        if (k != null) {
            AbyssMob m = AbyssMobsA.create(k, level, form, y);
            if (m == null) {
                return null;
            }
            m.moveTo(x, y, z, RAND.nextFloat() * 360f, 0f);
            m.addTag(GuardMob.TEMP_TAG);
            level.addFreshEntity(m);
            return m;
        }
        EntityType<?> type = typeOf(id);
        if (type == null) {
            return null;
        }
        Entity e = type.create(level);
        if (!(e instanceof AbyssMobB mob)) {
            return null;
        }
        mob.setPos(x, y, z);
        mob.setYRot(RAND.nextFloat() * 360f);
        tuneFor(mob, id, form);
        mob.addTag(GuardMob.TEMP_TAG);
        level.addFreshEntity(mob);
        return mob;
    }

    // ---------------------------------------------------------------- 表と選び方

    // 高さの層で、組 → 種を選ぶ (湧かせない roll なら null)
    static String pick(ServerLevel level, int layer, BlockPos center, Random r) {
        int roll = r.nextInt(100);
        int acc = 0;
        int g = -1;
        for (int i = 0; i < TABLE[layer].length; i++) {
            acc += TABLE[layer][i];
            if (roll < acc) {
                g = i;
                break;
            }
        }
        if (g < 0) {
            return null;
        }
        if (g <= 2) {
            List<AbyssMobKind> ks = AbyssMobKind.nativeTo(g);
            return ks.get(r.nextInt(ks.size())).id;
        }
        if (g == G_ANY) {
            AbyssMobKind[] all = AbyssMobKind.values();
            return all[r.nextInt(all.length)].id;
        }
        if (g == G_ASH) {
            return weighted(ASH, ASH_W, r);
        }
        if (g == G_DEEP) {
            return weighted(DEEP, DEEP_W, r);
        }
        // 深淵だけ: 縄の人は、吹き抜けの縁 (真ん中から 11〜16) にいる人の前にだけ、たまに一体
        if (ropeOk(level, center) && r.nextDouble() < ROPE_CHANCE) {
            return RopeHanger.ID;
        }
        return ONLY[r.nextInt(ONLY.length)];
    }

    static String weighted(String[] ids, int[] w, Random r) {
        int total = 0;
        for (int x : w) {
            total += x;
        }
        int roll = r.nextInt(total);
        int acc = 0;
        for (int i = 0; i < ids.length; i++) {
            acc += w[i];
            if (roll < acc) {
                return ids[i];
            }
        }
        return ids[ids.length - 1];
    }

    // 縄の人を出してよいか (縁にいて、まだ縄がない)
    static boolean ropeOk(ServerLevel level, BlockPos center) {
        double r = Math.sqrt((double) center.getX() * center.getX() + (double) center.getZ() * center.getZ());
        return r >= ROPE_MIN && r <= ROPE_MAX && !ropeNear(level);
    }

    static boolean ropeNear(ServerLevel level) {
        return !level.getEntitiesOfClass(RopeHanger.class, new AABB(-40, -400, -40, 40, 400, 40)).isEmpty();
    }

    // 種ごとの暗さの決まり: 灯りがこの値以上の所は 60% で見送る。上限なしは Integer.MAX_VALUE
    //   灰の這い跡は灯りで姿が出る種 (見えない所で待つ)。灯りを食う種・熱の種・鼓動もどき・縄の人は上限なし。
    static int maxLight(String id) {
        return switch (id) {
            case AshTrail.ID -> AshTrail.SEE_LIGHT;
            case EmberKeeper.ID, LampEater.ID, HeartbeatMimic.ID, RopeHanger.ID -> Integer.MAX_VALUE;
            default -> DARK_LIGHT;
        };
    }

    // 種の元の層 (段 1 になる層)。A 組は native、B 組の灰の種・深淵だけは灰 (3)、深みの種は深み (4)
    static int nativeOf(String id) {
        AbyssMobKind k = AbyssMobKind.byId(id);
        if (k != null) {
            return k.native_;
        }
        return id.equals(PastShadow.ID) || id.equals(NamelessShape.ID) ? AbyssScaling.LAYER_MAX : 3;
    }

    // 層に湧きうる種の ID (表で重みが 0 でないもの)。自己テストが見る
    public static Set<String> allowedAt(int layer) {
        Set<String> out = new TreeSet<>();
        for (int g = 0; g < TABLE[layer].length; g++) {
            if (TABLE[layer][g] > 0) {
                out.addAll(groupIds(g));
            }
        }
        return out;
    }

    static List<String> groupIds(int g) {
        List<String> out = new ArrayList<>();
        if (g <= 2) {
            for (AbyssMobKind k : AbyssMobKind.nativeTo(g)) {
                out.add(k.id);
            }
        } else if (g == G_ANY) {
            for (AbyssMobKind k : AbyssMobKind.values()) {
                out.add(k.id);
            }
        } else if (g == G_ASH) {
            out.addAll(List.of(ASH));
        } else if (g == G_DEEP) {
            out.addAll(List.of(DEEP));
        } else {
            out.addAll(List.of(ONLY));
            out.add(RopeHanger.ID);
        }
        return out;
    }

    // 湧く place: 種ごとの床・天井・縄の位置 (湧かせない場合は null)
    static Plan site(ServerLevel level, String id, BlockPos foot, BlockPos center, int form, Random r) {
        if (id.equals(RopeHanger.ID)) {
            return new Plan(id, form, 0.5, center.getY() + 2, 0.5, 1);
        }
        if (id.equals(CeilingWalker.ID)) {
            BlockPos c = ceiling(level, foot, r);
            if (c == null) {
                return null;
            }
            return new Plan(id, form, c.getX() + 0.5, c.getY() + 2 - CEILING_HEIGHT, c.getZ() + 0.5, 1);
        }
        int count = id.equals(SootChild.ID) ? 2 + r.nextInt(3) : 1;
        return new Plan(id, form, foot.getX() + 0.5, foot.getY(), foot.getZ() + 0.5, count);
    }

    // 縦穴の道の輪 (半径 PATH_IN〜PATH_OUT。設計図 tools/abyss/gen_abyss_layers.py と同じ)
    static final double PATH_IN = 15.5;
    static final double PATH_OUT = 19.5;

    // 人から 12〜22 ブロックの床を探す。35% は印 (溜まり・巣・野営の部屋) のまわり、残りは縦穴の螺旋の道の上
    // (道は縦穴の中ほどが吹き抜けなので、人のまわりの円で探すと多くが宙か壁になる)。なければ null
    static BlockPos findSpot(ServerLevel level, BlockPos center, Random r) {
        if (r.nextDouble() < ANCHOR_CHANCE) {
            BlockPos m = nearMark(center, r);
            if (m != null) {
                double a = r.nextDouble() * Math.PI * 2;
                double d = 2 + r.nextDouble() * 5;
                BlockPos f = floorNear(level, m.getX() + (int) Math.round(Math.cos(a) * d), m.getY(), m.getZ() + (int) Math.round(Math.sin(a) * d), 4, 6);
                if (f != null && f.distSqr(center) >= SPAWN_MIN * SPAWN_MIN) {
                    return f;
                }
            }
        }
        // 道の上: 人の角度から、弧の長さ 12〜22 だけ前か後ろ。道は回りながら下るので、上下は広めに見る
        double rr = PATH_IN + 0.5 + r.nextDouble() * (PATH_OUT - PATH_IN - 1);
        double arc = (SPAWN_MIN + r.nextDouble() * (SPAWN_MAX - SPAWN_MIN)) / rr * (r.nextBoolean() ? 1 : -1);
        double a = Math.atan2(center.getZ() + 0.5, center.getX() + 0.5) + arc;
        int x = (int) Math.floor(Math.cos(a) * rr);
        int z = (int) Math.floor(Math.sin(a) * rr);
        return floorNear(level, x, center.getY(), z, 10, 14);
    }

    // (x, z) の柱で、y の上 up から下 down までの間の床 (固い面の上に体の二段が空いている所)。上から順に。なければ null
    static BlockPos floorNear(ServerLevel level, int x, int y, int z, int up, int down) {
        BlockPos.MutableBlockPos b = new BlockPos.MutableBlockPos();
        for (int dy = up; dy >= -down; dy--) {
            b.set(x, y + dy, z);
            if (level.getBlockState(b).isFaceSturdy(level, b, Direction.UP) && level.getBlockState(b.above()).isAir()
                    && level.getBlockState(b.above(2)).isAir()) {
                return b.above().immutable();
            }
        }
        return null;
    }

    // 天井の下 (空気の二段の上が固い所)。近くを探す。なければ null
    static BlockPos ceiling(ServerLevel level, BlockPos from, Random r) {
        for (int tries = 0; tries < 12; tries++) {
            BlockPos c = from.offset(r.nextInt(9) - 4, 0, r.nextInt(9) - 4);
            for (int dy = 1; dy <= 5; dy++) {
                BlockPos b = c.offset(0, dy, 0);
                if (level.getBlockState(b).isAir() && level.getBlockState(b.above()).isAir()
                        && !level.getBlockState(b.above(2)).isAir()) {
                    return b;
                }
            }
        }
        return null;
    }

    // 印の近く (溜まり・巣・野営。24 ブロック以内) を一つ
    static BlockPos nearMark(BlockPos center, Random r) {
        List<BlockPos> near = new ArrayList<>();
        for (String layer : LAYER_NAMES) {
            for (String kind : ROOM_KINDS) {
                BlockPos m = AbyssBuild.mark(kind + "_" + layer);
                if (m != null && m.distSqr(center) < ANCHOR_RANGE * ANCHOR_RANGE) {
                    near.add(m);
                }
            }
        }
        return near.isEmpty() ? null : near.get(r.nextInt(near.size()));
    }

    // 休み場 (rest_1〜6) と着く所 (arrival) の近く
    public static boolean nearRest(BlockPos p, double r) {
        BlockPos arrival = AbyssBuild.mark("arrival");
        if (arrival != null && arrival.distSqr(p) < r * r) {
            return true;
        }
        for (int i = 1; i <= 6; i++) {
            BlockPos rest = AbyssBuild.mark("rest_" + i);
            if (rest != null && rest.distSqr(p) < r * r) {
                return true;
            }
        }
        return false;
    }

    // 心臓の間 (大部屋) の中。中心は印 heart の真下 (x と z)、y は床の下から天井の下まで
    public static boolean inHeartRoom(BlockPos p) {
        BlockPos heart = AbyssBuild.mark("heart");
        if (heart == null) {
            return false;
        }
        double dx = p.getX() - heart.getX();
        double dz = p.getZ() - heart.getZ();
        return dx * dx + dz * dz <= HEART_R * HEART_R && p.getY() >= AbyssScaling.HEART_Y - 4 && p.getY() <= HEART_ROOM_TOP;
    }

    // ---------------------------------------------------------------- 種ごとの型と数字

    public static EntityType<?> typeOf(String id) {
        return switch (id) {
            case AshTrail.ID -> AbyssMobsBSetup.ASH_TRAIL.get();
            case EmberKeeper.ID -> AbyssMobsBSetup.EMBER_KEEPER.get();
            case SootChild.ID -> AbyssMobsBSetup.SOOT_CHILD.get();
            case PastShadow.ID -> AbyssMobsBSetup.PAST_SHADOW.get();
            case NamelessShape.ID -> AbyssMobsBSetup.NAMELESS_SHAPE.get();
            case LampEater.ID -> AbyssMobsBSetup.LAMP_EATER.get();
            case CeilingWalker.ID -> AbyssMobsBSetup.CEILING_WALKER.get();
            case HeartbeatMimic.ID -> AbyssMobsBSetup.HEARTBEAT_MIMIC.get();
            case RopeHanger.ID -> AbyssMobsBSetup.ROPE_HANGER.get();
            default -> null;
        };
    }

    static void tuneFor(AbyssMobB mob, String id, int form) {
        switch (id) {
            case AshTrail.ID -> mob.tune(form, AshTrail.HP, AshTrail.DMG);
            case EmberKeeper.ID -> mob.tune(form, EmberKeeper.HP, EmberKeeper.DMG);
            case SootChild.ID -> mob.tune(form, SootChild.HP, SootChild.DMG);
            case PastShadow.ID -> mob.tune(form, PastShadow.HP, PastShadow.DMG);
            case NamelessShape.ID -> mob.tune(form, NamelessShape.HP, NamelessShape.DMG);
            case LampEater.ID -> mob.tune(form, LampEater.HP, LampEater.DMG);
            case CeilingWalker.ID -> mob.tune(form, CeilingWalker.HP, CeilingWalker.DMG);
            case HeartbeatMimic.ID -> mob.tune(form, HeartbeatMimic.HP, HeartbeatMimic.DMG);
            case RopeHanger.ID -> mob.tune(form, RopeHanger.HP, 0);
            default -> {
            }
        }
    }

    // ---------------------------------------------------------------- 開発用: /skyabyss abyss mob <種> [形]

    public static LiteralArgumentBuilder<CommandSourceStack> command() {
        return Commands.literal("mob").then(Commands.argument("id", StringArgumentType.word())
                .executes(c -> spawnCommand(c, -1))
                .then(Commands.argument("form", IntegerArgumentType.integer(1, AbyssScaling.FORM_MAX))
                        .executes(c -> spawnCommand(c, IntegerArgumentType.getInteger(c, "form")))));
    }

    static int spawnCommand(CommandContext<CommandSourceStack> c, int form) {
        CommandSourceStack src = c.getSource();
        String id = StringArgumentType.getString(c, "id");
        if (AbyssMobKind.byId(id) == null && typeOf(id) == null) {
            src.sendFailure(Component.literal("unknown foe (" + String.join(", ", FOES) + ", and the nine abyss kinds)"));
            return 0;
        }
        ServerPlayer p;
        try {
            p = src.getPlayerOrException();
        } catch (Exception e) {
            src.sendFailure(Component.literal("a player is needed"));
            return 0;
        }
        ServerLevel level = p.serverLevel();
        int f = form > 0 ? form : AbyssScaling.form(nativeOf(id), p.getY());
        Vec3 look = p.getLookAngle().multiply(1, 0, 1).normalize().scale(3);
        double x = p.getX() + look.x;
        double z = p.getZ() + look.z;
        double y = p.getY();
        if (id.equals(CeilingWalker.ID)) {
            BlockPos ceil = ceiling(level, BlockPos.containing(x, p.getY(), z), RAND);
            if (ceil == null) {
                src.sendFailure(Component.literal("no ceiling near in front (make one, 2 to 5 blocks above)"));
                return 0;
            }
            x = ceil.getX() + 0.5;
            z = ceil.getZ() + 0.5;
            y = ceil.getY() + 2 - CEILING_HEIGHT;
        }
        Entity e = spawnAt(level, id, x, y, z, f);
        String msg = "spawned " + id + " form " + f + " at y " + (int) y;
        src.sendSuccess(() -> Component.literal(msg), false);
        return e == null ? 0 : 1;
    }
}
