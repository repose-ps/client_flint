// Decompiled by Jad v1.5.8f. Copyright 2001 Pavel Kouznetsov.
// Jad home page: http://www.kpdus.com/jad.html
// Decompiler options: packimports(3) 
package rs2.scene.tile;

import rs2.Class50_Sub1_Sub4;

/**
 * A single renderable placed on the floor of a scene tile.
 *
 * <p>Unlike an interactive object, a floor decoration occupies one tile and
 * does not maintain a multi-tile footprint.</p>
 */
public class FloorDecoration {

    /** World-space elevation of the decoration. */
    public int z;

    /** World-space x-coordinate, normally the centre of the tile. */
    public int x;

    /** World-space y-coordinate, normally the centre of the tile. */
    public int y;

    /** Model rendered for the decoration. */
    public Class50_Sub1_Sub4 renderable;

    /** Packed identifier used by scene queries and menu actions. */
    public int uid;

    /** Packed scene configuration associated with {@link #uid}. */
    public byte config;
}