package jp.revati.skyabyss.titan;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import jp.revati.skyabyss.progress.Progression;
import net.minecraft.server.level.ServerPlayer;

// 巨神の背中への見えない入口と、背中から地上へ戻る段の共通の部品 (13 の 10.2節の TitanGate。元は潮殻の TideTravel)。
// - 入口 (潮門・熱の口): 渡ったすぐあとの待ち、乗る資格の確認 (断ったら待たせる)、乗り物から降ろす
// - 戻る段 (浜の門・見送りの石): 口の中に dwell tick いたら戻す (かすっただけ・押し戻しの一瞬では戻さない)
// 08 の 20.12節「初めての乗り込みで地上へ戻された」の手当て (待ちと 0.5 秒) を、巨神ごとに持ち直さないためにまとめた。
// 巨神ごとに一つ作る: new TitanGate("tide", Titans.TIDE_SHELL, 60, 10)。待ちは人の持続データ skyabyss_<name>_cooldown に書く
public final class TitanGate {
    private final String titan;
    private final String cooldownTag;
    private final int cooldown;
    private final int dwell;
    // 戻る段の中にいる人 → 何 tick いるか
    private final Map<UUID, Integer> inside = new HashMap<>();

    public TitanGate(String name, String titan, int cooldownTicks, int dwellTicks) {
        this.titan = titan;
        this.cooldownTag = "skyabyss_" + name + "_cooldown";
        this.cooldown = cooldownTicks;
        this.dwell = dwellTicks;
    }

    public String titan() {
        return titan;
    }

    public boolean coolingDown(ServerPlayer player) {
        return player.getPersistentData().getLong(cooldownTag) > player.server.overworld().getGameTime();
    }

    public void startCooldown(ServerPlayer player) {
        player.getPersistentData().putLong(cooldownTag, player.server.overworld().getGameTime() + cooldown);
    }

    // 入口の判定に入った人を、渡してよいか。待ちの間・眠っている人は黙って断る。資格がなければ文を出して待たせる
    // (乗り物から降ろす前に断る)。渡してよければ乗り物から降ろして true
    public boolean admit(ServerPlayer player) {
        if (coolingDown(player) || player.isSleeping()) {
            return false;
        }
        if (!checkBoard(player)) {
            return false;
        }
        if (player.isPassenger()) {
            player.stopRiding();
        }
        return true;
    }

    // 乗る資格 (10 の 3 章)。断ったら入口の判定がすぐまた来ないように待たせる。コマンドの board からも
    public boolean checkBoard(ServerPlayer player) {
        if (!Progression.checkBoard(player, titan)) {
            startCooldown(player);
            return false;
        }
        return true;
    }

    // 戻る段: step tick ごとに、今その中にいるかを渡す。dwell tick いたら true (数え直す)。待ちの間は数えない
    public boolean dwell(ServerPlayer player, boolean inZone, int step) {
        if (!inZone || coolingDown(player)) {
            inside.remove(player.getUUID());
            return false;
        }
        int t = inside.merge(player.getUUID(), step, Integer::sum);
        if (t >= dwell) {
            inside.remove(player.getUUID());
            return true;
        }
        return false;
    }

    public void forget(UUID player) {
        inside.remove(player);
    }

    public void clear() {
        inside.clear();
    }
}
