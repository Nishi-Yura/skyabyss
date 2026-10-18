package jp.revati.skyabyss.cinematic;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.mojang.serialization.Codec;

// プレイヤーごとに、見終えた (始めた) 場面の記録 (Data Attachment skyabyss:cinematic。死んでも引き継ぐ)。一度きりの場面は二度と出さない
public final class CinematicState {
    public static final Codec<CinematicState> CODEC = Codec.STRING.listOf().xmap(CinematicState::new, CinematicState::seenList);

    private final Set<String> seen = new LinkedHashSet<>();

    public CinematicState() {
    }

    private CinematicState(List<String> ids) {
        seen.addAll(ids);
    }

    private List<String> seenList() {
        return List.copyOf(seen);
    }

    public boolean seen(String scene) {
        return seen.contains(scene);
    }

    public void markSeen(String scene) {
        seen.add(scene);
    }

    public void forget(String scene) {
        seen.remove(scene);
    }

    public void forgetAll() {
        seen.clear();
    }

    public Set<String> seenSet() {
        return Set.copyOf(seen);
    }
}
