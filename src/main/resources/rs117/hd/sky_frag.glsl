/*
 * Copyright (c) 2023, Hooder <ahooder@protonmail.com>
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
#version 330

uniform sampler2D skyTexture;

#include uniforms/sky.glsl

#include utils/sky.glsl
#include utils/color_utils.glsl
#include utils/color_blindness.glsl
#include utils/aces.glsl

in vec3 fRay;
in vec2 fUv;

out vec4 FragColor;

void main() {
//    vec2 uv = (fUv * 2 - 1) * vec2(1, -1);
//    const float eps = .001;
//    mat4 inverseProjectionMatrix = inverse(projectionMatrix);
//    vec4 nearPos = inverseProjectionMatrix * vec4(uv, 0, 1);
//    vec4 farPos = inverseProjectionMatrix * vec4(uv, 1 - eps, 1);
//    nearPos.xyz /= nearPos.w;
//    farPos.xyz /= farPos.w;
//    vec3 ray = normalize(farPos.xyz - nearPos.xyz);

    vec3 ray = normalize(fRay);
    vec3 c = ray;

//    float azimuth = fract((atan(ray.x, ray.z) + TAU) / TAU);
//    float altitude = acos(-ray.y) / PI;
//    vec2 uv = vec2(azimuth, altitude);

    float azimuth = atan(ray.x, ray.z);
    float altitude = acos(-ray.y);
    vec2 uv = vec2(azimuth, altitude) / PI;
    uv.x = (uv.x + 1) / 2;

    c = vec3(uv, 0);

    c = sampleSky(ray);

    c = linearToSrgb(c);

    c = colorBlindnessCompensation(c.rgb);
    c = pow(c, vec3(gammaCorrection));
    FragColor = vec4(c, 1);
}
