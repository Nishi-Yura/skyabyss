package jp.revati.skyabyss.story.ch1;

import jp.revati.skyabyss.util.Players;

import javax.annotation.Nullable;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.lore.Lore;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.opening.OpeningSites;
import jp.revati.skyabyss.opening.site.Blueprint;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

// 歌の間 (docs/design/21 の 3.1。宿りの鐘の下)。歌石 5 つを順に叩いて鐘が応えたあと、鐘の下の地面の石レンガの戸 (一枚) が梯子になる。
// 地下の部屋 (7x7 ほど) に、歌の順番の壁画 (高さ 1〜5 の五本の柱。低い順 = Ch1Prep.BELL_ORDER) と、伝承 ch1/song_chamber。
//   置く     宿り (shelter) が建ち、炉端 (LodgeNook) のあと、世界に一度 (Ch1Data.chamber)。部品 ch1_song_chamber (鐘を原点にした座標。tools/gen_ch1_sites.py)。
//            置く所が自然の地面 (石・土・砂・水・空気) でなければ (人が掘った・建てた) 置かずにあきらめる (人のブロックは壊さない)
//   開く     SongStones.answer (5 つ目の歌石のあとの鐘の応え) から open。戸は世界に一つ (一度開けば、ほかの人も入れる)。伝承は入った人ごと
//   確かめ   SELFTEST ch1 sites (Ch1SitesSelfTest)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class SongChamber {
    private static final int EVERY = 20;
    private static final int PLACE_EVERY = 200;

    private SongChamber() {
    }

    @SubscribeEvent
    static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD || level.getGameTime() % EVERY != 7) {
            return;
        }
        Ch1Data data = Ch1Data.get(level.getServer().overworld());
        if (data.chamber == 0 && level.getGameTime() % PLACE_EVERY == 7 && data.nook && OpeningSites.planned(level.getServer())
                && OpeningSites.built(level.getServer(), OpeningSites.Kind.SHELTER)) {
            LodgeNook.Frame frame = LodgeNook.frame(level);
            if (frame != null) {
                data.chamber = place(level, frame) ? 1 : -1;
                data.setDirty();
            }
        }
        if (data.chamber >= 1) {
            LodgeNook.Frame frame = LodgeNook.frame(level);
            if (frame != null) {
                // 歌石を鳴らしきった人がいれば開く (置く前に鳴らしきった世界でも、あとから開く)
                if (data.chamber == 1) {
                    for (ServerPlayer p : Players.snapshot(level.players())) {
                        if (data.players().containsKey(p.getUUID()) && data.player(p.getUUID()).songDone) {
                            open(level);
                            break;
                        }
                    }
                }
                if (data.chamber == 2) {
                    for (ServerPlayer p : Players.snapshot(level.players())) {
                        if (!p.isSpectator() && inside(frame, p)) {
                            visit(p);
                        }
                    }
                }
            }
        }
    }

    // 鐘の座標 (宿りの部品の座標。x, z)
    private static int[] bell() {
        int[] m = Blueprint.get("shelter").marks.get("bell");
        return new int[] {m[0], m[2]};
    }

    // 部品 (鐘を原点) の座標 → 世界
    static BlockPos at(LodgeNook.Frame frame, int cx, int cy, int cz) {
        int[] b = bell();
        return frame.at(b[0] + cx, cy, b[1] + cz);
    }

    // 地下の部屋の中か (水平 3 ブロック以内、床から天井まで)
    static boolean inside(LodgeNook.Frame frame, ServerPlayer p) {
        BlockPos centre = at(frame, 0, -4, 0);
        return Math.abs(p.getX() - (centre.getX() + 0.5)) <= 3.4 && Math.abs(p.getZ() - (centre.getZ() + 0.5)) <= 3.4
                && p.getY() >= centre.getY() - 1 && p.getY() <= centre.getY() + 2;
    }

    // 部屋に入った (人ごとに一度の記し)
    static void visit(ServerPlayer p) {
        if (Lore.unlock(p, Ch1Prep.SONG_CHAMBER, true)) {
            Notice.narrate(p, "message.skyabyss.ch1.arrive.song_chamber");
        }
    }

    // 置く。自然の地面でない所があれば何も壊さず false
    static boolean place(ServerLevel level, LodgeNook.Frame frame) {
        Blueprint bp = Blueprint.get(Ch1Prep.SONG_CHAMBER_BLUEPRINT);
        for (Blueprint.Cell c : bp.cells) {
            BlockPos at = at(frame, c.x(), c.y(), c.z());
            if (!level.hasChunkAt(at)) {
                return false;
            }
            if (!natural(level.getBlockState(at))) {
                SkyAbyss.LOGGER.info("[ch1] the song chamber is skipped: {} at {} is not natural", level.getBlockState(at), at);
                return false;
            }
        }
        for (Blueprint.Cell c : bp.cells) {
            BlockPos at = at(frame, c.x(), c.y(), c.z());
            level.setBlock(at, Blueprint.transform(c.state(), frame.rot(), frame.mirror()), 2 | 16);
        }
        // 書見台に伝承の書は置かない (伝承は入った人ごと)
        return true;
    }

    static boolean natural(BlockState s) {
        return s.isAir() || s.is(BlockTags.BASE_STONE_OVERWORLD) || s.is(BlockTags.DIRT) || s.is(BlockTags.SAND) || s.is(Blocks.GRAVEL) || s.is(Blocks.WATER)
                || s.is(BlockTags.DEEPSLATE_ORE_REPLACEABLES) || s.is(BlockTags.COAL_ORES) || s.is(BlockTags.IRON_ORES) || s.is(BlockTags.COPPER_ORES)
                || s.is(Blocks.DIRT_PATH) || s.is(Blocks.GRASS_BLOCK) || s.is(BlockTags.REPLACEABLE)
                // 部品そのもの (置き直し・二度目の確認)
                || s.is(Blocks.STONE_BRICKS) || s.is(Blocks.MOSSY_STONE_BRICKS) || s.is(Blocks.CRACKED_STONE_BRICKS) || s.is(Blocks.LADDER)
                || s.is(Blocks.CHISELED_STONE_BRICKS) || s.is(Blocks.AMETHYST_BLOCK) || s.is(Blocks.POLISHED_ANDESITE) || s.is(Blocks.LECTERN)
                || s.is(Blocks.WALL_TORCH);
    }

    // 戸 (地面の石レンガ 1 枚) の世界の位置
    @Nullable
    static BlockPos door(LodgeNook.Frame frame) {
        int[] m = Blueprint.get(Ch1Prep.SONG_CHAMBER_BLUEPRINT).marks.get("door");
        return m == null ? null : at(frame, m[0], m[1], m[2]);
    }

    public static boolean isOpen(ServerLevel level) {
        return Ch1Data.get(level.getServer().overworld()).chamber == 2;
    }

    // 戸を開ける (世界に一度)。鐘が応えたとき SongStones.answer から呼ぶ
    public static void onBellAnswered(ServerLevel level) {
        open(level);
    }

    static boolean open(ServerLevel level) {
        Ch1Data data = Ch1Data.get(level.getServer().overworld());
        if (data.chamber != 1) {
            return false;
        }
        LodgeNook.Frame frame = LodgeNook.frame(level);
        BlockPos door = frame == null ? null : door(frame);
        if (frame == null || door == null) {
            return false;
        }
        BlockState ladder = Blueprint.transform(Blocks.LADDER.defaultBlockState().setValue(net.minecraft.world.level.block.LadderBlock.FACING,
                net.minecraft.core.Direction.WEST), frame.rot(), frame.mirror());
        level.setBlock(door, ladder, 3);
        level.playSound(null, door, SoundEvents.STONE_BREAK, SoundSource.BLOCKS, 1.2f, 0.6f);
        level.playSound(null, door, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.0f, 0.7f);
        level.sendParticles(ParticleTypes.POOF, door.getX() + 0.5, door.getY() + 0.6, door.getZ() + 0.5, 10, 0.4, 0.2, 0.4, 0.02);
        data.chamber = 2;
        data.setDirty();
        return true;
    }
}
