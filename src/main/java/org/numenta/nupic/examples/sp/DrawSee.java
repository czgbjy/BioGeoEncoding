package org.numenta.nupic.examples.sp;

import java.awt.Color;
import java.awt.Container;
import java.awt.Graphics;
import java.util.ArrayList;
import javax.swing.JFrame;


public class DrawSee extends JFrame {
	
	private static int width;
	private static int height;
	private static int sx=50;//相当于矩形网格在panel上起点的位置
	private static int sy = 50;//相当于矩形网格在panel上起点的位置
    private static int w = 6;//每个小方格的边长
    private static int rw = 610;//游戏区域10*10方块的边长
    private static int rh=0;
    
    private Graphics jg;
    
    private Color rectColor = new Color(0xf5f5f5);
    
    public DrawSee(ArrayList<Integer[]> cordinateSeries,int width,int height)
    {
    	this.width=width;
    	this.height=height;
    	this.rw=width*w;
    	this.rh=height*w;
    	
    	Container p=getContentPane();
    	setBounds(sx+100, sy+100, width*w+100, height*w+500);
    	setVisible(true);
    	p.setBackground(rectColor);
    	setLayout(null);
    	setResizable(true);
    	this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    	
    	try {
			Thread.sleep(500);
		} catch (Exception e) {
			e.printStackTrace();
		}
    	//获取专门用于在窗口界面上绘图的对象
    	jg=this.getGraphics();
    	///绘制游戏区域
    	paintComponents(cordinateSeries,jg);
    	
    }
    
    
    
    public void paintComponents(ArrayList<Integer[]> cordinateSeries,Graphics g)
    {
    	try
    	{
			//设置线条颜色为红色
    		g.setColor(Color.RED);
    		//绘制外层矩形框
    		g.drawRect(sx, sy, w, w);
    		for(int i=0;i<=height;i++)
    		{
       			g.drawLine(sx, sy + (i * w), sx + rw, sy + (i * w));
    		}
    		for (int i = 0; i <=width; i++) {
				//绘制第i条竖直线
    			g.drawLine(sx + (i * w), sy, sx + (i * w), sy + rh);
			}
    		setGrid(cordinateSeries, Color.pink);
		} 
    	catch (Exception e) {
			e.printStackTrace();
		}
    }
    
    private void setGrid(ArrayList<Integer[]> cordinateSeries,Color color)
    {
    	jg.setColor(color);
    	
    	for (int i = 0; i < cordinateSeries.size(); i++) {
    		
    		Integer[] cordinates=cordinateSeries.get(i);
    		int cx=cordinates[0];
    		int cy=cordinates[1];
    		int overlapValue=cordinates[2];
    		if (overlapValue>100) 
    		{
    			jg.setColor(Color.black);
			}
    		else if(overlapValue<=100&&overlapValue>80)
    		{
    			jg.setColor(Color.red);
    			
			}
    		else if(overlapValue<=80&&overlapValue>60)
    		{
    			jg.setColor(Color.magenta);
    			
			}
    		else if(overlapValue<=60&&overlapValue>40)
    		{
    			
    			jg.setColor(Color.pink);
			}
    		else if(overlapValue<=40&&overlapValue>20)
    		{
    			jg.setColor(Color.yellow);
   			}
    		else if (overlapValue<=20) 
    		{
    			jg.setColor(Color.blue);
			}
    		jg.fillRect(sx + (cx * w) + 1, sy + (cy * w) + 1, w , w);
			
		}
    	
    	
    }
}
