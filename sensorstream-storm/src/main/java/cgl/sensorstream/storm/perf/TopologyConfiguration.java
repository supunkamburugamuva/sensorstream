package cgl.sensorstream.storm.perf;

import java.io.Serializable;

public class TopologyConfiguration implements Serializable {
    private int noWorkers = 4;

    private String topologyName = "perf";

    private String ip;

    private int noQueues;

    private String recevBaseQueueName;

    private String sendBaseQueueName;

    public TopologyConfiguration(String ip, int noQueues, String baseQueueName, String sendBaseQueueName) {
        this.ip = ip;
        this.noQueues = noQueues;
        this.recevBaseQueueName = baseQueueName;
        this.sendBaseQueueName = sendBaseQueueName;
    }

    public int getNoWorkers() {
        return noWorkers;
    }

    public String getTopologyName() {
        return topologyName;
    }

    public String getIp() {
        return ip;
    }

    public int getNoQueues() {
        return noQueues;
    }

    public String getRecevBaseQueueName() {
        return recevBaseQueueName;
    }

    public void setNoWorkers(int noWorkers) {
        this.noWorkers = noWorkers;
    }

    public void setTopologyName(String topologyName) {
        this.topologyName = topologyName;
    }

    public String getSendBaseQueueName() {
        return sendBaseQueueName;
    }
}
