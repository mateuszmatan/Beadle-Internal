package com.bbh.itss.dso.portal.adapter;

import lombok.NoArgsConstructor;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
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
import java.util.concurrent.ConcurrentHashMap;

import static java.lang.invoke.MethodHandles.lookup;
import static java.lang.reflect.Modifier.isPrivate;
import static java.lang.reflect.Modifier.isStatic;
import static lombok.AccessLevel.PRIVATE;

@NoArgsConstructor(access = PRIVATE)
public final class RecordMapper {

    private static final MethodHandles.Lookup LOOKUP = lookup();

    private static final Map<Class<?>, Shape> SHAPES = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Map<String, MethodHandle>> READERS = new ConcurrentHashMap<>();

    private static Map<String, MethodHandle> readers(Class<?> type) {
        Map<String, MethodHandle> readers = new HashMap<>();
        try {
            for (Class<?> declaring = type; declaring != Object.class; declaring = declaring.getSuperclass()) {
                for (Method method : declaring.getDeclaredMethods()) {
                    if (method.getParameterCount() == 0 && method.getReturnType() != void.class
                            && !isStatic(method.getModifiers()) && !isPrivate(method.getModifiers())) {
                        method.setAccessible(true);
                        readers.putIfAbsent(method.getName(), LOOKUP.unreflect(method));
                    }
                }
            }
            for (Class<?> declaring = type; declaring != Object.class; declaring = declaring.getSuperclass()) {
                for (Field field : declaring.getDeclaredFields()) {
                    if (!isStatic(field.getModifiers())) {
                        field.setAccessible(true);
                        readers.putIfAbsent(field.getName(), LOOKUP.unreflectGetter(field));
                    }
                }
            }
        } catch (IllegalAccessException e) {
            throw new IllegalArgumentException(e);
        }
        return readers;
    }

    public static <T> T map(Object source, Class<T> target) {
        return target.cast(convert(source, target));
    }

    public static <T extends Record> T map(Class<T> target, Object... sources) {
        return target.cast(SHAPES.computeIfAbsent(target, Shape::of).build(sources));
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
        Shape shape = SHAPES.computeIfAbsent(type, Shape::of);
        Object source = shape.mirrored().filter(mirrored -> !mirrored.isInstance(value))
                .map(mirrored -> SHAPES.computeIfAbsent(mirrored, Shape::of).build(value)).orElse(value);
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
            MethodHandle reader = source == null ? null
                    : READERS.computeIfAbsent(source.getClass(), RecordMapper::readers).get(name);
            if (reader != null) {
                try {
                    return reader.invoke(source);
                } catch (RuntimeException | Error e) {
                    throw e;
                } catch (Throwable e) {
                    throw new IllegalArgumentException(e);
                }
            }
        }
        return null;
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
                throw new IllegalArgumentException(e);
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
                throw e.getCause() instanceof RuntimeException runtime ? runtime
                        : new IllegalArgumentException(e.getCause());
            } catch (ReflectiveOperationException e) {
                throw new IllegalArgumentException(e);
            }
        }
    }
}
