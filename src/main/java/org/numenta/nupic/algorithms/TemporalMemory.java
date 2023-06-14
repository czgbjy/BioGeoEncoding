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

import static org.numenta.nupic.util.GroupBy2.Slot.NONE;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.numenta.nupic.model.Cell;
import org.numenta.nupic.model.Column;
import org.numenta.nupic.model.ComputeCycle;
import org.numenta.nupic.model.Connections;
import org.numenta.nupic.model.Connections.Activity;
import org.numenta.nupic.model.DistalDendrite;
import org.numenta.nupic.model.Synapse;
import org.numenta.nupic.monitor.ComputeDecorator;
import org.numenta.nupic.util.GroupBy2;
import org.numenta.nupic.util.GroupBy2.Slot;
import org.numenta.nupic.util.SparseObjectMatrix;
import org.numenta.nupic.util.Tuple;

import chaschev.lang.Pair;

/**
 * Temporal Memory implementation in Java.
 * 
 * @author Numenta
 * @author cogmission
 */
public class TemporalMemory implements ComputeDecorator, Serializable{
    /** simple serial version id */
	private static final long serialVersionUID = 1L;
    
    private static final double EPSILON = 0.00001;
    
    private static final int ACTIVE_COLUMNS = 1;
    
    /**
     * Uses the specified {@link Connections} object to Build the structural 
     * anatomy needed by this {@code TemporalMemory} to implement its algorithms.
     * 
     * The connections object holds the {@link Column} and {@link Cell} infrastructure,
     * and is used by both the {@link SpatialPooler} and {@link TemporalMemory}. Either of
     * these can be used separately, and therefore this Connections object may have its
     * Columns and Cells initialized by either the init method of the SpatialPooler or the
     * init method of the TemporalMemory. We check for this so that complete initialization
     * of both Columns and Cells occurs, without either being redundant (initialized more than
     * once). However, {@link Cell}s only get created when initializing a TemporalMemory, because
     * they are not used by the SpatialPooler.
     * 
     * @param   c       {@link Connections} object
     */
    
    
    public static void init(Connections c) {
        SparseObjectMatrix<Column> matrix = c.getMemory() == null ?
            new SparseObjectMatrix<Column>(c.getColumnDimensions()) :
                c.getMemory();
        c.setMemory(matrix);
        
        int numColumns = matrix.getMaxIndex() + 1;
        c.setNumColumns(numColumns);
        int cellsPerColumn = c.getCellsPerColumn();//获取每一列有多少个单元
        Cell[] cells = new Cell[numColumns * cellsPerColumn];//创建一个单元数组
        
        //Used as flag to determine if Column objects have been created.
        Column colZero = matrix.getObject(0);//获取第1个列
        for(int i = 0;i < numColumns;i++) {//对于每一列
            Column column = colZero == null ? 
                new Column(cellsPerColumn, i) : matrix.getObject(i);
            for(int j = 0;j < cellsPerColumn;j++) {
                cells[i * cellsPerColumn + j] = column.getCell(j);//把Colum中的列放到单元数组中去，初始化所有单元的数组
            }
            //If columns have not been previously configured
            if(colZero == null) matrix.set(i, column);
        }
        //Only the TemporalMemory initializes cells so no need to test for redundancy
        c.setCells(cells);//为时间层初始化所有的单元的数组
    }

	@Override
	public ComputeCycle compute(Connections connections, int[] activeColumns, boolean learn) {
	    ComputeCycle cycle = new ComputeCycle();//新建一个ComputeCycle对象，看清楚这是每次循环都调用
		activateCells(connections, cycle, activeColumns, learn);//获取激活的单元和胜出的单元
		activateDendrites(connections, cycle, learn);//激活基底树突
		
		return cycle;
	}
	
	/**
	 * Calculate the active cells, using the current active columns and dendrite
     * segments. Grow and reinforce synapses.
     * 计算激活的单元，用目前激活的列和树突，增加和增强突触
     * <pre>
     * Pseudocode:伪代码
     *   for each column 对于每一列
     *     if column is active and has active distal dendrite segments 如果列是激活列并且有激活的基底树突
     *       call activatePredictedColumn 调用激活预测列函数activatePredictedColumn
     *     if column is active and doesn't have active distal dendrite segments 如果列是激活列但是没有激活的基底树突
     *       call burstColumn 调用 burstColumn 获取胜出单元
     *     if column is inactive and has matching distal dendrite segments 如果列是非激活的并且有匹配的基底树突
     *       call punishPredictedColumn 调用punishPredictedColumn 惩罚预测列函数
     *      
     * </pre>
     * 
	 * @param conn                     
	 * @param activeColumnIndices
	 * @param learn
	 */
	@SuppressWarnings("unchecked")
    public void activateCells(Connections conn, ComputeCycle cycle, int[] activeColumnIndices, boolean learn) {
	    
	    ColumnData columnData = new ColumnData();//存储列数据的对象，里面主要是一个Tuple,第一个对象为Column,第二个对象为激活的单元柱列表，第三个对象为激活的基底树突集合，第四个对象激活的匹配对象集合
        
        Set<Cell> prevActiveCells = conn.getActiveCells();//获取激活的单元集合，上一轮激活的单元集合
        Set<Cell> prevWinnerCells = conn.getWinnerCells();//获取胜出的单元集合，上一轮胜出的单元集合
        
        List<Column> activeColumns = Arrays.stream(activeColumnIndices)//获取SptialPooler轮激活的单元柱的列表
            .sorted()
            .mapToObj(i -> conn.getColumn(i))
            .collect(Collectors.toList());
        
        Function<Column, Column> identity = Function.identity();//Function.identity()返回一个输出跟输入一样的Lambda表达式对象，等价于t->t形式的Lambda表达式
        Function<DistalDendrite, Column> segToCol = segment -> segment.getParentCell().getColumn(); //这是一个Lambda表达式，相当于用基底树突获取其所对应的列
        
        @SuppressWarnings({ "rawtypes" })///下面这一行程序把本轮Spatial Pooler激活的列，以及上一轮激活的树突、匹配树突对应的列都搞到一起了
        GroupBy2<Column> grouper = GroupBy2.<Column>of(
            new Pair(activeColumns, identity),
            new Pair(new ArrayList<>(conn.getActiveSegments()), segToCol),
            new Pair(new ArrayList<>(conn.getMatchingSegments()), segToCol));//这个GroupBy2对象输入了3个Pair,第一个pair的值是第一个是激活的单元柱列表，其映射函数为identy，
        //第二个pair的第一个值是激活的基底树突列表，其映射函数为激活基底树突对应的单元柱，第三个Pair的第一个值为匹配的基底树突列表，其映射函数为匹配基地树突对应的单元柱，其目的是获取每个单元柱对应的单元组本身，其激活树突列表和匹配树突列表，这些值形成一个Tuple
        double permanenceIncrement = conn.getPermanenceIncrement();//突触学习过程中增加的量
        double permanenceDecrement = conn.getPermanenceDecrement();//突触学习过程中减少的量
        
        for(Tuple t : grouper) {//对每一个第一轮激活的单元柱，以及上一轮激活的树突、匹配树突对应的列都搞到一起了
            columnData = columnData.set(t);//把Tuple赋值到ColumnData中去
            
            if(columnData.isNotNone(ACTIVE_COLUMNS)) {//如果这个列不是非激活列，即这个列是激活列的话
                if(!columnData.activeSegments().isEmpty()) {//如果激活的基底树突不为空,也就是这个列包含上一轮中激活的基底树突
                    List<Cell> cellsToAdd = activatePredictedColumn(conn, columnData.activeSegments(),
                        columnData.matchingSegments(), prevActiveCells, prevWinnerCells, 
                            permanenceIncrement, permanenceDecrement, learn);//把这个列所包含的上一轮激活的基底树突所在的单元作为激活单元，并更新树突的突触值
                    
                    cycle.activeCells.addAll(cellsToAdd);//把激活单元添加入激活单元列表中去
                    cycle.winnerCells.addAll(cellsToAdd);//激活单元同时也作为胜出单元
                }else{
                    Tuple cellsXwinnerCell = burstColumn(conn, columnData.column(), columnData.matchingSegments(), 
                        prevActiveCells, prevWinnerCells, permanenceIncrement, permanenceDecrement, conn.getRandom(), 
                           learn);//获取处理的列的单元集合以及胜出的单元
                    
                    cycle.activeCells.addAll((List<Cell>)cellsXwinnerCell.get(0));//把所有处理的列的单元的集合都作为激活的单元放入ComputeCycle的激活单元数组
                    cycle.winnerCells.add((Cell)cellsXwinnerCell.get(1));//把胜出的单元作为胜出单元放入ComputeCycle的胜出单元数组
                }
            }else{//如果这个列不是激活列，即普通匹配树突和激活树突所在的列，那么这个匹配树突的突触连接的持久度是要减小的，现在由于这个减小值被设为了0，所以没有减小
                if(learn) {
                    punishPredictedColumn(conn, columnData.activeSegments(), columnData.matchingSegments(), 
                        prevActiveCells, prevWinnerCells, conn.getPredictedSegmentDecrement());
                }
            }
        }
	}
	
	/**
	 * Calculate dendrite segment activity, using the current active cells.
	 * 使用当前活动单元计算树突的活跃性
	 * <pre>
	 * Pseudocode:伪码
     *   for each distal dendrite segment with activity >= activationThreshold 对于每一个活性值大于等于激活阈值的基底树突
     *     mark the segment as active 把这个树突标记为活跃树突
     *   for each distal dendrite segment with unconnected activity >= minThreshold 对于每一个非连接活性大于等于最小阈值的基底树突
     *     mark the segment as matching 把这个树突标记为匹配树突
     * </pre>
     * 
	 * @param conn     the Connectivity
	 * @param cycle    Stores current compute cycle results
	 * @param learn    If true, segment activations will be recorded. This information is used
     *                 during segment cleanup.
	 */
	public void activateDendrites(Connections conn, ComputeCycle cycle, boolean learn) {
	    Activity activity = conn.computeActivity(cycle.activeCells, conn.getConnectedPermanence());//activity里面存储的是活跃单元所包含的每个树突有突触数量，以及连接的突触数量
	    
	    List<DistalDendrite> activeSegments = IntStream.range(0, activity.numActiveConnected.length)
	        .filter(i -> activity.numActiveConnected[i] >= conn.getActivationThreshold())//这个树突处于连接状态的突触的个数大于指定的阈值
	        .mapToObj(i -> conn.segmentForFlatIdx(i))
	        .collect(Collectors.toList());//这句话相当于获取了处于激活状态的树突列表
	    
	    List<DistalDendrite> matchingSegments = IntStream.range(0, activity.numActiveConnected.length)//
	        .filter(i -> activity.numActivePotential[i] >= conn.getMinThreshold())//这个树突的突触的数量大于指定的阈值，那么这个树突就是匹配的树突
	        .mapToObj(i -> conn.segmentForFlatIdx(i))
	        .collect(Collectors.toList());//获取匹配树突的列表
	    
	    Collections.sort(activeSegments, conn.segmentPositionSortKey);//对激活树突列表进行排序，按照其所属的单元的id大小进行排序
	    Collections.sort(matchingSegments, conn.segmentPositionSortKey);//对匹配数据列表进行排序，按照其所属的单元的id大小进行排序
	    
	    cycle.activeSegments = activeSegments;//把激活树突赋值给本轮ComputeCycle对象
	    cycle.matchingSegments = matchingSegments;//把匹配树突赋值给本轮ComputeCycle对象
	    
	    conn.lastActivity = activity;//把activity里面存储的是活跃单元所包含的每个树突拥有的突触数量，以及连接的突触数量，把这个值赋值给connection的lastActivity对象
	    conn.setActiveCells(new LinkedHashSet<>(cycle.activeCells));//设置激活单元
        conn.setWinnerCells(new LinkedHashSet<>(cycle.winnerCells));//设置胜出单元
        conn.setActiveSegments(activeSegments);//设置激活树突
        conn.setMatchingSegments(matchingSegments);//设置匹配树突
        // Forces generation of the predictive cells from the above active segments
        conn.clearPredictiveCells();
        conn.getPredictiveCells();//获取预测单元的集合
	    
	    if(learn) {
	        activeSegments.stream().forEach(s -> conn.recordSegmentActivity(s));
	        conn.startNewIteration();
	    }
	}
	
	/**
     * Indicates the start of a new sequence. Clears any predictions and makes sure
     * synapses don't grow to the currently active cells in the next time step.指示新序列的开始，清除所有预测，并确保在下一个时间序列突触不会生长到当前激活的单元
     */
    @Override
    public void reset(Connections connections) {
        connections.getActiveCells().clear();//清除掉所有的活跃单元
        connections.getWinnerCells().clear();//清除掉所有的胜出单元
        connections.getActiveSegments().clear();//清除掉所有的激活树突
        connections.getMatchingSegments().clear();//匹配树突也清除掉
    }
    
    /**
	 * Determines which cells in a predicted column should be added to winner cells
     * list, and learns on the segments that correctly predicted this column.
     * 决定在预测列中的那个单元应该成为胜出单元，学习正好预测了这个列的树突
	 * @param conn                 the connections
	 * @param activeSegments       Active segments in the specified column
	 * @param matchingSegments     Matching segments in the specified column
	 * @param prevActiveCells      Active cells in `t-1`
	 * @param prevWinnerCells      Winner cells in `t-1`
	 * @param learn                If true, grow and reinforce synapses
	 * 
	 * <pre>
	 * Pseudocode:
     *   for each cell in the column that has an active distal dendrite segment
     *     mark the cell as active
     *     mark the cell as a winner cell
     *     (learning) for each active distal dendrite segment
     *       strengthen active synapses
     *       weaken inactive synapses
     *       grow synapses to previous winner cells
     * </pre>
     * 
	 * @return A list of predicted cells that will be added to active cells and winner
     *         cells.
	 */
	public List<Cell> activatePredictedColumn(Connections conn, List<DistalDendrite> activeSegments,
	    List<DistalDendrite> matchingSegments, Set<Cell> prevActiveCells, Set<Cell> prevWinnerCells,
	        double permanenceIncrement, double permanenceDecrement, boolean learn) {
	    
	    List<Cell> cellsToAdd = new ArrayList<>();
        Cell previousCell = null;
        Cell currCell;
        for(DistalDendrite segment : activeSegments) {//对于每一个激活的树突，把树突所在的单元添加到激活列表中
            if((currCell = segment.getParentCell()) != previousCell) {
                cellsToAdd.add(currCell);//把树突对应的单元添加到列表中去
                previousCell = currCell;
            }
            
            if(learn) {
                adaptSegment(conn, segment, prevActiveCells, permanenceIncrement, permanenceDecrement);//更新树突的突触值
                
                int numActive = conn.getLastActivity().numActivePotential[segment.getIndex()];//获取这个树突上一轮输入的激活值数量
                int nGrowDesired = conn.getMaxNewSynapseCount() - numActive;
                
                if(nGrowDesired > 0) {//判断是否要新增树突
                    growSynapses(conn, prevWinnerCells, segment, conn.getInitialPermanence(),
                        nGrowDesired, conn.getRandom());
                }
            }
        }
        
        return cellsToAdd;
	}
	
	/**
     * Activates all of the cells in an unpredicted active column,
     * chooses a winner cell, and, if learning is turned on, either adapts or
     * creates a segment. growSynapses is invoked on this segment.
     * </p><p>激活不可预测活动列中所有的单元，选择一个获胜的单元，如果启用学习，则调整或者创建一个树突，这时候growSynapses被调用
     * <b>Pseudocode:</b>
     * </p><p>
     * <pre>
     *  mark all cells as active 标记所有的单元为激活的
     *  if there are any matching distal dendrite segments 如果有任何匹配基底树突
     *      find the most active matching segment 找到最活跃匹配基底树突
     *      mark its cell as a winner cell 把它标记为胜出单元
     *      (learning) 学习
     *      grow and reinforce synapses to previous winner cells 增长或者增强突触到之前胜出的单元集合
     *  else 如果没有任何匹配基底树突
     *      find the cell with the least segments, mark it as a winner cell 找到有着最少的树突的单元，把它标记为胜出单元
     *      (learning) 学习
     *      (optimization) if there are previous winner cells 如果有之前胜出的单元集合，添加一个树突到这个胜出的单元，增长或者增强突触到之前胜出的单元集合
     *          add a segment to this winner cell
     *          grow synapses to previous winner cells
     * </pre>
     * </p>
     * 
     * @param conn                      Connections instance for the TM Connection对象
     * @param column                    Bursting {@link Column} 刺激的列
     * @param matchingSegments          List of matching {@link DistalDendrite}s 匹配的基底树突的集合
     * @param prevActiveCells           Active cells in `t-1` t-1时刻激活的单元
     * @param prevWinnerCells           Winner cells in `t-1` t-1时刻胜出的单元
     * @param permanenceIncrement       Amount by which permanences of synapses
     *                                  are decremented during learning 在学习过程中突触持久度减少的量
     * @param permanenceDecrement       Amount by which permanences of synapses
     *                                  are incremented during learning 在学习过程中突触持久度增加的量
     * @param random                    Random number generator 随机数产生器
     * @param learn                     Whether or not learning is enabled 是否学习的标记
     * 
     * @return  Tuple containing: 返回值Tuple包含：
     *                  cells       list of the processed column's cells 处理的列的单元集合列表
     *                  bestCell    the best cell 胜出的单元
     */
    public Tuple burstColumn(Connections conn, Column column, List<DistalDendrite> matchingSegments, 
        Set<Cell> prevActiveCells, Set<Cell> prevWinnerCells, double permanenceIncrement, double permanenceDecrement, 
            Random random, boolean learn) {
        
        List<Cell> cells = column.getCells();//获取这个列的单元集合
        Cell bestCell = null;
        
        if(!matchingSegments.isEmpty()) {//如果匹配的树突不为空，也就是这个列本身是激活列，其还有匹配树突，那么从匹配树突里面选择输入激活值最大的树突所在单位作为胜出单元，并且对这个树突的突触进行强化，和上一轮激活单元有连接的，也就是上一轮激活的突触，持久度值增加，否则减小；最后把这个树突再增加几个突触
            int[] numPoten = conn.getLastActivity().numActivePotential;//获取上一轮各个树突的输入值列表
            Comparator<DistalDendrite> cmp = (dd1,dd2) -> numPoten[dd1.getIndex()] - numPoten[dd2.getIndex()]; //定义一个比较函数，比较函数的内容是比较上一轮两个树突输入激活值的大小，大的胜出
            
            DistalDendrite bestSegment = matchingSegments.stream().max(cmp).get();//获取这个列的匹配树突中，输入值最大的树突，并把这个树突作为最佳树突（这个匹配树突肯定是这个列的某个或者某几个单元的匹配树突，这里相当于在这个列的范围内把上一轮输入的激活值最多的这个树突给挑选出来）
            bestCell = bestSegment.getParentCell();//把最佳树突所在的单元作为最匹配单元
            
            if(learn) {
                adaptSegment(conn, bestSegment, prevActiveCells, permanenceIncrement, permanenceDecrement);//更新最佳树突的突触的持久度值，与上一轮激活单元有连接的突触的持久度值增加，没有连接的突触的持久度减少
                
                int nGrowDesired = conn.getMaxNewSynapseCount() - numPoten[bestSegment.getIndex()];//计算最佳树突在上一轮输入值与最大新增突触数量的阈值的差距
                
                if(nGrowDesired > 0) {
                    growSynapses(conn, prevWinnerCells, bestSegment, conn.getInitialPermanence(), 
                        nGrowDesired, random); //为最佳树突增加一个突触，突触前单元为上一轮胜出的单元
                }
            }
        }else{//如果这个列上没有匹配树突，那么只能把所有的都激活，从中随机选择一个单元作为胜出单元
            bestCell = leastUsedCell(conn, cells, random);//获取基底树突最少的单元
            if(learn) {
                int nGrowExact = Math.min(conn.getMaxNewSynapseCount(), prevWinnerCells.size());//nGrowExact为一次学习中添加至树突的突触的最大值 和 前一时刻胜出的单元的数量 之间的最小值,当prevWinnerCells小于6的时候是不新增突触的
                if(nGrowExact > 0) {
                    DistalDendrite bestSegment = conn.createSegment(bestCell);//为这个树突最少的单元创建了基底树突并且返回了这个创建的基底树突
                    growSynapses(conn, prevWinnerCells, bestSegment, conn.getInitialPermanence(), 
                        nGrowExact, random);//为这个基底树突添加突触，实现这个基底树突的每一个突触与上一时胜出单元的相连，不超过指定的数量
                }
            }
        }
        
        return new Tuple(cells, bestCell);//返回一个Tuple,tuple中放置的是所有激活的单元以及胜出的单元
    }
    
    /**
     * Punishes the Segments that incorrectly predicted a column to be active.
     * 惩罚错误预测一个列处于活动状态的树突
     * <p>
     * <pre>
     * Pseudocode:
     *  for each matching segment in the column
     *    weaken active synapses
     * </pre>
     * </p>
     *   
     * @param conn                              Connections instance for the tm
     * @param activeSegments                    An iterable of {@link DistalDendrite} actives
     * @param matchingSegments                  An iterable of {@link DistalDendrite} matching
     *                                          for the column compute is operating on
     *                                          that are matching; None if empty
     * @param prevActiveCells                   Active cells in `t-1`
     * @param prevWinnerCells                   Winner cells in `t-1`
     *                                          are decremented during learning.
     * @param predictedSegmentDecrement         Amount by which segments are punished for incorrect predictions
     */
    public void punishPredictedColumn(Connections conn, List<DistalDendrite> activeSegments, 
        List<DistalDendrite> matchingSegments, Set<Cell> prevActiveCells, Set<Cell> prevWinnerCells,
           double predictedSegmentDecrement) {
        //如果predictedSegmentDecrement的值大于0，那么对匹配的树突进行调整
        if(predictedSegmentDecrement > 0) {
            for(DistalDendrite segment : matchingSegments) {
                adaptSegment(conn, segment, prevActiveCells, -conn.getPredictedSegmentDecrement(), 0);//相当于把激活的
            }
        }
    }
	
	
    ////////////////////////////
    //     Helper Methods     //
    ////////////////////////////
    
	/**
     * Gets the cell with the smallest number of segments.
     * Break ties randomly.
     * 获取有最少树突数量的单元，如果多个单元的树突数量都是最少，那么从中随机选择一个作为树突最少的单元
     * @param conn      Connections instance for the tm tm的Connections实例
     * @param cells     List of {@link Cell}s 单元列表
     * @param random    Random Number Generator 随机数生成器
     * 
     * @return  the least used {@code Cell}
     */
    public Cell leastUsedCell(Connections conn, List<Cell> cells, Random random) {
        List<Cell> leastUsedCells = new ArrayList<>();//最近使用的单元集合
        int minNumSegments = Integer.MAX_VALUE;
        for(Cell cell : cells) {
            int numSegments = conn.numSegments(cell);//获取这个单元拥有的基底树突的数量
            
            if(numSegments < minNumSegments) {
                minNumSegments = numSegments;
                leastUsedCells.clear();
            }
            
            if(numSegments == minNumSegments) {
                leastUsedCells.add(cell);
            }
        }
        
        int i = random.nextInt(leastUsedCells.size());
        return leastUsedCells.get(i);
    }
    
    /**
     * Creates nDesiredNewSynapes synapses on the segment passed in if
     * possible, choosing random cells from the previous winner cells that are
     * not already on the segment.
     * <p>
     * <b>Notes:</b> The process of writing the last value into the index in the array
     * that was most recently changed is to ensure the same results that we get
     * in the c++ implementation using iter_swap with vectors.
     * </p>
     * 
     * @param conn                      Connections instance for the tm
     * @param prevWinnerCells           Winner cells in `t-1`
     * @param segment                   Segment to grow synapses on.     
     * @param initialPermanence         Initial permanence of a new synapse.
     * @param nDesiredNewSynapses       Desired number of synapses to grow
     * @param random                    Tm object used to generate random
     *                                  numbers
     */
    public void growSynapses(Connections conn, Set<Cell> prevWinnerCells, DistalDendrite segment, 
        double initialPermanence, int nDesiredNewSynapses, Random random) {
        
        List<Cell> candidates = new ArrayList<>(prevWinnerCells);//生成一个前一个时刻胜出的单元的列表
        Collections.sort(candidates);
        
        for(Synapse synapse : conn.getSynapses(segment)) {//获取这个树突的所有突触列表，并且对于每个突触，如果这个突触前单元已经是前一刻胜出单元（也就是现有的候选单元），那么把这个候选单元删除掉，这里的目的是防止在一个树突上重复建设突触
            Cell presynapticCell = synapse.getPresynapticCell();//获取这个突触的突触前单元
            int index = candidates.indexOf(presynapticCell);//如果这个单元是，前一时刻胜出的单元列表中的一员
            if(index != -1) {
                candidates.remove(index);//从胜出单元列表中清除这个单元
            }
        }
        
        int candidatesLength = candidates.size();//获取上一时刻激活的单元的数量
        int nActual = nDesiredNewSynapses < candidatesLength ? nDesiredNewSynapses : candidatesLength;//新增的突触的数量只能小于或等于candidatesLength，相当于取nDesiredNewSynapses和candidatesLength的较小者
        
        for(int i = 0;i < nActual;i++) {//下面该创建突触了，譬如有7个激活的单元，如果一次最多只能创建6个突触的话，会从这7个激活单元随机选择6个，形成连接突触
            int rand = random.nextInt(candidates.size());//以上一时刻激活的单元的数量为种子，获取一个一个随机数
            conn.createSynapse(segment, candidates.get(rand), initialPermanence);//为这个树突产生一个新的突触，其突触前单元为胜出单元
            candidates.remove(rand);
        }
    }

    /**
     * Updates synapses on segment.
     * Strengthens active synapses; weakens inactive synapses.
     *  更新树突上的突触的持久度值，加强激活的突触的值，减小未被激活的突触的值
     * @param conn                      {@link Connections} instance for the tm
     * @param segment                   {@link DistalDendrite} to adapt
     * @param prevActiveCells           Active {@link Cell}s in `t-1`
     * @param permanenceIncrement       Amount to increment active synapses    
     * @param permanenceDecrement       Amount to decrement inactive synapses
     */
    public void adaptSegment(Connections conn, DistalDendrite segment, Set<Cell> prevActiveCells, 
        double permanenceIncrement, double permanenceDecrement) {
        
        // Destroying a synapse modifies the set that we're iterating through.
        List<Synapse> synapsesToDestroy = new ArrayList<>();
        
        for(Synapse synapse : conn.getSynapses(segment)) {//获取这个树突的突触列表
            double permanence = synapse.getPermanence();//获取这个突触的持久度
            
            if(prevActiveCells.contains(synapse.getPresynapticCell())) {//如果这个突触和上一时刻激活的单元相连接了
                permanence += permanenceIncrement;//突触值增加
            }else{
                permanence -= permanenceDecrement;//否则减少
            }
            
            // Keep permanence within min/max bounds
            permanence = permanence < 0 ? 0 : permanence > 1.0 ? 1.0 : permanence;//把持久度值限定在0和1之间
            
            // Use this to examine issues caused by subtle floating point differences
            // be careful to set the scale (1 below) to the max significant digits right of the decimal point
            // between the permanenceIncrement and initialPermanence
            //
            // permanence = new BigDecimal(permanence).setScale(1, RoundingMode.HALF_UP).doubleValue(); 
            
            if(permanence < EPSILON) {
                synapsesToDestroy.add(synapse);//如果突触的值过小的情况下，把这个突触加入销毁突触列表里面
            }else{
                synapse.setPermanence(conn, permanence);//设置这个突触的新持久度值
            }
        }
        
        for(Synapse s : synapsesToDestroy) {//销毁持久度值过小的突触
            conn.destroySynapse(s);
        }
        
        if(conn.numSynapses(segment) == 0) {
            conn.destroySegment(segment);//如果一个树突没有突触了，那么把这个树突也销毁
        }
    }
   	/**
     * Used in the {@link TemporalMemory#compute(Connections, int[], boolean)} method
     * to make pulling values out of the {@link GroupBy2} more readable and named.在TemporalMemory的compute方法中使用，目的是使从GroupBy2中提取值更具可读性和命名性
     */
    @SuppressWarnings("unchecked")
    public static class ColumnData implements Serializable {
        /** Default Serial */
        private static final long serialVersionUID = 1L;
        Tuple t;//里面是一个Tuple,tuple里面存的是很多对象，每个对象都有一个索引,相当于包裹的是一个对象数组
        public ColumnData() {}
        public ColumnData(Tuple t) {
            this.t = t;
        }
        /**第0个元素存储的是这个列*/
        public Column column() { return (Column)t.get(0); }//这个对象数组的第0个元素为一个Colum对象
        /**第一个元素存储的是激活的单元柱的列表*/
        public List<Column> activeColumns() { return (List<Column>)t.get(1); }//这个对象数组的第1个元素为激活的单元柱的列表
        /**第二个元素存储的是激活的基底树突的集合*/
        public List<DistalDendrite> activeSegments() { 
            return ((List<?>)t.get(2)).get(0).equals(Slot.empty()) ? 
                Collections.emptyList() :
                    (List<DistalDendrite>)t.get(2); //第二个单元柱为激活的基底树突的列表
        }
        /**第三个元素存储的是匹配的基底树突*/
        public List<DistalDendrite> matchingSegments() {
            return ((List<?>)t.get(3)).get(0).equals(Slot.empty()) ? 
                Collections.emptyList() :
                    (List<DistalDendrite>)t.get(3); //第三个元素为匹配的基底树突的列表
        }
        
        public ColumnData set(Tuple t) { this.t = t; return this; }
        
        /**
         * Returns a boolean flag indicating whether the slot contained by the
         * tuple at the specified index is filled with the special empty
         * indicator.
         * 
         * @param memberIndex   the index of the tuple to assess.
         * @return  true if <em><b>not</b></em> none, false if it <em><b>is none</b></em>.
         */
        public boolean isNotNone(int memberIndex) {
            return !((List<?>)t.get(memberIndex)).get(0).equals(NONE);
        }
    } 
}
