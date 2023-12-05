#version 330
layout(triangles) in;
layout(max_vertices = 3, triangle_strip) out;

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

layout(std140) uniform MaterialUniforms
{
    Material MaterialArray[326];
} _238;

uniform float elapsedTime;
uniform mat4 lightProjectionMatrix;

flat in int gMaterialData[3];
flat in vec3 gUv[3];
flat in vec3 gPosition[3];
flat in int gCastShadow[3];
flat out int fMaterialData;
out vec3 fUvw;
out float fOpacity;
flat in float gOpacity[3];

void main()
{
    do
    {
        if (((gCastShadow[0] + gCastShadow[1]) + gCastShadow[2]) == 0)
        {
            break;
        }
        int _241 = gMaterialData[0] >> 11;
        vec2 param[3] = vec2[](vec2(0.0), vec2(0.0), vec2(0.0));
        if (((gMaterialData[0] >> 2) & 1) == 1)
        {
            float _411 = 1.0 / length(gUv[0]);
            vec3 _415 = gUv[0] * _411;
            vec3 _417 = cross(vec3(0.0, 0.0, 1.0), _415);
            vec3 _419 = cross(vec3(0.0, 1.0, 0.0), _415);
            bvec3 _427 = bvec3(length(_417) > length(_419));
            vec3 _429 = normalize(vec3(_427.x ? _417.x : _419.x, _427.y ? _417.y : _419.y, _427.z ? _417.z : _419.z));
            mat3 _448 = mat3(_429, cross(_415, _429), _415);
            for (int _567 = 0; _567 < 3; )
            {
                param[_567] = ((_448 * gPosition[_567]).xy * vec2(0.0078125)) * _411;
                _567++;
                continue;
            }
        }
        else
        {
            if (((gMaterialData[0] >> 1) & 1) == 1)
            {
                vec3 _481 = gUv[1] - gUv[0];
                vec3 _485 = gUv[2] - gUv[0];
                vec3 _488 = cross(_481, _485);
                vec3 _491 = cross(_481, _488);
                vec3 _494 = cross(_485, _488);
                float _498 = 1.0 / dot(_494, _481);
                float _502 = 1.0 / dot(_491, _485);
                for (int _566 = 0; _566 < 3; )
                {
                    vec3 _513 = gPosition[_566] - gUv[0];
                    param[_566] = vec2(dot(_494, _513) * _498, dot(_491, _513) * _502);
                    _566++;
                    continue;
                }
            }
            else
            {
                for (int _565 = 0; _565 < 3; )
                {
                    param[_565] = gUv[_565].xy;
                    _565++;
                    continue;
                }
            }
        }
        vec2 uvs[3] = param;
        fMaterialData = gMaterialData[0];
        for (int _568 = 0; _568 < 3; )
        {
            fUvw = vec3(uvs[_568], float(_238.MaterialArray[_241].colorMap));
            vec3 _320 = fUvw;
            vec2 _322 = _320.xy + (_238.MaterialArray[_241].scrollDuration * elapsedTime);
            fUvw.x = _322.x;
            fUvw.y = _322.y;
            vec3 _331 = fUvw;
            vec2 _339 = (_331.xy - vec2(0.5)) * _238.MaterialArray[_241].textureScale + vec2(0.5);
            fUvw.x = _339.x;
            fUvw.y = _339.y;
            fOpacity = gOpacity[_568];
            gl_Position = lightProjectionMatrix * vec4(gPosition[_568], 1.0);
            EmitVertex();
            _568++;
            continue;
        }
        EndPrimitive();
        break;
    } while(false);
}

