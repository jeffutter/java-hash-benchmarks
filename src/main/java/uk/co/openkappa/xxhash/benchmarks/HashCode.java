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

import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;

@State(Scope.Benchmark)
public class HashCode {
    @Param({"10", "100", "500", "1000", "2000"})
    int size;

    @Param({"VECTOR", "VECTOR_COPY", "HASH_CODE", "SCALAR"})
    Impl impl;

//    byte[] data;
    ByteBuffer data;

    HashCodeI hasher;

    public enum Impl {
        HASH_CODE {
            @Override
            HashCodeI create() {
                return new HashCodeHasher();
            }
        },

        SCALAR {
            @Override
            HashCodeI create() {
                return new ScalarHasher();
            }
        },

        VECTOR {
            @Override
            HashCodeI create() {
                return new SIMDHasher();
            }
        },
        VECTOR_COPY {
            @Override
            HashCodeI create() {
                return new SIMDHasherCopy();
            }
        };
        abstract HashCodeI create();
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

    static class HashCodeHasher implements HashCodeI {
        @Override
        public int hash(ByteBuffer data, int _seed) {
            return data.hashCode();
        }
    }

    static class ScalarHasher implements  HashCodeI {
        @Override
        public int hash(ByteBuffer data, int _seed) {
            return Arrays.hashCode(data.array());
        }
    }

    static class SIMDHasherCopy implements HashCodeI {
        @Override
        public int hash(ByteBuffer bbdata, int _seed) {
            var data = bbdata.array();

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

    static class SIMDHasher implements HashCodeI {
        @Override
        public int hash(ByteBuffer data, int _seed) {
            var origPos = data.position();
            var ms = MemorySegment.ofBuffer(data);

            IntVector h1 = IntVector.fromArray(INT_256_SPECIES, new int[]{1, 0, 0, 0, 0, 0, 0, 0}, 0);
            IntVector h2 = IntVector.zero(INT_256_SPECIES);
            IntVector h3 = IntVector.zero(INT_256_SPECIES);
            IntVector h4 = IntVector.zero(INT_256_SPECIES);
            int i = 0;
//            for (; i < (data.length & ~(BYTE_256_SPECIES.length() - 1)); i += BYTE_256_SPECIES.length()) {
            var initLen = data.remaining();
            var bound = initLen & ~(BYTE_256_SPECIES.length() - 1);
            while(i < (initLen & ~(BYTE_256_SPECIES.length() - 1))) {
//                ByteVector b = ByteVector.fromArray(BYTE_64_SPECIES, data, i);
                ByteVector b = ByteVector.fromMemorySegment(BYTE_64_SPECIES, ms, i, ByteOrder.nativeOrder());
//                data.position(data.position() + BYTE_64_SPECIES.length());

                IntVector x = (IntVector) b.castShape(INT_256_SPECIES, 0);
                h1 = h1.mul(H_COEFF_31_TO_32).add(x.mul(H_COEFF_32));

//                b = ByteVector.fromArray(BYTE_64_SPECIES, data, i + BYTE_64_SPECIES.length());
//                b = ByteVector.fromMemorySegment(BYTE_64_SPECIES, MemorySegment.ofBuffer(data.slice(0, BYTE_64_SPECIES.length())), 0, ByteOrder.nativeOrder());
//                data.position(data.position() + BYTE_64_SPECIES.length());
                b = ByteVector.fromMemorySegment(BYTE_64_SPECIES, ms, i + BYTE_64_SPECIES.length(), ByteOrder.nativeOrder());

                x = (IntVector) b.castShape(INT_256_SPECIES, 0);
                h2 = h2.mul(H_COEFF_31_TO_32).add(x.mul(H_COEFF_24));

//                b = ByteVector.fromArray(BYTE_64_SPECIES, data, i + BYTE_64_SPECIES.length() * 2);
//                b = ByteVector.fromMemorySegment(BYTE_64_SPECIES, MemorySegment.ofBuffer(data.slice(0, BYTE_64_SPECIES.length())), 0, ByteOrder.nativeOrder());
                b = ByteVector.fromMemorySegment(BYTE_64_SPECIES, ms, i + BYTE_64_SPECIES.length() * 2L, ByteOrder.nativeOrder());
                data.position(data.position() + BYTE_64_SPECIES.length());

                x = (IntVector) b.castShape(INT_256_SPECIES, 0);
                h3 = h3.mul(H_COEFF_31_TO_32).add(x.mul(H_COEFF_16));

//                b = ByteVector.fromArray(BYTE_64_SPECIES, data, i + BYTE_64_SPECIES.length() * 3);
//                b = ByteVector.fromMemorySegment(BYTE_64_SPECIES, MemorySegment.ofBuffer(data.slice(0, BYTE_64_SPECIES.length())), 0, ByteOrder.nativeOrder());
                b = ByteVector.fromMemorySegment(BYTE_64_SPECIES, ms, i + BYTE_64_SPECIES.length() * 3L, ByteOrder.nativeOrder());
//                data.position(BYTE_256_SPECIES.length() * 4);

                x = (IntVector) b.castShape(INT_256_SPECIES, 0);
                h4 = h4.mul(H_COEFF_31_TO_32).add(x.mul(H_COEFF_8));

                i += BYTE_256_SPECIES.length();
            }

            int sh = h1.reduceLanes(VectorOperators.ADD) +
                    h2.reduceLanes(VectorOperators.ADD) +
                    h3.reduceLanes(VectorOperators.ADD) +
                    h4.reduceLanes(VectorOperators.ADD);
//            for (; i < data.length; i++) {
//                sh = 31 * sh + data[i];
//            }
            data.position(origPos);
            while(data.hasRemaining()) {
               sh = 31 * sh + data.get();
            }
            data.position(origPos);
            return sh;
        }
    }


    @Setup(Level.Trial)
    public void init() {
//        data = BenchmarkUtils.newByteArray(size);
        var d = new byte[size];
        ThreadLocalRandom.current().nextBytes(d);
        data = ByteBuffer.wrap(d);
        hasher = impl.create();
    }
}
