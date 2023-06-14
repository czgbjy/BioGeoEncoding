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

import java.util.stream.IntStream;
import org.numenta.nupic.util.ArrayUtils;
import gnu.trove.map.TIntObjectMap;
import gnu.trove.map.hash.TIntObjectHashMap;
import gnu.trove.set.hash.TIntHashSet;

/**
 * Convenience container for "bound" {@link Synapse} values
 * which can be dereferenced from both a Synapse and the 
 * {@link Connections} object. All Synapses will have a reference
 * to a {@code Pool} to retrieve relevant values. In addition, that
 * same pool can be referenced from the Connections object externally
 * which will update the Synapse's internal reference.
 * 绑定突触值的方便容器，可以从Synapse和Connections对象中取消引用。所有Synapses都有一个Pool的索引以方便检索相关的值。
 * 此外，可以从外部的Connections对象引用相同的池，该对象将更新Synapse的内部引用。
 * 池是对于基底树突，一个一个突触有一个池，对于近端树突，是多个突触一个池，池中记录了多个突触潜在连接的突触前单元的索引，以及连接索引。提供了一些相关的操作，
 * 总体上感觉是一个近端树突对应的潜在输入感受野。      对于末端树突，每个池只有一个synapse
 * @author David Ray
 * @see Synapse
 * @see Connections
 */
public class Pool implements Persistable {
    /** keep it simple */
    private static final long serialVersionUID = 1L;
    /*池中元素的多少**/
    int size;

    /** Allows fast removal of connected synapse indexes.允许快速删除连接的突触的索引，这里存储的是突触与连接的突触前单元索引，HashSet是HashMap特殊的一种，存储不重复的值，这里存储不重复的int值 */
    private TIntHashSet synapseConnections = new TIntHashSet();
    /** 
     * Indexed according to the source Input Vector Bit (for ProximalDendrites),
     * and source cell (for DistalDendrites).根据源输入向量的二进制位（对于近端树突）和源单元（对远端树突）编制索引，存储的是突触前单元的索引与多个突触的匹配值
     */
    private TIntObjectMap<Synapse> synapsesBySourceIndex = new TIntObjectHashMap<>();

    public Pool(int size) {
        this.size = size;
    }

    /**
     * Returns the permanence value for the {@link Synapse} specified.
     * 获取指定树突的持久度值
     * @param s	the Synapse
     * @return	the permanence
     */
    public double getPermanence(Synapse s) {
        return synapsesBySourceIndex.get(s.getInputIndex()).getPermanence();//这个很有意思，s.getInputIndex（）获取的实际上是突触前单元的索引，synapsesBySourceIndex.get（突触前单元索引）获取对应的突触，
                                                                            //说明synapsesBySourceIndex存储的内容为synapsesBySourceIndex.put(突触前单元id,突触)
    }

    /**
     * Sets the specified  permanence value for the specified {@link Synapse}为指定的突触设置持久度值
     * @param s
     * @param permanence
     */
    public void setPermanence(Connections c, Synapse s, double permanence) {
        s.setPermanence(c, permanence);
    }

    /**
     * Updates this {@code Pool}'s store of permanences for the specified {@link Synapse}，把没有放入这个池中的突触添加至池，并根据这个突触的持久度值的大小决定突触是否成为连接突触，并用数组记录连接突触的索引
     * @param c				the connections memory
     * @param s				the synapse who's permanence is recorded
     * @param permanence	the permanence value to record
     */
    public void updatePool(Connections c, Synapse s, double permanence) {
        int inputIndex = s.getInputIndex();//获取突触的输入索引，也就是突触前单元的索引
        if(synapsesBySourceIndex.get(inputIndex) == null)//获取这个突触前单元的所有突触列表，如果这个突触前单元还没有突触，
        {
            synapsesBySourceIndex.put(inputIndex, s);//把突触添加至这个突触前单元所对应的突触列表中
        }
        if(permanence >= c.getSynPermConnected())//如果持久度值大于阈值
        {
            synapseConnections.add(inputIndex);//把这个突触前单元的索引值加入连接突触的列表中去，synapseConnections记录的是连接的突触前单元的列表
        }
        else
        {
            synapseConnections.remove(inputIndex);//如果持久度值小于阈值，那么从突触连接的突触前单元数组中删除这个单元的索引
        }
    }

    /**
     * Resets the current connections in preparation for new permanence
     * adjustments.
     * 重置当前当前连接以准备新的持久度值调整
     */
    public void resetConnections() {
        synapseConnections.clear();
    }

    /**
     * Returns the {@link Synapse} connected to the specified input bit
     * index.
     * 返回连接到指定输入位索引的Synapse,也就是指定突触前单元对应的突触
     * @param inputIndex	the input vector connection's index. 输入向量连接的索引
     * @return 
     */
    public Synapse getSynapseWithInput(int inputIndex) {
        return synapsesBySourceIndex.get(inputIndex);
    }

    /**
     * Returns an array of permanence values
     * 返回池中所有突触的持久度值集合
     * @return
     */
    public double[] getSparsePermanences() {
        double[] retVal = new double[size];
        int[] keys = synapsesBySourceIndex.keys();//获取的实际上是某个突触潜在连接的突触前单元的集合
        for(int x = 0, j = size - 1;x < size;x++, j--)
        {
            retVal[j] = synapsesBySourceIndex.get(keys[x]).getPermanence();//获取突触的持久度值集合 如果pool的size正好和突触的数量一致可以取完，如果不一致，就取不完，甚至出错
        }

        return retVal;
    }

    /**
     * Returns a dense array representing the potential pool permanences
     * 获取潜在池的持久度的数组
     * Note: Only called from tests for now...
     * @param c
     * @return
     */
    public double[] getDensePermanences(Connections c) {
        double[] retVal = new double[c.getNumInputs()];//为所有的输入建立一个数组
        int[] keys = synapsesBySourceIndex.keys();//把这个池中存储的建立突触（包括已经连接的和潜在连接的）的突触前单元序号取出来，
        for(int inputIndex : keys) {
            retVal[inputIndex] = synapsesBySourceIndex.get(inputIndex).getPermanence();//获取现有的建立突触的所有单元的突触的持久度值
        }
        return retVal;
    }

    /**
     * Returns an array of input bit indexes indicating the index of the source. 
     * (input vector bit or lateral cell)
     * 返回输入比特的索引集合，输入比特是指突触前单元的索引，这个输入值的索引包括连接上的没连接上的
     * @return the sparse array
     */
    public int[] getSparsePotential() {
        return ArrayUtils.reverse(synapsesBySourceIndex.keys());
    }
    
    /**
     * Returns a dense binary array containing 1's where the input bits are part
     * of this pool.
     * 返回包含1的二进制数组，1代表输入位是池的一部分。。。判断所有输入单元是否潜在连接了这些突触，如果连接了值为1，如果没有连接，值为0，进而生成一个0,1数组。
     * @param c     the {@link Connections}
     * @return  dense binary array of member inputs
     */
    public int[] getDensePotential(Connections c) {
        return IntStream.range(0, c.getNumInputs())///首先IntStream.range()函数返回一个子序列[a,b),c.getNumInputs()实际获取的是输入数据的总数
            .map(i -> synapsesBySourceIndex.containsKey(i) ? 1 : 0)//i -> synapsesBySourceIndex.containsKey(i) ? 1 : 0  是一个匿名函数，其中i代表输入，后面代表函数体；.map函数把子序列中的值作为i传入匿名函数，所以相当于判断输入比特是不是本输入池的一部分，获取潜在连接的比特数组，返回的是一个0,1数组
            .toArray();//这个函数的含义为判断所有输入单元是否潜在连接了这些突触，如果连接了值为1，如果没有连接，值为0，进而生成一个0,1数组。synapsesBySourceIndex是否
    }
    
    /**
     * Returns an binary array whose length is equal to the number of inputs;
     * and where 1's are set in the indexes of this pool's assigned bits.
     * 判断所有输入单元是否与这些（个）突触形成连接，形成连接的话其值为1，否则为0，生成一个二进制数组，二进制数组的长度等于输入的长度
     * @param   c   {@link Connections}
     * @return the sparse array
     */
    public int[] getDenseConnected(Connections c) {
        return IntStream.range(0, c.getNumInputs())
            .map(i -> synapseConnections.contains(i) ? 1 : 0)
            .toArray();//这个函数的含义是判断所有输入单元是否与这些（个）突触形成连接，形成连接的话其值为1，否则为0，生成一个二进制数组
    }

    /**
     * Destroys any references this {@code Pool} maintains on behalf
     * of the specified {@link Synapse}
     * 销毁这个池维护的任何索引所代表的指定突触
     * @param synapse
     */
    public void destroySynapse(Synapse synapse) {
        synapseConnections.remove(synapse.getInputIndex());//删除池所存储的连接突触
        synapsesBySourceIndex.remove(synapse.getInputIndex());//删除池所存储的潜在连接突触
        if(synapse.getSegment() instanceof DistalDendrite) {
            destroy();//如果是基底树突的话，直接把这池也销毁了，因为对于基底树突，每个池只有一个synapse
        }
    }
    
    /**
     * Clears the state of this {@code Pool}
     */
    public void destroy() {
        synapseConnections.clear();
        synapsesBySourceIndex.clear();
        synapseConnections = null;
        synapsesBySourceIndex = null;
    }

    /* (non-Javadoc)
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + size;
        result = prime * result + ((synapseConnections == null) ? 0 : synapseConnections.toString().hashCode());
        result = prime * result + ((synapsesBySourceIndex == null) ? 0 : synapsesBySourceIndex.toString().hashCode());
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
        Pool other = (Pool)obj;
        if(size != other.size)
            return false;
        if(synapseConnections == null) {
            if(other.synapseConnections != null)
                return false;
        } else if((!synapseConnections.containsAll(other.synapseConnections) || 
            !other.synapseConnections.containsAll(synapseConnections)))
                return false;
        if(synapsesBySourceIndex == null) {
            if(other.synapsesBySourceIndex != null)
                return false;
        } else if(!synapsesBySourceIndex.toString().equals(other.synapsesBySourceIndex.toString()))
            return false;
        return true;
    }
}
