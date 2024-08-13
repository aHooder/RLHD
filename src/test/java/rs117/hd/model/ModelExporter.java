package rs117.hd.model;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.imageio.ImageIO;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.coords.*;
import net.runelite.api.events.*;
import net.runelite.api.model.*;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.input.KeyListener;
import net.runelite.client.input.KeyManager;
import net.runelite.client.input.MouseListener;
import net.runelite.client.input.MouseManager;
import net.runelite.client.input.MouseWheelListener;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;
import net.runelite.client.ui.overlay.outline.ModelOutlineRenderer;
import org.lwjgl.assimp.AIExportFormatDesc;
import org.lwjgl.assimp.AIFace;
import org.lwjgl.assimp.AILogStream;
import org.lwjgl.assimp.AIMaterial;
import org.lwjgl.assimp.AIMaterialProperty;
import org.lwjgl.assimp.AIMatrix4x4;
import org.lwjgl.assimp.AIMesh;
import org.lwjgl.assimp.AINode;
import org.lwjgl.assimp.AIScene;
import org.lwjgl.assimp.AIString;
import org.lwjgl.assimp.AITexture;
import org.lwjgl.assimp.AIVector3D;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.Pointer;
import rs117.hd.HdPlugin;
import rs117.hd.data.materials.Material;
import rs117.hd.scene.ModelOverrideManager;
import rs117.hd.scene.SceneContext;
import rs117.hd.scene.TextureManager;
import rs117.hd.scene.model_overrides.ModelOverride;
import rs117.hd.utils.ColorUtils;
import rs117.hd.utils.HDUtils;
import rs117.hd.utils.ModelHash;
import rs117.hd.utils.Vector;

import static net.runelite.api.Perspective.*;
import static org.lwjgl.assimp.Assimp.*;
import static org.lwjgl.system.MemoryUtil.memAddress0;
import static org.lwjgl.system.MemoryUtil.memAlloc;
import static org.lwjgl.system.MemoryUtil.memAllocFloat;
import static org.lwjgl.system.MemoryUtil.memAllocInt;
import static org.lwjgl.system.MemoryUtil.memByteBuffer;
import static org.lwjgl.system.MemoryUtil.memPutAddress;
import static org.lwjgl.system.MemoryUtil.memSlice;
import static org.lwjgl.system.MemoryUtil.memUTF8;
import static org.lwjgl.system.MemoryUtil.nmemFree;
import static rs117.hd.model.ModelPusher.MAX_MATERIAL_COUNT;
import static rs117.hd.model.ModelPusher.interpolateHSL;
import static rs117.hd.utils.ResourcePath.path;

@Slf4j
public class ModelExporter extends Overlay implements MouseListener, MouseWheelListener, KeyListener, AutoCloseable {
	private static final Color OUTLINE_COLOR = new Color(0xFFFF00FF, true);
	private static final int OUTLINE_WIDTH = 4;
	private static final int OUTLINE_FEATHER = 4;
	private static final int EXPORTER_KEY = KeyCode.KC_CONTROL;
	private static final float[] ZEROS = new float[12];

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private EventBus eventBus;

	@Inject
	private KeyManager keyManager;

	@Inject
	private MouseManager mouseManager;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private ChatMessageManager chatMessageManager;

	@Inject
	private ModelOutlineRenderer modelOutlineRenderer;

	@Inject
	private HdPlugin plugin;

	@Inject
	private TextureManager textureManager;

	@Inject
	private ModelOverrideManager modelOverrideManager;

	@Inject
	private ModelPusher modelPusher;

	private int clientTickCount;
	private int selectedIndex;
	private Object selectedObject;
	private final HashSet<Object> hoverSet = new HashSet<>();

	@Inject
	public ModelExporter(
		EventBus eventBus,
		OverlayManager overlayManager,
		MouseManager mouseManager,
		KeyManager keyManager
	) {
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
		setPriority(OverlayPriority.HIGH);

		eventBus.register(this);
		mouseManager.registerMouseListener(this);
		mouseManager.registerMouseWheelListener(this);
		keyManager.registerKeyListener(this);
		SwingUtilities.invokeLater(() -> overlayManager.add(this));
	}

	@Override
	public void close() {
		eventBus.unregister(this);
		overlayManager.remove(this);
		mouseManager.unregisterMouseListener(this);
		mouseManager.unregisterMouseWheelListener(this);
		keyManager.unregisterKeyListener(this);
	}

	@AllArgsConstructor
	private static class ModelInfo {
		Object object;
		Renderable renderable;
		Model model;
	}

	@AllArgsConstructor
	private static class MeshData {
		AIMesh mesh;
		AIFace.Buffer faces;
	}

	public void exportModel(Map.Entry<String, String> objectName, Object object) {
		textureManager.ensureMaterialsAreLoaded();

		SceneContext currentSceneContext = plugin.getSceneContext();
		if (currentSceneContext == null) {
			log.debug("Scene context is null");
			return;
		}

		TextureProvider textureProvider = null;
		while (textureProvider == null)
			textureProvider = client.getTextureProvider();
		var vanillaImage = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);

		long formatCount = aiGetExportFormatCount();
		log.debug("Supported formats: {}", formatCount);
		for (int i = 0; i < formatCount; i++) {
			AIExportFormatDesc desc = null;
			try {
				desc = aiGetExportFormatDescription(i);
				log.debug(
					"    Format id={} extension={} description={}",
					memUTF8(desc.id()),
					desc.fileExtensionString(),
					memUTF8(desc.description())
				);
			} finally {
				if (desc != null)
					aiReleaseExportFormatDescription(desc);
			}
		}

//		String formatId = "obj", extension = formatId;
//		String formatId = "collada", extension = "dae";
//		String formatId = "fbx", extension = formatId;
//		String formatId = "gltf2", extension = "gltf";
		String formatId = "glb2", extension = "glb";
		var filename = (objectName.getKey() + " " + objectName.getValue())
			.replace(' ', '_')
			.replaceAll("[^0-9a-zA-Z_ -]", "")
			.toLowerCase();
		var exportPath = path("model-exports", filename + "." + extension);
		exportPath.mkdirs();
		int collisionCounter = 2;
		while (exportPath.exists())
			exportPath = path("model-exports", filename + "_" + (collisionCounter++) + "." + extension);

		var modelInfos = resolveModels(object);
		if (modelInfos.isEmpty()) {
			chatMessageManager.queue(QueuedMessage.builder()
				.type(ChatMessageType.CONSOLE)
				.runeLiteFormattedMessage(new ChatMessageBuilder()
					.append(objectName.getKey().substring(0, 1).toUpperCase() + objectName.getKey().substring(1) + " ")
					.append(Color.CYAN, objectName.getValue())
					.append(" has no model data.")
					.build())
				.build());
			return;
		}

		var cleanup = new ArrayList<Long>();
		var cleanupFaceBuffers = new ArrayList<AIFace.Buffer>();
		try (var stack = MemoryStack.stackPush()) {
			int totalFaceCount = modelInfos.stream().mapToInt(info -> info.model.getFaceCount()).sum();
			int totalVertexCount = totalFaceCount * 3;

			var vertices = memAllocFloat(totalVertexCount * 3);
			cleanup.add(memAddress0(vertices));
			var uvs = memAllocFloat(totalVertexCount * 3);
			cleanup.add(memAddress0(uvs));
			var normals = memAllocFloat(totalVertexCount * 3);
			cleanup.add(memAddress0(normals));
			var colors = memAllocFloat(totalVertexCount * 4);
			cleanup.add(memAddress0(colors));
			var indices = memAllocInt(totalVertexCount);
			cleanup.add(memAddress0(indices));

			var scene = AIScene.calloc(stack);
			float scale = 1f / 128;
			if (extension.equals("fbx"))
				scale = 1; // this scaling parameter results in wildly different scaling with FBX for some reason
			var transform = AIMatrix4x4.calloc(stack)
				.a1(-scale).b2(-scale).c3(scale).d4(1);
			var rootNode = AINode.calloc(stack)
				.mTransformation(transform)
				.mName(s -> s.data(stack.UTF8(filename)));
			scene.mRootNode(rootNode);

			var meshList = new ArrayList<AIMesh>();
			var materialList = new ArrayList<TextureManager.MaterialEntry>();

			// Dummy scene context for writing model data
			var sceneContext = new SceneContext(
				client,
				client.getScene(),
				currentSceneContext.expandedMapLoadingChunks,
				false,
				null
			);
			boolean worldUvWarningSent = false;
			for (ModelInfo modelInfo : modelInfos) {
				long hash = getHash(modelInfo.object);
				int uuid = ModelHash.generateUuid(client, hash, modelInfo.renderable);
				int[] worldPos = sceneContext.sceneToWorld(
					ModelHash.getSceneX(hash),
					ModelHash.getSceneY(hash),
					ModelHash.getPlane(hash)
				);
				ModelOverride modelOverride = modelOverrideManager.getOverride(uuid, worldPos);
				var model = modelInfo.model;
				int vertexOffset = sceneContext.getVertexOffset();
				int uvOffset = sceneContext.getUvOffset();
				int bakedOrientation = getBakedOrientation(modelInfo.object, modelInfo.renderable);
				modelPusher.pushModel(sceneContext, null, uuid, model, modelOverride, bakedOrientation, false);
				int numVertices = sceneContext.modelPusherResults[0] * 3;
				int numUvs = sceneContext.modelPusherResults[1] * 3;
				int numFaces = numVertices / 3;
				assert numUvs == numVertices || numUvs == 0;

				var meshDataMap = new HashMap<TextureManager.MaterialEntry, MeshData>();
				for (int face = 0; face < numFaces; face++) {
					int faceVertexOffset = vertexOffset + face * 3;
					int faceUvOffset = uvOffset + face * 3;

					int materialData = 0;
					TextureManager.MaterialEntry materialEntry = textureManager.materialUniformEntries.get(0);
					if (numUvs > 0) {
						materialData = (int) sceneContext.stagingBufferUvs.getBuffer().get(faceUvOffset * 4 + 3);
						materialEntry = textureManager.materialUniformEntries.get(materialData >> 12 & MAX_MATERIAL_COUNT);
					}

					var buffers = meshDataMap.get(materialEntry);
					if (buffers == null) {
						// Model#getHash() = ObjectDefinitionId << 10 | 7-bit model type << 3 | 1-bit mirroring? | 2-bit orientation
						int objectCompositionId = (int) (model.getHash() >> 10); // could use this to grab another name maybe
						var meshName = objectName.getValue() + "_" + materialEntry;

						int materialIndex = materialList.indexOf(materialEntry);
						if (materialIndex == -1) {
							materialIndex = materialList.size();
							materialList.add(materialEntry);
						}

						// All meshes share the same underlying buffers, with different indices
						var mesh = AIMesh.calloc(stack)
							.mPrimitiveTypes(aiPrimitiveType_TRIANGLE)
							.mNumVertices(totalVertexCount)
							.mVertices(AIVector3D.create(memAddress0(vertices), 0))
							.mNormals(AIVector3D.create(memAddress0(normals), 0))
							.mColors(stack.pointers(memAddress0(colors)))
							.mMaterialIndex(materialIndex)
							.mName(s -> s.data(stack.UTF8(meshName)))
							.mNumUVComponents(stack.ints(2))
							.mTextureCoords(stack.pointers(memAddress0(uvs)));

						var faces = AIFace.malloc(numFaces);
						cleanupFaceBuffers.add(faces);
						buffers = new MeshData(mesh, faces);
						meshDataMap.put(materialEntry, buffers);
						meshList.add(mesh);
					}

					// Advance the face buffer and assign the memory address of the current offset into the index buffer
					buffers.faces.get().mIndices(memSlice(indices, 0, 3));

					if (numUvs == 0) {
						uvs.put(ZEROS, 0, 9);
					} else {
						boolean vanillaUvs = (materialData >> 1 & 1) == 1;
						boolean worldUvs = (materialData >> 2 & 1) == 1;
						if (worldUvs) {
							if (!worldUvWarningSent) {
								chatMessageManager.queue(QueuedMessage.builder()
									.type(ChatMessageType.CONSOLE)
									.runeLiteFormattedMessage(new ChatMessageBuilder()
										.append("The model has world UVs, which cannot be exported. Using vanilla UVs instead.")
										.build())
									.build());
								worldUvWarningSent = true;
							}
							vanillaUvs = true;
						}

						if (vanillaUvs) {
							// Since vanilla UVs are computed in the geometry shader in HD, we can't use the data from ModelPusher
							fillVanillaUvs(uvs, model, face);
						} else {
							for (int i = 0; i < 3; i++)
								uvs
									.put(sceneContext.stagingBufferUvs.getBuffer().get((faceUvOffset + i) * 4))
									.put(sceneContext.stagingBufferUvs.getBuffer().get((faceUvOffset + i) * 4 + 1))
									.put(0);
						}

						for (int i = 0; i < 3; i++) {
							int offset = uvs.position() - 9 + i * 3;
							float u = uvs.get(offset);
							float v = uvs.get(offset + 1);

							// Apply HD's texture scale parameter
							u = (u - .5f) / materialEntry.material.textureScale[0] + .5f;
							v = (v - .5f) / materialEntry.material.textureScale[1] + .5f;

							// Flip UVs horizontally & vertically to match exported image orientation
							u = 1 - u;
							v = 1 - v;

							uvs.put(offset, u).put(offset + 1, v);
						}
					}

					float[][] v = new float[3][3];
					float[][] n = new float[3][3];
					for (int i = 0; i < 3; i++) {
						int vertex = faceVertexOffset + i;
						for (int c = 0; c < 3; c++) {
							v[i][c] = Float.intBitsToFloat(sceneContext.stagingBufferVertices.getBuffer().get(vertex * 4 + c));
							n[i][c] = sceneContext.stagingBufferNormals.getBuffer().get(vertex * 4 + c);
						}

						indices.put(indices.position());

						int packedColor = sceneContext.stagingBufferVertices.getBuffer().get(vertex * 4 + 3);
						float alpha = 1 - (packedColor >>> 24 & 0xFF) / (float) 0xFF;
						colors
							.put(ColorUtils.packedHslToSrgb(packedColor))
							.put(alpha);

						vertices.put(v[i]);
					}

					float[] flatNormal = null;
					for (int i = 0; i < 3; i++) {
						if (Vector.dot(n[i], n[i]) == 0) {
							if (flatNormal == null) {
								// Compute flat normal
								flatNormal = new float[3];
								float[] a = new float[3];
								float[] b = new float[3];
								Vector.subtract(a, v[1], v[0]);
								Vector.subtract(b, v[2], v[0]);
								Vector.cross(flatNormal, a, b);
								Vector.normalize(flatNormal);
							}
							n[i] = flatNormal;
						} else {
							// Normalize
							Vector.normalize(n[i]);
						}

						normals.put(n[i]);
					}
				}

				// Since we don't know the number of faces per mesh in advance, assign them after
				for (var meshData : meshDataMap.values())
					meshData.mesh.mFaces(meshData.faces.flip());
			}

			var meshPointers = stack.mallocPointer(meshList.size());
			var meshIndices = stack.mallocInt(meshList.size());
			for (int i = 0; i < meshList.size(); i++) {
				meshIndices.put(i);
				meshPointers.put(meshList.get(i));
			}
			scene.mMeshes(meshPointers.flip());
			rootNode.mMeshes(meshIndices.flip());

			// TODO: split player mesh into one mesh per equipment
			// TODO: if turning this into a general purpose exporter, consider exporting a material with vertex colors UV mapped
//			Player p;
//			p.getPlayerComposition().getEquipmentIds() // 12 slots, convert IDs to model IDs
//			client.loadModelData(id)

			var materialPointers = stack.callocPointer(materialList.size());
			var expandedMaterials = new ArrayList<Material>();
			for (var materialEntry : materialList) {
				var material = materialEntry.material;
				var aiMaterial = AIMaterial.calloc(stack);
				materialPointers.put(aiMaterial);

				Material[] materialTextures = {
					material.parent != null ? material.parent : material,
					material.normalMap,
					material.displacementMap,
					material.roughnessMap,
					material.ambientOcclusionMap
//					material.flowMap // Has an erroneous default value I think
				};

				// Writing properties is unfortunately quite cumbersome
				var materialPropertyPointers = new ArrayList<Long>();

				AIString propertyKey;
				ByteBuffer propertyBuffer;
				propertyKey = AIString.malloc(stack).data(stack.UTF8(AI_MATKEY_NAME));
				var materialName = AIString.malloc(stack).data(stack.UTF8(material.name()));
				propertyBuffer = stack.malloc(AIMaterialProperty.ALIGNOF, AIMaterialProperty.SIZEOF)
					// property name aiString
					.put(memByteBuffer(propertyKey))
					// texture type
					.position(AIMaterialProperty.MSEMANTIC)
					.putInt(aiTextureType_NONE)
					// texture index
					.putInt(0) // always zero for non-texture properties
					// data size (based on https://github.com/assimp/assimp/blob/42386b829ca44f8ca1929f437cae21729d8469d0/code/Material/MaterialSystem.cpp#L528)
					.putInt(materialName.length() + 1 + 4) // num chars + null terminator + integer
					// data type
					.putInt(aiPTI_String)
					// pointer to the data
					.position(AIMaterialProperty.MDATA) // alignment is important
					.put(memByteBuffer(stack.pointers(materialName)));
				materialPropertyPointers.add(memAddress0(propertyBuffer));

				propertyKey = AIString.malloc(stack).data(stack.UTF8(AI_MATKEY_GLTF_ALPHAMODE));
				var alphaMode = AIString.malloc(stack).data(stack.UTF8("MASK"));
				propertyBuffer = stack.malloc(AIMaterialProperty.ALIGNOF, AIMaterialProperty.SIZEOF)
					// property name aiString
					.put(memByteBuffer(propertyKey))
					// texture type
					.position(AIMaterialProperty.MSEMANTIC)
					.putInt(aiTextureType_NONE)
					// texture index
					.putInt(0) // always zero for non-texture properties
					// data size
					.putInt(alphaMode.length() + 1 + 4)
					// data type
					.putInt(aiPTI_String)
					// pointer to the data
					.position(AIMaterialProperty.MDATA) // alignment is important
					.put(memByteBuffer(stack.pointers(alphaMode)));
				materialPropertyPointers.add(memAddress0(propertyBuffer));

				for (var texture : materialTextures) {
					if (texture == null || texture == Material.NONE || expandedMaterials.contains(texture))
						continue;

					int index = expandedMaterials.size();
					expandedMaterials.add(texture);

					int type = aiTextureType_DIFFUSE; // FBX doesn't like aiTextureType_BASE_COLOR;
					if (texture == material.normalMap) {
						type = aiTextureType_NORMALS;
					} else if (texture == material.displacementMap) {
						type = aiTextureType_DISPLACEMENT;
					} else if (texture == material.roughnessMap) {
						type = aiTextureType_DIFFUSE_ROUGHNESS;
					} else if (texture == material.ambientOcclusionMap) {
						type = aiTextureType_AMBIENT_OCCLUSION;
					} else if (texture == material.flowMap) {
						type = aiTextureType_UNKNOWN;
					}

					propertyKey = AIString.malloc(stack).data(stack.UTF8(_AI_MATKEY_TEXTURE_BASE));
					var materialTextureUri = AIString.malloc(stack).data(stack.UTF8("*" + index)); // reference embedded texture by index
					propertyBuffer = stack.malloc(AIMaterialProperty.ALIGNOF, AIMaterialProperty.SIZEOF)
						.put(memByteBuffer(propertyKey))
						.position(AIMaterialProperty.MSEMANTIC)
						.putInt(type)
						.putInt(0) // index of the same texture type in the same material
						.putInt(materialTextureUri.length() + 1 + 4)
						.putInt(aiPTI_String)
						.position(AIMaterialProperty.MDATA) // alignment is important
						.put(memByteBuffer(stack.pointers(materialTextureUri)));
					materialPropertyPointers.add(memAddress0(propertyBuffer));

					// I think this is optional when only one UV map is used
//					propertyKey = AIString.malloc(stack).data(stack.UTF8(_AI_MATKEY_UVWSRC_BASE));
//					var materialUvMap = stack.ints(0); // UV map index
//					propertyBuffer = stack.malloc(AIMaterialProperty.ALIGNOF, AIMaterialProperty.SIZEOF)
//						.put(memByteBuffer(propertyKey))
//						.position(AIMaterialProperty.MSEMANTIC)
//						.putInt(type)
//						.putInt(0) // index of the same texture type in the same material
//						.putInt(Integer.BYTES)
//						.putInt(aiPTI_Integer)
//						.position(AIMaterialProperty.MDATA) // alignment is important
//						.put(memByteBuffer(stack.pointers(materialUvMap)));
//					materialPropertyPointers.add(memAddress0(propertyBuffer));
				}

				var mProperties = stack.mallocPointer(materialPropertyPointers.size());
				for (long address : materialPropertyPointers)
					mProperties.put(address);
				aiMaterial
					.mProperties(mProperties.flip())
					.mNumAllocated(mProperties.limit()); // used internally by Assimp when copying scenes
			}

			var texturePointers = stack.callocPointer(expandedMaterials.size());
			for (Material material : expandedMaterials) {
				String textureName = material.name().toLowerCase();

				BufferedImage textureImage = null;
				// Check if HD provides a texture for the material
				if (material != Material.VANILLA) {
					textureImage = textureManager.loadTextureImage(material);
					if (textureImage == null && material.vanillaTextureIndex == -1) {
						log.warn("No texture found for material: {}", material);
						continue;
					}
				}

				// Fallback to loading a vanilla image
				if (textureImage == null) {
					int vanillaIndex = material.vanillaTextureIndex;
					var texture = textureProvider.getTextures()[vanillaIndex];
					if (texture != null) {
						int[] pixels = textureProvider.load(vanillaIndex);
						if (pixels == null) {
							log.warn("No pixels for vanilla texture at index {}", vanillaIndex);
							continue;
						}
						int resolution = (int) Math.round(Math.sqrt(pixels.length));
						if (resolution * resolution != pixels.length) {
							log.warn("Unknown dimensions for vanilla texture at index {} ({} pixels)", vanillaIndex, pixels.length);
							continue;
						}

						for (int j = 0; j < pixels.length; j++) {
							int rgb = pixels[j];
							// Black is considered transparent in vanilla, with anything else being fully opaque
							int alpha = rgb == 0 ? 0 : 0xFF;
							vanillaImage.setRGB(j % 128, j / 128, alpha << 24 | rgb & 0xFFFFFF);
						}

						textureImage = vanillaImage;
					}
				}

				if (textureImage == null) {
					log.error("Failed to load texture for material: {}", material);
					chatMessageManager.queue(QueuedMessage.builder()
						.type(ChatMessageType.CONSOLE)
						.runeLiteFormattedMessage(new ChatMessageBuilder()
							.append(Color.RED, "Failed to load texture for material " + material)
							.build())
						.build());
					return; // TODO: skip textures which fail to load. Atm it crashes due to null pointers
				} else {
					AffineTransform t = new AffineTransform();
					// Flip vanilla textures horizontally
					if (textureImage == vanillaImage) {
						t.translate(textureImage.getWidth(), 0);
						t.scale(-1, 1);
					}
					AffineTransformOp op = new AffineTransformOp(t, AffineTransformOp.TYPE_NEAREST_NEIGHBOR);
					var orientedImage = new BufferedImage(
						textureImage.getWidth(),
						textureImage.getHeight(),
						BufferedImage.TYPE_INT_ARGB
					);
					op.filter(textureImage, orientedImage);

					log.debug("Loaded texture {} with dimensions {}x{}", material, orientedImage.getWidth(), orientedImage.getHeight());
					ByteBuffer textureEncoded;
					try (var stream = new ByteArrayOutputStream()) {
						ImageIO.write(orientedImage, "png", stream);
						byte[] encoded = stream.toByteArray();
						assert encoded.length > 0;
						textureEncoded = memAlloc(encoded.length)
							.put(encoded)
							.flip();
						cleanup.add(memAddress0(textureEncoded));
					} catch (IOException ex) {
						log.error("Error while encoding texture for material: {}", material, ex);
						continue;
					}

					var aiTexture = stack.calloc(AITexture.ALIGNOF, AITexture.SIZEOF)
						.putInt(textureEncoded.limit()) // texture width = data size if encoded image
						.putInt(0) // texture height = zero if encoded image
						.position(AITexture.ACHFORMATHINT)
						.put(stack.ASCII("png"))
						.position(AITexture.PCDATA)
						.put(memByteBuffer(stack.pointers(memAddress0(textureEncoded))))
						.position(AITexture.MFILENAME)
						.put(memByteBuffer(AIString.malloc(stack).data(stack.UTF8(textureName + ".png"))))
						.flip(); // skipping flip can easily result in an incorrect offset retrieved with memAddress
					texturePointers.put(memAddress0(aiTexture));
				}
			}

			scene
				.mMaterials(materialPointers.flip())
				.mTextures(texturePointers.flip());

			aiEnableVerboseLogging(true);
			var logStream = AILogStream.calloc(stack);
			aiGetPredefinedLogStream(aiDefaultLogStream_STDERR, (ByteBuffer) null, logStream);
			aiAttachLogStream(logStream);

			// Extreme hack to make Assimp v5.2.5 skip the aiProcess_JoinIdenticalVertices step, even though it's forced for glTF2,
			// by making it think that the step has already been completed:
			// https://github.com/assimp/assimp/blob/9519a62dd20799c5493c638d1ef5a6f484e5faf1/code/Common/Exporter.cpp#L398-L400C27
			// https://github.com/assimp/assimp/blob/9519a62dd20799c5493c638d1ef5a6f484e5faf1/code/Common/ScenePrivate.h#L57-L74
			var scenePrivate = stack.calloc(Pointer.POINTER_SIZE + Integer.BYTES + Integer.BYTES)
				.put(memByteBuffer(stack.pointers(0)))
				.putInt(aiProcess_JoinIdenticalVertices);
			memPutAddress(scene.address() + AIScene.MPRIVATE, memAddress0(scenePrivate));

			int statusCode = aiExportScene(scene, formatId, exportPath.toPath().toString(),
				aiProcess_EmbedTextures
				| aiProcess_PreTransformVertices
				| aiProcess_ValidateDataStructure
				| aiProcess_FindInvalidData
			);

			aiDetachLogStream(logStream);

			if (statusCode != 0) {
				System.err.println(aiGetErrorString());
				chatMessageManager.queue(QueuedMessage.builder()
					.type(ChatMessageType.CONSOLE)
					.runeLiteFormattedMessage(new ChatMessageBuilder()
						.append("Error while exporting " + objectName.getKey() + " model for ")
						.append(Color.CYAN, objectName.getValue())
						.append(Color.LIGHT_GRAY, " (status code " + statusCode + ")")
						.build())
					.build());
			} else {
				chatMessageManager.queue(QueuedMessage.builder()
					.type(ChatMessageType.CONSOLE)
					.runeLiteFormattedMessage(new ChatMessageBuilder()
						.append("Exported model as ")
						.append(Color.CYAN, exportPath.getFilename())
						.append(" (" + totalFaceCount + " faces)")
						.build())
					.build());
			}
		} finally {
			for (long address : cleanup)
				if (address != 0)
					nmemFree(address);
			cleanup.clear();
			for (var buffer : cleanupFaceBuffers)
				buffer.free();
			cleanupFaceBuffers.clear();
		}
	}

	private void pushVanillaModel(
		Model model,
		AIFace.Buffer faces,
		IntBuffer indices,
		FloatBuffer vertices,
		FloatBuffer uvs,
		FloatBuffer normals,
		FloatBuffer colors
	) {
		final int triangleCount = model.getFaceCount();

		final float[] verticesX = model.getVerticesX();
		final float[] verticesY = model.getVerticesY();
		final float[] verticesZ = model.getVerticesZ();

		final int[] indices1 = model.getFaceIndices1();
		final int[] indices2 = model.getFaceIndices2();
		final int[] indices3 = model.getFaceIndices3();

		final int[] color1s = model.getFaceColors1();
		final int[] color2s = model.getFaceColors2();
		final int[] color3s = model.getFaceColors3();

		final short[] faceTextures = model.getFaceTextures();

		final byte[] transparencies = model.getFaceTransparencies();

		final byte overrideAmount = model.getOverrideAmount();
		final byte overrideHue = model.getOverrideHue();
		final byte overrideSat = model.getOverrideSaturation();
		final byte overrideLum = model.getOverrideLuminance();

		for (int face = 0; face < triangleCount; ++face) {
			faces.get(face).mIndices(memSlice(indices, 0, 3));
			for (int i = 0; i < 3; i++)
				indices.put(indices.position());

			int color1 = color1s[face];
			int color2 = color2s[face];
			int color3 = color3s[face];

			if (color3 == -1) {
				color2 = color3 = color1;
			} else if (color3 == -2) {
				vertices.put(ZEROS, 0, 9);
				colors.put(ZEROS, 0, 12);
				normals.put(ZEROS, 0, 9);
				if (faceTextures != null)
					uvs.put(ZEROS, 0, 9);
				continue;
			}

			// HSL override is not applied to textured faces
			if (faceTextures == null || faceTextures[face] == -1) {
				if (overrideAmount > 0) {
					color1 = interpolateHSL(color1, overrideHue, overrideSat, overrideLum, overrideAmount);
					color2 = interpolateHSL(color2, overrideHue, overrideSat, overrideLum, overrideAmount);
					color3 = interpolateHSL(color3, overrideHue, overrideSat, overrideLum, overrideAmount);
				}
			}

			int triA = indices1[face];
			int triB = indices2[face];
			int triC = indices3[face];

			vertices.put(verticesX[triA]).put(verticesY[triA]).put(verticesZ[triA]);
			vertices.put(verticesX[triB]).put(verticesY[triB]).put(verticesZ[triB]);
			vertices.put(verticesX[triC]).put(verticesY[triC]).put(verticesZ[triC]);

			float alpha = 1;
			if (transparencies != null)
				alpha -= (float) (transparencies[face] & 0xFF) / 0xFF;
			colors
				.put(ColorUtils.packedHslToSrgb(color1)).put(alpha)
				.put(ColorUtils.packedHslToSrgb(color2)).put(alpha)
				.put(ColorUtils.packedHslToSrgb(color3)).put(alpha);

			final int[] vertexNormalsX = model.getVertexNormalsX();
			final int[] vertexNormalsY = model.getVertexNormalsY();
			final int[] vertexNormalsZ = model.getVertexNormalsZ();
			normals
				.put(vertexNormalsX[triA]).put(vertexNormalsY[triA]).put(vertexNormalsZ[triA])
				.put(vertexNormalsX[triB]).put(vertexNormalsY[triB]).put(vertexNormalsZ[triB])
				.put(vertexNormalsX[triC]).put(vertexNormalsY[triC]).put(vertexNormalsZ[triC]);

			fillVanillaUvs(uvs, model, face);
		}
	}

	private void fillVanillaUvs(FloatBuffer uvs, Model model, int face) {
		final short[] faceTextures = model.getFaceTextures();
		final byte[] textureFaces = model.getTextureFaces();
		if (faceTextures == null || faceTextures[face] == -1) {
			uvs.put(ZEROS, 0, 9);
			return;
		}

		final float[] verticesX = model.getVerticesX();
		final float[] verticesY = model.getVerticesY();
		final float[] verticesZ = model.getVerticesZ();

		final int[] indices1 = model.getFaceIndices1();
		final int[] indices2 = model.getFaceIndices2();
		final int[] indices3 = model.getFaceIndices3();

		final int[] texIndices1 = model.getTexIndices1();
		final int[] texIndices2 = model.getTexIndices2();
		final int[] texIndices3 = model.getTexIndices3();

		int triA = indices1[face];
		int triB = indices2[face];
		int triC = indices3[face];

		float u0, u1, u2, v0, v1, v2;

		if (textureFaces != null && textureFaces[face] != -1) {
			int tfaceIdx = textureFaces[face] & 0xff;
			int texA = texIndices1[tfaceIdx];
			int texB = texIndices2[tfaceIdx];
			int texC = texIndices3[tfaceIdx];

			// v1 = vertex[texA]
			float v1x = verticesX[texA];
			float v1y = verticesY[texA];
			float v1z = verticesZ[texA];
			// v2 = vertex[texB] - v1
			float v2x = verticesX[texB] - v1x;
			float v2y = verticesY[texB] - v1y;
			float v2z = verticesZ[texB] - v1z;
			// v3 = vertex[texC] - v1
			float v3x = verticesX[texC] - v1x;
			float v3y = verticesY[texC] - v1y;
			float v3z = verticesZ[texC] - v1z;

			// v4 = vertex[triangleA] - v1
			float v4x = verticesX[triA] - v1x;
			float v4y = verticesY[triA] - v1y;
			float v4z = verticesZ[triA] - v1z;
			// v5 = vertex[triangleB] - v1
			float v5x = verticesX[triB] - v1x;
			float v5y = verticesY[triB] - v1y;
			float v5z = verticesZ[triB] - v1z;
			// v6 = vertex[triangleC] - v1
			float v6x = verticesX[triC] - v1x;
			float v6y = verticesY[triC] - v1y;
			float v6z = verticesZ[triC] - v1z;

			// v7 = v2 x v3
			float v7x = v2y * v3z - v2z * v3y;
			float v7y = v2z * v3x - v2x * v3z;
			float v7z = v2x * v3y - v2y * v3x;

			// v8 = v3 x v7
			float v8x = v3y * v7z - v3z * v7y;
			float v8y = v3z * v7x - v3x * v7z;
			float v8z = v3x * v7y - v3y * v7x;

			// f = 1 / (v8 ⋅ v2)
			float f = 1.0F / (v8x * v2x + v8y * v2y + v8z * v2z);

			// u0 = (v8 ⋅ v4) * f
			u0 = (v8x * v4x + v8y * v4y + v8z * v4z) * f;
			// u1 = (v8 ⋅ v5) * f
			u1 = (v8x * v5x + v8y * v5y + v8z * v5z) * f;
			// u2 = (v8 ⋅ v6) * f
			u2 = (v8x * v6x + v8y * v6y + v8z * v6z) * f;

			// v8 = v2 x v7
			v8x = v2y * v7z - v2z * v7y;
			v8y = v2z * v7x - v2x * v7z;
			v8z = v2x * v7y - v2y * v7x;

			// f = 1 / (v8 ⋅ v3)
			f = 1.0F / (v8x * v3x + v8y * v3y + v8z * v3z);

			// v0 = (v8 ⋅ v4) * f
			v0 = (v8x * v4x + v8y * v4y + v8z * v4z) * f;
			// v1 = (v8 ⋅ v5) * f
			v1 = (v8x * v5x + v8y * v5y + v8z * v5z) * f;
			// v2 = (v8 ⋅ v6) * f
			v2 = (v8x * v6x + v8y * v6y + v8z * v6z) * f;
		} else {
			// Without a texture face, the client assigns tex = triangle, but the resulting
			// calculations can be reduced:
			//
			// v1 = vertex[texA]
			// v2 = vertex[texB] - v1
			// v3 = vertex[texC] - v1
			//
			// v4 = 0
			// v5 = v2
			// v6 = v3
			//
			// v7 = v2 x v3
			//
			// v8 = v3 x v7
			// u0 = (v8 . v4) / (v8 . v2) // 0 because v4 is 0
			// u1 = (v8 . v5) / (v8 . v2) // 1 because v5=v2
			// u2 = (v8 . v6) / (v8 . v2) // 0 because v8 is perpendicular to v3/v6
			//
			// v8 = v2 x v7
			// v0 = (v8 . v4) / (v8 ⋅ v3) // 0 because v4 is 0
			// v1 = (v8 . v5) / (v8 ⋅ v3) // 0 because v8 is perpendicular to v5/v2
			// v2 = (v8 . v6) / (v8 ⋅ v3) // 1 because v6=v3

			u0 = 0f;
			v0 = 0f;

			u1 = 1f;
			v1 = 0f;

			u2 = 0f;
			v2 = 1f;
		}

		uvs
			.put(u0).put(v0).put(0)
			.put(u1).put(v1).put(0)
			.put(u2).put(v2).put(0);
	}

	@Subscribe
	public void onPostMenuSort(PostMenuSort postMenuSort) {
		// The menu is not rebuilt when it is open, so don't swap or else it will
		// repeatedly swap entries
		if (client.isMenuOpen() || selectedObject == null)
			return;

		var name = getTypeAndName(selectedObject);
		client.createMenuEntry(client.getMenuEntries().length)
			.setOption("Export " + name.getKey())
			.setTarget("<col=00ffff>" + name.getValue() + "</col>")
			.setIdentifier(MenuAction.RUNELITE_HIGH_PRIORITY.getId())
			.onClick(e -> clientThread.invoke(() -> exportModel(name, selectedObject)));
	}

	@Subscribe
	public void onClientTick(ClientTick tick) {
		if (clientTickCount++ % 5 == 0)
			updateHoverSet();

		if (hoverSet.isEmpty()) {
			selectedObject = null;
			return;
		}

		int camX = client.getCameraX();
		int camY = client.getCameraY();
		int camZ = client.getCameraZ(); // up

		selectedIndex = HDUtils.clamp(selectedIndex, 0, Math.max(0, hoverSet.size() - 1));
		selectedObject = hoverSet.stream()
			.map(e -> {
				int x = 0, y = 0, z = 0;
				if (e instanceof Actor) {
					var a = (Actor) e;
					var lp = a.getLocalLocation();
					x = lp.getX();
					y = lp.getY();

					if (a instanceof NPC) {
						int size = client.getNpcDefinition(((NPC) a).getId()).getSize();
						var tileHeightPoint = new LocalPoint(
							size * LOCAL_HALF_TILE_SIZE - LOCAL_HALF_TILE_SIZE + x,
							size * LOCAL_HALF_TILE_SIZE - LOCAL_HALF_TILE_SIZE + z
						);
						z = getTileHeight(client, tileHeightPoint, client.getPlane());
					} else if (a instanceof Player) {
						z = getTileHeight(client, lp, client.getPlane());
					}
				} else if (e instanceof TileObject) {
					var o = (TileObject) e;
					x = o.getX();
					y = o.getY();
					z = o.getZ();
				} else if (e instanceof GraphicsObject) {
					var g = (GraphicsObject) e;
					LocalPoint lp = g.getLocation();
					x = lp.getX();
					y = lp.getY();
					z = g.getZ();
				} else if (e instanceof Projectile) {
					var p = (Projectile) e;
					x = (int) p.getX();
					y = (int) p.getY();
					z = (int) p.getZ();
				}
				x -= camX;
				y -= camY;
				z -= camZ;
				int dist = x * x + y * y + z * z;
				return Map.entry(dist, e);
			})
			.sorted(Comparator.comparingInt(Map.Entry::getKey))
			.skip(selectedIndex)
			.map(Map.Entry::getValue)
			.findFirst()
			.orElse(null);
	}

	@Override
	public Dimension render(Graphics2D g) {
		if (selectedObject != null) {
			if (selectedObject instanceof NPC) {
				modelOutlineRenderer.drawOutline((NPC) selectedObject, OUTLINE_WIDTH, OUTLINE_COLOR, OUTLINE_FEATHER);
			} else if (selectedObject instanceof Player) {
				modelOutlineRenderer.drawOutline((Player) selectedObject, OUTLINE_WIDTH, OUTLINE_COLOR, OUTLINE_FEATHER);
			} else if (selectedObject instanceof TileObject) {
				modelOutlineRenderer.drawOutline((TileObject) selectedObject, OUTLINE_WIDTH, OUTLINE_COLOR, OUTLINE_FEATHER);
			} else if (selectedObject instanceof GraphicsObject) {
				modelOutlineRenderer.drawOutline((GraphicsObject) selectedObject, OUTLINE_WIDTH, OUTLINE_COLOR, OUTLINE_FEATHER);
			} else if (selectedObject instanceof Projectile) {
				Shape shape = getConvexHull((Projectile) selectedObject);
				if (shape != null) {
					g.setColor(OUTLINE_COLOR);
					g.setStroke(new BasicStroke(OUTLINE_WIDTH));
					g.draw(shape);
				}
			}
		}
		return null;
	}

	private void updateHoverSet() {
		hoverSet.clear();

		if (!client.isKeyPressed(EXPORTER_KEY))
			return;

		Point canvasPoint = client.getMouseCanvasPosition();
		if (canvasPoint.getX() == -1 && canvasPoint.getY() == -1)
			return;
		Point2D hoverPoint = new Point2D.Float(canvasPoint.getX(), canvasPoint.getY());

		Scene scene = client.getScene();
		Tile[][][] tiles = scene.getTiles();
		for (int plane = tiles.length - 1; plane >= 0; plane--) {
			Tile[][] tilePlane = tiles[plane];
			for (Tile[] tileColumn : tilePlane) {
				for (Tile tile : tileColumn) {
					if (tile == null)
						continue;

					if (localToCanvas(client, tile.getLocalLocation(), tile.getRenderLevel()) == null)
						continue;

					processTile(hoverPoint, tile);
				}
			}
		}

		for (var npc : client.getNpcs())
			addIfHovered(hoverPoint, npc);
		for (var player : client.getPlayers())
			addIfHovered(hoverPoint, player);
		for (var object : client.getGraphicsObjects())
			addIfHovered(hoverPoint, object);
		for (var projectile : client.getProjectiles())
			addIfHovered(hoverPoint, projectile);
	}

	private void processTile(Point2D hoverPoint, Tile tile) {
		var bridge = tile.getBridge();
		if (bridge != null)
			processTile(hoverPoint, bridge);

		if (Perspective.getCanvasTilePoly(client, tile.getLocalLocation()) == null)
			return;

		for (GameObject gameObject : tile.getGameObjects())
			if (gameObject != null)
				addIfHovered(hoverPoint, gameObject);

		WallObject wallObject = tile.getWallObject();
		if (wallObject != null)
			addIfHovered(hoverPoint, wallObject);

		DecorativeObject decorativeObject = tile.getDecorativeObject();
		if (decorativeObject != null)
			addIfHovered(hoverPoint, decorativeObject);

		GroundObject groundObject = tile.getGroundObject();
		if (groundObject != null)
			addIfHovered(hoverPoint, groundObject);
	}

	private void addIfHovered(Point2D hoverPoint, Object object) {
		Shape shape = null;
		if (object instanceof Actor) {
			shape = ((Actor) object).getConvexHull();
		} else if (object instanceof TileObject) {
			shape = ((TileObject) object).getClickbox();
		} else if (object instanceof GraphicsObject) {
			shape = getConvexHull((GraphicsObject) object);
		} else if (object instanceof Projectile) {
			shape = getConvexHull((Projectile) object);
		}

		if (shape != null && shape.contains(hoverPoint))
			hoverSet.add(object);
	}

	private Shape getConvexHull(GraphicsObject o) {
		var model = o.getModel();
		int[] x2d = new int[model.getVerticesCount()];
		int[] y2d = new int[model.getVerticesCount()];

		Perspective.modelToCanvas(
			client,
			model.getVerticesCount(),
			o.getLocation().getX(),
			o.getLocation().getY(),
			o.getZ(),
			0,
			model.getVerticesX(),
			model.getVerticesZ(),
			model.getVerticesY(),
			x2d,
			y2d
		);

		return Jarvis.convexHull(x2d, y2d);
	}

	private Shape getConvexHull(Projectile p) {
		var model = p.getModel();
		int[] x2d = new int[model.getVerticesCount()];
		int[] y2d = new int[model.getVerticesCount()];

		Perspective.modelToCanvas(
			client,
			model.getVerticesCount(),
			(int) p.getX(),
			(int) p.getY(),
			(int) p.getZ(),
			0,
			model.getVerticesX(),
			model.getVerticesZ(),
			model.getVerticesY(),
			x2d,
			y2d
		);

		return Jarvis.convexHull(x2d, y2d);
	}

	private long getHash(Object object) {
		if (object instanceof Node)
			return ((Node) object).getHash();
		if (object instanceof TileObject)
			return ((TileObject) object).getHash();
		return 0;
	}

	private int getBakedOrientation(Object object, Renderable renderable) {
		if (object instanceof GameObject)
			return HDUtils.getBakedOrientation(((GameObject) object).getConfig());
		if (object instanceof WallObject) {
			var wallObject = (WallObject) object;
			return renderable == wallObject.getRenderable1() ?
				HDUtils.convertWallObjectOrientation(wallObject.getOrientationA()) :
				HDUtils.convertWallObjectOrientation(wallObject.getOrientationB());
		}
		if (object instanceof DecorativeObject)
			return HDUtils.getBakedOrientation(((DecorativeObject) object).getConfig());
		if (object instanceof GroundObject)
			return HDUtils.getBakedOrientation(((GroundObject) object).getConfig());
		return 0;
	}

	private Map.Entry<String, String> getTypeAndName(Object renderable) {
		String type = "model";
		String name = "Unknown";
		if (renderable instanceof TileObject) {
			var object = (TileObject) renderable;
			var def = client.getObjectDefinition(object.getId());
			type = "tile object";
			name = "ID " + def.getId();
			if (!def.getName().equals("null")) {
				name = def.getName() + " (" + name + ")";
			} else if (def.getImpostorIds() != null) {
				def = def.getImpostor();
				if (def != null && !def.getName().equals("null"))
					name = def.getName() + " (" + name + ")";
			}
		} else if (renderable instanceof NPC) {
			var npc = (NPC) renderable;
			var def = client.getNpcDefinition(npc.getId());
			type = "NPC";
			name = "ID " + def.getId();
			if (!def.getName().equals("null"))
				name = def.getName() + " (" + name + ")";
		} else if (renderable instanceof Player) {
			var player = (Player) renderable;
			type = "player";
			if (player.getName() != null)
				name = player.getName();
		} else if (renderable instanceof GraphicsObject) {
			var object = (GraphicsObject) renderable;
			type = "graphic";
			name = "ID " + object.getId();
		} else if (renderable instanceof Projectile) {
			var projectile = (Projectile) renderable;
			type = "projectile";
			name = "ID " + projectile.getId();
		}
		return Map.entry(type, name);
	}

	private List<Renderable> resolveRenderables(Object object) {
		if (object instanceof WallObject) {
			var wallObject = (WallObject) object;
			return list(wallObject.getRenderable1(), wallObject.getRenderable2());
		}
		if (object instanceof DecorativeObject) {
			var decorativeObject = (DecorativeObject) object;
			return list(decorativeObject.getRenderable(), decorativeObject.getRenderable2());
		}
		if (object instanceof GroundObject)
			return list(((GroundObject) object).getRenderable());
		if (object instanceof GameObject)
			return list(((GameObject) object).getRenderable());
		if (object instanceof Renderable)
			return list((Renderable) object);
		return list();
	}

	private List<ModelInfo> resolveModels(Object object) {
		return resolveRenderables(object).stream()
			.filter(Objects::nonNull)
			.map(r -> new ModelInfo(object, r, r instanceof Model ? (Model) r : r.getModel()))
			.filter(i -> i.model != null && i.model.getFaceCount() > 0)
			.collect(Collectors.toList());
	}

	@Override
	public MouseEvent mouseClicked(MouseEvent event) {
		return event;
	}

	@Override
	public MouseEvent mousePressed(MouseEvent event) {
		return event;
	}

	@Override
	public MouseEvent mouseReleased(MouseEvent event) {
		return event;
	}

	@Override
	public MouseEvent mouseEntered(MouseEvent event) {
		return event;
	}

	@Override
	public MouseEvent mouseExited(MouseEvent event) {
		return event;
	}

	@Override
	public MouseEvent mouseDragged(MouseEvent event) {
		return event;
	}

	@Override
	public MouseEvent mouseMoved(MouseEvent event) {
		return event;
	}

	@Override
	public MouseWheelEvent mouseWheelMoved(MouseWheelEvent event) {
		if (client.isKeyPressed(EXPORTER_KEY)) {
			selectedIndex -= Math.signum(event.getWheelRotation());
			event.consume();
		}
		return event;
	}

	@Override
	public void keyTyped(KeyEvent event) {}

	@Override
	public void keyPressed(KeyEvent event) {
		if (!client.isKeyPressed(EXPORTER_KEY))
			return;

		switch (event.getKeyCode()) {
			case KeyEvent.VK_LEFT:
			case KeyEvent.VK_DOWN:
				selectedIndex--;
				event.consume();
				break;
			case KeyEvent.VK_RIGHT:
			case KeyEvent.VK_UP:
				selectedIndex++;
				event.consume();
				break;
		}
	}

	@Override
	public void keyReleased(KeyEvent event) {}

	private static <T> ArrayList<T> list(T... elements) {
		var list = new ArrayList<T>();
		for (T t : elements)
			if (t != null)
				list.add(t);
		return list;
	}
}
