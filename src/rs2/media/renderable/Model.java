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

	/** Releases the model-header table and shared projection work arrays. */
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

	public static void initializeModelHeaders(int modelCount, OnDemandProvider provider) {
		modelHeaders = new ModelHeader[modelCount];
		modelProvider = provider;
	}

	/**
	 * Parses the 18-byte legacy model footer into stream offsets without decoding
	 * geometry.
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
		int zDataLength = footer.readUnsignedShort();
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

	public static void clearModelHeader(int modelId) {
		modelHeaders[modelId] = null;
	}

	public static Model getModel(int modelId) {
		if (modelHeaders == null)
			return null;
		ModelHeader class26 = modelHeaders[modelId];
		if (class26 == null) {
			modelProvider.requestModel(modelId);
			return null;
		} else {
			return new Model(modelId);
		}
	}

	public static boolean isLoaded(int modelId) {
		if (modelHeaders == null)
			return false;
		ModelHeader class26 = modelHeaders[modelId];
		if (class26 == null) {
			modelProvider.requestModel(modelId);
			return false;
		} else {
			return true;
		}
	}

	private Model() {
		singleTile = false;
	}

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
		int a = 0;
		int b = 0;
		int c = 0;
		int last = 0;
		for (int triangle = 0; triangle < triangleCount; triangle++) {
			int type = triangleTypes.readUnsignedByte();
			if (type == 1) {
				a = triangleData.readSignedSmart() + last;
				last = a;
				b = triangleData.readSignedSmart() + last;
				last = b;
				c = triangleData.readSignedSmart() + last;
				last = c;
			} else if (type == 2) {
				b = c;
				c = triangleData.readSignedSmart() + last;
				last = c;
			} else if (type == 3) {
				a = c;
				c = triangleData.readSignedSmart() + last;
				last = c;
			} else if (type == 4) {
				int swap = a;
				a = b;
				b = swap;
				c = triangleData.readSignedSmart() + last;
				last = c;
			}
			triangleVertexA[triangle] = a;
			triangleVertexB[triangle] = b;
			triangleVertexC[triangle] = c;
		}

		Buffer texturedTriangles = new Buffer(header.modelData);
		texturedTriangles.position = header.uvMapTriangleOffset;
		for (int triangle = 0; triangle < texturedTriangleCount; triangle++) {
			texturedTriangleA[triangle] = texturedTriangles.readUnsignedShort();
			texturedTriangleB[triangle] = texturedTriangles.readUnsignedShort();
			texturedTriangleC[triangle] = texturedTriangles.readUnsignedShort();
		}
	}

	public Model(int modelCount, Model[] models) {
		singleTile = false;
		boolean flag = false;
		boolean flag1 = false;
		boolean flag2 = false;
		boolean flag3 = false;
		vertexCount = 0;
		triangleCount = 0;
		texturedTriangleCount = 0;
		defaultTrianglePriority = -1;
		for (int j = 0; j < modelCount; j++) {
			Model class50_sub1_sub4_sub4 = models[j];
			if (class50_sub1_sub4_sub4 != null) {
				vertexCount += class50_sub1_sub4_sub4.vertexCount;
				triangleCount += class50_sub1_sub4_sub4.triangleCount;
				texturedTriangleCount += class50_sub1_sub4_sub4.texturedTriangleCount;
				flag |= class50_sub1_sub4_sub4.triangleDrawType != null;
				if (class50_sub1_sub4_sub4.trianglePriorities != null) {
					flag1 = true;
				} else {
					if (defaultTrianglePriority == -1)
						defaultTrianglePriority = class50_sub1_sub4_sub4.defaultTrianglePriority;
					if (defaultTrianglePriority != class50_sub1_sub4_sub4.defaultTrianglePriority)
						flag1 = true;
				}
				flag2 |= class50_sub1_sub4_sub4.triangleAlpha != null;
				flag3 |= class50_sub1_sub4_sub4.triangleSkins != null;
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
		if (flag)
			triangleDrawType = new int[triangleCount];
		if (flag1)
			trianglePriorities = new int[triangleCount];
		if (flag2)
			triangleAlpha = new int[triangleCount];
		if (flag3)
			triangleSkins = new int[triangleCount];
		triangleColors = new int[triangleCount];
		vertexCount = 0;
		triangleCount = 0;
		texturedTriangleCount = 0;
		int k = 0;
		for (int l = 0; l < modelCount; l++) {
			Model class50_sub1_sub4_sub4_1 = models[l];
			if (class50_sub1_sub4_sub4_1 != null) {
				for (int i1 = 0; i1 < class50_sub1_sub4_sub4_1.triangleCount; i1++) {
					if (flag)
						if (class50_sub1_sub4_sub4_1.triangleDrawType == null) {
							triangleDrawType[triangleCount] = 0;
						} else {
							int j1 = class50_sub1_sub4_sub4_1.triangleDrawType[i1];
							if ((j1 & 2) == 2)
								j1 += k << 2;
							triangleDrawType[triangleCount] = j1;
						}
					if (flag1)
						if (class50_sub1_sub4_sub4_1.trianglePriorities == null)
							trianglePriorities[triangleCount] = class50_sub1_sub4_sub4_1.defaultTrianglePriority;
						else
							trianglePriorities[triangleCount] = class50_sub1_sub4_sub4_1.trianglePriorities[i1];
					if (flag2)
						if (class50_sub1_sub4_sub4_1.triangleAlpha == null)
							triangleAlpha[triangleCount] = 0;
						else
							triangleAlpha[triangleCount] = class50_sub1_sub4_sub4_1.triangleAlpha[i1];
					if (flag3 && class50_sub1_sub4_sub4_1.triangleSkins != null)
						triangleSkins[triangleCount] = class50_sub1_sub4_sub4_1.triangleSkins[i1];
					triangleColors[triangleCount] = class50_sub1_sub4_sub4_1.triangleColors[i1];
					triangleVertexA[triangleCount] = getFirstIdenticalVertexId(class50_sub1_sub4_sub4_1,
							class50_sub1_sub4_sub4_1.triangleVertexA[i1]);
					triangleVertexB[triangleCount] = getFirstIdenticalVertexId(class50_sub1_sub4_sub4_1,
							class50_sub1_sub4_sub4_1.triangleVertexB[i1]);
					triangleVertexC[triangleCount] = getFirstIdenticalVertexId(class50_sub1_sub4_sub4_1,
							class50_sub1_sub4_sub4_1.triangleVertexC[i1]);
					triangleCount++;
				}

				for (int k1 = 0; k1 < class50_sub1_sub4_sub4_1.texturedTriangleCount; k1++) {
					texturedTriangleA[texturedTriangleCount] = getFirstIdenticalVertexId(class50_sub1_sub4_sub4_1,
							class50_sub1_sub4_sub4_1.texturedTriangleA[k1]);
					texturedTriangleB[texturedTriangleCount] = getFirstIdenticalVertexId(class50_sub1_sub4_sub4_1,
							class50_sub1_sub4_sub4_1.texturedTriangleB[k1]);
					texturedTriangleC[texturedTriangleCount] = getFirstIdenticalVertexId(class50_sub1_sub4_sub4_1,
							class50_sub1_sub4_sub4_1.texturedTriangleC[k1]);
					texturedTriangleCount++;
				}

				k += class50_sub1_sub4_sub4_1.texturedTriangleCount;
			}
		}

	}

	public Model(Model[] models, int modelCount) {
		singleTile = false;
		boolean flag1 = false;
		boolean flag2 = false;
		boolean flag3 = false;
		boolean flag4 = false;
		vertexCount = 0;
		triangleCount = 0;
		texturedTriangleCount = 0;
		defaultTrianglePriority = -1;
		for (int k = 0; k < modelCount; k++) {
			Model class50_sub1_sub4_sub4 = models[k];
			if (class50_sub1_sub4_sub4 != null) {
				vertexCount += class50_sub1_sub4_sub4.vertexCount;
				triangleCount += class50_sub1_sub4_sub4.triangleCount;
				texturedTriangleCount += class50_sub1_sub4_sub4.texturedTriangleCount;
				flag1 |= class50_sub1_sub4_sub4.triangleDrawType != null;
				if (class50_sub1_sub4_sub4.trianglePriorities != null) {
					flag2 = true;
				} else {
					if (defaultTrianglePriority == -1)
						defaultTrianglePriority = class50_sub1_sub4_sub4.defaultTrianglePriority;
					if (defaultTrianglePriority != class50_sub1_sub4_sub4.defaultTrianglePriority)
						flag2 = true;
				}
				flag3 |= class50_sub1_sub4_sub4.triangleAlpha != null;
				flag4 |= class50_sub1_sub4_sub4.triangleColors != null;
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
		if (flag1)
			triangleDrawType = new int[triangleCount];
		if (flag2)
			trianglePriorities = new int[triangleCount];
		if (flag3)
			triangleAlpha = new int[triangleCount];
		if (flag4)
			triangleColors = new int[triangleCount];
		vertexCount = 0;
		triangleCount = 0;
		texturedTriangleCount = 0;
		int l = 0;
		for (int i1 = 0; i1 < modelCount; i1++) {
			Model class50_sub1_sub4_sub4_1 = models[i1];
			if (class50_sub1_sub4_sub4_1 != null) {
				int j1 = vertexCount;
				for (int k1 = 0; k1 < class50_sub1_sub4_sub4_1.vertexCount; k1++) {
					verticesX[vertexCount] = class50_sub1_sub4_sub4_1.verticesX[k1];
					verticesY[vertexCount] = class50_sub1_sub4_sub4_1.verticesY[k1];
					verticesZ[vertexCount] = class50_sub1_sub4_sub4_1.verticesZ[k1];
					vertexCount++;
				}

				for (int l1 = 0; l1 < class50_sub1_sub4_sub4_1.triangleCount; l1++) {
					triangleVertexA[triangleCount] = class50_sub1_sub4_sub4_1.triangleVertexA[l1] + j1;
					triangleVertexB[triangleCount] = class50_sub1_sub4_sub4_1.triangleVertexB[l1] + j1;
					triangleVertexC[triangleCount] = class50_sub1_sub4_sub4_1.triangleVertexC[l1] + j1;
					triangleShadeA[triangleCount] = class50_sub1_sub4_sub4_1.triangleShadeA[l1];
					triangleShadeB[triangleCount] = class50_sub1_sub4_sub4_1.triangleShadeB[l1];
					triangleShadeC[triangleCount] = class50_sub1_sub4_sub4_1.triangleShadeC[l1];
					if (flag1)
						if (class50_sub1_sub4_sub4_1.triangleDrawType == null) {
							triangleDrawType[triangleCount] = 0;
						} else {
							int i2 = class50_sub1_sub4_sub4_1.triangleDrawType[l1];
							if ((i2 & 2) == 2)
								i2 += l << 2;
							triangleDrawType[triangleCount] = i2;
						}
					if (flag2)
						if (class50_sub1_sub4_sub4_1.trianglePriorities == null)
							trianglePriorities[triangleCount] = class50_sub1_sub4_sub4_1.defaultTrianglePriority;
						else
							trianglePriorities[triangleCount] = class50_sub1_sub4_sub4_1.trianglePriorities[l1];
					if (flag3)
						if (class50_sub1_sub4_sub4_1.triangleAlpha == null)
							triangleAlpha[triangleCount] = 0;
						else
							triangleAlpha[triangleCount] = class50_sub1_sub4_sub4_1.triangleAlpha[l1];
					if (flag4 && class50_sub1_sub4_sub4_1.triangleColors != null)
						triangleColors[triangleCount] = class50_sub1_sub4_sub4_1.triangleColors[l1];
					triangleCount++;
				}

				for (int j2 = 0; j2 < class50_sub1_sub4_sub4_1.texturedTriangleCount; j2++) {
					texturedTriangleA[texturedTriangleCount] = class50_sub1_sub4_sub4_1.texturedTriangleA[j2] + j1;
					texturedTriangleB[texturedTriangleCount] = class50_sub1_sub4_sub4_1.texturedTriangleB[j2] + j1;
					texturedTriangleC[texturedTriangleCount] = class50_sub1_sub4_sub4_1.texturedTriangleC[j2] + j1;
					texturedTriangleCount++;
				}

				l += class50_sub1_sub4_sub4_1.texturedTriangleCount;
			}
		}

		calculateDiagonals();
	}

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
			for (int i = 0; i < vertexCount; i++) {
				verticesX[i] = source.verticesX[i];
				verticesY[i] = source.verticesY[i];
				verticesZ[i] = source.verticesZ[i];
			}

		}
		if (shareColors) {
			triangleColors = source.triangleColors;
		} else {
			triangleColors = new int[triangleCount];
			for (int j = 0; j < triangleCount; j++)
				triangleColors[j] = source.triangleColors[j];

		}
		if (shareAlpha) {
			triangleAlpha = source.triangleAlpha;
		} else {
			triangleAlpha = new int[triangleCount];
			if (source.triangleAlpha == null) {
				for (int k = 0; k < triangleCount; k++)
					triangleAlpha[k] = 0;

			} else {
				for (int l = 0; l < triangleCount; l++)
					triangleAlpha[l] = source.triangleAlpha[l];

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

	public Model(Model source, boolean copyVerticesY, boolean copyLighting) {
		singleTile = false;
		vertexCount = source.vertexCount;
		triangleCount = source.triangleCount;
		texturedTriangleCount = source.texturedTriangleCount;
		if (copyVerticesY) {
			verticesY = new int[vertexCount];
			for (int j = 0; j < vertexCount; j++)
				verticesY[j] = source.verticesY[j];

		} else {
			verticesY = source.verticesY;
		}
		if (copyLighting) {
			triangleShadeA = new int[triangleCount];
			triangleShadeB = new int[triangleCount];
			triangleShadeC = new int[triangleCount];
			for (int k = 0; k < triangleCount; k++) {
				triangleShadeA[k] = source.triangleShadeA[k];
				triangleShadeB[k] = source.triangleShadeB[k];
				triangleShadeC[k] = source.triangleShadeC[k];
			}

			triangleDrawType = new int[triangleCount];
			if (source.triangleDrawType == null) {
				for (int l = 0; l < triangleCount; l++)
					triangleDrawType[l] = 0;

			} else {
				for (int i1 = 0; i1 < triangleCount; i1++)
					triangleDrawType[i1] = source.triangleDrawType[i1];

			}
			super.vertexNormals = new VertexNormal[vertexCount];
			for (int j1 = 0; j1 < vertexCount; j1++) {
				VertexNormal class40 = super.vertexNormals[j1] = new VertexNormal();
				VertexNormal class40_1 = ((Renderable) (source)).vertexNormals[j1];
				class40.x = class40_1.x;
				class40.y = class40_1.y;
				class40.z = class40_1.z;
				class40.magnitude = class40_1.magnitude;
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
		for (int j = 0; j < vertexCount; j++) {
			verticesX[j] = source.verticesX[j];
			verticesY[j] = source.verticesY[j];
			verticesZ[j] = source.verticesZ[j];
		}

		if (shareAlpha) {
			triangleAlpha = source.triangleAlpha;
		} else {
			if (sharedTriangleAlpha.length < triangleCount)
				sharedTriangleAlpha = new int[triangleCount + 100];
			triangleAlpha = sharedTriangleAlpha;
			if (source.triangleAlpha == null) {
				for (int k = 0; k < triangleCount; k++)
					triangleAlpha[k] = 0;

			} else {
				for (int l = 0; l < triangleCount; l++)
					triangleAlpha[l] = source.triangleAlpha[l];

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

	public void applyTransformation(int frameId) {
		if (vertexGroups == null)
			return;
		if (frameId == -1)
			return;
		AnimationFrame class21 = AnimationFrame.get(frameId);
		if (class21 == null)
			return;
		Skeleton class41 = class21.skeleton;
		transformPivotX = 0;
		transformPivotY = 0;
		transformPivotZ = 0;
		for (int j = 0; j < class21.transformCount; j++) {
			int k = class21.transformSkeletonLabels[j];
			transformFrame(class41.transformTypes[k], class41.labels[k], class21.transformXs[j], class21.transformYs[j],
					class21.transformZs[j]);
		}

	}

	public void mixAnimationFrames(int primaryFrameId, int secondaryFrameId, int[] interleaveOrder) {
		if (primaryFrameId == -1)
			return;
		if (interleaveOrder == null || secondaryFrameId == -1) {
			applyTransformation(primaryFrameId);
			return;
		}
		AnimationFrame class21 = AnimationFrame.get(primaryFrameId);
		if (class21 == null)
			return;
		AnimationFrame class21_1 = AnimationFrame.get(secondaryFrameId);
		if (class21_1 == null) {
			applyTransformation(primaryFrameId);
			return;
		}
		Skeleton class41 = class21.skeleton;
		transformPivotX = 0;
		transformPivotY = 0;
		transformPivotZ = 0;
		int l = 0;
		int i1 = interleaveOrder[l++];
		for (int j1 = 0; j1 < class21.transformCount; j1++) {
			int k1;
			for (k1 = class21.transformSkeletonLabels[j1]; k1 > i1; i1 = interleaveOrder[l++])
				;
			if (k1 != i1 || class41.transformTypes[k1] == 0)
				transformFrame(class41.transformTypes[k1], class41.labels[k1], class21.transformXs[j1],
						class21.transformYs[j1], class21.transformZs[j1]);
		}

		transformPivotX = 0;
		transformPivotY = 0;
		transformPivotZ = 0;
		l = 0;
		i1 = interleaveOrder[l++];
		for (int l1 = 0; l1 < class21_1.transformCount; l1++) {
			int i2;
			for (i2 = class21_1.transformSkeletonLabels[l1]; i2 > i1; i1 = interleaveOrder[l++])
				;
			if (i2 == i1 || class41.transformTypes[i2] == 0)
				transformFrame(class41.transformTypes[i2], class41.labels[i2], class21_1.transformXs[l1],
						class21_1.transformYs[l1], class21_1.transformZs[l1]);
		}

	}

	private void transformFrame(int transformType, int[] groups, int x, int y, int z) {
		int i1 = groups.length;
		if (transformType == 0) {
			int j1 = 0;
			transformPivotX = 0;
			transformPivotY = 0;
			transformPivotZ = 0;
			for (int k2 = 0; k2 < i1; k2++) {
				int l3 = groups[k2];
				if (l3 < vertexGroups.length) {
					int ai5[] = vertexGroups[l3];
					for (int i5 = 0; i5 < ai5.length; i5++) {
						int j6 = ai5[i5];
						transformPivotX += verticesX[j6];
						transformPivotY += verticesY[j6];
						transformPivotZ += verticesZ[j6];
						j1++;
					}

				}
			}

			if (j1 > 0) {
				transformPivotX = transformPivotX / j1 + x;
				transformPivotY = transformPivotY / j1 + y;
				transformPivotZ = transformPivotZ / j1 + z;
				return;
			} else {
				transformPivotX = x;
				transformPivotY = y;
				transformPivotZ = z;
				return;
			}
		}
		if (transformType == 1) {
			for (int k1 = 0; k1 < i1; k1++) {
				int l2 = groups[k1];
				if (l2 < vertexGroups.length) {
					int ai1[] = vertexGroups[l2];
					for (int i4 = 0; i4 < ai1.length; i4++) {
						int j5 = ai1[i4];
						verticesX[j5] += x;
						verticesY[j5] += y;
						verticesZ[j5] += z;
					}

				}
			}

			return;
		}
		if (transformType == 2) {
			for (int l1 = 0; l1 < i1; l1++) {
				int i3 = groups[l1];
				if (i3 < vertexGroups.length) {
					int ai2[] = vertexGroups[i3];
					for (int j4 = 0; j4 < ai2.length; j4++) {
						int k5 = ai2[j4];
						verticesX[k5] -= transformPivotX;
						verticesY[k5] -= transformPivotY;
						verticesZ[k5] -= transformPivotZ;
						int k6 = (x & 0xff) * 8;
						int l6 = (y & 0xff) * 8;
						int i7 = (z & 0xff) * 8;
						if (i7 != 0) {
							int j7 = SINE[i7];
							int i8 = COSINE[i7];
							int l8 = verticesY[k5] * j7 + verticesX[k5] * i8 >> 16;
							verticesY[k5] = verticesY[k5] * i8 - verticesX[k5] * j7 >> 16;
							verticesX[k5] = l8;
						}
						if (k6 != 0) {
							int k7 = SINE[k6];
							int j8 = COSINE[k6];
							int i9 = verticesY[k5] * j8 - verticesZ[k5] * k7 >> 16;
							verticesZ[k5] = verticesY[k5] * k7 + verticesZ[k5] * j8 >> 16;
							verticesY[k5] = i9;
						}
						if (l6 != 0) {
							int l7 = SINE[l6];
							int k8 = COSINE[l6];
							int j9 = verticesZ[k5] * l7 + verticesX[k5] * k8 >> 16;
							verticesZ[k5] = verticesZ[k5] * k8 - verticesX[k5] * l7 >> 16;
							verticesX[k5] = j9;
						}
						verticesX[k5] += transformPivotX;
						verticesY[k5] += transformPivotY;
						verticesZ[k5] += transformPivotZ;
					}

				}
			}

			return;
		}
		if (transformType == 3) {
			for (int i2 = 0; i2 < i1; i2++) {
				int j3 = groups[i2];
				if (j3 < vertexGroups.length) {
					int ai3[] = vertexGroups[j3];
					for (int k4 = 0; k4 < ai3.length; k4++) {
						int l5 = ai3[k4];
						verticesX[l5] -= transformPivotX;
						verticesY[l5] -= transformPivotY;
						verticesZ[l5] -= transformPivotZ;
						verticesX[l5] = (verticesX[l5] * x) / 128;
						verticesY[l5] = (verticesY[l5] * y) / 128;
						verticesZ[l5] = (verticesZ[l5] * z) / 128;
						verticesX[l5] += transformPivotX;
						verticesY[l5] += transformPivotY;
						verticesZ[l5] += transformPivotZ;
					}

				}
			}

			return;
		}
		if (transformType == 5 && triangleGroups != null && triangleAlpha != null) {
			for (int j2 = 0; j2 < i1; j2++) {
				int k3 = groups[j2];
				if (k3 < triangleGroups.length) {
					int ai4[] = triangleGroups[k3];
					for (int l4 = 0; l4 < ai4.length; l4++) {
						int i6 = ai4[l4];
						triangleAlpha[i6] += x * 8;
						if (triangleAlpha[i6] < 0)
							triangleAlpha[i6] = 0;
						if (triangleAlpha[i6] > 255)
							triangleAlpha[i6] = 255;
					}

				}
			}

		}
	}

	public void rotateY90Ccw() {
		for (int i = 0; i < vertexCount; i++) {
			int j = verticesX[i];
			verticesX[i] = verticesZ[i];
			verticesZ[i] = -j;
		}

	}

	public void rotateX(int angle) {
		int k = SINE[angle];
		int l = COSINE[angle];
		for (int i1 = 0; i1 < vertexCount; i1++) {
			int j1 = verticesY[i1] * l - verticesZ[i1] * k >> 16;
			verticesZ[i1] = verticesY[i1] * k + verticesZ[i1] * l >> 16;
			verticesY[i1] = j1;
		}
	}

	public void translate(int x, int y, int z) {
		for (int l = 0; l < vertexCount; l++) {
			verticesX[l] += x;
			verticesY[l] += y;
			verticesZ[l] += z;
		}

	}

	public void recolor(int fromColor, int toColor) {
		for (int k = 0; k < triangleCount; k++)
			if (triangleColors[k] == fromColor)
				triangleColors[k] = toColor;

	}

	public void mirror() {
		for (int k = 0; k < vertexCount; k++)
			verticesZ[k] = -verticesZ[k];

		for (int l = 0; l < triangleCount; l++) {
			int i1 = triangleVertexA[l];
			triangleVertexA[l] = triangleVertexC[l];
			triangleVertexC[l] = i1;
		}

	}

	public void scale(int xScale, int yScale, int zScale) {
		for (int i1 = 0; i1 < vertexCount; i1++) {
			verticesX[i1] = (verticesX[i1] * xScale) / 128;
			verticesY[i1] = (verticesY[i1] * yScale) / 128;
			verticesZ[i1] = (verticesZ[i1] * zScale) / 128;
		}
	}

	/**
	 * Accumulates face normals and either shades immediately or retains normals for
	 * scene merging.
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
			int a = triangleVertexA[triangle];
			int b = triangleVertexB[triangle];
			int c = triangleVertexC[triangle];
			int abX = verticesX[b] - verticesX[a];
			int abY = verticesY[b] - verticesY[a];
			int abZ = verticesZ[b] - verticesZ[a];
			int acX = verticesX[c] - verticesX[a];
			int acY = verticesY[c] - verticesY[a];
			int acZ = verticesZ[c] - verticesZ[a];
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
				VertexNormal normal = super.vertexNormals[a];
				normal.x += normalX;
				normal.y += normalY;
				normal.z += normalZ;
				normal.magnitude++;
				normal = super.vertexNormals[b];
				normal.x += normalX;
				normal.y += normalY;
				normal.z += normalZ;
				normal.magnitude++;
				normal = super.vertexNormals[c];
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

	/** Applies the stored ambient/contrast after scene normal merging. */
	public void applyDeferredLighting(int lightX, int lightY, int lightZ) {
		int ambient = lightingState >> 16;
		int contrast = (lightingState << 16) >> 16;
		handleShading(ambient, contrast, lightX, lightY, lightZ);
	}

	/**
	 * Finalizes Gouraud/flat face lightness values from the current vertex normals.
	 */
	public void handleShading(int ambient, int contrast, int lightX, int lightY, int lightZ) {
		for (int triangle = 0; triangle < triangleCount; triangle++) {
			int a = triangleVertexA[triangle];
			int b = triangleVertexB[triangle];
			int c = triangleVertexC[triangle];
			if (triangleDrawType == null) {
				int color = triangleColors[triangle];
				VertexNormal normal = super.vertexNormals[a];
				int lightness = ambient
						+ (lightX * normal.x + lightY * normal.y + lightZ * normal.z) / (contrast * normal.magnitude);
				triangleShadeA[triangle] = adjustLightness(color, lightness, 0);
				normal = super.vertexNormals[b];
				lightness = ambient
						+ (lightX * normal.x + lightY * normal.y + lightZ * normal.z) / (contrast * normal.magnitude);
				triangleShadeB[triangle] = adjustLightness(color, lightness, 0);
				normal = super.vertexNormals[c];
				lightness = ambient
						+ (lightX * normal.x + lightY * normal.y + lightZ * normal.z) / (contrast * normal.magnitude);
				triangleShadeC[triangle] = adjustLightness(color, lightness, 0);
			} else if ((triangleDrawType[triangle] & 1) == 0) {
				int color = triangleColors[triangle];
				int drawType = triangleDrawType[triangle];
				VertexNormal normal = super.vertexNormals[a];
				int lightness = ambient
						+ (lightX * normal.x + lightY * normal.y + lightZ * normal.z) / (contrast * normal.magnitude);
				triangleShadeA[triangle] = adjustLightness(color, lightness, drawType);
				normal = super.vertexNormals[b];
				lightness = ambient
						+ (lightX * normal.x + lightY * normal.y + lightZ * normal.z) / (contrast * normal.magnitude);
				triangleShadeB[triangle] = adjustLightness(color, lightness, drawType);
				normal = super.vertexNormals[c];
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

	public void renderSimple(int rotationX, int rotationY, int rotationZ, int cameraPitch, int translationX,
			int translationY, int translationZ) {
		int l1 = Rasterizer3D.centerX;
		int i2 = Rasterizer3D.centerY;
		int j2 = SINE[rotationX];
		int k2 = COSINE[rotationX];
		int l2 = SINE[rotationY];
		int i3 = COSINE[rotationY];
		int j3 = SINE[rotationZ];
		int k3 = COSINE[rotationZ];
		int l3 = SINE[cameraPitch];
		int i4 = COSINE[cameraPitch];
		int j4 = translationY * l3 + translationZ * i4 >> 16;
		for (int k4 = 0; k4 < vertexCount; k4++) {
			int l4 = verticesX[k4];
			int i5 = verticesY[k4];
			int j5 = verticesZ[k4];
			if (rotationZ != 0) {
				int k5 = i5 * j3 + l4 * k3 >> 16;
				i5 = i5 * k3 - l4 * j3 >> 16;
				l4 = k5;
			}
			if (rotationX != 0) {
				int l5 = i5 * k2 - j5 * j2 >> 16;
				j5 = i5 * j2 + j5 * k2 >> 16;
				i5 = l5;
			}
			if (rotationY != 0) {
				int i6 = j5 * l2 + l4 * i3 >> 16;
				j5 = j5 * i3 - l4 * l2 >> 16;
				l4 = i6;
			}
			l4 += translationX;
			i5 += translationY;
			j5 += translationZ;
			int j6 = i5 * i4 - j5 * l3 >> 16;
			j5 = i5 * l3 + j5 * i4 >> 16;
			i5 = j6;
			projectedDepth[k4] = j5 - j4;
			projectedX[k4] = l1 + (l4 << 9) / j5;
			projectedY[k4] = i2 + (i5 << 9) / j5;
			if (texturedTriangleCount > 0) {
				cameraX[k4] = l4;
				cameraY[k4] = i5;
				cameraZ[k4] = j5;
			}
		}

		try {
			drawFaces(false, false, 0);
			return;
		} catch (Exception _ex) {
			return;
		}
	}

	@Override
	public void draw(int orientation, int pitchSine, int pitchCosine, int yawSine, int yawCosine, int x, int y, int z,
			int uid) {
		drawInternal(orientation, pitchSine, pitchCosine, yawSine, yawCosine, x, y, z, uid);
	}

	private void drawInternal(int i, int j, int k, int l, int i1, int j1, int k1, int l1, int i2) {
		int j2 = l1 * i1 - j1 * l >> 16;
		int k2 = k1 * j + j2 * k >> 16;
		int l2 = horizontalRadius * k >> 16;
		int i3 = k2 + l2;
		if (i3 <= 50 || k2 >= 3500)
			return;
		int j3 = l1 * l + j1 * i1 >> 16;
		int k3 = j3 - horizontalRadius << 9;
		if (k3 / i3 >= Rasterizer.centerX)
			return;
		int l3 = j3 + horizontalRadius << 9;
		if (l3 / i3 <= -Rasterizer.centerX)
			return;
		int i4 = k1 * k - j2 * j >> 16;
		int j4 = horizontalRadius * j >> 16;
		int k4 = i4 + j4 << 9;
		if (k4 / i3 <= -Rasterizer.centerY)
			return;
		int l4 = j4 + (super.modelHeight * k >> 16);
		int i5 = i4 - l4 << 9;
		if (i5 / i3 >= Rasterizer.centerY)
			return;
		int j5 = l2 + (super.modelHeight * j >> 16);
		boolean flag = false;
		if (k2 - j5 <= 50)
			flag = true;
		boolean flag1 = false;
		if (i2 > 0 && pickingEnabled) {
			int k5 = k2 - l2;
			if (k5 <= 50)
				k5 = 50;
			if (j3 > 0) {
				k3 /= i3;
				l3 /= k5;
			} else {
				l3 /= i3;
				k3 /= k5;
			}
			if (i4 > 0) {
				i5 /= i3;
				k4 /= k5;
			} else {
				k4 /= i3;
				i5 /= k5;
			}
			int i6 = mouseX - Rasterizer3D.centerX;
			int k6 = mouseY - Rasterizer3D.centerY;
			if (i6 > k3 && i6 < l3 && k6 > i5 && k6 < k4)
				if (singleTile)
					pickedUids[pickedCount++] = i2;
				else
					flag1 = true;
		}
		int l5 = Rasterizer3D.centerX;
		int j6 = Rasterizer3D.centerY;
		int l6 = 0;
		int i7 = 0;
		if (i != 0) {
			l6 = SINE[i];
			i7 = COSINE[i];
		}
		for (int j7 = 0; j7 < vertexCount; j7++) {
			int k7 = verticesX[j7];
			int l7 = verticesY[j7];
			int i8 = verticesZ[j7];
			if (i != 0) {
				int j8 = i8 * l6 + k7 * i7 >> 16;
				i8 = i8 * i7 - k7 * l6 >> 16;
				k7 = j8;
			}
			k7 += j1;
			l7 += k1;
			i8 += l1;
			int k8 = i8 * l + k7 * i1 >> 16;
			i8 = i8 * i1 - k7 * l >> 16;
			k7 = k8;
			k8 = l7 * k - i8 * j >> 16;
			i8 = l7 * j + i8 * k >> 16;
			l7 = k8;
			projectedDepth[j7] = i8 - k2;
			if (i8 >= 50) {
				projectedX[j7] = l5 + (k7 << 9) / i8;
				projectedY[j7] = j6 + (l7 << 9) / i8;
			} else {
				projectedX[j7] = -5000;
				flag = true;
			}
			if (flag || texturedTriangleCount > 0) {
				cameraX[j7] = k7;
				cameraY[j7] = l7;
				cameraZ[j7] = i8;
			}
		}

		try {
			drawFaces(flag, flag1, i2);
			return;
		} catch (Exception _ex) {
			return;
		}
	}

	private void drawFaces(boolean flag, boolean flag1, int i) {
		for (int j = 0; j < depthSpan; j++)
			depthBucketCounts[j] = 0;

		for (int k = 0; k < triangleCount; k++)
			if (triangleDrawType == null || triangleDrawType[k] != -1) {
				int l = triangleVertexA[k];
				int k1 = triangleVertexB[k];
				int j2 = triangleVertexC[k];
				int i3 = projectedX[l];
				int l3 = projectedX[k1];
				int k4 = projectedX[j2];
				if (flag && (i3 == -5000 || l3 == -5000 || k4 == -5000)) {
					faceNearClipped[k] = true;
					int j5 = (projectedDepth[l] + projectedDepth[k1] + projectedDepth[j2]) / 3 + radius;
					depthBuckets[j5][depthBucketCounts[j5]++] = k;
				} else {
					if (flag1 && containsPoint(mouseX, mouseY, projectedY[l], projectedY[k1], projectedY[j2], i3, l3,
							k4)) {
						pickedUids[pickedCount++] = i;
						flag1 = false;
					}
					if ((i3 - l3) * (projectedY[j2] - projectedY[k1])
							- (projectedY[l] - projectedY[k1]) * (k4 - l3) > 0) {
						faceNearClipped[k] = false;
						if (i3 < 0 || l3 < 0 || k4 < 0 || i3 > Rasterizer.viewportRx || l3 > Rasterizer.viewportRx
								|| k4 > Rasterizer.viewportRx)
							faceOutOfBounds[k] = true;
						else
							faceOutOfBounds[k] = false;
						int k5 = (projectedDepth[l] + projectedDepth[k1] + projectedDepth[j2]) / 3 + radius;
						depthBuckets[k5][depthBucketCounts[k5]++] = k;
					}
				}
			}

		if (trianglePriorities == null) {
			for (int i1 = depthSpan - 1; i1 >= 0; i1--) {
				int l1 = depthBucketCounts[i1];
				if (l1 > 0) {
					int ai[] = depthBuckets[i1];
					for (int j3 = 0; j3 < l1; j3++)
						drawFace(ai[j3]);

				}
			}

			return;
		}
		for (int j1 = 0; j1 < 12; j1++) {
			priorityBucketCounts[j1] = 0;
			priorityDepthSums[j1] = 0;
		}

		for (int i2 = depthSpan - 1; i2 >= 0; i2--) {
			int k2 = depthBucketCounts[i2];
			if (k2 > 0) {
				int ai1[] = depthBuckets[i2];
				for (int i4 = 0; i4 < k2; i4++) {
					int l4 = ai1[i4];
					int l5 = trianglePriorities[l4];
					int j6 = priorityBucketCounts[l5]++;
					priorityBuckets[l5][j6] = l4;
					if (l5 < 10)
						priorityDepthSums[l5] += i2;
					else if (l5 == 10)
						priority10Depths[j6] = i2;
					else
						priority11Depths[j6] = i2;
				}

			}
		}

		int l2 = 0;
		if (priorityBucketCounts[1] > 0 || priorityBucketCounts[2] > 0)
			l2 = (priorityDepthSums[1] + priorityDepthSums[2]) / (priorityBucketCounts[1] + priorityBucketCounts[2]);
		int k3 = 0;
		if (priorityBucketCounts[3] > 0 || priorityBucketCounts[4] > 0)
			k3 = (priorityDepthSums[3] + priorityDepthSums[4]) / (priorityBucketCounts[3] + priorityBucketCounts[4]);
		int j4 = 0;
		if (priorityBucketCounts[6] > 0 || priorityBucketCounts[8] > 0)
			j4 = (priorityDepthSums[6] + priorityDepthSums[8]) / (priorityBucketCounts[6] + priorityBucketCounts[8]);
		int i6 = 0;
		int k6 = priorityBucketCounts[10];
		int ai2[] = priorityBuckets[10];
		int ai3[] = priority10Depths;
		if (i6 == k6) {
			i6 = 0;
			k6 = priorityBucketCounts[11];
			ai2 = priorityBuckets[11];
			ai3 = priority11Depths;
		}
		int i5;
		if (i6 < k6)
			i5 = ai3[i6];
		else
			i5 = -1000;
		for (int l6 = 0; l6 < 10; l6++) {
			while (l6 == 0 && i5 > l2) {
				drawFace(ai2[i6++]);
				if (i6 == k6 && ai2 != priorityBuckets[11]) {
					i6 = 0;
					k6 = priorityBucketCounts[11];
					ai2 = priorityBuckets[11];
					ai3 = priority11Depths;
				}
				if (i6 < k6)
					i5 = ai3[i6];
				else
					i5 = -1000;
			}
			while (l6 == 3 && i5 > k3) {
				drawFace(ai2[i6++]);
				if (i6 == k6 && ai2 != priorityBuckets[11]) {
					i6 = 0;
					k6 = priorityBucketCounts[11];
					ai2 = priorityBuckets[11];
					ai3 = priority11Depths;
				}
				if (i6 < k6)
					i5 = ai3[i6];
				else
					i5 = -1000;
			}
			while (l6 == 5 && i5 > j4) {
				drawFace(ai2[i6++]);
				if (i6 == k6 && ai2 != priorityBuckets[11]) {
					i6 = 0;
					k6 = priorityBucketCounts[11];
					ai2 = priorityBuckets[11];
					ai3 = priority11Depths;
				}
				if (i6 < k6)
					i5 = ai3[i6];
				else
					i5 = -1000;
			}
			int i7 = priorityBucketCounts[l6];
			int ai4[] = priorityBuckets[l6];
			for (int j7 = 0; j7 < i7; j7++)
				drawFace(ai4[j7]);

		}

		while (i5 != -1000) {
			drawFace(ai2[i6++]);
			if (i6 == k6 && ai2 != priorityBuckets[11]) {
				i6 = 0;
				ai2 = priorityBuckets[11];
				k6 = priorityBucketCounts[11];
				ai3 = priority11Depths;
			}
			if (i6 < k6)
				i5 = ai3[i6];
			else
				i5 = -1000;
		}
	}

	private void drawFace(int i) {
		if (faceNearClipped[i]) {
			drawNearClippedFace(i);
			return;
		}
		int j = triangleVertexA[i];
		int k = triangleVertexB[i];
		int l = triangleVertexC[i];
		Rasterizer3D.restrictEdges = faceOutOfBounds[i];
		if (triangleAlpha == null)
			Rasterizer3D.alpha = 0;
		else
			Rasterizer3D.alpha = triangleAlpha[i];
		int i1;
		if (triangleDrawType == null)
			i1 = 0;
		else
			i1 = triangleDrawType[i] & 3;
		if (i1 == 0) {
			Rasterizer3D.drawGouraudTriangle(projectedY[j], projectedY[k], projectedY[l], projectedX[j], projectedX[k],
					projectedX[l], triangleShadeA[i], triangleShadeB[i], triangleShadeC[i]);
			return;
		}
		if (i1 == 1) {
			Rasterizer3D.drawFlatTriangle(projectedY[j], projectedY[k], projectedY[l], projectedX[j], projectedX[k],
					projectedX[l], HSL_TO_RGB[triangleShadeA[i]]);
			return;
		}
		if (i1 == 2) {
			int j1 = triangleDrawType[i] >> 2;
			int l1 = texturedTriangleA[j1];
			int j2 = texturedTriangleB[j1];
			int l2 = texturedTriangleC[j1];
			Rasterizer3D.drawTexturedTriangle(projectedY[j], projectedY[k], projectedY[l], projectedX[j], projectedX[k],
					projectedX[l], triangleShadeA[i], triangleShadeB[i], triangleShadeC[i], cameraX[l1], cameraX[j2],
					cameraX[l2], cameraY[l1], cameraY[j2], cameraY[l2], cameraZ[l1], cameraZ[j2], cameraZ[l2],
					triangleColors[i]);
			return;
		}
		if (i1 == 3) {
			int k1 = triangleDrawType[i] >> 2;
			int i2 = texturedTriangleA[k1];
			int k2 = texturedTriangleB[k1];
			int i3 = texturedTriangleC[k1];
			Rasterizer3D.drawTexturedTriangle(projectedY[j], projectedY[k], projectedY[l], projectedX[j], projectedX[k],
					projectedX[l], triangleShadeA[i], triangleShadeA[i], triangleShadeA[i], cameraX[i2], cameraX[k2],
					cameraX[i3], cameraY[i2], cameraY[k2], cameraY[i3], cameraZ[i2], cameraZ[k2], cameraZ[i3],
					triangleColors[i]);
		}
	}

	private void drawNearClippedFace(int i) {
		int j = Rasterizer3D.centerX;
		int k = Rasterizer3D.centerY;
		int l = 0;
		int i1 = triangleVertexA[i];
		int j1 = triangleVertexB[i];
		int k1 = triangleVertexC[i];
		int l1 = cameraZ[i1];
		int i2 = cameraZ[j1];
		int j2 = cameraZ[k1];
		if (l1 >= 50) {
			clippedX[l] = projectedX[i1];
			clippedY[l] = projectedY[i1];
			clippedShade[l++] = triangleShadeA[i];
		} else {
			int k2 = cameraX[i1];
			int k3 = cameraY[i1];
			int k4 = triangleShadeA[i];
			if (j2 >= 50) {
				int k5 = (50 - l1) * RECIPROCAL_16[j2 - l1];
				clippedX[l] = j + (k2 + ((cameraX[k1] - k2) * k5 >> 16) << 9) / 50;
				clippedY[l] = k + (k3 + ((cameraY[k1] - k3) * k5 >> 16) << 9) / 50;
				clippedShade[l++] = k4 + ((triangleShadeC[i] - k4) * k5 >> 16);
			}
			if (i2 >= 50) {
				int l5 = (50 - l1) * RECIPROCAL_16[i2 - l1];
				clippedX[l] = j + (k2 + ((cameraX[j1] - k2) * l5 >> 16) << 9) / 50;
				clippedY[l] = k + (k3 + ((cameraY[j1] - k3) * l5 >> 16) << 9) / 50;
				clippedShade[l++] = k4 + ((triangleShadeB[i] - k4) * l5 >> 16);
			}
		}
		if (i2 >= 50) {
			clippedX[l] = projectedX[j1];
			clippedY[l] = projectedY[j1];
			clippedShade[l++] = triangleShadeB[i];
		} else {
			int l2 = cameraX[j1];
			int l3 = cameraY[j1];
			int l4 = triangleShadeB[i];
			if (l1 >= 50) {
				int i6 = (50 - i2) * RECIPROCAL_16[l1 - i2];
				clippedX[l] = j + (l2 + ((cameraX[i1] - l2) * i6 >> 16) << 9) / 50;
				clippedY[l] = k + (l3 + ((cameraY[i1] - l3) * i6 >> 16) << 9) / 50;
				clippedShade[l++] = l4 + ((triangleShadeA[i] - l4) * i6 >> 16);
			}
			if (j2 >= 50) {
				int j6 = (50 - i2) * RECIPROCAL_16[j2 - i2];
				clippedX[l] = j + (l2 + ((cameraX[k1] - l2) * j6 >> 16) << 9) / 50;
				clippedY[l] = k + (l3 + ((cameraY[k1] - l3) * j6 >> 16) << 9) / 50;
				clippedShade[l++] = l4 + ((triangleShadeC[i] - l4) * j6 >> 16);
			}
		}
		if (j2 >= 50) {
			clippedX[l] = projectedX[k1];
			clippedY[l] = projectedY[k1];
			clippedShade[l++] = triangleShadeC[i];
		} else {
			int i3 = cameraX[k1];
			int i4 = cameraY[k1];
			int i5 = triangleShadeC[i];
			if (i2 >= 50) {
				int k6 = (50 - j2) * RECIPROCAL_16[i2 - j2];
				clippedX[l] = j + (i3 + ((cameraX[j1] - i3) * k6 >> 16) << 9) / 50;
				clippedY[l] = k + (i4 + ((cameraY[j1] - i4) * k6 >> 16) << 9) / 50;
				clippedShade[l++] = i5 + ((triangleShadeB[i] - i5) * k6 >> 16);
			}
			if (l1 >= 50) {
				int l6 = (50 - j2) * RECIPROCAL_16[l1 - j2];
				clippedX[l] = j + (i3 + ((cameraX[i1] - i3) * l6 >> 16) << 9) / 50;
				clippedY[l] = k + (i4 + ((cameraY[i1] - i4) * l6 >> 16) << 9) / 50;
				clippedShade[l++] = i5 + ((triangleShadeA[i] - i5) * l6 >> 16);
			}
		}
		int j3 = clippedX[0];
		int j4 = clippedX[1];
		int j5 = clippedX[2];
		int i7 = clippedY[0];
		int j7 = clippedY[1];
		int k7 = clippedY[2];
		if ((j3 - j4) * (k7 - j7) - (i7 - j7) * (j5 - j4) > 0) {
			Rasterizer3D.restrictEdges = false;
			if (l == 3) {
				if (j3 < 0 || j4 < 0 || j5 < 0 || j3 > Rasterizer.viewportRx || j4 > Rasterizer.viewportRx
						|| j5 > Rasterizer.viewportRx)
					Rasterizer3D.restrictEdges = true;
				int l7;
				if (triangleDrawType == null)
					l7 = 0;
				else
					l7 = triangleDrawType[i] & 3;
				if (l7 == 0)
					Rasterizer3D.drawGouraudTriangle(i7, j7, k7, j3, j4, j5, clippedShade[0], clippedShade[1],
							clippedShade[2]);
				else if (l7 == 1)
					Rasterizer3D.drawFlatTriangle(i7, j7, k7, j3, j4, j5, HSL_TO_RGB[triangleShadeA[i]]);
				else if (l7 == 2) {
					int j8 = triangleDrawType[i] >> 2;
					int k9 = texturedTriangleA[j8];
					int k10 = texturedTriangleB[j8];
					int k11 = texturedTriangleC[j8];
					Rasterizer3D.drawTexturedTriangle(i7, j7, k7, j3, j4, j5, clippedShade[0], clippedShade[1],
							clippedShade[2], cameraX[k9], cameraX[k10], cameraX[k11], cameraY[k9], cameraY[k10],
							cameraY[k11], cameraZ[k9], cameraZ[k10], cameraZ[k11], triangleColors[i]);
				} else if (l7 == 3) {
					int k8 = triangleDrawType[i] >> 2;
					int l9 = texturedTriangleA[k8];
					int l10 = texturedTriangleB[k8];
					int l11 = texturedTriangleC[k8];
					Rasterizer3D.drawTexturedTriangle(i7, j7, k7, j3, j4, j5, triangleShadeA[i], triangleShadeA[i],
							triangleShadeA[i], cameraX[l9], cameraX[l10], cameraX[l11], cameraY[l9], cameraY[l10],
							cameraY[l11], cameraZ[l9], cameraZ[l10], cameraZ[l11], triangleColors[i]);
				}
			}
			if (l == 4) {
				if (j3 < 0 || j4 < 0 || j5 < 0 || j3 > Rasterizer.viewportRx || j4 > Rasterizer.viewportRx
						|| j5 > Rasterizer.viewportRx || clippedX[3] < 0 || clippedX[3] > Rasterizer.viewportRx)
					Rasterizer3D.restrictEdges = true;
				int i8;
				if (triangleDrawType == null)
					i8 = 0;
				else
					i8 = triangleDrawType[i] & 3;
				if (i8 == 0) {
					Rasterizer3D.drawGouraudTriangle(i7, j7, k7, j3, j4, j5, clippedShade[0], clippedShade[1],
							clippedShade[2]);
					Rasterizer3D.drawGouraudTriangle(i7, k7, clippedY[3], j3, j5, clippedX[3], clippedShade[0],
							clippedShade[2], clippedShade[3]);
					return;
				}
				if (i8 == 1) {
					int l8 = HSL_TO_RGB[triangleShadeA[i]];
					Rasterizer3D.drawFlatTriangle(i7, j7, k7, j3, j4, j5, l8);
					Rasterizer3D.drawFlatTriangle(i7, k7, clippedY[3], j3, j5, clippedX[3], l8);
					return;
				}
				if (i8 == 2) {
					int i9 = triangleDrawType[i] >> 2;
					int i10 = texturedTriangleA[i9];
					int i11 = texturedTriangleB[i9];
					int i12 = texturedTriangleC[i9];
					Rasterizer3D.drawTexturedTriangle(i7, j7, k7, j3, j4, j5, clippedShade[0], clippedShade[1],
							clippedShade[2], cameraX[i10], cameraX[i11], cameraX[i12], cameraY[i10], cameraY[i11],
							cameraY[i12], cameraZ[i10], cameraZ[i11], cameraZ[i12], triangleColors[i]);
					Rasterizer3D.drawTexturedTriangle(i7, k7, clippedY[3], j3, j5, clippedX[3], clippedShade[0],
							clippedShade[2], clippedShade[3], cameraX[i10], cameraX[i11], cameraX[i12], cameraY[i10],
							cameraY[i11], cameraY[i12], cameraZ[i10], cameraZ[i11], cameraZ[i12], triangleColors[i]);
					return;
				}
				if (i8 == 3) {
					int j9 = triangleDrawType[i] >> 2;
					int j10 = texturedTriangleA[j9];
					int j11 = texturedTriangleB[j9];
					int j12 = texturedTriangleC[j9];
					Rasterizer3D.drawTexturedTriangle(i7, j7, k7, j3, j4, j5, triangleShadeA[i], triangleShadeA[i],
							triangleShadeA[i], cameraX[j10], cameraX[j11], cameraX[j12], cameraY[j10], cameraY[j11],
							cameraY[j12], cameraZ[j10], cameraZ[j11], cameraZ[j12], triangleColors[i]);
					Rasterizer3D.drawTexturedTriangle(i7, k7, clippedY[3], j3, j5, clippedX[3], triangleShadeA[i],
							triangleShadeA[i], triangleShadeA[i], cameraX[j10], cameraX[j11], cameraX[j12],
							cameraY[j10], cameraY[j11], cameraY[j12], cameraZ[j10], cameraZ[j11], cameraZ[j12],
							triangleColors[i]);
				}
			}
		}
	}

	private boolean containsPoint(int i, int j, int k, int l, int i1, int j1, int k1, int l1) {
		if (j < k && j < l && j < i1)
			return false;
		if (j > k && j > l && j > i1)
			return false;
		if (i < j1 && i < k1 && i < l1)
			return false;
		return i <= j1 || i <= k1 || i <= l1;
	}

	public static final Model sharedModel = new Model();
	private static int sharedVerticesX[] = new int[2000];
	private static int sharedVerticesY[] = new int[2000];
	private static int sharedVerticesZ[] = new int[2000];
	private static int sharedTriangleAlpha[] = new int[2000];
	public int vertexCount;
	public int verticesX[];
	public int verticesY[];
	public int verticesZ[];
	public int triangleCount;
	public int triangleVertexA[];
	public int triangleVertexB[];
	public int triangleVertexC[];
	public int triangleShadeA[];
	public int triangleShadeB[];
	public int triangleShadeC[];
	public int triangleDrawType[];
	public int trianglePriorities[];
	public int triangleAlpha[];
	public int triangleColors[];
	public int defaultTrianglePriority;
	public int texturedTriangleCount;
	public int texturedTriangleA[];
	public int texturedTriangleB[];
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
	public int horizontalRadius;
	public int maxY;
	public int depthSpan;
	public int radius;
	/**
	 * Scene support height used when stacking ground-item piles on top of models.
	 */
	public int itemDropHeight;
	public int vertexSkins[];
	public int triangleSkins[];
	public int vertexGroups[][];
	public int triangleGroups[][];
	public boolean singleTile;
	public VertexNormal vertexNormalOffsets[];
	private static ModelHeader modelHeaders[];
	private static OnDemandProvider modelProvider;
	private static boolean faceOutOfBounds[] = new boolean[4096];
	private static boolean faceNearClipped[] = new boolean[4096];
	private static int projectedX[] = new int[4096];
	private static int projectedY[] = new int[4096];
	private static int projectedDepth[] = new int[4096];
	private static int cameraX[] = new int[4096];
	private static int cameraY[] = new int[4096];
	private static int cameraZ[] = new int[4096];
	private static int depthBucketCounts[] = new int[1500];
	private static int depthBuckets[][] = new int[1500][512];
	private static int priorityBucketCounts[] = new int[12];
	private static int priorityBuckets[][] = new int[12][2000];
	private static int priority10Depths[] = new int[2000];
	private static int priority11Depths[] = new int[2000];
	private static int priorityDepthSums[] = new int[12];
	private static int clippedX[] = new int[10];
	private static int clippedY[] = new int[10];
	private static int clippedShade[] = new int[10];
	private static int transformPivotX;
	private static int transformPivotY;
	private static int transformPivotZ;
	public static boolean pickingEnabled;
	public static int mouseX;
	public static int mouseY;
	public static int pickedCount;
	public static int pickedUids[] = new int[1000];
	public static int SINE[];
	public static int COSINE[];
	private static int HSL_TO_RGB[];
	private static int RECIPROCAL_16[];

	static {
		SINE = Rasterizer3D.SINE;
		COSINE = Rasterizer3D.COSINE;
		HSL_TO_RGB = Rasterizer3D.HSL_TO_RGB;
		RECIPROCAL_16 = Rasterizer3D.reciprocal16;
	}
}
