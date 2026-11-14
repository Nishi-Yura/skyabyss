package jp.revati.skyabyss.combat;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

// 主の登場の「一瞬の見せ場」: 地の中から這い出る (2026-11-11 の声。最初の目的の三つ目「地中からの出現」)。
// 層の主・淵の王は、呼ばれた所に立ったまま現れていた。出る間は体を床の下に沈めて描き (client/titan/BossEmergeClient)、
// 出方は主ごとに違う (style):
//   king       淵の王: 黒い淵からゆっくりせり上がる (墨の煙)
//   lord_rib   肋の主: 引っかかりながら三度に分けて這い上がる
//   lord_salt  白殻の主: 回りながらねじり出る
//   lord_storm 鼓動まねの主: 地の下で三度脈打ってから、跳び出して着地する
//   lord_ash   前のめりに這い出て、身を起こす (今は使っていない)
// 地の中から以外の出方 (2026-11-11 の声「色んな出現方法があって良い」):
//   drop    空 (天井) から落ちてきて潰れ、弾んで立つ          白殻の主・砦の将
//   thread  糸で天井から降り、揺れて、糸が切れて落ちる          灯り喰いの主 (蜘蛛)
//   sky     空から回りながらゆっくり降りる (雷をまとう)        雷の王・雷の梟・海月の巫女
//   grow    結晶のように小さく芽を出し、回りながら育つ          塩の王・氷の主
//   unfold  紙が開くように、平たい所から縦に広がる              影の王・神殿の守り手
//   erupt   地の下で溜めて、火と一緒に噴き上がる               灰の王・溶岩の竜
// 出る間は動かず傷も受けない。出きったら done (名前の札と顔のアップなど) を流す。
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class BossEmerge {
    public static final int EMERGE = 2;

    private record Run(ServerLevel level, UUID id, String style, int ticks, List<ServerPlayer> watch, Runnable done, boolean wasInvul, int[] age, boolean lookOnly, boolean wasNoAi) {
    }

    private static final List<Run> RUNS = new ArrayList<>();

    private BossEmerge() {
    }

    // 体が抜けきる (床が割れて大きな音) 時: 出る長さの割合
    static float burstAt(String style) {
        return switch (style) {
            case "lord_storm" -> 0.6f;
            case "lord_salt" -> 0.7f;
            case "lord_ash" -> 0.72f;
            case "drop" -> 0.72f;
            case "thread" -> 0.78f;
            case "sky" -> 0.85f;
            case "grow" -> 0.7f;
            case "unfold" -> 0.7f;
            case "erupt" -> 0.62f;
            case "lord_rib" -> 0.88f;
            default -> 0.82f;
        };
    }

    // 見た目だけ (止める・守る・あとの処理は呼ぶ側。ダンジョンの主の登場): 近くの人へ出方を送り、効果も出す
    public static void visual(LivingEntity boss, String style, int ticks) {
        if (!(boss.level() instanceof ServerLevel level)) {
            return;
        }
        List<ServerPlayer> watch = new ArrayList<>();
        for (ServerPlayer p : level.players()) {
            if (!(p instanceof net.neoforged.neoforge.common.util.FakePlayer) && p.distanceToSqr(boss) < BossIntro.RANGE * BossIntro.RANGE) {
                watch.add(p);
            }
        }
        BossIntro.Payload payload = new BossIntro.Payload(EMERGE, boss.getDisplayName(), style, ticks, Component.empty(), boss.getId());
        for (ServerPlayer p : watch) {
            PacketDistributor.sendToPlayer(p, payload);
        }
        RUNS.add(new Run(level, boss.getUUID(), style, ticks, watch, () -> {
        }, boss.isInvulnerable(), new int[] {0}, true, boss instanceof Mob m0 && m0.isNoAi()));
    }

    // ボスの登場のひとまとめ (終章・月・章の道中のボス。2026-11-11 の声「最初の作り込みを満たしてる？」):
    // 近くの人に、出方 style で現れ (null なら出方なし)、出きったら名前の札 (肩書き gui.skyabyss.boss_intro.sub.<subKey>) と顔のアップ
    public static void stage(LivingEntity boss, @javax.annotation.Nullable String style, int ticks, String subKey) {
        if (!(boss.level() instanceof ServerLevel level)) {
            return;
        }
        List<ServerPlayer> watch = new ArrayList<>();
        for (ServerPlayer p : level.players()) {
            if (!(p instanceof net.neoforged.neoforge.common.util.FakePlayer) && p.distanceToSqr(boss) < BossIntro.RANGE * BossIntro.RANGE) {
                watch.add(p);
            }
        }
        if (watch.isEmpty()) {
            // 見る人がいない (自己テストの偽の人だけ・誰もいない所で呼ばれた): 出方も止める守りもしない
            return;
        }
        Component sub = Component.translatable("gui.skyabyss.boss_intro.sub." + subKey);
        Runnable card = () -> BossIntro.card(watch, boss.getDisplayName(), sub, 90, boss);
        if (style == null) {
            card.run();
        } else {
            start(boss, style, ticks, watch, card);
        }
    }

    // 章の道中のボスの出方 (主の種類の名前 → 出方。null = 出方なしで名前の札と顔のアップだけ。自分で現れる潮招き・止まり木の雷獣など)
    static final java.util.Map<String, String> CHAPTER = java.util.Map.of(
            "elder_sentry", "lord_rib", "shore_warden", "grow", "elder_thrall", "erupt", "whalebone_captain", "drop", "ash_smith", "erupt");

    // 章の道中のボスの登場 (呼ばれた所から)。種類の名前で出方と肩書きを決める
    public static void chapter(LivingEntity boss) {
        String key = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(boss.getType()).getPath();
        boss.addTag(INTRO_TAG);
        stage(boss, CHAPTER.get(key), 44, key);
    }

    // 置かれたまま待つボス (焼けた羽守など): 本物の人が range の内に初めて来たら、名前の札と顔のアップ (一度だけ)
    public static final String INTRO_TAG = "skyabyss_boss_intro_done";

    public static void firstSight(LivingEntity boss, double range) {
        if (!(boss.level() instanceof ServerLevel level) || boss.getTags().contains(INTRO_TAG) || boss.tickCount % 5 != 0) {
            return;
        }
        for (ServerPlayer p : level.players()) {
            if (!(p instanceof net.neoforged.neoforge.common.util.FakePlayer) && !p.isSpectator() && p.isAlive() && p.distanceToSqr(boss) < range * range) {
                String key = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(boss.getType()).getPath();
                boss.addTag(INTRO_TAG);
                stage(boss, null, 0, key);
                return;
            }
        }
    }

    public static boolean ground(String style) {
        return style.startsWith("lord_") || style.equals("king") || style.equals("erupt");
    }

    public static void start(LivingEntity boss, String style, int ticks, Iterable<ServerPlayer> players, Runnable done) {
        if (!(boss.level() instanceof ServerLevel level)) {
            done.run();
            return;
        }
        List<ServerPlayer> watch = new ArrayList<>();
        players.forEach(watch::add);
        boolean invul = boss.isInvulnerable();
        boolean noAi = boss instanceof Mob m0 && m0.isNoAi();
        boss.setInvulnerable(true);
        if (boss instanceof Mob m) {
            m.setNoAi(true);
            m.getNavigation().stop();
        }
        BossIntro.Payload payload = new BossIntro.Payload(EMERGE, boss.getDisplayName(), style, ticks, Component.empty(), boss.getId());
        for (ServerPlayer p : watch) {
            PacketDistributor.sendToPlayer(p, payload);
        }
        RUNS.add(new Run(level, boss.getUUID(), style, ticks, watch, done, invul, new int[] {0}, false, noAi));
    }

    @SubscribeEvent
    static void onTick(ServerTickEvent.Post event) {
        if (RUNS.isEmpty()) {
            return;
        }
        for (Iterator<Run> it = RUNS.iterator(); it.hasNext(); ) {
            Run r = it.next();
            Entity e = r.level().getEntity(r.id());
            if (!(e instanceof LivingEntity le) || !le.isAlive()) {
                it.remove();
                continue;
            }
            int age = ++r.age()[0];
            effects(r, le, age);
            if (age >= r.ticks()) {
                it.remove();
                if (r.lookOnly()) {
                    continue;
                }
                le.setInvulnerable(r.wasInvul());
                if (le instanceof Mob m) {
                    // 出る前の AI の有無に戻す (自己テストや撮影で AI を止めている主を動かさない)
                    m.setNoAi(r.wasNoAi());
                }
                r.done().run();
            }
        }
    }

    // 地の中から以外の出方の、出きる前の効果
    private static void airEffects(Run r, LivingEntity le, int age, float p, double w) {
        ServerLevel level = r.level();
        double x = le.getX(), y = le.getY(), z = le.getZ();
        double top = y + le.getBbHeight() * 3;
        switch (r.style()) {
            case "drop", "thread" -> {
                // 落ちてくる所の床に影、上から灰がこぼれる
                if (age % 3 == 0) {
                    level.sendParticles(ParticleTypes.SMOKE, x, y + 0.1, z, 8, w * (1.2 - p), 0.02, w * (1.2 - p), 0.0);
                    level.sendParticles(ParticleTypes.ASH, x, top, z, 6, w * 0.6, 0.5, w * 0.6, 0.0);
                }
                if ("thread".equals(r.style()) && age % 2 == 0) {
                    // 天井から主の背へ下がる白い糸 (糸が切れる時まで)
                    double from = y + le.getBbHeight() * 2.6 * (1 - p * 0.6);
                    for (double yy = from; yy < from + 12; yy += 0.6) {
                        level.sendParticles(ParticleTypes.END_ROD, x, yy, z, 1, 0.02, 0.0, 0.02, 0.0);
                    }
                }
                if ("thread".equals(r.style()) && age % 8 == 0) {
                    level.playSound(null, x, y, z, SoundEvents.SPIDER_AMBIENT, SoundSource.HOSTILE, 1.4f, 0.5f);
                }
            }
            case "sky" -> {
                if (age % 3 == 0) {
                    level.sendParticles(ParticleTypes.ELECTRIC_SPARK, x, y + le.getBbHeight() * 2.2 * (1 - p) + 1, z, 16, w, 1.0, w, 0.2);
                }
                if (age % 15 == 0) {
                    level.playSound(null, x, y, z, SoundEvents.TRIDENT_THUNDER.value(), SoundSource.HOSTILE, 0.8f, 1.4f);
                }
            }
            case "grow" -> {
                if (age % 2 == 0) {
                    level.sendParticles(ParticleTypes.END_ROD, x, y + 0.6, z, 6, w * p, 1.0 * p + 0.2, w * p, 0.02);
                    level.sendParticles(ParticleTypes.SNOWFLAKE, x, y + 0.4, z, 8, w, 0.4, w, 0.02);
                }
                if (age % 6 == 0) {
                    level.playSound(null, x, y, z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.HOSTILE, 2.0f, 0.5f + p);
                }
            }
            case "unfold" -> {
                if (age % 2 == 0) {
                    level.sendParticles(ParticleTypes.WHITE_ASH, x, y + 0.5 + p * le.getBbHeight(), z, 18, w, 0.3, w, 0.02);
                }
                if (age % 7 == 0) {
                    level.playSound(null, x, y, z, SoundEvents.BOOK_PAGE_TURN, SoundSource.HOSTILE, 2.5f, 0.6f);
                }
            }
            default -> {
            }
        }
    }

    private static void effects(Run r, LivingEntity le, int age) {
        ServerLevel level = r.level();
        float p = age / (float) r.ticks();
        int burst = Math.round(burstAt(r.style()) * r.ticks());
        BlockState floor = level.getBlockState(BlockPos.containing(le.getX(), le.getY() - 0.5, le.getZ()));
        if (floor.isAir()) {
            floor = Blocks.DEEPSLATE.defaultBlockState();
        }
        BlockParticleOption dust = new BlockParticleOption(ParticleTypes.BLOCK, floor);
        double w = le.getBbWidth() * 0.7 + 0.8;
        double x = le.getX(), y = le.getY() + 0.15, z = le.getZ();
        if (age < burst && !ground(r.style())) {
            airEffects(r, le, age, p, w);
        } else if (age < burst) {
            // 地鳴り: 土が跳ね、低い音がだんだん大きく
            if (age % 2 == 0) {
                level.sendParticles(dust, x, y, z, (int) (6 + 22 * p), w, 0.1, w, 0.12);
            }
            switch (r.style()) {
                case "king" -> {
                    if (age % 3 == 0) {
                        level.sendParticles(ParticleTypes.SQUID_INK, x, y + 0.2, z, 10, w * 0.9, 0.05, w * 0.9, 0.02);
                        level.sendParticles(ParticleTypes.LARGE_SMOKE, x, y + 0.4, z, 4, w * 0.6, 0.2, w * 0.6, 0.01);
                    }
                }
                case "lord_storm" -> {
                    // 地の下の鼓動: 三度、土が輪に跳ねる
                    if (age == Math.round(r.ticks() * 0.15f) || age == Math.round(r.ticks() * 0.3f) || age == Math.round(r.ticks() * 0.45f)) {
                        level.sendParticles(dust, x, y, z, 50, w * 1.2, 0.3, w * 1.2, 0.25);
                        level.playSound(null, x, y, z, SoundEvents.WARDEN_HEARTBEAT, SoundSource.HOSTILE, 2.5f, 0.6f);
                    }
                }
                case "lord_salt" -> {
                    if (age % 3 == 0) {
                        level.sendParticles(ParticleTypes.WHITE_ASH, x, y + 0.3, z, 14, w, 0.3, w, 0.02);
                    }
                }
                case "lord_ash" -> {
                    if (age % 3 == 0) {
                        level.sendParticles(ParticleTypes.SMOKE, x, y + 0.3, z, 8, w, 0.2, w, 0.02);
                    }
                }
                default -> {
                }
            }
            if (age % 10 == 0) {
                level.playSound(null, x, y, z, SoundEvents.WARDEN_DIG, SoundSource.HOSTILE, 1.6f + p, 0.5f);
            }
            if ("lord_rib".equals(r.style()) && (age == Math.round(r.ticks() * 0.15f) || age == Math.round(r.ticks() * 0.42f) || age == Math.round(r.ticks() * 0.68f))) {
                // 這い上がる一段ごとに骨が鳴る
                level.sendParticles(dust, x, y + 0.4, z, 40, w, 0.4, w, 0.2);
                level.playSound(null, x, y, z, SoundEvents.SKELETON_STEP, SoundSource.HOSTILE, 2.5f, 0.5f);
            }
        } else if (age == burst) {
            // 抜けきる: 床が割れて噴く
            level.sendParticles(dust, x, y + 0.6, z, 140, w * 1.1, 0.8, w * 1.1, 0.3);
            level.sendParticles(ParticleTypes.EXPLOSION, x, y + 0.4, z, 3, w * 0.6, 0.2, w * 0.6, 0);
            level.playSound(null, x, y, z, SoundEvents.WARDEN_EMERGE, SoundSource.HOSTILE, 2.5f, "king".equals(r.style()) ? 0.5f : 0.7f);
            switch (r.style()) {
                case "sky" -> level.playSound(null, x, y, z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.HOSTILE, 1.6f, 1.2f);
                case "grow" -> level.playSound(null, x, y, z, SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.HOSTILE, 2.5f, 0.6f);
                case "erupt" -> {
                    level.sendParticles(ParticleTypes.LAVA, x, y + 0.5, z, 30, w, 0.5, w, 0.3);
                    level.sendParticles(ParticleTypes.FLAME, x, y + 0.5, z, 60, w, 1.0, w, 0.08);
                }
                case "unfold" -> level.sendParticles(ParticleTypes.WHITE_ASH, x, y + 1.5, z, 120, w, 1.5, w, 0.05);
                default -> {
                }
            }
        } else if (age % 3 == 0) {
            level.sendParticles(dust, x, y + 0.2, z, 10, w, 0.2, w, 0.1);
        }
    }
}
