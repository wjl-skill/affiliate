package com.affiliate.platform.affiliate.domain;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

/**
 * Apple SKAdNetwork 4.0 / AdAttributionKit 回传负载对象 (SKAN 4.0 Postback Payload)
 * <p>
 * 遵循 Apple 官方 SKAdNetwork 4.0 格式规范，支持：
 * 1. 4 位层级 Source Identifier (campaign, location, ad-group, creative)；
 * 2. 3 阶段窗口 postback-sequence-index (0, 1, 2)；
 * 3. 细粒度 (0-63) 与 粗粒度 (low, medium, high) 转化值；
 * 4. Apple 加密防伪签名。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SkanPostbackPayload(
        @JsonProperty("version")
        String version,

        @NotBlank
        @JsonProperty("ad-network-id")
        @JsonAlias({"ad_network_id", "adNetworkId"})
        String adNetworkId,

        @NotBlank
        @JsonProperty("source-identifier")
        @JsonAlias({"source_identifier", "sourceIdentifier", "campaign-id"})
        String sourceIdentifier,

        @JsonProperty("app-id")
        @JsonAlias({"app_id", "appId"})
        String appId,

        @NotBlank
        @JsonProperty("transaction-id")
        @JsonAlias({"transaction_id", "transactionId"})
        String transactionId,

        @JsonProperty("postback-sequence-index")
        @JsonAlias({"postback_sequence_index", "postbackSequenceIndex"})
        int postbackSequenceIndex,

        @JsonProperty("conversion-value")
        @JsonAlias({"conversion_value", "conversionValue"})
        Integer conversionValue,

        @JsonProperty("coarse-conversion-value")
        @JsonAlias({"coarse_conversion_value", "coarseConversionValue"})
        String coarseConversionValue,

        @JsonProperty("fidelity-type")
        @JsonAlias({"fidelity_type", "fidelityType"})
        int fidelityType,

        @JsonProperty("attribution-signature")
        @JsonAlias({"attribution_signature", "attributionSignature"})
        String attributionSignature
) {
    public SkanPostbackPayload {
        version = version != null ? version : "4.0";
        fidelityType = fidelityType >= 0 ? fidelityType : 1;
    }
}
