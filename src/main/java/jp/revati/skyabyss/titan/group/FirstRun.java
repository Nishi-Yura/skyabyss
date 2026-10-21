package jp.revati.skyabyss.titan.group;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;

import javax.annotation.Nullable;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.cinematic.Cinematics;
import jp.revati.skyabyss.cinematic.Scene;
import jp.revati.skyabyss.cinematic.Scenes;
import jp.revati.skyabyss.item.ModGear;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.phys.Vec3;

// 初回と周回の共通の部品 (docs/design/21 の 1.7、10 の 10.10)。
//
//  初回の定義   : そのグループの記録 (BackGroupData.Record) の `keep.first_cleared` が立っていない。心臓を止めた瞬間に立つ
//                 (TitanEnd.onHeartStopped → onFirstCleared)。"keep." の欄なので試練のリセットで消えず、パーティの記録の引き継ぎでは記録ごと写る
//  場面         : registerScene で場面を足し、playFirstScene で出す。人ごと (`cine_seen.<名前>`)。見ていない人だけに出る (スキップできる)。
//                 グループが周回でも、初めての人には出る (道はグループ、場面と宝は人ごと)
//  追憶の宝     : grantKeepsake(人, id, 品)。人ごとに一度きり。備えの品・素材・小さな装飾だけ (巨神の装備は渡さない)
//  素材の報酬   : grantMaterials(人, 品)。何度でも
//  既知の道     : 版 30 で廃止。部屋の登録・戸の開け直し・光る床・静かな通路は持たない (周回でも初回と同じ部屋になる)
public final class FirstRun {
    // 人ごとの場面の記録は CinematicState (プレイヤーの Data Attachment) に、この頭をつけて持つ
    public static final String SEEN_PREFIX = "cine_seen.";

    private FirstRun() {
    }

    // ---------------------------------------------------------------- 初回か周回か

    // そのグループの、この背中での初回か (心臓を一度も止めていない)
    public static boolean isFirstRun(ServerLevel back, UUID group) {
        BackGroupData.Record r = BackGroupData.get(back).peek(group);
        return r == null || !r.isFirstCleared();
    }

    public static boolean isFirstRun(ServerPlayer player) {
        return player.level() instanceof ServerLevel level && isFirstRun(level, Groups.keyOf(player));
    }

    // そのグループが、巨神 titan (null ならどれでも) の心臓を一度でも止めたか。地上の語り部などが周回の言い回しを選ぶのに使う
    public static boolean hasCleared(MinecraftServer server, UUID group, @Nullable String titan) {
        for (String t : jp.revati.skyabyss.progress.Titans.all()) {
            if (titan != null && !titan.equals(t)) {
                continue;
            }
            var dim = jp.revati.skyabyss.progress.Titans.backOf(t);
            ServerLevel back = dim == null ? null : server.getLevel(dim);
            if (back != null && !isFirstRun(back, group)) {
                return true;
            }
        }
        return false;
    }

    // 心臓を止めた: 戦った人 (と同じパーティ) のグループに first_cleared を立てる。TitanEnd.onHeartStopped から
    public static void onFirstCleared(ServerLevel back, Collection<UUID> fighters) {
        MinecraftServer server = back.getServer();
        BackGroupData data = BackGroupData.get(back);
        for (UUID id : fighters) {
            UUID group = Groups.keyOf(server, id);
            BackGroupData.Record r = data.of(group);
            if (!r.isFirstCleared()) {
                r.markFirstCleared();
                SkyAbyss.LOGGER.info("Group {} finished its first run of {}", group, back.dimension().location());
                GroupOverlays.refresh(back, group);
            }
        }
    }

    // ---------------------------------------------------------------- 場面

    public static String seenKey(String name) {
        return SEEN_PREFIX + name;
    }

    // 初回の場面を足す (場面の定義は Scenes と同じ。名前 = Scene の id)。同じ名前は置き換える
    public static void registerScene(Scene scene) {
        Scenes.register(scene);
    }

    // この人がその場面を見たか (`cine_seen.<名前>`)
    public static boolean sceneSeen(ServerPlayer player, String name) {
        return Cinematics.state(player).seen(seenKey(name));
    }

    // 初回の場面を出す枠。見ていない人にだけ出す (戻りは出したか)。場面が未登録なら何もしない (見たことにもしない)。
    // 出した時点で `cine_seen.<名前>` を記録する (途中で落ちても繰り返さない)。スキップは場面の仕組みのとおり
    public static boolean playFirstScene(ServerPlayer player, String name, @Nullable Entity target, @Nullable Vec3 faceToward) {
        if (Scenes.get(name) == null || sceneSeen(player, name)) {
            return false;
        }
        if (!Cinematics.start(player, name, target, faceToward)) {
            return false;
        }
        Cinematics.state(player).markSeen(seenKey(name));
        return true;
    }

    // 場面が終わったとき (スキップを含む) の効果を足す (Cinematics.onEnd と同じ)
    public static void onSceneEnd(String name, BiConsumer<ServerPlayer, Boolean> listener) {
        Cinematics.onEnd(name, listener);
    }

    // ---------------------------------------------------------------- 宝

    // 追憶の宝に入れてよい品か: 巨神の装備 (ModGear・武器・防具) は入れない。備えの品・素材・小さな装飾だけ
    public static boolean keepsakeAllowed(ItemStack stack) {
        var item = stack.getItem();
        return !stack.isEmpty() && !(item instanceof ModGear) && !(item instanceof TieredItem) && !(item instanceof ArmorItem);
    }

    public static boolean hasKeepsake(ServerPlayer player, String id) {
        return FirstRunData.get(player.server).hasKeepsake(player.getUUID(), id);
    }

    // 追憶の宝を渡す。その人が初めてなら渡して true (人ごとに一度きり。グループの初回・周回には関わらない)。
    // 受け取り済み・装備が混ざっているときは何も渡さず false
    public static boolean grantKeepsake(ServerPlayer player, String id, List<ItemStack> stacks) {
        for (ItemStack s : stacks) {
            if (!keepsakeAllowed(s)) {
                SkyAbyss.LOGGER.error("Keepsake {} refused: {} is gear or empty", id, s);
                return false;
            }
        }
        if (!FirstRunData.get(player.server).markKeepsake(player.getUUID(), id)) {
            return false;
        }
        give(player, stacks);
        return true;
    }

    // 素材の報酬。何度でも渡す
    public static void grantMaterials(ServerPlayer player, List<ItemStack> stacks) {
        give(player, stacks);
    }

    private static void give(ServerPlayer player, List<ItemStack> stacks) {
        for (ItemStack s : stacks) {
            jp.revati.skyabyss.pouch.PouchRouting.placeBack(player, s.copy());
        }
    }
}
