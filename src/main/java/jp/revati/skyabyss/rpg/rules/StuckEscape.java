package jp.revati.skyabyss.rpg.rules;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.notice.Notice;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

// 詰んだときの抜け道 (2026-11-08 の声「詰んだ時に回避できる方法作っておいて」)。/stuck (だれでも使える)。
// その場で WAIT tick 動かずにいると、いちばん近い安全な所へ移す:
//   地上    : 倒れたときと同じ休み場 (行ったことのある町の井戸の脇・都・野営地。島を出る前は島の野営地と祠)
//   巨神の背: 順路の途中なら、いまの段の部屋の入口の休み場。順路の外なら上の空いた所
//   地下のダンジョン: いまの層の入口
//   ほか    : 埋まっていれば上の空いた所
// 戦いの最中 (直前 COMBAT tick に傷を受けた) は使えない。使ったあと COOLDOWN tick は使えない (抜け道を移動の近道にしない)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class StuckEscape {
    public static final int WAIT = 60;
    public static final int COMBAT = 100;
    public static final int COOLDOWN = 20 * 60;

    private record Pending(Vec3 at, long until) {
    }

    private static final Map<UUID, Pending> PENDING = new HashMap<>();
    private static final Map<UUID, Long> LAST = new HashMap<>();

    private StuckEscape() {
    }

    @SubscribeEvent
    static void onCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("stuck").executes(c -> {
            ServerPlayer p = c.getSource().getPlayerOrException();
            return ask(p) ? 1 : 0;
        }));
    }

    // 頼む: 数え下ろしを始めたか
    public static boolean ask(ServerPlayer p) {
        long now = p.serverLevel().getGameTime();
        Long last = LAST.get(p.getUUID());
        if (last != null && now - last < COOLDOWN) {
            Notice.bar(p, ModText.deny("message.skyabyss.stuck.cooldown", (COOLDOWN - (now - last) + 19) / 20));
            return false;
        }
        if (p.getLastHurtByMobTimestamp() > 0 && p.tickCount - p.getLastHurtByMobTimestamp() < COMBAT) {
            Notice.bar(p, ModText.deny("message.skyabyss.stuck.combat"));
            return false;
        }
        PENDING.put(p.getUUID(), new Pending(p.position(), now + WAIT));
        Notice.hint(p, "message.skyabyss.stuck.wait", WAIT / 20);
        return true;
    }

    @SubscribeEvent
    static void onTick(ServerTickEvent.Post event) {
        if (PENDING.isEmpty()) {
            return;
        }
        var it = PENDING.entrySet().iterator();
        while (it.hasNext()) {
            var e = it.next();
            ServerPlayer p = event.getServer().getPlayerList().getPlayer(e.getKey());
            if (p == null || !p.isAlive()) {
                it.remove();
                continue;
            }
            if (p.position().distanceToSqr(e.getValue().at()) > 1.0) {
                Notice.bar(p, ModText.deny("message.skyabyss.stuck.moved"));
                it.remove();
                continue;
            }
            if (p.serverLevel().getGameTime() >= e.getValue().until()) {
                it.remove();
                LAST.put(p.getUUID(), p.serverLevel().getGameTime());
                boolean moved = escape(p);
                Notice.hint(p, moved ? "message.skyabyss.stuck.done" : "message.skyabyss.stuck.none");
            }
        }
    }

    // 移す。移したら true
    public static boolean escape(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        BlockPos from = p.blockPosition();
        if (level.dimension() == Level.OVERWORLD) {
            List<BlockPos> rests = new java.util.ArrayList<>(jp.revati.skyabyss.world.continent.ContinentOutskirts.ferried(p)
                    ? jp.revati.skyabyss.world.continent.ContinentRespawn.rests(level, p) : jp.revati.skyabyss.world.continent.ContinentRespawn.islandRests(level));
            BlockPos at = jp.revati.skyabyss.world.continent.ContinentRespawn.nearest(rests, from);
            if (at != null) {
                return move(p, level, Vec3.atBottomCenterOf(at));
            }
        } else if (jp.revati.skyabyss.progress.Titans.titanOfBack(level.dimension()) != null) {
            if (jp.revati.skyabyss.trial.TrialRoute.rejoinAfterDeath(level, p)) {
                return true;
            }
        } else if (level.dimension() == jp.revati.skyabyss.dungeon.DungeonWorld.LEVEL) {
            jp.revati.skyabyss.dungeon.DungeonRun run = jp.revati.skyabyss.dungeon.Dungeons.runOf(p);
            if (run != null) {
                Vec3 e = run.markCenter(Math.max(0, run.floorOf(p)), "entry");
                return move(p, level, e);
            }
        }
        if (jp.revati.skyabyss.cinematic.Cinematics.unstick(p)) {
            return true;
        }
        SkyAbyss.LOGGER.info("{} asked to get unstuck at {} in {}, but there was nowhere to move", p.getGameProfile().getName(), from, level.dimension().location());
        return false;
    }

    private static boolean move(ServerPlayer p, ServerLevel level, Vec3 to) {
        p.teleportTo(level, to.x, to.y, to.z, p.getYRot(), p.getXRot());
        p.resetFallDistance();
        p.setDeltaMovement(Vec3.ZERO);
        level.playSound(null, to.x, to.y, to.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.6f, 1.2f);
        SkyAbyss.LOGGER.info("{} got unstuck: moved to {}", p.getGameProfile().getName(), BlockPos.containing(to));
        return true;
    }
}
