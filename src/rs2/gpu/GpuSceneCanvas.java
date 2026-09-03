package rs2.gpu;

import static org.lwjgl.opengl.GL14C.glMultiDrawArrays;
import static org.lwjgl.opengl.GL33C.GL_ARRAY_BUFFER;
import static org.lwjgl.opengl.GL33C.GL_COLOR_BUFFER_BIT;
import static org.lwjgl.opengl.GL33C.GL_BLEND;
import static org.lwjgl.opengl.GL33C.GL_DEPTH_BUFFER_BIT;
import static org.lwjgl.opengl.GL33C.GL_DEPTH_TEST;
import static org.lwjgl.opengl.GL33C.GL_ONE_MINUS_SRC_ALPHA;
import static org.lwjgl.opengl.GL33C.GL_SRC_ALPHA;
import static org.lwjgl.opengl.GL33C.GL_NEAREST;
import static org.lwjgl.opengl.GL33C.GL_RGBA8;
import static org.lwjgl.opengl.GL33C.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL13C.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13C.glActiveTexture;
import static org.lwjgl.opengl.GL33C.GL_TEXTURE_WRAP_S;
import static org.lwjgl.opengl.GL33C.GL_TEXTURE_WRAP_T;
import static org.lwjgl.opengl.GL33C.GL_CLAMP_TO_EDGE;
import static org.lwjgl.opengl.GL33C.GL_TEXTURE_MAG_FILTER;
import static org.lwjgl.opengl.GL33C.GL_TEXTURE_MIN_FILTER;
import static org.lwjgl.opengl.GL33C.GL_TRIANGLE_STRIP;
import static org.lwjgl.opengl.GL33C.GL_UNPACK_ALIGNMENT;
import static org.lwjgl.opengl.GL33C.GL_INT;
import static org.lwjgl.opengl.GL33C.GL_LEQUAL;
import static org.lwjgl.opengl.GL33C.GL_RENDERER;
import static org.lwjgl.opengl.GL33C.GL_RGBA;
import static org.lwjgl.opengl.GL33C.GL_STATIC_DRAW;
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
import static org.lwjgl.opengl.GL33C.glDepthFunc;
import static org.lwjgl.opengl.GL33C.glDisable;
import static org.lwjgl.opengl.GL33C.glDrawArrays;
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
import static org.lwjgl.opengl.GL33C.glUniform1i;
import static org.lwjgl.opengl.GL33C.glUniform2f;
import static org.lwjgl.opengl.GL33C.glUniform2i;
import static org.lwjgl.opengl.GL33C.glUniform3i;
import static org.lwjgl.opengl.GL33C.glUniform4f;
import static org.lwjgl.opengl.GL33C.glUseProgram;
import static org.lwjgl.opengl.GL33C.glBindTexture;
import static org.lwjgl.opengl.GL33C.glVertexAttribIPointer;
import static org.lwjgl.opengl.GL33C.glVertexAttribPointer;
import static org.lwjgl.opengl.GL33C.glViewport;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.Arrays;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.awt.AWTGLCanvas;
import org.lwjgl.opengl.awt.GLData;

import rs2.game.render.SoftwareViewportOverlay;
import rs2.game.render.WorldRenderFrame;
import rs2.media.Rasterizer3D;
import rs2.scene.Scene;

/** OpenGL surface that renders complete loaded terrain and static scene geometry. */
final class GpuSceneCanvas extends AWTGLCanvas {

    private static final long serialVersionUID = 1L;
    private static final int CLEAR_RED = 9;
    private static final int CLEAR_GREEN = 11;
    private static final int CLEAR_BLUE = 15;
    private static final int RENDER_PLANE_VARIANTS = 4;
    private static final boolean PERF_STATS = Boolean.getBoolean("flint.gpu.perfStats");
    private static final long PERF_STATS_INTERVAL_NANOS = 5_000_000_000L;
    private static final int INITIAL_UPLOAD_SCRATCH_BYTES = 4 * 1024 * 1024;

    private static final String VERTEX_SHADER = """
            #version 330 core
            layout (location = 0) in ivec3 aPosition;
            layout (location = 1) in vec4 aColor;

            uniform ivec3 uCamera;
            uniform ivec2 uYawSinCos;
            uniform ivec2 uPitchSinCos;
            uniform vec2 uViewport;

            noperspective out vec3 vertexColor;

            void main() {
                ivec3 relative = aPosition - uCamera;

                int viewX = (relative.z * uYawSinCos.x + relative.x * uYawSinCos.y) >> 16;
                int yawDepth = (relative.z * uYawSinCos.y - relative.x * uYawSinCos.x) >> 16;
                int viewY = (relative.y * uPitchSinCos.y - yawDepth * uPitchSinCos.x) >> 16;
                int depth = (relative.y * uPitchSinCos.x + yawDepth * uPitchSinCos.y) >> 16;

                const float nearClip = 50.0;
                const float farClip = 25000.0;
                float viewDepth = float(depth);
                float clipZ = ((farClip + nearClip) / (farClip - nearClip)) * viewDepth
                        - ((2.0 * farClip * nearClip) / (farClip - nearClip));

                gl_Position = vec4(
                        float(viewX) * (1024.0 / uViewport.x),
                        -float(viewY) * (1024.0 / uViewport.y),
                        clipZ,
                        viewDepth);
                vertexColor = aColor.rgb;
            }
            """;

    private static final String FRAGMENT_SHADER = """
            #version 330 core
            noperspective in vec3 vertexColor;
            out vec4 fragmentColor;
            void main() {
                fragmentColor = vec4(vertexColor, 1.0);
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
    private Scene uploadedScene;
    private long uploadedGeometryRevision = Long.MIN_VALUE;
    private int uploadedPaletteRevision = Integer.MIN_VALUE;
    private int uploadedMinPlane = Integer.MIN_VALUE;
    private boolean sceneUploaded;
    private volatile boolean initialized;
    private volatile boolean validationRequested;
    private volatile boolean validationPassed;

    GpuSceneCanvas() {
        super(createGlData());
        setIgnoreRepaint(true);
        setFocusable(true);
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

        shader = GpuShaderProgram.compile(VERTEX_SHADER, FRAGMENT_SHADER);
        cameraUniform = requiredUniform(shader, "uCamera");
        yawUniform = requiredUniform(shader, "uYawSinCos");
        pitchUniform = requiredUniform(shader, "uPitchSinCos");
        viewportUniform = requiredUniform(shader, "uViewport");

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
        configureVertexArray(terrainVertexArray, terrainVertexBuffer);
        staticVertexArray = glGenVertexArrays();
        staticVertexBuffer = glGenBuffers();
        configureVertexArray(staticVertexArray, staticVertexBuffer);

        glEnable(GL_DEPTH_TEST);
        glDepthFunc(GL_LEQUAL);
        glClearColor(CLEAR_RED / 255.0f, CLEAR_GREEN / 255.0f, CLEAR_BLUE / 255.0f, 1.0f);
        if (gpuFrameTimer != null) {
            gpuFrameTimer.initialize();
        }
        initialized = true;
        System.out.println("GPU renderer initialized: " + glGetString(GL_VENDOR) + " / " + glGetString(GL_RENDERER)
                + " / OpenGL " + glGetString(GL_VERSION));
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
            validationPassed = framebufferContainsScene(width, height);
            validationRequested = false;
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

    private void configureVertexArray(int vertexArray, int vertexBuffer) {
        glBindVertexArray(vertexArray);
        glBindBuffer(GL_ARRAY_BUFFER, vertexBuffer);
        glBufferData(GL_ARRAY_BUFFER, 0L, GL_STATIC_DRAW);
        int stride = GpuVertexBuilder.BYTES_PER_VERTEX;
        glVertexAttribIPointer(0, 3, GL_INT, stride, 0L);
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(1, 4, GL_UNSIGNED_BYTE, true, stride, GpuVertexBuilder.COLOR_BYTE_OFFSET);
        glEnableVertexAttribArray(1);
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
        staticVertexCount = staticScene.vertexCount();
        staticChunks = staticScene.chunks();
        fillEligibleVertexCounts(staticChunks, staticEligibleVertices);
        glBindBuffer(GL_ARRAY_BUFFER, 0);

        cachedSceneBytes = terrain.byteSize() + staticScene.byteSize();
        sceneUploaded = true;
        long uploaded = System.nanoTime();
        double buildMs = (built - buildStarted) / 1_000_000.0;
        double uploadMs = (uploaded - built) / 1_000_000.0;
        double mib = cachedSceneBytes / (1024.0 * 1024.0);
        System.out.printf("GPU scene cached: %d terrain surfaces, %d terrain triangles, %d static models (%d unique), "
                + "%d static triangles, %d dynamic renderables deferred; %.1f MiB packed scene, build %.2f ms, "
                + "upload %.2f ms%n", terrain.surfaceCount(), terrain.triangleCount(), staticScene.instanceCount(),
                staticScene.uniqueModelCount(), staticScene.triangleCount(), staticScene.skippedDynamicCount(), mib,
                buildMs, uploadMs);
    }

    private void uploadPackedVertices(int vertexBuffer, int[] words) {
        int requiredBytes = words.length * Integer.BYTES;
        ensureUploadScratch(requiredBytes);
        uploadScratch.clear();
        uploadScratchWords.clear();
        uploadScratchWords.put(words);
        uploadScratch.limit(requiredBytes);
        glBindBuffer(GL_ARRAY_BUFFER, vertexBuffer);
        glBufferData(GL_ARRAY_BUFFER, uploadScratch, GL_STATIC_DRAW);
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

    private void drawScene(WorldRenderFrame frame, int width, int height) {
        if (terrainVertexCount == 0 && staticVertexCount == 0) {
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
        glUseProgram(shader.id());
        glUniform3i(cameraUniform, cameraX, frame.cameraHeight(), cameraY);
        glUniform2i(yawUniform, yawSin, yawCos);
        glUniform2i(pitchUniform, pitchSin, pitchCos);
        glUniform2f(viewportUniform, width, height);

        drawVisibleChunks(terrainVertexArray, terrainChunks, renderPlane, cameraX, cameraY, frame.cameraHeight(),
                yawSin, yawCos, pitchSin, pitchCos, width, height);
        frameTerrainVisible = lastDrawVisibleRanges;
        frameTerrainEligible = lastDrawEligibleRanges;
        frameTerrainCommands = lastDrawCommands;
        int submitted = lastDrawSubmittedVertices;
        if (gpuFrameTimer != null) {
            gpuFrameTimer.markTerrainDone();
        }

        drawVisibleChunks(staticVertexArray, staticChunks, renderPlane, cameraX, cameraY, frame.cameraHeight(), yawSin,
                yawCos, pitchSin, pitchCos, width, height);
        frameStaticVisible = lastDrawVisibleRanges;
        frameStaticEligible = lastDrawEligibleRanges;
        frameStaticCommands = lastDrawCommands;
        submitted += lastDrawSubmittedVertices;
        if (gpuFrameTimer != null) {
            gpuFrameTimer.markStaticDone();
        }

        frameSubmittedVertices = submitted;
        frameEligibleVertices = terrainEligibleVertices[renderPlane] + staticEligibleVertices[renderPlane];
        glBindVertexArray(0);
        glUseProgram(0);
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
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE,
                    (ByteBuffer) null);
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
        System.out.printf("GPU perf: %.1f frames/s, CPU submit %.3f ms, swap %.3f ms, GPU %.3f ms "
                + "(clear %.3f, terrain %.3f, static %.3f), submitted %.1f%%; "
                + "terrain ranges %d/%d (%d commands), static ranges %d/%d (%d commands), "
                + "viewport %dx%d, cache %.1f MiB%s%n", fps, cpuMs, swapMs, gpu.averageTotalMs(),
                gpu.averageClearMs(), gpu.averageTerrainMs(), gpu.averageStaticMs(), submittedPercent,
                perfLastTerrainVisible, perfLastTerrainEligible, perfLastTerrainCommands, perfLastStaticVisible,
                perfLastStaticEligible, perfLastStaticCommands, perfLastWidth, perfLastHeight,
                cachedSceneBytes / (1024.0 * 1024.0),
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
            if (Math.abs(red - CLEAR_RED) > 2 || Math.abs(green - CLEAR_GREEN) > 2
                    || Math.abs(blue - CLEAR_BLUE) > 2) {
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

    /**
     * Avoids the lwjgl3-awt 0.2.4 JAWT teardown crash when AWT removes this
     * canvas from a thread other than the thread which acquired the drawing
     * surface. Flint only destroys the GPU canvas while the standalone process is
     * exiting, so intentionally leaking the native surface until process exit is
     * safer than invoking JAWT_FreeDrawingSurface from the EDT.
     *
     * Remove this override after upgrading to a lwjgl3-awt build containing
     * LWJGLX/lwjgl3-awt #124.
     */
    @Override
    public void disposeCanvas() {
        // Intentionally no-op for lwjgl3-awt 0.2.4.
    }
}
