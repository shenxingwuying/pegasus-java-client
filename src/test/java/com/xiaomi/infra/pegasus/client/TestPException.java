// Copyright (c) 2019, Xiaomi, Inc.  All rights reserved.
// This source code is licensed under the Apache License Version 2.0, which
// can be found in the LICENSE file in the root directory of this source tree.

package com.xiaomi.infra.pegasus.client;

import com.xiaomi.infra.pegasus.apps.update_request;
import com.xiaomi.infra.pegasus.base.blob;
import com.xiaomi.infra.pegasus.base.error_code;
import com.xiaomi.infra.pegasus.base.error_code.error_types;
import com.xiaomi.infra.pegasus.base.gpid;
import com.xiaomi.infra.pegasus.client.PegasusTable.Request;
import com.xiaomi.infra.pegasus.operator.rrdb_put_operator;
import com.xiaomi.infra.pegasus.rpc.TableOptions;
import com.xiaomi.infra.pegasus.rpc.async.ClusterManager;
import com.xiaomi.infra.pegasus.rpc.async.TableHandler;
import io.netty.util.concurrent.DefaultPromise;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import org.junit.Assert;
import org.junit.Test;

public class TestPException {
  private String metaList = "127.0.0.1:34601,127.0.0.1:34602,127.0.0.1:34603";
  private Request request = new Request("hashKey".getBytes(), "sortKey".getBytes());

  @Test
  public void testThreadInterrupted() throws Exception {
    PException ex = PException.threadInterrupted("test", new InterruptedException("intxxx"));
    String exceptionInfo =
        "{version}: com.xiaomi.infra.pegasus.rpc.ReplicationException: ERR_THREAD_INTERRUPTED: [table=test] Thread was interrupted: intxxx";
    Assert.assertEquals(exceptionInfo, ex.getMessage());
  }

  @Test
  public void testTimeout() throws Exception {
    PException ex =
        PException.timeout(metaList, "test", request, 1000, new TimeoutException("tmxxx"));
    String exceptionInfo =
        String.format(
            "{version}: com.xiaomi.infra.pegasus.rpc.ReplicationException: ERR_TIMEOUT: [metaServer=%s, table=test, request=%s, timeout=1000ms] Timeout on Future await: tmxxx",
            metaList, request.toString());
    Assert.assertEquals(exceptionInfo, ex.getMessage());
  }

  @Test
  public void testVersion() {
    // Test the constructors of PException

    PException ex = new PException("test");
    Assert.assertEquals("{version}: test", ex.getMessage());

    ex = new PException("test", new TimeoutException());
    Assert.assertEquals("{version}: test", ex.getMessage());
  }

  @Test
  public void testHandleReplicationException() throws Exception {
    String metaList = "127.0.0.1:34601,127.0.0.1:34602,127.0.0.1:34603";
    ClusterManager manager =
        new ClusterManager(ClientOptions.builder().metaServers(metaList).build());
    TableHandler table = manager.openTable("temp", TableOptions.forTest());
    DefaultPromise<Void> promise = table.newPromise();
    update_request req = new update_request(new blob(), new blob(), 100);
    gpid gpid = table.getGpidByHash(1);
    rrdb_put_operator op = new rrdb_put_operator(gpid, table.getTableName(), req, 0);
    op.rpc_error.errno = error_code.error_types.ERR_OBJECT_NOT_FOUND;

    // set failure in promise, the exception is thrown as ExecutionException.
    int timeout = 1000;
    PegasusClient client = (PegasusClient) PegasusClientFactory.getSingletonClient();
    PegasusTable pegasusTable = new PegasusTable(client, table);
    pegasusTable.handleReplicaException(request, promise, op, table, timeout);
    try {
      promise.get();
    } catch (ExecutionException e) {
      TableHandler.ReplicaConfiguration replicaConfig = table.getReplicaConfig(gpid.get_pidx());
      String server =
          replicaConfig.primaryAddress.get_ip() + ":" + replicaConfig.primaryAddress.get_port();

      String msg =
          String.format(
              "com.xiaomi.infra.pegasus.client.PException: {version}: com.xiaomi.infra.pegasus.rpc.ReplicationException: ERR_OBJECT_NOT_FOUND: [metaServer=%s,table=temp,operation=put,request=%s,replicaServer=%s,gpid=(%s),timeout=%dms] The replica server doesn't serve this partition!",
              client.getMetaList(), request.toString(), server, gpid.toString(), timeout);
      Assert.assertEquals(e.getMessage(), msg);
      return;
    } catch (InterruptedException e) {
      Assert.fail();
    }
    Assert.fail();
  }

  @Test
  public void testTimeOutIsZero() throws Exception {
    // ensure "PException ERR_TIMEOUT" is thrown with the real timeout value, when user given
    // timeout is 0.
    String metaList = "127.0.0.1:34601,127.0.0.1:34602, 127.0.0.1:34603";
    ClusterManager manager =
        new ClusterManager(ClientOptions.builder().metaServers(metaList).build());
    TableHandler table = manager.openTable("temp", TableOptions.forTest());
    DefaultPromise<Void> promise = table.newPromise();
    update_request req = new update_request(new blob(), new blob(), 100);
    gpid gpid = table.getGpidByHash(1);
    rrdb_put_operator op = new rrdb_put_operator(gpid, table.getTableName(), req, 0);
    op.rpc_error.errno = error_types.ERR_TIMEOUT;

    PegasusClient client = (PegasusClient) PegasusClientFactory.getSingletonClient();
    PegasusTable pegasusTable = new PegasusTable(client, table);
    pegasusTable.handleReplicaException(request, promise, op, table, 0);
    try {
      promise.get();
    } catch (Exception e) {
      TableHandler.ReplicaConfiguration replicaConfig = table.getReplicaConfig(gpid.get_pidx());
      String server =
          replicaConfig.primaryAddress.get_ip() + ":" + replicaConfig.primaryAddress.get_port();

      String msg =
          String.format(
              "com.xiaomi.infra.pegasus.client.PException: {version}: com.xiaomi.infra.pegasus.rpc.ReplicationException: ERR_TIMEOUT: [metaServer=%s,table=temp,operation=put,request=%s,replicaServer=%s,gpid=(%s),timeout=1000ms] The operation is timed out!",
              client.getMetaList(), request.toString(), server, gpid.toString());
      Assert.assertEquals(e.getMessage(), msg);
    }
  }
}
