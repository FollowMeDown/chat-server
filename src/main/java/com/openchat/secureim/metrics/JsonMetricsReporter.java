package com.openchat.secureim.metrics;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricFilter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.ScheduledReporter;
import com.codahale.metrics.Snapshot;
import com.codahale.metrics.Timer;
import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.Map;
import java.util.SortedMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

public class JsonMetricsReporter extends ScheduledReporter {
  private final JsonFactory factory = new JsonFactory();

  private final String table;
  private final String sunnylabsHost;
  private final String host;

  public JsonMetricsReporter(MetricRegistry registry, String token, String sunnylabsHost)
      throws UnknownHostException
  {
    super(registry, "jsonmetrics-reporter", MetricFilter.ALL, TimeUnit.SECONDS, TimeUnit.MILLISECONDS);
    this.table         = token;
    this.sunnylabsHost = sunnylabsHost;
    this.host          = InetAddress.getLocalHost().getHostName();
  }

  @Override
  public void report(SortedMap<String, Gauge>     stringGaugeSortedMap,
                     SortedMap<String, Counter>   stringCounterSortedMap,
                     SortedMap<String, Histogram> stringHistogramSortedMap,
                     SortedMap<String, Meter>     stringMeterSortedMap,
                     SortedMap<String, Timer>     stringTimerSortedMap)
  {
    try {
      URL url = new URL("https", sunnylabsHost, 443, "/report/metrics?t=" + table + "&h=" + host);
      HttpURLConnection connection = (HttpURLConnection) url.openConnection();

      connection.setDoOutput(true);
      connection.addRequestProperty("Content-Type", "application/json");

      OutputStream  outputStream = connection.getOutputStream();
      JsonGenerator json         = factory.createGenerator(outputStream, JsonEncoding.UTF8);

      json.writeStartObject();

      for (Map.Entry<String, Gauge> gauge : stringGaugeSortedMap.entrySet()) {
        reportGauge(json, gauge.getKey(), gauge.getValue());
      }

      for (Map.Entry<String, Counter> counter : stringCounterSortedMap.entrySet()) {
        reportCounter(json, counter.getKey(), counter.getValue());
      }

      for (Map.Entry<String, Histogram> histogram : stringHistogramSortedMap.entrySet()) {
        reportHistogram(json, histogram.getKey(), histogram.getValue());
      }

      for (Map.Entry<String, Meter> meter : stringMeterSortedMap.entrySet()) {
        reportMeter(json, meter.getKey(), meter.getValue());
      }

      for (Map.Entry<String, Timer> timer : stringTimerSortedMap.entrySet()) {
        reportTimer(json, timer.getKey(), timer.getValue());
      }

      json.writeEndObject();
      json.close();

      outputStream.close();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  public void reportGauge(JsonGenerator json, String name, Gauge gauge) throws IOException {
    json.writeFieldName(sanitize(name));
    json.writeObject(evaluateGauge(gauge));
  }

  public void reportCounter(JsonGenerator json, String name, Counter counter) throws IOException {
    json.writeFieldName(sanitize(name));
    json.writeNumber(counter.getCount());
  }

  public void reportHistogram(JsonGenerator json, String name, Histogram histogram) throws IOException {
    Snapshot snapshot = histogram.getSnapshot();
    json.writeFieldName(sanitize(name));
    json.writeStartObject();
    json.writeNumberField("count", histogram.getCount());
    writeSnapshot(json, snapshot);
    json.writeEndObject();
  }

  public void reportMeter(JsonGenerator json, String name, Meter meter) throws IOException {
    json.writeFieldName(sanitize(name));
    json.writeStartObject();
    json.writeNumberField("count", meter.getCount());
    json.writeNumberField("mean", meter.getMeanRate());
    json.writeNumberField("m1", meter.getOneMinuteRate());
    json.writeNumberField("m5", meter.getFiveMinuteRate());
    json.writeNumberField("m15", meter.getFifteenMinuteRate());
    json.writeEndObject();
  }

  public void reportTimer(JsonGenerator json, String name, Timer timer) throws IOException {
    json.writeFieldName(sanitize(name));
    json.writeStartObject();
    json.writeNumberField("count", timer.getCount());
    writeSnapshot(json, timer.getSnapshot());
    json.writeEndObject();
  }

  private static Object evaluateGauge(Gauge<?> gauge) {
    try {
      return gauge.getValue();
    } catch (RuntimeException e) {
      return "error reading gauge: " + e.getMessage();
    }
  }

  private static void writeSnapshot(JsonGenerator json, Snapshot snapshot) throws IOException {
    json.writeNumberField("max", snapshot.getMax());
    json.writeNumberField("mean", snapshot.getMean());
    json.writeNumberField("min", snapshot.getMin());
    json.writeNumberField("stddev", snapshot.getStdDev());
    json.writeNumberField("median", snapshot.getMedian());
    json.writeNumberField("p75", snapshot.get75thPercentile());
    json.writeNumberField("p95", snapshot.get95thPercentile());
    json.writeNumberField("p98", snapshot.get98thPercentile());
    json.writeNumberField("p99", snapshot.get99thPercentile());
    json.writeNumberField("p999", snapshot.get999thPercentile());
  }

  private static final Pattern SIMPLE_NAMES = Pattern.compile("[^a-zA-Z0-9_.\\-~]");

  private String sanitize(String metricName) {
    return SIMPLE_NAMES.matcher(metricName).replaceAll("_");
  }

}
