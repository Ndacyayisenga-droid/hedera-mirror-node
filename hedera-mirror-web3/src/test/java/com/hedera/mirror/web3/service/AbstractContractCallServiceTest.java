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

package com.hedera.mirror.web3.service;

import static com.hedera.mirror.web3.utils.ContractCallTestUtil.ESTIMATE_GAS_ERROR_MESSAGE;
import static com.hedera.mirror.web3.utils.ContractCallTestUtil.TRANSACTION_GAS_LIMIT;
import static com.hedera.mirror.web3.utils.ContractCallTestUtil.isWithinExpectedGasRange;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.common.domain.token.TokenFreezeStatusEnum;
import com.hedera.mirror.common.domain.token.TokenKycStatusEnum;
import com.hedera.mirror.web3.Web3IntegrationTest;
import com.hedera.mirror.web3.common.ContractCallContext;
import com.hedera.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import com.hedera.mirror.web3.service.model.CallServiceParameters.CallType;
import com.hedera.mirror.web3.service.model.ContractDebugParameters;
import com.hedera.mirror.web3.service.model.ContractExecutionParameters;
import com.hedera.mirror.web3.utils.ContractFunctionProviderRecord;
import com.hedera.mirror.web3.viewmodel.BlockType;
import com.hedera.mirror.web3.web3j.TestWeb3jService;
import com.hedera.mirror.web3.web3j.TestWeb3jService.Web3jTestConfiguration;
import com.hedera.node.app.service.evm.store.models.HederaEvmAccount;
import com.hedera.services.store.models.Id;
import com.hedera.services.utils.EntityIdUtils;
import com.hederahashgraph.api.proto.java.Key;
import jakarta.annotation.Resource;
import java.math.BigInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.context.annotation.Import;
import org.web3j.protocol.core.RemoteFunctionCall;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.tx.Contract;

@Import(Web3jTestConfiguration.class)
@SuppressWarnings("unchecked")
public abstract class AbstractContractCallServiceTest extends Web3IntegrationTest {

    @Resource
    protected TestWeb3jService testWeb3jService;

    @Resource
    protected MirrorNodeEvmProperties mirrorNodeEvmProperties;

    public static Key getKeyWithDelegatableContractId(final Contract contract) {
        final var contractAddress = Address.fromHexString(contract.getContractAddress());

        return Key.newBuilder()
                .setDelegatableContractId(EntityIdUtils.contractIdFromEvmAddress(contractAddress))
                .build();
    }

    public static Key getKeyWithContractId(final Contract contract) {
        final var contractAddress = Address.fromHexString(contract.getContractAddress());

        return Key.newBuilder()
                .setContractID(EntityIdUtils.contractIdFromEvmAddress(contractAddress))
                .build();
    }

    @BeforeEach
    final void setup() {
        domainBuilder.recordFile().persist();
        testWeb3jService.reset();
    }

    @AfterEach
    void cleanup() {
        testWeb3jService.reset();
    }

    @SuppressWarnings("try")
    protected long gasUsedAfterExecution(final ContractExecutionParameters serviceParameters) {
        return ContractCallContext.run(ctx -> {
            ctx.initializeStackFrames(store.getStackedStateFrames());
            long result = processor
                    .execute(serviceParameters, serviceParameters.getGas())
                    .getGasUsed();

            assertThat(store.getStackedStateFrames().height()).isEqualTo(1);
            return result;
        });
    }

    protected void verifyEthCallAndEstimateGas(
            final RemoteFunctionCall<TransactionReceipt> functionCall, final Contract contract) {
        final var actualGasUsed = gasUsedAfterExecution(getContractExecutionParameters(functionCall, contract));

        testWeb3jService.setEstimateGas(true);
        final AtomicLong estimateGasUsedResult = new AtomicLong();
        // Verify eth_call
        assertDoesNotThrow(
                () -> estimateGasUsedResult.set(functionCall.send().getGasUsed().longValue()));

        // Verify eth_estimateGas
        assertThat(isWithinExpectedGasRange(estimateGasUsedResult.get(), actualGasUsed))
                .withFailMessage(ESTIMATE_GAS_ERROR_MESSAGE, estimateGasUsedResult.get(), actualGasUsed)
                .isTrue();
    }

    protected <T extends Exception> void verifyEstimateGasRevertExecution(
            final RemoteFunctionCall<TransactionReceipt> functionCall,
            final String exceptionMessage,
            Class<T> exceptionClass) {

        testWeb3jService.setEstimateGas(true);
        // Verify estimate reverts with proper message
        assertThatThrownBy(functionCall::send).isInstanceOf(exceptionClass).hasMessage(exceptionMessage);
    }

    protected void verifyEthCallAndEstimateGasWithValue(
            final RemoteFunctionCall<TransactionReceipt> functionCall,
            final Contract contract,
            final Address payerAddress,
            final long value) {
        final var actualGasUsed =
                gasUsedAfterExecution(getContractExecutionParameters(functionCall, contract, payerAddress, value));

        testWeb3jService.setEstimateGas(true);
        final AtomicLong estimateGasUsedResult = new AtomicLong();
        // Verify ethCall
        assertDoesNotThrow(
                () -> estimateGasUsedResult.set(functionCall.send().getGasUsed().longValue()));

        // Verify estimateGas
        assertThat(isWithinExpectedGasRange(estimateGasUsedResult.get(), actualGasUsed))
                .withFailMessage(ESTIMATE_GAS_ERROR_MESSAGE, estimateGasUsedResult.get(), actualGasUsed)
                .isTrue();
    }

    protected ContractExecutionParameters getContractExecutionParameters(
            final RemoteFunctionCall<?> functionCall, final Contract contract) {
        return getContractExecutionParameters(functionCall, contract, Address.ZERO, 0L);
    }

    protected ContractExecutionParameters getContractExecutionParameters(
            final Bytes data, final Address receiver, final Address payerAddress, final long value) {
        return ContractExecutionParameters.builder()
                .block(BlockType.LATEST)
                .callData(data)
                .callType(CallType.ETH_CALL)
                .gas(TRANSACTION_GAS_LIMIT)
                .isEstimate(false)
                .isStatic(false)
                .receiver(receiver)
                .sender(new HederaEvmAccount(payerAddress))
                .value(value)
                .build();
    }

    protected ContractExecutionParameters getContractExecutionParameters(
            final RemoteFunctionCall<?> functionCall,
            final Contract contract,
            final Address payerAddress,
            final long value) {
        return getContractExecutionParameters(
                Bytes.fromHexString(functionCall.encodeFunctionCall()),
                Address.fromHexString(contract.getContractAddress()),
                payerAddress,
                value);
    }

    protected Entity persistAccountEntity() {
        return domainBuilder
                .entity()
                .customize(e -> e.type(EntityType.ACCOUNT).deleted(false).balance(1_000_000_000_000L))
                .persist();
    }

    protected void persistAssociation(final Entity token, final Entity account) {
        domainBuilder
                .tokenAccount()
                .customize(ta -> ta.tokenId(token.getId())
                        .accountId(account.getId())
                        .kycStatus(TokenKycStatusEnum.GRANTED)
                        .associated(true))
                .persist();
    }

    protected void persistAssociation(final Entity token, final Long accountId) {
        domainBuilder
                .tokenAccount()
                .customize(ta -> ta.tokenId(token.getId())
                        .accountId(accountId)
                        .freezeStatus(TokenFreezeStatusEnum.UNFROZEN)
                        .kycStatus(TokenKycStatusEnum.GRANTED)
                        .associated(true))
                .persist();
    }

    protected String getAddressFromEntity(Entity entity) {
        return EntityIdUtils.asHexedEvmAddress(new Id(entity.getShard(), entity.getRealm(), entity.getNum()));
    }

    protected String getAliasFromEntity(Entity entity) {
        return Bytes.wrap(entity.getEvmAddress()).toHexString();
    }

    protected ContractDebugParameters getDebugParameters(
            final ContractFunctionProviderRecord functionProvider, final Bytes callDataBytes) {
        return ContractDebugParameters.builder()
                .block(functionProvider.block())
                .callData(callDataBytes)
                .consensusTimestamp(domainBuilder.timestamp())
                .gas(TRANSACTION_GAS_LIMIT)
                .receiver(functionProvider.contractAddress())
                .sender(new HederaEvmAccount(functionProvider.sender()))
                .value(functionProvider.value())
                .build();
    }

    protected ContractFunctionProviderRecord getContractFunctionProviderWithSender(
            final String contract, final Entity sender) {
        final var contractAddress = Address.fromHexString(contract);
        final var senderAddress = Address.fromHexString(getAliasFromEntity(sender));
        return ContractFunctionProviderRecord.builder()
                .contractAddress(contractAddress)
                .sender(senderAddress)
                .build();
    }

    public enum KeyType {
        ADMIN_KEY(1),
        KYC_KEY(2),
        FREEZE_KEY(4),
        WIPE_KEY(8),
        SUPPLY_KEY(16),
        FEE_SCHEDULE_KEY(32),
        PAUSE_KEY(64);
        final BigInteger keyTypeNumeric;

        KeyType(Integer keyTypeNumeric) {
            this.keyTypeNumeric = BigInteger.valueOf(keyTypeNumeric);
        }

        public BigInteger getKeyTypeNumeric() {
            return keyTypeNumeric;
        }
    }
}