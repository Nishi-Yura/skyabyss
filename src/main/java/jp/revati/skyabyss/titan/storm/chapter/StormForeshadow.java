package jp.revati.skyabyss.titan.storm.chapter;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.sky.ChapterSky;
import jp.revati.skyabyss.story.Chapters;
import jp.revati.skyabyss.story.arc.Beat;
import jp.revati.skyabyss.story.arc.ForeshadowRegisterEvent;
import jp.revati.skyabyss.titan.storm.StormKeeper;
import jp.revati.skyabyss.titan.storm.StormSetup;
import jp.revati.skyabyss.titan.storm.StormWingEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

// 三章の予兆 3 拍 (docs/design/12 の 12.2、01 の 4.1)。三章を遊んでいる人 (段階 2) にだけ、条件が合った順に一度ずつ。起こすのは跡・音・小さな異変だけ
// (敵も、ジャンプスケアも、チャットもない。字幕は Notice.narrate)。
//   1 痕跡  titan_storm/burn_line   昼。目の前の地面に、一直線に焼けた筋と、倒れた木 (黒石・玄武岩・焦げた丸太)。筋は嵐 (巨神) の方へ向く
//   2 間接  titan_storm/low_beasts  夜。空の見える所で、獣が身を伏せる (近くの動物の足を止める)。晴れた夜空の下で風上から遠雷。
//                                   空の影のときの獣の仕草と同じだが、昼ではなく夜で、音がある (影は音を立てない)
//   3 一部  titan_storm/wing_flash  雷翼が 150〜640 ブロックの所にいて、空が見えるとき。稲光 (演出だけ) の一瞬、雲の下に翼の形。雷が遅れて届く
// 登録は ForeshadowRegisterEvent (モッドバス) から。同じ登録で小要素 (StormSites) と章の空の段 (ChapterSky) も足す。
@EventBusSubscriber(modid = SkyAbyss.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class StormForeshadow {
    public static final String BURN_LINE = "burn_line";
    public static final String LOW_BEASTS = "low_beasts";
    public static final String WING_FLASH = "wing_flash";

    // 筋の長さ・置く場所 (人から)・必要な最小の本数
    static final int LINE_LENGTH = 30;
    static final int LINE_MIN_COLUMNS = 18;
    static final double LINE_NEAR = 30;
    static final double LINE_FAR = 58;
    // 伏せる獣を探す範囲
    static final double BEAST_RANGE = 40;
    // 稲光を見る人からの距離の上限 (これより遠いときは、方向だけを保って手前に落とす)
    static final double FLASH_REACH = 120;
    // 三章の空の段 (月の近さに足す値、空の色味)
    public static final ChapterSky.Stage SKY_STAGE = new ChapterSky.Stage(0.04f, 0x7A86A0, 0.07f);

    private StormForeshadow() {
    }

    @SubscribeEvent
    static void onRegister(ForeshadowRegisterEvent event) {
        for (Beat b : beats()) {
            event.register(b);
        }
        StormSites.register();
        ChapterSky.setStage(Chapters.STORM, SKY_STAGE);
    }

    public static List<Beat> beats() {
        List<Beat> out = new ArrayList<>();
        out.add(Beat.of(Chapters.STORM, 1, BURN_LINE, (p, b) -> burnLine(p)).time(Beat.Time.DAY));
        out.add(Beat.of(Chapters.STORM, 2, LOW_BEASTS, (p, b) -> lowBeasts(p)).time(Beat.Time.NIGHT).when(StormForeshadow::seesSky));
        out.add(Beat.of(Chapters.STORM, 3, WING_FLASH, (p, b) -> wingFlash(p)).distance(StormForeshadow::titanPos, 150, 640).when(StormForeshadow::seesSky));
        return out;
    }

    static boolean seesSky(ServerPlayer p) {
        return p.level().canSeeSky(p.blockPosition().above());
    }

    @Nullable
    static Vec3 titanPos(ServerPlayer p) {
        StormWingEntity titan = p.level() instanceof ServerLevel level ? StormKeeper.KEEPER.current(level) : null;
        return titan == null ? null : titan.position();
    }

    // ---------------------------------------------------------------- 1 焼けた筋
    static boolean burnLine(ServerPlayer player) {
        ServerLevel level = (ServerLevel) player.level();
        RandomSource rnd = level.random;
        Vec3 titan = titanPos(player);
        for (int attempt = 0; attempt < 14; attempt++) {
            // 見ている向きの前方 (±60 度) の 30〜58 ブロック先から始め、筋 (長い方の軸に 30 歩) は巨神の方 (いなければ適当な向き) へ
            double look = Math.toRadians(player.getYRot() + 90.0) + (rnd.nextDouble() - 0.5) * Math.toRadians(120);
            double dist = LINE_NEAR + rnd.nextDouble() * (LINE_FAR - LINE_NEAR);
            BlockPos start = BlockPos.containing(player.getX() + Math.cos(look) * dist, 0, player.getZ() + Math.sin(look) * dist);
            double heading = titan != null ? Math.atan2(titan.z - start.getZ(), titan.x - start.getX()) : rnd.nextDouble() * Math.PI * 2;
            heading += (rnd.nextDouble() - 0.5) * Math.toRadians(16);
            double ux = Math.cos(heading);
            double uz = Math.sin(heading);
            List<BlockPos> tops = line(level, start, ux, uz, LINE_LENGTH);
            if (tops.size() >= LINE_MIN_COLUMNS) {
                lay(level, tops, ux, uz, rnd);
                Notice.narrate(player, "message.skyabyss.ch3.burn_line");
                return true;
            }
        }
        return false;
    }

    // 筋にできる地表の列 (読み込み済み・自然の地面・上が空いている・水でない)。start から (ux, uz) の向きに length 歩
    static List<BlockPos> line(ServerLevel level, BlockPos start, double ux, double uz, int length) {
        List<BlockPos> out = new ArrayList<>();
        Set<Long> seen = new LinkedHashSet<>();
        // 長い方の軸に 1 歩ずつ (斜めでも列が切れない)
        double scale = 1.0 / Math.max(Math.abs(ux), Math.abs(uz));
        for (int i = 0; i < length; i++) {
            int x = start.getX() + (int) Math.round(ux * i * scale);
            int z = start.getZ() + (int) Math.round(uz * i * scale);
            if (!seen.add(BlockPos.asLong(x, 0, z)) || !level.hasChunkAt(new BlockPos(x, 0, z))) {
                continue;
            }
            BlockPos top = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1, z);
            if (top.getY() <= level.getMinBuildHeight() || !natural(level.getBlockState(top))) {
                continue;
            }
            BlockState above = level.getBlockState(top.above());
            if (!level.getFluidState(top.above()).isEmpty() || !(above.isAir() || above.canBeReplaced())) {
                continue;
            }
            out.add(top);
        }
        return out;
    }

    // 焦がしてよい自然の地面 (土・草・砂・砂利・雪・石)。人が置いた物 (板・レンガなど) は入らない
    static boolean natural(BlockState s) {
        return s.is(BlockTags.DIRT) || s.is(BlockTags.SAND) || s.is(BlockTags.BASE_STONE_OVERWORLD) || s.is(Blocks.GRAVEL) || s.is(Blocks.SNOW_BLOCK)
                || s.is(Blocks.CLAY) || s.is(BlockTags.TERRACOTTA);
    }

    static BlockState charred(RandomSource rnd) {
        double r = rnd.nextDouble();
        if (r < 0.55) {
            return Blocks.BLACKSTONE.defaultBlockState();
        }
        if (r < 0.8) {
            return Blocks.BASALT.defaultBlockState();
        }
        if (r < 0.93) {
            return Blocks.SMOOTH_BASALT.defaultBlockState();
        }
        return Blocks.COAL_BLOCK.defaultBlockState();
    }

    // 焼く: 列を黒く変え (たまに隣も)、筋に沿って倒れた木を 3 本まで寝かせる。置いた数 (焼けた地面) を返す
    static int lay(ServerLevel level, List<BlockPos> tops, double ux, double uz, RandomSource rnd) {
        int placed = 0;
        int sideX = (int) Math.round(-uz);
        int sideZ = (int) Math.round(ux);
        for (BlockPos top : tops) {
            if (scorch(level, top, rnd)) {
                placed++;
            }
            if (rnd.nextFloat() < 0.35f && (sideX != 0 || sideZ != 0)) {
                int x = top.getX() + (rnd.nextBoolean() ? sideX : -sideX);
                int z = top.getZ() + (rnd.nextBoolean() ? sideZ : -sideZ);
                if (level.hasChunkAt(new BlockPos(x, 0, z))) {
                    BlockPos t = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1, z);
                    BlockState above = level.getBlockState(t.above());
                    if (natural(level.getBlockState(t)) && level.getFluidState(t.above()).isEmpty() && (above.isAir() || above.canBeReplaced())) {
                        scorch(level, t, rnd);
                    }
                }
            }
        }
        // 倒れた木: 筋の向きに 3 本つなぎ、地面の高さがそろっている所だけ
        boolean alongX = Math.abs(ux) >= Math.abs(uz);
        Direction.Axis axis = alongX ? Direction.Axis.X : Direction.Axis.Z;
        int logs = 0;
        for (int i = 5; i < tops.size() - 3 && logs < 3; i += 8) {
            BlockPos a = tops.get(i);
            int dx = alongX ? 1 : 0;
            int dz = alongX ? 0 : 1;
            boolean ok = true;
            for (int k = 0; k < 3 && ok; k++) {
                BlockPos g = a.offset(dx * k, 0, dz * k);
                ok = level.hasChunkAt(g) && (isCharred(level.getBlockState(g)) || natural(level.getBlockState(g))) && level.getBlockState(g.above()).isAir();
            }
            if (!ok) {
                continue;
            }
            for (int k = 0; k < 3; k++) {
                level.setBlock(a.offset(dx * k, 1, dz * k), Blocks.DARK_OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, axis), 2);
            }
            logs++;
        }
        return placed;
    }

    static boolean isCharred(BlockState s) {
        return s.is(Blocks.BLACKSTONE) || s.is(Blocks.BASALT) || s.is(Blocks.SMOOTH_BASALT) || s.is(Blocks.COAL_BLOCK);
    }

    private static boolean scorch(ServerLevel level, BlockPos top, RandomSource rnd) {
        BlockPos above = top.above();
        BlockState up = level.getBlockState(above);
        if (!up.isAir() && up.canBeReplaced()) {
            level.setBlock(above, Blocks.AIR.defaultBlockState(), 2);
        }
        level.setBlock(top, charred(rnd), 2);
        return true;
    }

    // ---------------------------------------------------------------- 2 伏せる獣と遠雷
    static boolean lowBeasts(ServerPlayer player) {
        ServerLevel level = (ServerLevel) player.level();
        List<Animal> animals = level.getEntitiesOfClass(Animal.class, player.getBoundingBox().inflate(BEAST_RANGE));
        for (Animal a : animals) {
            a.getNavigation().stop();
            a.getLookControl().setLookAt(a.getX(), a.getY() - 1.0, a.getZ());
            a.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 300, 6, false, false));
        }
        // 遠雷は風上から (巨神がいればその方角。いなければ風見と同じ「風」の向き)
        Vec3 titan = titanPos(player);
        double angle = titan != null ? Math.atan2(titan.z - player.getZ(), titan.x - player.getX()) : StormVane.windAngle(level.getDayTime() / 24000L);
        double dist = 46;
        level.playSound(null, player.getX() + Math.cos(angle) * dist, player.getY() + 4, player.getZ() + Math.sin(angle) * dist,
                StormSetup.THUNDER_FAR.get(), SoundSource.AMBIENT, 3.0f, 0.8f);
        Notice.narrate(player, animals.isEmpty() ? "message.skyabyss.ch3.low_plain" : "message.skyabyss.ch3.low_beasts");
        return true;
    }

    // ---------------------------------------------------------------- 3 稲光の翼
    static boolean wingFlash(ServerPlayer player) {
        ServerLevel level = (ServerLevel) player.level();
        Vec3 titan = titanPos(player);
        if (titan == null) {
            return false;
        }
        double dx = titan.x - player.getX();
        double dz = titan.z - player.getZ();
        double d = Math.hypot(dx, dz);
        if (d < 1) {
            return false;
        }
        double reach = Math.min(d, FLASH_REACH);
        double bx = player.getX() + dx / d * reach;
        double bz = player.getZ() + dz / d * reach;
        if (!level.hasChunkAt(BlockPos.containing(bx, 0, bz))) {
            return false;
        }
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (bolt == null) {
            return false;
        }
        bolt.moveTo(bx, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) bx, (int) bz), bz);
        bolt.setVisualOnly(true);
        level.addFreshEntity(bolt);
        Notice.narrate(player, "message.skyabyss.ch3.wing_flash");
        // 雷は遅れて届く (遠いほど遅い。1 秒〜2.5 秒)
        int delay = 20 + (int) Math.min(50, d / 12.0);
        double sx = player.getX() + dx / d * 40;
        double sz = player.getZ() + dz / d * 40;
        StormChapter.later(level.getServer(), delay,
                () -> level.playSound(null, sx, player.getY() + 6, sz, StormSetup.THUNDER_FAR.get(), SoundSource.AMBIENT, 3.0f, 0.7f));
        return true;
    }
}
