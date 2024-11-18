package pascal.taie.collection;

import org.checkerframework.checker.units.qual.C;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class Maps {
    public static <K1, K2, V> TwoKeyMap<K1, K2, V> newTwoKeyMap() {
        return newTwoKeyMap(newMap(), Maps::newHybridMap);
    }

    public static <K1, K2, V> TwoKeyMap<K1, K2, V> newConcurrentTwoKeyMap() {
        return newTwoKeyMap(newConcurrentMap(), Maps::newConcurrentMap);
    }

    public static <K1, K2, V> TwoKeyMap<K1, K2, V> newTwoKeyMap(
            Map<K1, Map<K2, V>> map1,
            SSupplier<Map<K2, V>> map2Factory) {
        return new MapMapTwoKeyMap<>(map1, map2Factory);
    }

    public static <K, V> Map<K, V> newMap() {
        return new HashMap<>();
    }
    public static <K, V> Map<K, V> newHybridMap() {
        return new HybridHashMap<>();
    }

    public static <K, V> Map<K, V> newConcurrentMap() {
        return new ConcurrentHashMap<>();
    }
    public static <K, V> MultiMap<K, V> newMultiMap() {
        return newMultiMap(newMap(), Sets::newHybridSet);
    }

    public static <K, V> MultiMap<K, V> newMultiMap(Map<K, Set<V>> map,
                                                    SSupplier<Set<V>> setFactory) {
        return new MapSetMultiMap<>(map, setFactory);
    }

    public static <K, V> MultiMap<K, V> newConcurrentMultiMap() {
        return newMultiMap(newConcurrentMap(), Sets::newConcurrentSet);
    }


    public static <K, V> MultiMap<K, V> unmodifiableMultiMap(MultiMap<K, V> map) {
        if (map instanceof UnmodifiableMultiMap<K, V>) {
            return map;
        }
        return new UnmodifiableMultiMap<>(map);
    }




    public static <K1, K2, V> TwoKeyMultiMap<K1, K2, V> newTwoKeyMultiMap() {
        return new MapMultiMapTwoKeyMultiMap<>(newMap(), Maps::newMultiMap);
    }

    public static <K1, K2, V> TwoKeyMultiMap<K1, K2, V> newConcurrentTwoKeyMultiMap() {
        return new MapMultiMapTwoKeyMultiMap<>(new ConcurrentHashMap<>(), Maps::newConcurrentMultiMap);
    }




}
