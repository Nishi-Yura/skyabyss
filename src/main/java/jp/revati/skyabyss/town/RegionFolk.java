package jp.revati.skyabyss.town;

import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;

// 土地の町の人 (docs/design/42 の 1.2、00 の 278 = A)。二〜四章の町に二人ずつ。見た目は都の町の人 (TownGuests) の絵を借り、
// 名前 (entity.skyabyss.guest_<id>) と会話 (data/skyabyss/dialogue/guest_<id>.json) は新しい。
// 町の部品の印 (folk_a・folk_b。tools/gen/world/town_arch.py) に立つ。保存しない (TownGuestEntity) ので、人が近くに来るたびに ContinentTowns が立たせ直す
public final class RegionFolk {
    // id・借りる見た目 (都の町の人の id)・町 (鯨見 whale / 汐守 tide / 風待 storm / 灰炉 ember)・印
    public record Folk(String id, String look, String town, String mark) {
    }

    public static final List<Folk> ALL = List.of(
            new Folk("tide_ferryman", "sailor", "tide", "folk_a"),
            new Folk("tide_lightkeeper", "fisher", "tide", "folk_b"),
            new Folk("storm_elder", "weaver", "storm", "folk_a"),
            new Folk("storm_hutkeeper", "apprentice", "storm", "folk_b"),
            new Folk("ember_survivor", "ash_apprentice", "ember", "folk_a"),
            new Folk("ember_watch", "bard", "ember", "folk_b"),
            // はじまりの島の船着き場の渡し守 (汐守の渡し守と同じ絵。同じ人かどうかは語らない。world/continent/IslandTrail)
            new Folk("island_ferryman", "sailor", "island", "dock"),
            // 内海の渡し守 (両岸の船着き場に一人ずつ。話すと向こう岸へ渡す。world/continent/ContinentFerries。2026-10-27 の声)
            new Folk("inner_ferryman", "fisher", "ferry", "dock"));

    private RegionFolk() {
    }

    @Nullable
    public static Folk byId(String id) {
        for (Folk f : ALL) {
            if (f.id.equals(id)) {
                return f;
            }
        }
        return null;
    }

    // 絵を借りる相手 (土地の町の人でなければ id のまま)
    public static String lookOf(String id) {
        Folk f = byId(id);
        return f == null ? id : f.look;
    }

    // その町の印に、その人がいなければ立たせる (近くにいれば何もしない。離れていたら戻す)
    public static void ensure(ServerLevel level, String town, String mark, BlockPos at) {
        for (Folk f : ALL) {
            if (!f.town.equals(town) || !f.mark.equals(mark)) {
                continue;
            }
            List<TownGuestEntity> near = level.getEntities(TownSetup.GUEST.get(), new AABB(at).inflate(24), e -> e.isAlive() && f.id.equals(e.guestId()));
            if (!near.isEmpty()) {
                TownGuestEntity e = near.get(0);
                if (e.distanceToSqr(at.getX() + 0.5, at.getY(), at.getZ() + 0.5) > 36) {
                    e.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
                }
                for (int i = 1; i < near.size(); i++) {
                    near.get(i).discard();
                }
                continue;
            }
            TownGuestEntity e = TownSetup.GUEST.get().create(level);
            if (e == null) {
                continue;
            }
            e.setGuest(f.id);
            e.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, level.random.nextFloat() * 360f, 0f);
            e.setHome(at);
            level.addFreshEntity(e);
        }
    }
}
