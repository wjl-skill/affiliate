package com.affiliate.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;

@TableName("cdp_customer_event")
public class CdpCustomerEventEntity {
    @TableId(type = IdType.INPUT) private String eventId;
    private String tenantId;
    private String primaryId;
    private String eventType;
    private Instant eventAt;
    private String payload;
    public CdpCustomerEventEntity() {}
    public CdpCustomerEventEntity(String eventId,String tenantId,String primaryId,String eventType,Instant eventAt,String payload){this.eventId=eventId;this.tenantId=tenantId;this.primaryId=primaryId;this.eventType=eventType;this.eventAt=eventAt;this.payload=payload;}
    public String getEventId(){return eventId;} public void setEventId(String v){eventId=v;}
    public String getTenantId(){return tenantId;} public void setTenantId(String v){tenantId=v;}
    public String getPrimaryId(){return primaryId;} public void setPrimaryId(String v){primaryId=v;}
    public String getEventType(){return eventType;} public void setEventType(String v){eventType=v;}
    public Instant getEventAt(){return eventAt;} public void setEventAt(Instant v){eventAt=v;}
    public String getPayload(){return payload;} public void setPayload(String v){payload=v;}
}
