package jp.revati.skyabyss.titan.end;

import jp.revati.skyabyss.util.Players;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.cinematic.CameraMath;
import jp.revati.skyabyss.cinematic.Cinematics;
import jp.revati.skyabyss.cinematic.Scenes;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.party.PartyData;
import jp.revati.skyabyss.progress.Titans;
import jp.revati.skyabyss.story.ch1.Tasks;
import jp.revati.skyabyss.titan.StoneWhaleEntity;
import jp.revati.skyabyss.titan.TitanWorldData;
import jp.revati.skyabyss.titan.WhaleKeeper;
import jp.revati.skyabyss.titan.ember.EmberKeeper;
import jp.revati.skyabyss.titan.ember.EmberTitanEntity;
import jp.revati.skyabyss.titan.storm.StormKeeper;
import jp.revati.skyabyss.titan.storm.StormPerch;
import jp.revati.skyabyss.titan.storm.StormWingEntity;
import jp.revati.skyabyss.titan.tide.TideKeeper;
import jp.revati.skyabyss.titan.tide.TideOcean;
import jp.revati.skyabyss.titan.tide.TideShellEntity;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.network.PacketDistributor;

// 巨神の心臓を止めたあと (docs/design/01 の 4.3、02 の 7 章、20 の 3.4)。心臓は借りものの鼓動で、止めても巨神は死なない。巨神は深い眠りに落ちる。
//
//   T0       心臓の倒れる演出が終わり、各背中の撃破の報告 (報酬・欠片) が出る → onHeartStopped
//   T0+30    語り「鼓動が止まった」   T0+100 背中が震える (その巨神の言い回し)
//   T0+170   運び出される人の画面が黒へ落ちる
//   T0+190   運び出す: 戦った人 (参加者) とそのパーティの人 (同じ背中にいる人だけ) が、巨神の足もとの地面へ降ろされる。
//            戦わなかった人は背中に残る (マルチでも、人ごと・パーティごと)。降ろす先は巨神ごと (landing)。ブロックの中・水の中には降ろさない。
//            巨神はこの時から眠る姿になる (TitanDormancy: 動かず、歌わず、光らず、巨神ごとの沈み方をする)
//            初めての人 (その巨神の `titan_end_<巨神>` を見ていない) には、地上から眠りを見る場面 (約 16 秒、スキップ可)。
//            二回目からは場面なし、一行の字幕だけ
//   そのあと  章の余韻の札 (一章の Aftermath・各章の担当) は、場面が終わってから出る (ChapterCards が場面の間は預かる)
//   目覚め   背中の試練が元に戻る (TitanDormancy.requestWake。眠りは最短 90 秒) と、巨神は目を覚まして動きだす
@net.neoforged.fml.common.EventBusSubscriber(modid = SkyAbyss.MODID)
public final class TitanEnd {
    // 撃破の報告から、語り・震え・暗転・運び出しまで (tick)
    public static final int SILENCE_AT = 30;
    public static final int TREMBLE_AT = 100;
    public static final int FADE_AT = 170;
    public static final int CARRY_AT = 190;
    // 暗転の長さ。初めて (場面が続きを受け持つ) と、二回目から
    static final TitanEndNetwork.Fade FADE_FIRST = new TitanEndNetwork.Fade(20, 8, 12);
    static final TitanEndNetwork.Fade FADE_AGAIN = new TitanEndNetwork.Fade(20, 30, 40);
    // 降ろす場所を探すときに新しく読み込んでよいチャンクの数 (暗転のあいだに一度だけ)
    static final int LOAD_BUDGET = 160;

    // 降ろす先: 足の位置、向く向き (度)、巨神の位置 (場面の狙い)
    public record Landing(Vec3 pos, float yaw, Vec3 titan) {
    }

    // 降ろされる前の人 (運び出しの時刻まで。他の仕組み (天の鍵の場面) が割り込まないための印)。人ごと・保存しない。時刻 = オーバーワールドのゲーム時間
    private static final java.util.Map<UUID, Long> HOLDING = new java.util.HashMap<>();

    private TitanEnd() {
    }

    // 余韻・鍵の場面が待つべきか (背中で降ろされるのを待っている)
    public static boolean holding(ServerPlayer player) {
        Long until = HOLDING.get(player.getUUID());
        if (until == null) {
            return false;
        }
        if (player.server.overworld().getGameTime() >= until) {
            HOLDING.remove(player.getUUID());
            return false;
        }
        return true;
    }

    // ---------------------------------------------------------------- 心臓が止まった

    // 各背中の撃破の報告 (報酬のあと) から呼ぶ。ids = 戦った人 (参加者)。背中のディメンションは level
    public static void onHeartStopped(ServerLevel back, String titan, Collection<UUID> ids) {
        MinecraftServer server = back.getServer();
        Set<UUID> fought = new LinkedHashSet<>(ids);
        TitanDormancy.begin(server, titan, CARRY_AT);
        // このグループは初回を終えた (周回の道になる)
        jp.revati.skyabyss.titan.group.FirstRun.onFirstCleared(back, fought);
        // 追憶の宝 (任意の翼の品を、この巨神の初回の心臓の報いへ移した。人ごとに一度)
        HeartKeepsakes.grant(back, titan, fought);
        // 試練のプール: 周を一つ進める (次の乗り込みで新しく三つを引く。docs/design/29 の 2.2)
        jp.revati.skyabyss.trial.TrialRuntime.onHeartStopped(back, fought);
        // 周回の誓いの報酬 (素材の上乗せ・共鳴の欠片・称号・最高の段の記録)。誓いはここで降ろす
        jp.revati.skyabyss.titan.vow.Vows.onHeartStopped(back, titan, fought);
        if (Titans.THUNDER_WING.equals(titan)) {
            chooseStormPeak(server.overworld());
        }
        scheduleCarry(back, titan, fought);
    }

    // 報酬を出さずに、眠りと運び出しだけ (同じ試練の周で心臓をもう一度倒したとき。報酬・誓いは二重に出さない。
    // これを呼ばないと、二度目の撃破では巨神が眠らず、地上から去らない)
    public static void onHeartStoppedAgain(ServerLevel back, String titan, Collection<UUID> ids) {
        MinecraftServer server = back.getServer();
        Set<UUID> fought = new LinkedHashSet<>(ids);
        TitanDormancy.begin(server, titan, CARRY_AT);
        if (Titans.THUNDER_WING.equals(titan)) {
            chooseStormPeak(server.overworld());
        }
        scheduleCarry(back, titan, fought);
    }

    private static void scheduleCarry(ServerLevel back, String titan, Set<UUID> fought) {
        MinecraftServer server = back.getServer();
        long until = server.overworld().getGameTime() + CARRY_AT + 60;
        Set<UUID> carried = targetIds(server, back, fought);
        for (UUID id : carried) {
            HOLDING.put(id, until);
        }
        // 運ばれる人は、心臓を止めたあとも眠りが終わるまで巨神を見る (眠りの場面。32 の 3.3)
        jp.revati.skyabyss.titan.TitanVisibility.witnessRest(titan, carried);
        Tasks.later(SILENCE_AT, () -> narrateBack(back, "message.skyabyss.titan_end.silence"));
        Tasks.later(TREMBLE_AT, () -> tremble(back, titan));
        Tasks.later(FADE_AT, () -> fadeOut(server, back, titan, fought));
        Tasks.later(CARRY_AT, () -> carryOut(server, back, titan, fought));
        SkyAbyss.LOGGER.info("The heart of {} stopped; {} fighter(s) will be carried out in {} ticks", titan, fought.size(), CARRY_AT);
    }

    private static void narrateBack(ServerLevel back, String key) {
        for (ServerPlayer p : Players.snapshot(back.players())) {
            if (!p.isSpectator()) {
                Notice.narrate(p, key);
            }
        }
    }

    // 背中が震える: 足もとのうなり、粒、その巨神の言い回しの語り
    private static void tremble(ServerLevel back, String titan) {
        for (ServerPlayer p : Players.snapshot(back.players())) {
            if (p.isSpectator()) {
                continue;
            }
            back.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.AMBIENT, 1.4f, 0.5f);
            back.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, p.getX(), p.getY() + 0.2, p.getZ(), 6, 3, 0.1, 3, 0.002);
            Notice.narrate(p, "message.skyabyss.titan_end.tremble." + titan);
        }
    }

    // ---------------------------------------------------------------- 運ばれる人

    // 運ばれる人の UUID: 戦った人 + そのパーティの人のうち、この背中にいる人。戦っていなくても同じパーティなら (背中にいれば) 運ばれる
    public static Set<UUID> targetIds(MinecraftServer server, ServerLevel back, Collection<UUID> fought) {
        Set<UUID> out = new LinkedHashSet<>();
        for (ServerPlayer p : targets(server, back, fought)) {
            out.add(p.getUUID());
        }
        return out;
    }

    // 背中にいる (生きている・観戦でない) 人で、戦った人か、戦った人のパーティの人
    public static List<ServerPlayer> targets(MinecraftServer server, ServerLevel back, Collection<UUID> fought) {
        return pick(server, back, Players.snapshot(back.players()), fought);
    }

    // targets の選び方そのもの (自己テストが、いる人の一覧を渡して確かめる)。candidates のうち、背中 back にいる (生きている・観戦でない) 人で、
    // 戦った人か、戦った人のパーティの人だけ。戦わなかった人 (パーティでもない人) と、背中にいない人は選ばない
    public static List<ServerPlayer> pick(MinecraftServer server, ServerLevel back, Iterable<? extends ServerPlayer> candidates, Collection<UUID> fought) {
        Set<UUID> wanted = new LinkedHashSet<>(fought);
        for (UUID id : fought) {
            PartyData.Party party = PartyData.get(server).of(id);
            if (party != null) {
                wanted.addAll(party.members);
            }
        }
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer p : candidates) {
            if (wanted.contains(p.getUUID()) && p.isAlive() && !p.isSpectator() && p.level().dimension() == back.dimension()) {
                out.add(p);
            }
        }
        return out;
    }

    // ---------------------------------------------------------------- 暗転・運び出し

    private static void fadeOut(MinecraftServer server, ServerLevel back, String titan, Set<UUID> fought) {
        for (ServerPlayer p : targets(server, back, fought)) {
            boolean first = !Cinematics.state(p).seen(Scenes.endId(titan));
            if (!(p instanceof FakePlayer)) {
                PacketDistributor.sendToPlayer(p, first ? FADE_FIRST : FADE_AGAIN);
            }
            back.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.AMBIENT_CAVE.value(), SoundSource.AMBIENT, 1.0f, 0.6f);
        }
    }

    // 巨神が息を吐いて、人を地上へ降ろす。戻りは運んだ人数
    public static int carryOut(MinecraftServer server, ServerLevel back, String titan, Collection<UUID> fought) {
        return carryOut(server, titan, fought, targets(server, back, fought));
    }

    // 運ぶ人を決めて降ろす (自己テストは、いる人の一覧から pick した人を渡す)
    public static int carryOut(MinecraftServer server, String titan, Collection<UUID> fought, List<ServerPlayer> list) {
        if (list.isEmpty()) {
            return 0;
        }
        long t0 = System.currentTimeMillis();
        Landing landing = landing(server, titan);
        long searched = System.currentTimeMillis() - t0;
        int n = 0;
        for (ServerPlayer p : list) {
            HOLDING.remove(p.getUUID());
            deliver(p, titan, spread(server.overworld(), landing, n));
            n++;
        }
        SkyAbyss.LOGGER.info("The {} carried {} player(s) out to {} (landing search {} ms)", titan, n, landing.pos(), searched);
        return n;
    }

    // 何人も降ろすとき、同じ点に重ならないよう、n 人目は少しずらす (ずらした先も立てる地面のときだけ。だめなら同じ点)
    static Landing spread(ServerLevel level, Landing base, int n) {
        if (n == 0) {
            return base;
        }
        double[][] around = {{1.6, 0}, {-1.6, 0}, {0, 1.6}, {0, -1.6}, {1.6, 1.6}, {-1.6, -1.6}, {1.6, -1.6}, {-1.6, 1.6}};
        for (int k = 0; k < around.length; k++) {
            double[] d = around[(n - 1 + k) % around.length];
            int bx = net.minecraft.util.Mth.floor(base.pos().x + d[0]);
            int bz = net.minecraft.util.Mth.floor(base.pos().z + d[1]);
            if (level.getChunkSource().hasChunk(bx >> 4, bz >> 4)) {
                Vec3 v = SafeSpot.standable(level, bx, bz, SafeSpot.Kind.GROUND);
                if (v != null && Math.abs(v.y - base.pos().y) <= 1.0) {
                    return new Landing(v, base.yaw(), base.titan());
                }
            }
        }
        return base;
    }

    // 一人を地上の landing へ降ろし、初めてなら場面、二回目からは一行の字幕
    public static void deliver(ServerPlayer player, String titan, Landing landing) {
        ServerLevel overworld = player.server.overworld();
        // 背中へ上った場所の記録は要らない (もう降りた)
        jp.revati.skyabyss.titan.TitanTravel.takeBoardedFrom(player);
        player.stopRiding();
        player.fallDistance = 0;
        player.setDeltaMovement(Vec3.ZERO);
        // 岩鯨は空にいるので、降ろされた人は見上げる
        player.teleportTo(overworld, landing.pos().x, landing.pos().y, landing.pos().z, landing.yaw(), Titans.STONE_WHALE.equals(titan) ? -25f : 0f);
        arrivalEffects(overworld, titan, landing.pos());
        String scene = Scenes.endId(titan);
        Entity entity = entity(player.server, titan);
        if (!Cinematics.state(player).seen(scene) && Cinematics.startOnce(player, scene, entity, landing.titan())) {
            return;
        }
        Tasks.later(40, () -> {
            if (player.isAlive() && !player.isRemoved()) {
                Notice.narrate(player, "message.skyabyss.titan_end.again." + titan);
            }
        });
        // 場面のない降ろし (二回目から・始められなかった) では、少しあとに遺物の選択画面。場面があるときは、場面の終わりに開く (HeartReward.register)
        jp.revati.skyabyss.loot.reward.HeartReward.scheduleOpen(player, titan, jp.revati.skyabyss.loot.reward.HeartReward.OPEN_AFTER_CARRY, true);
    }

    // 巨神が吐く息: 降ろされた所の粒と音 (巨神ごと)
    static void arrivalEffects(ServerLevel level, String titan, Vec3 at) {
        ParticleOptions puff = switch (titan) {
            case Titans.TIDE_SHELL -> ParticleTypes.SPLASH;
            case Titans.THUNDER_WING -> ParticleTypes.CLOUD;
            case Titans.EMBER -> ParticleTypes.WHITE_ASH;
            default -> ParticleTypes.CLOUD;
        };
        level.sendParticles(puff, at.x, at.y + 0.6, at.z, 60, 1.6, 0.6, 1.6, 0.05);
        if (Titans.TIDE_SHELL.equals(titan)) {
            level.sendParticles(ParticleTypes.BUBBLE_POP, at.x, at.y + 0.3, at.z, 24, 1.2, 0.3, 1.2, 0.02);
        } else if (Titans.EMBER.equals(titan)) {
            level.sendParticles(ParticleTypes.SMALL_FLAME, at.x, at.y + 0.3, at.z, 10, 1.2, 0.3, 1.2, 0.01);
        } else if (Titans.THUNDER_WING.equals(titan)) {
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y + 1.0, at.z, 12, 1.0, 0.8, 1.0, 0.1);
        }
        SoundEvent sound = SoundEvents.BREEZE_WIND_CHARGE_BURST.value();
        level.playSound(null, at.x, at.y, at.z, sound, SoundSource.PLAYERS, 1.2f, 0.7f);
    }

    // ---------------------------------------------------------------- 降ろす先

    // その巨神の今の本体 (読み込まれているときだけ)
    @Nullable
    public static Entity entity(MinecraftServer server, String titan) {
        ServerLevel o = server.overworld();
        return switch (titan) {
            case Titans.STONE_WHALE -> WhaleKeeper.current(o);
            case Titans.TIDE_SHELL -> TideKeeper.KEEPER.current(o);
            case Titans.THUNDER_WING -> StormKeeper.KEEPER.current(o);
            case Titans.EMBER -> EmberKeeper.KEEPER.current(o);
            default -> null;
        };
    }

    // 巨神の位置 (本体、なければ見守りの最後の記録、なければ共有スポーン)
    public static Vec3 titanPosition(MinecraftServer server, String titan) {
        Entity e = entity(server, titan);
        if (e != null) {
            return e.position();
        }
        TitanWorldData.Slot slot = TitanWorldData.get(server.overworld()).slot(titan);
        Vec3 last = slot.lastPosition();
        if (last != null) {
            return last;
        }
        var spawn = server.overworld().getSharedSpawnPos();
        return new Vec3(spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5);
    }

    // 降ろす先 (巨神ごと。足もとの地面で、ブロックの中・水の中・木の上でない所)
    public static Landing landing(MinecraftServer server, String titan) {
        return landing(server, titan, titanPosition(server, titan), entity(server, titan));
    }

    // c = 巨神の位置、e = 本体 (読み込まれていなければ null。自己テストは仮の位置を渡す)
    public static Landing landing(MinecraftServer server, String titan, Vec3 c, @Nullable Entity e) {
        ServerLevel o = server.overworld();
        Vec3 spot = null;
        switch (titan) {
            case Titans.TIDE_SHELL -> {
                // 浜: 巨神から外へ、水際の地面を探す。なければ最後に休んだ岸、なければ陸ならどこでも
                spot = SafeSpot.find(o, c.x, c.z, 60, 300, SafeSpot.Kind.SHORE, LOAD_BUDGET);
                if (spot == null) {
                    TideShellEntity shell = TideKeeper.KEEPER.current(o);
                    Vec3 rest = shell != null ? shell.lastRestSpot() : null;
                    if (rest != null) {
                        spot = TideOcean.standableShore(o, rest.x, rest.z);
                    }
                }
                if (spot == null) {
                    spot = SafeSpot.find(o, c.x, c.z, 60, 300, SafeSpot.Kind.GROUND, LOAD_BUDGET / 4);
                }
            }
            case Titans.THUNDER_WING -> {
                // 眠る峰の足もと
                Vec3 peak = peakOf(server, c);
                c = peak;
                spot = SafeSpot.find(o, peak.x, peak.z, 14, 70, SafeSpot.Kind.GROUND, LOAD_BUDGET / 8);
            }
            case Titans.EMBER -> {
                // 丘 (火山の尾根) のかたわら
                Vec3 ridge = c;
                if (e instanceof EmberTitanEntity ember) {
                    ridge = ember.ridgePoint();
                }
                spot = SafeSpot.find(o, ridge.x, ridge.z, 36, 110, SafeSpot.Kind.GROUND, LOAD_BUDGET / 6);
            }
            default -> {
                // 岩鯨: 影の下の地面 (体の中心から少し離れて、見上げられる所)
                spot = SafeSpot.find(o, c.x, c.z, 12, 120, SafeSpot.Kind.GROUND, LOAD_BUDGET / 6);
            }
        }
        if (spot == null) {
            // どうしても見つからない: 共有スポーン地点のまわりの立てる地面 (それもなければ、スポーンの地表)
            var sp = o.getSharedSpawnPos();
            spot = SafeSpot.find(o, sp.getX(), sp.getZ(), 0, 80, SafeSpot.Kind.GROUND, LOAD_BUDGET / 4);
            if (spot == null) {
                int y = o.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, sp.getX(), sp.getZ());
                spot = new Vec3(sp.getX() + 0.5, y, sp.getZ() + 0.5);
            }
        }
        Vec3 look = c.subtract(spot);
        float yaw = look.horizontalDistanceSqr() > 1.0 ? CameraMath.yawOf(look) : 0f;
        return new Landing(spot, yaw, c);
    }

    // 雷翼の眠る峰: 眠りの記録の場所、なければ巨神の位置
    private static Vec3 peakOf(MinecraftServer server, Vec3 fallback) {
        TitanDormancy.Rest r = TitanDormancy.rest(server, Titans.THUNDER_WING);
        Vec3 s = r == null ? null : r.spot();
        return s != null ? s : fallback;
    }

    // 心臓が止まった時点で、雷翼が降りる峰を決めて記録する (降ろす先と、雷翼の向かう先を合わせるため)
    public static void chooseStormPeak(ServerLevel overworld) {
        MinecraftServer server = overworld.getServer();
        Vec3 c = titanPosition(server, Titans.THUNDER_WING);
        // 雷翼のまわりの読み込み済みの地面から、いちばん高い峰
        StormPerch.Peak p = StormPerch.find(overworld, c.x, c.z, 0, 160);
        if (p != null) {
            TitanDormancy.setSpot(server, Titans.THUNDER_WING, p.x(), p.y(), p.z());
            return;
        }
        double gy = StormPerch.surfaceAt(overworld, c.x, c.z);
        if (!Double.isNaN(gy)) {
            TitanDormancy.setSpot(server, Titans.THUNDER_WING, c.x, gy, c.z);
        }
    }

    // ---------------------------------------------------------------- 目覚め

    // 巨神が目を覚ました: 近く (640 ブロック) の地上の人に、その巨神の言い回しで一行
    static void onWake(MinecraftServer server, String titan) {
        Vec3 c = titanPosition(server, titan);
        ServerLevel o = server.overworld();
        for (ServerPlayer p : Players.snapshot(o.players())) {
            if (!p.isSpectator() && p.level().dimension() == Level.OVERWORLD && Math.hypot(p.getX() - c.x, p.getZ() - c.z) < 640) {
                Notice.narrate(p, "message.skyabyss.titan_end.wake." + titan);
            }
        }
    }

    // 自己テスト用・サーバーが止まったとき: 持ちこたえの記録を空にする
    public static void clearHolding() {
        HOLDING.clear();
    }

    @net.neoforged.bus.api.SubscribeEvent
    static void onServerStopped(net.neoforged.neoforge.event.server.ServerStoppedEvent event) {
        HOLDING.clear();
    }
}
