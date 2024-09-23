/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hedera.mirror.restjava.controller;

import static com.hedera.mirror.restjava.common.Constants.ACCOUNT_ID;
import static com.hedera.mirror.restjava.common.Constants.DEFAULT_LIMIT;
import static com.hedera.mirror.restjava.common.Constants.MAX_LIMIT;
import static com.hedera.mirror.restjava.common.Constants.RECEIVER_ID;
import static com.hedera.mirror.restjava.common.Constants.SENDER_ID;
import static com.hedera.mirror.restjava.common.Constants.TOKEN_ID;
import static com.hedera.mirror.restjava.dto.TokenAirdropRequest.AirdropRequestType.OUTSTANDING;
import static com.hedera.mirror.restjava.dto.TokenAirdropRequest.AirdropRequestType.PENDING;

import com.google.common.collect.ImmutableSortedMap;
import com.hedera.mirror.rest.model.TokenAirdrop;
import com.hedera.mirror.rest.model.TokenAirdropsResponse;
import com.hedera.mirror.restjava.common.EntityIdParameter;
import com.hedera.mirror.restjava.common.EntityIdRangeParameter;
import com.hedera.mirror.restjava.common.LinkFactory;
import com.hedera.mirror.restjava.dto.TokenAirdropRequest;
import com.hedera.mirror.restjava.dto.TokenAirdropRequest.AirdropRequestType;
import com.hedera.mirror.restjava.mapper.TokenAirdropMapper;
import com.hedera.mirror.restjava.service.Bound;
import com.hedera.mirror.restjava.service.TokenAirdropService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@CustomLog
@RequestMapping("/api/v1/accounts/{id}/airdrops")
@RequiredArgsConstructor
@RestController
public class TokenAirdropsController {
    private static final Function<TokenAirdrop, Map<String, String>> EXTRACTOR = tokenAirdrop -> ImmutableSortedMap.of(
            RECEIVER_ID, tokenAirdrop.getReceiverId(),
            SENDER_ID, tokenAirdrop.getSenderId(),
            TOKEN_ID, tokenAirdrop.getTokenId());

    private final LinkFactory linkFactory;
    private final TokenAirdropMapper tokenAirdropMapper;
    private final TokenAirdropService service;

    @GetMapping(value = "/outstanding")
    TokenAirdropsResponse getOutstandingAirdrops(
            @PathVariable EntityIdParameter id,
            @RequestParam(defaultValue = DEFAULT_LIMIT) @Positive @Max(MAX_LIMIT) int limit,
            @RequestParam(defaultValue = "asc") Sort.Direction order,
            @RequestParam(name = RECEIVER_ID, required = false) @Size(max = 2) List<EntityIdRangeParameter> receiverIds,
            @RequestParam(name = TOKEN_ID, required = false) @Size(max = 2) List<EntityIdRangeParameter> tokenIds) {
        var entityIdsBound = new Bound(receiverIds, true, ACCOUNT_ID);
        return processRequest(id, entityIdsBound, limit, order, tokenIds, OUTSTANDING, RECEIVER_ID);
    }

    @GetMapping(value = "/pending")
    TokenAirdropsResponse getPendingAirdrops(
            @PathVariable EntityIdParameter id,
            @RequestParam(defaultValue = DEFAULT_LIMIT) @Positive @Max(MAX_LIMIT) int limit,
            @RequestParam(defaultValue = "asc") Sort.Direction order,
            @RequestParam(name = SENDER_ID, required = false) @Size(max = 2) List<EntityIdRangeParameter> senderIds,
            @RequestParam(name = TOKEN_ID, required = false) @Size(max = 2) List<EntityIdRangeParameter> tokenIds) {
        var entityIdsBound = new Bound(senderIds, true, ACCOUNT_ID);
        return processRequest(id, entityIdsBound, limit, order, tokenIds, PENDING, SENDER_ID);
    }

    private TokenAirdropsResponse processRequest(
            EntityIdParameter id,
            Bound entityIdsBound,
            int limit,
            Sort.Direction order,
            List<EntityIdRangeParameter> tokenIds,
            AirdropRequestType type,
            String primarySortField) {
        var request = TokenAirdropRequest.builder()
                .accountId(id)
                .entityIds(entityIdsBound)
                .limit(limit)
                .order(order)
                .tokenIds(new Bound(tokenIds, false, TOKEN_ID))
                .type(type)
                .build();

        var response = service.getAirdrops(request);
        var airdrops = tokenAirdropMapper.map(response);
        var sort = Sort.by(order, primarySortField, TOKEN_ID);
        var pageable = PageRequest.of(0, limit, sort);
        var links = linkFactory.create(airdrops, pageable, EXTRACTOR);
        return new TokenAirdropsResponse().airdrops(airdrops).links(links);
    }
}