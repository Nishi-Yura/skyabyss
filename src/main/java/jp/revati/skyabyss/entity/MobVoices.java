package jp.revati.skyabyss.entity;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;

// 敵の声の表 (環境音・被弾・倒れる)。種の名前 (レジストリの path。例 dust_crawler) から三つの音を引く。
// 音は各 Setup が登録し、素材は tools/gen/sound/gen_mob_voices.py で合成している。表にない種は素の音 (バニラ) のまま。
public final class MobVoices {
    public record Voice(Supplier<SoundEvent> ambient, Supplier<SoundEvent> hurt, Supplier<SoundEvent> death) {
    }

    private static final Map<String, Voice> TABLE = new HashMap<>();

    private MobVoices() {
    }

    // 種の名前に音を結ぶ。同じ名前の二度目は登録しない (取り違えを防ぐ)
    public static void put(String id, Voice voice) {
        if (TABLE.putIfAbsent(id, voice) != null) {
            throw new IllegalStateException("mob voice registered twice: " + id);
        }
    }

    @Nullable
    public static Voice of(EntityType<?> type) {
        ResourceLocation key = BuiltInRegistries.ENTITY_TYPE.getKey(type);
        return key == null ? null : TABLE.get(key.getPath());
    }
}
