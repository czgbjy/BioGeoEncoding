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

package org.numenta.nupic.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import org.numenta.nupic.algorithms.SpatialPooler;
import org.numenta.nupic.algorithms.TemporalMemory;

/**
 * Abstraction of both an input bit and a columnal collection of
 * {@link Cell}s which have behavior associated with membership to
 * a given {@code Column}
 * 列的单元集合和一个输入位的抽象，它有着一个给定列的成员身份相关
 * @author Chetan Surpur
 * @author David Ray
 *
 */
public class Column implements Comparable<Column>, Serializable {
    /** keep it simple */
    private static final long serialVersionUID = 1L;
    
    /** The flat non-topological index of this column 列的索引 */
    private final int index;
    /** Stored boxed form to eliminate need for boxing on the fly 存储打包的索引以防止紧急需要打包 */
    private final Integer boxedIndex;
    /** Configuration of cell count 单元的数量 */
    private final int numCells;
    /** Connects {@link SpatialPooler} input pools 连接SpatialPooler输入池，也就是前段树突 */
    private ProximalDendrite proximalDendrite;
    /**存储了列的所有单元，是一个数组*/
    private Cell[] cells;
    /**存储了列的所有单元，是一个不可修改的数组*/
    private List<Cell> cellList;
    
    private final int hashcode;

    /**
     * Constructs a new {@code Column}
     * 产生一个新的列
     * @param numCells      number of cells per column 列中单元的数量
     * @param index         the index of this column 列的索引
     */
    public Column(int numCells, int index) {
        this.numCells = numCells;//记录这个列的单元的数量
        this.index = index;//记录这个列的索引
        this.boxedIndex = index;//这个列的索引，也是这个列的打包索引
        this.hashcode = hashCode();
        cells = new Cell[numCells];//新建单元数组
        for(int i = 0;i < numCells;i++) {
            cells[i] = new Cell(this, i);//为每个单元数组新建单元对象，单元对象的索引从0，一直到这个列的单元的数量
        }
        
        cellList = Collections.unmodifiableList(Arrays.asList(cells));//创建不可修改的单元数组
        
        proximalDendrite = new ProximalDendrite(index);//创建一个前端树突，这个前端树突的索引为列的索引，它们相同
    }

    /**
     * Returns the {@link Cell} residing at the specified index.
     * <p>
     * <b>IMPORTANT NOTE:</b> the index provided is the index of the Cell within this
     * column and is <b>not</b> the actual index of the Cell within the total
     * list of Cells of all columns. Each Cell maintains it's own <i><b>GLOBAL</i></b>
     * index which is the index describing the occurrence of a cell within the
     * total list of all cells. Thus, {@link Cell#getIndex()} returns the <i><b>GLOBAL</i></b>
     * index and <b>not</b> the index within this column.
     * 
     * @param index     the index of the {@link Cell} to return.
     * @return          the {@link Cell} residing at the specified index.
     */
    public Cell getCell(int index) {
        return cells[index];
    }

    /**
     * Returns a {@link List} view of this {@code Column}'s {@link Cell}s.
     * @return
     */
    public List<Cell> getCells() {
        return cellList;
    }

    /**
     * Returns the index of this {@code Column}
     * @return  the index of this {@code Column}
     */
    public int getIndex() {
        return index;
    }

    /**
     * Returns the configured number of cells per column for
     * all {@code Column} objects within the current {@link TemporalMemory}
     * @return
     */
    public int getNumCellsPerColumn() {
        return numCells;
    }

    /**
     * Returns the {@link Cell} with the least number of {@link DistalDendrite}s.
     * 
     * @param c         the connections state of the temporal memory
     * @param random
     * @return
     */
    public Cell getLeastUsedCell(Connections c, Random random) {
        List<Cell> leastUsedCells = new ArrayList<>();
        int minNumSegments = Integer.MAX_VALUE;

        for(Cell cell : cellList) {
            int numSegments = cell.getSegments(c).size();

            if(numSegments < minNumSegments) {
                minNumSegments = numSegments;
                leastUsedCells.clear();
            }

            if(numSegments == minNumSegments) {
                leastUsedCells.add(cell);
            }
        }
        int index = random.nextInt(leastUsedCells.size());
        Collections.sort(leastUsedCells);
        return leastUsedCells.get(index); 
    }

    /**
     * Returns this {@code Column}'s single {@link ProximalDendrite}
     * @return
     */
    public ProximalDendrite getProximalDendrite() {
        return proximalDendrite;
    }

    /**
     * Delegates the potential synapse creation to the one {@link ProximalDendrite}.
     * 将潜在的突触生成委托给一个近端树突
     * @param c						the {@link Connections} memory 连接Connections
     * @param inputVectorIndexes	indexes specifying the input vector bit 指定输入向量比特的索引
     */
    public Pool createPotentialPool(Connections c, int[] inputVectorIndexes) {
        return proximalDendrite.createPool(c, inputVectorIndexes);
    }

    /**
     * Sets the permanences on the {@link ProximalDendrite} {@link Synapse}s
     * 设置前端树突上突触的持久度值
     * @param c				the {@link Connections} memory object Connections对象
     * @param permanences	floating point degree of connectedness 持久度的数组
     */
    public void setProximalPermanences(Connections c, double[] permanences) {
        proximalDendrite.setPermanences(c, permanences);
    }

    /**
     * Sets the permanences on the {@link ProximalDendrite} {@link Synapse}s
     * 
     * @param c				the {@link Connections} memory object
     * @param permanences	floating point degree of connectedness
     */
    public void setProximalPermanencesSparse(Connections c, double[] permanences, int[] indexes) {
        proximalDendrite.setPermanences(c, permanences, indexes);
    }

    /**
     * Delegates the call to set synapse connected indexes to this 
     * {@code Column}'s {@link ProximalDendrite}
     * @param c
     * @param connections
     */
    public void setProximalConnectedSynapsesForTest(Connections c, int[] connections) {
        proximalDendrite.setConnectedSynapsesForTest(c, connections);
    }

    /**
     * {@inheritDoc}
     */
    public String toString() {
        return "" + index;
    }

    /**
     * {@inheritDoc}
     * @param otherColumn     the {@code Column} to compare to
     * @return
     */
    @Override
    public int compareTo(Column otherColumn) {
        return boxedIndex.compareTo(otherColumn.boxedIndex);
    }

    @Override
    public int hashCode() {
        if(hashcode == 0) {
            final int prime = 31;
            int result = 1;
            result = prime * result + index;
            return result;
        }
        return hashcode;
    }

    @Override
    public boolean equals(Object obj) {
        if(this == obj)
            return true;
        if(obj == null)
            return false;
        if(getClass() != obj.getClass())
            return false;
        Column other = (Column)obj;
        if(index != other.index)
            return false;
        return true;
    }
}
