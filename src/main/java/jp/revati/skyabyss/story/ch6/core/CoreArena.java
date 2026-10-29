package jp.revati.skyabyss.story.ch6.core;

import java.util.ArrayList;
import java.util.List;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.quest.Quests;
import jp.revati.skyabyss.story.ch1.Tasks;
import jp.revati.skyabyss.story.ch6.MoonWorld;
import jp.revati.skyabyss.story.ch6.mob.MoonCoreBoss;
import jp.revati.skyabyss.story.ch6.mob.MoonMob;
import jp.revati.skyabyss.story.ch6.mob.MoonMobsSetup;
import jp.revati.skyabyss.story.reel.StoryReels;
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
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

// 月の核の間 (月の奥の球の空洞の床。docs/design/46 の 4.7)。門で下りた人が来ると月の核が目を開け、誰もいなくなると片付ける。
// 段 3 で四つの台に前の自分たち四人を立たせ (CoreTicker が術で助ける)、満月落としの無事な円にする。
// 核が止まると主の間の内の人 (と組) に合図 moon_lord_core、四人は光に還り、人ごとに語りの紙芝居「月が止まる」→ 合図 moon_core_end
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class CoreArena {
    public static final String TAG = "skyabyss_moon_core_arena";
    public static final String[] SEATS = {"seat_valley", "seat_cliff", "seat_field", "seat_rift"};
    public static final String[] SELVES = {"moon_past1", "moon_past2", "moon_past3", "moon_past4"};
    static final double RADIUS = 20;
    static final int EMPTY_TICKS = 200;
    private static int empty;

    static {
        StoryReels.onDone(CorePuzzles.REEL, p -> Quests.signal(p, CorePuzzles.END));
    }

    private CoreArena() {
    }

    static AABB box() {
        return new AABB(MoonCore.mark("arena_center")).inflate(RADIUS, 16, RADIUS);
    }

    public static List<ServerPlayer> players(ServerLevel level) {
        return level.getEntitiesOfClass(ServerPlayer.class, box(), e -> e.isAlive() && !e.isSpectator());
    }

    public static List<MoonCoreBoss> lords(ServerLevel level) {
        return level.getEntitiesOfClass(MoonCoreBoss.class, box(), Entity::isAlive);
    }

    public static List<TownGuestEntity> allyList(ServerLevel level) {
        return level.getEntities(TownSetup.GUEST.get(), box(), e -> e.isAlive() && e.getTags().contains(TAG));
    }

    static void arrive(ServerLevel level, ServerPlayer p) {
        Notice.place(p, Component.translatable("area.skyabyss.moon_core_arena"), Component.translatable("area.skyabyss.moon_core_arena.sub"));
        if (lords(level).isEmpty()) {
            Tasks.later(40, () -> summon(level));
        }
    }

    // 月の核を呼ぶ (床の真ん中の上に、光が集まって珠になる)
    public static MoonCoreBoss summon(ServerLevel level) {
        List<MoonCoreBoss> now = lords(level);
        if (!now.isEmpty()) {
            return now.get(0);
        }
        BlockPos at = MoonCore.mark("arena_center");
        MoonCoreBoss e = MoonMobsSetup.MOON_CORE.get().create(level);
        if (e == null) {
            return null;
        }
        e.moveTo(at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, 180f, 0f);
        boolean full = MoonMob.fullMoon(level);
        // 核の体力は区画の倍率をかけない数字 (850。満月は 1.2 倍)
        e.tuneMoon(3, full, false, MoonCoreBoss.HP / MoonMob.SECTION_HP[3], MoonCoreBoss.DMG / MoonMob.SECTION_DMG[3]);
        e.addTag(TAG);
        level.addFreshEntity(e);
        e.playAction("roar");
        level.sendParticles(ParticleTypes.END_ROD, at.getX() + 0.5, at.getY() + 3, at.getZ() + 0.5, 120, 2.0, 2.0, 2.0, 0.08);
        level.playSound(null, at, MoonMobsSetup.CORE_ROAR.get(), SoundSource.HOSTILE, 3.0f, 0.7f);
        empty = 0;
        return e;
    }

    // 段 3: 四つの台に前の自分たち (傷を受けない。CoreTicker が術で助ける)
    public static void allies(ServerLevel level) {
        if (!allyList(level).isEmpty()) {
            return;
        }
        BlockPos c = MoonCore.mark("arena_center");
        for (int i = 0; i < 4; i++) {
            BlockPos at = MoonCore.mark(SEATS[i]);
            TownGuestEntity g = TownSetup.GUEST.get().create(level);
            if (g == null) {
                continue;
            }
            g.setGuest(SELVES[i]);
            float yaw = (float) Math.toDegrees(Math.atan2(-(c.getX() - at.getX()), c.getZ() - at.getZ()));
            g.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, yaw, 0f);
            g.setHome(at);
            g.addTag(TAG);
            level.addFreshEntity(g);
            level.sendParticles(ParticleTypes.END_ROD, at.getX() + 0.5, at.getY() + 1, at.getZ() + 0.5, 40, 0.4, 1.0, 0.4, 0.06);
        }
        level.playSound(null, c, MoonMobsSetup.CORE_ALLIES.get(), SoundSource.NEUTRAL, 2.0f, 1.0f);
    }

    // 満月落としで無事な所 (前の自分たちの立つ所。四人がいないときは無い)
    public static List<Vec3> safeSpots(ServerLevel level) {
        List<Vec3> out = new ArrayList<>();
        for (TownGuestEntity g : allyList(level)) {
            out.add(g.position());
        }
        return out;
    }

    // 誰もいなくなったら片付ける (CoreTicker から 20 tick ごと)
    static void tick(ServerLevel level) {
        List<MoonCoreBoss> l = lords(level);
        if (l.isEmpty()) {
            return;
        }
        empty = players(level).isEmpty() ? empty + 20 : 0;
        if (empty >= EMPTY_TICKS) {
            clear(level);
        }
    }

    public static void clear(ServerLevel level) {
        for (Entity e : level.getEntitiesOfClass(Entity.class, box().inflate(4), e -> e.getTags().contains(TAG) && !(e instanceof ServerPlayer))) {
            e.discard();
        }
        empty = 0;
    }

    @SubscribeEvent
    static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof MoonCoreBoss core) || !(core.level() instanceof ServerLevel level) || level.dimension() != MoonWorld.LEVEL) {
            return;
        }
        for (Entity e : level.getEntitiesOfClass(Entity.class, box().inflate(4), e -> e != core && e.getTags().contains(TAG)
                && !(e instanceof TownGuestEntity) && !(e instanceof ServerPlayer))) {
            e.discard();
        }
        // 四人は光に還る (少し間を置いて)
        Tasks.later(60, () -> {
            for (TownGuestEntity g : allyList(level)) {
                level.sendParticles(ParticleTypes.END_ROD, g.getX(), g.getY() + 1, g.getZ(), 60, 0.4, 1.2, 0.4, 0.08);
                g.discard();
            }
        });
        List<ServerPlayer> present = level.getEntitiesOfClass(ServerPlayer.class, box(), e -> e.isAlive());
        Quests.signalWithParty(level, present, CorePuzzles.LORD);
        for (ServerPlayer p : present) {
            Notice.narrate(p, "message.skyabyss.moon.core_down");
            Tasks.later(100, () -> {
                if (p.isAlive() && CorePuzzles.stage(p) == CorePuzzles.stageIndex("end")) {
                    StoryReels.play(p, CorePuzzles.REEL);
                }
            });
        }
    }
}
