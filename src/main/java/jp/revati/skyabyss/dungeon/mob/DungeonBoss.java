package jp.revati.skyabyss.dungeon.mob;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import jp.revati.skyabyss.combat.BossCue;
import jp.revati.skyabyss.entity.foe.RegionBoss;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

// 地下のダンジョンの主の共通 (docs/design/55 の 2 章「予告と隙のある主 (47)。帯・技名の知らせ」)。
// 技の名前はいつも出す (名づきかどうかによらない)。体力の帯と人数の強さは dungeon/DungeonFloor が持つ。
// 掘り穴・柱など、主の間の印の場所は holes (DungeonFloor が湧かせたときに渡す。卵から出したときは空)
public abstract class DungeonBoss extends RegionBoss {
    protected final List<Vec3> holes = new ArrayList<>();

    protected DungeonBoss(EntityType<? extends Monster> type, Level level, String animPrefix, Supplier<SoundEvent> ambient, Supplier<SoundEvent> hurt,
            Supplier<SoundEvent> death, String... actions) {
        super(type, level, animPrefix, ambient, hurt, death, actions);
        setPersistenceRequired();
    }

    @Override
    protected void cue(String boss, String attack, int ticks) {
        BossCue.send(this, boss, attack, ticks);
    }

    public void setHoles(List<Vec3> list) {
        holes.clear();
        holes.addAll(list);
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    // 体力の割合 (段の変わり目)
    protected float hp() {
        return getHealth() / getMaxHealth();
    }
}
