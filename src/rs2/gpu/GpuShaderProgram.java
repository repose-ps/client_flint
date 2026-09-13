package rs2.gpu;

import static org.lwjgl.opengl.GL33C.GL_COMPILE_STATUS;
import static org.lwjgl.opengl.GL33C.GL_FRAGMENT_SHADER;
import static org.lwjgl.opengl.GL33C.GL_GEOMETRY_SHADER;
import static org.lwjgl.opengl.GL33C.GL_LINK_STATUS;
import static org.lwjgl.opengl.GL33C.GL_VERTEX_SHADER;
import static org.lwjgl.opengl.GL33C.glAttachShader;
import static org.lwjgl.opengl.GL33C.glCompileShader;
import static org.lwjgl.opengl.GL33C.glCreateProgram;
import static org.lwjgl.opengl.GL33C.glCreateShader;
import static org.lwjgl.opengl.GL33C.glDeleteProgram;
import static org.lwjgl.opengl.GL33C.glDeleteShader;
import static org.lwjgl.opengl.GL33C.glGetProgramInfoLog;
import static org.lwjgl.opengl.GL33C.glGetProgrami;
import static org.lwjgl.opengl.GL33C.glGetShaderInfoLog;
import static org.lwjgl.opengl.GL33C.glGetShaderi;
import static org.lwjgl.opengl.GL33C.glLinkProgram;
import static org.lwjgl.opengl.GL33C.glShaderSource;

/** Small OpenGL shader-program owner used by the native renderer. */
final class GpuShaderProgram implements AutoCloseable {

    private final int id;

    private GpuShaderProgram(int id) {
        this.id = id;
    }

    static GpuShaderProgram compile(String vertexSource, String fragmentSource) {
        return compile(vertexSource, null, fragmentSource);
    }

    static GpuShaderProgram compile(String vertexSource, String geometrySource, String fragmentSource) {
        int vertexShader = compileShader(GL_VERTEX_SHADER, vertexSource);
        int geometryShader = 0;
        int fragmentShader = 0;
        int program = 0;
        try {
            if (geometrySource != null) {
                geometryShader = compileShader(GL_GEOMETRY_SHADER, geometrySource);
            }
            fragmentShader = compileShader(GL_FRAGMENT_SHADER, fragmentSource);
            program = glCreateProgram();
            glAttachShader(program, vertexShader);
            if (geometryShader != 0) {
                glAttachShader(program, geometryShader);
            }
            glAttachShader(program, fragmentShader);
            glLinkProgram(program);
            if (glGetProgrami(program, GL_LINK_STATUS) == 0) {
                throw new IllegalStateException("OpenGL program link failed:\n" + glGetProgramInfoLog(program));
            }
            return new GpuShaderProgram(program);
        } catch (RuntimeException exception) {
            if (program != 0) {
                glDeleteProgram(program);
            }
            throw exception;
        } finally {
            glDeleteShader(vertexShader);
            if (geometryShader != 0) {
                glDeleteShader(geometryShader);
            }
            if (fragmentShader != 0) {
                glDeleteShader(fragmentShader);
            }
        }
    }

    private static int compileShader(int type, String source) {
        int shader = glCreateShader(type);
        glShaderSource(shader, source);
        glCompileShader(shader);
        if (glGetShaderi(shader, GL_COMPILE_STATUS) == 0) {
            String log = glGetShaderInfoLog(shader);
            glDeleteShader(shader);
            throw new IllegalStateException("OpenGL shader compile failed:\n" + log);
        }
        return shader;
    }

    int id() {
        return id;
    }

    @Override
    public void close() {
        glDeleteProgram(id);
    }
}
