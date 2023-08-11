package uk.co.openkappa.xxhash;

@FunctionalInterface
public interface MemorySegment {
    int hash(java.lang.foreign.MemorySegment data);
}
