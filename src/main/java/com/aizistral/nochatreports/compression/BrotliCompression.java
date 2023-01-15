package com.aizistral.nochatreports.compression;

import com.aayushatharva.brotli4j.decoder.BrotliInputStream;
import com.aayushatharva.brotli4j.encoder.BrotliEncoderChannel;
import com.aayushatharva.brotli4j.encoder.Encoder;
import com.aizistral.nochatreports.NoChatReports;
import io.netty.handler.codec.compression.Brotli;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class BrotliCompression extends Compression {

    protected BrotliCompression() {
        try {
            Brotli.ensureAvailability();
            if (!Brotli.isAvailable())
                throw new RuntimeException("Brotli is not available (maybe not for your OS/Arch).");
        }catch (Throwable ex) {
            REGISTERED.remove(this);
            NoChatReports.LOGGER.error("Could not register Brotli Compression", ex);
        }
    }

    @Override
    public boolean hasValidHeader(byte[] data) {
        if(data.length == 0) return false;
        if(data[0] != 0x00) // Both id and metadata should be 0
            return false;
        return true;
    }

    @Override
    public String getCompressionName() {
        return "Brotli";
    }

    @Override
    public byte[] compress(byte[] message) throws IOException {
        byte[] brotliCompressed = BrotliEncoderChannel.compress(message, new Encoder.Parameters().setQuality(11));
        byte[] compressedWithHeader = new byte[1 + brotliCompressed.length];
        compressedWithHeader[0] = 0x00; // Header
        for(int i = 0; i < brotliCompressed.length; i++)
            compressedWithHeader[i+1] = brotliCompressed[i];
        return compressedWithHeader;
    }

    @Override
    public byte[] compress(byte[] header, byte[] message) throws IOException {
        if(header.length != 1 || !hasValidHeader(header)) throw new IOException("Invalid header!");
        return compress(message); // Will be same header as there is only 1 valid value for it
    }

    @Override
    public byte[] decompress(byte[] data) throws IOException {
        if(!hasValidHeader(data)) throw new IOException("Invalid header!");
        byte[] brotliCompressed = new byte[data.length - 1];
        for(int i = 1; i < data.length; i++)
            brotliCompressed[i-1] = data[i];
        ByteArrayInputStream dataIn = new ByteArrayInputStream(brotliCompressed);
        BrotliInputStream brotIn = new BrotliInputStream(dataIn);
        final int MAX_DECOMPRESSION_SIZE = 1024 * 10; // Don't allow more than 10 KiB of data (to mitigate "Zip Bombs")
        ByteArrayOutputStream decompressed = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int count;
        int total = 0;
        while((count = brotIn.read(buffer)) > 0) {
            total += count;
            if(total > MAX_DECOMPRESSION_SIZE) {
                throw new IOException("Decompressed data would have been over the internal limit (" + MAX_DECOMPRESSION_SIZE + " bytes). Decompression was aborted to mitigate \"Zip Bomb\" or other harmful actions to the MC Client.");
            }

            decompressed.write(buffer, 0, count);
        }

        return decompressed.toByteArray();
    }

}
