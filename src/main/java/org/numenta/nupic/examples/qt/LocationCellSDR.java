/* ---------------------------------------------------------------------
 * Numenta Platform for Intelligent Computing (NuPIC)
 * Copyright (C) 2014, Numenta, Inc.  Unless you have an agreement
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
package org.numenta.nupic.examples.qt;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.numenta.nupic.Parameters;
import org.numenta.nupic.Parameters.KEY;
import org.numenta.nupic.algorithms.Classification;
import org.numenta.nupic.algorithms.SDRClassifier;
import org.numenta.nupic.algorithms.SpatialPooler;
import org.numenta.nupic.algorithms.TemporalMemory;
//import org.numenta.nupic.algorithms.ClassifierResult;
import org.numenta.nupic.encoders.ScalarEncoder;
import org.numenta.nupic.model.Cell;
import org.numenta.nupic.model.ComputeCycle;
import org.numenta.nupic.model.Connections;
import org.numenta.nupic.util.ArrayUtils;
import org.numenta.nupic.util.FastRandom;

import gnu.trove.list.array.TIntArrayList;//这是一个对int数据进行管理的高级类
/**
 * Quick and dirty example of tying together a network of components.
 * This should hold off peeps until the Network API is complete.
 * (see: https://github.com/numenta/htm.java/wiki/Roadmap)
 *
 * <p>Warning: Sloppy sketchpad code, but it works!</p>
 *
 * <p><em><b>
 * To see the pretty printed test output and Classification results,
 *
 * UNCOMMENT ALL FUNCTIONAL (NON-LABEL) LINES BELOW!
 *
 * These are commented to avoid running during command line builds and
 * the ugly yellow "unused" markers that Eclipse puts on unused lines.
 *
 * </b></em></p>
 * @author PDove
 * @author cogmission
 */
public class LocationCellSDR {
    static boolean isResetting = true;

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        Parameters params = getParameters();
        System.out.println(params);

        // Toggle this to switch between resetting on every week start day
        isResetting = true;

        //Layer components
        ScalarEncoder.Builder distanceBuilder =
            ScalarEncoder.builder()
                .n(7)//代表输出编码的总位数
                .w(3)//数字编码位的数量
                .radius(1)//两个输入之间的差值大于此数，那么他们的编码没有重叠，如果他们的值小于此数会多少有点重叠
                .minVal(0)//输入信号的最小值
                .maxVal(4)//输入信号的最大值
                .periodic(false)//是否是周期性的编码
                .forced(true)//是否进行输入合法性检查
                .resolution(1);///当两个输入值比这个分辨率值大或者相等时，保证二者有不同的编码
        ScalarEncoder encoder = distanceBuilder.build();//获取ScalarEncoder编码器
        
        SpatialPooler sp = new SpatialPooler();//获取空间池化
        TemporalMemory tm = new TemporalMemory();///时间记忆
        SDRClassifier classifier = new SDRClassifier(new TIntArrayList(new int[] { 1 }), 0.1, 0.3, 0);///分类器

        Layer<Integer> layer = getLayer(params, encoder, sp, tm, classifier);
        
      ///把输入数据提前读出来，免得每次重新读，浪费时间
        String path = "D:\\workspace\\routes5.txt";
        List<String> scanListPath = null;
		try {
			scanListPath = readFile02(path);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
        
 
         int[] lastLocation=null;
        //j代表迭代的轮数、i代表输入值，x代表迭代的次数，下面需要重新写
        for(int i = 0;i<200002;i++) 
        {  // USE "X" here to control run length
            if (i == 0 && isResetting) {
                System.out.println("reset:");
                tm.reset(layer.getMemory());
            }
            ///这里得首先对61*61cm的图形进行分格子，其实也很简单：首先初始化一个位置，然后只能在这个位置相邻的8个位置移动
            int[] currentLocation=GetLocationFromRectangleGridRoutes(i,4,4,scanListPath);
            
            // For 3rd argument: Use "i" for record num if re-cycling records (isResetting == true) - otherwise use "x" (the sequence number)
            runThroughLayer(layer, currentLocation[0],currentLocation[1],currentLocation[2],currentLocation[3], i);
            lastLocation=currentLocation;
        }
    }
    
    public static List<String> readFile02(String path) throws IOException {
        // 使用一个字符串集合来存储文本中的路径 ，也可用String []数组
        List<String> list = new ArrayList<String>();
        FileInputStream fis = new FileInputStream(path);
        // 防止路径乱码   如果utf-8 乱码  改GBK     eclipse里创建的txt  用UTF-8，在电脑上自己创建的txt  用GBK
        InputStreamReader isr = new InputStreamReader(fis, "UTF-8");
        BufferedReader br = new BufferedReader(isr);
        String line = "";
        while ((line = br.readLine()) != null) {
            // 如果 t x t文件里的路径 不包含---字符串       这里是对里面的内容进行一个筛选
            if (line.lastIndexOf("---") < 0&&line.length()>0) {
                list.add(line);
            }
        }
        br.close();
        isr.close();
        fis.close();
        return list;
    }
	
    /**
     * 直接从现成的路径中获取位置
     * @param sequence
     * @return
     */
    private static int[] GetLocationFromRectangleGridRoutes(double sequence,int width,int height,List<String> scanListPath)
    {
    	
    	int[] currentLocation=new int[4];
       
			
            
            int index=(int) (sequence%scanListPath.size());
            String dataString=scanListPath.get(index);///获取指定的sequence对应的坐标点
            if (dataString!=null&&dataString.length()>0)
            {
				dataString=dataString.substring(1, dataString.length()-1);
	            String[] dataStringCoordinates=dataString.split(",");
	            ///距离被边界的距离
	            currentLocation[0]=Integer.valueOf(dataStringCoordinates[1]);
	            currentLocation[1]=height-currentLocation[0];
	            currentLocation[2]=Integer.valueOf(dataStringCoordinates[0]);
	            currentLocation[3]=width-currentLocation[2];
			}
            
            
						
		
        return currentLocation;
    }
    
    


    public static Parameters getParameters() {
        Parameters parameters = Parameters.getAllDefaultParameters();
        parameters.set(KEY.INPUT_DIMENSIONS, new int[] { 8,4 });//输入神经元的数量
        parameters.set(KEY.COLUMN_DIMENSIONS, new int[] { 4,4 });//列的维度数量
        parameters.set(KEY.CELLS_PER_COLUMN, 6);//每列有几个细胞

        //SpatialPooler specific
        parameters.set(KEY.POTENTIAL_RADIUS, 2);//潜在半径的宽度，潜在半径是以中心点左右两边的半径，这个半径不算中心点，例如设为三正常的输入宽度为3*2+1=7
        parameters.set(KEY.POTENTIAL_PCT, 0.9);//这个参数的作用是使得空间池潜在输入有多大比例被选中，由于我们这个输入只有7个，这里比例设置的高一些。
        parameters.set(KEY.GLOBAL_INHIBITION, false);//如果为真，则在抑制阶段，获胜列为整个区域中最活跃的列，否则，将根据其本地邻居选择获胜列。使用全局抑制可以提高60倍性能
        parameters.set(KEY.LOCAL_AREA_DENSITY, -1.0);//局部抑制区内激活列的期望密度，确保在局部抑制区最多有N个列被激活，N=localAreaDensity*(抑制区中列的总数）
        parameters.set(KEY.NUM_ACTIVE_COLUMNS_PER_INH_AREA, 5.0);//控制激活单元柱密度的另一种方法，如果NUM_ACTIVE_COLUMNS_PER_INH_AREA被指定，LOCAL_AREA_DENSITY必须小于0
        parameters.set(KEY.STIMULUS_THRESHOLD, 1.0);//指定使列激活必须处于激活状态的突触的最小数量。这样做的目的是防止噪波输入激活列。以完全发育的突触的百分比表示。
        parameters.set(KEY.SYN_PERM_INACTIVE_DEC, 0.0005);//每一轮中一个不活跃突触的持久度减少的值，用完全发育的突触的百分比来表示。
        parameters.set(KEY.SYN_PERM_ACTIVE_INC, 0.0015);//每一轮中一个活跃突触增加的持久度值，指定为完全发育的突触的百分比
        parameters.set(KEY.SYN_PERM_TRIM_THRESHOLD, 0.05);//这是一个派生值，由SpatialPooler算法的初始化覆盖。低于此值的值将被裁剪并置零。
        parameters.set(KEY.SYN_PERM_CONNECTED, 0.1);//默认连接阈值，任何持久度的值高于这个连接阈值的突触都是“连接的突触”，意味这个突触可以促进细胞的激活。
        parameters.set(KEY.MIN_PCT_OVERLAP_DUTY_CYCLES, 0.1);//一个0到1之间的值，用于设定一个列至少应该有刺激阈值的活跃输入的最低频率。每一列定期查看其抑制半径内所有其他列重叠占空比，
                                                             //并它内部最小可接受占空比设置为：minPctDutyCycleBeforeInh * max(其他列的占空比)。在每一次迭代中，任何重叠占空比低于
                                                             //此计算值的列将使得它的持久度的值增加synPermActiveInc。当单元先前学习到的输入不再活跃，或者当它们中的绝大多数被其他列“劫持”时，
                                                            //它就可以在抑制之前根据低于标准的占空比来提高所有的突触的持久度，从而寻找新的输入。
        parameters.set(KEY.MIN_PCT_ACTIVE_DUTY_CYCLES, 0.1);//一个0到1之间的值，用来设置一个列应该被激活的最低频率。每一列定期查看其抑制半径内所有其他列的活动占空比，并将其内部最小可接受占空比
                                                            //设置为minPctDutyCycleAfterInh *max(其他列的占空比)。在每次迭代中，抑制后占空比低于该计算值的任何列，其内部促进因子都会增加。
        parameters.set(KEY.DUTY_CYCLE_PERIOD, 10);//用于计算占空比的周期，较高的值会使相应boost或synPerConnectedCell中的更改所需要的时间更长，较小的值使他更不稳定，更容易震荡。
        parameters.set(KEY.MAX_BOOST, 10.0);//最大的重叠促进因子，在进行抑制之前，每一列的重合度都有乘以一个增强因子，领的实际的增强因子是一个1到maxBoot之间的数。如果占空系数大于等于minOverlapDutyCyle
                                            //则增强因子为0，如果占空比例为0，那么增强因子为maxBoost。其他任何占空比例被这两个端点之间的值所线性推断。
        parameters.set(KEY.SEED, 42);///随机数产生的种子。
        
        //Temporal Memory specific
        parameters.set(KEY.INITIAL_PERMANENCE, 0.2);//新的突触的初始持久度
        parameters.set(KEY.CONNECTED_PERMANENCE, 0.7);//如果突触的持久度值大于此值，则称为连接，这个有点大，需要改为0.5试试parameters.set(KEY.CONNECTED_PERMANENCE, 0.5);
        parameters.set(KEY.MIN_THRESHOLD, 4);         //如果在一个片段上激活的突触的数量至少是这个阈值，它被选为激活列中最佳匹配的单元。
        parameters.set(KEY.MAX_NEW_SYNAPSE_COUNT, 6);//在学习过程中加入到一个树突的最大突触数。
        parameters.set(KEY.PERMANENCE_INCREMENT, 0.1);//0.05在学习过程中，突触持久度增加的量
        parameters.set(KEY.PERMANENCE_DECREMENT, 0.1);//0.05在学习过程中，突触持久度减少的量
        parameters.set(KEY.ACTIVATION_THRESHOLD, 4);//如果一个片段上活跃连接的突触的数量至少是这个阈值，那么这个片段就成为是活跃的。
        parameters.set(KEY.LEARNING_RADIUS, 20);
        
        parameters.set(KEY.RANDOM, new FastRandom());//创建新的伪随机数生成器，种子被初始化为当前时间，就像通过setSeed(System.currentTimeMillis())

        return parameters;
    }

    public static <T> void runThroughLayer(Layer<T> l, T valueNorth,T valueSouth, T valueWest,T valueEast, int sequenceNum) {
        l.input(valueNorth, valueSouth, valueWest, valueEast, sequenceNum);
    }

    public static Layer<Integer> getLayer(Parameters p, ScalarEncoder e, SpatialPooler s, TemporalMemory t, SDRClassifier c) {
        Layer<Integer> l = new LayerImpl(p, e, s, t, c);
        return l;
    }

    ////////////////// Preliminary Network API Toy ///////////////////

    interface Layer<T> {
        public void input(T valueNorth,T valueSouth, T valueWest,T valueEast, int iteration);
        public int[] getPredicted();
        public Connections getMemory();
        public int[] getActual();
    }

    /**
     * I'm going to make an actual Layer, this is just temporary so I can
     * work out the details while I'm completing this for Peter
     *
     * @author David Ray
     *
     */
    static class LayerImpl implements Layer<Integer> {
        private Parameters params;

        private Connections memory = new Connections();//memory就是Connections

        private ScalarEncoder encoder;
        private SpatialPooler spatialPooler;
        private TemporalMemory temporalMemory;
        private SDRClassifier classifier;
        private Map<String, Object> classification = new LinkedHashMap<String, Object>();

        private int columnCount;
        private int cellsPerColumn;
        private int theNum;//标记迭代次数

        private int[] predictedColumns;
        private int[] actual;
        private int[] lastPredicted;

        public LayerImpl(Parameters p, ScalarEncoder e, SpatialPooler s, TemporalMemory t, SDRClassifier c) {
            this.params = p;
            this.encoder = e;
            this.spatialPooler = s;
            this.temporalMemory = t;
            this.classifier = c;

            params.apply(memory);
            spatialPooler.init(memory);//初始化空间池
            TemporalMemory.init(memory);//初始化时间池，主要是建立了一个所有单元的数组

            columnCount = memory.getPotentialPools().getMaxIndex() + 1; //If necessary, flatten multi-dimensional index 获取列的数量
            cellsPerColumn = memory.getCellsPerColumn();//获取每一列都有多个单元
        }
        

        @Override
        public void input(Integer valueNorth,Integer valueSouth, Integer valueWest,Integer valueEast, int sequenceNum) {
       
        	if (sequenceNum==0) {
				System.out.print(sequenceNum);
			}
            if (sequenceNum==49) {
				System.out.print(sequenceNum);
			}
            if (sequenceNum==199) {
				System.out.print(sequenceNum);
			}
            if(sequenceNum==1400)
            {
            	System.out.print(sequenceNum);
            }
            if(theNum==200)
            {
				System.out.print(sequenceNum);
			}
            int[] output = new int[columnCount];//记录每个列的输出值的数组

            //Input through encoder
            int[] encoding_North = encoder.encode(valueNorth.doubleValue());//获取距离北边缘的编码值
            int[] encoding_South = encoder.encode(valueSouth.doubleValue());//获取距离南边缘的编码值
            int[] encoding_East = encoder.encode(valueEast.doubleValue());//获取距离东边缘的编码值
            int[] encoding_West = encoder.encode(valueWest.doubleValue());//获取距离西边缘的编码值
            
            int[] encoding=contact(contact(encoding_North, encoding_South),contact(encoding_West, encoding_East));///把这四个距离合并成一个编码
                        
            System.out.println("ScalarEncoder Output = " + Arrays.toString(encoding));
            
            //int bucketIdx = encoder.getBucketIndices(value)[0];//获取bucket索引，如果编码是循环的，是中间位的索引；如果编码不是循环的，是左侧位的索引

            //Input through spatial pooler
            spatialPooler.compute(memory, encoding, output, true);
            System.out.println("SpatialPooler Output = " + Arrays.toString(output));
            theNum=sequenceNum;
            // Let the SpatialPooler train independently (warm up) first，首先让SpatialPooler单独训练（预热），训练200次以后再去训练TemporalMemory
            if(theNum < 2) return;
            
            //Input through temporal memory
            int[] input = actual = ArrayUtils.where(output, ArrayUtils.WHERE_1);//SpatialPool轮实际激活的列的索引
            ComputeCycle cc = temporalMemory.compute(memory, input, true);
            lastPredicted = predictedColumns;
            predictedColumns = getSDR(cc.predictiveCells()); //Get the predicted column indexes获取了预测单元集合所对应的列的索引集合
            int[] activeCellIndexes = Connections.asCellIndexes(cc.activeCells()).stream().mapToInt(i -> i).sorted().toArray();  //Get the active cells for classifier input
            System.out.println("TemporalMemory Input = " + Arrays.toString(input));
            System.out.println("TemporalMemory Prediction = " + Arrays.toString(predictedColumns));

                        
            //这里找几个值，当他的输出值稳定之后，我们认为训练完成
            if (sequenceNum==200000) //每100
            {
            	for(int i=0;i<5;i++)
            	{
            		for(int j=0;j<5;j++)
            		{
            			int[] constvalue=generateDistanceEncoding(j, 4-j, i, 4-i);//j是离北面的距离，i是离西面的距离
            			int[] outputResult =  new int[columnCount];
            			spatialPooler.compute(memory, constvalue, outputResult, false);
                        //System.out.println("SpatialPooler Output For ConstantValue = " + Arrays.toString(output));
                        writeSparseOutputToTxt(outputResult,"D:/workspace/resultSP.txt");
                        int[] inputResult = actual = ArrayUtils.where(outputResult, ArrayUtils.WHERE_1);//SpatialPool轮实际激活的列的索引
                        ComputeCycle ccResult= temporalMemory.compute(memory, inputResult, false);
                        int[] predictedCellIndexResult = Connections.asCellIndexes(ccResult.predictiveCells()).stream().mapToInt(k -> k).sorted().toArray(); //Get the predicted column indexes获取了预测单元集合所对应的列的索引集合
                        int[] activeCellIndexesResult = Connections.asCellIndexes(ccResult.activeCells()).stream().mapToInt(k -> k).sorted().toArray();  //Get the active cells for classifier input
                        int[] winnerCellIndexesResult = Connections.asCellIndexes(ccResult.winnerCells()).stream().mapToInt(k -> k).sorted().toArray();  //Get the active cells for classifier input

                        writeOutputToTxt(activeCellIndexesResult, "D:/workspace/resultSPActive.txt");
                        writeOutputToTxt(winnerCellIndexesResult, "D:/workspace/resultSPWinner.txt");
                        writeOutputToTxt(predictedCellIndexResult, "D:/workspace/resultSPPredicated.txt");
                        
            		}
            	}         	           	            	 
			}
            
            //classification.put("bucketIdx", bucketIdx);
            //classification.put("actValue", value);
            
            classifier.setPeriodic(false);
            Classification<Double> result = classifier.compute(sequenceNum, classification, activeCellIndexes, true, true);
           
            System.out.println("  |  CLAClassifier 1 step prob = " + Arrays.toString(result.getStats(1)) + "\n");

            System.out.println("");
        }
        
        
        public int[] generateDistanceEncoding(Integer valueNorth,Integer valueSouth, Integer valueWest,Integer valueEast)
        {
        	//Input through encoder
            int[] encoding_North = encoder.encode(valueNorth.doubleValue());//获取距离北边缘的编码值
            int[] encoding_South = encoder.encode(valueSouth.doubleValue());//获取距离南边缘的编码值
            int[] encoding_West = encoder.encode(valueWest.doubleValue());//获取距离西边缘的编码值
            int[] encoding_East = encoder.encode(valueEast.doubleValue());//获取距离东边缘的编码值
            
            int[] encoding=contact(contact(encoding_North, encoding_South),contact(encoding_West, encoding_East));///把这四个距离合并成一个编码
            return encoding;
        }
        
        /**
         * 把稀疏数组写入txt文档
         * @param activeArray
         */
        public void writeSparseOutputToTxt( int[] activeArray,String path)
        {
        	try 
        	{
        		FileWriter resultFile=new FileWriter(path,true);
        		resultFile.write("[");
            	if (activeArray!=null) 
            	{
					for (int i = 0; i < activeArray.length; i++) {
						if (activeArray[i]==1) 
						{
							resultFile.write(i+", ");
						}
						
					}
				}
            	resultFile.write("]");
            	resultFile.write("\r\n");//换行
            	resultFile.close();//关闭文件
			}
        	catch (Exception e) {
				// TODO: handle exception
			}       	      	
        }
        /**
         * 把稀疏数组写入txt文档
         * @param activeArray
         */
        public void writeOutputToTxt( int[] activeArray,String path)
        {
        	try 
        	{
        		FileWriter resultFile=new FileWriter(path,true);
        		resultFile.write("[");
            	if (activeArray!=null) 
            	{
					for (int i = 0; i < activeArray.length; i++) {
						//if (activeArray[i]==1) 
						{
							resultFile.write(activeArray[i]+", ");
						}
						
					}
				}
            	resultFile.write("]");
            	resultFile.write("\r\n");//换行
            	resultFile.close();//关闭文件
			}
        	catch (Exception e) {
				// TODO: handle exception
			}       	      	
        }
        
    /**
     * 把两个int数组拼接成一个    
     * @param a 第一个数组
     * @param b 第二个数组
     * @return 合并后的int数组
     */
    public static int[] contact(int[] a, int[] b) {
            int[] result = new int[a.length + b.length];
            for (int i = 0; i < result.length; i++) {
                if (i < a.length) {
                    result[i] = a[i];
                } else {
                    result[i] = b[i - a.length];
                }
            }
            return result;
        }



        public int[] inflateSDR(int[] SDR, int len) {
            int[] retVal = new int[len];
            for(int i : SDR) {
                retVal[i] = 1;
            }
            return retVal;
        }
        /**相当于是获取了预测单元集合所对应的列的索引集合***/
        public int[] getSDR(Set<Cell> cells) {
            int[] retVal = new int[cells.size()];//创建一个数组，数组中元素的数量等于预测单元的数量
            int i = 0;
            for(Iterator<Cell> it = cells.iterator();i < retVal.length;i++) {//获取每一个单元
                retVal[i] = it.next().getIndex();//获取这个单元的索引
                retVal[i] /= cellsPerColumn; // Get the column index把这个单元的索引整除以每个列拥有的列的数量，获取这个单元所在列的索引号，并赋值给retVal数组
            }
            Arrays.sort(retVal);//对列索引号数组进行升序排序
            retVal = ArrayUtils.unique(retVal);//返回已经排序的唯一整数数组

            return retVal;
        }

        /**
         * Returns the next predicted value.
         *
         * @return the SDR representing the prediction
         */
        @Override
        public int[] getPredicted() {
            return lastPredicted;
        }

        /**
         * Returns the actual columns in time t + 1 to compare
         * with {@link #getPrediction()} which returns the prediction
         * at time t for time t + 1.
         * @return
         */
        @Override
        public int[] getActual() {
            return actual;
        }

        /**
         * Simple getter for external reset
         * @return
         */
        public Connections getMemory() {
            return memory;
        }
    }


}
