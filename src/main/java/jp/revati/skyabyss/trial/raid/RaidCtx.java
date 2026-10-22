package jp.revati.skyabyss.trial.raid;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.party.Downed;
import jp.revati.skyabyss.titan.dungeon.TitanBackTrials;
import jp.revati.skyabyss.trial.TrialRoute;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Mob;

// レイドの部屋に渡す文脈 (docs/design/32 の 7 章。RaidRoom の約束も見る)。一回の挑戦・一つの部屋ごとに一つ。
//  origin   = 部屋の床の中心のブロック (区画の遠い島。立つ高さは origin.y + 1)。local(x, dy, z) で相対の位置
//  cell()   = RaidKit.buildCell が返した部屋の寸法・休み場・戸 (build のあと)
//  rng()    = この挑戦だけの乱数 (配置・順・敵の数を少し変える)
//  players()/standing() = 参加者 (倒れた人を含む/含まない)。scale() = 人数 (敵の数・体力の目安。1 以上)
//  spawnGuard/adopt/aliveMobs/clearMobs = 部屋の敵 (グループの敵として湧き、リセット・片付けで消える)
//  fail(key) = この部屋を最初からやり直す (全員が倒れたのと同じ。核が壊れた、など)。key は理由の lang キー
public final class RaidCtx {
    private final ServerLevel back;
    private final UUID group;
    private final String titan;
    private final String roomId;
    private final int slot;
    private final int step;
    private final BlockPos origin;
    private final long seed;
    private final RandomSource rng;
    private RaidKit.Cell cell;
    private final List<Mob> mobs = new ArrayList<>();
    private String failKey;

    // 部屋の自己テスト用: 部屋の箱を建てる前の文脈 (build に渡す)。人は順路の参加者から (TrialRoute.PLAYERS を差し替えて渡す)
    public static RaidCtx forTest(ServerLevel back, UUID group, String titan, String roomId, int slot, int step, long seed) {
        return new RaidCtx(back, group, titan, roomId, slot, step, RaidRun.originOf(slot, step), seed);
    }

    RaidCtx(ServerLevel back, UUID group, String titan, String roomId, int slot, int step, BlockPos origin, long seed) {
        this.back = back;
        this.group = group;
        this.titan = titan;
        this.roomId = roomId;
        this.slot = slot;
        this.step = step;
        this.origin = origin;
        this.seed = seed;
        this.rng = RandomSource.create(seed);
    }

    public ServerLevel level() {
        return back;
    }

    public UUID group() {
        return group;
    }

    public String titan() {
        return titan;
    }

    public String roomId() {
        return roomId;
    }

    public int slot() {
        return slot;
    }

    public int step() {
        return step;
    }

    public BlockPos origin() {
        return origin;
    }

    public BlockPos local(int x, int dy, int z) {
        return origin.offset(x, dy, z);
    }

    public long seed() {
        return seed;
    }

    public RandomSource rng() {
        return rng;
    }

    public long now() {
        return back.getGameTime();
    }

    public RaidKit.Cell cell() {
        return cell;
    }

    void setCell(RaidKit.Cell c) {
        this.cell = c;
    }

    // ---- 人
    // 順路の参加者のうち、この背にいる生きている人 (倒れた人を含む)
    public List<ServerPlayer> players() {
        return TrialRoute.members(back, group);
    }

    // 倒れていない人
    public List<ServerPlayer> standing() {
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer p : players()) {
            if (!Downed.isDowned(p)) {
                out.add(p);
            }
        }
        return out;
    }

    public int scale() {
        return Math.max(1, players().size());
    }

    public void say(Component text) {
        for (ServerPlayer p : players()) {
            Notice.bar(p, text);
        }
    }

    // ---- 敵
    // 岩鯨の背の敵 (kind は TitanBackTrials.spawnGuard の名前: sentinel archer crawler captain mortar gull elder)
    @Nullable
    public Mob spawnGuard(String kind, BlockPos pos) {
        return adopt(TitanBackTrials.spawnGuard(back, kind, pos, group));
    }

    // 自分で作った敵を部屋の敵として登録する (リセット・片付けで消える)。null はそのまま返す
    @Nullable
    public Mob adopt(@Nullable Mob mob) {
        if (mob != null) {
            mobs.add(mob);
        }
        return mob;
    }

    public List<Mob> aliveMobs() {
        mobs.removeIf(m -> !m.isAlive() || m.isRemoved());
        return mobs;
    }

    public void clearMobs() {
        for (Mob m : new ArrayList<>(mobs)) {
            if (!m.isRemoved()) {
                m.discard();
            }
        }
        mobs.clear();
    }

    // ---- やり直し
    // この部屋を最初からやり直す。全員が倒れたのと同じ (入口に戻り、reset → start)。key = 理由の lang キー (null なら出さない)
    public void fail(@Nullable String key) {
        failKey = key == null ? "" : key;
    }

    @Nullable
    String takeFail() {
        String k = failKey;
        failKey = null;
        return k;
    }
}
