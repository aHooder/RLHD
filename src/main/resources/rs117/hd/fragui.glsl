#version 330

uniform sampler2D uiTexture;
uniform vec4 alphaOverlay;
uniform int samplingMode;
uniform ivec2 sourceDimensions;
uniform ivec2 targetDimensions;
uniform float colorBlindnessIntensity;

in vec2 TexCoord;
layout(location = 0) out vec4 FragColor;

void main()
{
    vec4 _54 = texture(uiTexture, TexCoord);
    float _127 = _54.w;
    float _128 = 1.0 - _127;
    vec3 _130 = _54.xyz + (alphaOverlay.xyz * _128);
    float _140 = _130.x;
    vec4 _143 = vec4(_140, _130.yz, alphaOverlay.w * _128 + _127);
    _143.x = _140;
    _143.y = _130.y;
    _143.z = _130.z;
    FragColor = _143;
}

