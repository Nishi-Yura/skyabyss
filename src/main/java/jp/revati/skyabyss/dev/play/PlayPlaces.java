package jp.revati.skyabyss.dev.play;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.playtest.Playtest;
import jp.revati.skyabyss.story.ch5.AbyssWorld;
import jp.revati.skyabyss.story.ch5.AbyssBuild;
import jp.revati.skyabyss.titan.dungeon.TitanBackTrials;
import jp.revati.skyabyss.titan.ember.back.EmberBackLayout;
import jp.revati.skyabyss.titan.storm.back.StormBackLayout;
import jp.revati.skyabyss.titan.tide.back.TideBackLayout;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

// 試遊の「移動先」の名前と、その場所。名前: overworld / whale|tide|storm|ember (背の入口) /
// <巨神>-<地点> (背の中の休み場・心臓・中央) / abyss / abyss-<印> (arrival・rest_1..3・heart) / abyss-depth-<n>
// docs/design/37 の 3 節。解決できない場所は理由つきで飛ばす (建っていない・立てない場所)
public final class PlayPlaces {
    private PlayPlaces() {
    }

    // 立てる場所 (足の下が固く、体の高さに空きがある)。解決の結果
    public record Spot(ServerLevel level, BlockPos at, float yaw) {
    }

    // 候補に出す名前。背の地点は Playtest.spots (背の中の休み場と心臓・中央)
    public static List<String> names() {
        List<String> out = new ArrayList<>();
        out.add("overworld");
        for (String t : Playtest.TITANS) {
            out.add(t);
            for (String s : Playtest.spots(Playtest.index(t))) {
                out.add(t + "-" + s);
            }
        }
        out.add("abyss");
        for (String m : List.of("arrival", "rest_1", "rest_2", "rest_3", "heart")) {
            out.add("abyss-" + m);
        }
        for (int n : new int[] {1, 5, 10}) {
            out.add("abyss-depth-" + n);
        }
        return out;
    }

    // 移動する。成功 (呼び出しを始めた・移した) なら null、だめなら翻訳キー
    @Nullable
    public static String go(ServerPlayer p, String name) {
        if (name.equals("overworld")) {
            Playtest.toOverworld(p);
            return null;
        }
        if (Playtest.index(name) >= 0) {
            Playtest.board(p, Playtest.index(name), pp -> {
            });
            return null;
        }
        if (name.equals("abyss")) {
            return Playtest.run(p, "skyabyss abyss enter") > 0 ? null : "commands.skyabyss_play.place_missing";
        }
        if (name.startsWith("abyss-depth-")) {
            String n = name.substring("abyss-depth-".length());
            return Playtest.run(p, "skyabyss abyss depth go " + n) > 0 ? null : "commands.skyabyss_play.place_missing";
        }
        if (name.startsWith("abyss-")) {
            Spot s = spot(p.server, name);
            if (s == null) {
                return "commands.skyabyss_play.place_missing";
            }
            p.fallDistance = 0;
            p.teleportTo(s.level(), s.at().getX() + 0.5, s.at().getY(), s.at().getZ() + 0.5, s.yaw(), 0f);
            return null;
        }
        int dash = name.indexOf('-');
        if (dash < 0 || Playtest.index(name.substring(0, dash)) < 0) {
            return "commands.skyabyss_play.unknown_place";
        }
        int idx = Playtest.index(name.substring(0, dash));
        String spot = name.substring(dash + 1);
        if (!Playtest.spots(idx).contains(spot)) {
            return "commands.skyabyss_play.unknown_place";
        }
        // 背に乗せてから地点へ (乗るのは巨神の呼び出しを待つことがある)
        if (spot(p.server, name) == null) {
            return "commands.skyabyss_play.place_missing";
        }
        Playtest.board(p, idx, pp -> Playtest.gotoSpot(pp, idx, spot));
        return null;
    }

    // 名前の場所を解決する。立てない・まだ建っていない場所は null (自己テストが理由を見る)
    @Nullable
    public static Spot spot(MinecraftServer server, String name) {
        if (name.startsWith("abyss-") && !name.startsWith("abyss-depth-")) {
            String mark = name.substring("abyss-".length());
            ServerLevel level = server.getLevel(AbyssWorld.LEVEL);
            BlockPos at = AbyssBuild.mark(mark);
            if (level == null || at == null || !AbyssBuild.built(server)) {
                return null;
            }
            return standable(level, at) ? new Spot(level, at, 0f) : null;
        }
        int dash = name.indexOf('-');
        if (dash < 0) {
            return null;
        }
        int idx = Playtest.index(name.substring(0, dash));
        if (idx < 0) {
            return null;
        }
        String spot = name.substring(dash + 1);
        ServerLevel level = server.getLevel(Playtest.back(idx));
        if (level == null) {
            return null;
        }
        BlockPos at = null;
        float yaw = 0f;
        switch (idx) {
            case 0 -> {
                at = TitanBackTrials.checkpointSpawns().get(spot);
                if (at != null) {
                    at = TitanBackTrials.safeSpot(level, at);
                }
            }
            case 1 -> {
                for (var r : TideBackLayout.RESTS) {
                    if (r.id().equals(spot)) {
                        at = r.spawn();
                    }
                }
            }
            case 2 -> at = StormBackLayout.REST.get(spot);
            default -> {
                for (var r : EmberBackLayout.RESTS) {
                    if (r.name().equals(spot)) {
                        at = r.floor().above();
                        yaw = r.yaw();
                    }
                }
            }
        }
        if (at == null || !standable(level, at)) {
            return null;
        }
        return new Spot(level, at, yaw);
    }

    // 立てる: 足もとが固く、体の高さ (2 ブロック) に当たるものがない
    public static boolean standable(ServerLevel level, BlockPos at) {
        BlockPos below = at.below();
        return level.getBlockState(below).isSolid()
                && level.getBlockState(at).getCollisionShape(level, at).isEmpty()
                && level.getBlockState(at.above()).getCollisionShape(level, at.above()).isEmpty();
    }
}
