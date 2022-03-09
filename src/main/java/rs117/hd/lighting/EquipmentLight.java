package rs117.hd.lighting;

import com.google.common.collect.ImmutableMap;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Getter;
import net.runelite.api.ItemID;
import static net.runelite.api.ItemID.*;

@AllArgsConstructor
@Getter
@Deprecated
enum EquipmentLight
{
        FIRE_CAPE(75, Alignment.BACK, 135, 8f, rgb(198, 156, 74), LightType.PULSE, 2100, 10, ItemID.FIRE_CAPE, FIRE_CAPE_10566),
        FIRE_MAX_CAPE(75, Alignment.BACK, 135, 8f, rgb(198, 156, 74), LightType.PULSE, 2100, 10, ItemID.FIRE_MAX_CAPE, FIRE_MAX_CAPE_21186),
        INFERNAL_CAPE(75, Alignment.BACK, 135, 8f, rgb(133, 64, 0), LightType.PULSE, 2100, 10, ItemID.INFERNAL_CAPE, INFERNAL_CAPE_21297, INFERNAL_CAPE_23622),
        INFERNAL_MAX_CAPE(75, Alignment.BACK, 135, 8f, rgb(133, 64, 0), LightType.PULSE, 2100, 10, ItemID.INFERNAL_MAX_CAPE, INFERNAL_MAX_CAPE_21285),
    ;

    private final int[] id;
    private final int height;
    private final Alignment alignment;
    private final int size;
    private final float strength;
    private final float[] color;
    private final LightType lightType;
    private final float duration;
    private final float range;

    EquipmentLight(int height, Alignment alignment, int size, float strength, float[] color, LightType lightType, float duration, float range, int... ids)
    {
        this.height = height;
        this.alignment = alignment;
        this.size = size;
        this.strength = strength;
        this.color = color;
        this.lightType = lightType;
        this.duration = duration;
        this.range = range;
        this.id = ids;
    }

    private static final Map<Integer, EquipmentLight> LIGHTS;

    static
    {
        ImmutableMap.Builder<Integer, EquipmentLight> builder = new ImmutableMap.Builder<>();
        for (EquipmentLight equipmentLight : values())
        {
            for (int id : equipmentLight.id)
            {
                builder.put(id + 512, equipmentLight);
            }
        }
        LIGHTS = builder.build();
    }

    static EquipmentLight find(int id)
    {
        return LIGHTS.get(id);
    }

    private static float[] rgb(int r, int g, int b)
    {
		return new float[] { r / 255f, g / 255f, b / 255f };
    }
}
