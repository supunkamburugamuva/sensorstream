package cgl.sensorstream.storm.perf;

import backtype.storm.Config;
import backtype.storm.LocalCluster;
import backtype.storm.StormSubmitter;
import backtype.storm.topology.OutputFieldsDeclarer;
import backtype.storm.topology.TopologyBuilder;
import backtype.storm.tuple.Fields;
import backtype.storm.tuple.Tuple;
import com.ss.mqtt.*;
import com.ss.mqtt.bolt.MQTTBolt;
import org.fusesource.mqtt.client.QoS;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.*;

public class MQTTPerfTopology extends AbstractPerfTopology {
    public static void main(String[] args) throws Exception {
        TopologyBuilder builder = new TopologyBuilder();

        TopologyConfiguration configuration = parseArgs(args);
        int i = 0;
        for (String ip : configuration.getIp()) {
            MQTTSpout spout = new MQTTSpout(new SpoutConfigurator(configuration, ip), null);
            MQTTBolt bolt = new MQTTBolt(new BoltConfigurator(configuration, ip));
            builder.setSpout("mqtt_spout_" + i, spout, 1);
            builder.setBolt("mqtt_bolt_" + i, bolt, 1).shuffleGrouping("mqtt_spout_" + i);
            i++;
        }

        submit(args, "mqttTest", builder, configuration);
    }

    private static class TimeStampMessageBuilder implements MessageBuilder {
        @Override
        public List<Object> deSerialize(MQTTMessage envelope) {
            try {
                byte []body = envelope.getBody().toByteArray();
                String bodyS = new String(body);
                BufferedReader reader = new BufferedReader(new StringReader(bodyS));
                String timeStampS = reader.readLine();
                Long timeStamp = Long.parseLong(timeStampS);

                long currentTime = System.currentTimeMillis();

                System.out.println("latency: " + (currentTime - timeStamp) + " initial time: " + timeStamp + " current: " + currentTime);
                List<Object> tuples = new ArrayList<Object>();
                tuples.add(envelope);
                return tuples;
            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        public MQTTMessage serialize(Tuple tuple) {
            Object message = tuple.getValue(0);
            if (message instanceof  MQTTMessage){
                return (MQTTMessage) message;
            }
            return null;
        }
    }

    private static class SpoutConfigurator implements MQTTConfigurator {
        private TopologyConfiguration configuration;
        private String ip;

        public SpoutConfigurator(TopologyConfiguration configuration, String ip) {
            this.configuration = configuration;
            this.ip = ip;
        }

        public MessageBuilder getMessageBuilder() {
            return new TimeStampMessageBuilder();
        }

        @Override
        public QoS qosLevel() {
            return QoS.AT_MOST_ONCE;
        }

        @Override
        public String getURL() {
            return ip;
        }

        @Override
        public List<String> getQueueName() {
            List<String> list = new ArrayList<String>();
            for (int i = 0; i < configuration.getNoQueues(); i++) {
                list.add(configuration.getRecevBaseQueueName() + "_" + i);
            }
            return list;
        }

        public void declareOutputFields(OutputFieldsDeclarer outputFieldsDeclarer) {
            outputFieldsDeclarer.declare(new Fields("time1"));
        }

        public int queueSize() {
            return 1024;
        }

        @Override
        public DestinationSelector getDestinationSelector() {
            return null;
        }
    }

    private static class BoltConfigurator implements MQTTConfigurator {
        private TopologyConfiguration configuration;

        private String ip;

        private BoltConfigurator(TopologyConfiguration configuration, String ip) {
            this.configuration = configuration;
            this.ip = ip;
        }

        public MessageBuilder getMessageBuilder() {
            return new TimeStampMessageBuilder();
        }

        @Override
        public QoS qosLevel() {
            return QoS.AT_MOST_ONCE;
        }

        @Override
        public String getURL() {
            return ip;
        }

        @Override
        public List<String> getQueueName() {
            List<String> list = new ArrayList<String>();
            for (int i = 0; i < configuration.getNoQueues(); i++) {
                list.add(configuration.getSendBaseQueueName() + "_" + i);
            }
            return list;
        }

        public void declareOutputFields(OutputFieldsDeclarer outputFieldsDeclarer) {
            outputFieldsDeclarer.declare(new Fields("body"));
        }

        public int queueSize() {
            return 1024;
        }

        @Override
        public DestinationSelector getDestinationSelector() {
            return new DestinationSelector() {
                @Override
                public String select(Tuple message) {
                    MQTTMessage mqttMessage = (MQTTMessage) message.getValue(0);
                    String queue = mqttMessage.getQueue();
                    if (queue != null) {
                        String queueNumber = queue.substring(queue.indexOf("_") + 1);
                        return configuration.getSendBaseQueueName() + "_" + queueNumber;
                    }
                    return null;
                }
            };
        }
    }
}
