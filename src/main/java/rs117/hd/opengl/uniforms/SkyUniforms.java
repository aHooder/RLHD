package rs117.hd.opengl.uniforms;

import rs117.hd.utils.buffer.GLBuffer;

import static org.lwjgl.opengl.GL33C.*;

public class SkyUniforms extends UniformBuffer<GLBuffer> {
	public SkyUniforms() {
		super(GL_DYNAMIC_DRAW);
	}

	public Property inverseProjectionMatrix = addProperty(PropertyType.Mat4, "inverseProjectionMatrix");
	public Property viewportDimensions = addProperty(PropertyType.IVec2, "viewportDimensions");
	public Property colorBlindnessIntensity = addProperty(PropertyType.Float, "colorBlindnessIntensity");
	public Property lightDir = addProperty(PropertyType.FVec3, "lightDir");
	public Property lightColor = addProperty(PropertyType.FVec3, "lightColor");
	public Property gammaCorrection = addProperty(PropertyType.Float, "gammaCorrection");
}
