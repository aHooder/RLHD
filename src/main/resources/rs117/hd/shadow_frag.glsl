#version 330

uniform sampler2DArray textureArray;

in float fOpacity;
in vec3 fUvw;
flat in int fMaterialData;

void main()
{
    float _82 = 0.0;
    if (fUvw.z != (-1.0))
    {
        vec3 _28 = fUvw;
        vec3 _81 = vec3(0.0);
        if (((fMaterialData >> 1) & 1) == 1)
        {
            vec3 _80 = _28;
            _80.x = clamp(_28.x, 0.0, 0.984375);
            _81 = _80;
        }
        else
        {
            _81 = _28;
        }
        _82 = texture(textureArray, _81).w;
    }
    else
    {
        _82 = fOpacity;
    }
    gl_FragDepth = float((int((1.0 - _82) * 255.0) << 16) | int(gl_FragCoord.z * 65535.0)) * 5.9604651880817982601001858711243e-08;
}

