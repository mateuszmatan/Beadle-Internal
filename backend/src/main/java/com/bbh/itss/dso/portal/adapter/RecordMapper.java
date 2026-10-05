package com.bbh.itss.dso.portal.adapter;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class RecordMapper {

    private static final ClassValue<Shape> SHAPES = new ClassValue<>() {
        @Override
        protected Shape computeValue(Class<?> type) {
            return Shape.of(type);
        }
    };

    private static final ClassValue<Map<String, Method>> ACCESSORS = new ClassValue<>() {
        @Override
        protected Map<String, Method> computeValue(Class<?> type) {
            Map<String, Method> accessors = new HashMap<>();
            for (Class<?> declaring = type; declaring != null && declaring != Object.class;
                 declaring = declaring.getSuperclass()) {
                for (Method method : declaring.getDeclaredMethods()) {
                    if (method.getParameterCount() == 0 && method.getReturnType() != void.class
                            && !Modifier.isStatic(method.getModifiers()) && !Modifier.isPrivate(method.getModifiers())
                            && !accessors.containsKey(method.getName())) {
                        method.setAccessible(true);
                        accessors.put(method.getName(), method);
                    }
                }
            }
            return accessors;
        }
    };

    private RecordMapper() {
    }

    public static <T> T map(Object source, Class<T> target) {
        return target.cast(convert(source, target));
    }

    public static <T extends Record> T map(Class<T> target, Object... sources) {
        return target.cast(SHAPES.get(target).build(sources));
    }

    private static Object convert(Object value, Type target) {
        if (value == null) {
            return null;
        }
        if (value instanceof List<?> list) {
            Type element = argument(target, 0);
            return list.stream().map(item -> convert(item, element)).toList();
        }
        if (value instanceof Map<?, ?> map) {
            return convert(map, target);
        }
        Class<?> type = raw(target);
        if (!type.isRecord()) {
            return value;
        }
        Shape shape = SHAPES.get(type);
        Object source = shape.mirrored().filter(mirrored -> !mirrored.isInstance(value))
                .map(mirrored -> SHAPES.get(mirrored).build(value)).orElse(value);
        return shape.build(source);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Map<Object, Object> convert(Map<?, ?> map, Type target) {
        Class<?> key = raw(argument(target, 0));
        Map<Object, Object> converted = key.isEnum() ? new EnumMap(key) : new LinkedHashMap<>();
        Type value = argument(target, 1);
        map.forEach((name, item) -> converted.put(name, convert(item, value)));
        return converted;
    }

    private static Type argument(Type type, int index) {
        return type instanceof ParameterizedType parameterized ? parameterized.getActualTypeArguments()[index]
                : Object.class;
    }

    private static Class<?> raw(Type type) {
        if (type instanceof Class<?> plain) {
            return plain;
        }
        return type instanceof ParameterizedType parameterized ? raw(parameterized.getRawType()) : Object.class;
    }

    private static Object read(String name, Object... sources) {
        for (Object source : sources) {
            Method accessor = source == null ? null : ACCESSORS.get(source.getClass()).get(name);
            if (accessor != null) {
                try {
                    return accessor.invoke(source);
                } catch (InvocationTargetException e) {
                    throw rethrown(e);
                } catch (IllegalAccessException e) {
                    throw new IllegalStateException(e);
                }
            }
        }
        return null;
    }

    private static RuntimeException rethrown(InvocationTargetException e) {
        return e.getCause() instanceof RuntimeException runtime ? runtime : new IllegalStateException(e.getCause());
    }

    private record Shape(Constructor<?> constructor, List<RecordComponent> components, Optional<Class<?>> mirrored) {

        static Shape of(Class<?> type) {
            RecordComponent[] components = type.getRecordComponents();
            try {
                Constructor<?> constructor = type.getDeclaredConstructor(
                        Arrays.stream(components).map(RecordComponent::getType).toArray(Class<?>[]::new));
                constructor.setAccessible(true);
                return new Shape(constructor, List.of(components), mirrorOf(type));
            } catch (NoSuchMethodException e) {
                throw new IllegalStateException(e);
            }
        }

        private static Optional<Class<?>> mirrorOf(Class<?> type) {
            return Arrays.stream(type.getGenericInterfaces())
                    .filter(candidate -> candidate instanceof ParameterizedType parameterized
                            && parameterized.getRawType() == Mirrors.class)
                    .<Class<?>>map(candidate -> raw(argument(candidate, 0)))
                    .findFirst();
        }

        Object build(Object... sources) {
            Object[] values = components.stream()
                    .map(component -> convert(read(component.getName(), sources), component.getGenericType()))
                    .toArray();
            try {
                return constructor.newInstance(values);
            } catch (InvocationTargetException e) {
                throw rethrown(e);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        }
    }
}
