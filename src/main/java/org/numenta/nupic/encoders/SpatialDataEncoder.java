package org.numenta.nupic.encoders;
import java.io.FileWriter;
import java.util.ArrayList;

import org.netlib.util.doubleW;
import org.numenta.nupic.examples.sp.DrawSee;
import org.numenta.nupic.util.HPoint;

/**
 * 这里创建一个空间位置的编码器
 * @author czg
 *
 */
public class SpatialDataEncoder {
	/**
	 * 准备形成的编码的宽度
	 */
	private int code_width;
	/**
	 * 准备形成的编码的高度
	 */
	private int code_height;
	/**
	 * 空间范围的宽度
	 */
	private int spatial_width;
	/**
	 * 空间范围的高度
	 */
	private int spatial_height;
	/**
	 * 初始的角度
	 */
	private double major_angle=Math.PI/12d;
	


	/**
	 * 格网间最大的间距
	 */
	private double spacing_max;
	
	/**
	 * 格网间最小的间距
	 */
	public double spacing_min;
	
	public final int  radius_level=8;
	
	/**
	 * 半径
	 */
	public double radius=0;
	/**
	 * 初始相位点的坐标
	 */
	private HPoint phase_location;
	
	public SpatialDataEncoder(int code_width,int code_height,int spatail_width,int spatial_height)
	{
		this.code_width=code_width;
		this.code_height=code_height;

		
		this.spatial_width=spatail_width;
		this.spatial_height=spatial_height;
		
		radius=0.075*(code_height+code_width);
		
		phase_location=new HPoint();
		
	}
	public void drawLocationGrid(int codeX,int codeY)
	{
		ArrayList<Integer[]> cordinateSeries=new ArrayList<Integer[]>();///这个记录的是激活的位置的坐标序列
		
		
		///把这个值尽量弄到从0开始,两个格网场的间距起码得大于等于1，否则就太密占满平面了
		spacing_max=spatial_height;///让距离的最大值为空间范围的宽度
		spacing_min=2d/Math.sqrt(3)+2*(spacing_max-(2d/Math.sqrt(3)))/code_height;
		double spacing_increasing=(spacing_max-spacing_min)/code_height;
		
		////还有就是相位每次增加的值
		double phase_increasing=((double)spatial_width)/((double)code_width);
		
		double spacing=spacing_min+codeY*spacing_increasing;
		
		double kvalue=4*Math.PI/(Math.sqrt(3)*spacing);
		
		phase_location.x=phase_increasing*codeX;///x坐标是距离宽那个边界的距离
		phase_location.y=spacing_increasing*codeY;//这个y是距离长的那个边界的距离
		
		for (int locationY = 0; locationY < spatial_height; locationY++) 
		{
			for (int locationX = 0; locationX < spatial_width; locationX++)
			{
				double gratingOne=Math.cos((kvalue/Math.sqrt(2))*((Math.cos(major_angle)+Math.sin(major_angle))*(locationX-phase_location.x)+(Math.cos(major_angle)-Math.sin(major_angle))*(locationY-phase_location.y)));
				double gratingTwo=Math.cos((kvalue/Math.sqrt(2))*((Math.cos(major_angle+(Math.PI/3d))+Math.sin(major_angle+(Math.PI/3d)))*(locationX-phase_location.x)+(Math.cos(major_angle+(Math.PI/3d))-Math.sin(major_angle+(Math.PI/3d)))*(locationY-phase_location.y)));
                double gratingThree=Math.cos((kvalue/Math.sqrt(2))*((Math.cos(major_angle+(2*Math.PI/3d))+Math.sin(major_angle+(2*Math.PI/3d)))*(locationX-phase_location.x)+(Math.cos(major_angle+(2*Math.PI/3d))-Math.sin(major_angle+(2*Math.PI/3d)))*(locationY-phase_location.y)));
				
				double firingFunc=(gratingOne+gratingTwo+gratingThree)/3d;
				if (firingFunc>0)
				{
					Integer[] fireloaction=new Integer[]{locationX,locationY,20};
					cordinateSeries.add(fireloaction);
				}
							
			}
		}
		new DrawSee(cordinateSeries,spatial_width,spatial_height);
		
		
	}
	
	/**
	 * 高斯函数的编码方式
	 * @param locationX
	 * @param locationY
	 * @return
	 */
	public int[][] encode_Gaussian4DistanceBinaryFixed(int locationX,int locationY)
	{
		int[][] codes=new int[code_width][code_height];//每一行的值代表宽度上的值
		double height_scale=(double)spatial_height/(double)code_height;
		double width_scale=(double)spatial_width/(double)code_width;
		
		///这里的locationX和locationY是指峰值激活点，那么相应的
		double amplitude=400d;
		double dN=locationY;
		double dS=55-locationY;
		double dE=45-locationX;
		double dW=locationX;
		double b0=5d;
		double threshold=49d;
		////现在先假设编码宽度和实际宽度和高度是相等的
		for (int i = 0; i < spatial_width; i++) 
		{
			for (int j = 0; j < spatial_height; j++) 
			{
				double x=i;
				double y=j;
//				double testdn=(b0*(61d*61d+dN*dN)/(61d*61d));
//				double testPidn=Math.sqrt(2d*Math.PI*(b0*(61d*61d+dN*dN)/(61d*61d))*(b0*(61d*61d+dN*dN)/(61d*61d)));
//				double testPi=(-1d)*((y-dN)*(y-dN))/(2d*(b0*(61d*61d+dN*dN)/(61d*61d))*(b0*(61d*61d+dN*dN)/(61d*61d)));
//				double testEPi=Math.exp((-1d)*((y-dN)*(y-dN))/(2d*(b0*(61d*61d+dN*dN)/(61d*61d))*(b0*(61d*61d+dN*dN)/(61d*61d))));
//				System.out.println(testdn+":"+testPidn+":"+testPi+":"+testEPi);
				double northRate=Math.exp((-1d)*((y-dN)*(y-dN))/(2d*(b0*(61d*61d+dN*dN)/(61d*61d))*(b0*(61d*61d+dN*dN)/(61d*61d))))/Math.sqrt(2d*Math.PI*(b0*(61d*61d+dN*dN)/(61d*61d))*(b0*(61d*61d+dN*dN)/(61d*61d)));
				double sourthRate=Math.exp(((-1d)*(spatial_height-y-dS)*(spatial_height-y-dS))/(2d*(b0*(61d*61d+dS*dS)/(61d*61d))*(b0*(61d*61d+dS*dS)/(61d*61d))))/Math.sqrt(2d*Math.PI*(b0*(61d*61d+dS*dS)/(61d*61d))*(b0*(61d*61d+dS*dS)/(61d*61d)));
				double eastRate=Math.exp(((-1d)*((spatial_width-x)-dE)*((spatial_width-x)-dE))/(2d*(b0*(61d*61d+dE*dE)/(61d*61d))*(b0*(61d*61d+dE*dE)/(61d*61d))))/Math.sqrt(2d*Math.PI*(b0*(61d*61d+dE*dE)/(61d*61d))*(b0*(61d*61d+dE*dE)/(61d*61d)));
				double westRate=Math.exp(((-1d)*(x-dW)*(x-dW))/(2d*(b0*(61d*61d+dW*dW)/(61d*61d))*(b0*(61d*61d+dW*dW)/(61d*61d))))/Math.sqrt(2d*Math.PI*(b0*(61d*61d+dW*dW)/(61d*61d))*(b0*(61d*61d+dW*dW)/(61d*61d)));
				double firingFunc=amplitude*(northRate+sourthRate+eastRate+westRate)-threshold;
				if (firingFunc>0) 
				{
					codes[i][j]=1;
				}
				else
				{
					codes[i][j]=0;
				}
				
			}
		}
		return codes;
	}
	
	/**
	 * 高斯函数的编码方式
	 * @param locationX
	 * @param locationY
	 * @return
	 */
	public int[][] encode_Gaussian4DistanceBinary(int locationX,int locationY)
	{
		int[][] codes=new int[code_width][code_height];//每一行的值代表宽度上的值
		double height_scale=(double)spatial_height/(double)code_height;
		double width_scale=(double)spatial_width/(double)code_width;
		
		///这里的locationX和locationY是指峰值激活点，那么相应的
		double amplitude=400d;
		double dN=locationY;
		double dS=spatial_height-locationY;
		double dE=spatial_width-locationX-1;
		double dW=locationX;
		double b0=1.5d;
		double threshold=174.6d;
		////现在先假设编码宽度和实际宽度和高度是相等的
		for (int i = 0; i < spatial_width; i++) 
		{
			for (int j = 0; j < spatial_height; j++) 
			{
				double x=i;
				double y=j;
//				double testdn=(b0*(61d*61d+dN*dN)/(61d*61d));
//				double testPidn=Math.sqrt(2d*Math.PI*(b0*(61d*61d+dN*dN)/(61d*61d))*(b0*(61d*61d+dN*dN)/(61d*61d)));
//				double testPi=(-1d)*((y-dN)*(y-dN))/(2d*(b0*(61d*61d+dN*dN)/(61d*61d))*(b0*(61d*61d+dN*dN)/(61d*61d)));
//				double testEPi=Math.exp((-1d)*((y-dN)*(y-dN))/(2d*(b0*(61d*61d+dN*dN)/(61d*61d))*(b0*(61d*61d+dN*dN)/(61d*61d))));
//				System.out.println(testdn+":"+testPidn+":"+testPi+":"+testEPi);
				double northRate=Math.exp((-1d)*((y-dN)*(y-dN))/(2d*(IncreFunction(b0,spatial_height,dN)*IncreFunction(b0,spatial_height,dN))))/Math.sqrt(2d*Math.PI*IncreFunction(b0,spatial_height,dN)*IncreFunction(b0,spatial_height,dN));
				double sourthRate=Math.exp(((-1d)*(spatial_height-y-dS)*(spatial_height-y-dS))/(2d*IncreFunction(b0,spatial_height,dS)*IncreFunction(b0,spatial_height,dS)))/Math.sqrt(2d*Math.PI*IncreFunction(b0,spatial_height,dS)*IncreFunction(b0,spatial_height,dS));
				double eastRate=Math.exp(((-1d)*((spatial_width-x)-dE)*((spatial_width-x)-dE))/(2d*IncreFunction(b0,spatial_width,dE)*IncreFunction(b0,spatial_width,dE)))/Math.sqrt(2d*Math.PI*IncreFunction(b0,spatial_width,dE)*IncreFunction(b0,spatial_width,dE));
				double westRate=Math.exp(((-1d)*(x-dW)*(x-dW))/(2d*IncreFunction(b0,spatial_width,dW)*IncreFunction(b0,spatial_width,dW)))/Math.sqrt(2d*Math.PI*IncreFunction(b0,spatial_width,dW)*IncreFunction(b0,spatial_width,dW));
				double firingFunc=amplitude*(northRate+sourthRate+eastRate+westRate)-threshold;
				if (firingFunc>0) 
				{
					codes[i][j]=1;
				}
				else
				{
					codes[i][j]=0;
				}
				
			}
		}
		return codes;
	}
	
	private double IncreFunction(double b0, double radius, double distance)
	{
		double result=(b0*(radius*radius+distance*distance)/(radius*radius));
		return result;
	}

	
	/**
	 * 这个把空间位置以位置为圆心，在指定的半径上建立
	 * @param locationX
	 * @param locationY
	 * @return
	 */
	public int[][] encode_twoDCircleBinary(int locationX,int locationY)
	{
		int[][] codes=new int[code_height][code_width];//每一行的值代表宽度上的值
		double height_scale=(double)spatial_height/(double)code_height;
		double width_scale=(double)spatial_width/(double)code_width;
		
		////现在先假设编码宽度和实际宽度和高度是相等的
		for (int i = 0; i < spatial_width; i++) 
		{
			for (int j = 0; j < spatial_height; j++) 
			{
				double distanceToLocation=Math.sqrt((i-locationX)*(i-locationX)+(j-locationY)*(j-locationY));
				if (distanceToLocation<=radius) 
				{
					codes[i][j]=1;
				}
				else
				{
					codes[i][j]=0;
				}
			}
		}
		
		return codes;
	
	}
	
	/**
	 * 新的函数
	 * @param locationX
	 * @param locationY
	 * @return
	 */
	public int[][][] encode_threeD(int locationX,int locationY)
	{
		int[][][] codes=new int[radius_level][code_height][code_width];//每一行的值代表宽度上的值
		spacing_max=spatial_height*2;///让距离的最大值为空间范围的宽度2倍
		spacing_min=1;
		//spacing_min=8d;
		
		double spacing=spacing_min;
		
		double spacing_increasing=(spacing_max-spacing_min)/(radius_level-1);
		
        for (int i = 0; i < radius_level; i++) ///这里是圆的半径的级别数
        {
			///获取对应的k值
			double kvalue=4*Math.PI/(Math.sqrt(3)*spacing);
			
		////还有就是相位每次增加的值
			double phase_increasing_x=((double)spacing*0.5)/((double)(code_width-1));
			double phase_increasing_y=((double)spacing*Math.sqrt(3)/2)/((double)(code_height-1));
			
			///首先确定每个编码单元的激活范围，判断指定的坐标是不是在这个范围之内，如果在那么编码为1，如果不在编码为0
			for(int mec_height=0;mec_height<code_height;mec_height++)
			{
			  phase_location.y=phase_increasing_y*mec_height;//这个y是距离宽的那个边界的距离
			
				for (int mec_width = 0; mec_width <code_width; mec_width++) ///随着长度的增加，格网细胞的相位点不断的增加，但是相位点不能跑到长度或者宽度外面了
				{
					phase_location.x=phase_increasing_x*mec_width;///x坐标是距离高那个边界的距离,这里得仔细理理，当x不断增加，相当于图形不断的沿着x轴扯，如果locationX-p.x的值正好等于一个周期，那么这个图形相当于是重复了，是属于无效表征，	重复编码			
					
					if (mec_height==mec_width) 
					{
						double gratingOne=Math.cos((kvalue/Math.sqrt(2))*((Math.cos(major_angle)+Math.sin(major_angle))*(locationX-phase_location.x)+(Math.cos(major_angle)-Math.sin(major_angle))*(locationY-phase_location.y)));
					    double gratingTwo=Math.cos((kvalue/Math.sqrt(2))*((Math.cos(major_angle+(Math.PI/3d))+Math.sin(major_angle+(Math.PI/3d)))*(locationX-phase_location.x)+(Math.cos(major_angle+(Math.PI/3d))-Math.sin(major_angle+(Math.PI/3d)))*(locationY-phase_location.y)));
	                    double gratingThree=Math.cos((kvalue/Math.sqrt(2))*((Math.cos(major_angle+(2*Math.PI/3d))+Math.sin(major_angle+(2*Math.PI/3d)))*(locationX-phase_location.x)+(Math.cos(major_angle+(2*Math.PI/3d))-Math.sin(major_angle+(2*Math.PI/3d)))*(locationY-phase_location.y)));
					
						double firingFunc=(gratingOne+gratingTwo+gratingThree)/3d;
						if (firingFunc>0)
						{
							codes[i][mec_height][mec_width]=1;
						}
						else
						{
							codes[i][mec_height][mec_width]=0;
						}
					}
				}
			    		
			}
	    ///高度编码每增加1,间距就增加一部分
		spacing=spacing+spacing_increasing;
	   }
			
		return codes;
	}
		
	 /**
     * 对x,y位置进行编码
     * @param locationX X坐标
     * @param locationY Y坐标
     * @return
     */
	public int[][] encode_twoDAngle(int locationX,int locationY)
	{
		int[][] codes=new int[code_height][code_width];//每一行的值代表宽度上的值
		spacing_max=1/2*code_height;///让距离的最大值为空间范围的宽度2倍
		//spacing_max=16d;///让距离的最大值为空间范围的宽度2倍
		spacing_min=1/3d;
		//spacing_min=8d;
		
		double spacing=spacing_max;
			
		
		///首先确定每个编码单元的激活范围，判断指定的坐标是不是在这个范围之内，如果在那么编码为1，如果不在编码为0
		for(int mec_height=0;mec_height<code_height;mec_height++)
		{
			///获取对应的k值
			double kvalue=4*Math.PI/(Math.sqrt(3)*spacing);
			
		////还有就是相位每次增加的值
			double phase_increasing_x=((double)(spacing-1)*0.5)/((double)(code_width-1));
			double phase_increasing_y=((double)(spacing-1)*0.5)/((double)(code_height-1));
			
			//double phase_increasing_y=(spatial_height)/((double)(code_height-1));
			double spacing_increasing=(spacing_max-spacing_min)/(code_height-1);
			
			///随着间距越来越大，角度越来越小
			///major_angle=(60d/(mec_height+1))*Math.random()/180*3.141592653;
			
			phase_location.y=phase_increasing_y*mec_height;//这个y是距离宽的那个边界的距离
			
			major_angle=0.455*mec_height;
	        major_angle=major_angle/180*3.141592653;
			
			for (int mec_width = 0; mec_width <code_width; mec_width++) ///随着长度的增加，格网细胞的相位点不断的增加，但是相位点不能跑到长度或者宽度外面了
			{
				phase_location.x=phase_increasing_x*mec_width;///x坐标是距离高那个边界的距离,这里得仔细理理，当x不断增加，相当于图形不断的沿着x轴扯，如果locationX-p.x的值正好等于一个周期，那么这个图形相当于是重复了，是属于无效表征，	重复编码			
				
				////这里适宜求取整个格子的平均值作为是否激活的标准，单个格子难以代表整个平面的状况
		
				double totalResult=0;
				for (int i = 0; i < 10; i++) 
				{
					double locationInX=locationX+i/10d;
					for (int j = 0; j < 10; j++) 
					{
						double locationInY=locationY+j/10d;
						double gratingOne=Math.cos((kvalue/Math.sqrt(2))*((Math.cos(major_angle)+Math.sin(major_angle))*(locationInX-phase_location.x)+(Math.cos(major_angle)-Math.sin(major_angle))*(locationInY-phase_location.y)));
				        double gratingTwo=Math.cos((kvalue/Math.sqrt(2))*((Math.cos(major_angle+(Math.PI/3d))+Math.sin(major_angle+(Math.PI/3d)))*(locationInX-phase_location.x)+(Math.cos(major_angle+(Math.PI/3d))-Math.sin(major_angle+(Math.PI/3d)))*(locationInY-phase_location.y)));
                        double gratingThree=Math.cos((kvalue/Math.sqrt(2))*((Math.cos(major_angle+(2*Math.PI/3d))+Math.sin(major_angle+(2*Math.PI/3d)))*(locationInX-phase_location.x)+(Math.cos(major_angle+(2*Math.PI/3d))-Math.sin(major_angle+(2*Math.PI/3d)))*(locationInY-phase_location.y)));
			            double firingFunc=(gratingOne+gratingTwo+gratingThree)/3d;
			            totalResult=totalResult+firingFunc;
					}
				}
				
				totalResult=totalResult/100d;
				
				if (totalResult>0.00001)
				{
					codes[mec_height][mec_width]=1;
				}
				else
				{
					codes[mec_height][mec_width]=0;
				}
				
			}
			    ///高度编码每增加1,间距就增加一部分
				spacing=spacing-spacing_increasing;
		}
		return codes;
	}
	
	 /**
     * 对x,y位置进行编码
     * @param locationX X坐标
     * @param locationY Y坐标
     * @return
     */
	public int[][] encode_twoDAngleDouble(int locationX,int locationY)
	{
		int[][] codes=new int[code_height][code_width];//每一行的值代表宽度上的值
		spacing_max=1/2*code_height;///让距离的最大值为空间范围的宽度2倍
		//spacing_max=16d;///让距离的最大值为空间范围的宽度2倍
		spacing_min=1/3d;
		//spacing_min=8d;
		
		double spacing=spacing_max;
		
		///首先确定每个编码单元的激活范围，判断指定的坐标是不是在这个范围之内，如果在那么编码为1，如果不在编码为0
		for(int mec_height=0;mec_height<code_height;mec_height++)
		{
			///获取对应的k值
			double kvalue=4*Math.PI/(Math.sqrt(3)*spacing);
			
		////还有就是相位每次增加的值
			double phase_increasing_x=((double)(spacing-1)*0.5)/((double)(code_width-1));
			double phase_increasing_y=((double)(spacing-1)*0.5)/((double)(code_height-1));
			
			//double phase_increasing_y=(spatial_height)/((double)(code_height-1));
			double spacing_increasing=(spacing_max-spacing_min)/(code_height-1);
			
			///随着间距越来越大，角度越来越小
			///major_angle=(60d/(mec_height+1))*Math.random()/180*3.141592653;
			
			phase_location.y=phase_increasing_y*mec_height;//这个y是距离宽的那个边界的距离
			
			major_angle=0.455*mec_height;
	        major_angle=major_angle/180*3.141592653;
			
			for (int mec_width = 0; mec_width <code_width; mec_width++) ///随着长度的增加，格网细胞的相位点不断的增加，但是相位点不能跑到长度或者宽度外面了
			{
				phase_location.x=phase_increasing_x*mec_width;///x坐标是距离高那个边界的距离,这里得仔细理理，当x不断增加，相当于图形不断的沿着x轴扯，如果locationX-p.x的值正好等于一个周期，那么这个图形相当于是重复了，是属于无效表征，	重复编码			
				
				////这里适宜求取整个格子的平均值作为是否激活的标准，单个格子难以代表整个平面的状况
		
				double totalResult=0;
				for (int i = 0; i < 10; i++) 
				{
					double locationInX=locationX+i/10d;
					for (int j = 0; j < 10; j++) 
					{
						double locationInY=locationY+j/10d;
						double gratingOne=Math.cos((kvalue/Math.sqrt(2))*((Math.cos(major_angle)+Math.sin(major_angle))*(locationInX-phase_location.x)+(Math.cos(major_angle)-Math.sin(major_angle))*(locationInY-phase_location.y)));
				        double gratingTwo=Math.cos((kvalue/Math.sqrt(2))*((Math.cos(major_angle+(Math.PI/3d))+Math.sin(major_angle+(Math.PI/3d)))*(locationInX-phase_location.x)+(Math.cos(major_angle+(Math.PI/3d))-Math.sin(major_angle+(Math.PI/3d)))*(locationInY-phase_location.y)));
                        double gratingThree=Math.cos((kvalue/Math.sqrt(2))*((Math.cos(major_angle+(2*Math.PI/3d))+Math.sin(major_angle+(2*Math.PI/3d)))*(locationInX-phase_location.x)+(Math.cos(major_angle+(2*Math.PI/3d))-Math.sin(major_angle+(2*Math.PI/3d)))*(locationInY-phase_location.y)));
			            double firingFunc=(gratingOne+gratingTwo+gratingThree)/3d;
			            totalResult=totalResult+firingFunc;
					}
				}
				
				totalResult=totalResult/100d;
				
				if (totalResult>0.00001)
				{
					codes[mec_height][mec_width]=1;
				}
				else
				{
					codes[mec_height][mec_width]=0;
				}
				
			}
			    ///高度编码每增加1,间距就增加一部分
				spacing=spacing-spacing_increasing;
		}
		return codes;
	}
    /**
     * 对x,y位置进行编码
     * @param locationX X坐标
     * @param locationY Y坐标
     * @return
     */
	public int[][] encode_twoD(int locationX,int locationY)
	{
		int[][] codes=new int[code_height][code_width];//每一行的值代表宽度上的值
		spacing_max=spatial_height*2;///让距离的最大值为空间范围的宽度2倍
		spacing_min=2d/Math.sqrt(3)+4*(spacing_max-(2d/Math.sqrt(3)))/code_height;
		//spacing_min=8d;
		
		double spacing=spacing_min;
		
		
		
		///首先确定每个编码单元的激活范围，判断指定的坐标是不是在这个范围之内，如果在那么编码为1，如果不在编码为0
		for(int mec_height=0;mec_height<code_height;mec_height++)
		{
			///获取对应的k值
			double kvalue=4*Math.PI/(Math.sqrt(3)*spacing);
			
		////还有就是相位每次增加的值
			double phase_increasing_x=((double)spacing*0.5)/((double)(code_width-1));
			double phase_increasing_y=((double)spacing*Math.sqrt(3)/2)/((double)(code_height-1));
			double spacing_increasing=(spacing_max-spacing_min)/(code_height-1);
			
			///随着间距越来越大，角度越来越小
			///major_angle=(60d/(mec_height+1))*Math.random()/180*3.141592653;
			
			phase_location.y=phase_increasing_y*mec_height;//这个y是距离宽的那个边界的距离
			
			for (int mec_width = 0; mec_width <code_width; mec_width++) ///随着长度的增加，格网细胞的相位点不断的增加，但是相位点不能跑到长度或者宽度外面了
			{
				phase_location.x=phase_increasing_x*mec_width;///x坐标是距离高那个边界的距离,这里得仔细理理，当x不断增加，相当于图形不断的沿着x轴扯，如果locationX-p.x的值正好等于一个周期，那么这个图形相当于是重复了，是属于无效表征，	重复编码			
				
				double gratingOne=Math.cos((kvalue/Math.sqrt(2))*((Math.cos(major_angle)+Math.sin(major_angle))*(locationX-phase_location.x)+(Math.cos(major_angle)-Math.sin(major_angle))*(locationY-phase_location.y)));
				double gratingTwo=Math.cos((kvalue/Math.sqrt(2))*((Math.cos(major_angle+(Math.PI/3d))+Math.sin(major_angle+(Math.PI/3d)))*(locationX-phase_location.x)+(Math.cos(major_angle+(Math.PI/3d))-Math.sin(major_angle+(Math.PI/3d)))*(locationY-phase_location.y)));
                double gratingThree=Math.cos((kvalue/Math.sqrt(2))*((Math.cos(major_angle+(2*Math.PI/3d))+Math.sin(major_angle+(2*Math.PI/3d)))*(locationX-phase_location.x)+(Math.cos(major_angle+(2*Math.PI/3d))-Math.sin(major_angle+(2*Math.PI/3d)))*(locationY-phase_location.y)));
				
				double firingFunc=(gratingOne+gratingTwo+gratingThree)/3d;
				if (firingFunc>0)
				{
					codes[mec_height][mec_width]=1;
				}
				else
				{
					codes[mec_height][mec_width]=0;
				}
				
			}
			    ///高度编码每增加1,间距就增加一部分
				spacing=spacing+spacing_increasing;
		}
		return codes;
	}
	
	
	/**
     * 对x,y位置进行编码
     * @param locationX X坐标
     * @param locationY Y坐标
     * @return
     */
	public double[][] encode_twoDRoundDouble(int locationX,int locationY)
	{
		double[][] codes=new double[code_height][code_width];//每一行的值代表宽度上的值
		spacing_max=spatial_height*2/3;///让距离的最大值为空间范围的宽度2倍
		spacing_min=spatial_height*1/4;
		//spacing_min=8d;
		
		double spacing=spacing_min;		
		///首先确定每个编码单元的激活范围，判断指定的坐标是不是在这个范围之内，如果在那么编码为1，如果不在编码为0
		for(int mec_height=0;mec_height<code_height;mec_height++)
		{
			///获取对应的k值
			double kvalue=4*Math.PI/(Math.sqrt(3)*spacing);
			
		////还有就是相位每次增加的值
			//double phase_increasing_x=((double)spacing*0.5)/((double)(code_width-1));
			double phase_increasing_y=((double)spacing*Math.sqrt(3)/2)/((double)(code_height-1));
			double spacing_increasing=(spacing_max-spacing_min)/(code_height-1);
			///角度变换一下
			major_angle=(60d/(mec_height+1))*Math.random()/180*3.141592653;
			
			
			phase_location.y=phase_increasing_y*mec_height;//这个y是距离宽的那个边界的距离
			
			double angleIncrease=360d/(code_width-1);
			
			for (int mec_width = 0; mec_width <code_width; mec_width++) ///随着长度的增加，格网细胞的相位点不断的增加，但是相位点不能跑到长度或者宽度外面了
			{
				
				if (mec_width==0) 
				{
					phase_location.x=0;
				}
				else
				{
					double angle=(((mec_width-1)*angleIncrease)/180d)*Math.PI;
					phase_location.x=0.5*Math.sqrt(3)/2d*spacing*Math.cos(angle);
					phase_location.y=phase_increasing_y*mec_height+0.5*Math.sqrt(3)/2*spacing*Math.sin(angle);
				}
		 	 			 			 
				
				//phase_location.x=phase_increasing_x*mec_width;///x坐标是距离高那个边界的距离,这里得仔细理理，当x不断增加，相当于图形不断的沿着x轴扯，如果locationX-p.x的值正好等于一个周期，那么这个图形相当于是重复了，是属于无效表征，	重复编码			
				
				double gratingOne=Math.cos((kvalue/Math.sqrt(2))*((Math.cos(major_angle)+Math.sin(major_angle))*(locationX-phase_location.x)+(Math.cos(major_angle)-Math.sin(major_angle))*(locationY-phase_location.y)));
				double gratingTwo=Math.cos((kvalue/Math.sqrt(2))*((Math.cos(major_angle+(Math.PI/3d))+Math.sin(major_angle+(Math.PI/3d)))*(locationX-phase_location.x)+(Math.cos(major_angle+(Math.PI/3d))-Math.sin(major_angle+(Math.PI/3d)))*(locationY-phase_location.y)));
                double gratingThree=Math.cos((kvalue/Math.sqrt(2))*((Math.cos(major_angle+(2*Math.PI/3d))+Math.sin(major_angle+(2*Math.PI/3d)))*(locationX-phase_location.x)+(Math.cos(major_angle+(2*Math.PI/3d))-Math.sin(major_angle+(2*Math.PI/3d)))*(locationY-phase_location.y)));
				
				double firingFunc=(gratingOne+gratingTwo+gratingThree)/3d;

					codes[mec_height][mec_width]=firingFunc;
				
				
			}
			    ///高度编码每增加1,间距就增加一部分
				spacing=spacing+spacing_increasing;
		}
		return codes;
	}
	
	
	/**
     * 对x,y位置进行编码
     * @param locationX X坐标
     * @param locationY Y坐标
     * @return
     */
	public int[][] encode_twoDRound(int locationX,int locationY)
	{
		int[][] codes=new int[code_height][code_width];//每一行的值代表宽度上的值
		spacing_max=spatial_height*2/3;///让距离的最大值为空间范围的宽度2倍
		spacing_min=spatial_height*1/4;
		//spacing_min=8d;
		
		double spacing=spacing_min;		
		///首先确定每个编码单元的激活范围，判断指定的坐标是不是在这个范围之内，如果在那么编码为1，如果不在编码为0
		for(int mec_height=0;mec_height<code_height;mec_height++)
		{
			///获取对应的k值
			double kvalue=4*Math.PI/(Math.sqrt(3)*spacing);
			
		////还有就是相位每次增加的值
			//double phase_increasing_x=((double)spacing*0.5)/((double)(code_width-1));
			double phase_increasing_y=((double)spacing*Math.sqrt(3)/2)/((double)(code_height-1));
			double spacing_increasing=(spacing_max-spacing_min)/(code_height-1);
			///角度变换一下
			major_angle=(60d/(mec_height+1))*Math.random()/180*3.141592653;
			
			
			phase_location.y=phase_increasing_y*mec_height;//这个y是距离宽的那个边界的距离
			
			double angleIncrease=360d/(code_width-1);
			
			for (int mec_width = 0; mec_width <code_width; mec_width++) ///随着长度的增加，格网细胞的相位点不断的增加，但是相位点不能跑到长度或者宽度外面了
			{
				
				if (mec_width==0) 
				{
					phase_location.x=0;
				}
				else
				{
					double angle=(((mec_width-1)*angleIncrease)/180d)*Math.PI;
					phase_location.x=0.5*Math.sqrt(3)/2d*spacing*Math.cos(angle);
					phase_location.y=phase_increasing_y*mec_height+0.5*Math.sqrt(3)/2*spacing*Math.sin(angle);
				}
		 	 			 			 
				
				//phase_location.x=phase_increasing_x*mec_width;///x坐标是距离高那个边界的距离,这里得仔细理理，当x不断增加，相当于图形不断的沿着x轴扯，如果locationX-p.x的值正好等于一个周期，那么这个图形相当于是重复了，是属于无效表征，	重复编码			
				
				double gratingOne=Math.cos((kvalue/Math.sqrt(2))*((Math.cos(major_angle)+Math.sin(major_angle))*(locationX-phase_location.x)+(Math.cos(major_angle)-Math.sin(major_angle))*(locationY-phase_location.y)));
				double gratingTwo=Math.cos((kvalue/Math.sqrt(2))*((Math.cos(major_angle+(Math.PI/3d))+Math.sin(major_angle+(Math.PI/3d)))*(locationX-phase_location.x)+(Math.cos(major_angle+(Math.PI/3d))-Math.sin(major_angle+(Math.PI/3d)))*(locationY-phase_location.y)));
                double gratingThree=Math.cos((kvalue/Math.sqrt(2))*((Math.cos(major_angle+(2*Math.PI/3d))+Math.sin(major_angle+(2*Math.PI/3d)))*(locationX-phase_location.x)+(Math.cos(major_angle+(2*Math.PI/3d))-Math.sin(major_angle+(2*Math.PI/3d)))*(locationY-phase_location.y)));
				
				double firingFunc=(gratingOne+gratingTwo+gratingThree)/3d;
				if (firingFunc>0)
				{
					codes[mec_height][mec_width]=1;
				}
				else
				{
					codes[mec_height][mec_width]=0;
				}
				
			}
			    ///高度编码每增加1,间距就增加一部分
				spacing=spacing+spacing_increasing;
		}
		return codes;
	}
	
	/**
     * 对x,y位置进行编码
     * @param locationX X坐标
     * @param locationY Y坐标
     * @return
     */
	public int[][] encode_twoDRoundRandom(int locationX,int locationY)
	{
		int[][] codes=new int[code_height][code_width];//每一行的值代表宽度上的值
		spacing_max=spatial_height*2/3;///让距离的最大值为空间范围的宽度2倍
		spacing_min=2d/Math.sqrt(3)+4*(spacing_max-(2d/Math.sqrt(3)))/code_height;
		spacing_min=spatial_height*1/3;
		//spacing_min=8d;
		
		double spacing=spacing_min;
		
		
		
		///首先确定每个编码单元的激活范围，判断指定的坐标是不是在这个范围之内，如果在那么编码为1，如果不在编码为0
		for(int mec_height=0;mec_height<code_height;mec_height++)
		{
			///获取对应的k值
			double kvalue=4*Math.PI/(Math.sqrt(3)*spacing);
			
		////还有就是相位每次增加的值
			//double phase_increasing_x=((double)spacing*0.5)/((double)(code_width-1));
			//double phase_increasing_y=((double)spacing*Math.sqrt(3)/2)/((double)(code_height-1));
			double spacing_increasing=(spacing_max-spacing_min)/(code_height-1);
			///角度变换一下
			//major_angle=(60d/(mec_height+1))*Math.random()/180*3.141592653;
			major_angle=60d*Math.random()/180*3.141592653;
			
			//phase_location.y=phase_increasing_y*mec_height;//这个y是距离宽的那个边界的距离
			
			//double angleIncrease=360d/(code_width-1);///角度不按规律增长，我们依然设它是随机增长
			
			for (int mec_width = 0; mec_width <code_width; mec_width++) ///随着长度的增加，格网细胞的相位点不断的增加，但是相位点不能跑到长度或者宽度外面了
			{
				
				//if (mec_width==0) 
				//{
				//	phase_location.x=0;
				//}
				//else
				
					double angle=(360*Math.random()/180d)*Math.PI;
					phase_location.x=0.5*Math.sqrt(3)/2d*spacing*Math.cos(angle);
					phase_location.y=0.5*Math.sqrt(3)/2*spacing*Math.sin(angle);
				
		 	 			 			 
				
				//phase_location.x=phase_increasing_x*mec_width;///x坐标是距离高那个边界的距离,这里得仔细理理，当x不断增加，相当于图形不断的沿着x轴扯，如果locationX-p.x的值正好等于一个周期，那么这个图形相当于是重复了，是属于无效表征，	重复编码			
				
				
				double totalResult=0;
				for (int i = 0; i < 10; i++) 
				{
					double locationInX=locationX+i/10d;
					for (int j = 0; j < 10; j++) 
					{
						double locationInY=locationY+j/10d;
						double gratingOne=Math.cos((kvalue/Math.sqrt(2))*((Math.cos(major_angle)+Math.sin(major_angle))*(locationInX-phase_location.x)+(Math.cos(major_angle)-Math.sin(major_angle))*(locationInY-phase_location.y)));
				        double gratingTwo=Math.cos((kvalue/Math.sqrt(2))*((Math.cos(major_angle+(Math.PI/3d))+Math.sin(major_angle+(Math.PI/3d)))*(locationInX-phase_location.x)+(Math.cos(major_angle+(Math.PI/3d))-Math.sin(major_angle+(Math.PI/3d)))*(locationInY-phase_location.y)));
                        double gratingThree=Math.cos((kvalue/Math.sqrt(2))*((Math.cos(major_angle+(2*Math.PI/3d))+Math.sin(major_angle+(2*Math.PI/3d)))*(locationInX-phase_location.x)+(Math.cos(major_angle+(2*Math.PI/3d))-Math.sin(major_angle+(2*Math.PI/3d)))*(locationInY-phase_location.y)));
			            double firingFunc=(gratingOne+gratingTwo+gratingThree)/3d;
			            totalResult=totalResult+firingFunc;
					}
				}
				
				totalResult=totalResult/100d;
				
				if (totalResult>0)
				{
					codes[mec_height][mec_width]=1;
				}
				else
				{
					codes[mec_height][mec_width]=0;
				}
				
				
			}
			    ///高度编码每增加1,间距就增加一部分
				spacing=spacing+spacing_increasing;
		}
		return codes;
	}
	
	
	/**
     * 对x,y位置进行编码
     * @param locationX X坐标
     * @param locationY Y坐标
     * @return
     */
	public int[] GetEncodeTwoDim(int[][] encodeTwoDim)
	{
		int[] code=new int[encodeTwoDim.length*encodeTwoDim[0].length];
		for (int i = 0; i < encodeTwoDim.length; i++) 
		{
			for (int j = 0; j < encodeTwoDim[0].length; j++) 
			{
				code[i*encodeTwoDim[0].length+j]=encodeTwoDim[i][j];
			}
		}
		return code;
	}
	
	/**
     * 对x,y位置进行编码
     * @param locationX X坐标
     * @param locationY Y坐标
     * @return
     */
	public int[] GetEncodeThreeDim(int[][][] encodeThreeDim)
	{
		int[] code=new int[encodeThreeDim.length*encodeThreeDim[0].length*encodeThreeDim[1].length];
		for (int i = 0; i < encodeThreeDim.length; i++) 
		{
			for (int j = 0; j < encodeThreeDim[0].length; j++) 
			{
				for (int k = 0; k < encodeThreeDim[1].length; k++) {
					code[i*encodeThreeDim[0].length*encodeThreeDim[1].length+j*encodeThreeDim[1].length+k]=encodeThreeDim[i][j][k];
				}
			}
		}
		return code;
	}
	
    /**
     * 把稀疏数组写入txt文档
     * @param activeArray
     */
    public static void writeOutputToTxt( int[] activeArray,String path)
    {
    	try 
    	{
    		FileWriter resultFile=new FileWriter(path,true);
    		resultFile.write("[");
        	if (activeArray!=null) 
        	{
				for (int i = 0; i < activeArray.length; i++) {
					//if (activeArray[i]==1) 
					{
						resultFile.write(activeArray[i]+", ");
					}
					
				}
			}
        	resultFile.write("]");
        	resultFile.write("\r\n");//换行
        	resultFile.close();//关闭文件
		}
    	catch (Exception e) {
			// TODO: handle exception
		}       	      	
    }
	
	  public static void main(String[] args) 
	  {
		  SpatialDataEncoder spatialDataEncoder=new SpatialDataEncoder(16,16,16,16);
		  //spatialDataEncoder.drawLocationGrid(0, 5);
		  for (int i = 0; i < 16; i++)
		  {
			 for (int j = 0; j < 16; j++) 
			 {
				//spatialDataEncoder.encode_twoD(i, j);
    			int[][] constvalue=spatialDataEncoder.encode_twoDAngle(i, j);//j是离北面的距离，i是离西面的距离
    			int[] constVauleEncode=spatialDataEncoder.GetEncodeTwoDim(constvalue);
    			
    			 writeOutputToTxt(constVauleEncode, "D:/workspace/codeResult16.txt");
				
			 }
		  }
		  
	  }

}
