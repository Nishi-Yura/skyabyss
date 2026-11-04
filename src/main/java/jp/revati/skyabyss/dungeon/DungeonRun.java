package jp.revati.skyabyss.dungeon;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

// 一つの組の、一つのダンジョンの写し (docs/design/55 の 2 章)。区画 slot を使い、層ごとの DungeonFloor を持つ。
//   checkpoint  着いた一番深い層 (倒れて起きたとき・入り直したときはここの入口へ。戻り石)
//   members     入った人。あとから同じ組の人が層の門を使うと、ここに加わって checkpoint の層へ
//   deep        深く潜る (主を一度倒した人が、しゃがんで層の門を使う。敵と主 +40%・仕掛けが速い・主の箱が一つ上。55 の 2 章)
//   mood        その日の坑の様子 (DungeonMood。入ったときに決まる)
public final class DungeonRun {
    public final int id;
    public final Dungeon dungeon;
    public final int slot;
    final MinecraftServer server;
    public final List<UUID> members = new ArrayList<>();
    public final long startedTick;
    public int checkpoint;
    final DungeonFloor[] floors = new DungeonFloor[Dungeon.FLOORS.length];
    public boolean cleared;
    public final boolean deep;
    public DungeonMood mood;
    long clearedAt;
    long emptyTicks;

    DungeonRun(MinecraftServer server, int id, Dungeon dungeon, int slot, List<UUID> members, long startedTick, int checkpoint, boolean deep, DungeonMood mood) {
        this.server = server;
        this.deep = deep;
        this.mood = mood;
        this.id = id;
        this.dungeon = dungeon;
        this.slot = slot;
        this.members.addAll(members);
        this.startedTick = startedTick;
        this.checkpoint = checkpoint;
        for (int f = 0; f < floors.length; f++) {
            floors[f] = new DungeonFloor(this, f);
        }
    }

    public ServerLevel level() {
        return server == null ? null : DungeonWorld.level(server);
    }

    public String foeTag() {
        return "skyabyss_dungeon_run_" + id;
    }

    public DungeonFloor floor(int f) {
        return floors[f];
    }

    public BlockPos mark(int floor, String name) {
        return DungeonWorld.at(dungeon, slot, floor, name);
    }

    public Vec3 markCenter(int floor, String name) {
        return DungeonWorld.center(dungeon, slot, floor, name);
    }

    public List<ServerPlayer> online() {
        List<ServerPlayer> out = new ArrayList<>();
        if (server == null) {
            return out;
        }
        for (UUID u : members) {
            ServerPlayer p = server.getPlayerList().getPlayer(u);
            if (p != null) {
                out.add(p);
            }
        }
        return out;
    }

    // 写しの中にいる人 (生きている人と倒れている人)
    public List<ServerPlayer> present() {
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer p : online()) {
            if (DungeonWorld.in(p) && !p.isSpectator()) {
                DungeonWorld.Place at = DungeonWorld.place(p.blockPosition());
                if (at != null && at.dungeon() == dungeon && at.slot() == slot) {
                    out.add(p);
                }
            }
        }
        return out;
    }

    // その人が今いる層 (写しの外なら -1)
    public int floorOf(ServerPlayer p) {
        if (!DungeonWorld.in(p)) {
            return -1;
        }
        DungeonWorld.Place at = DungeonWorld.place(p.blockPosition());
        return at != null && at.dungeon() == dungeon && at.slot() == slot ? at.floor() : -1;
    }
}
