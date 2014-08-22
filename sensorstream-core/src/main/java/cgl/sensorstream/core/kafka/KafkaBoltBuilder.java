package cgl.sensorstream.core.kafka;

import backtype.storm.topology.IRichBolt;
import cgl.sensorstream.core.BoltBuilder;
import storm.kafka.bolt.KafkaBolt;

import java.util.List;
import java.util.Map;

public class KafkaBoltBuilder implements BoltBuilder {
    @Override
    public IRichBolt build(String topologyName, String sensor, String channel, List<String> fields, String convertor, Map<String, Object> properties, String zkConnection) {
        KafkaBolt kafkaBolt = new KafkaBolt();

        return null;
    }
}