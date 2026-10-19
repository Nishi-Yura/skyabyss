package jp.revati.skyabyss.trial;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import jp.revati.skyabyss.progress.Titans;
import jp.revati.skyabyss.titan.dungeon.TitanBackLayout;
import jp.revati.skyabyss.titan.dungeon.WhaleDeck;
import jp.revati.skyabyss.titan.ember.back.EmberBackLayout;
import jp.revati.skyabyss.titan.storm.back.StormBackLayout;
import jp.revati.skyabyss.titan.tide.back.TideBackLayout;
import jp.revati.skyabyss.trial.Trial.Slot;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

// 試練の「中」の範囲 (docs/design/29 の 4.6)。試練を越えたとき、その中にいる仲間だけを真ん中の鼓動の間へ戻し、
// 越えた試練へは、そのグループは入り直せない (中へ入ると鼓動の間へ戻される) ために使う。
// 試練の名前 (key) は石の合図 (TrialSteles) と同じ: 岩鯨は 0 西 / 1 東 / 2 下、潮殻・雷翼は west / east / front、余燼は west / east / south。
// 岩鯨・潮殻の新作のセル (試練の階) は、引いたセルの床の範囲 (TrialCell.Ports.area と休み場)。
// 範囲は鼓動の間・入口・宝物庫・心臓へ続く道を含まない。背の作りを変えたら、自己テスト (trial pool の regions) が
// 石と休み場が範囲に入っていること・鼓動の間が入っていないことを確かめる
public final class TrialRegions {
    private TrialRegions() {
    }

    private static AABB tide(TideBackLayout.Area a) {
        return new AABB(a.x1(), a.floor(), a.z1(), a.x2() + 1, a.floor() + a.height(), a.z2() + 1);
    }

    private static AABB ember(EmberBackLayout.Box b) {
        return new AABB(b.minX(), b.minY(), b.minZ(), b.maxX() + 1, b.maxY() + 1, b.maxZ() + 1);
    }

    // その試練の範囲 (複数の箱)。group は岩鯨の新作のセル (引きによる) を決めるために使う (null なら原型の範囲)
    public static List<AABB> of(ServerLevel back, String titan, String key, @Nullable UUID group) {
        List<AABB> out = new ArrayList<>();
        switch (titan) {
            case Titans.STONE_WHALE, "whale" -> {
                int f = TitanBackLayout.FLOOR;
                TrialCell cell = group == null ? null : drawnCell(back, group, Titans.STONE_WHALE, key);
                if (cell != null) {
                    out.add(cell.ports().area().inflate(1, 2, 1));
                    for (TrialCell.Rest r : cell.ports().rests()) {
                        out.add(new AABB(r.area().x1(), r.area().floorY(), r.area().z1(), r.area().x2() + 1, r.area().floorY() + 8, r.area().z2() + 1));
                    }
                } else {
                    switch (key) {
                        case "0" -> out.add(new AABB(-62, f - 3, -135, -22, f + 40, -16));
                        case "1" -> out.add(new AABB(22, f - 3, -110, 62, f + 40, 82));
                        case "2" -> out.add(new AABB(-50, TitanBackLayout.MAZE_FLOOR - 4, -125, 50, f - 5, 8));
                        default -> {
                        }
                    }
                }
            }
            case Titans.TIDE_SHELL, "tide" -> {
                TrialCell drawn = group == null ? null : drawnCell(back, group, titan, key);
                if (drawn != null) {
                    out.add(drawn.ports().area().inflate(1, 2, 1));
                    for (TrialCell.Rest r : drawn.ports().rests()) {
                        out.add(new AABB(r.area().x1(), r.area().floorY(), r.area().z1(), r.area().x2() + 1, r.area().floorY() + 8, r.area().z2() + 1));
                    }
                    break;
                }
                switch (key) {
                    case "west" -> out.add(tide(TideBackLayout.GARDEN));
                    case "east" -> out.add(new AABB(40, 70, -65, 100, 106, -4));
                    case "front" -> {
                        out.add(tide(TideBackLayout.FLATS));
                        out.add(tide(TideBackLayout.F0));
                    }
                    default -> {
                    }
                }
            }
            case Titans.THUNDER_WING, "storm" -> {
                // 嵐の背の試練の床は広間 (床 134) の肩の階段より上 (竜骨の道と心臓の間は広間の床より下)
                int y0 = StormBackLayout.HUB.floor() + 2;
                switch (key) {
                    case "west" -> out.add(new AABB(StormBackLayout.RIB_X1 - 20, y0, StormBackLayout.BONE_Z1 - 12, -23, 240, StormBackLayout.BONE_Z2 + 12));
                    case "east" -> out.add(new AABB(23, y0, StormBackLayout.BONE_Z1 - 12, StormBackLayout.RAIJU_NEST.getX() + 22, 240, StormBackLayout.BONE_Z2 + 12));
                    case "front" -> out.add(new AABB(-24, y0, -154, 24, 240, StormBackLayout.HUB.z1() - 1));
                    default -> {
                    }
                }
            }
            case Titans.EMBER -> {
                switch (key) {
                    case "west" -> out.add(ember(EmberBackLayout.WEST));
                    case "east" -> {
                        out.add(ember(EmberBackLayout.EAST));
                        out.add(ember(EmberBackLayout.SMITH));
                    }
                    case "south" -> out.add(ember(EmberBackLayout.SOUTH));
                    default -> {
                    }
                }
            }
            default -> {
            }
        }
        return out;
    }

    public static boolean inside(List<AABB> regions, Vec3 p) {
        for (AABB b : regions) {
            if (b.contains(p)) {
                return true;
            }
        }
        return false;
    }

    // 試練の名前の一覧 (石の合図の順)
    public static List<String> keys(String titan) {
        return switch (titan) {
            case Titans.STONE_WHALE, "whale" -> List.of("0", "1", "2");
            case Titans.TIDE_SHELL, "tide", Titans.THUNDER_WING, "storm" -> List.of("west", "east", "front");
            case Titans.EMBER -> List.of("west", "east", "south");
            default -> List.of();
        };
    }

    // 進行の名前 (stone_whale…) と石の合図の名前 (whale…) の両方を受ける
    public static String signalTitan(String titan) {
        return switch (titan) {
            case Titans.STONE_WHALE -> "whale";
            case Titans.TIDE_SHELL -> "tide";
            case Titans.THUNDER_WING -> "storm";
            case Titans.EMBER -> "ember";
            default -> titan;
        };
    }

    // その枠 (岩鯨は 0 歩 / 1 闘 / 2 智の石の番号、潮殻は west 歩 / front 闘 / east 智) に引いてある新作のセル。なければ null (原型)
    @Nullable
    private static TrialCell drawnCell(ServerLevel back, UUID group, String titan, String key) {
        Slot slot;
        if (Titans.STONE_WHALE.equals(titan)) {
            if (!WhaleDeck.placed()) {
                return null;
            }
            try {
                slot = Slot.ofStone(Integer.parseInt(key));
            } catch (NumberFormatException e) {
                return null;
            }
        } else {
            if (!jp.revati.skyabyss.titan.tide.back.TideDeck.placed()) {
                return null;
            }
            slot = switch (key) {
                case "west" -> Slot.WALK;
                case "east" -> Slot.WIT;
                case "front" -> Slot.FIGHT;
                default -> null;
            };
            if (slot == null) {
                return null;
            }
        }
        TrialDraw.Pick pick = TrialData.current(back, group).inSlot(slot);
        if (pick == null) {
            return null;
        }
        TrialCell cell = TrialCells.get(pick.cellId());
        return cell != null && cell.runtime() ? cell : null;
    }

    // 試験用: 石の位置 (その試練の石)
    @Nullable
    public static BlockPos stoneOf(String titan, String key) {
        try {
            return switch (signalTitan(titan)) {
                case "whale" -> TitanBackLayout.RESONANCE_STONES.get(Integer.parseInt(key));
                case "tide" -> switch (key) {
                    case "west" -> TideBackLayout.STONE_WEST;
                    case "east" -> TideBackLayout.STONE_EAST;
                    default -> TideBackLayout.STONE_FRONT;
                };
                case "storm" -> switch (key) {
                    case "west" -> StormBackLayout.WEST_STONE;
                    case "east" -> StormBackLayout.EAST_STONE;
                    default -> StormBackLayout.FRONT_STONE;
                };
                case "ember" -> switch (key) {
                    case "west" -> EmberBackLayout.STONE_WEST;
                    case "east" -> EmberBackLayout.STONE_EAST;
                    default -> EmberBackLayout.STONE_SOUTH;
                };
                default -> null;
            };
        } catch (RuntimeException e) {
            return null;
        }
    }
}
