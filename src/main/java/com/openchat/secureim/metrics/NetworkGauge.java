package com.openchat.secureim.metrics;


import com.yammer.metrics.core.Gauge;
import com.openchat.secureim.util.Pair;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;

public abstract class NetworkGauge extends Gauge<Long> {

  protected Pair<Long, Long> getSentReceived() throws IOException {
    File           proc          = new File("/proc/net/dev");
    BufferedReader reader        = new BufferedReader(new FileReader(proc));
    String         header        = reader.readLine();
    String         header2       = reader.readLine();

    long           bytesSent     = 0;
    long           bytesReceived = 0;

    String interfaceStats;

      while ((interfaceStats = reader.readLine()) != null) {
        String[] stats = interfaceStats.split("\\s+");

        if (!stats[1].equals("lo:")) {
          bytesReceived += Long.parseLong(stats[2]);
          bytesSent     += Long.parseLong(stats[10]);
        }
      }

    return new Pair<>(bytesSent, bytesReceived);
  }
}
