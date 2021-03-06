package com.openchat.secureim.configuration;

import com.fasterxml.jackson.annotation.JsonProperty;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

public class DirectoryConfiguration {

  @JsonProperty
  @NotNull
  @Valid
  private RedisConfiguration redis;
    
  @JsonProperty
  @NotNull
  @Valid
  private SqsConfiguration sqs;
    
  @JsonProperty
  @NotNull
  @Valid
  private DirectoryClientConfiguration client;

  @JsonProperty
  @NotNull
  @Valid
  private DirectoryServerConfiguration server;

  public RedisConfiguration getRedisConfiguration() {
    return redis;
  }

  public SqsConfiguration getSqsConfiguration() {
    return sqs;
  }

  public DirectoryClientConfiguration getDirectoryClientConfiguration() {
    return client;
  }

  public DirectoryServerConfiguration getDirectoryServerConfiguration() {
    return server;
  }

}
