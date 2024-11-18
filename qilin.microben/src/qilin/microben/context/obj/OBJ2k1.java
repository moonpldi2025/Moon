/* Qilin - a Java Pointer Analysis Framework
 * Copyright (C) 2021-2030 Qilin developers
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3.0 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Lesser Public License for more details.
 *
 * You should have received a copy of the GNU General Lesser Public
 * License along with this program.  If not, see
 * <https://www.gnu.org/licenses/lgpl-3.0.en.html>.
 */

package qilin.microben.context.obj;


import qilin.microben.utils.Assert;

public class OBJ2k1 {
    static class B {
        Object f;
    }

    static class A {
        B create() {
            return new B(); // B
        }
    }

    public static void main(String[] args) {
        A a1 = new A(); // A1
        A a2 = new A(); // A2
        Object o1 = new Object(); // O1
        Object o2 = new Object(); // O2
        B b1 = a1.create();
        B b2 = a2.create();
        b1.f = o1;
        b2.f = o2;
        Object w1 = b1.f;
        Object w2 = b2.f;
        Assert.notAlias(w1, w2);
        Assert.mayAlias(w1, o1);
    }
}
