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

#include VERSION_HEADER

#include THREAD_COUNT
#include FACES_PER_THREAD

shared int totalNum[12]; // number of faces with a given priority
shared int totalDistance[12]; // sum of distances to faces of a given priority

shared int totalMappedNum[18]; // number of faces with a given adjusted priority

shared int min10; // minimum distance to a face of priority 10
shared int renderPris[THREAD_COUNT * FACES_PER_THREAD]; // priority for face draw order

#include <uniforms/compute.glsl>

#include <comp_common.glsl>

layout(local_size_x = THREAD_COUNT) in;

#include <comp_sorting_utils.glsl>
#include <priority_render.glsl>

void main() {
    uint groupId = gl_WorkGroupID.x;
    uint localId = gl_LocalInvocationID.x * FACES_PER_THREAD;
    const ModelInfo minfo = ol[groupId];

    if (localId == 0) {
        min10 = 6000;
        for (int i = 0; i < 12; ++i) {
            totalNum[i] = 0;
            totalDistance[i] = 0;
        }
        for (int i = 0; i < 18; ++i) {
            totalMappedNum[i] = 0;
        }
    }

    ObjectWindSample windSample;
    #if WIND_DISPLACEMENT
    {
        float modelNoise = noise((vec2(minfo.x, minfo.z) + vec2(windOffset)) * WIND_DISPLACEMENT_NOISE_RESOLUTION);
        float angle = modelNoise * (PI / 2.0);
        float c = cos(angle);
        float s = sin(angle);
        float y = minfo.y >> 16;
        float height = minfo.y & 0xffff;

        windSample.direction = normalize(vec3(windDirectionX * c + windDirectionZ * s, 0.0, -windDirectionX * s + windDirectionZ * c));
        windSample.heightBasedStrength = saturate((abs(y) + height) / windCeiling) * windStrength;
        windSample.displacement = windSample.direction.xyz * (windSample.heightBasedStrength * modelNoise);
    }
    #endif

    // Ensure all invocations have their shared variables initialized
    barrier();

    int prio[FACES_PER_THREAD];
    int dis[FACES_PER_THREAD];

    for (int i = 0; i < FACES_PER_THREAD; i++)
        add_face_prio_distance(localId + i, minfo, prio[i], dis[i]);

    barrier();

    int prioAdj[FACES_PER_THREAD];
    int idx[FACES_PER_THREAD];
    for (int i = 0; i < FACES_PER_THREAD; i++)
        idx[i] = map_face_priority(localId + i, minfo, prio[i], dis[i], prioAdj[i]);

    barrier();

    for (int i = 0; i < FACES_PER_THREAD; i++)
        insert_face(localId + i, minfo, prioAdj[i], dis[i], idx[i]);

    barrier();

    for (int i = 0; i < FACES_PER_THREAD; i++)
        sort_and_insert(localId + i, minfo, prioAdj[i], dis[i], windSample);
}
