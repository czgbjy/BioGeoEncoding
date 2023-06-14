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

/**
 * Represents a connection with varying strength which when above 
 * a configured threshold represents a valid connection. 
 * 
 * IMPORTANT: 	For DistalDendrites, there is only one synapse per pool, so the
 * 				synapse's index doesn't really matter (in terms of tracking its
 * 				order within the pool). In that case, the index is a global counter
 * 				of all distal dendrite synapses.
 * 
 * 				For ProximalDendrites, there are many synapses within a pool, and in
 * 				that case, the index specifies the synapse's sequence order within
 * 				the pool object, and may be referenced by that index.
 *  表示具有不同强度的连接，当超过配置的阈值时，该连接表示有效的连接。对于末端树突，每个池只有一个synapse,因此synapse的索引实际上并不重要（就跟踪其在池中的顺序而言）。  
 * 在这种情况下，索引是所有末端树突突触的全局计数器。对于近端树突，在一个池中有很多的突触，在这种情况下，索引指定了突触在池对象中的序列顺序，并且可以被该索引引用。
 * @author Chetan Surpur
 * @author David Ray
 * 
 * @see DistalDendrite
 * @see Connections
 */
public class Synapse implements Persistable, Comparable<Synapse> {
    /** keep it simple */
    private static final long serialVersionUID = 1L;
    /**
     * 突触前单元
     */
    private Cell sourceCell;
    private Segment segment;
    private Pool pool;
    private int synapseIndex;
    private Integer boxedIndex;
    private int inputIndex;
    private double permanence;
    private boolean destroyed;
    
    /**
     * Constructor used when setting parameters later.
     */
    public Synapse() {}
    
    /**
     * Constructs a new {@code Synapse} for a {@link DistalDendrite}
     * 为一个基底树突构建一个新的突触
     * @param sourceCell    the {@link Cell} which will activate this {@code Synapse}; 突触前单元
     * @param segment       the owning dendritic segment 拥有者突触的树突
     * @param pool          this {@link Pool} of which this synapse is a member
     * @param index         this {@code Synapse}'s index 这个突触的索引
     * @param permanence     这个突触的持久度值
     */
    public Synapse(Cell presynapticCell, Segment segment, int index, double permanence) {
        this.sourceCell = presynapticCell;//突触前细胞
        this.segment = segment;//突触所在的树突
        this.synapseIndex = index;//突触的索引
        this.boxedIndex = new Integer(index);//突触的池的单元索引
        this.inputIndex = presynapticCell.getIndex();//突触的输入索引，是其突触前单元的索引
        this.permanence = permanence;//突触的初始化持久度值
    }

    /**
     * Constructs a new {@code Synapse}
     * 创建一个新的突触
     * @param c             the connections state of the temporal memory
     * @param sourceCell    the {@link Cell} which will activate this {@code Synapse};
     *                      Null if this Synapse is proximal
     * @param segment       the owning dendritic segment
     * @param pool		    this {@link Pool} of which this synapse is a member
     * @param index         this {@code Synapse}'s index
     * @param inputIndex	the index of this {@link Synapse}'s input; be it a Cell or InputVector bit.
     */
    public Synapse(Connections c, Cell sourceCell, Segment segment, Pool pool, int index, int inputIndex) {
        this.sourceCell = sourceCell;
        this.segment = segment;//所属的树突
        this.pool = pool;//树突的池
        this.synapseIndex = index;
        this.boxedIndex = new Integer(index);
        this.inputIndex = inputIndex;
    }

    /**
     * Returns this {@code Synapse}'s index.
     * @return
     */
    public int getIndex() {
        return synapseIndex;
    }

    /**
     * Returns the index of this {@code Synapse}'s input item
     * whether it is a "sourceCell" or inputVector bit.
     * @return
     */
    public int getInputIndex() {
        return inputIndex;
    }

    /**
     * Returns this {@code Synapse}'s degree of connectedness.
     * @return
     */
    public double getPermanence() {
        return permanence;
    }

    /**
     * Sets this {@code Synapse}'s degree of connectedness.
     * 设置突触的连接持久度
     * @param perm
     */
    public void setPermanence(Connections c, double perm) {
        this.permanence = perm;
        
        // On proximal dendrite which has no presynaptic cell
        if(sourceCell == null) {
            pool.updatePool(c, this, perm);
        }
    }

    /**
     * Returns the owning dendritic segment
     * @return
     */
    public Segment getSegment() {
        return segment;
    }
    
    /**
     * Called by {@link Connections#destroySynapse(Synapse)} to assign
     * a reused Synapse to another presynaptic Cell
     * @param cell  the new presynaptic cell
     */
    public void setPresynapticCell(Cell cell) {
        this.sourceCell = cell;
    }

    /**
     * Returns the containing {@link Cell} 
     * @return
     */
    public Cell getPresynapticCell() {
        return sourceCell;
    }
    
    /**
     * Returns the flag indicating whether this segment has been destroyed.
     * @return  the flag indicating whether this segment has been destroyed.
     */
    public boolean destroyed() {
        return destroyed;
    }
    
    /**
     * Sets the flag indicating whether this segment has been destroyed.
     * @param b the flag indicating whether this segment has been destroyed.
     */
    public void setDestroyed(boolean b) {
        this.destroyed = b;
    }

    /**
     * {@inheritDoc}
     */
    public String toString() {
        StringBuilder sb = new StringBuilder("synapse: [ synIdx=").append(synapseIndex).append(", inIdx=")
            .append(inputIndex).append(", sgmtIdx=").append(segment.getIndex());
        if(sourceCell != null) {
            sb.append(", srcCellIdx=").append(sourceCell.getIndex());
        }
        sb.append(" ]");
        return sb.toString();
    }
    
    /**
     * {@inheritDoc}
     * 
     * <em> Note: All comparisons use the segment's index only </em>
     */
    @Override
    public int compareTo(Synapse arg0) {
        return boxedIndex.compareTo(arg0.boxedIndex);
    }

    /* (non-Javadoc)
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + inputIndex;
        result = prime * result + ((segment == null) ? 0 : segment.hashCode());
        result = prime * result + ((sourceCell == null) ? 0 : sourceCell.hashCode());
        result = prime * result + synapseIndex;
        return result;
    }

    /* (non-Javadoc)
     * @see java.lang.Object#equals(java.lang.Object)
     */
    @Override
    public boolean equals(Object obj) {
        if(this == obj)
            return true;
        if(obj == null)
            return false;
        if(getClass() != obj.getClass())
            return false;
        Synapse other = (Synapse)obj;
        if(inputIndex != other.inputIndex)
            return false;
        if(segment == null) {
            if(other.segment != null)
                return false;
        } else if(!segment.equals(other.segment))
            return false;
        if(sourceCell == null) {
            if(other.sourceCell != null)
                return false;
        } else if(!sourceCell.equals(other.sourceCell))
            return false;
        if(synapseIndex != other.synapseIndex)
            return false;
        if(permanence != other.permanence)
            return false;
        return true;
    }
}
