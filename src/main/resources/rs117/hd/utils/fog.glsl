/*
 * Copyright (c) 2018, Adam <Adam@sigterm.info>
 * Copyright (c) 2021, 117 <https://twitter.com/117scape>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
#pragma once

#include <uniforms/global.glsl>
#include <uniforms/sky.glsl>

#include <utils/constants.glsl>

#define FOG_SCENE_EDGE_MIN ((    - uboGlobal.expandedMapLoadingChunks * 8 + 1) * TILE_SIZE)
#define FOG_SCENE_EDGE_MAX ((104 + uboGlobal.expandedMapLoadingChunks * 8 - 1) * TILE_SIZE)
#define FOG_CORNER_ROUNDING 1.5
#define FOG_CORNER_ROUNDING_SQUARED FOG_CORNER_ROUNDING * FOG_CORNER_ROUNDING

float fogFactorLinear(const float dist, const float start, const float end) {
    return 1.0 - clamp((dist - start) / (end - start), 0.0, 1.0);
}

// Integral of 1 - smoothstep(0, 1, height): uniform below the layer,
// smoothly thinning to zero at its top. Height is normalized to the authored range.
float groundFogDensityIntegral(float height) {
    float h = clamp(height, 0.0, 1.0);
    return min(height, 0.0) + h * (1.0 - h * h * (1.0 - 0.5 * h));
}

float calculateGroundFogOpticalDepth(vec3 position) {
    if (uboGlobal.groundFogDensity <= 0.0)
        return 0.0;

    float heightRange = -max(uboGlobal.groundFogHeight, 1.0);
    float fromHeight = (uboGlobal.cameraPos.y + uboGlobal.groundFogHeightOffset) / heightRange;
    float toHeight = (position.y + uboGlobal.groundFogHeightOffset) / heightRange;
    float heightDifference = toHeight - fromHeight;
    float density;
    if (abs(heightDifference) < 0.001) {
        // Avoid cancellation for nearly horizontal rays; midpoint density is sufficient here.
        density = 1.0 - smoothstep(0.0, 1.0, (fromHeight + toHeight) * 0.5);
    } else {
        density = (groundFogDensityIntegral(toHeight) - groundFogDensityIntegral(fromHeight)) / heightDifference;
    }

    // Density specifies optical depth over 1500 world units at full layer density.
    float extinction = uboGlobal.groundFogDensity / 1500.0;
    return extinction * distance(position, uboGlobal.cameraPos) * clamp(density, 0.0, 1.0);
}

float groundFogNightBlend() {
    return uboSky.enabled ? 1.0 - smoothstep(sin(radians(-6.0)), 0.0, uboSky.sunDir.y) : 0.0;
}

float skyGroundFogTransmittance(vec3 viewDir) {
    if (!uboGlobal.integratedGroundFog || uboGlobal.groundFogDensity <= 0.0)
        return 1.0;
    // A finite local layer avoids infinite extinction along horizontal sky rays.
    vec3 end = uboGlobal.cameraPos + viewDir * max(uboGlobal.drawDistance, 1.0) * TILE_SIZE;
    float depth = calculateGroundFogOpticalDepth(end);
    if (uboSky.enabled)
        depth *= mix(0.88, 0.352, groundFogNightBlend());
    return exp(-depth);
}

float calculateFogAmount(vec3 position) {
    if (uboGlobal.fogDepth == 0)
        return 0.f;

    float drawDistance2 = uboGlobal.drawDistance * TILE_SIZE;

    // the client draws one less tile to the north and east than it does to the south
    // and west, so subtract a tile's width from the north and east edges.
    float fogWest = max(FOG_SCENE_EDGE_MIN, uboGlobal.cameraPos.x - drawDistance2);
    float fogEast = min(FOG_SCENE_EDGE_MAX, uboGlobal.cameraPos.x + drawDistance2 - TILE_SIZE);
    float fogSouth = max(FOG_SCENE_EDGE_MIN, uboGlobal.cameraPos.z - drawDistance2);
    float fogNorth = min(FOG_SCENE_EDGE_MAX, uboGlobal.cameraPos.z + drawDistance2 - TILE_SIZE);

    // Calculate distance from the scene edge
    float xDist = min(position.x - fogWest, fogEast - position.x);
    float zDist = min(position.z - fogSouth, fogNorth - position.z);
    float nearestEdgeDistance = min(xDist, zDist);
    float secondNearestEdgeDistance = max(xDist, zDist);
    float fogDistance = nearestEdgeDistance
        - FOG_CORNER_ROUNDING * TILE_SIZE * max(0,
            (nearestEdgeDistance + FOG_CORNER_ROUNDING_SQUARED)
            / (secondNearestEdgeDistance + FOG_CORNER_ROUNDING_SQUARED)
        );

    // This is different from the GPU plugin, and seems to have worked this way from the start
    float edgeFogAmount = fogFactorLinear(fogDistance, 0, 5 * TILE_SIZE) * uboGlobal.useFog;

    // Use a combination of two different methods of calculating distance fog.
    // The is super arbitrary and is only eyeballed to provide a similar overall
    // appearance between equal fog depths at different draw distances.

    float fogStart1 = drawDistance2 * 0.85;
    float distance1 = length(uboGlobal.cameraPos.xz - position.xz);
    float distanceFogAmount1 = clamp((distance1 - fogStart1) / (drawDistance2 * .15), 0, 1);

    float minFogStart = 0.0;
    float maxFogStart = 0.3;
    drawDistance2 = min(uboGlobal.drawDistance, 90) * TILE_SIZE;
    float fogDepthMultiplier = clamp(uboGlobal.fogDepth, 0, 1000) / 1000.0;
    float fogStart2 = (maxFogStart - (fogDepthMultiplier * (maxFogStart - minFogStart))) * drawDistance2;
    float camToVertex = length(uboGlobal.cameraPos - vec3(position.x, (position.y + uboGlobal.cameraPos.y) / 2, position.z));
    float distance2 = max(camToVertex - fogStart2, 0) / max(drawDistance2 - fogStart2, 1);
    float density = uboGlobal.fogDepth / 100.0;
    float distanceFogAmount2 = 1 - clamp(exp(-distance2 * density), 0, 1);

    // Combine distance fogs
    float distanceFogAmount = max(distanceFogAmount1, distanceFogAmount2);

    // Combine distance fog with edge fog
    return max(distanceFogAmount, edgeFogAmount);
}
