#pragma once

#include <utils/constants.glsl>

#if ZONE_RENDERER
    #include MAX_SIMULTANEOUS_WORLD_VIEWS

    struct WorldView {
        mat4 projectionMatrix;
        ivec4 tint;
    };

    layout(std140) uniform UBOWorldViews {
        WorldView WorldViewArray[MAX_SIMULTANEOUS_WORLD_VIEWS];
    };

    #include WORLD_VIEW_GETTER

    mat4 getWorldViewProjection(int worldViewIndex) {
        if (worldViewIndex < 0)
            return mat4(1);
        return getWorldView(worldViewIndex).projectionMatrix;
    }

    ivec4 getWorldViewTint(int worldViewIndex) {
        if (worldViewIndex < 0)
            return ivec4(0);
        return getWorldView(worldViewIndex).tint;
    }

    int getWorldViewId(uint value) {
        uint shifted = value >> uint(27);
        uint masked = shifted & uint(0x1F);
        return int(masked) - 1;
    }

    int getZoneId(uint value) {
        uint shifted = value >> uint(17);
        uint masked = shifted & uint(0x3FFu);
        return int(masked) - 1;
    }

    int getModelId(uint value) {
        uint masked = value & uint(0x1FFFFu);
        return int(masked) - 1;
    }

    vec3 calculateBaseOffset(int zoneId) {
        if(zoneId < 0)
            return vec3(0);

        // for -1 (EXTENDED_SCENE_SIZE - SCENE_SIZE) / 2, sub scene have this set to 0
        int sceneOffset = (184 - 104) / 2;

        int sizeX = 184; // TODO: Should sizeX/sizeY be stored within the worldViewStruct?
        int sizeY = 184;
		int mzx = zoneId % (sizeX >> 3);
		int mzz = zoneId / (sizeY >> 3);

        vec3 offset;
        offset.x = (mzx - (sceneOffset >> 3)) << 10;
        offset.y = 0;
        offset.z = (mzz - (sceneOffset >> 3)) << 10;
        return offset;
    }
#else
    #define getWorldViewProjection(worldViewIndex) mat4(1)
    #define getWorldViewTint(worldViewIndex) ivec4(0)
#endif
