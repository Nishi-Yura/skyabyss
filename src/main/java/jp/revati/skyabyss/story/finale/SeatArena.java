package jp.revati.skyabyss.story.finale;

import java.util.List;
import java.util.UUID;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.quest.Quests;
import jp.revati.skyabyss.story.finale.mob.CloudMob;
import jp.revati.skyabyss.story.finale.mob.CloudMobsSetup;
import jp.revati.skyabyss.story.finale.mob.FallenPlume;
import jp.revati.skyabyss.story.finale.mob.PilgrimEye;
import jp.revati.skyabyss.story.finale.mob.PilgrimHand;
import jp.revati.skyabyss.story.finale.mob.PilgrimMoon;
import jp.revati.skyabyss.town.TownGuestEntity;
import jp.revati.skyabyss.town.TownSetup;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

// ラスボス「雲上の巡礼者」の戦い (docs/design/50 の 4.3)。巡礼者の全身は見えず、形態ごとに部位が現れる。
//   形態 1 手  輪の外の東西から二つの手 (PilgrimHand)。輪の鐘雲は鐘で固める (島 1 の決まり)。手は叩きつけたあと床に手を突いたまま (打てる隙)・輪の雲を削る (鐘の固まりを消す)
//   形態 2 目  雲が割れて一つ目 (PilgrimEye)。輪は固まったまま。回る視線と凝視は四本の柱の陰で避ける (島 2 の決まり)
//   形態 3 月  引き寄せた月 (PilgrimMoon)。打ちは同じ所でもう一度 (島 3 の決まり)。前の自分たち四人が座の四方に現れて加勢し、術は約束の島と同じ (島 4 の決まり。PromiseIsle の術が使う)
//   形態の間は FORM_GAP の語り。月が倒れたら主の間の内の人 (と組) に合図 finale_lord_pilgrim。誰もいなくなると片付けて座はまた触れられる
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class SeatArena {
    public static final String TAG = "skyabyss_finale_seat";
    public static final String ALLY_TAG = "skyabyss_finale_seat_ally";
    public static final String LORD = "finale_lord_pilgrim";
    static final int EMPTY_TICKS = 200;
    static final int FORM_GAP = 80;
    static final int KEEP_RINGING = 300;
    private static int form;
    private static long nextAt = -1;
    private static boolean spawned;
    private static int empty;
    private static long ringAt;

    private SeatArena() {
    }

    public static int form() {
        return form;
    }

    public static AABB box() {
        BlockPos c = PilgrimSeat.mark("arena");
        return new AABB(c).inflate(26, 6, 26).move(0, 8, -2);
    }

    public static List<ServerPlayer> present(ServerLevel level) {
        return level.getEntitiesOfClass(ServerPlayer.class, box(), e -> e.isAlive() && !e.isSpectator());
    }

    public static List<CloudMob> parts(ServerLevel level) {
        return level.getEntitiesOfClass(CloudMob.class, box().inflate(4), e -> e.isAlive() && e.getTags().contains(TAG)
                && (e instanceof PilgrimHand || e instanceof PilgrimEye || e instanceof PilgrimMoon));
    }

    // 戦っている部位 (崖の私が前へ出る向き)
    public static CloudMob boss(ServerLevel level) {
        List<CloudMob> l = parts(level);
        return l.isEmpty() ? null : l.get(0);
    }

    // ---------------------------------------------------------------- 加勢の四人 (形態 3)
    public static List<TownGuestEntity> allies(ServerLevel level) {
        return level.getEntities(TownSetup.GUEST.get(), box(), e -> e.isAlive() && e.getTags().contains(ALLY_TAG));
    }

    public static TownGuestEntity ally(ServerLevel level, int k) {
        if (form != 3 && form != 4) {
            return null;
        }
        for (TownGuestEntity g : allies(level)) {
            if (PromiseIsle.guest(k).equals(g.guestId())) {
                return g;
            }
        }
        return null;
    }

    static void callAllies(ServerLevel level) {
        if (!allies(level).isEmpty()) {
            return;
        }
        BlockPos c = PilgrimSeat.mark("lord_spawn");
        int[][] at = {{0, 5}, {-5, 0}, {5, 0}, {0, -5}};
        for (int k = 1; k <= 4; k++) {
            TownGuestEntity g = TownSetup.GUEST.get().create(level);
            if (g == null) {
                continue;
            }
            g.setGuest(PromiseIsle.guest(k));
            g.setCustomName(Component.translatable("entity.skyabyss.finale_self_" + k));
            BlockPos p = c.offset(at[k - 1][0], 0, at[k - 1][1]);
            g.moveTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, 0f, 0f);
            g.setHome(p);
            g.addTag(ALLY_TAG);
            g.addTag(TAG);
            level.addFreshEntity(g);
            level.sendParticles(ParticleTypes.END_ROD, p.getX() + 0.5, p.getY() + 1, p.getZ() + 0.5, 40, 0.4, 1.0, 0.4, 0.06);
        }
        level.playSound(null, c, CloudMobsSetup.SELF_JOIN.get(), SoundSource.NEUTRAL, 2.0f, 0.9f);
    }

    // 四人は座の中の人に付いて歩く (k 番目の人。人が少なければ先頭へ)
    static void leadAllies(ServerLevel level) {
        List<ServerPlayer> here = present(level);
        if (here.isEmpty()) {
            return;
        }
        for (TownGuestEntity g : allies(level)) {
            int k = Integer.parseInt(g.guestId().substring(g.guestId().length() - 1));
            if (k == 2 && PromiseIsle.taunting(level) == g) {
                continue;
            }
            ServerPlayer p = here.get((k - 1) % here.size());
            if (k == 1) {
                p = here.get(0);
            }
            if (!p.getUUID().equals(g.follow())) {
                g.setFollow(p.getUUID());
            }
        }
    }

    public static UUID leadOf(TownGuestEntity g) {
        return g.follow();
    }

    // ---------------------------------------------------------------- 形態
    public static void start(ServerLevel level, int f) {
        clear(level);
        form = f;
        spawned = false;
        nextAt = level.getGameTime() + (f == 1 ? 60 : 20);
        empty = 0;
        for (ServerPlayer p : present(level)) {
            Notice.place(p, Component.translatable("area.skyabyss.finale_seat"), Component.translatable("area.skyabyss.finale_seat.sub"));
            Notice.narrate(p, "message.skyabyss.seat.form" + f);
        }
        if (f == 1) {
            PilgrimSeat.ringAll(level);
        }
    }

    static void summonForm(ServerLevel level) {
        BlockPos c = PilgrimSeat.mark("lord_spawn");
        switch (form) {
            case 1 -> {
                for (String side : List.of("e", "w")) {
                    BlockPos at = PilgrimSeat.mark("hand_" + side);
                    PilgrimHand h = CloudMobsSetup.PILGRIM_HAND.get().create(level);
                    if (h != null) {
                        h.setHome(at, side.equals("e") ? 0 : 70);
                        h.moveTo(at.getX() + 0.5, at.getY() - 6, at.getZ() + 0.5, side.equals("e") ? 90f : 270f, 0f);
                        h.tuneCloud(1, PilgrimHand.HP, PilgrimHand.DMG);
                        h.addTag(TAG);
                        level.addFreshEntity(h);
                        level.sendParticles(ParticleTypes.CLOUD, at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 60, 2.0, 2.0, 2.0, 0.05);
                    }
                }
                level.playSound(null, c, CloudMobsSetup.HAND_RISE.get(), SoundSource.HOSTILE, 3.0f, 0.9f);
            }
            case 2 -> {
                PilgrimSeat.ringAll(level);
                ringAt = level.getGameTime();
                PilgrimEye e = CloudMobsSetup.PILGRIM_EYE.get().create(level);
                if (e != null) {
                    e.moveTo(c.getX() + 0.5, c.getY() + PilgrimEye.HOVER, c.getZ() + 0.5, 180f, 0f);
                    e.tuneCloud(1, PilgrimEye.HP, PilgrimEye.DMG);
                    e.addTag(TAG);
                    level.addFreshEntity(e);
                    e.playAction("call");
                }
                level.sendParticles(ParticleTypes.CLOUD, c.getX() + 0.5, c.getY() + 6, c.getZ() + 0.5, 120, 4.0, 2.0, 4.0, 0.08);
                level.playSound(null, c, CloudMobsSetup.EYE_OPEN.get(), SoundSource.HOSTILE, 3.0f, 0.8f);
            }
            case 3 -> {
                PilgrimSeat.ringAll(level);
                ringAt = level.getGameTime();
                PilgrimMoon m = CloudMobsSetup.PILGRIM_MOON.get().create(level);
                if (m != null) {
                    m.moveTo(c.getX() + 0.5, c.getY() + PilgrimMoon.HOVER, c.getZ() + 0.5, 180f, 0f);
                    m.tuneCloud(1, PilgrimMoon.HP, PilgrimMoon.DMG);
                    m.addTag(TAG);
                    level.addFreshEntity(m);
                    m.playAction("call");
                }
                callAllies(level);
                level.playSound(null, c, CloudMobsSetup.PILGRIM_ROAR.get(), SoundSource.HOSTILE, 3.0f, 0.8f);
            }
            default -> {
            }
        }
        spawned = true;
    }

    // 段の変わり目に呼ぶ落ち羽 (床の上に)
    public static void callPlumes(ServerLevel level, int n) {
        BlockPos c = PilgrimSeat.mark("lord_spawn");
        for (int i = 0; i < n; i++) {
            double a = level.random.nextDouble() * Math.PI * 2;
            BlockPos at = BlockPos.containing(c.getX() + 0.5 + Math.cos(a) * 9, c.getY() + 3, c.getZ() + 0.5 + Math.sin(a) * 9);
            CloudMob m = PromiseArena.put(level, CloudMobsSetup.FALLEN_PLUME.get(), at);
            if (m != null) {
                m.addTag(TAG);
            }
        }
    }

    // 毎 tick (PilgrimSeat から): 形態の切り替えと、四人の術
    static void everyTick(ServerLevel level) {
        long now = level.getGameTime();
        if (form >= 1 && form <= 3 && !spawned && nextAt >= 0 && now >= nextAt) {
            summonForm(level);
        }
        if ((form == 3 || form == 4) && !allies(level).isEmpty()) {
            PromiseIsle.arts(level);
        }
        if ((form == 2 || form == 3) && spawned && now - ringAt >= KEEP_RINGING) {
            PilgrimSeat.ringAll(level);
            ringAt = now;
        }
    }

    // 20 tick ごと (PilgrimSeat から): 次の形態へ・片付け
    static void tick(ServerLevel level) {
        if (form == 0) {
            return;
        }
        long now = level.getGameTime();
        List<ServerPlayer> here = present(level);
        empty = here.isEmpty() ? empty + 20 : 0;
        if (empty >= EMPTY_TICKS) {
            clear(level);
            return;
        }
        if (form == 3 || form == 4) {
            leadAllies(level);
        }
        if (form >= 1 && form <= 3 && spawned && parts(level).isEmpty()) {
            if (form == 3) {
                return;
            }
            form++;
            spawned = false;
            nextAt = now + FORM_GAP;
            for (ServerPlayer p : here) {
                Notice.narrate(p, "message.skyabyss.seat.form" + form);
                if (form == 3) {
                    jp.revati.skyabyss.story.finale.LampIsle.gaze(p, false, 0, 0f, null);
                }
            }
        }
    }

    public static void clear(ServerLevel level) {
        for (Entity e : level.getEntitiesOfClass(Entity.class, box().inflate(6), e -> e.getTags().contains(TAG))) {
            e.discard();
        }
        for (ServerPlayer p : present(level)) {
            LampIsle.gaze(p, false, 0, 0f, null);
        }
        form = 0;
        spawned = false;
        nextAt = -1;
        empty = 0;
    }

    // 月が倒れた: 合図と語り。四人はしばらく残って帰る
    @SubscribeEvent
    static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof PilgrimMoon moon) || !(moon.level() instanceof ServerLevel level) || level.dimension() != FinaleWorld.LEVEL) {
            return;
        }
        List<ServerPlayer> here = present(level);
        Quests.signalWithParty(level, here, LORD);
        for (ServerPlayer p : here) {
            Notice.narrate(p, "message.skyabyss.seat.down");
            Notice.narrate(p, "message.skyabyss.seat.next");
        }
        for (Entity e : level.getEntitiesOfClass(FallenPlume.class, box(), e -> e.getTags().contains(TAG))) {
            e.discard();
        }
        form = 4;
        jp.revati.skyabyss.story.ch1.Tasks.later(400, () -> {
            for (TownGuestEntity g : allies(level)) {
                level.sendParticles(ParticleTypes.END_ROD, g.getX(), g.getY() + 1, g.getZ(), 30, 0.4, 1.0, 0.4, 0.06);
                g.discard();
            }
            if (form == 4) {
                form = 0;
            }
        });
    }

    // 開発用・自己テスト向け: その形態の部位をすぐ出す
    public static void toForm(ServerLevel level, int f) {
        start(level, f);
        summonForm(level);
    }
}
