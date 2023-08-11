package uk.co.openkappa.xxhash.benchmarks;

import org.openjdk.jmh.annotations.*;

import java.util.concurrent.TimeUnit;

@OutputTimeUnit(TimeUnit.MICROSECONDS)
@BenchmarkMode(Mode.Throughput)
@Fork(value = 1, jvmArgsPrepend = {"--add-modules=jdk.incubator.vector", "-Djdk.incubator.vector.VECTOR_ACCESS_OOB_CHECK=0"})
public class XXHashBenchmark {

  @Benchmark
  @Fork(1)
  @Warmup(iterations=3)
  @Measurement(iterations = 3)
  public long xxhash64(XXHash64State state) {
    return state.hasher.hash(state.data, 0L);
  }

  @Benchmark
  @Fork(1)
  @Warmup(iterations=3)
  @Measurement(iterations = 3)
  public long xxhash32(XXHash32State state) {
    return state.hasher.hash(state.data, 0);
  }

  @Benchmark
  @Fork(1)
  @Warmup(iterations=3)
  @Measurement(iterations = 3)
  public long hashCode(HashCode state) {
    return state.hasher.hash(state.data, 0);
  }
}
