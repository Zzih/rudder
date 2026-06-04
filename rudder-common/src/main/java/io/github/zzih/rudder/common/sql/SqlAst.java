/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.github.zzih.rudder.common.sql;

import com.alibaba.druid.sql.SQLUtils;
import com.alibaba.druid.sql.ast.SQLExpr;
import com.alibaba.druid.sql.ast.SQLName;
import com.alibaba.druid.sql.ast.expr.SQLIdentifierExpr;
import com.alibaba.druid.sql.ast.expr.SQLPropertyExpr;

/** access / projection 两个 resolver 共用的 Druid AST 标识符工具(去引号、取段、星号判定)。 */
final class SqlAst {

    static final String STAR = "*";

    private SqlAst() {
    }

    /** 去掉标识符两侧的方言引号(`` ` `` / {@code "} / {@code []}),null 安全。 */
    static String normalize(String name) {
        return name == null ? null : SQLUtils.normalize(name);
    }

    /** 标识符末段(t.col → col;a.b.c → c)。 */
    static String lastName(SQLExpr expr) {
        if (expr instanceof SQLPropertyExpr p) {
            return normalize(p.getName());
        }
        if (expr instanceof SQLIdentifierExpr id) {
            return normalize(id.getName());
        }
        if (expr instanceof SQLName n) {
            return normalize(n.getSimpleName());
        }
        return null;
    }

    /** SQLPropertyExpr 的直接 owner 段(t.col → t;schema.t.col → t)。 */
    static String ownerAlias(SQLPropertyExpr p) {
        SQLExpr owner = p.getOwner();
        if (owner instanceof SQLPropertyExpr op) {
            return normalize(op.getName());
        }
        if (owner instanceof SQLIdentifierExpr oid) {
            return normalize(oid.getName());
        }
        return null;
    }

    /** 列名是否为具体值(非 null/空/{@code "*"} 通配)。 */
    static boolean isConcreteColumnName(String col) {
        return col != null && !col.isEmpty() && !STAR.equals(col);
    }
}
