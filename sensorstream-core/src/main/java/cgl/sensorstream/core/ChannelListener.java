package cgl.sensorstream.core;

import cgl.iotcloud.core.api.thrift.TChannel;
import cgl.iotcloud.core.utils.SerializationUtils;
import com.ss.commons.DestinationChangeListener;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.recipes.leader.LeaderSelector;
import org.apache.curator.framework.recipes.leader.LeaderSelectorListenerAdapter;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.curator.utils.CloseableUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class ChannelListener {
    private static Logger LOG = LoggerFactory.getLogger(ChannelListener.class);

    private CuratorFramework client = null;

    private String channelPath = null;

    private LeaderSelector leaderSelector;

    private final Lock lock = new ReentrantLock();

    private final Condition condition = lock.newCondition();

    private DestinationChangeListener dstListener;

    private ChannelListenerState state = ChannelListenerState.WAITING_FOR_LEADER;

    private ChannelsState channelsState;

    private boolean bolt = false;

    private TChannel channel;

    private String groupName;

    public ChannelListener(String channelPath, String groupName,
                           DestinationChangeListener dstListener, ChannelsState channelsState, CuratorFramework client) {
        this(channelPath, groupName, dstListener, channelsState, false, client);
    }

    public ChannelListener(String channelPath, String groupName,
                           DestinationChangeListener dstListener, ChannelsState channelsState, boolean bolt, CuratorFramework client) {
        try {
            this.channelPath = channelPath;
            this.dstListener = dstListener;
            this.channelsState = channelsState;
            this.bolt = bolt;
            this.client = client;
            this.groupName = groupName;
        } catch (Exception e) {
            String msg = "Failed to create the listener for ZK path " + channelPath;
            LOG.error(msg);
            throw new RuntimeException(msg);
        }
    }

    public void start() {
        if (!bolt) {
            leaderSelector = new LeaderSelector(client, channelPath, new ChannelLeaderSelector());
            leaderSelector.start();
            leaderSelector.autoRequeue();
            state = ChannelListenerState.WAITING_FOR_LEADER;
        } else {
            byte data[];
            try {
                data = client.getData().forPath(channelPath);
                TChannel channel = new TChannel();
                SerializationUtils.createThriftFromBytes(data, channel);
                if (dstListener != null) {
                    dstListener.addDestination(groupName, Utils.convertChannelToDestination(channel));
                }
            } catch (Exception e) {
                LOG.error("Failed to start a destination listener", e);
            }
        }
    }

    public void stop() {
        LOG.info("Stopping channel listener {}", channelPath);
        lock.lock();
        try {
            // leaderSelector.close();
            if (!bolt) {
                condition.signal();
            } else {
                if (dstListener != null && channel != null) {
                    dstListener.removeDestination(channel.getName());
                }
                channelsState.removeLeader();
            }
        } catch (Exception e) {
            LOG.error("Failed to get data", e);
        } finally {
            lock.unlock();
        }
    }

    public void addPath(String path) {
        if (dstListener != null) {
            dstListener.addPathToDestination(groupName, path);
        }
    }

    public void close() {
        CloseableUtils.closeQuietly(leaderSelector);
    }

    private class ChannelLeaderSelector extends LeaderSelectorListenerAdapter {
        @Override
        public void takeLeadership(CuratorFramework curatorFramework) throws Exception {
            LOG.info(channelPath + " is the new leader.");
            lock.lock();
            try {
                if (channelsState.addLeader()) {
                    byte data[] = curatorFramework.getData().forPath(channelPath);
                    channel = new TChannel();
                    SerializationUtils.createThriftFromBytes(data, channel);
                    if (dstListener != null) {
                        dstListener.addDestination(groupName, Utils.convertChannelToDestination(channel));
                    }

                    state = ChannelListenerState.LEADER;
                    condition.await();
                    if (dstListener != null) {
                        dstListener.removeDestination(groupName);
                    }
                    channelsState.removeLeader();
                    state = ChannelListenerState.LEADER_LEFT;
                }
            } catch (InterruptedException e) {
                LOG.info(channelPath + " leader was interrupted.");
                Thread.currentThread().interrupt();
            } finally {
                lock.unlock();
                LOG.info(channelPath + " leader relinquishing leadership.\n");
            }
        }
    }

    public ChannelListenerState getState() {
        return state;
    }
}
