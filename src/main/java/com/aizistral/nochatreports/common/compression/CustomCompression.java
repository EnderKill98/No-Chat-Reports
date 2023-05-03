package com.aizistral.nochatreports.common.compression;

import com.aizistral.nochatreports.common.NCRCore;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import me.shedaniel.cloth.clothconfig.shadowed.blue.endless.jankson.annotation.Nullable;
import net.minecraft.network.FriendlyByteBuf;
import org.apache.commons.lang3.NotImplementedException;

import java.io.*;
import java.nio.charset.StandardCharsets;

public class CustomCompression extends Compression {
    private static BiMap<Integer, String>[] CUSTOM_DICTIONARIES = null;

    private static void loadCustomDictionaries() {
        CUSTOM_DICTIONARIES = new BiMap[0];
        // Currently only one dictionary exists
        HashBiMap<Integer, String> bestDictWords = HashBiMap.create(560604);
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(Compression.class.getResourceAsStream("/assets/nochatreports/dictionaries/bestDict.txt")));
            String line;
            int i = 0;
            while((line = reader.readLine()) != null) {
                bestDictWords.put(i, line.strip());
                i++;
            }
            NCRCore.LOGGER.info("Loaded bestDict.txt: " + i + " entries");
            reader.close();
        }catch (IOException ex) {
            NCRCore.LOGGER.error("Failed to load dict 0 (\"bestDict.txt\")", ex);
        }
        CUSTOM_DICTIONARIES = new BiMap[] { bestDictWords };
    }

    public static @Nullable BiMap<Integer, String> findCustomDictionary(int dictId) {
        if(CUSTOM_DICTIONARIES == null) loadCustomDictionaries();
        if(dictId < 0 || dictId >= CUSTOM_DICTIONARIES.length) return null;
        return CUSTOM_DICTIONARIES[dictId];
    }

    @Override
    public boolean hasValidHeader(byte[] data) {
        if(data.length == 0) return false;
        int compressionId = (data[0] >> 4) & 0x0F;
        if(compressionId != 0x01) return false;
        if (findCustomDictionary(getDictId(data)) == null) return false;
        return true;
    }

    public int getDictId(byte[] data) {
        if(data.length == 0) throw new IllegalArgumentException("Data is empty!");
        return data[0] & 0x0F;
    }

    @Override
    public String getCompressionName() {
        return "Custom";
    }

    @Override
    public byte[] compress(byte[] message) throws IOException {
        if(CUSTOM_DICTIONARIES == null) loadCustomDictionaries();
        byte[] bestCompressed = null;
        for(int dictId = 0; dictId < CUSTOM_DICTIONARIES.length; dictId++) {
            if(dictId >= 0b1111) throw new NotImplementedException("Can't encode dict id " + dictId + "!");
            byte[] header = new byte[] { (byte) (0x10 | dictId) };
            byte[] compressed = compress(header, message);
            if(bestCompressed == null || compressed.length < bestCompressed.length)
                bestCompressed = compressed;
        }
        if(bestCompressed == null)
            throw new NotImplementedException("Unexpected: Not a single dictionary was found to compress!");
        return bestCompressed;
    }

    @Override
    public byte[] compress(byte[] header, byte[] message) throws IOException {
        if(!hasValidHeader(header)) throw new IOException("Invalid header!");
        int dictId = getDictId(header);
        BiMap<String, Integer> wordToIndex = findCustomDictionary(dictId).inverse();

        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        compressed.write(header);
        ByteArrayOutputStream currentWord = new ByteArrayOutputStream();
        for(int i = 0; i < message.length; i++) {
            if(message[i] == (byte) ' ') {
                // Compress previous word
                byte[] compressedWord = compressWord(wordToIndex, currentWord.toByteArray());
                compressed.write(compressedWord, 0, compressedWord.length);
                currentWord.reset();
            } else {
                // Add to word
                currentWord.write(new byte[] { message[i]}, 0, 1);
            }
        }

        // Last remaining word
        if(currentWord.size() > 0) {
            byte[] compressedWord = compressWord(wordToIndex, currentWord.toByteArray());
            compressed.write(compressedWord, 0, compressedWord.length);
        }

        return compressed.toByteArray();
    }

    private byte[] compressWord(BiMap<String, Integer> wordToIndex, byte[] word) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(ByteBufAllocator.DEFAULT.buffer());

        // Attempt to detect as UTF-8 String and find in dictionary
        try {
            String wordStr = new String(word, StandardCharsets.UTF_8);
            int wordIndex = wordToIndex.getOrDefault(wordStr, -1);
            if(wordIndex != -1) {
                buffer.writeVarInt(wordIndex + 1);
                return ByteBufUtil.getBytes(buffer); // Done
            }
        }catch (Exception ex) {}

        // Either not in dictionary or failed o read as string. Write as nonexistant word...
        buffer.writeVarInt(0);
        buffer.writeByteArray(word);
        return ByteBufUtil.getBytes(buffer);
    }

    @Override
    public byte[] decompress(byte[] data) throws IOException {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.wrappedBuffer(data));
        byte[] header = new byte[] { buffer.readByte() };
        if(!hasValidHeader(header)) throw new IOException("Invalid header!");
        int dictId = getDictId(header);
        BiMap<Integer, String> indexToWord = findCustomDictionary(dictId);

        int wordCount = 0;
        ByteArrayOutputStream decompressed = new ByteArrayOutputStream();
        while(buffer.readableBytes() > 0) {
            int maybeWordIndex = buffer.readVarInt();
            if(wordCount > 0)
                decompressed.write(' ');
            wordCount++;
            if(maybeWordIndex == 0) {
                // No dict entry but data (should be UTF-8, but we allow any bytes
                decompressed.write(buffer.readByteArray());
            }else {
                String word = indexToWord.getOrDefault(maybeWordIndex - 1, "<Missing word!>");
                decompressed.write(word.getBytes(StandardCharsets.UTF_8));
            }
        }
        return decompressed.toByteArray();
    }

}
