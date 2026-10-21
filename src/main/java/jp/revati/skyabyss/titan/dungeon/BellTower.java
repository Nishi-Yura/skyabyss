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
// 鐘は鳴るだけで何も起きない (周回も初回も同じ。戸は鐘を鳴らしたあとに開く)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class BellTower {
    public static final int COUNT = 5;
    public static final String FLAG = "bell";
    // 鐘の台 (台の高さ = 番号)。塔の北の壁ぎわに、西から東へ 1 → 5 と高くなって並ぶ (音階のように読める)
    public static final List<int[]> PILLARS = List.of(
            new int[]{-50, -124}, new int[]{-47, -124}, new int[]{-43, -124}, new int[]{-39, -124}, new int[]{-36, -124});
    // 入口の岩棚 (W2 と同じ高さ) と、そこから北の塔の床へ下りる階段
    public static final int LANDING_Y = FLOOR + 9;
    private static final int STAIR_X1 = -44;
    private static final int STAIR_X2 = -40;
    // 大きな吊り鐘の真下 (階段の上)。装飾
    private static final int GREAT_X = -43;
    private static final int GREAT_Z = -117;

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

    // 版 18 で作り直し (数え歌の鐘楼): 凝灰岩と緑青の銅の塔 (ZonePalette.BELL)。壁・床・天井は外周を必ず埋める (体の殻が薄くても穴が空かない)。
    // 入口は W2 の北の戸口 (BELL_PASS) 一か所だけで、銅の枠をつける。岩棚 (W2 と同じ高さ) から階段で塔の床へ下りる。
    // 塔の真ん中の天井から大きな吊り鐘 (飾り)、北の壁ぎわに 1〜5 の鐘が高さ順に並ぶ。四隅は銅の柱
    static void build(BuildKit kit) {
        kit.palette(ZonePalette.BELL);
        kit.roomSolid(BELL);
        // 戸口 (壁の厚さの分をくり抜く)。塔を作ったあとに開ける
        kit.room(BELL_PASS);
        kit.doorFrame(wall(BELL_PASS.x1(), BELL_PASS.x2(), LANDING_Y + 1, LANDING_Y + 6, BELL.z2() + 1, true), LANDING_Y);
        kit.doorFrame(wall(BELL_PASS.x1(), BELL_PASS.x2(), LANDING_Y + 1, LANDING_Y + 6, BELL_PASS.z2(), true), LANDING_Y);
        // 四隅の銅の柱 (灯りを埋める)
        for (int x : new int[]{BELL.x1(), BELL.x2()}) {
            for (int z : new int[]{BELL.z1(), BELL.z2()}) {
                for (int y = FLOOR + 1; y < BELL.ceilingY(); y++) {
                    kit.set(x, y, z, y == BELL.ceilingY() - 1 ? kit.pal().cap() : y == FLOOR + 1 ? kit.pal().trim()
                            : (y == FLOOR + 8 || y == FLOOR + 17) ? kit.pal().glow() : kit.pal().pillarAlong(Direction.Axis.Y));
                }
            }
        }
        kit.ribs(BELL, 6, true);
        kit.wallLamps(BELL, 5, FLOOR + 6);
        kit.wallLamps(BELL, 5, FLOOR + 16);
        // 床の模様: 中央の輪 (吊り鐘の真下) と、北の壁ぎわの鐘の列の帯
        for (int x = BELL.x1(); x <= BELL.x2(); x++) {
            for (int z = BELL.z1(); z <= BELL.z2(); z++) {
                double d = Math.hypot(x - GREAT_X, z - GREAT_Z);
                if (d <= 5.5 && d >= 4.5 || d <= 1.5) {
                    kit.set(x, FLOOR, z, kit.pal().trim());
                } else if (z >= -126 && z <= -122) {
                    kit.set(x, FLOOR, z, (x + z) % 2 == 0 ? kit.pal().floorAlt() : kit.pal().floor());
                }
            }
        }
        // 入口の岩棚 (W2 と同じ高さ): 塔の南の端。縁には低い手すり。岩棚の下は石で埋める (宙に浮かない)
        BlockState rail = Blocks.TUFF_BRICK_WALL.defaultBlockState();
        for (int x = BELL_PASS.x1() - 2; x <= BELL_PASS.x2() + 2; x++) {
            for (int z = BELL.z2() - 5; z <= BELL.z2(); z++) {
                kit.set(x, LANDING_Y, z, x == BELL_PASS.x1() - 2 || x == BELL_PASS.x2() + 2 ? kit.pal().trim() : kit.pal().floor());
                kit.fill(x, FLOOR + 1, z, x, LANDING_Y - 1, z, kit.pal().wall());
            }
        }
        for (int z = BELL.z2() - 4; z <= BELL.z2(); z++) {
            kit.set(BELL_PASS.x1() - 2, LANDING_Y + 1, z, rail);
            kit.set(BELL_PASS.x2() + 2, LANDING_Y + 1, z, rail);
        }
        // 岩棚から北の塔の床へ下りる階段 (登る向き = 南 = 背の高い側)。両脇は石の土手と低い壁
        BlockState step = Blocks.TUFF_BRICK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.SOUTH);
        int zStart = BELL.z2() - 6;
        for (int i = 0; ; i++) {
            int y = LANDING_Y - 1 - i;
            int z = zStart - i;
            if (y <= FLOOR) {
                break;
            }
            for (int x = STAIR_X1 - 1; x <= STAIR_X2 + 1; x++) {
                kit.fill(x, FLOOR + 1, z, x, y - 1, z, kit.pal().wall());
                boolean side = x == STAIR_X1 - 1 || x == STAIR_X2 + 1;
                if (side) {
                    kit.set(x, y, z, kit.pal().wall());
                    kit.set(x, y + 1, z, rail);
                    if (i == 0) {
                        // 岩棚の縁の手すりと土手の手すりのつなぎ目: 1 マスの隙間 (落ちる穴) を作らない
                        kit.set(x, y + 2, z, rail);
                    }
                } else {
                    kit.set(x, y, z, step);
                    kit.fill(x, y + 1, z, x, y + 6, z, BuildKit.AIR);
                }
            }
            // 階段の脇の灯り (1 段おきの手すりの上)
            if (i % 3 == 1) {
                kit.lantern(STAIR_X1 - 1, y + 2, z);
                kit.lantern(STAIR_X2 + 1, y + 2, z);
            }
        }
        // 鐘と台 (台の高さ = 番号)
        for (int n = 1; n <= COUNT; n++) {
            int[] p = PILLARS.get(n - 1);
            for (int y = FLOOR + 1; y <= FLOOR + n; y++) {
                kit.set(p[0], y, p[1], y == FLOOR + n ? kit.pal().trim() : kit.pal().pillarAlong(Direction.Axis.Y));
            }
            kit.set(bellPos(n), jp.revati.skyabyss.relic.RelicBlocks.bell(jp.revati.skyabyss.relic.RelicStyle.WHALE, Direction.NORTH));
            kit.lantern(p[0] + 1, FLOOR + 1, p[1]);
        }
        greatBell(kit);
        // 欠けた節の立て札 (岩棚の上)
        kit.standingSign(new BlockPos(-45, LANDING_Y + 1, BELL.z2() - 1), "sign.skyabyss.bell_hint");
        // 塔の天井から垂れる鎖と灯り (吊り鐘と岩棚の上は避ける)
        for (int[] l : new int[][]{{-49, -123}, {-37, -123}, {-49, -113}, {-37, -113}, {-49, -118}, {-37, -118}}) {
            kit.hangingLantern(l[0], l[1], BELL.ceilingY(), 6);
        }
    }

    // 天井から鎖で吊るした大きな鐘 (緑青の銅の塊。中に灯りの舌)。階段の上、頭の届かない高さ
    private static void greatBell(BuildKit kit) {
        BlockState copper = Blocks.WAXED_OXIDIZED_CUT_COPPER.defaultBlockState();
        BlockState dark = Blocks.WAXED_WEATHERED_CUT_COPPER.defaultBlockState();
        int top = BELL.ceilingY() - 1;
        // 鎖 (天井から 3 本)
        for (int y = top; y >= top - 3; y--) {
            kit.set(GREAT_X, y, GREAT_Z, Blocks.CHAIN.defaultBlockState());
        }
        int y0 = top - 4;
        kit.set(GREAT_X, y0, GREAT_Z, kit.pal().trim());
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                kit.set(GREAT_X + dx, y0 - 1, GREAT_Z + dz, copper);
            }
        }
        // 肩 (5×5 の層。中心は舌の鎖) と、口の縁 (広がる輪)
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                kit.set(GREAT_X + dx, y0 - 2, GREAT_Z + dz, copper);
                if (Math.abs(dx) == 2 || Math.abs(dz) == 2) {
                    kit.set(GREAT_X + dx, y0 - 3, GREAT_Z + dz, dark);
                }
            }
        }
        kit.set(GREAT_X, y0 - 2, GREAT_Z, Blocks.CHAIN.defaultBlockState());
        kit.set(GREAT_X, y0 - 3, GREAT_Z, kit.pal().hangingLantern());
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
