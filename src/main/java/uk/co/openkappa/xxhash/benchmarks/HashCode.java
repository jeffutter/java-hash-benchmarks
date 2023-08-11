package uk.co.openkappa.xxhash.benchmarks;

import jdk.incubator.vector.IntVector;
import org.openjdk.jmh.annotations.*;
import uk.co.openkappa.xxhash.*;
import jdk.incubator.vector.ByteVector;
import jdk.incubator.vector.VectorOperators;
import jdk.incubator.vector.VectorSpecies;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;

@State(Scope.Benchmark)
public class HashCode {
    @Param({"10", "100", "500", "1000", "2000"})
    int size;

    @Param({"VECTOR", "HASH_CODE", "SCALAR"})
    Impl impl;

    byte[] data;

    Hasher32 hasher;

    public enum Impl {
        HASH_CODE {
            @Override
            Hasher32 create() {
                return new HashCodeHasher();
            }
        },

        SCALAR {
            @Override
            Hasher32 create() {
                return new ScalarHasher();
            }
        },

        VECTOR {
            @Override
            Hasher32 create() {
                return new SIMDHasher();
            }
        };
        abstract Hasher32 create();
    }


    static final VectorSpecies<Integer> INT_256_SPECIES = IntVector.SPECIES_256;

    static final VectorSpecies<Byte> BYTE_64_SPECIES = ByteVector.SPECIES_64;
    static final VectorSpecies<Byte> BYTE_128_SPECIES = ByteVector.SPECIES_128;
    static final VectorSpecies<Byte> BYTE_256_SPECIES = ByteVector.SPECIES_256;

    static final int COEFF_31_TO_8;
    static final int COEFF_31_TO_16;
    static final int COEFF_31_TO_32;

    static final IntVector H_COEFF_31_TO_8;
    static final IntVector H_COEFF_31_TO_16;
    static final IntVector H_COEFF_31_TO_32;

    static final IntVector H_COEFF_8;
    static final IntVector H_COEFF_16;
    static final IntVector H_COEFF_24;
    static final IntVector H_COEFF_32;

    static {
        int[] x = new int[INT_256_SPECIES.length() * 4];
        x[x.length - 1] = 1;
        for (int i = 1; i < x.length; i++) {
            x[x.length - 1 - i] = x[x.length - 1 - i + 1] * 31;
        }

        COEFF_31_TO_8 = x[24] * 31;
        COEFF_31_TO_16 = x[16] * 31;
        COEFF_31_TO_32 = x[0] * 31;

        H_COEFF_31_TO_8 = IntVector.broadcast(INT_256_SPECIES, COEFF_31_TO_8);
        H_COEFF_31_TO_16 = IntVector.broadcast(INT_256_SPECIES, COEFF_31_TO_16);
        H_COEFF_31_TO_32 = IntVector.broadcast(INT_256_SPECIES, COEFF_31_TO_32);

        H_COEFF_8 = IntVector.fromArray(INT_256_SPECIES, x, 24);
        H_COEFF_16 = IntVector.fromArray(INT_256_SPECIES, x, 16);
        H_COEFF_24 = IntVector.fromArray(INT_256_SPECIES, x, 8);
        H_COEFF_32 = IntVector.fromArray(INT_256_SPECIES, x, 0);
    }

    static class HashCodeHasher implements Hasher32 {
        @Override
        public int hash(byte[] data, int _seed) {
            return data.hashCode();
        }
    }

    static class ScalarHasher implements Hasher32 {
        @Override
        public int hash(byte[] data, int _seed) {
            return Arrays.hashCode(data);
        }
    }

    static class SIMDHasher implements Hasher32 {
        @Override
        public int hash(byte[] data, int _seed) {
            IntVector h1 = IntVector.fromArray(INT_256_SPECIES, new int[]{1, 0, 0, 0, 0, 0, 0, 0}, 0);
            IntVector h2 = IntVector.zero(INT_256_SPECIES);
            IntVector h3 = IntVector.zero(INT_256_SPECIES);
            IntVector h4 = IntVector.zero(INT_256_SPECIES);
            int i = 0;
            for (; i < (data.length & ~(BYTE_256_SPECIES.length() - 1)); i += BYTE_256_SPECIES.length()) {
                ByteVector b = ByteVector.fromArray(BYTE_64_SPECIES, data, i);
                IntVector x = (IntVector) b.castShape(INT_256_SPECIES, 0);
                h1 = h1.mul(H_COEFF_31_TO_32).add(x.mul(H_COEFF_32));

                b = ByteVector.fromArray(BYTE_64_SPECIES, data, i + BYTE_64_SPECIES.length());
                x = (IntVector) b.castShape(INT_256_SPECIES, 0);
                h2 = h2.mul(H_COEFF_31_TO_32).add(x.mul(H_COEFF_24));

                b = ByteVector.fromArray(BYTE_64_SPECIES, data, i + BYTE_64_SPECIES.length() * 2);
                x = (IntVector) b.castShape(INT_256_SPECIES, 0);
                h3 = h3.mul(H_COEFF_31_TO_32).add(x.mul(H_COEFF_16));

                b = ByteVector.fromArray(BYTE_64_SPECIES, data, i + BYTE_64_SPECIES.length() * 3);
                x = (IntVector) b.castShape(INT_256_SPECIES, 0);
                h4 = h4.mul(H_COEFF_31_TO_32).add(x.mul(H_COEFF_8));
            }

            int sh = h1.reduceLanes(VectorOperators.ADD) +
                    h2.reduceLanes(VectorOperators.ADD) +
                    h3.reduceLanes(VectorOperators.ADD) +
                    h4.reduceLanes(VectorOperators.ADD);
            for (; i < data.length; i++) {
                sh = 31 * sh + data[i];
            }
            return sh;
        }
    }


    @Setup(Level.Trial)
    public void init() {
//        data = BenchmarkUtils.newByteArray(size);
        data = new byte[size];
        ThreadLocalRandom.current().nextBytes(data);
        hasher = impl.create();
    }
}
