/*
 * (C) Copyright 2016 Nuxeo SA (http://nuxeo.com/) and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package org.nuxeo.ecm.platform.importer.queue.manager;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import org.nuxeo.ecm.platform.importer.log.ImporterLogger;
import org.nuxeo.ecm.platform.importer.source.SourceNode;

/**
 * @since 8.3
 */
public abstract class AbstractQueuesManager implements QueuesManager {

    List<BlockingQueue<SourceNode>> queues = new ArrayList<BlockingQueue<SourceNode>>();

    protected int maxQueueSize = 1000;

    protected ImporterLogger log = null;

    public AbstractQueuesManager(ImporterLogger logger, int queuesNb, int maxQueueSize) {
        this.maxQueueSize = maxQueueSize;
        for (int i = 0; i < queuesNb; i++) {
            queues.add(new ArrayBlockingQueue<SourceNode>(maxQueueSize));
        }
        log = logger;
    }

    @Override
    public BlockingQueue<SourceNode> getQueue(int idx) {
        return queues.get(idx);
    }

    @Override
    public boolean isQueueEmpty(int idQueue) {
        return queues.get(idQueue).isEmpty();
    }

    // @Override
    // public int dispatch(SourceNode bh) throws InterruptedException {
    //
    // int idx = getTargetQueue(bh, queues.size());
    //
    // getQueue(idx).put(bh);
    //
    // return idx;
    // }

    @Override
    public int dispatch(SourceNode bh) throws InterruptedException {

        boolean accepted = false;
        int nbTries = 0;

        while (nbTries < 8) {
            int idx = getTargetQueue(bh, getNBConsumers());
            long timeWaitingInSeconds = (long) Math.pow(2, nbTries);
            accepted = getQueue(idx).offer(bh, timeWaitingInSeconds, TimeUnit.SECONDS);

            if (!accepted) {
                log.warn(String.format(
                        "Timeout while waiting for an available queue, waited %d seconds to get a queue for [%s] ",
                        timeWaitingInSeconds, bh.getName()));
                nbTries++;
            } else {
                return idx;
            }
        }
        log.error(String.format("Ended up after %d tries to find a queue for [%s]", nbTries, bh.getName()));
        return -1;

    }
    protected abstract int getTargetQueue(SourceNode bh, int nbQueues);

    @Override
    public int getNBConsumers() {
        return queues.size();
    }
}
