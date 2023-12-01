/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.tahu;

import java.util.concurrent.atomic.AtomicLong;

import org.eclipse.tahu.message.BdSeqManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AtomicBdSeqManager implements BdSeqManager {

    private static final Logger LOG = LoggerFactory.getLogger(AtomicBdSeqManager.class);

    private final AtomicLong bdSeqNum = new AtomicLong(0);

    @Override
    public long getNextDeathBdSeqNum() {
        LOG.trace("BdSeqManager getNextDeathBdSeqNum called");

        // Returns 0-255 by zeroing all bits above lowest 8
        long nextBdSeqNum = bdSeqNum.getAndIncrement() & 0xFFL;

        LOG.trace("BdSeqManager getNextDeathBdSeqNum complete: nextBdSeqNum {}", nextBdSeqNum);
        return nextBdSeqNum;
    }

    @Override
    public void storeNextDeathBdSeqNum(long nextBdSeqNum) {
        LOG.trace("BdSeqManager storeNextDeathBdSeqNum called: nextBdSeqNum {}", nextBdSeqNum);
        bdSeqNum.set(nextBdSeqNum);
        LOG.trace("BdSeqManager storeNextDeathBdSeqNum complete");
    }
}
