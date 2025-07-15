package rs117.hd.opengl.uniforms;

import static org.lwjgl.opengl.GL33C.*;

public class SkyUniforms extends UniformBuffer {
	public SkyUniforms() {
		super("Sky", GL_DYNAMIC_DRAW);
	}

	public Property projectionMatrix = addProperty(PropertyType.Mat4, "projectionMatrix");
	public Property viewportDimensions = addProperty(PropertyType.IVec2, "viewportDimensions");
	public Property colorBlindnessIntensity = addProperty(PropertyType.Float, "colorBlindnessIntensity");
	public Property lightDir = addProperty(PropertyType.FVec3, "lightDir");
	public Property lightColor = addProperty(PropertyType.FVec3, "lightColor");
	public Property gammaCorrection = addProperty(PropertyType.Float, "gammaCorrection");
}
