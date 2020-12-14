package com.coocaa.lite.os.common.utils;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;

/**
 * Created by IntelliJ IDEA.
 *
 * @author: olife
 * description:反射工具类
 * date: 2020/12/1
 * version: 1.0
 */
@Slf4j
public class ReflectUtil {

    /**
     * 清除对象里面不匹配filterMap的数据
     *
     * @param object
     * @param filterMap 过滤映射关系
     *                  遵循规则是： 类名：字段名：字段值List<Object> Object紧紧支持基础类型，不支持复杂对象以及Date
     * @param <T>
     * @return
     */
    public static <T> T removeObject(T object, Map<String, Map<String, List<Object>>> filterMap) {
        boolean b = removeData(object, object.getClass(), filterMap);
        if (b) {
            return null;
        }
        return object;
    }

    /**
     * @param src        源对象
     * @param target     目标对象
     * @param compareMap 比对值映射
     *                   遵循 src:target规则 字段名由class.getSimpleName_filedName组成
     * @param valueMap   替换值字段映射
     *                   遵循 src:target规则 字段名由class.getSimpleName_filedName组成
     * @param filterMap  过滤映射关系
     *                   遵循规则是： 类名：字段名：字段值List
     */
    public static void copyValue(Object src, Object target, Map<String, String> compareMap, Map<String, String> valueMap, Map<String, Map<String, List<Object>>> filterMap) {
        if (null != filterMap && filterMap.size() != 0) {
            target = removeObject(target, filterMap);
        }
        copyValue(src, target, compareMap, valueMap);
    }

    /**
     * @param src
     * @param target
     * @param compareMap 遵循 src:target规则
     *                   字段名由class.getSimpleName_filedName组成
     */
    public static void copyValue(Object src, Object target, Map<String, String> compareMap) {
        copyValue(src, target, compareMap, null);
    }

    /**
     * @param src
     * @param target
     * @param compareMap 遵循 src:target规则
     * @param valueMap   遵循 src:target规则
     *                   可以指定字段的名称对应关系 字段名由class.getSimpleName_filedName组成
     */
    public static void copyValue(Object src, Object target, Map<String, String> compareMap, Map<String, String> valueMap) {
        if (null == src || null == target || null == compareMap || compareMap.size() == 0) {
            return;
        }
        //1 分离出目标关联list和源关联list
        List<String> compareKeysSrc = new ArrayList<>();
        List<String> compareKeysTarget = new ArrayList<>();
        Set<String> keys = compareMap.keySet();
        for (String key : keys) {
            compareKeysSrc.add(key);
            String value = compareMap.get(key);
            if (null == value || value.isEmpty()) {
                //补充一下值，赋值为key
                value = key;
                compareMap.put(key, value);
            }
            compareKeysTarget.add(compareMap.get(key));
        }

        //2 获取包含当前关联key的对象（当前层）
        Multimap<String, Object> reflectSrc = reflect(src, src.getClass(), compareKeysSrc);
        //2 获取包含当前关联key的对象（当前层）
        Multimap<String, Object> reflectTarget = reflect(target, target.getClass(), compareKeysTarget);

        //3 赋值
        Set<String> srcList = reflectSrc.keySet();
        for (String key : srcList) {
            String targetKey = changeTargetKey(compareMap, key);
            if (null == targetKey || targetKey.isEmpty()) {
                continue;
            }

            Object[] srcObejcts = reflectSrc.get(key).toArray();
            Object srcObejct = srcObejcts[0];
            Collection<Object> targetObjects = reflectTarget.get(targetKey);
            for (Object targetObject : targetObjects) {
                copyObject(srcObejct, targetObject, valueMap);
            }

        }
    }

    /**
     * 获取对象中某个字段的valueList
     *
     * @param object     数据对象
     * @param cls        对象class
     * @param uniqueName 由class.getSimpleName_filedName组成
     */
    public static List<Object> getValueList(Object object, Class cls, String uniqueName) {
        Set<Object> valueList = new HashSet<Object>();
        if (null == uniqueName || uniqueName.isEmpty()) {
            return new ArrayList<>();
        }

        //传过来的对象就是list
        if (isList(cls)) {
            List<Object> list = (List) object;
            for (Object o1 : list) {
                List<Object> reflect = getValueList(o1, o1.getClass(), uniqueName);
                valueList.addAll(reflect);
            }
            return new ArrayList<>(valueList);
        }

        //传过来的对象为基本数据类型，不走一下逻辑
        if (!isCustomClass(cls)) {
            return new ArrayList<>(valueList);
        }

        //一下为object为对象的逻辑
        //获取参数类
        Field[] fields = cls.getDeclaredFields();
        for (int i = 0; i < fields.length; i++) {
            try {
                Field f = fields[i];
                f.setAccessible(true);
                String name = f.getName();
                Object value = f.get(object);
                Class<?> type = f.getType();
                //判断该字段的value
                if (null == value) {
                    //没有进行下去的必要
                    continue;
                }
                if (isList(type)) {
                    //List对象
                    List<Object> list = (List) value;
                    for (Object o1 : list) {
                        List<Object> reflect = getValueList(o1, o1.getClass(), uniqueName);
                        valueList.addAll(reflect);
                    }
                } else if (isCustomClass(type)) {
                    //自定义对象
                    List<Object> reflect = getValueList(value, value.getClass(), uniqueName);
                    valueList.addAll(reflect);
                } else {
                    //普通对象
                    //key为类名_字段名
                    String key = keyAddValue(cls.getSimpleName(), name);
                    if (!uniqueName.equals(key)) {
                        //对应的key不存在
                        continue;
                    }
                    //比对的字段名不应该为对象或者List
                    if (!valueList.contains(value)) {
                        valueList.add(value);
                    }

                }
            } catch (IllegalArgumentException | IllegalAccessException e) {
                e.printStackTrace();
            }
        }

        //判断是否有继承
        Class<?> superClass = cls.getSuperclass();
        if (superClass != Object.class) {
            List<Object> reflect = getValueList(object, superClass, uniqueName);
            valueList.addAll(reflect);
        }
        return new ArrayList<>(valueList);
    }

    /**
     * 拼装唯一key
     *
     * @param key
     * @param value
     * @return
     */
    public static String keyAddValue(String key, Object value) {
        if (null == value) {
            return key;
        }
        return key + "_" + value.toString();
    }

    /**
     * 判断是不是一个List
     *
     * @param type
     * @return
     */
    public static boolean isList(Class<?> type) {
        String name = type.getSimpleName();
        if (name.contains("List")) {
            return true;
        }
        return false;
    }

    /**
     * 判断是不是一个对象
     *
     * @param type
     * @return
     */
    public static boolean isCustomClass(Class<?> type) {
        String name = type.getSimpleName();
        if (name.contains("Integer") || name.contains("int") || name.contains("class [I")) {
            return false;
        } else if (name.contains("Boolean") || name.contains("boolean")) {
            return false;
        } else if (name.contains("Byte") || name.contains("byte")) {
            return false;
        } else if (name.contains("Character") || name.contains("char")) {
            return false;
        } else if (name.contains("Short") || name.contains("short")) {
            return false;
        } else if (name.contains("Long") || name.contains("long")) {
            return false;
        } else if (name.contains("Float") || name.contains("float")) {
            return false;
        } else if (name.contains("Double") || name.contains("double")) {
            return false;
        } else if (name.contains("Date")) {
            return false;
        } else if (name.contains("String") || name.contains("class [L")) {
            return false;
        } else if (name.contains("List")) {
            return false;
        }
        return true;
    }

    /**
     * 递归方法判断该对象是否要删除
     *
     * @param object
     * @param cls
     * @param nameMap
     * @return
     */
    private static boolean removeData(Object object, Class cls, Map<String, Map<String, List<Object>>> nameMap) {
        if (null == object) {
            return false;
        }

        //传过来的对象就是list
        if (isList(cls)) {
            List<Object> list = (List) object;
            Iterator<Object> iter = list.iterator();
            //迭代器remove()方法删除（推荐）,不然会报错
            while (iter.hasNext()) {
                Object item = iter.next();
                boolean remove = removeData(item, item.getClass(), nameMap);
                if (remove) {
                    iter.remove();
                }
            }
            return false;
        }

        //传过来的对象为基本数据类型，不走一下逻辑
        if (!isCustomClass(cls)) {
            return false;
        }

        //一下为object为对象的逻辑
        Field[] fields = cls.getDeclaredFields();
        for (int i = 0; i < fields.length; i++) {
            try {
                Field f = fields[i];
                f.setAccessible(true);
                String name = f.getName();
                Object value = f.get(object);
                Class<?> type = f.getType();
                //判断该字段的value
                if (isList(type)) {
                    //List对象
                    if (null == value) {
                        //没有进行下去的必要
                        continue;
                    }
                    List<Object> list = (List) value;
                    Iterator<Object> iter = list.iterator();
                    //迭代器remove()方法删除（推荐）,不然会报错
                    while (iter.hasNext()) {
                        Object item = iter.next();
                        boolean remove = removeData(item, item.getClass(), nameMap);
                        if (remove) {
                            iter.remove();
                        }
                    }
                } else if (isCustomClass(type)) {
                    //自定义对象
                    if (null == value) {
                        //没有进行下去的必要
                        continue;
                    }
                    boolean remove = removeData(value, value.getClass(), nameMap);
                    if (remove) {
                        f.set(object, null);
                    }
                } else {
                    //普通对象
                    //key为类名_字段名
                    String simpleName = cls.getSimpleName();
                    if (!nameMap.containsKey(simpleName)) {
                        continue;
                    }
                    Map<String, List<Object>> keyMap = nameMap.get(simpleName);
                    if (!keyMap.containsKey(name)) {
                        continue;
                    }
                    if (null == value) {
                        return true;
                    }
                    List<Object> va = keyMap.get(name);
                    if (!va.contains(value)) {
                        return true;
                    }
                }
            } catch (IllegalArgumentException | IllegalAccessException e) {
                e.printStackTrace();
            }
        }

        //判断是否有继承
        Class<?> superClass = cls.getSuperclass();
        if (superClass != Object.class) {
            return removeData(object, superClass, nameMap);
        }
        return false;
    }

    /**
     * 暂存当前字段的属性值
     */
    private static class FieldObject {
        String key;
        Class<?> type;
        Object value;
    }


    /**
     * 能进来这一层都都应该是当前对象类，没有多级
     *
     * @param src
     * @param target
     */
    private static void copyObject(Object src, Object target, Map<String, String> valueMap) {
        if (null == src || null == target) {
            return;
        }

        //把源目标的所有字段的值都存储到srcFieldMap中
        Map<String, FieldObject> srcFieldMap = new HashMap<>();
        Class<?> clsSrc = src.getClass();
        Field[] fieldsSrc = clsSrc.getDeclaredFields();
        for (int i = 0; i < fieldsSrc.length; i++) {
            try {
                Field f = fieldsSrc[i];
                f.setAccessible(true);
                String name = f.getName();
                Class<?> srcType = f.getType();
                Object srcValue = f.get(src);
                if (Modifier.isFinal(f.getModifiers())) {
                    //如果字段为final修饰的话不存储
                    continue;
                }
                if (isCustomClass(srcType)) {
                    //如果字段的value是类的话不存储
                    continue;
                }
                if (null == srcValue) {
                    //如果字段的value是null的话不存储
                    continue;
                }
                String nameKey = keyAddValue(clsSrc.getSimpleName(), name);
                FieldObject fieldObject = new FieldObject();
                fieldObject.key = name;
                fieldObject.type = srcType;
                fieldObject.value = srcValue;
                srcFieldMap.put(nameKey, fieldObject);
            } catch (IllegalArgumentException | IllegalAccessException e) {
                e.printStackTrace();
            }
        }

        //赋值目标对象
        Class<?> clsTarget = target.getClass();
        Field[] fieldsTarget = clsTarget.getDeclaredFields();
        for (int i = 0; i < fieldsTarget.length; i++) {
            try {
                Field f = fieldsTarget[i];
                f.setAccessible(true);
                String name = f.getName();
                Class<?> type = f.getType();
                //把目标key转换成源key
                String srcKey = keyAddValue(clsSrc.getSimpleName(), name);
                String targetKey = keyAddValue(clsTarget.getSimpleName(), name);

                if (null != valueMap && valueMap.containsKey(srcKey)) {
                    //如果目标字段与源对象的字段名字相同，但是源对象的字段又被指向了目标对象的另一个字段，那么这个目标与源对象相同的字段不应该被赋值
                    String value = valueMap.get(srcKey);
                    if (!targetKey.equalsIgnoreCase(value)) {
                        continue;
                    }
                }

                if (null != valueMap && valueMap.containsValue(targetKey)) {
                    srcKey = changeSrcKey(valueMap, targetKey);
                }

                FieldObject fieldObject = srcFieldMap.get(srcKey);
                if (null == fieldObject || !fieldObject.type.equals(type)) {
                    continue;
                }
                f.set(target, fieldObject.value);
            } catch (IllegalArgumentException | IllegalAccessException e) {
                e.printStackTrace();
            }
        }

        //赋值完成,清楚缓存
        srcFieldMap.clear();
    }

    /**
     * 源key置换成目标key
     *
     * @param compareMap
     * @param srcKey
     * @return
     */
    private static String changeTargetKey(Map<String, String> compareMap, String srcKey) {
        if (null == srcKey || srcKey.isEmpty() || null == compareMap) {
            return srcKey;
        }
        Set<String> keys = compareMap.keySet();
        for (String key : keys) {
            if (!srcKey.startsWith(key)) {
                continue;
            }
            //是对应的key，开始置换
            srcKey = srcKey.replace(key, compareMap.get(key));
            return srcKey;
        }
        return srcKey;
    }

    /**
     * 目标key置换成源key
     *
     * @param compareMap
     * @param targetKey
     * @return
     */
    private static String changeSrcKey(Map<String, String> compareMap, String targetKey) {
        if (null == targetKey || targetKey.isEmpty() || null == compareMap) {
            return targetKey;
        }
        Set<String> keys = compareMap.keySet();
        for (String key : keys) {
            if (!targetKey.startsWith(compareMap.get(key))) {
                continue;
            }
            //是对应的key，开始置换
            targetKey = targetKey.replace(compareMap.get(key), key);
            return targetKey;
        }
        return targetKey;
    }


    /**
     * 把对象里面所有数据按照key:value存在Map里面
     * <p>
     * 1 写一个函数 把object转成MultiMap
     * 2 写一个函数 赋值 参数是 俩个map以及一个List<Id>
     * <p>
     * compareKeys 中String的值为类名_字段名
     *
     * @param object 数据对象
     * @param cls    对象class
     */
    private static Multimap<String, Object> reflect(Object object, Class cls, List<String> compareKeys) {
        Multimap<String, Object> compareValueMap = ArrayListMultimap.create();
        if (null == compareKeys || compareKeys.size() == 0) {
            return compareValueMap;
        }

        //传过来的对象就是list
        if (isList(cls)) {
            List<Object> list = (List) object;
            for (Object o1 : list) {
                Multimap<String, Object> reflect = reflect(o1, o1.getClass(), compareKeys);
                compareValueMap.putAll(reflect);
            }
            return compareValueMap;
        }

        //传过来的对象为基本数据类型，不走一下逻辑
        if (!isCustomClass(cls)) {
            return compareValueMap;
        }

        //一下为object为对象的逻辑
        //获取参数类
//        log.info(" =====start======  " + cls);
        Field[] fields = cls.getDeclaredFields();
        for (int i = 0; i < fields.length; i++) {
            try {
                Field f = fields[i];
                f.setAccessible(true);
                String name = f.getName();
                Object value = f.get(object);
                Class<?> type = f.getType();
//                log.info("属性名：" + f.getName() + ";字段类型：" + f.getGenericType() + "；属性值：" + value);
                //判断该字段的value
                if (null == value) {
                    //没有进行下去的必要
                    continue;
                }
                if (isList(type)) {
                    //List对象
                    List<Object> list = (List) value;
                    for (Object o1 : list) {
                        Multimap<String, Object> reflect = reflect(o1, o1.getClass(), compareKeys);
                        compareValueMap.putAll(reflect);
                    }
                } else if (isCustomClass(type)) {
                    //自定义对象
                    Multimap<String, Object> reflect = reflect(value, value.getClass(), compareKeys);
                    compareValueMap.putAll(reflect);
                } else {
                    //普通对象
                    //key为类名_字段名
                    String key = keyAddValue(cls.getSimpleName(), name);
                    if (!compareKeys.contains(key)) {
                        //对应的key不存在
                        continue;
                    }
                    //比对的字段名不应该为对象或者List
                    compareValueMap.put(keyAddValue(key, value), object);
                }
            } catch (IllegalArgumentException | IllegalAccessException e) {
                e.printStackTrace();
            }
        }

        //判断是否有继承
        Class<?> superClass = cls.getSuperclass();
        if (superClass != Object.class) {
//            log.info(" ==========  " + superClass);
            Multimap<String, Object> reflect = reflect(object, superClass, compareKeys);
            compareValueMap.putAll(reflect);
        }
//        log.info(" =====end======  " + cls);
        return compareValueMap;
    }
}
