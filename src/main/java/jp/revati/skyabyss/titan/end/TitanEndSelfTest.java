package jp.revati.skyabyss.titan.end;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.cinematic.Cinematics;
import jp.revati.skyabyss.cinematic.Scenes;
import jp.revati.skyabyss.party.PartyData;
import jp.revati.skyabyss.progress.Progression;
import jp.revati.skyabyss.progress.Titans;
import jp.revati.skyabyss.registry.ModEntities;
import jp.revati.skyabyss.story.ch1.Tasks;
import jp.revati.skyabyss.titan.StoneWhaleEntity;
import jp.revati.skyabyss.titan.TitanTravel;
import jp.revati.skyabyss.titan.ember.EmberTitanEntity;
import jp.revati.skyabyss.titan.ember.EmberTitanSetup;
import jp.revati.skyabyss.titan.storm.StormSetup;
import jp.revati.skyabyss.titan.storm.StormWingEntity;
import jp.revati.skyabyss.titan.tide.TideSetup;
import jp.revati.skyabyss.titan.tide.TideShellEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

// 巨神の眠りと運び出しの自己テスト (SKYABYSS_SELFTEST。結果は SELFTEST titan end PASSED / FAILED)。docs/design/01 の 4.3、02 の 7 章、20 の 3.4。
//  - 眠りの状態: 始める (見える前でも乗れない)・最短の長さ・目覚め・乗り込みの拒否・光の判定
//  - 巨神ごとの眠り方 (本体を直接 tick する): 岩鯨は沈む・鎖をたたむ、潮殻は海へ沈む (門が出ない)、雷翼は峰へ降りて伏せる (飛び立つ)、余燼は埋まる。
//    どれも目覚めると REST が -1 に戻り、動きが戻る
//  - 運び出し: 戦った人とそのパーティの人 (同じ背中にいる人だけ) を選び、戦わなかった人・背中にいない人は選ばない
//  - 降ろす場所: 水・溶岩・木の葉の上・ブロックの中にならない (仮の舞台で、水たまり・溶岩・木の葉の中心から探す)。水際の浜も
//  - 初めてなら眠りの場面は一人に一度だけ。二回目からは場面なし
public final class TitanEndSelfTest {
    // ほかの自己テストと重ならない所 (岩鯨 4000,4000、潮殻 -4000,4000)
    private static final int PAD_X = 6000;
    private static final int PAD_Z = -6000;
    private static final int PAD = 64;
    // 床の上面 (足の高さ)。床のブロックは PAD_Y - 1
    private static final int PAD_Y = 100;

    private TitanEndSelfTest() {
    }

    public static void load(MinecraftServer server) {
        ServerLevel level = server.overworld();
        for (int cx = PAD_X >> 4; cx <= (PAD_X + PAD) >> 4; cx++) {
            for (int cz = PAD_Z >> 4; cz <= (PAD_Z + PAD) >> 4; cz++) {
                level.setChunkForced(cx, cz, true);
            }
        }
    }

    private static void unload(ServerLevel level) {
        for (int cx = PAD_X >> 4; cx <= (PAD_X + PAD) >> 4; cx++) {
            for (int cz = PAD_Z >> 4; cz <= (PAD_Z + PAD) >> 4; cz++) {
                level.setChunkForced(cx, cz, false);
            }
        }
    }

    // 心臓を倒す自己テストのあと、巨神の眠りを残さない (あとの乗り込みの検査が断られないように)
    public static void resetAll(MinecraftServer server) {
        for (String t : Titans.all()) {
            TitanDormancy.clear(server, t);
        }
        TitanEnd.clearHolding();
    }

    public static void runSelfTest(MinecraftServer server) {
        ServerLevel level = server.overworld();
        List<String> f = new ArrayList<>();
        try {
            state(server, f);
            bodies(server, level, f);
            carried(server, level, f);
            landing(server, level, f);
            cinematicOnce(server, level, f);
            flow(server, level, f);
        } catch (RuntimeException e) {
            SkyAbyss.LOGGER.error("SELFTEST titan end crashed", e);
            f.add("crashed: " + e);
        } finally {
            for (String t : Titans.all()) {
                TitanDormancy.clear(server, t);
            }
            TitanEnd.clearHolding();
            unload(level);
        }
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST titan end PASSED (sleep state and wake, each titan's sleep, carry-out picks, safe landing, first-clear scene once, flow)");
        } else {
            f.forEach(m -> SkyAbyss.LOGGER.error("SELFTEST titan end FAILED: {}", m));
        }
    }

    private static void check(List<String> f, boolean ok, String what) {
        if (!ok) {
            f.add(what);
        }
    }

    private static FakePlayer fake(ServerLevel level, String tail, String name) {
        FakePlayer p = FakePlayerFactory.get(level, new GameProfile(UUID.fromString("7e5a0e0d-0000-4000-8000-0000000e" + tail), name));
        return p;
    }

    // ---------------------------------------------------------------- 眠りの状態
    private static void state(MinecraftServer server, List<String> f) {
        for (String t : Titans.all()) {
            TitanDormancy.clear(server, t);
        }
        String whale = Titans.STONE_WHALE;
        check(f, !TitanDormancy.isResting(server, whale) && TitanDormancy.age(server, whale) == -1, "a whale that has not stopped is resting");
        // 眠りの始まり (まだ見えない): 乗れず、姿はまだ眠っていない
        TitanDormancy.Rest r = TitanDormancy.begin(server, whale, 200);
        check(f, TitanDormancy.isResting(server, whale), "begin did not make the whale rest");
        check(f, !TitanDormancy.isDormant(server, whale) && TitanDormancy.age(server, whale) == -1, "the sleep showed before its delay");
        FakePlayer a = fake(server.overworld(), "0001", "[titan_end_a]");
        boolean boarded = Progression.checkBoard(a, whale);
        check(f, !boarded, "a player could board a titan whose heart had just stopped");
        check(f, !BoardGlow.visibleTo(a, whale), "the boarding glow showed for a sleeping titan");
        // 続けて止めても、眠りは数え直さない
        TitanDormancy.Rest again = TitanDormancy.begin(server, whale, 5);
        check(f, again == r && again.since == r.since, "stopping a sleeping titan again reset its sleep");
        // 見える時刻から
        r.since = server.overworld().getGameTime() - 10;
        check(f, TitanDormancy.isDormant(server, whale) && TitanDormancy.age(server, whale) >= 10, "the sleep did not show after its delay");
        // 試練が元に戻っても、最短の長さまでは目覚めない
        TitanDormancy.requestWake(server, whale);
        check(f, TitanDormancy.tickWake(server) == 0 && TitanDormancy.isResting(server, whale), "the titan woke before the minimum sleep");
        check(f, r.wakeAt == r.since + TitanDormancy.MIN_TICKS, "the wake time is not the minimum sleep after the sleep began");
        // 最短を過ぎると目覚める
        r.since -= TitanDormancy.MIN_TICKS;
        r.wakeAt = server.overworld().getGameTime();
        check(f, TitanDormancy.tickWake(server) == 1 && !TitanDormancy.isResting(server, whale), "the titan did not wake after the minimum sleep");
        check(f, Progression.checkBoard(a, whale), "a player could not board the titan after it woke");
        check(f, BoardGlow.visibleTo(a, whale), "the boarding glow did not show for an eligible player once the titan woke");
        // 戻らないまま長くたつと、自分で目覚める (背中に居座る人がいても眠りっぱなしにならない)
        TitanDormancy.Rest stuck = TitanDormancy.begin(server, whale, 0);
        stuck.since -= TitanDormancy.MAX_TICKS + 5;
        check(f, TitanDormancy.tickWake(server) == 1 && !TitanDormancy.isResting(server, whale), "a titan slept forever");
        // 乗る資格のない人 (二体目) には、眠っていなくても光は見えない
        check(f, !BoardGlow.visibleTo(a, Titans.TIDE_SHELL) || Progression.canBoard(a, Titans.TIDE_SHELL), "the glow showed for a player who cannot board");
    }

    // ---------------------------------------------------------------- 巨神ごとの眠り方 (本体を直接 tick する)
    private static void tick(Entity e, int n) {
        for (int i = 0; i < n; i++) {
            e.tickCount++;
            e.tick();
        }
    }

    private static void bodies(MinecraftServer server, ServerLevel level, List<String> f) {
        buildPad(level);
        double cx = PAD_X + PAD / 2.0;
        double cz = PAD_Z + PAD / 2.0;
        // 岩鯨: 沈み、鎖をたたむ。目覚めると戻る
        StoneWhaleEntity whale = ModEntities.STONE_WHALE.get().create(level);
        whale.moveTo(cx, 200, cz, 0f, 0f);
        tick(whale, 3);
        check(f, whale.restTicks() == -1, "the whale showed a sleep before it slept");
        TitanDormancy.begin(server, Titans.STONE_WHALE, 0);
        tick(whale, 400);
        check(f, whale.restTicks() >= 0 && whale.isDormantNow(), "the sleeping whale's REST was not synced");
        check(f, whale.getY() < 190 && whale.getY() >= 145, "the sleeping whale did not sink slowly (y " + whale.getY() + ")");
        check(f, whale.chainLength() == 0f && !whale.isChainDown(), "the sleeping whale kept its chain down");
        check(f, whale.glowFactor(0f) < 1f, "the sleeping whale kept its glow");
        double sank = whale.getY();
        tick(whale, 400);
        check(f, whale.getY() <= sank && whale.getY() >= 145, "the sleeping whale kept moving after it had sunk (y " + whale.getY() + ")");
        TitanDormancy.clear(server, Titans.STONE_WHALE);
        tick(whale, 2);
        check(f, whale.restTicks() == -1 && whale.glowFactor(0f) == 1f, "the whale did not wake (REST " + whale.restTicks() + ")");
        whale.discard();

        // 潮殻: 海へ沈み、潮門が出ない。目覚めると戻る
        TideShellEntity shell = TideSetup.TIDE_SHELL.get().create(level);
        double sea = level.getSeaLevel();
        shell.moveTo(cx, sea - 24, cz, 0f, 0f);
        TitanDormancy.begin(server, Titans.TIDE_SHELL, 0);
        tick(shell, 260);
        check(f, shell.restTicks() >= 0, "the sleeping tide shell's REST was not synced");
        check(f, Math.abs(shell.getY() - (sea - TideShellEntity.SLEEP_DRAFT)) < 0.2, "the tide shell did not sink to its sleeping draft (y " + shell.getY() + ")");
        check(f, !shell.isResting(), "a sleeping tide shell showed its gate");
        TitanDormancy.clear(server, Titans.TIDE_SHELL);
        tick(shell, 2);
        check(f, shell.restTicks() == -1, "the tide shell did not wake");
        shell.discard();

        // 雷翼: 峰へ降りて伏せる (昇り風は立たない)。目覚めると飛び立つ
        StormWingEntity wing = StormSetup.THUNDER_WING.get().create(level);
        wing.moveTo(cx - 40, 150, cz, 0f, 0f);
        TitanDormancy.begin(server, Titans.THUNDER_WING, 0);
        TitanDormancy.setSpot(server, Titans.THUNDER_WING, cx + 20, PAD_Y, cz);
        tick(wing, 200);
        check(f, wing.restTicks() >= 0, "the sleeping thunder wing's REST was not synced");
        check(f, Math.abs(wing.getX() - (cx + 20)) < 1.5 && Math.abs(wing.getY() - PAD_Y) < 1.5,
                "the thunder wing did not settle on the peak (" + wing.position() + ")");
        check(f, !wing.isPerched() && !wing.cowering(), "a sleeping thunder wing counted as perched or cowering");
        TitanDormancy.clear(server, Titans.THUNDER_WING);
        tick(wing, 2);
        check(f, wing.restTicks() == -1 && wing.mode() == StormWingEntity.Mode.TAKE_OFF, "the thunder wing did not take off when it woke (" + wing.mode() + ")");
        wing.discard();

        // 余燼: 動かず、さらに埋まる。目覚めると潜行に戻る
        EmberTitanEntity ember = EmberTitanSetup.EMBER_TITAN.get().create(level);
        ember.moveTo(cx, PAD_Y - 58, PAD_Z + 10, 0f, 0f);
        TitanDormancy.begin(server, Titans.EMBER, 0);
        double before = ember.getY();
        tick(ember, 120);
        check(f, ember.restTicks() >= 0, "the sleeping ember titan's REST was not synced");
        check(f, ember.getY() <= before && ember.getX() == cx && ember.getZ() == PAD_Z + 10, "the sleeping ember titan moved sideways or rose (" + ember.position() + ")");
        check(f, !ember.isSurfaced() && !ember.mouthOpen(), "a sleeping ember titan showed its mouth");
        TitanDormancy.clear(server, Titans.EMBER);
        tick(ember, 2);
        check(f, ember.restTicks() == -1 && ember.mode() == EmberTitanEntity.Mode.SWIM, "the ember titan did not wake (" + ember.mode() + ")");
        ember.discard();
    }

    // ---------------------------------------------------------------- 運ばれる人
    private static void carried(MinecraftServer server, ServerLevel level, List<String> f) {
        ServerLevel back = server.getLevel(TitanTravel.TITAN_BACK);
        if (back == null) {
            f.add("the whale back dimension is missing");
            return;
        }
        // A = 戦った人、B = A とパーティの人 (戦っていない)、C = 戦っていない人、D = 戦った人だが地上にいる、E = 戦ったが死んでいる
        FakePlayer a = fake(back, "0011", "[titan_end_fa]");
        FakePlayer b = fake(back, "0012", "[titan_end_fb]");
        FakePlayer c = fake(back, "0013", "[titan_end_fc]");
        FakePlayer d = fake(level, "0014", "[titan_end_fd]");
        FakePlayer e = fake(back, "0015", "[titan_end_fe]");
        e.setHealth(0f);
        PartyData data = PartyData.get(server);
        PartyData.Party party = data.create(a.getUUID(), a.getGameProfile().getName());
        data.add(party, b.getUUID(), b.getGameProfile().getName());
        try {
            List<ServerPlayer> all = List.of(a, b, c, d, e);
            List<UUID> fought = List.of(a.getUUID(), d.getUUID(), e.getUUID());
            List<ServerPlayer> picked = TitanEnd.pick(server, back, all, fought);
            check(f, picked.contains(a), "the fighter in the back was not carried out");
            check(f, picked.contains(b), "the fighter's party mate in the back was not carried out");
            check(f, !picked.contains(c), "a player who did not fight (and is not in the party) was carried out");
            check(f, !picked.contains(d), "a fighter who was not in the back was picked");
            check(f, !picked.contains(e) || e.isAlive(), "a dead fighter was picked");
            check(f, picked.size() == 2, "the carry-out picked " + picked.size() + " players instead of 2");
            // パーティでない人だけで戦ったなら、仲間は連れていかない
            List<ServerPlayer> alone = TitanEnd.pick(server, back, all, List.of(c.getUUID()));
            check(f, alone.size() == 1 && alone.get(0) == c, "a lone fighter dragged others along");
        } finally {
            data.remove(a.getUUID());
            data.remove(b.getUUID());
        }
    }

    // ---------------------------------------------------------------- 降ろす場所
    private static void buildPad(ServerLevel level) {
        BlockState stone = Blocks.STONE.defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int x = PAD_X - 8; x < PAD_X + PAD + 8; x++) {
            for (int z = PAD_Z - 8; z < PAD_Z + PAD + 8; z++) {
                for (int y = PAD_Y + 40; y >= PAD_Y; y--) {
                    level.setBlock(new BlockPos(x, y, z), air, 2);
                }
                level.setBlock(new BlockPos(x, PAD_Y - 1, z), stone, 2);
                level.setBlock(new BlockPos(x, PAD_Y - 2, z), stone, 2);
            }
        }
    }

    // 中心 (cx, cz) のまわりに、水たまり (半径 5)・石の輪を挟んで溶岩 (半径 8〜11。水に触れると黒曜石になるので離す)・北西に木の葉の屋根を置く
    private static void hazards(ServerLevel level, int cx, int cz) {
        for (int x = cx - 12; x <= cx + 12; x++) {
            for (int z = cz - 12; z <= cz + 12; z++) {
                double d = Math.hypot(x - cx, z - cz);
                if (d <= 5) {
                    level.setBlock(new BlockPos(x, PAD_Y - 1, z), Blocks.WATER.defaultBlockState(), 2);
                } else if (d > 8 && d <= 11) {
                    level.setBlock(new BlockPos(x, PAD_Y - 1, z), Blocks.LAVA.defaultBlockState(), 2);
                } else if (x < cx - 8 && z < cz - 8) {
                    level.setBlock(new BlockPos(x, PAD_Y, z), Blocks.OAK_LEAVES.defaultBlockState(), 2);
                }
            }
        }
    }

    private static boolean safe(ServerLevel level, Vec3 p, List<String> f, String what) {
        BlockPos feet = BlockPos.containing(p.x, p.y + 0.01, p.z);
        BlockState ground = level.getBlockState(feet.below());
        boolean ok = ground.isSolid() && ground.getFluidState().isEmpty() && !ground.is(Blocks.LAVA) && !ground.is(Blocks.OAK_LEAVES)
                && level.getBlockState(feet).getCollisionShape(level, feet).isEmpty() && level.getBlockState(feet).getFluidState().isEmpty()
                && level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty() && level.getBlockState(feet.above()).getFluidState().isEmpty();
        check(f, ok, what + ": unsafe landing at " + p + " on " + ground.getBlock());
        return ok;
    }

    private static void landing(MinecraftServer server, ServerLevel level, List<String> f) {
        buildPad(level);
        int cx = PAD_X + PAD / 2;
        int cz = PAD_Z + PAD / 2;
        hazards(level, cx, cz);
        // 中心が水たまりの真ん中でも、水・溶岩・木の葉の上には降ろさない
        Vec3 spot = SafeSpot.find(level, cx, cz, 0, 40, SafeSpot.Kind.GROUND, 0);
        check(f, spot != null, "no landing spot found around a pool, lava and leaves");
        if (spot != null) {
            safe(level, spot, f, "ground");
            check(f, Math.hypot(spot.x - cx, spot.z - cz) > 5.5, "the landing is inside the pool (" + spot + ")");
            check(f, !(spot.x < cx - 8 && spot.z < cz - 8 && spot.x > cx - 13 && spot.z > cz - 13), "the landing is on the leaf canopy");
        }
        // 水際 (6 ブロック以内に水): 水たまりのそば
        Vec3 shore = SafeSpot.find(level, cx, cz, 0, 40, SafeSpot.Kind.SHORE, 0);
        if (shore != null) {
            safe(level, shore, f, "shore");
        }
        // 巨神ごと (仮の位置で): 岩鯨の影の下・雷翼の峰の足もと・余燼の丘のかたわら・潮殻の浜
        Vec3 over = new Vec3(PAD_X + 40, 180, PAD_Z + 40);
        TitanEnd.Landing l = TitanEnd.landing(server, Titans.STONE_WHALE, over, null);
        safe(level, l.pos(), f, "whale");
        check(f, Math.hypot(l.pos().x - over.x, l.pos().z - over.z) < 125, "the whale's landing is not below its shadow (" + l.pos() + ")");
        TitanDormancy.begin(server, Titans.THUNDER_WING, 0);
        TitanDormancy.setSpot(server, Titans.THUNDER_WING, PAD_X + 20, PAD_Y, PAD_Z + 20);
        TitanEnd.Landing ls = TitanEnd.landing(server, Titans.THUNDER_WING, new Vec3(PAD_X + 50, 150, PAD_Z + 50), null);
        safe(level, ls.pos(), f, "thunder wing");
        check(f, Math.hypot(ls.pos().x - (PAD_X + 20), ls.pos().z - (PAD_Z + 20)) >= 14, "the thunder wing's landing is on the peak itself");
        TitanDormancy.clear(server, Titans.THUNDER_WING);
        TitanEnd.Landing le = TitanEnd.landing(server, Titans.EMBER, new Vec3(PAD_X + 30, PAD_Y - 50, PAD_Z + 30), null);
        safe(level, le.pos(), f, "ember");
        TitanEnd.Landing lt = TitanEnd.landing(server, Titans.TIDE_SHELL, new Vec3(cx, 40, cz), null);
        safe(level, lt.pos(), f, "tide shell");
        // 向きは巨神のほう
        float expect = jp.revati.skyabyss.cinematic.CameraMath.yawOf(l.titan().subtract(l.pos()));
        check(f, Math.abs(net.minecraft.util.Mth.wrapDegrees(l.yaw() - expect)) < 1f, "the landing does not face the titan");
    }

    // ---------------------------------------------------------------- 初めての人だけの場面
    private static void cinematicOnce(MinecraftServer server, ServerLevel level, List<String> f) {
        buildPad(level);
        String titan = Titans.EMBER;
        String scene = Scenes.endId(titan);
        FakePlayer p = fake(level, "0021", "[titan_end_p]");
        FakePlayer q = fake(level, "0022", "[titan_end_q]");
        Cinematics.stop(p, false);
        Cinematics.stop(q, false);
        Cinematics.state(p).forgetAll();
        Cinematics.state(q).forgetAll();
        TitanEnd.Landing landing = TitanEnd.landing(server, titan, new Vec3(PAD_X + 30, PAD_Y - 50, PAD_Z + 30), null);
        check(f, Scenes.get(scene) != null && Scenes.get(scene).duration() >= 14f && Scenes.get(scene).duration() <= 20f,
                "the dormancy scene has an odd length");
        TitanEnd.deliver(p, titan, landing);
        check(f, scene.equals(Cinematics.activeScene(p)), "the first-clear scene did not start (" + Cinematics.activeScene(p) + ")");
        check(f, Cinematics.state(p).seen(scene), "the first-clear scene was not recorded");
        check(f, !Cinematics.active(q), "another player's screen got the scene");
        Cinematics.end(p, scene, false);
        check(f, !Cinematics.active(p), "the scene did not end");
        // 二回目: 場面なし
        TitanEnd.deliver(p, titan, landing);
        check(f, !Cinematics.active(p), "the scene played a second time for the same player");
        // 別の人は初めて
        TitanEnd.deliver(q, titan, landing);
        check(f, scene.equals(Cinematics.activeScene(q)), "the second player did not get their first-clear scene");
        Cinematics.stop(q, false);
        Cinematics.state(p).forgetAll();
        Cinematics.state(q).forgetAll();
        Tasks.flush();
    }

    // ---------------------------------------------------------------- 心臓が止まってから
    private static void flow(MinecraftServer server, ServerLevel level, List<String> f) {
        ServerLevel back = server.getLevel(TitanTravel.TITAN_BACK);
        if (back == null) {
            return;
        }
        String titan = Titans.STONE_WHALE;
        TitanDormancy.clear(server, titan);
        UUID a = UUID.fromString("7e5a0e0d-0000-4000-8000-0000000e0031");
        TitanEnd.onHeartStopped(back, titan, List.of(a));
        check(f, TitanDormancy.isResting(server, titan), "stopping the heart did not put the titan to sleep");
        check(f, !TitanDormancy.isDormant(server, titan), "the sleep showed at the moment the heart stopped (it should wait for the carry-out)");
        TitanDormancy.Rest r = TitanDormancy.rest(server, titan);
        check(f, r != null && r.since - server.overworld().getGameTime() >= TitanEnd.CARRY_AT - 2, "the sleep does not begin at the carry-out");
        TitanDormancy.clear(server, titan);
        Tasks.flush();
    }
}
