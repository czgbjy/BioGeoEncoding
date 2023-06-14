/* ---------------------------------------------------------------------
 * Numenta Platform for Intelligent Computing (NuPIC)
 * Copyright (C) 2016, Numenta, Inc.  Unless you have an agreement
 * with Numenta, Inc., for a separate license for this software code, the
 * following terms and conditions apply:
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 3 as
 * published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see http://www.gnu.org/licenses.
 *
 * http://numenta.org/licenses/
 * ---------------------------------------------------------------------
 */
package org.numenta.nupic.util;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import org.numenta.nupic.model.Connections;

import gnu.trove.list.TIntList;
import gnu.trove.list.array.TIntArrayList;

/**
 * 主要的功能为，实现索引值和二维坐标转换（包括二维坐标转换为索引坐标），获取指定中心点和半径的点的邻域的索引数组
 * @author czg
 *
 */
public class Topology extends Coordinator implements Serializable {
    /** keep it simple */
    private static final long serialVersionUID = 1L;
    
    private IntGenerator[] igs;
    private int[] centerPosition;
    
    
    /**
     * Constructs a new {@link AbstractFlatMatrix} object to be configured with specified
     * dimensions and major ordering.构造一个新的AbstractFlatMatrix对象，用指定的维度和主要顺序来配置
     * @param shape  the dimensions of this matrix 矩阵的维度
     */
    public Topology(int... shape) {
        super(shape, false);
    }
    
    /**
     * Translate an index into coordinates, using the given coordinate system.
     * 
     * @param index     The index of the point. The coordinates are expressed as a single index by
     *                  using the dimensions as a mixed radix definition. For example, in dimensions
     *                  42x10, the point [1, 4] is index 1*420 + 4*10 = 460.
     * @return          A array of coordinates of length len(dimensions).
     */
    public int[] coordinatesFromIndex(int index) {
        return computeCoordinates(index);
    }

    /**
     * Translate coordinates into an index, using the given coordinate system.
     * 
     * @param coordinates       A array of coordinates of length dimensions.size().
     * @param shape             The coordinate system.
     * @return                  The index of the point. The coordinates are expressed as a single index by
     *                          using the dimensions as a mixed radix definition. For example, in dimensions
     *                          42x10, the point [1, 4] is index 1*420 + 4*10 = 460.
     */
    public int indexFromCoordinates(int... coordinates) {
        return computeIndex(coordinates);
    }
    
    /**
     * Get the points in the neighborhood of a point.
     *
     * A point's neighborhood is the n-dimensional hypercube with sides ranging
     * [center - radius, center + radius], inclusive. For example, if there are two
     * dimensions and the radius is 3, the neighborhood is 6x6. Neighborhoods are
     * truncated when they are near an edge. 一个点的领域是n维的超立方体，它的边的范围为[中心-半径，中心+半径].例如，如果是一个二维的平面，且半径为3，那么邻域范围为6*6。
     * 当靠近边的时候，邻域被截掉。获取所有中心点的邻域的索引。
     * 
     * @param centerIndex       The index of the point. The coordinates are expressed as a single index by
     *                          using the dimensions as a mixed radix definition. For example, in dimensions
     *                          42x10, the point [1, 4] is index 1*420 + 4*10 = 460.点的索引，通过使用维度作为混合基数定义，
     *                          将坐标表示为单个索引，例如对于维度42*10，点[1,4]的索引为1*420+4*10=460.
     * @param radius            The radius of this neighborhood about the centerIndex.关于中心点索引的领域的半径。
     * @return  The points in the neighborhood, including centerIndex. 包括中心点在内的邻域的所有点。
     */
    public int[] neighborhood(int centerIndex, int[] radius) {
        centerPosition = coordinatesFromIndex(centerIndex);//这里把一个中心点的索引，譬如460又转换成了相应的坐标,譬如[1,4]
        
        
        igs = IntStream.range(0, dimensions.length)///IntStream.range()返回一个整数序列，本例是从0开始，一直到dimensions.length不包括，如果是2维，也就是[0,1]
            .mapToObj(i -> 
                IntGenerator.of(Math.max(0, centerPosition[i] - radius[i]), 
                    Math.min(dimensions[i], centerPosition[i] + radius[i])))
            .toArray(IntGenerator[]::new);//将输出值转换为IntGenerator[]类型的数组。 Java 8中使用了::的用法。就是把方法当做参数传递到stream内部，使得stream的每个元素都传入该方法里面执行一下
       
        List<TIntList> result = new ArrayList<>();
        result.add(new TIntArrayList());
        List<TIntList> interim = new ArrayList<>();
        for(IntGenerator pool : igs) {
            int size = result.size();
            interim.clear();
            interim.addAll(result);
            result.clear();
            for(int x = 0;x < size;x++) {
                TIntList lx = interim.get(x);
                pool.reset();
                for(int y = 0;y < pool.size();y++) {
                    int py = pool.next();
                    TIntArrayList tl = new TIntArrayList();
                    tl.addAll(lx);
                    tl.add(py);
                    result.add(tl);
                }
            }
        }
        
        return result.stream().mapToInt(tl -> indexFromCoordinates(tl.toArray())).toArray();
    }
    public int[] neighborhood(double[] centerIndex, int[] radius,Connections c) {
        //centerPosition = coordinatesFromIndex(centerIndex);//这里把一个中心点的索引，譬如460又转换成了相应的坐标,譬如[1,4]
        
        
        radius[0]=(int) (2+(centerIndex[1])*0.625);
        radius[1]=(int)(2+(centerIndex[1])*0.625);
        
        centerPosition= ArrayUtils.clip(ArrayUtils.toIntArray(centerIndex), c.getInputDimensions(), -1);
        
        igs = IntStream.range(0, dimensions.length)///IntStream.range()返回一个整数序列，本例是从0开始，一直到dimensions.length不包括，如果是2维，也就是[0,1]
            .mapToObj(i -> 
                IntGenerator.of(Math.max(0, centerPosition[i] - radius[i]), 
                    Math.min(dimensions[i], centerPosition[i] + radius[i])))///这地方得重写，没有这么简单
            .toArray(IntGenerator[]::new);//将输出值转换为IntGenerator[]类型的数组。 Java 8中使用了::的用法。就是把方法当做参数传递到stream内部，使得stream的每个元素都传入该方法里面执行一下
       
        List<TIntList> result = new ArrayList<>();
        result.add(new TIntArrayList());
        List<TIntList> interim = new ArrayList<>();
        for(IntGenerator pool : igs) {
            int size = result.size();
            interim.clear();
            interim.addAll(result);
            result.clear();
            for(int x = 0;x < size;x++) {
                TIntList lx = interim.get(x);
                pool.reset();
                for(int y = 0;y < pool.size();y++) {
                    int py = pool.next();
                    TIntArrayList tl = new TIntArrayList();
                    tl.addAll(lx);
                    tl.add(py);
                    result.add(tl);
                }
            }
        }
        
        return result.stream().mapToInt(tl -> indexFromCoordinates(tl.toArray())).toArray();
    }
    public int[] neighborhood(int centerIndex, int radius) {
        centerPosition = coordinatesFromIndex(centerIndex);//这里把一个中心点的索引，譬如460又转换成了相应的坐标,譬如[1,4]
        
        
        igs = IntStream.range(0, dimensions.length)///IntStream.range()返回一个整数序列，本例是从0开始，一直到dimensions.length不包括，如果是2维，也就是[0,1]
            .mapToObj(i -> 
                IntGenerator.of(Math.max(0, centerPosition[i] - radius), 
                    Math.min(dimensions[i] - 1, centerPosition[i] + radius) + 1))
            .toArray(IntGenerator[]::new);//将输出值转换为IntGenerator[]类型的数组。 Java 8中使用了::的用法。就是把方法当做参数传递到stream内部，使得stream的每个元素都传入该方法里面执行一下
       
        List<TIntList> result = new ArrayList<>();
        result.add(new TIntArrayList());
        List<TIntList> interim = new ArrayList<>();
        for(IntGenerator pool : igs) {
            int size = result.size();
            interim.clear();
            interim.addAll(result);
            result.clear();
            for(int x = 0;x < size;x++) {
                TIntList lx = interim.get(x);
                pool.reset();
                for(int y = 0;y < pool.size();y++) {
                    int py = pool.next();
                    TIntArrayList tl = new TIntArrayList();
                    tl.addAll(lx);
                    tl.add(py);
                    result.add(tl);
                }
            }
        }
        
        return result.stream().mapToInt(tl -> indexFromCoordinates(tl.toArray())).toArray();
    }
    
    
    /**
     * Like {@link #neighborhood(int, int)}, except that the neighborhood isn't truncated when it's
     * near an edge. It wraps around to the other side.就像neighborhood(int,int),除了邻域在靠近边时不会被截断，它绕到另一边。
     * 
     * @param centerIndex       The index of the point. The coordinates are expressed as a single index by
     *                          using the dimensions as a mixed radix definition. For example, in dimensions
     *                          42x10, the point [1, 4] is index 1*420 + 4*10 = 460.
     * @param radius            The radius of this neighborhood about the centerIndex.
     * @return  The points in the neighborhood, including centerIndex.
     */
    public int[] wrappingNeighborhood(int centerIndex, int[] radius) {
        int[] cp = coordinatesFromIndex(centerIndex);
        
        radius[0]=(int) (2+(centerPosition[1]-1)*0.625);
        radius[1]=(int)(2+(centerPosition[1]-1)*0.5);
        
        IntGenerator[] igs = IntStream.range(0, dimensions.length)//对于每一个维度，如果是一维就只有1，如果是多维，就是n
            .mapToObj(i -> 
                new IntGenerator(cp[i] - radius[i], 
                    Math.min((cp[i] - radius[i]) + dimensions[i] - 1, cp[i] + radius[i]) + 1))///产生一个centerIndex所覆盖的输入对象的范围数组，是坐标形式的
            .toArray(IntGenerator[]::new);
        
        List<TIntList> result = new ArrayList<>();//存储TIntList的数组，看来有多个TIntList要生成
        result.add(new TIntArrayList());//先放置一个空的TIntArrayList
        List<TIntList> interim = new ArrayList<>();//临时的存储TIntList的数组
        for(int i = 0;i < igs.length;i++) 
        {
            IntGenerator pool = igs[i];//获取某一维度要读取的范围
            int size = result.size();//获取结果数组的元素的数量
            interim.clear();
            interim.addAll(result);
            result.clear();
            for(int x = 0;x < size;x++) ///这个size取值只能是1吧，这段代码有问题
            {   
            	TIntList lx = interim.get(x);
                pool.reset();
                for(int y = 0;y < pool.size();y++) {
                    int py = ArrayUtils.modulo(pool.next(), dimensions[i]);
                    TIntArrayList tl = new TIntArrayList();
                    tl.addAll(lx);
                    tl.add(py);
                    result.add(tl);
                }
            }
        }
        
        return result.stream().mapToInt(tl -> indexFromCoordinates(tl.toArray())).toArray();
    }
    /**
     * 新增一个
     * @param centerIndex
     * @param radius
     * @return
     */
    public int[] wrappingNeighborhood(int centerIndex, int radius) {
        int[] cp = coordinatesFromIndex(centerIndex);
        
        IntGenerator[] igs = IntStream.range(0, dimensions.length)//对于每一个维度，如果是一维就只有1，如果是多维，就是n
            .mapToObj(i -> 
                new IntGenerator(cp[i] - radius, 
                    Math.min((cp[i] - radius) + dimensions[i] - 1, cp[i] + radius) + 1))///产生一个centerIndex所覆盖的输入对象的范围数组，是坐标形式的
            .toArray(IntGenerator[]::new);
        
        List<TIntList> result = new ArrayList<>();//存储TIntList的数组，看来有多个TIntList要生成
        result.add(new TIntArrayList());//先放置一个空的TIntArrayList
        List<TIntList> interim = new ArrayList<>();//临时的存储TIntList的数组
        for(int i = 0;i < igs.length;i++) 
        {
            IntGenerator pool = igs[i];//获取某一维度要读取的范围
            int size = result.size();//获取结果数组的元素的数量
            interim.clear();
            interim.addAll(result);
            result.clear();
            for(int x = 0;x < size;x++) ///这个size取值只能是1吧，这段代码有问题
            {   
            	TIntList lx = interim.get(x);
                pool.reset();
                for(int y = 0;y < pool.size();y++) {
                    int py = ArrayUtils.modulo(pool.next(), dimensions[i]);
                    TIntArrayList tl = new TIntArrayList();
                    tl.addAll(lx);
                    tl.add(py);
                    result.add(tl);
                }
            }
        }
        
        return result.stream().mapToInt(tl -> indexFromCoordinates(tl.toArray())).toArray();
    }
    
}
