package jp.revati.skyabyss.story.ch6.valley;

import java.util.List;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.quest.Quests;
import jp.revati.skyabyss.story.ch1.Tasks;
import jp.revati.skyabyss.story.ch6.MoonWorld;
import jp.revati.skyabyss.story.ch6.mob.MoonMob;
import jp.revati.skyabyss.story.ch6.mob.MoonMobsSetup;
import jp.revati.skyabyss.story.ch6.mob.ShadowEmbracer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

// 新月の谷の主の間 (台地の上。docs/design/46 の 4.3)。星の門で上がった人が来ると影抱きが現れ、誰もいなくなると片付ける。
// 倒れたら主の間の内の人 (と組) に合図 moon_lord_new。灯りの台は戦いが終わると消える。倒したあとも、また来れば戦える (繰り返し)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class ValleyArena {
    public static final String TAG = "skyabyss_moon_arena";
    static final double RADIUS = 26;
    static final int EMPTY_TICKS = 200;
    private static int empty;

    private ValleyArena() {
    }

    static AABB box() {
        return new AABB(MoonValley.mark("arena_center")).inflate(RADIUS, 12, RADIUS);
    }

    public static List<ShadowEmbracer> lords(ServerLevel level) {
        return level.getEntitiesOfClass(ShadowEmbracer.class, box(), Entity::isAlive);
    }

    static void arrive(ServerLevel level, ServerPlayer p) {
        Notice.place(p, net.minecraft.network.chat.Component.translatable("area.skyabyss.moon_arena"),
                net.minecraft.network.chat.Component.translatable("area.skyabyss.moon_arena.sub"));
        if (lords(level).isEmpty()) {
            Tasks.later(40, () -> summon(level));
        }
    }

    // 影抱きを呼ぶ (珠の台の上に、影から立ち上がる)
    public static ShadowEmbracer summon(ServerLevel level) {
        List<ShadowEmbracer> now = lords(level);
        if (!now.isEmpty()) {
            return now.get(0);
        }
        BlockPos at = MoonValley.mark("lord_spawn");
        ShadowEmbracer e = MoonMobsSetup.SHADOW_EMBRACER.get().create(level);
        if (e == null) {
            return null;
        }
        e.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 180f, 0f);
        boolean nm = MoonMob.newMoon(level);
        // 主の体力は区画の倍率をかけない数字 (1100。新月は 1.2 倍)
        e.tuneMoon(0, nm, false, ShadowEmbracer.HP / MoonMob.SECTION_HP[0], ShadowEmbracer.DMG / MoonMob.SECTION_DMG[0]);
        e.addTag(TAG);
        level.addFreshEntity(e);
        e.playAction("rise");
        level.sendParticles(ParticleTypes.SQUID_INK, at.getX() + 0.5, at.getY() + 1, at.getZ() + 0.5, 80, 1.2, 1.5, 1.2, 0.05);
        level.playSound(null, at, MoonMobsSetup.EMBRACER_ROAR.get(), net.minecraft.sounds.SoundSource.HOSTILE, 2.0f, 0.7f);
        empty = 0;
        return e;
    }

    // 誰もいなくなったら片付ける (ValleyTicker から 20 tick ごと)
    static void tick(ServerLevel level) {
        List<ShadowEmbracer> l = lords(level);
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
            e.discard();
        }
        for (int i = 0; i < 6; i++) {
            LampPostBlock.snuff(level, MoonValley.mark("lamp_" + i));
        }
        empty = 0;
    }

    @SubscribeEvent
    static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ShadowEmbracer lord) || !(lord.level() instanceof ServerLevel level) || level.dimension() != MoonWorld.LEVEL) {
            return;
        }
        List<ServerPlayer> present = level.getEntitiesOfClass(ServerPlayer.class, box(), e -> e.isAlive());
        Quests.signalWithParty(level, present, ValleyPuzzles.LORD);
        for (ServerPlayer p : present) {
            Notice.narrate(p, "message.skyabyss.moon.lord_down");
        }
        Tasks.later(100, () -> {
            for (Entity e : level.getEntitiesOfClass(Entity.class, box(), e -> e.getTags().contains(TAG) && e != lord)) {
                e.discard();
            }
            for (int i = 0; i < 6; i++) {
                LampPostBlock.snuff(level, MoonValley.mark("lamp_" + i));
            }
        });
    }
}
