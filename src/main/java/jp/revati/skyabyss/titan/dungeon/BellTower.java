package jp.revati.skyabyss.titan.dungeon;

import static jp.revati.skyabyss.titan.dungeon.TitanBackLayout.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.titan.TitanTravel;
import jp.revati.skyabyss.titan.group.BackGroupData;
import jp.revati.skyabyss.titan.group.FirstRun;
import jp.revati.skyabyss.titan.group.GroupGates;
import jp.revati.skyabyss.titan.group.Groups;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

// 数え歌の鐘楼 (21 の 3.3。西の翼の第二の休み場 W2 の北、縦の穴): 五つの鐘と、崩れ橋 (WC) へ行く通路の戸 (BELL_GATE)。
// 鐘は積み石の高さが番号 (1〜5。地上の歌石と同じ数え方)。1 → 2 → 3 → 4 → 5 の順に叩くと、戸の封印がそのグループにだけほどける。
// 間違えると鐘が低く鳴り、骨鳥が二羽降りる (ピースフルでは鳴るだけ)。初回に歌の間を見ていなくても、立て札の一行 (欠けた節) で解ける。
// 初回だけ働く: 周回のグループには戸が最初から開き (FirstRun の既知の道)、鐘は鳴るだけで何も起きない
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class BellTower {
    public static final int COUNT = 5;
    public static final String FIRST_ROOM = "whale_bell";
    public static final String FLAG = "bell";
    // 鐘の台 (台の高さ = 番号)。塔の床 (y FLOOR) の四隅と奥
    public static final List<int[]> PILLARS = List.of(
            new int[]{-50, -125}, new int[]{-50, -115}, new int[]{-36, -125}, new int[]{-36, -115}, new int[]{-43, -127});
    // 入口の岩棚 (W2 と同じ高さ) と、そこから北の塔の床へ下りる階段
    public static final int LANDING_Y = FLOOR + 9;
    private static final int STAIR_X1 = -44;
    private static final int STAIR_X2 = -40;

    private static final Map<UUID, Integer> PROGRESS = new HashMap<>();

    private BellTower() {
    }

    public static BlockPos bellPos(int n) {
        int[] p = PILLARS.get(n - 1);
        return new BlockPos(p[0], FLOOR + 1 + n, p[1]);
    }

    // 鐘の番号 (1〜5。鐘でなければ 0)
    public static int numberAt(BlockPos pos) {
        for (int n = 1; n <= COUNT; n++) {
            if (bellPos(n).equals(pos)) {
                return n;
            }
        }
        return 0;
    }

    static void build(BuildKit kit) {
        kit.room(BELL_PASS);
        kit.room(BELL);
        kit.ribs(BELL, 6, true);
        kit.pilasters(BELL, 6);
        kit.wallLamps(BELL, 5, FLOOR + 6);
        kit.wallLamps(BELL, 5, FLOOR + 16);
        // 入口の岩棚 (W2 と同じ高さ): 塔の南の端
        for (int x = -46; x <= -38; x++) {
            for (int z = BELL.z2() - 5; z <= BELL.z2(); z++) {
                kit.set(x, LANDING_Y, z, BuildKit.POLISHED);
                // 岩棚の下は石で埋める (宙に浮かない)
                kit.fill(x, FLOOR + 1, z, x, LANDING_Y - 1, z, BuildKit.BRICKS);
            }
        }
        // 岩棚から北の塔の床へ下りる階段 (登る向き = 南 = 背の高い側)。両脇は石の土手と低い壁
        BlockState step = Blocks.DEEPSLATE_BRICK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.SOUTH);
        int zStart = BELL.z2() - 6;
        for (int i = 0; ; i++) {
            int y = LANDING_Y - 1 - i;
            int z = zStart - i;
            if (y <= FLOOR) {
                break;
            }
            for (int x = STAIR_X1 - 1; x <= STAIR_X2 + 1; x++) {
                kit.fill(x, FLOOR + 1, z, x, y - 1, z, BuildKit.BRICKS);
                boolean side = x == STAIR_X1 - 1 || x == STAIR_X2 + 1;
                if (side) {
                    kit.set(x, y, z, BuildKit.BRICKS);
                    kit.set(x, y + 1, z, Blocks.DEEPSLATE_BRICK_WALL.defaultBlockState());
                } else {
                    kit.set(x, y, z, step);
                    kit.fill(x, y + 1, z, x, y + 6, z, BuildKit.AIR);
                }
            }
        }
        // 鐘と台
        for (int n = 1; n <= COUNT; n++) {
            int[] p = PILLARS.get(n - 1);
            for (int y = FLOOR + 1; y <= FLOOR + n; y++) {
                kit.set(p[0], y, p[1], y == FLOOR + n ? BuildKit.CHISELED : BuildKit.POLISHED);
            }
            kit.set(bellPos(n), Blocks.BELL.defaultBlockState());
            kit.lantern(p[0] + 1, FLOOR + 1, p[1]);
        }
        // 欠けた節の立て札 (岩棚の上)
        kit.standingSign(new BlockPos(-45, LANDING_Y + 1, BELL.z2() - 1), "sign.skyabyss.bell_hint");
        // 塔の天井から垂れる鎖と灯り
        for (int x : new int[]{-48, -43, -38}) {
            for (int z : new int[]{-123, -117}) {
                kit.hangingLantern(x, z, BELL.ceilingY(), 6);
            }
        }
        kit.moss(BELL, 0.03f);
        register();
    }

    // 初回だけ働く部屋として登録する (周回のグループには戸が最初から開く)。建てたあと・起動のあとに呼ぶ
    public static void register() {
        net.minecraft.world.phys.AABB box = BELL.aabb().minmax(BELL_PASS.aabb()).minmax(W2_CONN2.aabb());
        FirstRun.register(FirstRun.Room.firstRun(FIRST_ROOM, TitanTravel.TITAN_BACK, box).doors(BELL_GATE));
    }

    // ---------------------------------------------------------------- 叩く
    public enum Result {
        ADVANCE, RESTART, WRONG, DONE
    }

    // 進み progress の人が番号 n の鐘を叩いたときの結果 (純粋)。5 まで進めば DONE
    public static Result step(int progress, int n) {
        if (n == progress + 1) {
            return n == COUNT ? Result.DONE : Result.ADVANCE;
        }
        return n == 1 ? Result.RESTART : Result.WRONG;
    }

    @SubscribeEvent
    static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !(event.getLevel() instanceof ServerLevel level)
                || level.dimension() != TitanTravel.TITAN_BACK || event.getHand() != net.minecraft.world.InteractionHand.MAIN_HAND) {
            return;
        }
        int n = numberAt(event.getPos());
        if (n == 0) {
            return;
        }
        event.setCanceled(true);
        ring(level, player, n);
    }

    // 番号 n の鐘を叩く (自己テストからも)。戻りは結果
    public static Result ring(ServerLevel level, ServerPlayer player, int n) {
        UUID group = Groups.keyOf(player);
        BackGroupData.Record record = BackGroupData.get(level).of(group);
        BlockPos at = bellPos(n);
        float pitch = 0.62f + 0.1f * n;
        Groups.sound(level, group, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 2.2f, pitch);
        if (record.flag(FLAG) || !FirstRun.isFirstRun(level, group)) {
            // もう解いた・周回: 鳴るだけ
            return Result.DONE;
        }
        int progress = PROGRESS.getOrDefault(group, 0);
        Result result = step(progress, n);
        switch (result) {
            case ADVANCE -> {
                PROGRESS.put(group, n);
                Notice.narrate(player, "message.skyabyss.bell_ok");
            }
            case RESTART -> {
                PROGRESS.put(group, 1);
                Notice.narrate(player, "message.skyabyss.bell_ok");
            }
            case WRONG -> {
                PROGRESS.put(group, 0);
                Groups.sound(level, group, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, SoundEvents.BELL_RESONATE, SoundSource.BLOCKS, 2f, 0.5f);
                Notice.narrate(player, "message.skyabyss.bell_wrong");
                if (level.getDifficulty() != Difficulty.PEACEFUL) {
                    BackAmbientMobs.spawn(level, BELL, group, 2);
                }
            }
            case DONE -> {
                PROGRESS.remove(group);
                record.setFlag(FLAG, true);
                GroupGates.open(level, group, BELL_GATE);
                for (ServerPlayer p : Groups.online(level, group)) {
                    if (BELL.aabb().inflate(16).contains(p.position())) {
                        Notice.narrate(p, "message.skyabyss.bell_done");
                    }
                }
                Groups.sound(level, group, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 2f, 0.8f);
            }
        }
        return result;
    }

    // 鐘が鳴りやんだ (解いた) グループにも、記録が消える (リセット) ときに途中の進みを捨てる
    public static void forget(UUID group) {
        PROGRESS.remove(group);
    }

    public static void clearAll() {
        PROGRESS.clear();
    }
}
