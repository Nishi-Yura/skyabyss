package jp.revati.skyabyss.trial.ember;

import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;

import jp.revati.skyabyss.party.Parties;
import jp.revati.skyabyss.titan.ember.EmberIds;
import jp.revati.skyabyss.titan.ember.back.EmberBackLayout;
import jp.revati.skyabyss.titan.ember.back.EmberDeck;
import jp.revati.skyabyss.titan.ember.back.EmberHeat;
import jp.revati.skyabyss.titan.ember.back.EmberKit;
import jp.revati.skyabyss.titan.ember.back.trial.EmberTrialSetup;
import jp.revati.skyabyss.titan.ember.back.trial.EmberTrials;
import jp.revati.skyabyss.titan.ember.back.trial.HearthVents;
import jp.revati.skyabyss.trial.TrialCell;
import jp.revati.skyabyss.trial.TrialFx;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

// 試練の階の四つのセル (E02・E06・E07・E10。docs/design/29 の 3.4) の共通の道具。噴気の割れ目 (E02 と E10 の障害)・人数の数え方・敵を湧かせる口。
public final class EmberCellKit {
    private EmberCellKit() {
    }

    // 噴気: 1 組の割れ目は、光る (予告) → 噴く → しずか、の周期
    public static final int ERUPT = 24;
    // 予告の最短 (0.8 秒 = 16 tick は割らない。docs/design/29 の 2.3)
    public static final int WARN_MIN = 16;

    // 入った人の数 (パーティの上限 6 まで)
    public static int entrants(int inside) {
        return Math.max(1, Math.min(Parties.MAX_MEMBERS, inside));
    }

    // 周期の中の段階: 0 しずか / 1 光る (予告) / 2 噴く。base = 周期の起点、off = 割れ目ごとのずれ
    public static int phase(long now, long base, int period, long off, int warn) {
        if (now < base) {
            return 0;
        }
        long u = Math.floorMod(now - base + off, (long) period);
        if (u < warn) {
            return 1;
        }
        return u < warn + ERUPT ? 2 : 0;
    }

    // 何回目の噴気か (当たりの記録の鍵)
    public static long burstIndex(long now, long base, int period, long off) {
        return Math.floorDiv(now - base + off, (long) period);
    }

    // 3 × 3 の割れ目 (中心 (cx, y, cz)): 真ん中は噴気のブロック、まわりは割れた床 (建てるとき)
    public static void buildPatch(EmberKit kit, int cx, int y, int cz) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                kit.set(cx + dx, y, cz + dz, dx == 0 && dz == 0 ? ventBlock(0) : Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS.defaultBlockState());
            }
        }
    }

    public static BlockState ventBlock(int phase) {
        return EmberTrialSetup.HEAT_VENT.get().defaultBlockState().setValue(HearthVents.VentBlock.PHASE, phase);
    }

    // そのグループの画面での割れ目の姿。phase 0 は世界のまま (何も出さない)
    public static void patchOverlay(int cx, int y, int cz, int phase, BiConsumer<BlockPos, BlockState> out) {
        if (phase <= 0) {
            return;
        }
        BlockState ring = phase == 1 ? Blocks.GLOWSTONE.defaultBlockState() : Blocks.MAGMA_BLOCK.defaultBlockState();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                out.accept(new BlockPos(cx + dx, y, cz + dz), dx == 0 && dz == 0 ? ventBlock(phase) : ring);
            }
        }
    }

    // 割れ目を「ふつうの床」に見せる (障害が無いとき。E10)
    public static void patchPlain(int cx, int y, int cz, BiConsumer<BlockPos, BlockState> out) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                out.accept(new BlockPos(cx + dx, y, cz + dz), EmberKit.ruinFloor(cx + dx, cz + dz));
            }
        }
    }

    // 噴いている割れ目の上 (3 × 3、高さ 4) にいるか
    public static boolean over(ServerPlayer p, int cx, int y, int cz) {
        return Math.abs(p.getX() - (cx + 0.5)) <= 1.55 && Math.abs(p.getZ() - (cz + 0.5)) <= 1.55 && p.getY() >= y + 0.5 && p.getY() < y + 4.5;
    }

    // 噴気に焼かれた: 熱 +25・6 の傷・突き上げ (HearthVents と同じ数)
    public static void burn(ServerLevel back, TrialCell.Run run, ServerPlayer p, int cx, int cz) {
        if (p.isCreative()) {
            return;
        }
        EmberHeat.add(p, HearthVents.PLAYER_HEAT, EmberHeat.Reason.VENT);
        p.invulnerableTime = 0;
        p.hurt(EmberTrials.damage(back, EmberIds.HEAT_BURST, null), HearthVents.PLAYER_DAMAGE);
        Vec3 away = p.position().subtract(cx + 0.5, 0, cz + 0.5).multiply(1, 0, 1);
        Vec3 push = away.lengthSqr() < 1e-3 ? Vec3.ZERO : away.normalize().scale(0.25);
        p.setDeltaMovement(push.x, 0.65, push.z);
        p.hurtMarked = true;
        run.put("burns", run.getInt("burns") + 1);
    }

    // 噴く・光るの音と粒 (そのグループの人にだけ)
    public static void ventFx(ServerLevel back, UUID group, int cx, int y, int cz, int phase) {
        BlockPos at = new BlockPos(cx, y, cz);
        if (phase == 1) {
            TrialFx.sound(back, group, at, EmberTrialSetup.VENT_HISS.get(), 0.9f, 1f);
            TrialFx.particles(back, group, ParticleTypes.CLOUD, cx + 0.5, y + 1.1, cz + 0.5, 3, 0.8, 0.05, 0.8, 0.02);
        } else if (phase == 2) {
            TrialFx.sound(back, group, at, EmberTrialSetup.VENT_BURST.get(), 1.2f, 1f);
        }
    }

    public static void ventColumn(ServerLevel back, UUID group, int cx, int y, int cz) {
        TrialFx.particles(back, group, ParticleTypes.FLAME, cx + 0.5, y + 1.2, cz + 0.5, 6, 0.7, 1.3, 0.7, 0.05);
        TrialFx.particles(back, group, ParticleTypes.LARGE_SMOKE, cx + 0.5, y + 2.6, cz + 0.5, 2, 0.6, 1.1, 0.6, 0.03);
    }

    // 試練の敵を湧かせる (そのグループのもの。保存しない印つき)。湧かせられなければ null
    public static <T extends Mob> T spawn(ServerLevel back, UUID group, EntityType<T> type, double x, double y, double z) {
        return EmberTrials.spawn(back, group, type, x, y, z);
    }

    // 戻りの光る床 (3 × 3。中心 pad の床): そのセルが済んだグループの画面でだけ光る
    public static void padGlow(BlockPos pad, boolean cleared, BiConsumer<BlockPos, BlockState> out) {
        if (!cleared || pad == null) {
            return;
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                out.accept(pad.offset(dx, 0, dz), dx == 0 && dz == 0 ? EmberKit.EMBERLIGHT : Blocks.SHROOMLIGHT.defaultBlockState());
            }
        }
    }

    // 休み場 (TrialCell.Ports の休み場。EmberDeck.RESTS の名前から)
    public static TrialCell.Rest rest(String name) {
        for (EmberBackLayout.Rest r : EmberDeck.RESTS) {
            if (r.name().equals(name)) {
                EmberBackLayout.Box a = r.area();
                return new TrialCell.Rest(name, new jp.revati.skyabyss.titan.dungeon.TitanBackLayout.Box(a.minX(), a.maxX(), a.minZ(), a.maxZ(), a.minY(), a.sizeY()),
                        r.floor().above());
            }
        }
        throw new IllegalArgumentException(name);
    }

    public static boolean inBox(ServerPlayer p, EmberBackLayout.Box box) {
        return box.contains(p.position());
    }

    // 足もとが床 (床の高さ + 1 に立っている)
    public static boolean standing(ServerPlayer p) {
        return p.getY() >= EmberDeck.Y + 0.9 && p.getY() < EmberDeck.Y + 2.5;
    }

    public static List<ServerPlayer> alive(List<ServerPlayer> list) {
        return list.stream().filter(p -> p.isAlive() && !p.isSpectator()).toList();
    }
}
