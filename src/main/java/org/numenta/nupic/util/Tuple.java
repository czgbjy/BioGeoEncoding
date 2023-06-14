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

import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import org.numenta.nupic.model.Persistable;

/**
 * An immutable fixed data structure whose values are retrieved
 * via a given index. This data structure emulates multiple method
 * return values possible in Python.一种不可变的固定数据结构，其值通过给定的索引检索。本质上是一个对象数组。此数据结构模拟Python中可能的多个方法返回值
 * 
 * @author David Ray
 */
public class Tuple implements Persistable, Comparable<Tuple> {
    
    private static final long serialVersionUID = 1L;

    /** The internal container array */
	protected Object[] container;
	
	private int hashcode;
	
	private Comparator<Tuple> comparator;
	
	public Tuple() {}
	
	/**
	 * Instantiates a new {@code Tuple}
	 * 用输入的对象数组，制作一个Tuple对象
	 * @param objects
	 */
	public Tuple(Object... objects) {
		remake(objects);
	}
	
	/**
	 * Constructs a {@code Tuple} that will use the supplied
	 * {@link Comparator} to implement the {@link Comparable}
	 * interface.
	 * @param c            the Comparator function to use to compare the
	 *                     contents of the is {@code Tuple}
	 * @param objects      the objects contained within
	 */
	public Tuple(Comparator<Tuple> c, Object...objects) {
	    this.comparator = c;
	    remake(objects);
	}
	
	/**
	 * Remakes the internals for this Tuple.
	 * 把传入的对象数组，变成这个Tuple的内容
	 * @param objects
	 */
	protected void remake(Object...objects) {
	    container = new Object[objects.length];
        for(int i = 0;i < objects.length;i++) container[i] = objects[i];
        this.hashcode = hashCode();
	}
	
	/**
	 * Returns the object previously inserted into the
	 * specified index.
	 * 获取Tuple对象中的指定索引的对象
	 * 
	 * @param index    the index representing the insertion order.
	 * @return
	 */
	public Object get(int index) {
		return container[index];
	}
	
	/**
	 * Returns the number of items in this {@code Tuple}
	 * 返回此元组的长度
	 * @return
	 */
	public int size() {
	    return container.length;
	}
	
	/**
	 * Returns an <em>unmodifiable</em> view of the underlying data.
	 * @return
	 */
	public  List<Object> all() {
	    return Collections.unmodifiableList(Arrays.asList(container));//Collections.unmodifiableList后如果再对这个数组添加、修改、删除元素，会报错
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		for(int i = 0;i < container.length;i++) {
			try {
				new Double((double) container[i]);
				sb.append(container[i]);
			}catch(Exception e) { sb.append("'").append(container[i]).append("'");}
			sb.append(":");
		}
		sb.setLength(sb.length() - 1);
		
		return sb.toString();
	}
	
	/**
     * {@inheritDoc}
     */
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + Arrays.hashCode(container);
		return result;
	}

	/**
     * {@inheritDoc}
     */
	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Tuple other = (Tuple) obj;
		if (this.hashcode != other.hashcode)
			return false;
		return true;
	}

	/**
	 * Uses the supplied {@link Comparator} to compare this {@code Tuple}
	 * with the specified {@link Tuple}
	 */
    @Override
    public int compareTo(Tuple t) {
        if(comparator == null) {
            throw new IllegalStateException("Tuples used for comparison should be " +
                "instantiated using the constructor taking a Comparator");
        }
        
        return comparator.compare(this, t);
    }
}
