package jp.revati.skyabyss.endgame;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.combat.BreakGauge;
import jp.revati.skyabyss.combat.Telegraph;
import jp.revati.skyabyss.combat.TelegraphShape;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.world.continent.ContinentMap;
import jp.revati.skyabyss.world.continent.RegionSpawner;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

// 大陸の大事件の戦いの流れ (docs/design/54 の 3.3.1。2026-11-07 の声「大事件ででるモブが単調なのと HP が多いだけの敵になっている」)。
//   前触れ   誰かが近づくと、まず先触れ (その土地の敵の大きな精鋭) と、その土地の敵の群れが来る。みな倒すか HERALD_LAST で大いなるものが現れる
//   場の技   大いなるものは HAZARD_EVERY ごとに、土地ごとの場の技を出す (岩鯨 = 落石・潮殻 = 潮の波・雷翼 = 落雷・余燼 = 噴き上げ)。予兆つき
//   封じ     体力が SEAL_AT (2/3・1/3) を切ると守りを張る (受ける傷 ×SEALED)。まわりに封じの輪が三つ出て、輪の中に立つと満ちる
//            (一人 1 秒に FILL。二人なら倍)。三つ満ちると守りが割れ、膝をつく (崩れ。BreakGauge.solved)。SEAL_LAST で満ちなければ、守りは割れるが荒ぶる
//            封じのあいだは、その土地の敵が ADDS_SEAL ごとに 2 体来る
//   荒ぶり   体力が ENRAGE_AT を切ると (または封じに間に合わないと) 足が速く、場の技が速くなる
public final class WorldEventFight {
    public static final int HERALD_LAST = 90;
    public static final int HAZARD_EVERY = 13;
    public static final int HAZARD_ENRAGED = 8;
    public static final float[] SEAL_AT = {0.66f, 0.33f};
    public static final float SEALED = 0.1f;
    public static final int SEAL_LAST = 45;
    public static final double SEAL_RADIUS = 3.0;
    public static final float FILL = 0.25f;
    public static final int ADDS_SEAL = 10;
    public static final float ENRAGE_AT = 0.25f;
    public static final int KNEEL_TICKS = 160;
    static final ResourceLocation HERALD_HEALTH = ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "world_event_herald_health");
    static final ResourceLocation HERALD_SCALE = ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "world_event_herald_scale");
    static final ResourceLocation RAGE_SPEED = ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "world_event_rage_speed");

    // 一つの封じの輪
    public static final class Seal {
        final Vec3 pos;
        float fill;

        Seal(Vec3 pos) {
            this.pos = pos;
        }

        public boolean full() {
            return fill >= 1f;
        }

        public Vec3 pos() {
            return pos;
        }
    }

    private WorldEventFight() {
    }

    // ---------------------------------------------------------------- 前触れ
    // 先触れと群れを出す。出せた数
    public static int herald(ServerLevel level, WorldEvents.Event e, ServerPlayer near) {
        e.stage = WorldEvents.Event.HERALD;
        e.heraldUntil = level.getGameTime() + HERALD_LAST * 20L;
        e.herald.clear();
        int people = nearby(level, e.spot).size();
        int count = Math.min(9, 4 + people);
        ContinentMap map = ContinentMap.get();
        for (int i = 0, tries = 0; i <= count && tries < 40; tries++) {
            double a = level.random.nextDouble() * Math.PI * 2;
            double r = 5 + level.random.nextDouble() * 9;
            int x = (int) Math.floor(e.spot.getX() + Math.cos(a) * r);
            int z = (int) Math.floor(e.spot.getZ() + Math.sin(a) * r);
            level.getChunk(x >> 4, z >> 4);
            BlockPos g = Circuits.ground(level, x, z);
            if (g == null) {
                continue;
            }
            RegionSpawner.Entry en = RegionSpawner.pick(e.region, map.biome(x, z), RegionSpawner.night(level), level.random.nextInt(1000));
            Mob m = en == null ? null : RegionSpawner.spawn(level, en.entity(), g, near);
            if (m == null) {
                continue;
            }
            if (i == 0) {
                // 先触れ: 大きく固く、光る。名前つき
                WorldEvents.modifier(m.getAttribute(Attributes.MAX_HEALTH), HERALD_HEALTH, 3.0);
                WorldEvents.modifier(m.getAttribute(Attributes.SCALE), HERALD_SCALE, 0.35);
                m.setHealth(m.getMaxHealth());
                m.setCustomName(Component.translatable("entity.skyabyss.world_event.herald", m.getType().getDescription()));
                m.setCustomNameVisible(true);
                m.addEffect(new MobEffectInstance(MobEffects.GLOWING, 20 * HERALD_LAST, 0, false, false));
            }
            m.addTag(WorldEvents.ADD_TAG);
            m.setTarget(near);
            e.herald.add(m.getUUID());
            i++;
        }
        for (ServerPlayer p : nearby(level, e.spot)) {
            Notice.alert(p, "message.skyabyss.world_event.herald");
            p.playNotifySound(SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 0.8f, 1.2f);
        }
        return e.herald.size();
    }

    // 前触れの残り (生きている数)
    public static int heraldLeft(ServerLevel level, WorldEvents.Event e) {
        int left = 0;
        for (UUID id : e.herald) {
            if (level.getEntity(id) instanceof LivingEntity le && le.isAlive()) {
                left++;
            }
        }
        return left;
    }

    // ---------------------------------------------------------------- 大いなるものとの戦い (毎秒)
    public static void step(ServerLevel level, WorldEvents.Event e, Mob boss, long now) {
        float hp = boss.getHealth() / Math.max(1f, boss.getMaxHealth());
        // 封じを始める
        if (e.seals.isEmpty() && e.sealsDone < SEAL_AT.length && hp < SEAL_AT[e.sealsDone]) {
            beginSeal(level, e, boss, now);
        }
        if (!e.seals.isEmpty()) {
            stepSeal(level, e, boss, now);
        }
        // 荒ぶり
        if (!e.enraged && hp < ENRAGE_AT) {
            enrage(level, e, boss);
        }
        // 場の技 (崩れている間は出さない)
        if (now >= e.nextHazard && !BreakGauge.broken(boss)) {
            e.nextHazard = now + (e.enraged ? HAZARD_ENRAGED : HAZARD_EVERY) * 20L;
            hazard(level, e, boss);
        }
        e.bar.setName(barName(e, boss));
    }

    static Component barName(WorldEvents.Event e, Mob boss) {
        Component name = boss.getDisplayName();
        if (!e.seals.isEmpty()) {
            int full = 0;
            for (Seal s : e.seals) {
                full += s.full() ? 1 : 0;
            }
            return Component.translatable("gui.skyabyss.world_event.bar_seal", name, full, e.seals.size());
        }
        if (e.enraged) {
            return Component.translatable("gui.skyabyss.world_event.bar_rage", name);
        }
        return name;
    }

    static void beginSeal(ServerLevel level, WorldEvents.Event e, Mob boss, long now) {
        e.sealUntil = now + SEAL_LAST * 20L;
        e.nextSealAdds = now + ADDS_SEAL * 20L;
        double turn = level.random.nextDouble() * Math.PI * 2;
        for (int i = 0; i < 3; i++) {
            double a = turn + i * Math.PI * 2 / 3;
            double r = 11 + level.random.nextDouble() * 4;
            int x = (int) Math.floor(e.spot.getX() + Math.cos(a) * r);
            int z = (int) Math.floor(e.spot.getZ() + Math.sin(a) * r);
            level.getChunk(x >> 4, z >> 4);
            BlockPos g = Circuits.ground(level, x, z);
            Vec3 at = g == null ? new Vec3(x + 0.5, boss.getY(), z + 0.5) : new Vec3(g.getX() + 0.5, g.getY(), g.getZ() + 0.5);
            e.seals.add(new Seal(at));
        }
        level.playSound(null, boss.getX(), boss.getY(), boss.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.HOSTILE, 2f, 0.6f);
        level.sendParticles(ParticleTypes.END_ROD, boss.getX(), boss.getY() + boss.getBbHeight() * 0.6, boss.getZ(), 80, 2.0, 1.5, 2.0, 0.05);
        for (ServerPlayer p : nearby(level, e.spot)) {
            Notice.alert(p, "message.skyabyss.world_event.seal", boss.getDisplayName());
        }
    }

    static void stepSeal(ServerLevel level, WorldEvents.Event e, Mob boss, long now) {
        List<ServerPlayer> people = nearby(level, e.spot);
        boolean all = true;
        for (Seal s : e.seals) {
            if (!s.full()) {
                for (ServerPlayer p : people) {
                    if (p.isAlive() && !p.isSpectator() && Math.hypot(p.getX() - s.pos.x, p.getZ() - s.pos.z) <= SEAL_RADIUS && Math.abs(p.getY() - s.pos.y) < 4) {
                        s.fill = Math.min(1f, s.fill + FILL);
                        if (s.full()) {
                            level.playSound(null, s.pos.x, s.pos.y, s.pos.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 2f, 1.2f);
                            level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, s.pos.x, s.pos.y + 1, s.pos.z, 40, 1.2, 1.0, 1.2, 0.2);
                        } else {
                            level.playSound(null, s.pos.x, s.pos.y, s.pos.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.2f, 0.8f + s.fill);
                        }
                    }
                }
            }
            all &= s.full();
            // 輪 (満ちたものは見せない)。満ちた分だけ光の柱が高い
            if (!s.full()) {
                Telegraph.in(level, TelegraphShape.circle(s.pos.x, s.pos.z, SEAL_RADIUS)).floor(s.pos.y).warn(0).active(22).safe().start();
                double h = 1 + 6 * s.fill;
                for (double y = 0; y < h; y += 0.5) {
                    level.sendParticles(ParticleTypes.END_ROD, s.pos.x, s.pos.y + y, s.pos.z, 1, 0.05, 0, 0.05, 0);
                }
            }
        }
        // 守りの見た目: 大いなるものから輪へ光の筋
        for (Seal s : e.seals) {
            if (!s.full()) {
                Vec3 from = boss.position().add(0, boss.getBbHeight() * 0.6, 0);
                Vec3 d = s.pos.add(0, 1, 0).subtract(from);
                for (int i = 1; i < 12; i++) {
                    Vec3 q = from.add(d.scale(i / 12.0));
                    level.sendParticles(ParticleTypes.ENCHANT, q.x, q.y, q.z, 1, 0, 0, 0, 0);
                }
            }
        }
        if (now >= e.nextSealAdds) {
            e.nextSealAdds = now + ADDS_SEAL * 20L;
            WorldEvents.adds(level, e, boss);
        }
        if (all) {
            endSeal(level, e, boss, true);
        } else if (now >= e.sealUntil) {
            endSeal(level, e, boss, false);
        }
    }

    static void endSeal(ServerLevel level, WorldEvents.Event e, Mob boss, boolean solved) {
        e.seals.clear();
        e.sealsDone++;
        level.playSound(null, boss.getX(), boss.getY(), boss.getZ(), SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 2.5f, 0.5f);
        level.sendParticles(ParticleTypes.FLASH, boss.getX(), boss.getY() + 1, boss.getZ(), 1, 0, 0, 0, 0);
        if (solved) {
            // 膝をつく: 隙を開け、崩しのゲージを満たしきる
            BreakGauge.solved(boss, KNEEL_TICKS);
            if (!BreakGauge.broken(boss)) {
                BreakGauge.add(boss, BreakGauge.size(boss, BreakGauge.breaks(boss)));
            }
            for (ServerPlayer p : nearby(level, e.spot)) {
                Notice.alert(p, "message.skyabyss.world_event.seal_broken", boss.getDisplayName());
            }
        } else {
            for (ServerPlayer p : nearby(level, e.spot)) {
                Notice.warn(p, "message.skyabyss.world_event.seal_failed", boss.getDisplayName());
            }
            enrage(level, e, boss);
        }
    }

    public static boolean sealed(WorldEvents.Event e) {
        return !e.seals.isEmpty();
    }

    static void enrage(ServerLevel level, WorldEvents.Event e, Mob boss) {
        if (e.enraged) {
            return;
        }
        e.enraged = true;
        WorldEvents.modifier(boss.getAttribute(Attributes.MOVEMENT_SPEED), RAGE_SPEED, 0.3);
        level.playSound(null, boss.getX(), boss.getY(), boss.getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 2.5f, 0.5f);
        level.sendParticles(ParticleTypes.ANGRY_VILLAGER, boss.getX(), boss.getY() + boss.getBbHeight(), boss.getZ(), 12, 1.2, 0.5, 1.2, 0);
        for (ServerPlayer p : nearby(level, e.spot)) {
            Notice.warn(p, "message.skyabyss.world_event.rage", boss.getDisplayName());
        }
    }

    // ---------------------------------------------------------------- 場の技 (土地ごと)
    static void hazard(ServerLevel level, WorldEvents.Event e, Mob boss) {
        List<ServerPlayer> people = nearby(level, e.spot);
        if (people.isEmpty()) {
            return;
        }
        float dmg = (float) Math.max(6, boss.getAttributeValue(Attributes.ATTACK_DAMAGE) * 0.8);
        DamageSource src = level.damageSources().mobAttack(boss);
        double y = boss.getY();
        switch (e.region) {
            case ContinentMap.TIDE -> {
                // 潮の波: 片側から、間の抜けた三本の帯が順に押し寄せる
                ServerPlayer t = people.get(level.random.nextInt(people.size()));
                double ang = level.random.nextDouble() * Math.PI * 2;
                Vec3 dir = new Vec3(Math.cos(ang), 0, Math.sin(ang));
                Vec3 side = new Vec3(-dir.z, 0, dir.x);
                for (int k = 0; k < 3; k++) {
                    Vec3 c = t.position().add(dir.scale(-12 + k * 6));
                    double gap = (level.random.nextDouble() - 0.5) * 14;
                    for (int half = -1; half <= 1; half += 2) {
                        // 帯を二つに分け、間に通れる隙間 (幅 3) を残す
                        Vec3 a0 = c.add(side.scale(gap + half * 1.5));
                        double len = 14;
                        double angle = Telegraph.angleOf(side.scale(half));
                        Telegraph.in(level, TelegraphShape.line(a0.x, a0.z, angle, len, 1.4))
                                .floor(y).warn(30 + k * 12).danger().owner(boss).target(t)
                                .onImpact(server -> server.sendParticles(ParticleTypes.SPLASH, a0.x, y + 0.5, a0.z, 30, 3, 0.3, 3, 0.2))
                                .onHit((server, player) -> {
                                    player.hurt(src, dmg);
                                    player.knockback(1.0, -dir.x, -dir.z);
                                })
                                .start();
                    }
                }
            }
            case ContinentMap.STORM -> {
                // 落雷: 人ごとに足もとの輪、少し遅れてもう一度 (動き続けると避けられる)
                for (ServerPlayer p : people) {
                    for (int k = 0; k < 2; k++) {
                        Vec3 at = p.position().add((level.random.nextDouble() - 0.5) * 3, 0, (level.random.nextDouble() - 0.5) * 3);
                        Telegraph.in(level, TelegraphShape.circle(at.x, at.z, 2.4))
                                .floor(y).warn(26 + k * 16).danger().owner(boss).target(p)
                                .onImpact(server -> {
                                    net.minecraft.world.entity.LightningBolt bolt = net.minecraft.world.entity.EntityType.LIGHTNING_BOLT.create(server);
                                    if (bolt != null) {
                                        bolt.moveTo(at.x, y, at.z);
                                        bolt.setVisualOnly(true);
                                        server.addFreshEntity(bolt);
                                    }
                                })
                                .onHit((server, player) -> player.hurt(src, dmg))
                                .start();
                    }
                }
            }
            case ContinentMap.EMBER -> {
                // 噴き上げ: 大いなるもののまわりの輪 (内は安全) と、人ごとの火の柱
                Telegraph.in(level, TelegraphShape.ring(boss.getX(), boss.getZ(), 4, 12))
                        .floor(y).warn(34).danger().owner(boss)
                        .onImpact(server -> server.sendParticles(ParticleTypes.LAVA, boss.getX(), y + 0.5, boss.getZ(), 60, 6, 0.4, 6, 0.1))
                        .onHit((server, player) -> {
                            player.hurt(src, dmg);
                            player.igniteForSeconds(3);
                        })
                        .start();
                for (ServerPlayer p : people) {
                    Vec3 at = p.position();
                    Telegraph.in(level, TelegraphShape.circle(at.x, at.z, 2.2))
                            .floor(y).warn(46).danger().owner(boss).target(p)
                            .onImpact(server -> server.sendParticles(ParticleTypes.FLAME, at.x, y + 1, at.z, 40, 0.6, 1.5, 0.6, 0.05))
                            .onHit((server, player) -> {
                                player.hurt(src, dmg * 0.8f);
                                player.igniteForSeconds(3);
                            })
                            .start();
                }
            }
            default -> {
                // 岩鯨: 空から岩が落ちる。人ごとに二つ、ほかに三つ
                List<Vec3> spots = new ArrayList<>();
                for (ServerPlayer p : people) {
                    spots.add(p.position());
                    spots.add(p.position().add((level.random.nextDouble() - 0.5) * 8, 0, (level.random.nextDouble() - 0.5) * 8));
                }
                for (int i = 0; i < 3; i++) {
                    double a = level.random.nextDouble() * Math.PI * 2;
                    double r = 4 + level.random.nextDouble() * 10;
                    spots.add(new Vec3(e.spot.getX() + 0.5 + Math.cos(a) * r, y, e.spot.getZ() + 0.5 + Math.sin(a) * r));
                }
                int k = 0;
                for (Vec3 s : spots) {
                    int warn = 30 + (k++ % 3) * 8;
                    Telegraph.in(level, TelegraphShape.circle(s.x, s.z, 2.6))
                            .floor(y).warn(warn).danger().owner(boss)
                            .onImpact(server -> {
                                server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE.defaultBlockState()), s.x, y + 4, s.z, 40, 1.0, 1.5, 1.0, 0.1);
                                server.playSound(null, s.x, y, s.z, SoundEvents.DRIPSTONE_BLOCK_BREAK, SoundSource.HOSTILE, 1.4f, 0.6f);
                            })
                            .onHit((server, player) -> player.hurt(src, dmg))
                            .start();
                }
            }
        }
        level.playSound(null, boss.getX(), boss.getY(), boss.getZ(), SoundEvents.EVOKER_PREPARE_ATTACK, SoundSource.HOSTILE, 1.6f, 0.6f);
    }

    static List<ServerPlayer> nearby(ServerLevel level, BlockPos spot) {
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer p : level.players()) {
            if (!p.isSpectator() && p.distanceToSqr(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5) < WorldEvents.BAR_RANGE * WorldEvents.BAR_RANGE) {
                out.add(p);
            }
        }
        out.addAll(WorldEvents.TEST.values().stream().filter(p -> !out.contains(p) && p.level() == level).toList());
        return out;
    }
}
