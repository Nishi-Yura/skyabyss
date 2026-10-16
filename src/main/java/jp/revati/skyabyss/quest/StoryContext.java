package jp.revati.skyabyss.quest;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.npc.NpcEntity;
import net.minecraft.server.level.ServerPlayer;

// 条件と結果を評価するときの場面。会話の中なら相手の NPC (役の ID と実体) が入る。
// クエストの見回りから呼ぶときは npcRole も npc も null
public record StoryContext(ServerPlayer player, @Nullable String npcRole, @Nullable NpcEntity npc) {
    public static StoryContext of(ServerPlayer player) {
        return new StoryContext(player, null, null);
    }

    // 条件や結果で npc を省いたときは、話している相手の役を使う
    @Nullable
    public String role(@Nullable String explicit) {
        return explicit != null ? explicit : npcRole;
    }
}
