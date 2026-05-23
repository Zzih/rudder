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

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.apache.calcite.config.Lex;
import org.apache.calcite.sql.parser.SqlParser;
import org.apache.calcite.sql.parser.babel.SqlBabelParserImpl;

/**
 * Calcite Babel parser 的 {@link SqlParser.Config} 工厂。Babel 比 base parser 宽容,支持 DDL、LATERAL VIEW、
 * INSERT OVERWRITE 等方言扩展;不认识的 stmt 解析为 generic node,由下游 instanceof 白名单忽略(fail-open)。
 *
 * <p>Config 是 immutable,按 dialect 缓存避免热路径每次 alloc。
 */
public final class RudderSqlParser {

    private static final ConcurrentMap<Lex, SqlParser.Config> CACHE = new ConcurrentHashMap<>();

    private RudderSqlParser() {
    }

    /** null dialect 走 MySQL lex(项目里多数 JDBC 引擎语法都跟 MySQL 接近)。 */
    public static SqlParser.Config babelConfig(SqlDialect dialect) {
        Lex lex = dialect != null ? dialect.lex() : Lex.MYSQL;
        return CACHE.computeIfAbsent(lex, RudderSqlParser::buildConfig);
    }

    private static SqlParser.Config buildConfig(Lex lex) {
        return SqlParser.config()
                .withLex(lex)
                .withQuoting(lex.quoting)
                .withCaseSensitive(false)
                .withParserFactory(SqlBabelParserImpl.FACTORY);
    }
}
