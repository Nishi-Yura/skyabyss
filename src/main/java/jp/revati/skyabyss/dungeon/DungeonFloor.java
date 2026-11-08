package jp.revati.skyabyss.dungeon;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.combat.PartyScale;
import jp.revati.skyabyss.combat.Telegraph;
import jp.revati.skyabyss.combat.TelegraphShape;
import jp.revati.skyabyss.loot.SharedChests;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.tower.TowerTerrain;
import jp.revati.skyabyss.world.continent.ContinentDanger;
import jp.revati.skyabyss.world.continent.NamedFoes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

// ダンジョンの一つの層の動き (docs/design/55 の 2 章)。印の意味は tools/gen/world/build_dungeons.py の頭。
//   部屋     人が WAKE まで近づくと、その部屋の敵 (foe_<部屋>_<n>_<種>) を出す。種は Dungeon.foes、key は扉の鍵持ち、mid は中ボス
//   扉       door_<n>_<条件>_lo/hi の鉄格子。条件 key = 鍵持ちを倒したあと誰かが寄る、mid = 中ボスが倒れる
//   罠       trap_<n>_<種>。踏むと予告の輪のあとに当たる (体力の TRAP_HURT)。TRAP_REST の間は休む
//   宝箱     chest_<n>。流し込んだあと、見本のない箱だけ中身を入れる (人ごとに 20 分。loot/SharedChests)
//   主       主の間の boss に人が寄ると現れる。誰も寄らなくなって BOSS_RESET たつと体力を戻して元の所へ
public final class DungeonFloor {
    static final double WAKE = 17;
    static final double TRAP_REACH = 2.2;
    static final int TRAP_WARN = 18;
    static final int TRAP_REST = 80;
    // 深く潜る: 罠の休みが短い (落石・棘が早く戻る)
    static final int DEEP_TRAP_REST = 50;
    // 罠の日: 二度目の落ち (一度目のあと)
    static final int TRAP_ECHO = 16;
    static final float TRAP_HURT = 0.16f;
    static final double DOOR_REACH = 6;
    static final double BOSS_WAKE = 18;
    static final double BAR_RANGE = 48;
    static final int BOSS_RESET = 200;
    // 少しずつ流す: 一 tick に流す列の数
    static final int POUR_STEP = 240;

    final DungeonRun run;
    final int index;
    final BlockPos origin;
    final TowerTerrain terrain;
    // 部屋 → 敵の印 (印の名前)
    final Map<String, List<String>> roomFoes = new LinkedHashMap<>();
    final Set<String> woke = new HashSet<>();
    final Map<UUID, String> foeMark = new HashMap<>();
    final Set<String> opened = new HashSet<>();
    final Map<String, Long> trapReady = new HashMap<>();
    // 仕掛け (Mechanics): 結晶の溜まり (印 → 0..CHARGE)、満ち引き・溶岩の今の向き
    final Map<String, Integer> charge = new HashMap<>();
    final Map<String, Boolean> phase = new HashMap<>();
    boolean keyTaken;
    // 鍵の日は鍵持ちが二人 (二人とも倒すと鍵が開く)
    int keyNeed = 1;
    int keyKills;
    // 群れの日: 二つに一つの印でもう一体
    int swarmCount;
    UUID mid;
    boolean midDown;
    UUID boss;
    boolean bossDown;
    long bossDownAt;
    long bossAlone;
    ServerBossEvent bar;
    int poured = -1;

    DungeonFloor(DungeonRun run, int index) {
        this.run = run;
        this.index = index;
        this.origin = DungeonWorld.origin(run.dungeon, run.slot, index);
        this.terrain = DungeonWorld.terrain(run.dungeon, index);
        for (String k : terrain.marksStarting("foe_")) {
            String[] p = k.split("_", 4);
            if (p.length == 4) {
                roomFoes.computeIfAbsent(p[1], r -> new ArrayList<>()).add(k);
            }
        }
    }

    BlockPos at(String mark) {
        return origin.offset(terrain.mark(mark));
    }

    Vec3 center(String mark) {
        return Vec3.atBottomCenterOf(at(mark));
    }

    boolean ready() {
        return poured >= terrain.runCount();
    }

    // ---------------------------------------------------------------- 流し込み
    // 一気に流す (人がその層へ入るとき)
    void pourNow(ServerLevel level) {
        if (ready()) {
            return;
        }
        if (poured < 0) {
            clearFoes(level);
            terrain.pour(level, origin);
        } else {
            terrain.pour(level, origin, poured, terrain.runCount());
        }
        poured = terrain.runCount();
        afterPour(level);
    }

    // 少しずつ流す (主の間や次の層を、組が上の層にいるあいだに)
    void pourStep(ServerLevel level) {
        if (ready()) {
            return;
        }
        if (poured < 0) {
            clearFoes(level);
            poured = 0;
        }
        int to = Math.min(terrain.runCount(), poured + POUR_STEP);
        terrain.pour(level, origin, poured, to);
        poured = to;
        if (ready()) {
            afterPour(level);
        }
    }

    // 前の組の残りの敵を片づける (写しの区画は使い回す)
    void clearFoes(ServerLevel level) {
        int[] r = terrain.rect();
        AABB box = new AABB(origin.getX() + r[0] - 8, origin.getY() - 64, origin.getZ() + r[1] - 8, origin.getX() + r[2] + 8, origin.getY() + 96, origin.getZ() + r[3] + 8);
        for (Mob m : level.getEntitiesOfClass(Mob.class, box, m -> m.getTags().contains(Dungeons.FOE_TAG))) {
            m.discard();
        }
    }

    void afterPour(ServerLevel level) {
        MiniDungeons.afterPour(this, level);
        for (String k : terrain.marksStarting("chest_")) {
            BlockPos at = at(k);
            if (!(level.getBlockEntity(at) instanceof ChestBlockEntity chest) || SharedChests.known(level, at)) {
                continue;
            }
            DungeonLoot.fill(chest, run.dungeon, index, level.random);
        }
    }

    // ---------------------------------------------------------------- 毎 tick
    void tick(ServerLevel level, long t, List<ServerPlayer> here) {
        if (!ready()) {
            return;
        }
        if (t % 10 == 0) {
            wakeRooms(level, here);
            doors(level, here);
        }
        traps(level, t, here);
        Mechanics.tick(this, level, t, here);
        if (index == run.dungeon.bossFloor()) {
            bossTick(level, t, here);
        }
        if (t % 20 == 0) {
            bars(level, here);
        }
        // 入口の戻り石の灯と、帰り石の印
        if (t % 40 == 0 && !here.isEmpty()) {
            if (terrain.has("cp")) {
                Vec3 c = center("cp");
                level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, c.x, c.y + 0.4, c.z, 2, 0.15, 0.1, 0.15, 0.005);
            }
            if (terrain.has("leave")) {
                Vec3 c = center("leave");
                level.sendParticles(ParticleTypes.END_ROD, c.x, c.y + 1.2, c.z, 2, 0.2, 0.3, 0.2, 0.01);
            }
        }
    }

    void wakeRooms(ServerLevel level, List<ServerPlayer> here) {
        for (var en : roomFoes.entrySet()) {
            String room = en.getKey();
            if (woke.contains(room) || !terrain.has("room_" + room)) {
                continue;
            }
            Vec3 c = center("room_" + room);
            boolean near = here.stream().anyMatch(p -> !p.isSpectator() && p.isAlive() && p.position().distanceTo(c) < WAKE && Math.abs(p.getY() - c.y) < 10);
            if (!near) {
                continue;
            }
            woke.add(room);
            for (String mark : en.getValue()) {
                spawnFoe(level, mark);
            }
        }
    }

    Mob spawnFoe(ServerLevel level, String mark) {
        String rest = mark.split("_", 4)[3];
        boolean key = rest.endsWith("_key");
        String kind = key ? rest.substring(0, rest.length() - 4) : rest;
        boolean isMid = kind.equals("mid");
        String id = isMid ? run.dungeon.midBoss : run.dungeon.foes.get(kind);
        if (id == null) {
            SkyAbyss.LOGGER.warn("[dungeon] {} unknown foe kind {} at {}", run.dungeon.id, kind, mark);
            return null;
        }
        Mob m = spawnOne(level, id, at(mark), mark, key, isMid);
        if (m == null) {
            return null;
        }
        // 鍵の日: 鍵持ちがもう一人 (少し離れて)
        if (key && run.mood == DungeonMood.KEYS) {
            keyNeed = 2;
            spawnOne(level, id, at(mark).offset(2, 0, 0), mark, true, false);
        }
        // 群れの日: 道中の敵は二つに一つの印でもう一体 (中ボス・鍵持ちは増やさない)
        if (!key && !isMid && run.mood == DungeonMood.SWARM && swarmCount++ % 2 == 0) {
            spawnOne(level, id, at(mark).offset(1, 0, 1), mark, false, false);
        }
        return m;
    }

    Mob spawnOne(ServerLevel level, String id, BlockPos pos, String mark, boolean key, boolean isMid) {
        Mob m = create(level, id, pos);
        if (m == null) {
            return null;
        }
        // 大陸の新しい敵 (ContinentFoe) は岩鯨を 1 とした素の値なので、土地の強さを掛ける (土地ごとの敵は章の釣り合いのまま)
        if (m instanceof jp.revati.skyabyss.entity.foe.ContinentFoe) {
            ContinentDanger.apply(m, run.dungeon.region);
        }
        if (isMid) {
            // 中ボス: 名づきの大物と同じ扱い (技の名前を出す・光る縁取り)。人数で強く
            m.addTag(NamedFoes.TAG);
            m.setCustomName(Component.translatable("entity.skyabyss.dungeon_mid", m.getType().getDescription()));
            m.setCustomNameVisible(true);
            var hp = m.getAttribute(Attributes.MAX_HEALTH);
            if (hp != null) {
                hp.setBaseValue(hp.getBaseValue() * 1.6);
                m.setHealth(m.getMaxHealth());
            }
            mid = m.getUUID();
        }
        if (key) {
            m.addTag(Dungeons.KEY_TAG);
            m.setCustomName(Component.translatable("entity.skyabyss.dungeon_key_bearer", m.getType().getDescription()));
            m.setCustomNameVisible(true);
        }
        PartyScale.apply(m, Math.max(1, run.members.size()));
        Dungeons.deepen(m, run);
        foeMark.put(m.getUUID(), mark);
        return m;
    }

    Mob create(ServerLevel level, String id, BlockPos at) {
        ResourceLocation key = ResourceLocation.tryParse(id);
        EntityType<?> type = key == null ? null : BuiltInRegistries.ENTITY_TYPE.getOptional(key).orElse(null);
        if (type == null) {
            SkyAbyss.LOGGER.warn("[dungeon] missing entity {}", id);
            return null;
        }
        Entity e = type.create(level);
        if (!(e instanceof Mob m)) {
            if (e != null) {
                e.discard();
            }
            return null;
        }
        m.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, level.random.nextFloat() * 360f, 0f);
        m.finalizeSpawn(level, level.getCurrentDifficultyAt(at), MobSpawnType.EVENT, null);
        m.addTag(Dungeons.FOE_TAG);
        m.addTag(run.foeTag());
        m.setPersistenceRequired();
        if (!level.addFreshEntity(m)) {
            return null;
        }
        return m;
    }

    // 敵が倒れた (Dungeons.onDeath から)
    void foeDied(ServerLevel level, LivingEntity e) {
        foeMark.remove(e.getUUID());
        if (e.getTags().contains(Dungeons.KEY_TAG) && !keyTaken && ++keyKills >= keyNeed) {
            keyTaken = true;
            for (ServerPlayer p : run.present()) {
                Notice.gain(p, "message.skyabyss.dungeon.key");
            }
            level.playSound(null, e.blockPosition(), SoundEvents.CHAIN_BREAK, SoundSource.HOSTILE, 1f, 1.2f);
        }
        if (e.getUUID().equals(mid)) {
            midDown = true;
            for (ServerPlayer p : run.present()) {
                Notice.gain(p, "message.skyabyss.dungeon.mid_down", e.getDisplayName());
            }
        }
        if (e.getUUID().equals(boss)) {
            bossDown = true;
            bossDownAt = level.getGameTime();
            // 宝の日: 主の間 (主の立っていた所) に宝箱がもう一つ
            if (run.mood == DungeonMood.TREASURE) {
                treasure(level);
            }
            Dungeons.cleared(run, level);
        }
    }

    void treasure(ServerLevel level) {
        BlockPos at = at("boss");
        level.setBlock(at, Blocks.CHEST.defaultBlockState(), 3);
        if (level.getBlockEntity(at) instanceof ChestBlockEntity chest) {
            DungeonLoot.fill(chest, run.dungeon, 1, level.random);
        }
        level.sendParticles(ParticleTypes.END_ROD, at.getX() + 0.5, at.getY() + 1.0, at.getZ() + 0.5, 24, 0.3, 0.6, 0.3, 0.03);
        level.playSound(null, at, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.4f, 1.0f);
        for (ServerPlayer p : run.present()) {
            if (Dungeons.real(p)) {
                Notice.gain(p, "message.skyabyss.dungeon.mood_chest");
            }
        }
    }

    // ---------------------------------------------------------------- 扉
    void doors(ServerLevel level, List<ServerPlayer> here) {
        for (String lo : terrain.marksStarting("door_")) {
            if (!lo.endsWith("_lo")) {
                continue;
            }
            String door = lo.substring(0, lo.length() - 3);
            if (opened.contains(door)) {
                continue;
            }
            String cond = door.substring(door.lastIndexOf('_') + 1);
            BlockPos a = at(lo);
            BlockPos b = at(door + "_hi");
            Vec3 mid3 = new Vec3((a.getX() + b.getX()) / 2.0 + 0.5, a.getY(), (a.getZ() + b.getZ()) / 2.0 + 0.5);
            boolean near = here.stream().anyMatch(p -> p.isAlive() && p.position().distanceTo(mid3) < DOOR_REACH);
            boolean open = switch (cond) {
                case "key" -> keyTaken && near;
                case "mid" -> midDown;
                default -> cond.startsWith("clear") ? roomCleared(level, cond.substring("clear".length())) : Mechanics.doorOpen(this, door);
            };
            if (open) {
                openDoor(level, door);
            } else if (cond.startsWith("clear") && level.getGameTime() % 20 == 0) {
                // 全滅で開く部屋: 部屋の中の人に、残りの敵の数 (2026-11-08 の声「エリアに何体敵残ってるか出してほしい」)
                String room = cond.substring("clear".length());
                int left = roomLeft(level, room);
                if (left > 0 && terrain.has("room_" + room)) {
                    Vec3 rc = center("room_" + room);
                    for (ServerPlayer p : here) {
                        if (Dungeons.real(p) && p.isAlive() && p.position().distanceTo(rc) < WAKE + 6 && Math.abs(p.getY() - rc.y) < 10) {
                            Notice.bar(p, jp.revati.skyabyss.ModText.battle("message.skyabyss.dungeon.room_left", left));
                        }
                    }
                }
            } else if (near && cond.equals("key") && level.getGameTime() % 60 == 0) {
                for (ServerPlayer p : here) {
                    if (Dungeons.real(p) && p.position().distanceTo(mid3) < DOOR_REACH) {
                        Notice.hint(p, "message.skyabyss.dungeon.locked");
                    }
                }
            }
        }
    }

    // 部屋 room の敵が出て、みな倒れたか (小ダンジョンの扉 door_<n>_clear<部屋>。2026-11-07 の声「前の部屋の敵を全部倒したら」)
    boolean roomCleared(ServerLevel level, String room) {
        if (!woke.contains(room)) {
            return false;
        }
        for (var en : foeMark.entrySet()) {
            String mark = en.getValue();
            if (mark.startsWith("foe_" + room + "_")) {
                Entity e = level.getEntity(en.getKey());
                if (e != null && e.isAlive()) {
                    return false;
                }
                if (e == null && !level.isPositionEntityTicking(at(mark))) {
                    return false;
                }
            }
        }
        return true;
    }

    // 部屋 room に残っている敵の数 (小ダンジョンの知らせ)
    int roomLeft(ServerLevel level, String room) {
        int n = 0;
        for (var en : foeMark.entrySet()) {
            if (en.getValue().startsWith("foe_" + room + "_")) {
                Entity e = level.getEntity(en.getKey());
                if (e != null && e.isAlive()) {
                    n++;
                }
            }
        }
        return n;
    }

    void openDoor(ServerLevel level, String door) {
        opened.add(door);
        BlockPos a = at(door + "_lo");
        BlockPos b = at(door + "_hi");
        for (BlockPos q : BlockPos.betweenClosed(a, b)) {
            if (level.getBlockState(q).is(Blocks.IRON_BARS)) {
                level.setBlock(q, Blocks.AIR.defaultBlockState(), 3);
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.IRON_BARS.defaultBlockState()), q.getX() + 0.5, q.getY() + 0.5, q.getZ() + 0.5, 6, 0.3, 0.3, 0.3, 0.05);
            }
        }
        level.playSound(null, a, SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS, 1.4f, 0.7f);
        for (ServerPlayer p : run.present()) {
            Notice.hint(p, "message.skyabyss.dungeon.door_open");
        }
    }

    // ---------------------------------------------------------------- 罠
    void traps(ServerLevel level, long t, List<ServerPlayer> here) {
        for (String k : terrain.marksStarting("trap_")) {
            if (trapReady.getOrDefault(k, 0L) > t) {
                continue;
            }
            Vec3 c = center(k);
            ServerPlayer hit = null;
            for (ServerPlayer p : here) {
                if (p.isAlive() && !p.isSpectator() && Math.hypot(p.getX() - c.x, p.getZ() - c.z) < TRAP_REACH && Math.abs(p.getY() - c.y) < 2) {
                    hit = p;
                    break;
                }
            }
            if (hit == null) {
                continue;
            }
            boolean traps = run.mood == DungeonMood.TRAPS;
            int rest = run.deep ? DEEP_TRAP_REST : TRAP_REST;
            trapReady.put(k, t + (traps ? rest / 2 : rest));
            String kind = k.substring(k.lastIndexOf('_') + 1);
            spring(level, kind, c);
            // 罠の日: 少し遅れてもう一度落ちる (一度目をよけた所へ)
            if (traps) {
                jp.revati.skyabyss.story.ch1.Tasks.later(TRAP_ECHO, () -> {
                    if (Dungeons.RUNS.containsKey(run.id)) {
                        spring(level, kind, c);
                    }
                });
            }
        }
    }

    void spring(ServerLevel level, String kind, Vec3 c) {
        double r = kind.equals("spikes") ? 2.6 : 3.2;
        var block = switch (kind) {
            case "spikes" -> Blocks.BONE_BLOCK.defaultBlockState();
            case "steam" -> Blocks.MAGMA_BLOCK.defaultBlockState();
            case "spark" -> Blocks.AMETHYST_BLOCK.defaultBlockState();
            case "brine" -> Blocks.PRISMARINE.defaultBlockState();
            default -> Blocks.COBBLED_DEEPSLATE.defaultBlockState();
        };
        Telegraph.in(level, TelegraphShape.circle(c.x, c.z, r)).floor(c.y).warn(TRAP_WARN).danger()
                .startSound(kind.equals("spark") ? SoundEvents.LIGHTNING_BOLT_THUNDER : SoundEvents.GRAVEL_BREAK, 0.8f, kind.equals("spark") ? 1.8f : 0.6f)
                .victims(p -> !p.isSpectator())
                .onImpact(server -> {
                    server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, block), c.x, c.y + (kind.equals("rocks") ? 3.5 : 0.6), c.z, 50, r * 0.4, 0.8, r * 0.4, 0.1);
                    if (kind.equals("steam")) {
                        server.sendParticles(ParticleTypes.CLOUD, c.x, c.y + 0.4, c.z, 30, r * 0.4, 0.6, r * 0.4, 0.05);
                    }
                    if (kind.equals("spark")) {
                        server.sendParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y + 0.8, c.z, 40, r * 0.4, 0.6, r * 0.4, 0.2);
                    }
                    server.playSound(null, c.x, c.y, c.z, kind.equals("rocks") ? SoundEvents.DRIPSTONE_BLOCK_BREAK : SoundEvents.BONE_BLOCK_BREAK, SoundSource.BLOCKS, 1.4f, 0.7f);
                })
                .onHit((server, player) -> player.hurt(level.damageSources().generic(), player.getMaxHealth() * TRAP_HURT))
                .start();
    }

    // ---------------------------------------------------------------- 主
    void bossTick(ServerLevel level, long t, List<ServerPlayer> here) {
        if (bossDown) {
            // 帰りの光
            if (t % 5 == 0 && terrain.has("exit")) {
                Vec3 c = center("exit");
                level.sendParticles(ParticleTypes.END_ROD, c.x, c.y + 0.2, c.z, 6, 0.4, 1.6, 0.4, 0.02);
                level.sendParticles(ParticleTypes.GLOW, c.x, c.y + 1.0, c.z, 3, 0.6, 1.0, 0.6, 0.0);
            }
            return;
        }
        Vec3 c = center("boss");
        boolean near = here.stream().anyMatch(p -> p.isAlive() && !p.isSpectator() && p.position().distanceTo(c) < BOSS_WAKE);
        Entity e = boss == null ? null : level.getEntity(boss);
        if (e == null && near) {
            Mob m = create(level, run.dungeon.boss, at("boss"));
            if (m != null) {
                boss = m.getUUID();
                PartyScale.apply(m, Math.max(1, run.members.size()));
                // 主の硬さ (47 の 2.6): 目安 Lv が高いほど人の傷を割る
                PartyScale.resist(m, run.dungeon.mini ? 1.0 + Math.max(0, run.dungeon.level - 6) * 0.02 : 1.2 + (run.dungeon.level - 10) * 0.035);
                // 入る人のレベルに合わせた硬さ (combat/LevelScale): ダンジョンの目安 Lv より上で来たら、その分だけ受ける傷を割る
                jp.revati.skyabyss.combat.LevelScale.apply(m, run.dungeon.level,
                        here.stream().filter(p -> p.isAlive() && !p.isSpectator()).map(p -> (net.minecraft.server.level.ServerPlayer) p).toList());
                if (run.dungeon.mini) {
                    // 小ダンジョンの主: 名づきの大物と同じ扱い (技の名前・ボスバーの見た目)。潮の番人は床を水に沈める技を使う (ShoreWarden.named)
                    m.addTag(MiniDungeons.BOSS_TAG);
                    m.addTag(NamedFoes.TAG);
                }
                Dungeons.deepen(m, run);
                if (m instanceof jp.revati.skyabyss.dungeon.mob.DungeonBoss b) {
                    List<Vec3> holes = new ArrayList<>();
                    for (String k : terrain.marksStarting("burrow_")) {
                        holes.add(center(k));
                    }
                    b.setHoles(holes);
                }
                m.setPersistenceRequired();
                bar = new ServerBossEvent(m.getDisplayName(), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_10);
                for (ServerPlayer p : here) {
                    if (Dungeons.real(p)) {
                        Notice.alert(p, "message.skyabyss.dungeon.boss_appear", m.getDisplayName());
                    }
                }
                level.playSound(null, at("boss"), SoundEvents.WARDEN_EMERGE, SoundSource.HOSTILE, 1.2f, 0.8f);
            }
            return;
        }
        if (e instanceof LivingEntity le && le.isAlive()) {
            boolean anyone = here.stream().anyMatch(p -> p.isAlive() && p.distanceTo(le) < BAR_RANGE);
            bossAlone = anyone ? 0 : bossAlone + 1;
            if (bossAlone >= BOSS_RESET) {
                // 誰もいなくなった: 体力を戻して元の所へ (やり直しは手前の戻り石から)
                le.setHealth(le.getMaxHealth());
                Vec3 home = center("boss");
                le.teleportTo(home.x, home.y, home.z);
                if (le instanceof Mob mob) {
                    mob.setTarget(null);
                }
                bossAlone = 0;
            }
        }
    }

    void bars(ServerLevel level, List<ServerPlayer> here) {
        UUID id = index == run.dungeon.bossFloor() ? boss : mid;
        Entity e = id == null ? null : level.getEntity(id);
        if (!(e instanceof LivingEntity le) || !le.isAlive()) {
            if (bar != null) {
                bar.removeAllPlayers();
                if (e == null || !e.isAlive()) {
                    bar = null;
                }
            }
            return;
        }
        if (bar == null) {
            bar = new ServerBossEvent(le.getDisplayName(), index == run.dungeon.bossFloor() ? BossEvent.BossBarColor.RED : BossEvent.BossBarColor.YELLOW, BossEvent.BossBarOverlay.NOTCHED_10);
        }
        bar.setProgress(Math.max(0f, le.getHealth() / le.getMaxHealth()));
        for (ServerPlayer p : here) {
            boolean in = p.distanceTo(le) < BAR_RANGE;
            if (in && !bar.getPlayers().contains(p)) {
                bar.addPlayer(p);
            } else if (!in && bar.getPlayers().contains(p)) {
                bar.removePlayer(p);
            }
        }
    }

    void close() {
        if (bar != null) {
            bar.removeAllPlayers();
            bar = null;
        }
    }
}
