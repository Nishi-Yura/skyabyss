package jp.revati.skyabyss.rpg.skill;

import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.rebirth.RebirthView;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

// 変異 (A08)・紋の盤 (A14)・転生の回数の取り込み (B08・B09) の、サーバー側の操作。検めはすべてここ (クライアントは頼むだけ)
public final class SkillExtOps {
    private SkillExtOps() {
    }

    // ---------------------------------------------------------------- 転生の回数
    // 転生の回数を記録へ写す (回数が下がったとき、足りなくなった淵・前世の技を戻す)。変わったら true
    public static boolean refreshRebirth(ServerPlayer player) {
        SkillData d = SkillManager.get(player);
        boolean lost = absorbRebirth(player);
        SkillManager.sync(player);
        if (lost) {
            SkillManager.applyModifiers(player);
        }
        return lost;
    }

    // 記録の回数を今の転生の回数へ合わせる (SkillManager.sync が毎回呼ぶ。下がったら足りない技を戻す)。技を戻したら true
    static boolean absorbRebirth(Player player) {
        SkillData d = SkillManager.get(player);
        int count = RebirthView.count(player);
        if (d.rebirth == count) {
            return false;
        }
        d.rebirth = count;
        return d.enforceRebirth();
    }

    // ---------------------------------------------------------------- 変異 (48 で技の木の節に。素材は節を取るときに一度だけ払う。SkillManager.takeNode)
    // 技が今変異して効いているか (極めていて、職の木ならその節を取っているあいだ)
    public static Mutation active(Player player, Skill skill) {
        SkillData d = SkillManager.get(player);
        String id = d.mutations.get(skill.id);
        Mutation m = id == null ? null : Mutation.byId(id);
        if (m == null || m.skill != skill || d.rank(skill) < skill.maxRank) {
            return null;
        }
        jp.revati.skyabyss.rpg.skill.tree.AbilityTree tree = jp.revati.skyabyss.rpg.skill.tree.AbilityTrees.of(skill.tree);
        if (tree != null) {
            jp.revati.skyabyss.rpg.skill.tree.AbilityNode node = tree.mutationNode(skill);
            if (node != null && !d.hasNode(skill.tree, node.id)) {
                return null;
            }
        }
        return m;
    }

    // 素材は留め袋と持ち物の両方を数える (炉と同じ。拾った素材は袋へ自動で入るので、持ち物だけでは足りないと見えてしまう)
    public static int count(Player player, Item item) {
        return jp.revati.skyabyss.pouch.Pouch.countAll(player, item);
    }

    public static boolean hasMaterial(Player player, Mutation m) {
        return count(player, m.coreItem()) >= Mutation.CORES && count(player, m.shardItem()) >= Mutation.SHARDS;
    }

    // 留め袋から先に、足りなければ持ち物から (炉の交換と同じ)
    private static void take(Player player, Item item, int amount) {
        jp.revati.skyabyss.pouch.Pouch.takeAny(player, item, amount);
    }

    // 変異の素材を払って、その技の変異を記録に刻む (木の節を取るとき。一度払えば振り直しても残る)
    public static void payMutation(ServerPlayer player, Mutation m) {
        take(player, m.coreItem(), Mutation.CORES);
        take(player, m.shardItem(), Mutation.SHARDS);
        SkillData d = SkillManager.get(player);
        d.mutations.put(m.skill.id, m.id);
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(), Skills.SOUND_LEARN.get(), SoundSource.PLAYERS, 1.0f, 0.6f);
        player.serverLevel().sendParticles(Skills.RUNE_SPARK.get(), player.getX(), player.getY() + 1.0, player.getZ(), 30, 0.5, 0.8, 0.5, 0.06);
        Notice.send(player, Notice.Kind.LEVEL_UP, ModText.lore("message.skyabyss.skill.mutated", Component.translatable(m.skill.nameKey()), Component.translatable(m.nameKey())));
    }

    private static void done(ServerPlayer player) {
        SkillManager.sync(player);
        SkillManager.applyModifiers(player);
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(), Skills.SOUND_LEARN.get(), SoundSource.PLAYERS, 0.6f, 1.5f);
    }
}
