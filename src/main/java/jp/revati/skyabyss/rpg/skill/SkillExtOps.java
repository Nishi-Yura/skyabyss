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

    // ---------------------------------------------------------------- 変異
    public enum MutateResult {
        OK, UNKNOWN, NOT_MASTERED, ALREADY, NO_MATERIAL
    }

    // 技が今変異して効いているか (極めているあいだだけ)
    public static Mutation active(Player player, Skill skill) {
        SkillData d = SkillManager.get(player);
        String id = d.mutations.get(skill.id);
        Mutation m = id == null ? null : Mutation.byId(id);
        return m != null && m.skill == skill && d.rank(skill) >= skill.maxRank ? m : null;
    }

    public static int count(Player player, Item item) {
        int n = 0;
        for (ItemStack s : player.getInventory().items) {
            if (s.is(item)) {
                n += s.getCount();
            }
        }
        return n;
    }

    public static boolean hasMaterial(Player player, Mutation m) {
        return count(player, m.coreItem()) >= Mutation.CORES && count(player, m.shardItem()) >= Mutation.SHARDS;
    }

    private static void take(Player player, Item item, int amount) {
        int left = amount;
        for (ItemStack s : player.getInventory().items) {
            if (left > 0 && s.is(item)) {
                int n = Math.min(left, s.getCount());
                s.shrink(n);
                left -= n;
            }
        }
    }

    public static MutateResult mutate(ServerPlayer player, Skill skill) {
        Mutation m = skill == null ? null : Mutation.of(skill);
        if (m == null) {
            return MutateResult.UNKNOWN;
        }
        SkillData d = SkillManager.get(player);
        if (!skill.active || d.rank(skill) < skill.maxRank) {
            return MutateResult.NOT_MASTERED;
        }
        if (d.mutations.containsKey(skill.id)) {
            return MutateResult.ALREADY;
        }
        if (!hasMaterial(player, m)) {
            return MutateResult.NO_MATERIAL;
        }
        take(player, m.coreItem(), Mutation.CORES);
        take(player, m.shardItem(), Mutation.SHARDS);
        d.mutations.put(skill.id, m.id);
        SkillManager.sync(player);
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(), Skills.SOUND_LEARN.get(), SoundSource.PLAYERS, 1.0f, 0.6f);
        player.serverLevel().sendParticles(Skills.RUNE_SPARK.get(), player.getX(), player.getY() + 1.0, player.getZ(), 30, 0.5, 0.8, 0.5, 0.06);
        Notice.send(player, Notice.Kind.LEVEL_UP, ModText.lore("message.skyabyss.skill.mutated", Component.translatable(skill.nameKey()), Component.translatable(m.nameKey())));
        return MutateResult.OK;
    }

    // ---------------------------------------------------------------- 紋
    public enum GlyphResult {
        OK, BAD_SOCKET, CLOSED, BAD_ITEM, BAD_SKILL, NOT_QUIET, BAG_FULL
    }

    // 盤の穴 socket に、持っている紋 bag 番目を、中心の技 skill で差す (先に差してあれば、それは持ち物へ戻る)
    public static GlyphResult place(ServerPlayer player, int socket, int bag, Skill skill) {
        SkillData d = SkillManager.get(player);
        if (socket < 0 || socket >= d.glyphSockets.length) {
            return GlyphResult.BAD_SOCKET;
        }
        if (socket >= SkillRules.glyphSockets(d.rebirth)) {
            return GlyphResult.CLOSED;
        }
        if (bag < 0 || bag >= d.glyphBag.size()) {
            return GlyphResult.BAD_ITEM;
        }
        if (skill == null || !d.knows(skill)) {
            return GlyphResult.BAD_SKILL;
        }
        if (!SkillManager.quiet(player)) {
            return GlyphResult.NOT_QUIET;
        }
        Glyph.Item item = Glyph.parse(d.glyphBag.get(bag));
        if (item == null) {
            return GlyphResult.BAD_ITEM;
        }
        String old = d.glyphSockets[socket];
        d.glyphBag.remove(bag);
        if (old != null && !old.isEmpty()) {
            Glyph.Placed p = Glyph.parsePlaced(old);
            if (p != null) {
                d.glyphBag.add(p.item().code());
            }
        }
        d.glyphSockets[socket] = new Glyph.Placed(item, skill).code();
        done(player);
        return GlyphResult.OK;
    }

    public static GlyphResult remove(ServerPlayer player, int socket) {
        SkillData d = SkillManager.get(player);
        if (socket < 0 || socket >= d.glyphSockets.length) {
            return GlyphResult.BAD_SOCKET;
        }
        Glyph.Placed p = Glyph.parsePlaced(d.glyphSockets[socket]);
        if (p == null) {
            return GlyphResult.BAD_ITEM;
        }
        if (!SkillManager.quiet(player)) {
            return GlyphResult.NOT_QUIET;
        }
        if (d.glyphBag.size() >= SkillRules.GLYPH_BAG_MAX) {
            return GlyphResult.BAG_FULL;
        }
        d.glyphBag.add(p.item().code());
        d.glyphSockets[socket] = "";
        done(player);
        return GlyphResult.OK;
    }

    private static void done(ServerPlayer player) {
        SkillManager.sync(player);
        SkillManager.applyModifiers(player);
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(), Skills.SOUND_LEARN.get(), SoundSource.PLAYERS, 0.6f, 1.5f);
    }

    // 紋を 1 つ持ち物へ足す (空きがなければ false)
    public static boolean give(ServerPlayer player, Glyph glyph, int level) {
        SkillData d = SkillManager.get(player);
        if (d.glyphBag.size() >= SkillRules.GLYPH_BAG_MAX) {
            return false;
        }
        d.glyphBag.add(new Glyph.Item(glyph, Math.max(1, Math.min(Glyph.MAX_LEVEL, level))).code());
        SkillManager.sync(player);
        return true;
    }

    // 試練を越えたときの紋。巨神ごとに近いタグ (岩鯨=岩・雷翼=風・潮殻=潮・余燼=灰) が 7 割、残りは 5 つのタグから (骨はここから)。
    // 段は 1、転生 2 回で 2、5 回で 3。同じ試練では一度だけ。持ち物が満杯なら受け取らず、知らせる
    public static boolean award(ServerPlayer player, String titan, String trial) {
        SkillData d = SkillManager.get(player);
        String claim = titan + "|" + trial;
        if (d.glyphClaims.contains(claim)) {
            return false;
        }
        if (d.glyphBag.size() >= SkillRules.GLYPH_BAG_MAX) {
            Notice.bar(player, ModText.deny("message.skyabyss.skill.glyph_full"));
            return false;
        }
        RandomSource rng = player.getRandom();
        Glyph glyph = rng.nextFloat() < 0.7f ? home(titan) : Glyph.values()[rng.nextInt(Glyph.values().length)];
        int level = 1 + (d.rebirth >= 2 ? 1 : 0) + (d.rebirth >= 5 ? 1 : 0);
        d.glyphClaims.add(claim);
        give(player, glyph, level);
        Notice.send(player, Notice.Kind.LEVEL_UP, ModText.lore("message.skyabyss.skill.glyph_got", Component.translatable(glyph.nameKey()), level));
        return true;
    }

    static Glyph home(String titan) {
        return switch (titan) {
            case "stone_whale" -> Glyph.ROCK;
            case "thunder_wing" -> Glyph.WIND;
            case "tide_shell" -> Glyph.TIDE;
            case "ember" -> Glyph.ASH;
            default -> Glyph.BONE;
        };
    }
}
