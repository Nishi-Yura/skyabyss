package jp.revati.skyabyss.titan.travel;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.loot.TitanLoot;
import jp.revati.skyabyss.titan.StoneWhaleEntity;
import jp.revati.skyabyss.titan.TitanTravel;
import jp.revati.skyabyss.titan.TitanWorldData;
import jp.revati.skyabyss.titan.WhaleKeeper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.Vec3;

// 巨神での移動に使える巨神の一覧。巨神ごとに、心臓の記録 (解放の条件)、今の個体の探し方、呼び寄せ方、呼び笛を持つ。
// 後の巨神 (潮殻・雷翼・余燼) は、TravelTitan を実装した巨神と一緒にここへ一行足す
public record TitanKind(
        // 巨神の名前 (保存と通信に使う。巨神の記憶 TitanMemoryData のダンジョン名と同じ)
        String id,
        // 表示名の翻訳キー
        String nameKey,
        // 今の本物の個体 (読み込まれているときだけ)
        Function<ServerLevel, TravelTitan> current,
        // 読み込まれていない・まだ現れていない巨神を、この位置 (y は高さ) へ呼び寄せる。一体だけの決まりは巨神の見守りが守る
        Respawner respawn,
        // 背中で乗り降りしている人がいる (この間は呼べない)
        Supplier<Boolean> busyAboard,
        // 呼び笛
        Supplier<Item> callItem) {

    @FunctionalInterface
    public interface Respawner {
        TravelTitan spawn(ServerLevel overworld, double x, double y, double z);
    }

    private static final Map<String, TitanKind> KINDS = new LinkedHashMap<>();

    public static final TitanKind STONE_WHALE = register(new TitanKind(TitanLoot.STONE_WHALE, "entity.skyabyss.stone_whale",
            WhaleKeeper::current,
            (level, x, y, z) -> {
                // 読み込まれていない前の個体は、次に読み込まれたときに取り消される (WhaleKeeper の一体だけの決まり)
                CompoundTag state = TitanWorldData.get(level).lastState();
                StoneWhaleEntity whale = WhaleKeeper.spawn(level, x, z, state);
                whale.setPos(x, y, z);
                return whale;
            },
            TitanTravel::isLifting,
            () -> TravelSetup.STONE_WHALE_CALL.get()));

    private static TitanKind register(TitanKind kind) {
        KINDS.put(kind.id(), kind);
        return kind;
    }

    @Nullable
    public static TitanKind byId(String id) {
        return KINDS.get(id);
    }

    public static Iterable<TitanKind> all() {
        return KINDS.values();
    }

    @Nullable
    public TravelTitan find(ServerLevel overworld) {
        return current.apply(overworld);
    }

    // 巨神の中心を、乗り場の鎖の垂れ口が point の真上に来る位置へ (向き yaw のとき)
    public static Vec3 centerForChainAt(TravelTitan titan, Vec3 point, float yaw, double centerY) {
        Vec3 off = titan.boardingChainOffset(yaw);
        return new Vec3(point.x - off.x, centerY, point.z - off.z);
    }
}
