package jp.revati.skyabyss.tower;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

// あいだの塔の一回の挑戦 (一つの隊、一つの区画)。docs/design/54 の 2 章
//   members  入った人 (1〜12)。arena は橋の上の一戦 (TowerArena)
public final class TowerRun {
    public final int id;
    public final int slot;
    public final List<UUID> members;
    public final long startedTick;
    public TowerArena arena;
    public final Set<UUID> fallen = new HashSet<>();
    public boolean cleared;
    final MinecraftServer server;

    TowerRun(MinecraftServer server, int id, int slot, List<UUID> members, long now) {
        this.server = server;
        this.id = id;
        this.slot = slot;
        this.members = new ArrayList<>(new LinkedHashSet<>(members));
        this.startedTick = now;
    }

    public ServerLevel level() {
        return TowerWorld.level(server);
    }

    public BlockPos mark(String m) {
        return TowerWorld.at(slot, m);
    }

    public Vec3 markCenter(String m) {
        return TowerWorld.center(slot, m);
    }

    // 区画の舞台の箱 (橋・広場・玉座の段・側の台)
    public AABB box() {
        BlockPos o = TowerWorld.origin(slot);
        return new AABB(o).inflate(48, 0, 0).expandTowards(0, 64, 0).expandTowards(0, -46, 0).expandTowards(0, 0, 64).expandTowards(0, 0, -96);
    }

    // 塔の中にいて舞台の箱の中の、生きている人 (観戦を除く)
    public List<ServerPlayer> present() {
        List<ServerPlayer> out = new ArrayList<>();
        ServerLevel l = level();
        if (l == null) {
            return out;
        }
        AABB b = box();
        for (UUID u : members) {
            ServerPlayer p = server.getPlayerList().getPlayer(u);
            if (p != null && p.level() == l && p.isAlive() && !p.isSpectator() && b.contains(p.position()) && Towers.BY_PLAYER.getOrDefault(u, -1) == id) {
                out.add(p);
            }
        }
        return out;
    }

    // 隊の人で、いまオンラインで、まだこの挑戦にいる人
    public List<ServerPlayer> online() {
        List<ServerPlayer> out = new ArrayList<>();
        for (UUID u : members) {
            ServerPlayer p = server.getPlayerList().getPlayer(u);
            if (p != null && Towers.BY_PLAYER.getOrDefault(u, -1) == id) {
                out.add(p);
            }
        }
        return out;
    }

    public String foeTag() {
        return TowerWorld.TAG + "_" + id;
    }
}
