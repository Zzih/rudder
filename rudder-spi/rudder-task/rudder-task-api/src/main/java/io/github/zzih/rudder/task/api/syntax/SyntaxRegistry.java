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

package io.github.zzih.rudder.task.api.syntax;

import io.github.zzih.rudder.task.api.syntax.LanguageSyntax.Snippet;
import io.github.zzih.rudder.task.api.task.enums.TaskType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 各任务类型语言语法定义的中心注册表。
 * 通过 /api/config/syntax/{taskType} 提供给前端。
 */
public final class SyntaxRegistry {

    private SyntaxRegistry() {
    }

    private static final Map<String, LanguageSyntax> REGISTRY = new HashMap<>();

    static {
        // ===== 共享 SQL 基础 =====
        List<String> baseKeywords = List.of(
                "SELECT", "FROM", "WHERE", "AND", "OR", "NOT", "IN", "BETWEEN", "LIKE", "IS", "NULL",
                "INSERT", "INTO", "VALUES", "UPDATE", "SET", "DELETE", "CREATE", "DROP", "ALTER", "TABLE",
                "INDEX", "VIEW", "DATABASE", "SCHEMA", "IF", "EXISTS",
                "JOIN", "INNER", "LEFT", "RIGHT", "FULL", "OUTER", "CROSS", "ON", "USING",
                "GROUP BY", "ORDER BY", "HAVING", "LIMIT", "OFFSET", "UNION", "ALL", "DISTINCT",
                "AS", "ASC", "DESC", "CASE", "WHEN", "THEN", "ELSE", "END",
                "PRIMARY", "KEY", "FOREIGN", "REFERENCES", "UNIQUE", "DEFAULT",
                "WITH", "RECURSIVE", "OVER", "PARTITION BY",
                "EXPLAIN", "SHOW", "DESCRIBE", "USE", "TRUNCATE",
                "BEGIN", "COMMIT", "ROLLBACK", "TRANSACTION", "TRUE", "FALSE");

        List<String> baseFunctions = List.of(
                "COUNT", "SUM", "AVG", "MIN", "MAX",
                "COALESCE", "NULLIF", "CAST", "CONVERT",
                "ABS", "CEIL", "FLOOR", "ROUND", "MOD",
                "CONCAT", "SUBSTRING", "LENGTH", "TRIM", "UPPER", "LOWER", "REPLACE",
                "NOW", "ROW_NUMBER", "RANK", "DENSE_RANK", "LAG", "LEAD", "FIRST_VALUE", "LAST_VALUE");

        List<Snippet> sqlSnippets = List.of(
                Snippet.builder().label("sel").insertText("SELECT ${1:*}\nFROM ${2:table}\nWHERE ${3:1=1}")
                        .detail("SELECT template").build(),
                Snippet.builder().label("selc").insertText("SELECT COUNT(*)\nFROM ${1:table}\nWHERE ${2:1=1}")
                        .detail("SELECT COUNT").build(),
                Snippet.builder().label("ins").insertText("INSERT INTO ${1:table} (${2:columns})\nVALUES (${3:values})")
                        .detail("INSERT template").build(),
                Snippet.builder().label("upd")
                        .insertText("UPDATE ${1:table}\nSET ${2:col} = ${3:val}\nWHERE ${4:id} = ${5:1}")
                        .detail("UPDATE template").build(),
                Snippet.builder().label("del").insertText("DELETE FROM ${1:table}\nWHERE ${2:id} = ${3:1}")
                        .detail("DELETE template").build(),
                Snippet.builder().label("cte")
                        .insertText("WITH ${1:name} AS (\n  ${2:SELECT 1}\n)\nSELECT * FROM ${1:name}")
                        .detail("CTE template").build(),
                Snippet.builder().label("join")
                        .insertText("${1:LEFT} JOIN ${2:table} ${3:t}\n  ON ${3:t}.${4:id} = ${5:a}.${6:id}")
                        .detail("JOIN template").build());

        // ===== MySQL =====
        REGISTRY.put(TaskType.MYSQL.name(), LanguageSyntax.builder()
                .language("sql")
                .keywords(merge(baseKeywords, List.of(
                        "AUTO_INCREMENT", "ENGINE", "CHARSET", "COLLATE", "COMMENT",
                        "ON DUPLICATE KEY UPDATE", "IGNORE", "REPLACE",
                        "BINARY", "UNSIGNED", "ZEROFILL",
                        "PROCEDURE", "FUNCTION", "TRIGGER", "EVENT", "LOCK", "UNLOCK", "TABLES")))
                .functions(merge(baseFunctions, List.of(
                        "IFNULL", "IF", "GROUP_CONCAT", "FIND_IN_SET",
                        "DATE_FORMAT", "STR_TO_DATE", "DATEDIFF", "DATE_ADD", "DATE_SUB", "CURDATE", "CURTIME",
                        "JSON_EXTRACT", "JSON_OBJECT", "JSON_ARRAY", "JSON_UNQUOTE",
                        "UUID", "MD5", "SHA1", "SHA2")))
                .snippets(sqlSnippets)
                .build());

        // ===== Hive =====
        REGISTRY.put(TaskType.HIVE_SQL.name(), LanguageSyntax.builder()
                .language("sql")
                .keywords(merge(baseKeywords, List.of(
                        "PARTITIONED BY", "CLUSTERED BY", "SORTED BY", "BUCKETS",
                        "STORED AS", "ROW FORMAT", "DELIMITED", "FIELDS TERMINATED BY",
                        "LOCATION", "TBLPROPERTIES", "EXTERNAL", "TEMPORARY",
                        "OVERWRITE", "DIRECTORY", "LATERAL VIEW", "EXPLODE", "POSEXPLODE",
                        "DISTRIBUTE BY", "SORT BY", "CLUSTER BY",
                        "MSCK", "REPAIR", "LOAD DATA", "INPATH",
                        "ORC", "PARQUET", "AVRO", "TEXTFILE", "SEQUENCEFILE", "SERDE")))
                .functions(merge(baseFunctions, List.of(
                        "COLLECT_LIST", "COLLECT_SET", "SIZE", "ARRAY", "MAP", "STRUCT",
                        "SPLIT", "REGEXP_REPLACE", "REGEXP_EXTRACT", "PARSE_URL",
                        "GET_JSON_OBJECT", "FROM_UNIXTIME", "UNIX_TIMESTAMP", "TO_DATE",
                        "NVL", "NVL2", "PERCENTILE", "PERCENTILE_APPROX", "NTILE")))
                .snippets(sqlSnippets)
                .build());

        // ===== StarRocks =====
        REGISTRY.put(TaskType.STARROCKS_SQL.name(), LanguageSyntax.builder()
                .language("sql")
                .keywords(merge(baseKeywords, List.of(
                        "DUPLICATE KEY", "AGGREGATE KEY", "UNIQUE KEY",
                        "DISTRIBUTED BY HASH", "PROPERTIES", "REPLICATION_NUM",
                        "BITMAP", "HLL", "ROUTINE LOAD", "STREAM LOAD", "BROKER LOAD",
                        "MATERIALIZED VIEW", "REFRESH")))
                .functions(merge(baseFunctions, List.of(
                        "BITMAP_UNION", "BITMAP_COUNT", "BITMAP_AND", "BITMAP_OR",
                        "HLL_UNION_AGG", "HLL_CARDINALITY", "APPROX_COUNT_DISTINCT",
                        "ARRAY_AGG", "ARRAY_CONTAINS", "ARRAY_MAP",
                        "JSON_QUERY", "PARSE_JSON", "GET_JSON_STRING",
                        "WINDOW_FUNNEL", "RETENTION")))
                .snippets(sqlSnippets)
                .build());

        // ===== Trino =====
        REGISTRY.put(TaskType.TRINO_SQL.name(), LanguageSyntax.builder()
                .language("sql")
                .keywords(merge(baseKeywords, List.of(
                        "CATALOG", "CONNECTOR", "TABLESAMPLE", "UNNEST",
                        "FORMAT", "ANALYZE", "PREPARE", "EXECUTE", "DEALLOCATE",
                        "GRANT", "REVOKE", "ROLE")))
                .functions(merge(baseFunctions, List.of(
                        "APPROX_DISTINCT", "APPROX_PERCENTILE",
                        "ARRAY_JOIN", "ARRAY_DISTINCT", "ARRAY_SORT", "CONTAINS", "CARDINALITY",
                        "MAP_KEYS", "MAP_VALUES", "MAP_ENTRIES",
                        "TRY_CAST", "TRY", "TYPEOF",
                        "DATE_TRUNC", "DATE_DIFF", "FORMAT_DATETIME",
                        "REGEXP_LIKE", "REGEXP_REPLACE", "REGEXP_EXTRACT",
                        "TRANSFORM", "FILTER", "REDUCE", "ZIP_WITH")))
                .snippets(sqlSnippets)
                .build());

        // ===== Spark SQL =====
        REGISTRY.put(TaskType.SPARK_SQL.name(), LanguageSyntax.builder()
                .language("sql")
                .keywords(merge(baseKeywords, List.of(
                        "PARTITIONED BY", "CLUSTERED BY", "SORTED BY",
                        "STORED AS", "LOCATION", "TBLPROPERTIES",
                        "LATERAL VIEW", "EXPLODE", "POSEXPLODE", "INLINE",
                        "DISTRIBUTE BY", "SORT BY", "CLUSTER BY",
                        "CACHE TABLE", "UNCACHE TABLE", "REFRESH",
                        "DELTA", "PARQUET", "ORC")))
                .functions(merge(baseFunctions, List.of(
                        "COLLECT_LIST", "COLLECT_SET", "ARRAY_AGG",
                        "SPLIT", "REGEXP_REPLACE", "REGEXP_EXTRACT",
                        "FROM_UNIXTIME", "UNIX_TIMESTAMP", "TO_DATE", "TO_TIMESTAMP", "DATE_TRUNC",
                        "GET_JSON_OBJECT", "FROM_JSON", "TO_JSON", "SCHEMA_OF_JSON",
                        "PERCENTILE_APPROX", "APPROX_COUNT_DISTINCT")))
                .snippets(sqlSnippets)
                .build());

        // ===== Flink SQL =====
        REGISTRY.put(TaskType.FLINK_SQL.name(), LanguageSyntax.builder()
                .language("sql")
                .keywords(merge(baseKeywords, List.of(
                        "WATERMARK", "FOR", "SYSTEM_TIME",
                        "TEMPORARY", "CONNECTOR", "FORMAT",
                        "PRIMARY KEY NOT ENFORCED",
                        "TUMBLE", "HOP", "CUMULATE", "SESSION",
                        "MATCH_RECOGNIZE", "PATTERN", "DEFINE", "MEASURES",
                        "PROCTIME", "ROWTIME")))
                .functions(merge(baseFunctions, List.of(
                        "TUMBLE_START", "TUMBLE_END", "TUMBLE_ROWTIME",
                        "HOP_START", "HOP_END", "HOP_ROWTIME",
                        "LISTAGG", "JSON_STRING", "JSON_OBJECT", "JSON_ARRAY",
                        "TO_TIMESTAMP_LTZ", "CURRENT_WATERMARK")))
                .snippets(sqlSnippets)
                .build());

        // ===== Python =====
        REGISTRY.put(TaskType.PYTHON.name(), LanguageSyntax.builder()
                .language("python")
                .keywords(List.of(
                        "import", "from", "as", "def", "class", "return", "yield",
                        "if", "elif", "else", "for", "while", "break", "continue",
                        "try", "except", "finally", "raise", "with", "assert",
                        "lambda", "pass", "del", "global", "nonlocal", "async", "await",
                        "True", "False", "None", "and", "or", "not", "in", "is"))
                .functions(List.of(
                        "print", "len", "range", "enumerate", "zip", "map", "filter",
                        "sorted", "reversed", "list", "dict", "set", "tuple",
                        "str", "int", "float", "bool", "type", "isinstance",
                        "open", "read", "write", "close",
                        "os.path.join", "os.listdir", "os.makedirs",
                        "json.loads", "json.dumps",
                        "datetime.now", "time.sleep"))
                .snippets(List.of(
                        Snippet.builder().label("def").insertText("def ${1:func}(${2:args}):\n    ${3:pass}")
                                .detail("Function").build(),
                        Snippet.builder().label("for").insertText("for ${1:item} in ${2:items}:\n    ${3:pass}")
                                .detail("For loop").build(),
                        Snippet.builder().label("if").insertText("if ${1:condition}:\n    ${2:pass}").detail("If block")
                                .build(),
                        Snippet.builder().label("try")
                                .insertText("try:\n    ${1:pass}\nexcept ${2:Exception} as e:\n    ${3:raise}")
                                .detail("Try/except").build(),
                        Snippet.builder().label("with")
                                .insertText("with open(${1:path}, '${2:r}') as ${3:f}:\n    ${4:pass}")
                                .detail("With open").build(),
                        Snippet.builder().label("main").insertText("if __name__ == '__main__':\n    ${1:main()}")
                                .detail("Main guard").build()))
                .build());

        // ===== Shell =====
        REGISTRY.put(TaskType.SHELL.name(), LanguageSyntax.builder()
                .language("shell")
                .keywords(List.of(
                        "if", "then", "elif", "else", "fi",
                        "for", "do", "done", "while", "until",
                        "case", "esac", "in",
                        "function", "return", "exit",
                        "echo", "printf", "read", "export", "source", "local",
                        "set", "unset", "shift", "trap", "eval", "exec"))
                .functions(List.of(
                        "grep", "awk", "sed", "cut", "sort", "uniq", "wc", "head", "tail",
                        "find", "xargs", "tee", "tr", "diff",
                        "curl", "wget", "ssh", "scp", "rsync",
                        "date", "sleep", "kill", "ps", "top",
                        "mkdir", "rm", "cp", "mv", "chmod", "chown", "ln",
                        "cat", "less", "more", "touch", "basename", "dirname"))
                .snippets(List.of(
                        Snippet.builder().label("if").insertText("if [ ${1:condition} ]; then\n  ${2:echo}\nfi")
                                .detail("If block").build(),
                        Snippet.builder().label("for").insertText("for ${1:i} in ${2:items}; do\n  ${3:echo}\ndone")
                                .detail("For loop").build(),
                        Snippet.builder().label("while").insertText("while ${1:true}; do\n  ${2:echo}\ndone")
                                .detail("While loop").build(),
                        Snippet.builder().label("func").insertText("${1:func_name}() {\n  ${2:echo}\n}")
                                .detail("Function").build(),
                        Snippet.builder().label("shebang").insertText("#!/bin/bash\nset -euo pipefail\n\n${1:}")
                                .detail("Script header").build()))
                .build());
    }

    public static LanguageSyntax get(TaskType taskType) {
        return REGISTRY.get(taskType.name());
    }

    public static Map<String, LanguageSyntax> getAll() {
        return Map.copyOf(REGISTRY);
    }

    private static List<String> merge(List<String> base, List<String> extra) {
        var list = new java.util.ArrayList<>(base);
        list.addAll(extra);
        return List.copyOf(list);
    }
}
