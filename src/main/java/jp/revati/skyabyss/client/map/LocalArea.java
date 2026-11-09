package jp.revati.skyabyss.client.map;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.mojang.blaze3d.platform.NativeImage;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.facility.AttendantEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;

// 町の地図 (2026-11-08 の声「町内に入ったら町のマップも見れるようにしたい」): 地図を近くまで寄せたとき、読み込んである土地を上から描いた絵
// (バニラの地図と同じ色と陰) を、大陸の絵の上に重ねる。家・道・広場がそのまま見える。絵は地図を開いたときに一度だけ作る (自分のまわり R)。
// 施設の係の人 (AttendantEntity) は、役の名前つきの点として地図に載せる (TravelMapScreen.drawLocal)
public final class LocalArea {
    // 絵の半径 (ブロック)。この中の読み込んであるチャンクだけ描く
    public static final int R = 128;
    // これより寄せたときだけ重ねる (地図の半径)
    public static final double SHOW_BELOW = 260;
    // 町の中で地図を開いたときの寄せ方
    public static final double TOWN_VIEW = 110;
    static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "map/local_area");

    private static DynamicTexture texture;
    private static int originX;
    private static int originZ;

    private LocalArea() {
    }

    // 施設の係の人の名前と位置
    public record Keeper(double x, double z, net.minecraft.network.chat.Component name) {
    }

    // 町の中か (施設の係の人が近くにいる)
    public static boolean inTown(Minecraft mc) {
        return mc.player != null && mc.level != null && mc.level.dimension() == Level.OVERWORLD
                && !mc.level.getEntitiesOfClass(AttendantEntity.class, mc.player.getBoundingBox().inflate(72)).isEmpty();
    }

    // 自分のまわりの絵を作り直す (地図を開いたとき)
    public static void rebuild(Minecraft mc) {
        if (mc.player == null || mc.level == null || mc.level.dimension() != Level.OVERWORLD) {
            return;
        }
        Level level = mc.level;
        originX = mc.player.getBlockX() - R;
        originZ = mc.player.getBlockZ() - R;
        int size = R * 2;
        NativeImage img = new NativeImage(NativeImage.Format.RGBA, size, size, true);
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (int dz = 0; dz < size; dz++) {
            int prevH = Integer.MIN_VALUE;
            for (int dx = 0; dx < size; dx++) {
                img.setPixelRGBA(dx, dz, 0);
            }
            for (int dx = 0; dx < size; dx++) {
                int x = originX + dx;
                int z = originZ + dz;
                if (!level.hasChunk(x >> 4, z >> 4)) {
                    continue;
                }
                int h = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
                at.set(x, h, z);
                BlockState s = level.getBlockState(at);
                MapColor c = s.getMapColor(level, at);
                int down = 0;
                while (c == MapColor.NONE && down < 6 && h > level.getMinBuildHeight()) {
                    h--;
                    down++;
                    at.set(x, h, z);
                    s = level.getBlockState(at);
                    c = s.getMapColor(level, at);
                }
                if (c == MapColor.NONE) {
                    continue;
                }
                // 北 (一つ上の行) より高ければ明るく、低ければ暗く (バニラの地図と同じ陰の付け方)
                int north = level.hasChunk(x >> 4, (z - 1) >> 4) ? level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z - 1) - 1 : h;
                MapColor.Brightness b = h > north ? MapColor.Brightness.HIGH : h < north ? MapColor.Brightness.LOW : MapColor.Brightness.NORMAL;
                img.setPixelRGBA(dx, dz, c.calculateRGBColor(b));
            }
        }
        if (texture == null) {
            texture = new DynamicTexture(img);
            mc.getTextureManager().register(ID, texture);
        } else {
            texture.setPixels(img);
            texture.upload();
        }
    }

    @Nullable
    public static ResourceLocation id() {
        return texture == null ? null : ID;
    }

    public static int originX() {
        return originX;
    }

    public static int originZ() {
        return originZ;
    }

    public static List<Keeper> keepers(Minecraft mc) {
        List<Keeper> out = new ArrayList<>();
        if (mc.player == null || mc.level == null) {
            return out;
        }
        for (AttendantEntity a : mc.level.getEntitiesOfClass(AttendantEntity.class, mc.player.getBoundingBox().inflate(R))) {
            out.add(new Keeper(a.getX(), a.getZ(), a.getDisplayName()));
        }
        return out;
    }
}
