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

package org.numenta.nupic.algorithms;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.numenta.nupic.model.Persistable;
import org.numenta.nupic.util.ArrayUtils;
import org.numenta.nupic.util.Deque;
import org.numenta.nupic.util.Tuple;

import gnu.trove.list.TIntList;
import gnu.trove.list.array.TIntArrayList;

/**
 * A CLA classifier accepts a binary input from the level below (the
 * "activationPattern") and information from the sensor and encoders (the
 * "classification") describing the input to the system at that time step.
 *  CLA分类器接受二进制的输入，这个输入来自下层和感受器和编码器的信息，描述在该时间向系统输入的信息
 * When learning, for every bit in activation pattern, it records a history of the
 * classification each time that bit was active. The history is weighted so that
 * more recent activity has a bigger impact than older activity. The alpha
 * parameter controls this weighting.
 *当学习的时候，对于每一个激活模式的bit,他记录这个比特每次激活的分类的历史。这个历史被加权，以便较为近期的活动比较老的活动产生更大的影响，这个alpha参数控制这个权重
 * For inference, it takes an ensemble approach. For every active bit in the
 * activationPattern, it looks up the most likely classification(s) from the
 * history stored for that bit and then votes across these to get the resulting
 * classification(s).
 * 对于推理，他采用了集成方法，对于在激活模式中每个激活比特，他从这个bit的历史存储中查找最可能的分类，然后对这些分类进行投票以获取分类结果
 * This classifier can learn and infer a number of simultaneous classifications
 * at once, each representing a shift of a different number of time steps. For
 * example, say you are doing multi-step prediction and want the predictions for
 * 1 and 3 time steps in advance. The CLAClassifier would learn the associations
 * between the activation pattern for time step T and the classifications for
 * time step T+1, as well as the associations between activation pattern T and
 * the classifications for T+3. The 'steps' constructor argument specifies the
 * list of time-steps you want.
 * 该分类器可以立即学习和推断多个同时分类，每个分类表示不同时间不长的移动。例如，假设您正在进行多步预测，并希望提前对1和3时间点进行预测。CLAClassifier将会学习时间步T的激活模式与时间T+1的分类之间的关联，以及激活模式T与T+3的分类之间的关联。”steps"构造函数参数指定所需的时间步长列表。
 * @author Numenta
 * @author David Ray
 * @see BitHistory
 */
public class CLAClassifier implements Persistable, Classifier {
    private static final long serialVersionUID = 1L;

    int verbosity = 0;
    /**
     * The alpha used to compute running averages of the bucket duty
     * cycles for each activation pattern bit. A lower alpha results
     * in longer term memory.用于计算每个激活比特模式的桶占空比运行平均值，较低的alpha会导致更长的记忆。
     */
    double alpha = 0.001;
    double actValueAlpha = 0.3;
    /** 
     * The bit's learning iteration. This is updated each time store() gets
     * called on this bit.比特的学习迭代，每次对此位调用store()函数时，都会更新此值。
     */
    int learnIteration;
    /**
     * This contains the offset between the recordNum (provided by caller) and
     * learnIteration (internal only, always starts at 0).它包含recordNum(由调用者提供）和learn迭代（仅限内部，总是从0开始）之间的偏移量
     */
    int recordNumMinusLearnIteration = -1;
    /**
     * This contains the value of the highest bucket index we've ever seen
     * It is used to pre-allocate fixed size arrays that hold the weights of
     * each bucket index during inference 他包含了我们见过的最高的bucket索引的值，用于预先分配固定大小的数组，这些数组在推理过程中保存每个bucket索引的权重。
     */
    int maxBucketIdx;
    /** The sequence different steps of multi-step predictions多步预测的不同步骤的顺序 */
    TIntList steps = new TIntArrayList();
    /**
     * History of the last _maxSteps activation patterns. We need to keep
     * these so that we can associate the current iteration's classification
     * with the activationPattern from N steps ago。上一个maxSteps激活模式的历史记录。我们需要保留这些，以便我们可以将当前迭代的分类与N步前的激活模式相关联。
     */
    Deque<Tuple> patternNZHistory;
    /**
     * These are the bit histories. Each one is a BitHistory instance, stored in
     * this dict, where the key is (bit, nSteps). The 'bit' is the index of the
     * bit in the activation pattern and nSteps is the number of steps of
     * prediction desired for that bit.存储比特历史，每一个都是一个BitHistory实例，存储在这个dict中，其中key值是(bit,nSteps).bit是激活模式中位的索引，nSteps是该位所需的预测步数。
     */
    Map<Tuple, BitHistory> activeBitHistory = new HashMap<Tuple, BitHistory>();
    /**
     * This keeps track of the actual value to use for each bucket index. We
     * start with 1 bucket, no actual value so that the first infer has something
     * to return.它跟踪每个bucket索引要使用的实际值。我们从1个bucket开始，没有实际值，这样第一个推断就可以返回一些东西。
     */
    List<?> actualValues = new ArrayList<Object>();

    String g_debugPrefix = "CLAClassifier";


    /**
     * CLAClassifier no-arg constructor with defaults
     */
    public CLAClassifier() {
        this(new TIntArrayList(new int[] { 1 }), 0.001, 0.3, 0);
    }

    /**
     * Constructor for the CLA classifier
     * 
     * @param steps             sequence of the different steps of multi-step predictions to learn（需要学习的多步预测的不同步骤的序列）
     * @param alpha             The alpha used to compute running averages of the bucket duty
                                cycles for each activation pattern bit. A lower alpha results
                                in longer term memory.（用来计算每一个激活比特模式的桶占空比运行平均值，一个低的alpha值导致更长的时间记忆）
     * @param actValueAlpha
     * @param verbosity         verbosity level, can be 0, 1, or 2 冗余水平，可以是0,1，或者2
     */
    public CLAClassifier(TIntList steps, double alpha, double actValueAlpha, int verbosity) {
        this.steps = steps;
        this.alpha = alpha;
        this.actValueAlpha = actValueAlpha;
        this.verbosity = verbosity;
        actualValues.add(null);//它跟踪每个bucket索引要使用的实际值。我们从1个bucket开始，没有实际值，这样第一个推断就可以返回一些东西。
        patternNZHistory = new Deque<Tuple>(ArrayUtils.max(steps.toArray()) + 1);//上一个maxSteps激活模式的历史记录。我们需要保留这些，以便我们可以将当前迭代的分类与N步前的激活模式相关联。这是一个具有固定大小的双端队列。Tuple是一个存储对象的列表
        ////固定大小的双端队列的大小为ArrayUtils.max(steps.toArray()) + 1，队列中的元素为一个Tuple,存储多个对象的列表
    }

    /**
     * Process one input sample.
     * This method is called by outer loop code outside the nupic-engine. We
     * use this instead of the nupic engine compute() because our inputs and
     * outputs aren't fixed size vectors of reals.
     * 
     * @param recordNum         Record number of this input pattern. Record numbers should
     *                          normally increase sequentially by 1 each time unless there
     *                          are missing records in the dataset. Knowing this information
     *                          insures that we don't get confused by missing records.
     * @param classification    {@link Map} of the classification information:
     *                          bucketIdx: index of the encoder bucket
     *                          actValue:  actual value going into the encoder
     * @param patternNZ         list of the active indices from the output below
     * @param learn             if true, learn this sample
     * @param infer             if true, perform inference
     * 
     * @return                  {@link Classification} containing inference results, there is one entry for each
     *                          step in steps, where the key is the number of steps, and
     *                          the value is an array containing the relative likelihood for
     *                          each bucketIdx starting from bucketIdx 0.
     *
     *                          There is also an entry containing the average actual value to
     *                          use for each bucket. The key is 'actualValues'.
     *
     *                          for example:
     *                          {	
     *                              1 :             [0.1, 0.3, 0.2, 0.7],
     *                              4 :             [0.2, 0.4, 0.3, 0.5],
     *                              'actualValues': [1.5, 3.5, 5.5, 7.6],
     *                          }
     */
    @SuppressWarnings("unchecked")
    public <T> Classification<T> compute(int recordNum, Map<String, Object> classification, int[] patternNZ, boolean learn, boolean infer) {
        Classification<T> retVal = new Classification<T>();//存储算法计算结果的容器
        List<T> actualValues = (List<T>)this.actualValues;//实际值列表

        // Save the offset between recordNum and learnIteration if this is the first
        // compute
        if(recordNumMinusLearnIteration == -1) {
            recordNumMinusLearnIteration = recordNum - learnIteration;//记录数和学习迭代次数的差值
        }

        // Update the learn iteration
        learnIteration = recordNum - recordNumMinusLearnIteration;//学习迭代次数的值为记录数与记录数和学习迭代次数的差值

        if(verbosity >= 1) {
            System.out.println(String.format("\n%s: compute ", g_debugPrefix));
            System.out.println(" recordNum: " + recordNum);
            System.out.println(" learnIteration: " + learnIteration);
            System.out.println(String.format(" patternNZ(%d): ", patternNZ.length, patternNZ));
            System.out.println(" classificationIn: " + classification);
        }

        patternNZHistory.append(new Tuple(learnIteration, patternNZ));//把两个对象即learnIteration和patternNZ存储入tuple，把这个Tuple放入双端数组

        //------------------------------------------------------------------------
        // Inference:
        // For each active bit in the activationPattern, get the classification
        // votes
        // 对于每一个激活的比特位在这激活的模式里面，获取分类的投票。
        // Return value dict. For buckets which we don't have an actual value
        // for yet, just plug in any valid actual value. It doesn't matter what
        // we use because that bucket won't have non-zero likelihood anyways. 返回值词典。对于还没有实际的bucket，插入任何实际的值。我们用什么没有关系因为bucket无论如何都不会有非零的可能性
        if(infer) {
            // NOTE: If doing 0-step prediction, we shouldn't use any knowledge
            //		 of the classification input during inference.如果执行0步预测，我们不应该使用分类输入的任何知识，在推理过程中。
            Object defaultValue = null;
            if(steps.get(0) == 0) {
                defaultValue = 0;
            }else{
                defaultValue = classification.get("actValue");//把默认值设置为actValue
            }

            T[] actValues = (T[])new Object[this.actualValues.size()];
            for(int i = 0;i < actualValues.size();i++) {
                actValues[i] = (T)(actualValues.get(i) == null ? defaultValue : actualValues.get(i));//把默认值赋值给实际值
            }

            retVal.setActualValues(actValues);///设置实际值

            // For each n-step prediction...
            for(int nSteps : steps.toArray()) {
                // Accumulate bucket index votes and actValues into these arrays
                double[] sumVotes = new double[maxBucketIdx + 1];//积累bucket索引投票到这些数组中
                double[] bitVotes = new double[maxBucketIdx + 1];//积累actValues到这些数组中

                for(int bit : patternNZ) {//对于每一个激活的单元
                    Tuple key = new Tuple(bit, nSteps);//新建一个Tuple,里面存储了这个激活单元的索引，和步长
                    BitHistory history = activeBitHistory.get(key);
                    if(history == null) continue;

                    history.infer(learnIteration, bitVotes);//把近期活性放入这个votes并且更新这运行总和，目的是实现规范化

                    sumVotes = ArrayUtils.d_add(sumVotes, bitVotes);
                }

                // Return the votes for each bucket, normalized
                double total = ArrayUtils.sum(sumVotes);
                if(total > 0) {
                    sumVotes = ArrayUtils.divide(sumVotes, total);
                }else{
                    // If all buckets have zero probability then simply make all of the
                    // buckets equally likely. There is no actual prediction for this
                    // timestep so any of the possible predictions are just as good.
                    if(sumVotes.length > 0) {
                        Arrays.fill(sumVotes, 1.0 / (double)sumVotes.length);
                    }
                }

                retVal.setStats(nSteps, sumVotes);
            }
        }

        // ------------------------------------------------------------------------
        // Learning:
        // For each active bit in the activationPattern, store the classification
        // info. If the bucketIdx is None, we can't learn. This can happen when the
        // field is missing in a specific record.
        if(learn && classification.get("bucketIdx") != null) {//如果设置了学习，并且classification中存储了bucket的Id和真实值
            // Get classification info
            int bucketIdx = (int)classification.get("bucketIdx");//获取桶的id
            Object actValue = classification.get("actValue");//获取真实值

            // Update maxBucketIndex
            maxBucketIdx = (int) Math.max(maxBucketIdx, bucketIdx);//更新maxBucketIdx的值，查验当前的bucketId是不是最大的id,获取最大的bucketId

            // Update rolling average of actual values if it's a scalar. If it's
            // not, it must be a category, in which case each bucket only ever
            // sees one category so we don't need a running average.如果是一个标量，则更新实际值的滚动平均值。如果不是，它必须是一个类别，在这种情况下，每个桶只能看到一个类别，所以我们不需要运行平均值。
            while(maxBucketIdx > actualValues.size() - 1) {
                actualValues.add(null);
            }
            if(actualValues.get(bucketIdx) == null) {//从actualValues数组中获取bucketIdx指定的值，如果为空的话
                actualValues.set(bucketIdx, (T)actValue);//把actValue值添加到这个数组中去
            }else{
                if(Number.class.isAssignableFrom(actValue.getClass())) {//isAssignableFrom方法判断前者是否是后者的父类或者和后者类型相同或者后者实现了前者接口，是类与类之间的比较。
                    Double val = ((1.0 - actValueAlpha) * ((Number)actualValues.get(bucketIdx)).doubleValue() + 
                                    actValueAlpha * ((Number)actValue).doubleValue());
                    actualValues.set(bucketIdx, (T)val);
                }else{
                    actualValues.set(bucketIdx, (T)actValue);
                }
            }

            // Train each pattern that we have in our history that aligns with the
            // steps we have in steps 训练我们之前的每一种模式，使其与我们在步骤中的步骤保持一致
            int nSteps = -1;
            int iteration = 0;
            int[] learnPatternNZ = null;
            for(int n : steps.toArray()) {
                nSteps = n;
                // Do we have the pattern that should be assigned to this classification
                // in our pattern history? If not, skip it 在模式历史里面，我们是否有应该分配到这个分类的模式，如果没有，忽略他。
                boolean found = false;
                for(Tuple t : patternNZHistory) {//对每一种历史的模式
                    iteration = (int)t.get(0);//获取这个历史模式的时间
                    learnPatternNZ = (int[]) t.get(1);//获取这个模式激活的单元列表
                    if(iteration == learnIteration - nSteps) {//如果这个模式的时间点等于学习的次数减去当前时间
                        found = true;
                        break;
                    }
                    iteration++;
                }
                if(!found) continue;

                // Store classification info for each active bit from the pattern
                // that we got nSteps time steps ago.对于每一个激活的列存储其分类信息，这个激活的比特来自于我们n步骤以前的模式
                for(int bit : learnPatternNZ) {
                    // Get the history structure for this bit and step
                    Tuple key = new Tuple(bit, nSteps);
                    BitHistory history = activeBitHistory.get(key);
                    if(history == null) {
                        activeBitHistory.put(key, history = new BitHistory(this, bit, nSteps));
                    }
                    history.store(learnIteration, bucketIdx);
                }
            }
        }

        if(infer && verbosity >= 1) {
            System.out.println(" inference: combined bucket likelihoods:");
            System.out.println("   actual bucket values: " + Arrays.toString((T[])retVal.getActualValues()));

            for(int key : retVal.stepSet()) {
                if(retVal.getActualValue(key) == null) continue;

                Object[] actual = new Object[] { (T)retVal.getActualValue(key) };
                System.out.println(String.format("  %d steps: ", key, pFormatArray(actual)));
                int bestBucketIdx = retVal.getMostProbableBucketIndex(key);
                System.out.println(String.format("   most likely bucket idx: %d, value: %s ", bestBucketIdx, 
                                retVal.getActualValue(bestBucketIdx)));

            }
        }

        return retVal;
    }

    /**
     * Return a string with pretty-print of an array using the given format
     * for each element
     * 
     * @param arr
     * @return
     */
    private <T> String pFormatArray(T[] arr) {
        if(arr == null) return "";

        StringBuilder sb = new StringBuilder("[ ");
        for(T t : arr) {
            sb.append(String.format("%.2s", t));
        }
        sb.append(" ]");
        return sb.toString();
    }

}
