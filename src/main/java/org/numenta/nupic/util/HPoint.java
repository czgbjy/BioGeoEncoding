package org.numenta.nupic.util;


/**
 * 用来存储坐标点，这个坐标用的是浮点型的数字表示
 * @author czg
 *
 */
public class HPoint 
{
public double x;
public double y;

public HPoint()
{}

public HPoint(double x,double y)
{
	this.x=x;
	this.y=y;
}

public double getX() {
	return x;
}

public void setX(double x) {
	this.x = x;
}

public double getY() {
	return y;
}

public void setY(double y) {
	this.y = y;
}

}
