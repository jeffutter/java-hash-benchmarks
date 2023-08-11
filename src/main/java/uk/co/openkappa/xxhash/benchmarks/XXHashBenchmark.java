package uk.co.openkappa.xxhash.benchmarks;

import org.openjdk.jmh.annotations.*;

import java.util.concurrent.TimeUnit;

@OutputTimeUnit(TimeUnit.MICROSECONDS)
@BenchmarkMode(Mode.Throughput)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(value = 1, jvmArgsPrepend = {"--add-modules=jdk.incubator.vector"})
//@Fork(value = 1, jvmArgsPrepend = {"--add-modules=jdk.incubator.vector", "-Djdk.incubator.vector.VECTOR_ACCESS_OOB_CHECK=0"})
public class XXHashBenchmark {

 @Benchmark
 public long xxhash64(XXHash64State state) {
   return state.hasher.hash(state.data, 0L);
 }

 @Benchmark
 public long xxhash32(XXHash32State state) {
   return state.hasher.hash(state.data, 0);
 }

  @Benchmark
  public long ByteBuffer(ByteBufferState state) {
    return state.hasher.hash(state.data);
  }

  @Benchmark
  public long bytes(ByteState state) {
    return state.hasher.hash(state.data, 0);
  }

  @Benchmark
  public long memorySegment(MemorySegmentState state) {
    return state.hasher.hash(state.data);
  }
}
