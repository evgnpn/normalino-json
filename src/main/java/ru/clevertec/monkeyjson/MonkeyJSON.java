package ru.clevertec.monkeyjson;

import java.lang.constant.Constable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;

public abstract class MonkeyJSON {

    public static String toJsonString(Object obj)
            throws InvocationTargetException, IllegalAccessException {
        return toJsonString(obj, false);
    }

    public static String toJsonString(Object obj, boolean isFormatted)
            throws InvocationTargetException, IllegalAccessException {

        var sb = new StringBuilder();

        if (obj instanceof Constable) {
            appendValue(sb, obj, true, false, isFormatted);
        }
        else if (isArray(obj)) {
            appendArray(sb, 0, false, getArray(obj), isFormatted);
        }
        else {
            appendObject(sb, 0, false, false, obj, isFormatted);
        }

        return sb.toString();
    }

    protected static void appendObject(StringBuilder sb, int deep,
                                       boolean isAssignable, boolean commaInTheEnd, Object obj, boolean isFormatted)
            throws InvocationTargetException, IllegalAccessException {

        if (obj == null) {
            sb.append("null");
            if (commaInTheEnd) {
                sb.append(',');
            }
            if (isFormatted) {
                sb.append('\n');
            }
            return;
        }

        if (!isAssignable) {
            if (isFormatted) {
                appendTabs(sb, deep);
            }
        }

        sb.append('{');

        if (isFormatted) {
            sb.append('\n');
        }

        var i = 0;
        var pairs = getJsonKeyValuePairs(obj);

        for (var kv : pairs) {

            var comma = i + 1 != pairs.size();

            appendKey(sb, kv.getKey(), deep + 1, isFormatted);

            if (kv.getValue() instanceof Constable) {
                appendValue(sb, kv.getValue(), true, comma, isFormatted);
            }
            else if (isArray(kv.getValue())) {
                appendArray(sb, deep + 1, comma, getArray(kv.getValue()), isFormatted);
            }
            else {
                appendObject(sb, deep + 1, true, comma, kv.getValue(), isFormatted);
            }

            i++;
        }

        if (isFormatted) {
            appendTabs(sb, deep);
        }

        sb.append('}');

        if (commaInTheEnd) {
            sb.append(',');
        }

        if (isFormatted) {
            sb.append('\n');
        }
    }

    protected static void appendKey(StringBuilder sb, String key, int deep, boolean isFormatted) {
        if (isFormatted) {
            appendTabs(sb, deep);
        }
        sb.append('"').append(key).append('"').append(':');
        if (isFormatted) {
            sb.append(' ');
        }
    }

    protected static void appendValue(StringBuilder sb, Object value,
                                      boolean quoted, boolean commaInTheEnd, boolean isFormatted) {
        if (quoted) {
            sb.append('"').append(value).append('"');
        } else {
            sb.append(value);
        }
        if (commaInTheEnd) {
            sb.append(',');
        }
        if (isFormatted) {
            sb.append('\n');
        }
    }

    private static void appendArray(StringBuilder sb, int deep,
                                    boolean commaInTheEnd, Object[] array, boolean isFormatted)
            throws InvocationTargetException, IllegalAccessException {

        if (array == null) {
            sb.append("null");
        } else if (array.length == 0) {
            sb.append("[]");
        } else {
            sb.append('[');

            if (isFormatted) {
                sb.append('\n');
            }

            var i = 0;
            for (var arrItem : array) {

                var comma = i + 1 < array.length;

                if (arrItem instanceof Constable) {
                    appendObject(sb, deep + 1, false, comma,
                            new Object() {
                                private final Object value = arrItem;
                                public Object getValue() {
                                    return value;
                                }
                            }, isFormatted);
                }
                else if (isArray(arrItem)) {
                    appendArray(sb, deep + 1, comma, getArray(arrItem), isFormatted);
                }
                else {
                    appendObject(sb, deep + 1, false, comma, arrItem, isFormatted);
                }

                i++;
            }

            if (isFormatted) {
                appendTabs(sb, deep);
            }
            sb.append(']');
        }

        if (commaInTheEnd) {
            sb.append(',');
        }

        if (isFormatted) {
            sb.append('\n');
        }
    }

    private static void appendTabs(StringBuilder sb, int count) {
        for (int i = 0; i < count; i++) {
            sb.append("    ");
        }
    }

    private static Collection<Map.Entry<String, Object>> getJsonKeyValuePairs(Object obj)
            throws InvocationTargetException, IllegalAccessException {

        var list = new ArrayList<Map.Entry<String, Object>>();

        for (Class<?> c = obj.getClass(); c != null; c = c.getSuperclass())
        {
            for (var method : c.getDeclaredMethods()) {
                if (isGetter(method)) {

                    var ignoreAnnotation = method.getAnnotation(MonkeyIgnore.class);
                    if (ignoreAnnotation != null && ignoreAnnotation.value()) {
                        continue;
                    }

                    var methodName = normalizeKey(method.getName());
                    var methodResult = method.invoke(obj);
                    list.add(new AbstractMap.SimpleEntry<>(methodName, methodResult));
                }
            }
        }

        return list;
    }

    private static boolean isGetter(Method method) {
        if (Modifier.isPublic(method.getModifiers()) &&
                method.getParameterTypes().length == 0) {
            if (!method.getName().equals("getClass")) {
                if (method.getName().matches("^get[A-Z].*") &&
                        !method.getReturnType().equals(void.class))
                    return true;
                return method.getName().matches("^is[A-Z].*") &&
                        method.getReturnType().equals(boolean.class);
            }
        }
        return false;
    }

    private static String normalizeKey(String key) {

        var prefixes = new HashSet<String>();
        prefixes.add("get");
        prefixes.add("is");

        for (var prefix : prefixes) {
            if (key.startsWith(prefix)) {
                var newKey = key.substring(prefix.length());
                newKey = newKey.substring(0, 1).toLowerCase() + newKey.substring(1);
                return newKey;
            }
        }

        return key.substring(0, 1).toLowerCase() + key.substring(1);
    }

    private static boolean isArray(Object o) {
        return o != null && (o.getClass().isArray() || o instanceof Collection);
    }

    private static Object[] getArray(Object o) {
        if (o instanceof Collection) {
            return ((Collection<?>)o).toArray();
        }
        if (o.getClass().isArray()) {
            return (Object[])o;
        }
        throw new IllegalArgumentException("Not supported type");
    }
}
