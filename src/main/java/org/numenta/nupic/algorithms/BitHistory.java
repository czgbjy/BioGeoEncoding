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

import org.numenta.nupic.model.Persistable;
import org.numenta.nupic.util.ArrayUtils;

import gnu.trove.list.TDoubleList;
import gnu.trove.list.array.TDoubleArrayList;

/**
 * Stores an activationPattern bit history.
 * 
 * @author David Ray
 * @see CLAClassifier
 */
public class BitHistory implements Persistable {
    private static final long serialVersionUID = 1L;

    /** Store reference to the classifier 存储到classifier的引用*/
    CLAClassifier classifier;
    /** Form our "id" 形成id*/
    String id;
    /**
     * Dictionary of bucket entries. The key is the bucket index, the
     * value is the dutyCycle, which is the rolling average of the duty cycle bucket实体的词典，键值是bucket的索引，值是dutyCycle,是duty Cycle bucket的滚动平均值
     */
    TDoubleList stats;
    /** lastUpdate is the iteration number of the last time it was updated. lastUpdate是最后一次更新时的循环标记 */
    int lastTotalUpdate = -1;

    // This determines how large one of the duty cycles must get before each of the
    // duty cycles are updated to the current iteration.
    // This must be less than float32 size since storage is float32 size
    private static final int DUTY_CYCLE_UPDATE_INTERVAL = Integer.MAX_VALUE;


    /**
     * Package protected constructor for serialization purposes.
     */
    BitHistory() {}

    /**
     * Constructs a new {@code BitHistory}
     * 
     * @param classifier    instance of the {@link CLAClassifier} that owns us
     * @param bitNum        activation pattern bit number this history is for,
     *                      used only for debug messages
     * @param nSteps        number of steps of prediction this history is for, used
     *                      only for debug messages
     */
    public BitHistory(CLAClassifier classifier, int bitNum, int nSteps) {
        this.classifier = classifier;
        this.id = String.format("%d[%d]", bitNum, nSteps);
        this.stats = new TDoubleArrayList();
    }

    /**
     * Store a new item in our history. 在历史记录中存储新的条目
     * <p>
     * This gets called for a bit whenever it is active and learning is enabled 这个函数因输入比特被调用，在它激活和可学习的时候
     * <p>
     * Save duty cycle by normalizing it to the same iteration as
     * the rest of the duty cycles which is lastTotalUpdate. 通过归一化近期活性为相同的迭代来保存近期活性，lastTotalUpdate代表的近期活性的剩余值
     * <p>
     * This is done to speed up computation in inference since all of the duty
     * cycles can now be scaled by a single number.这样做是为了加速推理中的计算，因为所有近期活性都可以通过单个数字进行缩放
     * <p>
     * The duty cycle is brought up to the current iteration only at inference and
     * only when one of the duty cycles gets too large (to avoid overflow to
     * larger data type) since the ratios between the duty cycles are what is
     * important. As long as all of the duty cycles are at the same iteration
     * their ratio is the same as it would be for any other iteration, because the
     * update is simply a multiplication by a scalar that depends on the number of
     * steps between the last update of the duty cycle and the current iteration.
     * 由于近期活性值之间的比例很重要，因此只有在推断时并且只有其中一个近期活性变得很大时，近期活性才会上升到当前的迭代。只要所有的近期活性在相同的迭代，他们的比率就与任何其他迭代的比率相同，因为更新只是一个标量的乘法，这个标量依赖于这个近期活性的最后更新和当前迭代之间的步数的数量
     * @param iteration     the learning iteration number, which is only incremented
     *                      when learning is enabled学习迭代的数，当被设置为可学习时，仅仅增长
     * @param bucketIdx     the bucket index to store 要存储的桶的索引
     */
    public void store(int iteration, int bucketIdx) {
        // If lastTotalUpdate has not been set, set it to the current iteration.如果lastTotalUpdate没有被设置，把他设置为当前的迭代
        if(lastTotalUpdate == -1) {
            lastTotalUpdate = iteration;
        }

        // Get the duty cycle stored for this bucket.
        int statsLen = stats.size() - 1;//获取为这个bucket存储的近期活性值
        if(bucketIdx > statsLen) {//如果bucketIdx大于statsLen
            stats.add(new double[bucketIdx - statsLen]);//创建一个double数组，数组中的元素个数为bucketIdx-statsLen,并把这个double数组添加到这个TDoubleArrayList数组中0
        }

        // Update it now.更新它
        // duty cycle n steps ago is dc{-n} 近期活性值n步以前是dc{-n}
        // duty cycle for current iteration is (1-alpha)*dc{-n}*(1-alpha)**(n)+alpha 当前迭代的近期活性是
        double dc = stats.get(bucketIdx);//获取当前bucketIdx对应的值

        // To get the duty cycle from n iterations ago that when updated to the 为了从n次以前的迭代中获取近期活性值，这个n次以前的迭代更新到当前迭代，将等于当前迭代的dc
        // current iteration would equal the dc of the current iteration we simply 我们仅仅用(1-alpha)**(n)除近期活性。在这个公式中的结果dc'(-n)=dc(-n)+alpha/(1-alpha)**n
        // divide the duty cycle by (1-alpha)**(n). This results in the formula 这个撇号被用来标识这是一个在这次迭代的新的近期活性值。它等同于近期活性值值dc(-n)
        // dc'{-n} = dc{-n} + alpha/(1-alpha)**n where the apostrophe symbol is used
        // to denote that this is the new duty cycle at that iteration. This is
        // equivalent to the duty cycle dc{-n}
        double denom = Math.pow((1.0 - classifier.alpha), (iteration - lastTotalUpdate));

        double dcNew = 0;
        if(denom > 0) dcNew = dc + (classifier.alpha / denom);

        // This is to prevent errors associated with infinite rescale if too large
        if(denom == 0 || dcNew > DUTY_CYCLE_UPDATE_INTERVAL) {
            double exp = Math.pow((1.0 - classifier.alpha), (iteration - lastTotalUpdate));
            double dcT = 0;
            for(int i = 0;i < stats.size();i++) {
				dcT = stats.get(i);
                dcT *= exp;
                stats.set(i, dcT);
            }

            // Reset time since last update
            lastTotalUpdate = iteration;

            // Add alpha since now exponent is 0
            dc = stats.get(bucketIdx) + classifier.alpha;
        } else {
            dc = dcNew;
        }

        stats.set(bucketIdx, dc);
        if(classifier.verbosity >= 2) {
            System.out.println(String.format("updated DC for %s,  bucket %d to %f", id, bucketIdx, dc));
        }
    }

    /**
     * Look up and return the votes for each bucketIdx for this bit.
     * 查找和返回这个bit的每一个bucketIdx的投票
     * @param iteration     the learning iteration number, which is only incremented
     *                      when learning is enabled
     * @param votes         array, initialized to all 0's, that should be filled
     *                      in with the votes for each bucket. The vote for bucket index N
     *                      should go into votes[N].
     */
    public void infer(int iteration, double[] votes) {
        // Place the duty cycle into the votes and update the running total for
        // normalization  把近期活性放入这个votes并且更新这运行总和，目的是实现规范化
        double total = 0;
        for(int i = 0;i < stats.size();i++) {
            double dc = stats.get(i);
            if(dc > 0.0) {
                votes[i] = dc;
                total += dc;
            }
        }

        // Experiment... try normalizing the votes from each bit
        if(total > 0) {
            double[] temp = ArrayUtils.divide(votes, total);
            for(int i = 0;i < temp.length;i++) votes[i] = temp[i];
        }

        if(classifier.verbosity >= 2) {
            System.out.println(String.format("bucket votes for %s:", id, pFormatArray(votes)));
        }
    }

    /**
     * Return a string with pretty-print of an array using the given format
     * for each element
     * 
     * @param arr
     * @return
     */
    private String pFormatArray(double[] arr) {
        StringBuilder sb = new StringBuilder("[ ");
        for(double d : arr) {
            sb.append(String.format("%.2f ", d));
        }
        sb.append("]");
        return sb.toString();
    }
}
