package jp.revati.skyabyss.rpg.skill;

import net.minecraft.server.level.ServerPlayer;

// 装備の「木の印」(特性) が技の力へ足す割合。手の武器の印 (docs/design/27 の 4.1)。P3 で中身を入れる
public final class SkillMarks {
    private SkillMarks() {
    }

    public static double power(ServerPlayer player, SkillTree tree) {
        return 0;
    }
}
