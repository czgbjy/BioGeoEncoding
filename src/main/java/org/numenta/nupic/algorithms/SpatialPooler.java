/* ---------------------------------------------------------------------
 * Numenta Platform for Intelligent Computing (NuPIC)
 * Copyright (C) 2016, Numenta, Inc.  Unless you have an agreement
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
package org.numenta.nupic.algorithms;

import java.util.Arrays;
import java.util.stream.IntStream;
import org.numenta.nupic.model.Column;
import org.numenta.nupic.model.Connections;
import org.numenta.nupic.model.Persistable;
import org.numenta.nupic.model.Pool;
import org.numenta.nupic.util.ArrayUtils;
import org.numenta.nupic.util.Condition;
import org.numenta.nupic.util.SparseBinaryMatrix;
import org.numenta.nupic.util.SparseMatrix;
import org.numenta.nupic.util.SparseObjectMatrix;
import org.numenta.nupic.util.Topology;

import chaschev.lang.Pair;
import gnu.trove.list.TIntList;
import gnu.trove.list.array.TDoubleArrayList;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.set.hash.TIntHashSet;

/**
 * Handles the relationships between the columns of a region 
 * and the inputs bits. The primary public interface to this function is the 
 * "compute" method, which takes in an input vector and returns a list of 
 * activeColumns columns.
 * Example Usage:
 * >
 * > SpatialPooler sp = SpatialPooler();
 * > Connections c = new Connections();
 * > sp.init(c);
 * > for line in file:
 * >   inputVector = prepared int[] (containing 1's and 0's)
 * >   sp.compute(inputVector)
 * 
 * @author David Ray
 *
 */
public class SpatialPooler implements Persistable {
    /** Default Serial Version  */
    private static final long serialVersionUID = 1L;

    /**
     * Constructs a new {@code SpatialPooler}
     */
    public SpatialPooler() {}
    
    /**
     * Initializes the specified {@link Connections} object which contains
     * the memory and structural anatomy this spatial pooler uses to implement
     * its algorithms.
     * 
     * @param c     a {@link Connections} object
     */
    public void init(Connections c) {
        if(c.getNumActiveColumnsPerInhArea() == 0 && (c.getLocalAreaDensity() == 0 ||
            c.getLocalAreaDensity() > 0.5)) {
            throw new InvalidSPParamValueException("Inhibition parameters are invalid");
        }
        
        c.doSpatialPoolerPostInit();
        initMatrices(c);
        connectAndConfigureInputs(c);
    }
    
    /**
     * Called to initialize the structural anatomy with configured values and prepare
     * the anatomical entities for activation.
     * 
     * @param c
     */
    public void initMatrices(final Connections c) {
        SparseObjectMatrix<Column> mem = c.getMemory();
        c.setMemory(mem == null ? 
            mem = new SparseObjectMatrix<>(c.getColumnDimensions()) : mem);//如果mem为空，则新建一个SparseObjectMatrix<>对象
        
        c.setInputMatrix(new SparseBinaryMatrix(c.getInputDimensions()));//设置输入矩阵，
        
        // Initiate the topologies
        c.setColumnTopology(new Topology(c.getColumnDimensions()));//设置列的拓扑对象
        c.setInputTopology(new Topology(c.getInputDimensions()));

        //Calculate numInputs and numColumns
        int numInputs = c.getInputMatrix().getMaxIndex() + 1;//输入数据的数量
        int numColumns = c.getMemory().getMaxIndex() + 1;//列的数量
        if(numColumns <= 0) {
            throw new InvalidSPParamValueException("Invalid number of columns: " + numColumns);
        }
        if(numInputs <= 0) {
            throw new InvalidSPParamValueException("Invalid number of inputs: " + numInputs);
        }
        c.setNumInputs(numInputs);//设置输入数据的总数
        c.setNumColumns(numColumns);//设置列的总数
        
        //Fill the sparse matrix with column objects
        for(int i = 0;i < numColumns;i++) { mem.set(i, new Column(c.getCellsPerColumn(), i)); }//创建列

        c.setPotentialPools(new SparseObjectMatrix<Pool>(c.getMemory().getDimensions()));//创建存储池的匹配数组

        c.setConnectedMatrix(new SparseBinaryMatrix(new int[] { numColumns, numInputs }));//创建输入数据到输入列的连接矩阵，也就是创建每个列和所有输入的连接

        //Initialize state meta-management statistics
        c.setOverlapDutyCycles(new double[numColumns]);//为每个列设置重叠占空比数组
        c.setActiveDutyCycles(new double[numColumns]);//为每个列设置活跃占空比数组
        c.setMinOverlapDutyCycles(new double[numColumns]);//为每个列设置最小重叠占空比数组
        c.setMinActiveDutyCycles(new double[numColumns]);//为每个列设置最小活跃冲占空比数组
        c.setBoostFactors(new double[numColumns]);//为每个列设置Boost因子
        Arrays.fill(c.getBoostFactors(), 1);//将1赋值给Boost数组中的每个元素
    }
    
    /**
     * Step two of pooler initialization kept separate from initialization
     * of static members so that they may be set at a different point in 
     * the initialization (as sometimes needed by tests).
     * 池初始化的第二步与静态成员的初始化保持分离，以便可以在初始化的不同点设置它们（有时测试需要这样做）
     * This step prepares the proximal dendritic synapse pools with their 
     * initial permanence values and connected inputs.
     * 这一步准备近端树突突触池以及他们的初始化持久度值和连接输入值，更新抑制半径
     * @param c     the {@link Connections} memory
     */
    public void connectAndConfigureInputs(Connections c) {
        // Initialize the set of permanence values for each column. Ensure that
        // each column is connected to enough input bits to allow it to be
        // activated.
        int numColumns = c.getNumColumns();//获取输入列的数量
        for(int i = 0;i < numColumns;i++) {//为每一列建立与输入值的连接，初始化连接的突触，以及突触的持久度值
            int[] potential = mapPotential(c, i, c.isWrapAround());//返回指定的列的潜在输入列表，潜在输入列表是由所有的输入乘以潜在连接的百分比形成
            Column column = c.getColumn(i);//获取这个列
            c.getPotentialPools().set(i, column.createPotentialPool(c, potential));//为这个列创建潜在的池，实际是近端树突创建潜在的池，进而把这个列的潜在池加入列表
            double[] perm = initPermanence(c, potential, i, c.getInitConnectedPct());
            updatePermanencesForColumn(c, perm, column, potential, true);
        }

        // The inhibition radius determines the size of a column's local
        // neighborhood.  A cortical column must overcome the overlap score of
        // columns in its neighborhood in order to become active. This radius is
        // updated every learning round. It grows and shrinks with the average
        // number of connected synapses per column.
        updateInhibitionRadius(c);//更新抑制半径
    }
    
    /**
     * This is the primary public method of the SpatialPooler class. This
     * function takes a input vector and outputs the indices of the active columns.
     * If 'learn' is set to True, this method also updates the permanences of the
     * columns. 
     * @param inputVector       An array of 0's and 1's that comprises the input to
     *                          the spatial pooler. The array will be treated as a one
     *                          dimensional array, therefore the dimensions of the array
     *                          do not have to match the exact dimensions specified in the
     *                          class constructor. In fact, even a list would suffice.
     *                          The number of input bits in the vector must, however,
     *                          match the number of bits specified by the call to the
     *                          constructor. Therefore there must be a '0' or '1' in the
     *                          array for every input bit.
     * @param activeArray       An array whose size is equal to the number of columns.
     *                          Before the function returns this array will be populated
     *                          with 1's at the indices of the active columns, and 0's
     *                          everywhere else.
     * @param learn             A boolean value indicating whether learning should be
     *                          performed. Learning entails updating the  permanence
     *                          values of the synapses, and hence modifying the 'state'
     *                          of the model. Setting learning to 'off' freezes the SP
     *                          and has many uses. For example, you might want to feed in
     *                          various inputs and examine the resulting SDR's.一个布尔值指示学习是否应该被执行，学习需要更新突触的持久度值，进而改变模型的状态。设置学习为off状态会冻结SP并有多种用途
     */
    public void compute(Connections c, int[] inputVector, int[] activeArray, boolean learn) {
        if(inputVector.length != c.getNumInputs()) {
            throw new InvalidSPParamValueException(
                    "Input array must be same size as the defined number of inputs: From Params: " + c.getNumInputs() +
                    ", From Input Vector: " + inputVector.length);
        }

        updateBookeepingVars(c, learn);//更新迭代次数计数（spIterationNum)和迭代学习次数（spIterationLearnNum)
        int[] overlaps = c.setOverlaps(calculateOverlap(c, inputVector));//获取到了所有单元柱的重叠值

        double[] boostedOverlaps;
        if(learn) {
            boostedOverlaps = ArrayUtils.multiply(c.getBoostFactors(), overlaps);//所有单元的重叠值乘以BootFactors
        }else{
            boostedOverlaps = ArrayUtils.toDoubleArray(overlaps);
        }
        
        int[] activeColumns = inhibitColumns(c, c.setBoostedOverlaps(boostedOverlaps));///通过内部抑制，获取激活的列的数组

        if(learn) {
            adaptSynapses(c, inputVector, activeColumns);//更新激活单元柱的突触持久度值
            updateDutyCycles(c, overlaps, activeColumns);//更新overlapDutyCycles和activeDutyCycles
            bumpUpWeakColumns(c);//把overlapDutyCycles小于minOverlapDutyCycles的列的突触的持久度进行一个增加
            updateBoostFactors(c);//更新BoostFactor
            if(isUpdateRound(c)) {//循环指定的次数后才更新抑制半径和minDutyCycles
                updateInhibitionRadius(c);
                updateMinDutyCycles(c);
            }
        }

        Arrays.fill(activeArray, 0);
        if(activeColumns.length > 0) {
            ArrayUtils.setIndexesTo(activeArray, activeColumns, 1);//记录激活的列
        }
    }
    
    /**
     * Removes the set of columns who have never been active from the set of
     * active columns selected in the inhibition round. Such columns cannot
     * represent learned pattern and are therefore meaningless if only inference
     * is required. This should not be done when using a random, unlearned SP
     * since you would end up with no active columns.
     *  
     * @param activeColumns An array containing the indices of the active columns
     * @return  a list of columns with a chance of activation
     */
    public int[] stripUnlearnedColumns(Connections c, int[] activeColumns) {
        TIntHashSet active = new TIntHashSet(activeColumns);
        TIntHashSet aboveZero = new TIntHashSet();
        int numCols = c.getNumColumns();
        double[] colDutyCycles = c.getActiveDutyCycles();
        for(int i = 0;i < numCols;i++) {
            if(colDutyCycles[i] <= 0) {
                aboveZero.add(i);
            }
        }
        active.removeAll(aboveZero);
        TIntArrayList l = new TIntArrayList(active);
        l.sort();
        
        return Arrays.stream(activeColumns).filter(i -> c.getActiveDutyCycles()[i] > 0).toArray();
    }
    
    /**
     * Updates the minimum duty cycles defining normal activity for a column. A
     * column with activity duty cycle below this minimum threshold is boosted.
     *  
     * @param c
     */
    public void updateMinDutyCycles(Connections c) {
        if(c.getGlobalInhibition() || c.getInhibitionRadius() > c.getNumInputs()) {
            updateMinDutyCyclesGlobal(c);
        }else{
            updateMinDutyCyclesLocal(c);
        }
    }

    /**
     * Updates the minimum duty cycles in a global fashion. Sets the minimum duty
     * cycles for the overlap and activation of all columns to be a percent of the
     * maximum in the region, specified by {@link Connections#getMinOverlapDutyCycles()} and
     * minPctActiveDutyCycle respectively. Functionality it is equivalent to
     * {@link #updateMinDutyCyclesLocal(Connections)}, but this function exploits the globalness of the
     * computation to perform it in a straightforward, and more efficient manner.
     * 
     * @param c
     */
    public void updateMinDutyCyclesGlobal(Connections c) {
        Arrays.fill(c.getMinOverlapDutyCycles(), 
                c.getMinPctOverlapDutyCycles() * ArrayUtils.max(c.getOverlapDutyCycles()));
        Arrays.fill(c.getMinActiveDutyCycles(), 
                c.getMinPctActiveDutyCycles() * ArrayUtils.max(c.getActiveDutyCycles()));
    }

    /**
     * Updates the minimum duty cycles. The minimum duty cycles are determined
     * locally. Each column's minimum duty cycles are set to be a percent of the
     * maximum duty cycles in the column's neighborhood. Unlike
     * {@link #updateMinDutyCyclesGlobal(Connections)}, here the values can be 
     * quite different for different columns.
     * 
     * @param c
     */
    public void updateMinDutyCyclesLocal(final Connections c) {
        int len = c.getNumColumns();//获取列数
        int inhibitionRadius = c.getInhibitionRadius();//获取抑制半径
        double[] activeDutyCycles = c.getActiveDutyCycles();//获取激活单元的近期活性
        double minPctActiveDutyCycles = c.getMinPctActiveDutyCycles();//返回列应该被激活的最小频率
        double[] overlapDutyCycles = c.getOverlapDutyCycles();//获取列的重叠近期活性值
        double minPctOverlapDutyCycles = c.getMinPctOverlapDutyCycles();///返回一个列应该至少有stimulusThreshold个激活输入的频率
        
        // Parallelize for speed up
        IntStream.range(0, len).forEach(i -> {
            int[] neighborhood = getColumnNeighborhood(c, i, inhibitionRadius);//获取每个列的抑制邻域
            
            double maxActiveDuty = ArrayUtils.max(
                ArrayUtils.sub(activeDutyCycles, neighborhood));//邻域的近期活性值里面的最大值
            double maxOverlapDuty = ArrayUtils.max(
                ArrayUtils.sub(overlapDutyCycles, neighborhood));//邻域的重叠活性值里面的最大值
            
            c.getMinActiveDutyCycles()[i] = maxActiveDuty * minPctActiveDutyCycles;//邻域的近期活性值里面的最大值乘以返回列应该被激活的最小频率
                
            c.getMinOverlapDutyCycles()[i] = maxOverlapDuty * minPctOverlapDutyCycles;//邻域的重叠活性值里面的最大值乘以列应该至少有stimulusThreshold个激活输入的频率
        });
    }

    /**
     * Updates the duty cycles for each column. The OVERLAP duty cycle is a moving
     * average of the number of inputs which overlapped with each column. The
     * ACTIVITY duty cycles is a moving average of the frequency of activation for
     * each column.
     * 
     * @param c                 the {@link Connections} (spatial pooler memory)
     * @param overlaps          an array containing the overlap score for each column.
     *                          The overlap score for a column is defined as the number
     *                          of synapses in a "connected state" (connected synapses)
     *                          that are connected to input bits which are turned on.
     * @param activeColumns     An array containing the indices of the active columns,
     *                          the sparse set of columns which survived inhibition
     */
    public void updateDutyCycles(Connections c, int[] overlaps, int[] activeColumns) {
        double[] overlapArray = new double[c.getNumColumns()];//为所有的列新建一个叠加数组
        double[] activeArray = new double[c.getNumColumns()];//为所有的列新建一个激活数组
        ArrayUtils.greaterThanXThanSetToYInB(overlaps, overlapArray, 0, 1);//如果“overlaps”中同一索引中的值大于0，则在overlapArray中相同索引处将值设置为1。相当于把overlap中的大于1的值设置为1了，其余的不变。overlapArray记录哪些列的重叠值大于1
        if(activeColumns.length > 0) {//如果活跃
            ArrayUtils.setIndexesTo(activeArray, activeColumns, 1);//把activeArray中，由activeColumns指定的索引处的值，设定为1，activeColums记录哪些列被激活
        }

        int period = c.getDutyCyclePeriod();//周期记为10
        if(period > c.getIterationNum()) {//如果周期的数大于当前迭代的次数
            period  = c.getIterationNum();//周期就记为当前迭代的次数
        }

        c.setOverlapDutyCycles(
                updateDutyCyclesHelper(c, c.getOverlapDutyCycles(), overlapArray, period));//设置重叠近期活性值，是所有单元柱的

        c.setActiveDutyCycles(
                updateDutyCyclesHelper(c, c.getActiveDutyCycles(), activeArray, period));//设置活跃近期活性值，是所有单元柱的
    }

    /**
     * Updates a duty cycle estimate with a new value. This is a helper
     * function that is used to update several duty cycle variables in
     * the Column class, such as: overlapDutyCucle, activeDutyCycle,
     * minPctDutyCycleBeforeInh, minPctDutyCycleAfterInh, etc. returns
     * the updated duty cycle. Duty cycles are updated according to the following
     * formula:
     * 用一个新的值来更新近期活性估值。这是一个辅助函数，其被用来估计几个近期活性变量在列的类别上，如overlapDutyCycle,activeDutyCycle,minPctDutyCycleBeforeInh，minPctDutyCycleAfterInh
     * 返回更新后的近期活性值。近期活性值根据如下的函数进行更新。
     *                (period - 1)*dutyCycle + newValue
     *  dutyCycle := ----------------------------------
     *                        period
     *
     * @param c             the {@link Connections} (spatial pooler memory) Connections变量
     * @param dutyCycles    An array containing one or more duty cycle values that need
     *                      to be updated  包含已过或者更多个需要被更新的近期活性值数组
     * @param newInput      A new numerical value used to update the duty cycle 用来更新近期活性值数组的一个新的数值
     * @param period        The period of the duty cycle 近期活性值的周期
     * @return
     */
    public double[] updateDutyCyclesHelper(Connections c, double[] dutyCycles, double[] newInput, double period) {
        return ArrayUtils.divide(ArrayUtils.d_add(ArrayUtils.multiply(dutyCycles, period - 1), newInput), period);
    }
    
    /**
     * Update the inhibition radius. The inhibition radius is a measure of the
     * square (or hypersquare) of columns that each a column is "connected to"
     * on average. Since columns are are not connected to each other directly, we
     * determine this quantity by first figuring out how many *inputs* a column is
     * connected to, and then multiplying it by the total number of columns that
     * exist for each input. For multiple dimension the aforementioned
     * calculations are averaged over all dimensions of inputs and columns. This
     * value is meaningless if global inhibition is enabled.
     * 抑制半径通过统计每一个列的输入值的跨度，把这些跨度求取平均值，进而计算每个输入对应几个列，把跨度的平均值和每个输入对应几个列的平均值相乘作为抑制的直径，进而除以2作为抑制半径
     * @param c     the {@link Connections} (spatial pooler memory)
     */
    public void updateInhibitionRadius(Connections c) {
        if(c.getGlobalInhibition()) {
            c.setInhibitionRadius(ArrayUtils.max(c.getColumnDimensions()));
            return;
        }

        TDoubleArrayList avgCollected = new TDoubleArrayList();//生成一个Double的数组avgCollected
        int len = c.getNumColumns();//获取列的数量
        for(int i = 0;i < len;i++) {//对于每一列
            avgCollected.add(avgConnectedSpanForColumnND(c, i));//计算其输入数据的索引的跨度
        }
        double avgConnectedSpan = ArrayUtils.average(avgCollected.toArray());//获取这所有跨度的平均值
        double diameter = avgConnectedSpan * avgColumnsPerInput(c);//抑制的直径为列的输入的平均跨度乘以每个输入对应的列数（即总列数除以总输入数）
        double radius = (diameter - 1) / 2.0d;//抑制半径
        radius = Math.max(1, radius);//抑制半径最小为1
        c.setInhibitionRadius((int)(radius + 0.5));
    }
    
    /**
     * The average number of columns per input, taking into account the topology
     * of the inputs and columns. This value is used to calculate the inhibition
     * radius. This function supports an arbitrary number of dimensions. If the
     * number of column dimensions does not match the number of input dimensions,
     * we treat the missing, or phantom dimensions as 'ones'.
     *  每个输入对应的列数的平均值，也就是列的数量除以输入的数量，
     * @param c     the {@link Connections} (spatial pooler memory)
     * @return
     */
    public double avgColumnsPerInput(Connections c) {
        int[] colDim = Arrays.copyOf(c.getColumnDimensions(), c.getColumnDimensions().length);//把列的维度数组复制一份Arrays的copyOf()方法传回的数组是新的数组对象，改变传回数组中的元素值，不会影响原来的数组
        int[] inputDim = Arrays.copyOf(c.getInputDimensions(), c.getInputDimensions().length);//把输入数组的维度复制一份copyOf()的第二个自变量指定要建立的新数组长度，如果新数组的长度超过原数组的长度，则保留数组默认值
        double[] columnsPerInput = ArrayUtils.divide(
            ArrayUtils.toDoubleArray(colDim), ArrayUtils.toDoubleArray(inputDim), 0, 0);//用列的维度除以输入的维度，也就是每个输入对应几个列
        return ArrayUtils.average(columnsPerInput);
    }
    
    /**
     * The range of connectedSynapses per column, averaged for each dimension.
     * This value is used to calculate the inhibition radius. This variation of
     * the function supports arbitrary column dimensions.
     *  
     * @param c             the {@link Connections} (spatial pooler memory)
     * @param columnIndex   the current column for which to avg.
     * @return
     */
    public double avgConnectedSpanForColumnND(Connections c, int columnIndex) {
        int[] dimensions = c.getInputDimensions();//获取输入的维度
        int[] connected = c.getColumn(columnIndex).getProximalDendrite().getConnectedSynapsesSparse(c);//获取这个列连接的输入序列，是指持久度值大于0的
        if(connected == null || connected.length == 0) return 0;

        int[] maxCoord = new int[c.getInputDimensions().length];//是维度的length，看清楚
        int[] minCoord = new int[c.getInputDimensions().length];
        Arrays.fill(maxCoord, -1);//将指定的int值赋值给int数组中的每个元素
        Arrays.fill(minCoord, ArrayUtils.max(dimensions));
        SparseMatrix<?> inputMatrix = c.getInputMatrix();
        for(int i = 0;i < connected.length;i++) {//对于这个列连接的输入序列的每个值，
            maxCoord = ArrayUtils.maxBetween(maxCoord, inputMatrix.computeCoordinates(connected[i]));//把输入的两个数组中相对应的每个元素中的较大值挑选出来组成一个新的数组
            minCoord = ArrayUtils.minBetween(minCoord, inputMatrix.computeCoordinates(connected[i]));//把输入的两个数组中相对应的每个元素中的较小值挑选出来组成一个新的数组
        }
        return ArrayUtils.average(ArrayUtils.add(ArrayUtils.subtract(maxCoord, minCoord), 1));
    }
    
    /**
     * The primary method in charge of learning. Adapts the permanence values of
     * the synapses based on the input vector, and the chosen columns after
     * inhibition round. Permanence values are increased for synapses connected to
     * input bits that are turned on, and decreased for synapses connected to
     * inputs bits that are turned off.
     * 管理学习的主要方法。根据输入向量和抑制时选择的列来跟新突触的持久度值。对于输入值大于1的连接突触他们的持久度值增加，对于输入值小于1的连接突触，其持久度值减少
     * @param c                 the {@link Connections} (spatial pooler memory)
     * @param inputVector       a integer array that comprises the input to
     *                          the spatial pooler. There exists an entry in the array
     *                          for every input bit.
     * @param activeColumns     an array containing the indices of the columns that
     *                          survived inhibition.
     */
    public void adaptSynapses(Connections c, int[] inputVector, int[] activeColumns) {
        int[] inputIndices = ArrayUtils.where(inputVector, ArrayUtils.INT_GREATER_THAN_0);//扫描指定的值并将条件应用于每个值，返回条件计算为true的值的索引。

        double[] permChanges = new double[c.getNumInputs()];
        Arrays.fill(permChanges, -1 * c.getSynPermInactiveDec());//生成一个与输入位数量大小一致的，非激活突触减少的持久度值
        ArrayUtils.setIndexesTo(permChanges, inputIndices, c.getSynPermActiveInc());//这句话相当于把输入值为1的输入位的突触持久度改变值设置为增加值
        for(int i = 0;i < activeColumns.length;i++) {//对于每一个激活的列
            Pool pool = c.getPotentialPools().get(activeColumns[i]);//获取激活单元柱对应的池
            double[] perm = pool.getDensePermanences(c);//获取连接至这个单元柱的近端树突的突触的连接持久度值，有连接的地方为值，没有连接的地方为0.获取激活单元柱突触的持久度值，这个数组的大小是和输入一样的大小，那么没有建立突触的地方其持久度值就为0
            int[] indexes = pool.getSparsePotential();//获取有树突连接的输入的索引集合，包括连接上的和没有连接的
            ArrayUtils.raiseValuesBy(permChanges, perm);//把perm中的每个元素的值增加permChanges，所有有输入的地方，不管其是否有突触都增加，没有输入的地方，不管其是否有突触其连接值都减小，这里来理理，相当于把输入值大于0的地方的持久度值增加了，其余地方的持久度值减小了，有刺激就增加，没有刺激就减小，这里的问题在于有刺激的地方不一定有突触，或者是连接突触
            Column col = c.getColumn(activeColumns[i]);//获取这个激活的列（接上一行，后面的函数又接着处理，最终的效果是凡是有突出连接又有输入的地方突触持久度值增加，凡是有突触连接但是没有输入的地方持久度值减少，这样会新城新的连接突触和非连接突触）
            updatePermanencesForColumn(c, perm, col, indexes, true);//更新这个列的树突的突触持久度值
        }
    }
    
    /**
     * This method increases the permanence values of synapses of columns whose
     * activity level has been too low. Such columns are identified by having an
     * overlap duty cycle that drops too much below those of their peers. The
     * permanence values for such columns are increased.
     *  这个方法增加活跃程度过低的列的突触的持久度值，这样的列通过重叠近期活性值确定，如果该重叠近期活性值比其等同的列低得多。这样的列的持久度值将会被增加
     * @param c
     */
    public void bumpUpWeakColumns(final Connections c) {
        int[] weakColumns = ArrayUtils.where(c.getMemory().get1DIndexes(), new Condition.Adapter<Integer>() {
            @Override public boolean eval(int i) {
                return c.getOverlapDutyCycles()[i] < c.getMinOverlapDutyCycles()[i];//获取近期重叠活性值低于最小值的列
            }
        });

        for(int i = 0;i < weakColumns.length;i++) {
            Pool pool = c.getPotentialPools().get(weakColumns[i]);
            double[] perm = pool.getSparsePermanences();
            ArrayUtils.raiseValuesBy(c.getSynPermBelowStimulusInc(), perm);
            int[] indexes = pool.getSparsePotential();
            Column col = c.getColumn(weakColumns[i]);
            updatePermanencesForColumnSparse(c, perm, col, indexes, true);
        }
    }
    
    /**
     * This method ensures that each column has enough connections to input bits
     * to allow it to become active. Since a column must have at least
     * 'stimulusThreshold' overlaps in order to be considered during the
     * inhibition phase, columns without such minimal number of connections, even
     * if all the input bits they are connected to turn on, have no chance of
     * obtaining the minimum threshold. For such columns, the permanence values
     * are increased until the minimum number of connections are formed.这个方法确保每一列有足够到输入bit的连接，从而使得它处于激活状态，由于一个列必须有至少'stimulusThreshold'个重叠为了被考虑在抑制阶段
     * 没有这么最小数量的链接，即使所有的连接的输入位激活了，也没有获取最小阈值的机会。对于这样的列，持久度值会增加指导最小的连接数量形成
     * @param c                 the {@link Connections} memory Connections对象
     * @param perm              the permanence values 持久度值
     * @param maskPotential 潜在输入        
     */
    public void raisePermanenceToThreshold(Connections c, double[] perm, int[] maskPotential) {
        if(maskPotential.length < c.getStimulusThreshold()) {
            throw new IllegalStateException("This is likely due to a " +
                "value of stimulusThreshold that is too large relative " +
                "to the input size. [len(mask) < self._stimulusThreshold]");
        }
        
        ArrayUtils.clip(perm, c.getSynPermMin(), c.getSynPermMax());//确保perm数组中的值都处于0,1之间
        while(true) {
            int numConnected = ArrayUtils.valueGreaterCountAtIndex(c.getSynPermConnected(), perm, maskPotential);//获取持久度值大于阈值的突触的数量
            if(numConnected >= c.getStimulusThreshold()) return;//如果大于持久度阈值的突触的数量大于激活突触的阈值，则返回，否则，把现有的连接持久度值都加一个最小值，确保有大于连接阈值的值，这里暴力添加的做法估计有问题，应该是让刺激来不断的增加连接持久度值
            ArrayUtils.raiseValuesBy(c.getSynPermBelowStimulusInc(), perm, maskPotential);
        }
    }
    
    /**
     * This method ensures that each column has enough connections to input bits
     * to allow it to become active. Since a column must have at least
     * 'stimulusThreshold' overlaps in order to be considered during the
     * inhibition phase, columns without such minimal number of connections, even
     * if all the input bits they are connected to turn on, have no chance of
     * obtaining the minimum threshold. For such columns, the permanence values
     * are increased until the minimum number of connections are formed.
     * 
     * Note: This method services the "sparse" versions of corresponding methods
     * 
     * @param c         The {@link Connections} memory
     * @param perm      permanence values
     */
    public void raisePermanenceToThresholdSparse(Connections c, double[] perm) {
        ArrayUtils.clip(perm, c.getSynPermMin(), c.getSynPermMax());
        while(true) {
            int numConnected = ArrayUtils.valueGreaterCount(c.getSynPermConnected(), perm);
            if(numConnected >= c.getStimulusThreshold()) return;
            ArrayUtils.raiseValuesBy(c.getSynPermBelowStimulusInc(), perm);
        }
    }
    
    /**
     * This method updates the permanence matrix with a column's new permanence 
     * values. The column is identified by its index, which reflects the row in
     * the matrix, and the permanence is given in 'sparse' form, i.e. an array
     * whose members are associated with specific indexes. It is in
     * charge of implementing 'clipping' - ensuring that the permanence values are
     * always between 0 and 1 - and 'trimming' - enforcing sparseness by zeroing out
     * all permanence values below 'synPermTrimThreshold'. It also maintains
     * the consistency between 'permanences' (the matrix storing the
     * permanence values), 'connectedSynapses', (the matrix storing the bits
     * each column is connected to), and 'connectedCounts' (an array storing
     * the number of input bits each column is connected to). Every method wishing
     * to modify the permanence matrix should do so through this method.此方法用列的新的持久度值更新持久度矩阵。列由其索引标识，该索引反映矩阵中的行，持久度以“稀疏”形式给出，即一个数组。其成员与特定索引相关联。他负责实现裁剪，确保持久度值始终介于0和1之间-以及“修剪”——通过将持久度值低于“synpermtrimsthreshold”的归零来实现稀疏性
     * 它还保持了'permanences'（存储持久度值的矩阵），'connectedSynapses'(每一列连接到的比特矩阵）和'connectedCounts'(每一个列连接的输入比特的数量数组）之间的一致性，每一个希望修改持久度矩阵的方法应该由这个方法实现。
     * @param c                 the {@link Connections} which is the memory model. Connections参数
     * @param perm              An array of permanence values for a column. The array is
     *                          "dense", i.e. it contains an entry for each input bit, even
     *                          if the permanence value is 0.列的持久度值数组，数组是“密集”的，即每个输入bit都有一个值，即使它的持久度值为0
     * @param column            The column in the permanence, potential and connectivity matrices 持久度、潜在连接和连通性矩阵中的列
     * @param maskPotential     The indexes of inputs in the specified {@link Column}'s pool.指定的列的池中输入索引
     * @param raisePerm         a boolean value indicating whether the permanence values 只要raisePerm设置为true了，那么这个列肯定会被激活
     */
    public void updatePermanencesForColumn(Connections c, double[] perm, Column column, int[] maskPotential, boolean raisePerm) {
        if(raisePerm) {//raisePerm标记是否增加突触的持久度值，如果为true就去增加持久度值，但实际并不一定增加，特别是已有持久度值大于阈值形成连接，并且连接超过列激活的最小突触数量阈值的情况下
            raisePermanenceToThreshold(c, perm, maskPotential);
        }

        ArrayUtils.lessThanOrEqualXThanSetToY(perm, c.getSynPermTrimThreshold(), 0);//把突触持久度值低于最小阈值的突触持久度值直接归于0
        ArrayUtils.clip(perm, c.getSynPermMin(), c.getSynPermMax());//把突触的持久度截取为指定的最小突触值或者最大突触值之间的值，如果本身符合这个条件则不截取
        column.setProximalPermanences(c, perm);//把突触的持久度值赋值给前端树突
    }
    
    /**
     * This method updates the permanence matrix with a column's new permanence
     * values. The column is identified by its index, which reflects the row in
     * the matrix, and the permanence is given in 'sparse' form, (i.e. an array
     * whose members are associated with specific indexes). It is in
     * charge of implementing 'clipping' - ensuring that the permanence values are
     * always between 0 and 1 - and 'trimming' - enforcing sparseness by zeroing out
     * all permanence values below 'synPermTrimThreshold'. Every method wishing
     * to modify the permanence matrix should do so through this method.
     * 
     * @param c                 the {@link Connections} which is the memory model.
     * @param perm              An array of permanence values for a column. The array is
     *                          "sparse", i.e. it contains an entry for each input bit, even
     *                          if the permanence value is 0.
     * @param column            The column in the permanence, potential and connectivity matrices
     * @param raisePerm         a boolean value indicating whether the permanence values
     */
    public void updatePermanencesForColumnSparse(Connections c, double[] perm, Column column, int[] maskPotential, boolean raisePerm) {
        if(raisePerm) {
            raisePermanenceToThresholdSparse(c, perm);
        }

        ArrayUtils.lessThanOrEqualXThanSetToY(perm, c.getSynPermTrimThreshold(), 0);
        ArrayUtils.clip(perm, c.getSynPermMin(), c.getSynPermMax());
        column.setProximalPermanencesSparse(c, perm, maskPotential);
    }
    
    /**
     * Returns a randomly generated permanence value for a synapse that is
     * initialized in a connected state. The basic idea here is to initialize
     * permanence values very close to synPermConnected so that a small number of
     * learning steps could make it disconnected or connected.
     *
     * Note: experimentation was done a long time ago on the best way to initialize
     * permanence values, but the history for this particular scheme has been lost.
     * 
     * @return  a randomly generated permanence value
     */
    public static double initPermConnected(Connections c) {
        double p = c.getSynPermConnected() + (c.getSynPermMax() - c.getSynPermConnected()) * c.random.nextDouble();//这个算法是突触的连接的持久度阈值+（突触最大的连接持久度值-连接阈值）*随机数，保证了这个值一定大于连接阈值

        // Note from Python implementation on conditioning below:
        // Ensure we don't have too much unnecessary precision. A full 64 bits of
        // precision causes numerical stability issues across platforms and across
        // implementations
        p = ((int)(p * 100000)) / 100000.0d;
        return p;
    }

    /**
     * Returns a randomly generated permanence value for a synapses that is to be
     * initialized in a non-connected state.
     * 为突触产生一个随机的持久度值，这个突触将被初始化为一个非连接状态的突触，这是因为产生的连接持久度值小于连接阈值
     * @return  a randomly generated permanence value
     */
    public static double initPermNonConnected(Connections c) {
        double p = c.getSynPermConnected() * c.getRandom().nextDouble();//产生一个随机的连接持久度值，这个值由于乘以连接阈值，且随机数的大小在【0,1】之间，所以这个值一定小于阈值

        // Note from Python implementation on conditioning below:
        // Ensure we don't have too much unnecessary precision. A full 64 bits of
        // precision causes numerical stability issues across platforms and across
        // implementations
        p = ((int)(p * 100000)) / 100000.0d;
        return p;
    }
    
    /**
     * Initializes the permanences of a column. The method 
     * returns a 1-D array the size of the input, where each entry in the
     * array represents the initial permanence value between the input bit
     * at the particular index in the array, and the column represented by
     * the 'index' parameter.初始化列的持久度，这个方法返回一个1维的数组，数组的大小同输入大小一致，其中数组中的每一个元素代表初始的持久度值，
     * 是这个数组中特定的索引处的输入比特和索引参数所代表的列之间的值
     * @param c                 the {@link Connections} which is the memory model 连接对象Connections
     * @param potentialPool     An array specifying the potential pool of the column.指定列的潜在池的数组
     *                          Permanence values will only be generated for input bits 持久度值只为与掩码值为1的索引相对应的输入位产生。注意：potentialPool是稀疏的，不是1的数组
     *                          corresponding to indices for which the mask value is 1.
     *                          WARNING: potentialPool is sparse, not an array of "1's"
     * @param index             the index of the column being initialized  被初始化的列的索引
     * @param connectedPct      A value between 0 or 1 specifying the percent of the input
     *                          bits that will start off in a connected state.介于0或1之间的值，指定初始处于连接状态的输入位的百分比
     * @return
     */
    public double[] initPermanence(Connections c, int[] potentialPool, int index, double connectedPct) {
        double[] perm = new double[c.getNumInputs()];//为每输入位创建一个数组，数组的元素数量为输入的位数
        for(int idx : potentialPool) { //对于选择的每一个潜在输入池中的元素
            double x;
			if(( x=c.random.nextDouble()) <= connectedPct) {//创建一个0-1之间的随机数，如果这个随机数小于连接的百分比
                perm[idx] = initPermConnected(c);//形成一个连接的持久度值，也就是随机产生一个大于连接阈值的持久度值
            }else{
                perm[idx] = initPermNonConnected(c);//形成一个连接持久度值，也就是随机产生一个小于连接阈值的持久度值
            }
            //System.out.print(x);
            perm[idx] = perm[idx] < c.getSynPermTrimThreshold() ? 0 : perm[idx];//如果这个连接持久度值过于小，直接把它变为0
        }
        c.getColumn(index).setProximalPermanences(c, perm);
        return perm;
    }
    
    /**
     * Maps a column to its respective input index, keeping to the topology of
     * the region. It takes the index of the column as an argument and determines
     * what is the index of the flattened input vector that is to be the center of
     * the column's potential pool. It distributes the columns over the inputs
     * uniformly. The return value is an integer representing the index of the
     * input bit. Examples of the expected output of this method:
     * * If the topology is one dimensional, and the column index is 0, this
     *   method will return the input index 0. If the column index is 1, and there
     *   are 3 columns over 7 inputs, this method will return the input index 3.
     * * If the topology is two dimensional, with column dimensions [3, 5] and
     *   input dimensions [7, 11], and the column index is 3, the method
     *   returns input index 8. 
     *  把各个列匹配到相应的输入索引，保持区域的拓扑结构不变。它会把列的索引作为参数，并确定作为列的潜在
     *  池中心的平坦输入向量的索引。它将输入均匀的分布在列上。返回值是一个整数代表输入比特值的索引。
     *  这个方法预期的输出案例：
     *  如果拓扑结构是1维的，并且列的索引是0，这个方法将返回输入索引0，如果列的索引是1，并且有7个输入和3个列，
     *  这个方法将会返回输入索引3。
     *  如果这个拓扑结构是2维的，列的维度为【3,5】 并且输入维度为[7,11]，并且列的索引为3，这个方法将会返回输入索引8.
     * @param columnIndex   The index identifying a column in the permanence, potential
     *                      and connectivity matrices.
     * @return              A boolean value indicating that boundaries should be
     *                      ignored.
     */
    public int mapColumn(Connections c, int columnIndex) {
        int[] columnCoords = c.getMemory().computeCoordinates(columnIndex);//获取指定的列索引对应的坐标值
        double[] colCoords = ArrayUtils.toDoubleArray(columnCoords);//转换成double型数值
        double[] ratios = ArrayUtils.divide(
            colCoords, ArrayUtils.toDoubleArray(c.getColumnDimensions()), 0, 0);//这个比例是指定列的索引与列的总数的商
        double[] inputCoords = ArrayUtils.multiply(
            ArrayUtils.toDoubleArray(c.getInputDimensions()), ratios, 0, 0);//用单元柱的索引比例和输入数组的长度成绩，获取输入坐标
        inputCoords = ArrayUtils.d_add(
            inputCoords, 
            ArrayUtils.multiply(
                ArrayUtils.divide(
                    ArrayUtils.toDoubleArray(c.getInputDimensions()), 
                    ArrayUtils.toDoubleArray(c.getColumnDimensions()), 0, 0), 
                0.5));//这条语句首先拿输入的维度除以列的维度，然后乘以0.5，然后和
        int[] inputCoordInts = ArrayUtils.clip(ArrayUtils.toIntArray(inputCoords), c.getInputDimensions(), -1);
        return c.getInputMatrix().computeIndex(inputCoordInts);
    }
    
    /**
     * Maps a column to its input bits. This method encapsulates the topology of
     * the region. It takes the index of the column as an argument and determines
     * what are the indices of the input vector that are located within the
     * column's potential pool. The return value is a list containing the indices
     * of the input bits. The current implementation of the base class only
     * supports a 1 dimensional topology of columns with a 1 dimensional topology
     * of inputs. To extend this class to support 2-D topology you will need to
     * override this method. Examples of the expected output of this method:
     * * If the potentialRadius is greater than or equal to the entire input
     *   space, (global visibility), then this method returns an array filled with
     *   all the indices
     * * If the topology is one dimensional, and the potentialRadius is 5, this
     *   method will return an array containing 5 consecutive values centered on
     *   the index of the column (wrapping around if necessary).
     * * If the topology is two dimensional (not implemented), and the
     *   potentialRadius is 5, the method should return an array containing 25
     *   '1's, where the exact indices are to be determined by the mapping from
     *   1-D index to 2-D position.把输入的比特值和列进行匹配。这个方法封装了区域的拓扑。
     *   它将列的索引作为参数，并确定位于列的潜在池中的输入向量的索引。返回值为包含输入比特值索引的列表。
     *   基类目前的实现仅仅支持1维输入拓扑和一维的列拓扑。为了扩展这个类去支持2维拓扑，你需要覆写这个方法。
     *   这个方法预期的输出案例为：
     *   如果potentialRadius参数大于或者等于输入空间，这个方法返回有着所有索引的的数组。
     *   如果拓扑结构是一维的，并且potentialRadius是5，此方法将返回一个数组，其中包含以列索引为中心的5个连续值
     *   （如有必要，请循环）
     *   如果拓扑结构是二维的（目前没有实现），并且potentialRadius是5，这个方法应该返回一个包含25个1的数组，准确的
     *   索引值应当通过匹配1维索引到二维空间而确定。
     * 
     * @param c             {@link Connections} the main memory model
     * @param columnIndex   The index identifying a column in the permanence, potential
     *                      and connectivity matrices.
     * @param wrapAround    A boolean value indicating that boundaries should be
     *                      ignored.
     * @return    返回随机选择的输入的索引列表
     */
    public int[] mapPotential(Connections c, int columnIndex, boolean wrapAround) {
        int centerInput = mapColumn(c, columnIndex);//获取输入中心点的列的索引，其算法为，首先把索引转换为坐标，输入点中心坐标的计算公式为：InputDimension(0.5+colCoords)/ColumDimensions
        int[] columnInputs = getInputNeighborhood(c, centerInput, c.getPotentialRadius()); //获取输入的邻域，请注意是输入的
        
        // Select a subset of the receptive field to serve as the
        // the potential pool
        int numPotential = (int)(columnInputs.length * c.getPotentialPct() + 0.5);///有多少个输入被选中，不是所有的输入都能被选中
        int[] retVal = new int[numPotential];
        return ArrayUtils.sample(columnInputs, retVal, c.getRandom());//返回选择的输入的索引列表
    }
    
    /**
     * Performs inhibition. This method calculates the necessary values needed to
     * actually perform inhibition and then delegates the task of picking the
     * active columns to helper functions.
     * 执行抑制。此方法计算执行抑制所需的必须值。然后把挑选激活列的任务委托给辅助函数
     * @param c             the {@link Connections} matrix Connections对象
     * @param overlaps      an array containing the overlap score for each  column.
     *                      The overlap score for a column is defined as the number
     *                      of synapses in a "connected state" (connected synapses)
     *                      that are connected to input bits which are turned on.
     * @return
     */
    public int[] inhibitColumns(Connections c, double[] overlaps) {
        overlaps = Arrays.copyOf(overlaps, overlaps.length);//把这个重叠数组复制一遍

        double density;
        double inhibitionArea;
        if((density = c.getLocalAreaDensity()) <= 0) {//如果本地面积密度小于0
            inhibitionArea = Math.pow(2 * c.getInhibitionRadius() + 1, c.getColumnDimensions().length);//看来抑制区是一个正方形，是抑制区直径的几次方
            inhibitionArea = Math.min(c.getNumColumns(), inhibitionArea);//抑制区域最大为整个区域
            density = c.getNumActiveColumnsPerInhArea() / inhibitionArea;///每个抑制区允许的最大激活单元柱的数量，除以抑制区单元柱数量的总数，就是抑制区密度
            density = Math.min(density, 0.5);//这个密度不能超过0.5
        }

        //Add our fixed little bit of random noise to the scores to help break ties.
        //ArrayUtils.d_add(overlaps, c.getTieBreaker());

        if(c.getGlobalInhibition() || c.getInhibitionRadius() > ArrayUtils.max(c.getColumnDimensions())) {
            return inhibitColumnsGlobal(c, overlaps, density);//全局抑制函数
        }

        return inhibitColumnsLocal(c, overlaps, density);//局部抑制函数
    }

    /**
     * Perform global inhibition. Performing global inhibition entails picking the
     * top 'numActive' columns with the highest overlap score in the entire
     * region. At most half of the columns in a local neighborhood are allowed to
     * be active.
     * 
     * @param c             the {@link Connections} matrix
     * @param overlaps      an array containing the overlap score for each  column.
     *                      The overlap score for a column is defined as the number
     *                      of synapses in a "connected state" (connected synapses)
     *                      that are connected to input bits which are turned on.
     * @param density       The fraction of columns to survive inhibition.
     * 
     * @return
     */
    public int[] inhibitColumnsGlobal(Connections c, double[] overlaps, double density) {
        int numCols = c.getNumColumns();
        int numActive = (int)(density * numCols);
 
        int[] sortedWinnerIndices = IntStream.range(0,overlaps.length)
            .mapToObj(i-> new Pair<>(i,overlaps[i]))
            .sorted(c.inhibitionComparator)
            .mapToInt(Pair<Integer,Double>::getFirst)
            .toArray();
        
        // Enforce the stimulus threshold
        double stimulusThreshold = c.getStimulusThreshold();
        int start = sortedWinnerIndices.length - numActive;
        while(start < sortedWinnerIndices.length) {
            int i = sortedWinnerIndices[start];
            if(overlaps[i] >= stimulusThreshold) break;
            ++start;
        }
        
        return IntStream.of(sortedWinnerIndices).skip(start).toArray();
    }
    
    /**
     * Performs inhibition. This method calculates the necessary values needed to
     * actually perform inhibition and then delegates the task of picking the
     * active columns to helper functions.
     * 执行抑制。这个方法计算实际执行抑制所需要的值，然后把存储激活列的任务委托给辅助函数
     * @param c         the {@link Connections} matrix Connection变量
     * @param overlaps  an array containing the overlap score for each  column.
     *                  The overlap score for a column is defined as the number
     *                  of synapses in a "connected state" (connected synapses)
     *                  that are connected to input bits which are turned on.对于每个列，包含叠加值分数的数组，这个列的叠加分数被定义为处于连接状态的突触的数量
     * @param density   The fraction of columns to survive inhibition. This
     *                  value is only an intended target. Since the surviving
     *                  columns are picked in a local fashion, the exact fraction
     *                  of surviving columns is likely to vary. 
     * @return  indices of the winning columns 胜出的单元
     */
    public int[] inhibitColumnsLocal(Connections c, double[] overlaps, double density) {
        double addToWinners = ArrayUtils.max(overlaps) / 1000.0d;//胜出的列的激活值将要被添加的值，这个值是叠加值数组里面最大值除以1000
        if(addToWinners == 0) {
            addToWinners = 0.001;
        }
        double[] tieBrokenOverlaps = Arrays.copyOf(overlaps, overlaps.length);//把空间池的数组再复制一份
        
        TIntList winners = new TIntArrayList();//创建一个int数组
        double stimulusThreshold = c.getStimulusThreshold();//获取激活阈值
        int inhibitionRadius = c.getInhibitionRadius();//获取抑制半径
        for(int i = 0;i < overlaps.length;i++) {//对于空间池的每一个叠加的成果值
            int column = i;
            if(overlaps[column] >= stimulusThreshold) {//如果第i个单元柱的叠加值大于激活阈值
               int[] neighborhood = getColumnNeighborhood(c, column, inhibitionRadius);//获取这个单元柱的邻域
               double[] neighborhoodOverlaps = ArrayUtils.sub(tieBrokenOverlaps, neighborhood);//在源数组中，截取指定索引的元素，构成新的数组，这里相当于获取了邻域单元柱的叠加值
               
               long numBigger = Arrays.stream(neighborhoodOverlaps)
                   .parallel() //DoubleStream类的方法返回一个等效的并行流，相当于复制操作
                   .filter(d -> d > overlaps[column])//把邻域单元柱中叠加值大于中心单元柱的挑出来，
                   .count();//统计一下数量
               
               int numActive = (int)(0.5 + density * neighborhood.length);//最大激活单元柱的数量，在这个邻域单元柱数组中，这个领域中允许激活的单元柱总数
               if(numBigger < numActive) {//如果邻域单元柱数组中叠加值大于中心单元柱叠加值的单元柱的数量小于邻域单元柱中最大激活单元柱的数量，也就是说中心单元柱的值能排进前几名，那么这个单元柱就是一个激活单元柱
                   winners.add(column);//则这个单元柱为胜出单元柱
                   tieBrokenOverlaps[column] += addToWinners;//把胜出单元柱的值加最大叠加值除以1000，稍微变大了一点点
               }
            }
        }

        return winners.toArray();
    }
    
    /**
     * Update the boost factors for all columns. The boost factors are used to
     * increase the overlap of inactive columns to improve their chances of
     * becoming active. and hence encourage participation of more columns in the
     * learning process. This is a line defined as: y = mx + b boost =
     * (1-maxBoost)/minDuty * dutyCycle + maxFiringBoost. Intuitively this means
     * that columns that have been active enough have a boost factor of 1, meaning
     * their overlap is not boosted. Columns whose active duty cycle drops too much
     * below that of their neighbors are boosted depending on how infrequently they
     * have been active. The more infrequent, the more they are boosted. The exact
     * boost factor is linearly interpolated between the points (dutyCycle:0,
     * boost:maxFiringBoost) and (dutyCycle:minDuty, boost:1.0).对于所有的列，更新其BootFactor。boostFactor被用来增加非激活列的叠加值，来提升成为激活列的机会。从而鼓励在学习过程中更多列的参加。它被定义为boost =(1-maxBoost)/minDuty * dutyCycle + maxFiringBoost
     * 它意味着这个列已经能被容易激活，当它有一个boostFactor为1，意味着叠加值没有被提升。激活占空比过度低于相邻的列被提升依据它们不被激活的频率。约不经常被激活，它们被提升的越多。这个boostfactor的值处于(dutyCycle:0, boost:maxFiringBoost) 和 (dutyCycle:minDuty, boost:1.0)
     *         boostFactor
     *             ^
     * maxBoost _  |
     *             |\
     *             | \
     *       1  _  |  \ _ _ _ _ _ _ _
     *             |
     *             +--------------------> activeDutyCycle
     *                |
     *         minActiveDutyCycle
     */
    public void updateBoostFactors(Connections c) {
        double[] activeDutyCycles = c.getActiveDutyCycles();//获取激活的占空比
        double[] minActiveDutyCycles = c.getMinActiveDutyCycles();//获取最小激活占空比

        //Indexes of values > 0
        int[] mask = ArrayUtils.where(minActiveDutyCycles, ArrayUtils.GREATER_THAN_0);//返回的是符合条件的数组的值的索引

        double[] boostInterim;
        if(mask.length < 1) {//如果符合条件的minActiveDutyCycles的数量小于1
            boostInterim = c.getBoostFactors();//获取每个列的boostFactor
        }else{
            double[] numerator = new double[c.getNumColumns()];
            Arrays.fill(numerator, 1 - c.getMaxBoost());//numerator数组的值赋值为-9
            boostInterim = ArrayUtils.divide(numerator, minActiveDutyCycles, 0, 0);
            boostInterim = ArrayUtils.multiply(boostInterim, activeDutyCycles, 0, 0);
            boostInterim = ArrayUtils.d_add(boostInterim, c.getMaxBoost());///目的是让低于最低活跃值的的单元提升指数增加
        }

        ArrayUtils.setIndexesTo(boostInterim, ArrayUtils.where(activeDutyCycles, new Condition.Adapter<Object>() {
            int i = 0;
            @Override public boolean eval(double d) { return d > minActiveDutyCycles[i++]; }
        }), 1.0d);

        c.setBoostFactors(boostInterim);//设置BoostFactor
    }
    
    /**
     * This function determines each column's overlap with the current input
     * vector. The overlap of a column is the number of synapses for that column
     * that are connected (permanence value is greater than '_synPermConnected')
     * to input bits which are turned on. Overlap values that are lower than
     * the 'stimulusThreshold' are ignored. The implementation takes advantage of
     * the SpraseBinaryMatrix class to perform this calculation efficiently.
     *  
     * @param c             the {@link Connections} memory encapsulation
     * @param inputVector   an input array of 0's and 1's that comprises the input to
     *                      the spatial pooler.
     * @return
     */
    public int[] calculateOverlap(Connections c, int[] inputVector) {
        int[] overlaps = new int[c.getNumColumns()];
        c.getConnectedCounts().rightVecSumAtNZ(inputVector, overlaps, c.getStimulusThreshold());
        return overlaps;
    }
    
    /**
     * Return the overlap to connected counts ratio for a given column
     * @param c
     * @param overlaps
     * @return
     */
    public double[] calculateOverlapPct(Connections c, int[] overlaps) {
        return ArrayUtils.divide(overlaps, c.getConnectedCounts().getTrueCounts());
    }
    
//    /**
//     * Similar to _getNeighbors1D and _getNeighbors2D (Not included in this implementation), 
//     * this function Returns a list of indices corresponding to the neighbors of a given column. 
//     * Since the permanence values are stored in such a way that information about topology
//     * is lost. This method allows for reconstructing the topology of the inputs,
//     * which are flattened to one array. Given a column's index, its neighbors are
//     * defined as those columns that are 'radius' indices away from it in each
//     * dimension. The method returns a list of the flat indices of these columns.
//     * 
//     * @param c                     matrix configured to this {@code SpatialPooler}'s dimensions
//     *                              for transformation work.
//     * @param columnIndex           The index identifying a column in the permanence, potential
//     *                              and connectivity matrices.
//     * @param topology              A {@link SparseMatrix} with dimensionality info.
//     * @param inhibitionRadius      Indicates how far away from a given column are other
//     *                              columns to be considered its neighbors. In the previous 2x3
//     *                              example, each column with coordinates:
//     *                              [2+/-radius, 3+/-radius] is considered a neighbor.
//     * @param wrapAround            A boolean value indicating whether to consider columns at
//     *                              the border of a dimensions to be adjacent to columns at the
//     *                              other end of the dimension. For example, if the columns are
//     *                              laid out in one dimension, columns 1 and 10 will be
//     *                              considered adjacent if wrapAround is set to true:
//     *                              [1, 2, 3, 4, 5, 6, 7, 8, 9, 10]
//     *               
//     * @return              a list of the flat indices of these columns
//     */
//    public TIntArrayList getNeighborsND(Connections c, int columnIndex, SparseMatrix<?> topology, int inhibitionRadius, boolean wrapAround) {
//        final int[] dimensions = topology.getDimensions();
//        int[] columnCoords = topology.computeCoordinates(columnIndex);
//        List<int[]> dimensionCoords = new ArrayList<>();
//
//        for(int i = 0;i < dimensions.length;i++) {
//            int[] range = ArrayUtils.range(columnCoords[i] - inhibitionRadius, columnCoords[i] + inhibitionRadius + 1);
//            int[] curRange = new int[range.length];
//
//            if(wrapAround) {
//                for(int j = 0;j < curRange.length;j++) {
//                    curRange[j] = (int)ArrayUtils.positiveRemainder(range[j], dimensions[i]);
//                }
//            }else{
//                final int idx = i;
//                curRange = ArrayUtils.retainLogicalAnd(range, 
//                    new Condition[] { ArrayUtils.GREATER_OR_EQUAL_0,
//                        new Condition.Adapter<Integer>() {
//                            @Override public boolean eval(int n) { return n < dimensions[idx]; }
//                        }
//                    }
//                );
//            }
//            dimensionCoords.add(ArrayUtils.unique(curRange));
//        }
//
//        List<int[]> neighborList = ArrayUtils.dimensionsToCoordinateList(dimensionCoords);
//        TIntArrayList neighbors = new TIntArrayList(neighborList.size());
//        int size = neighborList.size();
//        for(int i = 0;i < size;i++) {
//            int flatIndex = topology.computeIndex(neighborList.get(i), false);
//            if(flatIndex == columnIndex) continue;
//            neighbors.add(flatIndex);
//        }
//        return neighbors;
//    }
    
    /**
     * Returns true if enough rounds have passed to warrant updates of
     * duty cycles
     * 如果足够多的迭代次数已经传递，以保证占空比的更新
     * @param c the {@link Connections} memory encapsulation
     * @return
     */
    public boolean isUpdateRound(Connections c) {
        return c.getIterationNum() % c.getUpdatePeriod() == 0;
    }
    
    /**
     * Updates counter instance variables each cycle.
     *  更新迭代次数计数（spIterationNum)和迭代学习次数（spIterationLearnNum)
     * @param c         the {@link Connections} memory encapsulation
     * @param learn     a boolean value indicating whether learning should be
     *                  performed. Learning entails updating the  permanence
     *                  values of the synapses, and hence modifying the 'state'
     *                  of the model. setting learning to 'off' might be useful
     *                  for indicating separate training vs. testing sets.
     */
    public void updateBookeepingVars(Connections c, boolean learn) {
        c.spIterationNum += 1;
        if(learn) c.spIterationLearnNum += 1;
    }
    
    /**
     * Gets a neighborhood of columns.
     * 
     * Simply calls topology.neighborhood or topology.wrappingNeighborhood
     * 
     * A subclass can insert different topology behavior by overriding this method.
     * 
     * @param c                     the {@link Connections} memory encapsulation
     * @param centerColumn          The center of the neighborhood.
     * @param inhibitionRadius      Span of columns included in each neighborhood
     * @return                      The columns in the neighborhood (1D)
     */
    public int[] getColumnNeighborhood(Connections c, int centerColumn, int inhibitionRadius) {
        return c.isWrapAround() ? 
            c.getColumnTopology().wrappingNeighborhood(centerColumn, inhibitionRadius) :
                c.getColumnTopology().neighborhood(centerColumn, inhibitionRadius);
    }
    
    /**
     * Gets a neighborhood of inputs.
     * 获取输入的邻域
     * Simply calls topology.wrappingNeighborhood or topology.neighborhood.
     * 就是简单的调用topology.wrappingNeighborhood或者topology.neighborhood
     * A subclass can insert different topology behavior by overriding this method.
     * 子类可以插入不同的拓扑行为通过覆写这个方法
     * @param c                     the {@link Connections} memory encapsulation Connections的对象
     * @param centerInput           The center of the neighborhood. 邻域的中心
     * @param potentialRadius       Span of the input field included in each neighborhood 包含在每个邻域的输入字段的跨度
     * @return                      The input's in the neighborhood. (1D) 输入的邻域
     */
    public int[] getInputNeighborhood(Connections c, int centerInput, int[] potentialRadius) {
        return c.isWrapAround() ? 
            c.getInputTopology().wrappingNeighborhood(centerInput, potentialRadius) :
                c.getInputTopology().neighborhood(centerInput, potentialRadius);
    }
    
    /**
     * Thrown for basic sanity violations
     */
    class InvalidSPParamValueException extends RuntimeException {
        /** Default Serial Version */
        private static final long serialVersionUID = 1L;
        public InvalidSPParamValueException(String message) { super(message); }
    }

}
