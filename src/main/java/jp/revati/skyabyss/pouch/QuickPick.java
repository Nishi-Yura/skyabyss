package jp.revati.skyabyss.pouch;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.minecraft.resources.ResourceLocation;

// 一枚札 (ホットバーの左の「短押しで使う道具」) の選び方。画面に依らない純粋な関数 (自己テストが直接呼ぶ)。
//   順: 置いた札 (pin) > 巨神の乗り場・巨神のそば (呼び笛・呼び貝) > 海の上 (潮見の貝) > 嵐 (風見鶏) > 前回の道具 > 使える品の先頭
public final class QuickPick {
    private QuickPick() {
    }

    public enum Role {
        // 呼び笛・呼び貝
        CALL,
        // 潮見の貝
        SEA,
        // 風見鶏
        STORM,
        OTHER
    }

    // kind: 呼び笛・呼び貝の巨神の名前 (TitanKind.id)。それ以外は null
    public record Cand(ResourceLocation id, Role role, @Nullable String kind) {
    }

    // nearTitanKind: 近くにいる巨神の名前 (いなければ null)。nearWaymark: 乗り場の標の近く
    public record Ctx(@Nullable String nearTitanKind, boolean nearWaymark, boolean onSea, boolean storm) {
        public static final Ctx NONE = new Ctx(null, false, false, false);
    }

    @Nullable
    public static ResourceLocation pick(List<Cand> usable, Ctx ctx, @Nullable ResourceLocation pinned, @Nullable ResourceLocation last) {
        if (usable.isEmpty()) {
            return null;
        }
        if (pinned != null && has(usable, pinned)) {
            return pinned;
        }
        if (ctx.nearTitanKind() != null || ctx.nearWaymark()) {
            if (ctx.nearTitanKind() != null) {
                for (Cand c : usable) {
                    if (c.role() == Role.CALL && ctx.nearTitanKind().equals(c.kind())) {
                        return c.id();
                    }
                }
            }
            // 巨神が決まらない (乗り場だけ) ときは、前回の笛・貝があればそれ、なければ先頭
            Cand first = null;
            for (Cand c : usable) {
                if (c.role() == Role.CALL) {
                    if (c.id().equals(last)) {
                        return c.id();
                    }
                    if (first == null) {
                        first = c;
                    }
                }
            }
            if (first != null) {
                return first.id();
            }
        }
        if (ctx.onSea()) {
            ResourceLocation r = ofRole(usable, Role.SEA);
            if (r != null) {
                return r;
            }
        }
        if (ctx.storm()) {
            ResourceLocation r = ofRole(usable, Role.STORM);
            if (r != null) {
                return r;
            }
        }
        if (last != null && has(usable, last)) {
            return last;
        }
        return usable.get(0).id();
    }

    private static boolean has(List<Cand> usable, ResourceLocation id) {
        for (Cand c : usable) {
            if (c.id().equals(id)) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    private static ResourceLocation ofRole(List<Cand> usable, Role role) {
        for (Cand c : usable) {
            if (c.role() == role) {
                return c.id();
            }
        }
        return null;
    }
}
