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

package qilin.microben.core.reflog;

import qilin.microben.utils.Assert;

public class MethodInvoke {
    public static void main(String[] args) throws Exception {
        Object c = new Object();
        Object d = MethodInvoke.class.getMethod("id", Object.class).invoke(new MethodInvokeInstance(), c);
        Assert.mayAlias(c, d);
    }

    public Object id(Object a) {
        return null;
    }

    static class MethodInvokeInstance extends MethodInvoke {
        public Object id(Object a) {
            return a;
        }
    }
}