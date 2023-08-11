package uk.co.openkappa.xxhash;

import java.nio.ByteBuffer;

@FunctionalInterface
public interface HashCodeI {
    int hash(ByteBuffer data);
}
