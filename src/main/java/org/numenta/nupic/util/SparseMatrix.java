/* ---------------------------------------------------------------------
 * Numenta Platform for Intelligent Computing (NuPIC)
 * Copyright (C) 2014, Numenta, Inc.  Unless you have an agreement
 * with Numenta, Inc., for a separate license for this software code, the
 * following terms and conditions apply:
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero Public License version 3 as
 * published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Affero Public License for more details.
 *
 * You should have received a copy of the GNU Affero Public License
 * along with this program.  If not, see http://www.gnu.org/licenses.
 *
 * http://numenta.org/licenses/
 * ---------------------------------------------------------------------
 */

package org.numenta.nupic.util;

/**
 * Allows storage of array data in sparse form, meaning that the indexes
 * of the data stored are maintained while empty indexes are not. This allows
 * savings in memory and computational efficiency because iterative algorithms
 * need only query indexes containing valid data. The dimensions of matrix defined
 * at construction time and immutable - matrix fixed size data structure.允许以稀疏形式存储数组数据
 * 这意味着维护存储的数据的索引，而不维护空索引。这可以节省内存和计算效率，因为迭代算法只需要包含有效数据的查询索引。
 * 构造时定义的矩阵维数和不变矩阵固定大小的数据结构。
 * 
 * @author David Ray
 * @author Jose Luis Martin
 *
 * @param <T>
 */
public interface SparseMatrix<T> extends FlatMatrix<T>{

    /**
     * Returns a sorted array of occupied indexes.
     * @return  a sorted array of occupied indexes.
     */
    int[] getSparseIndices();

    /**
     * Returns an array of all the flat indexes that can be 
     * computed from the current configuration.
     * @return
     */
    int[] get1DIndexes();

    /**
     * Uses the specified {@link TypeFactory} to return an array
     * filled with the specified object type, according this {@code SparseMatrix}'s 
     * configured dimensions
     * 
     * @param factory   a factory to make a specific type
     * @return  the dense array
     */
    T[] asDense(TypeFactory<T> factory);

}