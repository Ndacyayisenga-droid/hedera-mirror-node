package com.hedera.mirror.importer.util;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2021 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.ByteString;
import com.google.protobuf.Internal;
import com.google.protobuf.UnsafeByteOperations;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractLoginfo;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
import com.hederahashgraph.api.proto.java.ThresholdKey;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionID;
import java.nio.ByteBuffer;
import java.time.Instant;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang3.RandomUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class UtilityTest {

    private static final String KEY = "c83755a935e442f18f12fbb9ecb5bc416417059ddb3c15aac32c1702e7da6734";

    @Test
    void getPublicKeyWhenNull() {
        assertThat(Utility.getPublicKey(null)).isNull();
    }

    @Test
    void getPublicKeyWhenError() {
        assertThat(Utility.getPublicKey(new byte[] {0, 1, 2})).isNull();
    }

    @Test
    void getPublicKeyWhenEd25519() throws Exception {
        var bytes = Key.newBuilder().setEd25519(ByteString.copyFrom(Hex.decodeHex(KEY))).build().toByteArray();
        assertThat(Utility.getPublicKey(bytes)).isEqualTo(KEY);
    }

    @Test
    void getPublicKeyWhenECDSASecp256K1() throws Exception {
        var bytes = Key.newBuilder().setECDSASecp256K1(ByteString.copyFrom(Hex.decodeHex(KEY))).build().toByteArray();
        assertThat(Utility.getPublicKey(bytes)).isEqualTo(KEY);
    }

    @Test
    void getPublicKeyWhenECDSA384() throws Exception {
        var bytes = Key.newBuilder().setECDSA384(ByteString.copyFrom(Hex.decodeHex(KEY))).build().toByteArray();
        assertThat(Utility.getPublicKey(bytes)).isEqualTo(KEY);
    }

    @Test
    void getPublicKeyWhenRSA3072() throws Exception {
        var bytes = Key.newBuilder().setRSA3072(ByteString.copyFrom(Hex.decodeHex(KEY))).build().toByteArray();
        assertThat(Utility.getPublicKey(bytes)).isEqualTo(KEY);
    }

    @Test
    void getPublicKeyWhenDefaultInstance() {
        byte[] keyBytes = Key.getDefaultInstance().toByteArray();
        assertThat(Utility.getPublicKey(keyBytes)).isEmpty();
    }

    @Test
    void getPublicKeyWhenEmpty() {
        byte[] keyBytes = Key.newBuilder().setEd25519(ByteString.EMPTY).build().toByteArray();
        assertThat(Utility.getPublicKey(keyBytes)).isEmpty();
    }

    @Test
    void getPublicKeyWhenSimpleKeyList() throws Exception {
        var key = Key.newBuilder().setECDSASecp256K1(ByteString.copyFrom(Hex.decodeHex(KEY))).build();
        var keyList = KeyList.newBuilder().addKeys(key).build();
        var bytes = Key.newBuilder().setKeyList(keyList).build().toByteArray();
        assertThat(Utility.getPublicKey(bytes)).isEqualTo(KEY);
    }

    @Test
    void getPublicKeyWhenMaxDepth() throws Exception {
        var primitiveKey = Key.newBuilder().setEd25519(ByteString.copyFrom(Hex.decodeHex(KEY))).build();
        var keyList2 = KeyList.newBuilder().addKeys(primitiveKey).build();
        var key2 = Key.newBuilder().setKeyList(keyList2).build();
        var keyList1 = KeyList.newBuilder().addKeys(key2).build();
        var bytes = Key.newBuilder().setKeyList(keyList1).build().toByteArray();
        assertThat(Utility.getPublicKey(bytes)).isNull();
    }

    @Test
    void getPublicKeyWhenKeyList() throws Exception {
        var key = Key.newBuilder().setEd25519(ByteString.copyFrom(Hex.decodeHex(KEY))).build();
        var keyList = KeyList.newBuilder().addKeys(key).addKeys(key).build();
        var bytes = Key.newBuilder().setKeyList(keyList).build().toByteArray();
        assertThat(Utility.getPublicKey(bytes)).isNull();
    }

    @Test
    void getPublicKeyWhenSimpleThreshHoldKey() throws Exception {
        var key = Key.newBuilder().setEd25519(ByteString.copyFrom(Hex.decodeHex(KEY))).build();
        var keyList = KeyList.newBuilder().addKeys(key).build();
        var tk = ThresholdKey.newBuilder().setThreshold(1).setKeys(keyList).build();
        var bytes = Key.newBuilder().setThresholdKey(tk).build().toByteArray();
        assertThat(Utility.getPublicKey(bytes)).isEqualTo(KEY);
    }

    @Test
    void getPublicKeyWhenThreshHoldKey() throws Exception {
        var key = Key.newBuilder().setEd25519(ByteString.copyFrom(Hex.decodeHex(KEY))).build();
        var keyList = KeyList.newBuilder().addKeys(key).addKeys(key).build();
        var tk = ThresholdKey.newBuilder().setThreshold(1).setKeys(keyList).build();
        var bytes = Key.newBuilder().setThresholdKey(tk).build().toByteArray();
        assertThat(Utility.getPublicKey(bytes)).isNull();
    }

    @Test
    void getTopic() {
        ContractLoginfo contractLoginfo = ContractLoginfo.newBuilder()
                .addTopic(ByteString.copyFrom(new byte[] {0, 0, 0, 0, 0, 0, 1}))
                .addTopic(ByteString.copyFrom(new byte[] {0, 127}))
                .addTopic(ByteString.copyFrom(new byte[] {-1}))
                .addTopic(ByteString.copyFrom(new byte[] {0}))
                .addTopic(ByteString.copyFrom(new byte[] {0, 0, 0, 0}))
                .addTopic(ByteString.copyFrom(new byte[0]))
                .build();
        assertThat(Utility.getTopic(contractLoginfo, 0)).isEqualTo("01");
        assertThat(Utility.getTopic(contractLoginfo, 1)).isEqualTo("7f");
        assertThat(Utility.getTopic(contractLoginfo, 2)).isEqualTo("ff");
        assertThat(Utility.getTopic(contractLoginfo, 3)).isEqualTo("00");
        assertThat(Utility.getTopic(contractLoginfo, 4)).isEqualTo("00");
        assertThat(Utility.getTopic(contractLoginfo, 5)).isEmpty();
        assertThat(Utility.getTopic(contractLoginfo, 999)).isNull();
    }

    @Test
    @DisplayName("get TransactionId")
    void getTransactionID() {
        AccountID payerAccountId = AccountID.newBuilder().setShardNum(0).setRealmNum(0).setAccountNum(2).build();
        TransactionID transactionId = Utility.getTransactionId(payerAccountId);
        assertThat(transactionId)
                .isNotEqualTo(TransactionID.getDefaultInstance());

        AccountID testAccountId = transactionId.getAccountID();
        assertAll(
                // row counts
                () -> assertEquals(payerAccountId.getShardNum(), testAccountId.getShardNum())
                , () -> assertEquals(payerAccountId.getRealmNum(), testAccountId.getRealmNum())
                , () -> assertEquals(payerAccountId.getAccountNum(), testAccountId.getAccountNum())
        );
    }

    @ParameterizedTest(name = "with seconds {0} and nanos {1}")
    @CsvSource({
            "1569936354, 901",
            "0, 901",
            "1569936354, 0",
            "0,0"
    })
    void instantToTimestamp(long seconds, int nanos) {
        Instant instant = Instant.ofEpochSecond(seconds).plusNanos(nanos);
        Timestamp test = Utility.instantToTimestamp(instant);
        assertAll(
                () -> assertEquals(instant.getEpochSecond(), test.getSeconds())
                , () -> assertEquals(instant.getNano(), test.getNanos())
        );
    }

    @ParameterizedTest(name = "with seconds {0} and nanos {1}")
    @CsvSource({
            "1569936354, 901",
            "0, 901",
            "1569936354, 0",
            "0,0"
    })
    void convertInstantToNanos(long seconds, int nanos) {
        Long timeNanos = Utility.convertToNanos(seconds, nanos);
        Instant fromTimeStamp = Instant.ofEpochSecond(0, timeNanos);

        assertAll(
                () -> assertEquals(seconds, fromTimeStamp.getEpochSecond())
                , () -> assertEquals(nanos, fromTimeStamp.getNano())
        );
    }

    @ParameterizedTest(name = "with seconds {0} and nanos {1}")
    @CsvSource({
            "1568376750538, 0",
            "9223372036854775807, 0",
            "-9223372036854775808, 0",
            "9223372036, 854775808",
            "-9223372036, -854775809"
    })
    void convertInstantToNanosThrows(long seconds, int nanos) {
        assertThrows(ArithmeticException.class, () -> Utility.convertToNanos(seconds, nanos));
    }

    @ParameterizedTest(name = "with seconds {0} and nanos {1}")
    @CsvSource({
            "0, 0, 0",
            "1574880387, 0, 1574880387000000000",
            "1574880387, 999999999, 1574880387999999999",
            "1568376750538, 0, 9223372036854775807",
            "9223372036854775807, 0, 9223372036854775807",
            "-9223372036854775808, 0, -9223372036854775808",
            "9223372036, 854775808, 9223372036854775807",
            "-9223372036, -854775809, -9223372036854775808"
    })
    void convertInstantToNanosMax(long seconds, int nanos, long expected) {
        assertThat(Utility.convertToNanosMax(seconds, nanos)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "with instant {0}")
    @CsvSource({
            ", 0",
            "2019-11-27T18:46:27Z, 1574880387000000000",
            "2019-11-27T18:46:27.999999999Z, 1574880387999999999",
            "2262-04-11T23:47:16.854775807Z, 9223372036854775807",
            "2262-04-11T23:47:16.854775808Z, 9223372036854775807",
    })
    void convertInstantToNanosMax(Instant instant, long expected) {
        assertThat(Utility.convertToNanosMax(instant)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "with seconds {0} and nanos {1}")
    @CsvSource({
            "1569936354, 901",
            "0, 901",
            "1569936354, 0",
            "0,0"
    })
    void timeStampInNanosTimeStamp(long seconds, int nanos) {
        Timestamp timestamp = Timestamp.newBuilder().setSeconds(seconds).setNanos(nanos).build();

        Long timeStampInNanos = Utility.timeStampInNanos(timestamp);
        assertThat(timeStampInNanos).isNotNull();
        Instant fromTimeStamp = Instant.ofEpochSecond(0, timeStampInNanos);

        assertAll(
                () -> assertEquals(timestamp.getSeconds(), fromTimeStamp.getEpochSecond())
                , () -> assertEquals(timestamp.getNanos(), fromTimeStamp.getNano())
        );
    }

    @Test
    @DisplayName("converting illegal timestamp to nanos")
    void convertTimestampToNanosIllegalInput() {
        Timestamp timestamp = Timestamp.newBuilder().setSeconds(1568376750538L).setNanos(0).build();
        assertThrows(ArithmeticException.class, () -> {
            Utility.timeStampInNanos(timestamp);
        });
    }

    @Test
    void sanitize() {
        assertThat(Utility.sanitize(null)).isNull();
        assertThat(Utility.sanitize("")).isEmpty();
        assertThat(Utility.sanitize("abc")).isEqualTo("abc");
        assertThat(Utility.sanitize("abc" + (char) 0 + "123" + (char) 0)).isEqualTo("abc�123�");
    }

    @Test
    void toBytes() {
        byte[] smallArray = RandomUtils.nextBytes(Utility.UnsafeByteOutput.SIZE);
        byte[] largeArray = RandomUtils.nextBytes(256);

        assertThat(Utility.toBytes(null)).isNull();
        assertThat(Utility.toBytes(ByteString.EMPTY)).isEqualTo(new byte[0]).isSameAs(Internal.EMPTY_BYTE_ARRAY);
        assertThat(Utility.toBytes(UnsafeByteOperations.unsafeWrap(smallArray)))
                .isEqualTo(smallArray)
                .isNotSameAs(smallArray);

        assertThat(Utility.toBytes(UnsafeByteOperations.unsafeWrap(largeArray)))
                .isEqualTo(largeArray)
                .isSameAs(largeArray);

        assertThat(Utility.toBytes(UnsafeByteOperations.unsafeWrap(ByteBuffer.wrap(largeArray))))
                .isEqualTo(largeArray)
                .isNotSameAs(largeArray);
    }
}

