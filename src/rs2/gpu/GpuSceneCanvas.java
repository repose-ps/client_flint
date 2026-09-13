package rs2.gpu;

import static org.lwjgl.opengl.GL14C.glMultiDrawArrays;
import static org.lwjgl.opengl.GL11C.GL_POLYGON_OFFSET_FILL;
import static org.lwjgl.opengl.GL11C.glPolygonOffset;
import static org.lwjgl.opengl.GL33C.glFrontFace;
import static org.lwjgl.opengl.GL33C.GL_ARRAY_BUFFER;
import static org.lwjgl.opengl.GL33C.GL_ELEMENT_ARRAY_BUFFER;
import static org.lwjgl.opengl.GL33C.GL_COLOR_BUFFER_BIT;
import static org.lwjgl.opengl.GL33C.GL_CCW;
import static org.lwjgl.opengl.GL33C.GL_CULL_FACE;
import static org.lwjgl.opengl.GL33C.GL_CW;
import static org.lwjgl.opengl.GL33C.GL_BLEND;
import static org.lwjgl.opengl.GL33C.GL_DEPTH_BUFFER_BIT;
import static org.lwjgl.opengl.GL33C.GL_DEPTH_TEST;
import static org.lwjgl.opengl.GL33C.GL_ONE_MINUS_SRC_ALPHA;
import static org.lwjgl.opengl.GL33C.GL_SRC_ALPHA;
import static org.lwjgl.opengl.GL33C.GL_NEAREST;
import static org.lwjgl.opengl.GL33C.GL_RGBA8;
import static org.lwjgl.opengl.GL33C.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL13C.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13C.GL_TEXTURE1;
import static org.lwjgl.opengl.GL13C.GL_TEXTURE2;
import static org.lwjgl.opengl.GL13C.glActiveTexture;
import static org.lwjgl.opengl.GL33C.GL_TEXTURE_WRAP_S;
import static org.lwjgl.opengl.GL33C.GL_TEXTURE_WRAP_T;
import static org.lwjgl.opengl.GL33C.GL_CLAMP_TO_EDGE;
import static org.lwjgl.opengl.GL33C.GL_TEXTURE_MAG_FILTER;
import static org.lwjgl.opengl.GL33C.GL_TEXTURE_MIN_FILTER;
import static org.lwjgl.opengl.GL33C.GL_TRIANGLE_STRIP;
import static org.lwjgl.opengl.GL33C.GL_UNPACK_ALIGNMENT;
import static org.lwjgl.opengl.GL33C.GL_INT;
import static org.lwjgl.opengl.GL33C.GL_SHORT;
import static org.lwjgl.opengl.GL33C.GL_UNSIGNED_INT;
import static org.lwjgl.opengl.GL33C.GL_TEXTURE_2D_ARRAY;
import static org.lwjgl.opengl.GL33C.GL_TEXTURE_BUFFER;
import static org.lwjgl.opengl.GL33C.GL_RGBA32I;
import static org.lwjgl.opengl.GL33C.GL_R32UI;
import static org.lwjgl.opengl.GL33C.glTexBuffer;
import static org.lwjgl.opengl.GL33C.GL_LEQUAL;
import static org.lwjgl.opengl.GL33C.GL_RENDERER;
import static org.lwjgl.opengl.GL33C.GL_RGBA;
import static org.lwjgl.opengl.GL33C.GL_STATIC_DRAW;
import static org.lwjgl.opengl.GL33C.GL_DYNAMIC_DRAW;
import static org.lwjgl.opengl.GL33C.GL_TRIANGLES;
import static org.lwjgl.opengl.GL33C.GL_UNSIGNED_BYTE;
import static org.lwjgl.opengl.GL33C.GL_VENDOR;
import static org.lwjgl.opengl.GL33C.GL_VERSION;
import static org.lwjgl.opengl.GL33C.glBindBuffer;
import static org.lwjgl.opengl.GL33C.glBlendFunc;
import static org.lwjgl.opengl.GL33C.glBindVertexArray;
import static org.lwjgl.opengl.GL33C.glBufferData;
import static org.lwjgl.opengl.GL33C.glClear;
import static org.lwjgl.opengl.GL33C.glClearColor;
import static org.lwjgl.opengl.GL33C.glColorMask;
import static org.lwjgl.opengl.GL33C.glDepthFunc;
import static org.lwjgl.opengl.GL33C.glDepthMask;
import static org.lwjgl.opengl.GL33C.glDisable;
import static org.lwjgl.opengl.GL33C.glDrawArrays;
import static org.lwjgl.opengl.GL33C.glDrawElements;
import static org.lwjgl.opengl.GL33C.glEnable;
import static org.lwjgl.opengl.GL33C.glEnableVertexAttribArray;
import static org.lwjgl.opengl.GL33C.glGenBuffers;
import static org.lwjgl.opengl.GL33C.glGenTextures;
import static org.lwjgl.opengl.GL33C.glGenVertexArrays;
import static org.lwjgl.opengl.GL33C.glGetString;
import static org.lwjgl.opengl.GL33C.glGetUniformLocation;
import static org.lwjgl.opengl.GL33C.glReadPixels;
import static org.lwjgl.opengl.GL33C.glPixelStorei;
import static org.lwjgl.opengl.GL33C.glTexImage2D;
import static org.lwjgl.opengl.GL33C.glTexParameteri;
import static org.lwjgl.opengl.GL33C.glTexSubImage2D;
import static org.lwjgl.opengl.GL33C.glUniform1f;
import static org.lwjgl.opengl.GL33C.glUniform1i;
import static org.lwjgl.opengl.GL33C.glUniform2f;
import static org.lwjgl.opengl.GL33C.glUniform2i;
import static org.lwjgl.opengl.GL33C.glUniform3i;
import static org.lwjgl.opengl.GL33C.glUniform4f;
import static org.lwjgl.opengl.GL33C.glUseProgram;
import static org.lwjgl.opengl.GL33C.glBindTexture;
import static org.lwjgl.opengl.GL33C.glVertexAttribIPointer;
import static org.lwjgl.opengl.GL33C.glVertexAttribI4i;
import static org.lwjgl.opengl.GL33C.glVertexAttrib2f;
import static org.lwjgl.opengl.GL33C.glVertexAttribPointer;
import static org.lwjgl.opengl.GL33C.glViewport;

import java.awt.Point;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.awt.AWTGLCanvas;
import org.lwjgl.opengl.awt.GLData;

import rs2.game.render.SoftwareViewportOverlay;
import rs2.game.render.WorldRenderFrame;
import rs2.media.Rasterizer3D;
import rs2.media.model.Model;
import rs2.scene.Scene;

/**
 * OpenGL surface that renders complete loaded terrain and static scene
 * geometry.
 */
final class GpuSceneCanvas extends AWTGLCanvas {

	private static final long serialVersionUID = 1L;
	private static final int CLEAR_RED = 9;
	private static final int CLEAR_GREEN = 11;
	private static final int CLEAR_BLUE = 15;
	private static final int RENDER_PLANE_VARIANTS = 4;
	private static final int WALL_PRIORITY_METADATA_TAG = 0x40;
	private static final int ROOF_OBJECT_PRIORITY_METADATA_TAG = 0xc0;
	private static final boolean PERF_STATS = Boolean.getBoolean("flint.gpu.perfStats");
	private static final boolean WALL_DECORATION_DEBUG = Boolean.getBoolean("flint.gpu.wallDecorationDebug");
	private static final boolean STATIC_TEXTURE_GEOMETRY_DEBUG = Boolean
			.getBoolean("flint.gpu.staticTextureGeometryDebug");
	private static final boolean STATIC_MODEL_DEBUG = Boolean.getBoolean("flint.gpu.staticModelDebug");
	private static final boolean STATIC_PRIORITY_DEPTH_DEBUG = Boolean.getBoolean("flint.gpu.staticPriorityDepthDebug");
	private static final boolean STATIC_PRIORITY_FLOOR_ONLY_DEBUG = Boolean
			.getBoolean("flint.gpu.staticPriorityFloorOnlyDebug");
	private static final boolean STATIC_PRIORITY_WALL_DECORATION_DEBUG = Boolean
			.getBoolean("flint.gpu.staticPriorityWallDecorationDebug");
	private static final boolean STATIC_PRIORITY_WALL_PAINTER_DEBUG = Boolean
			.getBoolean("flint.gpu.staticPriorityWallPainterDebug");
	private static final boolean STATIC_DEPTH_WALL_PAINTER_DEBUG = Boolean
			.getBoolean("flint.gpu.staticDepthWallPainterDebug");
	private static final boolean STATIC_PRIORITY_WALL_PAINTER_IGNORE_SCENE_DEPTH_DEBUG = Boolean
			.getBoolean("flint.gpu.staticPriorityWallPainterIgnoreSceneDepthDebug");
	private static final boolean STATIC_PRIORITY_WALL_PAINTER_DEPTH_BIAS_DEBUG = Boolean
			.getBoolean("flint.gpu.staticPriorityWallPainterDepthBiasDebug");
	private static final float STATIC_PRIORITY_WALL_PAINTER_DEPTH_BIAS_FACTOR = staticWallPainterDepthBias(
			"flint.gpu.staticPriorityWallPainterDepthBiasFactor", -1.0f);
	private static final float STATIC_PRIORITY_WALL_PAINTER_DEPTH_BIAS_UNITS = staticWallPainterDepthBias(
			"flint.gpu.staticPriorityWallPainterDepthBiasUnits", -1.0f);
	private static final boolean STATIC_ALPHA_BLEND_DEBUG = Boolean.getBoolean("flint.gpu.staticAlphaBlendDebug");
	private static final float STATIC_ALPHA_BLEND_DEPTH_BIAS_FACTOR = staticWallPainterDepthBias(
			"flint.gpu.staticAlphaBlendDepthBiasFactor", -1.0f);
	private static final float STATIC_ALPHA_BLEND_DEPTH_BIAS_UNITS = staticWallPainterDepthBias(
			"flint.gpu.staticAlphaBlendDepthBiasUnits", -1.0f);
	private static final boolean STATIC_BACKFACE_CULL_DEBUG = Boolean.getBoolean("flint.gpu.staticBackfaceCullDebug");
	private static final boolean STATIC_LEGACY_PIXEL_SNAP_DEBUG = Boolean
			.getBoolean("flint.gpu.staticLegacyPixelSnapDebug");
	private static final boolean STATIC_LEGACY_FACE_CULL_DEBUG = Boolean
			.getBoolean("flint.gpu.staticLegacyFaceCullDebug");
	private static final boolean RENDER_PLANE_PAINTER_DEBUG = Boolean.getBoolean("flint.gpu.renderPlanePainterDebug");
	/**
	 * Small clip-space nudge used only for terrain on higher legacy render planes.
	 *
	 * <p>Revision 377 can paint a roof/floor after a slightly nearer lower-plane
	 * wall because Scene tile order, not camera-space Z alone, decides that overlap.
	 * Clearing the whole depth buffer between planes reproduced that roof case but
	 * incorrectly let unrelated upper floors and tree tops punch through walls.
	 * Keeping real depth and nudging only terrain preserves both behaviours.
	 */
	private static final float RENDER_PLANE_TERRAIN_DEPTH_BIAS = renderPlaneTerrainDepthBias();
	private static final float STATIC_ROOF_OBJECT_PLANE_DEPTH_BIAS = staticRoofObjectPlaneDepthBias();
	private static final float STATIC_FLOOR_DECORATION_DEPTH_BIAS = staticFloorDecorationDepthBias();
	private static final float STATIC_PRIORITY_DEPTH_SCALE = staticPriorityDepthScale();
	private static final long PERF_STATS_INTERVAL_NANOS = 5_000_000_000L;
	private static final long WALL_DECORATION_DEBUG_INTERVAL_NANOS = 1_000_000_000L;
	private static final long STATIC_MODEL_DEBUG_INTERVAL_NANOS = 750_000_000L;
	private static final int INITIAL_UPLOAD_SCRATCH_BYTES = 4 * 1024 * 1024;

	private static final String VERTEX_SHADER = """
			#version 330 core
			layout (location = 0) in ivec3 aPosition;
			layout (location = 1) in vec4 aColor;
			layout (location = 4) in uvec4 aColorBytes;
			layout (location = 2) in vec2 aTextureCoordinateFixed;
			layout (location = 3) in uint aMaterial;

			uniform ivec3 uCamera;
			uniform ivec2 uYawSinCos;
			uniform ivec2 uPitchSinCos;
			uniform vec2 uViewport;
			uniform isamplerBuffer uModelTextureMappings;
			uniform int uModelTextureProjection;
			uniform float uStaticPriorityDepthScale;
			uniform int uStaticPriorityFloorOnly;
			uniform int uStaticPriorityWallDecoration;
			uniform int uStaticLegacyPixelSnapDebug;
			uniform float uRenderPlaneTerrainDepthBias;
			uniform float uStaticRoofObjectPlaneDepthBias;
			uniform float uStaticFloorDecorationDepthBias;

			noperspective out vec3 vVertexColor;
			noperspective out float vVertexAlpha;
			noperspective out float vLegacyHslShade;
			out vec2 vTextureCoordinate;
			flat out uint vMaterialId;
			flat out int vTextureProjectionMode;
			flat out vec3 vTextureMapA;
			flat out vec3 vTextureMapB;
			flat out vec3 vTextureMapC;
			flat out ivec3 vLegacyCameraPosition;

			ivec3 cameraSpace(ivec3 worldPosition) {
			    ivec3 relative = worldPosition - uCamera;
			    int viewX = (relative.z * uYawSinCos.x + relative.x * uYawSinCos.y) >> 16;
			    int yawDepth = (relative.z * uYawSinCos.y - relative.x * uYawSinCos.x) >> 16;
			    int viewY = (relative.y * uPitchSinCos.y - yawDepth * uPitchSinCos.x) >> 16;
			    int depth = (relative.y * uPitchSinCos.x + yawDepth * uPitchSinCos.y) >> 16;
			    return ivec3(viewX, viewY, depth);
			}

			void main() {
			    ivec3 cameraPosition = cameraSpace(aPosition);
			    int viewX = cameraPosition.x;
			    int viewY = cameraPosition.y;
			    int depth = cameraPosition.z;

			    const float nearClip = 50.0;
			    const float farClip = 25000.0;
			    float viewDepth = float(depth);
			    float clipZ = ((farClip + nearClip) / (farClip - nearClip)) * viewDepth
			            - ((2.0 * farClip * nearClip) / (farClip - nearClip));

			    // Diagnostic for legacy Model triangle priorities on static models.
			    // GpuModelUploader stores 0..11 in the otherwise-unused alpha byte only
			    // for models with explicit per-face priorities; ordinary static/terrain
			    // vertices retain alpha=255.
			    int priorityMetadata = int(round(aColor.a * 255.0));
			    bool floorPriority = priorityMetadata >= 0x80 && priorityMetadata <= 0x8b;
			    bool wallDecorationPriority = priorityMetadata >= 0x40 && priorityMetadata <= 0x4b;
			    bool legacyGouraud = priorityMetadata >= 0x20 && priorityMetadata <= 0x2b;
			    bool staticRoofObject = priorityMetadata >= 0x10 && priorityMetadata <= 0x13;
			    int legacyPriority = floorPriority ? priorityMetadata - 0x80
			            : (wallDecorationPriority ? priorityMetadata - 0x40
			                    : (legacyGouraud ? priorityMetadata - 0x20 : priorityMetadata));
			    bool categoryFilterActive = uStaticPriorityFloorOnly != 0 || uStaticPriorityWallDecoration != 0;
			    bool priorityClassAccepted = !categoryFilterActive
			            || (uStaticPriorityFloorOnly != 0 && floorPriority)
			            || (uStaticPriorityWallDecoration != 0 && wallDecorationPriority);
			    if (uStaticPriorityDepthScale > 0.0 && priorityClassAccepted
			            && legacyPriority >= 1 && legacyPriority <= 11) {
			        clipZ -= float(legacyPriority) * uStaticPriorityDepthScale;
			    }
			    // Scene.renderTile() paints floor decorations after the tile surface. A tiny
			    // class-level nudge reproduces that relationship without changing unrelated
			    // static models or globally clearing depth.
			    if (floorPriority) {
			        clipZ -= uStaticFloorDecorationDepthBias;
			    }
			    // Opaque roof models without explicit face priorities stay in the immutable
			    // static mesh. Their metadata stores the minimum render plane in the low two
			    // bits so they retain the same narrowly-scoped cross-plane preference as the
			    // deferred roof painter without paying per-object painter cost.
			    if (staticRoofObject) {
			        clipZ -= float(priorityMetadata - 0x10) * uStaticRoofObjectPlaneDepthBias;
			    }
			    // Preserve the real depth buffer between legacy render planes, but give only
			    // higher-plane terrain a small painter-style nudge. This is reset before
			    // static models are submitted so trees/walls retain ordinary scene depth.
			    clipZ -= uRenderPlaneTerrainDepthBias;

			    float clipX = float(viewX) * (1024.0 / uViewport.x);
			    float clipY = -float(viewY) * (1024.0 / uViewport.y);
			    if (uStaticLegacyPixelSnapDebug != 0 && depth > 0) {
			        // Model.drawFaces() performs its facing/degeneracy test after integer
			        // projection: center + (view << 9) / depth. Reproduce those exact
			        // integer vertex positions for this diagnostic so zero-area/sliver
			        // faces collapse exactly as they do in the 377 software renderer.
			        int projectedX = (viewX << 9) / depth;
			        int projectedY = (viewY << 9) / depth;
			        clipX = float(projectedX) * (2.0 / uViewport.x) * viewDepth;
			        clipY = -float(projectedY) * (2.0 / uViewport.y) * viewDepth;
			    }

			    gl_Position = vec4(clipX, clipY, clipZ, viewDepth);
			    vVertexColor = aColor.rgb;
			    vVertexAlpha = aColor.a;
			    vLegacyHslShade = legacyGouraud
			            ? float((int(aColorBytes.r) << 8) | int(aColorBytes.g))
			            : 0.0;
			    vTextureCoordinate = aTextureCoordinateFixed / 256.0;
			    vTextureProjectionMode = 0;
			    vLegacyCameraPosition = cameraPosition;

			    if (uModelTextureProjection != 0) {
			        int mappingIndex = (gl_VertexID / 3) * 3;
			        ivec4 mapA = texelFetch(uModelTextureMappings, mappingIndex);
			        ivec4 mapB = texelFetch(uModelTextureMappings, mappingIndex + 1);
			        ivec4 mapC = texelFetch(uModelTextureMappings, mappingIndex + 2);
			        bool unifiedSolid = uModelTextureProjection == 2 && mapA.w < 0;
			        if (unifiedSolid) {
			            vTextureMapA = vec3(0.0);
			            vTextureMapB = vec3(0.0);
			            vTextureMapC = vec3(0.0);
			            vMaterialId = 0u;
			        } else {
			            vTextureProjectionMode = 1;
			            vTextureMapA = vec3(cameraSpace(mapA.xyz));
			            vTextureMapB = vec3(cameraSpace(mapB.xyz));
			            vTextureMapC = vec3(cameraSpace(mapC.xyz));
			            vMaterialId = uint(max(0, mapA.w) + 1);
			        }
			    } else {
			        vTextureMapA = vec3(0.0);
			        vTextureMapB = vec3(0.0);
			        vTextureMapC = vec3(0.0);
			        vMaterialId = aMaterial & 255u;
			    }
			}
			""";

	private static final String GEOMETRY_SHADER = """
			#version 330 core
			layout (triangles) in;
			layout (triangle_strip, max_vertices = 3) out;

			uniform int uStaticLegacyFaceCullDebug;

			noperspective in vec3 vVertexColor[];
			noperspective in float vVertexAlpha[];
			noperspective in float vLegacyHslShade[];
			in vec2 vTextureCoordinate[];
			flat in uint vMaterialId[];
			flat in int vTextureProjectionMode[];
			flat in vec3 vTextureMapA[];
			flat in vec3 vTextureMapB[];
			flat in vec3 vTextureMapC[];
			flat in ivec3 vLegacyCameraPosition[];

			noperspective out vec3 vertexColor;
			noperspective out float vertexAlpha;
			noperspective out float legacyHslShade;
			out vec2 textureCoordinate;
			flat out uint materialId;
			flat out int textureProjectionMode;
			flat out vec3 textureMapA;
			flat out vec3 textureMapB;
			flat out vec3 textureMapC;

			bool legacyFrontFacing() {
			    // Revision 377 performs its face rejection after integer projection.
			    // The screen center cancels from the signed-area expression, so the
			    // projected offsets alone reproduce Model.drawFaces() exactly for
			    // triangles wholly in front of the 50-unit near plane. Near-clipped
			    // faces are left to the normal OpenGL clipper, matching the existing
			    // GPU path until we add explicit legacy near-plane clipping.
			    if (vLegacyCameraPosition[0].z < 50
			            || vLegacyCameraPosition[1].z < 50
			            || vLegacyCameraPosition[2].z < 50) {
			        return true;
			    }
			    ivec2 a = ivec2((vLegacyCameraPosition[0].x << 9) / vLegacyCameraPosition[0].z,
			            (vLegacyCameraPosition[0].y << 9) / vLegacyCameraPosition[0].z);
			    ivec2 b = ivec2((vLegacyCameraPosition[1].x << 9) / vLegacyCameraPosition[1].z,
			            (vLegacyCameraPosition[1].y << 9) / vLegacyCameraPosition[1].z);
			    ivec2 c = ivec2((vLegacyCameraPosition[2].x << 9) / vLegacyCameraPosition[2].z,
			            (vLegacyCameraPosition[2].y << 9) / vLegacyCameraPosition[2].z);
			    int signedArea = (a.x - b.x) * (c.y - b.y) - (a.y - b.y) * (c.x - b.x);
			    return signedArea > 0;
			}

			void main() {
			    if (uStaticLegacyFaceCullDebug != 0 && !legacyFrontFacing()) {
			        return;
			    }

			    for (int i = 0; i < 3; i++) {
			        gl_Position = gl_in[i].gl_Position;
			        vertexColor = vVertexColor[i];
			        vertexAlpha = vVertexAlpha[i];
			        legacyHslShade = vLegacyHslShade[i];
			        textureCoordinate = vTextureCoordinate[i];
			        materialId = vMaterialId[i];
			        textureProjectionMode = vTextureProjectionMode[i];
			        textureMapA = vTextureMapA[i];
			        textureMapB = vTextureMapB[i];
			        textureMapC = vTextureMapC[i];
			        EmitVertex();
			    }
			    EndPrimitive();
			}
			""";

	private static final String FRAGMENT_SHADER = """
			#version 330 core
			uniform sampler2DArray uTextures;
			uniform usamplerBuffer uLegacyHslPalette;
			uniform vec2 uViewport;
			uniform int uStaticTextureGeometryDebug;
			uniform int uLegacySolidAlphaBlend;
			noperspective in vec3 vertexColor;
			noperspective in float vertexAlpha;
			noperspective in float legacyHslShade;
			in vec2 textureCoordinate;
			flat in uint materialId;
			flat in int textureProjectionMode;
			flat in vec3 textureMapA;
			flat in vec3 textureMapB;
			flat in vec3 textureMapC;
			out vec4 fragmentColor;

			float classicTextureBrightness(float shadeValue) {
			    int shade = clamp(int(round(shadeValue * 255.0)), 0, 127);
			    int page = (shade >> 4) & 3;
			    float pageScale = page == 0 ? 1.0 : page == 1 ? 0.875 : page == 2 ? 0.75 : 0.625;
			    return (shade >= 64) ? pageScale * 0.5 : pageScale;
			}

			// Match Rasterizer3D.drawTexturedTriangle() directly. The software
			// rasterizer evaluates two projective cofactors and a shared
			// denominator against the camera ray for each screen pixel. The
			// resulting ratios are the texture-reference coordinates where
			// A=(0,0), B=(1,0), C=(0,1).
			vec2 projectedModelTextureCoordinate() {
			    // gl_FragCoord is bottom-left / half-pixel based. Convert it to the
			    // legacy integer top-left raster coordinate before constructing the
			    // same (x-centerX, y-centerY, 512) camera ray used by 377.
			    float softwareX = floor(gl_FragCoord.x);
			    float softwareY = floor(uViewport.y - gl_FragCoord.y);
			    vec2 center = floor(uViewport * 0.5);
			    vec3 ray = vec3(softwareX - center.x, softwareY - center.y, 512.0);

			    vec3 uCofactor = cross(textureMapC, textureMapA);
			    vec3 vCofactor = cross(textureMapA, textureMapB);
			    vec3 denominatorCofactor = cross(textureMapB - textureMapA, textureMapC - textureMapA);
			    float denominator = dot(denominatorCofactor, ray);
			    if (abs(denominator) < 0.000001) {
			        return vec2(0.0);
			    }
			    return vec2(dot(uCofactor, ray), dot(vCofactor, ray)) / denominator;
			}

			void main() {
			    if (textureProjectionMode != 0 && uStaticTextureGeometryDebug != 0) {
			        fragmentColor = vec4(0.0, 1.0, 1.0, 1.0);
			        return;
			    }
			    if (materialId == 0u) {
			        float sourceAlpha = 1.0;
			        if (uLegacySolidAlphaBlend != 0) {
			            // Revision 377 stores destination weight in triangleAlpha.
			            // Its solid rasterizers use (256-alpha)/256 for the source.
			            float legacyAlpha = round(vertexAlpha * 255.0);
			            sourceAlpha = (256.0 - legacyAlpha) / 256.0;
			        }
			        int metadata = int(round(vertexAlpha * 255.0));
			        if (metadata >= 0x20 && metadata <= 0x2b) {
			            int shade = clamp(int(round(legacyHslShade)), 0, 65535);
			            uint packedRgb = texelFetch(uLegacyHslPalette, shade).r;
			            vec3 paletteRgb = vec3(float((packedRgb >> 16) & 255u),
			                    float((packedRgb >> 8) & 255u), float(packedRgb & 255u)) / 255.0;
			            fragmentColor = vec4(paletteRgb, sourceAlpha);
			            return;
			        }
			        fragmentColor = vec4(vertexColor, sourceAlpha);
			        return;
			    }

			    vec2 uv = textureProjectionMode != 0 ? projectedModelTextureCoordinate() : textureCoordinate;
			    vec4 texel = texture(uTextures, vec3(uv, float(materialId - 1u)));
			    if (texel.a < 0.5) {
			        discard;
			    }
			    float brightness = classicTextureBrightness(vertexColor.r);
			    fragmentColor = vec4(texel.rgb * brightness, 1.0);
			}
			""";

	private static final String OVERLAY_VERTEX_SHADER = """
			#version 330 core
			uniform vec4 uRect;
			uniform vec2 uViewport;
			out vec2 textureCoordinate;

			const vec2 corners[4] = vec2[](
			        vec2(0.0, 0.0),
			        vec2(0.0, 1.0),
			        vec2(1.0, 0.0),
			        vec2(1.0, 1.0));

			void main() {
			    vec2 corner = corners[gl_VertexID];
			    vec2 pixel = uRect.xy + corner * uRect.zw;
			    gl_Position = vec4(
			            pixel.x * (2.0 / uViewport.x) - 1.0,
			            1.0 - pixel.y * (2.0 / uViewport.y),
			            0.0,
			            1.0);
			    textureCoordinate = corner;
			}
			""";

	private static final String OVERLAY_FRAGMENT_SHADER = """
			#version 330 core
			uniform sampler2D uOverlay;
			in vec2 textureCoordinate;
			out vec4 fragmentColor;

			void main() {
			    fragmentColor = texture(uOverlay, textureCoordinate);
			}
			""";

	private volatile WorldRenderFrame frameData;
	private volatile SoftwareViewportOverlay viewportOverlay;
	private GpuShaderProgram shader;
	private GpuShaderProgram overlayShader;
	private GpuTextureManager textureManager;
	private int overlayVertexArray;
	private int overlayTexture;
	private int overlayTextureWidth;
	private int overlayTextureHeight;
	private int overlayRectUniform;
	private int overlayViewportUniform;
	private int overlaySamplerUniform;
	private ByteBuffer overlayUploadScratch = BufferUtils.createByteBuffer(64 * 1024);
	private int terrainVertexArray;
	private int terrainVertexBuffer;
	private int terrainVertexCount;
	private GpuSceneChunk[] terrainChunks;
	private final int[] terrainEligibleVertices = new int[RENDER_PLANE_VARIANTS];
	private int staticVertexArray;
	private int staticVertexBuffer;
	private int staticVertexCount;
	private GpuSceneChunk[] staticChunks;
	private int staticTexturedVertexArray;
	private int staticTexturedVertexBuffer;
	private int staticTexturedVertexCount;
	private GpuSceneChunk[] staticTexturedChunks;
	private GpuStaticSceneMesh.WallPriorityDecoration[] staticWallPriorityDecorations = new GpuStaticSceneMesh.WallPriorityDecoration[0];
	private GpuStaticSceneMesh.InteractivePriorityObject[] staticInteractivePriorityObjects = new GpuStaticSceneMesh.InteractivePriorityObject[0];
	private GpuStaticSceneMesh.AlphaPriorityObject[] staticAlphaPriorityObjects = new GpuStaticSceneMesh.AlphaPriorityObject[0];
	private int wallPriorityVertexArray;
	private int wallPriorityVertexBuffer;
	private int wallPriorityTexturedVertexArray;
	private int wallPriorityTexturedVertexBuffer;
	private int wallPriorityIndexBuffer;
	private int wallPriorityTextureMappingBuffer;
	private int wallPriorityTextureMappingTexture;
	private final IdentityHashMap<Object, CachedPainterGeometry> cachedPainterGeometry = new IdentityHashMap<>();
	private final ArrayList<PainterCommandSpan> interactivePriorityFrameSpans = new ArrayList<>();
	private final ArrayList<PainterBatch> interactivePriorityFrameBatches = new ArrayList<>();
	private int[] wallPriorityFrameIndices = new int[0];
	private int wallPriorityFrameIndexCount;
	private int painterScreenMinX;
	private int painterScreenMinY;
	private int painterScreenMaxX;
	private int painterScreenMaxY;
	private boolean painterScreenBatchSafe;
	private int[] wallSortProjectedX = new int[0];
	private int[] wallSortProjectedY = new int[0];
	private int[] wallSortProjectedDepth = new int[0];
	private int[] wallSortFaceDepth = new int[0];
	private int[] wallSortFaceOrder = new int[0];
	private int[] wallSortDepthBucketCounts = new int[0];
	private int[] wallSortDepthBucketOffsets = new int[0];
	private final int[][] wallSortPriorityFaces = new int[12][0];
	private final int[] wallSortPriorityCounts = new int[12];
	private final int[] wallSortPriorityDepthSums = new int[12];
	private int[] wallSortPriority10Depths = new int[0];
	private int[] wallSortPriority11Depths = new int[0];
	private int staticTextureMappingBuffer;
	private int staticTextureMappingTexture;
	private int legacyHslPaletteBuffer;
	private int legacyHslPaletteTexture;
	private final int[] staticEligibleVertices = new int[RENDER_PLANE_VARIANTS];
	private int cachedSceneBytes;
	private ByteBuffer uploadScratch = createUploadScratch(INITIAL_UPLOAD_SCRATCH_BYTES);
	private IntBuffer uploadScratchWords = uploadScratch.asIntBuffer();
	private IntBuffer multiDrawFirsts = BufferUtils.createIntBuffer(1024);
	private IntBuffer multiDrawCounts = BufferUtils.createIntBuffer(1024);
	private int lastDrawVisibleRanges;
	private int lastDrawEligibleRanges;
	private int lastDrawCommands;
	private int lastDrawSubmittedVertices;
	private int frameTerrainVisible;
	private int frameTerrainEligible;
	private int frameTerrainCommands;
	private int frameStaticVisible;
	private int frameStaticEligible;
	private int frameStaticCommands;
	private int frameSubmittedVertices;
	private int frameEligibleVertices;
	private long perfWindowStarted;
	private long wallDecorationDebugNextNanos;
	private long staticModelDebugNextNanos;
	private long perfSubmittedVertices;
	private long perfEligibleVertices;
	private long perfCpuRenderNanos;
	private long perfSwapNanos;
	private int perfFrames;
	private int perfLastTerrainVisible;
	private int perfLastTerrainEligible;
	private int perfLastTerrainCommands;
	private int perfLastStaticVisible;
	private int perfLastStaticEligible;
	private int perfLastStaticCommands;
	private int perfLastWidth;
	private int perfLastHeight;
	private final GpuFrameTimer gpuFrameTimer = PERF_STATS ? new GpuFrameTimer() : null;
	private int cameraUniform;
	private int yawUniform;
	private int pitchUniform;
	private int viewportUniform;
	private int textureSamplerUniform;
	private int modelTextureMappingSamplerUniform;
	private int legacyHslPaletteSamplerUniform;
	private int modelTextureProjectionUniform;
	private int staticTextureGeometryDebugUniform;
	private int legacySolidAlphaBlendUniform;
	private int staticPriorityDepthScaleUniform;
	private int staticPriorityFloorOnlyUniform;
	private int staticPriorityWallDecorationUniform;
	private int staticLegacyPixelSnapDebugUniform;
	private int staticLegacyFaceCullDebugUniform;
	private int renderPlaneTerrainDepthBiasUniform;
	private int staticRoofObjectPlaneDepthBiasUniform;
	private int staticFloorDecorationDepthBiasUniform;
	private Scene uploadedScene;
	private long uploadedGeometryRevision = Long.MIN_VALUE;
	private int uploadedPaletteRevision = Integer.MIN_VALUE;
	private int uploadedMinPlane = Integer.MIN_VALUE;
	private boolean sceneUploaded;
	private volatile boolean initialized;
	private volatile boolean validationRequested;
	private volatile boolean validationPassed;
	private volatile boolean greenTextureValidationRequested;
	private volatile boolean staticTexturePatternValidationRequested;

	GpuSceneCanvas() {
		super(createGlData());
		setIgnoreRepaint(true);
		setFocusable(true);
	}

	private static float staticWallPainterDepthBias(String property, float fallback) {
		String raw = System.getProperty(property, Float.toString(fallback));
		try {
			float value = Float.parseFloat(raw);
			return Float.isFinite(value) ? value : fallback;
		} catch (NumberFormatException ignored) {
			return fallback;
		}
	}

	private static float renderPlaneTerrainDepthBias() {
		String raw = System.getProperty("flint.gpu.renderPlaneTerrainDepthBias", "1.0");
		try {
			float value = Float.parseFloat(raw);
			return Float.isFinite(value) && value >= 0.0f ? value : 1.0f;
		} catch (NumberFormatException ignored) {
			return 1.0f;
		}
	}

	private static float staticRoofObjectPlaneDepthBias() {
		String raw = System.getProperty("flint.gpu.staticRoofObjectPlaneDepthBias",
				Float.toString(RENDER_PLANE_TERRAIN_DEPTH_BIAS));
		try {
			float value = Float.parseFloat(raw);
			return Float.isFinite(value) && value >= 0.0f ? value : RENDER_PLANE_TERRAIN_DEPTH_BIAS;
		} catch (NumberFormatException ignored) {
			return RENDER_PLANE_TERRAIN_DEPTH_BIAS;
		}
	}

	private static float staticFloorDecorationDepthBias() {
		String raw = System.getProperty("flint.gpu.staticFloorDecorationDepthBias", "1.0");
		try {
			float value = Float.parseFloat(raw);
			return Float.isFinite(value) && value >= 0.0f ? value : 1.0f;
		} catch (NumberFormatException ignored) {
			return 1.0f;
		}
	}

	private static float staticPriorityDepthScale() {
		String raw = System.getProperty("flint.gpu.staticPriorityDepthScale", "4.0");
		try {
			float value = Float.parseFloat(raw);
			return Float.isFinite(value) && value >= 0.0f ? value : 4.0f;
		} catch (NumberFormatException ignored) {
			return 4.0f;
		}
	}

	private static GLData createGlData() {
		GLData data = new GLData();
		data.majorVersion = 3;
		data.minorVersion = 3;
		data.profile = GLData.Profile.CORE;
		data.forwardCompatible = true;
		return data;
	}

	private static ByteBuffer createUploadScratch(int bytes) {
		return BufferUtils.createByteBuffer(bytes).order(ByteOrder.nativeOrder());
	}

	void setFrameData(WorldRenderFrame frameData) {
		this.frameData = frameData;
	}

	void setViewportOverlay(SoftwareViewportOverlay viewportOverlay) {
		this.viewportOverlay = viewportOverlay;
	}

	@Override
	public void initGL() {
		GL.createCapabilities();
		if (!GL.getCapabilities().OpenGL33) {
			throw new IllegalStateException("Flint GPU rendering requires OpenGL 3.3 or newer.");
		}

		shader = GpuShaderProgram.compile(VERTEX_SHADER, GEOMETRY_SHADER, FRAGMENT_SHADER);
		cameraUniform = requiredUniform(shader, "uCamera");
		yawUniform = requiredUniform(shader, "uYawSinCos");
		pitchUniform = requiredUniform(shader, "uPitchSinCos");
		viewportUniform = requiredUniform(shader, "uViewport");
		textureSamplerUniform = requiredUniform(shader, "uTextures");
		modelTextureMappingSamplerUniform = requiredUniform(shader, "uModelTextureMappings");
		legacyHslPaletteSamplerUniform = requiredUniform(shader, "uLegacyHslPalette");
		modelTextureProjectionUniform = requiredUniform(shader, "uModelTextureProjection");
		staticTextureGeometryDebugUniform = requiredUniform(shader, "uStaticTextureGeometryDebug");
		legacySolidAlphaBlendUniform = requiredUniform(shader, "uLegacySolidAlphaBlend");
		staticPriorityDepthScaleUniform = requiredUniform(shader, "uStaticPriorityDepthScale");
		staticPriorityFloorOnlyUniform = requiredUniform(shader, "uStaticPriorityFloorOnly");
		staticPriorityWallDecorationUniform = requiredUniform(shader, "uStaticPriorityWallDecoration");
		staticLegacyPixelSnapDebugUniform = requiredUniform(shader, "uStaticLegacyPixelSnapDebug");
		staticLegacyFaceCullDebugUniform = requiredUniform(shader, "uStaticLegacyFaceCullDebug");
		renderPlaneTerrainDepthBiasUniform = requiredUniform(shader, "uRenderPlaneTerrainDepthBias");
		staticRoofObjectPlaneDepthBiasUniform = requiredUniform(shader, "uStaticRoofObjectPlaneDepthBias");
		staticFloorDecorationDepthBiasUniform = requiredUniform(shader, "uStaticFloorDecorationDepthBias");

		textureManager = new GpuTextureManager();
		textureManager.initialize();

		overlayShader = GpuShaderProgram.compile(OVERLAY_VERTEX_SHADER, OVERLAY_FRAGMENT_SHADER);
		overlayRectUniform = requiredUniform(overlayShader, "uRect");
		overlayViewportUniform = requiredUniform(overlayShader, "uViewport");
		overlaySamplerUniform = requiredUniform(overlayShader, "uOverlay");
		overlayVertexArray = glGenVertexArrays();
		overlayTexture = glGenTextures();
		glBindTexture(GL_TEXTURE_2D, overlayTexture);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
		glBindTexture(GL_TEXTURE_2D, 0);

		terrainVertexArray = glGenVertexArrays();
		terrainVertexBuffer = glGenBuffers();
		configureTerrainVertexArray(terrainVertexArray, terrainVertexBuffer);
		staticVertexArray = glGenVertexArrays();
		staticVertexBuffer = glGenBuffers();
		configureStaticVertexArray(staticVertexArray, staticVertexBuffer);
		staticTexturedVertexArray = glGenVertexArrays();
		staticTexturedVertexBuffer = glGenBuffers();
		configureStaticVertexArray(staticTexturedVertexArray, staticTexturedVertexBuffer);

		wallPriorityVertexArray = glGenVertexArrays();
		wallPriorityVertexBuffer = glGenBuffers();
		configureStaticVertexArray(wallPriorityVertexArray, wallPriorityVertexBuffer);
		wallPriorityTexturedVertexArray = glGenVertexArrays();
		wallPriorityTexturedVertexBuffer = glGenBuffers();
		configureStaticVertexArray(wallPriorityTexturedVertexArray, wallPriorityTexturedVertexBuffer);
		wallPriorityIndexBuffer = glGenBuffers();
		glBindVertexArray(wallPriorityTexturedVertexArray);
		glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, wallPriorityIndexBuffer);
		glBufferData(GL_ELEMENT_ARRAY_BUFFER, 0L, GL_DYNAMIC_DRAW);
		glBindVertexArray(0);
		wallPriorityTextureMappingBuffer = glGenBuffers();
		wallPriorityTextureMappingTexture = glGenTextures();
		glBindBuffer(GL_TEXTURE_BUFFER, wallPriorityTextureMappingBuffer);
		glBufferData(GL_TEXTURE_BUFFER, 0L, GL_DYNAMIC_DRAW);
		glBindTexture(GL_TEXTURE_BUFFER, wallPriorityTextureMappingTexture);
		glTexBuffer(GL_TEXTURE_BUFFER, GL_RGBA32I, wallPriorityTextureMappingBuffer);
		glBindTexture(GL_TEXTURE_BUFFER, 0);
		glBindBuffer(GL_TEXTURE_BUFFER, 0);

		staticTextureMappingBuffer = glGenBuffers();
		staticTextureMappingTexture = glGenTextures();
		glBindBuffer(GL_TEXTURE_BUFFER, staticTextureMappingBuffer);
		glBufferData(GL_TEXTURE_BUFFER, 0L, GL_STATIC_DRAW);
		glBindTexture(GL_TEXTURE_BUFFER, staticTextureMappingTexture);
		glTexBuffer(GL_TEXTURE_BUFFER, GL_RGBA32I, staticTextureMappingBuffer);
		glBindTexture(GL_TEXTURE_BUFFER, 0);
		glBindBuffer(GL_TEXTURE_BUFFER, 0);

		legacyHslPaletteBuffer = glGenBuffers();
		legacyHslPaletteTexture = glGenTextures();
		glBindBuffer(GL_TEXTURE_BUFFER, legacyHslPaletteBuffer);
		glBufferData(GL_TEXTURE_BUFFER, 0L, GL_STATIC_DRAW);
		glBindTexture(GL_TEXTURE_BUFFER, legacyHslPaletteTexture);
		glTexBuffer(GL_TEXTURE_BUFFER, GL_R32UI, legacyHslPaletteBuffer);
		glBindTexture(GL_TEXTURE_BUFFER, 0);
		glBindBuffer(GL_TEXTURE_BUFFER, 0);
		glVertexAttrib2f(2, 0.0f, 0.0f);
		glVertexAttribI4i(3, 0, 0, 0, 0);

		glEnable(GL_DEPTH_TEST);
		glDepthFunc(GL_LEQUAL);
		glClearColor(CLEAR_RED / 255.0f, CLEAR_GREEN / 255.0f, CLEAR_BLUE / 255.0f, 1.0f);
		if (gpuFrameTimer != null) {
			gpuFrameTimer.initialize();
		}
		initialized = true;
		System.out.println("GPU renderer initialized: " + glGetString(GL_VENDOR) + " / " + glGetString(GL_RENDERER)
				+ " / OpenGL " + glGetString(GL_VERSION));
		System.out.printf("GPU textures resident: %d/50 layers, %.1f MiB texture array%n",
				textureManager.residentLayers(), textureManager.memoryMiB());
	}

	@Override
	public void paintGL() {
		long cpuStarted = PERF_STATS ? System.nanoTime() : 0L;
		int width = Math.max(1, getFramebufferWidth());
		int height = Math.max(1, getFramebufferHeight());
		WorldRenderFrame frame = frameData;
		if (frame != null) {
			ensureSceneUploaded(frame);
		}
		if (textureManager != null) {
			textureManager.updateResidentTextures();
		}

		if (gpuFrameTimer != null) {
			gpuFrameTimer.beginFrame();
		}
		glViewport(0, 0, width, height);
		glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
		if (gpuFrameTimer != null) {
			gpuFrameTimer.markClearDone();
		}

		if (frame != null) {
			drawScene(frame, width, height);
		} else {
			clearFrameStats();
			if (gpuFrameTimer != null) {
				gpuFrameTimer.markTerrainDone();
				gpuFrameTimer.markStaticDone();
			}
		}
		drawViewportOverlay(viewportOverlay, width, height);
		if (gpuFrameTimer != null) {
			gpuFrameTimer.endFrame();
		}

		if (validationRequested) {
			if (staticTexturePatternValidationRequested) {
				validationPassed = framebufferContainsStaticTexturePattern(width, height);
			} else if (greenTextureValidationRequested) {
				validationPassed = framebufferContainsGreenTexture(width, height);
			} else {
				validationPassed = framebufferContainsScene(width, height);
			}
			validationRequested = false;
			greenTextureValidationRequested = false;
			staticTexturePatternValidationRequested = false;
		}

		long swapStarted = PERF_STATS ? System.nanoTime() : 0L;
		swapBuffers();
		if (PERF_STATS) {
			long finished = System.nanoTime();
			perfLastWidth = width;
			perfLastHeight = height;
			recordPerformance(swapStarted - cpuStarted, finished - swapStarted);
		}
	}

	private void configureTerrainVertexArray(int vertexArray, int vertexBuffer) {
		glBindVertexArray(vertexArray);
		glBindBuffer(GL_ARRAY_BUFFER, vertexBuffer);
		glBufferData(GL_ARRAY_BUFFER, 0L, GL_STATIC_DRAW);
		int stride = GpuTerrainMesh.BYTES_PER_VERTEX;
		glVertexAttribIPointer(0, 3, GL_INT, stride, 0L);
		glEnableVertexAttribArray(0);
		glVertexAttribPointer(1, 4, GL_UNSIGNED_BYTE, true, stride, GpuTerrainVertexBuilder.COLOR_BYTE_OFFSET);
		glEnableVertexAttribArray(1);
		glVertexAttribIPointer(4, 4, GL_UNSIGNED_BYTE, stride, GpuTerrainVertexBuilder.COLOR_BYTE_OFFSET);
		glEnableVertexAttribArray(4);
		glVertexAttribPointer(2, 2, GL_SHORT, false, stride, GpuTerrainVertexBuilder.UV_BYTE_OFFSET);
		glEnableVertexAttribArray(2);
		glVertexAttribIPointer(3, 1, GL_UNSIGNED_INT, stride, GpuTerrainVertexBuilder.MATERIAL_BYTE_OFFSET);
		glEnableVertexAttribArray(3);
		glBindBuffer(GL_ARRAY_BUFFER, 0);
		glBindVertexArray(0);
	}

	private void configureStaticVertexArray(int vertexArray, int vertexBuffer) {
		glBindVertexArray(vertexArray);
		glBindBuffer(GL_ARRAY_BUFFER, vertexBuffer);
		glBufferData(GL_ARRAY_BUFFER, 0L, GL_STATIC_DRAW);
		int stride = GpuVertexBuilder.BYTES_PER_VERTEX;
		glVertexAttribIPointer(0, 3, GL_INT, stride, 0L);
		glEnableVertexAttribArray(0);
		glVertexAttribPointer(1, 4, GL_UNSIGNED_BYTE, true, stride, GpuVertexBuilder.COLOR_BYTE_OFFSET);
		glEnableVertexAttribArray(1);
		glVertexAttribIPointer(4, 4, GL_UNSIGNED_BYTE, stride, GpuVertexBuilder.COLOR_BYTE_OFFSET);
		glEnableVertexAttribArray(4);
		glBindBuffer(GL_ARRAY_BUFFER, 0);
		glBindVertexArray(0);
	}

	private void ensureSceneUploaded(WorldRenderFrame frame) {
		Scene scene = frame.scene();
		long geometryRevision = scene.geometryRevision();
		int paletteRevision = Rasterizer3D.paletteRevision();
		if (scene != uploadedScene || geometryRevision != uploadedGeometryRevision
				|| paletteRevision != uploadedPaletteRevision || scene.minPlane != uploadedMinPlane) {
			sceneUploaded = false;
			uploadedScene = scene;
			uploadedGeometryRevision = geometryRevision;
			uploadedPaletteRevision = paletteRevision;
			uploadedMinPlane = scene.minPlane;
		}
		if (sceneUploaded) {
			return;
		}

		long buildStarted = System.nanoTime();
		GpuTerrainMesh terrain = GpuSceneUploader.buildTerrain(scene);
		GpuStaticSceneMesh staticScene = GpuSceneUploader.buildStaticGeometry(scene);
		long built = System.nanoTime();

		uploadPackedVertices(terrainVertexBuffer, terrain.vertices());
		terrainVertexCount = terrain.vertexCount();
		terrainChunks = terrain.chunks();
		fillEligibleVertexCounts(terrainChunks, terrainEligibleVertices);

		uploadPackedVertices(staticVertexBuffer, staticScene.vertices());
		staticVertexCount = staticScene.untexturedVertexCount();
		staticChunks = staticScene.chunks();
		fillEligibleVertexCounts(staticChunks, staticEligibleVertices);

		uploadPackedVertices(staticTexturedVertexBuffer, staticScene.texturedVertices());
		staticTexturedVertexCount = staticScene.texturedVertexCount();
		staticTexturedChunks = staticScene.texturedChunks();
		staticWallPriorityDecorations = staticScene.wallPriorityDecorations();
		staticInteractivePriorityObjects = staticScene.interactivePriorityObjects();
		staticAlphaPriorityObjects = staticScene.alphaPriorityObjects();
		addEligibleVertexCounts(staticTexturedChunks, staticEligibleVertices);
		uploadTextureMappings(staticScene.textureMappings());
		int painterCacheBytes = uploadDeferredPainterCache();
		uploadLegacyHslPalette();
		glBindBuffer(GL_ARRAY_BUFFER, 0);

		cachedSceneBytes = terrain.byteSize() + staticScene.byteSize() + painterCacheBytes;
		sceneUploaded = true;
		long uploaded = System.nanoTime();
		double buildMs = (built - buildStarted) / 1_000_000.0;
		double uploadMs = (uploaded - built) / 1_000_000.0;
		double mib = cachedSceneBytes / (1024.0 * 1024.0);
		System.out.printf(
				"GPU scene cached: %d terrain surfaces, %d terrain triangles, %d static models (%d unique), "
						+ "%d static triangles (%d textured), %d dynamic renderables deferred; %.1f MiB packed scene, "
						+ "build %.2f ms, upload %.2f ms%n",
				terrain.surfaceCount(), terrain.triangleCount(), staticScene.instanceCount(),
				staticScene.uniqueModelCount(), staticScene.triangleCount(), staticScene.texturedTriangleCount(),
				staticScene.skippedDynamicCount(), mib, buildMs, uploadMs);
	}

	private void uploadPackedVertices(int vertexBuffer, int[] words) {
		uploadPackedVertices(vertexBuffer, words, words.length, GL_STATIC_DRAW);
	}

	private void uploadPackedVertices(int vertexBuffer, int[] words, int wordCount, int usage) {
		int requiredBytes = wordCount * Integer.BYTES;
		ensureUploadScratch(requiredBytes);
		uploadScratch.clear();
		uploadScratchWords.clear();
		uploadScratchWords.put(words, 0, wordCount);
		uploadScratch.limit(requiredBytes);
		glBindBuffer(GL_ARRAY_BUFFER, vertexBuffer);
		glBufferData(GL_ARRAY_BUFFER, uploadScratch, usage);
	}

	private void uploadTextureMappings(int[] words) {
		uploadTextureMappings(staticTextureMappingBuffer, words, words.length, GL_STATIC_DRAW);
	}

	private void uploadTextureMappings(int mappingBuffer, int[] words, int wordCount, int usage) {
		int requiredBytes = wordCount * Integer.BYTES;
		ensureUploadScratch(requiredBytes);
		uploadScratch.clear();
		uploadScratchWords.clear();
		uploadScratchWords.put(words, 0, wordCount);
		uploadScratch.limit(requiredBytes);
		glBindBuffer(GL_TEXTURE_BUFFER, mappingBuffer);
		glBufferData(GL_TEXTURE_BUFFER, uploadScratch, usage);
		glBindBuffer(GL_TEXTURE_BUFFER, 0);
	}

	/**
	 * Uploads every deferred painter face once when the scene cache is rebuilt. Per-frame
	 * work then consists only of sorting faces and streaming a compact index list; static
	 * vertices and projective texture mappings never need to be rebuilt or re-uploaded.
	 */
	private int uploadDeferredPainterCache() {
		cachedPainterGeometry.clear();
		GpuVertexBuilder solidScratch = new GpuVertexBuilder(1024);
		GpuModelTextureBuilder texturedScratch = new GpuModelTextureBuilder(256);
		GpuModelTextureBuilder unified = new GpuModelTextureBuilder(4096);

		if (staticWallPriorityDecorations != null) {
			for (GpuStaticSceneMesh.WallPriorityDecoration decoration : staticWallPriorityDecorations) {
				if (decoration != null && decoration.model() != null) {
					cachePainterGeometry(decoration, decoration.model(), decoration.orientation(), decoration.worldX(),
							decoration.worldHeight(), decoration.worldY(), WALL_PRIORITY_METADATA_TAG, false, solidScratch,
							texturedScratch, unified);
				}
			}
		}
		if (staticInteractivePriorityObjects != null) {
			for (GpuStaticSceneMesh.InteractivePriorityObject object : staticInteractivePriorityObjects) {
				if (object != null && object.model() != null) {
					cachePainterGeometry(object, object.model(), object.orientation(), object.worldX(), object.worldHeight(),
							object.worldY(), object.priorityMetadataTag(), false, solidScratch, texturedScratch, unified);
				}
			}
		}
		if (staticAlphaPriorityObjects != null) {
			for (GpuStaticSceneMesh.AlphaPriorityObject object : staticAlphaPriorityObjects) {
				if (object != null && object.model() != null) {
					cachePainterGeometry(object, object.model(), object.orientation(), object.worldX(), object.worldHeight(),
							object.worldY(), 0, true, solidScratch, texturedScratch, unified);
				}
			}
		}

		int[] vertices = new int[unified.vertexWordCount()];
		unified.copyVerticesTo(vertices, 0);
		int[] mappings = new int[unified.mappingWordCount()];
		unified.copyMappingsTo(mappings, 0);
		uploadPackedVertices(wallPriorityTexturedVertexBuffer, vertices, vertices.length, GL_STATIC_DRAW);
		uploadTextureMappings(wallPriorityTextureMappingBuffer, mappings, mappings.length, GL_STATIC_DRAW);
		return (vertices.length + mappings.length) * Integer.BYTES;
	}

	private void cachePainterGeometry(Object key, Model model, int orientation, int worldX, int worldHeight, int worldY,
			int priorityMetadataTag, boolean alpha, GpuVertexBuilder solidScratch,
			GpuModelTextureBuilder texturedScratch, GpuModelTextureBuilder unified) {
		int[] faceFirstVertices = new int[model.triangleCount];
		Arrays.fill(faceFirstVertices, -1);
		solidScratch.clear();
		texturedScratch.clear();

		for (int triangle = 0; triangle < model.triangleCount; triangle++) {
			int solidFirst = solidScratch.vertexCount();
			int texturedFirst = texturedScratch.vertexCount();
			int uploadType = alpha
					? GpuModelUploader.appendLegacyAlphaFace(model, triangle, orientation, worldX, worldHeight, worldY,
							solidScratch, texturedScratch)
					: GpuModelUploader.appendFace(model, triangle, orientation, worldX, worldHeight, worldY, solidScratch,
							texturedScratch, priorityMetadataTag);
			if (uploadType == GpuModelUploader.FACE_SOLID) {
				faceFirstVertices[triangle] = unified.vertexCount();
				unified.appendSolidTriangleFrom(solidScratch, solidFirst);
			} else if (uploadType == GpuModelUploader.FACE_TEXTURED) {
				faceFirstVertices[triangle] = unified.vertexCount();
				unified.appendTexturedTriangleFrom(texturedScratch, texturedFirst);
			}
		}
		int[] sortWorldX = null;
		int[] sortWorldHeight = null;
		int[] sortWorldY = null;
		if (model.verticesX != null && model.verticesY != null && model.verticesZ != null
				&& model.verticesX.length >= model.vertexCount && model.verticesY.length >= model.vertexCount
				&& model.verticesZ.length >= model.vertexCount) {
			sortWorldX = new int[model.vertexCount];
			sortWorldHeight = new int[model.vertexCount];
			sortWorldY = new int[model.vertexCount];
			int normalizedOrientation = orientation & 0x7ff;
			int sine = normalizedOrientation == 0 ? 0 : Rasterizer3D.SINE[normalizedOrientation];
			int cosine = normalizedOrientation == 0 ? 65536 : Rasterizer3D.COSINE[normalizedOrientation];
			for (int vertex = 0; vertex < model.vertexCount; vertex++) {
				int localX = model.verticesX[vertex];
				int localY = model.verticesZ[vertex];
				if (normalizedOrientation != 0) {
					int rotatedX = localY * sine + localX * cosine >> 16;
					localY = localY * cosine - localX * sine >> 16;
					localX = rotatedX;
				}
				sortWorldX[vertex] = localX + worldX;
				sortWorldHeight[vertex] = model.verticesY[vertex] + worldHeight;
				sortWorldY[vertex] = localY + worldY;
			}
		}

		int[] sortTriangleScratch = new int[model.triangleCount];
		int sortTriangleCount = 0;
		for (int triangle = 0; triangle < model.triangleCount; triangle++) {
			if (validWallSortTriangle(model, triangle)) {
				sortTriangleScratch[sortTriangleCount++] = triangle;
			}
		}
		int[] sortTriangles = Arrays.copyOf(sortTriangleScratch, sortTriangleCount);
		cachedPainterGeometry.put(key,
				new CachedPainterGeometry(faceFirstVertices, sortWorldX, sortWorldHeight, sortWorldY, sortTriangles));
	}

	private void uploadLegacyHslPalette() {
		int[] palette = Rasterizer3D.HSL_TO_RGB;
		int wordCount = palette == null ? 0 : Math.min(0x10000, palette.length);
		uploadTextureMappings(legacyHslPaletteBuffer, palette == null ? new int[0] : palette, wordCount,
				GL_STATIC_DRAW);
	}

	private static void addEligibleVertexCounts(GpuSceneChunk[] chunks, int[] counts) {
		if (chunks == null) {
			return;
		}
		for (GpuSceneChunk chunk : chunks) {
			for (int plane = Math.max(0, chunk.minRenderPlane()); plane < RENDER_PLANE_VARIANTS; plane++) {
				counts[plane] += chunk.vertexCount();
			}
		}
	}

	private static void addEligibleVertexCounts(GpuSceneChunk[][] chunkGroups, int[] counts) {
		if (chunkGroups == null) {
			return;
		}
		for (GpuSceneChunk[] chunks : chunkGroups) {
			addEligibleVertexCounts(chunks, counts);
		}
	}

	private void ensureUploadScratch(int requiredBytes) {
		if (uploadScratch.capacity() >= requiredBytes) {
			return;
		}
		int capacity = uploadScratch.capacity();
		while (capacity < requiredBytes) {
			int grown = capacity << 1;
			if (grown <= capacity) {
				capacity = requiredBytes;
				break;
			}
			capacity = grown;
		}
		uploadScratch = createUploadScratch(capacity);
		uploadScratchWords = uploadScratch.asIntBuffer();
	}

	private static void fillEligibleVertexCounts(GpuSceneChunk[] chunks, int[] counts) {
		Arrays.fill(counts, 0);
		if (chunks == null) {
			return;
		}
		for (GpuSceneChunk chunk : chunks) {
			for (int plane = Math.max(0, chunk.minRenderPlane()); plane < RENDER_PLANE_VARIANTS; plane++) {
				counts[plane] += chunk.vertexCount();
			}
		}
	}

	private static int normalizeRenderPlane(int renderPlane) {
		return Math.max(0, Math.min(RENDER_PLANE_VARIANTS - 1, renderPlane));
	}

	private void debugNearbyWallDecorations(WorldRenderFrame frame, int renderPlane, int cameraX, int cameraY) {
		if (!WALL_DECORATION_DEBUG) {
			return;
		}
		long now = System.nanoTime();
		if (now < wallDecorationDebugNextNanos) {
			return;
		}
		wallDecorationDebugNextNanos = now + WALL_DECORATION_DEBUG_INTERVAL_NANOS;

		Scene scene = frame.scene();
		int cameraTileX = cameraX >> 7;
		int cameraTileY = cameraY >> 7;
		int radius = 12;
		int minX = Math.max(0, cameraTileX - radius);
		int maxX = Math.min(scene.width - 1, cameraTileX + radius);
		int minY = Math.max(0, cameraTileY - radius);
		int maxY = Math.min(scene.height - 1, cameraTileY + radius);
		int found = 0;

		System.out.printf(
				"GPU wall-decoration debug: renderPlane=%d cameraTile=(%d,%d) cameraWorld=(%d,%d) yaw=%d pitch=%d%n",
				renderPlane, cameraTileX, cameraTileY, cameraX, cameraY, frame.yaw() & 0x7ff, frame.pitch() & 0x7ff);

		for (int plane = 0; plane < scene.planeCount; plane++) {
			for (int tileX = minX; tileX <= maxX; tileX++) {
				for (int tileY = minY; tileY <= maxY; tileY++) {
					rs2.scene.tile.SceneTile tile = scene.tiles[plane][tileX][tileY];
					if (tile == null || tile.wallDecoration == null) {
						continue;
					}
					rs2.scene.tile.WallDecoration decoration = tile.wallDecoration;
					int objectId = decoration.uid >> rs2.scene.SceneUid.ENTITY_ID_SHIFT
							& rs2.scene.SceneUid.ENTITY_ID_MASK;
					int type = rs2.scene.SceneConfig.type(decoration.config);
					int configOrientation = rs2.scene.SceneConfig.orientation(decoration.config);
					int relativeX = decoration.x - cameraX;
					int relativeY = decoration.y - cameraY;
					String renderableType = decoration.renderable == null ? "null"
							: decoration.renderable.getClass().getSimpleName();

					System.out.printf(
							"  p=%d tile=(%d,%d) id=%d type=%d cfgOri=%d face=%d bits=0x%03x "
									+ "world=(%d,%d,%d) rel=(%d,%d) renderable=%s%n",
							plane, tileX, tileY, objectId, type, configOrientation, decoration.face,
							decoration.configBits & 0x3ff, decoration.x, decoration.z, decoration.y, relativeX,
							relativeY, renderableType);

					if ((decoration.configBits & 0x300) == 0) {
						int expectedFace = configOrientation * rs2.media.Angle.QUARTER_TURN & rs2.media.Angle.MASK;
						System.out.printf("    ordinary: legacyAngle=%d gpuAngle=%d %s%n", expectedFace,
								decoration.face & rs2.media.Angle.MASK,
								expectedFace == (decoration.face & rs2.media.Angle.MASK) ? "MATCH" : "MISMATCH");
					} else {
						int face = decoration.face & 3;
						int expectedFace = configOrientation & 3;
						int signedX = face == 1 || face == 2 ? -relativeX : relativeX;
						int signedY = face == 2 || face == 3 ? -relativeY : relativeY;
						int angle100 = face * rs2.media.Angle.QUARTER_TURN + rs2.media.Angle.EIGHTH_TURN
								& rs2.media.Angle.MASK;
						int angle200 = face * rs2.media.Angle.QUARTER_TURN + rs2.media.Angle.FIVE_EIGHTHS_TURN
								& rs2.media.Angle.MASK;
						System.out.printf(
								"    special: faceExpected=%d %s compareY/X=%d/%d; "
										+ "0x100 angle=%d pos=(%d,%d); 0x200 angle=%d pos=(%d,%d)%n",
								expectedFace, expectedFace == face ? "MATCH" : "MISMATCH", signedY, signedX, angle100,
								decoration.x + Scene.WALL_DECORATION_INSET_X[face],
								decoration.y + Scene.WALL_DECORATION_INSET_Y[face], angle200,
								decoration.x + Scene.WALL_DECORATION_OUTSET_X[face],
								decoration.y + Scene.WALL_DECORATION_OUTSET_Y[face]);
					}
					found++;
				}
			}
		}
		System.out.printf("GPU wall-decoration debug: %d decorations within %d tiles%n", found, radius);
	}

	private void debugHoveredStaticModel(WorldRenderFrame frame, int renderPlane, int cameraX, int cameraY, int yawSin,
			int yawCos, int pitchSin, int pitchCos, int width, int height) {
		if (!STATIC_MODEL_DEBUG) {
			return;
		}
		long now = System.nanoTime();
		if (now < staticModelDebugNextNanos) {
			return;
		}
		staticModelDebugNextNanos = now + STATIC_MODEL_DEBUG_INTERVAL_NANOS;
		Point mouse = getMousePosition();
		if (mouse == null || mouse.x < 0 || mouse.y < 0 || mouse.x >= width || mouse.y >= height) {
			return;
		}
		GpuStaticModelDebug.dumpHovered(frame.scene(), renderPlane, cameraX, frame.cameraHeight(), cameraY, yawSin,
				yawCos, pitchSin, pitchCos, mouse.x, mouse.y, width, height);
	}

	private void drawScene(WorldRenderFrame frame, int width, int height) {
		if (terrainVertexCount == 0 && staticVertexCount == 0 && staticTexturedVertexCount == 0) {
			clearFrameStats();
			if (gpuFrameTimer != null) {
				gpuFrameTimer.markTerrainDone();
				gpuFrameTimer.markStaticDone();
			}
			return;
		}
		int renderPlane = normalizeRenderPlane(frame.renderPlane());
		int yaw = frame.yaw() & 0x7ff;
		int pitch = frame.pitch() & 0x7ff;
		int yawSin = Rasterizer3D.SINE[yaw];
		int yawCos = Rasterizer3D.COSINE[yaw];
		int pitchSin = Rasterizer3D.SINE[pitch];
		int pitchCos = Rasterizer3D.COSINE[pitch];

		int cameraX = Math.max(0, Math.min(frame.cameraX(), frame.scene().width * 128 - 1));
		int cameraY = Math.max(0, Math.min(frame.cameraY(), frame.scene().height * 128 - 1));
		debugNearbyWallDecorations(frame, renderPlane, cameraX, cameraY);
		debugHoveredStaticModel(frame, renderPlane, cameraX, cameraY, yawSin, yawCos, pitchSin, pitchCos, width,
				height);
		glUseProgram(shader.id());
		glUniform3i(cameraUniform, cameraX, frame.cameraHeight(), cameraY);
		glUniform2i(yawUniform, yawSin, yawCos);
		glUniform2i(pitchUniform, pitchSin, pitchCos);
		glUniform2f(viewportUniform, width, height);
		glActiveTexture(GL_TEXTURE0);
		glBindTexture(GL_TEXTURE_2D_ARRAY, textureManager == null ? 0 : textureManager.textureId());
		glUniform1i(textureSamplerUniform, 0);
		glActiveTexture(GL_TEXTURE1);
		glBindTexture(GL_TEXTURE_BUFFER, staticTextureMappingTexture);
		glUniform1i(modelTextureMappingSamplerUniform, 1);
		glActiveTexture(GL_TEXTURE2);
		glBindTexture(GL_TEXTURE_BUFFER, legacyHslPaletteTexture);
		glUniform1i(legacyHslPaletteSamplerUniform, 2);
		glActiveTexture(GL_TEXTURE0);
		glUniform1f(staticRoofObjectPlaneDepthBiasUniform, STATIC_ROOF_OBJECT_PLANE_DEPTH_BIAS);
		glUniform1f(staticFloorDecorationDepthBiasUniform, STATIC_FLOOR_DECORATION_DEPTH_BIAS);

		glUniform1i(staticTextureGeometryDebugUniform, STATIC_TEXTURE_GEOMETRY_DEBUG ? 1 : 0);
		int submitted;
		if (RENDER_PLANE_PAINTER_DEBUG) {
			submitted = drawSceneByRenderPlane(frame, renderPlane, cameraX, cameraY, yawSin, yawCos, pitchSin, pitchCos,
					width, height);
		} else {
			glUniform1f(staticPriorityDepthScaleUniform, 0.0f);
			glUniform1i(staticPriorityFloorOnlyUniform, 0);
			glUniform1i(staticPriorityWallDecorationUniform, 0);
			glUniform1i(staticLegacyPixelSnapDebugUniform, 0);
			glUniform1i(staticLegacyFaceCullDebugUniform, 0);
			glUniform1i(modelTextureProjectionUniform, 0);
			glUniform1f(renderPlaneTerrainDepthBiasUniform, 0.0f);
			drawVisibleChunks(terrainVertexArray, terrainChunks, renderPlane, cameraX, cameraY, frame.cameraHeight(),
					yawSin, yawCos, pitchSin, pitchCos, width, height);
			frameTerrainVisible = lastDrawVisibleRanges;
			frameTerrainEligible = lastDrawEligibleRanges;
			frameTerrainCommands = lastDrawCommands;
			submitted = lastDrawSubmittedVertices;
			if (gpuFrameTimer != null) {
				gpuFrameTimer.markTerrainDone();
			}

			if (STATIC_BACKFACE_CULL_DEBUG) {
				// Legacy Model.drawFaces() accepts positive signed area in its
				// top-left raster coordinates. The vertex shader flips Y for
				// OpenGL, so those same faces are CCW in window coordinates.
				glFrontFace(GL_CCW);
				glEnable(GL_CULL_FACE);
			}
			boolean staticPriorityDepthEnabled = STATIC_PRIORITY_DEPTH_DEBUG || STATIC_PRIORITY_FLOOR_ONLY_DEBUG
					|| STATIC_PRIORITY_WALL_DECORATION_DEBUG;
			glUniform1f(staticPriorityDepthScaleUniform,
					staticPriorityDepthEnabled ? STATIC_PRIORITY_DEPTH_SCALE : 0.0f);
			glUniform1i(staticPriorityFloorOnlyUniform, STATIC_PRIORITY_FLOOR_ONLY_DEBUG ? 1 : 0);
			glUniform1i(staticPriorityWallDecorationUniform, STATIC_PRIORITY_WALL_DECORATION_DEBUG ? 1 : 0);
			glUniform1i(staticLegacyPixelSnapDebugUniform, STATIC_LEGACY_PIXEL_SNAP_DEBUG ? 1 : 0);
			glUniform1i(staticLegacyFaceCullDebugUniform, STATIC_LEGACY_FACE_CULL_DEBUG ? 1 : 0);
			glUniform1i(legacySolidAlphaBlendUniform, 0);
			drawVisibleChunks(staticVertexArray, staticChunks, renderPlane, cameraX, cameraY, frame.cameraHeight(),
					yawSin, yawCos, pitchSin, pitchCos, width, height);
			glUniform1f(staticPriorityDepthScaleUniform, 0.0f);
			glUniform1i(staticPriorityFloorOnlyUniform, 0);
			glUniform1i(staticPriorityWallDecorationUniform, 0);
			glUniform1i(staticLegacyPixelSnapDebugUniform, 0);
			glUniform1i(staticLegacyFaceCullDebugUniform, 0);
			frameStaticVisible = lastDrawVisibleRanges;
			frameStaticEligible = lastDrawEligibleRanges;
			frameStaticCommands = lastDrawCommands;
			submitted += lastDrawSubmittedVertices;

			glUniform1f(staticPriorityDepthScaleUniform,
					staticPriorityDepthEnabled ? STATIC_PRIORITY_DEPTH_SCALE : 0.0f);
			glUniform1i(staticPriorityFloorOnlyUniform, STATIC_PRIORITY_FLOOR_ONLY_DEBUG ? 1 : 0);
			glUniform1i(staticPriorityWallDecorationUniform, STATIC_PRIORITY_WALL_DECORATION_DEBUG ? 1 : 0);
			glUniform1i(staticLegacyPixelSnapDebugUniform, STATIC_LEGACY_PIXEL_SNAP_DEBUG ? 1 : 0);
			glUniform1i(staticLegacyFaceCullDebugUniform, STATIC_LEGACY_FACE_CULL_DEBUG ? 1 : 0);
			glUniform1i(modelTextureProjectionUniform, 1);
			drawVisibleChunks(staticTexturedVertexArray, staticTexturedChunks, renderPlane, cameraX, cameraY,
					frame.cameraHeight(), yawSin, yawCos, pitchSin, pitchCos, width, height);
			frameStaticVisible += lastDrawVisibleRanges;
			frameStaticEligible += lastDrawEligibleRanges;
			frameStaticCommands += lastDrawCommands;
			submitted += lastDrawSubmittedVertices;
			glUniform1i(modelTextureProjectionUniform, 0);
			glUniform1f(staticPriorityDepthScaleUniform, 0.0f);
			glUniform1i(staticPriorityFloorOnlyUniform, 0);
			glUniform1i(staticPriorityWallDecorationUniform, 0);
			glUniform1i(staticLegacyPixelSnapDebugUniform, STATIC_LEGACY_PIXEL_SNAP_DEBUG ? 1 : 0);
			glUniform1i(staticLegacyFaceCullDebugUniform, STATIC_LEGACY_FACE_CULL_DEBUG ? 1 : 0);
			int[] wallPainterTotals = drawWallPriorityPainter(renderPlane, false, cameraX, cameraY,
					frame.cameraHeight(), yawSin, yawCos, pitchSin, pitchCos, width, height);
			frameStaticVisible += wallPainterTotals[0];
			frameStaticEligible += wallPainterTotals[1];
			frameStaticCommands += wallPainterTotals[2];
			submitted += wallPainterTotals[3];
			int[] interactivePainterTotals = drawInteractivePriorityPainter(renderPlane, false, cameraX, cameraY,
					frame.cameraHeight(), yawSin, yawCos, pitchSin, pitchCos, width, height);
			frameStaticVisible += interactivePainterTotals[0];
			frameStaticEligible += interactivePainterTotals[1];
			frameStaticCommands += interactivePainterTotals[2];
			submitted += interactivePainterTotals[3];
			int[] alphaPainterTotals = drawAlphaPriorityPainter(renderPlane, false, cameraX, cameraY,
					frame.cameraHeight(), yawSin, yawCos, pitchSin, pitchCos, width, height);
			frameStaticVisible += alphaPainterTotals[0];
			frameStaticEligible += alphaPainterTotals[1];
			frameStaticCommands += alphaPainterTotals[2];
			submitted += alphaPainterTotals[3];
			glUniform1i(staticLegacyPixelSnapDebugUniform, 0);
			glUniform1i(staticLegacyFaceCullDebugUniform, 0);
			if (STATIC_BACKFACE_CULL_DEBUG) {
				glDisable(GL_CULL_FACE);
			}
			if (gpuFrameTimer != null) {
				gpuFrameTimer.markStaticDone();
			}
		}

		frameSubmittedVertices = submitted;
		frameEligibleVertices = terrainEligibleVertices[renderPlane] + staticEligibleVertices[renderPlane];
		glBindVertexArray(0);
		glActiveTexture(GL_TEXTURE1);
		glBindTexture(GL_TEXTURE_BUFFER, 0);
		glActiveTexture(GL_TEXTURE0);
		glBindTexture(GL_TEXTURE_2D_ARRAY, 0);
		glUseProgram(0);
	}

	/**
	 * Render one minimum render-plane at a time while preserving scene depth.
	 * Higher-plane terrain receives a small clip-space nudge to reproduce the
	 * revision-377 tile painter in roof/floor overlaps without making every
	 * higher-plane model ignore lower-plane occlusion.
	 */
	private int drawSceneByRenderPlane(WorldRenderFrame frame, int renderPlane, int cameraX, int cameraY, int yawSin,
			int yawCos, int pitchSin, int pitchCos, int width, int height) {
		int terrainVisible = 0;
		int terrainEligible = 0;
		int terrainCommands = 0;
		int staticVisible = 0;
		int staticEligible = 0;
		int staticCommands = 0;
		int submitted = 0;
		boolean staticPriorityDepthEnabled = STATIC_PRIORITY_DEPTH_DEBUG || STATIC_PRIORITY_FLOOR_ONLY_DEBUG
				|| STATIC_PRIORITY_WALL_DECORATION_DEBUG;

		for (int plane = 0; plane <= renderPlane; plane++) {
			glUniform1f(staticPriorityDepthScaleUniform, 0.0f);
			glUniform1i(staticPriorityFloorOnlyUniform, 0);
			glUniform1i(staticPriorityWallDecorationUniform, 0);
			glUniform1i(staticLegacyPixelSnapDebugUniform, 0);
			glUniform1i(staticLegacyFaceCullDebugUniform, 0);
			glUniform1i(modelTextureProjectionUniform, 0);
			glUniform1i(legacySolidAlphaBlendUniform, 0);
			glUniform1f(renderPlaneTerrainDepthBiasUniform, plane * RENDER_PLANE_TERRAIN_DEPTH_BIAS);
			drawVisibleChunksForExactPlane(terrainVertexArray, terrainChunks, plane, cameraX, cameraY,
					frame.cameraHeight(), yawSin, yawCos, pitchSin, pitchCos, width, height);
			glUniform1f(renderPlaneTerrainDepthBiasUniform, 0.0f);
			terrainVisible += lastDrawVisibleRanges;
			terrainEligible += lastDrawEligibleRanges;
			terrainCommands += lastDrawCommands;
			submitted += lastDrawSubmittedVertices;

			if (STATIC_BACKFACE_CULL_DEBUG) {
				glFrontFace(GL_CCW);
				glEnable(GL_CULL_FACE);
			}
			glUniform1f(staticPriorityDepthScaleUniform,
					staticPriorityDepthEnabled ? STATIC_PRIORITY_DEPTH_SCALE : 0.0f);
			glUniform1i(staticPriorityFloorOnlyUniform, STATIC_PRIORITY_FLOOR_ONLY_DEBUG ? 1 : 0);
			glUniform1i(staticPriorityWallDecorationUniform, STATIC_PRIORITY_WALL_DECORATION_DEBUG ? 1 : 0);
			glUniform1i(staticLegacyPixelSnapDebugUniform, STATIC_LEGACY_PIXEL_SNAP_DEBUG ? 1 : 0);
			glUniform1i(staticLegacyFaceCullDebugUniform, STATIC_LEGACY_FACE_CULL_DEBUG ? 1 : 0);
			drawVisibleChunksForExactPlane(staticVertexArray, staticChunks, plane, cameraX, cameraY,
					frame.cameraHeight(), yawSin, yawCos, pitchSin, pitchCos, width, height);
			staticVisible += lastDrawVisibleRanges;
			staticEligible += lastDrawEligibleRanges;
			staticCommands += lastDrawCommands;
			submitted += lastDrawSubmittedVertices;

			glUniform1i(modelTextureProjectionUniform, 1);
			drawVisibleChunksForExactPlane(staticTexturedVertexArray, staticTexturedChunks, plane, cameraX, cameraY,
					frame.cameraHeight(), yawSin, yawCos, pitchSin, pitchCos, width, height);
			staticVisible += lastDrawVisibleRanges;
			staticEligible += lastDrawEligibleRanges;
			staticCommands += lastDrawCommands;
			submitted += lastDrawSubmittedVertices;
			glUniform1i(modelTextureProjectionUniform, 0);

			glUniform1f(staticPriorityDepthScaleUniform, 0.0f);
			glUniform1i(staticPriorityFloorOnlyUniform, 0);
			glUniform1i(staticPriorityWallDecorationUniform, 0);
			int[] wallPainterTotals = drawWallPriorityPainter(plane, true, cameraX, cameraY, frame.cameraHeight(),
					yawSin, yawCos, pitchSin, pitchCos, width, height);
			staticVisible += wallPainterTotals[0];
			staticEligible += wallPainterTotals[1];
			staticCommands += wallPainterTotals[2];
			submitted += wallPainterTotals[3];
			int[] interactivePainterTotals = drawInteractivePriorityPainter(plane, true, cameraX, cameraY,
					frame.cameraHeight(), yawSin, yawCos, pitchSin, pitchCos, width, height);
			staticVisible += interactivePainterTotals[0];
			staticEligible += interactivePainterTotals[1];
			staticCommands += interactivePainterTotals[2];
			submitted += interactivePainterTotals[3];
			int[] alphaPainterTotals = drawAlphaPriorityPainter(plane, true, cameraX, cameraY, frame.cameraHeight(),
					yawSin, yawCos, pitchSin, pitchCos, width, height);
			staticVisible += alphaPainterTotals[0];
			staticEligible += alphaPainterTotals[1];
			staticCommands += alphaPainterTotals[2];
			submitted += alphaPainterTotals[3];

			if (STATIC_BACKFACE_CULL_DEBUG) {
				glDisable(GL_CULL_FACE);
			}
		}

		glUniform1f(staticPriorityDepthScaleUniform, 0.0f);
		glUniform1i(staticPriorityFloorOnlyUniform, 0);
		glUniform1i(staticPriorityWallDecorationUniform, 0);
		glUniform1i(staticLegacyPixelSnapDebugUniform, 0);
		glUniform1i(staticLegacyFaceCullDebugUniform, 0);
		glUniform1i(modelTextureProjectionUniform, 0);
		glUniform1i(legacySolidAlphaBlendUniform, 0);
		glUniform1f(renderPlaneTerrainDepthBiasUniform, 0.0f);
		frameTerrainVisible = terrainVisible;
		frameTerrainEligible = terrainEligible;
		frameTerrainCommands = terrainCommands;
		frameStaticVisible = staticVisible;
		frameStaticEligible = staticEligible;
		frameStaticCommands = staticCommands;
		if (gpuFrameTimer != null) {
			// Terrain/static are interleaved in this diagnostic, so the split timing is
			// intentionally not meaningful. Keep the timer state machine valid.
			gpuFrameTimer.markTerrainDone();
			gpuFrameTimer.markStaticDone();
		}
		return submitted;
	}

	private int[] drawWallPriorityPainter(int plane, boolean exactPlane, int cameraX, int cameraY, int cameraHeight,
			int yawSin, int yawCos, int pitchSin, int pitchCos, int width, int height) {
		int[] totals = new int[4];
		if ((!STATIC_PRIORITY_WALL_PAINTER_DEBUG && !STATIC_DEPTH_WALL_PAINTER_DEBUG)
				|| staticWallPriorityDecorations == null || staticWallPriorityDecorations.length == 0) {
			return totals;
		}

		wallPriorityFrameIndexCount = 0;
		int eligibleObjects = 0;
		int visibleObjects = 0;
		for (GpuStaticSceneMesh.WallPriorityDecoration decoration : staticWallPriorityDecorations) {
			if (decoration == null || decoration.model() == null) {
				continue;
			}
			if (exactPlane ? decoration.minRenderPlane() != plane : decoration.minRenderPlane() > plane) {
				continue;
			}
			eligibleObjects++;
			if (!GpuSceneVisibility.isVisible(decoration.bounds(), cameraX, cameraY, cameraHeight, yawSin, yawCos,
					pitchSin, pitchCos, width, height)) {
				continue;
			}
			visibleObjects++;
			Model model = decoration.model();
			int orderedFaces = buildLegacyFaceOrder(decoration, model, decoration.orientation(), decoration.worldX(),
					decoration.worldHeight(), decoration.worldY(), cameraX, cameraY, cameraHeight, yawSin, yawCos, pitchSin,
					pitchCos);
			for (int index = 0; index < orderedFaces; index++) {
				appendCachedPainterFace(decoration, wallSortFaceOrder[index]);
			}
		}

		totals[0] = visibleObjects;
		totals[1] = eligibleObjects;
		if (wallPriorityFrameIndexCount == 0) {
			return totals;
		}

		uploadPainterIndices();
		boolean ignoreSceneDepth = STATIC_PRIORITY_WALL_PAINTER_IGNORE_SCENE_DEPTH_DEBUG;
		boolean depthBias = STATIC_PRIORITY_WALL_PAINTER_DEPTH_BIAS_DEBUG && !ignoreSceneDepth;
		if (ignoreSceneDepth) {
			glDisable(GL_DEPTH_TEST);
		} else if (depthBias) {
			glEnable(GL_POLYGON_OFFSET_FILL);
			glPolygonOffset(STATIC_PRIORITY_WALL_PAINTER_DEPTH_BIAS_FACTOR,
					STATIC_PRIORITY_WALL_PAINTER_DEPTH_BIAS_UNITS);
		}
		glDepthMask(false);
		try {
			bindPainterCache();
			drawPainterElements(0, wallPriorityFrameIndexCount);
			totals[2] = 1;
			totals[3] = wallPriorityFrameIndexCount;
		} finally {
			glDepthMask(true);
			if (depthBias) {
				glDisable(GL_POLYGON_OFFSET_FILL);
			}
			if (ignoreSceneDepth) {
				glEnable(GL_DEPTH_TEST);
			}
			restoreStaticPainterBindings();
		}
		return totals;
	}

	private int[] drawInteractivePriorityPainter(int plane, boolean exactPlane, int cameraX, int cameraY,
			int cameraHeight, int yawSin, int yawCos, int pitchSin, int pitchCos, int width, int height) {
		int[] totals = new int[4];
		if (staticInteractivePriorityObjects == null || staticInteractivePriorityObjects.length == 0) {
			return totals;
		}

		wallPriorityFrameIndexCount = 0;
		interactivePriorityFrameSpans.clear();
		interactivePriorityFrameBatches.clear();
		int eligibleObjects = 0;
		int visibleObjects = 0;

		for (GpuStaticSceneMesh.InteractivePriorityObject object : staticInteractivePriorityObjects) {
			if (object == null || object.model() == null) {
				continue;
			}
			if (exactPlane ? object.minRenderPlane() != plane : object.minRenderPlane() > plane) {
				continue;
			}
			eligibleObjects++;
			if (!GpuSceneVisibility.isVisible(object.bounds(), cameraX, cameraY, cameraHeight, yawSin, yawCos, pitchSin,
					pitchCos, width, height)) {
				continue;
			}
			visibleObjects++;

			int firstIndex = wallPriorityFrameIndexCount;
			Model model = object.model();
			int orderedFaces = buildLegacyFaceOrder(object, model, object.orientation(), object.worldX(), object.worldHeight(),
					object.worldY(), cameraX, cameraY, cameraHeight, yawSin, yawCos, pitchSin, pitchCos);
			for (int index = 0; index < orderedFaces; index++) {
				appendCachedPainterFace(object, wallSortFaceOrder[index]);
			}
			int indexCount = wallPriorityFrameIndexCount - firstIndex;
			if (indexCount > 0) {
				float roofPlaneDepthBias = object.priorityMetadataTag() == ROOF_OBJECT_PRIORITY_METADATA_TAG
						? object.minRenderPlane() * STATIC_ROOF_OBJECT_PLANE_DEPTH_BIAS
						: 0.0f;
				interactivePriorityFrameSpans.add(new PainterCommandSpan(firstIndex, indexCount, roofPlaneDepthBias,
						painterScreenBatchSafe, painterScreenMinX, painterScreenMinY, painterScreenMaxX,
						painterScreenMaxY));
			}
		}

		totals[0] = visibleObjects;
		totals[1] = eligibleObjects;
		if (wallPriorityFrameIndexCount == 0) {
			return totals;
		}

		buildInteractivePainterBatches();
		uploadPainterIndices();
		bindPainterCache();
		try {
			for (PainterBatch batch : interactivePriorityFrameBatches) {
				glUniform1f(renderPlaneTerrainDepthBiasUniform, batch.roofPlaneDepthBias());

				// Objects inside one batch are pairwise screen-disjoint. Their color passes
				// therefore cannot affect one another, so all of them can be submitted in
				// one ordered draw before committing the same combined coverage to depth.
				glDepthMask(false);
				drawPainterElements(batch.firstIndex(), batch.indexCount());
				totals[2]++;

				glColorMask(false, false, false, false);
				glDepthMask(true);
				drawPainterElements(batch.firstIndex(), batch.indexCount());
				totals[2]++;
				glColorMask(true, true, true, true);
			}

			totals[3] = wallPriorityFrameIndexCount * 2;
		} finally {
			glUniform1f(renderPlaneTerrainDepthBiasUniform, 0.0f);
			glColorMask(true, true, true, true);
			glDepthMask(true);
			restoreStaticPainterBindings();
		}
		return totals;
	}

	/**
	 * Coalesces consecutive priority objects when they provably cannot touch the same
	 * screen pixel. The per-object color/depth sequence is only semantically required
	 * between overlapping objects; disjoint objects can share one color pass and one
	 * depth commit without changing revision-377 painter results.
	 */
	private void buildInteractivePainterBatches() {
		interactivePriorityFrameBatches.clear();
		for (int spanIndex = 0; spanIndex < interactivePriorityFrameSpans.size(); spanIndex++) {
			PainterCommandSpan span = interactivePriorityFrameSpans.get(spanIndex);
			int batchCount = interactivePriorityFrameBatches.size();
			if (batchCount > 0) {
				PainterBatch previous = interactivePriorityFrameBatches.get(batchCount - 1);
				if (canAppendPainterSpan(previous, span)) {
					int endIndex = span.firstIndex() + span.indexCount();
					interactivePriorityFrameBatches.set(batchCount - 1,
							new PainterBatch(previous.firstSpan(), previous.spanCount() + 1, previous.firstIndex(),
									endIndex - previous.firstIndex(), previous.roofPlaneDepthBias()));
					continue;
				}
			}
			interactivePriorityFrameBatches.add(new PainterBatch(spanIndex, 1, span.firstIndex(), span.indexCount(),
					span.roofPlaneDepthBias()));
		}
	}

	private boolean canAppendPainterSpan(PainterBatch batch, PainterCommandSpan candidate) {
		if (!candidate.batchSafe() || batch.roofPlaneDepthBias() != candidate.roofPlaneDepthBias()) {
			return false;
		}
		int end = batch.firstSpan() + batch.spanCount();
		for (int index = batch.firstSpan(); index < end; index++) {
			PainterCommandSpan existing = interactivePriorityFrameSpans.get(index);
			if (!existing.batchSafe() || painterScreenBoundsOverlap(existing, candidate)) {
				return false;
			}
		}
		return true;
	}

	private static boolean painterScreenBoundsOverlap(PainterCommandSpan a, PainterCommandSpan b) {
		// One projected pixel of guard keeps raster edge rules/conservative integer
		// projection from turning an apparent edge-touch into an unsafe shared fragment.
		return !((long) a.maxX() + 1L < b.minX() || (long) b.maxX() + 1L < a.minX()
				|| (long) a.maxY() + 1L < b.minY() || (long) b.maxY() + 1L < a.minY());
	}

	private void appendCachedPainterFace(Object key, int triangle) {
		CachedPainterGeometry geometry = cachedPainterGeometry.get(key);
		if (geometry == null || triangle < 0 || triangle >= geometry.faceFirstVertices().length) {
			return;
		}
		int firstVertex = geometry.faceFirstVertices()[triangle];
		if (firstVertex < 0) {
			return;
		}
		wallPriorityFrameIndices = ensureIntCapacity(wallPriorityFrameIndices, wallPriorityFrameIndexCount + 3);
		wallPriorityFrameIndices[wallPriorityFrameIndexCount++] = firstVertex;
		wallPriorityFrameIndices[wallPriorityFrameIndexCount++] = firstVertex + 1;
		wallPriorityFrameIndices[wallPriorityFrameIndexCount++] = firstVertex + 2;
	}

	private void uploadPainterIndices() {
		int requiredBytes = wallPriorityFrameIndexCount * Integer.BYTES;
		ensureUploadScratch(requiredBytes);
		uploadScratch.clear();
		uploadScratchWords.clear();
		uploadScratchWords.put(wallPriorityFrameIndices, 0, wallPriorityFrameIndexCount);
		uploadScratch.limit(requiredBytes);
		glBindVertexArray(wallPriorityTexturedVertexArray);
		glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, wallPriorityIndexBuffer);
		glBufferData(GL_ELEMENT_ARRAY_BUFFER, uploadScratch, GL_DYNAMIC_DRAW);
		glBindVertexArray(0);
	}

	private void bindPainterCache() {
		glActiveTexture(GL_TEXTURE1);
		glBindTexture(GL_TEXTURE_BUFFER, wallPriorityTextureMappingTexture);
		glActiveTexture(GL_TEXTURE0);
		glUniform1i(modelTextureProjectionUniform, 2);
		glBindVertexArray(wallPriorityTexturedVertexArray);
	}

	private void drawPainterElements(int firstIndex, int indexCount) {
		glDrawElements(GL_TRIANGLES, indexCount, GL_UNSIGNED_INT, (long) firstIndex * Integer.BYTES);
	}

	private void restoreStaticPainterBindings() {
		glBindVertexArray(0);
		glUniform1i(modelTextureProjectionUniform, 0);
		glActiveTexture(GL_TEXTURE1);
		glBindTexture(GL_TEXTURE_BUFFER, staticTextureMappingTexture);
		glActiveTexture(GL_TEXTURE0);
	}

	private int buildLegacyFaceOrder(Object painterKey, Model model, int modelOrientation, int modelWorldX,
			int modelWorldHeight, int modelWorldY, int cameraX, int cameraY, int cameraHeight, int yawSin, int yawCos,
			int pitchSin, int pitchCos) {
		if (model.vertexCount <= 0 || model.triangleCount <= 0 || model.depthSpan <= 0) {
			return 0;
		}
		ensureWallSortCapacity(model.vertexCount, model.triangleCount, model.depthSpan);
		Arrays.fill(wallSortDepthBucketCounts, 0, model.depthSpan, 0);
		CachedPainterGeometry cachedGeometry = cachedPainterGeometry.get(painterKey);
		int[] cachedWorldX = cachedGeometry == null ? null : cachedGeometry.worldX();
		int[] cachedWorldHeight = cachedGeometry == null ? null : cachedGeometry.worldHeight();
		int[] cachedWorldY = cachedGeometry == null ? null : cachedGeometry.worldY();
		boolean cachedWorldVertices = cachedWorldX != null && cachedWorldX.length >= model.vertexCount
				&& cachedWorldHeight != null && cachedWorldHeight.length >= model.vertexCount && cachedWorldY != null
				&& cachedWorldY.length >= model.vertexCount;
		int[] sortTriangles = cachedGeometry == null ? null : cachedGeometry.sortTriangles();
		painterScreenMinX = Integer.MAX_VALUE;
		painterScreenMinY = Integer.MAX_VALUE;
		painterScreenMaxX = Integer.MIN_VALUE;
		painterScreenMaxY = Integer.MIN_VALUE;
		painterScreenBatchSafe = true;

		int orientation = modelOrientation & 0x7ff;
		int orientationSin = orientation == 0 ? 0 : Rasterizer3D.SINE[orientation];
		int orientationCos = orientation == 0 ? 65536 : Rasterizer3D.COSINE[orientation];
		int originX = modelWorldX - cameraX;
		int originHeight = modelWorldHeight - cameraHeight;
		int originY = modelWorldY - cameraY;
		int originYawDepth = originY * yawCos - originX * yawSin >> 16;
		int centerDepth = originHeight * pitchSin + originYawDepth * pitchCos >> 16;

		for (int vertex = 0; vertex < model.vertexCount; vertex++) {
			int relativeX;
			int relativeHeight;
			int relativeY;
			if (cachedWorldVertices) {
				relativeX = cachedWorldX[vertex] - cameraX;
				relativeHeight = cachedWorldHeight[vertex] - cameraHeight;
				relativeY = cachedWorldY[vertex] - cameraY;
			} else {
				int localX = model.verticesX[vertex];
				int localHeight = model.verticesY[vertex];
				int localY = model.verticesZ[vertex];
				if (orientation != 0) {
					int rotatedX = localY * orientationSin + localX * orientationCos >> 16;
					localY = localY * orientationCos - localX * orientationSin >> 16;
					localX = rotatedX;
				}
				relativeX = localX + originX;
				relativeHeight = localHeight + originHeight;
				relativeY = localY + originY;
			}
			int viewX = relativeY * yawSin + relativeX * yawCos >> 16;
			int yawDepth = relativeY * yawCos - relativeX * yawSin >> 16;
			int viewY = relativeHeight * pitchCos - yawDepth * pitchSin >> 16;
			int depth = relativeHeight * pitchSin + yawDepth * pitchCos >> 16;
			wallSortProjectedDepth[vertex] = depth - centerDepth;
			if (depth >= 50) {
				wallSortProjectedX[vertex] = (viewX << 9) / depth;
				wallSortProjectedY[vertex] = (viewY << 9) / depth;
			} else {
				wallSortProjectedX[vertex] = -5000;
				wallSortProjectedY[vertex] = 0;
			}
		}

		int candidateCount = sortTriangles == null ? model.triangleCount : sortTriangles.length;
		int visibleCount = 0;
		for (int candidate = 0; candidate < candidateCount; candidate++) {
			int triangle = sortTriangles == null ? candidate : sortTriangles[candidate];
			wallSortFaceDepth[triangle] = -1;
			if (sortTriangles == null && !validWallSortTriangle(model, triangle)) {
				continue;
			}
			int a = model.triangleVertexA[triangle];
			int b = model.triangleVertexB[triangle];
			int c = model.triangleVertexC[triangle];
			int ax = wallSortProjectedX[a];
			int bx = wallSortProjectedX[b];
			int cx = wallSortProjectedX[c];
			boolean nearClipped = ax == -5000 || bx == -5000 || cx == -5000;
			if (!nearClipped) {
				int signedArea = (ax - bx) * (wallSortProjectedY[c] - wallSortProjectedY[b])
						- (wallSortProjectedY[a] - wallSortProjectedY[b]) * (cx - bx);
				if (signedArea <= 0) {
					continue;
				}
			} else {
				// Legacy near-plane clipping can create screen coverage not represented by
				// the sentinel projected coordinates. Keep this object isolated rather than
				// risk batching it with something that the clipped polygon can overlap.
				painterScreenBatchSafe = false;
			}

			int depth = (wallSortProjectedDepth[a] + wallSortProjectedDepth[b] + wallSortProjectedDepth[c]) / 3
					+ model.radius;
			// Model.drawFaces() indexes a fixed depth bucket and later walks only
			// [0, depthSpan). Static model bounds should make this range check redundant,
			// but retaining it avoids turning malformed bounds into an array failure.
			if (depth < 0 || depth >= model.depthSpan) {
				painterScreenBatchSafe = false;
				continue;
			}
			wallSortFaceDepth[triangle] = depth;
			wallSortDepthBucketCounts[depth]++;
			visibleCount++;

			if (!nearClipped) {
				int ay = wallSortProjectedY[a];
				int by = wallSortProjectedY[b];
				int cy = wallSortProjectedY[c];
				painterScreenMinX = Math.min(painterScreenMinX, Math.min(ax, Math.min(bx, cx)));
				painterScreenMinY = Math.min(painterScreenMinY, Math.min(ay, Math.min(by, cy)));
				painterScreenMaxX = Math.max(painterScreenMaxX, Math.max(ax, Math.max(bx, cx)));
				painterScreenMaxY = Math.max(painterScreenMaxY, Math.max(ay, Math.max(by, cy)));
			}
		}
		if (visibleCount > 0 && painterScreenMinX == Integer.MAX_VALUE) {
			painterScreenBatchSafe = false;
		}

		// Revision 377 does not comparison-sort faces. It buckets them by integer
		// camera-relative depth, then walks the buckets far-to-near while preserving
		// original triangle order inside each bucket. Build that order in linear time.
		int outputOffset = 0;
		for (int depth = model.depthSpan - 1; depth >= 0; depth--) {
			wallSortDepthBucketOffsets[depth] = outputOffset;
			outputOffset += wallSortDepthBucketCounts[depth];
			wallSortDepthBucketCounts[depth] = 0;
		}
		for (int candidate = 0; candidate < candidateCount; candidate++) {
			int triangle = sortTriangles == null ? candidate : sortTriangles[candidate];
			int depth = wallSortFaceDepth[triangle];
			if (depth < 0) {
				continue;
			}
			int position = wallSortDepthBucketOffsets[depth] + wallSortDepthBucketCounts[depth]++;
			wallSortFaceOrder[position] = triangle;
		}

		// Model.drawFaces() has a simpler branch when there is no per-face priority
		// array: the linear bucket pass above is already the final face order.
		if (model.trianglePriorities == null) {
			return visibleCount;
		}

		Arrays.fill(wallSortPriorityCounts, 0);
		Arrays.fill(wallSortPriorityDepthSums, 0);
		for (int index = 0; index < visibleCount; index++) {
			int triangle = wallSortFaceOrder[index];
			int priority = Math.max(0, Math.min(11, model.trianglePriorities[triangle]));
			int bucketIndex = wallSortPriorityCounts[priority]++;
			wallSortPriorityFaces[priority][bucketIndex] = triangle;
			int depth = wallSortFaceDepth[triangle];
			if (priority < 10) {
				wallSortPriorityDepthSums[priority] += depth;
			} else if (priority == 10) {
				wallSortPriority10Depths[bucketIndex] = depth;
			} else {
				wallSortPriority11Depths[bucketIndex] = depth;
			}
		}

		int threshold12 = averagePriorityDepth(1, 2);
		int threshold34 = averagePriorityDepth(3, 4);
		int threshold68 = averagePriorityDepth(6, 8);
		int outputCount = 0;
		int specialIndex = 0;
		int specialCount = wallSortPriorityCounts[10];
		int[] specialFaces = wallSortPriorityFaces[10];
		int[] specialDepths = wallSortPriority10Depths;
		boolean usingPriority11 = false;
		if (specialCount == 0) {
			specialCount = wallSortPriorityCounts[11];
			specialFaces = wallSortPriorityFaces[11];
			specialDepths = wallSortPriority11Depths;
			usingPriority11 = true;
		}
		int specialDepth = specialIndex < specialCount ? specialDepths[specialIndex] : -1000;

		for (int priority = 0; priority < 10; priority++) {
			int threshold = priority == 0 ? threshold12 : priority == 3 ? threshold34 : priority == 5 ? threshold68
					: Integer.MAX_VALUE;
			while ((priority == 0 || priority == 3 || priority == 5) && specialDepth > threshold) {
				wallSortFaceOrder[outputCount++] = specialFaces[specialIndex++];
				if (specialIndex == specialCount && !usingPriority11) {
					specialIndex = 0;
					specialCount = wallSortPriorityCounts[11];
					specialFaces = wallSortPriorityFaces[11];
					specialDepths = wallSortPriority11Depths;
					usingPriority11 = true;
				}
				specialDepth = specialIndex < specialCount ? specialDepths[specialIndex] : -1000;
			}
			int count = wallSortPriorityCounts[priority];
			for (int index = 0; index < count; index++) {
				wallSortFaceOrder[outputCount++] = wallSortPriorityFaces[priority][index];
			}
		}
		while (specialDepth != -1000) {
			wallSortFaceOrder[outputCount++] = specialFaces[specialIndex++];
			if (specialIndex == specialCount && !usingPriority11) {
				specialIndex = 0;
				specialCount = wallSortPriorityCounts[11];
				specialFaces = wallSortPriorityFaces[11];
				specialDepths = wallSortPriority11Depths;
				usingPriority11 = true;
			}
			specialDepth = specialIndex < specialCount ? specialDepths[specialIndex] : -1000;
		}
		return outputCount;
	}

	private int averagePriorityDepth(int firstPriority, int secondPriority) {
		int count = wallSortPriorityCounts[firstPriority] + wallSortPriorityCounts[secondPriority];
		if (count == 0) {
			return 0;
		}
		return (wallSortPriorityDepthSums[firstPriority] + wallSortPriorityDepthSums[secondPriority]) / count;
	}

	private void ensureWallSortCapacity(int vertexCount, int triangleCount, int depthSpan) {
		if (wallSortProjectedX.length < vertexCount) {
			int capacity = growCapacity(wallSortProjectedX.length, vertexCount);
			wallSortProjectedX = new int[capacity];
			wallSortProjectedY = new int[capacity];
			wallSortProjectedDepth = new int[capacity];
		}
		if (wallSortFaceDepth.length < triangleCount) {
			int capacity = growCapacity(wallSortFaceDepth.length, triangleCount);
			wallSortFaceDepth = new int[capacity];
			wallSortFaceOrder = new int[capacity];
			wallSortPriority10Depths = new int[capacity];
			wallSortPriority11Depths = new int[capacity];
			for (int priority = 0; priority < wallSortPriorityFaces.length; priority++) {
				wallSortPriorityFaces[priority] = new int[capacity];
			}
		}
		if (wallSortDepthBucketCounts.length < depthSpan) {
			int capacity = growCapacity(wallSortDepthBucketCounts.length, depthSpan);
			wallSortDepthBucketCounts = new int[capacity];
			wallSortDepthBucketOffsets = new int[capacity];
		}
	}

	private static boolean validWallSortTriangle(Model model, int triangle) {
		if (model.triangleVertexA == null || model.triangleVertexB == null || model.triangleVertexC == null
				|| triangle < 0 || triangle >= model.triangleVertexA.length || triangle >= model.triangleVertexB.length
				|| triangle >= model.triangleVertexC.length) {
			return false;
		}
		if (model.verticesX == null || model.verticesY == null || model.verticesZ == null) {
			return false;
		}
		if (model.trianglePriorities != null && triangle >= model.trianglePriorities.length) {
			return false;
		}
		if (model.triangleDrawType != null && triangle < model.triangleDrawType.length
				&& model.triangleDrawType[triangle] == -1) {
			return false;
		}
		int a = model.triangleVertexA[triangle];
		int b = model.triangleVertexB[triangle];
		int c = model.triangleVertexC[triangle];
		return a >= 0 && b >= 0 && c >= 0 && a < model.vertexCount && b < model.vertexCount && c < model.vertexCount;
	}

	private static int growCapacity(int current, int required) {
		int capacity = Math.max(16, current);
		while (capacity < required) {
			capacity = capacity + (capacity >> 1) + 1;
		}
		return capacity;
	}

	private static int[] ensureIntCapacity(int[] values, int required) {
		if (values.length >= required) {
			return values;
		}
		return new int[growCapacity(values.length, required)];
	}

	private record CachedPainterGeometry(int[] faceFirstVertices, int[] worldX, int[] worldHeight, int[] worldY,
			int[] sortTriangles) {
	}

	private record PainterCommandSpan(int firstIndex, int indexCount, float roofPlaneDepthBias, boolean batchSafe,
			int minX, int minY, int maxX, int maxY) {
	}

	private record PainterBatch(int firstSpan, int spanCount, int firstIndex, int indexCount,
			float roofPlaneDepthBias) {
	}

	private int[] drawAlphaPriorityPainter(int plane, boolean exactPlane, int cameraX, int cameraY, int cameraHeight,
			int yawSin, int yawCos, int pitchSin, int pitchCos, int width, int height) {
		int[] totals = new int[4];
		if (!STATIC_ALPHA_BLEND_DEBUG || staticAlphaPriorityObjects == null || staticAlphaPriorityObjects.length == 0) {
			return totals;
		}

		wallPriorityFrameIndexCount = 0;
		int eligibleObjects = 0;
		int visibleObjects = 0;
		for (GpuStaticSceneMesh.AlphaPriorityObject object : staticAlphaPriorityObjects) {
			if (object == null || object.model() == null) {
				continue;
			}
			if (exactPlane ? object.minRenderPlane() != plane : object.minRenderPlane() > plane) {
				continue;
			}
			eligibleObjects++;
			if (!GpuSceneVisibility.isVisible(object.bounds(), cameraX, cameraY, cameraHeight, yawSin, yawCos, pitchSin,
					pitchCos, width, height)) {
				continue;
			}
			visibleObjects++;

			Model model = object.model();
			int orderedFaces = buildLegacyFaceOrder(object, model, object.orientation(), object.worldX(), object.worldHeight(),
					object.worldY(), cameraX, cameraY, cameraHeight, yawSin, yawCos, pitchSin, pitchCos);
			for (int index = 0; index < orderedFaces; index++) {
				appendCachedPainterFace(object, wallSortFaceOrder[index]);
			}
		}

		totals[0] = visibleObjects;
		totals[1] = eligibleObjects;
		if (wallPriorityFrameIndexCount == 0) {
			return totals;
		}

		uploadPainterIndices();
		glUniform1i(legacySolidAlphaBlendUniform, 1);
		glEnable(GL_BLEND);
		glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
		boolean depthBias = STATIC_ALPHA_BLEND_DEPTH_BIAS_FACTOR != 0.0f
				|| STATIC_ALPHA_BLEND_DEPTH_BIAS_UNITS != 0.0f;
		if (depthBias) {
			glEnable(GL_POLYGON_OFFSET_FILL);
			glPolygonOffset(STATIC_ALPHA_BLEND_DEPTH_BIAS_FACTOR, STATIC_ALPHA_BLEND_DEPTH_BIAS_UNITS);
		}
		glDepthMask(false);
		try {
			bindPainterCache();
			drawPainterElements(0, wallPriorityFrameIndexCount);
			totals[2] = 1;
			totals[3] = wallPriorityFrameIndexCount;
		} finally {
			glDepthMask(true);
			if (depthBias) {
				glDisable(GL_POLYGON_OFFSET_FILL);
			}
			glDisable(GL_BLEND);
			glUniform1i(legacySolidAlphaBlendUniform, 0);
			restoreStaticPainterBindings();
		}
		return totals;
	}

	private void accumulateLastDraw(int[] totals) {
		totals[0] += lastDrawVisibleRanges;
		totals[1] += lastDrawEligibleRanges;
		totals[2] += lastDrawCommands;
		totals[3] += lastDrawSubmittedVertices;
	}

	private void drawVisibleChunksForExactPlane(int vertexArray, GpuSceneChunk[] chunks, int exactPlane, int cameraX,
			int cameraY, int cameraHeight, int yawSin, int yawCos, int pitchSin, int pitchCos, int width, int height) {
		lastDrawVisibleRanges = 0;
		lastDrawEligibleRanges = 0;
		lastDrawCommands = 0;
		lastDrawSubmittedVertices = 0;
		if (chunks == null || chunks.length == 0) {
			return;
		}

		ensureMultiDrawCapacity(chunks.length);
		multiDrawFirsts.clear();
		multiDrawCounts.clear();
		for (GpuSceneChunk chunk : chunks) {
			if (chunk.minRenderPlane() != exactPlane) {
				continue;
			}
			lastDrawEligibleRanges++;
			if (!GpuSceneVisibility.isVisible(chunk, cameraX, cameraY, cameraHeight, yawSin, yawCos, pitchSin, pitchCos,
					width, height)) {
				continue;
			}
			lastDrawVisibleRanges++;
			lastDrawSubmittedVertices += chunk.vertexCount();
			appendOrMergeDrawRange(chunk.firstVertex(), chunk.vertexCount());
		}

		lastDrawCommands = multiDrawFirsts.position();
		if (lastDrawCommands == 0) {
			return;
		}
		multiDrawFirsts.flip();
		multiDrawCounts.flip();
		glBindVertexArray(vertexArray);
		glMultiDrawArrays(GL_TRIANGLES, multiDrawFirsts, multiDrawCounts);
	}

	private void drawVisibleChunks(int vertexArray, GpuSceneChunk[] chunks, int renderPlane, int cameraX, int cameraY,
			int cameraHeight, int yawSin, int yawCos, int pitchSin, int pitchCos, int width, int height) {
		lastDrawVisibleRanges = 0;
		lastDrawEligibleRanges = 0;
		lastDrawCommands = 0;
		lastDrawSubmittedVertices = 0;
		if (chunks == null || chunks.length == 0) {
			return;
		}

		ensureMultiDrawCapacity(chunks.length);
		multiDrawFirsts.clear();
		multiDrawCounts.clear();
		for (GpuSceneChunk chunk : chunks) {
			if (!chunk.visibleOnPlane(renderPlane)) {
				continue;
			}
			lastDrawEligibleRanges++;
			if (!GpuSceneVisibility.isVisible(chunk, cameraX, cameraY, cameraHeight, yawSin, yawCos, pitchSin, pitchCos,
					width, height)) {
				continue;
			}
			lastDrawVisibleRanges++;
			lastDrawSubmittedVertices += chunk.vertexCount();
			appendOrMergeDrawRange(chunk.firstVertex(), chunk.vertexCount());
		}

		lastDrawCommands = multiDrawFirsts.position();
		if (lastDrawCommands > 0) {
			multiDrawFirsts.flip();
			multiDrawCounts.flip();
			glBindVertexArray(vertexArray);
			glMultiDrawArrays(GL_TRIANGLES, multiDrawFirsts, multiDrawCounts);
		}
	}

	/** Coalesces adjacent visible ranges so one multi-draw entry can cover them. */
	private void appendOrMergeDrawRange(int firstVertex, int vertexCount) {
		int commandCount = multiDrawFirsts.position();
		if (commandCount > 0) {
			int previous = commandCount - 1;
			int previousFirst = multiDrawFirsts.get(previous);
			int previousCount = multiDrawCounts.get(previous);
			if (previousFirst + previousCount == firstVertex) {
				multiDrawCounts.put(previous, previousCount + vertexCount);
				return;
			}
		}
		multiDrawFirsts.put(firstVertex);
		multiDrawCounts.put(vertexCount);
	}

	private void ensureMultiDrawCapacity(int required) {
		if (multiDrawFirsts.capacity() >= required) {
			return;
		}
		int capacity = Math.max(required, multiDrawFirsts.capacity() * 2);
		multiDrawFirsts = BufferUtils.createIntBuffer(capacity);
		multiDrawCounts = BufferUtils.createIntBuffer(capacity);
	}

	/** Composites one small legacy software rectangle above the native world. */
	private void drawViewportOverlay(SoftwareViewportOverlay overlay, int framebufferWidth, int framebufferHeight) {
		if (overlay == null) {
			return;
		}
		int width = Math.min(overlay.width(), framebufferWidth - overlay.x());
		int height = Math.min(overlay.height(), framebufferHeight - overlay.y());
		if (width <= 0 || height <= 0) {
			return;
		}

		int requiredBytes = width * height * 4;
		ensureOverlayScratch(requiredBytes);
		overlayUploadScratch.clear();
		int[] source = overlay.pixels();
		int stride = overlay.sourceStride();
		boolean keyedTransparency = overlay.hasTransparencyKey();
		int transparentPixelKey = overlay.transparentPixelKey();
		int sourceRow = overlay.y() * stride + overlay.x();
		for (int y = 0; y < height; y++) {
			int sourceIndex = sourceRow;
			for (int x = 0; x < width; x++) {
				int pixel = source[sourceIndex++];
				overlayUploadScratch.put((byte) (pixel >> 16));
				overlayUploadScratch.put((byte) (pixel >> 8));
				overlayUploadScratch.put((byte) pixel);
				overlayUploadScratch.put((byte) (keyedTransparency && pixel == transparentPixelKey ? 0 : 0xff));
			}
			sourceRow += stride;
		}
		overlayUploadScratch.flip();

		glActiveTexture(GL_TEXTURE0);
		glBindTexture(GL_TEXTURE_2D, overlayTexture);
		glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
		if (overlayTextureWidth != width || overlayTextureHeight != height) {
			overlayTextureWidth = width;
			overlayTextureHeight = height;
			glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, (ByteBuffer) null);
		}
		glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, width, height, GL_RGBA, GL_UNSIGNED_BYTE, overlayUploadScratch);

		glDisable(GL_DEPTH_TEST);
		if (keyedTransparency) {
			glEnable(GL_BLEND);
			glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
		}
		glUseProgram(overlayShader.id());
		glUniform4f(overlayRectUniform, overlay.x(), overlay.y(), width, height);
		glUniform2f(overlayViewportUniform, framebufferWidth, framebufferHeight);
		glUniform1i(overlaySamplerUniform, 0);
		glBindVertexArray(overlayVertexArray);
		glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
		glBindVertexArray(0);
		glUseProgram(0);
		glBindTexture(GL_TEXTURE_2D, 0);
		if (keyedTransparency) {
			glDisable(GL_BLEND);
		}
		glEnable(GL_DEPTH_TEST);
	}

	private void ensureOverlayScratch(int requiredBytes) {
		if (overlayUploadScratch.capacity() >= requiredBytes) {
			return;
		}
		int capacity = overlayUploadScratch.capacity();
		while (capacity < requiredBytes) {
			capacity <<= 1;
		}
		overlayUploadScratch = BufferUtils.createByteBuffer(capacity);
	}

	private void clearFrameStats() {
		frameTerrainVisible = 0;
		frameTerrainEligible = 0;
		frameTerrainCommands = 0;
		frameStaticVisible = 0;
		frameStaticEligible = 0;
		frameStaticCommands = 0;
		frameSubmittedVertices = 0;
		frameEligibleVertices = 0;
	}

	private void recordPerformance(long cpuRenderNanos, long swapNanos) {
		long now = System.nanoTime();
		if (perfWindowStarted == 0L) {
			perfWindowStarted = now;
		}
		perfFrames++;
		perfSubmittedVertices += frameSubmittedVertices;
		perfEligibleVertices += frameEligibleVertices;
		perfCpuRenderNanos += cpuRenderNanos;
		perfSwapNanos += swapNanos;
		perfLastTerrainVisible = frameTerrainVisible;
		perfLastTerrainEligible = frameTerrainEligible;
		perfLastTerrainCommands = frameTerrainCommands;
		perfLastStaticVisible = frameStaticVisible;
		perfLastStaticEligible = frameStaticEligible;
		perfLastStaticCommands = frameStaticCommands;

		long elapsed = now - perfWindowStarted;
		if (elapsed < PERF_STATS_INTERVAL_NANOS) {
			return;
		}
		double seconds = elapsed / 1_000_000_000.0;
		double fps = perfFrames / seconds;
		double submittedPercent = perfEligibleVertices == 0 ? 0.0
				: perfSubmittedVertices * 100.0 / perfEligibleVertices;
		double cpuMs = perfFrames == 0 ? 0.0 : perfCpuRenderNanos / 1_000_000.0 / perfFrames;
		double swapMs = perfFrames == 0 ? 0.0 : perfSwapNanos / 1_000_000.0 / perfFrames;
		GpuFrameTimer.Snapshot gpu = gpuFrameTimer.snapshotAndReset();
		System.out.printf(
				"GPU perf: %.1f frames/s, CPU submit %.3f ms, swap %.3f ms, GPU %.3f ms "
						+ "(clear %.3f, terrain %.3f, static %.3f), submitted %.1f%%; "
						+ "terrain ranges %d/%d (%d commands), static ranges %d/%d (%d commands), "
						+ "viewport %dx%d, scene %.1f MiB, textures %d/50 %.1f MiB%s%n",
				fps, cpuMs, swapMs, gpu.averageTotalMs(), gpu.averageClearMs(), gpu.averageTerrainMs(),
				gpu.averageStaticMs(), submittedPercent, perfLastTerrainVisible, perfLastTerrainEligible,
				perfLastTerrainCommands, perfLastStaticVisible, perfLastStaticEligible, perfLastStaticCommands,
				perfLastWidth, perfLastHeight, cachedSceneBytes / (1024.0 * 1024.0),
				textureManager == null ? 0 : textureManager.residentLayers(),
				textureManager == null ? 0.0 : textureManager.memoryMiB(),
				gpu.droppedFrames() == 0 ? "" : ", timer dropped " + gpu.droppedFrames());
		perfWindowStarted = now;
		perfFrames = 0;
		perfSubmittedVertices = 0;
		perfEligibleVertices = 0;
		perfCpuRenderNanos = 0;
		perfSwapNanos = 0;
	}

	private int requiredUniform(GpuShaderProgram program, String name) {
		int location = glGetUniformLocation(program.id(), name);
		if (location < 0) {
			throw new IllegalStateException("Required OpenGL uniform was optimized out or not found: " + name);
		}
		return location;
	}

	private boolean framebufferContainsScene(int width, int height) {
		ByteBuffer pixels = BufferUtils.createByteBuffer(width * height * 4);
		glReadPixels(0, 0, width, height, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
		for (int offset = 0; offset < pixels.capacity(); offset += 4) {
			int red = Byte.toUnsignedInt(pixels.get(offset));
			int green = Byte.toUnsignedInt(pixels.get(offset + 1));
			int blue = Byte.toUnsignedInt(pixels.get(offset + 2));
			if (Math.abs(red - CLEAR_RED) > 2 || Math.abs(green - CLEAR_GREEN) > 2 || Math.abs(blue - CLEAR_BLUE) > 2) {
				return true;
			}
		}
		return false;
	}

	private boolean framebufferContainsGreenTexture(int width, int height) {
		ByteBuffer pixels = BufferUtils.createByteBuffer(width * height * 4);
		glReadPixels(0, 0, width, height, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
		for (int offset = 0; offset < pixels.capacity(); offset += 4) {
			int red = Byte.toUnsignedInt(pixels.get(offset));
			int green = Byte.toUnsignedInt(pixels.get(offset + 1));
			int blue = Byte.toUnsignedInt(pixels.get(offset + 2));
			if (green > 40 && green > red * 2 && green > blue * 2) {
				return true;
			}
		}
		return false;
	}

	private boolean framebufferContainsStaticTexturePattern(int width, int height) {
		ByteBuffer pixels = BufferUtils.createByteBuffer(width * height * 4);
		glReadPixels(0, 0, width, height, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
		boolean redFound = false;
		boolean greenFound = false;
		boolean blueFound = false;
		for (int offset = 0; offset < pixels.capacity(); offset += 4) {
			int red = Byte.toUnsignedInt(pixels.get(offset));
			int green = Byte.toUnsignedInt(pixels.get(offset + 1));
			int blue = Byte.toUnsignedInt(pixels.get(offset + 2));
			redFound |= red > 32 && red > green * 2 && red > blue * 2;
			greenFound |= green > 32 && green > red * 2 && green > blue * 2;
			blueFound |= blue > 32 && blue > red * 2 && blue > green * 2;
			if (redFound && greenFound && blueFound) {
				return true;
			}
		}
		return false;
	}

	boolean initialized() {
		return initialized;
	}

	void requestValidation() {
		validationRequested = true;
		validationPassed = false;
	}

	boolean validationPassed() {
		return validationPassed;
	}

	void requestGreenTextureValidation() {
		validationRequested = true;
		validationPassed = false;
		greenTextureValidationRequested = true;
	}

	void requestStaticTexturePatternValidation() {
		validationRequested = true;
		validationPassed = false;
		staticTexturePatternValidationRequested = true;
	}

	/**
	 * Avoids the lwjgl3-awt 0.2.4 JAWT teardown crash when AWT removes this canvas
	 * from a thread other than the thread which acquired the drawing surface. Flint
	 * only destroys the GPU canvas while the standalone process is exiting, so
	 * intentionally leaking the native surface until process exit is safer than
	 * invoking JAWT_FreeDrawingSurface from the EDT.
	 *
	 * Remove this override after upgrading to a lwjgl3-awt build containing
	 * LWJGLX/lwjgl3-awt #124.
	 */
	@Override
	public void disposeCanvas() {
		// Intentionally no-op for lwjgl3-awt 0.2.4.
	}
}
