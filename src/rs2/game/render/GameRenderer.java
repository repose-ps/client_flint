package rs2.game.render;

import rs2.gpu.GpuRenderer;
import rs2.shell.GameFrame;

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;

import rs2.cache.Archive;
import rs2.game.ActorSynchronizer;
import rs2.game.CameraController;
import rs2.game.SceneEntityRenderer;
import rs2.game.WorldState;
import rs2.game.entity.Player;
import rs2.media.GraphicsBuffer;
import rs2.media.Rasterizer;
import rs2.media.Rasterizer3D;
import rs2.media.sprite.ImageRGB;
import rs2.media.sprite.IndexedImage;
import rs2.scene.Scene;
import rs2.ui.ClientLayout;

/**
 * Owns size-dependent game rendering buffers, projection tables, redraw
 * invalidation state, and final frame composition.
 *
 * <p>
 * The renderer does not own world, protocol, interface, or chat state. Those
 * systems decide what needs to be drawn; this class owns the software surfaces
 * onto which they draw and the single-buffer AWT presentation step used by the
 * resizable client.
 * </p>
 */
public final class GameRenderer {

	/** Selected 3D world-rendering implementation. */
	private final WorldRenderer worldRenderer;
	/** Backend-only timing counters used for renderer comparisons. */
	private final RendererMetrics rendererMetrics = new RendererMetrics();

	/** Fixed chatbox software surface. */
	private GraphicsBuffer chatboxBuffer;
	/** Fixed minimap software surface. */
	private GraphicsBuffer minimapBuffer;
	/** Fixed sidebar software surface. */
	private GraphicsBuffer sidebarBuffer;
	/** Dynamically sized world viewport software surface. */
	private GraphicsBuffer viewportBuffer;
	/** Fixed chat-mode strip software surface. */
	private GraphicsBuffer chatModesBuffer;
	/** Fixed bottom-tab strip software surface. */
	private GraphicsBuffer bottomTabsBuffer;
	/** Fixed top-tab strip software surface. */
	private GraphicsBuffer topTabsBuffer;

	/** Classic top-left frame-decoration surface. */
	private GraphicsBuffer viewportLeftBorder;
	/** Classic bottom-left frame-decoration surface. */
	private GraphicsBuffer chatboxLeftBorder;
	/** Classic upper-right frame-decoration surface. */
	private GraphicsBuffer minimapRightBorder;
	/** Classic lower-right frame-decoration surface. */
	private GraphicsBuffer tabsRightBorder;
	/** Classic top border surface. */
	private GraphicsBuffer minimapTopBorder;
	/** First classic vertical-middle border surface. */
	private GraphicsBuffer minimapLeftBorder;
	/** Second classic vertical-middle border surface. */
	private GraphicsBuffer tabsLeftBorderTop;
	/** Third classic vertical-middle border surface. */
	private GraphicsBuffer chatTabsVerticalBorder;
	/** Classic horizontal-middle border surface. */
	private GraphicsBuffer chatboxTopBorder;

	/**
	 * The array index for bottom-side resizable UI elements.
	 */
	public static final int RESIZABLE_ELEMENT_BOTTOM_INDEX = 0;

	/**
	 * The suffix for bottom-side box elements in the resizable UI.
	 */
	private static final String RESIZABLE_ELEMENT_BOTTOM = "_bottom";

	/**
	 * The array index for left-side resizable UI elements.
	 */
	public static final int RESIZABLE_ELEMENT_LEFT_INDEX = 1;

	/**
	 * The suffix for left-side box elements in the resizable UI.
	 */
	private static final String RESIZABLE_ELEMENT_LEFT = "_left";

	/**
	 * The array index for right-side resizable UI elements.
	 */
	public static final int RESIZABLE_ELEMENT_RIGHT_INDEX = 2;

	/**
	 * The suffix for right-side box elements in the resizable UI.
	 */
	private static final String RESIZABLE_ELEMENT_RIGHT = "_right";

	/**
	 * The array index for top-side resizable UI elements.
	 */
	public static final int RESIZABLE_ELEMENT_TOP_INDEX = 3;

	/**
	 * The suffix for top-side box elements in the resizable UI.
	 */
	private static final String RESIZABLE_ELEMENT_TOP = "_top";

	/**
	 * The amount of UI elements for a resizable boxed element.
	 */
	private static final int RESIZABLE_BOX_ELEMENT_COUNT = 4;

	/**
	 * The prefix for the resizable chatbox UI.
	 */
	private static final String RESIZABLE_CHATBOX_PREFIX = "resizable_chat";

	/**
	 * The prefix for the resizable minimap UI.
	 */
	private static final String RESIZABLE_MINIMAP_PREFIX = "resizable_map";

	/**
	 * The prefix for the resizable tabs area UI.
	 */
	private static final String RESIZABLE_TABS_PREFIX = "resizable_tabs";

	/**
	 * The resizable chat UI elements.
	 */
	private GraphicsBuffer[] resizableChatBuffers;

	/**
	 * The resizable chatbox UI elements.
	 */
	private GraphicsBuffer[] resizableMapBuffers;

	/**
	 * The resizable tab area UI elements.
	 */
	private GraphicsBuffer[] resizableTabAreaBuffers;

	/** Immutable source pixels used to restore mutable resizable chat decorations. */
	private int[][] resizableChatBasePixels;
	/** Immutable source pixels used to restore mutable resizable minimap decorations. */
	private int[][] resizableMapBasePixels;
	/** Immutable source pixels used to restore mutable resizable tab decorations. */
	private int[][] resizableTabAreaBasePixels;

	/** 3D scanline offsets for the current viewport dimensions. */
	private int[] viewportScanlineOffsets;
	/**
	 * 3D scanline offsets for the classic 765x503 full-screen interface surface.
	 */
	private int[] fullScreenScanlineOffsets;
	/** 3D scanline offsets for the chatbox surface. */
	private int[] chatboxScanlineOffsets;
	/** 3D scanline offsets for the sidebar surface. */
	private int[] sidebarScanlineOffsets;

	/** Pending legacy whole-screen redraw event used by keepalive cadence. */
	private boolean gameScreenRedraw;
	/** Whether the sidebar must be rerasterized. */
	private boolean sidebarRedraw;
	/** Whether the chatbox must be rerasterized. */
	private boolean chatboxRedraw;
	/** Whether the tab strips must be rerasterized. */
	private boolean tabAreaRedraw;
	/** Whether the chat-mode strip must be rerasterized. */
	private boolean chatModesRedraw;

	/** Client-sized off-screen image used for one-blit AWT presentation. */
	private BufferedImage presentationBuffer;
	/** Direct pixel storage for the presentation image, used by native HUD composition. */
	private int[] presentationPixels;
	/** Texture ids scrolled by the classic animated-texture path. */
	private final int[] animatedTextureIds = { 17, 24, 34, 40 };
	/** Reusable scratch pixels exchanged with animated indexed textures. */
	private byte[] textureScrollScratch = new byte[16_384];

	/**
	 * Creates an empty renderer; buffers are allocated lazily after resource
	 * loading.
	 */
	public GameRenderer() {
		this(createConfiguredWorldRenderer());
	}

	/**
	 * Creates a renderer around an explicit world backend.
	 *
	 * <p>
	 * Public primarily as the stable integration boundary for Phase 1 and renderer
	 * tests; normal client startup uses {@link #GameRenderer()}.
	 * </p>
	 *
	 * @param worldRenderer 3D world-rendering implementation
	 */
	public GameRenderer(WorldRenderer worldRenderer) {
		if (worldRenderer == null) {
			throw new NullPointerException("worldRenderer");
		}
		this.worldRenderer = worldRenderer;
	}

	/**
	 * Returns the active 3D renderer backend.
	 *
	 * @return active renderer backend
	 */
	public RendererBackend rendererBackend() {
		return worldRenderer.backend();
	}

	/**
	 * Returns backend-only timing counters for diagnostics.
	 *
	 * @return renderer metrics
	 */
	public RendererMetrics rendererMetrics() {
		return rendererMetrics;
	}

	/** Creates the startup-selected world renderer. */
	private static WorldRenderer createConfiguredWorldRenderer() {
		RendererBackend backend = RendererBackend.configured();
		return switch (backend) {
		case SOFTWARE -> new SoftwareWorldRenderer();
		case GPU -> new GpuRenderer();
		};
	}

	/** Attaches native renderer surfaces after the standalone AWT frame exists. */
	public void attachToFrame(GameFrame frame) {
		if (worldRenderer instanceof GpuRenderer gpuRenderer) {
			gpuRenderer.attach(frame);
		}
	}

	/** Releases backend-specific native resources during client shutdown. */
	public void closeWorldRenderer() {
		if (worldRenderer instanceof AutoCloseable closeable) {
			try {
				closeable.close();
			} catch (Exception exception) {
				throw new IllegalStateException("Failed to close world renderer", exception);
			}
		}
	}

	/** Shows or hides any native world surface without changing renderer state. */
	public void setWorldSurfaceActive(boolean active) {
		if (worldRenderer instanceof GpuRenderer gpuRenderer) {
			gpuRenderer.setSurfaceActive(active);
		}
	}

	/**
	 * Captures fixed-area scanline tables and builds the initial viewport
	 * projection tables.
	 *
	 * @param layout current client layout
	 */
	public void initializeProjectionTables(ClientLayout layout) {
		Rasterizer3D.setBounds(ClientLayout.FIXED_WIDTH, ClientLayout.FIXED_HEIGHT);
		fullScreenScanlineOffsets = Rasterizer3D.scanlineOffsets;
		Rasterizer3D.setBounds(ClientLayout.CHATBOX_WIDTH, ClientLayout.CHATBOX_HEIGHT);
		chatboxScanlineOffsets = Rasterizer3D.scanlineOffsets;
		Rasterizer3D.setBounds(ClientLayout.SIDEBAR_WIDTH, ClientLayout.SIDEBAR_HEIGHT);
		sidebarScanlineOffsets = Rasterizer3D.scanlineOffsets;
		rebuildViewportProjection(layout);
	}

	/**
	 * Rebuilds projection scanlines and scene visibility maps for the current
	 * viewport dimensions.
	 *
	 * @param layout current client layout
	 */
	public void rebuildViewportProjection(ClientLayout layout) {
		Rasterizer3D.setBounds(layout.viewportWidth(), layout.viewportHeight());
		viewportScanlineOffsets = Rasterizer3D.scanlineOffsets;
		int[] visibilityPitchHeights = new int[9];
		for (int pitchIndex = 0; pitchIndex < visibilityPitchHeights.length; pitchIndex++) {
			int pitchAngle = 128 + pitchIndex * 32 + 15;
			int projectionDistance = 600 + pitchAngle * 3;
			int pitchSine = Rasterizer3D.SINE[pitchAngle];
			visibilityPitchHeights[pitchIndex] = projectionDistance * pitchSine >> 16;
		}
		Scene.buildVisibilityMaps(500, 800, layout.viewportWidth(), layout.viewportHeight(), visibilityPitchHeights);
	}

	/**
	 * Reallocates only the dynamic viewport surface after a client resize.
	 *
	 * @param component AWT drawing component
	 * @param layout    current client layout
	 */
	public void resizeViewport(Component component, ClientLayout layout) {
		if (viewportBuffer != null) {
			viewportBuffer = new GraphicsBuffer(component, layout.viewportWidth(), layout.viewportHeight());
		}
		rebuildViewportProjection(layout);
		bindViewport();
		invalidateAll();
	}

	/**
	 * Allocates the fixed HUD buffers and current-size world viewport if they are
	 * not already present.
	 *
	 * @param component         AWT drawing component
	 * @param layout            current layout
	 * @param minimapBackground fixed minimap frame image
	 * @return {@code true} when buffers were newly allocated
	 */
	public boolean createGameScreenBuffers(Component component, ClientLayout layout, IndexedImage minimapBackground) {
		if (chatboxBuffer != null) {
			return false;
		}
		chatboxBuffer = new GraphicsBuffer(component, ClientLayout.CHATBOX_WIDTH, ClientLayout.CHATBOX_HEIGHT);
		minimapBuffer = new GraphicsBuffer(component, ClientLayout.MINIMAP_WIDTH, ClientLayout.MINIMAP_HEIGHT);
		Rasterizer.resetPixels();
		minimapBackground.draw(0, 0);
		sidebarBuffer = new GraphicsBuffer(component, ClientLayout.SIDEBAR_WIDTH, ClientLayout.SIDEBAR_HEIGHT);
		viewportBuffer = new GraphicsBuffer(component, layout.viewportWidth(), layout.viewportHeight());
		Rasterizer.resetPixels();
		chatModesBuffer = new GraphicsBuffer(component, ClientLayout.CHAT_MODES_WIDTH, ClientLayout.CHAT_MODES_HEIGHT);
		bottomTabsBuffer = new GraphicsBuffer(component, ClientLayout.BOTTOM_TABS_WIDTH,
				ClientLayout.BOTTOM_TABS_HEIGHT);
		topTabsBuffer = new GraphicsBuffer(component, ClientLayout.TOP_TABS_WIDTH, ClientLayout.TOP_TABS_HEIGHT);
		gameScreenRedraw = true;
		invalidatePanels();
		bindViewport();
		return true;
	}

	/**
	 * Decodes the classic fixed-frame decoration sprites into reusable software
	 * buffers.
	 *
	 * @param component    AWT drawing component
	 * @param mediaArchive loaded media archive
	 */
	public void initializeFrameDecorations(Component component, Archive mediaArchive) {
		viewportLeftBorder = createDecorationBuffer(component, mediaArchive, "viewportleftborder");
		chatboxLeftBorder = createDecorationBuffer(component, mediaArchive, "chatboxleftborder");
		minimapRightBorder = createDecorationBuffer(component, mediaArchive, "minimaprightborder");
		tabsRightBorder = createDecorationBuffer(component, mediaArchive, "tabsrightborder");
		minimapTopBorder = createDecorationBuffer(component, mediaArchive, "minimaptopborder");
		minimapLeftBorder = createDecorationBuffer(component, mediaArchive, "minimapleftborder");
		tabsLeftBorderTop = createDecorationBuffer(component, mediaArchive, "tabsleftbordertop");
		chatTabsVerticalBorder = createDecorationBuffer(component, mediaArchive, "chattabsverticalborder");
		chatboxTopBorder = createDecorationBuffer(component, mediaArchive, "chatboxtopborder");

		// resizable elements
		resizableChatBuffers = new GraphicsBuffer[RESIZABLE_BOX_ELEMENT_COUNT];
		resizableMapBuffers = new GraphicsBuffer[RESIZABLE_BOX_ELEMENT_COUNT];
		resizableTabAreaBuffers = new GraphicsBuffer[RESIZABLE_BOX_ELEMENT_COUNT];

		initResizableUiBox(component, mediaArchive, RESIZABLE_CHATBOX_PREFIX, resizableChatBuffers);
		initResizableUiBox(component, mediaArchive, RESIZABLE_MINIMAP_PREFIX, resizableMapBuffers);
		initResizableUiBox(component, mediaArchive, RESIZABLE_TABS_PREFIX, resizableTabAreaBuffers);
		resizableChatBasePixels = snapshotPixels(resizableChatBuffers);
		resizableMapBasePixels = snapshotPixels(resizableMapBuffers);
		resizableTabAreaBasePixels = snapshotPixels(resizableTabAreaBuffers);
	}

	private void initResizableUiBox(Component component, Archive mediaArchive, String prefix,
			GraphicsBuffer[] bufferArray) {
		bufferArray[RESIZABLE_ELEMENT_BOTTOM_INDEX] = createDecorationBuffer(component, mediaArchive,
				prefix + RESIZABLE_ELEMENT_BOTTOM);
		bufferArray[RESIZABLE_ELEMENT_LEFT_INDEX] = createDecorationBuffer(component, mediaArchive,
				prefix + RESIZABLE_ELEMENT_LEFT);
		bufferArray[RESIZABLE_ELEMENT_RIGHT_INDEX] = createDecorationBuffer(component, mediaArchive,
				prefix + RESIZABLE_ELEMENT_RIGHT);
		bufferArray[RESIZABLE_ELEMENT_TOP_INDEX] = createDecorationBuffer(component, mediaArchive,
				prefix + RESIZABLE_ELEMENT_TOP);
	}

	/** Copies each decoration's original raster so text/highlights can be redrawn cleanly. */
	private static int[][] snapshotPixels(GraphicsBuffer[] buffers) {
		int[][] snapshots = new int[buffers.length][];
		for (int index = 0; index < buffers.length; index++) {
			snapshots[index] = buffers[index].pixels.clone();
		}
		return snapshots;
	}

	/** Restores and binds one mutable resizable decoration as the software raster. */
	private static void restoreAndBind(GraphicsBuffer[] buffers, int[][] basePixels, int index) {
		GraphicsBuffer buffer = buffers[index];
		System.arraycopy(basePixels[index], 0, buffer.pixels, 0, buffer.pixels.length);
		buffer.bindRaster();
	}

	/**
	 * Draws the classic fixed-frame pieces over the resizable world underlay.
	 *
	 * @param graphics current presentation graphics
	 * @param layout   current fixed/resizable layout
	 */
	public void drawFrameDecorations(Graphics graphics, ClientLayout layout) {
		if (!layout.isResizableMode()) {
			viewportLeftBorder.draw(graphics, 0, layout.viewportY());
			chatboxLeftBorder.draw(graphics, 0, layout.chatboxY());
			minimapTopBorder.draw(graphics, layout.width() - minimapTopBorder.getWidth(), 0);
			chatboxTopBorder.draw(graphics, 0, layout.lowerBorderY());
			minimapRightBorder.draw(graphics, layout.rightFrameTopX(), layout.viewportY());
			tabsRightBorder.draw(graphics, layout.rightFrameMiddleX(), layout.sidebarY());
			minimapLeftBorder.draw(graphics, layout.middleBorderX(), layout.viewportY());
			tabsLeftBorderTop.draw(graphics, layout.middleBorderX(), layout.sidebarY());
			chatTabsVerticalBorder.draw(graphics, layout.bottomTabsX(), layout.lowerVerticalMiddleY());
			return;
		}

		// Top-right minimap frame. The classic 172x156 minimap is composited into
		// the aperture after these pieces are drawn.
		resizableMapBuffers[RESIZABLE_ELEMENT_LEFT_INDEX].draw(graphics, layout.minimapFrameX(),
				layout.minimapFrameY());
		resizableMapBuffers[RESIZABLE_ELEMENT_TOP_INDEX].draw(graphics, layout.minimapX(),
				layout.minimapFrameY());
		resizableMapBuffers[RESIZABLE_ELEMENT_RIGHT_INDEX].draw(graphics,
				layout.width() - resizableMapBuffers[RESIZABLE_ELEMENT_RIGHT_INDEX].getWidth(), layout.minimapFrameY());
		resizableMapBuffers[RESIZABLE_ELEMENT_BOTTOM_INDEX].draw(graphics, layout.minimapX(),
				layout.minimapY() + ClientLayout.MINIMAP_HEIGHT);

		// Bottom-right tab/sidebar frame. Top and bottom are also the mutable tab
		// strip rasters; they are redrawn later with highlights/icons on top.
		resizableTabAreaBuffers[RESIZABLE_ELEMENT_LEFT_INDEX].draw(graphics, layout.tabsFrameX(),
				layout.tabsFrameY());
		resizableTabAreaBuffers[RESIZABLE_ELEMENT_TOP_INDEX].draw(graphics, layout.topTabsX(),
				layout.topTabsY());
		resizableTabAreaBuffers[RESIZABLE_ELEMENT_RIGHT_INDEX].draw(graphics,
				layout.width() - resizableTabAreaBuffers[RESIZABLE_ELEMENT_RIGHT_INDEX].getWidth(), layout.tabsFrameY());
		resizableTabAreaBuffers[RESIZABLE_ELEMENT_BOTTOM_INDEX].draw(graphics, layout.bottomTabsX(),
				layout.bottomTabsY());

		// Bottom-left chat frame. The custom bottom piece supplies the resizable
		// chat-mode button background and is redrawn later with mode labels.
		resizableChatBuffers[RESIZABLE_ELEMENT_LEFT_INDEX].draw(graphics, layout.chatFrameX(), layout.chatFrameY());
		resizableChatBuffers[RESIZABLE_ELEMENT_TOP_INDEX].draw(graphics, layout.chatboxX(), layout.chatFrameY());
		resizableChatBuffers[RESIZABLE_ELEMENT_RIGHT_INDEX].draw(graphics,
				layout.chatFrameX() + ClientLayout.RESIZABLE_CHAT_FRAME_WIDTH
						- resizableChatBuffers[RESIZABLE_ELEMENT_RIGHT_INDEX].getWidth(),
				layout.chatFrameY());
		resizableChatBuffers[RESIZABLE_ELEMENT_BOTTOM_INDEX].draw(graphics, layout.resizableChatModesX(),
				layout.chatModesY());
	}

	/** Releases the classic frame-decoration surfaces. */
	public void clearFrameDecorations() {
		viewportLeftBorder = null;
		chatboxLeftBorder = null;
		minimapRightBorder = null;
		tabsRightBorder = null;
		minimapTopBorder = null;
		minimapLeftBorder = null;
		tabsLeftBorderTop = null;
		chatTabsVerticalBorder = null;
		chatboxTopBorder = null;

		resizableChatBuffers = null;
		resizableMapBuffers = null;
		resizableTabAreaBuffers = null;
		resizableChatBasePixels = null;
		resizableMapBasePixels = null;
		resizableTabAreaBasePixels = null;
	}

	/**
	 * Creates and rasterizes one fixed frame-decoration sprite.
	 * 
	 * @param component    AWT drawing component
	 * @param mediaArchive loaded media archive
	 * @param spriteName   archive sprite name
	 * @return software buffer containing the decoded decoration
	 */
	private GraphicsBuffer createDecorationBuffer(Component component, Archive mediaArchive, String spriteName) {
		ImageRGB sprite = new ImageRGB(mediaArchive, spriteName, 0);
		GraphicsBuffer buffer = new GraphicsBuffer(component, sprite.width, sprite.height);
		sprite.drawInverse(0, 0);
		return buffer;
	}

	/** Releases all logged-in rendering surfaces. */
	public void clearGameScreenBuffers() {
		chatboxBuffer = null;
		minimapBuffer = null;
		sidebarBuffer = null;
		viewportBuffer = null;
		chatModesBuffer = null;
		bottomTabsBuffer = null;
		topTabsBuffer = null;
	}

	/** Binds the dynamic viewport as the active software raster when available. */
	public void bindViewport() {
		if (viewportBuffer != null) {
			viewportBuffer.bindRaster();
			Rasterizer3D.scanlineOffsets = viewportScanlineOffsets;
		}
	}

	/** Binds the chatbox software raster. */
	public void bindChatbox() {
		chatboxBuffer.bindRaster();
		Rasterizer3D.scanlineOffsets = chatboxScanlineOffsets;
	}

	/** Binds the sidebar software raster. */
	public void bindSidebar() {
		sidebarBuffer.bindRaster();
		Rasterizer3D.scanlineOffsets = sidebarScanlineOffsets;
	}

	/** Assigns the full-screen projection scanlines to the active 3D rasterizer. */
	public void bindFullScreenScanlines() {
		Rasterizer3D.scanlineOffsets = fullScreenScanlineOffsets;
	}

	/**
	 * Returns the viewport scanline table for legacy rendering collaborators.
	 * 
	 * @return current viewport scanline offsets
	 */
	public int[] viewportScanlineOffsets() {
		return viewportScanlineOffsets;
	}

	/**
	 * Returns the dynamic viewport buffer, or {@code null} before game-screen
	 * initialization.
	 * 
	 * @return viewport software surface, or {@code null}
	 */
	public GraphicsBuffer viewportBuffer() {
		return viewportBuffer;
	}

	/**
	 * Returns the minimap buffer, or {@code null} before game-screen
	 * initialization.
	 * 
	 * @return minimap software surface, or {@code null}
	 */
	public GraphicsBuffer minimapBuffer() {
		return minimapBuffer;
	}

	/**
	 * Returns the sidebar buffer, or {@code null} before game-screen
	 * initialization.
	 * 
	 * @return sidebar software surface, or {@code null}
	 */
	public GraphicsBuffer sidebarBuffer() {
		return sidebarBuffer;
	}

	/**
	 * Returns the chatbox buffer, or {@code null} before game-screen
	 * initialization.
	 * 
	 * @return chatbox software surface, or {@code null}
	 */
	public GraphicsBuffer chatboxBuffer() {
		return chatboxBuffer;
	}

	/**
	 * Returns the chat-mode strip buffer, or {@code null} before initialization.
	 * 
	 * @return chat-mode software surface, or {@code null}
	 */
	public GraphicsBuffer chatModesBuffer() {
		return chatModesBuffer;
	}

	/**
	 * Returns the top-tab strip buffer, or {@code null} before initialization.
	 * 
	 * @return top-tab software surface, or {@code null}
	 */
	public GraphicsBuffer topTabsBuffer() {
		return topTabsBuffer;
	}

	/**
	 * Returns the bottom-tab strip buffer, or {@code null} before initialization.
	 * 
	 * @return bottom-tab software surface, or {@code null}
	 */
	public GraphicsBuffer bottomTabsBuffer() {
		return bottomTabsBuffer;
	}

	/** Requests a legacy game-screen redraw event. */
	public void requestGameScreenRedraw() {
		gameScreenRedraw = true;
	}

	/**
	 * Returns and clears the legacy game-screen redraw event.
	 * 
	 * @return whether a redraw event had been pending
	 */
	public boolean consumeGameScreenRedraw() {
		boolean redraw = gameScreenRedraw;
		gameScreenRedraw = false;
		return redraw;
	}

	/** Requests sidebar rerasterization. */
	public void requestSidebarRedraw() {
		sidebarRedraw = true;
	}

	/**
	 * Returns whether sidebar rerasterization is pending.
	 * 
	 * @return whether sidebar redraw is pending
	 */
	public boolean sidebarRedrawPending() {
		return sidebarRedraw;
	}

	/** Clears the sidebar redraw request. */
	public void clearSidebarRedraw() {
		sidebarRedraw = false;
	}

	/** Requests chatbox rerasterization. */
	public void requestChatboxRedraw() {
		chatboxRedraw = true;
	}

	/**
	 * Returns whether chatbox rerasterization is pending.
	 * 
	 * @return whether chatbox redraw is pending
	 */
	public boolean chatboxRedrawPending() {
		return chatboxRedraw;
	}

	/** Clears the chatbox redraw request. */
	public void clearChatboxRedraw() {
		chatboxRedraw = false;
	}

	/** Requests tab-strip rerasterization. */
	public void requestTabAreaRedraw() {
		tabAreaRedraw = true;
	}

	/**
	 * Returns whether tab-strip rerasterization is pending.
	 * 
	 * @return whether tab-strip redraw is pending
	 */
	public boolean tabAreaRedrawPending() {
		return tabAreaRedraw;
	}

	/** Clears the tab-strip redraw request. */
	public void clearTabAreaRedraw() {
		tabAreaRedraw = false;
	}

	/** Requests chat-mode strip rerasterization. */
	public void requestChatModesRedraw() {
		chatModesRedraw = true;
	}

	/**
	 * Returns whether chat-mode strip rerasterization is pending.
	 * 
	 * @return whether chat-mode redraw is pending
	 */
	public boolean chatModesRedrawPending() {
		return chatModesRedraw;
	}

	/** Clears the chat-mode strip redraw request. */
	public void clearChatModesRedraw() {
		chatModesRedraw = false;
	}

	/** Requests every fixed UI area plus the legacy game-screen event. */
	public void invalidateAll() {
		gameScreenRedraw = true;
		sidebarRedraw = true;
		chatboxRedraw = true;
		tabAreaRedraw = true;
		chatModesRedraw = true;
	}

	/**
	 * Requests every fixed UI panel without synthesizing a game-screen keepalive
	 * event.
	 */
	public void invalidatePanels() {
		sidebarRedraw = true;
		chatboxRedraw = true;
		tabAreaRedraw = true;
		chatModesRedraw = true;
	}

	/**
	 * Builds and renders one 3D world frame, including actor overlays, world hints,
	 * texture animation, and viewport overlays.
	 *
	 * @param frame current scene rendering dependencies and frame state
	 * @return updated destination X marker after local-player arrival handling
	 */
	public int renderScene(SceneFrame frame) {
		int destinationX = frame.sceneEntityRenderer.beginFrame(frame.localPlayer, frame.destinationX,
				frame.destinationY);
		frame.sceneEntityRenderer.addPlayers(frame.worldState, frame.actorSynchronizer, frame.localPlayer,
				frame.currentPlane, frame.gameCycle, frame.lowMemory, true);
		frame.sceneEntityRenderer.addNpcs(frame.worldState, frame.actorSynchronizer, frame.currentPlane, true);
		frame.sceneEntityRenderer.addPlayers(frame.worldState, frame.actorSynchronizer, frame.localPlayer,
				frame.currentPlane, frame.gameCycle, frame.lowMemory, false);
		frame.sceneEntityRenderer.addNpcs(frame.worldState, frame.actorSynchronizer, frame.currentPlane, false);
		frame.worldState.submitProjectiles(frame.currentPlane, frame.gameCycle);
		frame.worldState.submitGraphicsObjects(frame.currentPlane, frame.gameCycle);

		CameraController.Snapshot logicalCamera = frame.cameraController.snapshot();
		frame.cameraController.restore(frame.cameraController.interpolatedSnapshot(frame.interpolationAlpha));
		frame.cameraController.applyShake();

		WorldRenderFrame worldFrame = new WorldRenderFrame(frame.worldState.scene, frame.cameraController.x,
				frame.cameraController.y, frame.cameraController.height, frame.renderPlane, frame.cameraController.yaw,
				frame.cameraController.pitch, frame.mouseX - frame.layout.viewportX(),
				frame.mouseY - frame.layout.viewportY(), frame.layout.viewportX(), frame.layout.viewportY(),
				frame.layout.viewportWidth(), frame.layout.viewportHeight());
		boolean gpuWorld = worldRenderer.backend() == RendererBackend.GPU;
		/*
		 * A native AWTGLCanvas is a heavyweight child above the parent's Java2D
		 * viewport. Legacy viewport UI therefore cannot be made visible by drawing it
		 * into the parent after OpenGL. For GPU mode, rasterize legacy overlays first
		 * and hand the small opaque region that needs native composition to the GPU
		 * backend before it presents the frame.
		 */
		if (gpuWorld) {
			frame.actorOverlayRenderer.drawActors(frame.actorOverlayContext);
			frame.actorOverlayRenderer.drawWorldHint(frame.actorOverlayContext);
			frame.viewportOverlayDrawer.run();
			if (frame.viewportOverlayRegions.length != 0 && worldRenderer instanceof GpuRenderer gpuRenderer) {
				gpuRenderer.setViewportOverlays(clippedViewportOverlays(frame.viewportOverlayRegions));
			}
		}

		long worldRenderStarted = System.nanoTime();
		worldRenderer.render(worldFrame);
		rendererMetrics.recordWorldRender(System.nanoTime() - worldRenderStarted);
		frame.worldState.scene.clearTemporaryObjects();
		if (!gpuWorld) {
			frame.actorOverlayRenderer.drawActors(frame.actorOverlayContext);
			frame.actorOverlayRenderer.drawWorldHint(frame.actorOverlayContext);
			frame.viewportOverlayDrawer.run();
			viewportBuffer.draw(frame.graphics, frame.layout.viewportX(), frame.layout.viewportY());
		}
		frame.cameraController.restore(logicalCamera);
		return destinationX;
	}

	/** Clips all requested viewport overlay regions, preserving their presentation order. */
	private SoftwareViewportOverlay[] clippedViewportOverlays(ViewportOverlayRegion[] regions) {
		SoftwareViewportOverlay[] overlays = new SoftwareViewportOverlay[regions.length];
		int count = 0;
		for (ViewportOverlayRegion region : regions) {
			SoftwareViewportOverlay overlay = clippedViewportOverlay(region);
			if (overlay != null) {
				overlays[count++] = overlay;
			}
		}
		if (count == overlays.length) {
			return overlays;
		}
		SoftwareViewportOverlay[] clipped = new SoftwareViewportOverlay[count];
		System.arraycopy(overlays, 0, clipped, 0, count);
		return clipped;
	}

	/**
	 * Clips one legacy viewport-overlay request to the actual software raster.
	 *
	 * <p>The original menu layout intentionally uses a one-pixel difference between
	 * its bottom-edge clamp height and its drawn height. The software Rasterizer
	 * simply clips that last pixel. The GPU overlay bridge must mirror that behavior
	 * instead of treating the legacy overhang as a fatal bounds error. Clipping here
	 * also makes overlay presentation tolerant of a viewport resize between layout
	 * calculation and buffer replacement.</p>
	 */
	private SoftwareViewportOverlay clippedViewportOverlay(ViewportOverlayRegion region) {
		if (viewportBuffer == null) {
			return null;
		}

		int sourceWidth = viewportBuffer.getWidth();
		int sourceHeight = viewportBuffer.getHeight();
		int x = Math.min(region.x(), sourceWidth);
		int y = Math.min(region.y(), sourceHeight);
		int right = (int) Math.min((long) sourceWidth, (long) region.x() + region.width());
		int bottom = (int) Math.min((long) sourceHeight, (long) region.y() + region.height());
		if (right <= x || bottom <= y) {
			return null;
		}
		return new SoftwareViewportOverlay(viewportBuffer.pixels, sourceWidth, x, y, right - x, bottom - y,
				region.transparentPixelKey());
	}

	/** Immutable dependencies and state needed to render one world frame. */
	public static final class SceneFrame {
		/** Current client layout. */
		final ClientLayout layout;
		/** Scene actor insertion renderer. */
		final SceneEntityRenderer sceneEntityRenderer;
		/** Current local world state. */
		final WorldState worldState;
		/** Synchronized actor registry. */
		final ActorSynchronizer actorSynchronizer;
		/** Local player instance. */
		final Player localPlayer;
		/** Camera state and projection controller. */
		final CameraController cameraController;
		/** Actor overlay renderer. */
		final ActorOverlayRenderer actorOverlayRenderer;
		/** Per-frame actor overlay state/assets. */
		final ActorOverlayRenderer.Context actorOverlayContext;
		/** Current off-screen presentation graphics. */
		final Graphics graphics;
		/** Callback that draws non-actor viewport overlays after the scene. */
		final Runnable viewportOverlayDrawer;
		/** Current destination marker X tile. */
		final int destinationX;
		/** Current destination marker Y tile. */
		final int destinationY;
		/** Current scene plane. */
		final int currentPlane;
		/** Roof-filtered render plane resolved by the fixed logic update. */
		final int renderPlane;
		/** Current client cycle. */
		final int gameCycle;
		/** Interpolation fraction between previous/current fixed logic camera state. */
		final float interpolationAlpha;
		/** Current client mouse X. */
		final int mouseX;
		/** Current client mouse Y. */
		final int mouseY;
		/** Legacy viewport rectangles to composite above native GL in presentation order. */
		final ViewportOverlayRegion[] viewportOverlayRegions;
		/** Whether low-memory rendering behavior is active. */
		final boolean lowMemory;

		/**
		 * Creates one scene-frame description.
		 *
		 * @param layout                 current client layout
		 * @param sceneEntityRenderer    scene entity renderer
		 * @param worldState             world state
		 * @param actorSynchronizer      synchronized actor registry
		 * @param localPlayer            local player
		 * @param cameraController       camera controller
		 * @param actorOverlayRenderer   actor overlay renderer
		 * @param actorOverlayContext    actor overlay frame context
		 * @param graphics               current presentation graphics
		 * @param viewportOverlayDrawer  viewport overlay callback
		 * @param destinationX           destination marker X
		 * @param destinationY           destination marker Y
		 * @param currentPlane           current scene plane
		 * @param renderPlane            roof-filtered render plane
		 * @param gameCycle              current client cycle
		 * @param interpolationAlpha     render interpolation fraction
		 * @param mouseX                 client mouse X
		 * @param mouseY                 client mouse Y
		 * @param viewportOverlayRegions software overlay rectangles to composite above native GL
		 * @param lowMemory              low-memory rendering mode
		 */
		public SceneFrame(ClientLayout layout, SceneEntityRenderer sceneEntityRenderer, WorldState worldState,
				ActorSynchronizer actorSynchronizer, Player localPlayer, CameraController cameraController,
				ActorOverlayRenderer actorOverlayRenderer, ActorOverlayRenderer.Context actorOverlayContext, Graphics graphics,
				Runnable viewportOverlayDrawer, int destinationX, int destinationY, int currentPlane, int renderPlane,
				int gameCycle, float interpolationAlpha, int mouseX, int mouseY, ViewportOverlayRegion[] viewportOverlayRegions,
				boolean lowMemory) {
			this.layout = layout;
			this.sceneEntityRenderer = sceneEntityRenderer;
			this.worldState = worldState;
			this.actorSynchronizer = actorSynchronizer;
			this.localPlayer = localPlayer;
			this.cameraController = cameraController;
			this.actorOverlayRenderer = actorOverlayRenderer;
			this.actorOverlayContext = actorOverlayContext;
			this.graphics = graphics;
			this.viewportOverlayDrawer = viewportOverlayDrawer;
			this.destinationX = destinationX;
			this.destinationY = destinationY;
			this.currentPlane = currentPlane;
			this.renderPlane = renderPlane;
			this.gameCycle = gameCycle;
			this.interpolationAlpha = interpolationAlpha;
			this.mouseX = mouseX;
			this.mouseY = mouseY;
			this.viewportOverlayRegions = viewportOverlayRegions == null ? new ViewportOverlayRegion[0]
					: viewportOverlayRegions;
			this.lowMemory = lowMemory;
		}
	}

	/**
	 * Scrolls the classic animated textures that were used during the current
	 * scene-render cycle.
	 *
	 * @param textureCycle        rasterizer texture-usage cycle threshold
	 * @param animationCycleDelta elapsed client animation cycles
	 * @param lowMemory           whether low-memory rendering disables texture
	 *                            animation
	 */
	public void animateTextures(int textureCycle, int animationCycleDelta, boolean lowMemory) {
		if (lowMemory) {
			return;
		}
		for (int textureId : animatedTextureIds) {
			if (Rasterizer3D.textureLastUsed[textureId] < textureCycle) {
				continue;
			}
			IndexedImage texture = Rasterizer3D.textures[textureId];
			int pixelMask = texture.width * texture.height - 1;
			int scrollOffset = texture.width * animationCycleDelta * 2;
			byte[] sourcePixels = texture.pixels;
			byte[] scrolledPixels = textureScrollScratch;
			for (int pixelIndex = 0; pixelIndex <= pixelMask; pixelIndex++) {
				scrolledPixels[pixelIndex] = sourcePixels[pixelIndex - scrollOffset & pixelMask];
			}
			texture.pixels = scrolledPixels;
			textureScrollScratch = sourcePixels;
			Rasterizer3D.releaseTexture(textureId);
			Rasterizer3D.markTextureChanged(textureId);
		}
	}

	/**
	 * Returns a cleared graphics context for the current off-screen presentation
	 * surface.
	 * 
	 * @param width  drawable client width
	 * @param height drawable client height
	 * @return graphics context targeting the presentation image
	 */
	public Graphics presentationGraphics(int width, int height) {
		if (presentationBuffer == null || presentationBuffer.getWidth() != width
				|| presentationBuffer.getHeight() != height) {
			presentationBuffer = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
			presentationPixels = ((DataBufferInt) presentationBuffer.getRaster().getDataBuffer()).getData();
			Graphics initialGraphics = presentationBuffer.getGraphics();
			try {
				initialGraphics.setColor(Color.black);
				initialGraphics.fillRect(0, 0, width, height);
			} finally {
				initialGraphics.dispose();
			}
		}
		return presentationBuffer.getGraphics();
	}

	/**
	 * Supplies the three opaque resizable HUD boxes to the native renderer.
	 *
	 * <p>The OpenGL canvas is a heavyweight child covering the complete resizable
	 * world underlay, so Java2D content drawn into the parent cannot appear above
	 * it. The legacy HUD is already composed into {@link #presentationBuffer}; this
	 * method exposes only the minimap, tabs/sidebar, and chat rectangles to OpenGL
	 * instead of copying the entire client-sized image.</p>
	 *
	 * @param layout current resizable client layout
	 */
	public void prepareResizableGpuHud(ClientLayout layout) {
		if (!layout.isResizableMode() || presentationBuffer == null || presentationPixels == null
				|| !(worldRenderer instanceof GpuRenderer gpuRenderer)) {
			return;
		}

		SoftwareViewportOverlay minimap = presentationHudOverlay(layout, layout.minimapFrameX(),
				layout.minimapFrameY(), ClientLayout.RESIZABLE_MINIMAP_FRAME_WIDTH,
				ClientLayout.RESIZABLE_MINIMAP_FRAME_HEIGHT);
		SoftwareViewportOverlay tabs = presentationHudOverlay(layout, layout.tabsFrameX(), layout.tabsFrameY(),
				ClientLayout.RESIZABLE_TABS_FRAME_WIDTH, ClientLayout.RESIZABLE_TABS_FRAME_HEIGHT);
		SoftwareViewportOverlay chat = presentationHudOverlay(layout, layout.chatFrameX(), layout.chatFrameY(),
				ClientLayout.RESIZABLE_CHAT_FRAME_WIDTH, ClientLayout.RESIZABLE_CHAT_FRAME_HEIGHT);

		SoftwareViewportOverlay[] overlays = new SoftwareViewportOverlay[3];
		int count = 0;
		if (minimap != null) overlays[count++] = minimap;
		if (tabs != null) overlays[count++] = tabs;
		if (chat != null) overlays[count++] = chat;
		if (count != overlays.length) {
			SoftwareViewportOverlay[] clipped = new SoftwareViewportOverlay[count];
			System.arraycopy(overlays, 0, clipped, 0, count);
			overlays = clipped;
		}
		gpuRenderer.setHudOverlays(overlays);
	}

	/** Builds one client-space HUD rectangle clipped to the native viewport child. */
	private SoftwareViewportOverlay presentationHudOverlay(ClientLayout layout, int x, int y, int width, int height) {
		int viewportX = layout.viewportX();
		int viewportY = layout.viewportY();
		int viewportRight = viewportX + layout.viewportWidth();
		int viewportBottom = viewportY + layout.viewportHeight();
		int left = Math.max(x, viewportX);
		int top = Math.max(y, viewportY);
		int right = Math.min(x + width, viewportRight);
		int bottom = Math.min(y + height, viewportBottom);
		if (right <= left || bottom <= top) {
			return null;
		}

		return new SoftwareViewportOverlay(presentationPixels, presentationBuffer.getWidth(), left, top,
				left - viewportX, top - viewportY, right - left, bottom - top);
	}

	/**
	 * Presents the composed software image. When the native GPU viewport is active,
	 * only pixels outside that child canvas are copied through Java2D; the viewport
	 * itself is already presented by OpenGL and copying the hidden backing pixels is
	 * pure memory bandwidth.
	 *
	 * @param displayGraphics     current AWT display graphics
	 * @param layout              current client layout
	 * @param nativeViewportActive whether the native world child is covering the viewport
	 */
	public void blitPresentation(Graphics displayGraphics, ClientLayout layout, boolean nativeViewportActive) {
		if (presentationBuffer == null) {
			return;
		}
		if (!nativeViewportActive || worldRenderer.backend() != RendererBackend.GPU) {
			displayGraphics.drawImage(presentationBuffer, 0, 0, null);
			return;
		}

		int width = presentationBuffer.getWidth();
		int height = presentationBuffer.getHeight();
		int viewportX = clamp(layout.viewportX(), 0, width);
		int viewportY = clamp(layout.viewportY(), 0, height);
		int viewportRight = clamp(layout.viewportX() + layout.viewportWidth(), viewportX, width);
		int viewportBottom = clamp(layout.viewportY() + layout.viewportHeight(), viewportY, height);

		blitRegion(displayGraphics, 0, 0, width, viewportY);
		blitRegion(displayGraphics, 0, viewportBottom, width, height);
		blitRegion(displayGraphics, 0, viewportY, viewportX, viewportBottom);
		blitRegion(displayGraphics, viewportRight, viewportY, width, viewportBottom);
	}

	private void blitRegion(Graphics graphics, int x1, int y1, int x2, int y2) {
		if (x2 <= x1 || y2 <= y1) {
			return;
		}
		graphics.drawImage(presentationBuffer, x1, y1, x2, y2, x1, y1, x2, y2, null);
	}

	private static int clamp(int value, int minimum, int maximum) {
		return Math.max(minimum, Math.min(maximum, value));
	}

	/** Restores and binds the resizable chat-mode button strip for text drawing. */
	public void bindResizableChatModes() {
		restoreAndBind(resizableChatBuffers, resizableChatBasePixels, RESIZABLE_ELEMENT_BOTTOM_INDEX);
	}

	/** Draws the resizable chat-mode strip at its bottom-left anchored position. */
	public void drawResizableChatModes(Graphics graphics, ClientLayout layout) {
		resizableChatBuffers[RESIZABLE_ELEMENT_BOTTOM_INDEX].draw(graphics, layout.resizableChatModesX(),
				layout.chatModesY());
	}

	/**
	 * Restores all four resizable tab-frame pieces before tab highlights and icons
	 * are rasterized across their original 377 coordinates.
	 */
	public void beginResizableTabsFrame() {
		for (int index = 0; index < RESIZABLE_BOX_ELEMENT_COUNT; index++) {
			GraphicsBuffer buffer = resizableTabAreaBuffers[index];
			System.arraycopy(resizableTabAreaBasePixels[index], 0, buffer.pixels, 0, buffer.pixels.length);
		}
	}

	/**
	 * Draws one classic tab sprite over the custom resizable frame. The custom
	 * resources are literal crops of the original tab backgrounds, so preserving
	 * the original sprite coordinates produces pixel-exact alignment with their
	 * stone recesses. Sprites are drawn into every frame piece they can overlap;
	 * normal raster clipping handles the crop boundaries.
	 *
	 * @param sprite classic sidebar icon or redstone highlight
	 * @param tab    tab index 0..13
	 * @param x      original fixed-strip sprite X coordinate
	 * @param y      original fixed-strip sprite Y coordinate
	 */
	public void drawResizableTabSprite(IndexedImage sprite, int tab, int x, int y) {
		if (tab < 7) {
			drawResizableTopTabSprite(sprite, x, y);
		} else {
			drawResizableBottomTabSprite(sprite, x, y);
		}
	}

	/** Draws all four currently composed resizable tab-frame pieces. */
	public void drawResizableTabsFrame(Graphics graphics, ClientLayout layout) {
		resizableTabAreaBuffers[RESIZABLE_ELEMENT_LEFT_INDEX].draw(graphics, layout.tabsFrameX(), layout.tabsFrameY());
		resizableTabAreaBuffers[RESIZABLE_ELEMENT_TOP_INDEX].draw(graphics, layout.topTabsX(), layout.topTabsY());
		resizableTabAreaBuffers[RESIZABLE_ELEMENT_RIGHT_INDEX].draw(graphics,
			layout.width() - resizableTabAreaBuffers[RESIZABLE_ELEMENT_RIGHT_INDEX].getWidth(), layout.tabsFrameY());
		resizableTabAreaBuffers[RESIZABLE_ELEMENT_BOTTOM_INDEX].draw(graphics, layout.bottomTabsX(),
			layout.bottomTabsY());
	}

	/** Rasterizes a top-row tab sprite across the left/top/right crop pieces. */
	private void drawResizableTopTabSprite(IndexedImage sprite, int x, int y) {
		int mappedY = y - ClientLayout.RESIZABLE_TOP_TABS_SOURCE_Y;

		resizableTabAreaBuffers[RESIZABLE_ELEMENT_LEFT_INDEX].bindRaster();
		sprite.draw(x, mappedY);

		resizableTabAreaBuffers[RESIZABLE_ELEMENT_TOP_INDEX].bindRaster();
		sprite.draw(x - ClientLayout.RESIZABLE_TOP_TABS_SOURCE_X, mappedY);

		GraphicsBuffer right = resizableTabAreaBuffers[RESIZABLE_ELEMENT_RIGHT_INDEX];
		right.bindRaster();
		sprite.draw(x - (ClientLayout.RESIZABLE_TABS_FRAME_WIDTH - right.getWidth()), mappedY);
	}

	/** Rasterizes a bottom-row tab sprite across the left/bottom/right crop pieces. */
	private void drawResizableBottomTabSprite(IndexedImage sprite, int x, int y) {
		GraphicsBuffer left = resizableTabAreaBuffers[RESIZABLE_ELEMENT_LEFT_INDEX];
		int sideY = left.getHeight() - ClientLayout.RESIZABLE_BOTTOM_TAB_STRIP_HEIGHT + y;
		left.bindRaster();
		sprite.draw(x - ClientLayout.RESIZABLE_BOTTOM_TABS_FRAME_SOURCE_X, sideY);

		resizableTabAreaBuffers[RESIZABLE_ELEMENT_BOTTOM_INDEX].bindRaster();
		sprite.draw(x - ClientLayout.RESIZABLE_BOTTOM_TABS_SOURCE_X, y);

		GraphicsBuffer right = resizableTabAreaBuffers[RESIZABLE_ELEMENT_RIGHT_INDEX];
		right.bindRaster();
		sprite.draw(x - ClientLayout.RESIZABLE_BOTTOM_TABS_RIGHT_SOURCE_X, sideY);
	}

	public GraphicsBuffer[] getResizableChatBuffers() {
		return resizableChatBuffers;
	}

	public GraphicsBuffer[] getResizableMapBuffers() {
		return resizableMapBuffers;
	}

	public GraphicsBuffer[] getResizableTabAreaBuffers() {
		return resizableTabAreaBuffers;
	}
}
