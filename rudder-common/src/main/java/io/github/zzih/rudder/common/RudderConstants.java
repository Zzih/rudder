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

package io.github.zzih.rudder.common;

public final class RudderConstants {

    public static final String WORKFLOW_DEFINITIONS_DIR = "workflowDefinitions";
    public static final String SCRIPTS_DIR = "scripts";
    public static final String WORKFLOW_DAG_FILE = "dag.json";
    /** GIT 模式下脚本统一存放的仓库名(每个工作空间一个 ide 仓库)。 */
    public static final String IDE_REPO = "ide";

    private RudderConstants() {
    }
}
