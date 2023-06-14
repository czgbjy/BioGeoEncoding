package org.numenta.nupic.model;

import java.io.PrintWriter;
import java.io.Serializable;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import org.numenta.nupic.Parameters;
import org.numenta.nupic.algorithms.SpatialPooler;
import org.numenta.nupic.algorithms.TemporalMemory;
import org.numenta.nupic.network.Persistence;
import org.numenta.nupic.network.PersistenceAPI;
import org.numenta.nupic.serialize.SerialConfig;
import org.numenta.nupic.util.AbstractSparseBinaryMatrix;
import org.numenta.nupic.util.ArrayUtils;
import org.numenta.nupic.util.FlatMatrix;
import org.numenta.nupic.util.SparseMatrix;
import org.numenta.nupic.util.SparseObjectMatrix;
import org.numenta.nupic.util.Topology;
import org.numenta.nupic.util.UniversalRandom;
import chaschev.lang.Pair;
import gnu.trove.list.array.TIntArrayList;

/**
 * Contains the definition of the interconnected structural state of the {@link SpatialPooler} and
 * {@link TemporalMemory} as well as the state of all support structures
 * (i.e. Cells, Columns, Segments, Synapses etc.).包含空间池和时间内存的相互连接结构状态的定义，以及所有支持结构（即单元、列、片段、突触等）的状态。
 *
 * In the separation of data from logic, this class represents the data/state.在数据与逻辑的分离中，此类标识数据/状态
 */
public class Connections implements Persistable {
    /** keep it simple */
    private static final long serialVersionUID = 1L;
    
    private static final double EPSILON = 0.00001;
    
    /////////////////////////////////////// Spatial Pooler Vars ///////////////////////////////////////////
    /** <b>WARNING:</b> potentialRadius **must** be set to 
     * the inputWidth if using "globalInhibition" and if not 
     * using the Network API (which sets this automatically) 
     * 潜在半径的宽度
     */
    private int[] potentialRadius = new int[]{16};//潜在半径的宽度
    /**使得空间池潜在输入有多大比例被选中*/
    private double potentialPct = 0.5;//这个参数的作用是使得空间池潜在输入有多大比例被选中
    private boolean globalInhibition = false;//如果为真，则在抑制阶段，获胜列为整个区域中最活跃的列，否则，将根据其本地邻居选择获胜列。使用全局抑制可以提高60倍性能
    private double localAreaDensity = -1.0;//局部抑制区内激活列的期望密度，确保在局部抑制区最多有N个列被激活，N=localAreaDensity*(抑制区中列的总数）
    /**
     * 控制激活单元柱密度的另一种方法，如果NUM_ACTIVE_COLUMNS_PER_INH_AREA被指定，LOCAL_AREA_DENSITY必须小于0
     */
    private double numActiveColumnsPerInhArea;//
    /**指定使列激活必须处于激活状态的突触的最小数量。这样做的目的是防止噪波输入激活列。*/
    private double stimulusThreshold = 0;//指定使列激活必须处于激活状态的突触的最小数量。这样做的目的是防止噪波输入激活列。以完全发育的突触的百分比表示。
    private double synPermInactiveDec = 0.008;//每一轮中一个不活跃突触的持久度减少的值，用完全发育的突触的百分比来表示。
    /**
     * 每一轮中一个活跃突触增加的持久度值，指定为完全发育的突触的百分比，初始值为0.05
     */
    private double synPermActiveInc = 0.05;//每一轮中一个活跃突触增加的持久度值，指定为完全发育的突触的百分比
    /**
     * 默认连接阈值，任何持久度的值高于这个连接阈值的突触都是“连接的突触”，意味这个突触可以促进细胞的激活，其值初始设置为0.1。
     */
    private double synPermConnected = 0.10;//
    private double synPermBelowStimulusInc = synPermConnected / 10.0;
    private double minPctOverlapDutyCycles = 0.001;//一个0到1之间的值，用于设定一个列至少应该有刺激阈值的活跃输入的最低频率。每一列定期查看其抑制半径内所有其他列重叠占空比，
    private double minPctActiveDutyCycles = 0.001;//一个0到1之间的值，用来设置一个列应该被激活的最低频率。每一列定期查看其抑制半径内所有其他列的活动占空比，并将其内部最小可接受占空比
    private double predictedSegmentDecrement = 0.001;//2022年10月7日在这里做了改动，目的是让预测错误的突触的连接权重的大小减少
    private int dutyCyclePeriod = 1000;//用于计算占空比的周期，较高的值会使相应boost或synPerConnectedCell中的更改所需要的时间更长，较小的值使他更不稳定，更容易震荡。
    private double maxBoost = 10.0;//最大的重叠促进因子，在进行抑制之前，每一列的重合度都有乘以一个增强因子，领的实际的增强因子是一个1到maxBoot之间的数。
    private boolean wrapAround = true;
    //private boolean wrapAround = false;
    /**输入数据的维度*/
    private int numInputs = 1;  //product of input dimensions 输入数据的维度
    private int numColumns = 1; //product of column dimensions 列的维度

    /**突触的持久度值的最小值*/
    private double synPermMin = 0.0;
    /**突触的持久度值的最大值**/
    private double synPermMax = 1.0;
    /**
     * 每一轮中一个活跃突触增加的持久度值，指定为完全发育的突触的百分比synPermActiveInc除以2，为其阈值。突触值的最小阈值，低于这个值的突触持久度值直接被归零
     */
    private double synPermTrimThreshold = synPermActiveInc / 2.0;
    private int updatePeriod = 50;
    private double initConnectedPct = 0.5;

    //Internal state
    private double version = 1.0;
    public int spIterationNum = 0;
    public int spIterationLearnNum = 0;
    public long tmIteration = 0;
    
    public double[] boostedOverlaps;
    public int[] overlaps;
    
    /** Manages input neighborhood transformations */
    private Topology inputTopology;
    /** Manages column neighborhood transformations */
    private Topology columnTopology;
    /** A matrix representing the shape of the input. 代表输入形状的矩阵 */
    protected SparseMatrix<?> inputMatrix;
    /**
     * Store the set of all inputs that are within each column's potential pool.
     * 'potentialPools' is a matrix, whose rows represent cortical columns, and
     * whose columns represent the input bits. if potentialPools[i][j] == 1,
     * then input bit 'j' is in column 'i's potential pool. A column can only be
     * connected to inputs in its potential pool. The indices refer to a
     * flattened version of both the inputs and columns. Namely, irrespective
     * of the topology of the inputs and columns, they are treated as being a
     * one dimensional array. Since a column is typically connected to only a
     * subset of the inputs, many of the entries in the matrix are 0. Therefore
     * the potentialPool matrix is stored using the SparseObjectMatrix
     * class, to reduce memory footprint and computation time of algorithms that
     * require iterating over the data structure.存储在每个列的潜在感受野中的所有输入的集合。“potentialPools”是一个矩阵，其行表示皮质列，其列表示输入二进制序列。
     * 如果potentialPools[i][j]==1,则输入位"j"位于"i"列的potential pool中。一个列只能连接到其潜在池中的输入。索引指的是输入和列的扁平化版本。也就是说，不管输入和列
     * 的图谱关系如何，他们都被看做是一个一维数组。由于一列通常只连接到输入的一个子集，因此矩阵中的许多条目为0.因此，potentialPool矩阵使用SparseObjectMatrix类存储，以
     * 减少需要迭代数据的算法的内存占用和计算时间。
     */
    private FlatMatrix<Pool> potentialPools;
    /**
     * Initialize a tiny random tie breaker. This is used to determine winning
     * columns where the overlaps are identical.初始化一个小的随机节中断器，用于确定重叠值相同的列中的获胜列。
     */
    private double[] tieBreaker;
    /**
     * Stores the number of connected synapses for each column. This is simply
     * a sum of each row of 'connectedSynapses'. again, while this
     * information is readily available from 'connectedSynapses', it is
     * stored separately for efficiency purposes.为每一个列存储连接的突触的数量，这是每一行连接的突触的简单加和，同样的这个信息也可可以从连接的突触中获取，这里分开存储是为了提高效率
     */
    private AbstractSparseBinaryMatrix connectedCounts;
    /**
     * The inhibition radius determines the size of a column's local
     * neighborhood. of a column. A cortical column must overcome the overlap
     * score of columns in its neighborhood in order to become actives. This
     * radius is updated every learning round. It grows and shrinks with the
     * average number of connected synapses per column.
     */
    private int inhibitionRadius = 0;

    private double[] overlapDutyCycles;//重叠占空比
    private double[] activeDutyCycles;//激活占空比
    private volatile double[] minOverlapDutyCycles;//最小重叠占空比
    private volatile double[] minActiveDutyCycles;//最小激活占空比
    private double[] boostFactors;//提升因子

    /////////////////////////////////////// Temporal Memory Vars ///////////////////////////////////////////
    /**活跃的单元集合*/
    protected Set<Cell> activeCells = new LinkedHashSet<Cell>();//
    /**胜出的单元集合*/
    protected Set<Cell> winnerCells = new LinkedHashSet<Cell>();//胜出的单元集合
    /**预测单元集合*/
    protected Set<Cell> predictiveCells = new LinkedHashSet<>();//预测单元集合
    /**激活的基底树突集合*/
    protected List<DistalDendrite> activeSegments = new ArrayList<>();//激活的基底树突集合
    /**匹配的基底树突集合*/
    protected List<DistalDendrite> matchingSegments = new ArrayList<>();//匹配的基底树突集合
    
    /** Total number of columns */
    protected int[] columnDimensions = new int[] { 2048 };//列的维度
    /** Total number of cells per column 每一列有的单元数量*/
    protected int cellsPerColumn = 32;//
    /** What will comprise the Layer input. Input (i.e. from encoder) */
    protected int[] inputDimensions = new int[] { 32, 32 };//输入数据的维度
    /**
     * If the number of active connected synapses on a segment
     * is at least this threshold, the segment is said to be active.
     * 如果一个节段上活跃的突触的数量至少是这个阈值，那么这个节段就被称为是活跃节段。
     */
    private int activationThreshold = 13;
    /**
     * Radius around cell from which it can
     * sample to form distal {@link DistalDendrite} connections.
     * 细胞的半径，在这个半径之内可以形成末端树突连接。
     */
    private int learningRadius = 2048;
    /**
     * If the number of synapses active on a segment is at least this
     * threshold, it is selected as the best matching
     * cell in a bursting column.如果一个片段上活跃的突触的数量至少是这个阈值，那么它就被选为一列中最匹配的细胞。
     */
    private int minThreshold = 10;
    /** The maximum number of synapses added to a segment during learning. 在学习的过程中，被新添加到一个片段中的突触的最大数量*/
    private int maxNewSynapseCount = 20;
    /** The maximum number of segments (distal dendrites) allowed on a cell细胞上允许的最大片段数（远端树突） */
    private int maxSegmentsPerCell = 255;
    /** The maximum number of synapses allowed on a given segment (distal dendrite) 一个给定远端树突上允许的最大数量的突触数量 */
    private int maxSynapsesPerSegment = 255;
    /** Initial permanence of a new synapse 一个新的突触的初始化持久度值 */
    private double initialPermanence = 0.21;
    /**
     * If the permanence value for a synapse
     * is greater than this value, it is said
     * to be connected. 如果一个突触的持久度值比这个值大，那么说这个突触是一个连接突触
     */
    private double connectedPermanence = 0.50;
    /**
     * Amount by which permanences of synapses
     * are incremented during learning.在学习过程中，突触的持久度的增加值
     */
    private double permanenceIncrement = 0.10;
    /**
     * Amount by which permanences of synapses
     * are decremented during learning.在学习过程中，突触的持久度的减少值
     */
    private double permanenceDecrement = 0.10;

    /** The main data structure containing columns, cells, and synapses 主要的数据结构，包含列，单元和突触*/
    private SparseObjectMatrix<Column> memory;
    /**单元的集合***/
    private Cell[] cells;

    ///////////////////////   Structural Elements /////////////////////////
    /** Reverse mapping from source cell to {@link Synapse}所有的源细胞到突触的反向映射 */
    public Map<Cell, LinkedHashSet<Synapse>> receptorSynapses;

    /**所有细胞所有的基底树突集合*/
    protected Map<Cell, List<DistalDendrite>> segments;
    /** 所有基底树突拥有的突触集合*/
    public Map<Segment, List<Synapse>> distalSynapses;
    /**所有近端树突拥有的突触集合*/
    protected Map<Segment, List<Synapse>> proximalSynapses;

    /** Helps index each new proximal Synapse 帮助索引每个新的近端突触  */
    protected int proximalSynapseCounter = -1;
    /** Global tracker of the next available segment index 下一个可用的片段的索引的全局跟踪器 */
    protected int nextFlatIdx;
    /** Global counter incremented for each DD segment creation 为每个基底树突创建递增的全局计数器*/
    protected int nextSegmentOrdinal;
    /** Global counter incremented for each DD synapse creation 为每个基底突触创建而标记的自增长全局计数器*/
    protected int nextSynapseOrdinal;
    /** Total number of synapses 突触的总数 */
    protected long numSynapses;
    /** Used for recycling {@link DistalDendrite} indexes 用来回收的基底树突的索引 */
    protected TIntArrayList freeFlatIdxs = new TIntArrayList();
    /** Indexed segments by their global index (can contain nulls) 按它们的全局索引来索引的树突的集合 */
    protected List<DistalDendrite> segmentForFlatIdx = new ArrayList<>();
    /** Stores each cycle's most recent activity 存储每个周期的最新activity */
    public Activity lastActivity;
    /** The default random number seed */
    protected int seed = 42;
    /** The random number generator 随机数的产生器 */
    public Random random = new UniversalRandom(seed);
    
    /** Sorting Lambda used for sorting active and matching segments 排序Lambda，用于对活动段和匹配树突进行排序*////(Comparator<DistalDendrite> & Serializable)的含义是把Lambda函数的返回值转换为可序列化的比较器
    public Comparator<DistalDendrite> segmentPositionSortKey = (Comparator<DistalDendrite> & Serializable)(s1,s2) -> {
        double c1 = s1.getParentCell().getIndex() + ((double)(s1.getOrdinal() / (double)nextSegmentOrdinal));
        double c2 = s2.getParentCell().getIndex() + ((double)(s2.getOrdinal() / (double)nextSegmentOrdinal));
        return c1 == c2 ? 0 : c1 > c2 ? 1 : -1;
    };

    /** Sorting Lambda used for SpatialPooler inhibition 空间池的抑制区的排序Lambda函数，Pair里面就是存储了A和B两个对象，这里是存储了一个Integer对象和一个Double对象*/
    public Comparator<Pair<Integer, Double>> inhibitionComparator = (Comparator<Pair<Integer, Double>> & Serializable)
        (p1, p2) -> { 
            int p1key = p1.getFirst();
            int p2key = p2.getFirst();
            double p1val = p1.getSecond();
            double p2val = p2.getSecond();
            if(Math.abs(p2val - p1val) < 0.000000000000000000001) //如果这两个组的值（基本）相等
            {
                return Math.abs(p2key - p1key) < 0.000000000000000000001 ? 0 : p2key > p1key ? -1 : 1;//如果他们的索引页基本相等，那么返回0，如果第二个值的索引小于第一个值的索引，则返回1，否则返回-1
            } 
            else
            {
                return p2val > p1val ? -1 : 1;//如果这两个组的值不相等，且第二个值小于第一个值，那么返回1，否则返回-1
            }
        };
    
    ////////////////////////////////////////
    //       Connections Constructor      //
    ////////////////////////////////////////
    /**
     * Constructs a new {@code OldConnections} object. This object
     * is usually configured via the {@link Parameters#apply(Object)}
     * method.
     */
    public Connections() {}
    
    /**
     * Returns a deep copy of this {@code Connections} object.
     * @return a deep copy of this {@code Connections}
     */
    public Connections copy() {
        PersistenceAPI api = Persistence.get(new SerialConfig());
        byte[] myBytes = api.serializer().serialize(this);
        return api.serializer().deSerialize(myBytes);
    }
    
    /**
     * Sets the derived values of the {@link SpatialPooler}'s initialization.一是明确了synPermBelowStimulusInc参数的值其来源于synPermConnected默认连接阈值除以10
     * ，synPermTrimThreshold参数的值，来源于synPermActiveInc每一轮中一个活跃突触增加的持久度值除以2
     */
    public void doSpatialPoolerPostInit() {
        synPermBelowStimulusInc = synPermConnected / 10.0;///synPermConnected默认连接阈值，任何持久度的值高于这个连接阈值的突触都是“连接的突触”，意味这个突触可以促进细胞的激活。
        synPermTrimThreshold = synPermActiveInc / 2.0;/////synPermActiveInc每一轮中一个活跃突触增加的持久度值，指定为完全发育的突触的百分比
        if(potentialRadius[0] == -1) 
        {
            potentialRadius = inputDimensions;///直接为输入的维度
        }
    }
    
    /////////////////////////////////////////
    //         General Methods             //
    /////////////////////////////////////////
    /**
     * Sets the seed used for the internal random number generator.
     * If the generator has been instantiated, this method will initialize
     * a new random generator with the specified seed.
     *
     * @param seed
     */
    public void setSeed(int seed) {
        this.seed = seed;
    }

    /**
     * Returns the configured random number seed
     * @return
     */
    public int getSeed() {
        return seed;
    }

    /**
     * Returns the thread specific {@link Random} number generator.
     * @return
     */
    public Random getRandom() {
        return random;
    }

    /**
     * Sets the random number generator.
     * @param random
     */
    public void setRandom(Random random){
        this.random = random;
    }
    
    /**
     * Returns the {@link Cell} specified by the index passed in.
     * 返回传入的索引指定的单元
     * @param index     of the specified cell to return. 要返回的单元的索引
     * @return
     */
    public Cell getCell(int index) {
        return cells[index];
    }

    /**
     * Returns an array containing all of the {@link Cell}s.
     * 返回包括所有单元的数组
     * @return
     */
    public Cell[] getCells() {
        return cells;
    }

    /**
     * Sets the flat array of cells
     * 设置单元的平面数组
     * @param cells
     */
    public void setCells(Cell[] cells) {
        this.cells = cells;
    }

    /**
     * Returns an array containing the {@link Cell}s specified
     * by the passed in indexes.
     * 返回由传入的索引数组所指定的的单元的数组
     *
     * @param cellIndexes   indexes of the Cells to return
     * @return
     */
    public Cell[] getCells(int... cellIndexes) {
        Cell[] retVal = new Cell[cellIndexes.length];
        for(int i = 0;i < cellIndexes.length;i++) {
            retVal[i] = cells[cellIndexes[i]];
        }
        return retVal;
    }

    /**
     * Returns a {@link LinkedHashSet} containing the {@link Cell}s specified
     * by the passed in indexes. 返回一个包含由传入索引指定的单元序列的LinkedHashSet
     *
     * @param cellIndexes   indexes of the Cells to return
     * @return
     */
    public LinkedHashSet<Cell> getCellSet(int... cellIndexes) {
        LinkedHashSet<Cell> retVal = new LinkedHashSet<Cell>(cellIndexes.length);
        for(int i = 0;i < cellIndexes.length;i++) {
            retVal.add(cells[cellIndexes[i]]);
        }
        return retVal;
    }

    /**
     * Sets the matrix containing the {@link Column}s
     * 设置包含单元柱的列
     * @param mem
     */
    public void setMemory(SparseObjectMatrix<Column> mem) {
        this.memory = mem;
    }

    /**
     * Returns the matrix containing the {@link Column}s
     * 返回包含单元柱的列
     * @return
     */
    public SparseObjectMatrix<Column> getMemory() {
        return memory;
    }
    
    /**
     * Returns the {@link Topology} overseeing input 
     * neighborhoods.
     * 返回监视输入邻域的拓扑
     * @return 
     */
    public Topology getInputTopology() {
        return inputTopology;
    }
    
    /**
     * Sets the {@link Topology} overseeing input 
     * neighborhoods.
     * 设置监视输入邻域的拓扑
     * @param topology  the input Topology
     */
    public void setInputTopology(Topology topology) {
        this.inputTopology = topology;
    }
    
    /**
     * Returns the {@link Topology} overseeing {@link Column} 
     * neighborhoods.
     * 返回监视列邻域的拓扑
     * @return
     */
    public Topology getColumnTopology() {
        return columnTopology;
    }
    
    /**
     * Sets the {@link Topology} overseeing {@link Column} 
     * neighborhoods.
     * 设置监视列邻域的拓扑
     * @param topology  the column Topology
     */
    public void setColumnTopology(Topology topology) {
        this.columnTopology = topology;
    }

    /**
     * Returns the input column mapping
     * 返回输入列的匹配
     */
    public SparseMatrix<?> getInputMatrix() {
        return inputMatrix;
    }

    /**
     * Sets the input column mapping matrix
     * 设置输入列匹配矩阵
     * @param matrix
     */
    public void setInputMatrix(SparseMatrix<?> matrix) {
        this.inputMatrix = matrix;
    }

    ////////////////////////////////////////
    //       SpatialPooler Methods        //
    ////////////////////////////////////////
    /**
     * Returns the configured initial connected percent.
     * 返回配置的初始连接比例
     * @return
     */
    public double getInitConnectedPct() {
        return this.initConnectedPct;
    }

    /**
     * Returns the cycle count.
     * 返回循环计数
     * @return
     */
    public int getIterationNum() {
        return spIterationNum;
    }

    /**
     * Sets the iteration count.
     * 设置迭代次数
     * @param num
     */
    public void setIterationNum(int num) {
        this.spIterationNum = num;
    }

    /**
     * Returns the period count which is the number of cycles
     * between meta information updates.
     * 返回周期计数，即元信息更新之间的循环次数
     * @return
     */
    public int getUpdatePeriod() {
        return updatePeriod;
    }

    /**
     * Sets the update period
     * 设置更新周期
     * @param period
     */
    public void setUpdatePeriod(int period) {
        this.updatePeriod = period;
    }

    /**
     * Returns the inhibition radius
     * 返回抑制半径
     * @return
     */
    public int getInhibitionRadius() {
        return inhibitionRadius;
    }

    /**
     * Sets the inhibition radius
     * 设置抑制半径
     * @param radius
     */
    public void setInhibitionRadius(int radius) {
        this.inhibitionRadius = radius;
    }

    /**
     * Returns the product of the input dimensions
     * 返回输入数据维度的乘积
     * @return  the product of the input dimensions
     */
    public int getNumInputs() {
        return numInputs;
    }

    /**
     * Sets the product of the input dimensions to
     * establish a flat count of bits in the input field.
     * 设置输入维度的乘积，以在输入字段中建立位的单位计数
     * @param n
     */
    public void setNumInputs(int n) {
        this.numInputs = n;
    }

    /**
     * Returns the product of the column dimensions
     * 返回列的维度的乘积
     * @return  the product of the column dimensions
     */
    public int getNumColumns() {
        return numColumns;
    }

    /**
     * Sets the product of the column dimensions to be
     * the column count.
     * 设置列的维度的乘积，也就是列的数量
     * @param n
     */
    public void setNumColumns(int n) {
        this.numColumns = n;
    }

    /**
     * This parameter determines the extent of the input
     * that each column can potentially be connected to.
     * This can be thought of as the input bits that
     * are visible to each column, or a 'receptiveField' of
     * the field of vision. A large enough value will result
     * in 'global coverage', meaning that each column
     * can potentially be connected to every input bit. This
     * parameter defines a square (or hyper square) area: a
     * column will have a max square potential pool with
     * sides of length 2 * potentialRadius + 1.
     * 
     * <b>WARNING:</b> potentialRadius **must** be set to 
     * the inputWidth if using "globalInhibition" and if not 
     * using the Network API (which sets this automatically) 
     * 此参数定义了一个正方形（或超正方形）区域，一列将有一个最大正方形电位池，其边长为2*potentialRadius+1.
     *
     * @param potentialRadius
     */
    public void setPotentialRadius(int[] potentialRadius) {
        this.potentialRadius = potentialRadius;
    }

    /**
     * Returns the configured potential radius
     * 返回配置的潜在半径
     * @return  the configured potential radius
     * @see setPotentialRadius
     */
    public int[] getPotentialRadius() {
        return potentialRadius;
    }

    /**
     * The percent of the inputs, within a column's
     * potential radius, that a column can be connected to.
     * If set to 1, the column will be connected to every
     * input within its potential radius. This parameter is
     * used to give each column a unique potential pool when
     * a large potentialRadius causes overlap between the
     * columns. At initialization time we choose
     * ((2*potentialRadius + 1)^(# inputDimensions) *
     * potentialPct) input bits to comprise the column's
     * potential pool.
     * 在列的潜在半径内，列可以连接到的输入的百分比。如果设置为1，这个列将会连接到潜在输入
     * 半径内每个输入。这个参数被用来给每个列一个唯一的潜在池当一个大的潜在输入半径引起列之间的
     * 输入叠加的时候。在初始化的时候，我们选择（（2*potentialRadius+1)^({@link #inputDimensions})*potentialPct)个输入比特来组成列的潜在输入池
     * @param potentialPct
     */
    public void setPotentialPct(double potentialPct) {
        this.potentialPct = potentialPct;
    }

    /**
     * Returns the configured potential pct
     * 返回潜在输入百分比,使得空间池潜在输入有多大比例被选中
     * @return the configured potential pct
     * @see setPotentialPct
     */
    public double getPotentialPct() {
        return potentialPct;
    }

    /**
     * Sets the {@link SparseObjectMatrix} which represents the
     * proximal dendrite permanence values.
     * 设置SparseObjectMatrix这代表了近端树突的持久度值
     * @param s the {@link SparseObjectMatrix}
     */
    public void setProximalPermanences(SparseObjectMatrix<double[]> s) {
        for(int idx : s.getSparseIndices()) {
            memory.getObject(idx).setProximalPermanences(this, s.getObject(idx));
        }
    }

    /**
     * Returns the count of {@link Synapse}s on
     * {@link ProximalDendrite}s
     * 返回在近端树突上突触的数量
     * @return
     */
    public int getProximalSynapseCount() {
        return proximalSynapseCounter + 1;
    }
    
    /**
     * Sets the count of {@link Synapse}s on
     * {@link ProximalDendrite}s
     * 设置在近端树突上突触的数量
     * @param i
     */
    public void setProximalSynapseCount(int i) {
        this.proximalSynapseCounter = i;
    }
    
    /**
     * Increments and returns the incremented
     * proximal {@link Synapse} count.
     * 增加和返回增加后的近端突触的数量
     * @return
     */
    public int incrementProximalSynapses() {
        return ++proximalSynapseCounter;
    }

    /**
     * Decrements and returns the decremented
     * proximal {link Synapse} count
     * 减少和返回减少后的近端突触的数量
     * @return
     */
    public int decrementProximalSynapses() {
        return --proximalSynapseCounter;
    }
    
    /**
     * Returns the indexed count of connected synapses per column.
     * 返回每列连接的突触的索引计数
     * @return
     */
    public AbstractSparseBinaryMatrix getConnectedCounts() {
        return connectedCounts;
    }

    /**
     * Returns the connected count for the specified column.
     * 返回指定列的连接的（突触）的数量
     * @param columnIndex
     * @return
     */
    public int getConnectedCount(int columnIndex) {
        return connectedCounts.getTrueCount(columnIndex);
    }

    /**
     * Sets the indexed count of synapses connected at the columns in each index.
     * 设置在每个索引列中连接的突触的索引计数，这个counts是一个数组，应该是代表列中每个单元的连接的突触的数量
     * @param counts
     */
    public void setConnectedCounts(int[] counts) {
        for(int i = 0;i < counts.length;i++) {
            connectedCounts.setTrueCount(i, counts[i]);
        }
    }

    /**
     * Sets the connected count {@link AbstractSparseBinaryMatrix}
     * 设置连接的数量，是AbstractSparseBinaryMatrix形式的
     * @param columnIndex
     * @param count
     */
    public void setConnectedMatrix(AbstractSparseBinaryMatrix matrix) {
        this.connectedCounts = matrix;
    }

    /**
     * Sets the array holding the random noise added to proximal dendrite overlaps.
     * 设置容纳随机噪声的数组，这个噪声是添加到近端树突重叠值的
     * @param tieBreaker	random values to help break ties
     */
    public void setTieBreaker(double[] tieBreaker) {
        this.tieBreaker = tieBreaker;
    }

    /**
     * Returns the array holding random values used to add to overlap scores
     * to break ties.
     * 返回容纳随机值的数组，随机值被用来添加叠加值到破节中。
     * @return
     */
    public double[] getTieBreaker() {
        return tieBreaker;
    }

    /**
     * If true, then during inhibition phase the winning
     * columns are selected as the most active columns from
     * the region as a whole. Otherwise, the winning columns
     * are selected with respect to their local
     * neighborhoods. Using global inhibition boosts
     * performance x60.
     * 设置全局抑制
     * @param globalInhibition
     */
    public void setGlobalInhibition(boolean globalInhibition) {
        this.globalInhibition = globalInhibition;
    }

    /**
     * Returns the configured global inhibition flag
     * @return  the configured global inhibition flag
     *  返回配置的全局抑制标识
     * @see setGlobalInhibition
     */
    public boolean getGlobalInhibition() {
        return globalInhibition;
    }

    /**
     * The desired density of active columns within a local
     * inhibition area (the size of which is set by the
     * internally calculated inhibitionRadius, which is in
     * turn determined from the average size of the
     * connected potential pools of all columns). The
     * inhibition logic will insure that at most N columns
     * remain ON within a local inhibition area, where N =
     * localAreaDensity * (total number of columns in
     * inhibition area).
     * 设置局地面积密度
     * @param localAreaDensity
     */
    public void setLocalAreaDensity(double localAreaDensity) {
        this.localAreaDensity = localAreaDensity;
    }

    /**
     * Returns the configured local area density
     * @return  the configured local area density
     * 返回配置的局地面积密度
     * @see setLocalAreaDensity
     */
    public double getLocalAreaDensity() {
        return localAreaDensity;
    }

    /**
     * An alternate way to control the density of the active
     * columns. If numActivePerInhArea is specified then
     * localAreaDensity must be less than 0, and vice versa.
     * When using numActivePerInhArea, the inhibition logic
     * will insure that at most 'numActivePerInhArea'
     * columns remain ON within a local inhibition area (the
     * size of which is set by the internally calculated
     * inhibitionRadius, which is in turn determined from
     * the average size of the connected receptive fields of
     * all columns). When using this method, as columns
     * learn and grow their effective receptive fields, the
     * inhibitionRadius will grow, and hence the net density
     * of the active columns will *decrease*. This is in
     * contrast to the localAreaDensity method, which keeps
     * the density of active columns the same regardless of
     * the size of their receptive fields.
     * 在抑制区激活的列的最大数量
     * @param numActiveColumnsPerInhArea
     */
    public void setNumActiveColumnsPerInhArea(double numActiveColumnsPerInhArea) {
        this.numActiveColumnsPerInhArea = numActiveColumnsPerInhArea;
    }

    /**
     * Returns the configured number of active columns per
     * inhibition area.
     * 返回抑制区内激活的单元柱的最大数量
     * @return  the configured number of active columns per
     * inhibition area.
     * @see setNumActiveColumnsPerInhArea
     */
    public double getNumActiveColumnsPerInhArea() {
        return numActiveColumnsPerInhArea;
    }

    /**
     * This is a number specifying the minimum number of
     * synapses that must be on in order for a columns to
     * turn ON. The purpose of this is to prevent noise
     * input from activating columns. Specified as a percent
     * of a fully grown synapse.
     * 指定为使一个单元柱激活，必须处于活跃状态的突触的最小数量。这样做的目的是防止噪声输入激活列，以完全发育的突触的百分比表示
     * @param stimulusThreshold
     */
    public void setStimulusThreshold(double stimulusThreshold) {
        this.stimulusThreshold = stimulusThreshold;
    }

    /**
     * Returns the stimulus threshold
     * 返回激活阈值
     * @return  the stimulus threshold
     * @see setStimulusThreshold
     */
    public double getStimulusThreshold() {
        return stimulusThreshold;
    }

    /**
     * The amount by which an inactive synapse is
     * decremented in each round. Specified as a percent of
     * a fully grown synapse.
     * 一个未被激活的突触在每一轮中持久度值减少的量。以完全发育的突触的百分比表示
     * @param synPermInactiveDec
     */
    public void setSynPermInactiveDec(double synPermInactiveDec) {
        this.synPermInactiveDec = synPermInactiveDec;
    }

    /**
     * Returns the synaptic permanence inactive decrement.
     * 返回未激活突触的减少的持久度值
     * @return  the synaptic permanence inactive decrement.
     * @see setSynPermInactiveDec
     */
    public double getSynPermInactiveDec() {
        return synPermInactiveDec;
    }

    /**
     * The amount by which an active synapse is incremented
     * in each round. Specified as a percent of a
     * fully grown synapse.
     * 一个激活的突触在每一轮增加的值，由完全发育的突触的百分比表示
     * @param synPermActiveInc
     */
    public void setSynPermActiveInc(double synPermActiveInc) {
        this.synPermActiveInc = synPermActiveInc;
    }

    /**
     * Returns the configured active permanence increment
     * 返回配置的激活突触的持久度增加值
     * @return the configured active permanence increment
     * @see setSynPermActiveInc
     */
    public double getSynPermActiveInc() {
        return synPermActiveInc;
    }

    /**
     * The default connected threshold. Any synapse whose
     * permanence value is above the connected threshold is
     * a "connected synapse", meaning it can contribute to
     * the cell's firing.
     * 默认的连接阈值。任何突触的持久度值大于这个连接阈值就成为一个连接突触，意味着它能对单元的激活做出贡献
     * @param synPermConnected
     */
    public void setSynPermConnected(double synPermConnected) {
        this.synPermConnected = synPermConnected;
    }

    /**
     * Returns the synapse permanence connected threshold
     * 返回突触持久度连接阈值
     * @return the synapse permanence connected threshold
     * @see setSynPermConnected
     */
    public double getSynPermConnected() {
        return synPermConnected;
    }

    /**
     * Sets the stimulus increment for synapse permanences below
     * the measured threshold.
     * 对于突触的持久度值低于测量的阈值，设置其刺激增量
     * @param stim
     */
    public void setSynPermBelowStimulusInc(double stim) {
        this.synPermBelowStimulusInc = stim;
    }

    /**
     * Returns the stimulus increment for synapse permanences below
     * the measured threshold.
     * 对于突触的持久度值低于测量的阈值，返回其刺激增量
     * @return
     */
    public double getSynPermBelowStimulusInc() {
        return synPermBelowStimulusInc;
    }

    /**
     * A number between 0 and 1.0, used to set a floor on
     * how often a column should have at least
     * stimulusThreshold active inputs. Periodically, each
     * column looks at the overlap duty cycle of
     * all other columns within its inhibition radius and
     * sets its own internal minimal acceptable duty cycle
     * to: minPctDutyCycleBeforeInh * max(other columns'
     * duty cycles).
     * On each iteration, any column whose overlap duty
     * cycle falls below this computed value will  get
     * all of its permanence values boosted up by
     * synPermActiveInc. Raising all permanences in response
     * to a sub-par duty cycle before  inhibition allows a
     * cell to search for new inputs when either its
     * previously learned inputs are no longer ever active,
     * or when the vast majority of them have been
     * "hijacked" by other columns.
     * 一个0到1之间的值，用来设定一个最低值，来表达一个列应该至少有stimulusThreshold个激活输入的频率。
     * 周期新的，每个列周期性的查看在其抑制半径的所有别的列的重叠占空比，并且设置他们内部的最小可接受占空比
     * 为minPctDutyCycleBeforeInh * max(别的列的占空比)。在每一次迭代，任何一列的重叠占空比小于这个计算的
     * 值将会使得这个列的所有持久度值增加synPermActiveInc。在抑制之前提高所有的突触的持久度以响应低于标准的占空比。
     * 使得一个单元搜索新的输入，当它之前学习的输入不再活跃，或者它们中的大部分被别的列劫持了。
     * @param minPctOverlapDutyCycle
     */
    public void setMinPctOverlapDutyCycles(double minPctOverlapDutyCycle) {
        this.minPctOverlapDutyCycles = minPctOverlapDutyCycle;
    }

    /**
     * see {@link #setMinPctOverlapDutyCycles(double)}
     * 返回一个列应该至少有stimulusThreshold个激活输入的频率
     * @return
     */
    public double getMinPctOverlapDutyCycles() {
        return minPctOverlapDutyCycles;
    }

    /**
     * A number between 0 and 1.0, used to set a floor on
     * how often a column should be activate.
     * Periodically, each column looks at the activity duty
     * cycle of all other columns within its inhibition
     * radius and sets its own internal minimal acceptable
     * duty cycle to:
     *   minPctDutyCycleAfterInh *
     *   max(other columns' duty cycles).
     * On each iteration, any column whose duty cycle after
     * inhibition falls below this computed value will get
     * its internal boost factor increased.
     * 一个0到1之间的值，用来设置一个列应该被激活的最小频率。每个列周期性的检查它的抑制区
     * 里面别的活动占空比，并且设置它内部的最低可接受占空比为 minPctDutyCycleAfterInh *max(other columns' duty cycles)
     * 在每一个迭代，任何占空比在抑制后低于这个值的列将会使得它的所有突触的持久度值增加。
     * @param minPctActiveDutyCycle
     */
    public void setMinPctActiveDutyCycles(double minPctActiveDutyCycle) {
        this.minPctActiveDutyCycles = minPctActiveDutyCycle;
    }

    /**
     * Returns the minPctActiveDutyCycle
     * 返回列应该被激活的最小频率
     * see {@link #setMinPctActiveDutyCycles(double)}
     * @return  the minPctActiveDutyCycle
     */
    public double getMinPctActiveDutyCycles() {
        return minPctActiveDutyCycles;
    }

    /**
     * The period used to calculate duty cycles. Higher
     * values make it take longer to respond to changes in
     * boost or synPerConnectedCell. Shorter values make it
     * more unstable and likely to oscillate.
     * 用于计算占空比的周期。高的值使得响应在boost或synPerConnectedCell中的变化花费更多的时间。
     * 小的值使得它不稳定和易于震荡
     * @param dutyCyclePeriod
     */
    public void setDutyCyclePeriod(int dutyCyclePeriod) {
        this.dutyCyclePeriod = dutyCyclePeriod;
    }

    /**
     * Returns the configured duty cycle period
     * see {@link #setDutyCyclePeriod(double)}
     * 返回配置的占空比周期
     * @return  the configured duty cycle period
     */
    public int getDutyCyclePeriod() {
        return dutyCyclePeriod;
    }

    /**
     * The maximum overlap boost factor. Each column's
     * overlap gets multiplied by a boost factor
     * before it gets considered for inhibition.
     * The actual boost factor for a column is number
     * between 1.0 and maxBoost. A boost factor of 1.0 is
     * used if the duty cycle is &gt;= minOverlapDutyCycle,
     * maxBoost is used if the duty cycle is 0, and any duty
     * cycle in between is linearly extrapolated from these
     * 2 end points.
     * 最大的重叠增加因子。每一列的重叠值都由一个增加因子相乘，在考虑抑制之前。列的实际的增加因子
     * 是一个1到maxBoost之间的数。增加因子是1如果占空比大于等于minOverlapDutyCycle，最大增加因子被使用当占空比为0，任何
     * 占空比在二者之间可以线性的推测增加应在在这两个值之间。
     * @param maxBoost
     */
    public void setMaxBoost(double maxBoost) {
        this.maxBoost = maxBoost;
    }

    /**
     * Returns the max boost
     * 返回max boost
     * see {@link #setMaxBoost(double)}
     * @return  the max boost
     */
    public double getMaxBoost() {
        return maxBoost;
    }
    
    /**
     * Specifies whether neighborhoods wider than the 
     * borders wrap around to the other side.
     * 当邻域比边界宽的时候，设置邻域是否绕到另外一端
     * @param b
     */
    public void setWrapAround(boolean b) {
        this.wrapAround = b;
    }
    
    /**
     * Returns a flag indicating whether neighborhoods
     * wider than the borders, wrap around to the other
     * side.
     * 返回当领域比边界宽的时候，领域是否绕到另外一端
     * @return
     */
    public boolean isWrapAround() {
        return wrapAround;
    }
    
    /**
     * Sets and Returns the boosted overlap score for each column
     *  设置为每一列增加的叠加分数
     * @param boostedOverlaps
     * @return
     */
    public double[] setBoostedOverlaps(double[] boostedOverlaps) {
        return this.boostedOverlaps = boostedOverlaps;
    }
   
    /**
     * Returns the boosted overlap score for each column
     * 返回为每一列增加的叠加的分数
     * @return the boosted overlaps
     */
    public double[] getBoostedOverlaps() {
        return boostedOverlaps;
    }
    
    /**
     * Sets and Returns the overlap score for each column
     * 设置每一列重叠的分数
     * @param overlaps
     * @return
     */
    public int[] setOverlaps(int[] overlaps) {
        return this.overlaps = overlaps;
    }
   
    /**
     * Returns the overlap score for each column
     * 返回每一列的重叠分数
     * @return the overlaps
     */
    public int[] getOverlaps() {
        return overlaps;
    }

    /**
     * Sets the synPermTrimThreshold
     * @param threshold
     */
    public void setSynPermTrimThreshold(double threshold) {
        this.synPermTrimThreshold = threshold;
    }

    /**
     * Returns the synPermTrimThreshold
     * @return
     */
    public double getSynPermTrimThreshold() {
        return synPermTrimThreshold;
    }

    /**
     * Sets the {@link FlatMatrix} which holds the mapping
     * of column indexes to their lists of potential inputs.
     * 设置FlatMatrix，它保存列索引到潜在输入列表的映射。
     * @param pools		{@link FlatMatrix} which holds the pools.
     */
    public void setPotentialPools(FlatMatrix<Pool>   pools) {
        this.potentialPools = pools;
    }

    /**
     * Returns the {@link FlatMatrix} which holds the mapping
     * of column indexes to their lists of potential inputs.
     * 返回FlatMatrix，它保存着列索引到潜在输入列表的映射
     * @return	the potential pools
     */
    public FlatMatrix<Pool> getPotentialPools() {
        return this.potentialPools;//相当于每个列有一个池，存储了所有列的pool
    }

    /**
     * Returns the minimum {@link Synapse} permanence.
     * 返回最小的突触的持久度值
     * @return
     */
    public double getSynPermMin() {
        return synPermMin;
    }

    /**
     * Returns the maximum {@link Synapse} permanence.
     * 返回最大的突触持久度值
     * @return
     */
    public double getSynPermMax() {
        return synPermMax;
    }

    /**
     * Returns the version number
     * @return
     */
    public double getVersion() {
        return version;
    }

    /**
     * Returns the overlap duty cycles.
     * 返回重叠占空比。获取列的重叠近期活性值
     * @return
     */
    public double[] getOverlapDutyCycles() {
        return overlapDutyCycles;
    }

    /**
     * Sets the overlap duty cycles
     * 设置重叠占空比
     * @param overlapDutyCycles
     */
    public void setOverlapDutyCycles(double[] overlapDutyCycles) {
        this.overlapDutyCycles = overlapDutyCycles;
    }

    /**
     * Returns the dense (size=numColumns) array of duty cycle stats.
     * 返回占空比统计信息的密度数组（size=numColumns)，获取激活单元的近期活性
     * @return	the dense array of active duty cycle values.
     */
    public double[] getActiveDutyCycles() {
        return activeDutyCycles;
    }

    /**
     * Sets the dense (size=numColumns) array of duty cycle stats.
     * 设置占空比统计信息的密度数组（size=numColumns)
     * @param activeDutyCycles
     */
    public void setActiveDutyCycles(double[] activeDutyCycles) {
        this.activeDutyCycles = activeDutyCycles;
    }

    /**
     * Applies the dense array values which aren't -1 to the array containing
     * the active duty cycles of the column corresponding to the index specified.
     * The length of the specified array must be as long as the configured number
     * of columns of this {@code OldConnections}' column configuration.
     * 把值不是-1的密度数组应用于包含激活占空比的且与指定的索引相对应的数组，指定数组的长度必须与此oldConnection列配置的配置列数相同。
     * @param	denseActiveDutyCycles	a dense array containing values to set.
     */
    public void updateActiveDutyCycles(double[] denseActiveDutyCycles) {
        for(int i = 0;i < denseActiveDutyCycles.length;i++) {
            if(denseActiveDutyCycles[i] != -1) {
                activeDutyCycles[i] = denseActiveDutyCycles[i];
            }
        }
    }

    /**
     * Returns the minOverlapDutyCycles.
     * @return	the minOverlapDutyCycles.
     */
    public double[] getMinOverlapDutyCycles() {
        return minOverlapDutyCycles;
    }

    /**
     * Sets the minOverlapDutyCycles
     * @param minOverlapDutyCycles	the minOverlapDutyCycles
     */
    public void setMinOverlapDutyCycles(double[] minOverlapDutyCycles) {
        this.minOverlapDutyCycles = minOverlapDutyCycles;
    }

    /**
     * Returns the minActiveDutyCycles
     * @return	the minActiveDutyCycles
     */
    public double[] getMinActiveDutyCycles() {
        return minActiveDutyCycles;
    }

    /**
     * Sets the minActiveDutyCycles
     * @param minActiveDutyCycles	the minActiveDutyCycles
     */
    public void setMinActiveDutyCycles(double[] minActiveDutyCycles) {
        this.minActiveDutyCycles = minActiveDutyCycles;
    }

    /**
     * Returns the array of boost factors
     * 返回boost factors数组
     * @return	the array of boost factors
     */
    public double[] getBoostFactors() {
        return boostFactors;
    }

    /**
     * Sets the array of boost factors
     * 设置boost factors数组
     * @param boostFactors	the array of boost factors
     */
    public void setBoostFactors(double[] boostFactors) {
        this.boostFactors = boostFactors;
    }
    
    
	////////////////////////////////////////
	//       TemporalMemory Methods       //
	////////////////////////////////////////
    
    /**
     * Return type from {@link Connections#computeActivity(Set, double, int, double, int, boolean)}
     * 存储所有树突潜在突触的数量和连接突触的数量的类
     */
    public static class Activity implements Serializable {
    	/** default serial */
        private static final long serialVersionUID = 1L;
        
        public int[] numActiveConnected;
        public int[] numActivePotential;
        
        public Activity(int[] numConnected, int[] numPotential) {
            this.numActiveConnected = numConnected;
            this.numActivePotential = numPotential;
        }
    }
    
    /**
     * Compute each segment's number of active synapses for a given input.
     * In the returned lists, a segment's active synapse count is stored at index
     * `segment.flatIdx`.
     * 对于一个给定的输入，计算每一个部分激活突触的数量。在返回的列表中，一个部分激活突触的数量存储在索引“segment.flatIdx”
     * 这个函数计算了所有树突的连接突触的数量和潜在突触的数量
     * @param activePresynapticCells
     * @param connectedPermanence
     * @return
     */
    public Activity computeActivity(Collection<Cell> activePresynapticCells, double connectedPermanence) {
        int[] numActiveConnectedSynapsesForSegment = new int[nextFlatIdx];//nextFlatIdx基本上指树突的总数，这里为每一个树突建立一个统计其连接突触数量的数组
        int[] numActivePotentialSynapsesForSegment = new int[nextFlatIdx];//nextFlatIdx基本上指树突的总数，这里为每一个树突建立一个统计其潜在突触数量的数组
        
        double threshold = connectedPermanence - EPSILON;//阈值
        
        for(Cell cell : activePresynapticCells)//对于每一个给定的激活的单元
        {
            for(Synapse synapse : getReceptorSynapses(cell)) //获取以这个单元为突触前单元的突出列表，那么这个激活的神经元会通过突触向连接神经元传导激活性
            {
                int flatIdx = synapse.getSegment().getIndex();//获取这个突触对应的树突的索引
                ++numActivePotentialSynapsesForSegment[flatIdx];//为树突索引所代表的树突的潜在突触数量+1，对于每一个激活的单元，如果有其他几个树突连接到这个单元，那么每个树突的潜在连接突触的数量增加1，这里实际上记录的是每个树突潜在输入的刺激数量
                if(synapse.getPermanence() > threshold) {
                    ++numActiveConnectedSynapsesForSegment[flatIdx];//对于突触的持久度值大于阈值的突触，为树突索引所代表的树突的连接突触数量+1
                }
            }
        }
        
    	return lastActivity = new Activity(
    	    numActiveConnectedSynapsesForSegment, 
    	        numActivePotentialSynapsesForSegment);
    }
    
    /**
     * Returns the last {@link Activity} computed during the most
     * recently executed cycle.
     * 返回最新的Activity，在最近执行的周期中计算的
     * @return  the last activity to be computed.
     */
    public Activity getLastActivity() {
        return lastActivity;
    }
    
    /**
     * Record the fact that a segment had some activity. This information is
     * used during segment cleanup.
     * 记录一个基底树突最后使用的迭代标识
     * @param segment		the segment for which to record activity
     */
    public void recordSegmentActivity(DistalDendrite segment) {
    	segment.setLastUsedIteration(tmIteration);
    }
    
    /**
     * Mark the passage of time. This information is used during segment
     * cleanup.
     * 标记过去的时间，这个信息通常在片段（基底树突）清理的时候使用
     */
    public void startNewIteration() {
    	++tmIteration;
    }
    
    
	/////////////////////////////////////////////////////////////////
	//     Segment (Specifically, Distal Dendrite) Operations      //
	/////////////////////////////////////////////////////////////////
    
    /**
     * Adds a new {@link DistalDendrite} segment on the specified {@link Cell},
     * or reuses an existing one.
     *  添加一个新的基底树突片段到指定的单元上，或者重用现有的基底树突
     * @param cell  the Cell to which a segment is added. 拟添加树突的单元
     * @return  the newly created segment or a reused segment 返回新添加或者重用的树突
     */
    public DistalDendrite createSegment(Cell cell) {
    	while(numSegments(cell) >= maxSegmentsPerCell)///首先判断指定的单元有的树突的数量是否超过最大树突数量阈值，如果大于数量阈值，则删除一部分
    	{
            destroySegment(leastRecentlyUsedSegment(cell));//删除最新使用的树突
        }
    	
    	int flatIdx;//树突的全局编号
    	int len;
    	if((len = freeFlatIdxs.size()) > 0) //如果要删除的树突的数量大于1
    	{
    		flatIdx = freeFlatIdxs.get(len - 1);//获取最后要删除的一个树突的id
    		freeFlatIdxs.remove(len - 1, 1);//把最后一个树突删除
    	}
    	else//如果要删除的树突的数量等于0
    	{
    		flatIdx = nextFlatIdx;//获取全部的树突的数量，作为下一个树突的ID
    		segmentForFlatIdx.add(null);//把空的树突添加到树突的存储容器中去
    		++nextFlatIdx;//下一个树突的全局ID号增加一个
    	}
    	
    	int ordinal = nextSegmentOrdinal;//每个基底树突创建的全局增长计数器
    	++nextSegmentOrdinal;//增加一个基底树突的全局计数器
    	
    	DistalDendrite segment = new DistalDendrite(cell, flatIdx, tmIteration, ordinal);//创建一个新的基底树突，cell指这个树突所属的单元，flatIdx指的是这个树突的全局编号，tmIteration标记的时间，ordinal标记的是这个树突的创建先后
    	getSegments(cell, true).add(segment);//把这个树突添加至单元的树突集合
    	segmentForFlatIdx.set(flatIdx, segment);//在树突的存储容器中设置上这个新添加的树突
    	
    	return segment;
    }
    
    /**
     * Destroys a segment ({@link DistalDendrite})
     * @param segment   the segment to destroy
     */
    public void destroySegment(DistalDendrite segment) {
    	// Remove the synapses from all data structures outside this Segment.
    	List<Synapse> synapses = getSynapses(segment);//获取拟销毁的树突的所有突触
    	int len = synapses.size();
    	getSynapses(segment).stream().forEach(s -> removeSynapseFromPresynapticMap(s));//从突触前单元激活的突触图谱移除指定的突触
    	numSynapses -= len;//突触的总数减少
    	
    	// Remove the segment from the cell's list.从单元的树突列表中删除这个树突
    	getSegments(segment.getParentCell()).remove(segment);
    	
    	// Remove the segment from the map，从树突到突触的匹配列表中删除这个树突
    	distalSynapses.remove(segment);
    	
    	// Free the flatIdx and remove the final reference so the Segment can be
        // garbage-collected.示范flatIdx并删除最终引用，这样就可以对这个树突进行垃圾回收
    	freeFlatIdxs.add(segment.getIndex());//在拟销毁的数组中登记这个树突的索引
    	segmentForFlatIdx.set(segment.getIndex(), null);///把这个树突对应的索引设置为空
    }
    
    /**
     * Used internally to return the least recently activated segment on 
     * the specified cell
     * 用于返回指定单元上最近激活最少的树突
     * @param cell  cell to search for segments on 拟寻找最近使用的树突的单元
     * @return  the least recently activated segment on  最近使用的树突
     *          the specified cell
     */
    private DistalDendrite leastRecentlyUsedSegment(Cell cell) {
        List<DistalDendrite> segments = getSegments(cell, false);//获取这个单元所有的基底树突
        DistalDendrite minSegment = null;//最近激活最少的基底树突
        long minIteration = Long.MAX_VALUE;//最小的迭代次数被设置为整型数的最大值
        
        for(DistalDendrite dd : segments)//对于每个树突
        {
            if(dd.lastUsedIteration() < minIteration)//如果这个树突的最后被使用的时间，小于最小的时间
            {
            	minSegment = dd;//把这个树突设置为最近使用的树突
                minIteration = dd.lastUsedIteration();//把使用的时间最为最近使用的时间
            }
        }
        
        return minSegment;
    }
    
    /**
     * Returns the total number of {@link DistalDendrite}s
     * 返回基底树突的总数
     * @return  the total number of segments
     */
    public int numSegments() {
        return numSegments(null);
    }
    
    /**
     * Returns the number of {@link DistalDendrite}s on a given {@link Cell}
     * if specified, or the total number if the "optionalCellArg" is null.
     * 返回一个给定单元上基底树突的数量（如果被指定），或者树突的总数，如果optionalCellArg为null的情况下
     * @param optionalCellArg   an optional Cell to specify the context of the segment count.一个可选的单元用来指定片段数量的上下文
     * @return  either the total number of segments or the number on a specified cell.//部分的总数或者指定单元上的树突的总数
     */
    public int numSegments(Cell optionalCellArg) {
        if(optionalCellArg != null) {
            return getSegments(optionalCellArg).size();
        }
        
        return nextFlatIdx - freeFlatIdxs.size();//nextFlatIdx基本上是指突触的总数
    }
    
    /**
     * Returns the mapping of {@link Cell}s to their {@link DistalDendrite}s.
     * 返回指定单元的基地树突的列表
     * @param cell      the {@link Cell} used as a key.
     * @return          the mapping of {@link Cell}s to their {@link DistalDendrite}s.
     */
    public List<DistalDendrite> getSegments(Cell cell) {
        return getSegments(cell, false);
    }

    /**
     * Returns the mapping of {@link Cell}s to their {@link DistalDendrite}s.
     * 返回单元到其基底树突的映射，也就是获取单元的基底树突集合
     * @param cell              the {@link Cell} used as a key. 单元作为一个key
     * @param doLazyCreate      create a container for future use if true, if false
     *                          return an orphaned empty set.如果为true,则创建容器以供将来使用；
     *                          如果为false,则返回孤立的空集。
     * @return          the mapping of {@link Cell}s to their {@link DistalDendrite}s. 单元的树突集合
     */
    public List<DistalDendrite> getSegments(Cell cell, boolean doLazyCreate) {
        if(cell == null) {
            throw new IllegalArgumentException("Cell was null");
        }

        if(segments == null) {
            segments = new LinkedHashMap<Cell, List<DistalDendrite>>();//如果单元的树突集合为空，那么创建一个树突集合（LinkedHashMap）
        }

        List<DistalDendrite> retVal = null;
        if((retVal = segments.get(cell)) == null) //如果指定单元的树突的集合为空
        {
            if(!doLazyCreate) return Collections.emptyList();
            segments.put(cell, retVal = new ArrayList<DistalDendrite>());//创建一个树突集合，并加入所有细胞的基底树突集合之中
        }

        return retVal;
    }
    
    /**
     * Get the segment with the specified flatIdx.
     * 用指定的flatIdx获取对应的基底树突
     * @param index		The segment's flattened list index. 树突的全局列表索引
     * @return	the {@link DistalDendrite} who's index matches. 索引所匹配的树突
     */
    public DistalDendrite segmentForFlatIdx(int index) {
    	return segmentForFlatIdx.get(index);
    }
    
    /**
     * Returns the index of the {@link Column} owning the cell which owns 
     * the specified segment.
     *  返回拥有指定树突的单元的列的索引，也就是通过树突获取列的索引
     * @param segment   the {@link DistalDendrite} of the cell whose column index is desired. 期望的列的索引的单元的树突
     * @return  the owning column's index 列的索引
     */
    public int columnIndexForSegment(DistalDendrite segment) {
        return segment.getParentCell().getIndex() / cellsPerColumn;//计算方法为单元的索引号整除每列拥有的单元数
    }
    
    /**
     * <b>FOR TEST USE ONLY</b>
     * @return
     */
    public Map<Cell, List<DistalDendrite>> getSegmentMapping() {
        return new LinkedHashMap<>(segments);
    }
    
    /**
     * Set by the {@link TemporalMemory} following a compute cycle.
     * 一个计算周期后，由TemporalMemory设置的激活树突集合
     * @param l
     */
    public void setActiveSegments(List<DistalDendrite> l) {
        this.activeSegments = l;
    }
    
    /**
     * Retrieved by the {@link TemporalMemorty} prior to a compute cycle.
     * 在计算周期之前由TemporalMemory检索，获取激活的树突集合
     * @return
     */
    public List<DistalDendrite> getActiveSegments() {
        return activeSegments;
    }
    
    /**
     * Set by the {@link TemporalMemory} following a compute cycle.
     * 在计算周期后，由TemporalMemorty设置，设置匹配的树突集合
     * @param l
     */
    public void setMatchingSegments(List<DistalDendrite> l) {
        this.matchingSegments = l;
    }
    
    /**
     * Retrieved by the {@link TemporalMemorty} prior to a compute cycle.
     * 一个计算周期之前，由TemporalMemory检索
     * @return
     */
    public List<DistalDendrite> getMatchingSegments() {
        return matchingSegments;
    }
    
    
    /////////////////////////////////////////////////////////////////
    //                    Synapse Operations                       //
    /////////////////////////////////////////////////////////////////
    
    /**
     * Creates a new synapse on a segment.
     * 在一个树突上产生新的突触
     * @param segment               the {@link DistalDendrite} segment to which a {@link Synapse} is 将产生新的突触的树突
     *                              being created
     * @param presynapticCell       the source {@link Cell} 突触前单元
     * @param permanence            the initial permanence 初始的持久度值
     * @return  the created {@link Synapse} 产生的突触
     */
    public Synapse createSynapse(DistalDendrite segment, Cell presynapticCell, double permanence) {
        while(numSynapses(segment) >= maxSynapsesPerSegment)//如果这个树突上的突触已经大于最大拥有量，
        {
            destroySynapse(minPermanenceSynapse(segment));//则把持久度值最小的突触删除掉
        }
        
        Synapse synapse = null;
	    getSynapses(segment).add(
	        synapse = new Synapse(
	            presynapticCell, segment, nextSynapseOrdinal, permanence));//新建一个突触，并添加到这个树突的突触集合列表里面，请注意所有的突触都是在这个位置添加的
	    
        getReceptorSynapses(presynapticCell, true).add(synapse);//返回突触前单元的连接突触列表，并把新建的突触添加进去
        
        ++nextSynapseOrdinal;//突触的创建标记
        
        ++numSynapses;///突触的总数标记
        
        return synapse;
    }
    
    /**
     * Destroys the specified {@link Synapse}
     * 删除指定的突触
     * @param synapse   the Synapse to destroy 拟被删除的突触
     */
    public void destroySynapse(Synapse synapse) {
        --numSynapses;
        
        removeSynapseFromPresynapticMap(synapse);
        
        getSynapses((DistalDendrite)synapse.getSegment()).remove(synapse);
    }
    
    /**
     * Removes the specified {@link Synapse} from its
     * pre-synaptic {@link Cell}'s map of synapses it 
     * activates.
     * 从突触前单元激活的突触图谱移除指定的突触
     * @param synapse   the synapse to remove 将要移除的突触
     */
    public void removeSynapseFromPresynapticMap(Synapse synapse) {
    	Set<Synapse> presynapticSynapses;
        Cell cell = synapse.getPresynapticCell();//获取突触前单元
        (presynapticSynapses = getReceptorSynapses(cell, false)).remove(synapse);//突触前单元的突触集合中移除指定的突触
        
        if(presynapticSynapses.isEmpty()) //如果突触前的单元的突触集合为空了，那么其从源细胞到突触的反向映射也就不存在了，在相应的数组中删除此记录
        {
            receptorSynapses.remove(cell);
        }
    }
    
    /**
     * Used internally to find the synapse with the smallest permanence
     * on the given segment.
     *  在一个给定的树突上，找到具有最小持久度值的突触
     * @param dd    Segment object to search for synapses on 用来搜索突触的树突
     * @return  Synapse object on the segment with the minimal permanence 在树突山具有最小持久度值的突触
     */
    private Synapse minPermanenceSynapse(DistalDendrite dd)
    {
        List<Synapse> synapses = getSynapses(dd).stream().sorted().collect(Collectors.toList());//获取这个树突的突触列表
        Synapse min = null;
        double minPermanence = Double.MAX_VALUE;
        
        for(Synapse synapse : synapses) {
            if(!synapse.destroyed() && synapse.getPermanence() < minPermanence - EPSILON) {
                min = synapse;
                minPermanence = synapse.getPermanence();
            }
        }
        
        return min;//返回持久度值最小的突触
    }
    
    /**
     * Returns the total number of {@link Synapse}s
     * 返回突触的总数
     * @return  either the total number of synapses
     */
    public long numSynapses() {
        return numSynapses(null);
    }
    
    /**
     * Returns the number of {@link Synapse}s on a given {@link DistalDendrite}
     * if specified, or the total number if the "optionalSegmentArg" is null.
     * 返回一个给定树突的突触的总数
     * @param optionalSegmentArg    an optional Segment to specify the context of the synapse count.
     * @return  either the total number of synapses or the number on a specified segment.
     */
    public long numSynapses(DistalDendrite optionalSegmentArg) {
        if(optionalSegmentArg != null) {
            return getSynapses(optionalSegmentArg).size();//给定树突的突触的总数
        }
        
        return numSynapses;
    }
    
    /**
     * Returns the mapping of {@link Cell}s to their reverse mapped
     * {@link Synapse}s.
     * 返回指定单元的感受野突触集合
     * @param cell      the {@link Cell} used as a key. 指定的单元 
     * @return          the mapping of {@link Cell}s to their reverse mapped 
     *                  {@link Synapse}s. 指定单元反向映射的突触的结合
     */
    public Set<Synapse> getReceptorSynapses(Cell cell) {
        return getReceptorSynapses(cell, false);
    }

    /**
     * Returns the mapping of {@link Cell}s to their reverse mapped
     * {@link Synapse}s.
     * 返回细胞到反向映射突触的映射，也就是返回指定单元的感受野突触集合
     * @param cell              the {@link Cell} used as a key. 细胞作为键值
     * @param doLazyCreate      create a container for future use if true, if false
     *                          return an orphaned empty set.产生一个容器为未来使用，如果设置为true,
     *                          如果为false,返回孤立的空集
     * @return          the mapping of {@link Cell}s to their reverse mapped
     *                  {@link Synapse}s.
     */
    public Set<Synapse> getReceptorSynapses(Cell cell, boolean doLazyCreate) {
        if(cell == null) {
            throw new IllegalArgumentException("Cell was null");
        }

        if(receptorSynapses == null) //如果 receptorSynapses (Reverse mapping from source cell to Synapse从源细胞到突触的反向映射 )为空，则新建一个对象
        {
            receptorSynapses = new LinkedHashMap<>();//新建一个LinkedHashMap用来存储感受野突触
        }

        LinkedHashSet<Synapse> retVal = null;
        if((retVal = receptorSynapses.get(cell)) == null)//如果 receptorSynapses不为空的话，获取指定单元的感受野突触集合，肯定在另外一个地方初始化了receptorSynapses
        {
            if(!doLazyCreate) return Collections.emptySet();
            receptorSynapses.put(cell, retVal = new LinkedHashSet<>());//如果为空的话，则新建一个LinkedHashSet对象，赋值到receptorSynapses对象
        }

        return retVal;//返回指定单元的感受野突触集合
    }
    
    /**
     * Returns the mapping of {@link DistalDendrite}s to their {@link Synapse}s.
     * 返回基底树突到它拥有的突触集合的映射，即返回基底树突的突触集合
     * @param segment   the {@link DistalDendrite} used as a key.
     * @return          the mapping of {@link DistalDendrite}s to their {@link Synapse}s.
     */
    public List<Synapse> getSynapses(DistalDendrite segment) {
        if(segment == null) {
            throw new IllegalArgumentException("Segment was null");
        }

        if(distalSynapses == null) {
            distalSynapses = new LinkedHashMap<Segment, List<Synapse>>();///新建一个LinkedHashMap对象，以树突为key,以树突的突触集合为值
        }

        List<Synapse> retVal = null;
        if((retVal = distalSynapses.get(segment)) == null) ///获取指定的树突的突触集合，
        {
            distalSynapses.put(segment, retVal = new ArrayList<Synapse>());//如果没有这个树突的突触集合的话,则新建一个空的突触集合添加至这个LinkedHashMap对象
        }

        return retVal;
    }

    /**
     * Returns the mapping of {@link ProximalDendrite}s to their {@link Synapse}s.
     * 返回近端树突到它们的突触的集合映射，即返回近端树突的的突触列表
     * @param segment   the {@link ProximalDendrite} used as a key. 近端树突对象作为键值
     * @return          the mapping of {@link ProximalDendrite}s to their {@link Synapse}s. 近端树突对应的突触集合
     */
    public List<Synapse> getSynapses(ProximalDendrite segment) {
        if(segment == null) {
            throw new IllegalArgumentException("Segment was null");
        }

        if(proximalSynapses == null) {
            proximalSynapses = new LinkedHashMap<Segment, List<Synapse>>();
        }

        List<Synapse> retVal = null;
        if((retVal = proximalSynapses.get(segment)) == null) {
            proximalSynapses.put(segment, retVal = new ArrayList<Synapse>());
        }

        return retVal;
    }
    
    /**
     * <b>FOR TEST USE ONLY<b>
     * @return
     */
    public Map<Cell, HashSet<Synapse>> getReceptorSynapseMapping() {
        return new LinkedHashMap<>(receptorSynapses);
    }

    /**
     * Clears all {@link TemporalMemory} state.
     * 清理所有TemporalMemory的状态，实际上就是把活跃单元集合清空、胜出的单元清空、预测的单元清空
     */
    public void clear() {
        activeCells.clear();
        winnerCells.clear();
        predictiveCells.clear();
    }

    /**
     * Returns the current {@link Set} of active {@link Cell}s
     * 返回目前活跃的单元集合
     * @return  the current {@link Set} of active {@link Cell}s 目前活跃的单元集合
     */
    public Set<Cell> getActiveCells() {
        return activeCells;
    }

    /**
     * Sets the current {@link Set} of active {@link Cell}s
     * 设置当前活跃单元的集合
     * @param cells 单元集合
     */
    public void setActiveCells(Set<Cell> cells) {
        this.activeCells = cells;
    }

    /**
     * Returns the current {@link Set} of winner cells
     * 返回当前胜出的单元的集合
     * @return  the current {@link Set} of winner cells 当前胜出单元的集合
     */
    public Set<Cell> getWinnerCells() {
        return winnerCells;
    }

    /**
     * Sets the current {@link Set} of winner {@link Cell}s
     * 设置当前胜出的单元的集合
     * @param cells 胜出的单元集合
     */
    public void setWinnerCells(Set<Cell> cells) {
        this.winnerCells = cells;
    }

    /**
     * Returns the {@link Set} of predictive cells.
     * 返回预测的单元的集合
     * @return
     */
    public Set<Cell> getPredictiveCells() {
        if(predictiveCells.isEmpty()) //如果预测的单元为空的情况下
        {
            Cell previousCell = null;
            Cell currCell = null;
            
            List<DistalDendrite> temp = new ArrayList<>(activeSegments);//按照集合迭代器返回的顺序，构造包含指定集合元素的列表
            for(DistalDendrite activeSegment : temp)//对于每一个激活的树突
            {
                if((currCell = activeSegment.getParentCell()) != previousCell) //获取当前树突所在的单元，如果这个单元和之前的一个单元不重复（所有的树突已经按照其单元id的大小做排序）
                {
                    predictiveCells.add(previousCell = currCell); //那么，这个单元加入预测单元的集合，这里可以发现，凡是一个单元上有激活的树突，那么这个单元都是预测单元
                }
            }
        }
        return predictiveCells;
    }
    
    /**
     * Clears the previous predictive cells from the list.
     * 从列表中清理之前预测的单元集合
     */
    public void clearPredictiveCells() {
        this.predictiveCells.clear();
    }

    /**
     * Returns the column at the specified index.
     * 返回指定索引的列
     * @param index 指定的索引
     * @return
     */
    public Column getColumn(int index) {
        return memory.getObject(index);
    }

    /**
     * Sets the number of {@link Column}.
     * 设置列的维度
     * @param columnDimensions 列的维度数组
     */
    public void setColumnDimensions(int[] columnDimensions) {
        this.columnDimensions = columnDimensions;
    }

    /**
     * Gets the number of {@link Column}.
     * 获取列的数量（维度）
     * @return columnDimensions 列的维度
     */
    public int[] getColumnDimensions() {
        return this.columnDimensions;
    }

    /**
     * A list representing the dimensions of the input
     * vector. Format is [height, width, depth, ...], where
     * each value represents the size of the dimension. For a
     * topology of one dimension with 100 inputs use 100, or
     * [100]. For a two dimensional topology of 10x5 use
     * [10,5].
     * 代表输入向量维度的列表。格式是[height,width,depth,...]，其中的
     * 每一个值代表维度的大小。例如，有着100个输入的一维拓扑用100或者[100]
     * 表达。一个10*5的二维拓扑用[10,5]表达。
     * @param inputDimensions
     */
    public void setInputDimensions(int[] inputDimensions) {
        this.inputDimensions = inputDimensions;
    }

    /**
     * Returns the configured input dimensions
     * 返回配置的输入维度
     * see {@link #setInputDimensions(int[])}
     * @return the configured input dimensions 返回输入维度数组
     */
    public int[] getInputDimensions() {
        return inputDimensions;
    }

    /**
     * Sets the number of {@link Cell}s per {@link Column}
     * 设置每列拥有的单元数量
     * @param cellsPerColumn
     */
    public void setCellsPerColumn(int cellsPerColumn) {
        this.cellsPerColumn = cellsPerColumn;
    }

    /**
     * Gets the number of {@link Cell}s per {@link Column}.
     * 返回每列拥有的单元数量
     * @return cellsPerColumn
     */
    public int getCellsPerColumn() {
        return this.cellsPerColumn;
    }

    /**
     * Sets the activation threshold.
     * If the number of active connected synapses on a segment
     * is at least this threshold, the segment is said to be active.
     * 设置激活阈值，如果在一个树突上的激活连接突触的数量大于等于这个阈值，这个
     * 树突就是激活树突
     * @param activationThreshold
     */
    public void setActivationThreshold(int activationThreshold) {
        this.activationThreshold = activationThreshold;
    }

    /**
     * Returns the activation threshold.
     * 返回激活阈值
     * @return
     */
    public int getActivationThreshold() {
        return activationThreshold;
    }

    /**
     * Radius around cell from which it can
     * sample to form distal dendrite connections.
     * 细胞的半径，在这个半径内细胞都能取样形成基底树突连接。
     * @param   learningRadius 学习半径
     */
    public void setLearningRadius(int learningRadius) {
        this.learningRadius = learningRadius;
    }

    /**
     * Returns the learning radius.
     * 返回学习半径
     * @return
     */
    public int getLearningRadius() {
        return learningRadius;
    }

    /**
     * If the number of synapses active on a segment is at least this
     * threshold, it is selected as the best matching
     * cell in a bursting column.
     * 如果一个树突上激活的突触的数量大于等于此阈值，那么这个树突所在的细胞被选为列中最匹配的单元
     * @param   minThreshold 最小阈值
     */
    public void setMinThreshold(int minThreshold) {
        this.minThreshold = minThreshold;
    }

    /**
     * Returns the minimum threshold of active synapses to be picked as best.
     * 返回要选择为最匹配的单元需要的激活树突的最小阈值
     * @return
     */
    public int getMinThreshold() {
        return minThreshold;
    }

    /**
     * The maximum number of synapses added to a segment during learning.
     * 在学习过程中添加到一个树突的最大突触的数量
     * @param   maxNewSynapseCount
     */
    public void setMaxNewSynapseCount(int maxNewSynapseCount) {
        this.maxNewSynapseCount = maxNewSynapseCount;
    }

    /**
     * Returns the maximum number of synapses added to a segment during
     * learning.
     * 返回在学习过程中添加到一个树突的最大突触的数量
     * @return
     */
    public int getMaxNewSynapseCount() {
        return maxNewSynapseCount;
    }
    
    /**
     * The maximum number of segments allowed on a given cell
     * 一个给定单元上允许的最大树突的数量
     * @param maxSegmentsPerCell
     */
    public void setMaxSegmentsPerCell(int maxSegmentsPerCell) {
        this.maxSegmentsPerCell = maxSegmentsPerCell;
    }
    
    /**
     * Returns the maximum number of segments allowed on a given cell
     * 返回一个单元上允许的最大树突的数量
     * @return
     */
    public int getMaxSegmentsPerCell() {
        return maxSegmentsPerCell;
    }
    
    /**
     * The maximum number of synapses allowed on a given segment
     * 设置一个给定树突上允许的突触的最大数量
     * @param maxSynapsesPerSegment
     */
    public void setMaxSynapsesPerSegment(int maxSynapsesPerSegment) {
        this.maxSynapsesPerSegment = maxSynapsesPerSegment;
    }
    
    /**
     * Returns the maximum number of synapses allowed per segment
     * 返回每个树突上允许的突触的最大数量
     * @return
     */
    public int getMaxSynapsesPerSegment() {
        return maxSynapsesPerSegment;
    }

    /**
     * Initial permanence of a new synapse
     * 一个新的树突的初始化持久度值
     * @param   initialPermanence
     */
    public void setInitialPermanence(double initialPermanence) {
        this.initialPermanence = initialPermanence;
    }

    /**
     * Returns the initial permanence setting.
     * 返回持久度值初始化设置
     * @return
     */
    public double getInitialPermanence() {
        return initialPermanence;
    }

    /**
     * If the permanence value for a synapse
     * is greater than this value, it is said
     * to be connected.
     * 如果一个突触的持久度值大于这个设定的值，那么说这个突触是一个连接突触
     * @param connectedPermanence
     */
    public void setConnectedPermanence(double connectedPermanence) {
        this.connectedPermanence = connectedPermanence;
    }

    /**
     * If the permanence value for a synapse
     * is greater than this value, it is said
     * to be connected.
     * 返回连接突触的阈值
     * @return
     */
    public double getConnectedPermanence() {
        return connectedPermanence;
    }

    /**
     * Amount by which permanences of synapses
     * are incremented during learning.
     * 在学习过程中，突触的持久度值的增加量
     * @param   permanenceIncrement 持久度值的增加量
     */
    public void setPermanenceIncrement(double permanenceIncrement) {
        this.permanenceIncrement = permanenceIncrement;
    }

    /**
     * Amount by which permanences of synapses
     * are incremented during learning.
     * 
     * 返回学习过程中持久度值增加的量
     */
    public double getPermanenceIncrement() {
        return this.permanenceIncrement;
    }

    /**
     * Amount by which permanences of synapses
     * are decremented during learning.
     * 设置 学习过程中，突触的持久度值减少的量
     * @param   permanenceDecrement 持久度值减少量
     */
    public void setPermanenceDecrement(double permanenceDecrement) {
        this.permanenceDecrement = permanenceDecrement;
    }

    /**
     * Amount by which permanences of synapses
     * are decremented during learning.
     * 返回，学习过程中突触的持久度值减少的量
     */
    public double getPermanenceDecrement() {
        return this.permanenceDecrement;
    }

    /**
     * Amount by which active permanences of synapses of previously predicted but inactive segments are decremented.
     * 先前预测的但是未被激活的树突的突触的激活持久度值减少的量
     * @param predictedSegmentDecrement
     */
    public void setPredictedSegmentDecrement(double predictedSegmentDecrement) {
        this.predictedSegmentDecrement = predictedSegmentDecrement;
    }

    /**
     * Returns the predictedSegmentDecrement amount.
     * 返回 先前处于预测状态但是未被激活的树突的突触的激活持久度值减少的量
     * @return
     */
    public double getPredictedSegmentDecrement() {
        return this.predictedSegmentDecrement;
    }

    /**
     * Converts a {@link Collection} of {@link Cell}s to a list
     * of cell indexes.
     * 把单元集合转变为单元的索引集合
     * @param cells 输入的单元集合
     * @return 输入的单元索引的集合
     */
    public static List<Integer> asCellIndexes(Collection<Cell> cells) {
        List<Integer> ints = new ArrayList<Integer>();
        for(Cell cell : cells) {
            ints.add(cell.getIndex());
        }

        return ints;
    }

    /**
     * Converts a {@link Collection} of {@link Column}s to a list
     * of column indexes.
     * 把列集合转变为列的索引集合
     * @param columns 输入的列集合
     * @return 列集合的索引
     */
    public static List<Integer> asColumnIndexes(Collection<Column> columns) {
        List<Integer> ints = new ArrayList<Integer>();
        for(Column col : columns) {
            ints.add(col.getIndex());
        }

        return ints;
    }

    /**
     * Returns a list of the {@link Cell}s specified.
     * 返回单元索引所指定的单元集合
     * @param cells		the indexes of the {@link Cell}s to return 输入的单元索引的集合
     * @return	the specified list of cells 指定的单元集合
     */
    public List<Cell> asCellObjects(Collection<Integer> cells) {
        List<Cell> objs = new ArrayList<Cell>();
        for(int i : cells) {
            objs.add(this.cells[i]);
        }
        return objs;
    }

    /**
     * Returns a list of the {@link Column}s specified.
     * 返回列索引对应的列的集合
     * @param cols		the indexes of the {@link Column}s to return 列的索引集合
     * @return		the specified list of columns 列的结合
     */
    public List<Column> asColumnObjects(Collection<Integer> cols) {
        List<Column> objs = new ArrayList<Column>();
        for(int i : cols) {
            objs.add(this.memory.getObject(i));//获取第i列
        }
        return objs;
    }

    /**
     * Returns a {@link Set} view of the {@link Column}s specified by
     * the indexes passed in.
     * 返回由传入的索引数组指定的列的LinkedHashSet集合
     * @param indexes		the indexes of the Columns to return 列的索引数组
     * @return				a set view of the specified columns 指定的列的集合视图
     */
    public LinkedHashSet<Column> getColumnSet(int[] indexes) {
        LinkedHashSet<Column> retVal = new LinkedHashSet<Column>();
        for(int i = 0;i < indexes.length;i++) {
            retVal.add(memory.getObject(indexes[i]));
        }
        return retVal;
    }

    /**
     * Returns a {@link List} view of the {@link Column}s specified by
     * the indexes passed in.
     * 返回由传入索引指定的列的List集合
     * @param indexes		the indexes of the Columns to return 传入的列的索引集合
     * @return				a List view of the specified columns 指定的列的List集合
     */
    public List<Column> getColumnList(int[] indexes) 
    {
        List<Column> retVal = new ArrayList<Column>();
        for(int i = 0;i < indexes.length;i++) {
            retVal.add(memory.getObject(indexes[i]));
        }
        return retVal;
    }
    
    /**
     * High verbose output useful for debugging
     */
    public void printParameters() {
        System.out.println("------------ SpatialPooler Parameters ------------------");
        System.out.println("numInputs                  = " + getNumInputs());
        System.out.println("numColumns                 = " + getNumColumns());
        System.out.println("cellsPerColumn             = " + getCellsPerColumn());
        System.out.println("columnDimensions           = " + Arrays.toString(getColumnDimensions()));
        System.out.println("numActiveColumnsPerInhArea = " + getNumActiveColumnsPerInhArea());
        System.out.println("potentialPct               = " + getPotentialPct());
        System.out.println("potentialRadius            = " + getPotentialRadius());
        System.out.println("globalInhibition           = " + getGlobalInhibition());
        System.out.println("localAreaDensity           = " + getLocalAreaDensity());
        System.out.println("inhibitionRadius           = " + getInhibitionRadius());
        System.out.println("stimulusThreshold          = " + getStimulusThreshold());
        System.out.println("synPermActiveInc           = " + getSynPermActiveInc());
        System.out.println("synPermInactiveDec         = " + getSynPermInactiveDec());
        System.out.println("synPermConnected           = " + getSynPermConnected());
        System.out.println("minPctOverlapDutyCycle     = " + getMinPctOverlapDutyCycles());
        System.out.println("minPctActiveDutyCycle      = " + getMinPctActiveDutyCycles());
        System.out.println("dutyCyclePeriod            = " + getDutyCyclePeriod());
        System.out.println("maxBoost                   = " + getMaxBoost());
        System.out.println("version                    = " + getVersion());

        System.out.println("\n------------ TemporalMemory Parameters ------------------");
        System.out.println("activationThreshold        = " + getActivationThreshold());
        System.out.println("learningRadius             = " + getLearningRadius());
        System.out.println("minThreshold               = " + getMinThreshold());
        System.out.println("maxNewSynapseCount         = " + getMaxNewSynapseCount());
        System.out.println("maxSynapsesPerSegment      = " + getMaxSynapsesPerSegment());
        System.out.println("maxSegmentsPerCell         = " + getMaxSegmentsPerCell());
        System.out.println("initialPermanence          = " + getInitialPermanence());
        System.out.println("connectedPermanence        = " + getConnectedPermanence());
        System.out.println("permanenceIncrement        = " + getPermanenceIncrement());
        System.out.println("permanenceDecrement        = " + getPermanenceDecrement());
        System.out.println("predictedSegmentDecrement  = " + getPredictedSegmentDecrement());
    }
    
    /**
     * High verbose output useful for debugging
     */
    public String getPrintString() {
        StringWriter sw;
        PrintWriter pw = new PrintWriter(sw = new StringWriter());
        
        pw.println("---------------------- General -------------------------");
        pw.println("columnDimensions           = " + Arrays.toString(getColumnDimensions()));
        pw.println("inputDimensions            = " + Arrays.toString(getInputDimensions()));
        pw.println("cellsPerColumn             = " + getCellsPerColumn());
        
        pw.println("random                     = " + getRandom());
        pw.println("seed                       = " + getSeed());
        
        pw.println("\n------------ SpatialPooler Parameters ------------------");
        pw.println("numInputs                  = " + getNumInputs());
        pw.println("numColumns                 = " + getNumColumns());
        pw.println("numActiveColumnsPerInhArea = " + getNumActiveColumnsPerInhArea());
        pw.println("potentialPct               = " + getPotentialPct());
        pw.println("potentialRadius            = " + getPotentialRadius());
        pw.println("globalInhibition           = " + getGlobalInhibition());
        pw.println("localAreaDensity           = " + getLocalAreaDensity());
        pw.println("inhibitionRadius           = " + getInhibitionRadius());
        pw.println("stimulusThreshold          = " + getStimulusThreshold());
        pw.println("synPermActiveInc           = " + getSynPermActiveInc());
        pw.println("synPermInactiveDec         = " + getSynPermInactiveDec());
        pw.println("synPermConnected           = " + getSynPermConnected());
        pw.println("synPermBelowStimulusInc    = " + getSynPermBelowStimulusInc());
        pw.println("synPermTrimThreshold       = " + getSynPermTrimThreshold());
        pw.println("minPctOverlapDutyCycles    = " + getMinPctOverlapDutyCycles());
        pw.println("minPctActiveDutyCycles     = " + getMinPctActiveDutyCycles());
        pw.println("dutyCyclePeriod            = " + getDutyCyclePeriod());
        pw.println("wrapAround                 = " + isWrapAround());
        pw.println("maxBoost                   = " + getMaxBoost());
        pw.println("version                    = " + getVersion());

        pw.println("\n------------ TemporalMemory Parameters ------------------");
        pw.println("activationThreshold        = " + getActivationThreshold());
        pw.println("learningRadius             = " + getLearningRadius());
        pw.println("minThreshold               = " + getMinThreshold());
        pw.println("maxNewSynapseCount         = " + getMaxNewSynapseCount());
        pw.println("maxSynapsesPerSegment      = " + getMaxSynapsesPerSegment());
        pw.println("maxSegmentsPerCell         = " + getMaxSegmentsPerCell());
        pw.println("initialPermanence          = " + getInitialPermanence());
        pw.println("connectedPermanence        = " + getConnectedPermanence());
        pw.println("permanenceIncrement        = " + getPermanenceIncrement());
        pw.println("permanenceDecrement        = " + getPermanenceDecrement());
        pw.println("predictedSegmentDecrement  = " + getPredictedSegmentDecrement());
        
        return sw.toString();
    }
    
    /**
     * Returns a 2 Dimensional array of 1's and 0's indicating
     * which of the column's pool members are above the connected
     * threshold, and therefore considered "connected"
     *  返回1和0组成的二维数组，该数组指示列的哪些池成员高于已经连接的阈值，
     *  因此被视为“已连接”。
     *  也就是形成所有列的所有连接情况数组
     * @return
     */
    public int[][] getConnecteds() {
        int[][] retVal = new int[getNumColumns()][];//新建一个2维数组，第一维为列的数量，第二维未定
        for(int i = 0;i < getNumColumns();i++) //对于每一个列
        {
            Pool pool = getPotentialPools().get(i);//获取第i个列的潜在输入感受野（池）
            int[] indexes = pool.getDenseConnected(this);//获取感受野与这个单元柱的连接情况，生成一个连接的二进制数组，其中1代表形成连接，0代表没有形成连接
            retVal[i] = indexes;//记录每一个列的情况
        }
        
        return retVal;
    }
    
    /**
     * Returns a 2 Dimensional array of 1's and 0's indicating
     * which input bits belong to which column's pool.
     * 返回一个二维的0和1数组，表示哪个输入序列属于那个列的感受野（池）
     * @return
     */
    public int[][] getPotentials() {
        int[][] retVal = new int[getNumColumns()][];//新建一个2维数组，第一维为列的数量，第二维未定
        for(int i = 0;i < getNumColumns();i++) //对于每一个列
        {
            Pool pool = getPotentialPools().get(i);//获取这个列的感受野，也就是列的池
            int[] indexes = pool.getDensePotential(this);//获取感受野与这个单元柱的潜在连接情况，生成生成一个潜在连接的二进制数组，其中1代表形成潜在连接，0代表没有形成潜在连接
            retVal[i] = indexes;
        }
        
        return retVal;
    }
    
    /**
     * Returns a 2 Dimensional array of the permanences for SP
     * proximal dendrite column pooled connections.
     * 返回近端树突列的池化的连接的持久度的二维数组，即为所有列返回其感受野连接突触的持久度值数组。
     * @return
     */
    public double[][] getPermanences() {
        double[][] retVal = new double[getNumColumns()][];
        for(int i = 0;i < getNumColumns();i++) {
            Pool pool = getPotentialPools().get(i);
            double[] perm = pool.getDensePermanences(this);
            retVal[i] = perm;
        }
        
        return retVal;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + activationThreshold;
        result = prime * result + ((activeCells == null) ? 0 : activeCells.hashCode());
        result = prime * result + Arrays.hashCode(activeDutyCycles);
        result = prime * result + Arrays.hashCode(boostFactors);
        result = prime * result + Arrays.hashCode(cells);
        result = prime * result + cellsPerColumn;
        result = prime * result + Arrays.hashCode(columnDimensions);
        result = prime * result + ((connectedCounts == null) ? 0 : connectedCounts.hashCode());
        long temp;
        temp = Double.doubleToLongBits(connectedPermanence);
        result = prime * result + (int)(temp ^ (temp >>> 32));
        result = prime * result + dutyCyclePeriod;
        result = prime * result + (globalInhibition ? 1231 : 1237);
        result = prime * result + inhibitionRadius;
        temp = Double.doubleToLongBits(initConnectedPct);
        result = prime * result + (int)(temp ^ (temp >>> 32));
        temp = Double.doubleToLongBits(initialPermanence);
        result = prime * result + (int)(temp ^ (temp >>> 32));
        result = prime * result + Arrays.hashCode(inputDimensions);
        result = prime * result + ((inputMatrix == null) ? 0 : inputMatrix.hashCode());
        result = prime * result + spIterationLearnNum;
        result = prime * result + spIterationNum;
        result = prime * result + (new Long(tmIteration)).intValue();
        result = prime * result + learningRadius;
        temp = Double.doubleToLongBits(localAreaDensity);
        result = prime * result + (int)(temp ^ (temp >>> 32));
        temp = Double.doubleToLongBits(maxBoost);
        result = prime * result + (int)(temp ^ (temp >>> 32));
        result = prime * result + maxNewSynapseCount;
        result = prime * result + ((memory == null) ? 0 : memory.hashCode());
        result = prime * result + Arrays.hashCode(minActiveDutyCycles);
        result = prime * result + Arrays.hashCode(minOverlapDutyCycles);
        temp = Double.doubleToLongBits(minPctActiveDutyCycles);
        result = prime * result + (int)(temp ^ (temp >>> 32));
        temp = Double.doubleToLongBits(minPctOverlapDutyCycles);
        result = prime * result + (int)(temp ^ (temp >>> 32));
        result = prime * result + minThreshold;
        temp = Double.doubleToLongBits(numActiveColumnsPerInhArea);
        result = prime * result + (int)(temp ^ (temp >>> 32));
        result = prime * result + numColumns;
        result = prime * result + numInputs;
        temp = numSynapses;
        result = prime * result + (int)(temp ^ (temp >>> 32));
        result = prime * result + Arrays.hashCode(overlapDutyCycles);
        temp = Double.doubleToLongBits(permanenceDecrement);
        result = prime * result + (int)(temp ^ (temp >>> 32));
        temp = Double.doubleToLongBits(permanenceIncrement);
        result = prime * result + (int)(temp ^ (temp >>> 32));
        temp = Double.doubleToLongBits(potentialPct);
        result = prime * result + (int)(temp ^ (temp >>> 32));
        result = prime * result + ((potentialPools == null) ? 0 : potentialPools.hashCode());
        result = prime * result + potentialRadius[0];////这里有
        temp = Double.doubleToLongBits(predictedSegmentDecrement);
        result = prime * result + (int)(temp ^ (temp >>> 32));
        result = prime * result + ((predictiveCells == null) ? 0 : predictiveCells.hashCode());
        result = prime * result + ((random == null) ? 0 : random.hashCode());
        result = prime * result + ((receptorSynapses == null) ? 0 : receptorSynapses.hashCode());
        result = prime * result + seed;
        result = prime * result + ((segments == null) ? 0 : segments.hashCode());
        temp = Double.doubleToLongBits(stimulusThreshold);
        result = prime * result + (int)(temp ^ (temp >>> 32));
        temp = Double.doubleToLongBits(synPermActiveInc);
        result = prime * result + (int)(temp ^ (temp >>> 32));
        temp = Double.doubleToLongBits(synPermBelowStimulusInc);
        result = prime * result + (int)(temp ^ (temp >>> 32));
        temp = Double.doubleToLongBits(synPermConnected);
        result = prime * result + (int)(temp ^ (temp >>> 32));
        temp = Double.doubleToLongBits(synPermInactiveDec);
        result = prime * result + (int)(temp ^ (temp >>> 32));
        temp = Double.doubleToLongBits(synPermMax);
        result = prime * result + (int)(temp ^ (temp >>> 32));
        temp = Double.doubleToLongBits(synPermMin);
        result = prime * result + (int)(temp ^ (temp >>> 32));
        temp = Double.doubleToLongBits(synPermTrimThreshold);
        result = prime * result + (int)(temp ^ (temp >>> 32));
        result = prime * result + proximalSynapseCounter;
        result = prime * result + ((proximalSynapses == null) ? 0 : proximalSynapses.hashCode());
        result = prime * result + ((distalSynapses == null) ? 0 : distalSynapses.hashCode());
        result = prime * result + Arrays.hashCode(tieBreaker);
        result = prime * result + updatePeriod;
        temp = Double.doubleToLongBits(version);
        result = prime * result + (int)(temp ^ (temp >>> 32));
        result = prime * result + ((winnerCells == null) ? 0 : winnerCells.hashCode());
        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object obj) {
        if(this == obj)
            return true;
        if(obj == null)
            return false;
        if(getClass() != obj.getClass())
            return false;
        Connections other = (Connections)obj;
        if(activationThreshold != other.activationThreshold)
            return false;
        if(activeCells == null) {
            if(other.activeCells != null)
                return false;
        } else if(!activeCells.equals(other.activeCells))
            return false;
        if(!Arrays.equals(activeDutyCycles, other.activeDutyCycles))
            return false;
        if(!Arrays.equals(boostFactors, other.boostFactors))
            return false;
        if(!Arrays.equals(cells, other.cells))
            return false;
        if(cellsPerColumn != other.cellsPerColumn)
            return false;
        if(!Arrays.equals(columnDimensions, other.columnDimensions))
            return false;
        if(connectedCounts == null) {
            if(other.connectedCounts != null)
                return false;
        } else if(!connectedCounts.equals(other.connectedCounts))
            return false;
        if(Double.doubleToLongBits(connectedPermanence) != Double.doubleToLongBits(other.connectedPermanence))
            return false;
        if(dutyCyclePeriod != other.dutyCyclePeriod)
            return false;
        if(globalInhibition != other.globalInhibition)
            return false;
        if(inhibitionRadius != other.inhibitionRadius)
            return false;
        if(Double.doubleToLongBits(initConnectedPct) != Double.doubleToLongBits(other.initConnectedPct))
            return false;
        if(Double.doubleToLongBits(initialPermanence) != Double.doubleToLongBits(other.initialPermanence))
            return false;
        if(!Arrays.equals(inputDimensions, other.inputDimensions))
            return false;
        if(inputMatrix == null) {
            if(other.inputMatrix != null)
                return false;
        } else if(!inputMatrix.equals(other.inputMatrix))
            return false;
        if(spIterationLearnNum != other.spIterationLearnNum)
            return false;
        if(spIterationNum != other.spIterationNum)
            return false;
        if(tmIteration != other.tmIteration)
            return false;
        if(learningRadius != other.learningRadius)
            return false;
        if(Double.doubleToLongBits(localAreaDensity) != Double.doubleToLongBits(other.localAreaDensity))
            return false;
        if(Double.doubleToLongBits(maxBoost) != Double.doubleToLongBits(other.maxBoost))
            return false;
        if(maxNewSynapseCount != other.maxNewSynapseCount)
            return false;
        if(memory == null) {
            if(other.memory != null)
                return false;
        } else if(!memory.equals(other.memory))
            return false;
        if(!Arrays.equals(minActiveDutyCycles, other.minActiveDutyCycles))
            return false;
        if(!Arrays.equals(minOverlapDutyCycles, other.minOverlapDutyCycles))
            return false;
        if(Double.doubleToLongBits(minPctActiveDutyCycles) != Double.doubleToLongBits(other.minPctActiveDutyCycles))
            return false;
        if(Double.doubleToLongBits(minPctOverlapDutyCycles) != Double.doubleToLongBits(other.minPctOverlapDutyCycles))
            return false;
        if(minThreshold != other.minThreshold)
            return false;
        if(Double.doubleToLongBits(numActiveColumnsPerInhArea) != Double.doubleToLongBits(other.numActiveColumnsPerInhArea))
            return false;
        if(numColumns != other.numColumns)
            return false;
        if(numInputs != other.numInputs)
            return false;
        if(numSynapses != other.numSynapses)
            return false;
        if(!Arrays.equals(overlapDutyCycles, other.overlapDutyCycles))
            return false;
        if(Double.doubleToLongBits(permanenceDecrement) != Double.doubleToLongBits(other.permanenceDecrement))
            return false;
        if(Double.doubleToLongBits(permanenceIncrement) != Double.doubleToLongBits(other.permanenceIncrement))
            return false;
        if(Double.doubleToLongBits(potentialPct) != Double.doubleToLongBits(other.potentialPct))
            return false;
        if(potentialPools == null) {
            if(other.potentialPools != null)
                return false;
        } else if(!potentialPools.equals(other.potentialPools))
            return false;
        if(potentialRadius != other.potentialRadius)
            return false;
        if(Double.doubleToLongBits(predictedSegmentDecrement) != Double.doubleToLongBits(other.predictedSegmentDecrement))
            return false;
        if(predictiveCells == null) {
            if(other.predictiveCells != null)
                return false;
        } else if(!getPredictiveCells().equals(other.getPredictiveCells()))
            return false;
        if(receptorSynapses == null) {
            if(other.receptorSynapses != null)
                return false;
        } else if(!receptorSynapses.toString().equals(other.receptorSynapses.toString()))
            return false;
        if(seed != other.seed)
            return false;
        if(segments == null) {
            if(other.segments != null)
                return false;
        } else if(!segments.equals(other.segments))
            return false;
        if(Double.doubleToLongBits(stimulusThreshold) != Double.doubleToLongBits(other.stimulusThreshold))
            return false;
        if(Double.doubleToLongBits(synPermActiveInc) != Double.doubleToLongBits(other.synPermActiveInc))
            return false;
        if(Double.doubleToLongBits(synPermBelowStimulusInc) != Double.doubleToLongBits(other.synPermBelowStimulusInc))
            return false;
        if(Double.doubleToLongBits(synPermConnected) != Double.doubleToLongBits(other.synPermConnected))
            return false;
        if(Double.doubleToLongBits(synPermInactiveDec) != Double.doubleToLongBits(other.synPermInactiveDec))
            return false;
        if(Double.doubleToLongBits(synPermMax) != Double.doubleToLongBits(other.synPermMax))
            return false;
        if(Double.doubleToLongBits(synPermMin) != Double.doubleToLongBits(other.synPermMin))
            return false;
        if(Double.doubleToLongBits(synPermTrimThreshold) != Double.doubleToLongBits(other.synPermTrimThreshold))
            return false;
        if(proximalSynapseCounter != other.proximalSynapseCounter)
            return false;
        if(proximalSynapses == null) {
            if(other.proximalSynapses != null)
                return false;
        } else if(!proximalSynapses.equals(other.proximalSynapses))
            return false;
        if(distalSynapses == null) {
            if(other.distalSynapses != null)
                return false;
        } else if(!distalSynapses.equals(other.distalSynapses))
            return false;
        if(!Arrays.equals(tieBreaker, other.tieBreaker))
            return false;
        if(updatePeriod != other.updatePeriod)
            return false;
        if(Double.doubleToLongBits(version) != Double.doubleToLongBits(other.version))
            return false;
        if(winnerCells == null) {
            if(other.winnerCells != null)
                return false;
        } else if(!winnerCells.equals(other.winnerCells))
            return false;
        return true;
    }
}
