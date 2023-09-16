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

layout (location = 0) in vec3 vPos;
layout (location = 1) in vec2 vUv;

uniform mat4 projectionMatrix;
uniform vec2 viewportDimensions;

out vec3 fRay;
out vec2 fUv;

void main()
{
    gl_Position = vec4(vPos, 1.0);
    vec2 uv = (vUv * 2 - 1) * vec2(1, -1);

    const float eps = .001;
    mat4 invProj = inverse(projectionMatrix);
    vec4 nearPos = invProj * vec4(uv, 0, 1);
    vec4 farPos = invProj * vec4(uv, 1 - eps, 1);
    nearPos.xyz /= nearPos.w;
    farPos.xyz /= farPos.w;
    vec3 ray = normalize(farPos.xyz - nearPos.xyz);

    fRay = ray;
    fUv = vUv;
}
