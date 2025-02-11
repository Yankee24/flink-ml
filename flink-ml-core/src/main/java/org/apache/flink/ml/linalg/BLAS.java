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

package org.apache.flink.ml.linalg;

import org.apache.flink.util.Preconditions;

/** A utility class that provides BLAS routines over matrices and vectors. */
public class BLAS {
    /** For level-1 function dspmv, use javaBLAS for better performance. */
    private static final dev.ludovic.netlib.BLAS JAVA_BLAS =
            dev.ludovic.netlib.JavaBLAS.getInstance();

    /** \sum_i |x_i| . */
    public static double asum(DenseVector x) {
        return JAVA_BLAS.dasum(x.size(), x.values, 0, 1);
    }

    /** y += a * x . */
    public static void axpy(double a, Vector x, DenseVector y) {
        Preconditions.checkArgument(x.size() == y.size(), "Vector size mismatched.");
        if (x instanceof SparseVector) {
            axpy(a, (SparseVector) x, y);
        } else {
            axpy(a, (DenseVector) x, y);
        }
    }

    /** Computes the hadamard product of the two vectors (y = y \hdot x). */
    public static void hDot(Vector x, Vector y) {
        Preconditions.checkArgument(x.size() == y.size(), "Vector size mismatched.");
        if (x instanceof SparseVector) {
            if (y instanceof SparseVector) {
                hDot((SparseVector) x, (SparseVector) y);
            } else {
                hDot((SparseVector) x, (DenseVector) y);
            }
        } else {
            if (y instanceof SparseVector) {
                hDot((DenseVector) x, (SparseVector) y);
            } else {
                hDot((DenseVector) x, (DenseVector) y);
            }
        }
    }

    /** x \cdot y . */
    public static double dot(DenseVector x, DenseVector y) {
        Preconditions.checkArgument(x.size() == y.size(), "Vector size mismatched.");
        return JAVA_BLAS.ddot(x.size(), x.values, 1, y.values, 1);
    }

    /** \sqrt(\sum_i x_i * x_i) . */
    public static double norm2(DenseVector x) {
        return JAVA_BLAS.dnrm2(x.size(), x.values, 1);
    }

    /** x = x * a . */
    public static void scal(double a, DenseVector x) {
        JAVA_BLAS.dscal(x.size(), a, x.values, 1);
    }

    /**
     * y = alpha * matrix * x + beta * y or y = alpha * (matrix^T) * x + beta * y.
     *
     * @param alpha The alpha value.
     * @param matrix Dense matrix with size m x n.
     * @param transMatrix Whether transposes matrix before multiply.
     * @param x Dense vector with size n.
     * @param beta The beta value.
     * @param y Dense vector with size m.
     */
    public static void gemv(
            double alpha,
            DenseMatrix matrix,
            boolean transMatrix,
            DenseVector x,
            double beta,
            DenseVector y) {
        Preconditions.checkArgument(
                transMatrix
                        ? (matrix.numRows() == x.size() && matrix.numCols() == y.size())
                        : (matrix.numRows() == y.size() && matrix.numCols() == x.size()),
                "Matrix and vector size mismatched.");
        final String trans = transMatrix ? "T" : "N";
        JAVA_BLAS.dgemv(
                trans,
                matrix.numRows(),
                matrix.numCols(),
                alpha,
                matrix.values,
                matrix.numRows(),
                x.values,
                1,
                beta,
                y.values,
                1);
    }

    private static void axpy(double a, DenseVector x, DenseVector y) {
        JAVA_BLAS.daxpy(x.size(), a, x.values, 1, y.values, 1);
    }

    private static void axpy(double a, SparseVector x, DenseVector y) {
        for (int i = 0; i < x.indices.length; i++) {
            int index = x.indices[i];
            y.values[index] += a * x.values[i];
        }
    }

    private static void hDot(SparseVector x, SparseVector y) {
        int idx = 0;
        int idy = 0;
        while (idx < x.indices.length && idy < y.indices.length) {
            int indexX = x.indices[idx];
            while (idy < y.indices.length && y.indices[idy] < indexX) {
                y.values[idy] = 0;
                idy++;
            }
            if (idy < y.indices.length && y.indices[idy] == indexX) {
                y.values[idy] *= x.values[idx];
                idy++;
            }
            idx++;
        }
        while (idy < y.indices.length) {
            y.values[idy] = 0;
            idy++;
        }
    }

    private static void hDot(SparseVector x, DenseVector y) {
        int idx = 0;
        for (int i = 0; i < y.size(); i++) {
            if (idx < x.indices.length && x.indices[idx] == i) {
                y.values[i] *= x.values[idx];
                idx++;
            } else {
                y.values[i] = 0;
            }
        }
    }

    private static void hDot(DenseVector x, SparseVector y) {
        for (int i = 0; i < y.values.length; i++) {
            y.values[i] *= x.values[y.indices[i]];
        }
    }

    private static void hDot(DenseVector x, DenseVector y) {
        for (int i = 0; i < x.values.length; i++) {
            y.values[i] *= x.values[i];
        }
    }
}
