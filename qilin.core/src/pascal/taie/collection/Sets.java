/*
 * Tai-e: A Static Analysis Framework for Java
 *
 * Copyright (C) 2022 Tian Tan <tiantan@nju.edu.cn>
 * Copyright (C) 2022 Yue Li <yueli@nju.edu.cn>
 *
 * This file is part of Tai-e.
 *
 * Tai-e is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation, either version 3
 * of the License, or (at your option) any later version.
 *
 * Tai-e is distributed in the hope that it will be useful,but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with Tai-e. If not, see <https://www.gnu.org/licenses/>.
 */

package pascal.taie.collection;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Static utility methods for {@link Set}.
 */
public final class Sets {

    private Sets() {
    }

    // Factory methods for sets and maps
    public static <E> Set<E> newSet() {
        return new HashSet<>();
    }

    public static <E> Set<E> newSet(Collection<? extends E> set) {
        return new HashSet<>(set);
    }

    public static <E> Set<E> newConcurrentSet(){
        return Collections.newSetFromMap(new ConcurrentHashMap<>());
    }

    public static <E> Set<E> newHybridSet(Collection<E> c) {
        return new HybridHashSet<>(c);
    }

    public static <E> Set<E> newHybridSet() {
        return new HybridHashSet<>();
    }

    /**
     * @return {@code true} if two sets have at least one overlapped element.
     */
    public static <E> boolean haveOverlap(Set<E> s1, Set<E> s2) {
        Set<E> small, large;
        if (s1.size() <= s2.size()) {
            small = s1;
            large = s2;
        } else {
            small = s2;
            large = s1;
        }
        for (E o : small) {
            if (large.contains(o)) {
                return true;
            }
        }
        return false;
    }
}
