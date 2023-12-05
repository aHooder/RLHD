#version 330

struct Material
{
    int colorMap;
    int normalMap;
    int displacementMap;
    int roughnessMap;
    int ambientOcclusionMap;
    int flowMap;
    int flags;
    float brightness;
    float displacementScale;
    float specularStrength;
    float specularGloss;
    float flowMapStrength;
    vec2 flowMapDuration;
    vec2 scrollDuration;
    vec2 textureScale;
    vec2 pad;
};

struct WaterType
{
    uint isFlat;
    float specularStrength;
    float specularGloss;
    float normalStrength;
    float baseOpacity;
    int hasFoam;
    float duration;
    float fresnelAmount;
    vec3 surfaceColor;
    float pad0;
    vec3 foamColor;
    float pad1;
    vec3 depthColor;
    float pad2;
    float causticsStrength;
    int normalMap;
    int foamMap;
    int flowMap;
    int underwaterFlowMap;
};

struct PointLight
{
    vec4 position;
    vec3 color;
    float pad;
};

layout(std140) uniform MaterialUniforms
{
    Material MaterialArray[326];
} _373;

layout(std140) uniform WaterTypeUniforms
{
    WaterType WaterTypeArray[13];
} _1174;

layout(std140) uniform PointLightUniforms
{
    PointLight PointLightArray[100];
} _2189;

uniform float elapsedTime;
uniform sampler2DArray textureArray;
uniform mat4 lightProjectionMatrix;
uniform sampler2D shadowMap;
uniform float saturation;
uniform float contrast;
uniform vec3 fogColor;
uniform vec3 ambientColor;
uniform int pointLightsCount;
uniform vec3 lightDir;
uniform float ambientStrength;
uniform float lightningBrightness;
uniform vec3 underglowColor;
uniform float underglowStrength;
uniform vec3 waterColorDark;
uniform vec3 waterColorMid;
uniform vec3 waterColorLight;
uniform bool underwaterCaustics;
uniform vec3 underwaterCausticsColor;
uniform float underwaterCausticsStrength;
uniform vec3 cameraPos;
uniform vec3 lightColor;
uniform float lightStrength;
uniform bool underwaterEnvironment;
uniform float groundFogStart;
uniform float groundFogEnd;
uniform float groundFogOpacity;
uniform float colorBlindnessIntensity;
uniform float fogDepth;
uniform float shadowMaxBias;
uniform int shadowsEnabled;

in FragmentData
{
    vec3 position;
    vec3 normal;
    vec3 texBlend;
    float fogAmount;
} IN;

flat in int vTerrainData[3];
flat in vec2 vUv[3];
flat in vec4 vColor[3];
flat in int vMaterialData[3];
flat in vec3 T;
flat in vec3 B;
layout(location = 0) out vec4 FragColor;

void main()
{
    vec3 _2934 = normalize(cameraPos - IN.position);
    vec3 _2948 = lightColor * lightStrength;
    int _2957 = vMaterialData[0] >> 11;
    int _2996 = vMaterialData[1] >> 11;
    int _3034 = vMaterialData[2] >> 11;
    vec2 _3095 = ((vUv[0] * IN.texBlend.x) + (vUv[1] * IN.texBlend.y)) + (vUv[2] * IN.texBlend.z);
    float _3465 = float((vTerrainData[2] >> 8) & 2047) * IN.texBlend.z + (float((vTerrainData[0] >> 8) & 2047) * IN.texBlend.x + (float((vTerrainData[1] >> 8) & 2047) * IN.texBlend.y));
    int _14928 = 0;
    if ((vTerrainData[0] & 1) != 0)
    {
        _14928 = (vTerrainData[0] >> 3) & 31;
    }
    else
    {
        _14928 = 0;
    }
    bool _3518 = _3465 != 0.0;
    bool _3521 = _14928 > 0;
    bool _3527 = false;
    if (_3521)
    {
        _3527 = !_3518;
    }
    else
    {
        _3527 = _3521;
    }
    vec4 _17035 = vec4(0.0);
    if (_3527)
    {
        bool _3593 = _1174.WaterTypeArray[_14928].isFlat != 0u;
        float _3635 = 28.0 * _1174.WaterTypeArray[_14928].duration;
        float _15915 = 0.0;
        do
        {
            if (_3635 == 0.0)
            {
                _15915 = 0.0;
                break;
            }
            _15915 = mod(elapsedTime, _3635) / _3635;
            break;
        } while(false);
        float _3642 = 24.0 * _1174.WaterTypeArray[_14928].duration;
        float _15916 = 0.0;
        do
        {
            if (_3642 == 0.0)
            {
                _15916 = 0.0;
                break;
            }
            _15916 = mod(elapsedTime, _3642) / _3642;
            break;
        } while(false);
        float _3651 = 50.0 * _1174.WaterTypeArray[_14928].duration;
        float _15919 = 0.0;
        do
        {
            if (_3651 == 0.0)
            {
                _15919 = 0.0;
                break;
            }
            _15919 = mod(elapsedTime, _3651) / _3651;
            break;
        } while(false);
        vec4 _3663 = texture(textureArray, vec3(IN.position.xz * vec2(-0.0005208333604969084262847900390625) + vec2(_15919), float(_1174.WaterTypeArray[_14928].flowMap)));
        vec2 _3664 = _3663.xy;
        vec2 _3666 = _3664 * 0.02500000037252902984619140625;
        float _3681 = float(_1174.WaterTypeArray[_14928].normalMap);
        vec4 _3685 = texture(textureArray, vec3(((IN.position.xz * vec2(-0.00260416674427688121795654296875)).yx - vec2(_15915)) + _3666, _3681));
        vec4 _3695 = texture(textureArray, vec3((IN.position.xz * vec2(-0.00260416674427688121795654296875) + vec2(_15916)) + _3666, _3681));
        vec4 _3705 = texture(textureArray, vec3(_3095 + _3666, float(_1174.WaterTypeArray[_14928].foamMap)));
        vec3 _3746 = normalize((-vec3((_3685.x * 2.0 + (-1.0)) * _1174.WaterTypeArray[_14928].normalStrength, _3685.z, (_3685.y * 2.0 + (-1.0)) * _1174.WaterTypeArray[_14928].normalStrength)) + (-vec3((_3695.x * 2.0 + (-1.0)) * _1174.WaterTypeArray[_14928].normalStrength, _3695.z, (_3695.y * 2.0 + (-1.0)) * _1174.WaterTypeArray[_14928].normalStrength)));
        float _4052 = _3746.y;
        float _4054 = max(-_4052, 0.0);
        float _4070 = max(dot(_3746, lightDir), 0.0);
        vec3 _3767 = vec3(_1174.WaterTypeArray[_14928].specularGloss);
        vec3 _3771 = vec3(_1174.WaterTypeArray[_14928].specularStrength);
        float _16214 = 0.0;
        if (((vMaterialData[0] >> 5) & 1) == 0)
        {
            float _16213 = 0.0;
            do
            {
                vec4 _4135 = lightProjectionMatrix * vec4(IN.position, 1.0);
                vec4 _4143 = ((_4135 / vec4(_4135.w)) * 0.5) + vec4(0.5);
                vec2 _4147 = _4143.xy + (_3664 * 0.000750000006519258022308349609375);
                vec4 _14761 = _4143;
                _14761.x = _4147.x;
                _14761.y = _4147.y;
                vec4 _4155 = clamp(_14761, vec4(0.0), vec4(1.0));
                vec2 _4157 = _4155.xy;
                vec2 _4160 = (_4157 * 2.0) - vec2(1.0);
                float _4164 = smoothstep(0.75, 1.0, dot(_4160, _4160));
                if (_4164 >= 1.0)
                {
                    _16213 = 0.0;
                    break;
                }
                float _4181 = (-0.000899999984540045261383056640625) * max(1.0, 1.0 - _4070) + _4155.z;
                ivec2 _4190 = ivec2((_4157 * vec2(textureSize(shadowMap, 0)) + vec2(-1.5)) + vec2(0.5));
                float _16208 = 0.0;
                _16208 = 0.0;
                float _16211 = 0.0;
                for (int _16207 = 0; _16207 < 3; _16208 = _16211, _16207++)
                {
                    _16211 = _16208;
                    float _19255 = 0.0;
                    for (int _16209 = 0; _16209 < 3; _16211 = _19255, _16209++)
                    {
                        int _4211 = int(texelFetch(shadowMap, _4190 + ivec2(_16207, _16209), 0).x * 16777215.0);
                        if (_4181 > (float(_4211 & 65535) * 1.525902189314365386962890625e-05))
                        {
                            _19255 = _16211 + ((-float(_4211 >> 16)) * 0.0039215688593685626983642578125 + 1.0);
                        }
                        else
                        {
                            _19255 = _16211;
                        }
                    }
                }
                _16213 = (_16208 * 0.111111111938953399658203125) * (1.0 - _4164);
                break;
            } while(false);
            _16214 = _16213;
        }
        else
        {
            _16214 = 0.0;
        }
        float _4095 = 1.0 - max(_16214, 0.0);
        vec3 _3805 = (_2948 * dot(pow(vec3(clamp(dot(_2934, reflect(-lightDir, _3746)), 1.0000000133514319600180897396058e-10, 1.0)), _3767) * _3771, IN.texBlend)) * _4095;
        vec3 _16739 = vec3(0.0);
        vec3 _16740 = vec3(0.0);
        _16740 = vec3(0.0);
        _16739 = vec3(0.0);
        vec3 _19256 = vec3(0.0);
        vec3 _19257 = vec3(0.0);
        for (int _16738 = 0; _16738 < pointLightsCount; _16740 = _19257, _16739 = _19256, _16738++)
        {
            vec3 _4312 = _2189.PointLightArray[_16738].position.xyz - IN.position;
            float _4315 = dot(_4312, _4312);
            if (_4315 <= _2189.PointLightArray[_16738].position.w)
            {
                vec3 _4329 = normalize(_4312);
                float _4416 = 1.0 - sqrt(min(_4315 / _2189.PointLightArray[_16738].position.w, 1.0));
                vec3 _4344 = _2189.PointLightArray[_16738].color * (_4416 * _4416);
                _19257 = _16740 + (_4344 * dot(pow(vec3(clamp(dot(_2934, reflect(-_4329, _3746)), 1.0000000133514319600180897396058e-10, 1.0)), _3767) * _3771, IN.texBlend));
                _19256 = _16739 + (_4344 * max(dot(_3746, _4329), 0.0));
            }
            else
            {
                _19257 = _16740;
                _19256 = _16739;
            }
        }
        float _3828 = min((-0.7200000286102294921875) * max(dot(_2934, _3746), 0.0) + 1.12000000476837158203125, 1.0);
        vec3 _16744 = vec3(0.0);
        if (_3828 < 0.5)
        {
            _16744 = mix(waterColorDark, waterColorMid, vec3(_3828 * 2.0));
        }
        else
        {
            _16744 = mix(waterColorMid, waterColorLight, vec3((_3828 - 0.5) * 2.0));
        }
        vec3 _3866 = ((((((((ambientColor * ambientStrength) + ((fogColor * _4054) * 0.5)) + ((_2948 * _4070) * _4095)) + _3805) + _16739) + _16740) + ((underglowColor * max(_4052, 0.0)) * underglowStrength)) + ((vec3(0.25) * _4054) * lightningBrightness)) + (_16744 * max(_1174.WaterTypeArray[_14928].specularStrength, 0.20000000298023223876953125));
        vec3 _3896 = (_1174.WaterTypeArray[_14928].foamColor * _3705.x) * _3866;
        float _3911 = (float(_1174.WaterTypeArray[_14928].hasFoam) * clamp(pow((min(1.0 - dot(IN.texBlend, vec3(vColor[0].x, vColor[1].x, vColor[2].x)), 0.800000011920928955078125) - 1.0) * 1.4285714626312255859375 + 1.0, 3.0), 0.0, 1.0)) * _3896.x;
        vec3 _3915 = vec3(_3911);
        vec3 _3937 = mix(mix(_1174.WaterTypeArray[_14928].surfaceColor * _3866, _16744, vec3(_1174.WaterTypeArray[_14928].fresnelAmount)), _3896, _3915) + (_3805 * vec3(0.3333333432674407958984375) + _16740);
        float _3948 = max(_1174.WaterTypeArray[_14928].baseOpacity, max(_3911, max(max(_3828, 1.0 + _2934.y) * mix(1.0, _4095, 0.20000000298023223876953125), length(mix(_3805, vec3(0.0), _3915) * vec3(0.3333333432674407958984375)))));
        vec3 _16750 = vec3(0.0);
        if (_3593)
        {
            _16750 = mix(_1174.WaterTypeArray[_14928].depthColor, _3937, vec3(_3948));
        }
        else
        {
            _16750 = _3937;
        }
        _17035 = vec4(_16750, _3593 ? 1.0 : _3948);
    }
    else
    {
        vec2 _14930 = vec2(0.0);
        float _14932 = 0.0;
        if (((vMaterialData[0] >> 1) & 1) == 1)
        {
            vec2 _14776 = _3095;
            _14776.x = clamp(_3095.x, 0.0, 0.984375);
            _14932 = (_373.MaterialArray[_2957].colorMap == _373.MaterialArray[27].colorMap) ? (-100.0) : 0.0;
            _14930 = _14776;
        }
        else
        {
            _14932 = 0.0;
            _14930 = _3095;
        }
        vec2 _4636 = ((_14930 + (_373.MaterialArray[_2957].scrollDuration * elapsedTime)) - vec2(0.5)) * _373.MaterialArray[_2957].textureScale + vec2(0.5);
        vec2 _14934 = vec2(0.0);
        do
        {
            if (all(equal(_373.MaterialArray[_2957].flowMapDuration, vec2(0.0))))
            {
                _14934 = vec2(0.0);
                break;
            }
            _14934 = mod(vec2(elapsedTime), _373.MaterialArray[_2957].flowMapDuration) / _373.MaterialArray[_2957].flowMapDuration;
            break;
        } while(false);
        vec2 _14936 = vec2(0.0);
        if (_3518)
        {
            float _4679 = 10.0 * _1174.WaterTypeArray[_14928].duration;
            float _14935 = 0.0;
            do
            {
                if (_4679 == 0.0)
                {
                    _14935 = 0.0;
                    break;
                }
                _14935 = mod(elapsedTime, _4679) / _4679;
                break;
            } while(false);
            _14936 = IN.position.xz * vec2(-0.0052083334885537624359130859375) + (vec2(1.0, -1.0) * _14935);
        }
        else
        {
            _14936 = _4636 - _14934;
        }
        vec4 _4692 = texture(textureArray, vec3(_14936, float(_373.MaterialArray[_2957].flowMap)));
        vec2 _4696 = _4692.xy * (_3518 ? 0.07500000298023223876953125 : _373.MaterialArray[_2957].flowMapStrength);
        vec2 _4699 = _4636 + _4696;
        vec2 _4706 = (((_14930 + (_373.MaterialArray[_2996].scrollDuration * elapsedTime)) - vec2(0.5)) * _373.MaterialArray[_2996].textureScale + vec2(0.5)) + _4696;
        vec2 _4713 = (((_14930 + (_373.MaterialArray[_3034].scrollDuration * elapsedTime)) - vec2(0.5)) * _373.MaterialArray[_3034].textureScale + vec2(0.5)) + _4696;
        vec3 _4775 = normalize(IN.normal) * min(length(T), length(B));
        mat3 _4788 = mat3(T, B, _4775);
        vec3 _4811 = inverse(_4788) * _2934;
        vec2 _14952 = vec2(0.0);
        vec3 _14953 = vec3(0.0);
        do
        {
            if (_373.MaterialArray[_2957].displacementMap == (-1))
            {
                _14953 = vec3(0.0);
                _14952 = _4699;
                break;
            }
            vec3 _4889 = normalize(_4811);
            float _4890 = _4889.z;
            float _4898 = 1.0 / mix(1.0, 16.0, 1.0 - clamp(_4890 * _4890, 0.0, 1.0));
            vec2 _4904 = (_4811.xy / vec2(_4811.z)) * _373.MaterialArray[_2957].displacementScale;
            vec2 _4908 = _4699 + (_4904 * 0.5);
            vec3 _4913 = vec3(_4904, _373.MaterialArray[_2957].displacementScale);
            float _14947 = 0.0;
            float _14948 = 0.0;
            float _14949 = 0.0;
            _14949 = 0.0;
            _14948 = 0.0;
            _14947 = 1.0;
            for (; (_14947 >= 0.0) && (_14948 <= _14947); )
            {
                vec4 _4979 = texture(textureArray, vec3(_4908 - (_4904 * _14947), float(_373.MaterialArray[_2957].displacementMap)));
                float _4980 = _4979.x;
                _14949 = _14948;
                _14948 = mix(_4980 * 12.9200000762939453125, 1.05499994754791259765625 * pow(_4980, 0.4166666567325592041015625) + (-0.054999999701976776123046875), step(0.003130800090730190277099609375, _4980));
                _14947 -= _4898;
                continue;
            }
            float _4941 = _14948 - _14947;
            vec3 _4961 = _4913 * mix(_14948, _14949, _4941 / (_4941 + ((_14947 + _4898) - _14949)));
            _14953 = (-(_4913 * 0.5)) + _4961;
            _14952 = _4908 - _4961.xy;
            break;
        } while(false);
        vec2 _14961 = vec2(0.0);
        vec3 _14962 = vec3(0.0);
        do
        {
            if (_373.MaterialArray[_2996].displacementMap == (-1))
            {
                _14962 = _14953;
                _14961 = _4706;
                break;
            }
            vec3 _5013 = normalize(_4811);
            float _5014 = _5013.z;
            float _5022 = 1.0 / mix(1.0, 16.0, 1.0 - clamp(_5014 * _5014, 0.0, 1.0));
            vec2 _5028 = (_4811.xy / vec2(_4811.z)) * _373.MaterialArray[_2996].displacementScale;
            vec2 _5032 = _4706 + (_5028 * 0.5);
            vec3 _5037 = vec3(_5028, _373.MaterialArray[_2996].displacementScale);
            float _14956 = 0.0;
            float _14957 = 0.0;
            float _14958 = 0.0;
            _14958 = 0.0;
            _14957 = 0.0;
            _14956 = 1.0;
            for (; (_14956 >= 0.0) && (_14957 <= _14956); )
            {
                vec4 _5103 = texture(textureArray, vec3(_5032 - (_5028 * _14956), float(_373.MaterialArray[_2996].displacementMap)));
                float _5104 = _5103.x;
                _14958 = _14957;
                _14957 = mix(_5104 * 12.9200000762939453125, 1.05499994754791259765625 * pow(_5104, 0.4166666567325592041015625) + (-0.054999999701976776123046875), step(0.003130800090730190277099609375, _5104));
                _14956 -= _5022;
                continue;
            }
            float _5065 = _14957 - _14956;
            vec3 _5085 = _5037 * mix(_14957, _14958, _5065 / (_5065 + ((_14956 + _5022) - _14958)));
            _14962 = (_14953 - (_5037 * 0.5)) + _5085;
            _14961 = _5032 - _5085.xy;
            break;
        } while(false);
        vec2 _14972 = vec2(0.0);
        vec3 _14973 = vec3(0.0);
        do
        {
            if (_373.MaterialArray[_3034].displacementMap == (-1))
            {
                _14973 = _14962;
                _14972 = _4713;
                break;
            }
            vec3 _5137 = normalize(_4811);
            float _5138 = _5137.z;
            float _5146 = 1.0 / mix(1.0, 16.0, 1.0 - clamp(_5138 * _5138, 0.0, 1.0));
            vec2 _5152 = (_4811.xy / vec2(_4811.z)) * _373.MaterialArray[_3034].displacementScale;
            vec2 _5156 = _4713 + (_5152 * 0.5);
            vec3 _5161 = vec3(_5152, _373.MaterialArray[_3034].displacementScale);
            float _14967 = 0.0;
            float _14968 = 0.0;
            float _14969 = 0.0;
            _14969 = 0.0;
            _14968 = 0.0;
            _14967 = 1.0;
            for (; (_14967 >= 0.0) && (_14968 <= _14967); )
            {
                vec4 _5227 = texture(textureArray, vec3(_5156 - (_5152 * _14967), float(_373.MaterialArray[_3034].displacementMap)));
                float _5228 = _5227.x;
                _14969 = _14968;
                _14968 = mix(_5228 * 12.9200000762939453125, 1.05499994754791259765625 * pow(_5228, 0.4166666567325592041015625) + (-0.054999999701976776123046875), step(0.003130800090730190277099609375, _5228));
                _14967 -= _5146;
                continue;
            }
            float _5189 = _14968 - _14967;
            vec3 _5209 = _5161 * mix(_14968, _14969, _5189 / (_5189 + ((_14967 + _5146) - _14969)));
            _14973 = (_14962 - (_5161 * 0.5)) + _5209;
            _14972 = _5156 - _5209.xy;
            break;
        } while(false);
        vec3 _4867 = IN.position + (_4788 * (_14973 * vec3(0.3333333432674407958984375)));
        vec4 _14986 = vec4(0.0);
        if (_373.MaterialArray[_2957].colorMap == (-1))
        {
            _14986 = vec4(1.0);
        }
        else
        {
            _14986 = texture(textureArray, vec3(_14952, float(_373.MaterialArray[_2957].colorMap)), _14932);
        }
        vec3 _5411 = _14986.xyz * _373.MaterialArray[_2957].brightness;
        vec4 _14778 = _14986;
        _14778.x = _5411.x;
        _14778.y = _5411.y;
        _14778.z = _5411.z;
        vec4 _14987 = vec4(0.0);
        if (_373.MaterialArray[_2996].colorMap == (-1))
        {
            _14987 = vec4(1.0);
        }
        else
        {
            _14987 = texture(textureArray, vec3(_14961, float(_373.MaterialArray[_2996].colorMap)), _14932);
        }
        vec3 _5439 = _14987.xyz * _373.MaterialArray[_2996].brightness;
        vec4 _14784 = _14987;
        _14784.x = _5439.x;
        _14784.y = _5439.y;
        _14784.z = _5439.z;
        vec4 _14988 = vec4(0.0);
        if (_373.MaterialArray[_3034].colorMap == (-1))
        {
            _14988 = vec4(1.0);
        }
        else
        {
            _14988 = texture(textureArray, vec3(_14972, float(_373.MaterialArray[_3034].colorMap)), _14932);
        }
        vec3 _5467 = _14988.xyz * _373.MaterialArray[_3034].brightness;
        vec4 _14790 = _14988;
        _14790.x = _5467.x;
        _14790.y = _5467.y;
        _14790.z = _5467.z;
        vec4 _14989 = vec4(0.0);
        if (((_373.MaterialArray[_2957].flags >> 2) & 1) == 1)
        {
            _14989 = _14778;
        }
        else
        {
            _14989 = vec4(_14778.xyz * vColor[0].xyz, min(_14986.w, vColor[0].w));
        }
        vec4 _14990 = vec4(0.0);
        if (((_373.MaterialArray[_2996].flags >> 2) & 1) == 1)
        {
            _14990 = _14784;
        }
        else
        {
            _14990 = vec4(_14784.xyz * vColor[1].xyz, min(_14987.w, vColor[1].w));
        }
        vec4 _14991 = vec4(0.0);
        if (((_373.MaterialArray[_3034].flags >> 2) & 1) == 1)
        {
            _14991 = _14790;
        }
        else
        {
            _14991 = vec4(_14790.xyz * vColor[2].xyz, min(_14988.w, vColor[2].w));
        }
        int _5521 = (vMaterialData[0] >> 0) & 1;
        int _5525 = (vMaterialData[1] >> 0) & 1;
        int _5529 = (vMaterialData[2] >> 0) & 1;
        ivec3 _5530 = ivec3(_5521, _5525, _5529);
        int _5538 = (_5521 + _5525) + _5529;
        ivec3 _5540 = ivec3(1) - _5530;
        int _5548 = (_5540.x + _5540.y) + _5540.z;
        vec3 _5552 = IN.texBlend * vec3(_5540);
        vec3 _5556 = IN.texBlend * vec3(_5530);
        vec3 _14992 = vec3(0.0);
        vec3 _14993 = vec3(0.0);
        if ((_5548 == 0) || (_5538 == 0))
        {
            _14993 = IN.texBlend;
            _14992 = IN.texBlend;
        }
        else
        {
            _14993 = clamp(_5556 * (1.0 / ((_5556.x + _5556.y) + _5556.z)), vec3(0.0), vec3(1.0));
            _14992 = clamp(_5552 * (1.0 / ((_5552.x + _5552.y) + _5552.z)), vec3(0.0), vec3(1.0));
        }
        float _15031 = 0.0;
        if ((_5538 > 0) && (_5548 > 0))
        {
            bool _5638 = _5538 == 1;
            bvec3 _20043 = bvec3(_5638);
            ivec3 _20044 = ivec3(_20043.x ? _5530.x : _5540.x, _20043.y ? _5530.y : _5540.y, _20043.z ? _5530.z : _5540.z);
            bool _5644 = _20044.x == 1;
            vec2 _14997 = vec2(0.0);
            vec2 _14999 = vec2(0.0);
            if (_5644)
            {
                _14999 = vUv[0];
                _14997 = vUv[2];
            }
            else
            {
                bvec2 _20045 = bvec2(_20044.y == 1);
                _14999 = vec2(_20045.x ? vUv[1].x : vUv[2].x, _20045.y ? vUv[1].y : vUv[2].y);
                _14997 = vec2(_20045.x ? vUv[2].x : vUv[1].x, _20045.y ? vUv[2].y : vUv[1].y);
            }
            vec2 _5744 = vec2(0.0);
            bvec2 _20049 = bvec2(_5644);
            vec2 _20050 = vec2(_20049.x ? vUv[1].x : vUv[0].x, _20049.y ? vUv[1].y : vUv[0].y);
            float _15001 = 0.0;
            do
            {
                _5744 = _14997 - _20050;
                float _5747 = dot(_5744, _5744);
                if (_5747 == 0.0)
                {
                    _15001 = 1.0;
                    break;
                }
                else
                {
                    _15001 = dot(_14999 - _20050, _5744) / _5747;
                    break;
                }
                break; // unreachable workaround
            } while(false);
            vec2 _5690 = _20050 + (_5744 * _15001);
            float _15005 = 0.0;
            do
            {
                vec2 _5775 = _5690 - _14999;
                float _5778 = dot(_5775, _5775);
                if (_5778 == 0.0)
                {
                    _15005 = 1.0;
                    break;
                }
                else
                {
                    _15005 = dot(_14930 - _14999, _5775) / _5778;
                    break;
                }
                break; // unreachable workaround
            } while(false);
            float _15011 = 0.0;
            if (_5638 ? true : false)
            {
                _15011 = 1.0 - _15005;
            }
            else
            {
                _15011 = _15005;
            }
            float _5706 = distance(_14999, _5690);
            float _5715 = clamp((clamp(_15011, 0.0, 1.0) - 0.5) * 2.0, 0.0, 1.0);
            float _15014 = 0.0;
            if (_5706 > 2.5)
            {
                _15014 = clamp((_5715 - 1.0) * (_5706 * 0.4000000059604644775390625) + 1.0, 0.0, 1.0);
            }
            else
            {
                _15014 = _5715;
            }
            _15031 = _15014;
        }
        else
        {
            _15031 = 0.0;
        }
        vec4 _5388 = mix(((_14989 * _14992.x) + (_14990 * _14992.y)) + (_14991 * _14992.z), ((_14989 * _14993.x) + (_14990 * _14993.y)) + (_14991 * _14993.z), vec4(_15031));
        vec3 _15036 = vec3(0.0);
        if (((vMaterialData[0] >> 4) & 1) == 1)
        {
            _15036 = vec3(0.0, -1.0, 0.0);
        }
        else
        {
            vec3 _15033 = vec3(0.0);
            do
            {
                if (_373.MaterialArray[_2957].normalMap == (-1))
                {
                    _15033 = _4775;
                    break;
                }
                vec3 _5864 = texture(textureArray, vec3(_14952, float(_373.MaterialArray[_2957].normalMap))).xyz;
                vec3 _5892 = mix(_5864 * 12.9200000762939453125, (pow(_5864, vec3(0.4166666567325592041015625)) * 1.05499994754791259765625) - vec3(0.054999999701976776123046875), step(vec3(0.003130800090730190277099609375), _5864));
                vec2 _5871 = (_5892.xy * 2.0) - vec2(1.0);
                vec3 _14822 = _5892;
                _14822.x = _5871.x;
                _14822.y = _5871.y;
                _15033 = _4788 * _14822;
                break;
            } while(false);
            vec3 _15034 = vec3(0.0);
            do
            {
                if (_373.MaterialArray[_2996].normalMap == (-1))
                {
                    _15034 = _4775;
                    break;
                }
                vec3 _5912 = texture(textureArray, vec3(_14961, float(_373.MaterialArray[_2996].normalMap))).xyz;
                vec3 _5940 = mix(_5912 * 12.9200000762939453125, (pow(_5912, vec3(0.4166666567325592041015625)) * 1.05499994754791259765625) - vec3(0.054999999701976776123046875), step(vec3(0.003130800090730190277099609375), _5912));
                vec2 _5919 = (_5940.xy * 2.0) - vec2(1.0);
                vec3 _14826 = _5940;
                _14826.x = _5919.x;
                _14826.y = _5919.y;
                _15034 = _4788 * _14826;
                break;
            } while(false);
            vec3 _15035 = vec3(0.0);
            do
            {
                if (_373.MaterialArray[_3034].normalMap == (-1))
                {
                    _15035 = _4775;
                    break;
                }
                vec3 _5960 = texture(textureArray, vec3(_14972, float(_373.MaterialArray[_3034].normalMap))).xyz;
                vec3 _5988 = mix(_5960 * 12.9200000762939453125, (pow(_5960, vec3(0.4166666567325592041015625)) * 1.05499994754791259765625) - vec3(0.054999999701976776123046875), step(vec3(0.003130800090730190277099609375), _5960));
                vec2 _5967 = (_5988.xy * 2.0) - vec2(1.0);
                vec3 _14830 = _5988;
                _14830.x = _5967.x;
                _14830.y = _5967.y;
                _15035 = _4788 * _14830;
                break;
            } while(false);
            _15036 = normalize(((_15033 * IN.texBlend.x) + (_15034 * IN.texBlend.y)) + (_15035 * IN.texBlend.z));
        }
        vec3 _5998 = vec3(_373.MaterialArray[_2957].specularGloss, _373.MaterialArray[_2996].specularGloss, _373.MaterialArray[_3034].specularGloss);
        float _15037 = 0.0;
        do
        {
            if (_373.MaterialArray[_2957].roughnessMap == (-1))
            {
                _15037 = 1.0;
                break;
            }
            vec4 _6071 = texture(textureArray, vec3(_14952, float(_373.MaterialArray[_2957].roughnessMap)));
            float _6072 = _6071.x;
            _15037 = mix(_6072 * 12.9200000762939453125, 1.05499994754791259765625 * pow(_6072, 0.4166666567325592041015625) + (-0.054999999701976776123046875), step(0.003130800090730190277099609375, _6072));
            break;
        } while(false);
        float _15038 = 0.0;
        do
        {
            if (_373.MaterialArray[_2996].roughnessMap == (-1))
            {
                _15038 = 1.0;
                break;
            }
            vec4 _6103 = texture(textureArray, vec3(_14961, float(_373.MaterialArray[_2996].roughnessMap)));
            float _6104 = _6103.x;
            _15038 = mix(_6104 * 12.9200000762939453125, 1.05499994754791259765625 * pow(_6104, 0.4166666567325592041015625) + (-0.054999999701976776123046875), step(0.003130800090730190277099609375, _6104));
            break;
        } while(false);
        float _15039 = 0.0;
        do
        {
            if (_373.MaterialArray[_3034].roughnessMap == (-1))
            {
                _15039 = 1.0;
                break;
            }
            vec4 _6135 = texture(textureArray, vec3(_14972, float(_373.MaterialArray[_3034].roughnessMap)));
            float _6136 = _6135.x;
            _15039 = mix(_6136 * 12.9200000762939453125, 1.05499994754791259765625 * pow(_6136, 0.4166666567325592041015625) + (-0.054999999701976776123046875), step(0.003130800090730190277099609375, _6136));
            break;
        } while(false);
        bool _6032 = ((vColor[0].w + vColor[1].w) + vColor[2].w) < 2.9900000095367431640625;
        vec3 _15047 = vec3(0.0);
        if (_6032)
        {
            _15047 = vec3(clamp((1.0 - vColor[0].w) * 2.0, 0.0, 1.0), clamp((1.0 - vColor[1].w) * 2.0, 0.0, 1.0), clamp((1.0 - vColor[2].w) * 2.0, 0.0, 1.0));
        }
        else
        {
            _15047 = vec3(_373.MaterialArray[_2957].specularStrength, _373.MaterialArray[_2996].specularStrength, _373.MaterialArray[_3034].specularStrength) * vec3(_15037, _15038, _15039);
        }
        bvec3 _20051 = bvec3(_6032);
        vec3 _20052 = vec3(_20051.x ? vec3(30.0).x : _5998.x, _20051.y ? vec3(30.0).y : _5998.y, _20051.z ? vec3(30.0).z : _5998.z);
        float _6162 = max(-_15036.y, 0.0);
        float _6178 = max(dot(_15036, lightDir), 0.0);
        vec3 _15054 = vec3(0.0);
        if (underwaterCaustics && underwaterEnvironment)
        {
            vec2 _6209 = IN.position.xz * vec2(-0.0006103515625) + ((lightDir.xy * IN.position.y) * vec2(0.0006103515625));
            vec2 _6211 = vec2(1.0, -2.0) * (mod(elapsedTime, 231.0) * 0.004329004324972629547119140625);
            float _6314 = float(_373.MaterialArray[6].colorMap);
            _15054 = _2948 + ((((vec3(min(texture(textureArray, vec3((_6209 + (vec2(1.0, -1.0) * (mod(elapsedTime, 19.0) * 0.052631579339504241943359375))) + _6211, _6314)).x, texture(textureArray, vec3(((_6209 * 1.25) + (vec2(-1.0, 1.0) * (mod(elapsedTime, 37.0) * 0.02702702768146991729736328125))) + _6211, _6314)).x)) * 2.0) * (underwaterCausticsColor * underwaterCausticsStrength)) * _6178) * pow(lightStrength, 1.5));
        }
        else
        {
            _15054 = _2948;
        }
        float _15062 = 0.0;
        if (((vMaterialData[0] >> 5) & 1) == 0)
        {
            float _15061 = 0.0;
            do
            {
                vec4 _6393 = lightProjectionMatrix * vec4(_4867, 1.0);
                vec4 _6401 = ((_6393 / vec4(_6393.w)) * 0.5) + vec4(0.5);
                vec4 _14839 = _6401;
                _14839.x = _6401.x;
                _14839.y = _6401.y;
                vec4 _6413 = clamp(_14839, vec4(0.0), vec4(1.0));
                vec2 _6415 = _6413.xy;
                vec2 _6418 = (_6415 * 2.0) - vec2(1.0);
                float _6422 = smoothstep(0.75, 1.0, dot(_6418, _6418));
                if (_6422 >= 1.0)
                {
                    _15061 = 0.0;
                    break;
                }
                float _6439 = (-0.000899999984540045261383056640625) * max(1.0, 1.0 - _6178) + _6413.z;
                ivec2 _6448 = ivec2((_6415 * vec2(textureSize(shadowMap, 0)) + vec2(-1.5)) + vec2(0.5));
                float _15056 = 0.0;
                _15056 = 0.0;
                float _15059 = 0.0;
                for (int _15055 = 0; _15055 < 3; _15056 = _15059, _15055++)
                {
                    _15059 = _15056;
                    float _18573 = 0.0;
                    for (int _15057 = 0; _15057 < 3; _15059 = _18573, _15057++)
                    {
                        int _6469 = int(texelFetch(shadowMap, _6448 + ivec2(_15055, _15057), 0).x * 16777215.0);
                        if (_6439 > (float(_6469 & 65535) * 1.525902189314365386962890625e-05))
                        {
                            _18573 = _15059 + ((-float(_6469 >> 16)) * 0.0039215688593685626983642578125 + 1.0);
                        }
                        else
                        {
                            _18573 = _15059;
                        }
                    }
                }
                _15061 = (_15056 * 0.111111111938953399658203125) * (1.0 - _6422);
                break;
            } while(false);
            _15062 = _15061;
        }
        else
        {
            _15062 = 0.0;
        }
        float _6353 = 1.0 - max(_15062, 0.0);
        vec3 _15587 = vec3(0.0);
        vec3 _15588 = vec3(0.0);
        _15588 = vec3(0.0);
        _15587 = vec3(0.0);
        vec3 _18574 = vec3(0.0);
        vec3 _18575 = vec3(0.0);
        for (int _15586 = 0; _15586 < pointLightsCount; _15588 = _18575, _15587 = _18574, _15586++)
        {
            vec3 _6570 = _2189.PointLightArray[_15586].position.xyz - _4867;
            float _6573 = dot(_6570, _6570);
            if (_6573 <= _2189.PointLightArray[_15586].position.w)
            {
                vec3 _6587 = normalize(_6570);
                float _6674 = 1.0 - sqrt(min(_6573 / _2189.PointLightArray[_15586].position.w, 1.0));
                vec3 _6602 = _2189.PointLightArray[_15586].color * (_6674 * _6674);
                _18575 = _15588 + (_6602 * dot(pow(vec3(clamp(dot(_2934, reflect(-_6587, _15036)), 1.0000000133514319600180897396058e-10, 1.0)), _20052) * _15047, IN.texBlend));
                _18574 = _15587 + (_6602 * max(dot(_15036, _6587), 0.0));
            }
            else
            {
                _18575 = _15588;
                _18574 = _15587;
            }
        }
        vec3 _3262 = _5388.xyz * mix((((((((ambientColor * ambientStrength) + ((fogColor * _6162) * 0.5)) + ((_15054 * _6178) * _6353)) + ((_15054 * dot(pow(vec3(clamp(dot(_2934, reflect(-lightDir, _15036)), 1.0000000133514319600180897396058e-10, 1.0)), _20052) * _15047, IN.texBlend)) * _6353)) + _15587) + _15588) + ((underglowColor * max(_15036.y, 0.0)) * underglowStrength)) + ((vec3(0.25) * _6162) * lightningBrightness), vec3(1.0), vec3(dot(IN.texBlend, vec3(float((_373.MaterialArray[_2957].flags >> 1) & 1), float((_373.MaterialArray[_2996].flags >> 1) & 1), float((_373.MaterialArray[_3034].flags >> 1) & 1)))));
        vec4 _14851 = _5388;
        _14851.x = _3262.x;
        _14851.y = _3262.y;
        _14851.z = _3262.z;
        vec3 _6879 = mix(_14851.xyz * 12.9200000762939453125, (pow(_14851.xyz, vec3(0.4166666567325592041015625)) * 1.05499994754791259765625) - vec3(0.054999999701976776123046875), step(vec3(0.003130800090730190277099609375), _14851.xyz));
        vec4 _14857 = _14851;
        _14857.x = _6879.x;
        _14857.y = _6879.y;
        _14857.z = _6879.z;
        vec3 _15691 = vec3(0.0);
        do
        {
            if (!_3518)
            {
                _15691 = _14857.xyz;
                break;
            }
            vec3 _15689 = vec3(0.0);
            if (_3465 < 150.0)
            {
                _15689 = _14857.xyz * mix(vec3(1.0), _1174.WaterTypeArray[_14928].depthColor, vec3(_3465 * 0.006666666828095912933349609375));
            }
            else
            {
                vec3 _15690 = vec3(0.0);
                if (_3465 < 500.0)
                {
                    _15690 = _14857.xyz * mix(_1174.WaterTypeArray[_14928].depthColor, vec3(0.0), vec3((_3465 - 150.0) * 0.00285714282654225826263427734375));
                }
                else
                {
                    _15690 = vec3(0.0);
                }
                _15689 = _15690;
            }
            vec3 _15692 = vec3(0.0);
            if (underwaterCaustics)
            {
                float _6954 = ((IN.position.y - (IN.position.y - _3465)) - 512.0) * (-0.001953125);
                vec2 _6966 = IN.position.xz * vec2(-0.0044642859138548374176025390625) + ((lightDir.xy * IN.position.y) * vec2(0.0044642859138548374176025390625));
                vec2 _6970 = _6966 + (vec2(1.0, -2.0) * (mod(elapsedTime, 17.0) * 0.0588235296308994293212890625));
                vec2 _6975 = (_6966 * 1.5) + (vec2(-1.0, 2.0) * (mod(elapsedTime, 23.0) * 0.0434782616794109344482421875));
                float _7084 = float(_373.MaterialArray[6].colorMap);
                _15692 = _15689 * (vec3(1.0) + ((((vec3(min(texture(textureArray, vec3(_6970 + vec2(0.004999999888241291046142578125), _7084)).x, texture(textureArray, vec3(_6975 + vec2(0.004999999888241291046142578125), _7084)).x), min(texture(textureArray, vec3(_6970 + vec2(0.004999999888241291046142578125, -0.004999999888241291046142578125), _7084)).x, texture(textureArray, vec3(_6975 + vec2(0.004999999888241291046142578125, -0.004999999888241291046142578125), _7084)).x), min(texture(textureArray, vec3(_6970 + vec2(-0.004999999888241291046142578125), _7084)).x, texture(textureArray, vec3(_6975 + vec2(-0.004999999888241291046142578125), _7084)).x)) * (underwaterCausticsColor * underwaterCausticsStrength)) * (_6954 * _6954)) * _6178) * lightStrength));
            }
            else
            {
                _15692 = _15689;
            }
            _15691 = _15692;
            break;
        } while(false);
        _14857.x = _15691.x;
        _14857.y = _15691.y;
        _14857.z = _15691.z;
        _17035 = _14857;
    }
    vec3 _7159 = clamp(_17035.xyz, vec3(0.0), vec3(1.0));
    vec3 _17044 = vec3(0.0);
    if ((saturation != 1.0) || (contrast != 1.0))
    {
        float _7215 = _7159.x;
        float _7217 = _7159.y;
        float _7220 = _7159.z;
        float _7221 = max(max(_7215, _7217), _7220);
        float _7229 = min(min(_7215, _7217), _7220);
        float _7232 = _7221 - _7229;
        float _17038 = 0.0;
        if (_7232 > 0.0)
        {
            float _17039 = 0.0;
            if (_7221 == _7215)
            {
                _17039 = mod((_7217 - _7220) / _7232, 6.0);
            }
            else
            {
                float _17040 = 0.0;
                if (_7221 == _7217)
                {
                    _17040 = ((_7220 - _7215) / _7232) + 2.0;
                }
                else
                {
                    _17040 = ((_7215 - _7217) / _7232) + 4.0;
                }
                _17039 = _17040;
            }
            _17038 = _17039;
        }
        else
        {
            _17038 = 0.0;
        }
        float _7277 = _7221 + _7229;
        float _7278 = _7277 * 0.5;
        float _7283 = 1.0 - abs(_7277 - 1.0);
        float _17036 = 0.0;
        if (abs(_7283) < 1.0000000133514319600180897396058e-10)
        {
            _17036 = 0.0;
        }
        else
        {
            _17036 = _7232 / _7283;
        }
        float _7314 = _7277 * 0.5 + (_17036 * min(_7278, (-_7277) * 0.5 + 1.0));
        float _17041 = 0.0;
        if (abs(_7314) < 1.0000000133514319600180897396058e-10)
        {
            _17041 = 0.0;
        }
        else
        {
            _17041 = 2.0 * (1.0 - (_7278 / _7314));
        }
        vec3 _7331 = vec3(_17038 * 0.16666667163372039794921875, _17041, _7314);
        _7331.y = _17041 * saturation;
        vec3 _17042 = vec3(0.0);
        if (_7314 > 0.5)
        {
            vec3 _14894 = _7331;
            _14894.z = (_7314 - 0.5) * contrast + 0.5;
            _17042 = _14894;
        }
        else
        {
            vec3 _14897 = _7331;
            _14897.z = (_7314 - 0.5) * contrast + 0.5;
            _17042 = _14897;
        }
        float _7349 = (-_17042.y) * 0.5 + 1.0;
        float _7350 = _17042.z * _7349;
        float _20017 = -_17042.z;
        float _7354 = min(_7350, _20017 * _7349 + 1.0);
        float _17043 = 0.0;
        if (abs(_7354) < 1.0000000133514319600180897396058e-10)
        {
            _17043 = 0.0;
        }
        else
        {
            _17043 = (_20017 * _7349 + _17042.z) / _7354;
        }
        float _7388 = (1.0 - abs(2.0 * _7350 + (-1.0))) * _17043;
        _17044 = (vec3(clamp(abs(_17042.x * 6.0 + (-3.0)) - 1.0, 0.0, 1.0), clamp(2.0 - abs(_17042.x * 6.0 + (-2.0)), 0.0, 1.0), clamp(2.0 - abs(_17042.x * 6.0 + (-4.0)), 0.0, 1.0)) * _7388) + vec3(_17042.z * _7349 + (_7388 * (-0.5)));
    }
    else
    {
        _17044 = _7159;
    }
    vec4 _14907 = _17035;
    _14907.x = _17044.x;
    _14907.y = _17044.y;
    _14907.z = _17044.z;
    vec4 _18494 = vec4(0.0);
    do
    {
        if (_3518)
        {
            _18494 = _14907;
            break;
        }
        float _7460 = (IN.fogAmount - 1.0) * ((-mix(0.0, groundFogOpacity, 1.0 - clamp((IN.position.y - groundFogStart) / (groundFogEnd - groundFogStart), 0.0, 1.0))) * clamp(distance(IN.position, cameraPos) * 0.000666666659526526927947998046875, 0.0, 1.0) + 1.0) + 1.0;
        vec4 _18493 = vec4(0.0);
        if (_3527)
        {
            _18493 = vec4(_17044, _17035.w * (1.0 - _7460) + _7460);
        }
        else
        {
            _18493 = _14907;
        }
        vec3 _7478 = mix(_18493.xyz, fogColor, vec3(_7460));
        vec4 _14917 = _18493;
        _14917.x = _7478.x;
        _14917.y = _7478.y;
        _14917.z = _7478.z;
        _18494 = _14917;
        break;
    } while(false);
    FragColor = _18494;
}

