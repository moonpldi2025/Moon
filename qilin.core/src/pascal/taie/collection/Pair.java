package pascal.taie.collection;

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




import java.io.Serializable;
import java.util.Objects;

public record Pair<T1, T2>(T1 first, T2 second)
        implements Serializable {

    @Override
    public String toString() {
        return "<" + first + ", " + second + ">";
    }

    @Override
    public int hashCode() {
        return Hashes.hash(first, second);
    }

    @Override
    public boolean equals(Object obj) {
        if(this == obj ) return true;

        if(!(obj instanceof Pair<?,?>)) return false;

        return Objects.equals(this.first, ((Pair<?,?>)obj).first)
                &&
                Objects.equals(this.second, ((Pair<?,?>)obj).second);
    }

    public static <T1, T2> Pair<T1, T2> of(T1 first, T2 second) {
        return new Pair<>(first, second);
    }
}

