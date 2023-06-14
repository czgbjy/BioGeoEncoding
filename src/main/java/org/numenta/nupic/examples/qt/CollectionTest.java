package org.numenta.nupic.examples.qt;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;


public class CollectionTest{
	
	public static String datafile="C:\\Users\\czg\\Desktop\\out.out";
	interface Function
	{
		void func(String s);
	}
	public static Function get()
	{
		return (Function&Serializable)((s)->System.out.println(s));
	}
	
	public static void main(String[] args) throws IOException
	{
		Function function=get();
		function.func("我是大哥");
		ObjectOutputStream outputStream=new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream(datafile)));
		outputStream.writeObject(function);
		System.out.println("输出完成...");
		outputStream.close();
	}
}