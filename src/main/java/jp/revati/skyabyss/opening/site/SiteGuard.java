package jp.revati.skyabyss.opening.site;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.notice.Notice;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.FireChargeItem;
import net.minecraft.world.item.FlintAndSteelItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityMobGriefingEvent;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

// 荒らしよけと、都の中の安全 (2026-10-21 の試遊: 「町の建築壊せちゃう」「いろんなプレイヤーがいるので荒らし対策も」「町ではプレイヤーは無敵、mod の敵も湧かない・入れない」)。
//   都 (設計図 capital の範囲): ブロックを壊す・置く・バケツ・火打ち石・TNT・爆発・敵の持ち去り・畑の踏み荒らしを止める (空き区画はない)
//   (借りた区画はそのギルドの人だけ、借りていない区画は誰でも)。中の人は傷を受けない (/kill と奈落は別)。敵は湧かず、入ったら 1 秒ごとの見回りで消える。
//   章の小要素 (割れ殻の洞・骨の坑など): 設計図で置いたブロックは壊せない (まわりの自然の地面は掘れる。割れ殻の洞は地表の口だけ壊せる)。
//   小要素の箱の中にはブロックを置けない・バケツ・火も使えない (あとから来た人の道をふさがない。2026-10-22 の二人の試遊)。
//   OP のクリエイティブは除く
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class SiteGuard {
    private SiteGuard() {
    }

    // 都の範囲 (世界の座標)。都がまだ建っていなければ null
    public record Area(int x0, int y0, int z0, int x1, int y1, int z1) {
        public boolean contains(BlockPos p) {
            return p.getX() >= x0 && p.getX() <= x1 && p.getZ() >= z0 && p.getZ() <= z1 && p.getY() >= y0 && p.getY() <= y1;
        }

        public boolean containsXZ(double x, double z) {
            return x >= x0 && x < x1 + 1 && z >= z0 && z < z1 + 1;
        }

        AABB box() {
            return new AABB(x0, y0, z0, x1 + 1, y1 + 1, z1 + 1);
        }
    }

    // 自己テストの世界では都が世界の出発点にあり、ほかの段が偽の人や敵をそこで動かすので、安全 (無敵・湧かない・見回り) は止める。
    // 荒らしよけ (壊す・置く) は止めない。この仕組みの段 (SiteGuardSelfTest) だけ testing = true にして確かめる
    public static volatile boolean testing;

    private static boolean safetyOff() {
        return jp.revati.skyabyss.SelfTestRunner.enabled() && !testing;
    }

    private static Area capital;
    private static long capitalKey = Long.MIN_VALUE;
    // 小要素ごとの守るブロック (場所の鍵 → 世界の座標の long)
    private static final Map<String, Set<Long>> SITE_CELLS = new HashMap<>();
    private static long sitesStamp = Long.MIN_VALUE;
    private static final List<Set<Long>> SITE_LIST = new ArrayList<>();
    // 小要素の外接の箱 ({x0, y0, z0, x1, y1, z1})。中にブロックを置かない・液体を流さない (あとから来た人の道をふさがない)
    private static final List<int[]> SITE_BOXES = new ArrayList<>();

    // ---------------------------------------------------------------- 範囲
    @Nullable
    public static Area capital(MinecraftServer server) {
        SiteData.Entry e = SiteData.get(server.overworld()).first("shelter");
        if (e == null || !e.built) {
            return null;
        }
        long key = e.pos.asLong() * 31 + e.yaw;
        if (key == capitalKey && capital != null) {
            return capital;
        }
        Blueprint bp = Blueprint.get("capital");
        if (bp == null) {
            return null;
        }
        int rot = SiteFinder.rot(e.yaw);
        boolean mirror = SiteFinder.mirror(e.yaw);
        BlockPos origin = originOf(e, bp, rot, mirror);
        if (origin == null) {
            origin = e.pos.below();
        }
        int[] r = bp.worldRect(rot, mirror, origin);
        // 下は五章の大坑道の底 (設計図の y -101) まで
        capital = new Area(r[0], origin.getY() - 112, r[1], r[2], origin.getY() + 96, r[3]);
        capitalKey = key;
        return capital;
    }

    // 建てたときの設計図の原点 (SiteJob.mark は transform(x, y + 1, z, …, origin))。写した付属の点の一つから逆に求める
    @Nullable
    public static BlockPos originOf(SiteData.Entry e, Blueprint bp, int rot, boolean mirror) {
        for (int i = 0; i < e.extraNames.size(); i++) {
            int[] m = bp.marks.get(e.extraNames.get(i));
            if (m == null) {
                continue;
            }
            BlockPos rel = Blueprint.transform(m[0], m[1] + 1, m[2], rot, mirror, BlockPos.ZERO);
            return e.extraPos.get(i).subtract(rel);
        }
        return null;
    }

    public static boolean inCapital(Level level, BlockPos pos) {
        if (level.isClientSide || level.dimension() != Level.OVERWORLD || level.getServer() == null) {
            return false;
        }
        Area a = capital(level.getServer());
        return a != null && a.contains(pos);
    }

    // 土地の町 (大陸の世界の鯨見・潮・風・灰の町。world/continent/ContinentTowns) の範囲。建った町だけ
    private static final List<Area> TOWNS = new ArrayList<>();
    private static long townsStamp = Long.MIN_VALUE;

    public static List<Area> towns(MinecraftServer server) {
        List<SiteData.Entry> list = SiteData.get(server.overworld()).entries(jp.revati.skyabyss.world.continent.ContinentTowns.KIND);
        long stamp = 17;
        for (SiteData.Entry e : list) {
            if (e.built) {
                stamp = stamp * 31 + e.pos.asLong() * 7 + e.yaw + e.blueprint.hashCode();
            }
        }
        if (stamp == townsStamp) {
            return TOWNS;
        }
        TOWNS.clear();
        for (SiteData.Entry e : list) {
            Blueprint bp = e.built ? Blueprint.get(e.blueprint.isEmpty() ? "town_" + jp.revati.skyabyss.world.continent.ContinentTowns.region(e.index) : e.blueprint) : null;
            if (bp == null) {
                continue;
            }
            int rot = SiteFinder.rot(e.yaw);
            boolean mirror = SiteFinder.mirror(e.yaw);
            BlockPos origin = originOf(e, bp, rot, mirror);
            if (origin == null) {
                origin = e.pos.below();
            }
            int[] r = bp.worldRect(rot, mirror, origin);
            TOWNS.add(new Area(r[0], origin.getY() - 24, r[1], r[2], origin.getY() + 64, r[3]));
        }
        townsStamp = stamp;
        return TOWNS;
    }

    // 都か土地の町の中か (守り・安全・敵の湧きと見回りは、都と町で同じ)
    public static boolean inTown(Level level, BlockPos pos) {
        if (inCapital(level, pos)) {
            return true;
        }
        if (level.isClientSide || level.dimension() != Level.OVERWORLD || level.getServer() == null) {
            return false;
        }
        for (Area a : towns(level.getServer())) {
            if (a.contains(pos)) {
                return true;
            }
        }
        return false;
    }

    // 小要素の守るブロックか
    static boolean siteBlock(ServerLevel level, BlockPos pos) {
        refreshSites(level);
        long k = pos.asLong();
        for (Set<Long> s : SITE_LIST) {
            if (s.contains(k)) {
                return true;
            }
        }
        return false;
    }

    private static void refreshSites(ServerLevel level) {
        SiteData data = SiteData.get(level.getServer().overworld());
        long stamp = 0;
        for (SiteData.Entry e : data.entries()) {
            if (!e.blueprint.isEmpty() && e.built) {
                stamp = stamp * 31 + e.pos.asLong() + e.yaw;
            }
        }
        if (stamp == sitesStamp) {
            return;
        }
        SITE_LIST.clear();
        SITE_BOXES.clear();
        for (SiteData.Entry e : data.entries()) {
            if (e.blueprint.isEmpty() || !e.built) {
                continue;
            }
            String key = e.blueprint + "@" + e.pos.asLong() + "/" + e.yaw;
            Set<Long> cells = SITE_CELLS.computeIfAbsent(key, k -> cellsOf(e));
            if (!cells.isEmpty()) {
                SITE_LIST.add(cells);
                int[] box = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
                for (long k : cells) {
                    int x = BlockPos.getX(k), y = BlockPos.getY(k), z = BlockPos.getZ(k);
                    box[0] = Math.min(box[0], x);
                    box[1] = Math.min(box[1], y);
                    box[2] = Math.min(box[2], z);
                    box[3] = Math.max(box[3], x);
                    box[4] = Math.max(box[4], y);
                    box[5] = Math.max(box[5], z);
                }
                SITE_BOXES.add(box);
            }
        }
        sitesStamp = stamp;
    }

    private static Set<Long> cellsOf(SiteData.Entry e) {
        Set<Long> out = new HashSet<>();
        Blueprint bp = Blueprint.get(e.blueprint);
        if (bp == null) {
            return out;
        }
        int rot = SiteFinder.rot(e.yaw);
        boolean mirror = SiteFinder.mirror(e.yaw);
        BlockPos origin = e.pos.below();
        boolean cave = e.blueprint.equals("ch1_cleft_shell_cave");
        for (Blueprint.Cell c : bp.cells) {
            // 鉱石 (骨の坑の鉄の筋など。掘ってよい) は守らない
            if (c.state().isAir() || c.state().is(net.neoforged.neoforge.common.Tags.Blocks.ORES)) {
                continue;
            }
            // 割れ殻の洞: 地表の口 (門の柱と楣・口の低い壁。部品の z -1..3、地面より上) は壊せる
            if (cave && c.z() <= 3 && c.y() >= -1) {
                continue;
            }
            out.add(Blueprint.transform(c.x(), c.y() + 1, c.z(), rot, mirror, origin).asLong());
        }
        return out;
    }

    // 小要素の箱の中か (建物の中のすき間・部屋も含む)
    static boolean inSite(ServerLevel level, BlockPos p) {
        refreshSites(level);
        for (int[] b : SITE_BOXES) {
            if (p.getX() >= b[0] && p.getX() <= b[3] && p.getY() >= b[1] && p.getY() <= b[4] && p.getZ() >= b[2] && p.getZ() <= b[5]) {
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- 壊す・置く
    private static boolean bypass(@Nullable Player p) {
        return p != null && p.isCreative() && p.hasPermissions(2);
    }

    // 都の中で、この人がここのブロックを変えてはいけないか (都に空き区画はない。35 の 7 章)
    private static boolean capitalLocked(Level level, @Nullable Player p, BlockPos pos) {
        return !bypass(p) && inTown(level, pos);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    static void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || bypass(event.getPlayer())) {
            return;
        }
        BlockPos pos = event.getPos();
        if (capitalLocked(level, event.getPlayer(), pos) || (level.dimension() == Level.OVERWORLD && siteBlock(level, pos))) {
            event.setCanceled(true);
            Notice.hint(event.getPlayer(), "message.skyabyss.guard.block");
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof Level level) || level.isClientSide) {
            return;
        }
        Player p = event.getEntity() instanceof Player pl ? pl : null;
        if (p == null) {
            // 人でない物 (エンダーマン・雪だるま…) は都の中に置かない
            if (inTown(level, event.getPos())) {
                event.setCanceled(true);
            }
            return;
        }
        if (capitalLocked(level, p, event.getPos())
                || (!bypass(p) && level instanceof ServerLevel sl && level.dimension() == Level.OVERWORLD && inSite(sl, event.getPos()))) {
            event.setCanceled(true);
            Notice.hint(p, "message.skyabyss.guard.block");
        }
    }

    // バニラの畑の踏み荒らし
    @SubscribeEvent
    static void onTrample(BlockEvent.FarmlandTrampleEvent event) {
        if (event.getLevel() instanceof Level level && inTown(level, event.getPos())) {
            event.setCanceled(true);
        }
    }

    // バケツ (水・溶岩・粉雪)・火打ち石・ファイヤーチャージ・TNT を都の中で使わない
    @SubscribeEvent(priority = EventPriority.HIGH)
    static void onUseOnBlock(PlayerInteractEvent.RightClickBlock event) {
        Level level = event.getLevel();
        if (level.isClientSide || bypass(event.getEntity())) {
            return;
        }
        ItemStack stack = event.getItemStack();
        boolean risky = stack.getItem() instanceof BucketItem || stack.getItem() instanceof FlintAndSteelItem || stack.getItem() instanceof FireChargeItem
                || stack.is(Items.TNT) || stack.is(Items.END_CRYSTAL) || stack.is(Items.POWDER_SNOW_BUCKET);
        if (!risky) {
            return;
        }
        BlockPos at = event.getPos().relative(event.getFace() == null ? net.minecraft.core.Direction.UP : event.getFace());
        boolean site = level instanceof ServerLevel sl && level.dimension() == Level.OVERWORLD && (inSite(sl, event.getPos()) || inSite(sl, at));
        if (capitalLocked(level, event.getEntity(), event.getPos()) || capitalLocked(level, event.getEntity(), at) || site) {
            event.setCanceled(true);
            Notice.hint(event.getEntity(), "message.skyabyss.guard.block");
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    static void onUseItem(PlayerInteractEvent.RightClickItem event) {
        Level level = event.getLevel();
        if (level.isClientSide || bypass(event.getEntity())) {
            return;
        }
        // 液体へ向けたバケツ (水を汲む・流す) も
        if (event.getItemStack().getItem() instanceof BucketItem && inTown(level, event.getEntity().blockPosition())
                && capitalLocked(level, event.getEntity(), event.getEntity().blockPosition())) {
            event.setCanceled(true);
            Notice.hint(event.getEntity(), "message.skyabyss.guard.block");
        }
    }

    // 爆発 (クリーパー・TNT・ほか) は都と小要素のブロックを壊さない
    @SubscribeEvent
    static void onExplosion(ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) {
            return;
        }
        event.getAffectedBlocks().removeIf(p -> inTown(level, p) || siteBlock(level, p));
    }

    // 敵がブロックを持ち去る・壊す (エンダーマン・ラヴェジャー…)
    @SubscribeEvent
    static void onGriefing(EntityMobGriefingEvent event) {
        Entity e = event.getEntity();
        if (e.level() instanceof ServerLevel level && inTown(level, e.blockPosition())) {
            event.setCanGrief(false);
        }
    }

    // ---------------------------------------------------------------- 都の中の安全
    @SubscribeEvent(priority = EventPriority.HIGH)
    static void onDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || p instanceof net.neoforged.neoforge.common.util.FakePlayer || safetyOff()) {
            return;
        }
        if (event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY) || event.getSource().is(DamageTypes.FELL_OUT_OF_WORLD)) {
            return;
        }
        if (inTown(p.level(), p.blockPosition())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    static void onSpawn(FinalizeSpawnEvent event) {
        Mob mob = event.getEntity();
        if (safetyOff()) {
            return;
        }
        if (mob instanceof Enemy && mob.level() instanceof ServerLevel level && inTown(level, BlockPos.containing(event.getX(), event.getY(), event.getZ()))) {
            event.setSpawnCancelled(true);
        }
    }

    // 0.25 秒ごと: 都と土地の町の中に入った敵を、いちばん近い縁の外へ押し戻す (消さない。2026-10-27 の声「町に近づいたら敵が消えるけど、近づけないだけにして」)
    @SubscribeEvent
    static void onTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % 100 == 41) {
            SiteData.Entry shelter = SiteData.get(server.overworld()).first("shelter");
            if (shelter != null && shelter.built) {
                ShelterBuilder.keepLabels(server.overworld(), shelter);
            }
        }
        if (server.getTickCount() % 5 != 2 || safetyOff()) {
            return;
        }
        List<Area> areas = new ArrayList<>(towns(server));
        Area cap = capital(server);
        if (cap != null) {
            areas.add(cap);
        }
        ServerLevel level = server.overworld();
        for (Area a : areas) {
            sweepEnemies(level, a);
        }
    }

    // 範囲の中の敵を、いちばん近い縁の外 (縁から 1.5) へ押し戻し、歩みを止める。高さはそこの地面
    private static void sweepEnemies(ServerLevel level, Area a) {
        for (Mob m : level.getEntitiesOfClass(Mob.class, a.box(), m -> m instanceof Enemy && m.isAlive())) {
            double x = m.getX();
            double z = m.getZ();
            double west = x - a.x0();
            double east = a.x1() + 1 - x;
            double north = z - a.z0();
            double south = a.z1() + 1 - z;
            double min = Math.min(Math.min(west, east), Math.min(north, south));
            double nx = x;
            double nz = z;
            if (min == west) {
                nx = a.x0() - 1.5;
            } else if (min == east) {
                nx = a.x1() + 2.5;
            } else if (min == north) {
                nz = a.z0() - 1.5;
            } else {
                nz = a.z1() + 2.5;
            }
            int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(nx), (int) Math.floor(nz));
            m.getNavigation().stop();
            // 町の中の人は狙わない (縁で行き来しないように)
            if (m.getTarget() != null && a.containsXZ(m.getTarget().getX(), m.getTarget().getZ())) {
                m.setTarget(null);
            }
            m.teleportTo(nx, Math.max(y, level.getMinBuildHeight() + 1), nz);
            m.setDeltaMovement(m.getDeltaMovement().multiply(0, 1, 0));
        }
    }

    // 開発・自己テスト用: 覚えた範囲を捨てる
    public static void reset() {
        capital = null;
        TOWNS.clear();
        townsStamp = Long.MIN_VALUE;
        capitalKey = Long.MIN_VALUE;
        SITE_CELLS.clear();
        SITE_LIST.clear();
        SITE_BOXES.clear();
        sitesStamp = Long.MIN_VALUE;
    }

    static boolean levelAccessorInCapital(LevelAccessor l, BlockPos p) {
        return l instanceof Level level && inTown(level, p);
    }
}
