package jp.revati.skyabyss.world.continent;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.opening.site.SiteData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BellAttachType;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

// 隠れた発見 (2026-11-03 の案「隠れた発見」。Wynncraft の Secret Discoveries)。地図に出ない小さな見つけ物を、土地ごとに 4 か所
// (data/skyabyss/continent/secrets.json。tools/gen/world/gen_secrets.py が、道・町・見どころから離れた崖の下や丘の肩を選ぶ)。
//   建てる  人が BUILD_RADIUS まで近づいたら、その所の地面の上に小さな形を置く (苔の祠・旅人の跡・埋もれた頭・鐘の欠片・巨神の骨の先・
//           貝の塚・避雷の残り・灰の像)。建った印は SiteData の種類 KIND (地上を作り直すと消え、建て直す。upgrade/ContinentRegen)
//   気配    まだ見つけていない人が HINT_RADIUS まで来ると、3 秒ごとにかすかな鈴の音と光の粒 (その人にだけ)
//   見つける FIND_RADIUS まで来ると、名前の札・一行の言い伝え・土地の経験値 (見どころの 2 倍)。人ごとに覚え、倒れても消えない。
//           すべて見つけると鐘銭 ALL_COINS。地図には出さない
// はじまりの島には置かない (島へは戻れないので、見落とすと二度と見つけられない)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class ContinentSecrets {
    public static final String KIND = "secret";
    static final String FOUND_TAG = "skyabyss_secret_found";
    static final int BUILD_RADIUS = 64;
    static final int HINT_RADIUS = 24;
    static final double FIND_RADIUS = 4.5;
    static final int ALL_COINS = 1000;

    public record Secret(int id, String region, String kind, int x, int z) {
    }

    private static List<Secret> all;

    private ContinentSecrets() {
    }

    public static synchronized List<Secret> all() {
        if (all == null) {
            List<Secret> out = new ArrayList<>();
            try (InputStream in = ContinentSecrets.class.getResourceAsStream("/data/skyabyss/continent/secrets.json")) {
                if (in != null) {
                    JsonObject o = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
                    for (JsonElement e : o.getAsJsonArray("secrets")) {
                        JsonObject s = e.getAsJsonObject();
                        out.add(new Secret(s.get("id").getAsInt(), s.get("region").getAsString(), s.get("kind").getAsString(), s.get("x").getAsInt(),
                                s.get("z").getAsInt()));
                    }
                }
            } catch (Exception e) {
                SkyAbyss.LOGGER.warn("Continent secrets could not be read: {}", e.toString());
            }
            all = List.copyOf(out);
        }
        return all;
    }

    // ---------------------------------------------------------------- 人ごとの記録
    static CompoundTag persisted(Player p) {
        CompoundTag root = p.getPersistentData();
        if (!root.contains(Player.PERSISTED_NBT_TAG)) {
            root.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        }
        return root.getCompound(Player.PERSISTED_NBT_TAG);
    }

    public static Set<Integer> found(Player p) {
        Set<Integer> out = new HashSet<>();
        for (int i : persisted(p).getIntArray(FOUND_TAG)) {
            out.add(i);
        }
        return out;
    }

    // 初めてなら覚えて、札・言い伝え・経験値。見つけたら true
    public static boolean discover(ServerPlayer p, Secret s) {
        Set<Integer> found = found(p);
        if (!found.add(s.id())) {
            return false;
        }
        persisted(p).putIntArray(FOUND_TAG, found.stream().mapToInt(Integer::intValue).sorted().toArray());
        // 言い伝えは場所の札の副題に (語りの一行は画面に出さないので)。数は経験値の札に
        Notice.place(p, Component.translatable("secret.skyabyss." + s.kind()), Component.translatable("secret.skyabyss." + s.region() + "." + s.kind()));
        Notice.gain(p, "message.skyabyss.secret.found", found.size(), all().size());
        int region = ContinentMap.get().region(s.x(), s.z());
        jp.revati.skyabyss.rpg.Growth.addXp(p, 2 * ContinentPois.DISCOVER_XP[Math.max(0, Math.min(ContinentPois.DISCOVER_XP.length - 1, region))],
                "growth.skyabyss.reason.secret");
        p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.8f, 1.2f);
        if (found.size() == all().size()) {
            jp.revati.skyabyss.facility.Coins.give(p, ALL_COINS);
            Notice.unlock(p, "message.skyabyss.secret.all", ALL_COINS);
        }
        return true;
    }

    // 自己テスト用
    public static void forgetForTest(Player p) {
        persisted(p).remove(FOUND_TAG);
    }

    // ---------------------------------------------------------------- 見回り (1 秒ごと)
    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % 20 != 11 || !RoadClearance.live()) {
            return;
        }
        ServerLevel over = server.overworld();
        SiteData data = SiteData.get(over);
        boolean hintTick = server.getTickCount() % 60 == 11;
        for (ServerPlayer p : over.players()) {
            if (p.isSpectator() || !p.isAlive()) {
                continue;
            }
            Set<Integer> found = null;
            for (Secret s : all()) {
                double dx = p.getX() - (s.x() + 0.5);
                double dz = p.getZ() - (s.z() + 0.5);
                double d2 = dx * dx + dz * dz;
                if (d2 > (double) BUILD_RADIUS * BUILD_RADIUS) {
                    continue;
                }
                SiteData.Entry e = entry(data, s.id());
                if (e == null || !e.built) {
                    e = build(over, data, s, e);
                    if (e == null) {
                        continue;
                    }
                }
                if (found == null) {
                    found = found(p);
                }
                if (found.contains(s.id())) {
                    continue;
                }
                if (d2 <= FIND_RADIUS * FIND_RADIUS && Math.abs(p.getY() - e.pos.getY()) <= 6) {
                    discover(p, s);
                    found.add(s.id());
                } else if (hintTick && d2 <= (double) HINT_RADIUS * HINT_RADIUS) {
                    hint(p, e.pos);
                }
            }
        }
    }

    // かすかな鈴の音と光の粒 (その人にだけ)
    static void hint(ServerPlayer p, BlockPos at) {
        p.connection.send(new net.minecraft.network.protocol.game.ClientboundSoundPacket(net.minecraft.core.Holder.direct(SoundEvents.AMETHYST_BLOCK_CHIME), SoundSource.AMBIENT,
                at.getX() + 0.5, at.getY() + 1, at.getZ() + 0.5, 0.45f, 1.5f, p.getRandom().nextLong()));
        p.serverLevel().sendParticles(p, ParticleTypes.END_ROD, true, at.getX() + 0.5, at.getY() + 1.6, at.getZ() + 0.5, 3, 0.3, 0.4, 0.3, 0.01);
    }

    static SiteData.Entry entry(SiteData data, int id) {
        for (SiteData.Entry e : data.entries(KIND)) {
            if (e.index == id) {
                return e;
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- 建てる
    // 地面の上 (葉と草は除く) に形を置き、建った印を書く。チャンクがまだなら null
    static SiteData.Entry build(ServerLevel level, SiteData data, Secret s, SiteData.Entry e) {
        if (!level.hasChunkAt(new BlockPos(s.x(), 0, s.z()))) {
            return null;
        }
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, s.x(), s.z());
        BlockPos base = new BlockPos(s.x(), y, s.z());
        place(level, s.kind(), base, Direction.from2DDataValue(Math.floorMod(s.id() * 7 + s.x(), 4)));
        if (e == null) {
            e = data.add(KIND, s.id(), base, 0);
        } else {
            data.remove(e);
            e = data.add(KIND, s.id(), base, 0);
        }
        e.built = true;
        data.setDirty();
        return e;
    }

    private static void set(ServerLevel level, BlockPos p, BlockState s) {
        level.setBlock(p, s, Block.UPDATE_CLIENTS);
    }

    // 地面の一段を置き換える (草の上に石を敷くなど)
    private static void ground(ServerLevel level, BlockPos p, BlockState s) {
        set(level, p.below(), s);
        BlockState above = level.getBlockState(p);
        if (!above.isAir() && above.canBeReplaced()) {
            set(level, p, Blocks.AIR.defaultBlockState());
        }
    }

    // 形 (どれも 3x3 ほど。base = 地面の一つ上、facing = 正面)
    static void place(ServerLevel level, String kind, BlockPos base, Direction facing) {
        Direction side = facing.getClockWise();
        switch (kind) {
            case "moss_shrine" -> {
                // 苔の祠: 石の台、後ろに苔の石の壁と笠、小さな蝋燭
                for (int a = -1; a <= 1; a++) {
                    for (int b = -1; b <= 1; b++) {
                        ground(level, base.relative(side, a).relative(facing, b), (a + b) % 2 == 0 ? Blocks.MOSSY_COBBLESTONE.defaultBlockState() : Blocks.MOSS_BLOCK.defaultBlockState());
                    }
                }
                BlockPos back = base.relative(facing.getOpposite());
                for (int a = -1; a <= 1; a++) {
                    set(level, back.relative(side, a), Blocks.MOSSY_STONE_BRICKS.defaultBlockState());
                    set(level, back.relative(side, a).above(), a == 0 ? Blocks.CHISELED_STONE_BRICKS.defaultBlockState() : Blocks.MOSSY_STONE_BRICK_WALL.defaultBlockState());
                    set(level, back.relative(side, a).above(2), Blocks.MOSSY_STONE_BRICK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, facing));
                }
                set(level, base, Blocks.STONE_BRICK_SLAB.defaultBlockState());
                set(level, base.above(), Blocks.CANDLE.defaultBlockState().setValue(CandleBlock.CANDLES, 3).setValue(CandleBlock.LIT, true));
                set(level, base.relative(side, 1), Blocks.MOSS_CARPET.defaultBlockState());
                set(level, base.relative(side, -1), Blocks.FERN.defaultBlockState());
            }
            case "old_camp" -> {
                // 旅人の跡: 消えた焚き火、腰かけの丸太二本、荒れた土の輪
                for (int a = -2; a <= 2; a++) {
                    for (int b = -2; b <= 2; b++) {
                        if (a * a + b * b <= 5) {
                            ground(level, base.offset(a, 0, b), (a * 3 + b) % 3 == 0 ? Blocks.GRAVEL.defaultBlockState() : Blocks.COARSE_DIRT.defaultBlockState());
                        }
                    }
                }
                set(level, base, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, false));
                Direction.Axis along = side.getAxis();
                for (int a = -1; a <= 1; a++) {
                    set(level, base.relative(facing, 2).relative(side, a), Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, along));
                }
                set(level, base.relative(side, -2), Blocks.SPRUCE_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, facing.getAxis()));
                set(level, base.relative(side, 2), Blocks.DEAD_BUSH.defaultBlockState());
            }
            case "buried_head" -> {
                // 埋もれた頭: 半ば土に沈んだ石の顔 (額・目・鼻)
                for (int a = -1; a <= 1; a++) {
                    for (int h = -1; h <= 1; h++) {
                        set(level, base.relative(side, a).above(h), h == 1 && a != 0 ? Blocks.MOSSY_STONE_BRICKS.defaultBlockState()
                                : Blocks.POLISHED_ANDESITE.defaultBlockState());
                    }
                }
                BlockPos face = base.relative(facing);
                set(level, face.relative(side, -1), Blocks.ANDESITE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, facing.getOpposite())
                        .setValue(StairBlock.HALF, net.minecraft.world.level.block.state.properties.Half.TOP));
                set(level, face.relative(side, 1), Blocks.ANDESITE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, facing.getOpposite())
                        .setValue(StairBlock.HALF, net.minecraft.world.level.block.state.properties.Half.TOP));
                set(level, face.below(), Blocks.ANDESITE_WALL.defaultBlockState());
                set(level, base.above(2), Blocks.MOSS_CARPET.defaultBlockState());
            }
            case "bell_shard" -> {
                // 鐘の欠片: 草に落ちた古い鐘と、割れた銅の枠
                ground(level, base, Blocks.COARSE_DIRT.defaultBlockState());
                set(level, base, Blocks.BELL.defaultBlockState().setValue(BellBlock.ATTACHMENT, BellAttachType.FLOOR).setValue(BellBlock.FACING, facing));
                set(level, base.relative(side, 1), Blocks.WEATHERED_CUT_COPPER_SLAB.defaultBlockState());
                set(level, base.relative(side, -1), Blocks.OXIDIZED_CUT_COPPER_STAIRS.defaultBlockState().setValue(StairBlock.FACING, side));
                set(level, base.relative(facing.getOpposite()), Blocks.WEATHERED_COPPER.defaultBlockState());
                set(level, base.relative(facing.getOpposite()).above(), Blocks.LIGHTNING_ROD.defaultBlockState());
            }
            case "bone_tip" -> {
                // 巨神の骨の先: 地面から弧を描いて出る骨
                int[][] arc = {{0, 0}, {0, 1}, {1, 2}, {1, 3}, {2, 3}};
                for (int[] c : arc) {
                    set(level, base.relative(facing, c[0]).above(c[1]), Blocks.BONE_BLOCK.defaultBlockState());
                }
                set(level, base.relative(facing, -1), Blocks.CALCITE.defaultBlockState());
                set(level, base.relative(side, 1), Blocks.BONE_BLOCK.defaultBlockState().setValue(RotatedPillarBlock.AXIS, side.getAxis()));
                ground(level, base.relative(side, -1), Blocks.CALCITE.defaultBlockState());
            }
            case "shell_cairn" -> {
                // 貝の塚: 砂岩に積んだ海晶の石と、乾いた珊瑚
                ground(level, base, Blocks.SAND.defaultBlockState());
                set(level, base, Blocks.SMOOTH_SANDSTONE.defaultBlockState());
                set(level, base.above(), Blocks.PRISMARINE_BRICKS.defaultBlockState());
                set(level, base.above(2), Blocks.DEAD_BRAIN_CORAL_BLOCK.defaultBlockState());
                set(level, base.above(3), Blocks.SEA_PICKLE.defaultBlockState());
                set(level, base.relative(side, 1), Blocks.DEAD_FIRE_CORAL_FAN.defaultBlockState());
                set(level, base.relative(side, -1), Blocks.SANDSTONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM));
            }
            case "rod_stub" -> {
                // 避雷の残り: 錆びた銅の台に折れた避雷の棒
                for (int a = -1; a <= 1; a++) {
                    ground(level, base.relative(side, a), Blocks.OXIDIZED_CUT_COPPER.defaultBlockState());
                }
                set(level, base, Blocks.WEATHERED_COPPER.defaultBlockState());
                set(level, base.above(), Blocks.LIGHTNING_ROD.defaultBlockState());
                set(level, base.relative(side, 1), Blocks.EXPOSED_CUT_COPPER_SLAB.defaultBlockState());
                set(level, base.relative(facing), Blocks.LIGHTNING_ROD.defaultBlockState().setValue(net.minecraft.world.level.block.LightningRodBlock.FACING, side));
            }
            case "ash_idol" -> {
                // 灰の像: 黒い石の小さな像。目に金の筋
                set(level, base, Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState());
                set(level, base.above(), Blocks.GILDED_BLACKSTONE.defaultBlockState());
                set(level, base.above(2), Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, facing.getOpposite()));
                set(level, base.relative(facing), Blocks.SOUL_CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, false));
                ground(level, base.relative(side, 1), Blocks.BLACKSTONE.defaultBlockState());
                ground(level, base.relative(side, -1), Blocks.BASALT.defaultBlockState());
            }
            default -> set(level, base, Blocks.MOSSY_COBBLESTONE.defaultBlockState());
        }
    }
}
