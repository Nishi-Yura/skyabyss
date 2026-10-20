package jp.revati.skyabyss.trial;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.progress.Titans;
import jp.revati.skyabyss.titan.end.ReturnBeacons;
import jp.revati.skyabyss.titan.group.Groups;
import jp.revati.skyabyss.trial.Trial.Def;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;

// 今週の試練の入口 (docs/design/29 の 4.4・4.7)。鼓動の間 (背の真ん中) のすぐそばに、金の光る床 (パッド) と、そこへ続く灯りの道と、立て札を置く。
// パッドに立つと、その人だけが今週の試練の入口へ移る (引いていない試練でも入れる。戸はそのグループに開く)。越えたら、その人だけが鼓動の間へ戻る
// (TrialRuntime が戻す)。置き場は、碑の弧の反対側から決まった順に探す (背の作りが変わっても追う。同じ作りなら毎回同じ場所)
public final class WeeklyGate {
    private WeeklyGate() {
    }

    // 置き場: pad = パッドに立つ足もと (パッドのブロックはその 1 下)、path = 灯りの道の床 (足もとの 1 下)、sign = 立て札の足もと
    public record Site(BlockPos pad, List<BlockPos> path, @Nullable BlockPos sign, int signRotation) {
    }

    private static final Map<String, Site> SITES = new HashMap<>();
    private static final Map<UUID, Long> USED = new HashMap<>();
    private static final int COOLDOWN = 80;
    private static final double REACH = 0.8;

    public static void clearAll() {
        SITES.clear();
        USED.clear();
    }

    @Nullable
    public static Site site(ServerLevel back, String titan) {
        if (!HubTablets.enabled(titan)) {
            return null;
        }
        Site cached = SITES.get(titan);
        if (cached != null) {
            return cached;
        }
        BlockPos hub = ReturnBeacons.hubOf(titan);
        if (hub == null || !back.hasChunkAt(hub)) {
            return null;
        }
        Site found = find(back, titan, hub);
        if (found != null) {
            SITES.put(titan, found);
        }
        return found;
    }

    // パッドの置き場: 休み場から 3〜6 マスの開いた床で、碑 (台座) から 4 マス以上離れ、休み場までの道が歩ける所。碑の弧の反対側から
    @Nullable
    static Site find(ServerLevel back, String titan, BlockPos hub) {
        List<HubTablets.Spot> tablets = HubTablets.spots(back, titan);
        double awayX = 0;
        double awayZ = 0;
        for (HubTablets.Spot s : tablets) {
            awayX += hub.getX() - s.pos().getX();
            awayZ += hub.getZ() - s.pos().getZ();
        }
        double base = awayX == 0 && awayZ == 0 ? 0 : Math.atan2(awayZ, awayX);
        List<BlockPos> cand = new ArrayList<>();
        List<Double> order = new ArrayList<>();
        int[] radii = {4, 5, 3, 6};
        for (int a = 0; a < 360; a += 15) {
            double ang = base + Math.toRadians(a <= 180 ? a : a - 360);
            for (int r : radii) {
                int x = hub.getX() + (int) Math.round(Math.cos(ang) * r);
                int z = hub.getZ() + (int) Math.round(Math.sin(ang) * r);
                BlockPos p = new BlockPos(x, hub.getY(), z);
                boolean clear = true;
                for (HubTablets.Spot s : tablets) {
                    if (s.pos().distSqr(p) < 16) {
                        clear = false;
                    }
                }
                if (clear && free(back, p, true) && walkable(back, hub, p)) {
                    cand.add(p);
                    order.add(Math.abs(a <= 180 ? a : a - 360) + r * 0.01);
                    break;
                }
            }
        }
        if (cand.isEmpty()) {
            return null;
        }
        Integer[] idx = new Integer[cand.size()];
        for (int i = 0; i < idx.length; i++) {
            idx[i] = i;
        }
        java.util.Arrays.sort(idx, Comparator.comparingDouble(order::get));
        for (int i : idx) {
            BlockPos pad = cand.get(i);
            // 立て札: パッドの休み場と反対側 → 左右 の順に、足もとが空いている所
            Direction away = facingAway(hub, pad);
            BlockPos sign = null;
            int rotation = 0;
            for (Direction d : new Direction[] {away, away.getClockWise(), away.getCounterClockWise()}) {
                BlockPos s = pad.relative(d);
                if (free(back, s, false) && s.distSqr(hub) > 4) {
                    sign = s;
                    rotation = rotationToward(s, pad);
                    break;
                }
            }
            if (sign == null) {
                continue;
            }
            return new Site(pad, line(hub, pad), sign, rotation);
        }
        return null;
    }

    // 休み場からパッドへの道 (両端を除く)。足もとの 1 下の床
    private static List<BlockPos> line(BlockPos hub, BlockPos pad) {
        List<BlockPos> out = new ArrayList<>();
        int steps = Math.max(Math.abs(pad.getX() - hub.getX()), Math.abs(pad.getZ() - hub.getZ()));
        for (int i = 1; i < steps; i++) {
            int x = hub.getX() + Math.round((pad.getX() - hub.getX()) * (float) i / steps);
            int z = hub.getZ() + Math.round((pad.getZ() - hub.getZ()) * (float) i / steps);
            BlockPos p = new BlockPos(x, hub.getY() - 1, z);
            if (!out.contains(p)) {
                out.add(p);
            }
        }
        return out;
    }

    private static boolean walkable(ServerLevel back, BlockPos hub, BlockPos pad) {
        for (BlockPos floor : line(hub, pad)) {
            BlockPos f = floor.above();
            if (!back.getBlockState(floor).isFaceSturdy(back, floor, Direction.UP) || !passable(back, f) || !passable(back, f.above())) {
                return false;
            }
        }
        return true;
    }

    private static boolean passable(ServerLevel back, BlockPos p) {
        return back.getBlockState(p).isAir() || back.getBlockState(p).is(Blocks.LIGHT);
    }

    // 立てられる床: 足もとが空気 (または立て札)・頭上 2 が空気・足もとの下が当たりのある床。lateral = 四方の空きも見る
    private static boolean free(ServerLevel back, BlockPos p, boolean lateral) {
        BlockState at = back.getBlockState(p);
        if (!(at.isAir() || at.getBlock() instanceof StandingSignBlock)) {
            return false;
        }
        if (!back.getBlockState(p.above()).isAir() || !back.getBlockState(p.above(2)).isAir()) {
            return false;
        }
        BlockState below = back.getBlockState(p.below());
        if (!below.isFaceSturdy(back, p.below(), Direction.UP)) {
            return false;
        }
        if (lateral) {
            for (Direction d : Direction.Plane.HORIZONTAL) {
                BlockState n = back.getBlockState(p.relative(d));
                if (!(n.isAir() || n.getBlock() instanceof StandingSignBlock)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static Direction facingAway(BlockPos hub, BlockPos pad) {
        int dx = pad.getX() - hub.getX();
        int dz = pad.getZ() - hub.getZ();
        return Math.abs(dx) > Math.abs(dz) ? (dx > 0 ? Direction.EAST : Direction.WEST) : (dz > 0 ? Direction.SOUTH : Direction.NORTH);
    }

    // 立て札の向き (表が pad のほうを向く。StandingSign: 0 = 南向き・4 = 西・8 = 北・12 = 東 の表)
    private static int rotationToward(BlockPos sign, BlockPos target) {
        int dx = target.getX() - sign.getX();
        int dz = target.getZ() - sign.getZ();
        return Math.abs(dx) > Math.abs(dz) ? (dx > 0 ? 12 : 4) : (dz > 0 ? 0 : 8);
    }

    // ---------------------------------------------------------------- 置く
    // 光る床・灯りの道・立て札を世界に置く (何度でも。立て札の今週の試練の名前は週で変わるので書き直す)。置いたら true
    public static boolean install(ServerLevel back, String titan) {
        Site site = site(back, titan);
        if (site == null) {
            return false;
        }
        boolean changed = false;
        BlockState padBlock = Blocks.GOLD_BLOCK.defaultBlockState();
        BlockPos under = site.pad().below();
        if (!back.getBlockState(under).is(padBlock.getBlock())) {
            back.setBlock(under, padBlock, 3);
            changed = true;
        }
        BlockState lamp = HubTablets.inlayLight(titan);
        for (BlockPos p : site.path()) {
            if (!back.getBlockState(p).is(lamp.getBlock())) {
                back.setBlock(p, lamp, 3);
                changed = true;
            }
        }
        // パッドの四隅の床 (灯り)
        for (int dx = -1; dx <= 1; dx += 2) {
            for (int dz = -1; dz <= 1; dz += 2) {
                BlockPos c = under.offset(dx, 0, dz);
                if (!back.getBlockState(c).is(lamp.getBlock()) && back.getBlockState(c).isFaceSturdy(back, c, Direction.UP)
                        && !(back.getBlockState(c.above()).getBlock() instanceof StandingSignBlock)) {
                    back.setBlock(c, lamp, 3);
                    changed = true;
                }
            }
        }
        if (site.sign() != null) {
            BlockPos sp = site.sign();
            if (!(back.getBlockState(sp).getBlock() instanceof StandingSignBlock)) {
                back.setBlock(sp, Blocks.DARK_OAK_SIGN.defaultBlockState().setValue(StandingSignBlock.ROTATION, site.signRotation()), 3);
                changed = true;
            }
            if (back.getBlockEntity(sp) instanceof SignBlockEntity sign) {
                Def weekly = TrialData.weekly(titan, TrialData.worldSeed(back), TrialData.week());
                Component name = weekly == null ? Component.empty() : Component.translatable(weekly.nameKey());
                Component[] lines = {Component.translatable("sign.skyabyss.weekly_gate.1"), name, Component.translatable("sign.skyabyss.weekly_gate.3"),
                        Component.translatable("sign.skyabyss.weekly_gate.4")};
                SignText now = sign.getFrontText();
                if (!now.getMessage(1, false).equals(name) || !now.getMessage(0, false).equals(lines[0])) {
                    SignText text = new SignText(lines, lines, DyeColor.YELLOW, true);
                    sign.setText(text, true);
                    sign.setWaxed(true);
                    sign.setChanged();
                    back.sendBlockUpdated(sp, back.getBlockState(sp), back.getBlockState(sp), 3);
                    changed = true;
                }
            }
        }
        return changed;
    }

    // ---------------------------------------------------------------- 入口
    // 今週の試練の入口へ人が立つ所 (戸の足もと)。入口の戸がなければ、休み場・床の範囲の中。なければ null
    @Nullable
    public static BlockPos entryPoint(ServerLevel back, String titan) {
        Def weekly = TrialData.weekly(titan, TrialData.worldSeed(back), TrialData.week());
        if (weekly == null) {
            return null;
        }
        TrialCell cell = TrialCells.get(weekly.id());
        if (cell == null) {
            return null;
        }
        List<BlockPos> door = cell.ports().entrance();
        if (!door.isEmpty()) {
            BlockPos best = door.get(0);
            for (BlockPos b : door) {
                if (b.getY() < best.getY() || b.getY() == best.getY() && b.distSqr(ReturnBeacons.hubOf(titan)) < best.distSqr(ReturnBeacons.hubOf(titan))) {
                    best = b;
                }
            }
            return best;
        }
        if (!cell.ports().rests().isEmpty()) {
            return cell.ports().rests().get(0).spawn();
        }
        // 入口の戸も休み場もない原型 (潮殻の西・東・前): その試練の範囲の中の休み場のうち、鼓動の間にいちばん近い所
        String key;
        try {
            key = TrialRegions.keyOfDef(titan, weekly, null);
        } catch (RuntimeException e) {
            key = null;
        }
        BlockPos hub = ReturnBeacons.hubOf(titan);
        if (key == null || hub == null) {
            return null;
        }
        List<net.minecraft.world.phys.AABB> region = TrialRegions.of(back, titan, key, null);
        BlockPos best = null;
        for (Map.Entry<String, BlockPos> e : ReturnBeacons.restSpots(titan).entrySet()) {
            BlockPos r = e.getValue();
            if (!e.getKey().equals("hub") && !e.getKey().equals("heart") && TrialRegions.inside(region, net.minecraft.world.phys.Vec3.atCenterOf(r))
                    && (best == null || r.distSqr(hub) < best.distSqr(hub))) {
                best = r;
            }
        }
        return best;
    }

    // 10 tick ごとに TrialHub から: パッドに立った人を、今週の試練の入口へ送る。送った人の数
    static int tick(ServerLevel back, String titan, List<ServerPlayer> players, long now) {
        Site site = site(back, titan);
        if (site == null) {
            return 0;
        }
        int sent = 0;
        for (ServerPlayer p : players) {
            if (!p.isAlive() || p.isSpectator() || p.getY() < site.pad().getY() - 0.5 || p.getY() > site.pad().getY() + 1.2
                    || Math.abs(p.getX() - (site.pad().getX() + 0.5)) > REACH || Math.abs(p.getZ() - (site.pad().getZ() + 0.5)) > REACH) {
                continue;
            }
            Long last = USED.get(p.getUUID());
            if (last != null && now - last < COOLDOWN) {
                continue;
            }
            USED.put(p.getUUID(), now);
            if (send(back, titan, p)) {
                sent++;
            }
        }
        return sent;
    }

    // その人を今週の試練の入口へ送る (済ませた週・入口のないときは一行だけ)。送れたか
    public static boolean send(ServerLevel back, String titan, ServerPlayer p) {
        Def weekly = TrialData.weekly(titan, TrialData.worldSeed(back), TrialData.week());
        if (weekly == null) {
            return false;
        }
        UUID group = Groups.keyOf(p);
        TrialCodex codex = TrialCodex.get(back.getServer());
        if (codex.weeklyDone(p.getUUID(), titan) == TrialData.week()) {
            Notice.bar(p, ModText.hint("message.skyabyss.trial_hub.weekly_already"));
            return false;
        }
        BlockPos at = entryPoint(back, titan);
        if (at == null) {
            return false;
        }
        TrialRuntime.syncGates(back, group, titan, TrialData.current(back, group));
        p.stopRiding();
        back.sendParticles(ParticleTypes.END_ROD, p.getX(), p.getY() + 1, p.getZ(), 14, 0.3, 0.6, 0.3, 0.03);
        p.teleportTo(back, at.getX() + 0.5, at.getY(), at.getZ() + 0.5, p.getYRot(), 0f);
        if (p instanceof net.neoforged.neoforge.common.util.FakePlayer) {
            p.setPos(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        }
        p.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        p.fallDistance = 0;
        back.playSound(null, at.getX() + 0.5, at.getY(), at.getZ() + 0.5, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.8f, 1.8f);
        Notice.bar(p, ModText.progress("message.skyabyss.trial_hub.weekly_go", Component.translatable(weekly.nameKey())));
        return true;
    }
}
