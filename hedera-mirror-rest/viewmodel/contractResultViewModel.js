/*
 * Copyright (C) 2021-2024 Hedera Hashgraph, LLC
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

import _ from 'lodash';
import EntityId from '../entityId';
import {nsToSecNs, toHexString} from '../utils';
import {proto} from '@hashgraph/proto';

/**
 * Contract results view model
 */
class ContractResultViewModel {
  /**
   * Constructs contractResult view model
   *
   * @param {ContractResult} contractResult
   */
  constructor(contractResult) {
    const contractId = EntityId.parse(contractResult.contractId, {isNullable: true});
    this.address = contractResult?.evmAddress?.length
      ? toHexString(contractResult.evmAddress, true)
      : contractId.toEvmAddress();
    this.amount = contractResult.amount;
    this.bloom = toHexString(contractResult.bloom, true);
    this.call_result = toHexString(contractResult.callResult, true);
    this.contract_id = contractId.toString();
    this.created_contract_ids = _.toArray(contractResult.createdContractIds).map((id) => EntityId.parse(id).toString());
    this.error_message = _.isEmpty(contractResult.errorMessage) ? null : contractResult.errorMessage;
    this.from = EntityId.parse(contractResult.senderId, {isNullable: true}).toEvmAddress() || this.#extractSenderFromFunctionResult(contractResult);
    this.function_parameters = toHexString(contractResult.functionParameters, true);
    this.gas_consumed = contractResult.gasConsumed;
    this.gas_limit = contractResult.gasLimit;
    this.gas_used = contractResult.gasUsed;
    this.timestamp = nsToSecNs(contractResult.consensusTimestamp);
    this.to = contractId.toEvmAddress();
    this.hash = toHexString(contractResult.transactionHash, true);
  }

  #extractSenderFromFunctionResult(contractResult) {
    if (!contractResult.sender_id && contractResult.functionResult) {
      try {
        const functionResult = proto.ContractFunctionResult.decode(contractResult.functionResult);
        return functionResult?.senderId?.alias?.length ? toHexString(functionResult.senderId.alias, true) : null;
      } catch (error) {
        logger.warn('Error decoding function result', error);
      }
    }

    return null;
  }
}

export default ContractResultViewModel;
