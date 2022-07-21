package kafka.cli.emulator;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;

public class EmulatorArchive {

  private final Map<TopicPartition, List<EmulatorRecord>> records = new HashMap<>();
  private final Map<TopicPartition, Long> oldestOffsets = new HashMap<>();
  private final Map<TopicPartition, Long> oldestTimestamps = new HashMap<>();

  List<String> includeTopics = new ArrayList<>();
  List<String> excludeTopics = new ArrayList<>();

  public static EmulatorArchive create() {
    return new EmulatorArchive();
  }

  public void append(TopicPartition topicPartition, EmulatorRecord record) {
    records.computeIfPresent(
      topicPartition,
      (tp, records) -> {
        records.add(record);
        oldestOffsets.put(
          tp,
          oldestOffsets.get(tp) < record.offset()
            ? record.offset()
            : oldestOffsets.get(tp)
        );
        oldestTimestamps.put(
          tp,
          oldestTimestamps.get(tp) < record.timestamp()
            ? record.timestamp()
            : oldestTimestamps.get(tp)
        );
        return records;
      }
    );
    records.computeIfAbsent(
      topicPartition,
      tp -> {
        final var records = new ArrayList<EmulatorRecord>();
        records.add(record);
        oldestOffsets.put(tp, record.offset());
        oldestTimestamps.put(tp, record.timestamp());
        return records;
      }
    );
  }

  public void setExcludeTopics(List<String> excludeTopics) {
    this.excludeTopics = excludeTopics;
  }

  public void setIncludeTopics(List<String> includeTopics) {
    this.includeTopics = includeTopics;
  }

  public Set<TopicPartition> topicPartitions() {
    return records
      .keySet()
      .stream()
      .filter(topicPartition ->
        includeTopics.isEmpty() || includeTopics.contains(topicPartition.topic())
      )
      .filter(topicPartition ->
        excludeTopics.isEmpty() || !excludeTopics.contains(topicPartition.topic())
      )
      .collect(Collectors.toSet());
  }

  public Map<String, Integer> topicPartitionNumber() {
    final var map = new HashMap<String, Integer>();
    for (var tp : topicPartitions()) {
      final var partitions = tp.partition() + 1;
      map.computeIfPresent(tp.topic(), (t, p) -> partitions > p ? partitions : p);
      map.putIfAbsent(tp.topic(), partitions);
    }
    return map;
  }

  public Collection<List<EmulatorRecord>> all() {
    return records.values();
  }

  public List<EmulatorRecord> records(TopicPartition tp) {
    return records.get(tp);
  }

  public Long oldestOffsets(TopicPartition tp) {
    return oldestOffsets.get(tp);
  }

  public Long oldestTimestamps(TopicPartition tp) {
    return oldestTimestamps.get(tp);
  }

  record EmulatorRecord(
    String topic,
    int partition,
    long offset,
    long timestamp,
    long afterMs,
    FieldFormat keyFormat,
    byte[] key,
    FieldFormat valueFormat,
    byte[] value
  ) {
    public static EmulatorRecord from(
      ConsumerRecord<byte[], byte[]> record,
      FieldFormat keyFormat,
      FieldFormat valueFormat,
      long afterMs
    ) {
      return new EmulatorRecord(
        record.topic(),
        record.partition(),
        record.offset(),
        record.timestamp(),
        afterMs,
        keyFormat,
        record.key(),
        valueFormat,
        record.value()
      );
    }
  }

  enum FieldFormat {
    STRING,
    LONG,
    INTEGER,
    BYTES,
  }
}
