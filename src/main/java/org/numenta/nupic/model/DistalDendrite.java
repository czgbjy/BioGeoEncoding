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

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Represents a proximal or distal dendritic segment. Segments are owned by
 * {@link Cell}s and in turn own {@link Synapse}s which are obversely connected
 * to by a "source cell", which is the {@link Cell} which will activate a given
 * {@link Synapse} owned by this {@code Segment}.
 * 代表一个近端或者远端树突。单元拥有树突，树突拥有突触，
 * 突触被显式的连接到一个源细胞上，源细胞将激活树突所有的给定突触
 * @author Chetan Surpur
 * @author David Ray
 */
public class DistalDendrite extends Segment implements Persistable {
    /** keep it simple */
    private static final long serialVersionUID = 1L;
    
    private Cell cell;
    
    private long lastUsedIteration;
    
    public int ordinal = -1;
    
    /**
     * Constructs a new {@code Segment} object with the specified owner
     * {@link Cell} and the specified index.
     * 
     * @param cell      the owner
     * @param flatIdx     this {@code Segment}'s index.
     */
    public DistalDendrite(Cell cell, int flatIdx, long lastUsedIteration, int ordinal) {
        super(flatIdx);
        
        this.cell = cell;
        this.ordinal = ordinal;
        this.lastUsedIteration = lastUsedIteration;
    }

    /**
     * Returns the owner {@link Cell}
     * 
     * @return
     */
    public Cell getParentCell() {
        return cell;
    }
    
    /**
     * Returns all {@link Synapse}s
     * 返回本树突的突触集合
     * @param c     the connections state of the temporal memory 
     * @return
     */
    public List<Synapse> getAllSynapses(Connections c) {
        return c.getSynapses(this);
    }

    /**
     * Returns the synapses on a segment that are active due to lateral input
     * from active cells.
     * 返回树突上激活的突触集合，这些突触激活的原因是因为来自于激活单元的横向输入
     * @param c                 the layer connectivity 图层的连通性
     * @param activeCells       the active cells 激活细胞
     * @return  Set of {@link Synapse}s connected to active presynaptic cells. 连接到激活突触前细胞的突触集合
     */
    public Set<Synapse> getActiveSynapses(Connections c, Set<Cell> activeCells) {
        Set<Synapse> synapses = new LinkedHashSet<>();
        
        for(Synapse synapse : c.getSynapses(this))
        {
            if(activeCells.contains(synapse.getPresynapticCell()))//如果这个突触的突触前细胞是激活细胞，那么这个突触就是激活突触
            {
                synapses.add(synapse);
            }
        }
        
        return synapses;
    }

    /**
     * Sets the last iteration in which this segment was active.
     * @param iteration
     */
    public void setLastUsedIteration(long iteration) {
        this.lastUsedIteration = iteration;
    }
    
    /**
     * Returns the iteration in which this segment was last active.
     * 返回此基底树突上次处于活跃状态的迭代标记（时间标记）
     * @return  the iteration in which this segment was last active.
     */
    public long lastUsedIteration() {
        return lastUsedIteration;
    }
    
    /**
     * Returns this {@code DistalDendrite} segment's ordinal
     * 返回此基底树突的序号，这个序号是标记树突产生先后的序号
     * @return	this segment's ordinal
     */
    public int getOrdinal() {
		return ordinal;
	}

    /**
     * Sets the ordinal value (used for age determination) on this segment.
     * 设置此段的序数值，用于年龄确定
     * @param ordinal	the age or order of this segment
     */
	public void setOrdinal(int ordinal) {
		this.ordinal = ordinal;
	}

	/**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return String.valueOf(index);
    }

    /* (non-Javadoc)
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + ((cell == null) ? 0 : cell.hashCode());
        return result;
    }

    /* (non-Javadoc)
     * @see java.lang.Object#equals(java.lang.Object)
     */
    @Override
    public boolean equals(Object obj) {
        if(this == obj)
            return true;
        if(!super.equals(obj))
            return false;
        if(getClass() != obj.getClass())
            return false;
        DistalDendrite other = (DistalDendrite)obj;
        if(cell == null) {
            if(other.cell != null)
                return false;
        } else if(!cell.equals(other.cell))
            return false;
        return true;
    }
    
    
  
}
