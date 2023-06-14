package org.numenta.nupic.algorithms;

import java.util.Arrays;

import org.numenta.nupic.model.Persistable;

import gnu.trove.map.TIntObjectMap;
import gnu.trove.map.hash.TIntObjectHashMap;

/**
 * Container for the results of a classification computation by the
 * {@link CLAClassifier}
 * 由CLAClassifier实施的分类计算的结果的容器
 * @author David Ray
 *
 * @param <T>
 */
public class Classification<T> implements Persistable {
    private static final long serialVersionUID = 1L;

    /** Array of actual values */
    private T[] actualValues;//存储实际值的数组

    /** Map of step count -to- probabilities */
    TIntObjectMap<double[]> probabilities = new TIntObjectHashMap<double[]>();//存储double[]数组的HashMap对象
    
    /**
     * Utility method to copy the contents of a ClassifierResult.
     * 
     * @return  a copy of this {@code ClassifierResult} which will not be affected
     * by changes to the original.
     */
    public Classification<T> copy() {
        Classification<T> retVal = new Classification<T>();
        retVal.actualValues = Arrays.copyOf(actualValues, actualValues.length);
        retVal.probabilities = new TIntObjectHashMap<double[]>(probabilities);
        
        return retVal;
    }

    /**
     * Returns the actual value for the specified bucket index
     * 返回由指定的bucket索引指定的实际值（actual value)
     * @param bucketIndex 桶的索引值
     * @return
     */
    public T getActualValue(int bucketIndex) {
        if(actualValues == null || actualValues.length < bucketIndex + 1) {
            return null;
        }
        return (T)Double.valueOf(actualValues[bucketIndex].toString());
    }

    /**
     * Returns all actual values entered
     * 获取所有的实际值
     * @return  array of type &lt;T&gt;
     */
    public T[] getActualValues() {
        return actualValues;
    }

    /**
     * Sets the array of actual values being entered.
     * 设置实际值
     * @param values
     * @param &lt;T&gt;[]	the value array type
     */
    public void setActualValues(T[] values) {
        actualValues = values;
    }

    /**
     * Returns a count of actual values entered 获取实际值的数量
     * @return
     */
    public int getActualValueCount() {
        return actualValues.length;
    }

    /**
     * Returns the probability at the specified index for the given step 返回给定时间点的给定索引指定的概率
     * @param step
     * @param bucketIndex
     * @return
     */
    public double getStat(int step, int bucketIndex) {
        return probabilities.get(step)[bucketIndex]; //
    }

    /**
     * Sets the array of probabilities for the specified step 对给定的时间点设置概率的数组
     * @param step
     * @param votes
     */
    public void setStats(int step, double[] votes) {
        probabilities.put(step, votes);
    }

    /**
     * Returns the probabilities for the specified step 返回指定时间点的概率数组
     * @param step
     * @return
     */
    public double[] getStats(int step) {
        return probabilities.get(step);
    }

    /**
     * Returns the input value corresponding with the highest probability
     * for the specified step.
     * 对于指定的时间点，返回最大概率对应的输入值
     * @param step		the step key under which the most probable value will be returned.
     * @return
     */
    public T getMostProbableValue(int step) {
        int idx = -1;
        if(probabilities.get(step) == null || (idx = getMostProbableBucketIndex(step)) == -1) {//获取最高概率对应的bucketIndex
            return null;
        }
        return getActualValue(idx);
    }

    /**
     * Returns the bucket index corresponding with the highest probability
     * for the specified step.
     * 对指定的时间点，返回最高概率值对应的bucket索引
     * @param step		the step key under which the most probable index will be returned.
     * @return			-1 if there is no such entry
     */
    public int getMostProbableBucketIndex(int step) {
        if(probabilities.get(step) == null) return -1;

        double max = 0;
        int bucketIdx = -1;
        int i = 0;
        for(double d : probabilities.get(step)) {
            if(d > max) {
                max = d;
                bucketIdx = i;
            }
            ++i;
        }
        return bucketIdx;
    }

    /**
     * Returns the count of steps 返回时间点的数量
     * @return
     */
    public int getStepCount() {
        return probabilities.size();
    }

    /**
     * Returns the count of probabilities for the specified step 返回指定时间点的概率的数量
     * @param	the step indexing the probability values
     * @return
     */
    public int getStatCount(int step) {
        return probabilities.get(step).length;
    }

    /**
     * Returns a set of steps being recorded. 返回被记录的时间点的集合
     * @return
     */
    public int[] stepSet() {
        return probabilities.keySet().toArray();
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + Arrays.hashCode(actualValues);
        result = prime * result + ((probabilities == null) ? 0 : probabilities.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if(this == obj)
            return true;
        if(obj == null)
            return false;
        if(getClass() != obj.getClass())
            return false;
        @SuppressWarnings("rawtypes")
        Classification other = (Classification)obj;
        if(!Arrays.equals(actualValues, other.actualValues))
            return false;
        if(probabilities == null) {
            if(other.probabilities != null)
                return false;
        } else {
            for(int key : probabilities.keys()) {
                if(!Arrays.equals(probabilities.get(key), (double[])other.probabilities.get(key))) {
                    return false;
                }
            }
        }
            
        return true;
    }
}
