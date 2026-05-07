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

package io.github.zzih.rudder.common.utils.naming;

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Objects;

public class CodeGenerateUtils {

    private static final CodeGenerator codeGenerator;

    public static int getProcessID() {
        RuntimeMXBean runtimeMXBean = ManagementFactory.getRuntimeMXBean();
        return Integer.parseInt(runtimeMXBean.getName().split("@")[0]);
    }

    static {
        try {
            codeGenerator = new CodeGenerator(InetAddress.getLocalHost().getHostName() + "-" + getProcessID());
        } catch (UnknownHostException e) {
            throw new CodeGenerateException(e.getMessage());
        }
    }

    public static long genCode() throws CodeGenerateException {
        return codeGenerator.genCode();
    }

    public static class CodeGenerator {

        // start timestamp
        private static final long START_TIMESTAMP = 1609430400000L; // 2021-01-01 00:00:00
        // Each machine generates 32 in the same millisecond
        private static final long LOW_DIGIT_BIT = 5L;
        private static final long MACHINE_BIT = 5L;
        private static final long MAX_LOW_DIGIT = ~(-1L << LOW_DIGIT_BIT);
        // The displacement to the left
        private static final long HIGH_DIGIT_LEFT = LOW_DIGIT_BIT + MACHINE_BIT;
        public final long machineHash;
        private long lowDigit = 0L;
        private long recordMillisecond = -1L;

        private static final long SYSTEM_TIMESTAMP = System.currentTimeMillis();
        private static final long SYSTEM_NANOTIME = System.nanoTime();

        public CodeGenerator(String appName) {
            this.machineHash = Math.abs(Objects.hash(appName)) % (1 << MACHINE_BIT);
        }

        public synchronized long genCode() throws CodeGenerateException {
            long nowtMillisecond = systemMillisecond();
            if (nowtMillisecond < recordMillisecond) {
                throw new CodeGenerateException("New code exception because time is set back.");
            }
            if (nowtMillisecond == recordMillisecond) {
                lowDigit = (lowDigit + 1) & MAX_LOW_DIGIT;
                if (lowDigit == 0L) {
                    while (nowtMillisecond <= recordMillisecond) {
                        nowtMillisecond = systemMillisecond();
                    }
                }
            } else {
                lowDigit = 0L;
            }
            recordMillisecond = nowtMillisecond;
            return (nowtMillisecond - START_TIMESTAMP) << HIGH_DIGIT_LEFT | machineHash << LOW_DIGIT_BIT | lowDigit;
        }

        private long systemMillisecond() {
            return SYSTEM_TIMESTAMP + (System.nanoTime() - SYSTEM_NANOTIME) / 1000000;
        }
    }

    public static class CodeGenerateException extends RuntimeException {

        public CodeGenerateException(String message) {
            super(message);
        }
    }
}
