/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink.ml.feature.standardscaler;

import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.common.serialization.Encoder;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.connector.file.src.reader.SimpleStreamFormat;
import org.apache.flink.core.fs.FSDataInputStream;
import org.apache.flink.core.memory.DataInputViewStreamWrapper;
import org.apache.flink.core.memory.DataOutputViewStreamWrapper;
import org.apache.flink.ml.linalg.DenseVector;
import org.apache.flink.ml.linalg.typeinfo.DenseVectorSerializer;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.table.api.Table;
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment;
import org.apache.flink.table.api.internal.TableImpl;
import org.apache.flink.types.Row;

import java.io.EOFException;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Model data of {@link StandardScalerModel}.
 *
 * <p>This class also provides methods to convert model data from Table to Datastream, and classes
 * to save/load model data.
 */
public class StandardScalerModelData {
    /** Mean of each dimension. */
    public DenseVector mean;
    /** Standard deviation of each dimension. */
    public DenseVector std;

    public StandardScalerModelData() {}

    public StandardScalerModelData(DenseVector mean, DenseVector std) {
        this.mean = mean;
        this.std = std;
    }

    /**
     * Converts the table model to a data stream.
     *
     * @param modelData The table model data.
     * @return The data stream model data.
     */
    public static DataStream<StandardScalerModelData> getModelDataStream(Table modelData) {
        StreamTableEnvironment tEnv =
                (StreamTableEnvironment) ((TableImpl) modelData).getTableEnvironment();

        return tEnv.toDataStream(modelData)
                .map(
                        (MapFunction<Row, StandardScalerModelData>)
                                row ->
                                        new StandardScalerModelData(
                                                (DenseVector) row.getField("mean"),
                                                (DenseVector) row.getField("std")));
    }

    /** Data encoder for the {@link StandardScalerModel} model data. */
    public static class ModelDataEncoder implements Encoder<StandardScalerModelData> {
        @Override
        public void encode(StandardScalerModelData modelData, OutputStream outputStream)
                throws IOException {
            DataOutputViewStreamWrapper outputViewStreamWrapper =
                    new DataOutputViewStreamWrapper(outputStream);

            DenseVectorSerializer.INSTANCE.serialize(modelData.mean, outputViewStreamWrapper);
            DenseVectorSerializer.INSTANCE.serialize(modelData.std, outputViewStreamWrapper);
        }
    }

    /** Data decoder for the {@link StandardScalerModel} model data. */
    public static class ModelDataDecoder extends SimpleStreamFormat<StandardScalerModelData> {
        @Override
        public Reader<StandardScalerModelData> createReader(
                Configuration configuration, FSDataInputStream inputStream) {
            return new Reader<StandardScalerModelData>() {

                @Override
                public StandardScalerModelData read() throws IOException {
                    DataInputViewStreamWrapper inputViewStreamWrapper =
                            new DataInputViewStreamWrapper(inputStream);

                    try {
                        DenseVector mean =
                                DenseVectorSerializer.INSTANCE.deserialize(inputViewStreamWrapper);
                        DenseVector std =
                                DenseVectorSerializer.INSTANCE.deserialize(inputViewStreamWrapper);
                        return new StandardScalerModelData(mean, std);
                    } catch (EOFException e) {
                        return null;
                    }
                }

                @Override
                public void close() throws IOException {
                    inputStream.close();
                }
            };
        }

        @Override
        public TypeInformation<StandardScalerModelData> getProducedType() {
            return TypeInformation.of(StandardScalerModelData.class);
        }
    }
}
