package rs2.gpu;

import static org.lwjgl.opengl.GL33C.GL_ARRAY_BUFFER;
import static org.lwjgl.opengl.GL33C.GL_COLOR_BUFFER_BIT;
import static org.lwjgl.opengl.GL33C.GL_DEPTH_BUFFER_BIT;
import static org.lwjgl.opengl.GL33C.GL_DEPTH_TEST;
import static org.lwjgl.opengl.GL33C.GL_FLOAT;
import static org.lwjgl.opengl.GL33C.GL_LEQUAL;
import static org.lwjgl.opengl.GL33C.GL_RENDERER;
import static org.lwjgl.opengl.GL33C.GL_RGBA;
import static org.lwjgl.opengl.GL33C.GL_STATIC_DRAW;
import static org.lwjgl.opengl.GL33C.GL_TRIANGLES;
import static org.lwjgl.opengl.GL33C.GL_UNSIGNED_BYTE;
import static org.lwjgl.opengl.GL33C.GL_VENDOR;
import static org.lwjgl.opengl.GL33C.GL_VERSION;
import static org.lwjgl.opengl.GL33C.glBindBuffer;
import static org.lwjgl.opengl.GL33C.glBindVertexArray;
import static org.lwjgl.opengl.GL33C.glBufferData;
import static org.lwjgl.opengl.GL33C.glClear;
import static org.lwjgl.opengl.GL33C.glClearColor;
import static org.lwjgl.opengl.GL33C.glDepthFunc;
import static org.lwjgl.opengl.GL33C.glDrawArrays;
import static org.lwjgl.opengl.GL33C.glEnable;
import static org.lwjgl.opengl.GL33C.glEnableVertexAttribArray;
import static org.lwjgl.opengl.GL33C.glGenBuffers;
import static org.lwjgl.opengl.GL33C.glGenVertexArrays;
import static org.lwjgl.opengl.GL33C.glGetString;
import static org.lwjgl.opengl.GL33C.glGetUniformLocation;
import static org.lwjgl.opengl.GL33C.glReadPixels;
import static org.lwjgl.opengl.GL33C.glUniform2f;
import static org.lwjgl.opengl.GL33C.glUniform2i;
import static org.lwjgl.opengl.GL33C.glUniform3i;
import static org.lwjgl.opengl.GL33C.glUseProgram;
import static org.lwjgl.opengl.GL33C.glVertexAttribPointer;
import static org.lwjgl.opengl.GL33C.glViewport;

import java.nio.ByteBuffer;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.awt.AWTGLCanvas;
import org.lwjgl.opengl.awt.GLData;

import rs2.game.render.WorldRenderFrame;
import rs2.media.Rasterizer3D;
import rs2.scene.Scene;

/** OpenGL surface that renders the complete loaded terrain mesh. */
final class GpuSceneCanvas extends AWTGLCanvas {

	private static final long serialVersionUID = 1L;
	private static final int CLEAR_RED = 9;
	private static final int CLEAR_GREEN = 11;
	private static final int CLEAR_BLUE = 15;

	private static final String VERTEX_SHADER = """
			#version 330 core
			layout (location = 0) in vec3 aPosition;
			layout (location = 1) in vec3 aColor;

			uniform ivec3 uCamera;
			uniform ivec2 uYawSinCos;
			uniform ivec2 uPitchSinCos;
			uniform vec2 uViewport;

			noperspective out vec3 vertexColor;

			void main() {
			    ivec3 relative = ivec3(aPosition) - uCamera;

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
			    vertexColor = aColor;
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

	private volatile WorldRenderFrame frameData;
	private GpuShaderProgram shader;
	private int vertexArray;
	private int vertexBuffer;
	private int vertexCount;
	private int cameraUniform;
	private int yawUniform;
	private int pitchUniform;
	private int viewportUniform;
	private Scene uploadedScene;
	private long uploadedGeometryRevision = Long.MIN_VALUE;
	private int uploadedPaletteRevision = Integer.MIN_VALUE;
	private int uploadedRenderPlane = Integer.MIN_VALUE;
	private int uploadedMinPlane = Integer.MIN_VALUE;
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

	void setFrameData(WorldRenderFrame frameData) {
		this.frameData = frameData;
	}

	@Override
	public void initGL() {
		GL.createCapabilities();
		if (!GL.getCapabilities().OpenGL33) {
			throw new IllegalStateException("Flint GPU rendering requires OpenGL 3.3 or newer.");
		}

		shader = GpuShaderProgram.compile(VERTEX_SHADER, FRAGMENT_SHADER);
		cameraUniform = requiredUniform("uCamera");
		yawUniform = requiredUniform("uYawSinCos");
		pitchUniform = requiredUniform("uPitchSinCos");
		viewportUniform = requiredUniform("uViewport");

		vertexArray = glGenVertexArrays();
		vertexBuffer = glGenBuffers();
		glBindVertexArray(vertexArray);
		glBindBuffer(GL_ARRAY_BUFFER, vertexBuffer);
		glBufferData(GL_ARRAY_BUFFER, 0L, GL_STATIC_DRAW);
		int stride = GpuTerrainMesh.FLOATS_PER_VERTEX * Float.BYTES;
		glVertexAttribPointer(0, 3, GL_FLOAT, false, stride, 0L);
		glEnableVertexAttribArray(0);
		glVertexAttribPointer(1, 3, GL_FLOAT, false, stride, 3L * Float.BYTES);
		glEnableVertexAttribArray(1);
		glBindBuffer(GL_ARRAY_BUFFER, 0);
		glBindVertexArray(0);

		glEnable(GL_DEPTH_TEST);
		glDepthFunc(GL_LEQUAL);
		glClearColor(CLEAR_RED / 255.0f, CLEAR_GREEN / 255.0f, CLEAR_BLUE / 255.0f, 1.0f);
		initialized = true;
		System.out.println("GPU renderer initialized: " + glGetString(GL_VENDOR) + " / " + glGetString(GL_RENDERER)
				+ " / OpenGL " + glGetString(GL_VERSION));
	}

	@Override
	public void paintGL() {
		int width = Math.max(1, getFramebufferWidth());
		int height = Math.max(1, getFramebufferHeight());
		glViewport(0, 0, width, height);
		glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

		WorldRenderFrame frame = frameData;
		if (frame != null) {
			ensureTerrainUploaded(frame);
			drawTerrain(frame, width, height);
		}

		if (validationRequested) {
			validationPassed = framebufferContainsTerrain(width, height);
			validationRequested = false;
		}
		swapBuffers();
	}

	private void ensureTerrainUploaded(WorldRenderFrame frame) {
		Scene scene = frame.scene();
		long geometryRevision = scene.geometryRevision();
		int paletteRevision = Rasterizer3D.paletteRevision();
		if (scene == uploadedScene && geometryRevision == uploadedGeometryRevision
				&& paletteRevision == uploadedPaletteRevision && frame.renderPlane() == uploadedRenderPlane
				&& scene.minPlane == uploadedMinPlane) {
			return;
		}

		GpuTerrainMesh mesh = GpuSceneUploader.buildTerrain(scene, frame.renderPlane());
		glBindBuffer(GL_ARRAY_BUFFER, vertexBuffer);
		glBufferData(GL_ARRAY_BUFFER, mesh.vertices(), GL_STATIC_DRAW);
		glBindBuffer(GL_ARRAY_BUFFER, 0);
		vertexCount = mesh.vertexCount();
		uploadedScene = scene;
		uploadedGeometryRevision = geometryRevision;
		uploadedPaletteRevision = paletteRevision;
		uploadedRenderPlane = frame.renderPlane();
		uploadedMinPlane = scene.minPlane;
		System.out.println("GPU terrain uploaded: " + mesh.surfaceCount() + " surfaces, " + mesh.triangleCount()
				+ " triangles, " + mesh.vertexCount() + " vertices, plane <= " + frame.renderPlane());
	}

	private void drawTerrain(WorldRenderFrame frame, int width, int height) {
		if (vertexCount == 0) {
			return;
		}
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
		glBindVertexArray(vertexArray);
		glDrawArrays(GL_TRIANGLES, 0, vertexCount);
		glBindVertexArray(0);
		glUseProgram(0);
	}

	private int requiredUniform(String name) {
		int location = glGetUniformLocation(shader.id(), name);
		if (location < 0) {
			throw new IllegalStateException("Required OpenGL uniform was optimized out or not found: " + name);
		}
		return location;
	}

	private boolean framebufferContainsTerrain(int width, int height) {
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
