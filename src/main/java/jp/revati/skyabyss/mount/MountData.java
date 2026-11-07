package jp.revati.skyabyss.mount;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.common.util.INBTSerializable;

// 人ごとの乗り物の持ち物 (保存する。死んでも残る。同期はしない = 店の画面を開くときに送る)。
//   owned  : 買った見た目 (MountLook の順の bit)。角の荷獣は最初から持つ
//   chosen : 今の見た目 (MountLook の ordinal)
//   tiers  : 速さ・跳び・体力・乗れる人数の段 (MountStats。体力の段は 2026-11-02 から使わない。前に買った分は PlayerUpgrade が返して 0 にする)
//   ridden : 乗った距離 (ブロック)。絆の段 (BOND_STEPS: 5 km・20 km・50 km。2026-11-05 の封の箱の頁 3-2)
//   name   : 付けた名前 (絆 II から。空 = 見た目の名前)
public final class MountData implements INBTSerializable<CompoundTag> {
    public static final double[] BOND_STEPS = {5000, 20000, 50000};
    public static final int NAME_MAX = 16;
    int owned = 1 << MountLook.HORN_BEAST.ordinal();
    int chosen = MountLook.HORN_BEAST.ordinal();
    final int[] tiers = new int[4];
    public double ridden;
    String name = "";

    public double ridden() {
        return ridden;
    }

    public void ride(double blocks) {
        ridden += Math.max(0, blocks);
    }

    // 絆の段 (0〜3)
    public int bond() {
        int b = 0;
        for (double step : BOND_STEPS) {
            if (ridden >= step) {
                b++;
            }
        }
        return b;
    }

    public String name() {
        return name;
    }

    public void setName(String n) {
        name = n == null ? "" : n;
    }

    public boolean owns(MountLook look) {
        return (owned & (1 << look.ordinal())) != 0;
    }

    public void own(MountLook look) {
        owned |= 1 << look.ordinal();
    }

    public MountLook chosen() {
        MountLook l = MountLook.byOrdinal(chosen);
        return owns(l) ? l : MountLook.HORN_BEAST;
    }

    public void choose(MountLook look) {
        chosen = look.ordinal();
    }

    public int tier(int kind) {
        return tiers[kind];
    }

    public void setTier(int kind, int tier) {
        tiers[kind] = MountStats.clamp(kind, tier);
    }

    public int ownedBits() {
        return owned;
    }

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag t = new CompoundTag();
        t.putInt("owned", owned);
        t.putInt("chosen", chosen);
        t.putIntArray("tiers", tiers);
        t.putDouble("ridden", ridden);
        t.putString("name", name);
        return t;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag t) {
        owned = t.getInt("owned") | (1 << MountLook.HORN_BEAST.ordinal());
        chosen = t.getInt("chosen");
        ridden = t.getDouble("ridden");
        name = t.getString("name");
        int[] a = t.getIntArray("tiers");
        for (int i = 0; i < tiers.length; i++) {
            tiers[i] = i < a.length ? MountStats.clamp(i, a[i]) : 0;
        }
    }
}
