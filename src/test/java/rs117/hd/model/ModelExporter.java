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
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import javax.imageio.ImageIO;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.coords.*;
import net.runelite.api.events.*;
import net.runelite.api.gameval.*;
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
import rs117.hd.renderer.legacy.LegacyModelPusher;
import rs117.hd.renderer.legacy.LegacySceneContext;
import rs117.hd.renderer.zone.SceneManager;
import rs117.hd.scene.GamevalManager;
import rs117.hd.scene.MaterialManager;
import rs117.hd.scene.ModelOverrideManager;
import rs117.hd.scene.SceneContext;
import rs117.hd.scene.TextureManager;
import rs117.hd.scene.WaterTypeManager;
import rs117.hd.scene.water_types.WaterType;
import rs117.hd.scene.materials.Material;
import rs117.hd.scene.model_overrides.ModelOverride;
import rs117.hd.utils.ColorUtils;
import rs117.hd.utils.HDUtils;
import rs117.hd.utils.Mat4;
import rs117.hd.utils.ModelHash;

import static net.runelite.api.Perspective.*;
import static org.lwjgl.assimp.Assimp.AI_MATKEY_GLTF_ALPHAMODE;
import static org.lwjgl.assimp.Assimp.AI_MATKEY_NAME;
import static org.lwjgl.assimp.Assimp._AI_MATKEY_TEXTURE_BASE;
import static org.lwjgl.assimp.Assimp.aiAttachLogStream;
import static org.lwjgl.assimp.Assimp.aiDefaultLogStream_STDERR;
import static org.lwjgl.assimp.Assimp.aiDetachLogStream;
import static org.lwjgl.assimp.Assimp.aiEnableVerboseLogging;
import static org.lwjgl.assimp.Assimp.aiExportScene;
import static org.lwjgl.assimp.Assimp.aiGetErrorString;
import static org.lwjgl.assimp.Assimp.aiGetExportFormatCount;
import static org.lwjgl.assimp.Assimp.aiGetExportFormatDescription;
import static org.lwjgl.assimp.Assimp.aiGetPredefinedLogStream;
import static org.lwjgl.assimp.Assimp.aiPTI_String;
import static org.lwjgl.assimp.Assimp.aiPrimitiveType_TRIANGLE;
import static org.lwjgl.assimp.Assimp.aiProcess_EmbedTextures;
import static org.lwjgl.assimp.Assimp.aiProcess_FindInvalidData;
import static org.lwjgl.assimp.Assimp.aiProcess_JoinIdenticalVertices;
import static org.lwjgl.assimp.Assimp.aiProcess_ValidateDataStructure;
import static org.lwjgl.assimp.Assimp.aiReleaseExportFormatDescription;
import static org.lwjgl.assimp.Assimp.aiTextureType_AMBIENT_OCCLUSION;
import static org.lwjgl.assimp.Assimp.aiTextureType_DIFFUSE;
import static org.lwjgl.assimp.Assimp.aiTextureType_DIFFUSE_ROUGHNESS;
import static org.lwjgl.assimp.Assimp.aiTextureType_DISPLACEMENT;
import static org.lwjgl.assimp.Assimp.aiTextureType_NONE;
import static org.lwjgl.assimp.Assimp.aiTextureType_NORMALS;
import static org.lwjgl.system.MemoryUtil.memAddress0;
import static org.lwjgl.system.MemoryUtil.memAlloc;
import static org.lwjgl.system.MemoryUtil.memAllocFloat;
import static org.lwjgl.system.MemoryUtil.memAllocInt;
import static org.lwjgl.system.MemoryUtil.memByteBuffer;
import static org.lwjgl.system.MemoryUtil.memCopy;
import static org.lwjgl.system.MemoryUtil.memPutAddress;
import static org.lwjgl.system.MemoryUtil.memSlice;
import static org.lwjgl.system.MemoryUtil.memUTF8;
import static org.lwjgl.system.MemoryUtil.nmemFree;
import static rs117.hd.renderer.zone.SceneUploader.interpolateHSL;
import static rs117.hd.scene.materials.Material.MAX_MATERIAL_INDEX;
import static rs117.hd.utils.MathUtils.*;
import static rs117.hd.utils.ResourcePath.path;

@Slf4j
public class ModelExporter extends Overlay implements MouseListener, MouseWheelListener, KeyListener, AutoCloseable {
	private static final boolean EMBED_TEXTURES = false;
	private static final Color OUTLINE_COLOR = new Color(0xFFFF00FF, true);
	private static final int OUTLINE_WIDTH = 4;
	private static final int OUTLINE_FEATHER = 4;
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
	private GamevalManager gamevalManager;

	@Inject
	private ModelOverrideManager modelOverrideManager;

	@Inject
	private LegacyModelPusher modelPusher;

	@Inject
	private MaterialManager materialManager;

	@Inject
	private SceneManager sceneManager;

	@Inject
	private SceneTerrainExporter terrainExporter;

	private int clientTickCount;
	private int selectedIndex;
	private int lastSelectedIndex = -1;
	private Object selectedObject;
	private final HashSet<Object> hoverSet = new HashSet<>();
	private final Map<ActorSpotAnim, Actor> spotAnimActors = new IdentityHashMap<>();
	private final Set<Object> hoverChecked = Collections.newSetFromMap(new IdentityHashMap<>());

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
		selectedObject = null;
		hoverSet.clear();
		hoverChecked.clear();
		spotAnimActors.clear();
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
		WorldView worldView;
		Tile tile;
		LegacySceneContext terrain;

		ModelInfo(Object object, Renderable renderable, Model model) {
			this.object = object;
			this.renderable = renderable;
			this.model = model;
		}

		int faceCount() {
			return terrain == null ? model.getFaceCount() : terrain.getVertexOffset() / 3;
		}
	}

	@AllArgsConstructor
	private static class MeshData {
		AIMesh mesh;
		AIFace.Buffer faces;
	}

	public void exportModel(Map.Entry<String, String> objectName, Object object) {
		exportModels(objectName, resolveModels(object), false);
	}

	private void exportModels(Map.Entry<String, String> objectName, List<ModelInfo> modelInfos, boolean wholeScene) {
		if (materialManager.uboMaterials == null)
			return;

		SceneContext currentSceneContext = plugin.getSceneContext();
		if (currentSceneContext == null) {
			log.debug("Scene context is null");
			return;
		}

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
		String exportName = objectName.getKey() + "-" + objectName.getValue();
		if (!wholeScene && !modelInfos.isEmpty()) {
			var object = modelInfos.get(0).object;
			try (var gamevals = gamevalManager.obtainHandle()) {
				String type = objectName.getKey();
				int id = -1;
				String name = null;
				String gameval = null;
				if (object instanceof NPC) {
					var npc = (NPC) object;
					id = npc.getId();
					name = npc.getName();
					gameval = gamevals.getNpcName(id);
				} else if (object instanceof TileObject) {
					type = "object";
					id = ((TileObject) object).getId();
					var definition = client.getObjectDefinition(id);
					if (definition.getImpostorIds() != null)
						definition = definition.getImpostor();
					name = definition == null ? null : definition.getName();
					gameval = gamevals.getObjectName(id);
				} else if (object instanceof Player) {
					id = ((Player) object).getId();
					name = ((Player) object).getName();
				} else if (object instanceof ActorSpotAnim) {
					id = ((ActorSpotAnim) object).getId();
					gameval = gamevals.getSpotanimName(id);
				} else if (object instanceof GraphicsObject) {
					id = ((GraphicsObject) object).getId();
					gameval = gamevals.getSpotanimName(id);
				} else if (object instanceof Projectile) {
					id = ((Projectile) object).getId();
					gameval = gamevals.getSpotanimName(id);
				}
				if (id != -1) {
					exportName = type + "-" + id;
					if (name != null && !name.isEmpty() && !name.equals("null"))
						exportName += "-" + name;
					if (gameval != null && !gameval.isEmpty())
						exportName += "-" + gameval;
				}
			}
		}
		var filename = exportName
			.replace(' ', '_')
			.replaceAll("[^0-9a-zA-Z_ -]", "")
			.toLowerCase(java.util.Locale.ROOT);
		var exportPath = path("model-exports", filename + "." + extension);
		exportPath.resolve("..").mkdirs();
		int collisionCounter = 2;
		while (exportPath.exists())
			exportPath = path("model-exports", filename + "_" + (collisionCounter++) + "." + extension);

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

		// Custom UVs are indexed against one runtime Model, not a combined object's renderables.
		if (!wholeScene && (modelInfos.size() != 1 || modelInfos.get(0).model.getFaceCount() > HdPlugin.MAX_FACE_COUNT)) {
			chatMessageManager.queue(QueuedMessage.builder()
				.type(ChatMessageType.CONSOLE)
				.runeLiteFormattedMessage("Cannot export this object for custom UV mapping: select a single model with at most " +
					HdPlugin.MAX_FACE_COUNT + " faces.")
				.build());
			return;
		}

		var cleanup = new ArrayList<Long>();
		var cleanupFaceBuffers = new ArrayList<AIFace.Buffer>();
		var sceneContext = new LegacySceneContext(
			client, currentSceneContext.scene,
			currentSceneContext.expandedMapLoadingChunks, null
		);
		// Scene exports need substantially more native metadata than the thread-local stack.
		var metadata = memAlloc(Math.max(1024 * 1024, Math.multiplyExact(modelInfos.size(), 8192)));
		cleanup.add(memAddress0(metadata));
		try (var stack = MemoryStack.create(metadata)) {
			int totalFaceCount = modelInfos.stream().mapToInt(ModelInfo::faceCount).reduce(0, Math::addExact);
			int totalVertexCount = totalFaceCount * 3;

			var vertices = memAllocFloat(totalVertexCount * 3);
			cleanup.add(memAddress0(vertices));
			var uvs = memAllocFloat(totalVertexCount * 3);
			cleanup.add(memAddress0(uvs));
			// UV1 is immutable corner identity, independent of vertex welding and material grouping.
			var cornerIds = memAllocFloat(totalVertexCount * 3);
			cleanup.add(memAddress0(cornerIds));
			for (int corner = 0; corner < totalVertexCount; corner++)
				cornerIds.put(corner).put(totalVertexCount).put(0);
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
			var nodes = new ArrayList<AINode>();
			var materialList = new ArrayList<Material>();
			var waterMaterials = new HashMap<WaterType, Material>();

			boolean worldUvWarningSent = false;
			for (ModelInfo modelInfo : modelInfos) {
				sceneContext.stagingBufferVertices.clear();
				sceneContext.stagingBufferNormals.clear();
				sceneContext.stagingBufferUvs.clear();
				int firstMesh = meshList.size();
				int firstVertex = vertices.position() / 3;
				long hash = getHash(modelInfo.object);
				int uuid = ModelHash.generateUuid(client, hash, modelInfo.renderable);
				int[] worldPos = sceneContext.sceneToWorld(
					ModelHash.getSceneX(hash),
					ModelHash.getSceneY(hash),
					ModelHash.getPlane(hash)
				);
				if (modelInfo.worldView != null) {
					var sourceScene = modelInfo.worldView.getScene();
					if (modelInfo.tile != null) {
						var location = modelInfo.tile.getWorldLocation();
						worldPos = new int[] { location.getX(), location.getY(), location.getPlane() };
					} else if (modelInfo.object instanceof Actor) {
						var location = ((Actor) modelInfo.object).getWorldLocation();
						worldPos = new int[] { location.getX(), location.getY(), location.getPlane() };
					} else {
						worldPos = new int[] { sourceScene.getBaseX(), sourceScene.getBaseY(), modelInfo.worldView.getPlane() };
					}
				}
				ModelOverride modelOverride = modelOverrideManager.getOverride(uuid, worldPos);
				var model = modelInfo.model;
				int vertexOffset = sceneContext.getVertexOffset();
				int uvOffset = sceneContext.getUvOffset();
				int bakedOrientation = getBakedOrientation(modelInfo.object, modelInfo.renderable);
				boolean skipHiddenFaces = LegacyModelPusher.SKIP_HIDDEN_FACES;
				LegacyModelPusher.SKIP_HIDDEN_FACES = false;
				try {
					if (model == null) {
						var terrain = modelInfo.terrain;
						int size = terrain.stagingBufferVertices.position();
						sceneContext.stagingBufferVertices.ensureCapacity(size).put(terrain.stagingBufferVertices.getBuffer().duplicate().flip());
						sceneContext.stagingBufferNormals.ensureCapacity(size).put(terrain.stagingBufferNormals.getBuffer().duplicate().flip());
						sceneContext.stagingBufferUvs.ensureCapacity(size).put(terrain.stagingBufferUvs.getBuffer().duplicate().flip());
						sceneContext.modelPusherResults[0] = sceneContext.modelPusherResults[1] = size / 12;
					} else {
						modelPusher.pushModel(sceneContext, modelInfo.tile, uuid, model, modelOverride, bakedOrientation, false);
					}
				} finally {
					LegacyModelPusher.SKIP_HIDDEN_FACES = skipHiddenFaces;
				}
				int numVertices = sceneContext.modelPusherResults[0] * 3;
				int numUvs = sceneContext.modelPusherResults[1] * 3;
				int numFaces = numVertices / 3;
				assert numUvs == numVertices || numUvs == 0;

				var meshDataMap = new HashMap<Material, MeshData>();
				var materialFaceCounts = new HashMap<Material, Integer>();
				var faceMaterials = new Material[numFaces];
				for (int face = 0; face < numFaces; face++) {
					Material material = Material.NONE;
					if (numUvs > 0) {
						int packed = Float.floatToRawIntBits(sceneContext.stagingBufferUvs.getBuffer().get((uvOffset + face * 3) * 4 + 3));
						material = materialManager.uboMaterials.materials[packed >>> 21 & MAX_MATERIAL_INDEX];
					}
					if (model == null) {
						int terrainData = Float.floatToRawIntBits(sceneContext.stagingBufferNormals.getBuffer().get(face * 12 + 3));
						int waterIndex = terrainData >> 3 & 0xFF;
						int depth = terrainData >> 11 & 0xFFF;
						if (waterIndex != 0 && depth == 0) {
							var water = WaterTypeManager.WATER_TYPES[waterIndex];
							material = waterMaterials.computeIfAbsent(water, type -> new Material()
								.name("117HD_Water_" + type.name)
								.normalMap((Material) waterProperty(type, "normalMap")));
						}
					}
					faceMaterials[face] = material;
					materialFaceCounts.merge(material, 1, Integer::sum);
				}
				for (int face = 0; face < numFaces; face++) {
					int faceVertexOffset = vertexOffset + face * 3;
					int faceUvOffset = uvOffset + face * 3;

					int materialData = 0;
					Material materialEntry = faceMaterials[face];
					boolean waterSurface = waterMaterials.containsValue(materialEntry);
					if (numUvs > 0) {
						materialData = Float.floatToRawIntBits(sceneContext.stagingBufferUvs.getBuffer().get(faceUvOffset * 4 + 3));
					}

					var buffers = meshDataMap.get(materialEntry);
					if (buffers == null) {
						var meshName = objectName.getValue() + "_" + nodes.size();

						int materialIndex = materialList.indexOf(materialEntry);
						if (materialIndex == -1) {
							materialIndex = materialList.size();
							materialList.add(materialEntry);
						}

						// Material primitives share this model's vertex slice and belong to one node.
						var mesh = AIMesh.calloc(stack)
							.mPrimitiveTypes(aiPrimitiveType_TRIANGLE)
							.mNumVertices(numVertices)
							.mVertices(AIVector3D.create(memAddress0(vertices) + firstVertex * 12L, numVertices))
							.mNormals(AIVector3D.create(memAddress0(normals) + firstVertex * 12L, numVertices))
							.mColors(stack.pointers(memAddress0(colors) + firstVertex * 16L))
							.mMaterialIndex(materialIndex)
							.mName(s -> s.data(stack.UTF8(meshName)))
							.mNumUVComponents(stack.ints(2, 2))
							.mTextureCoords(stack.pointers(
								memAddress0(uvs) + firstVertex * 12L,
								memAddress0(cornerIds) + firstVertex * 12L
							));

						var faces = AIFace.malloc(materialFaceCounts.get(materialEntry));
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
							// The legacy upload stores a texture triangle rather than the final UV coordinates
							fillVanillaUvs(uvs, model, face);
						} else {
							for (int i = 0; i < 3; i++)
								uvs
									.put(sceneContext.stagingBufferUvs.getBuffer().get((faceUvOffset + i) * 4))
									.put(sceneContext.stagingBufferUvs.getBuffer().get((faceUvOffset + i) * 4 + 1))
									.put(0);
						}

						float[] textureScale = (float[]) materialProperty(materialEntry, "textureScale");
						for (int i = 0; i < 3; i++) {
							int offset = uvs.position() - 9 + i * 3;
							float u = uvs.get(offset);
							float v = uvs.get(offset + 1);

							// Apply HD's texture scale parameter
							u = (u - .5f) / textureScale[0] + .5f;
							v = (v - .5f) / textureScale[1] + .5f;

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

						indices.put(face * 3 + i);

						int packedColor = sceneContext.stagingBufferVertices.getBuffer().get(vertex * 4 + 3);
						float alpha = 1 - (packedColor >>> 24 & 0xFF) / (float) 0xFF;
						if (waterSurface) {
							// Shoreline vertex colors encode foam, not water albedo.
							colors.put(1).put(1).put(1).put(1);
						} else {
							colors.put(ColorUtils.packedHslToSrgb(packedColor)).put(alpha);
						}

						vertices.put(v[i]);
					}

					float[] flatNormal = null;
					for (int i = 0; i < 3; i++) {
						if (dot(n[i], n[i]) == 0) {
							if (flatNormal == null) {
								// Compute flat normal
								flatNormal = new float[3];
								float[] a = new float[3];
								float[] b = new float[3];
								subtract(a, v[1], v[0]);
								subtract(b, v[2], v[0]);
								cross(flatNormal, a, b);

								float len = length(flatNormal);
								if (len == 0) {
									// Force invalid faces to have valid normals, so Assimp doesn't delete all normals
									flatNormal[1] = -1;
								} else {
									// Normalize
									for (int j = 0; j < 3; j++)
										flatNormal[j] /= len;
								}
							}
							n[i] = flatNormal;
						} else {
							// Normalize
							normalize(n[i], n[i]);
						}

						normals.put(n[i]);
					}
				}

				// Since we don't know the number of faces per mesh in advance, assign them after
				for (var meshData : meshDataMap.values()) {
					meshData.mesh.mFaces(meshData.faces.flip());
					if (wholeScene)
						compactMesh(stack, meshData.mesh, cleanup);
				}
				var nodeMeshes = stack.mallocInt(meshList.size() - firstMesh);
				for (int i = firstMesh; i < meshList.size(); i++)
					nodeMeshes.put(i);
				var node = AINode.calloc(stack)
					.mName(s -> s.data(stack.UTF8(model == null ? "Terrain_" + nodes.size() : "Model_" + nodes.size())))
					.mParent(rootNode)
					.mMeshes(nodeMeshes.flip())
					.mTransformation(sceneTransform(stack, modelInfo, wholeScene));
				nodes.add(node);
			}

			var meshPointers = stack.mallocPointer(meshList.size());
			for (int i = 0; i < meshList.size(); i++) {
				meshPointers.put(meshList.get(i));
			}
			scene.mMeshes(meshPointers.flip());
			var children = stack.mallocPointer(nodes.size());
			for (var node : nodes)
				children.put(node);
			rootNode.mChildren(children.flip());

			// TODO: split player mesh into one mesh per equipment
			// TODO: if turning this into a general purpose exporter, consider exporting a material with vertex colors UV mapped
//			Player p;
//			p.getPlayerComposition().getEquipmentIds() // 12 slots, convert IDs to model IDs
//			client.loadModelData(id)

			if (!EMBED_TEXTURES) {
				// Identify auxiliary maps by their usage, not naming conventions such as _N or _D.
				// They are exported below in their owners' texture slots, not as standalone materials.
				var textureMaps = new HashSet<Material>();
				String[] mapProperties = { "normalMap", "displacementMap", "roughnessMap", "ambientOcclusionMap" };
				for (var material : MaterialManager.MATERIALS) {
					for (String property : mapProperties) {
						var map = (Material) materialProperty(material, property);
						if (map != null) {
							textureMaps.add(map);
							textureMaps.add(map.resolveTextureOwner());
						}
					}
				}
				// Keep materials already assigned to exported geometry, even if also used as a map.
				for (var material : MaterialManager.MATERIALS)
					if (!textureMaps.contains(material) && !materialList.contains(material))
						materialList.add(material);
			}

			var textureDirectory = path("model-exports", "textures").toPath().toAbsolutePath().normalize();
			if (!EMBED_TEXTURES)
				Files.createDirectories(textureDirectory);

			// Seven properties per material, each with two AIStrings and an AIMaterialProperty.
			// The complete material library is too large for the thread-local LWJGL stack.
			var materialStorage = memAlloc(Math.multiplyExact(max(1, materialList.size()), 32 * 1024));
			cleanup.add(memAddress0(materialStorage));
			var materialStack = MemoryStack.create(materialStorage);
			var materialPointers = materialStack.callocPointer(materialList.size());
			var expandedMaterials = new ArrayList<Material>();
			for (var material : materialList) {
				var aiMaterial = AIMaterial.calloc(materialStack);
				materialPointers.put(aiMaterial);

				Material[] materialTextures = {
					material.resolveTextureOwner(),
					(Material) materialProperty(material, "normalMap"),
					(Material) materialProperty(material, "displacementMap"),
					(Material) materialProperty(material, "roughnessMap"),
					(Material) materialProperty(material, "ambientOcclusionMap")
				};

				// Writing properties is unfortunately quite cumbersome
				var materialPropertyPointers = new ArrayList<Long>();

				AIString propertyKey;
				ByteBuffer propertyBuffer;
				propertyKey = AIString.malloc(materialStack).data(materialStack.UTF8(AI_MATKEY_NAME));
				var materialName = AIString.malloc(materialStack).data(materialStack.UTF8(material.name));
				propertyBuffer = materialStack.malloc(AIMaterialProperty.ALIGNOF, AIMaterialProperty.SIZEOF)
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
					.put(memByteBuffer(materialStack.pointers(materialName)));
				materialPropertyPointers.add(memAddress0(propertyBuffer));

				propertyKey = AIString.malloc(materialStack).data(materialStack.UTF8(AI_MATKEY_GLTF_ALPHAMODE));
				var alphaMode = AIString.malloc(materialStack).data(materialStack.UTF8("MASK"));
				propertyBuffer = materialStack.malloc(AIMaterialProperty.ALIGNOF, AIMaterialProperty.SIZEOF)
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
					.put(memByteBuffer(materialStack.pointers(alphaMode)));
				materialPropertyPointers.add(memAddress0(propertyBuffer));

				int[] textureTypes = {
					aiTextureType_DIFFUSE, aiTextureType_NORMALS, aiTextureType_DISPLACEMENT,
					aiTextureType_DIFFUSE_ROUGHNESS, aiTextureType_AMBIENT_OCCLUSION
				};
				for (int i = 0; i < materialTextures.length; i++) {
					var texture = materialTextures[i];
					if (texture == null)
						continue;
					texture = texture.resolveTextureOwner();
					if (texture.textureLayer < 0)
						continue;

					int index = expandedMaterials.indexOf(texture);
					if (index == -1) {
						index = expandedMaterials.size();
						expandedMaterials.add(texture);
					}
					int type = textureTypes[i];

					propertyKey = AIString.malloc(materialStack).data(materialStack.UTF8(_AI_MATKEY_TEXTURE_BASE));
					String textureUri = EMBED_TEXTURES ? "*" + index :
						textureDirectory.resolve(texture.name.toLowerCase(java.util.Locale.ROOT) + ".png").toString().replace('\\', '/');
					var materialTextureUri = AIString.malloc(materialStack).data(materialStack.UTF8(textureUri));
					propertyBuffer = materialStack.malloc(AIMaterialProperty.ALIGNOF, AIMaterialProperty.SIZEOF)
						.put(memByteBuffer(propertyKey))
						.position(AIMaterialProperty.MSEMANTIC)
						.putInt(type)
						.putInt(0) // index of the same texture type in the same material
						.putInt(materialTextureUri.length() + 1 + 4)
						.putInt(aiPTI_String)
						.position(AIMaterialProperty.MDATA) // alignment is important
						.put(memByteBuffer(materialStack.pointers(materialTextureUri)));
					materialPropertyPointers.add(memAddress0(propertyBuffer));

					// I think this is optional when only one UV map is used
//					propertyKey = AIString.malloc(materialStack).data(materialStack.UTF8(_AI_MATKEY_UVWSRC_BASE));
//					var materialUvMap = materialStack.ints(0); // UV map index
//					propertyBuffer = materialStack.malloc(AIMaterialProperty.ALIGNOF, AIMaterialProperty.SIZEOF)
//						.put(memByteBuffer(propertyKey))
//						.position(AIMaterialProperty.MSEMANTIC)
//						.putInt(type)
//						.putInt(0) // index of the same texture type in the same material
//						.putInt(Integer.BYTES)
//						.putInt(aiPTI_Integer)
//						.position(AIMaterialProperty.MDATA) // alignment is important
//						.put(memByteBuffer(materialStack.pointers(materialUvMap)));
//					materialPropertyPointers.add(memAddress0(propertyBuffer));
				}

				var mProperties = materialStack.mallocPointer(materialPropertyPointers.size());
				for (long address : materialPropertyPointers)
					mProperties.put(address);
				aiMaterial
					.mProperties(mProperties.flip())
					.mNumAllocated(mProperties.limit()); // used internally by Assimp when copying scenes
			}

			var texturePointers = materialStack.callocPointer(EMBED_TEXTURES ? expandedMaterials.size() : 0);
			for (Material material : expandedMaterials) {
				String textureName = material.name.toLowerCase(java.util.Locale.ROOT);
				var texturePath = textureDirectory.resolve(textureName + ".png");
				if (!EMBED_TEXTURES && Files.exists(texturePath))
					continue;

				var textureOwner = material.resolveTextureOwner();
				String textureFile = textureOwner.getTextureName();
				BufferedImage textureImage = textureFile == null ? null : textureManager.loadTexture(textureFile);
				boolean vanillaImage = textureImage == null;
				if (vanillaImage)
					textureImage = textureManager.loadTexture(null, textureOwner.vanillaTextureIndex);

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
					if (vanillaImage) {
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
					if (!EMBED_TEXTURES) {
						if (!ImageIO.write(orientedImage, "png", texturePath.toFile()))
							throw new IOException("No PNG writer available for " + textureName);
						continue;
					}
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

			scene.mMaterials(materialPointers.flip());
			if (EMBED_TEXTURES)
				scene.mTextures(texturePointers.flip());

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

			log.info("Exporting {} nodes, {} material primitives and {} triangles to {}",
				nodes.size(), meshList.size(), totalFaceCount, exportPath);
			long exportStart = System.nanoTime();
			int statusCode;
			try {
				statusCode = aiExportScene(scene, formatId, exportPath.toPath().toString(),
					(EMBED_TEXTURES ? aiProcess_EmbedTextures : 0)
					| aiProcess_ValidateDataStructure
					| aiProcess_FindInvalidData
				);
			} finally {
				aiDetachLogStream(logStream);
				log.info("Assimp export finished after {} seconds", (System.nanoTime() - exportStart) / 1e9);
			}

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
				if (wholeScene)
					SceneGltfExporter.embed(exportPath.toPath(), plugin.uboGlobal, waterMaterials);
				chatMessageManager.queue(QueuedMessage.builder()
					.type(ChatMessageType.CONSOLE)
					.runeLiteFormattedMessage(new ChatMessageBuilder()
						.append(wholeScene ? "Exported scene as " : "Exported model as ")
						.append(Color.CYAN, exportPath.getFilename())
						.append(" (" + totalFaceCount + " faces)")
						.build())
					.build());
			}
		} catch (IOException ex) {
			log.error("Unable to export model or scene", ex);
		} finally {
			sceneContext.destroy();
			sceneContext.staticUnorderedModelBuffer.destroy();
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

	// This development-only exporter reads authored material data without widening the production API.
	static Object materialProperty(Material material, String name) {
		try {
			var field = Material.class.getDeclaredField(name);
			field.setAccessible(true);
			return field.get(material);
		} catch (ReflectiveOperationException ex) {
			throw new IllegalStateException("Unable to read material property " + name, ex);
		}
	}

	static Object waterProperty(WaterType water, String name) {
		try {
			var field = WaterType.class.getDeclaredField(name);
			field.setAccessible(true);
			return field.get(water);
		} catch (ReflectiveOperationException ex) {
			throw new IllegalStateException("Unable to read water property " + name, ex);
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
		if (!client.isMenuOpen()) {
			for (var entry : client.getMenuEntries()) {
				if (entry.getParam1() == InterfaceID.Orbs.WORLDMAP || entry.getParam1() == InterfaceID.Orbs.ORB_WORLDMAP) {
					client.createMenuEntry(-1)
						.setOption("Export current scene")
						.setTarget("117 HD")
						.setType(MenuAction.RUNELITE)
						.onClick(e -> clientThread.invoke(this::exportScene));
					break;
				}
			}
		}
		// The menu is not rebuilt when it is open, so don't swap or else it will
		// repeatedly swap entries
		if (client.isMenuOpen() || selectedObject == null)
			return;

		var object = selectedObject;
		var name = getTypeAndName(object);
		client.createMenuEntry(client.getMenuEntries().length)
			.setOption("Export " + name.getKey())
			.setTarget("<col=00ffff>" + name.getValue() + "</col>")
			.setType(MenuAction.RUNELITE_HIGH_PRIORITY)
			.onClick(e -> clientThread.invoke(() -> exportModel(name, object)));
	}

	@Subscribe
	public void onClientTick(ClientTick tick) {
		if (client.isMenuOpen())
			return;
		if (!isExporterShortcutHeld()) {
			hoverSet.clear();
			spotAnimActors.clear();
			selectedObject = null;
			return;
		}
		boolean refresh = clientTickCount++ % 5 == 0;
		if (refresh)
			updateHoverSet();
		if (!refresh && selectedIndex == lastSelectedIndex)
			return;

		if (hoverSet.isEmpty()) {
			selectedObject = null;
			return;
		}

		int camX = client.getCameraX();
		int camY = client.getCameraY();
		int camZ = client.getCameraZ(); // up

		selectedIndex = clamp(selectedIndex, 0, Math.max(0, hoverSet.size() - 1));
		lastSelectedIndex = selectedIndex;
		selectedObject = hoverSet.stream()
			.map(e -> {
				int x = 0, y = 0, z = 0;
				if (e instanceof Actor || e instanceof ActorSpotAnim) {
					var a = e instanceof Actor ? (Actor) e : spotAnimActors.get(e);
					var lp = a.getLocalLocation();
					x = lp.getX();
					y = lp.getY();

					if (a instanceof NPC) {
						int size = client.getNpcDefinition(((NPC) a).getId()).getSize();
						var tileHeightPoint = new LocalPoint(
							size * LOCAL_HALF_TILE_SIZE - LOCAL_HALF_TILE_SIZE + x,
							size * LOCAL_HALF_TILE_SIZE - LOCAL_HALF_TILE_SIZE + y
						);
						z = getTileHeight(client, tileHeightPoint, client.getPlane());
					} else if (a instanceof Player) {
						z = getTileHeight(client, lp, client.getPlane());
					}
					if (e instanceof ActorSpotAnim)
						z += a.getAnimationHeightOffset() - ((ActorSpotAnim) e).getHeight();
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
				long dist = (long) x * x + (long) y * y + (long) z * z;
				return Map.entry(dist, e);
			})
			.sorted(Comparator.comparingLong(Map.Entry::getKey))
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
			} else if (selectedObject instanceof Projectile || selectedObject instanceof ActorSpotAnim) {
				Shape shape = selectedObject instanceof Projectile ? getConvexHull((Projectile) selectedObject) :
					getConvexHull((ActorSpotAnim) selectedObject);
				if (shape != null) {
					g.setColor(OUTLINE_COLOR);
					g.setStroke(new BasicStroke(OUTLINE_WIDTH));
					g.draw(shape);
				}
			}
			int faceCount = 0;
			for (var renderable : resolveRenderables(selectedObject)) {
				if (renderable == null)
					continue;
				var model = renderable instanceof Model ? (Model) renderable : renderable.getModel();
				if (model != null)
					faceCount += model.getFaceCount();
			}
			var mouse = client.getMouseCanvasPosition();
			String label = "Faces: " + faceCount;
			g.setColor(Color.BLACK);
			g.drawString(label, mouse.getX() + 17, mouse.getY() + 21);
			g.setColor(Color.WHITE);
			g.drawString(label, mouse.getX() + 16, mouse.getY() + 20);
		}
		return null;
	}

	private void updateHoverSet() {
		hoverSet.clear();
		spotAnimActors.clear();
		hoverChecked.clear();

		if (!isExporterShortcutHeld())
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
		hoverChecked.clear();
	}

	private void processTile(Point2D hoverPoint, Tile tile) {
		var bridge = tile.getBridge();
		if (bridge != null)
			processTile(hoverPoint, bridge);

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
		// Multi-tile objects and bridged tiles may encounter the same object repeatedly.
		if (!hoverChecked.add(object))
			return;
		Shape shape = null;
		if (object instanceof Actor) {
			var actor = (Actor) object;
			shape = actor.getConvexHull();
			for (var spotAnim : actor.getSpotAnims()) {
				if (spotAnim.getFrame() < 0 || client.getGameCycle() < spotAnim.getStartCycle())
					continue;
				spotAnimActors.put(spotAnim, actor);
				addIfHovered(hoverPoint, spotAnim);
			}
		} else if (object instanceof ActorSpotAnim) {
			shape = getConvexHull((ActorSpotAnim) object);
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

	private Shape getConvexHull(ActorSpotAnim spotAnim) {
		var actor = spotAnimActors.get(spotAnim);
		var model = spotAnim.getModel();
		if (actor == null || model == null)
			return null;
		var location = actor.getLocalLocation();
		int height = getTileHeight(client, location, actor.getWorldView().getPlane()) +
			actor.getAnimationHeightOffset() - spotAnim.getHeight();
		int[] x2d = new int[model.getVerticesCount()];
		int[] y2d = new int[model.getVerticesCount()];
		Perspective.modelToCanvas(client, model.getVerticesCount(), location.getX(), location.getY(), height, 0,
			model.getVerticesX(), model.getVerticesZ(), model.getVerticesY(), x2d, y2d);
		return Jarvis.convexHull(x2d, y2d);
	}

	private Shape getConvexHull(GraphicsObject o) {
		var model = o.getModel();
		if (model == null)
			return null;
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
		if (model == null)
			return null;
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
			return HDUtils.getModelPreOrientation(((GameObject) object).getConfig());
		if (object instanceof WallObject) {
			var wallObject = (WallObject) object;
			return renderable == wallObject.getRenderable1() ?
				HDUtils.convertWallObjectOrientation(wallObject.getOrientationA()) :
				HDUtils.convertWallObjectOrientation(wallObject.getOrientationB());
		}
		if (object instanceof DecorativeObject)
			return HDUtils.getModelPreOrientation(((DecorativeObject) object).getConfig());
		if (object instanceof GroundObject)
			return HDUtils.getModelPreOrientation(((GroundObject) object).getConfig());
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
		} else if (renderable instanceof ActorSpotAnim) {
			type = "spotanim";
			name = "ID " + ((ActorSpotAnim) renderable).getId();
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
		if (object instanceof ItemLayer) {
			var items = (ItemLayer) object;
			return list(items.getBottom(), items.getMiddle(), items.getTop());
		}
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
			.map(r -> {
				Model model;
				if (r instanceof Model) {
					model = (Model) r;
				} else if (r instanceof DynamicObject) {
					// Match SceneUploader's object model, including its baked orientation.
					model = ((DynamicObject) r).getModelZbuf();
				} else {
					model = r.getModel();
				}
				return new ModelInfo(object, r, model);
			})
			.filter(i -> i.model != null && i.model.getFaceCount() > 0)
			.collect(Collectors.toList());
	}

	// Assimp exports every vertex of each material primitive, including unreferenced vertices.
	// Keep scene primitives compact; UV1 corner identities are only needed for individual-model round trips.
	private void compactMesh(MemoryStack stack, AIMesh mesh, List<Long> cleanup) {
		int count = mesh.mNumFaces() * 3;
		var data = memAlloc(count * (12 + 12 + 12 + 16));
		long positions = memAddress0(data);
		cleanup.add(positions);
		long normals = positions + count * 12L;
		long uvs = normals + count * 12L;
		long colors = uvs + count * 12L;
		long sourcePositions = mesh.mVertices().address();
		long sourceNormals = mesh.mNormals().address();
		long sourceUvs = mesh.mTextureCoords(0).address();
		long sourceColors = mesh.mColors(0).address();
		var faces = mesh.mFaces();
		for (int face = 0; face < mesh.mNumFaces(); face++) {
			var indices = faces.get(face).mIndices();
			for (int corner = 0; corner < 3; corner++) {
				int source = indices.get(corner);
				int target = face * 3 + corner;
				memCopy(sourcePositions + source * 12L, positions + target * 12L, 12);
				memCopy(sourceNormals + source * 12L, normals + target * 12L, 12);
				memCopy(sourceUvs + source * 12L, uvs + target * 12L, 12);
				memCopy(sourceColors + source * 16L, colors + target * 16L, 16);
				indices.put(corner, target);
			}
		}
		mesh.mNumVertices(count)
			.mVertices(AIVector3D.create(positions, count))
			.mNormals(AIVector3D.create(normals, count))
			.mTextureCoords(stack.pointers(uvs, 0))
			.mColors(stack.pointers(colors))
			.mNumUVComponents(stack.ints(2, 0));
	}

	private void exportScene() {
		var top = client.getTopLevelWorldView();
		if (top == null || plugin.getSceneContext() == null)
			return;
		var models = new ArrayList<ModelInfo>();
		collectScene(models, top);
		for (var entity : top.worldEntities())
			collectScene(models, entity.getWorldView());
		for (var projectile : client.getProjectiles())
			addSceneModels(models, top, null, projectile);
		// Terrain is one object, with material primitives, rather than thousands of tiny tile meshes.
		var terrain = new ModelInfo(null, null, null);
		var tiles = new java.util.LinkedHashMap<WorldView, List<Tile>>();
		models.removeIf(info -> {
			if (info.model != null)
				return false;
			tiles.computeIfAbsent(info.worldView, view -> new ArrayList<>()).add(info.tile);
			return true;
		});
		terrain.terrain = terrainExporter.build(tiles);
		try {
			if (terrain.faceCount() > 0)
				models.add(terrain);
			exportModels(Map.entry("scene", Integer.toString(client.getTickCount())), models, true);
		} finally {
			terrain.terrain.destroy();
			terrain.terrain.staticUnorderedModelBuffer.destroy();
		}
	}

	private void collectScene(List<ModelInfo> models, WorldView view) {
		if (!view.isTopLevel()) {
			var context = sceneManager.getContext(view);
			if (context == null || context.projection == null) {
				log.warn("Skipping worldview {}: no scene projection available", view.getId());
				return;
			}
		}
		var seen = Collections.newSetFromMap(new IdentityHashMap<Object, Boolean>());
		for (var plane : view.getScene().getTiles())
			for (var column : plane)
				for (var tile : column)
					collectTile(models, view, tile, seen);
		for (var actor : view.players())
			addSceneModels(models, view, null, actor);
		for (var actor : view.npcs())
			addSceneModels(models, view, null, actor);
		for (var effect : view.getGraphicsObjects())
			addSceneModels(models, view, null, effect);
	}

	private void collectTile(List<ModelInfo> models, WorldView view, Tile tile, Set<Object> seen) {
		if (tile == null || !seen.add(tile))
			return;
		collectTile(models, view, tile.getBridge(), seen);
		var terrain = new ModelInfo(tile, null, null);
		terrain.tile = tile;
		terrain.worldView = view;
		models.add(terrain); // Hidden surfaces can still have generated underwater geometry.
		var objects = new ArrayList<Object>();
		Collections.addAll(objects, tile.getGameObjects());
		objects.add(tile.getWallObject());
		objects.add(tile.getDecorativeObject());
		objects.add(tile.getGroundObject());
		objects.add(tile.getItemLayer());
		for (var object : objects)
			if (object != null && seen.add(object))
				addSceneModels(models, view, tile, object);
	}

	private void addSceneModels(List<ModelInfo> models, WorldView view, Tile tile, Object object) {
		for (var info : resolveModels(object)) {
			info.worldView = view;
			info.tile = tile;
			models.add(info);
		}
	}

	private AIMatrix4x4 sceneTransform(MemoryStack stack, ModelInfo info, boolean wholeScene) {
		float[] matrix = Mat4.identity();
		if (wholeScene && info.terrain == null) {
			float x = 0, y = 0, z = 0;
			int orientation = 0;
			if (info.object instanceof TileObject) {
				var object = (TileObject) info.object;
				x = object.getX();
				y = object.getZ();
				z = object.getY();
				if (object instanceof ItemLayer)
					y -= ((ItemLayer) object).getHeight();
				if (object instanceof GameObject)
					orientation = ((GameObject) object).getModelOrientation();
				if (object instanceof DecorativeObject) {
					var decoration = (DecorativeObject) object;
					boolean first = info.renderable == decoration.getRenderable();
					x += first ? decoration.getXOffset() : decoration.getXOffset2();
					z += first ? decoration.getYOffset() : decoration.getYOffset2();
				}
			} else if (info.object instanceof Actor) {
				var actor = (Actor) info.object;
				x = actor.getLocalLocation().getX();
				z = actor.getLocalLocation().getY();
				y = info.worldView.getTileHeight((int) x, (int) z, info.worldView.getPlane()) + actor.getAnimationHeightOffset();
				orientation = actor.getOrientation();
			} else if (info.object instanceof GraphicsObject) {
				var effect = (GraphicsObject) info.object;
				x = effect.getLocation().getX();
				z = effect.getLocation().getY();
				y = effect.getZ();
			} else if (info.object instanceof Projectile) {
				var projectile = (Projectile) info.object;
				x = (float) projectile.getX();
				y = (float) projectile.getZ();
				z = (float) projectile.getY();
				orientation = projectile.getOrientation();
			}
			float angle = orientation * JAU_TO_RAD;
			matrix[0] = matrix[10] = cos(angle);
			matrix[2] = -sin(angle);
			matrix[8] = sin(angle);
			matrix[12] = x;
			matrix[13] = y;
			matrix[14] = z;
			if (!info.worldView.isTopLevel()) {
				var projection = sceneManager.getContext(info.worldView).projection;
				// Transform the basis and origin, preserving child-worldview pitch and scale.
				var origin = new float[] { x, y, z };
				Mat4.transformVecAffine(origin, projection, origin);
				for (int column = 0; column < 3; column++) {
					var axis = new float[] { matrix[column * 4] + x, matrix[column * 4 + 1] + y, matrix[column * 4 + 2] + z };
					Mat4.transformVecAffine(axis, projection, axis);
					for (int row = 0; row < 3; row++)
						matrix[column * 4 + row] = axis[row] - origin[row];
				}
				System.arraycopy(origin, 0, matrix, 12, 3);
			}
		}
		return AIMatrix4x4.calloc(stack)
			.a1(matrix[0]).a2(matrix[4]).a3(matrix[8]).a4(matrix[12])
			.b1(matrix[1]).b2(matrix[5]).b3(matrix[9]).b4(matrix[13])
			.c1(matrix[2]).c2(matrix[6]).c3(matrix[10]).c4(matrix[14]).d4(1);
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
		if (isExporterShortcutHeld()) {
			selectedIndex -= Math.signum(event.getWheelRotation());
			event.consume();
		}
		return event;
	}

	@Override
	public void keyTyped(KeyEvent event) {}

	@Override
	public void keyPressed(KeyEvent event) {
		if (!isExporterShortcutHeld())
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

	private boolean isExporterShortcutHeld() {
		return client.isKeyPressed(KeyCode.KC_CONTROL) && client.isKeyPressed(KeyCode.KC_SHIFT);
	}

	private static <T> ArrayList<T> list(T... elements) {
		var list = new ArrayList<T>();
		for (T t : elements)
			if (t != null)
				list.add(t);
		return list;
	}
}
