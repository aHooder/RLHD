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

#version 330

#include UI_SCALING_MODE

// Constants
#define SAMPLING_MITCHELL 1
#define SAMPLING_CATROM 2
#define SAMPLING_XBR 3
#define TRANSPARENCY_COLOR_PACKED 12345678

// Uniforms
uniform sampler2D uiTexture;
uniform sampler2D minimapMask;
uniform sampler2D minimapImage;
uniform int samplingMode;
uniform ivec2 sourceDimensions;
uniform ivec2 targetDimensions;
uniform ivec2 minimapLocation;
uniform ivec2 playerLocation;
uniform float colorBlindnessIntensity;
uniform vec4 alphaOverlay;

const ivec3 TRANSPARENCY_COLOR = ivec3(
    TRANSPARENCY_COLOR_PACKED >> 16 & 0xFF,
    TRANSPARENCY_COLOR_PACKED >> 8 & 0xFF,
    TRANSPARENCY_COLOR_PACKED & 0xFF
);

// Function to replace transparency with black
vec4 replaceTransparency(vec4 c) {
    return ivec3(round(c.rgb * 0xFF)) == TRANSPARENCY_COLOR ? vec4(0) : c;
}

#include scaling/bicubic.glsl
#include utils/constants.glsl
#include utils/color_blindness.glsl

#if SHADOW_MAP_OVERLAY
uniform sampler2D shadowMap;
uniform ivec4 shadowMapOverlayDimensions;
#endif

#if UI_SCALING_MODE == SAMPLING_XBR
#include scaling/xbr_lv2_frag.glsl

in XBRTable xbrTable;
#endif

in vec2 TexCoord;
out vec4 FragColor;

// Function for alpha blending
vec4 alphaBlend(vec4 src, vec4 dst) {
     return dst * (1 - src.a) + src;
}

// Function to get minimap location in screen space
ivec2 getMinimapLocation() {
    return ivec2(minimapLocation.x, sourceDimensions.y - minimapLocation.y - 152);
}

vec4 applyMinimapOverlay(vec4 originalColor);

void main() {
       vec2 playerPos = vec2(playerLocation.x, playerLocation.y - 4) - vec2(106.0, 106.0);
    // Original color sampling
    #if SHADOW_MAP_OVERLAY
    vec2 uv = (gl_FragCoord.xy - shadowMapOverlayDimensions.xy) / shadowMapOverlayDimensions.zw;
    if (0 <= uv.x && uv.x <= 1 && 0 <= uv.y && uv.y <= 1) {
        FragColor = texture(shadowMap, uv);
        return;
    }
    #endif

    #if UI_SCALING_MODE == SAMPLING_MITCHELL || UI_SCALING_MODE == SAMPLING_CATROM
    vec4 originalColor = textureCubic(uiTexture, TexCoord);
    #elif UI_SCALING_MODE == SAMPLING_XBR
    vec4 originalColor = textureXBR(uiTexture, TexCoord, xbrTable, ceil(1.0 * targetDimensions.x / sourceDimensions.x));
    #else
    vec4 originalColor = texture(uiTexture, TexCoord);
    originalColor = replaceTransparency(originalColor);
    #endif

    // Apply transition color overlay before UI
    originalColor = alphaBlend(originalColor, alphaOverlay);

    originalColor = applyMinimapOverlay(originalColor);

    originalColor.rgb /= originalColor.a;
    originalColor.rgb = colorBlindnessCompensation(originalColor.rgb);

    FragColor = originalColor;
}

vec4 applyMinimapOverlay(vec4 originalColor) {
    ivec2 fragCoordInt = ivec2(gl_FragCoord.xy);

    ivec2 minimapPosition = getMinimapLocation();
    ivec2 minimapImageSize = ivec2(700, 700);

    // Align minimapPosition to the pixel grid
    minimapPosition = ivec2(
        int(floor(minimapPosition.x + 0.5)),
        int(floor(minimapPosition.y + 0.5))
    );

    bool insideMinimapBounds = (
        fragCoordInt.x >= minimapPosition.x &&
        fragCoordInt.x <= (minimapPosition.x + minimapImageSize.x) &&
        fragCoordInt.y >= minimapPosition.y &&
        fragCoordInt.y <= (minimapPosition.y + minimapImageSize.y)
    );

    if (insideMinimapBounds) {
        ivec2 playerPos = ivec2(
            int(playerLocation.x),
            int(playerLocation.y - 4)
        ) - ivec2(
            int(106.0 - 32.0),
            int(106.0)
        );

        ivec2 playerLocInt = ivec2((gl_FragCoord.xy - minimapPosition + playerPos));

        vec4 minimapImageColor = texelFetch(minimapImage, playerLocInt, 0);

        ivec2 textureCoordInt = ivec2((gl_FragCoord.xy - minimapPosition));

        textureCoordInt = ivec2(clamp(textureCoordInt.x, 0, textureSize(minimapMask, 0).x - 1),
                        clamp(textureCoordInt.y, 0, textureSize(minimapMask, 0).y - 1));
        vec4 minimapMaskColor = texelFetch(minimapMask, textureCoordInt, 0);

        // Invert the mask
        originalColor = alphaBlend(originalColor, minimapImageColor * (1.0 - minimapMaskColor.a));
    }

    return originalColor;
}