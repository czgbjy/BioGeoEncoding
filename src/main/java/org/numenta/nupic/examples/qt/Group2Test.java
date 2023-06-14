package org.numenta.nupic.examples.qt;

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

import org.numenta.nupic.util.GroupBy2;
import org.numenta.nupic.util.GroupBy2.Slot;
import org.numenta.nupic.util.Tuple;

import chaschev.lang.Pair;

public class Group2Test {

	public static void main(String[] args) {
		// TODO Auto-generated method stub
		List<Integer> sequence0 = Arrays.asList(new Integer[] { 7, 12, 16 });//创建一个整形数列表
	       List<Integer> sequence1 = Arrays.asList(new Integer[] { 3, 4, 5 });//创建另外一个整形数列表
	       
	       Function<Integer, Integer> identity = Function.identity();//Lamda表达式，表达输入和输出一样的函数
	       Function<Integer, Integer> times3 = x -> x * 3;//Lamda表达式，
	       
	       @SuppressWarnings({ "unchecked", "rawtypes" })
	       GroupBy2<Integer> groupby2 = GroupBy2.of(
	           new Pair(sequence0, identity), 
	           new Pair(sequence1, times3));
	       
	       for(Tuple tuple : groupby2) {
	           System.out.println(tuple);
	       }

	}

}
