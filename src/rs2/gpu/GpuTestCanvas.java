package rs2.gpu;

import static org.lwjgl.opengl.GL33C.GL_ARRAY_BUFFER;
import static org.lwjgl.opengl.GL33C.GL_COLOR_BUFFER_BIT;
import static org.lwjgl.opengl.GL33C.GL_DEPTH_BUFFER_BIT;
import static org.lwjgl.opengl.GL33C.GL_FLOAT;
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
import static org.lwjgl.opengl.GL33C.glDeleteBuffers;
import static org.lwjgl.opengl.GL33C.glDeleteVertexArrays;
import static org.lwjgl.opengl.GL33C.glDrawArrays;
import static org.lwjgl.opengl.GL33C.glEnableVertexAttribArray;
import static org.lwjgl.opengl.GL33C.glGenBuffers;
import static org.lwjgl.opengl.GL33C.glGenVertexArrays;
import static org.lwjgl.opengl.GL33C.glGetString;
import static org.lwjgl.opengl.GL33C.glReadPixels;
import static org.lwjgl.opengl.GL33C.glUseProgram;
import static org.lwjgl.opengl.GL33C.glVertexAttribPointer;
import static org.lwjgl.opengl.GL33C.glViewport;

import java.nio.ByteBuffer;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.awt.AWTGLCanvas;
import org.lwjgl.opengl.awt.GLData;

/**
 * Phase-1 OpenGL surface. It deliberately renders only a diagnostic triangle;
 * RuneScape scene geometry begins in Phase 2.
 */
final class GpuTestCanvas extends AWTGLCanvas {

    private static final long serialVersionUID = 1L;

    private static final String VERTEX_SHADER = """
            #version 330 core
            layout (location = 0) in vec2 aPosition;
            layout (location = 1) in vec3 aColor;
            out vec3 vertexColor;
            void main() {
                vertexColor = aColor;
                gl_Position = vec4(aPosition, 0.0, 1.0);
            }
            """;

    private static final String FRAGMENT_SHADER = """
            #version 330 core
            in vec3 vertexColor;
            out vec4 fragmentColor;
            void main() {
                fragmentColor = vec4(vertexColor, 1.0);
            }
            """;

    private static final float[] TRIANGLE = {
            // x, y, r, g, b
             0.0f,  0.62f, 0.90f, 0.45f, 0.18f,
            -0.62f, -0.55f, 0.18f, 0.72f, 0.90f,
             0.62f, -0.55f, 0.55f, 0.86f, 0.30f
    };

    private GpuShaderProgram shader;
    private int vertexArray;
    private int vertexBuffer;
    private volatile boolean closeRequested;
    private volatile boolean initialized;
    private volatile boolean validationRequested;
    private volatile boolean validationPassed;

    GpuTestCanvas() {
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

    @Override
    public void initGL() {
        GL.createCapabilities();
        if (!GL.getCapabilities().OpenGL33) {
            throw new IllegalStateException("Flint GPU rendering requires OpenGL 3.3 or newer.");
        }

        shader = GpuShaderProgram.compile(VERTEX_SHADER, FRAGMENT_SHADER);
        vertexArray = glGenVertexArrays();
        vertexBuffer = glGenBuffers();

        glBindVertexArray(vertexArray);
        glBindBuffer(GL_ARRAY_BUFFER, vertexBuffer);
        glBufferData(GL_ARRAY_BUFFER, TRIANGLE, GL_STATIC_DRAW);
        int stride = 5 * Float.BYTES;
        glVertexAttribPointer(0, 2, GL_FLOAT, false, stride, 0L);
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(1, 3, GL_FLOAT, false, stride, 2L * Float.BYTES);
        glEnableVertexAttribArray(1);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindVertexArray(0);

        glClearColor(0.035f, 0.045f, 0.060f, 1.0f);
        initialized = true;
        System.out.println("GPU renderer initialized: " + glGetString(GL_VENDOR) + " / " + glGetString(GL_RENDERER)
                + " / OpenGL " + glGetString(GL_VERSION));
    }

    @Override
    public void paintGL() {
        if (closeRequested) {
            releaseGlResources();
            return;
        }

        int width = Math.max(1, getFramebufferWidth());
        int height = Math.max(1, getFramebufferHeight());
        glViewport(0, 0, width, height);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        glUseProgram(shader.id());
        glBindVertexArray(vertexArray);
        glDrawArrays(GL_TRIANGLES, 0, 3);
        glBindVertexArray(0);
        glUseProgram(0);
        if (validationRequested) {
            ByteBuffer pixel = BufferUtils.createByteBuffer(4);
            glReadPixels(width / 2, height / 2, 1, 1, GL_RGBA, GL_UNSIGNED_BYTE, pixel);
            int red = Byte.toUnsignedInt(pixel.get(0));
            int green = Byte.toUnsignedInt(pixel.get(1));
            int blue = Byte.toUnsignedInt(pixel.get(2));
            validationPassed = red + green + blue > 80;
            validationRequested = false;
        }
        swapBuffers();
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

    void closeGl() {
        if (!initialized) {
            return;
        }
        closeRequested = true;
        if (isDisplayable()) {
            render();
        }
    }

    private void releaseGlResources() {
        if (!initialized) {
            return;
        }
        if (vertexBuffer != 0) {
            glDeleteBuffers(vertexBuffer);
            vertexBuffer = 0;
        }
        if (vertexArray != 0) {
            glDeleteVertexArrays(vertexArray);
            vertexArray = 0;
        }
        if (shader != null) {
            shader.close();
            shader = null;
        }
        initialized = false;
    }
}
