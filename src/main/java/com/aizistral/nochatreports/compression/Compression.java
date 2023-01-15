package com.aizistral.nochatreports.compression;

import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;

public abstract class Compression {

    protected static ArrayList<Compression> REGISTERED = new ArrayList<>();
    public static BrotliCompression COMPRESSION_BROTLI = new BrotliCompression();
    public static CustomCompression COMPRESSION_CUSTOM = new CustomCompression();

    public abstract String getCompressionName();
    public abstract boolean hasValidHeader(byte[] data);
    public abstract byte[] compress(byte[] message) throws IOException;
    public abstract byte[] compress(byte[] header, byte[] message) throws IOException;
    public abstract byte[] decompress(byte[] data) throws IOException;

    protected Compression() {
        if(REGISTERED.stream().anyMatch((c) -> c.getCompressionName().equals(getCompressionName())))
            throw new IllegalArgumentException("This class can only be instantiated once!");
        REGISTERED.add(this);
    }

    public static Compression[] getRegistered() {
        return REGISTERED.toArray(new Compression[0]);
    }

    public static byte[] compressWithBest(byte[] message) throws IOException {
        byte[] bestCompressed = null;
        for(Compression compression : getRegistered()) {
            byte[] compressed = compression.compress(message);
            if(bestCompressed == null || compressed.length < bestCompressed.length)
                bestCompressed = compressed;
        }
        return bestCompressed;
    }

    public static @Nullable Compression findCompression(byte[] data) {
        for(Compression compression : getRegistered()) {
            if(compression.hasValidHeader(data)) return compression;
        }
        return null;
    }

    public static @Nullable byte[] tryDecompress(byte[] data) {
        Compression compression = findCompression(data);
        if(compression == null) return null;
        try {
            return compression.decompress(data);
        }catch (Exception ex) {
            return null;
        }
    }

}
