package jp.revati.skyabyss.item.accessory;

// 装飾の枠の種類 (03 の 10章)。護符 = 使って効く品 (キーで使う)、装飾 = 付けているだけで効く品
public enum AccessoryKind {
    CHARM("charm"),
    TRINKET("trinket");

    public final String key;

    AccessoryKind(String key) {
        this.key = key;
    }
}
