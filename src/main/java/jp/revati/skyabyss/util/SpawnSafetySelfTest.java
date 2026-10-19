package jp.revati.skyabyss.util;

import java.util.ArrayList;
import java.util.List;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.block.Blocks;

// 敵が壁・地面の中に湧かない決まり (SpawnSafety) の自己テスト。段の名前は "spawn safety"。
// このモッドの MONSTER の生き物すべて (大きな敵を除く) を、①石の塊の真ん中 ②床のない穴の中 ③開けた床の上 に湧かせる。
// ①②は動かされて当たりのない場所に立つか、湧かない。③は動かされずに湧く。モッドの湧き口は全部 addFreshEntity を通るので、これで全部の波を保証する
public final class SpawnSafetySelfTest {
    private SpawnSafetySelfTest() {
    }

    // 試験の場所 (地表から離れた空の高さ。ブロックは終わりに空気へ戻す)
    private static final int BASE_X = 20000;
    private static final int BASE_Z = 20000;
    private static final int BASE_Y = 200;

    public static void runSelfTest(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        ServerLevel level = server.overworld();
        for (int cx = (BASE_X - 8) >> 4; cx <= (BASE_X + 24) >> 4; cx++) {
            for (int cz = (BASE_Z - 8) >> 4; cz <= (BASE_Z + 24) >> 4; cz++) {
                level.getChunk(cx, cz);
            }
        }
        clear(level);
        int tested = 0;
        int movedBefore = SpawnSafety.moved;
        List<Entity> made = new ArrayList<>();
        try {
            // ① 石の塊 (7×7×7) の真ん中
            BlockPos solid = new BlockPos(BASE_X, BASE_Y, BASE_Z);
            fillBox(level, solid.offset(-3, -3, -3), solid.offset(3, 3, 3), true);
            // ② 床のない穴: 3×3×6 の空洞の天井近く (下は石の柱が深く落ち込むだけ、空気)。石の天井・壁に囲まれた縦穴の上端
            BlockPos pit = new BlockPos(BASE_X + 16, BASE_Y, BASE_Z);
            fillBox(level, pit.offset(-4, -12, -4), pit.offset(4, 6, 4), true);
            fillBox(level, pit.offset(-1, -12, -1), pit.offset(1, 4, 1), false);
            // ③ 開けた床: 床 y BASE_Y-1 の上 9×9×6 の空き
            BlockPos open = new BlockPos(BASE_X, BASE_Y, BASE_Z + 16);
            fillBox(level, open.offset(-6, -1, -6), open.offset(6, -1, 6), true);
            fillBox(level, open.offset(-6, 0, -6), open.offset(6, 6, 6), false);
            for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
                if (!BuiltInRegistries.ENTITY_TYPE.getKey(type).getNamespace().startsWith(SkyAbyss.MODID) || type.getCategory() != MobCategory.MONSTER) {
                    continue;
                }
                Entity probe = type.create(level);
                if (!(probe instanceof Mob sample) || sample.getBbWidth() > SpawnSafety.MAX_WIDTH) {
                    continue;
                }
                String name = BuiltInRegistries.ENTITY_TYPE.getKey(type).getPath();
                tested++;
                // ① 塊の真ん中
                Mob a = (Mob) type.create(level);
                a.setPos(solid.getX() + 0.5, solid.getY(), solid.getZ() + 0.5);
                boolean addedA = level.addFreshEntity(a);
                if (addedA) {
                    made.add(a);
                    check(f, level.noCollision(a, a.getBoundingBox()), name + " was added inside a solid block at " + a.blockPosition());
                }
                // ② 床のない穴 (上の端から 4 マス落ちる場所)。床が三マス以内になければ、動かされるか湧かない
                Mob b = (Mob) type.create(level);
                b.setPos(pit.getX() + 0.5, pit.getY() + 3.5, pit.getZ() + 0.5);
                boolean addedB = level.addFreshEntity(b);
                if (addedB) {
                    made.add(b);
                    check(f, level.noCollision(b, b.getBoundingBox()), name + " was added inside a wall at " + b.blockPosition());
                    check(f, SpawnSafety.flier(b) || SpawnSafety.hasFloor(level, b.getBoundingBox()) || !level.getFluidState(b.blockPosition()).isEmpty(),
                            name + " was added with no floor under it at " + b.blockPosition());
                }
                // ③ 開けた床の上: 動かされずに湧く
                Mob c = (Mob) type.create(level);
                double cx = open.getX() + 0.5;
                double cz = open.getZ() + 0.5;
                c.setPos(cx, open.getY(), cz);
                boolean addedC = level.addFreshEntity(c);
                check(f, addedC, name + " was refused on open ground");
                if (addedC) {
                    made.add(c);
                    check(f, Math.abs(c.getX() - cx) < 1e-6 && Math.abs(c.getZ() - cz) < 1e-6, name + " was moved on open ground");
                }
            }
            check(f, tested >= 10, "too few mod monster types were tested: " + tested);
            // 穴の脇の壁に埋まった位置 (壁の中・床の下) から、近くの空きへ動かされる
            Mob w = EntityType.ZOMBIE.create(level);
            w.setPos(open.getX() + 0.5, open.getY() - 1, open.getZ() + 0.5);
            check(f, !SpawnSafety.ok(level, w), "a zombie in the floor counted as fine");
            check(f, SpawnSafety.settle(level, w) && SpawnSafety.ok(level, w), "a zombie in the floor could not be settled");
            check(f, SpawnSafety.moved > movedBefore, "nothing was moved in the whole test");
        } finally {
            for (Entity e : made) {
                e.discard();
            }
            clear(level);
        }
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST spawn safety PASSED ({} monster types, moved {}, refused {})", tested, SpawnSafety.moved - movedBefore, SpawnSafety.refused);
        } else {
            f.forEach(p -> SkyAbyss.LOGGER.error("SELFTEST spawn safety FAILED: {}", p));
        }
    }

    private static void check(List<String> f, boolean ok, String what) {
        if (!ok) {
            f.add(what);
        }
    }

    private static void fillBox(ServerLevel level, BlockPos a, BlockPos b, boolean solid) {
        for (BlockPos p : BlockPos.betweenClosed(a, b)) {
            level.setBlock(p, solid ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2);
        }
    }

    private static void clear(ServerLevel level) {
        fillBox(level, new BlockPos(BASE_X - 8, BASE_Y - 14, BASE_Z - 8), new BlockPos(BASE_X + 24, BASE_Y + 8, BASE_Z + 24), false);
    }
}
