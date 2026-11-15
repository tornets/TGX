/*
 * This file is a part of Telegram X
 * Copyright © 2014 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package org.thunderdog.challegram.mediaview;

import java.io.EOFException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;

/**
 * Parses the video keyframe table (stss/stts/stsz/stsc/stco) out of an MP4 moov atom
 * stored in a sparse local file. Returns byte ranges of sync samples so ranged
 * downloads can fetch exactly the keyframe needed for a seek preview.
 */
public final class Mp4Keyframes {
  public final long[] timesMs;
  public final long[] offsets;
  public final long[] sizes;

  private Mp4Keyframes (long[] timesMs, long[] offsets, long[] sizes) {
    this.timesMs = timesMs;
    this.offsets = offsets;
    this.sizes = sizes;
  }

  public int keyframeCount () {
    return timesMs.length;
  }

  public int indexForTime (long timeMs) {
    int lo = 0, hi = timesMs.length - 1, result = -1;
    while (lo <= hi) {
      int mid = (lo + hi) >>> 1;
      if (timesMs[mid] <= timeMs) {
        result = mid;
        lo = mid + 1;
      } else {
        hi = mid - 1;
      }
    }
    return result;
  }

  public static final class HoleException extends IOException {
    public final long offset;
    public final long length;

    HoleException (long offset, long length) {
      super("hole @" + offset + "+" + length);
      this.offset = offset;
      this.length = length;
    }
  }

  public static Mp4Keyframes parse (RandomAccessFile raf, long fileSize) throws IOException {
    ArrayList<Box> topLevel = new ArrayList<>();
    long pos = 0;
    while (pos < fileSize) {
      Box box = readBoxHeader(raf, pos);
      if (box == null) {
        break;
      }
      topLevel.add(box);
      if (box.size <= 0) {
        break;
      }
      pos += box.size;
    }
    Box moov = findBox(topLevel, "moov");
    if (moov == null) {
      return null;
    }
    ArrayList<Box> moovChildren = readChildren(raf, moov, fileSize);
    Box trak = findBox(moovChildren, "trak");
    if (trak == null) {
      return null;
    }
    ArrayList<Box> trakChildren = readChildren(raf, trak, fileSize);
    Box mdia = findBox(trakChildren, "mdia");
    if (mdia == null) {
      return null;
    }
    ArrayList<Box> mdiaChildren = readChildren(raf, mdia, fileSize);
    Box mdhd = findBox(mdiaChildren, "mdhd");
    if (mdhd == null) {
      return null;
    }
    BoxReader mdhdReader = new BoxReader(raf, mdhd);
    mdhdReader.skip(4); // version + flags
    if (mdhdReader.u8() == 1) {
      mdhdReader.skip(16);
    } else {
      mdhdReader.skip(8);
    }
    long timescale = mdhdReader.u32();
    if (timescale <= 0) {
      return null;
    }
    Box minf = findBox(mdiaChildren, "minf");
    if (minf == null) {
      return null;
    }
    Box stbl = findBox(readChildren(raf, minf, fileSize), "stbl");
    if (stbl == null) {
      return null;
    }
    ArrayList<Box> stblChildren = readChildren(raf, stbl, fileSize);
    Box stts = findBox(stblChildren, "stts");
    Box stsz = findBox(stblChildren, "stsz");
    Box stsc = findBox(stblChildren, "stsc");
    Box stco = findBox(stblChildren, "stco");
    if (stco == null) {
      stco = findBox(stblChildren, "co64");
    }
    if (stts == null || stsz == null || stsc == null || stco == null) {
      return null;
    }
    Box stss = findBox(stblChildren, "stss");
    if (stss == null) {
      return null; // every sample is a sync frame; treat as unsupported
    }

    long[] chunkOffsets = readChunkOffsets(raf, stco, fileSize);
    int[] chunkSampleCounts = readChunkSampleCounts(raf, stsc, fileSize);
    long[] sampleSizes = readSampleSizes(raf, stsz, fileSize);
    int sampleCount = sampleSizes.length;
    int[] sttsCounts = new int[0];
    int[] sttsDeltas = new int[0];
    {
      BoxReader r = new BoxReader(raf, stts);
      r.skip(4);
      int entryCount = (int) r.u32();
      sttsCounts = new int[entryCount];
      sttsDeltas = new int[entryCount];
      for (int i = 0; i < entryCount; i++) {
        sttsCounts[i] = (int) r.u32();
        sttsDeltas[i] = (int) r.u32();
      }
    }
    int[] syncSamples = new int[0];
    {
      BoxReader r = new BoxReader(raf, stss);
      r.skip(4);
      int entryCount = (int) r.u32();
      syncSamples = new int[entryCount];
      for (int i = 0; i < entryCount; i++) {
        syncSamples[i] = (int) r.u32();
      }
    }

    long[] sampleTimes = new long[sampleCount];
    long[] sampleOffsets = new long[sampleCount];
    long time = 0;
    int sampleIndex = 0;
    int sttsIndex = 0;
    int sttsRemaining = sttsCounts.length > 0 ? sttsCounts[0] : 0;
    int chunkIndex = 0;
    int samplesInChunkRemaining = chunkSampleCounts.length > 0 ? chunkSampleCounts[0] : 0;
    long offset = chunkOffsets.length > 0 ? chunkOffsets[0] : 0;
    while (sampleIndex < sampleCount) {
      sampleTimes[sampleIndex] = time;
      sampleOffsets[sampleIndex] = offset;
      time += sttsIndex < sttsDeltas.length ? sttsDeltas[sttsIndex] : 0;
      offset += sampleSizes[sampleIndex];
      sampleIndex++;
      if (--sttsRemaining <= 0 && sttsIndex + 1 < sttsCounts.length) {
        sttsIndex++;
        sttsRemaining = sttsCounts[sttsIndex];
      }
      if (--samplesInChunkRemaining <= 0 && chunkIndex + 1 < chunkOffsets.length) {
        chunkIndex++;
        offset = chunkOffsets[chunkIndex];
        samplesInChunkRemaining = chunkIndex < chunkSampleCounts.length ? chunkSampleCounts[chunkIndex] : 1;
      }
    }

    ArrayList<Long> kfTimes = new ArrayList<>();
    ArrayList<Long> kfOffsets = new ArrayList<>();
    ArrayList<Long> kfSizes = new ArrayList<>();
    for (int s : syncSamples) {
      int index = s - 1;
      if (index >= 0 && index < sampleCount) {
        kfTimes.add(sampleTimes[index] * 1000L / timescale);
        kfOffsets.add(sampleOffsets[index]);
        kfSizes.add(sampleSizes[index]);
      }
    }
    if (kfTimes.isEmpty()) {
      return null;
    }
    return new Mp4Keyframes(toArray(kfTimes), toArray(kfOffsets), toArray(kfSizes));
  }

  private static long[] toArray (ArrayList<Long> list) {
    long[] result = new long[list.size()];
    for (int i = 0; i < result.length; i++) {
      result[i] = list.get(i);
    }
    return result;
  }

  private static Box findBox (ArrayList<Box> boxes, String type) {
    for (Box box : boxes) {
      if (box.type.equals(type)) {
        return box;
      }
    }
    return null;
  }

  private static ArrayList<Box> readChildren (RandomAccessFile raf, Box parent, long fileSize) throws IOException {
    ArrayList<Box> children = new ArrayList<>();
    long end = parent.offset + parent.size;
    long pos = parent.offset + 8;
    while (pos + 8 <= end) {
      Box box = readBoxHeader(raf, pos);
      if (box == null || box.size <= 0 || box.offset + box.size > end) {
        break;
      }
      children.add(box);
      pos += box.size;
    }
    return children;
  }

  private static Box readBoxHeader (RandomAccessFile raf, long offset) throws IOException {
    byte[] header = new byte[8];
    try {
      raf.seek(offset);
      raf.readFully(header);
    } catch (EOFException e) {
      return null;
    }
    long size = ((header[0] & 0xffL) << 24) | ((header[1] & 0xffL) << 16) | ((header[2] & 0xffL) << 8) | (header[3] & 0xffL);
    String type = new String(header, 4, 4, "ISO-8859-1");
    if (size == 1) {
      byte[] large = new byte[8];
      raf.readFully(large);
      size = 0;
      for (byte b : large) {
        size = (size << 8) | (b & 0xffL);
      }
      return new Box(offset, size, type);
    }
    if (size == 0) {
      return new Box(offset, Long.MAX_VALUE - offset, type);
    }
    if (size < 8) {
      throw new HoleException(offset, 64 * 1024);
    }
    return new Box(offset, size, type);
  }

  private static long[] readChunkOffsets (RandomAccessFile raf, Box stco, long fileSize) throws IOException {
    BoxReader r = new BoxReader(raf, stco);
    r.skip(4);
    int entryCount = (int) r.u32();
    boolean wide = stco.type.equals("co64");
    long[] result = new long[entryCount];
    for (int i = 0; i < entryCount; i++) {
      result[i] = wide ? r.u64() : r.u32();
    }
    return result;
  }

  private static int[] readChunkSampleCounts (RandomAccessFile raf, Box stsc, long fileSize) throws IOException {
    BoxReader r = new BoxReader(raf, stsc);
    r.skip(4);
    int entryCount = (int) r.u32();
    long[] firstChunks = new long[entryCount];
    int[] counts = new int[entryCount];
    for (int i = 0; i < entryCount; i++) {
      firstChunks[i] = r.u32();
      counts[i] = (int) r.u32();
      r.skip(4); // sample description index
    }
    int[] result = new int[0];
    ArrayList<Integer> list = new ArrayList<>();
    for (int i = 0; i < entryCount; i++) {
      long nextFirst = i + 1 < entryCount ? firstChunks[i + 1] : Long.MAX_VALUE;
      int runLength = (int) Math.max(1, Math.min(nextFirst - firstChunks[i], 1_000_000L));
      for (int c = 0; c < runLength; c++) {
        list.add(counts[i]);
      }
    }
    result = new int[list.size()];
    for (int i = 0; i < result.length; i++) {
      result[i] = list.get(i);
    }
    return result;
  }

  private static long[] readSampleSizes (RandomAccessFile raf, Box stsz, long fileSize) throws IOException {
    BoxReader r = new BoxReader(raf, stsz);
    r.skip(4);
    long uniformSize = r.u32();
    int sampleCount = (int) r.u32();
    long[] result = new long[sampleCount];
    if (uniformSize != 0) {
      for (int i = 0; i < sampleCount; i++) {
        result[i] = uniformSize;
      }
    } else {
      for (int i = 0; i < sampleCount; i++) {
        result[i] = r.u32();
      }
    }
    return result;
  }

  private static final class Box {
    final long offset;
    final long size;
    final String type;

    Box (long offset, long size, String type) {
      this.offset = offset;
      this.size = size;
      this.type = type;
    }
  }

  private static final class BoxReader {
    private final RandomAccessFile raf;
    private final long end;
    private long pos;

    BoxReader (RandomAccessFile raf, Box box) throws IOException {
      this.raf = raf;
      this.pos = box.offset + 8;
      this.end = box.offset + box.size;
      raf.seek(pos);
    }

    void skip (long count) throws IOException {
      pos += count;
      raf.seek(pos);
    }

    long u8 () throws IOException {
      return raf.readUnsignedByte();
    }

    long u32 () throws IOException {
      long result = ((long) raf.readUnsignedShort() << 16) | raf.readUnsignedShort();
      pos += 4;
      return result;
    }

    long u64 () throws IOException {
      long result = raf.readLong();
      pos += 8;
      return result;
    }
  }
}
