package jp.revati.skyabyss.fishing;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.world.continent.ContinentChunkGenerator;
import jp.revati.skyabyss.world.continent.ContinentMap;
import jp.revati.skyabyss.world.continent.ContinentPois;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

// 釣り場 (docs/design/54 の 6.2.4.1。2026-11-05 の封の箱の頁 2-2)。土地ごとに二つ (都のまわり・岩鯨・潮殻・雷翼・余燼)。
//   置き場: その土地の見どころのうち、まわり (8〜28) に水 (地面が海の高さより低い) のあるものを番号の順に、互いに 250 以上離して二つ。
//           足りなければ土地の中の水辺を粗くさがす。所は見どころから一番近い水辺の岸 (大陸の作り直しは要らない。ContinentMap だけで決まる)
//   見つける: 人が岸の 24 以内に来たら、人ごとに覚え (FOUND)、知らせ、岸に立て札を立てる (無ければ)。地図に印 (MapMarks.Kind.FISHING)
//   効き目: 浮きが釣り場の 24 以内なら、ときどき・まれの魚が 2 倍 (Fish.roll の boost)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class FishingSpots {
    public static final int PER_LAND = 2;
    public static final double REACH = 24;
    static final int APART = 250;
    public static final String FOUND = "skyabyss_fish_spots";
    public static final int[] LANDS = {ContinentMap.MEADOW, ContinentMap.WHALE, ContinentMap.TIDE, ContinentMap.STORM, ContinentMap.EMBER};

    public record Spot(String id, int land, int x, int z, @Nullable String place) {
    }

    private static volatile List<Spot> spots;

    private FishingSpots() {
    }

    public static List<Spot> all() {
        List<Spot> s = spots;
        if (s == null) {
            synchronized (FishingSpots.class) {
                if (spots == null) {
                    spots = List.copyOf(build());
                }
                s = spots;
            }
        }
        return s;
    }

    static boolean water(ContinentMap map, int x, int z) {
        return map.inside(x, z) && map.surface(x, z) < ContinentChunkGenerator.SEA_LEVEL;
    }

    static int landOf(ContinentMap map, int x, int z) {
        return Fish.fishingRegion(map.inside(x, z) ? map.region(x, z) : ContinentMap.MEADOW);
    }

    // 見どころ (x, z) から一番近い岸 (陸で、隣が水)。なければ null
    @Nullable
    static int[] shore(ContinentMap map, int x, int z) {
        for (int r = 8; r <= 28; r += 2) {
            for (int a = 0; a < 16; a++) {
                double ang = a * Math.PI / 8;
                int wx = x + (int) Math.round(Math.cos(ang) * r);
                int wz = z + (int) Math.round(Math.sin(ang) * r);
                if (!water(map, wx, wz)) {
                    continue;
                }
                // 水から見どころの方へ戻って、最初の陸
                for (int b = 1; b <= 6; b++) {
                    int lx = x + (int) Math.round(Math.cos(ang) * (r - b));
                    int lz = z + (int) Math.round(Math.sin(ang) * (r - b));
                    if (!water(map, lx, lz)) {
                        return new int[] {lx, lz};
                    }
                }
            }
        }
        return null;
    }

    static List<Spot> build() {
        ContinentMap map = ContinentMap.get();
        List<Spot> out = new ArrayList<>();
        List<ContinentPois.Poi> pois = ContinentPois.all();
        for (int land : LANDS) {
            List<Spot> mine = new ArrayList<>();
            for (ContinentPois.Poi poi : pois) {
                if (mine.size() >= PER_LAND || poi.kind().equals("gone") || poi.kind().startsWith("dungeon_") || landOf(map, poi.x(), poi.z()) != land) {
                    continue;
                }
                int[] s = shore(map, poi.x(), poi.z());
                if (s != null && far(mine, s)) {
                    mine.add(new Spot("fish_" + land + "_" + mine.size(), land, s[0], s[1], poi.titleKey()));
                }
            }
            // 見どころで足りなければ、土地の中の岸を粗くさがす
            for (int gx = -2200; gx <= 2200 && mine.size() < PER_LAND; gx += 48) {
                for (int gz = -2200; gz <= 2200 && mine.size() < PER_LAND; gz += 48) {
                    if (water(map, gx, gz) || landOf(map, gx, gz) != land || !map.inside(gx, gz)) {
                        continue;
                    }
                    int[] s = shore(map, gx, gz);
                    if (s != null && far(mine, s)) {
                        mine.add(new Spot("fish_" + land + "_" + mine.size(), land, s[0], s[1], null));
                    }
                }
            }
            out.addAll(mine);
        }
        return out;
    }

    static boolean far(List<Spot> mine, int[] s) {
        for (Spot o : mine) {
            if (Math.hypot(o.x() - s[0], o.z() - s[1]) < APART) {
                return false;
            }
        }
        return true;
    }

    // 浮きの所が釣り場のそばか
    public static boolean near(int x, int z) {
        for (Spot s : all()) {
            if (Math.hypot(s.x() - x, s.z() - z) <= REACH) {
                return true;
            }
        }
        return false;
    }

    public static boolean found(ServerPlayer p, Spot s) {
        return ("," + p.getPersistentData().getString(FOUND) + ",").contains("," + s.id() + ",");
    }

    // 人が釣り場の近くに来たら見つける (2 秒ごと)
    @SubscribeEvent
    static void onTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || p.tickCount % 40 != 21 || p.level().dimension() != Level.OVERWORLD) {
            return;
        }
        for (Spot s : all()) {
            if (Math.hypot(s.x() - p.getX(), s.z() - p.getZ()) <= REACH && !found(p, s)) {
                find(p, s);
            }
        }
    }

    public static void find(ServerPlayer p, Spot s) {
        String seen = p.getPersistentData().getString(FOUND);
        p.getPersistentData().putString(FOUND, seen.isEmpty() ? s.id() : seen + "," + s.id());
        sign(p.serverLevel(), s);
        if (!(p instanceof net.neoforged.neoforge.common.util.FakePlayer)) {
            Notice.unlock(p, "message.skyabyss.fishing.spot_found",
                    s.place() == null ? Component.translatable("message.skyabyss.fishing.spot_shore") : Component.translatable(s.place()));
            p.playNotifySound(SoundEvents.FISHING_BOBBER_SPLASH, SoundSource.PLAYERS, 0.8f, 1.2f);
        }
    }

    // 岸に立て札 (無ければ。空気の所にだけ)
    static void sign(ServerLevel level, Spot s) {
        if (!level.hasChunk(s.x() >> 4, s.z() >> 4)) {
            return;
        }
        int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, s.x(), s.z());
        BlockPos at = new BlockPos(s.x(), y, s.z());
        if (level.getBlockState(at).getBlock() instanceof StandingSignBlock) {
            return;
        }
        if (!level.getBlockState(at).isAir() || !level.getBlockState(at.below()).isSolid()) {
            return;
        }
        level.setBlock(at, Blocks.SPRUCE_SIGN.defaultBlockState(), 3);
        if (level.getBlockEntity(at) instanceof SignBlockEntity sign) {
            SignText text = new SignText()
                    .setMessage(1, Component.translatable("sign.skyabyss.fishing_spot"))
                    .setMessage(2, Component.translatable("sign.skyabyss.fishing_spot.sub"));
            sign.setText(text, true);
            sign.setWaxed(true);
            sign.setChanged();
        }
    }
}
