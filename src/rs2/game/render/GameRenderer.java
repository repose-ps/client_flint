package rs2.game.render;

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.image.BufferedImage;

import rs2.cache.Archive;
import rs2.game.ActorSynchronizer;
import rs2.game.CameraController;
import rs2.game.SceneEntityRenderer;
import rs2.game.WorldState;
import rs2.game.entity.Player;
import rs2.media.Angle;
import rs2.media.GraphicsBuffer;
import rs2.media.Rasterizer;
import rs2.media.Rasterizer3D;
import rs2.media.model.Model;
import rs2.media.sprite.ImageRGB;
import rs2.media.sprite.IndexedImage;
import rs2.net.Buffer;
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
	private GraphicsBuffer backLeft1Buffer;
	/** Classic bottom-left frame-decoration surface. */
	private GraphicsBuffer backLeft2Buffer;
	/** Classic upper-right frame-decoration surface. */
	private GraphicsBuffer backRight1Buffer;
	/** Classic lower-right frame-decoration surface. */
	private GraphicsBuffer backRight2Buffer;
	/** Classic top border surface. */
	private GraphicsBuffer backTop1Buffer;
	/** First classic vertical-middle border surface. */
	private GraphicsBuffer backVerticalMiddle1Buffer;
	/** Second classic vertical-middle border surface. */
	private GraphicsBuffer backVerticalMiddle2Buffer;
	/** Third classic vertical-middle border surface. */
	private GraphicsBuffer backVerticalMiddle3Buffer;
	/** Classic horizontal-middle border surface. */
	private GraphicsBuffer backHorizontalMiddle2Buffer;

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
	/** Texture ids scrolled by the classic animated-texture path. */
	private final int[] animatedTextureIds = { 17, 24, 34, 40 };
	/** Reusable scratch pixels exchanged with animated indexed textures. */
	private byte[] textureScrollScratch = new byte[16_384];

	/**
	 * Creates an empty renderer; buffers are allocated lazily after resource
	 * loading.
	 */
	public GameRenderer() {
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
		backLeft1Buffer = createDecorationBuffer(component, mediaArchive, "backleft1");
		backLeft2Buffer = createDecorationBuffer(component, mediaArchive, "backleft2");
		backRight1Buffer = createDecorationBuffer(component, mediaArchive, "backright1");
		backRight2Buffer = createDecorationBuffer(component, mediaArchive, "backright2");
		backTop1Buffer = createDecorationBuffer(component, mediaArchive, "backtop1");
		backVerticalMiddle1Buffer = createDecorationBuffer(component, mediaArchive, "backvmid1");
		backVerticalMiddle2Buffer = createDecorationBuffer(component, mediaArchive, "backvmid2");
		backVerticalMiddle3Buffer = createDecorationBuffer(component, mediaArchive, "backvmid3");
		backHorizontalMiddle2Buffer = createDecorationBuffer(component, mediaArchive, "backhmid2");
	}

	/**
	 * Draws the classic fixed-frame pieces over the resizable world underlay.
	 *
	 * @param graphics current presentation graphics
	 * @param layout   current fixed/resizable layout
	 */
	public void drawFrameDecorations(Graphics graphics, ClientLayout layout) {
		backLeft1Buffer.draw(graphics, 0, layout.viewportY());
		backLeft2Buffer.draw(graphics, 0, layout.chatboxY());
		backTop1Buffer.draw(graphics, 0, 0);
		backHorizontalMiddle2Buffer.draw(graphics, 0, layout.lowerBorderY());
		backRight1Buffer.draw(graphics, layout.rightFrameTopX(), layout.viewportY());
		backRight2Buffer.draw(graphics, layout.rightFrameMiddleX(), layout.sidebarY());
		backVerticalMiddle1Buffer.draw(graphics, layout.middleBorderX(), layout.viewportY());
		backVerticalMiddle2Buffer.draw(graphics, layout.middleBorderX(), layout.sidebarY());
		backVerticalMiddle3Buffer.draw(graphics, layout.bottomTabsX(), layout.lowerVerticalMiddleY());
	}

	/** Releases the classic frame-decoration surfaces. */
	public void clearFrameDecorations() {
		backLeft1Buffer = null;
		backLeft2Buffer = null;
		backRight1Buffer = null;
		backRight2Buffer = null;
		backTop1Buffer = null;
		backVerticalMiddle1Buffer = null;
		backVerticalMiddle2Buffer = null;
		backVerticalMiddle3Buffer = null;
		backHorizontalMiddle2Buffer = null;
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
		frame.worldState.updateProjectiles(frame.currentPlane, frame.gameCycle, frame.animationCycleDelta,
				frame.localPlayerServerIndex, frame.localPlayer, frame.actorSynchronizer, frame.outgoing);
		frame.worldState.updateGraphicsObjects(frame.currentPlane, frame.gameCycle, frame.animationCycleDelta);

		if (!frame.cameraController.cinematic) {
			int pitch = frame.cameraController.getMinimumPitchForRender();
			int yaw = frame.cameraController.followYaw + frame.cameraController.yawOffset & Angle.MASK;
			frame.cameraController.positionFromTarget(
					frame.worldState.getTileHeight(frame.localPlayer.x, frame.localPlayer.y, frame.currentPlane) - 50,
					frame.cameraController.followTargetX, pitch, 600 + pitch * 3, yaw,
					frame.cameraController.followTargetY);
		}
		int renderPlane = frame.cameraController.cinematic
				? frame.cameraController.selectCinematicRenderPlane(frame.worldState, frame.currentPlane)
				: frame.cameraController.selectNormalRenderPlane(frame.worldState, frame.currentPlane,
						frame.localPlayer, frame.outgoing);
		CameraController.Snapshot cameraSnapshot = frame.cameraController.snapshot();
		frame.cameraController.applyShake();

		int textureCycle = Rasterizer3D.textureCycle;
		Model.pickingEnabled = true;
		Model.pickedCount = 0;
		Model.mouseX = frame.mouseX - frame.layout.viewportX();
		Model.mouseY = frame.mouseY - frame.layout.viewportY();
		Rasterizer.resetPixels();
		frame.worldState.scene.render(frame.cameraController.x, frame.cameraController.y, frame.cameraController.height,
				renderPlane, frame.cameraController.yaw, frame.cameraController.pitch);
		frame.worldState.scene.clearTemporaryObjects();
		frame.actorOverlayRenderer.drawActors(frame.actorOverlayContext);
		frame.actorOverlayRenderer.drawWorldHint(frame.actorOverlayContext);
		animateTextures(textureCycle, frame.animationCycleDelta, frame.lowMemory);
		frame.viewportOverlayDrawer.run();
		viewportBuffer.draw(frame.graphics, frame.layout.viewportX(), frame.layout.viewportY());
		frame.cameraController.restore(cameraSnapshot);
		return destinationX;
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
		/**
		 * Outgoing revision-377 packet buffer used by render-plane
		 * selection/projectiles.
		 */
		final Buffer outgoing;
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
		/** Current client cycle. */
		final int gameCycle;
		/** Elapsed animation cycles since the previous draw. */
		final int animationCycleDelta;
		/** Local player protocol index. */
		final int localPlayerServerIndex;
		/** Current client mouse X. */
		final int mouseX;
		/** Current client mouse Y. */
		final int mouseY;
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
		 * @param outgoing               outgoing protocol buffer
		 * @param graphics               current presentation graphics
		 * @param viewportOverlayDrawer  viewport overlay callback
		 * @param destinationX           destination marker X
		 * @param destinationY           destination marker Y
		 * @param currentPlane           current scene plane
		 * @param gameCycle              current client cycle
		 * @param animationCycleDelta    elapsed animation cycles
		 * @param localPlayerServerIndex local player protocol index
		 * @param mouseX                 client mouse X
		 * @param mouseY                 client mouse Y
		 * @param lowMemory              low-memory rendering mode
		 */
		public SceneFrame(ClientLayout layout, SceneEntityRenderer sceneEntityRenderer, WorldState worldState,
				ActorSynchronizer actorSynchronizer, Player localPlayer, CameraController cameraController,
				ActorOverlayRenderer actorOverlayRenderer, ActorOverlayRenderer.Context actorOverlayContext,
				Buffer outgoing, Graphics graphics, Runnable viewportOverlayDrawer, int destinationX, int destinationY,
				int currentPlane, int gameCycle, int animationCycleDelta, int localPlayerServerIndex, int mouseX,
				int mouseY, boolean lowMemory) {
			this.layout = layout;
			this.sceneEntityRenderer = sceneEntityRenderer;
			this.worldState = worldState;
			this.actorSynchronizer = actorSynchronizer;
			this.localPlayer = localPlayer;
			this.cameraController = cameraController;
			this.actorOverlayRenderer = actorOverlayRenderer;
			this.actorOverlayContext = actorOverlayContext;
			this.outgoing = outgoing;
			this.graphics = graphics;
			this.viewportOverlayDrawer = viewportOverlayDrawer;
			this.destinationX = destinationX;
			this.destinationY = destinationY;
			this.currentPlane = currentPlane;
			this.gameCycle = gameCycle;
			this.animationCycleDelta = animationCycleDelta;
			this.localPlayerServerIndex = localPlayerServerIndex;
			this.mouseX = mouseX;
			this.mouseY = mouseY;
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
		}
		Graphics graphics = presentationBuffer.getGraphics();
		graphics.setColor(Color.black);
		graphics.fillRect(0, 0, width, height);
		return graphics;
	}

	/**
	 * Presents the already-composed off-screen image.
	 * 
	 * @param displayGraphics current AWT display graphics
	 */
	public void blitPresentation(Graphics displayGraphics) {
		if (presentationBuffer != null) {
			displayGraphics.drawImage(presentationBuffer, 0, 0, null);
		}
	}
}
