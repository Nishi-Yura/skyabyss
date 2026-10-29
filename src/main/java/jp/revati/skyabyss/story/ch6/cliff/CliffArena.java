package jp.revati.skyabyss.story.ch6.cliff;

import java.util.List;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.quest.Quests;
import jp.revati.skyabyss.story.ch1.Tasks;
import jp.revati.skyabyss.story.ch6.MoonWorld;
import jp.revati.skyabyss.story.ch6.mob.MoonMobsSetup;
import jp.revati.skyabyss.story.ch6.mob.MoonScale;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

// 上弦の崖の主の間 (東の端の皿。docs/design/46 の 4.4)。頂の門で上がった人が来ると月秤が現れ、誰もいなくなると片付ける。
// 倒れたら主の間の内の人 (と組) に合図 moon_lord_waxing。倒したあとも、また来れば戦える (繰り返し)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class CliffArena {
    public static final String TAG = "skyabyss_moon_cliff_arena";
    static final double RADIUS = 22;
    static final int EMPTY_TICKS = 200;
    private static int empty;

    private CliffArena() {
    }

    static AABB box() {
        return new AABB(MoonCliff.mark("arena_center")).inflate(RADIUS, 14, RADIUS);
    }

    public static List<MoonScale> lords(ServerLevel level) {
        return level.getEntitiesOfClass(MoonScale.class, box(), Entity::isAlive);
    }

    static void arrive(ServerLevel level, ServerPlayer p) {
        Notice.place(p, Component.translatable("area.skyabyss.moon_cliff_arena"), Component.translatable("area.skyabyss.moon_cliff_arena.sub"));
        if (lords(level).isEmpty()) {
            Tasks.later(40, () -> summon(level));
        }
    }

    // 月秤を呼ぶ (主の出る所に、皿の縁から登ってくる)
    public static MoonScale summon(ServerLevel level) {
        List<MoonScale> now = lords(level);
        if (!now.isEmpty()) {
            return now.get(0);
        }
        BlockPos at = MoonCliff.mark("lord_spawn");
        MoonScale e = MoonMobsSetup.MOON_SCALE.get().create(level);
        if (e == null) {
            return null;
        }
        BlockPos entry = MoonCliff.mark("arena_entry");
        float yaw = (float) Math.toDegrees(Math.atan2(-(entry.getX() - at.getX()), entry.getZ() - at.getZ()));
        e.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, yaw, 0f);
        boolean wx = jp.revati.skyabyss.story.ch6.mob.MoonMob.waxing(level);
        // 主の体力は区画の倍率をかけない数字 (850。上弦は 1.2 倍)
        e.tuneMoon(1, wx, false, MoonScale.HP / jp.revati.skyabyss.story.ch6.mob.MoonMob.SECTION_HP[1],
                MoonScale.DMG / jp.revati.skyabyss.story.ch6.mob.MoonMob.SECTION_DMG[1]);
        e.addTag(TAG);
        level.addFreshEntity(e);
        e.playAction("roar");
        level.sendParticles(ParticleTypes.END_ROD, at.getX() + 0.5, at.getY() + 2, at.getZ() + 0.5, 60, 1.2, 2.0, 1.2, 0.05);
        level.playSound(null, at, MoonMobsSetup.SCALE_ROAR.get(), net.minecraft.sounds.SoundSource.HOSTILE, 2.0f, 0.7f);
        empty = 0;
        return e;
    }

    // 誰もいなくなったら片付ける (CliffTicker から 20 tick ごと)
    static void tick(ServerLevel level) {
        List<MoonScale> l = lords(level);
        if (l.isEmpty()) {
            return;
        }
        boolean anyone = !level.getEntitiesOfClass(ServerPlayer.class, box(), e -> e.isAlive() && !e.isSpectator()).isEmpty();
        empty = anyone ? 0 : empty + 20;
        if (empty >= EMPTY_TICKS) {
            clear(level);
        }
    }

    public static void clear(ServerLevel level) {
        for (Entity e : level.getEntitiesOfClass(Entity.class, box(), e -> e.getTags().contains(TAG))) {
            if (e instanceof MoonScale s) {
                s.tidy(level);
            }
            e.discard();
        }
        empty = 0;
    }

    @SubscribeEvent
    static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof MoonScale lord) || !(lord.level() instanceof ServerLevel level) || level.dimension() != MoonWorld.LEVEL) {
            return;
        }
        List<ServerPlayer> present = level.getEntitiesOfClass(ServerPlayer.class, box(), e -> e.isAlive());
        Quests.signalWithParty(level, present, CliffPuzzles.LORD);
        for (ServerPlayer p : present) {
            Notice.narrate(p, "message.skyabyss.moon.scale_down");
        }
    }
}
