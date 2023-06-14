package org.numenta.nupic.util;

import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.netlib.util.doubleW;
import org.numenta.nupic.algorithms.SpatialPooler;
import org.numenta.nupic.encoders.DateEncoder;
import org.numenta.nupic.encoders.RandomDistributedScalarEncoder;
import org.numenta.nupic.model.Connections;

public class htmtestmain {
	
	public static void runHotgym(int numRecords)
	{
		DateEncoder dateEncoder=(DateEncoder)DateEncoder.builder().timeOfDay(21,1).weekend(21).build();
		DateTimeFormatter format = DateTimeFormat .forPattern("yyyy-MM-dd HH:mm:ss");
		String dateString="2010-7-2 00:00:00";
		DateTime dateTime=DateTime.parse(dateString,format);
		int[] outputs=new int[dateEncoder.getWidth()];
		dateEncoder.encodeIntoArray(dateTime, outputs);
		
		for(int i=0;i<outputs.length;i++)
		{
			System.out.print(outputs[i]);
		}
		
		
		//resolution是一个浮点型正数，表示输出表达的分辨率，在[offset-resolution/2，offset+resolution/2]内大小的数字将会落入相同的桶中，
		//进而有着相同的表达（编码），相邻的桶将会有一个字节的差异，分辨率是一个必须的参数。
		RandomDistributedScalarEncoder scalarEncoder=RandomDistributedScalarEncoder.builder().resolution(0.88).build();
		int[] scarOutputs=new int[scalarEncoder.getWidth()];
		double consumptionValue=21.2d;
		scalarEncoder.encodeIntoArray(consumptionValue, scarOutputs);
		
		for(int i=0;i<scarOutputs.length;i++)
		{
			System.out.print(scarOutputs[i]);
		}
		
		
		int[] encoding=new int[outputs.length+scarOutputs.length];
		System.arraycopy(outputs, 0, encoding, 0, outputs.length);
		System.arraycopy(scarOutputs, 0, encoding, outputs.length, scarOutputs.length);
		
		SpatialPooler spatialPooler=new SpatialPooler();
		Connections c=new Connections();
		c.setNumInputs(encoding.length);
		//c.setColumnDimensions(new int[]{32,64});
		//c.setConnectedCounts(new int[]{3,6});
		AbstractSparseBinaryMatrix matrix=new AbstractSparseBinaryMatrix(new int[]{32,64}) {
			
			@Override
			public AbstractFlatMatrix set(int index, Object value) {
				// TODO Auto-generated method stub
				return null;
			}
			
			@Override
			public AbstractSparseBinaryMatrix setForTest(int index, int value) {
				// TODO Auto-generated method stub
				return null;
			}
			
			@Override
			public AbstractSparseBinaryMatrix set(int value, int... coordinates) {
				// TODO Auto-generated method stub
				return null;
			}
			
			@Override
			public void rightVecSumAtNZ(int[] inputVector, int[] results,
					double stimulusThreshold) {
				// TODO Auto-generated method stub
				
			}
			
			@Override
			public void rightVecSumAtNZ(int[] inputVector, int[] results) {
				// TODO Auto-generated method stub
				
			}
			
			@Override
			public Object getSlice(int... coordinates) {
				// TODO Auto-generated method stub
				return null;
			}
			
			@Override
			public Integer get(int index) {
				// TODO Auto-generated method stub
				return null;
			}
		};
		c.setConnectedMatrix(matrix);
		c.setNumColumns(2048);
		c.setPotentialPct(0.85);
		c.setGlobalInhibition(true);
		c.setLocalAreaDensity(-1);
		c.setNumActiveColumnsPerInhArea(40);
		c.setSynPermInactiveDec(0.005);
		c.setSynPermActiveInc(0.04);
		c.setSynPermConnected(0.1);
		c.setMaxBoost(3);
		c.setSeed(1956);
		c.setWrapAround(false);
		
		
		double[] boostFactors=new double[2048];
		for (int i = 0; i < 2048; i++) {
			boostFactors[i]=Math.random();
		}
		c.setBoostFactors(boostFactors);
	
		//spatialPooler.init(c);
		int[] activeSpatialArray=new int[2048];
		spatialPooler.compute(c, encoding, activeSpatialArray,true);//learn 表示学习是否开始如果learn设置为true,这个方法也更新列的持久度
		
	    System.out.print(activeSpatialArray);
		
		
		
		
		
	}
	
	
	
	

	public static void main(String[] args) {
		// TODO Auto-generated method stub
     System.out.println("测试结果");
     runHotgym(1000);
	}

}
