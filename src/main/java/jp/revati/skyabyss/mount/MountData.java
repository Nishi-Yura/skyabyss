package jp.revati.skyabyss.mount;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.common.util.INBTSerializable;

// 人ごとの乗り物の持ち物 (保存する。死んでも残る。同期はしない = 店の画面を開くときに送る)。
//   owned  : 買った見た目 (MountLook の順の bit)。角の荷獣は最初から持つ
//   chosen : 今の見た目 (MountLook の ordinal)
//   tiers  : 速さ・跳び・体力の段 (MountStats)
public final class MountData implements INBTSerializable<CompoundTag> {
    int owned = 1 << MountLook.HORN_BEAST.ordinal();
    int chosen = MountLook.HORN_BEAST.ordinal();
    final int[] tiers = new int[3];

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
        tiers[kind] = Math.max(0, Math.min(MountStats.TIERS, tier));
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
        return t;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag t) {
        owned = t.getInt("owned") | (1 << MountLook.HORN_BEAST.ordinal());
        chosen = t.getInt("chosen");
        int[] a = t.getIntArray("tiers");
        for (int i = 0; i < tiers.length; i++) {
            tiers[i] = i < a.length ? Math.max(0, Math.min(MountStats.TIERS, a[i])) : 0;
        }
    }
}
