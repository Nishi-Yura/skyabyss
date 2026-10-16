package jp.revati.skyabyss.titan.travel;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.loot.TitanLoot;
import jp.revati.skyabyss.titan.StoneWhaleEntity;
import jp.revati.skyabyss.titan.TitanTravel;
import jp.revati.skyabyss.titan.TitanWorldData;
import jp.revati.skyabyss.titan.WhaleKeeper;
import jp.revati.skyabyss.titan.tide.TideKeeper;
import jp.revati.skyabyss.titan.tide.TideShellEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.Vec3;

// 巨神での移動に使える巨神の一覧。巨神ごとに、心臓の記録 (解放の条件)、今の個体の探し方、呼び寄せ方、呼び笛、旅の型を持つ。
// 後の巨神 (雷翼・余燼) は、TravelTitan を実装した巨神と一緒にここへ一行足す (09 の 11 章・16 章)。
//   旅の型 (Style): SKY は雲の上を泳ぐ (岩鯨)、SEA は海と浅瀬を歩いて浜で降ろす (潮殻)。
//   雷翼は SKY のまま数値を変える、余燼は新しい型を足して Voyages.newVoyage に一行、という広げ方を想定している
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
        Supplier<Item> callItem,
        // 旅の型
        Style style,
        // 呼んだとき・出発・帳の音
        Supplier<SoundEvent> callSound,
        Supplier<SoundEvent> departSound,
        Supplier<SoundEvent> veilSound) {

    public enum Style {
        // 雲の上を泳ぐ。遠い旅は雲の帳で縮める (岩鯨)
        SKY,
        // 海と浅瀬を歩く。陸の行き先には、いちばん近い浜で降ろす。遠い旅は潮の深みで縮める (潮殻)
        SEA
    }

    @FunctionalInterface
    public interface Respawner {
        TravelTitan spawn(ServerLevel overworld, double x, double y, double z);
    }

    // 巨神ごとに言い回しを変える文 (message.skyabyss.travel.<名前>.<鍵>)。岩鯨は元の文 (名前なし)
    private static final Set<String> VOICED = Set.of("called", "arrived_pickup", "recognized", "recognized_hint", "veil", "arrived", "climbing",
            "seated_leader", "seated", "chain_hint", "woke_landed", "disembark", "lowering", "no_dismount", "underway",
            "refuse.not_recognized", "refuse.wrong_dimension", "refuse.indoors", "refuse.underwater", "refuse.in_combat", "refuse.riding", "refuse.cooldown",
            "cancel.disembark", "cancel.gone", "cancel.waited", "cancel.empty", "lost", "busy", "join", "already_coming");

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
            () -> TravelSetup.STONE_WHALE_CALL.get(), Style.SKY,
            () -> TravelSetup.CALL_SOUND.get(), () -> TravelSetup.DEPART_SOUND.get(), () -> TravelSetup.VEIL_SOUND.get()));

    // 潮殻の巨神: 海辺で呼び貝を吹くと、浅瀬へ寄って潮門の前に泡の柱を立てる。乗ると甲羅の頂の座へ運ばれ、海を渡る
    public static final TitanKind TIDE_SHELL = register(new TitanKind(TideKeeper.SLOT, "entity.skyabyss.tide_shell",
            level -> TideKeeper.KEEPER.current(level),
            (level, x, y, z) -> {
                CompoundTag state = TideKeeper.KEEPER.slot(level).state;
                TideShellEntity shell = TideKeeper.KEEPER.spawn(level, x, z, state == null || state.isEmpty() ? null : state);
                shell.setPos(x, y, z);
                return shell;
            },
            () -> false,
            () -> TravelSetup.TIDE_SHELL_CALL.get(), Style.SEA,
            () -> TravelSetup.TIDE_CALL_SOUND.get(), () -> TravelSetup.TIDE_DEPART_SOUND.get(), () -> TravelSetup.TIDE_CURRENT_SOUND.get()));

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

    // 巨神ごとの言い回しの鍵 (例: message.skyabyss.travel.called → message.skyabyss.travel.tide_shell.called)
    public String text(String key) {
        if (this == STONE_WHALE) {
            return key;
        }
        String prefix = "message.skyabyss.travel.";
        if (key.startsWith(prefix) && VOICED.contains(key.substring(prefix.length()))) {
            return prefix + id + "." + key.substring(prefix.length());
        }
        String title = "title.skyabyss.travel.";
        if (key.startsWith(title)) {
            return title + id + "." + key.substring(title.length());
        }
        return key;
    }

    // 巨神の中心を、乗り場の鎖の垂れ口が point の真上に来る位置へ (向き yaw のとき)
    public static Vec3 centerForChainAt(TravelTitan titan, Vec3 point, float yaw, double centerY) {
        Vec3 off = titan.boardingChainOffset(yaw);
        return new Vec3(point.x - off.x, centerY, point.z - off.z);
    }
}
