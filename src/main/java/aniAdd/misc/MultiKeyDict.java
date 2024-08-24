package aniAdd.misc;

import lombok.val;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MultiKeyDict<C extends Enum<C>, K, V> {

    private final IKeyMapper<C, K, V> keyMapper;
    private final Map<C, Map<K, V>> dict = new ConcurrentHashMap<>();

    public MultiKeyDict(Class<C> category, IKeyMapper<C, K, V> keyMapper) {
        this.keyMapper = keyMapper;

        for (C cat : EnumSet.allOf(category)) {
            dict.put(cat, new ConcurrentHashMap<>());
        }
    }

    public void clear() {
        for (val entry : dict.entrySet()) {
            entry.getValue().clear();
        }
    }

    public V get(C cat, K key) {
        return dict.get(cat).get(key);
    }

    public void put(V value) {
        for (val entry : dict.entrySet()) {
            entry.getValue().put(keyMapper.getKey(entry.getKey(), value), value);
        }
    }

    public void remove(K key) {
        for (val category : dict.keySet()) {
            dict.get(category).remove(key);
        }
    }

    public boolean contains(C cat, K key) {
        return dict.get(cat).containsKey(key);
    }

    public Collection<V> values() {
        if (dict.isEmpty()) {
            return new ArrayList<>();
        }
        return anyMap().values();
    }

    public int size() {
        return !dict.isEmpty() ? anyMap().size() : 0;
    }

    private Map<K, V> anyMap() {
        return dict.get(dict.keySet().iterator().next());
    }

    public interface IKeyMapper<C, K, V> {
        K getKey(C category, V value);
    }
}
