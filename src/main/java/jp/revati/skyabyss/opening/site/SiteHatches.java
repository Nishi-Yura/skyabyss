package jp.revati.skyabyss.opening.site;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiFunction;

import javax.annotation.Nullable;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.util.Players;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

// 小さなダンジョンの奥から地上へ出る縦穴の跳ね上げ戸 (2026-11-07 の声「外から入ってボスへ行けた」)。
// 戸は内側 (下) からだけ開く: 戸より上に立つ人が触れても開かず、一行を出す。開いたままの戸は、そばに人がいなければ閉じる
// (内から出た人が開けたままにすると、外の人が下りられた)。戸がない (梯子だけの口) ときは、部品の戸の形 (want) を置く。
// 登録 (場所の要素ごと): 沈んだ塩の蔵 (延ばした戸の高さは SaltVault.exitTop)・骨の坑 (一章)・消えた炉の坑 (四章)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class SiteHatches {
    public static final String HINT = "message.skyabyss.hatch.inside_only";
    static final double NEAR = 2.5;
    static final int EVERY = 20;

    // 場所の要素 site の戸の位置 (建った要素から)。want = 戸がないときに置く形 (null なら置かない)
    record Hatch(String site, BiFunction<ServerLevel, SiteData.Entry, BlockPos> at, @Nullable BlockState want, @Nullable int[] cell) {
    }

    private static final List<Hatch> HATCHES = new CopyOnWriteArrayList<>();

    private SiteHatches() {
    }

    public static void register(String site, BiFunction<ServerLevel, SiteData.Entry, BlockPos> at) {
        HATCHES.add(new Hatch(site, at, null, null));
    }

    // 部品の座標 (x, y, z) の戸。部品のその所の形を、戸がないときに置く (部品は初めて使うときに読む)
    public static void registerCell(String site, int x, int y, int z) {
        HATCHES.add(new Hatch(site, (level, e) -> Blueprint.transform(x, y, z, SiteFinder.rot(e.yaw), SiteFinder.mirror(e.yaw), e.pos), null, new int[] {x, y, z}));
    }

    @Nullable
    static BlockState cellState(String site, int[] c) {
        for (Blueprint.Cell cell : Blueprint.get(site).cells) {
            if (cell.x() == c[0] && cell.y() == c[1] && cell.z() == c[2] && cell.state().getBlock() instanceof TrapDoorBlock) {
                return cell.state();
            }
        }
        return null;
    }

    // 建った要素ごとの戸の位置と、置く形 (向きを要素に合わせる)
    public record Spot(BlockPos pos, @Nullable BlockState want) {
    }

    static List<Spot> spots(ServerLevel level) {
        List<Spot> out = new ArrayList<>();
        if (HATCHES.isEmpty()) {
            return out;
        }
        for (SiteData.ChapterPlan plan : SiteData.get(level).plans()) {
            for (SiteData.Entry e : plan.sites) {
                if (!e.built) {
                    continue;
                }
                for (Hatch h : HATCHES) {
                    if (h.site().equals(e.siteId)) {
                        BlockPos at = h.at().apply(level, e);
                        if (at != null) {
                            BlockState raw = h.cell() != null ? cellState(h.site(), h.cell()) : h.want();
                            BlockState want = raw == null ? null : Blueprint.transform(raw, SiteFinder.rot(e.yaw), SiteFinder.mirror(e.yaw));
                            out.add(new Spot(at, want));
                        }
                    }
                }
            }
        }
        return out;
    }

    // 戸より上 (外) にいる人か
    public static boolean outside(ServerPlayer p, BlockPos hatch) {
        return p.getY() >= hatch.getY() + 0.5;
    }

    @SubscribeEvent
    static void onUse(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide || event.getHand() != InteractionHand.MAIN_HAND || !(event.getEntity() instanceof ServerPlayer p)
                || !(event.getLevel() instanceof ServerLevel level) || level != level.getServer().overworld()) {
            return;
        }
        BlockPos pos = event.getPos();
        if (!(level.getBlockState(pos).getBlock() instanceof TrapDoorBlock) || !outside(p, pos)) {
            return;
        }
        for (Spot s : spots(level)) {
            if (s.pos().equals(pos)) {
                event.setCanceled(true);
                Notice.hint(p, HINT);
                return;
            }
        }
    }

    @SubscribeEvent
    static void onTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % EVERY != 9 || HATCHES.isEmpty()) {
            return;
        }
        ServerLevel level = server.overworld();
        List<ServerPlayer> players = Players.snapshot(level.players());
        for (Spot s : spots(level)) {
            mend(level, s, players);
        }
    }

    // 戸がなければ置き、開いていてそばに人がいなければ閉じる。変えたら true
    public static boolean mend(ServerLevel level, Spot s, List<ServerPlayer> players) {
        BlockPos pos = s.pos();
        if (!level.isLoaded(pos)) {
            return false;
        }
        BlockState st = level.getBlockState(pos);
        if (!(st.getBlock() instanceof TrapDoorBlock)) {
            if (s.want() != null && (st.isAir() || st.is(Blocks.LADDER))) {
                level.setBlock(pos, s.want(), Block.UPDATE_ALL);
                return true;
            }
            return false;
        }
        if (!st.getValue(TrapDoorBlock.OPEN)) {
            return false;
        }
        for (ServerPlayer p : players) {
            if (!p.isSpectator() && p.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) < NEAR * NEAR) {
                return false;
            }
        }
        level.setBlock(pos, st.setValue(TrapDoorBlock.OPEN, false), Block.UPDATE_ALL);
        level.playSound(null, pos, SoundEvents.WOODEN_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 0.8f, 1f);
        return true;
    }

    // 自己テスト: その場所の戸を今すぐ見回る。変えた数
    public static int mendNow(ServerLevel level) {
        int n = 0;
        List<ServerPlayer> players = Players.snapshot(level.players());
        for (Spot s : spots(level)) {
            if (mend(level, s, players)) {
                n++;
            }
        }
        return n;
    }
}
