package rs117.hd.opengl;

import rs117.hd.data.WaterType;
import rs117.hd.data.materials.Material;
import rs117.hd.scene.TextureManager;

import static org.lwjgl.opengl.GL15C.*;

public class WaterTypesBuffer extends UniformBuffer{
	public WaterTypesBuffer() {
		super("WaterTypes", GL_STATIC_DRAW);
	}

	public WaterTypeStruct[] waterTypes = addStructs(new WaterTypeStruct[WaterType.values().length], WaterTypeStruct::new);

	public static class WaterTypeStruct extends StructProperty {
		public Property isFlat = addProperty(PropertyType.Int, "isFlat");
		public Property specularStrength = addProperty(PropertyType.Float, "specularStrength");
		public Property specularGloss = addProperty(PropertyType.Float, "specularGloss");
		public Property normalStrength = addProperty(PropertyType.Float, "normalStrength");
		public Property baseOpacity = addProperty(PropertyType.Float, "baseOpacity");
		public Property hasFoam = addProperty(PropertyType.Int, "hasFoam");
		public Property duration = addProperty(PropertyType.Float, "duration");
		public Property fresnelAmount = addProperty(PropertyType.Float, "fresnelAmount");
		public Property surfaceColor = addProperty(PropertyType.FVec3, "surfaceColor");
		public Property foamColor = addProperty(PropertyType.FVec3, "foamColor");
		public Property depthColor = addProperty(PropertyType.FVec3, "depthColor");
		public Property causticsStrength = addProperty(PropertyType.Float, "causticsStrength");
		public Property normalMap = addProperty(PropertyType.Int, "normalMap");
		public Property foamMap = addProperty(PropertyType.Int, "foamMap");
		public Property flowMap = addProperty(PropertyType.Int, "flowMap");
		public Property underwaterFlowMap = addProperty(PropertyType.Int, "underwaterFlowMap");
	}

	public void update(TextureManager textureManager) {
		for (WaterType type : WaterType.values()) {
			WaterTypesBuffer.WaterTypeStruct uniformStruct = waterTypes[type.ordinal()];
			uniformStruct.isFlat.set(type.flat ? 1 : 0);
			uniformStruct.specularStrength.set(type.specularStrength);
			uniformStruct.specularGloss.set(type.specularGloss);
			uniformStruct.normalStrength.set(type.normalStrength);
			uniformStruct.baseOpacity.set(type.baseOpacity);
			uniformStruct.hasFoam.set(type.hasFoam ? 1 : 0);
			uniformStruct.duration.set(type.duration);
			uniformStruct.fresnelAmount.set(type.fresnelAmount);
			uniformStruct.surfaceColor.set(type.surfaceColor);
			uniformStruct.foamColor.set(type.foamColor);
			uniformStruct.depthColor.set(type.depthColor);
			uniformStruct.causticsStrength.set(type.causticsStrength);
			uniformStruct.normalMap.set(textureManager.getTextureLayer(type.normalMap));
			uniformStruct.foamMap.set(textureManager.getTextureLayer(Material.WATER_FOAM));
			uniformStruct.flowMap.set(textureManager.getTextureLayer(Material.WATER_FLOW_MAP));
			uniformStruct.underwaterFlowMap.set(textureManager.getTextureLayer(Material.UNDERWATER_FLOW_MAP));
		}
		upload();
	}
}
