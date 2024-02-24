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

#define SAMPLING_MITCHELL 1
#define SAMPLING_CATROM 2
#define SAMPLING_XBR 3

uniform sampler2D uiTexture;
uniform int samplingMode;
uniform ivec2 sourceDimensions;
uniform ivec2 targetDimensions;
uniform float colorBlindnessIntensity;
uniform vec4 alphaOverlay;

#define TRANSPARENCY_COLOR_PACKED 12345678
const ivec3 TRANSPARENCY_COLOR = ivec3(
    TRANSPARENCY_COLOR_PACKED >> 16 & 0xFF,
    TRANSPARENCY_COLOR_PACKED >> 8 & 0xFF,
    TRANSPARENCY_COLOR_PACKED & 0xFF
);

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

vec4 alphaBlend(vec4 src, vec4 dst) {
     return dst * (1 - src.a) + src;
}

vec2 getMinimapLocation(bool isResized) {
    float offsetY = isResized ? 1.0 : 0.0;
    if (!isResized) {
        return vec2(0, 0);
    }
    return vec2(sourceDimensions.x - 159, sourceDimensions.y - 9 - 152.0 + offsetY);
}

void main() {
    // Size of the red square
    vec2 squareSize = vec2(150.0, 150.0); // Adjust these values based on your needs

    // Coordinates for the center of the top right corner
    vec2 topRightCenter = vec2(1.0, 1.0) - squareSize * 0.5;

    // Calculate the position of the red square in screen space
    vec2 squarePosition = getMinimapLocation(true);

    // Check if the current fragment is inside the red square
    bool insideRedSquare = (
        gl_FragCoord.x >= squarePosition.x &&
        gl_FragCoord.x <= (squarePosition.x + squareSize.x) &&
        gl_FragCoord.y >= squarePosition.y &&
        gl_FragCoord.y <= (squarePosition.y + squareSize.y)
    );

    #if SHADOW_MAP_OVERLAY
    {
        vec2 uv = (gl_FragCoord.xy - shadowMapOverlayDimensions.xy) / shadowMapOverlayDimensions.zw;
        if (0 <= uv.x && uv.x <= 1 && 0 <= uv.y && uv.y <= 1) {
            FragColor = texture(shadowMap, uv);
            return;
        }
    }
    #endif

    #if UI_SCALING_MODE == SAMPLING_MITCHELL || UI_SCALING_MODE == SAMPLING_CATROM
    vec4 c = textureCubic(uiTexture, TexCoord);
    #elif UI_SCALING_MODE == SAMPLING_XBR
    vec4 c = textureXBR(uiTexture, TexCoord, xbrTable, ceil(1.0 * targetDimensions.x / sourceDimensions.x));
    #else // NEAREST or LINEAR, which uses GL_TEXTURE_MIN_FILTER/GL_TEXTURE_MAG_FILTER to affect sampling
    vec4 c = texture(uiTexture, TexCoord);
    c = replaceTransparency(c); // TODO: Fix bilinear by implementing it in software
    #endif

    c.rgb /= c.a; // Undo vanilla premultiplied alpha

    c = alphaBlend(c, alphaOverlay);
    c.rgb = colorBlindnessCompensation(c.rgb);

    vec4 redSquareColor = vec4(1.0, 0.0, 0.0, 1.0); // Red color with alpha of 1.0

    // Blend redSquareColor under the minimap color
    if (insideRedSquare) {
      c = alphaBlend(c, redSquareColor);

    }

    c.rgb /= c.a; // Undo vanilla premultiplied alpha

    c = alphaBlend(c, alphaOverlay);
    c.rgb = colorBlindnessCompensation(c.rgb);

    FragColor = c;
}
