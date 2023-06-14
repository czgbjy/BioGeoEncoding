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

import java.util.List;

public class ProximalDendrite extends Segment implements Persistable {
    /** keep it simple */
    private static final long serialVersionUID = 1L;
    /*每个近端树突都有一个池，存储突触**/
    private Pool pool;

    /**
     * 
     * @param index     this {@code ProximalDendrite}'s index.
     */
    public ProximalDendrite(int index) {
        super(index);
    }

    /**
     * Creates the pool of {@link Synapse}s representing the connection
     * to the input vector.
     * 创建 表示输入向量到连接的突触池
     * @param c					the {@link Connections} memory Connections
     * @param inputIndexes		indexes specifying the input vector bit 指定输入向量bit的索引
     */
    public Pool createPool(Connections c, int[] inputIndexes) {
        pool = new Pool(inputIndexes.length);//为这个树突新建一个池
        for(int i = 0;i < inputIndexes.length;i++) {//对于选中的每一个输入，创建相应的连接突触
            int synCount = c.getProximalSynapseCount();//获取近端树突突触的数量，作为突触的编号
            pool.setPermanence(c, createSynapse(c, c.getSynapses(this), null, pool, synCount, inputIndexes[i]), 0);//创建一个新的突触，并把突触添加至这个树突的突触列表，设置这个突触的持久度值，在Pool中通过输入索引记录这个突触
            c.setProximalSynapseCount(synCount + 1);//设置近端树突的突触总数，尽管这个总数是实际的二倍
        }
        return pool;
    }

    public void clearSynapses(Connections c) {
        c.getSynapses(this).clear();
    }

    /**
     * Sets the permanences for each {@link Synapse}. The number of synapses
     * is set by the potentialPct variable which determines the number of input
     * bits a given column will be "attached" to which is the same number as the
     * number of {@link Synapse}s 为每一个突触设置持久度值，突触的数量由potentialPct变量设定，这个值决定了一个输入位的数量，一个给定列将会被连接到相同数量的突触上
     * 
     * @param c			the {@link Connections} memory
     * @param perms		the floating point degree of connectedness*************这里可以改动一下，根据突触的值来决定是否生成新的突触，当前这里只要突触建立以后，只能更新已经建立的突触的持久度值，没有建立的突触的持久度值不能被采用了
     */
    public void setPermanences(Connections c, double[] perms) {
        pool.resetConnections();//重置当前连接以准备新的持久度值调整
        c.getConnectedCounts().clearStatistics(index);//把当前突触的连接数量值清零
        List<Synapse> synapses = c.getSynapses(this);//获取这个树突的突触列表
        for(Synapse s : synapses) { //对于突触列表中的每个突触，也就是只能更新已经建立的突触的持久度值，如果连突触都没有，那么这个持久度值是不会被更新进来的
            s.setPermanence(c, perms[s.getInputIndex()]);//设置突触的持久度值，请注意只有形成了潜在连接的突触才更新持久度值，没有形成的直接抛弃掉了
            if(perms[s.getInputIndex()] >= c.getSynPermConnected()) {//如果这个突触的持久度值大于连接阈值
            	if (index==535&&s.getInputIndex()==1)
            	{
					System.out.println(0);
				}
                c.getConnectedCounts().set(1, index, s.getInputIndex());
            }
        }
    }

    /**
     * Sets the permanences for each {@link Synapse} specified by the indexes
     * passed in which identify the input vector indexes associated with the
     * {@code Synapse}. The permanences passed in are understood to be in "sparse"
     * format and therefore require the int array identify their corresponding
     * indexes.
     * 
     * Note: This is the "sparse" version of this method.
     * 
     * @param c			the {@link Connections} memory
     * @param perms		the floating point degree of connectedness
     */
    public void setPermanences(Connections c, double[] perms, int[] inputIndexes) {
        pool.resetConnections();
        c.getConnectedCounts().clearStatistics(index);
        for(int i = 0;i < inputIndexes.length;i++) {
            pool.setPermanence(c, pool.getSynapseWithInput(inputIndexes[i]), perms[i]);
            if(perms[i] >= c.getSynPermConnected()) {
                c.getConnectedCounts().set(1, index, inputIndexes[i]);/////这里在2022年7月30日做了一个修改，之前是i,这次加上了inputIndexes[i]
            }
        }
    }

    /**
     * Sets the input vector synapse indexes which are connected (&gt;= synPermConnected)
     * @param c
     * @param connectedIndexes
     */
    public void setConnectedSynapsesForTest(Connections c, int[] connectedIndexes) {
        Pool pool = createPool(c, connectedIndexes);
        c.getPotentialPools().set(index, pool);
    }

    /**
     * Returns an array of synapse indexes as a dense binary array.
     * @param c
     * @return
     */
    public int[] getConnectedSynapsesDense(Connections c) {
        return c.getPotentialPools().get(index).getDenseConnected(c);
    }

    /**
     * Returns an sparse array of synapse indexes representing the connected bits.
     * @param c
     * @return
     */
    public int[] getConnectedSynapsesSparse(Connections c) {
        return c.getPotentialPools().get(index).getSparsePotential();
    }
}
