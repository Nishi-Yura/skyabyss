package jp.revati.skyabyss.world.continent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.cinematic.Cinematics;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.opening.site.SiteData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

// はじまりの島の道 (2026-10-26 の試遊「島が雑」「道をそれても行けてしまう」「島から都へ急に行って話がない」。Wynncraft の King's Recruit を手本に)。
// 谷の一本道 (地形は gen_continent.shape_island) を、人ごとの目的の順で歩く:
//   0. 野営地で職を選ぶ (island_job。野営地のそばに立つと職を選ぶ画面 (rpg/skill/JobChoice)。選ぶと始めの武器と 1 行目の技)。
//      2026-10-26 の声「職業選んでから戦いでしょ」: 選ぶまでは島の目的が進まず、峠の野盗も立たない
//   1. 野営地の帳面 (今までどおり examine_camp)
//   2. 足跡の窪地を見る (island_prints。窪地の足跡の 40 以内か、足跡の伝承)
//   3. 峠の野盗を退ける (island_bandits。島で野盗を 3 人。足りなければ野営に出る)。峠の柵は、誰かが退けると開く (世界に一つ)
//   4. 旅の休み場の焚き火で休む (island_rest。そばに 3 秒)
//   5. 宿り木の祠に祈る (island_shrine。そばに立つと済む。ここで目覚めるようになり、技の画面 (K) の手がかり)
//   6. 船着き場の渡し守と話す (take_ferry)。渡し守は 1〜5 を済ませた人だけを乗せる (会話の印 island_ready)。乗ると渡りの場面 (ferry_crossing) →
//      鐘の都の南の門の外で、都を見上げる場面 (capital_arrival)
// 印は人の永続データの KEY (倒れても残る。util/LifeKeep)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class IslandTrail {
    public static final String KEY = "skyabyss_island";
    static final String KILLS = "skyabyss_island_kills";
    public static final int PRINTS = 1;
    public static final int BANDITS = 2;
    public static final int REST = 4;
    public static final int SHRINE = 8;
    public static final int ALL = PRINTS | BANDITS | REST | SHRINE;
    public static final int BANDITS_NEEDED = 3;
    public static final String FERRYMAN = "island_ferryman";
    public static final String FERRYMAN_ROLE = "skyabyss:guest_island_ferryman";
    public static final String READY_FLAG = "island_ready";
    public static final String GATE_KIND = "island_gate";
    public static final String CROSSING = "ferry_crossing";
    public static final String ARRIVAL = "capital_arrival";
    static final String TEMP_TAG = "skyabyss_ferry_temp";
    static final double PRINTS_NEAR = 40;
    static final double REST_NEAR = 6;
    static final double SHRINE_NEAR = 5;
    // 野営地で職を選ぶ画面が開く近さ (離れると閉じる)
    static final double CAMP_NEAR = 10;
    static final double BANDIT_NEAR = 36;
    static final String LEADER_TAG = "skyabyss_island_leader";
    static final net.minecraft.resources.ResourceLocation LEADER_HP = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "island_leader_hp");
    static final int LEADER_COINS = 5;
    static final int GATE_HALF = 14;
    // 渡りの場面: 島の船着き場から都の向きへ、この先の海の上
    static final int SEA_AHEAD = 90;

    private record Crossing(Boat boat, @Nullable Entity ferryman, long start) {
    }

    private static final Map<UUID, Crossing> CROSSING_NOW = new HashMap<>();
    // 都に着いて、都を見上げる場面を始める時刻 (チャンクを待つ)
    private static final Map<UUID, Long> ARRIVE_AT = new HashMap<>();

    private IslandTrail() {
    }

    // ---------------------------------------------------------------- 状態
    public static int done(ServerPlayer p) {
        return p.getPersistentData().getInt(KEY);
    }

    public static boolean has(ServerPlayer p, int bit) {
        return (done(p) & bit) != 0;
    }

    static void mark(ServerPlayer p, int bit) {
        int now = done(p);
        if ((now & bit) != 0) {
            return;
        }
        p.getPersistentData().putInt(KEY, now | bit);
        if ((now | bit) == ALL) {
            setReady(p, true);
        }
    }

    static void setReady(ServerPlayer p, boolean on) {
        var flags = jp.revati.skyabyss.npc.NpcBonds.of(p).bond(FERRYMAN_ROLE).flags();
        boolean changed = on ? flags.add(READY_FLAG) : flags.remove(READY_FLAG);
        if (changed && !(p instanceof net.neoforged.neoforge.common.util.FakePlayer)) {
            jp.revati.skyabyss.npc.NpcBonds.sync(p);
        }
    }

    // 島の谷の図がある世界 (天と淵の大陸の今の図) か
    public static boolean active(ServerLevel level) {
        return level.dimension() == Level.OVERWORLD && level.getChunkSource().getGenerator() instanceof ContinentChunkGenerator
                && ContinentLayout.get().island("gate") != null;
    }

    // 今の目的 (帯の id と目印)。島の外・渡った人・島の図のない世界は null
    @Nullable
    public static Object[] objective(ServerPlayer p) {
        if (!active(p.serverLevel()) || ContinentOutskirts.ferried(p)) {
            return null;
        }
        ContinentLayout lay = ContinentLayout.get();
        if (jp.revati.skyabyss.rpg.skill.JobChoice.chosen(p) == null) {
            return new Object[] {"island_job", ground(p.serverLevel(), lay.start())};
        }
        if (!has(p, PRINTS)) {
            BlockPos v = lay.islandPrints().isEmpty() ? lay.island("prints_view") : lay.islandPrints().get(0);
            return new Object[] {"island_prints", ground(p.serverLevel(), v)};
        }
        if (!has(p, BANDITS)) {
            return new Object[] {"island_bandits", ground(p.serverLevel(), lay.island("bandits"))};
        }
        if (!has(p, REST)) {
            return new Object[] {"island_rest", ground(p.serverLevel(), restPos(p.serverLevel(), 0))};
        }
        if (!has(p, SHRINE)) {
            return new Object[] {"island_shrine", ground(p.serverLevel(), restPos(p.serverLevel(), 1))};
        }
        return new Object[] {"take_ferry", ContinentOutskirts.ferryPos(p.serverLevel())};
    }

    private static BlockPos ground(ServerLevel level, BlockPos at) {
        return new BlockPos(at.getX(), level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at.getX(), at.getZ()), at.getZ());
    }

    // 休み場 (0) と祠 (1) の立つ所 (建っていなければ図の場所)
    static BlockPos restPos(ServerLevel level, int index) {
        for (SiteData.Entry e : SiteData.get(level).entries(ContinentOutskirts.KIND)) {
            if (e.index == index) {
                BlockPos heal = e.extra("heal");
                return heal != null ? heal : e.pos;
            }
        }
        return ContinentLayout.get().island(index == 0 ? "fire" : "shrine");
    }

    // ---------------------------------------------------------------- 見回り (1 秒ごと)
    @SubscribeEvent
    static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || p.isSpectator() || (p.tickCount + p.getId()) % 20 != 0
                || !(p.level() instanceof ServerLevel level) || !active(level) || ContinentOutskirts.ferried(p)) {
            return;
        }
        update(level, p);
    }

    // 一人の進みを見る (自己テストからも)
    static void update(ServerLevel level, ServerPlayer p) {
        ContinentLayout lay = ContinentLayout.get();
        buildGate(level);
        buildSigns(level);
        // 職を選ぶまでは、ほかの目的は進まない (野営地のそばで選ぶ画面が開く)
        if (jp.revati.skyabyss.rpg.skill.JobChoice.chosen(p) == null) {
            double camp = Math.hypot(p.getX() - lay.start().getX(), p.getZ() - lay.start().getZ());
            if (camp < CAMP_NEAR) {
                jp.revati.skyabyss.rpg.skill.JobChoice.prompt(p);
            } else if (camp > CAMP_NEAR + 4) {
                jp.revati.skyabyss.rpg.skill.JobChoice.away(p);
            }
            return;
        }
        if (!has(p, PRINTS)) {
            boolean near = jp.revati.skyabyss.lore.Lore.state(p).has("prologue/footprints");
            for (BlockPos q : lay.islandPrints()) {
                near |= Math.hypot(p.getX() - q.getX(), p.getZ() - q.getZ()) < PRINTS_NEAR;
            }
            if (near) {
                mark(p, PRINTS);
            }
        }
        if (!has(p, BANDITS)) {
            if (p.getPersistentData().getInt(KILLS) >= BANDITS_NEEDED) {
                mark(p, BANDITS);
                openGate(level);
                Notice.objective(p, "message.skyabyss.island.bandits_done");
            } else {
                keepBandits(level, p);
            }
        }
        if (has(p, BANDITS) && !has(p, REST) && restPos(level, 0).distToCenterSqr(p.position()) < REST_NEAR * REST_NEAR) {
            mark(p, REST);
            Notice.hint(p, "message.skyabyss.island.rested");
        }
        // 祠に祈る (職は野営地で選んである)
        double shrine = restPos(level, 1).distToCenterSqr(p.position());
        if (has(p, REST) && shrine < SHRINE_NEAR * SHRINE_NEAR && !has(p, SHRINE)) {
            mark(p, SHRINE);
            Notice.objective(p, "message.skyabyss.island.prayed");
            Notice.hint(p, "message.skyabyss.island.skill_hint");
        }
        if ((done(p) & ALL) == ALL) {
            setReady(p, true);
        }
        BlockPos ferry = ContinentOutskirts.ferryPos(level);
        if (ferry != null && ferry.distToCenterSqr(p.position()) < 80 * 80) {
            jp.revati.skyabyss.town.RegionFolk.ensure(level, "island", "dock", ferry);
        }
    }

    // ---------------------------------------------------------------- 峠の野盗
    static AABB banditBox() {
        BlockPos b = ContinentLayout.get().island("bandits");
        return new AABB(b.getX() - BANDIT_NEAR, -64, b.getZ() - BANDIT_NEAR, b.getX() + BANDIT_NEAR, 320, b.getZ() + BANDIT_NEAR);
    }

    static boolean bandit(Entity e) {
        return e.getType() == EntityType.PILLAGER || e.getType() == EntityType.VINDICATOR;
    }

    // 退ける数に足りない人が野営の近くにいれば、足りない分の野盗を野営に立たせる (ほかの人がすでに退けていても、その人の分)
    static void keepBandits(ServerLevel level, ServerPlayer p) {
        BlockPos b = ContinentLayout.get().island("bandits");
        if (Math.hypot(p.getX() - b.getX(), p.getZ() - b.getZ()) > BANDIT_NEAR + 20) {
            return;
        }
        int need = BANDITS_NEEDED - p.getPersistentData().getInt(KILLS);
        int alive = level.getEntitiesOfClass(Mob.class, banditBox(), m -> m.isAlive() && bandit(m)).size();
        for (int i = alive; i < Math.min(need, 3); i++) {
            EntityType<? extends Mob> type = i == 2 ? EntityType.PILLAGER : EntityType.VINDICATOR;
            Mob m = type.create(level);
            if (m == null) {
                continue;
            }
            int x = b.getX() + (i - 1) * 3;
            int z = b.getZ() + (i % 2 == 0 ? 2 : -2);
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            m.moveTo(x + 0.5, y, z + 0.5, level.random.nextFloat() * 360f, 0f);
            m.finalizeSpawn(level, level.getCurrentDifficultyAt(m.blockPosition()), MobSpawnType.EVENT, null);
            m.setPersistenceRequired();
            if (i == 1) {
                // 峠の頭目 (Wynncraft の最初の戦いの小さな頭のように。名札つき・体力 2 倍・倒すと鐘銭)
                m.setCustomName(Component.translatable("entity.skyabyss.island_bandit_leader"));
                m.setCustomNameVisible(true);
                m.addTag(LEADER_TAG);
                var hp = m.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
                if (hp != null) {
                    hp.addPermanentModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(LEADER_HP, 1.0,
                            net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
                    m.setHealth(m.getMaxHealth());
                }
            }
            level.addFreshEntity(m);
        }
    }

    @SubscribeEvent
    static void onDeath(LivingDeathEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer p) || !bandit(event.getEntity()) || !(p.level() instanceof ServerLevel level)
                || !active(level) || ContinentOutskirts.ferried(p) || has(p, BANDITS)) {
            return;
        }
        BlockPos b = ContinentLayout.get().island("bandits");
        if (Math.hypot(event.getEntity().getX() - b.getX(), event.getEntity().getZ() - b.getZ()) > BANDIT_NEAR + 30) {
            return;
        }
        if (event.getEntity().getTags().contains(LEADER_TAG)) {
            jp.revati.skyabyss.facility.Coins.give(p, LEADER_COINS);
            Notice.hint(p, "message.skyabyss.island.leader_down", LEADER_COINS);
        }
        int n = p.getPersistentData().getInt(KILLS) + 1;
        p.getPersistentData().putInt(KILLS, n);
        Notice.bar(p, Component.translatable("message.skyabyss.island.bandit_count", Math.min(n, BANDITS_NEEDED), BANDITS_NEEDED));
    }

    // ---------------------------------------------------------------- 峠の柵 (世界に一つ。誰かが野盗を退けると開く)
    @Nullable
    static SiteData.Entry gateEntry(ServerLevel level) {
        SiteData data = SiteData.get(level);
        SiteData.Entry e = data.first(GATE_KIND);
        if (e == null) {
            BlockPos g = ContinentLayout.get().island("gate");
            if (g == null) {
                return null;
            }
            e = data.add(GATE_KIND, 0, g, 0);
            // 建てるのは IslandTrail (SiteBuilds の部品ではない)。柵のブロックは buildGate が、人が近くに来たときに置く
            e.built = true;
            data.setDirty();
        }
        return e;
    }

    // 柵の列 (道を横切る。道の向きに直角)。{x, z} の並び
    static int[][] gateColumns() {
        BlockPos g = ContinentLayout.get().island("gate");
        List<BlockPos> road = ContinentLayout.get().roads().get(0);
        double best = Double.MAX_VALUE;
        double dx = 1;
        double dz = 0;
        for (int i = 0; i + 1 < road.size(); i++) {
            BlockPos a = road.get(i);
            BlockPos b = road.get(i + 1);
            double d = Math.hypot((a.getX() + b.getX()) / 2.0 - g.getX(), (a.getZ() + b.getZ()) / 2.0 - g.getZ());
            if (d < best) {
                best = d;
                double len = Math.max(1e-6, Math.hypot(b.getX() - a.getX(), b.getZ() - a.getZ()));
                dx = (b.getX() - a.getX()) / len;
                dz = (b.getZ() - a.getZ()) / len;
            }
        }
        java.util.LinkedHashSet<Long> seen = new java.util.LinkedHashSet<>();
        for (double k = -GATE_HALF; k <= GATE_HALF; k += 0.5) {
            int x = (int) Math.round(g.getX() - dz * k);
            int z = (int) Math.round(g.getZ() + dx * k);
            seen.add(BlockPos.asLong(x, 0, z));
        }
        int[][] out = new int[seen.size()][];
        int i = 0;
        for (long l : seen) {
            out[i++] = new int[] {BlockPos.getX(l), BlockPos.getZ(l)};
        }
        return out;
    }

    static void buildGate(ServerLevel level) {
        SiteData.Entry e = gateEntry(level);
        if (e == null || e.extra("built") != null || e.extra("open") != null || !level.isLoaded(e.pos)) {
            return;
        }
        int[][] cols = gateColumns();
        for (int[] c : cols) {
            if (!level.isLoaded(new BlockPos(c[0], 0, c[1]))) {
                return;
            }
        }
        BlockState log = Blocks.SPRUCE_LOG.defaultBlockState();
        BlockState fence = Blocks.SPRUCE_FENCE.defaultBlockState();
        for (int i = 0; i < cols.length; i++) {
            int x = cols[i][0];
            int z = cols[i][1];
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            boolean post = i % 4 == 0;
            for (int k = 0; k < (post ? 3 : 2); k++) {
                BlockPos at = new BlockPos(x, y + k, z);
                if (level.getBlockState(at).canBeReplaced()) {
                    level.setBlock(at, post ? log : fence, 3);
                }
            }
        }
        e.extra("built", e.pos);
        SiteData.get(level).setDirty();
    }

    static void openGate(ServerLevel level) {
        SiteData.Entry e = gateEntry(level);
        if (e == null || e.extra("open") != null) {
            return;
        }
        for (int[] c : gateColumns()) {
            int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, c[0], c[1]);
            for (int y = top - 4; y <= top + 1; y++) {
                BlockPos at = new BlockPos(c[0], y, c[1]);
                BlockState s = level.getBlockState(at);
                if (s.is(Blocks.SPRUCE_LOG) || s.is(Blocks.SPRUCE_FENCE)) {
                    level.setBlock(at, Blocks.AIR.defaultBlockState(), 3);
                    if (y % 2 == 0) {
                        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, s), c[0] + 0.5, y + 0.5, c[1] + 0.5, 6, 0.3, 0.3, 0.3, 0.05);
                    }
                }
            }
        }
        level.playSound(null, e.pos, SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, SoundSource.BLOCKS, 1.2f, 0.8f);
        e.extra("open", e.pos);
        SiteData.get(level).setDirty();
        for (ServerPlayer q : level.players()) {
            if (q.distanceToSqr(e.pos.getX(), q.getY(), e.pos.getZ()) < 96 * 96) {
                Notice.hint(q, "message.skyabyss.island.gate_open");
            }
        }
    }

    // ---------------------------------------------------------------- 道の分かれ目の札 (Wynncraft の道しるべのように。首の入口から 5 入った所に、道の方を向けて)
    static final String SIGNS_KIND = "island_signs";

    static void buildSigns(ServerLevel level) {
        SiteData data = SiteData.get(level);
        SiteData.Entry e = data.first(SIGNS_KIND);
        if (e == null) {
            BlockPos g = ContinentLayout.get().island("gate");
            if (g == null) {
                return;
            }
            e = data.add(SIGNS_KIND, 0, g, 0);
            e.built = true;
            data.setDirty();
        }
        int i = 0;
        for (Map.Entry<BlockPos, String> s : ContinentLayout.get().islandSigns().entrySet()) {
            String done = "s" + i++;
            if (e.extra(done) != null) {
                continue;
            }
            BlockPos at = s.getKey();
            BlockPos to = signTarget(s.getValue());
            if (to == null || !level.isLoaded(at)) {
                continue;
            }
            double dx = to.getX() - at.getX();
            double dz = to.getZ() - at.getZ();
            double len = Math.max(1, Math.hypot(dx, dz));
            int x = (int) Math.round(at.getX() + dx / len * 5);
            int z = (int) Math.round(at.getZ() + dz / len * 5);
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos p = new BlockPos(x, y, z);
            if (!level.getBlockState(p).canBeReplaced()) {
                e.extra(done, p);
                continue;
            }
            // 札の表は道の方 (行き先と逆の向き)。回転 0 = 南向き、16 段
            double face = Math.toDegrees(Math.atan2(dx / len, -dz / len));
            int rot = Math.floorMod((int) Math.round(face / 22.5), 16);
            level.setBlock(p, Blocks.SPRUCE_SIGN.defaultBlockState().setValue(net.minecraft.world.level.block.StandingSignBlock.ROTATION, rot), 3);
            if (level.getBlockEntity(p) instanceof net.minecraft.world.level.block.entity.SignBlockEntity be) {
                be.setText(new net.minecraft.world.level.block.entity.SignText()
                        .setMessage(1, Component.translatable("sign.skyabyss.island." + s.getValue()))
                        .setMessage(2, Component.literal("→")), true);
                be.setWaxed(true);
                be.setChanged();
                level.sendBlockUpdated(p, be.getBlockState(), be.getBlockState(), 3);
            }
            e.extra(done, p);
            data.setDirty();
        }
    }

    @Nullable
    static BlockPos signTarget(String name) {
        ContinentLayout lay = ContinentLayout.get();
        return switch (name) {
            case "prints" -> lay.islandPrints().isEmpty() ? null : lay.islandPrints().get(0);
            default -> lay.island(name);
        };
    }

    public static boolean gateOpen(ServerLevel level) {
        SiteData.Entry e = gateEntry(level);
        return e != null && e.extra("open") != null;
    }

    // ---------------------------------------------------------------- 渡り (渡し守の会話の「渡してもらう」。town の act island_cross)
    public static void cross(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        if (ContinentOutskirts.ferried(p) || CROSSING_NOW.containsKey(p.getUUID())) {
            return;
        }
        if (active(level) && (done(p) & ALL) != ALL) {
            return;
        }
        BlockPos dock = ContinentOutskirts.ferryPos(level);
        BlockPos arrival = ContinentOutskirts.arrival(level);
        Vec3 sea = dock == null ? null : seaPoint(dock, arrival);
        if (sea == null) {
            ContinentOutskirts.ferry(p);
            return;
        }
        Boat boat = EntityType.BOAT.create(level);
        if (boat == null) {
            ContinentOutskirts.ferry(p);
            return;
        }
        float yaw = (float) Math.toDegrees(Math.atan2(-(arrival.getX() - sea.x), arrival.getZ() - sea.z));
        boat.moveTo(sea.x, sea.y, sea.z, yaw, 0f);
        boat.addTag(TEMP_TAG);
        level.addFreshEntity(boat);
        p.teleportTo(level, sea.x, sea.y + 0.5, sea.z, yaw, 0f);
        p.startRiding(boat, true);
        Entity rower = null;
        jp.revati.skyabyss.town.TownGuestEntity g = jp.revati.skyabyss.town.TownSetup.GUEST.get().create(level);
        if (g != null) {
            g.setGuest(FERRYMAN);
            g.moveTo(sea.x, sea.y + 0.5, sea.z, yaw, 0f);
            g.addTag(TEMP_TAG);
            level.addFreshEntity(g);
            g.startRiding(boat, true);
            rower = g;
        }
        CROSSING_NOW.put(p.getUUID(), new Crossing(boat, rower, level.getGameTime()));
        if (!Cinematics.start(p, CROSSING, null, Vec3.atCenterOf(arrival))) {
            land(p);
        }
    }

    // 島の船着き場から都の向きへ SEA_AHEAD 先の、深い海の上 (無ければその先を探す)
    @Nullable
    static Vec3 seaPoint(BlockPos dock, BlockPos arrival) {
        double dx = arrival.getX() - dock.getX();
        double dz = arrival.getZ() - dock.getZ();
        double len = Math.max(1, Math.hypot(dx, dz));
        ContinentMap map = ContinentMap.get();
        for (int d = SEA_AHEAD; d < SEA_AHEAD + 300; d += 10) {
            int x = (int) Math.round(dock.getX() + dx / len * d);
            int z = (int) Math.round(dock.getZ() + dz / len * d);
            if (map.surface(x, z) < ContinentChunkGenerator.SEA_LEVEL - 6) {
                return new Vec3(x + 0.5, ContinentChunkGenerator.SEA_LEVEL, z + 0.5);
            }
        }
        return null;
    }

    static void land(ServerPlayer p) {
        Crossing c = CROSSING_NOW.remove(p.getUUID());
        if (c != null) {
            p.stopRiding();
            if (c.ferryman() != null) {
                c.ferryman().discard();
            }
            c.boat().discard();
        }
        ContinentOutskirts.ferry(p);
        ARRIVE_AT.put(p.getUUID(), p.serverLevel().getGameTime() + 70);
    }

    static {
        Cinematics.onEnd(CROSSING, (p, skipped) -> land(p));
    }

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        if (ARRIVE_AT.isEmpty() && CROSSING_NOW.isEmpty()) {
            return;
        }
        ServerLevel over = event.getServer().overworld();
        long now = over.getGameTime();
        for (UUID id : List.copyOf(ARRIVE_AT.keySet())) {
            ServerPlayer p = event.getServer().getPlayerList().getPlayer(id);
            if (p == null) {
                ARRIVE_AT.remove(id);
                continue;
            }
            if (now >= ARRIVE_AT.get(id)) {
                ARRIVE_AT.remove(id);
                BlockPos c = jp.revati.skyabyss.opening.OpeningSites.shelter(event.getServer()).orElse(ContinentLayout.get().capital());
                Cinematics.start(p, ARRIVAL, null, Vec3.atCenterOf(c));
            }
        }
        // 渡りの間: 鐘が遠くで鳴る (12 秒め)・舟を止めておく
        for (Map.Entry<UUID, Crossing> e : CROSSING_NOW.entrySet()) {
            Crossing c = e.getValue();
            c.boat().setDeltaMovement(Vec3.ZERO);
            if (now - c.start() == 240) {
                over.playSound(null, c.boat().blockPosition(), SoundEvents.BELL_RESONATE, SoundSource.AMBIENT, 1.4f, 0.7f);
            }
        }
    }

    // 開発用: /skyabyss island ready (島の道を済ませたことに) | cross (すぐ渡る) | reset (島の道を最初から。渡ったことも消す)
    @SubscribeEvent
    static void onCommands(net.neoforged.neoforge.event.RegisterCommandsEvent event) {
        event.getDispatcher().register(net.minecraft.commands.Commands.literal(SkyAbyss.MODID).requires(s -> s.hasPermission(2))
                .then(net.minecraft.commands.Commands.literal("island")
                        .then(net.minecraft.commands.Commands.literal("ready").executes(c -> {
                            ServerPlayer p = c.getSource().getPlayerOrException();
                            p.getPersistentData().putInt(KILLS, BANDITS_NEEDED);
                            p.getPersistentData().putInt(KEY, ALL);
                            setReady(p, true);
                            openGate(p.serverLevel());
                            return 1;
                        }))
                        .then(net.minecraft.commands.Commands.literal("cross").executes(c -> {
                            ServerPlayer p = c.getSource().getPlayerOrException();
                            p.getPersistentData().putInt(KEY, ALL);
                            cross(p);
                            return 1;
                        }))
                        .then(net.minecraft.commands.Commands.literal("reset").executes(c -> {
                            ServerPlayer p = c.getSource().getPlayerOrException();
                            p.getPersistentData().remove(KEY);
                            p.getPersistentData().remove(KILLS);
                            p.getPersistentData().remove(ContinentOutskirts.FERRIED);
                            setReady(p, false);
                            return 1;
                        }))));
    }

    // 古い島の世界の後片付け (渡りの途中でサーバーが止まった舟と渡し守)
    @SubscribeEvent
    static void onServerStopped(net.neoforged.neoforge.event.server.ServerStoppingEvent event) {
        for (Crossing c : CROSSING_NOW.values()) {
            if (c.ferryman() != null) {
                c.ferryman().discard();
            }
            c.boat().discard();
        }
        CROSSING_NOW.clear();
        ARRIVE_AT.clear();
    }
}
