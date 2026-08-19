// Decompiled by Jad v1.5.8f. Copyright 2001 Pavel Kouznetsov.
// Jad home page: http://www.kpdus.com/jad.html
// Decompiler options: packimports(3) 
package rs2;

import java.io.PrintStream;

import rs2.cache.Archive;
import rs2.collection.LruCache;
import rs2.net.Buffer;
import rs2.sign.signlink;

public class Class27
{

    public static void method305(Archive class2, int i)
    {
        Buffer class50_sub1_sub2 = new Buffer(class2.read("spotanim.dat"));
        anInt553 = class50_sub1_sub2.readUnsignedShort();
        if(i != 36135)
            aBoolean551 = !aBoolean551;
        if(aClass27Array554 == null)
            aClass27Array554 = new Class27[anInt553];
        for(int j = 0; j < anInt553; j++)
        {
            if(aClass27Array554[j] == null)
                aClass27Array554[j] = new Class27();
            aClass27Array554[j].anInt555 = j;
            aClass27Array554[j].method306(aByte550, class50_sub1_sub2);
        }

    }

    public void method306(byte byte0, Buffer class50_sub1_sub2)
    {
        if(byte0 == 6)
            byte0 = 0;
        else
            anInt552 = 458;
        do
        {
            int i = class50_sub1_sub2.readUnsignedByte();
            if(i == 0)
                return;
            if(i == 1)
                anInt556 = class50_sub1_sub2.readUnsignedShort();
            else
            if(i == 2)
            {
                anInt557 = class50_sub1_sub2.readUnsignedShort();
                if(Class14.aClass14Array293 != null)
                    aClass14_558 = Class14.aClass14Array293[anInt557];
            } else
            if(i == 4)
                anInt561 = class50_sub1_sub2.readUnsignedShort();
            else
            if(i == 5)
                anInt562 = class50_sub1_sub2.readUnsignedShort();
            else
            if(i == 6)
                anInt563 = class50_sub1_sub2.readUnsignedShort();
            else
            if(i == 7)
                anInt564 = class50_sub1_sub2.readUnsignedByte();
            else
            if(i == 8)
                anInt565 = class50_sub1_sub2.readUnsignedByte();
            else
            if(i >= 40 && i < 50)
                anIntArray559[i - 40] = class50_sub1_sub2.readUnsignedShort();
            else
            if(i >= 50 && i < 60)
                anIntArray560[i - 50] = class50_sub1_sub2.readUnsignedShort();
            else
                System.out.println("Error unrecognised spotanim config code: " + i);
        } while(true);
    }

    public Class50_Sub1_Sub4_Sub4 method307()
    {
        Class50_Sub1_Sub4_Sub4 class50_sub1_sub4_sub4 = (Class50_Sub1_Sub4_Sub4)aClass33_566.get(anInt555);
        if(class50_sub1_sub4_sub4 != null)
            return class50_sub1_sub4_sub4;
        class50_sub1_sub4_sub4 = Class50_Sub1_Sub4_Sub4.method577(anInt556);
        if(class50_sub1_sub4_sub4 == null)
            return null;
        for(int i = 0; i < 6; i++)
            if(anIntArray559[0] != 0)
                class50_sub1_sub4_sub4.method591(anIntArray559[i], anIntArray560[i]);

        aClass33_566.put(anInt555, class50_sub1_sub4_sub4);
        return class50_sub1_sub4_sub4;
    }

    public Class27()
    {
        anInt552 = -214;
        anInt557 = -1;
        anIntArray559 = new int[6];
        anIntArray560 = new int[6];
        anInt561 = 128;
        anInt562 = 128;
    }

    public static byte aByte550 = 6;
    public static boolean aBoolean551 = true;
    public int anInt552;
    public static int anInt553;
    public static Class27 aClass27Array554[];
    public int anInt555;
    public int anInt556;
    public int anInt557;
    public Class14 aClass14_558;
    public int anIntArray559[];
    public int anIntArray560[];
    public int anInt561;
    public int anInt562;
    public int anInt563;
    public int anInt564;
    public int anInt565;
    public static LruCache aClass33_566 = new LruCache(30);

}
