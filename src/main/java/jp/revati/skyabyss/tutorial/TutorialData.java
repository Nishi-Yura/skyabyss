package jp.revati.skyabyss.tutorial;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.world.entity.player.Player;

// 初回の手引きの人ごとの記録 (Data Attachment skyabyss:tutorial。プレイヤーのデータに保存し、死んでも・再ログインしても残る)。
//   active : 出ている (始まって、まだ済んでいない) 手引きの ID
//   done   : 済んだ手引きの ID (二度と出ない)
public final class TutorialData {
    public static final Codec<TutorialData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.listOf().optionalFieldOf("active", List.of()).forGetter(d -> List.copyOf(d.active)),
            Codec.STRING.listOf().optionalFieldOf("done", List.of()).forGetter(d -> List.copyOf(d.done))).apply(i, TutorialData::new));

    private final Set<String> active = new LinkedHashSet<>();
    private final Set<String> done = new LinkedHashSet<>();

    public TutorialData() {
    }

    private TutorialData(List<String> active, List<String> done) {
        this.done.addAll(done);
        for (String a : active) {
            if (!this.done.contains(a)) {
                this.active.add(a);
            }
        }
    }

    public static TutorialData of(Player player) {
        return player.getData(TutorialSetup.STATE);
    }

    public boolean isDone(String id) {
        return done.contains(id);
    }

    public boolean isActive(String id) {
        return active.contains(id);
    }

    // 始める。初めてなら true (済んでいる・すでに出ているなら何もしない)
    boolean start(String id) {
        return !done.contains(id) && active.add(id);
    }

    // 済ませる。出ていたなら true。出ていなくても「済み」にする
    boolean finish(String id) {
        boolean was = active.remove(id);
        done.add(id);
        return was;
    }

    public List<String> active() {
        return List.copyOf(active);
    }

    public List<String> done() {
        return List.copyOf(done);
    }
}
