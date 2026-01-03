package ij.io;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class TiffPatcher {

    /**
     * Generates a standard Uncompressed RGB TIFF header.
     * Total size: 8 bytes (Header) + ~160 bytes (IFD tags).
     */
    public static byte[] generateHeader(int width, int height) {
        // We will construct a minimal TIFF. 
        // 8-byte Header + (12 bytes * NumTags) + 4 bytes (NextIFD) + Values
        
        // Tags we need for a valid RGB image:
        // 256: Width
        // 257: Height
        // 258: BitsPerSample (8,8,8)
        // 259: Compression (1 = None)
        // 262: PhotometricInterpretation (2 = RGB)
        // 273: StripOffsets (Location of pixel data)
        // 277: SamplesPerPixel (3)
        // 278: RowsPerStrip (height)
        // 279: StripByteCounts (width * height * 3)
        // 282: XResolution (72/1)
        // 283: YResolution (72/1)
        // 296: ResolutionUnit (2 = Inch)
        
        int numTags = 12;
        int headerSize = 8;
        int ifdSize = 2 + (numTags * 12) + 4; // Count + Tags + NextIFD
        int valuesSize = 100; // Buffer for values that don't fit in the tag (like BitsPerSample array)
        
        ByteBuffer buffer = ByteBuffer.allocate(headerSize + ifdSize + valuesSize);
        buffer.order(ByteOrder.LITTLE_ENDIAN);

        // --- TIFF HEADER (8 bytes) ---
        buffer.put((byte) 'I'); buffer.put((byte) 'I'); // Byte Order (Intel/Little Endian)
        buffer.putShort((short) 42);                    // TIFF Magic Number
        buffer.putInt(8);                               // Offset to first IFD (immediately after header)

        // --- IFD (Image File Directory) ---
        buffer.putShort((short) numTags);

        // Helper to write directory entry: Tag, Type, Count, Value/Offset
        // Type 1=Byte, 3=Short, 4=Long, 5=Rational

        int extraDataOffset = headerSize + ifdSize; // Where we store arrays/rationals
        
        // 256: ImageWidth
        writeTag(buffer, 256, 4, 1, width);
        
        // 257: ImageHeight
        writeTag(buffer, 257, 4, 1, height);
        
        // 258: BitsPerSample (8, 8, 8) - Pointer to array
        writeTag(buffer, 258, 3, 3, extraDataOffset);
        int bpsOffset = extraDataOffset; extraDataOffset += 6; 
        
        // 259: Compression (1 = None)
        writeTag(buffer, 259, 3, 1, 1);
        
        // 262: PhotometricInterpretation (2 = RGB)
        writeTag(buffer, 262, 3, 1, 2);
        
        // 273: StripOffsets (Where the raw image data starts)
        // It starts after the header + IFD + values
        int pixelDataStart = extraDataOffset + 16; // +16 for resolution rationals below
        writeTag(buffer, 273, 4, 1, pixelDataStart);
        
        // 277: SamplesPerPixel (3)
        writeTag(buffer, 277, 3, 1, 3);
        
        // 278: RowsPerStrip (height)
        writeTag(buffer, 278, 4, 1, height);
        
        // 279: StripByteCounts (Total size of pixel data)
        writeTag(buffer, 279, 4, 1, width * height * 3);
        
        // 282: XResolution (72/1) - Pointer
        writeTag(buffer, 282, 5, 1, extraDataOffset); 
        int xResOffset = extraDataOffset; extraDataOffset += 8;

        // 283: YResolution (72/1) - Pointer
        writeTag(buffer, 283, 5, 1, extraDataOffset);
        int yResOffset = extraDataOffset; extraDataOffset += 8;
        
        // 296: ResolutionUnit (2 = Inch)
        writeTag(buffer, 296, 3, 1, 2);

        // Next IFD Offset (0 = None)
        buffer.putInt(0);

        // --- WRITE EXTRA VALUES ---
        
        // BitsPerSample Array {8, 8, 8}
        buffer.position(bpsOffset);
        buffer.putShort((short) 8); buffer.putShort((short) 8); buffer.putShort((short) 8);
        
        // XResolution {72, 1}
        buffer.position(xResOffset);
        buffer.putInt(72); buffer.putInt(1);
        
        // YResolution {72, 1}
        buffer.position(yResOffset);
        buffer.putInt(72); buffer.putInt(1);
        
        // Final sanity check: ensure we didn't overrun the calculated start
        if (buffer.position() > pixelDataStart) {
            throw new RuntimeException("TIFF Header calculation error");
        }
        
        // Pad with zeros until the pixel data start
        byte[] header = new byte[pixelDataStart];
        System.arraycopy(buffer.array(), 0, header, 0, buffer.position());
        return header;
    }

    private static void writeTag(ByteBuffer b, int tag, int type, int count, int value) {
        b.putShort((short) tag);
        b.putShort((short) type);
        b.putInt(count);
        b.putInt(value);
    }
}