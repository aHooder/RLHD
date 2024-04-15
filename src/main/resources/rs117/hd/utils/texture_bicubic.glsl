#pragma once

// Adapted from code written by theagentd, from https://web.archive.org/web/20200417234120/http://www.java-gaming.org/index.php?topic=35123.0

vec4 cubic(float v){
    vec4 n = vec4(1.0, 2.0, 3.0, 4.0) - v;
    vec4 s = n * n * n;
    float x = s.x;
    float y = s.y - 4.0 * s.x;
    float z = s.z - 4.0 * s.y + 6.0 * s.x;
    float w = 6.0 - x - y - z;
    return vec4(x, y, z, w) * (1.0/6.0);
}

vec4 textureBicubic(sampler2D sampler, vec2 texCoords) {
    vec2 texSize = textureSize(sampler, 0).xy;
    vec2 invTexSize = 1.0 / texSize;

    texCoords = texCoords * texSize - 0.5;

    vec2 fxy = fract(texCoords);
    texCoords -= fxy;

    vec4 xcubic = cubic(fxy.x);
    vec4 ycubic = cubic(fxy.y);

    vec4 c = texCoords.xxyy + vec2 (-0.5, +1.5).xyxy;

    vec4 s = vec4(xcubic.xz + xcubic.yw, ycubic.xz + ycubic.yw);
    vec4 offset = c + vec4 (xcubic.yw, ycubic.yw) / s;

    offset *= invTexSize.xxyy;

    vec4 sample0 = texture(sampler, offset.xz);
    vec4 sample1 = texture(sampler, offset.yz);
    vec4 sample2 = texture(sampler, offset.xw);
    vec4 sample3 = texture(sampler, offset.yw);

    float sx = s.x / (s.x + s.y);
    float sy = s.z / (s.z + s.w);

    return mix(
        mix(sample3, sample2, sx),
        mix(sample1, sample0, sx),
        sy
    );
}

vec4 textureBicubicSrgb(sampler2D sampler, vec2 texCoords) {
    vec2 texSize = textureSize(sampler, 0).xy;
    vec2 invTexSize = 1.0 / texSize;

    texCoords = texCoords * texSize - 0.5;

    vec2 fxy = fract(texCoords);
    texCoords -= fxy;

    vec4 xcubic = cubic(fxy.x);
    vec4 ycubic = cubic(fxy.y);

    vec4 c = texCoords.xxyy + vec2 (-0.5, +1.5).xyxy;

    vec4 s = vec4(xcubic.xz + xcubic.yw, ycubic.xz + ycubic.yw);
    vec4 offset = c + vec4 (xcubic.yw, ycubic.yw) / s;

    offset *= invTexSize.xxyy;

    vec4 sample0 = linearToSrgb(texture(sampler, offset.xz));
    vec4 sample1 = linearToSrgb(texture(sampler, offset.yz));
    vec4 sample2 = linearToSrgb(texture(sampler, offset.xw));
    vec4 sample3 = linearToSrgb(texture(sampler, offset.yw));

    float sx = s.x / (s.x + s.y);
    float sy = s.z / (s.z + s.w);

    return mix(
        mix(sample3, sample2, sx),
        mix(sample1, sample0, sx),
        sy
    );
}

vec4 textureBicubic(sampler2DArray sampler, vec3 texCoords) {
    vec2 texSize = textureSize(sampler, 0).xy;
    vec2 invTexSize = 1.0 / texSize;

    texCoords.xy = texCoords.xy * texSize - 0.5;

    vec2 fxy = fract(texCoords.xy);
    texCoords.xy -= fxy;

    vec4 xcubic = cubic(fxy.x);
    vec4 ycubic = cubic(fxy.y);

    vec4 c = texCoords.xxyy + vec2 (-0.5, +1.5).xyxy;

    vec4 s = vec4(xcubic.xz + xcubic.yw, ycubic.xz + ycubic.yw);
    vec4 offset = c + vec4 (xcubic.yw, ycubic.yw) / s;

    offset *= invTexSize.xxyy;

    vec4 sample0 = texture(sampler, vec3(offset.xz, texCoords.z));
    vec4 sample1 = texture(sampler, vec3(offset.yz, texCoords.z));
    vec4 sample2 = texture(sampler, vec3(offset.xw, texCoords.z));
    vec4 sample3 = texture(sampler, vec3(offset.yw, texCoords.z));

    float sx = s.x / (s.x + s.y);
    float sy = s.z / (s.z + s.w);

    return mix(
        mix(sample3, sample2, sx),
        mix(sample1, sample0, sx),
        sy
    );
}

vec4 textureBicubicSrgb(sampler2DArray sampler, vec3 texCoords) {
    vec2 texSize = textureSize(sampler, 0).xy;
    vec2 invTexSize = 1.0 / texSize;

    texCoords.xy = texCoords.xy * texSize - 0.5;

    vec2 fxy = fract(texCoords.xy);
    texCoords.xy -= fxy;

    vec4 xcubic = cubic(fxy.x);
    vec4 ycubic = cubic(fxy.y);

    vec4 c = texCoords.xxyy + vec2 (-0.5, +1.5).xyxy;

    vec4 s = vec4(xcubic.xz + xcubic.yw, ycubic.xz + ycubic.yw);
    vec4 offset = c + vec4 (xcubic.yw, ycubic.yw) / s;

    offset *= invTexSize.xxyy;

    vec4 sample0 = linearToSrgb(texture(sampler, vec3(offset.xz, texCoords.z)));
    vec4 sample1 = linearToSrgb(texture(sampler, vec3(offset.yz, texCoords.z)));
    vec4 sample2 = linearToSrgb(texture(sampler, vec3(offset.xw, texCoords.z)));
    vec4 sample3 = linearToSrgb(texture(sampler, vec3(offset.yw, texCoords.z)));

    float sx = s.x / (s.x + s.y);
    float sy = s.z / (s.z + s.w);

    return mix(
        mix(sample3, sample2, sx),
        mix(sample1, sample0, sx),
        sy
    );
}
