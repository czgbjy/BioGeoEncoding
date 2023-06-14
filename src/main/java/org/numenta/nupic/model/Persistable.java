package org.numenta.nupic.model;

import java.io.Serializable;

/**
 * Extends {@link Serializable} to add preparation tasks prior to
 * serialization and repair tasks following deserialization.
 * 接口是抽象方法和常量值的定义的集合
 * @author cogmission
 */
public interface Persistable extends Serializable {
    
    /**
     * <em>FOR INTERNAL USE ONLY</em><p>
     * Called prior to this object being serialized. Any
     * preparation required for serialization should be done
     * in this method.
     */
    @SuppressWarnings("unchecked")
    public default <T> T preSerialize() { return (T)this; }///JDK8新增接口中抽象方法的默认实现，使用default关键字，用于标识抽象方法的默认实现，子类中需覆盖改方法，static也可以实现接口方法
    /**
     * <em>FOR INTERNAL USE ONLY</em><p>
     * Called following deserialization to execute logic required
     * to "fix up" any inconsistencies within the object being
     * reified.
     */
    @SuppressWarnings("unchecked")
    public default <T> T postDeSerialize() { return postDeSerialize((T)this); }///这里第一个<T>标识泛型，第二个T表示T类型，第三个T也是表示T类型
    /**
     * <em>FOR INTERNAL USE ONLY</em><p>
     * Called to implement a full or partial copy of an object 
     * upon de-serialization.
     * 
     * @param t     the instance of type &lt;T&gt;
     * @return  a post serialized custom form of T
     */
    public default <T> T postDeSerialize(T t) { return t; }
    
}
