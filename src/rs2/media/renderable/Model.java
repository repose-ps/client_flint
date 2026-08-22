package rs2.media.renderable;

import rs2.media.AnimationFrame;
import rs2.media.Rasterizer;
import rs2.media.Rasterizer3D;
import rs2.cache.ondemand.OnDemandProvider;
import rs2.media.Skeleton;
import rs2.media.VertexNormal;

import rs2.net.Buffer;

/**
 * Revision-377 software model: legacy model decoding, skeletal transforms,
 * lighting, projection, depth/priority sorting, near-plane clipping, picking,
 * and triangle dispatch.
 *
 * <p>
 * The fixed-size static projection/sorting work arrays and 50-unit near plane
 * are intentionally retained. Private raster ordering code stays close to the
 * original integer arithmetic to preserve clipping, rounding, and priority
 * behavior.
 * </p>
 */
public class Model extends Renderable {

	/**
	 * Releases the model-header table and shared projection work arrays.
	 */
	public static void clearModelLoader() {
		modelHeaders = null;
		faceOutOfBounds = null;
		faceNearClipped = null;
		projectedX = null;
		projectedY = null;
		projectedDepth = null;
		cameraX = null;
		cameraY = null;
		cameraZ = null;
		depthBucketCounts = null;
		depthBuckets = null;
		priorityBucketCounts = null;
		priorityBuckets = null;
		priority10Depths = null;
		priority11Depths = null;
		priorityDepthSums = null;
		SINE = null;
		COSINE = null;
		HSL_TO_RGB = null;
		RECIPROCAL_16 = null;
	}

	/**
	 * Performs initialize model headers.
	 * 
	 * @param modelCount the model count
	 * @param provider   the provider
	 */
	public static void initializeModelHeaders(int modelCount, OnDemandProvider provider) {
		modelHeaders = new ModelHeader[modelCount];
		modelProvider = provider;
	}

	/**
	 * Parses the 18-byte legacy model footer into stream offsets without decoding
	 * geometry.
	 * 
	 * @param modelData the model data
	 * @param modelId   the model id
	 */
	public static void loadModelHeader(byte[] modelData, int modelId) {
		if (modelData == null) {
			ModelHeader header = modelHeaders[modelId] = new ModelHeader();
			header.vertexCount = 0;
			header.triangleCount = 0;
			header.texturedTriangleCount = 0;
			return;
		}
		Buffer footer = new Buffer(modelData);
		footer.position = modelData.length - 18;
		ModelHeader header = modelHeaders[modelId] = new ModelHeader();
		header.modelData = modelData;
		header.vertexCount = footer.readUnsignedShort();
		header.triangleCount = footer.readUnsignedShort();
		header.texturedTriangleCount = footer.readUnsignedByte();
		int hasDrawTypes = footer.readUnsignedByte();
		int priority = footer.readUnsignedByte();
		int hasAlpha = footer.readUnsignedByte();
		int hasTriangleSkins = footer.readUnsignedByte();
		int hasVertexSkins = footer.readUnsignedByte();
		int xDataLength = footer.readUnsignedShort();
		int yDataLength = footer.readUnsignedShort();
//		int zDataLength = footer.readUnsignedShort();
		footer.readUnsignedShort(); // unused
		int triangleDataLength = footer.readUnsignedShort();

		int offset = 0;
		header.vertexDirectionOffset = offset;
		offset += header.vertexCount;
		header.triangleTypeOffset = offset;
		offset += header.triangleCount;
		header.trianglePriorityOffset = offset;
		if (priority == 255)
			offset += header.triangleCount;
		else
			header.trianglePriorityOffset = -priority - 1;
		header.triangleSkinOffset = offset;
		if (hasTriangleSkins == 1)
			offset += header.triangleCount;
		else
			header.triangleSkinOffset = -1;
		header.texturePointerOffset = offset;
		if (hasDrawTypes == 1)
			offset += header.triangleCount;
		else
			header.texturePointerOffset = -1;
		header.vertexSkinOffset = offset;
		if (hasVertexSkins == 1)
			offset += header.vertexCount;
		else
			header.vertexSkinOffset = -1;
		header.triangleAlphaOffset = offset;
		if (hasAlpha == 1)
			offset += header.triangleCount;
		else
			header.triangleAlphaOffset = -1;
		header.triangleDataOffset = offset;
		offset += triangleDataLength;
		header.colorDataOffset = offset;
		offset += header.triangleCount * 2;
		header.uvMapTriangleOffset = offset;
		offset += header.texturedTriangleCount * 6;
		header.xDataOffset = offset;
		offset += xDataLength;
		header.yDataOffset = offset;
		offset += yDataLength;
		header.zDataOffset = offset;
	}

	/**
	 * Clears model header state.
	 * 
	 * @param modelId the model id
	 */
	public static void clearModelHeader(int modelId) {
		modelHeaders[modelId] = null;
	}

	/**
	 * Returns model.
	 * 
	 * @return the resulting model
	 * @param modelId the model id
	 */
	public static Model getModel(int modelId) {
		if (modelHeaders == null)
			return null;
		ModelHeader modelHeader = modelHeaders[modelId];
		if (modelHeader == null) {
			modelProvider.requestModel(modelId);
			return null;
		} else {
			return new Model(modelId);
		}
	}

	/**
	 * Returns whether loaded.
	 * 
	 * @return the resulting boolean
	 * @param modelId the model id
	 */
	public static boolean isLoaded(int modelId) {
		if (modelHeaders == null)
			return false;
		ModelHeader modelHeader = modelHeaders[modelId];
		if (modelHeader == null) {
			modelProvider.requestModel(modelId);
			return false;
		} else {
			return true;
		}
	}

	/**
	 * Initializes this instance.
	 */
	private Model() {
		singleTile = false;
	}

	/**
	 * Initializes this instance.
	 * 
	 * @param modelId the model id
	 */
	private Model(int modelId) {
		singleTile = false;
		ModelHeader header = modelHeaders[modelId];
		vertexCount = header.vertexCount;
		triangleCount = header.triangleCount;
		texturedTriangleCount = header.texturedTriangleCount;
		verticesX = new int[vertexCount];
		verticesY = new int[vertexCount];
		verticesZ = new int[vertexCount];
		triangleVertexA = new int[triangleCount];
		triangleVertexB = new int[triangleCount];
		triangleVertexC = new int[triangleCount];
		texturedTriangleA = new int[texturedTriangleCount];
		texturedTriangleB = new int[texturedTriangleCount];
		texturedTriangleC = new int[texturedTriangleCount];
		if (header.vertexSkinOffset >= 0)
			vertexSkins = new int[vertexCount];
		if (header.texturePointerOffset >= 0)
			triangleDrawType = new int[triangleCount];
		if (header.trianglePriorityOffset >= 0)
			trianglePriorities = new int[triangleCount];
		else
			defaultTrianglePriority = -header.trianglePriorityOffset - 1;
		if (header.triangleAlphaOffset >= 0)
			triangleAlpha = new int[triangleCount];
		if (header.triangleSkinOffset >= 0)
			triangleSkins = new int[triangleCount];
		triangleColors = new int[triangleCount];

		Buffer vertexFlags = new Buffer(header.modelData);
		vertexFlags.position = header.vertexDirectionOffset;
		Buffer xData = new Buffer(header.modelData);
		xData.position = header.xDataOffset;
		Buffer yData = new Buffer(header.modelData);
		yData.position = header.yDataOffset;
		Buffer zData = new Buffer(header.modelData);
		zData.position = header.zDataOffset;
		Buffer vertexSkinData = new Buffer(header.modelData);
		vertexSkinData.position = header.vertexSkinOffset;
		int previousX = 0;
		int previousY = 0;
		int previousZ = 0;
		for (int vertex = 0; vertex < vertexCount; vertex++) {
			int flags = vertexFlags.readUnsignedByte();
			int deltaX = (flags & 1) != 0 ? xData.readSignedSmart() : 0;
			int deltaY = (flags & 2) != 0 ? yData.readSignedSmart() : 0;
			int deltaZ = (flags & 4) != 0 ? zData.readSignedSmart() : 0;
			verticesX[vertex] = previousX + deltaX;
			verticesY[vertex] = previousY + deltaY;
			verticesZ[vertex] = previousZ + deltaZ;
			previousX = verticesX[vertex];
			previousY = verticesY[vertex];
			previousZ = verticesZ[vertex];
			if (vertexSkins != null)
				vertexSkins[vertex] = vertexSkinData.readUnsignedByte();
		}

		Buffer colorData = new Buffer(header.modelData);
		colorData.position = header.colorDataOffset;
		Buffer drawTypeData = new Buffer(header.modelData);
		drawTypeData.position = header.texturePointerOffset;
		Buffer priorityData = new Buffer(header.modelData);
		priorityData.position = header.trianglePriorityOffset;
		Buffer alphaData = new Buffer(header.modelData);
		alphaData.position = header.triangleAlphaOffset;
		Buffer triangleSkinData = new Buffer(header.modelData);
		triangleSkinData.position = header.triangleSkinOffset;
		for (int triangle = 0; triangle < triangleCount; triangle++) {
			triangleColors[triangle] = colorData.readUnsignedShort();
			if (triangleDrawType != null)
				triangleDrawType[triangle] = drawTypeData.readUnsignedByte();
			if (trianglePriorities != null)
				trianglePriorities[triangle] = priorityData.readUnsignedByte();
			if (triangleAlpha != null)
				triangleAlpha[triangle] = alphaData.readUnsignedByte();
			if (triangleSkins != null)
				triangleSkins[triangle] = triangleSkinData.readUnsignedByte();
		}

		Buffer triangleData = new Buffer(header.modelData);
		triangleData.position = header.triangleDataOffset;
		Buffer triangleTypes = new Buffer(header.modelData);
		triangleTypes.position = header.triangleTypeOffset;
		int vertexA = 0;
		int vertexB = 0;
		int vertexC = 0;
		int lastVertex = 0;
		for (int triangle = 0; triangle < triangleCount; triangle++) {
			int type = triangleTypes.readUnsignedByte();
			if (type == 1) {
				vertexA = triangleData.readSignedSmart() + lastVertex;
				lastVertex = vertexA;
				vertexB = triangleData.readSignedSmart() + lastVertex;
				lastVertex = vertexB;
				vertexC = triangleData.readSignedSmart() + lastVertex;
				lastVertex = vertexC;
			} else if (type == 2) {
				vertexB = vertexC;
				vertexC = triangleData.readSignedSmart() + lastVertex;
				lastVertex = vertexC;
			} else if (type == 3) {
				vertexA = vertexC;
				vertexC = triangleData.readSignedSmart() + lastVertex;
				lastVertex = vertexC;
			} else if (type == 4) {
				int swap = vertexA;
				vertexA = vertexB;
				vertexB = swap;
				vertexC = triangleData.readSignedSmart() + lastVertex;
				lastVertex = vertexC;
			}
			triangleVertexA[triangle] = vertexA;
			triangleVertexB[triangle] = vertexB;
			triangleVertexC[triangle] = vertexC;
		}

		Buffer texturedTriangles = new Buffer(header.modelData);
		texturedTriangles.position = header.uvMapTriangleOffset;
		for (int triangle = 0; triangle < texturedTriangleCount; triangle++) {
			texturedTriangleA[triangle] = texturedTriangles.readUnsignedShort();
			texturedTriangleB[triangle] = texturedTriangles.readUnsignedShort();
			texturedTriangleC[triangle] = texturedTriangles.readUnsignedShort();
		}
	}

	/**
	 * Initializes this instance.
	 * 
	 * @param modelCount the model count
	 * @param models     the models
	 */
	public Model(int modelCount, Model[] models) {
		singleTile = false;
		boolean conditionFlag = false;
		boolean conditionFlag2 = false;
		boolean conditionFlag3 = false;
		boolean conditionFlag4 = false;
		vertexCount = 0;
		triangleCount = 0;
		texturedTriangleCount = 0;
		defaultTrianglePriority = -1;
		for (int loopIndex = 0; loopIndex < modelCount; loopIndex++) {
			Model model = models[loopIndex];
			if (model != null) {
				vertexCount += model.vertexCount;
				triangleCount += model.triangleCount;
				texturedTriangleCount += model.texturedTriangleCount;
				conditionFlag |= model.triangleDrawType != null;
				if (model.trianglePriorities != null) {
					conditionFlag2 = true;
				} else {
					if (defaultTrianglePriority == -1)
						defaultTrianglePriority = model.defaultTrianglePriority;
					if (defaultTrianglePriority != model.defaultTrianglePriority)
						conditionFlag2 = true;
				}
				conditionFlag3 |= model.triangleAlpha != null;
				conditionFlag4 |= model.triangleSkins != null;
			}
		}

		verticesX = new int[vertexCount];
		verticesY = new int[vertexCount];
		verticesZ = new int[vertexCount];
		vertexSkins = new int[vertexCount];
		triangleVertexA = new int[triangleCount];
		triangleVertexB = new int[triangleCount];
		triangleVertexC = new int[triangleCount];
		texturedTriangleA = new int[texturedTriangleCount];
		texturedTriangleB = new int[texturedTriangleCount];
		texturedTriangleC = new int[texturedTriangleCount];
		if (conditionFlag)
			triangleDrawType = new int[triangleCount];
		if (conditionFlag2)
			trianglePriorities = new int[triangleCount];
		if (conditionFlag3)
			triangleAlpha = new int[triangleCount];
		if (conditionFlag4)
			triangleSkins = new int[triangleCount];
		triangleColors = new int[triangleCount];
		vertexCount = 0;
		triangleCount = 0;
		texturedTriangleCount = 0;
		int intermediateValue = 0;
		for (int loopIndex2 = 0; loopIndex2 < modelCount; loopIndex2++) {
			Model model2 = models[loopIndex2];
			if (model2 != null) {
				for (int loopIndex3 = 0; loopIndex3 < model2.triangleCount; loopIndex3++) {
					if (conditionFlag)
						if (model2.triangleDrawType == null) {
							triangleDrawType[triangleCount] = 0;
						} else {
							int intermediateValue2 = model2.triangleDrawType[loopIndex3];
							if ((intermediateValue2 & 2) == 2)
								intermediateValue2 += intermediateValue << 2;
							triangleDrawType[triangleCount] = intermediateValue2;
						}
					if (conditionFlag2)
						if (model2.trianglePriorities == null)
							trianglePriorities[triangleCount] = model2.defaultTrianglePriority;
						else
							trianglePriorities[triangleCount] = model2.trianglePriorities[loopIndex3];
					if (conditionFlag3)
						if (model2.triangleAlpha == null)
							triangleAlpha[triangleCount] = 0;
						else
							triangleAlpha[triangleCount] = model2.triangleAlpha[loopIndex3];
					if (conditionFlag4 && model2.triangleSkins != null)
						triangleSkins[triangleCount] = model2.triangleSkins[loopIndex3];
					triangleColors[triangleCount] = model2.triangleColors[loopIndex3];
					triangleVertexA[triangleCount] = getFirstIdenticalVertexId(model2,
							model2.triangleVertexA[loopIndex3]);
					triangleVertexB[triangleCount] = getFirstIdenticalVertexId(model2,
							model2.triangleVertexB[loopIndex3]);
					triangleVertexC[triangleCount] = getFirstIdenticalVertexId(model2,
							model2.triangleVertexC[loopIndex3]);
					triangleCount++;
				}

				for (int loopIndex4 = 0; loopIndex4 < model2.texturedTriangleCount; loopIndex4++) {
					texturedTriangleA[texturedTriangleCount] = getFirstIdenticalVertexId(model2,
							model2.texturedTriangleA[loopIndex4]);
					texturedTriangleB[texturedTriangleCount] = getFirstIdenticalVertexId(model2,
							model2.texturedTriangleB[loopIndex4]);
					texturedTriangleC[texturedTriangleCount] = getFirstIdenticalVertexId(model2,
							model2.texturedTriangleC[loopIndex4]);
					texturedTriangleCount++;
				}

				intermediateValue += model2.texturedTriangleCount;
			}
		}

	}

	/**
	 * Initializes this instance.
	 * 
	 * @param models     the models
	 * @param modelCount the model count
	 */
	public Model(Model[] models, int modelCount) {
		singleTile = false;
		boolean conditionFlag = false;
		boolean conditionFlag2 = false;
		boolean conditionFlag3 = false;
		boolean conditionFlag4 = false;
		vertexCount = 0;
		triangleCount = 0;
		texturedTriangleCount = 0;
		defaultTrianglePriority = -1;
		for (int loopIndex = 0; loopIndex < modelCount; loopIndex++) {
			Model model = models[loopIndex];
			if (model != null) {
				vertexCount += model.vertexCount;
				triangleCount += model.triangleCount;
				texturedTriangleCount += model.texturedTriangleCount;
				conditionFlag |= model.triangleDrawType != null;
				if (model.trianglePriorities != null) {
					conditionFlag2 = true;
				} else {
					if (defaultTrianglePriority == -1)
						defaultTrianglePriority = model.defaultTrianglePriority;
					if (defaultTrianglePriority != model.defaultTrianglePriority)
						conditionFlag2 = true;
				}
				conditionFlag3 |= model.triangleAlpha != null;
				conditionFlag4 |= model.triangleColors != null;
			}
		}

		verticesX = new int[vertexCount];
		verticesY = new int[vertexCount];
		verticesZ = new int[vertexCount];
		triangleVertexA = new int[triangleCount];
		triangleVertexB = new int[triangleCount];
		triangleVertexC = new int[triangleCount];
		triangleShadeA = new int[triangleCount];
		triangleShadeB = new int[triangleCount];
		triangleShadeC = new int[triangleCount];
		texturedTriangleA = new int[texturedTriangleCount];
		texturedTriangleB = new int[texturedTriangleCount];
		texturedTriangleC = new int[texturedTriangleCount];
		if (conditionFlag)
			triangleDrawType = new int[triangleCount];
		if (conditionFlag2)
			trianglePriorities = new int[triangleCount];
		if (conditionFlag3)
			triangleAlpha = new int[triangleCount];
		if (conditionFlag4)
			triangleColors = new int[triangleCount];
		vertexCount = 0;
		triangleCount = 0;
		texturedTriangleCount = 0;
		int intermediateValue = 0;
		for (int loopIndex2 = 0; loopIndex2 < modelCount; loopIndex2++) {
			Model model2 = models[loopIndex2];
			if (model2 != null) {
				int intermediateValue2 = vertexCount;
				for (int loopIndex3 = 0; loopIndex3 < model2.vertexCount; loopIndex3++) {
					verticesX[vertexCount] = model2.verticesX[loopIndex3];
					verticesY[vertexCount] = model2.verticesY[loopIndex3];
					verticesZ[vertexCount] = model2.verticesZ[loopIndex3];
					vertexCount++;
				}

				for (int loopIndex4 = 0; loopIndex4 < model2.triangleCount; loopIndex4++) {
					triangleVertexA[triangleCount] = model2.triangleVertexA[loopIndex4] + intermediateValue2;
					triangleVertexB[triangleCount] = model2.triangleVertexB[loopIndex4] + intermediateValue2;
					triangleVertexC[triangleCount] = model2.triangleVertexC[loopIndex4] + intermediateValue2;
					triangleShadeA[triangleCount] = model2.triangleShadeA[loopIndex4];
					triangleShadeB[triangleCount] = model2.triangleShadeB[loopIndex4];
					triangleShadeC[triangleCount] = model2.triangleShadeC[loopIndex4];
					if (conditionFlag)
						if (model2.triangleDrawType == null) {
							triangleDrawType[triangleCount] = 0;
						} else {
							int intermediateValue3 = model2.triangleDrawType[loopIndex4];
							if ((intermediateValue3 & 2) == 2)
								intermediateValue3 += intermediateValue << 2;
							triangleDrawType[triangleCount] = intermediateValue3;
						}
					if (conditionFlag2)
						if (model2.trianglePriorities == null)
							trianglePriorities[triangleCount] = model2.defaultTrianglePriority;
						else
							trianglePriorities[triangleCount] = model2.trianglePriorities[loopIndex4];
					if (conditionFlag3)
						if (model2.triangleAlpha == null)
							triangleAlpha[triangleCount] = 0;
						else
							triangleAlpha[triangleCount] = model2.triangleAlpha[loopIndex4];
					if (conditionFlag4 && model2.triangleColors != null)
						triangleColors[triangleCount] = model2.triangleColors[loopIndex4];
					triangleCount++;
				}

				for (int loopIndex5 = 0; loopIndex5 < model2.texturedTriangleCount; loopIndex5++) {
					texturedTriangleA[texturedTriangleCount] = model2.texturedTriangleA[loopIndex5]
							+ intermediateValue2;
					texturedTriangleB[texturedTriangleCount] = model2.texturedTriangleB[loopIndex5]
							+ intermediateValue2;
					texturedTriangleC[texturedTriangleCount] = model2.texturedTriangleC[loopIndex5]
							+ intermediateValue2;
					texturedTriangleCount++;
				}

				intermediateValue += model2.texturedTriangleCount;
			}
		}

		calculateDiagonals();
	}

	/**
	 * Initializes this instance.
	 * 
	 * @param source        the source
	 * @param shareVertices the share vertices
	 * @param shareColors   the share colors
	 * @param shareAlpha    the share alpha
	 */
	public Model(Model source, boolean shareVertices, boolean shareColors, boolean shareAlpha) {
		singleTile = false;
		vertexCount = source.vertexCount;
		triangleCount = source.triangleCount;
		texturedTriangleCount = source.texturedTriangleCount;
		if (shareVertices) {
			verticesX = source.verticesX;
			verticesY = source.verticesY;
			verticesZ = source.verticesZ;
		} else {
			verticesX = new int[vertexCount];
			verticesY = new int[vertexCount];
			verticesZ = new int[vertexCount];
			for (int loopIndex = 0; loopIndex < vertexCount; loopIndex++) {
				verticesX[loopIndex] = source.verticesX[loopIndex];
				verticesY[loopIndex] = source.verticesY[loopIndex];
				verticesZ[loopIndex] = source.verticesZ[loopIndex];
			}

		}
		if (shareColors) {
			triangleColors = source.triangleColors;
		} else {
			triangleColors = new int[triangleCount];
			for (int loopIndex2 = 0; loopIndex2 < triangleCount; loopIndex2++)
				triangleColors[loopIndex2] = source.triangleColors[loopIndex2];

		}
		if (shareAlpha) {
			triangleAlpha = source.triangleAlpha;
		} else {
			triangleAlpha = new int[triangleCount];
			if (source.triangleAlpha == null) {
				for (int loopIndex3 = 0; loopIndex3 < triangleCount; loopIndex3++)
					triangleAlpha[loopIndex3] = 0;

			} else {
				for (int loopIndex4 = 0; loopIndex4 < triangleCount; loopIndex4++)
					triangleAlpha[loopIndex4] = source.triangleAlpha[loopIndex4];

			}
		}
		vertexSkins = source.vertexSkins;
		triangleSkins = source.triangleSkins;
		triangleDrawType = source.triangleDrawType;
		triangleVertexA = source.triangleVertexA;
		triangleVertexB = source.triangleVertexB;
		triangleVertexC = source.triangleVertexC;
		trianglePriorities = source.trianglePriorities;
		defaultTrianglePriority = source.defaultTrianglePriority;
		texturedTriangleA = source.texturedTriangleA;
		texturedTriangleB = source.texturedTriangleB;
		texturedTriangleC = source.texturedTriangleC;
	}

	/**
	 * Initializes this instance.
	 * 
	 * @param source        the source
	 * @param copyVerticesY the copy vertices y
	 * @param copyLighting  the copy lighting
	 */
	public Model(Model source, boolean copyVerticesY, boolean copyLighting) {
		singleTile = false;
		vertexCount = source.vertexCount;
		triangleCount = source.triangleCount;
		texturedTriangleCount = source.texturedTriangleCount;
		if (copyVerticesY) {
			verticesY = new int[vertexCount];
			for (int loopIndex = 0; loopIndex < vertexCount; loopIndex++)
				verticesY[loopIndex] = source.verticesY[loopIndex];

		} else {
			verticesY = source.verticesY;
		}
		if (copyLighting) {
			triangleShadeA = new int[triangleCount];
			triangleShadeB = new int[triangleCount];
			triangleShadeC = new int[triangleCount];
			for (int loopIndex2 = 0; loopIndex2 < triangleCount; loopIndex2++) {
				triangleShadeA[loopIndex2] = source.triangleShadeA[loopIndex2];
				triangleShadeB[loopIndex2] = source.triangleShadeB[loopIndex2];
				triangleShadeC[loopIndex2] = source.triangleShadeC[loopIndex2];
			}

			triangleDrawType = new int[triangleCount];
			if (source.triangleDrawType == null) {
				for (int loopIndex3 = 0; loopIndex3 < triangleCount; loopIndex3++)
					triangleDrawType[loopIndex3] = 0;

			} else {
				for (int loopIndex4 = 0; loopIndex4 < triangleCount; loopIndex4++)
					triangleDrawType[loopIndex4] = source.triangleDrawType[loopIndex4];

			}
			super.vertexNormals = new VertexNormal[vertexCount];
			for (int loopIndex5 = 0; loopIndex5 < vertexCount; loopIndex5++) {
				VertexNormal vertexNormal = super.vertexNormals[loopIndex5] = new VertexNormal();
				VertexNormal vertexNormal2 = ((Renderable) (source)).vertexNormals[loopIndex5];
				vertexNormal.x = vertexNormal2.x;
				vertexNormal.y = vertexNormal2.y;
				vertexNormal.z = vertexNormal2.z;
				vertexNormal.magnitude = vertexNormal2.magnitude;
			}

			vertexNormalOffsets = source.vertexNormalOffsets;
		} else {
			triangleShadeA = source.triangleShadeA;
			triangleShadeB = source.triangleShadeB;
			triangleShadeC = source.triangleShadeC;
			triangleDrawType = source.triangleDrawType;
		}
		verticesX = source.verticesX;
		verticesZ = source.verticesZ;
		triangleColors = source.triangleColors;
		triangleAlpha = source.triangleAlpha;
		trianglePriorities = source.trianglePriorities;
		defaultTrianglePriority = source.defaultTrianglePriority;
		triangleVertexA = source.triangleVertexA;
		triangleVertexB = source.triangleVertexB;
		triangleVertexC = source.triangleVertexC;
		texturedTriangleA = source.texturedTriangleA;
		texturedTriangleB = source.texturedTriangleB;
		texturedTriangleC = source.texturedTriangleC;
		super.modelHeight = ((Renderable) (source)).modelHeight;
		maxY = source.maxY;
		horizontalRadius = source.horizontalRadius;
		radius = source.radius;
		depthSpan = source.depthSpan;
		packedXBounds = source.packedXBounds;
		packedZBounds = source.packedZBounds;
		lightingState = source.lightingState;
	}

	/**
	 * Performs replace with model.
	 * 
	 * @param source     the source
	 * @param shareAlpha the share alpha
	 */
	public void replaceWithModel(Model source, boolean shareAlpha) {
		vertexCount = source.vertexCount;
		triangleCount = source.triangleCount;
		texturedTriangleCount = source.texturedTriangleCount;
		if (sharedVerticesX.length < vertexCount) {
			sharedVerticesX = new int[vertexCount + 100];
			sharedVerticesY = new int[vertexCount + 100];
			sharedVerticesZ = new int[vertexCount + 100];
		}
		verticesX = sharedVerticesX;
		verticesY = sharedVerticesY;
		verticesZ = sharedVerticesZ;
		for (int loopIndex = 0; loopIndex < vertexCount; loopIndex++) {
			verticesX[loopIndex] = source.verticesX[loopIndex];
			verticesY[loopIndex] = source.verticesY[loopIndex];
			verticesZ[loopIndex] = source.verticesZ[loopIndex];
		}

		if (shareAlpha) {
			triangleAlpha = source.triangleAlpha;
		} else {
			if (sharedTriangleAlpha.length < triangleCount)
				sharedTriangleAlpha = new int[triangleCount + 100];
			triangleAlpha = sharedTriangleAlpha;
			if (source.triangleAlpha == null) {
				for (int loopIndex2 = 0; loopIndex2 < triangleCount; loopIndex2++)
					triangleAlpha[loopIndex2] = 0;

			} else {
				for (int loopIndex3 = 0; loopIndex3 < triangleCount; loopIndex3++)
					triangleAlpha[loopIndex3] = source.triangleAlpha[loopIndex3];

			}
		}
		triangleDrawType = source.triangleDrawType;
		triangleColors = source.triangleColors;
		trianglePriorities = source.trianglePriorities;
		defaultTrianglePriority = source.defaultTrianglePriority;
		triangleGroups = source.triangleGroups;
		vertexGroups = source.vertexGroups;
		triangleVertexA = source.triangleVertexA;
		triangleVertexB = source.triangleVertexB;
		triangleVertexC = source.triangleVertexC;
		triangleShadeA = source.triangleShadeA;
		triangleShadeB = source.triangleShadeB;
		triangleShadeC = source.triangleShadeC;
		texturedTriangleA = source.texturedTriangleA;
		texturedTriangleB = source.texturedTriangleB;
		texturedTriangleC = source.texturedTriangleC;
	}

	/**
	 * Returns first identical vertex id.
	 * 
	 * @return the resulting int
	 * @param source      the source
	 * @param vertexIndex the vertex index
	 */
	private int getFirstIdenticalVertexId(Model source, int vertexIndex) {
		int sourceX = source.verticesX[vertexIndex];
		int sourceY = source.verticesY[vertexIndex];
		int sourceZ = source.verticesZ[vertexIndex];
		for (int vertex = 0; vertex < vertexCount; vertex++) {
			if (sourceX == verticesX[vertex] && sourceY == verticesY[vertex] && sourceZ == verticesZ[vertex])
				return vertex;
		}
		verticesX[vertexCount] = sourceX;
		verticesY[vertexCount] = sourceY;
		verticesZ[vertexCount] = sourceZ;
		if (source.vertexSkins != null)
			vertexSkins[vertexCount] = source.vertexSkins[vertexIndex];
		return vertexCount++;
	}

	/**
	 * Computes vertical extents and the radial/depth bounds used by projection
	 * buckets.
	 */
	public void calculateDiagonals() {
		super.modelHeight = 0;
		horizontalRadius = 0;
		maxY = 0;
		for (int vertex = 0; vertex < vertexCount; vertex++) {
			int x = verticesX[vertex];
			int y = verticesY[vertex];
			int z = verticesZ[vertex];
			if (-y > super.modelHeight)
				super.modelHeight = -y;
			if (y > maxY)
				maxY = y;
			int horizontalDistanceSquared = x * x + z * z;
			if (horizontalDistanceSquared > horizontalRadius)
				horizontalRadius = horizontalDistanceSquared;
		}
		horizontalRadius = (int) (Math.sqrt(horizontalRadius) + 0.98999999999999999D);
		radius = (int) (Math.sqrt(horizontalRadius * horizontalRadius + super.modelHeight * super.modelHeight)
				+ 0.98999999999999999D);
		depthSpan = radius
				+ (int) (Math.sqrt(horizontalRadius * horizontalRadius + maxY * maxY) + 0.98999999999999999D);
	}

	/**
	 * Recomputes only the vertical-dependent radii using the existing horizontal
	 * radius.
	 */
	public void normalise() {
		super.modelHeight = 0;
		maxY = 0;
		for (int vertex = 0; vertex < vertexCount; vertex++) {
			int y = verticesY[vertex];
			if (-y > super.modelHeight)
				super.modelHeight = -y;
			if (y > maxY)
				maxY = y;
		}
		radius = (int) (Math.sqrt(horizontalRadius * horizontalRadius + super.modelHeight * super.modelHeight)
				+ 0.98999999999999999D);
		depthSpan = radius
				+ (int) (Math.sqrt(horizontalRadius * horizontalRadius + maxY * maxY) + 0.98999999999999999D);
	}

	/**
	 * Computes radial bounds plus the packed x/z extrema used by scene normal
	 * merging.
	 */
	public void calculateDiagonalsAndBounds() {
		super.modelHeight = 0;
		horizontalRadius = 0;
		maxY = 0;
		int minX = 32767;
		int maxX = -32767;
		int maxZ = -32767;
		int minZ = 32767;
		for (int vertex = 0; vertex < vertexCount; vertex++) {
			int x = verticesX[vertex];
			int y = verticesY[vertex];
			int z = verticesZ[vertex];
			if (x < minX)
				minX = x;
			if (x > maxX)
				maxX = x;
			if (z < minZ)
				minZ = z;
			if (z > maxZ)
				maxZ = z;
			if (-y > super.modelHeight)
				super.modelHeight = -y;
			if (y > maxY)
				maxY = y;
			int horizontalDistanceSquared = x * x + z * z;
			if (horizontalDistanceSquared > horizontalRadius)
				horizontalRadius = horizontalDistanceSquared;
		}
		horizontalRadius = (int) Math.sqrt(horizontalRadius);
		radius = (int) Math.sqrt(horizontalRadius * horizontalRadius + super.modelHeight * super.modelHeight);
		depthSpan = radius + (int) Math.sqrt(horizontalRadius * horizontalRadius + maxY * maxY);
		packedXBounds = (minX << 16) + (maxX & 0xffff);
		packedZBounds = (maxZ << 16) + (minZ & 0xffff);
	}

	/**
	 * Converts per-vertex/per-triangle skin labels into grouped index arrays for
	 * animation.
	 */
	public void createBones() {
		if (vertexSkins != null) {
			int[] counts = new int[256];
			int maxSkin = 0;
			for (int vertex = 0; vertex < vertexCount; vertex++) {
				int skin = vertexSkins[vertex];
				counts[skin]++;
				if (skin > maxSkin)
					maxSkin = skin;
			}
			vertexGroups = new int[maxSkin + 1][];
			for (int skin = 0; skin <= maxSkin; skin++) {
				vertexGroups[skin] = new int[counts[skin]];
				counts[skin] = 0;
			}
			for (int vertex = 0; vertex < vertexCount; vertex++) {
				int skin = vertexSkins[vertex];
				vertexGroups[skin][counts[skin]++] = vertex;
			}
			vertexSkins = null;
		}
		if (triangleSkins != null) {
			int[] counts = new int[256];
			int maxSkin = 0;
			for (int triangle = 0; triangle < triangleCount; triangle++) {
				int skin = triangleSkins[triangle];
				counts[skin]++;
				if (skin > maxSkin)
					maxSkin = skin;
			}
			triangleGroups = new int[maxSkin + 1][];
			for (int skin = 0; skin <= maxSkin; skin++) {
				triangleGroups[skin] = new int[counts[skin]];
				counts[skin] = 0;
			}
			for (int triangle = 0; triangle < triangleCount; triangle++) {
				int skin = triangleSkins[triangle];
				triangleGroups[skin][counts[skin]++] = triangle;
			}
			triangleSkins = null;
		}
	}

	/**
	 * Applies transformation.
	 * 
	 * @param frameId the frame id
	 */
	public void applyTransformation(int frameId) {
		if (vertexGroups == null)
			return;
		if (frameId == -1)
			return;
		AnimationFrame animationFrame = AnimationFrame.get(frameId);
		if (animationFrame == null)
			return;
		Skeleton skeleton = animationFrame.skeleton;
		transformPivotX = 0;
		transformPivotY = 0;
		transformPivotZ = 0;
		for (int loopIndex = 0; loopIndex < animationFrame.transformCount; loopIndex++) {
			int intermediateValue = animationFrame.transformSkeletonLabels[loopIndex];
			transformFrame(skeleton.transformTypes[intermediateValue], skeleton.labels[intermediateValue],
					animationFrame.transformXs[loopIndex], animationFrame.transformYs[loopIndex],
					animationFrame.transformZs[loopIndex]);
		}

	}

	/**
	 * Performs mix animation frames.
	 * 
	 * @param primaryFrameId   the primary frame id
	 * @param secondaryFrameId the secondary frame id
	 * @param interleaveOrder  the interleave order
	 */
	public void mixAnimationFrames(int primaryFrameId, int secondaryFrameId, int[] interleaveOrder) {
		if (primaryFrameId == -1)
			return;
		if (interleaveOrder == null || secondaryFrameId == -1) {
			applyTransformation(primaryFrameId);
			return;
		}
		AnimationFrame animationFrame = AnimationFrame.get(primaryFrameId);
		if (animationFrame == null)
			return;
		AnimationFrame animationFrame2 = AnimationFrame.get(secondaryFrameId);
		if (animationFrame2 == null) {
			applyTransformation(primaryFrameId);
			return;
		}
		Skeleton skeleton = animationFrame.skeleton;
		transformPivotX = 0;
		transformPivotY = 0;
		transformPivotZ = 0;
		int intermediateValue = 0;
		int intermediateValue2 = interleaveOrder[intermediateValue++];
		for (int loopIndex = 0; loopIndex < animationFrame.transformCount; loopIndex++) {
			int intermediateValue3;
			for (intermediateValue3 = animationFrame.transformSkeletonLabels[loopIndex]; intermediateValue3 > intermediateValue2; intermediateValue2 = interleaveOrder[intermediateValue++])
				;
			if (intermediateValue3 != intermediateValue2 || skeleton.transformTypes[intermediateValue3] == 0)
				transformFrame(skeleton.transformTypes[intermediateValue3], skeleton.labels[intermediateValue3],
						animationFrame.transformXs[loopIndex], animationFrame.transformYs[loopIndex],
						animationFrame.transformZs[loopIndex]);
		}

		transformPivotX = 0;
		transformPivotY = 0;
		transformPivotZ = 0;
		intermediateValue = 0;
		intermediateValue2 = interleaveOrder[intermediateValue++];
		for (int loopIndex2 = 0; loopIndex2 < animationFrame2.transformCount; loopIndex2++) {
			int intermediateValue4;
			for (intermediateValue4 = animationFrame2.transformSkeletonLabels[loopIndex2]; intermediateValue4 > intermediateValue2; intermediateValue2 = interleaveOrder[intermediateValue++])
				;
			if (intermediateValue4 == intermediateValue2 || skeleton.transformTypes[intermediateValue4] == 0)
				transformFrame(skeleton.transformTypes[intermediateValue4], skeleton.labels[intermediateValue4],
						animationFrame2.transformXs[loopIndex2], animationFrame2.transformYs[loopIndex2],
						animationFrame2.transformZs[loopIndex2]);
		}

	}

	/**
	 * Performs transform frame.
	 * 
	 * @param transformType the transform type
	 * @param groups        the groups
	 * @param x             the x
	 * @param y             the y
	 * @param z             the z
	 */
	private void transformFrame(int transformType, int[] groups, int x, int y, int z) {
		int intermediateValue = groups.length;
		if (transformType == 0) {
			int intermediateValue2 = 0;
			transformPivotX = 0;
			transformPivotY = 0;
			transformPivotZ = 0;
			for (int loopIndex = 0; loopIndex < intermediateValue; loopIndex++) {
				int intermediateValue3 = groups[loopIndex];
				if (intermediateValue3 < vertexGroups.length) {
					int values[] = vertexGroups[intermediateValue3];
					for (int loopIndex2 = 0; loopIndex2 < values.length; loopIndex2++) {
						int intermediateValue4 = values[loopIndex2];
						transformPivotX += verticesX[intermediateValue4];
						transformPivotY += verticesY[intermediateValue4];
						transformPivotZ += verticesZ[intermediateValue4];
						intermediateValue2++;
					}

				}
			}

			if (intermediateValue2 > 0) {
				transformPivotX = transformPivotX / intermediateValue2 + x;
				transformPivotY = transformPivotY / intermediateValue2 + y;
				transformPivotZ = transformPivotZ / intermediateValue2 + z;
				return;
			} else {
				transformPivotX = x;
				transformPivotY = y;
				transformPivotZ = z;
				return;
			}
		}
		if (transformType == 1) {
			for (int loopIndex3 = 0; loopIndex3 < intermediateValue; loopIndex3++) {
				int intermediateValue5 = groups[loopIndex3];
				if (intermediateValue5 < vertexGroups.length) {
					int values2[] = vertexGroups[intermediateValue5];
					for (int loopIndex4 = 0; loopIndex4 < values2.length; loopIndex4++) {
						int intermediateValue6 = values2[loopIndex4];
						verticesX[intermediateValue6] += x;
						verticesY[intermediateValue6] += y;
						verticesZ[intermediateValue6] += z;
					}

				}
			}

			return;
		}
		if (transformType == 2) {
			for (int loopIndex5 = 0; loopIndex5 < intermediateValue; loopIndex5++) {
				int intermediateValue7 = groups[loopIndex5];
				if (intermediateValue7 < vertexGroups.length) {
					int values3[] = vertexGroups[intermediateValue7];
					for (int loopIndex6 = 0; loopIndex6 < values3.length; loopIndex6++) {
						int intermediateValue8 = values3[loopIndex6];
						verticesX[intermediateValue8] -= transformPivotX;
						verticesY[intermediateValue8] -= transformPivotY;
						verticesZ[intermediateValue8] -= transformPivotZ;
						int intermediateValue9 = (x & 0xff) * 8;
						int intermediateValue10 = (y & 0xff) * 8;
						int intermediateValue11 = (z & 0xff) * 8;
						if (intermediateValue11 != 0) {
							int intermediateValue12 = SINE[intermediateValue11];
							int intermediateValue13 = COSINE[intermediateValue11];
							int intermediateValue14 = verticesY[intermediateValue8] * intermediateValue12
									+ verticesX[intermediateValue8] * intermediateValue13 >> 16;
							verticesY[intermediateValue8] = verticesY[intermediateValue8] * intermediateValue13
									- verticesX[intermediateValue8] * intermediateValue12 >> 16;
							verticesX[intermediateValue8] = intermediateValue14;
						}
						if (intermediateValue9 != 0) {
							int intermediateValue15 = SINE[intermediateValue9];
							int intermediateValue16 = COSINE[intermediateValue9];
							int intermediateValue17 = verticesY[intermediateValue8] * intermediateValue16
									- verticesZ[intermediateValue8] * intermediateValue15 >> 16;
							verticesZ[intermediateValue8] = verticesY[intermediateValue8] * intermediateValue15
									+ verticesZ[intermediateValue8] * intermediateValue16 >> 16;
							verticesY[intermediateValue8] = intermediateValue17;
						}
						if (intermediateValue10 != 0) {
							int intermediateValue18 = SINE[intermediateValue10];
							int intermediateValue19 = COSINE[intermediateValue10];
							int intermediateValue20 = verticesZ[intermediateValue8] * intermediateValue18
									+ verticesX[intermediateValue8] * intermediateValue19 >> 16;
							verticesZ[intermediateValue8] = verticesZ[intermediateValue8] * intermediateValue19
									- verticesX[intermediateValue8] * intermediateValue18 >> 16;
							verticesX[intermediateValue8] = intermediateValue20;
						}
						verticesX[intermediateValue8] += transformPivotX;
						verticesY[intermediateValue8] += transformPivotY;
						verticesZ[intermediateValue8] += transformPivotZ;
					}

				}
			}

			return;
		}
		if (transformType == 3) {
			for (int loopIndex7 = 0; loopIndex7 < intermediateValue; loopIndex7++) {
				int intermediateValue21 = groups[loopIndex7];
				if (intermediateValue21 < vertexGroups.length) {
					int values4[] = vertexGroups[intermediateValue21];
					for (int loopIndex8 = 0; loopIndex8 < values4.length; loopIndex8++) {
						int intermediateValue22 = values4[loopIndex8];
						verticesX[intermediateValue22] -= transformPivotX;
						verticesY[intermediateValue22] -= transformPivotY;
						verticesZ[intermediateValue22] -= transformPivotZ;
						verticesX[intermediateValue22] = (verticesX[intermediateValue22] * x) / 128;
						verticesY[intermediateValue22] = (verticesY[intermediateValue22] * y) / 128;
						verticesZ[intermediateValue22] = (verticesZ[intermediateValue22] * z) / 128;
						verticesX[intermediateValue22] += transformPivotX;
						verticesY[intermediateValue22] += transformPivotY;
						verticesZ[intermediateValue22] += transformPivotZ;
					}

				}
			}

			return;
		}
		if (transformType == 5 && triangleGroups != null && triangleAlpha != null) {
			for (int loopIndex9 = 0; loopIndex9 < intermediateValue; loopIndex9++) {
				int intermediateValue23 = groups[loopIndex9];
				if (intermediateValue23 < triangleGroups.length) {
					int values5[] = triangleGroups[intermediateValue23];
					for (int loopIndex10 = 0; loopIndex10 < values5.length; loopIndex10++) {
						int intermediateValue24 = values5[loopIndex10];
						triangleAlpha[intermediateValue24] += x * 8;
						if (triangleAlpha[intermediateValue24] < 0)
							triangleAlpha[intermediateValue24] = 0;
						if (triangleAlpha[intermediateValue24] > 255)
							triangleAlpha[intermediateValue24] = 255;
					}

				}
			}

		}
	}

	/**
	 * Performs rotate y90 ccw.
	 */
	public void rotateY90Ccw() {
		for (int loopIndex = 0; loopIndex < vertexCount; loopIndex++) {
			int intermediateValue = verticesX[loopIndex];
			verticesX[loopIndex] = verticesZ[loopIndex];
			verticesZ[loopIndex] = -intermediateValue;
		}

	}

	/**
	 * Performs rotate x.
	 * 
	 * @param angle the angle
	 */
	public void rotateX(int angle) {
		int intermediateValue = SINE[angle];
		int intermediateValue2 = COSINE[angle];
		for (int loopIndex = 0; loopIndex < vertexCount; loopIndex++) {
			int intermediateValue3 = verticesY[loopIndex] * intermediateValue2
					- verticesZ[loopIndex] * intermediateValue >> 16;
			verticesZ[loopIndex] = verticesY[loopIndex] * intermediateValue
					+ verticesZ[loopIndex] * intermediateValue2 >> 16;
			verticesY[loopIndex] = intermediateValue3;
		}
	}

	/**
	 * Performs translate.
	 * 
	 * @param x the x
	 * @param y the y
	 * @param z the z
	 */
	public void translate(int x, int y, int z) {
		for (int loopIndex = 0; loopIndex < vertexCount; loopIndex++) {
			verticesX[loopIndex] += x;
			verticesY[loopIndex] += y;
			verticesZ[loopIndex] += z;
		}

	}

	/**
	 * Performs recolor.
	 * 
	 * @param fromColor the from color
	 * @param toColor   the to color
	 */
	public void recolor(int fromColor, int toColor) {
		for (int loopIndex = 0; loopIndex < triangleCount; loopIndex++)
			if (triangleColors[loopIndex] == fromColor)
				triangleColors[loopIndex] = toColor;

	}

	/**
	 * Performs mirror.
	 */
	public void mirror() {
		for (int loopIndex = 0; loopIndex < vertexCount; loopIndex++)
			verticesZ[loopIndex] = -verticesZ[loopIndex];

		for (int loopIndex2 = 0; loopIndex2 < triangleCount; loopIndex2++) {
			int intermediateValue = triangleVertexA[loopIndex2];
			triangleVertexA[loopIndex2] = triangleVertexC[loopIndex2];
			triangleVertexC[loopIndex2] = intermediateValue;
		}

	}

	/**
	 * Performs scale.
	 * 
	 * @param xScale the x scale
	 * @param yScale the y scale
	 * @param zScale the z scale
	 */
	public void scale(int xScale, int yScale, int zScale) {
		for (int loopIndex = 0; loopIndex < vertexCount; loopIndex++) {
			verticesX[loopIndex] = (verticesX[loopIndex] * xScale) / 128;
			verticesY[loopIndex] = (verticesY[loopIndex] * yScale) / 128;
			verticesZ[loopIndex] = (verticesZ[loopIndex] * zScale) / 128;
		}
	}

	/**
	 * Accumulates face normals and either shades immediately or retains normals for
	 * scene merging.
	 * 
	 * @param ambient          the ambient
	 * @param contrast         the contrast
	 * @param lightX           the light x
	 * @param lightY           the light y
	 * @param lightZ           the light z
	 * @param shadeImmediately the shade immediately
	 */
	public void light(int ambient, int contrast, int lightX, int lightY, int lightZ, boolean shadeImmediately) {
		int lightMagnitude = (int) Math.sqrt(lightX * lightX + lightY * lightY + lightZ * lightZ);
		int scaledContrast = contrast * lightMagnitude >> 8;
		if (triangleShadeA == null) {
			triangleShadeA = new int[triangleCount];
			triangleShadeB = new int[triangleCount];
			triangleShadeC = new int[triangleCount];
		}
		if (super.vertexNormals == null) {
			super.vertexNormals = new VertexNormal[vertexCount];
			for (int vertex = 0; vertex < vertexCount; vertex++)
				super.vertexNormals[vertex] = new VertexNormal();
		}
		for (int triangle = 0; triangle < triangleCount; triangle++) {
			int vertexA = triangleVertexA[triangle];
			int vertexB = triangleVertexB[triangle];
			int vertexC = triangleVertexC[triangle];
			int abX = verticesX[vertexB] - verticesX[vertexA];
			int abY = verticesY[vertexB] - verticesY[vertexA];
			int abZ = verticesZ[vertexB] - verticesZ[vertexA];
			int acX = verticesX[vertexC] - verticesX[vertexA];
			int acY = verticesY[vertexC] - verticesY[vertexA];
			int acZ = verticesZ[vertexC] - verticesZ[vertexA];
			int normalX = abY * acZ - acY * abZ;
			int normalY = abZ * acX - acZ * abX;
			int normalZ;
			for (normalZ = abX * acY - acX * abY; normalX > 8192 || normalY > 8192 || normalZ > 8192 || normalX < -8192
					|| normalY < -8192 || normalZ < -8192; normalZ >>= 1) {
				normalX >>= 1;
				normalY >>= 1;
			}
			int normalLength = (int) Math.sqrt(normalX * normalX + normalY * normalY + normalZ * normalZ);
			if (normalLength <= 0)
				normalLength = 1;
			normalX = normalX * 256 / normalLength;
			normalY = normalY * 256 / normalLength;
			normalZ = normalZ * 256 / normalLength;
			if (triangleDrawType == null || (triangleDrawType[triangle] & 1) == 0) {
				VertexNormal normal = super.vertexNormals[vertexA];
				normal.x += normalX;
				normal.y += normalY;
				normal.z += normalZ;
				normal.magnitude++;
				normal = super.vertexNormals[vertexB];
				normal.x += normalX;
				normal.y += normalY;
				normal.z += normalZ;
				normal.magnitude++;
				normal = super.vertexNormals[vertexC];
				normal.x += normalX;
				normal.y += normalY;
				normal.z += normalZ;
				normal.magnitude++;
			} else {
				int lightness = ambient + (lightX * normalX + lightY * normalY + lightZ * normalZ)
						/ (scaledContrast + scaledContrast / 2);
				triangleShadeA[triangle] = adjustLightness(triangleColors[triangle], lightness,
						triangleDrawType[triangle]);
			}
		}
		if (shadeImmediately) {
			handleShading(ambient, scaledContrast, lightX, lightY, lightZ);
		} else {
			vertexNormalOffsets = new VertexNormal[vertexCount];
			for (int vertex = 0; vertex < vertexCount; vertex++) {
				VertexNormal normal = super.vertexNormals[vertex];
				VertexNormal copy = vertexNormalOffsets[vertex] = new VertexNormal();
				copy.x = normal.x;
				copy.y = normal.y;
				copy.z = normal.z;
				copy.magnitude = normal.magnitude;
			}
			lightingState = (ambient << 16) + (scaledContrast & 0xffff);
		}
		if (shadeImmediately)
			calculateDiagonals();
		else
			calculateDiagonalsAndBounds();
	}

	/**
	 * Applies the stored ambient/contrast after scene normal merging.
	 * 
	 * @param lightX the light x
	 * @param lightY the light y
	 * @param lightZ the light z
	 */
	public void applyDeferredLighting(int lightX, int lightY, int lightZ) {
		int ambient = lightingState >> 16;
		int contrast = (lightingState << 16) >> 16;
		handleShading(ambient, contrast, lightX, lightY, lightZ);
	}

	/**
	 * Finalizes Gouraud/flat face lightness values from the current vertex normals.
	 * 
	 * @param ambient  the ambient
	 * @param contrast the contrast
	 * @param lightX   the light x
	 * @param lightY   the light y
	 * @param lightZ   the light z
	 */
	public void handleShading(int ambient, int contrast, int lightX, int lightY, int lightZ) {
		for (int triangle = 0; triangle < triangleCount; triangle++) {
			int vertexA = triangleVertexA[triangle];
			int vertexB = triangleVertexB[triangle];
			int vertexC = triangleVertexC[triangle];
			if (triangleDrawType == null) {
				int color = triangleColors[triangle];
				VertexNormal normal = super.vertexNormals[vertexA];
				int lightness = ambient
						+ (lightX * normal.x + lightY * normal.y + lightZ * normal.z) / (contrast * normal.magnitude);
				triangleShadeA[triangle] = adjustLightness(color, lightness, 0);
				normal = super.vertexNormals[vertexB];
				lightness = ambient
						+ (lightX * normal.x + lightY * normal.y + lightZ * normal.z) / (contrast * normal.magnitude);
				triangleShadeB[triangle] = adjustLightness(color, lightness, 0);
				normal = super.vertexNormals[vertexC];
				lightness = ambient
						+ (lightX * normal.x + lightY * normal.y + lightZ * normal.z) / (contrast * normal.magnitude);
				triangleShadeC[triangle] = adjustLightness(color, lightness, 0);
			} else if ((triangleDrawType[triangle] & 1) == 0) {
				int color = triangleColors[triangle];
				int drawType = triangleDrawType[triangle];
				VertexNormal normal = super.vertexNormals[vertexA];
				int lightness = ambient
						+ (lightX * normal.x + lightY * normal.y + lightZ * normal.z) / (contrast * normal.magnitude);
				triangleShadeA[triangle] = adjustLightness(color, lightness, drawType);
				normal = super.vertexNormals[vertexB];
				lightness = ambient
						+ (lightX * normal.x + lightY * normal.y + lightZ * normal.z) / (contrast * normal.magnitude);
				triangleShadeB[triangle] = adjustLightness(color, lightness, drawType);
				normal = super.vertexNormals[vertexC];
				lightness = ambient
						+ (lightX * normal.x + lightY * normal.y + lightZ * normal.z) / (contrast * normal.magnitude);
				triangleShadeC[triangle] = adjustLightness(color, lightness, drawType);
			}
		}
		super.vertexNormals = null;
		vertexNormalOffsets = null;
		vertexSkins = null;
		triangleSkins = null;
		if (triangleDrawType != null) {
			for (int triangle = 0; triangle < triangleCount; triangle++)
				if ((triangleDrawType[triangle] & 2) == 2)
					return;
		}
		triangleColors = null;
	}

	/**
	 * Performs adjust lightness.
	 * 
	 * @return the resulting int
	 * @param color     the color
	 * @param lightness the lightness
	 * @param drawType  the draw type
	 */
	private static int adjustLightness(int color, int lightness, int drawType) {
		if ((drawType & 2) == 2) {
			if (lightness < 0)
				lightness = 0;
			else if (lightness > 127)
				lightness = 127;
			lightness = 127 - lightness;
			return lightness;
		}
		lightness = lightness * (color & 0x7f) >> 7;
		if (lightness < 2)
			lightness = 2;
		else if (lightness > 126)
			lightness = 126;
		return (color & 0xff80) + lightness;
	}

	/**
	 * Renders simple.
	 * 
	 * @param rotationX    the rotation x
	 * @param rotationY    the rotation y
	 * @param rotationZ    the rotation z
	 * @param cameraPitch  the camera pitch
	 * @param translationX the translation x
	 * @param translationY the translation y
	 * @param translationZ the translation z
	 */
	public void renderSimple(int rotationX, int rotationY, int rotationZ, int cameraPitch, int translationX,
			int translationY, int translationZ) {
		int screenCenterX = Rasterizer3D.centerX;
		int screenCenterY = Rasterizer3D.centerY;
		int sineX = SINE[rotationX];
		int cosineX = COSINE[rotationX];
		int sineY = SINE[rotationY];
		int cosineY = COSINE[rotationY];
		int sineZ = SINE[rotationZ];
		int cosineZ = COSINE[rotationZ];
		int pitchSine = SINE[cameraPitch];
		int pitchCosine = COSINE[cameraPitch];
		int depthOrigin = translationY * pitchSine + translationZ * pitchCosine >> 16;
		for (int vertex = 0; vertex < vertexCount; vertex++) {
			int x = verticesX[vertex];
			int y = verticesY[vertex];
			int z = verticesZ[vertex];
			if (rotationZ != 0) {
				int rotatedX = y * sineZ
						+ x * cosineZ >> 16;
				y = y * cosineZ
						- x * sineZ >> 16;
				x = rotatedX;
			}
			if (rotationX != 0) {
				int rotatedY = y * cosineX
						- z * sineX >> 16;
				z = y * sineX
						+ z * cosineX >> 16;
				y = rotatedY;
			}
			if (rotationY != 0) {
				int rotatedX = z * sineY
						+ x * cosineY >> 16;
				z = z * cosineY
						- x * sineY >> 16;
				x = rotatedX;
			}
			x += translationX;
			y += translationY;
			z += translationZ;
			int viewY = y * pitchCosine
					- z * pitchSine >> 16;
			z = y * pitchSine
					+ z * pitchCosine >> 16;
			y = viewY;
			projectedDepth[vertex] = z - depthOrigin;
			projectedX[vertex] = screenCenterX + (x << 9) / z;
			projectedY[vertex] = screenCenterY + (y << 9) / z;
			if (texturedTriangleCount > 0) {
				cameraX[vertex] = x;
				cameraY[vertex] = y;
				cameraZ[vertex] = z;
			}
		}

		try {
			drawFaces(false, false, 0);
			return;
		} catch (Exception ignored) {
			return;
		}
	}

	/**
	 * Draws value.
	 * 
	 * @param orientation the orientation
	 * @param pitchSine   the pitch sine
	 * @param pitchCosine the pitch cosine
	 * @param yawSine     the yaw sine
	 * @param yawCosine   the yaw cosine
	 * @param x           the x
	 * @param y           the y
	 * @param z           the z
	 * @param uid         the uid
	 */
	@Override
	public void draw(int orientation, int pitchSine, int pitchCosine, int yawSine, int yawCosine, int x, int y, int z,
			int uid) {
		drawInternal(orientation, pitchSine, pitchCosine, yawSine, yawCosine, x, y, z, uid);
	}

	/**
	 * Draws internal.
	 * 
	 * @param inputValue  the input value
	 * @param inputValue2 the input value2
	 * @param inputValue3 the input value3
	 * @param inputValue4 the input value4
	 * @param inputValue5 the input value5
	 * @param inputValue6 the input value6
	 * @param inputValue7 the input value7
	 * @param inputValue8 the input value8
	 * @param inputValue9 the input value9
	 */
	private void drawInternal(int inputValue, int inputValue2, int inputValue3, int inputValue4, int inputValue5,
			int inputValue6, int inputValue7, int inputValue8, int inputValue9) {
		int intermediateValue = inputValue8 * inputValue5 - inputValue6 * inputValue4 >> 16;
		int intermediateValue2 = inputValue7 * inputValue2 + intermediateValue * inputValue3 >> 16;
		int intermediateValue3 = horizontalRadius * inputValue3 >> 16;
		int intermediateValue4 = intermediateValue2 + intermediateValue3;
		if (intermediateValue4 <= 50 || intermediateValue2 >= 3500)
			return;
		int intermediateValue5 = inputValue8 * inputValue4 + inputValue6 * inputValue5 >> 16;
		int intermediateValue6 = intermediateValue5 - horizontalRadius << 9;
		if (intermediateValue6 / intermediateValue4 >= Rasterizer.centerX)
			return;
		int intermediateValue7 = intermediateValue5 + horizontalRadius << 9;
		if (intermediateValue7 / intermediateValue4 <= -Rasterizer.centerX)
			return;
		int intermediateValue8 = inputValue7 * inputValue3 - intermediateValue * inputValue2 >> 16;
		int intermediateValue9 = horizontalRadius * inputValue2 >> 16;
		int intermediateValue10 = intermediateValue8 + intermediateValue9 << 9;
		if (intermediateValue10 / intermediateValue4 <= -Rasterizer.centerY)
			return;
		int intermediateValue11 = intermediateValue9 + (super.modelHeight * inputValue3 >> 16);
		int intermediateValue12 = intermediateValue8 - intermediateValue11 << 9;
		if (intermediateValue12 / intermediateValue4 >= Rasterizer.centerY)
			return;
		int intermediateValue13 = intermediateValue3 + (super.modelHeight * inputValue2 >> 16);
		boolean conditionFlag = false;
		if (intermediateValue2 - intermediateValue13 <= 50)
			conditionFlag = true;
		boolean conditionFlag2 = false;
		if (inputValue9 > 0 && pickingEnabled) {
			int intermediateValue14 = intermediateValue2 - intermediateValue3;
			if (intermediateValue14 <= 50)
				intermediateValue14 = 50;
			if (intermediateValue5 > 0) {
				intermediateValue6 /= intermediateValue4;
				intermediateValue7 /= intermediateValue14;
			} else {
				intermediateValue7 /= intermediateValue4;
				intermediateValue6 /= intermediateValue14;
			}
			if (intermediateValue8 > 0) {
				intermediateValue12 /= intermediateValue4;
				intermediateValue10 /= intermediateValue14;
			} else {
				intermediateValue10 /= intermediateValue4;
				intermediateValue12 /= intermediateValue14;
			}
			int intermediateValue15 = mouseX - Rasterizer3D.centerX;
			int intermediateValue16 = mouseY - Rasterizer3D.centerY;
			if (intermediateValue15 > intermediateValue6 && intermediateValue15 < intermediateValue7
					&& intermediateValue16 > intermediateValue12 && intermediateValue16 < intermediateValue10)
				if (singleTile)
					pickedUids[pickedCount++] = inputValue9;
				else
					conditionFlag2 = true;
		}
		int intermediateValue17 = Rasterizer3D.centerX;
		int intermediateValue18 = Rasterizer3D.centerY;
		int intermediateValue19 = 0;
		int intermediateValue20 = 0;
		if (inputValue != 0) {
			intermediateValue19 = SINE[inputValue];
			intermediateValue20 = COSINE[inputValue];
		}
		for (int loopIndex = 0; loopIndex < vertexCount; loopIndex++) {
			int intermediateValue21 = verticesX[loopIndex];
			int intermediateValue22 = verticesY[loopIndex];
			int intermediateValue23 = verticesZ[loopIndex];
			if (inputValue != 0) {
				int intermediateValue24 = intermediateValue23 * intermediateValue19
						+ intermediateValue21 * intermediateValue20 >> 16;
				intermediateValue23 = intermediateValue23 * intermediateValue20
						- intermediateValue21 * intermediateValue19 >> 16;
				intermediateValue21 = intermediateValue24;
			}
			intermediateValue21 += inputValue6;
			intermediateValue22 += inputValue7;
			intermediateValue23 += inputValue8;
			int intermediateValue25 = intermediateValue23 * inputValue4 + intermediateValue21 * inputValue5 >> 16;
			intermediateValue23 = intermediateValue23 * inputValue5 - intermediateValue21 * inputValue4 >> 16;
			intermediateValue21 = intermediateValue25;
			intermediateValue25 = intermediateValue22 * inputValue3 - intermediateValue23 * inputValue2 >> 16;
			intermediateValue23 = intermediateValue22 * inputValue2 + intermediateValue23 * inputValue3 >> 16;
			intermediateValue22 = intermediateValue25;
			projectedDepth[loopIndex] = intermediateValue23 - intermediateValue2;
			if (intermediateValue23 >= 50) {
				projectedX[loopIndex] = intermediateValue17 + (intermediateValue21 << 9) / intermediateValue23;
				projectedY[loopIndex] = intermediateValue18 + (intermediateValue22 << 9) / intermediateValue23;
			} else {
				projectedX[loopIndex] = -5000;
				conditionFlag = true;
			}
			if (conditionFlag || texturedTriangleCount > 0) {
				cameraX[loopIndex] = intermediateValue21;
				cameraY[loopIndex] = intermediateValue22;
				cameraZ[loopIndex] = intermediateValue23;
			}
		}

		try {
			drawFaces(conditionFlag, conditionFlag2, inputValue9);
			return;
		} catch (Exception _ex) {
			return;
		}
	}

	/**
	 * Draws faces.
	 * 
	 * @param conditionFlag  the condition flag
	 * @param conditionFlag2 the condition flag2
	 * @param inputValue     the input value
	 */
	private void drawFaces(boolean conditionFlag, boolean conditionFlag2, int inputValue) {
		for (int loopIndex = 0; loopIndex < depthSpan; loopIndex++)
			depthBucketCounts[loopIndex] = 0;

		for (int loopIndex2 = 0; loopIndex2 < triangleCount; loopIndex2++)
			if (triangleDrawType == null || triangleDrawType[loopIndex2] != -1) {
				int intermediateValue = triangleVertexA[loopIndex2];
				int intermediateValue2 = triangleVertexB[loopIndex2];
				int intermediateValue3 = triangleVertexC[loopIndex2];
				int intermediateValue4 = projectedX[intermediateValue];
				int intermediateValue5 = projectedX[intermediateValue2];
				int intermediateValue6 = projectedX[intermediateValue3];
				if (conditionFlag && (intermediateValue4 == -5000 || intermediateValue5 == -5000
						|| intermediateValue6 == -5000)) {
					faceNearClipped[loopIndex2] = true;
					int intermediateValue7 = (projectedDepth[intermediateValue] + projectedDepth[intermediateValue2]
							+ projectedDepth[intermediateValue3]) / 3 + radius;
					depthBuckets[intermediateValue7][depthBucketCounts[intermediateValue7]++] = loopIndex2;
				} else {
					if (conditionFlag2 && containsPoint(mouseX, mouseY, projectedY[intermediateValue],
							projectedY[intermediateValue2], projectedY[intermediateValue3], intermediateValue4,
							intermediateValue5, intermediateValue6)) {
						pickedUids[pickedCount++] = inputValue;
						conditionFlag2 = false;
					}
					if ((intermediateValue4 - intermediateValue5)
							* (projectedY[intermediateValue3] - projectedY[intermediateValue2])
							- (projectedY[intermediateValue] - projectedY[intermediateValue2])
									* (intermediateValue6 - intermediateValue5) > 0) {
						faceNearClipped[loopIndex2] = false;
						if (intermediateValue4 < 0 || intermediateValue5 < 0 || intermediateValue6 < 0
								|| intermediateValue4 > Rasterizer.viewportRx
								|| intermediateValue5 > Rasterizer.viewportRx
								|| intermediateValue6 > Rasterizer.viewportRx)
							faceOutOfBounds[loopIndex2] = true;
						else
							faceOutOfBounds[loopIndex2] = false;
						int intermediateValue8 = (projectedDepth[intermediateValue] + projectedDepth[intermediateValue2]
								+ projectedDepth[intermediateValue3]) / 3 + radius;
						depthBuckets[intermediateValue8][depthBucketCounts[intermediateValue8]++] = loopIndex2;
					}
				}
			}

		if (trianglePriorities == null) {
			for (int loopIndex3 = depthSpan - 1; loopIndex3 >= 0; loopIndex3--) {
				int intermediateValue9 = depthBucketCounts[loopIndex3];
				if (intermediateValue9 > 0) {
					int values[] = depthBuckets[loopIndex3];
					for (int loopIndex4 = 0; loopIndex4 < intermediateValue9; loopIndex4++)
						drawFace(values[loopIndex4]);

				}
			}

			return;
		}
		for (int loopIndex5 = 0; loopIndex5 < 12; loopIndex5++) {
			priorityBucketCounts[loopIndex5] = 0;
			priorityDepthSums[loopIndex5] = 0;
		}

		for (int loopIndex6 = depthSpan - 1; loopIndex6 >= 0; loopIndex6--) {
			int intermediateValue10 = depthBucketCounts[loopIndex6];
			if (intermediateValue10 > 0) {
				int values2[] = depthBuckets[loopIndex6];
				for (int loopIndex7 = 0; loopIndex7 < intermediateValue10; loopIndex7++) {
					int intermediateValue11 = values2[loopIndex7];
					int intermediateValue12 = trianglePriorities[intermediateValue11];
					int intermediateValue13 = priorityBucketCounts[intermediateValue12]++;
					priorityBuckets[intermediateValue12][intermediateValue13] = intermediateValue11;
					if (intermediateValue12 < 10)
						priorityDepthSums[intermediateValue12] += loopIndex6;
					else if (intermediateValue12 == 10)
						priority10Depths[intermediateValue13] = loopIndex6;
					else
						priority11Depths[intermediateValue13] = loopIndex6;
				}

			}
		}

		int intermediateValue14 = 0;
		if (priorityBucketCounts[1] > 0 || priorityBucketCounts[2] > 0)
			intermediateValue14 = (priorityDepthSums[1] + priorityDepthSums[2])
					/ (priorityBucketCounts[1] + priorityBucketCounts[2]);
		int intermediateValue15 = 0;
		if (priorityBucketCounts[3] > 0 || priorityBucketCounts[4] > 0)
			intermediateValue15 = (priorityDepthSums[3] + priorityDepthSums[4])
					/ (priorityBucketCounts[3] + priorityBucketCounts[4]);
		int intermediateValue16 = 0;
		if (priorityBucketCounts[6] > 0 || priorityBucketCounts[8] > 0)
			intermediateValue16 = (priorityDepthSums[6] + priorityDepthSums[8])
					/ (priorityBucketCounts[6] + priorityBucketCounts[8]);
		int intermediateValue17 = 0;
		int intermediateValue18 = priorityBucketCounts[10];
		int values3[] = priorityBuckets[10];
		int values4[] = priority10Depths;
		if (intermediateValue17 == intermediateValue18) {
			intermediateValue17 = 0;
			intermediateValue18 = priorityBucketCounts[11];
			values3 = priorityBuckets[11];
			values4 = priority11Depths;
		}
		int intermediateValue19;
		if (intermediateValue17 < intermediateValue18)
			intermediateValue19 = values4[intermediateValue17];
		else
			intermediateValue19 = -1000;
		for (int loopIndex8 = 0; loopIndex8 < 10; loopIndex8++) {
			while (loopIndex8 == 0 && intermediateValue19 > intermediateValue14) {
				drawFace(values3[intermediateValue17++]);
				if (intermediateValue17 == intermediateValue18 && values3 != priorityBuckets[11]) {
					intermediateValue17 = 0;
					intermediateValue18 = priorityBucketCounts[11];
					values3 = priorityBuckets[11];
					values4 = priority11Depths;
				}
				if (intermediateValue17 < intermediateValue18)
					intermediateValue19 = values4[intermediateValue17];
				else
					intermediateValue19 = -1000;
			}
			while (loopIndex8 == 3 && intermediateValue19 > intermediateValue15) {
				drawFace(values3[intermediateValue17++]);
				if (intermediateValue17 == intermediateValue18 && values3 != priorityBuckets[11]) {
					intermediateValue17 = 0;
					intermediateValue18 = priorityBucketCounts[11];
					values3 = priorityBuckets[11];
					values4 = priority11Depths;
				}
				if (intermediateValue17 < intermediateValue18)
					intermediateValue19 = values4[intermediateValue17];
				else
					intermediateValue19 = -1000;
			}
			while (loopIndex8 == 5 && intermediateValue19 > intermediateValue16) {
				drawFace(values3[intermediateValue17++]);
				if (intermediateValue17 == intermediateValue18 && values3 != priorityBuckets[11]) {
					intermediateValue17 = 0;
					intermediateValue18 = priorityBucketCounts[11];
					values3 = priorityBuckets[11];
					values4 = priority11Depths;
				}
				if (intermediateValue17 < intermediateValue18)
					intermediateValue19 = values4[intermediateValue17];
				else
					intermediateValue19 = -1000;
			}
			int intermediateValue20 = priorityBucketCounts[loopIndex8];
			int values5[] = priorityBuckets[loopIndex8];
			for (int loopIndex9 = 0; loopIndex9 < intermediateValue20; loopIndex9++)
				drawFace(values5[loopIndex9]);

		}

		while (intermediateValue19 != -1000) {
			drawFace(values3[intermediateValue17++]);
			if (intermediateValue17 == intermediateValue18 && values3 != priorityBuckets[11]) {
				intermediateValue17 = 0;
				values3 = priorityBuckets[11];
				intermediateValue18 = priorityBucketCounts[11];
				values4 = priority11Depths;
			}
			if (intermediateValue17 < intermediateValue18)
				intermediateValue19 = values4[intermediateValue17];
			else
				intermediateValue19 = -1000;
		}
	}

	/**
	 * Draws face.
	 * 
	 * @param inputValue the input value
	 */
	private void drawFace(int triangle) {
		if (faceNearClipped[triangle]) {
			drawNearClippedFace(triangle);
			return;
		}
		int vertexA = triangleVertexA[triangle];
		int vertexB = triangleVertexB[triangle];
		int vertexC = triangleVertexC[triangle];
		Rasterizer3D.restrictEdges = faceOutOfBounds[triangle];
		if (triangleAlpha == null)
			Rasterizer3D.alpha = 0;
		else
			Rasterizer3D.alpha = triangleAlpha[triangle];
		int drawType;
		if (triangleDrawType == null)
			drawType = 0;
		else
			drawType = triangleDrawType[triangle] & 3;
		if (drawType == 0) {
			Rasterizer3D.drawGouraudTriangle(projectedY[vertexA], projectedY[vertexB],
					projectedY[vertexC], projectedX[vertexA], projectedX[vertexB],
					projectedX[vertexC], triangleShadeA[triangle], triangleShadeB[triangle],
					triangleShadeC[triangle]);
			return;
		}
		if (drawType == 1) {
			Rasterizer3D.drawFlatTriangle(projectedY[vertexA], projectedY[vertexB],
					projectedY[vertexC], projectedX[vertexA], projectedX[vertexB],
					projectedX[vertexC], HSL_TO_RGB[triangleShadeA[triangle]]);
			return;
		}
		if (drawType == 2) {
			int textureTriangle = triangleDrawType[triangle] >> 2;
			int textureVertexA = texturedTriangleA[textureTriangle];
			int textureVertexB = texturedTriangleB[textureTriangle];
			int textureVertexC = texturedTriangleC[textureTriangle];
			Rasterizer3D.drawTexturedTriangle(projectedY[vertexA], projectedY[vertexB],
					projectedY[vertexC], projectedX[vertexA], projectedX[vertexB],
					projectedX[vertexC], triangleShadeA[triangle], triangleShadeB[triangle],
					triangleShadeC[triangle], cameraX[textureVertexA], cameraX[textureVertexB],
					cameraX[textureVertexC], cameraY[textureVertexA], cameraY[textureVertexB],
					cameraY[textureVertexC], cameraZ[textureVertexA], cameraZ[textureVertexB],
					cameraZ[textureVertexC], triangleColors[triangle]);
			return;
		}
		if (drawType == 3) {
			int textureTriangle = triangleDrawType[triangle] >> 2;
			int textureVertexA = texturedTriangleA[textureTriangle];
			int textureVertexB = texturedTriangleB[textureTriangle];
			int textureVertexC = texturedTriangleC[textureTriangle];
			Rasterizer3D.drawTexturedTriangle(projectedY[vertexA], projectedY[vertexB],
					projectedY[vertexC], projectedX[vertexA], projectedX[vertexB],
					projectedX[vertexC], triangleShadeA[triangle], triangleShadeA[triangle],
					triangleShadeA[triangle], cameraX[textureVertexA], cameraX[textureVertexB],
					cameraX[textureVertexC], cameraY[textureVertexA], cameraY[textureVertexB],
					cameraY[textureVertexC], cameraZ[textureVertexA], cameraZ[textureVertexB],
					cameraZ[textureVertexC], triangleColors[triangle]);
		}
	}

	/**
	 * Draws near clipped face.
	 * 
	 * @param inputValue the input value
	 */
	private void drawNearClippedFace(int inputValue) {
		int intermediateValue = Rasterizer3D.centerX;
		int intermediateValue2 = Rasterizer3D.centerY;
		int intermediateValue3 = 0;
		int intermediateValue4 = triangleVertexA[inputValue];
		int intermediateValue5 = triangleVertexB[inputValue];
		int intermediateValue6 = triangleVertexC[inputValue];
		int intermediateValue7 = cameraZ[intermediateValue4];
		int intermediateValue8 = cameraZ[intermediateValue5];
		int intermediateValue9 = cameraZ[intermediateValue6];
		if (intermediateValue7 >= 50) {
			clippedX[intermediateValue3] = projectedX[intermediateValue4];
			clippedY[intermediateValue3] = projectedY[intermediateValue4];
			clippedShade[intermediateValue3++] = triangleShadeA[inputValue];
		} else {
			int intermediateValue10 = cameraX[intermediateValue4];
			int intermediateValue11 = cameraY[intermediateValue4];
			int intermediateValue12 = triangleShadeA[inputValue];
			if (intermediateValue9 >= 50) {
				int intermediateValue13 = (50 - intermediateValue7)
						* RECIPROCAL_16[intermediateValue9 - intermediateValue7];
				clippedX[intermediateValue3] = intermediateValue + (intermediateValue10
						+ ((cameraX[intermediateValue6] - intermediateValue10) * intermediateValue13 >> 16) << 9) / 50;
				clippedY[intermediateValue3] = intermediateValue2 + (intermediateValue11
						+ ((cameraY[intermediateValue6] - intermediateValue11) * intermediateValue13 >> 16) << 9) / 50;
				clippedShade[intermediateValue3++] = intermediateValue12
						+ ((triangleShadeC[inputValue] - intermediateValue12) * intermediateValue13 >> 16);
			}
			if (intermediateValue8 >= 50) {
				int intermediateValue14 = (50 - intermediateValue7)
						* RECIPROCAL_16[intermediateValue8 - intermediateValue7];
				clippedX[intermediateValue3] = intermediateValue + (intermediateValue10
						+ ((cameraX[intermediateValue5] - intermediateValue10) * intermediateValue14 >> 16) << 9) / 50;
				clippedY[intermediateValue3] = intermediateValue2 + (intermediateValue11
						+ ((cameraY[intermediateValue5] - intermediateValue11) * intermediateValue14 >> 16) << 9) / 50;
				clippedShade[intermediateValue3++] = intermediateValue12
						+ ((triangleShadeB[inputValue] - intermediateValue12) * intermediateValue14 >> 16);
			}
		}
		if (intermediateValue8 >= 50) {
			clippedX[intermediateValue3] = projectedX[intermediateValue5];
			clippedY[intermediateValue3] = projectedY[intermediateValue5];
			clippedShade[intermediateValue3++] = triangleShadeB[inputValue];
		} else {
			int intermediateValue15 = cameraX[intermediateValue5];
			int intermediateValue16 = cameraY[intermediateValue5];
			int intermediateValue17 = triangleShadeB[inputValue];
			if (intermediateValue7 >= 50) {
				int intermediateValue18 = (50 - intermediateValue8)
						* RECIPROCAL_16[intermediateValue7 - intermediateValue8];
				clippedX[intermediateValue3] = intermediateValue + (intermediateValue15
						+ ((cameraX[intermediateValue4] - intermediateValue15) * intermediateValue18 >> 16) << 9) / 50;
				clippedY[intermediateValue3] = intermediateValue2 + (intermediateValue16
						+ ((cameraY[intermediateValue4] - intermediateValue16) * intermediateValue18 >> 16) << 9) / 50;
				clippedShade[intermediateValue3++] = intermediateValue17
						+ ((triangleShadeA[inputValue] - intermediateValue17) * intermediateValue18 >> 16);
			}
			if (intermediateValue9 >= 50) {
				int intermediateValue19 = (50 - intermediateValue8)
						* RECIPROCAL_16[intermediateValue9 - intermediateValue8];
				clippedX[intermediateValue3] = intermediateValue + (intermediateValue15
						+ ((cameraX[intermediateValue6] - intermediateValue15) * intermediateValue19 >> 16) << 9) / 50;
				clippedY[intermediateValue3] = intermediateValue2 + (intermediateValue16
						+ ((cameraY[intermediateValue6] - intermediateValue16) * intermediateValue19 >> 16) << 9) / 50;
				clippedShade[intermediateValue3++] = intermediateValue17
						+ ((triangleShadeC[inputValue] - intermediateValue17) * intermediateValue19 >> 16);
			}
		}
		if (intermediateValue9 >= 50) {
			clippedX[intermediateValue3] = projectedX[intermediateValue6];
			clippedY[intermediateValue3] = projectedY[intermediateValue6];
			clippedShade[intermediateValue3++] = triangleShadeC[inputValue];
		} else {
			int intermediateValue20 = cameraX[intermediateValue6];
			int intermediateValue21 = cameraY[intermediateValue6];
			int intermediateValue22 = triangleShadeC[inputValue];
			if (intermediateValue8 >= 50) {
				int intermediateValue23 = (50 - intermediateValue9)
						* RECIPROCAL_16[intermediateValue8 - intermediateValue9];
				clippedX[intermediateValue3] = intermediateValue + (intermediateValue20
						+ ((cameraX[intermediateValue5] - intermediateValue20) * intermediateValue23 >> 16) << 9) / 50;
				clippedY[intermediateValue3] = intermediateValue2 + (intermediateValue21
						+ ((cameraY[intermediateValue5] - intermediateValue21) * intermediateValue23 >> 16) << 9) / 50;
				clippedShade[intermediateValue3++] = intermediateValue22
						+ ((triangleShadeB[inputValue] - intermediateValue22) * intermediateValue23 >> 16);
			}
			if (intermediateValue7 >= 50) {
				int intermediateValue24 = (50 - intermediateValue9)
						* RECIPROCAL_16[intermediateValue7 - intermediateValue9];
				clippedX[intermediateValue3] = intermediateValue + (intermediateValue20
						+ ((cameraX[intermediateValue4] - intermediateValue20) * intermediateValue24 >> 16) << 9) / 50;
				clippedY[intermediateValue3] = intermediateValue2 + (intermediateValue21
						+ ((cameraY[intermediateValue4] - intermediateValue21) * intermediateValue24 >> 16) << 9) / 50;
				clippedShade[intermediateValue3++] = intermediateValue22
						+ ((triangleShadeA[inputValue] - intermediateValue22) * intermediateValue24 >> 16);
			}
		}
		int intermediateValue25 = clippedX[0];
		int intermediateValue26 = clippedX[1];
		int intermediateValue27 = clippedX[2];
		int intermediateValue28 = clippedY[0];
		int intermediateValue29 = clippedY[1];
		int intermediateValue30 = clippedY[2];
		if ((intermediateValue25 - intermediateValue26) * (intermediateValue30 - intermediateValue29)
				- (intermediateValue28 - intermediateValue29) * (intermediateValue27 - intermediateValue26) > 0) {
			Rasterizer3D.restrictEdges = false;
			if (intermediateValue3 == 3) {
				if (intermediateValue25 < 0 || intermediateValue26 < 0 || intermediateValue27 < 0
						|| intermediateValue25 > Rasterizer.viewportRx || intermediateValue26 > Rasterizer.viewportRx
						|| intermediateValue27 > Rasterizer.viewportRx)
					Rasterizer3D.restrictEdges = true;
				int intermediateValue31;
				if (triangleDrawType == null)
					intermediateValue31 = 0;
				else
					intermediateValue31 = triangleDrawType[inputValue] & 3;
				if (intermediateValue31 == 0)
					Rasterizer3D.drawGouraudTriangle(intermediateValue28, intermediateValue29, intermediateValue30,
							intermediateValue25, intermediateValue26, intermediateValue27, clippedShade[0],
							clippedShade[1], clippedShade[2]);
				else if (intermediateValue31 == 1)
					Rasterizer3D.drawFlatTriangle(intermediateValue28, intermediateValue29, intermediateValue30,
							intermediateValue25, intermediateValue26, intermediateValue27,
							HSL_TO_RGB[triangleShadeA[inputValue]]);
				else if (intermediateValue31 == 2) {
					int intermediateValue32 = triangleDrawType[inputValue] >> 2;
					int intermediateValue33 = texturedTriangleA[intermediateValue32];
					int intermediateValue34 = texturedTriangleB[intermediateValue32];
					int intermediateValue35 = texturedTriangleC[intermediateValue32];
					Rasterizer3D.drawTexturedTriangle(intermediateValue28, intermediateValue29, intermediateValue30,
							intermediateValue25, intermediateValue26, intermediateValue27, clippedShade[0],
							clippedShade[1], clippedShade[2], cameraX[intermediateValue33],
							cameraX[intermediateValue34], cameraX[intermediateValue35], cameraY[intermediateValue33],
							cameraY[intermediateValue34], cameraY[intermediateValue35], cameraZ[intermediateValue33],
							cameraZ[intermediateValue34], cameraZ[intermediateValue35], triangleColors[inputValue]);
				} else if (intermediateValue31 == 3) {
					int intermediateValue36 = triangleDrawType[inputValue] >> 2;
					int intermediateValue37 = texturedTriangleA[intermediateValue36];
					int intermediateValue38 = texturedTriangleB[intermediateValue36];
					int intermediateValue39 = texturedTriangleC[intermediateValue36];
					Rasterizer3D.drawTexturedTriangle(intermediateValue28, intermediateValue29, intermediateValue30,
							intermediateValue25, intermediateValue26, intermediateValue27, triangleShadeA[inputValue],
							triangleShadeA[inputValue], triangleShadeA[inputValue], cameraX[intermediateValue37],
							cameraX[intermediateValue38], cameraX[intermediateValue39], cameraY[intermediateValue37],
							cameraY[intermediateValue38], cameraY[intermediateValue39], cameraZ[intermediateValue37],
							cameraZ[intermediateValue38], cameraZ[intermediateValue39], triangleColors[inputValue]);
				}
			}
			if (intermediateValue3 == 4) {
				if (intermediateValue25 < 0 || intermediateValue26 < 0 || intermediateValue27 < 0
						|| intermediateValue25 > Rasterizer.viewportRx || intermediateValue26 > Rasterizer.viewportRx
						|| intermediateValue27 > Rasterizer.viewportRx || clippedX[3] < 0
						|| clippedX[3] > Rasterizer.viewportRx)
					Rasterizer3D.restrictEdges = true;
				int intermediateValue40;
				if (triangleDrawType == null)
					intermediateValue40 = 0;
				else
					intermediateValue40 = triangleDrawType[inputValue] & 3;
				if (intermediateValue40 == 0) {
					Rasterizer3D.drawGouraudTriangle(intermediateValue28, intermediateValue29, intermediateValue30,
							intermediateValue25, intermediateValue26, intermediateValue27, clippedShade[0],
							clippedShade[1], clippedShade[2]);
					Rasterizer3D.drawGouraudTriangle(intermediateValue28, intermediateValue30, clippedY[3],
							intermediateValue25, intermediateValue27, clippedX[3], clippedShade[0], clippedShade[2],
							clippedShade[3]);
					return;
				}
				if (intermediateValue40 == 1) {
					int intermediateValue41 = HSL_TO_RGB[triangleShadeA[inputValue]];
					Rasterizer3D.drawFlatTriangle(intermediateValue28, intermediateValue29, intermediateValue30,
							intermediateValue25, intermediateValue26, intermediateValue27, intermediateValue41);
					Rasterizer3D.drawFlatTriangle(intermediateValue28, intermediateValue30, clippedY[3],
							intermediateValue25, intermediateValue27, clippedX[3], intermediateValue41);
					return;
				}
				if (intermediateValue40 == 2) {
					int intermediateValue42 = triangleDrawType[inputValue] >> 2;
					int intermediateValue43 = texturedTriangleA[intermediateValue42];
					int intermediateValue44 = texturedTriangleB[intermediateValue42];
					int intermediateValue45 = texturedTriangleC[intermediateValue42];
					Rasterizer3D.drawTexturedTriangle(intermediateValue28, intermediateValue29, intermediateValue30,
							intermediateValue25, intermediateValue26, intermediateValue27, clippedShade[0],
							clippedShade[1], clippedShade[2], cameraX[intermediateValue43],
							cameraX[intermediateValue44], cameraX[intermediateValue45], cameraY[intermediateValue43],
							cameraY[intermediateValue44], cameraY[intermediateValue45], cameraZ[intermediateValue43],
							cameraZ[intermediateValue44], cameraZ[intermediateValue45], triangleColors[inputValue]);
					Rasterizer3D.drawTexturedTriangle(intermediateValue28, intermediateValue30, clippedY[3],
							intermediateValue25, intermediateValue27, clippedX[3], clippedShade[0], clippedShade[2],
							clippedShade[3], cameraX[intermediateValue43], cameraX[intermediateValue44],
							cameraX[intermediateValue45], cameraY[intermediateValue43], cameraY[intermediateValue44],
							cameraY[intermediateValue45], cameraZ[intermediateValue43], cameraZ[intermediateValue44],
							cameraZ[intermediateValue45], triangleColors[inputValue]);
					return;
				}
				if (intermediateValue40 == 3) {
					int intermediateValue46 = triangleDrawType[inputValue] >> 2;
					int intermediateValue47 = texturedTriangleA[intermediateValue46];
					int intermediateValue48 = texturedTriangleB[intermediateValue46];
					int intermediateValue49 = texturedTriangleC[intermediateValue46];
					Rasterizer3D.drawTexturedTriangle(intermediateValue28, intermediateValue29, intermediateValue30,
							intermediateValue25, intermediateValue26, intermediateValue27, triangleShadeA[inputValue],
							triangleShadeA[inputValue], triangleShadeA[inputValue], cameraX[intermediateValue47],
							cameraX[intermediateValue48], cameraX[intermediateValue49], cameraY[intermediateValue47],
							cameraY[intermediateValue48], cameraY[intermediateValue49], cameraZ[intermediateValue47],
							cameraZ[intermediateValue48], cameraZ[intermediateValue49], triangleColors[inputValue]);
					Rasterizer3D.drawTexturedTriangle(intermediateValue28, intermediateValue30, clippedY[3],
							intermediateValue25, intermediateValue27, clippedX[3], triangleShadeA[inputValue],
							triangleShadeA[inputValue], triangleShadeA[inputValue], cameraX[intermediateValue47],
							cameraX[intermediateValue48], cameraX[intermediateValue49], cameraY[intermediateValue47],
							cameraY[intermediateValue48], cameraY[intermediateValue49], cameraZ[intermediateValue47],
							cameraZ[intermediateValue48], cameraZ[intermediateValue49], triangleColors[inputValue]);
				}
			}
		}
	}

	/**
	 * Performs contains point.
	 * 
	 * @return the resulting boolean
	 * @param inputValue  the input value
	 * @param inputValue2 the input value2
	 * @param inputValue3 the input value3
	 * @param inputValue4 the input value4
	 * @param inputValue5 the input value5
	 * @param inputValue6 the input value6
	 * @param inputValue7 the input value7
	 * @param inputValue8 the input value8
	 */
	private boolean containsPoint(int inputValue, int inputValue2, int inputValue3, int inputValue4, int inputValue5,
			int inputValue6, int inputValue7, int inputValue8) {
		if (inputValue2 < inputValue3 && inputValue2 < inputValue4 && inputValue2 < inputValue5)
			return false;
		if (inputValue2 > inputValue3 && inputValue2 > inputValue4 && inputValue2 > inputValue5)
			return false;
		if (inputValue < inputValue6 && inputValue < inputValue7 && inputValue < inputValue8)
			return false;
		return inputValue <= inputValue6 || inputValue <= inputValue7 || inputValue <= inputValue8;
	}

	/**
	 * Stores shared model.
	 */
	public static final Model sharedModel = new Model();
	/**
	 * Stores shared vertices x.
	 */
	private static int sharedVerticesX[] = new int[2000];
	/**
	 * Stores shared vertices y.
	 */
	private static int sharedVerticesY[] = new int[2000];
	/**
	 * Stores shared vertices z.
	 */
	private static int sharedVerticesZ[] = new int[2000];
	/**
	 * Stores shared triangle alpha.
	 */
	private static int sharedTriangleAlpha[] = new int[2000];
	/**
	 * Number of vertex entries.
	 */
	public int vertexCount;
	/**
	 * Stores vertices x.
	 */
	public int verticesX[];
	/**
	 * Stores vertices y.
	 */
	public int verticesY[];
	/**
	 * Stores vertices z.
	 */
	public int verticesZ[];
	/**
	 * Number of triangle entries.
	 */
	public int triangleCount;
	/**
	 * Stores triangle vertex a.
	 */
	public int triangleVertexA[];
	/**
	 * Stores triangle vertex b.
	 */
	public int triangleVertexB[];
	/**
	 * Stores triangle vertex c.
	 */
	public int triangleVertexC[];
	/**
	 * Stores triangle shade a.
	 */
	public int triangleShadeA[];
	/**
	 * Stores triangle shade b.
	 */
	public int triangleShadeB[];
	/**
	 * Stores triangle shade c.
	 */
	public int triangleShadeC[];
	/**
	 * Stores triangle draw type.
	 */
	public int triangleDrawType[];
	/**
	 * Stores triangle priorities.
	 */
	public int trianglePriorities[];
	/**
	 * Stores triangle alpha.
	 */
	public int triangleAlpha[];
	/**
	 * Stores triangle colors.
	 */
	public int triangleColors[];
	/**
	 * Stores default triangle priority.
	 */
	public int defaultTrianglePriority;
	/**
	 * Number of textured triangle entries.
	 */
	public int texturedTriangleCount;
	/**
	 * Stores textured triangle a.
	 */
	public int texturedTriangleA[];
	/**
	 * Stores textured triangle b.
	 */
	public int texturedTriangleB[];
	/**
	 * Stores textured triangle c.
	 */
	public int texturedTriangleC[];
	/**
	 * Packed deferred-lighting state: ambient in the high 16 bits, scaled contrast
	 * in the low 16 bits.
	 */
	public int lightingState;
	/** Minimum x in the high 16 bits and maximum x in the low 16 bits. */
	public int packedXBounds;
	/**
	 * Maximum z in the high 16 bits and minimum z in the low 16 bits, matching the
	 * original packing.
	 */
	public int packedZBounds;
	/**
	 * Stores horizontal radius.
	 */
	public int horizontalRadius;
	/**
	 * Stores max y.
	 */
	public int maxY;
	/**
	 * Stores depth span.
	 */
	public int depthSpan;
	/**
	 * Stores radius.
	 */
	public int radius;
	/**
	 * Scene support height used when stacking ground-item piles on top of models.
	 */
	public int itemDropHeight;
	/**
	 * Stores vertex skins.
	 */
	public int vertexSkins[];
	/**
	 * Stores triangle skins.
	 */
	public int triangleSkins[];
	/**
	 * Stores vertex groups.
	 */
	public int vertexGroups[][];
	/**
	 * Stores triangle groups.
	 */
	public int triangleGroups[][];
	/**
	 * Whether single tile.
	 */
	public boolean singleTile;
	/**
	 * Stores vertex normal offsets.
	 */
	public VertexNormal vertexNormalOffsets[];
	/**
	 * Stores model headers.
	 */
	private static ModelHeader modelHeaders[];
	/**
	 * Stores model provider.
	 */
	private static OnDemandProvider modelProvider;
	/**
	 * Stores face out of bounds.
	 */
	private static boolean faceOutOfBounds[] = new boolean[4096];
	/**
	 * Stores face near clipped.
	 */
	private static boolean faceNearClipped[] = new boolean[4096];
	/**
	 * Stores projected x.
	 */
	private static int projectedX[] = new int[4096];
	/**
	 * Stores projected y.
	 */
	private static int projectedY[] = new int[4096];
	/**
	 * Stores projected depth.
	 */
	private static int projectedDepth[] = new int[4096];
	/**
	 * Stores camera x.
	 */
	private static int cameraX[] = new int[4096];
	/**
	 * Stores camera y.
	 */
	private static int cameraY[] = new int[4096];
	/**
	 * Stores camera z.
	 */
	private static int cameraZ[] = new int[4096];
	/**
	 * Stores depth bucket counts.
	 */
	private static int depthBucketCounts[] = new int[1500];
	/**
	 * Stores depth buckets.
	 */
	private static int depthBuckets[][] = new int[1500][512];
	/**
	 * Stores priority bucket counts.
	 */
	private static int priorityBucketCounts[] = new int[12];
	/**
	 * Stores priority buckets.
	 */
	private static int priorityBuckets[][] = new int[12][2000];
	/**
	 * Stores priority10 depths.
	 */
	private static int priority10Depths[] = new int[2000];
	/**
	 * Stores priority11 depths.
	 */
	private static int priority11Depths[] = new int[2000];
	/**
	 * Stores priority depth sums.
	 */
	private static int priorityDepthSums[] = new int[12];
	/**
	 * Stores clipped x.
	 */
	private static int clippedX[] = new int[10];
	/**
	 * Stores clipped y.
	 */
	private static int clippedY[] = new int[10];
	/**
	 * Stores clipped shade.
	 */
	private static int clippedShade[] = new int[10];
	/**
	 * Stores transform pivot x.
	 */
	private static int transformPivotX;
	/**
	 * Stores transform pivot y.
	 */
	private static int transformPivotY;
	/**
	 * Stores transform pivot z.
	 */
	private static int transformPivotZ;
	/**
	 * Whether picking enabled.
	 */
	public static boolean pickingEnabled;
	/**
	 * Stores mouse x.
	 */
	public static int mouseX;
	/**
	 * Stores mouse y.
	 */
	public static int mouseY;
	/**
	 * Number of picked entries.
	 */
	public static int pickedCount;
	/**
	 * Stores picked uids.
	 */
	public static int pickedUids[] = new int[1000];
	/**
	 * Stores sine.
	 */
	public static int SINE[];
	/**
	 * Stores cosine.
	 */
	public static int COSINE[];
	/**
	 * Stores hsl to rgb.
	 */
	private static int HSL_TO_RGB[];
	/**
	 * Stores reciprocal 16.
	 */
	private static int RECIPROCAL_16[];

	static {
		SINE = Rasterizer3D.SINE;
		COSINE = Rasterizer3D.COSINE;
		HSL_TO_RGB = Rasterizer3D.HSL_TO_RGB;
		RECIPROCAL_16 = Rasterizer3D.reciprocal16;
	}
}
