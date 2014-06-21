/*
 * Copyright (C) 2014 yvolk (Yuri Volkov), http://yurivolkov.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.andstatus.app.service;

import android.test.InstrumentationTestCase;

import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.TestSuite;
import org.andstatus.app.data.TimelineTypeEnum;
import org.andstatus.app.net.HttpConnectionMock;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.TriState;

import java.net.MalformedURLException;
import java.util.Arrays;

public class MyServiceTest extends InstrumentationTestCase implements MyServiceListener {
    private MyAccount ma;
    private HttpConnectionMock httpConnection;
    MyServiceReceiver serviceConnector;

    private volatile CommandData listentedToCommand = CommandData.getEmpty();
    
    private volatile long executionStartCount = 0;
    private volatile long executionEndCount = 0;
    private volatile boolean serviceStopped = false;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        MyLog.i(this, "setUp started");
        TestSuite.initializeWithData(this);

        MyServiceManager.setServiceUnavailable();
        MyServiceManager.stopService();

        httpConnection = new HttpConnectionMock();
        TestSuite.setHttpConnection(httpConnection);
        assertEquals("HttpConnection mocked", MyContextHolder.get().getHttpConnectionMock(),
                httpConnection);
        TestSuite.getMyContextForTest().setOnline(TriState.TRUE);
        // In order the mocked connection to have effect:
        MyContextHolder.get().persistentAccounts().initialize();

        ma = MyContextHolder.get().persistentAccounts()
                .fromAccountName(TestSuite.CONVERSATION_ACCOUNT_NAME);
        assertTrue(TestSuite.CONVERSATION_ACCOUNT_NAME + " exists", ma != null);
        serviceConnector = new MyServiceReceiver(this);
        serviceConnector.registerReceiver(MyContextHolder.get().context());
        MyLog.i(this, "setUp ended");
    }

    public void testRepeatingFailingCommand() throws MalformedURLException {
        MyLog.v(this, "testRepeatingFailingCommand started");
        dropQueues();
        httpConnection.clearPostedData();

        String urlString = "http://andstatus.org/nonexistent2_avatar_" + System.currentTimeMillis() +  ".png";
        AvatarDownloaderTest.changeAvatarUrl(ma, urlString);
        
        listentedToCommand = new CommandData(CommandEnum.FETCH_AVATAR, "", TimelineTypeEnum.UNKNOWN, ma.getUserId());

        long startCount = executionStartCount;
        long endCount = executionEndCount;

        sendListenedToCommand();
        assertTrue("First command started executing", waitForCommandExecutionStarted(startCount));
        sendListenedToCommand();
        assertTrue("First command ended executing", waitForCommandExecutionEnded(endCount));
        assertTrue("Data was posted " + httpConnection.getPostedCounter() + " times",
                httpConnection.getPostedCounter() == 0);
        serviceStopped = false;
        sendListenedToCommand();
        assertFalse("Duplicated commands started executing",
                waitForCommandExecutionStarted(startCount + 1));
        MyServiceManager.stopService();
        assertTrue("Service stopped", waitForServiceStopped());
        MyLog.v(this, "testRepeatingFailingCommand ended");
    }

    private void dropQueues() {
        listentedToCommand = new CommandData(CommandEnum.DROP_QUEUES, "", TimelineTypeEnum.UNKNOWN, 0);
        sendListenedToCommand();
    }

    private void sendListenedToCommand() {
        MyServiceManager.sendCommandEvenForUnavailable(listentedToCommand);
    }

    public void testAutomaticUpdates() {
        MyLog.v(this, "testAutomaticUpdates started");
        dropQueues();
        httpConnection.clearPostedData();

        listentedToCommand = new CommandData(CommandEnum.AUTOMATIC_UPDATE, "", TimelineTypeEnum.ALL, 0);
        long startCount = executionStartCount;
        long endCount = executionEndCount;

        sendListenedToCommand();
        assertTrue("First command started executing", waitForCommandExecutionStarted(startCount));
        assertTrue("First command ended executing", waitForCommandExecutionEnded(endCount));
        assertTrue("Data was posted " + httpConnection.getPostedCounter() + " times",
                httpConnection.getPostedCounter() > 1);
        MyLog.v(this, "testAutomaticUpdates ended");
    }

    public void testHomeTimeline() {
        MyLog.v(this, "testHomeTimeline started");
        dropQueues();
        httpConnection.clearPostedData();

        listentedToCommand = new CommandData(CommandEnum.FETCH_TIMELINE, ma.getAccountName(), TimelineTypeEnum.HOME, 0);
        long startCount = executionStartCount;
        long endCount = executionEndCount;

        sendListenedToCommand();
        assertTrue("First command started executing", waitForCommandExecutionStarted(startCount));
        assertTrue("First command ended executing", waitForCommandExecutionEnded(endCount));
        String message = "Data was posted " + httpConnection.getPostedCounter() + " times; "
                + Arrays.toString(httpConnection.getPathStringList().toArray(new String[]{}));
        assertTrue(message, httpConnection.getPostedCounter() == 1);
        MyLog.v(this, "testHomeTimeline ended");
    }

    public void testRateLimitStatus() {
        MyLog.v(this, "RateLimitStatus started");
        dropQueues();
        httpConnection.clearPostedData();

        listentedToCommand = new CommandData(CommandEnum.RATE_LIMIT_STATUS, TestSuite.STATUSNET_TEST_ACCOUNT_NAME, TimelineTypeEnum.ALL, 0);
        long startCount = executionStartCount;
        long endCount = executionEndCount;

        sendListenedToCommand();
        assertTrue("First command started executing", waitForCommandExecutionStarted(startCount));
        assertTrue("First command ended executing", waitForCommandExecutionEnded(endCount));
        assertTrue("Data was posted " + httpConnection.getPostedCounter() + " times",
                httpConnection.getPostedCounter() > 0);
        MyLog.v(this, "testHomeTimeline ended");
    }

    private boolean waitForCommandExecutionStarted(long startCount) {
        for (int pass = 0; pass < 1000; pass++) {
            if (executionStartCount > startCount) {
                return true;
            }
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                e.printStackTrace();
                return false;
            }
        }
        return false;
    }

    private boolean waitForCommandExecutionEnded(long endCount) {
        for (int pass = 0; pass < 1000; pass++) {
            if (executionEndCount > endCount) {
                return true;
            }
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                e.printStackTrace();
                return false;
            }
        }
        return false;
    }

    private boolean waitForServiceStopped() {
        for (int pass = 0; pass < 10000; pass++) {
            if (serviceStopped) {
                return true;
            }
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                e.printStackTrace();
                return false;
            }
        }
        return false;
    }

    @Override
    public void onReceive(CommandData commandData, MyServiceEvent myServiceEvent) {
        String locEvent = "ignored";
        switch (myServiceEvent) {
            case BEFORE_EXECUTING_COMMAND:
                if (commandData.equals(listentedToCommand)) {
                    executionStartCount++;
                    locEvent = "execution started";
                }
                break;
            case AFTER_EXECUTING_COMMAND:
                if (commandData.equals(listentedToCommand)) {
                    executionEndCount++;
                    locEvent = "execution ended";
                }
                break;
            case ON_STOP:
                serviceStopped = true;
                locEvent = "service stopped";
                break;
            default:
                break;
        }
        MyLog.v(this, "onReceive; " + locEvent + "; " + commandData + "; event=" + myServiceEvent);

    }

    @Override
    protected void tearDown() throws Exception {
        MyLog.v(this, "tearDown started");
        serviceConnector.unregisterReceiver(MyContextHolder.get().context());
        TestSuite.setHttpConnection(null);
        TestSuite.getMyContextForTest().setOnline(TriState.UNKNOWN);
        MyContextHolder.get().persistentAccounts().initialize();
        super.tearDown();
        MyLog.v(this, "tearDown ended");
    }
}
