package com.davidthomasbernal.stardict.parsers;

import com.davidthomasbernal.stardict.dictionary.DictionaryIndex;
import com.davidthomasbernal.stardict.dictionary.DictionaryInfo;
import com.davidthomasbernal.stardict.dictionary.IndexEntry;
import com.davidthomasbernal.stardict.util.IndexInputStream;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public class IdxParser {
    private final DictionaryInfo dictionaryInfo;
    private Logger logger = Logger.getLogger(this.getClass().getName());
    private boolean tolerateInfoMismatch;

    public IdxParser(DictionaryInfo info, boolean tolerateInfoMismatch) {
        this.tolerateInfoMismatch = tolerateInfoMismatch;
        this.dictionaryInfo = info;
    }

    public DictionaryIndex parse(InputStream stream) {
        IndexInputStream indexStream = new IndexInputStream(stream);

        List<IndexEntry> entries = readEntries(indexStream);
        validateEntries(entries);

        return new DictionaryIndex(entries);
    }

    protected List<IndexEntry> readEntries(IndexInputStream indexStream) {
        List<IndexEntry> entries = new ArrayList<>(dictionaryInfo.getWordCount());

        boolean isPartial = false;
        try {
            //noinspection InfiniteLoopStatement
            while (true) {
                isPartial = false;

                String word = indexStream.readWordString();

                isPartial = true;

                long dataOffset = getDataOffset(indexStream);
                long dataSize = indexStream.readInt();

                entries.add(new IndexEntry(word, dataOffset, dataSize));

                if (entries.size() > dictionaryInfo.getWordCount()) {
                    throw new IndexFormatException("Found more words than specified in info.");
                }
            }
        } catch (EOFException exception) {
            // this is thrown when we reach the end of the file. If we're partway through initializing the entry,
            // that's bad otherwise it's fine
            if (isPartial) {
                throw new IndexFormatException("Reached the end of the index before we finished parsing a word entry. The index is probably invalid.");
            }
        } catch (IOException exception) {
            throw new IndexFormatException("IOException reading index", exception);
        }

        return entries;
    }

    private long getDataOffset(IndexInputStream indexStream) throws IOException {
        long dataOffset;

        switch (dictionaryInfo.getIdxOffsetFormat()) {
            case DictionaryInfo.IDX_OFFSET_FORMAT_LONG:
                dataOffset = indexStream.readLong();
                break;
            case DictionaryInfo.IDX_OFFSET_FORMAT_INT:
                dataOffset = indexStream.readInt();
                break;
            default:
                throw new IllegalArgumentException("DictionaryInfo contains an unknown offset format");
        }

        return dataOffset;
    }

    protected void validateEntries(List<IndexEntry> entries) {
        if (entries.size() != dictionaryInfo.getWordCount()) {
            String message = "Info and syn word counts did not match: " + entries.size() + " " + dictionaryInfo.getWordCount();
            if (tolerateInfoMismatch) {
                logger.warning(message);
            } else {
                throw new IndexFormatException(message);
            }
        }
    }
}
