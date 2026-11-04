package jp.revati.skyabyss.story;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.npc.NpcBonds;
import jp.revati.skyabyss.npc.NpcEntity;
import jp.revati.skyabyss.npc.NpcSetup;
import jp.revati.skyabyss.opening.site.SiteData;
import jp.revati.skyabyss.progress.Progression;
import jp.revati.skyabyss.quest.QuestLog;
import jp.revati.skyabyss.world.continent.ContinentTowns;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

// イサナ (鯨追い) の章ごとの居場所 (2026-11-04 の決めごと c2_isana = harbor、c3_isana = hut。docs/design/07 の 3.2)。
// ふだんは鯨見の町の見張り台のそば (NpcSpawner.keepTownChaser)。章のあいだだけ、その土地の町に来る。
// 居場所は人ごと: 鯨見のイサナと、港・火のそばのイサナを立て、その人の進みで見える方を一人に決める (NpcEntity.broadcastToPlayer)。
//   HARBOR  汐守の港 (二章)。段階 1 (岩鯨を止めた) で、噂の話を聞いた (印 c2_harbor) か、頼み main_tide が噂の段を越えた・終えた人。
//           段階 2 (潮殻を止めた) でも、港で心臓のあとの話 (印 c2_harbor_done) をするまで。三章の頼み main_thunder が支度の段を越えたら港を離れる
//           立つ所: 汐守の町の中心から海へいちばん近い岸の、水際から 3〜12 手前の平らな所。顔は町の方へ (海に背を向ける)
//   HUT     風待の里の焚き火のそば (三章)。段階 2 で港を離れたあと。段階 3 (雷翼を止めた) でも、「俺は残る」の話 (印 ch3g_stay_told) をするまで。
//           四章の頼み main_ember が支度の段を越えたら離れる。立つ所: 町の休み場 (印 rest) にいちばん近い焚き火 (24 以内) から 2〜6 の平らな所。
//           そばに焚き火が無ければ、立つ所の 2 となりに一つ置く (印 isana_hut_fire)。顔は火の方へ
//   立つ所は町の記録 (SiteData) に印として書く。あとから地ならし・小岩が埋めたら選び直し、イサナが埋まっていたら立つ所へ戻す
//   (見回りは町の中心から 192 以内の、その居場所のはずの人のそばで 2 秒ごと)。話す相手の目印 (SiteTargets の npc:whale_chaser) もそこを指す (SiteNetwork.payload)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class IsanaPost {
    public static final String ROLE = "skyabyss:whale_chaser";
    // 噂の話で「先に港へ行く」と言った
    public static final String FLAG_TOLD = "c2_harbor";
    // 港で心臓のあとの話をした (港を離れる)
    public static final String FLAG_DONE = "c2_harbor_done";
    // 三章の心臓のあと「俺は残る」と話した (火のそばを離れて鯨見へ戻る)
    public static final String FLAG_STAY = "ch3g_stay_told";

    public enum Post {
        TOWN(null, null, null),
        HARBOR("skyabyss_harbor_isana", "isana_harbor", "tide"),
        HUT("skyabyss_hut_isana", "isana_hut", "storm");

        @Nullable
        public final String tag;
        @Nullable
        final String mark;
        @Nullable
        final String town;

        Post(@Nullable String tag, @Nullable String mark, @Nullable String town) {
            this.tag = tag;
            this.mark = mark;
            this.town = town;
        }
    }

    static final int SEARCH_FROM = 16;
    static final int SEARCH_TO = 160;

    private IsanaPost() {
    }

    // この人にとって、イサナがどこにいるか
    public static Post post(ServerPlayer p) {
        int stage = Progression.stage(p);
        if (stage < 1 || stage > 3) {
            return Post.TOWN;
        }
        Set<String> flags = NpcBonds.of(p).bond(ROLE).flags();
        QuestLog log = QuestLog.of(p);
        if (stage == 1) {
            QuestLog.Entry tide = log.entry("main_tide");
            return flags.contains(FLAG_TOLD) || tide != null && (tide.done || tide.stage >= 1) ? Post.HARBOR : Post.TOWN;
        }
        if (stage == 2) {
            QuestLog.Entry thunder = log.entry("main_thunder");
            boolean thunderEarly = thunder == null || !thunder.done && thunder.stage <= 1;
            return !flags.contains(FLAG_DONE) && thunderEarly ? Post.HARBOR : Post.HUT;
        }
        if (flags.contains(FLAG_STAY)) {
            return Post.TOWN;
        }
        QuestLog.Entry ember = log.entry("main_ember");
        return ember == null || !ember.done && ember.stage <= 1 ? Post.HUT : Post.TOWN;
    }

    public static boolean atHarbor(ServerPlayer p) {
        return post(p) == Post.HARBOR;
    }

    // NPC の居場所 (印のない鯨追いは鯨見)
    public static Post postOf(NpcEntity npc) {
        for (Post post : Post.values()) {
            if (post.tag != null && npc.getTags().contains(post.tag)) {
                return post;
            }
        }
        return Post.TOWN;
    }

    // 章の居場所のイサナか (野に立つ鯨追いを消す処理で残す)
    public static boolean isPosted(NpcEntity npc) {
        return postOf(npc) != Post.TOWN;
    }

    // この人に、この NPC が見えるか (鯨追いだけを分ける。ほかの NPC はいつも見える)
    public static boolean visibleTo(NpcEntity npc, ServerPlayer p) {
        if (npc.getType() != NpcSetup.WHALE_CHASER.get()) {
            return true;
        }
        return postOf(npc) == post(p);
    }

    // ---------------------------------------------------------------- 立つ所
    @Nullable
    static SiteData.Entry town(ServerLevel level, Post post) {
        if (post.town == null) {
            return null;
        }
        for (SiteData.Entry e : SiteData.get(level).entries(ContinentTowns.KIND)) {
            if (e.built && post.town.equals(ContinentTowns.region(e.index))) {
                return e;
            }
        }
        return null;
    }

    // その居場所の立つ所 (分かっていなければ null。道しるべと確かめ用)
    @Nullable
    public static BlockPos spot(MinecraftServer server, Post post) {
        ServerLevel level = server.overworld();
        SiteData.Entry e = level == null || post.mark == null ? null : town(level, post);
        return e == null ? null : e.extra(post.mark);
    }

    // この人の話す相手の目印 (章の居場所にいれば、その立つ所。鯨見なら null)
    @Nullable
    public static BlockPos target(MinecraftServer server, ServerPlayer p) {
        Post post = post(p);
        return post == Post.TOWN ? null : spot(server, post);
    }

    // 汐守: 町の中心から 16 方向に海を探し、いちばん近い水際の手前の立てる所。周りが読み込まれていなければ null (あとでもう一度)
    @Nullable
    static BlockPos findShore(ServerLevel level, BlockPos center) {
        int sea = level.getSeaLevel();
        BlockPos best = null;
        int bestR = Integer.MAX_VALUE;
        for (int k = 0; k < 16; k++) {
            double a = Math.PI * 2 * k / 16;
            double dx = Math.cos(a);
            double dz = Math.sin(a);
            for (int r = SEARCH_FROM; r <= SEARCH_TO && r < bestR; r += 2) {
                int x = center.getX() + (int) Math.round(dx * r);
                int z = center.getZ() + (int) Math.round(dz * r);
                if (!level.hasChunk(x >> 4, z >> 4)) {
                    break;
                }
                BlockPos w = new BlockPos(x, sea - 1, z);
                if (!level.getFluidState(w).is(FluidTags.WATER) || level.getHeight(Heightmap.Types.OCEAN_FLOOR, x, z) >= sea - 1) {
                    continue;
                }
                // 水際から町の方へ 3〜12 戻った、立てる所 (崖と水のあいだの細い帯には立たない。2026-11-04 の撮影)
                for (int back = 3; back <= 12; back++) {
                    int bx = center.getX() + (int) Math.round(dx * (r - back));
                    int bz = center.getZ() + (int) Math.round(dz * (r - back));
                    int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz);
                    BlockPos feet = new BlockPos(bx, y, bz);
                    if (y >= sea && y <= sea + 6 && standable(level, feet) && open(level, feet)) {
                        best = feet;
                        bestR = r;
                        break;
                    }
                }
                break;
            }
        }
        return best;
    }

    // 風待: 休み場 (rest) にいちばん近い町の焚き火 (FIRE_R の中。無ければ休み場) から 2〜6 の、平らな立てる所 (決まった順に探す)
    static final int FIRE_R = 24;

    @Nullable
    static BlockPos findNearRest(ServerLevel level, BlockPos rest) {
        if (!level.hasChunk(rest.getX() >> 4, rest.getZ() >> 4)) {
            return null;
        }
        BlockPos fire = fireNear(level, rest);
        BlockPos from = fire != null ? fire : rest;
        for (int r = 2; r <= 6; r++) {
            for (int[] d : new int[][] {{r, 0}, {-r, 0}, {0, r}, {0, -r}, {r, r}, {-r, r}, {r, -r}, {-r, -r}}) {
                for (int dy = -2; dy <= 2; dy++) {
                    BlockPos feet = from.offset(d[0], dy, d[1]);
                    if (standable(level, feet) && open(level, feet)) {
                        return feet;
                    }
                }
            }
        }
        return null;
    }

    // 休み場にいちばん近い焚き火 (読み込まれた所だけ)
    @Nullable
    static BlockPos fireNear(ServerLevel level, BlockPos rest) {
        BlockPos best = null;
        double bd = Double.MAX_VALUE;
        for (BlockPos q : BlockPos.betweenClosed(rest.offset(-FIRE_R, -4, -FIRE_R), rest.offset(FIRE_R, 4, FIRE_R))) {
            if (!level.hasChunkAt(q) || !level.getBlockState(q).is(net.minecraft.world.level.block.Blocks.CAMPFIRE)) {
                continue;
            }
            double d = q.distSqr(rest);
            if (d < bd) {
                bd = d;
                best = q.immutable();
            }
        }
        return best;
    }

    static boolean standable(ServerLevel level, BlockPos feet) {
        BlockPos below = feet.below();
        return level.getFluidState(below).isEmpty() && level.getBlockState(below).isFaceSturdy(level, below, net.minecraft.core.Direction.UP)
                && level.getBlockState(feet).isAir() && level.getBlockState(feet.above()).isAir();
    }

    // まわり 8 マスのうち 6 マス以上が、同じ高さ ±1 の水でない地面
    static boolean open(ServerLevel level, BlockPos feet) {
        int ok = 0;
        for (int ox = -1; ox <= 1; ox++) {
            for (int oz = -1; oz <= 1; oz++) {
                if (ox == 0 && oz == 0) {
                    continue;
                }
                int x = feet.getX() + ox;
                int z = feet.getZ() + oz;
                int h = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                if (Math.abs(h - feet.getY()) <= 1 && level.getFluidState(new BlockPos(x, h - 1, z)).isEmpty()) {
                    ok++;
                }
            }
        }
        return ok >= 6;
    }

    // 立つ所 (印が無ければ探して書く)
    @Nullable
    static BlockPos ensureSpot(ServerLevel level, SiteData.Entry town, Post post) {
        BlockPos at = town.extra(post.mark);
        if (at != null) {
            return at;
        }
        if (post == Post.HARBOR) {
            at = findShore(level, town.pos);
        } else {
            BlockPos rest = town.extra("rest");
            at = rest == null ? null : findNearRest(level, rest);
        }
        if (at != null) {
            town.extra(post.mark, at);
            SiteData.get(level).setDirty();
            SkyAbyss.LOGGER.info("[isana] {} spot of the {} town: {}", post, post.town, at.toShortString());
        }
        return at;
    }

    // 火のそばの焚き火: 町の焚き火が近くに無ければ、立つ所の 2 となりに一つ置く (町の記録に印 isana_hut_fire。消えていれば置き直す)
    static final String FIRE_MARK = "isana_hut_fire";

    @Nullable
    static BlockPos ensureFire(ServerLevel level, SiteData.Entry town, BlockPos spot) {
        BlockPos near = fireNear(level, spot);
        if (near != null && near.distSqr(spot) <= 16) {
            return near;
        }
        BlockPos at = town.extra(FIRE_MARK);
        if (at != null && at.distSqr(spot) <= 16) {
            if (level.getBlockState(at).is(net.minecraft.world.level.block.Blocks.CAMPFIRE)) {
                return at;
            }
            if (level.getBlockState(at).isAir()) {
                level.setBlock(at, net.minecraft.world.level.block.Blocks.CAMPFIRE.defaultBlockState(), 3);
                return at;
            }
        }
        for (int[] d : new int[][] {{2, 0}, {-2, 0}, {0, 2}, {0, -2}, {2, 1}, {-2, 1}, {1, 2}, {1, -2}}) {
            BlockPos f = spot.offset(d[0], 0, d[1]);
            BlockPos below = f.below();
            if (level.getBlockState(f).isAir() && level.getFluidState(below).isEmpty()
                    && level.getBlockState(below).isFaceSturdy(level, below, net.minecraft.core.Direction.UP)) {
                level.setBlock(f, net.minecraft.world.level.block.Blocks.CAMPFIRE.defaultBlockState(), 3);
                town.extra(FIRE_MARK, f);
                SiteData.get(level).setDirty();
                SkyAbyss.LOGGER.info("[isana] lit a fire by the hut spot at {}", f.toShortString());
                return f;
            }
        }
        return null;
    }

    // 顔の向き: 港は町の方 (海に背を向ける)、火のそばは焚き火 (無ければ休み場) の方
    static float yaw(ServerLevel level, SiteData.Entry town, Post post, BlockPos spot) {
        BlockPos look = town.pos;
        if (post == Post.HUT && town.extra("rest") != null) {
            BlockPos fire = fireNear(level, spot);
            look = fire != null && fire.distSqr(spot) <= 64 ? fire : town.extra("rest");
        }
        return (float) (Mth.atan2(look.getZ() - spot.getZ(), look.getX() - spot.getX()) * (180 / Math.PI)) - 90f;
    }

    // 章の居場所のイサナ: その居場所のはずの人が町の近くにいて、立っていなければ立たせる。離れたり埋まったりしていたら戻す
    public static void keep(ServerLevel level, SiteData.Entry town, Post post, ServerPlayer player) {
        if (post == Post.TOWN || post(player) != post) {
            return;
        }
        BlockPos spot = ensureSpot(level, town, post);
        if (spot == null || !level.hasChunk(spot.getX() >> 4, spot.getZ() >> 4) || !level.areEntitiesLoaded(net.minecraft.world.level.ChunkPos.asLong(spot))) {
            return;
        }
        // 町のまわりの地ならし (town_outskirts) や小岩が、あとから立つ所を埋めることがある (2026-11-04 の撮影)。埋まったら選び直す
        if (!standable(level, spot)) {
            town.extraRemove(post.mark);
            SiteData.get(level).setDirty();
            spot = ensureSpot(level, town, post);
            if (spot == null) {
                return;
            }
        }
        java.util.List<NpcEntity> found = level.getEntities(NpcSetup.WHALE_CHASER.get(), new AABB(spot).inflate(32), n -> n.isAlive() && n.getTags().contains(post.tag));
        for (int i = 1; i < found.size(); i++) {
            found.get(i).discard();
        }
        if (post == Post.HUT) {
            ensureFire(level, town, spot);
        }
        float yaw = yaw(level, town, post, spot);
        if (!found.isEmpty()) {
            NpcEntity live = found.get(0);
            BlockPos at = live.blockPosition();
            boolean buried = !level.getBlockState(at).getCollisionShape(level, at).isEmpty() || !level.getBlockState(at.above()).getCollisionShape(level, at.above()).isEmpty();
            if (buried || live.distanceToSqr(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5) > 64) {
                live.teleportTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
                live.setYRot(yaw);
                live.setYHeadRot(yaw);
            }
            return;
        }
        NpcEntity npc = NpcSetup.WHALE_CHASER.get().create(level);
        if (npc == null) {
            return;
        }
        npc.moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, yaw, 0f);
        npc.setYHeadRot(yaw);
        npc.setHome(spot);
        npc.nameByRole();
        npc.addTag(post.tag);
        npc.finalizeSpawn(level, level.getCurrentDifficultyAt(spot), net.minecraft.world.entity.MobSpawnType.STRUCTURE, null);
        level.addFreshEntity(npc);
        SkyAbyss.LOGGER.info("[isana] stands at the {} {}", post, spot.toShortString());
    }

    // ---------------------------------------------------------------- 見回りと道しるべ
    // 居場所が変わった人に、話す相手の目印を送り直す (2 秒ごと)
    private static final Map<UUID, Post> LAST = new ConcurrentHashMap<>();

    // 章の居場所のイサナを立たせる・戻す見回りの届く距離 (町の中心から。港は町の外の水際なので、町の見回り 96 より広く)
    static final int KEEP_RADIUS = 192;

    @SubscribeEvent
    static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || p.tickCount % 40 != 17 || p.level().dimension() != Level.OVERWORLD || p.isSpectator()) {
            return;
        }
        Post now = post(p);
        if (now != Post.TOWN) {
            SiteData.Entry town = town(p.serverLevel(), now);
            if (town != null && town.pos.distSqr(p.blockPosition()) <= (double) KEEP_RADIUS * KEEP_RADIUS) {
                keep(p.serverLevel(), town, now, p);
            }
        }
        Post before = LAST.put(p.getUUID(), now);
        if (before != null && before != now) {
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(p, jp.revati.skyabyss.opening.site.SiteNetwork.payload(p.server, p));
        }
    }

    @SubscribeEvent
    static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        LAST.remove(event.getEntity().getUUID());
    }

    // OP の確かめ: /skyabyss dev isana (この人にとってのイサナの居場所・立つ所・近くの鯨追いと見えるか)
    @SubscribeEvent
    static void onCommands(net.neoforged.neoforge.event.RegisterCommandsEvent event) {
        event.getDispatcher().register(net.minecraft.commands.Commands.literal(SkyAbyss.MODID).requires(src -> src.hasPermission(2))
                .then(net.minecraft.commands.Commands.literal("dev").then(net.minecraft.commands.Commands.literal("isana").executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                    Post post = post(p);
                    StringBuilder b = new StringBuilder("post=" + post + " stage=" + Progression.stage(p) + " spot=" + (post == Post.TOWN ? "-" : spot(p.server, post)));
                    for (NpcEntity n : p.serverLevel().getEntities(NpcSetup.WHALE_CHASER.get(), p.getBoundingBox().inflate(128), NpcEntity::isAlive)) {
                        b.append(" | ").append(postOf(n)).append(" @").append(n.blockPosition().toShortString())
                                .append(" visible=").append(visibleTo(n, p)).append(" broadcast=").append(n.broadcastToPlayer(p));
                    }
                    String msg = b.toString();
                    ctx.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal(msg), false);
                    SkyAbyss.LOGGER.info("[isana] {}", msg);
                    return 1;
                }))));
    }
}
