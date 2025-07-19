/*
 * Copyright (c) 2018, Adam <Adam@sigterm.info>
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

#include utils/constants.glsl

layout(binding = 3) uniform isampler3D tileHeightMap;

// Calculate adjusted priority for a face with a given priority, distance, and
// model global min10 and face distance averages. This allows positioning faces
// with priorities 10/11 into the correct 'slots' resulting in 18 possible
// adjusted priorities
uint priority_map(uint p, uint distance, uint avg1, uint avg2, uint avg3) {
    // (10, 11)  0  1  2  (10, 11)  3  4  (10, 11)  5  6  7  8  9  (10, 11)
    //   0   1   2  3  4    5   6   7  8    9  10  11 12 13 14 15   16  17
    switch (p) {
        case 0: return 2;
        case 1: return 3;
        case 2: return 4;
        case 3: return 7;
        case 4: return 8;
        case 5: return 11;
        case 6: return 12;
        case 7: return 13;
        case 8: return 14;
        case 9: return 15;
        case 10:
        if (distance > avg1) {
            return 0;
        } else if (distance > avg2) {
            return 5;
        } else if (distance > avg3) {
            return 9;
        } else {
            return 16;
        }
        case 11:
        if (distance > avg1 && min10 > avg1) {
            return 1;
        } else if (distance > avg2 && (min10 > avg1 || min10 > avg2)) {
            return 6;
        } else if (distance > avg3 && (min10 > avg1 || min10 > avg2 || min10 > avg3)) {
            return 10;
        } else {
            return 17;
        }
        default:
        // this can't happen unless an invalid priority is sent. just assume 0.
        return 0;
    }
}

// calculate the number of faces with a lower adjusted priority than
// the given adjusted priority
uint count_prio_offset(uint priority) {
    // this shouldn't ever be outside of (0, 17) because it is the return value from priority_map
    priority = min(priority, 17u);
    uint total = 0;
    for (int i = 0; i < priority; i++)
        total += totalMappedNum[i];
    return total;
}

void add_face_prio_distance(const uint localId, const ModelInfo minfo, const vec3 modelPos, out uint prio, out uint dis) {
    if (localId < minfo.size) {
        uint offset = minfo.offset & 0x3FFFFFFF;
        int orientation = minfo.flags & 0x7ff;

        vec3 vertices[3];
        uint ahsl;
        for (int i = 0; i < 3; i++) {
            VertexData data = vb[offset + localId * 3 + i];
            ahsl = data.ahsl;
            // rotate for model orientation
            vertices[i] = rotate(data.pos, orientation);
        }

        // calculate distance to face
        prio = ahsl >> 16u & 0xFu; // all vertices on the face have the same priority
        dis = face_distance(vertices[0], vertices[1], vertices[2]);

        // if the face is not culled, it is calculated into priority distance averages
        if (face_visible(vertices[0], vertices[1], vertices[2], modelPos)) {
            atomicAdd(totalNum[prio], 1);
            atomicAdd(totalDistance[prio], dis);

            // calculate minimum distance to any face of priority 10 for positioning the 11 faces later
            if (prio == 10) {
                atomicMin(min10, dis);
            }
        }
    }
}

void map_face_priority(const uint localId, const ModelInfo minfo, uint thisPriority, uint thisDistance, out uint prio, out uint prioIdx) {
    // Compute average distances for 0/2, 3/4, and 6/8
    if (localId < minfo.size) {
        uint avg1 = 0;
        uint avg2 = 0;
        uint avg3 = 0;

        if (totalNum[1] > 0 || totalNum[2] > 0) {
            avg1 = (totalDistance[1] + totalDistance[2]) / (totalNum[1] + totalNum[2]);
        }

        if (totalNum[3] > 0 || totalNum[4] > 0) {
            avg2 = (totalDistance[3] + totalDistance[4]) / (totalNum[3] + totalNum[4]);
        }

        if (totalNum[6] > 0 || totalNum[8] > 0) {
            avg3 = (totalDistance[6] + totalDistance[8]) / (totalNum[6] + totalNum[8]);
        }

        prio = priority_map(thisPriority, thisDistance, avg1, avg2, avg3);
        prioIdx = atomicAdd(totalMappedNum[prio], 1);
    } else {
        prio = 0;
        prioIdx = 0;
    }
}

void insert_face(const uint localId, const ModelInfo minfo, uint adjPrio, uint distance, uint prioIdx) {
    if (localId < minfo.size) {
        // calculate base offset into renderPris based on number of faces with a lower priority
        uint baseOff = count_prio_offset(adjPrio);
        // the furthest faces draw first, and have the highest priority.
        // if two faces have the same distance, the one with the
        // lower id draws first.
        renderPris[baseOff + prioIdx] = distance << 16u | ~localId & 0xffffu;
    }
}

int tile_height(int z, int x, int y) {
    #define ESCENE_OFFSET 40 // (184-104)/2
    return texelFetch(tileHeightMap, ivec3(x + ESCENE_OFFSET, y + ESCENE_OFFSET, z), 0).r << 3;
}

void hillskew_vertex(inout vec3 v, int hillskewMode, float modelPosY, float modelHeight, int plane) {
    float heightFrac = abs(v.y - modelPosY) / modelHeight;
    if (hillskewMode == HILLSKEW_TILE_SNAPPING && heightFrac > HILLSKEW_TILE_SNAPPING_BLEND)
        return; // Only apply tile snapping, which will only be applied to vertices close to the bottom of the model

    int x = int(v.x);
    int z = int(v.z);
    int px = x & 127;
    int pz = z & 127;
    int sx = x >> 7;
    int sz = z >> 7;
    int h1 = (px * tile_height(plane, sx + 1, sz) + (128 - px) * tile_height(plane, sx, sz)) >> 7;
    int h2 = (px * tile_height(plane, sx + 1, sz + 1) + (128 - px) * tile_height(plane, sx, sz + 1)) >> 7;
    int h3 = (pz * h2 + (128 - pz) * h1) >> 7;

    if ((hillskewMode & HILLSKEW_TILE_SNAPPING) != 0 && heightFrac <= HILLSKEW_TILE_SNAPPING_BLEND) {
        v.y = mix(h3, v.y, heightFrac / HILLSKEW_TILE_SNAPPING_BLEND); // Blend tile snapping
    } else {
        v.y += h3 - modelPosY; // Hillskew the whole model
    }
}

void undoVanillaShading(inout uint ahsl, const vec3 unrotatedNormal) {
    const vec3 LIGHT_DIR_MODEL = vec3(0.57735026, 0.57735026, 0.57735026);
    // subtracts the X lowest lightness levels from the formula.
    // helps keep darker colors appropriately dark
    const int IGNORE_LOW_LIGHTNESS = 3;
    // multiplier applied to vertex' lightness value.
    // results in greater lightening of lighter colors
    const float LIGHTNESS_MULTIPLIER = 3.f;
    // the minimum amount by which each color will be lightened
    const int BASE_LIGHTEN = 10;

    uint saturation = ahsl >> 7u & 0x7u;
    uint lightness = ahsl & 0x7Fu;
    float vanillaLightDotNormals = dot(LIGHT_DIR_MODEL, unrotatedNormal);
    if (vanillaLightDotNormals > 0) {
        float lighten = max(0, lightness - IGNORE_LOW_LIGHTNESS);
        lightness += int((lighten * LIGHTNESS_MULTIPLIER + BASE_LIGHTEN - lightness) * vanillaLightDotNormals);
    }
    int maxLightness;
    #if LEGACY_GREY_COLORS
    maxLightness = 55;
    #else
    maxLightness = int(127 - 72 * pow(saturation / 7., .05));
    #endif
    lightness = min(lightness, maxLightness);
    ahsl &= ~0x7Fu;
    ahsl |= lightness;
}

vec3 applyCharacterDisplacement(vec3 characterPos, vec2 vertPos, float height, float strength, inout float offsetAccum) {
    vec2 offset = vertPos - characterPos.xy;
    float offsetLen = length(offset);

    if (offsetLen >= characterPos.z)
        return vec3(0);

    float offsetFrac = saturate(1.0 - (offsetLen / characterPos.z));
    float displacementFrac = offsetFrac * offsetFrac;

    vec3 horizontalDisplacement = normalize(vec3(offset.x, 0.0, offset.y)) * (height * strength * displacementFrac * 0.5);
    vec3 verticalDisplacement = vec3(0.0, height * strength * displacementFrac, 0.0);

    offsetAccum += offsetFrac;

    return mix(horizontalDisplacement, verticalDisplacement, offsetFrac);
}

vec3 applyWindDisplacement(const ObjectWindSample windSample, int vertexFlags, float height, vec3 worldPos,
    in vec3 vertex, in vec3 normal
) {
    vec3 displacement = vec3(0);
    int windDisplacementMode = (vertexFlags >> MATERIAL_FLAG_WIND_SWAYING) & 0x7;
    if (windDisplacementMode <= WIND_DISPLACEMENT_DISABLED)
        return displacement;

    vec3 strength = vec3(0);
    for (int i = 0; i < 3; i++)
        strength[i] = saturate(abs(vertex.y) / height);

    #if WIND_DISPLACEMENT
    if (windDisplacementMode >= WIND_DISPLACEMENT_VERTEX) {
        const float VertexSnapping = 150.0; // Snap so vertices which are almost overlapping will obtain the same noise value
        const float VertexDisplacementMod = 0.2; // Avoid over stretching which can cause issues in ComputeUVs
        vec3 windNoise;
        for (int i = 0; i < 3; i++)
            windNoise[i] = mix(-0.5, 0.5,
                noise((snap(vertex, VertexSnapping).xz + vec2(windOffset)) * WIND_DISPLACEMENT_NOISE_RESOLUTION));

        if (windDisplacementMode == WIND_DISPLACEMENT_VERTEX_WITH_HEMISPHERE_BLEND) {
            const float minDist = 50;
            const float blendDist = 10.0;

            vec3 distBlend;
            vec3 heightFade;
            for (int i = 0; i < 3; i++) {
                distBlend[i] = saturate(((abs(vertex.x) + abs(vertex.z)) - minDist) / blendDist);
                heightFade[i] = saturate((strength[i] - 0.5) / 0.2);
                strength[i] *= mix(0.0, mix(distBlend[i], 1.0, heightFade[i]), step(0.3, strength[i]));
            }
        } else {
            if (windDisplacementMode == WIND_DISPLACEMENT_VERTEX_JIGGLE) {
                vec3 vertSkew[3];
                for (int i = 0; i < 3; i++) {
                    vertSkew[i] = safe_normalize(cross(normal, vec3(0, 1, 0)));
                    displacement = ((windNoise[i] * (windSample.heightBasedStrength * strength[i]) * 0.5) * vertSkew[i]);

                    vertSkew[i] = safe_normalize(cross(normal, vec3(1, 0, 0)));
                    displacement += (((1.0 - windNoise[i]) * (windSample.heightBasedStrength * strength[i]) * 0.5) * vertSkew[i]);
                }
            } else {
                for (int i = 0; i < 3; i++) {
                    displacement = ((windNoise[i] * (windSample.heightBasedStrength * strength[i] * VertexDisplacementMod)) * windSample.direction);
                    strength[i] = saturate(strength[i] - VertexDisplacementMod);
                }
            }
        }
    }
    #endif

    #if CHARACTER_DISPLACEMENT
    if (windDisplacementMode == WIND_DISPLACEMENT_OBJECT) {
        vec2 worldVerts[3];
        for (int i = 0; i < 3; i++)
            worldVerts[i] = (worldPos + vertex).xz;

        float fractAccum = 0.0;
        for (int j = 0; j < characterPositionCount; j++) {
            for (int i = 0; i < 3; i++)
                displacement += applyCharacterDisplacement(characterPositions[j], worldVerts[i], height, strength[i], fractAccum);
            if (fractAccum >= 2.0)
                break;
        }
    }
    #endif

    #if WIND_DISPLACEMENT
    if (windDisplacementMode != WIND_DISPLACEMENT_VERTEX_JIGGLE) {
        // Object Displacement
        for (int i = 0; i < 3; i++)
            displacement += windSample.displacement * strength[i];
    }
    #endif

    return displacement;
}

void sort_and_insert(uint localId, const ModelInfo minfo, uint thisPriority, uint thisDistance, const ObjectWindSample windSample) {
    if (localId >= minfo.size)
        return;

    uint offset = minfo.offset & 0x3FFFFFFF;
    uint offsetFlags = minfo.offset >> 30 & 3;
    bool skipNormals = (offsetFlags & 1u) != 0;
    bool skipUvs = (offsetFlags & 2u) != 0;
    uint normalOffset = offset + minfo.size * 3;
    uint uvOffset = offset + minfo.size * 3 * (2 - offsetFlags);
    uint outOffset = minfo.idx;
    int flags = minfo.flags;
    vec3 pos = vec3(minfo.x, minfo.y >> 16, minfo.z);
    float height = minfo.y & 0xffff;
    int orientation = flags & 0x7ff;

    // we only have to order faces against others of the same priority
    const uint priorityOffset = count_prio_offset(thisPriority);
    const uint numOfPriority = totalMappedNum[thisPriority];
    const uint start = priorityOffset; // index of first face with this priority
    const uint end = priorityOffset + numOfPriority; // index of last face with this priority
    const uint renderPriority = thisDistance << 16u | ~localId & 0xffffu;
    uint myOffset = priorityOffset;

    // calculate position this face will be in
    for (uint i = start; i < end; ++i)
        if (renderPriority > renderPris[i])
            ++myOffset;

    vec4 flatNormal = vec4(0);

    #if UNDO_VANILLA_SHADING
        if ((vb[offset + localId * 3].ahsl >> 20 & 1) == 0)
            skipNormals = true;
    #endif

    // Compute flat normal if necessary, and rotate it back to match unrotated normals
    if (skipNormals) {
        vec3 vertices[3];
        for (int i = 0; i < 3; i++)
            vertices[i] = vb[offset + localId * 3 + i].pos;
        flatNormal = vec4(normalize(cross(vertices[0] - vertices[1], vertices[0] - vertices[2])), 0);
    }

    for (int i = 0; i < 3; i++) {
        VertexData vertex = vb[offset + localId * 3 + i];
        vec4 normal = flatNormal;

        // rotate for model orientation
        vertex.pos = rotate(vertex.pos, orientation);

        // Apply displacement after orientation
        int vertexFlags = skipUvs ? 0 : uvb[uvOffset + localId * 3].materialFlags;
        vec3 displacement = applyWindDisplacement(windSample, vertexFlags, height, pos, vertex.pos, normal.xyz);
        vertex.pos += displacement;

        // Shift to world space
        vertex.pos += pos;

        // apply hillskew
        int plane = flags >> 24 & 3;
        int hillskewFlags = flags >> 26 & 1;
        if ((vertexFlags >> MATERIAL_FLAG_TERRAIN_VERTEX_SNAPPING & 1) == 1)
            hillskewFlags |= HILLSKEW_TILE_SNAPPING;
        if (hillskewFlags != HILLSKEW_NONE)
            hillskew_vertex(vertex.pos, hillskewFlags, pos.y, height, plane);

        if (!skipNormals) {
            normal = normalb[normalOffset + localId * 3 + i];
            normal.xyz = normalize(normal.xyz);
        }

        #if UNDO_VANILLA_SHADING
            undoVanillaShading(vertex.ahsl, normal.xyz);
        #endif

        renderBuffer[outOffset + myOffset * 3 + i].vertex = vertex;

        normal = rotate(normal, orientation);
        renderBuffer[outOffset + myOffset * 3 + i].normal = normal;

        UVData uv = UVData(vec3(0), 0);
        if (!skipUvs) {
            uv = uvb[uvOffset + localId * 3 + i];

            if ((vertexFlags >> MATERIAL_FLAG_VANILLA_UVS & 1) == 1) {
                // Rotate the texture triangles to match model orientation
                uv.uvw = rotate(uv.uvw, orientation);

                // Apply displacement after orientation
                uv.uvw += displacement;

                // Shift texture triangles to world space
                uv.uvw += pos;

                // For vanilla UVs, the first 3 components represent a position vector
                if (hillskewFlags != HILLSKEW_NONE)
                    hillskew_vertex(uv.uvw, hillskewFlags, pos.y, height, plane);
            }
        }
        renderBuffer[outOffset + myOffset * 3 + i].uv = uv;
    }
}
