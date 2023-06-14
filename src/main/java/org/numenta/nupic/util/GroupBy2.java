/* ---------------------------------------------------------------------
 * Numenta Platform for Intelligent Computing (NuPIC)
 * Copyright (C) 2014-2016, Numenta, Inc.  Unless you have an agreement
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
package org.numenta.nupic.util;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.numenta.nupic.util.GroupBy2.Slot;

import chaschev.lang.Pair;


/**
 * An Java extension to groupby in Python's itertools. Allows to walk across n sorted lists
 * with respect to their key functions and yields a {@link Tuple} of n lists of the
 * members of the next *smallest* group.
 * Python的itertools中groupby的java扩展。允许遍历n个已排序列表用他们的key函数，产生下一个“最小”组的n个成员列表的元组
 * @author cogmission
 * @param <R>   The return type of the user-provided {@link Function}s
 */
public class GroupBy2<R extends Comparable<R>> implements Generator<Tuple> {
    /** serial version */
    private static final long serialVersionUID = 1L;
    
    /** stores the user inputted pairs entries是一个Pair数组，Pair中存储两个对象即List<Object>和Function<Object,R>,这两个对象构成一对*/
    private Pair<List<Object>, Function<Object, R>>[] entries;//entries是一个Pair数组，Pair中存储两个对象即List<Object>和Function<Object,R>,这两个对象构成一对
    
    /** stores the {@link GroupBy} {@link Generator}s created from the supplied lists 存储GroupBy对象的列表*/
    private List<GroupBy<Object, R>> generatorList;
    
    /** the current interation's minimum key value 当前迭代的最小键值 */
    private R minKeyVal;
    
    
    ///////////////////////
    //    Control Lists  //
    ///////////////////////
    private boolean[] advanceList;
    private Slot<Pair<Object, R>>[] nextList;//一个Slot数组，数组中的元素为Pair<Object,R>
    
    private int numEntries;//GroupBy对象的数量
    
    /**
     * Private internally used constructor. To instantiate objects of this
     * class, please see the static factory method {@link #of(Pair...)}
     * 私有的内部使用的构造函数，为了实例化这个类，请使用静态工厂函数of
     * @param entries   a {@link Pair} of lists and their key-producing functions 一个list和他们的键值生成函数的Pair数组
     */
    private GroupBy2(Pair<List<Object>, Function<Object, R>>[] entries) {
        this.entries = entries;
    }
    
    /**
     * <p>
     * Returns a {@code GroupBy2} instance which is used to group lists of objects
     * in ascending order using keys supplied by their associated {@link Function}s.
     * </p><p>返回一个GroupBy2实例，这个实例被用来给列表对象分组，分组按照给定的关联函数提供的键值的升序排列。
     * <b>Here is an example of the usage and output of this object: (Taken from {@link GroupBy2Test})</b>
     * </p>
     * <pre>
     *  List<Integer> sequence0 = Arrays.asList(new Integer[] { 7, 12, 16 });//创建一个整形数列表
     *  List<Integer> sequence1 = Arrays.asList(new Integer[] { 3, 4, 5 });//创建另外一个整形数列表
     *  
     *  Function<Integer, Integer> identity = Function.identity();//Lamda表达式，表达输入和输出一样的函数
     *  Function<Integer, Integer> times3 = x -> x * 3;//Lamda表达式，
     *  
     *  @SuppressWarnings({ "unchecked", "rawtypes" })
     *  GroupBy2<Integer> groupby2 = GroupBy2.of(
     *      new Pair(sequence0, identity), 
     *      new Pair(sequence1, times3));
     *  
     *  for(Tuple tuple : groupby2) {
     *      System.out.println(tuple);
     *  }
     * </pre>
     * <br>
     * <b>Will output the following {@link Tuple}s:</b>
     * <pre>
     *  '7':'[7]':'[NONE]'
     *  '9':'[NONE]':'[3]'
     *  '12':'[12]':'[4]'
     *  '15':'[NONE]':'[5]'
     *  '16':'[16]':'[NONE]'
     *  
     *  From row 1 of the output:
     *  Where '7' == Tuple.get(0), 'List[7]' == Tuple.get(1), 'List[NONE]' == Tuple.get(2) == empty list with no members
     * </pre>
     * 
     * <b>Note: Read up on groupby here:</b><br>
     *   https://docs.python.org/dev/library/itertools.html#itertools.groupby
     * <p> 
     * @param entries 输入的Pair数组
     * @return  a n + 1 dimensional tuple, where the first element is the
     *          key of the group and the other n entries are lists of
     *          objects that are a member of the current group that is being
     *          iterated over in the nth list passed in. Note that this
     *          is a generator and a n+1 dimensional tuple is yielded for
     *          every group. If a list has no members in the current
     *          group, {@link Slot#NONE} is returned in place of a generator.返回一个n+1维的tuple,第一个元素是这个组的键值，别的n个实体是对象列表，别的对象是是当前组的成员，这个成员是在传入的第n个列表中正在迭代的成员，注意这是一个生成器，对于每个组，一个n+1维的tuple被生成。如果列表中在当前的组中没有成员，Slot None在生成器的位置被返回。
     */
    @SuppressWarnings("unchecked")
    public static <R extends Comparable<R>> GroupBy2<R> of(Pair<List<Object>, Function<Object, R>>... entries) {
        return new GroupBy2<>(entries);
    }
    
    /**
     * (Re)initializes the internal {@link Generator}(s). This method
     * may be used to "restart" the internal {@link Iterator}s and
     * reuse this object. 初始化内部的Generators,这个方法可能被用来重启这内部的Iterators和重用这个对象
     */
    @SuppressWarnings("unchecked")
    public void reset() {
        generatorList = new ArrayList<>();//存储GroupBy的列表
        
        for(int i = 0;i < entries.length;i++) {
            generatorList.add(GroupBy.of(entries[i].getFirst(), entries[i].getSecond()));//把GroupBy添加至这个列表，把实体中的对象（第一个是激活的列的列表，第二个是，激活树突列表，第三个是匹配树突列表）及其匿名函数一起形成GroupBy对象
        }
        
        numEntries = generatorList.size();//记录GroupBy对象的数量，获取GroupBy对象的数量
        
//        for(int i = 0;i < numEntries;i++) {
//            for(Pair<Object, R> p : generatorList.get(i)) {
//                System.out.println("generator " + i + ": " + p.getKey() + ",  " + p.getValue());
//            }
//            System.out.println("");
//        }
//        
//        generatorList = new ArrayList<>();
//        
//        for(int i = 0;i < entries.length;i++) {
//            generatorList.add(GroupBy.of(entries[i].getKey(), entries[i].getValue()));
//        }
        
        advanceList = new boolean[numEntries];//为GroupBy对象新建一个Boolean数组，
        Arrays.fill(advanceList, true);//首先全部赋值为true，创建advanceList以及把其所有值都初始化为true，
        nextList = new Slot[numEntries];//为GroupBy对象新建一个Slot数组，创建Slot数组，nextList
        Arrays.fill(nextList, Slot.NONE);//里面的内容赋值为None，并把Slot数组赋值为null
    }
    
    /**
     * {@inheritDoc}
     */
    public Iterator<Tuple> iterator() { return this; }
    
    /**
     * Returns a flag indicating that at least one {@link Generator} has
     * a matching key for the current "smallest" key generated.
     * 
     * @return a flag indicating that at least one {@link Generator} has
     * a matching key for the current "smallest" key generated.
     */
    @Override
    public boolean hasNext() {
        if(generatorList == null) {//如果GroupBy列表为空，则新建列表
            reset();
        }
        
        advanceSequences();//相当于把GroupBy列表中非空的元素放入Slot<Pair>数组中nextList
        
        return nextMinKey();//返回下一个最小boxIndex的Column的判断，如果有返回True
    }
    
    /**
     * Returns a {@link Tuple} containing the current key in the
     * zero'th slot, and a list objects which are members of the
     * group specified by that key.
     * 
     * {@inheritDoc}
     */
    @SuppressWarnings("unchecked")
    @Override
    public Tuple next() {
        
        Object[] objs = IntStream
            .range(0, numEntries + 1)//产生一个0,到3的数组刘
            .mapToObj(i -> i==0 ? minKeyVal : new ArrayList<R>())//当i=0时返回上一轮找到的具有最小boxIndex的Column,否则返回一个ArrayList数组，最终形成一个对象数组
            .toArray();//为GroupBy实体数组生成一个，Object对象数组
        
        Tuple retVal = new Tuple((Object[])objs);//把对象数组中的对象存储在Tuple中，Tuple最大的好处就是能通过索引获取对象了吧。
        
        for(int i = 0;i < numEntries;i++) {
            if(isEligibleList(i, minKeyVal)) {//如果i代表的实体的值是Pair的最小值键值
                ((List<Object>)retVal.get(i + 1)).add(nextList[i].get().getFirst());//那么把这个Pair的第一个值添加到Tuple中去，这里相当于把当前列表中的Pair的第一个值，放入Tuple的下一个对象中，这里相当于把boxIndex最小的Colunm存入了Tuple数组的第二个元素中（ArrayList)
                drainKey(retVal, i, minKeyVal);
                advanceList[i] = true;
            }else{
                advanceList[i] = false;
                ((List<Object>)retVal.get(i + 1)).add(Slot.empty());
            }
        }
        
        return retVal;
    }
    
    /**
     * Internal method which advances index of the current
     * {@link GroupBy}s for each group present.内部方法，该方法为每个存在的组提高当前GroupBy的索引,相当于把nextList中填充上了值
     */
    private void advanceSequences() {
        for(int i = 0;i < numEntries;i++) {//对于每个GroupBy对象
            if(advanceList[i]) {//如果数组中的GroupBy对象对应的是否提高标记为true
                nextList[i] = generatorList.get(i).hasNext() ?//如果这个GroupBy列表中还有下一个元素，
                    Slot.of(generatorList.get(i).next()) : Slot.empty();//这个GroupBy对象对应的Slot为这个GroupBy对象，否则返回空的Slot，相当于把传入的一个数组里包含的多个值抠出每一个值，应用匿名函数，形成一个GroupBy的对，放入Slot对象。如果这个数组本身没有值，直接生成空的SLot对象
            }
        }
    }
    
    /**
     * Returns the next smallest generated key.
     * 返回下一个最小的生成key,也就是下一个boxIndex最小的Column
     * @return  the next smallest generated key.
     */
    private boolean nextMinKey() {
    	
    	/*Stream<Slot<Pair<Object, R>>>  resultObject=Arrays.stream(nextList).filter(opt -> opt.isPresent());
    	Optional<R> resultObjectOnStream=resultObject.map(opt -> opt.get().getSecond()).min((k, k2) -> k.compareTo(k2));
    	Optional<Object> resultObjectTwoStream=resultObjectOnStream.map(k -> { minKeyVal = k; return k; } );
    	Boolean resultBoolean=resultObjectTwoStream.isPresent();*/
    	
        return Arrays.stream(nextList)//nextList是一个Slot的数组，数组中有多个Slot对象，每个Slot对象里面封装一个GroupBy对象，每个GroupBy对象里封装着要产生Pair对象的列表，以及相应的匿名函数，列表的范围，当前的Pair
            .filter(opt -> opt.isPresent())//把Slot数组中非空的对象（一个Slot对象)挑选出来
            .map(opt -> opt.get().getSecond())//由于opt是一个Slot对象，而这个Slot里面包裹的其实是一个Pair，因而opt.get()函数返回的是一个Pair对象，Pair对象的的第二个对象这里为一个Colum
            .min((k, k2) -> k.compareTo(k2))//这里对过滤的结果column进行排序后，排序规则是按照Column的boxIndex大小排列的，升序，这里取boxIndex最小的column
            .map(k -> { minKeyVal = k; return k; } )//把boxIndex最小的Column值赋值给minKeyVal,并确认一下这个值是否不为空
            .isPresent();//返回这个最小键值是否存在的标记，存在为true,否则为false
    }
    
    /**
     * Returns a flag indicating whether the list currently pointed
     * to by the specified index contains a key which matches the
     * specified "targetKey".返回一个标志，标明由指定的索引当前指向的列表是否包含与指定“targetKey"匹配的键。
     * 相当于判断listIdx指定的Pair的计算结果是不是targetKey eligible有资格的;合格的;具备条件的;(指认为可做夫妻的男女)合意的，合适的，中意的
     * @param listIdx       the index pointing to the {@link GroupBy} being
     *                      processed.
     * @param targetKey     the specified key to match.
     * @return  true if so, false if not
     */
    private boolean isEligibleList(int listIdx, Object targetKey) {
       return nextList[listIdx].isPresent() && nextList[listIdx].get().getSecond().equals(targetKey);//相当于判断由listIdx指定的Pair的ID是不是targetKey
    }
    
    /**
     * Each input grouper may generate multiple members which match the
     * specified "targetVal". This method guarantees that all members 
     * are added to the list residing at the specified Tuple index.
     * 相当于把listIdx指定的GroupBy所包含的pair的第二个值targetVal的pair赋值个nextList，这个pair第一个值赋值给tuple
     * @param retVal        the Tuple being added to
     * @param listIdx       the index specifying the list within the 
     *                      tuple which will have members added to it
     * @param targetVal     the value to match in order to be an added member
     */
    @SuppressWarnings("unchecked")
    private void drainKey(Tuple retVal, int listIdx, R targetVal) {
        while(generatorList.get(listIdx).hasNext()) {//判定当前的GroupBy对象是否还有下一个Pair
            if(generatorList.get(listIdx).peek().getSecond().equals(targetVal)) {//如果这个Pair的第二个值还等于targetVal
                nextList[listIdx] = Slot.of(generatorList.get(listIdx).next());//那么listIdx代表的Slot就为当前这个Pair
                ((List<Object>)retVal.get(listIdx + 1)).add(nextList[listIdx].get().getFirst());//并且把当前这个Pair的第一个值添加进入Tuple的下一个元素
            }else{
                nextList[listIdx] = Slot.empty();//否则为一个空的Slot
                break;
            }
        }
    }
    
    /**
     * A minimal {@link Serializable} version of an {@link Slot}
     * @param <T>   the value held within this {@code Slot}
     */
    public static final class Slot<T> implements Serializable {
        /** Default Serial */
        private static final long serialVersionUID = 1L;
        
        /**
         * Common instance for {@code empty()}.
         */
        public static final Slot<?> NONE = new Slot<>();

        /**
         * If non-null, the value; if null, indicates no value is present 
         */
        private final T value;
        
        private Slot() { this.value = null; }
        
        /**
         * Constructs an instance with the value present.
         * 用提供的值构成一个实例
         * @param value the non-null value to be present 提供的非空的实例值
         * @throws NullPointerException if value is null
         */
        private Slot(T value) {
            this.value = Objects.requireNonNull(value);//如果value不为空直接返回value值，如果value值为空，则抛出Nullpointer错误
        }

        /**
         * Returns an {@code Slot} with the specified present non-null value.
         * 返回一个带有指定的提供的非空值的Slot
         * @param <T> the class of the value 值的类型，标识这是一个泛型函数
         * @param value the value to be present, which must be non-null 提供的值，这个值必须非空
         * @return an {@code Slot} with the value present 返回一个有着提供值的Slot
         * @throws NullPointerException if value is null 如果提供的值为空的话，将会抛出NullPointerException
         */
        public static <T> Slot<T> of(T value) {
            return new Slot<>(value);
        }

        /**
         * Returns an {@code Slot} describing the specified value, if non-null,
         * otherwise returns an empty {@code Slot}.
         * 如果非空，返回一个Slot描述指定的值，否则返回一个空值Slot
         * @param <T> the class of the value 输入值的类型
         * @param value the possibly-null value to describe 要描述的可能空的值
         * @return an {@code Slot} with a present value if the specified value 返回一个Slot用提供的指定值
         * is non-null, otherwise an empty {@code Slot}
         */
        @SuppressWarnings("unchecked")
        public static <T> Slot<T> ofNullable(T value) {
            return value == null ? (Slot<T>)NONE : of(value);
        }

        /**
         * If a value is present in this {@code Slot}, returns the value,
         * otherwise throws {@code NoSuchElementException}.
         *如果一个值在这个Slot里面被提供了，返回这个值，否则抛出NoSuchElementException
         * @return the non-null value held by this {@code Slot} 返回由这个slot代表的非空的值
         * @throws NoSuchElementException if there is no value present
         *
         * @see Slot#isPresent()
         */
        public T get() {
            if (value == null) {
                throw new NoSuchElementException("No value present");
            }
            return value;
        }
        
        /**
         * Returns an empty {@code Slot} instance.  No value is present for this
         * Slot.
         * 返回一个空的Slot实例，这个Slot中没有只
         * @param <T> Type of the non-existent value 这个不存在的值的类型
         * @return an empty {@code Slot} 返回一个空的Slot
         */
        public static<T> Slot<T> empty() {
            @SuppressWarnings("unchecked")
            Slot<T> t = (Slot<T>) NONE;
            return t;
        }

        /**
         * Return {@code true} if there is a value present, otherwise {@code false}.
         * 如果有一个值，那么返回True，否则返回false
         * @return {@code true} if there is a value present, otherwise {@code false}
         */
        public boolean isPresent() {
            return value != null;
        }
        
        /**
         * Indicates whether some other object is "equal to" this Slot. The
         * other object is considered equal if:
         * <ul>
         * <li>it is also an {@code Slot} and;
         * <li>both instances have no value present or;
         * <li>the present values are "equal to" each other via {@code equals()}.
         * </ul>
         *
         * @param obj an object to be tested for equality
         * @return {code true} if the other object is "equal to" this object
         * otherwise {@code false}
         */
        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }

            if (!(obj instanceof Slot)) {
                return false;
            }

            Slot<?> other = (Slot<?>) obj;
            return Objects.equals(value, other.value);
        }

        /**
         * Returns the hash code value of the present value, if any, or 0 (zero) if
         * no value is present.
         *
         * @return hash code value of the present value or 0 if no value is present
         */
        @Override
        public int hashCode() {
            return Objects.hashCode(value);
        }

        /**
         * Returns a non-empty string representation of this Slot suitable for
         * debugging. The exact presentation format is unspecified and may vary
         * between implementations and versions.
         *
         * @implSpec If a value is present the result must include its string
         * representation in the result. Empty and present Slots must be
         * unambiguously differentiable.
         *
         * @return the string representation of this instance
         */
        @Override
        public String toString() {
            return value != null ? String.format("Slot[%s]", value) : "NONE";
        }
    }
}
