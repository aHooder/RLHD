/*
 * Copyright (c) 2024, Hooder <ahooder@protonmail.com>
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

vec3 sampleWaterReflection(vec3 flatR, vec3 R) {
    // Only use the reflection map when enabled and the height difference is negligible
    if (!waterReflectionEnabled || abs(IN.position.y - waterHeight) > 32)
        return srgbToLinear(fogColor);

    // TODO: use actual viewport size here
    vec2 screenSize = vec2(textureSize(waterReflectionMap, 0)) / PLANAR_REFLECTION_RESOLUTION;
    vec2 texelSize = 1 / screenSize;
    vec2 uv = gl_FragCoord.xy / screenSize;

    float dist = length(IN.position - cameraPos);
    float distortionFactor = 1 - exp(-dist * .0004);

    vec3 uvX = normalize(cross(flatR * vec3(1, 0, 1), flatR));
    vec3 uvY = cross(uvX, flatR);
    float x = dot(R, uvX);
    float y = dot(R, uvY);
    vec2 distortion = vec2(x, y) * 50 * distortionFactor;
    // TODO: Don't distort too close to the shore
    float shoreLineMask = 1.0 - dot(IN.texBlend, vec3(vColor[0].x, vColor[1].x, vColor[2].x));
    distortion *= 1.4 - (shoreLineMask *1.54); // safety factor to remove artifacts
    uv += texelSize * distortion;

    uv = clamp(uv, texelSize, 1 - texelSize);

    // This will be linear or sRGB depending on the linear alpha blending setting
    vec3 c = texture(waterReflectionMap, uv, -1).rgb;
//    c = textureBicubic(waterReflectionMap, uv).rgb;

    #if !LINEAR_ALPHA_BLENDING
    // When linear alpha blending is on, the texture is in sRGB, and OpenGL will automatically convert it to linear
    c = srgbToLinear(c);
    #endif
    return c;
}