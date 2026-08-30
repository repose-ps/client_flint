package rs2.ui;

import rs2.cache.def.AnimationSequence;
import rs2.cache.def.IdentityKit;
import rs2.game.entity.Actor;
import rs2.game.entity.Player;
import rs2.game.entity.PlayerAppearancePalettes;
import rs2.media.Angle;
import rs2.media.model.Model;
import rs2.media.sprite.ImageRGB;
import rs2.net.Buffer;
import rs2.net.OutgoingPacketOpcode;

/**
 * Owns the character-design interface state and appearance preview model.
 *
 * <p>The editor retains the classic revision-377 seven identity-kit selections,
 * five recolour selections, and sex toggle. It also owns the cached gender
 * button sprites used by the appearance widgets.</p>
 */
public final class AppearanceEditor {

    /** Number of identity-kit body-part selections sent by revision 377. */
    public static final int BODY_PART_COUNT = 7;
    /** Number of appearance recolour selections sent by revision 377. */
    public static final int COLOR_SLOT_COUNT = 5;

    /** Selected identity-kit IDs for the seven body-part slots. */
    private final int[] kitIds = new int[BODY_PART_COUNT];
    /** Selected recolour indices for the five colour slots. */
    private final int[] colors = new int[COLOR_SLOT_COUNT];

    /** Whether the editor is currently selecting the male kit range. */
    private boolean male = true;
    /** Whether the preview model must be rebuilt before it is next drawn. */
    private boolean modelDirty;
    /** Original male-button sprite captured from the cache-defined widget. */
    private ImageRGB maleButtonSprite;
    /** Original female-button sprite captured from the cache-defined widget. */
    private ImageRGB femaleButtonSprite;

    /** Creates an editor with the original zero-initialized appearance state. */
    public AppearanceEditor() {
    }

    /** Resets the editor to the login/default appearance state. */
    public void resetForLogin() {
        male = true;
        for (int slot = 0; slot < colors.length; slot++) {
            colors[slot] = 0;
        }
        resetKits();
    }

    /** Selects the first valid identity kit for every body-part slot. */
    public void resetKits() {
        modelDirty = true;
        for (int bodyPart = 0; bodyPart < kitIds.length; bodyPart++) {
            kitIds[bodyPart] = -1;
            int expectedPart = bodyPart + (male ? 0 : BODY_PART_COUNT);
            for (int kitId = 0; kitId < IdentityKit.count; kitId++) {
                IdentityKit kit = IdentityKit.definitions[kitId];
                if (kit.nonSelectable || kit.bodyPartId != expectedPart) {
                    continue;
                }
                kitIds[bodyPart] = kitId;
                break;
            }
        }
    }

    /**
     * Cycles one body-part identity kit in the requested direction.
     *
     * @param bodyPart zero-based appearance body-part slot
     * @param direction {@code 0} for previous or {@code 1} for next
     */
    public void cycleKit(int bodyPart, int direction) {
        int kitId = kitIds[bodyPart];
        if (kitId == -1) {
            return;
        }
        int expectedPart = bodyPart + (male ? 0 : BODY_PART_COUNT);
        do {
            if (direction == 0 && --kitId < 0) {
                kitId = IdentityKit.count - 1;
            }
            if (direction == 1 && ++kitId >= IdentityKit.count) {
                kitId = 0;
            }
        } while (IdentityKit.definitions[kitId].nonSelectable
                || IdentityKit.definitions[kitId].bodyPartId != expectedPart);
        kitIds[bodyPart] = kitId;
        modelDirty = true;
    }

    /**
     * Cycles one appearance recolour in the requested direction.
     *
     * @param colorSlot zero-based recolour slot
     * @param direction {@code 0} for previous or {@code 1} for next
     */
    public void cycleColor(int colorSlot, int direction) {
        int colorIndex = colors[colorSlot];
        if (direction == 0 && --colorIndex < 0) {
            colorIndex = PlayerAppearancePalettes.bodyColorCount(colorSlot) - 1;
        }
        if (direction == 1 && ++colorIndex >= PlayerAppearancePalettes.bodyColorCount(colorSlot)) {
            colorIndex = 0;
        }
        colors[colorSlot] = colorIndex;
        modelDirty = true;
    }

    /** Selects the male identity-kit range if it is not already active. */
    public void selectMale() {
        if (!male) {
            male = true;
            resetKits();
        }
    }

    /** Selects the female identity-kit range if it is not already active. */
    public void selectFemale() {
        if (male) {
            male = false;
            resetKits();
        }
    }

    /**
     * Writes the revision-377 appearance-update packet from the current editor
     * selections.
     *
     * @param outgoing outgoing game buffer
     */
    public void writeUpdate(Buffer outgoing) {
        outgoing.writeOpcode(OutgoingPacketOpcode.APPEARANCE_UPDATE);
        outgoing.writeByte(male ? 0 : 1);
        for (int kitId : kitIds) {
            outgoing.writeByte(kitId);
        }
        for (int color : colors) {
            outgoing.writeByte(color);
        }
    }

    /**
     * Updates the rotating character-design preview widget, rebuilding the cached
     * model when appearance selections have changed and all kit models are ready.
     *
     * @param widget appearance-preview widget
     * @param gameCycle current client cycle
     * @param localPlayer local player supplying the idle animation
     */
    public void updatePreview(Widget widget, int gameCycle, Player localPlayer) {
        widget.modelPitch = 150;
        widget.modelYaw = (int) (Math.sin((double) gameCycle / 40D) * 256D) & Angle.MASK;
        if (!modelDirty) {
            return;
        }
        for (int kitId : kitIds) {
            if (kitId >= 0 && !IdentityKit.definitions[kitId].areBodyModelsReady()) {
                return;
            }
        }

        modelDirty = false;
        Model[] parts = new Model[BODY_PART_COUNT];
        int modelCount = 0;
        for (int kitId : kitIds) {
            if (kitId >= 0) {
                parts[modelCount++] = IdentityKit.definitions[kitId].buildBodyModel();
            }
        }

        Model model = new Model(modelCount, parts);
        for (int colorSlot = 0; colorSlot < colors.length; colorSlot++) {
            int color = colors[colorSlot];
            if (color == 0) {
                continue;
            }
            model.recolor(PlayerAppearancePalettes.bodyColor(colorSlot, 0),
                    PlayerAppearancePalettes.bodyColor(colorSlot, color));
            if (colorSlot == 1) {
                model.recolor(PlayerAppearancePalettes.skinColor(0), PlayerAppearancePalettes.skinColor(color));
            }
        }

        model.createBones();
        model.applyTransformation(AnimationSequence.sequences[((Actor) localPlayer).idleSequence].primaryFrameIds[0]);
        model.light(64, 850, -30, -50, -30, true);
        widget.mediaType = Widget.MEDIA_CACHED_MODEL;
        widget.mediaId = 0;
        Widget.cacheModel(Widget.MEDIA_CACHED_MODEL, 0, model);
    }

    /**
     * Applies the appropriate selected/unselected sprite to a sex-selection widget.
     *
     * @param widget widget being updated
     * @param maleButton whether this is the male selection widget
     */
    public void updateGenderButton(Widget widget, boolean maleButton) {
        if (maleButtonSprite == null) {
            maleButtonSprite = widget.sprite;
            femaleButtonSprite = widget.activeSprite;
        }
        if (maleButton) {
            widget.sprite = male ? femaleButtonSprite : maleButtonSprite;
        } else {
            widget.sprite = male ? maleButtonSprite : femaleButtonSprite;
        }
    }
}
