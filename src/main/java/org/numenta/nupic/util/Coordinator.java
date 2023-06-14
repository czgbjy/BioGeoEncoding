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

/**
 * Specializes in handling coordinate transforms for N-dimensional
 * integer arrays, between flat and coordinate indexing.专门为N维整数数组处理坐标转换的，实现N维整数数组的平面索引和坐标索引之间的转换。
 * 
 * @author cogmission
 * @see Topology
 */
public class Coordinator implements Serializable {
    /** keep it simple */
    private static final long serialVersionUID = 1L;

    protected int[] dimensions;
    protected int[] dimensionMultiples;
    protected boolean isColumnMajor;
    protected int numDimensions;
    
    /**
     * Constructs a new {@link Coordinator} object to be configured with specified
     * dimensions and major ordering.
     * @param shape  the dimensions of this matrix 
     */
    public Coordinator(int[] shape) {
        this(shape, false);
    }

    /**
     * Constructs a new {@link Coordinator} object to be configured with specified
     * dimensions and major ordering.
     * 构造一个新的Coordinator对象，用指定的维度和主要顺序配置
     * @param shape                     the dimensions of this sparse array 
     * @param useColumnMajorOrdering    flag indicating whether to use column ordering or
     *                                  row major ordering. if false (the default), then row
     *                                  major ordering will be used. If true, then column major
     *                                  ordering will be used.
     */
    public Coordinator(int[] shape, boolean useColumnMajorOrdering) {
        this.dimensions = shape;//初始化维度数组
        this.numDimensions = shape.length;//维度数组的维数
        this.dimensionMultiples = initDimensionMultiples(
            useColumnMajorOrdering ? reverse(shape) : shape);//这个dimensionMultiple是维度的乘法的算子
        isColumnMajor = useColumnMajorOrdering;
    }
    
    /**
     * Returns a flat index computed from the specified coordinates
     * which represent a "dimensioned" index.
     * 
     * @param   coordinates     an array of coordinates
     * @return  a flat index
     */
    public int computeIndex(int[] coordinates) {
        int[] localMults = isColumnMajor ? reverse(dimensionMultiples) : dimensionMultiples;
        int base = 0;
        for(int i = 0;i < coordinates.length;i++) {
            base += (localMults[i] * coordinates[i]);
        }
        return base;
    }

    /**
     * Returns an array of coordinates calculated from
     * a flat index.
     * 
     * @param   index   specified flat index
     * @return  a coordinate array
     */
    public int[] computeCoordinates(int index) {
        int[] returnVal = new int[numDimensions];
        int base = index;
        for(int i = 0;i < dimensionMultiples.length; i++) {
            int quotient = base / dimensionMultiples[i];///这个是整除，求整除的值
            base %= dimensionMultiples[i];////这个是取余数，获取余数值
            returnVal[i] = quotient;
        }
        return isColumnMajor ? reverse(returnVal) : returnVal;
    }

    /**
     * Initializes internal helper array which is used for multidimensional
     * index computation. 初始化内部的帮助数组，被用来多维索引计算
     * @param dimensions matrix dimensions 矩阵的维度数组
     * @return array for use in coordinates to flat index computation. 用于坐标系中的数组，用于展开索引计算
     */
    protected int[] initDimensionMultiples(int[] dimensions) {
        int holder = 1;
        int len = dimensions.length;
        int[] dimensionMultiples = new int[numDimensions];
        for(int i = 0;i < len;i++) {
            holder *= (i == 0 ? 1 : dimensions[len - i]);
            dimensionMultiples[len - 1 - i] = holder;
        }
        return dimensionMultiples;
    }
    
    /**
     * Reverses the specified array.
     * @param input
     * @return
     */
    public static int[] reverse(int[] input) {
        int[] retVal = new int[input.length];
        for(int i = input.length - 1, j = 0;i >= 0;i--, j++) {
            retVal[j] = input[i];
        }
        return retVal;
    }
}
