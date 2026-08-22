package rs2.cache.def;

import rs2.cache.Archive;
import rs2.collection.LruCache;
import rs2.media.ItemSpriteFactory;
import rs2.media.renderable.Model;
import rs2.net.Buffer;

public class ItemDefinition {

	public boolean areHeadModelsReady(int i) {
		int k = maleHeadModel0;
		int l = maleHeadModel1;
		if (i == 1) {
			k = femaleHeadModel0;
			l = femaleHeadModel1;
		}
		if (k == -1)
			return true;
		boolean flag = true;
		if (!Model.isLoaded(k))
			flag = false;
		if (l != -1 && !Model.isLoaded(l))
			flag = false;
		return flag;
	}

	public static ItemDefinition lookup(int i) {
		for (int j = 0; j < 10; j++)
			if (cache[j].id == i)
				return cache[j];

		cacheIndex = (cacheIndex + 1) % 10;
		ItemDefinition class16 = cache[cacheIndex];
		dataBuffer.position = offsets[i];
		class16.id = i;
		class16.method223();
		class16.decode(dataBuffer);
		if (class16.noteTemplateId != -1)
			class16.toNote();
		if (!membersWorld && class16.membersOnly) {
			class16.name = "Members Object";
			class16.description = "Login to a members' server to use this object.".getBytes();
			class16.groundActions = null;
			class16.inventoryActions = null;
			class16.team = 0;
		}
		return class16;
	}

	public Model getWearableModel(int i) {
		int j = maleModel0;
		int k = maleModel1;
		int l = maleModel2;
		if (i == 1) {
			j = femaleModel0;
			k = femaleModel1;
			l = femaleModel2;
		}
		if (j == -1)
			return null;
		Model class50_sub1_sub4_sub4 = Model.getModel(j);
		if (k != -1)
			if (l != -1) {
				Model class50_sub1_sub4_sub4_1 = Model.getModel(k);
				Model class50_sub1_sub4_sub4_3 = Model.getModel(l);
				Model aclass50_sub1_sub4_sub4_1[] = { class50_sub1_sub4_sub4, class50_sub1_sub4_sub4_1,
						class50_sub1_sub4_sub4_3 };
				class50_sub1_sub4_sub4 = new Model(3, aclass50_sub1_sub4_sub4_1);
			} else {
				Model class50_sub1_sub4_sub4_2 = Model.getModel(k);
				Model aclass50_sub1_sub4_sub4[] = { class50_sub1_sub4_sub4, class50_sub1_sub4_sub4_2 };
				class50_sub1_sub4_sub4 = new Model(2, aclass50_sub1_sub4_sub4);
			}
		if (i == 0 && maleOffset != 0)
			class50_sub1_sub4_sub4.translate(0, maleOffset, 0);
		if (i == 1 && femaleOffset != 0)
			class50_sub1_sub4_sub4.translate(0, femaleOffset, 0);
		if (recolorFrom != null) {
			for (int i1 = 0; i1 < recolorFrom.length; i1++)
				class50_sub1_sub4_sub4.recolor(recolorFrom[i1], recolorTo[i1]);

		}
		return class50_sub1_sub4_sub4;
	}

	public static void load(Archive class2) {
		dataBuffer = new Buffer(class2.read("obj.dat"));
		Buffer class50_sub1_sub2 = new Buffer(class2.read("obj.idx"));
		count = class50_sub1_sub2.readUnsignedShort();
		offsets = new int[count];
		int i = 2;
		for (int j = 0; j < count; j++) {
			offsets[j] = i;
			i += class50_sub1_sub2.readUnsignedShort();
		}

		cache = new ItemDefinition[10];
		for (int k = 0; k < 10; k++)
			cache[k] = new ItemDefinition();

	}

	public void toNote() {
		ItemDefinition class16 = lookup(noteTemplateId);
		modelId = class16.modelId;
		zoom2d = class16.zoom2d;
		xan2d = class16.xan2d;
		yan2d = class16.yan2d;
		zan2d = class16.zan2d;
		offsetX2d = class16.offsetX2d;
		offsetY2d = class16.offsetY2d;
		recolorFrom = class16.recolorFrom;
		recolorTo = class16.recolorTo;
		ItemDefinition class16_1 = lookup(noteId);
		name = class16_1.name;
		membersOnly = class16_1.membersOnly;
		price = class16_1.price;
		String s = "a";
		char c = class16_1.name.charAt(0);
		if (c == 'A' || c == 'E' || c == 'I' || c == 'O' || c == 'U')
			s = "an";
		description = ("Swap this note at any bank for " + s + " " + class16_1.name + ".").getBytes();
		stackable = true;
	}

	public boolean areWearableModelsReady(int j) {
		int k = maleModel0;
		int l = maleModel1;
		int i1 = maleModel2;
		if (j == 1) {
			k = femaleModel0;
			l = femaleModel1;
			i1 = femaleModel2;
		}
		if (k == -1)
			return true;
		boolean flag = true;
		if (!Model.isLoaded(k))
			flag = false;
		if (l != -1 && !Model.isLoaded(l))
			flag = false;
		if (i1 != -1 && !Model.isLoaded(i1))
			flag = false;
		return flag;
	}

	public Model getUnlitModel(int j) {
		if (stackVariantIds != null && j > 1) {
			int k = -1;
			for (int l = 0; l < 10; l++)
				if (j >= stackVariantAmounts[l] && stackVariantAmounts[l] != 0)
					k = stackVariantIds[l];

			if (k != -1)
				return lookup(k).getUnlitModel(1);
		}
		Model class50_sub1_sub4_sub4 = Model.getModel(modelId);
		if (class50_sub1_sub4_sub4 == null)
			return null;
		if (recolorFrom != null) {
			for (int i1 = 0; i1 < recolorFrom.length; i1++)
				class50_sub1_sub4_sub4.recolor(recolorFrom[i1], recolorTo[i1]);

		}
		return class50_sub1_sub4_sub4;
	}

	public void decode(Buffer class50_sub1_sub2) {
		do {
			int i = class50_sub1_sub2.readUnsignedByte();
			if (i == 0)
				return;
			if (i == 1)
				modelId = class50_sub1_sub2.readUnsignedShort();
			else if (i == 2)
				name = class50_sub1_sub2.readString();
			else if (i == 3)
				description = class50_sub1_sub2.readStringBytes();
			else if (i == 4)
				zoom2d = class50_sub1_sub2.readUnsignedShort();
			else if (i == 5)
				xan2d = class50_sub1_sub2.readUnsignedShort();
			else if (i == 6)
				yan2d = class50_sub1_sub2.readUnsignedShort();
			else if (i == 7) {
				offsetX2d = class50_sub1_sub2.readUnsignedShort();
				if (offsetX2d > 32767)
					offsetX2d -= 0x10000;
			} else if (i == 8) {
				offsetY2d = class50_sub1_sub2.readUnsignedShort();
				if (offsetY2d > 32767)
					offsetY2d -= 0x10000;
			} else if (i == 10)
				opcode10Value = class50_sub1_sub2.readUnsignedShort();
			else if (i == 11)
				stackable = true;
			else if (i == 12)
				price = class50_sub1_sub2.readInt();
			else if (i == 16)
				membersOnly = true;
			else if (i == 23) {
				maleModel0 = class50_sub1_sub2.readUnsignedShort();
				maleOffset = class50_sub1_sub2.readSignedByte();
			} else if (i == 24)
				maleModel1 = class50_sub1_sub2.readUnsignedShort();
			else if (i == 25) {
				femaleModel0 = class50_sub1_sub2.readUnsignedShort();
				femaleOffset = class50_sub1_sub2.readSignedByte();
			} else if (i == 26)
				femaleModel1 = class50_sub1_sub2.readUnsignedShort();
			else if (i >= 30 && i < 35) {
				if (groundActions == null)
					groundActions = new String[5];
				groundActions[i - 30] = class50_sub1_sub2.readString();
				if (groundActions[i - 30].equalsIgnoreCase("hidden"))
					groundActions[i - 30] = null;
			} else if (i >= 35 && i < 40) {
				if (inventoryActions == null)
					inventoryActions = new String[5];
				inventoryActions[i - 35] = class50_sub1_sub2.readString();
			} else if (i == 40) {
				int j = class50_sub1_sub2.readUnsignedByte();
				recolorFrom = new int[j];
				recolorTo = new int[j];
				for (int k = 0; k < j; k++) {
					recolorFrom[k] = class50_sub1_sub2.readUnsignedShort();
					recolorTo[k] = class50_sub1_sub2.readUnsignedShort();
				}

			} else if (i == 78)
				maleModel2 = class50_sub1_sub2.readUnsignedShort();
			else if (i == 79)
				femaleModel2 = class50_sub1_sub2.readUnsignedShort();
			else if (i == 90)
				maleHeadModel0 = class50_sub1_sub2.readUnsignedShort();
			else if (i == 91)
				femaleHeadModel0 = class50_sub1_sub2.readUnsignedShort();
			else if (i == 92)
				maleHeadModel1 = class50_sub1_sub2.readUnsignedShort();
			else if (i == 93)
				femaleHeadModel1 = class50_sub1_sub2.readUnsignedShort();
			else if (i == 95)
				zan2d = class50_sub1_sub2.readUnsignedShort();
			else if (i == 97)
				noteId = class50_sub1_sub2.readUnsignedShort();
			else if (i == 98)
				noteTemplateId = class50_sub1_sub2.readUnsignedShort();
			else if (i >= 100 && i < 110) {
				if (stackVariantIds == null) {
					stackVariantIds = new int[10];
					stackVariantAmounts = new int[10];
				}
				stackVariantIds[i - 100] = class50_sub1_sub2.readUnsignedShort();
				stackVariantAmounts[i - 100] = class50_sub1_sub2.readUnsignedShort();
			} else if (i == 110)
				resizeX = class50_sub1_sub2.readUnsignedShort();
			else if (i == 111)
				resizeY = class50_sub1_sub2.readUnsignedShort();
			else if (i == 112)
				resizeZ = class50_sub1_sub2.readUnsignedShort();
			else if (i == 113)
				ambient = class50_sub1_sub2.readSignedByte();
			else if (i == 114)
				contrast = class50_sub1_sub2.readSignedByte() * 5;
			else if (i == 115)
				team = class50_sub1_sub2.readUnsignedByte();
		} while (true);
	}

	public Model getHeadModel(int i) {
		int j = maleHeadModel0;
		int k = maleHeadModel1;
		if (i == 1) {
			j = femaleHeadModel0;
			k = femaleHeadModel1;
		}
		if (j == -1)
			return null;
		Model class50_sub1_sub4_sub4 = Model.getModel(j);
		if (k != -1) {
			Model class50_sub1_sub4_sub4_1 = Model.getModel(k);
			Model aclass50_sub1_sub4_sub4[] = { class50_sub1_sub4_sub4, class50_sub1_sub4_sub4_1 };
			class50_sub1_sub4_sub4 = new Model(2, aclass50_sub1_sub4_sub4);
		}
		if (recolorFrom != null) {
			for (int l = 0; l < recolorFrom.length; l++)
				class50_sub1_sub4_sub4.recolor(recolorFrom[l], recolorTo[l]);

		}
		return class50_sub1_sub4_sub4;
	}

	public Model getModel(int i) {
		if (stackVariantIds != null && i > 1) {
			int j = -1;
			for (int k = 0; k < 10; k++)
				if (i >= stackVariantAmounts[k] && stackVariantAmounts[k] != 0)
					j = stackVariantIds[k];

			if (j != -1)
				return lookup(j).getModel(1);
		}
		Model class50_sub1_sub4_sub4 = (Model) modelCache.get(id);
		if (class50_sub1_sub4_sub4 != null)
			return class50_sub1_sub4_sub4;
		class50_sub1_sub4_sub4 = Model.getModel(modelId);
		if (class50_sub1_sub4_sub4 == null)
			return null;
		if (resizeX != 128 || resizeY != 128 || resizeZ != 128)
			class50_sub1_sub4_sub4.scale(resizeX, resizeY, resizeZ);
		if (recolorFrom != null) {
			for (int l = 0; l < recolorFrom.length; l++)
				class50_sub1_sub4_sub4.recolor(recolorFrom[l], recolorTo[l]);

		}
		class50_sub1_sub4_sub4.light(64 + ambient, 768 + contrast, -50, -10, -50, true);
		class50_sub1_sub4_sub4.singleTile = true;
		modelCache.put(id, class50_sub1_sub4_sub4);
		return class50_sub1_sub4_sub4;
	}

	public static void clear() {
		modelCache = null;
		ItemSpriteFactory.clear();
		offsets = null;
		cache = null;
		dataBuffer = null;
	}

	public void method223() {
		modelId = 0;
		name = null;
		description = null;
		recolorFrom = null;
		recolorTo = null;
		zoom2d = 2000;
		xan2d = 0;
		yan2d = 0;
		zan2d = 0;
		offsetX2d = 0;
		offsetY2d = 0;
		opcode10Value = -1;
		stackable = false;
		price = 1;
		membersOnly = false;
		groundActions = null;
		inventoryActions = null;
		maleModel0 = -1;
		maleModel1 = -1;
		maleOffset = 0;
		femaleModel0 = -1;
		femaleModel1 = -1;
		femaleOffset = 0;
		maleModel2 = -1;
		femaleModel2 = -1;
		maleHeadModel0 = -1;
		maleHeadModel1 = -1;
		femaleHeadModel0 = -1;
		femaleHeadModel1 = -1;
		stackVariantIds = null;
		stackVariantAmounts = null;
		noteId = -1;
		noteTemplateId = -1;
		resizeX = 128;
		resizeY = 128;
		resizeZ = 128;
		ambient = 0;
		contrast = 0;
		team = 0;
	}

	public ItemDefinition() {
		id = -1;
	}

	public int femaleModel0;
	public int offsetX2d;
	public byte description[];
	public String name;
	public byte femaleOffset;
	public int maleModel1;
	public int team;
	public int noteId;
	public int maleHeadModel0;
	public static int count;
	public static ItemDefinition cache[];
	public static LruCache modelCache = new LruCache(50);
	public String groundActions[];
	public int zan2d;
	public int offsetY2d;
	public int recolorTo[];
	public static int offsets[];
	public int noteTemplateId;
	public static boolean membersWorld = true;
	public int price;
	public String inventoryActions[];
	public static int cacheIndex;
	public int maleModel0;
	public int ambient;
	public int femaleModel1;
	public int yan2d;
	public int resizeY;
	public int contrast;
	public int xan2d;
	public int modelId;
	public int maleHeadModel1;
	public int femaleHeadModel1;
	public int id;
	public int recolorFrom[];
	public int stackVariantIds[];
	public int resizeX;
	public int femaleModel2;
	public int resizeZ;
	public int zoom2d;
	public int maleModel2;
	public boolean stackable;
	public int opcode10Value;
	public static Buffer dataBuffer;
	public int femaleHeadModel0;
	public int stackVariantAmounts[];
	public boolean membersOnly;
	public byte maleOffset;

}
