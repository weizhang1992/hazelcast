/*
 * Copyright (c) 2008-2012, Hazelcast, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hazelcast.map;

import com.hazelcast.impl.Record;
import com.hazelcast.map.GenericBackupOperation.BackupOpType;
import com.hazelcast.nio.Data;
import com.hazelcast.spi.BackupAwareOperation;
import com.hazelcast.spi.NodeEngine;
import com.hazelcast.spi.Operation;
import com.hazelcast.spi.ResponseHandler;

import static com.hazelcast.nio.IOUtil.toData;
import static com.hazelcast.nio.IOUtil.toObject;

public abstract class BaseRemoveOperation extends LockAwareOperation implements BackupAwareOperation {
    Object key;
    Record record;

    Data valueData;
    PartitionContainer pc;
    ResponseHandler responseHandler;
    MapPartition mapPartition;
    MapService mapService;
    NodeEngine nodeEngine;


    public BaseRemoveOperation(String name, Data dataKey, String txnId) {
        super(name, dataKey);
        setTxnId(txnId);
    }

    public BaseRemoveOperation() {
    }

    protected boolean prepareTransaction() {
        if (txnId != null) {
            pc.addTransactionLogItem(txnId, new TransactionLogItem(name, dataKey, null, false, true));
            responseHandler.sendResponse(null);
            return true;
        }
        return false;
    }

    protected void init() {
        responseHandler = getResponseHandler();
        mapService = (MapService) getService();
        nodeEngine = (NodeEngine) getNodeEngine();
        pc = mapService.getPartitionContainer(getPartitionId());
        mapPartition = pc.getMapPartition(name);
    }

    protected void load() {
        if (mapPartition.loader != null) {
            key = toObject(dataKey);
            Object oldValue = mapPartition.loader.load(key);
            valueData = toData(oldValue);
        }
    }

    protected void store() {
        if (mapPartition.store != null && mapPartition.writeDelayMillis == 0) {
            if (key == null) {
                key = toObject(dataKey);
            }
            mapPartition.store.delete(key);
        }
    }

    protected void prepareValue() {
        record = mapPartition.records.get(dataKey);
        if (record == null) {
            load();
        } else {
            valueData = record.getValueData();
        }
    }

    public abstract void doOp();

    @Override
    public Object getResponse() {
        return valueData;
    }

    public Operation getBackupOperation() {
        final GenericBackupOperation op = new GenericBackupOperation(name, dataKey, dataValue, ttl);
        op.setBackupOpType(BackupOpType.REMOVE);
        return op;
    }

    public int getAsyncBackupCount() {
        return mapPartition.getAsyncBackupCount();
    }

    public int getSyncBackupCount() {
        return mapPartition.getBackupCount();
    }

    public boolean shouldBackup() {
        return true;
    }

    public void remove() {
        mapPartition.records.remove(dataKey);
    }

    private int getClusterSize() {
        return getNodeEngine().getCluster().getMembers().size();
    }

    @Override
    public String toString() {
        return "BaseRemoveOperation{" + name + "}";
    }
}
