package com.openchat.messaging.server.internal;


import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class GcmRequestEntity {

  @JsonProperty(value = "collapse_key")
  private String collapseKey;

  @JsonProperty(value = "time_to_live")
  private Long ttl;

  @JsonProperty(value = "delay_while_idle")
  private Boolean delayWhileIdle;

  @JsonProperty(value = "data")
  private Map<String, String> data;

  @JsonProperty(value = "registration_ids")
  private List<String> registrationIds;

  public GcmRequestEntity(String collapseKey, Long ttl, Boolean delayWhileIdle,
                          Map<String, String> data, List<String> registrationIds)
  {
    this.collapseKey     = collapseKey;
    this.ttl             = ttl;
    this.delayWhileIdle  = delayWhileIdle;
    this.data            = data;
    this.registrationIds = registrationIds;
  }
}
